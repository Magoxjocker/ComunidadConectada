const { createHash } = require("node:crypto");
const { initializeApp, applicationDefault, deleteApp } = require("firebase-admin/app");
const { getDatabase, ServerValue } = require("firebase-admin/database");
const { getMessaging } = require("firebase-admin/messaging");
const { connectAsync } = require("mqtt");
const { RECORD_ID, deliveryError, errorCode, withDelivery, updateDelivery } = require("./spark-delivery-state");

const PROJECT_ID = "comunidadconectada-35c3c";
const DATABASE_URL = "https://comunidadconectada-35c3c-default-rtdb.firebaseio.com";
const COMMUNITY_KEY = /^[A-Za-z0-9_-]{1,120}$/;
const BATCH_SIZE = 500;
const ANNOUNCEMENT_TTL_MS = 24 * 60 * 60 * 1000;
const DEVICE_MAX_AGE_MS = 30 * ANNOUNCEMENT_TTL_MS;
const MQTT_TIMEOUT_MS = 12000;
const EXPIRED_TOKEN_ERRORS = new Set([
  "messaging/registration-token-not-registered", "messaging/invalid-registration-token"
]);

function firebaseConfiguration(environment) {
  const { CC_PROJECT_ID: projectId, CC_DATABASE_URL: databaseURL } = environment;
  if (projectId !== PROJECT_ID || databaseURL?.replace(/\/$/, "") !== DATABASE_URL ||
      environment.FIREBASE_DATABASE_EMULATOR_HOST || environment.FIREBASE_AUTH_EMULATOR_HOST) {
    throw new Error("Configura el proyecto real explícitamente en un entorno separado de los emuladores.");
  }
  return { projectId, databaseURL };
}

function configuration(args, environment) {
  const [operation, community, recordId] = args;
  if (args.length !== 3 || !["fcm", "mqtt"].includes(operation) ||
      !COMMUNITY_KEY.test(community) || !RECORD_ID.test(recordId)) {
    throw new Error("Uso: npm run spark:delivery -- mqtt|fcm COMUNIDAD ID_REGISTRO");
  }
  return { operation, community, recordId, ...firebaseConfiguration(environment) };
}

function mqttConfiguration(environment) {
  const { CC_MQTT_HOST: host, CC_MQTT_PUBLISHER_USERNAME: username, CC_MQTT_PUBLISHER_PASSWORD: password } = environment;
  if (!host || !/^[A-Za-z0-9.-]+:\d{1,5}$/.test(host) ||
      !Number(host.split(":")[1]) || Number(host.split(":")[1]) > 65535 || !username?.trim() || !password) {
    throw new Error("Configura el host MQTT con puerto TLS y el usuario y contraseña del publicador.");
  }
  return { endpoint: "mqtts://" + host, username, password };
}

async function withBroker({ endpoint, username, password }, operation) {
  const client = await connectAsync(endpoint, {
    username, password, protocolVersion: 5, reconnectPeriod: 0,
    connectTimeout: MQTT_TIMEOUT_MS, rejectUnauthorized: true
  });
  let timer;
  try {
    const timeout = new Promise((resolve, reject) => {
      timer = setTimeout(() => reject(deliveryError("mqtt-puback-timeout")), MQTT_TIMEOUT_MS);
    });
    return await Promise.race([operation(client), timeout]);
  } finally {
    clearTimeout(timer);
    await client.endAsync(true);
  }
}

function probeBroker(broker) {
  return withBroker(broker, async () => {});
}

function pendingAlert(alert, community) {
  return alert?.community === community && alert.status === "active" && alert.delivery === "pending";
}

function publishAlert(root, recordId, broker) {
  const community = root.key;
  return withDelivery(root, "alerts", recordId, alert => pendingAlert(alert, community),
    async (record, alert, progress, checkpoint, sameRecord) => {
      let { brokerAcknowledgedAt } = progress;
      if (!brokerAcknowledgedAt) {
        await checkpoint();
        await withBroker(broker, client => client.publishAsync("comunidades/" + community + "/alertas",
          JSON.stringify({ id: recordId }), { qos: 1, retain: false }));
        ({ brokerAcknowledgedAt } = await checkpoint({ brokerAcknowledgedAt: ServerValue.TIMESTAMP }));
      }
      await updateDelivery(record, sameRecord, { delivery: "published", brokerAcknowledgedAt });
      return "El broker confirmó la publicación del identificador SOS.";
    });
}

function notificationTargets(members, devices, cutoff) {
  const targets = new Map();
  for (const [uid, registrations] of Object.entries(devices)) {
    if (members[uid]?.active !== true || !registrations) continue;
    for (const [deviceId, registration] of Object.entries(registrations)) {
      if (typeof registration?.token !== "string" || !registration.token.trim() ||
          !Number.isFinite(registration.updatedAt) || registration.updatedAt < cutoff) continue;
      const previous = targets.get(registration.token);
      if (previous && previous.updatedAt >= registration.updatedAt) continue;
      targets.set(registration.token, { uid, deviceId, token: registration.token, updatedAt: registration.updatedAt });
    }
  }
  return [...targets.values()];
}

function pendingAnnouncement(announcement, since = 0) {
  return announcement?.status === "published" && !announcement.notificationDispatchedAt &&
    (announcement.createdAt ?? 0) >= since;
}

