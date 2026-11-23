const { createHash } = require("node:crypto");
const { getMessaging } = require("firebase-admin/messaging");
const { onValueCreated } = require("firebase-functions/v2/database");
const { defineSecret, defineString } = require("firebase-functions/params");
const { HttpsError } = require("firebase-functions/v2/https");
const jwt = require("jsonwebtoken");
const { connectAsync } = require("mqtt");
const validation = require("./validation");

const mqttSecret = defineSecret("CC_MQTT_JWT_SECRET");
const mqttHost = defineString("CC_MQTT_HOST");
const databaseInstance = defineString("CC_DATABASE_INSTANCE");
const REGION = "us-central1";
const CREDENTIAL_DURATION_SECONDS = 300;
const MAX_DEVICES_PER_USER = 12;
const FCM_BATCH_SIZE = 500;

function topic(community) {
  return `comunidades/${community}/alertas`;
}

function credentials(community, username, permission) {
  const host = mqttHost.value();
  const parsedHost = new URL(`mqtts://${host}`);
  if (parsedHost.username || parsedHost.password || parsedHost.pathname || parsedHost.search || parsedHost.hash ||
      !parsedHost.hostname || parsedHost.host !== host) throw new Error("CC_MQTT_HOST inválido");
  const secret = mqttSecret.value();
  if (Buffer.byteLength(secret) < 32) throw new Error("CC_MQTT_JWT_SECRET requiere al menos 32 bytes");
  const rule = { permission: "allow", action: permission, topic: `eq ${topic(community)}`, qos: [1] };
  if (permission === "publish") rule.retain = false;
  const token = jwt.sign({ username, acl: [rule] }, secret, {
    algorithm: "HS256", expiresIn: CREDENTIAL_DURATION_SECONDS
  });
  return { host, username, token, topic: topic(community) };
}

function mqttSubscription(context) {
  return credentials(context.account.community, context.uid, "subscribe");
}

const publishAlert = onValueCreated({
  ref: "/communityData/{community}/alerts/{record}", region: REGION, secrets: [mqttSecret], retry: true
}, async event => {
  if (event.instance !== databaseInstance.value()) return;
  const current = (await event.data.ref.get()).val();
  if (!current || current.delivery === "published") return;
  const { community, record } = event.params;
  const { host, username, token, topic: destination } = credentials(community, "cc-publisher", "publish");
  const client = await connectAsync(`mqtts://${host}`, {
    username, password: token, protocolVersion: 4, reconnectPeriod: 0,
    connectTimeout: 12000, rejectUnauthorized: true
  });
  const timer = setTimeout(() => client.end(true), 15000);
  try {
    await client.publishAsync(destination, JSON.stringify({ id: record }), { qos: 1, retain: false });
    await event.data.ref.update({ delivery: "published", brokerAcknowledgedAt: Date.now() });
  } finally {
    clearTimeout(timer);
    await client.endAsync(true);
  }
});

async function notificationDevice(context, input) {
  const token = validation.text(input.token, "dispositivo", 4096, 20);
  const enabled = validation.boolean(input.enabled, "notificaciones");
  const deviceId = createHash("sha256").update(token).digest("hex");
  const reference = context.root.child(`devices/${context.uid}`);
  await reference.transaction(devices => {
    if (!enabled) {
      if (devices) delete devices[deviceId];
      return devices || null;
    }
    const updated = { ...devices, [deviceId]: { token, updatedAt: Date.now() } };
    const sorted = Object.entries(updated).sort((left, right) => right[1].updatedAt - left[1].updatedAt);
    return Object.fromEntries(sorted.slice(0, MAX_DEVICES_PER_USER));
  });
}

const notifyAnnouncement = onValueCreated({
  ref: "/communityData/{community}/announcements/{record}", region: REGION, retry: true
}, async event => {
  if (event.instance !== databaseInstance.value()) return;
  const { community, record } = event.params;
  const root = event.data.ref.parent.parent;
  const [announcementSnapshot, membersSnapshot, devicesSnapshot] = await Promise.all([
    event.data.ref.get(), root.child("members").get(), root.child("devices").get()
  ]);
  const announcement = announcementSnapshot.val();
  if (!announcement || announcement.notificationDispatchedAt) return;
  const members = membersSnapshot.val() || {};
  const messages = [];
  const locations = [];
  for (const [uid, devices] of Object.entries(devicesSnapshot.val() || {})) {
    if (members[uid]?.active !== true) continue;
    for (const [deviceId, device] of Object.entries(devices)) {
      messages.push({ token: device.token, data: {
        community, uid, id: record, title: announcement.title, summary: Array.from(announcement.description).slice(0, 160).join("")
      }, android: { priority: announcement.priority === "urgent" ? "high" : "normal", ttl: 86400000 } });
      locations.push(`devices/${uid}/${deviceId}`);
    }
  }
  for (let start = 0; start < messages.length; start += FCM_BATCH_SIZE) {
    const response = await getMessaging().sendEach(messages.slice(start, start + FCM_BATCH_SIZE));
    const removals = {};
    let retry = false;
    response.responses.forEach((delivery, index) => {
      if (delivery.success) return;
      if (["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(delivery.error.code)) {
        removals[locations[start + index]] = null;
      } else {
        retry = true;
      }
    });
    if (Object.keys(removals).length) await root.update(removals);
    if (retry) throw new HttpsError("unavailable", "FCM no procesó todos los destinos.");
  }
  await event.data.ref.transaction(current => current ? { ...current, notificationDispatchedAt: Date.now() } : null);
});

module.exports = { REGION, mqttSecret, mqttSubscription, notificationDevice, publishAlert, notifyAnnouncement };
