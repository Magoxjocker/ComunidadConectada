const { initializeApp, applicationDefault, deleteApp } = require("firebase-admin/app");
const { getDatabase, ServerValue } = require("firebase-admin/database");
const { getMessaging } = require("firebase-admin/messaging");
const { connectAsync } = require("mqtt");

const BATCH_SIZE = 500;
const DEVICE_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;
const MQTT_TIMEOUT_MS = 12000;

function configuration(args, environment) {
  const [operation, community, recordId] = args;
  if (args.length !== 3 || !["fcm", "mqtt"].includes(operation) ||
      !/^[A-Za-z0-9_-]{1,120}$/.test(community) || !/^[A-Za-z0-9_-]{1,170}$/.test(recordId)) {
    throw new Error("Uso: npm run spark:delivery -- mqtt|fcm COMUNIDAD ID_REGISTRO");
  }
  const { CC_PROJECT_ID: projectId, CC_DATABASE_URL: databaseURL } = environment;
  if (projectId !== "comunidadconectada-35c3c" ||
      databaseURL?.replace(/\/$/, "") !== "https://comunidadconectada-35c3c-default-rtdb.firebaseio.com" ||
      environment.FIREBASE_DATABASE_EMULATOR_HOST || environment.FIREBASE_AUTH_EMULATOR_HOST) {
    throw new Error("Configura el proyecto real explícitamente en un entorno separado de los emuladores.");
  }
  return { operation, community, recordId, projectId, databaseURL };
}

function mqttConfiguration(environment) {
  const { CC_MQTT_HOST: host, CC_MQTT_PUBLISHER_USERNAME: username, CC_MQTT_PUBLISHER_PASSWORD: password } = environment;
  if (!host || !/^[A-Za-z0-9.-]+:\d{1,5}$/.test(host) || !username || !password) {
    throw new Error("Configura el host MQTT con puerto TLS y el usuario y contraseña del publicador.");
  }
  return { endpoint: "mqtts://" + host, username, password };
}

async function publishAlert(root, community, recordId, environment) {
  const reference = root.child("alerts/" + recordId);
  const alert = (await reference.get()).val();
  if (!alert || alert.status !== "active" || alert.delivery !== "pending") {
    return "El SOS debe existir, estar activo y tener publicación pendiente.";
  }
  const { endpoint, username, password } = mqttConfiguration(environment);
  const client = await connectAsync(endpoint, {
    username, password, protocolVersion: 4, reconnectPeriod: 0,
    connectTimeout: MQTT_TIMEOUT_MS, rejectUnauthorized: true
  });
  const timeout = setTimeout(() => client.end(true), MQTT_TIMEOUT_MS);
  try {
    await client.publishAsync("comunidades/" + community + "/alertas", JSON.stringify({ id: recordId }),
      { qos: 1, retain: false });
    await reference.transaction(current => current?.delivery === "pending"
      ? { ...current, delivery: "published", brokerAcknowledgedAt: ServerValue.TIMESTAMP } : undefined);
    return "El broker confirmó la publicación del identificador SOS.";
  } finally {
    clearTimeout(timeout);
    await client.endAsync(true);
  }
}

function notificationTargets(members, devices, cutoff) {
  const targets = new Map();
  for (const [uid, registrations] of Object.entries(devices)) {
    if (members[uid]?.active !== true) continue;
    for (const [deviceId, registration] of Object.entries(registrations)) {
      if (typeof registration.token !== "string" || !Number.isFinite(registration.updatedAt) ||
          registration.updatedAt < cutoff) continue;
      const previous = targets.get(registration.token);
      if (previous && previous.updatedAt >= registration.updatedAt) continue;
      targets.set(registration.token, { uid, deviceId, token: registration.token, updatedAt: registration.updatedAt });
    }
  }
  return [...targets.values()];
}

async function sendAnnouncement(root, messaging, community, recordId) {
  const reference = root.child("announcements/" + recordId);
  const announcement = (await reference.get()).val();
  if (!announcement || announcement.status !== "published" || announcement.notificationDispatchedAt) {
    return "El anuncio debe estar publicado y tener notificación pendiente.";
  }
  const [members, devices] = await Promise.all([root.child("members").get(), root.child("devices").get()]);
  const targets = notificationTargets(members.val() || {}, devices.val() || {}, Date.now() - DEVICE_MAX_AGE_MS);
  if (targets.length === 0) return "No hay dispositivos recientes de miembros activos.";
  let accepted = 0;
  let retryable = false;
  for (let offset = 0; offset < targets.length; offset += BATCH_SIZE) {
    const batch = targets.slice(offset, offset + BATCH_SIZE);
    const result = await messaging.sendEach(batch.map(target => ({
      token: target.token, data: { uid: target.uid, community, id: recordId },
      android: { priority: announcement.priority === "urgent" ? "high" : "normal", ttl: 86400000 }
    })));
    accepted += result.successCount;
    await Promise.all(result.responses.map((delivery, index) => {
      if (delivery.success) return undefined;
      if (delivery.error?.code === "messaging/registration-token-not-registered") {
        const target = batch[index];
        return root.child("devices/" + target.uid + "/" + target.deviceId).transaction(current =>
          current?.token === target.token && current.updatedAt === target.updatedAt ? null : undefined);
      }
      retryable = true;
      return undefined;
    }));
  }
  if (retryable) throw new Error("FCM rechazó parte de los destinos; revisa el servicio antes de reintentar el anuncio.");
  if (!accepted) return "FCM no aceptó ningún dispositivo vigente.";
  await reference.transaction(current => current?.status === "published" && !current.notificationDispatchedAt
    ? { ...current, notificationDispatchedAt: ServerValue.TIMESTAMP } : undefined);
  return "FCM aceptó " + accepted + " solicitudes de envío.";
}

async function main() {
  const { operation, community, recordId, projectId, databaseURL } = configuration(process.argv.slice(2), process.env);
  const app = initializeApp({ projectId, databaseURL, credential: applicationDefault() });
  try {
    const root = getDatabase(app).ref("communityData/" + community);
    const result = operation === "mqtt"
      ? await publishAlert(root, community, recordId, process.env)
      : await sendAnnouncement(root, getMessaging(app), community, recordId);
    console.log(result);
  } finally {
    getDatabase(app).goOffline();
    await deleteApp(app);
  }
}

if (require.main === module) {
  main().catch(error => {
    console.error("Entrega incompleta.", error.code || (error.constructor === Error ? error.message : error.name));
    process.exitCode = 1;
  });
}

module.exports = { configuration, mqttConfiguration, notificationTargets };