function announcementMessage(community, recordId, announcement, target) {
  const important = ["important", "urgent"].includes(announcement.priority);
  const description = typeof announcement.description === "string" ? announcement.description : "";
  return {
    token: target.token,
    data: {
      uid: target.uid, community, id: recordId,
      title: typeof announcement.title === "string" ? announcement.title : "",
      summary: Array.from(description).slice(0, 160).join("")
    },
    android: { priority: important ? "high" : "normal", ttl: ANNOUNCEMENT_TTL_MS }
  };
}

async function notifyBatch(root, messaging, recordId, announcement, batch) {
  const result = await messaging.sendEach(batch.map(({ target }) =>
    announcementMessage(root.key, recordId, announcement, target)));
  return result.responses.map((response, position) => ({
    ...batch[position],
    status: response.success ? "accepted" :
      EXPIRED_TOKEN_ERRORS.has(response.error?.code) ? "invalid" : "retry",
    code: response.success ? undefined : errorCode(response.error ?? {})
  }));
}

async function removeExpiredDevice(root, target) {
  await root.child("devices").child(target.uid).child(target.deviceId).transaction(current => {
    if (current === null) return null;
    return current.token === target.token && current.updatedAt === target.updatedAt ? null : undefined;
  }, undefined, false);
}

async function deliverAnnouncement(context, record, announcement, progress, checkpoint, sameRecord) {
  const { root, messaging, recordId, log } = context;
  if (progress.notificationDispatchedAt) {
    await updateDelivery(record, sameRecord, { notificationDispatchedAt: progress.notificationDispatchedAt });
    return "FCM ya había aceptado la entrega.";
  }

  const [members, devices] = await Promise.all([root.child("members").get(), root.child("devices").get()]);
  const outcomes = progress.notifications ?? {};
  const targets = notificationTargets(members.val() || {}, devices.val() || {}, Date.now() - DEVICE_MAX_AGE_MS);
  const pending = targets.map(target => ({
    target, digest: createHash("sha256").update(target.token).digest("hex")
  })).filter(({ digest }) => !outcomes[digest]);
  let needsRetry = false;

  for (let offset = 0; offset < pending.length; offset += BATCH_SIZE) {
    if (!sameRecord((await record.get()).val())) return;
    await checkpoint();
    const results = await notifyBatch(root, messaging, recordId, announcement, pending.slice(offset, offset + BATCH_SIZE));
    for (const result of results) {
      if (result.status === "retry") needsRetry = true;
      else outcomes[result.digest] = result.status;
    }
    await checkpoint({ notifications: outcomes });
    const counts = results.reduce((totals, result) => {
      totals[result.status] += 1;
      if (result.code) totals.codes[result.code] = (totals.codes[result.code] ?? 0) + 1;
      return totals;
    }, { accepted: 0, invalid: 0, retry: 0, codes: Object.create(null) });
    log("fcm-batch", { recordId, ...counts });
    const invalid = results.filter(result => result.status === "invalid");
    const cleanups = await Promise.allSettled(invalid.map(result => removeExpiredDevice(root, result.target)));
    for (const cleanup of cleanups) {
      if (cleanup.status === "rejected") log("fcm-device-cleanup-error", { recordId, code: errorCode(cleanup.reason) });
    }
  }

  if (needsRetry) throw deliveryError("fcm-retryable-destinations");
  const accepted = Object.values(outcomes).filter(status => status === "accepted").length;
  if (!accepted) throw deliveryError("fcm-no-active-destinations");
  const { notificationDispatchedAt } = await checkpoint({ notificationDispatchedAt: ServerValue.TIMESTAMP });
  await updateDelivery(record, sameRecord, { notificationDispatchedAt });
  return "FCM aceptó " + accepted + " solicitudes de envío.";
}

function sendAnnouncement(root, messaging, recordId, { since = 0, log = console.info } = {}) {
  const context = { root, messaging, recordId, log };
  return withDelivery(root, "announcements", recordId, announcement => pendingAnnouncement(announcement, since),
    (...delivery) => deliverAnnouncement(context, ...delivery));
}

async function main() {
  const { operation, community, recordId, projectId, databaseURL } = configuration(process.argv.slice(2), process.env);
  const broker = operation === "mqtt" ? mqttConfiguration(process.env) : undefined;
  const app = initializeApp({ projectId, databaseURL, credential: applicationDefault() });
  try {
    const root = getDatabase(app).ref("communityData").child(community);
    const result = operation === "mqtt"
      ? await publishAlert(root, recordId, broker)
      : await sendAnnouncement(root, getMessaging(app), recordId);
    console.log(result ?? "El registro debe existir y tener entrega pendiente.");
  } finally {
    getDatabase(app).goOffline();
    await deleteApp(app);
  }
}

if (require.main === module) {
  main().catch(error => {
    console.error("Entrega incompleta.", error.code ? errorCode(error) : error.message);
    process.exitCode = 1;
  });
}

module.exports = {
  configuration, firebaseConfiguration, mqttConfiguration, notificationTargets,
  probeBroker, pendingAlert, pendingAnnouncement, publishAlert, sendAnnouncement, announcementMessage
};
