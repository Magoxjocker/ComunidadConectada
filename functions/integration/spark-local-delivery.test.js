const assert = require("node:assert/strict");
const { spawn } = require("node:child_process");
const { once } = require("node:events");
const { setTimeout: pause } = require("node:timers/promises");
const { test } = require("node:test");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const { connectAsync } = require("mqtt");
const { mqttConfiguration, probeBroker, publishAlert } = require("../spark-delivery");
const { supervise } = require("../spark-local-delivery");

const COMMUNITY = "comuna-centro";
const TOPIC = "comunidades/" + COMMUNITY + "/alertas";
const PROJECT = "demo-comunidad-delivery";
const WAIT_MS = 15000;

async function until(check, description) {
  const deadline = Date.now() + WAIT_MS;
  while (Date.now() < deadline) {
    if (await check()) return;
    await pause(50);
  }
  assert.fail(description);
}

function fakeMessaging() {
  const attempts = [];
  let retryAvailable = false;
  return {
    attempts,
    recover() { retryAvailable = true; },
    async sendEach(messages) {
      attempts.push(messages.map(message => message.token));
      for (const message of messages) {
        assert.equal(message.data.community, COMMUNITY);
        assert.equal(message.data.id, "new-announcement");
        assert.equal(message.android.priority, "high");
        assert.equal(message.data.title, "Aviso importante");
        assert.equal(message.data.summary, "Resumen comprobado");
        assert.equal(message.notification, undefined);
      }
      return { responses: messages.map(({ token }) => {
        if (token === "fictitious-invalid") {
          return { success: false, error: { code: "messaging/registration-token-not-registered" } };
        }
        if (token === "fictitious-retry" && !retryAvailable) {
          return { success: false, error: { code: "messaging/server-unavailable" } };
        }
        return { success: true };
      }) };
    }
  };
}

test("entrega automática con RTDB emulada, Mosquitto TLS y un transporte FCM ficticio", { timeout: 90000 }, async context => {
  assert.match(process.env.FIREBASE_DATABASE_EMULATOR_HOST ?? "", /^127\.0\.0\.1:\d+$/);
  assert.match(process.env.CC_MQTT_HOST ?? "", /^127\.0\.0\.1:\d+$/);
  assert.ok(process.env.CC_TEST_MOSQUITTO_EXE && process.env.CC_TEST_MOSQUITTO_CONFIG);
  assert.ok(process.env.CC_TEST_MQTT_SUBSCRIBER_USERNAME && process.env.CC_TEST_MQTT_SUBSCRIBER_PASSWORD);
  const broker = mqttConfiguration(process.env);
  const app = initializeApp({ projectId: PROJECT, databaseURL: "https://" + PROJECT + "-default-rtdb.firebaseio.com" });
  const concurrentApp = initializeApp(app.options, "concurrent");
  const database = getDatabase(app);
  const root = database.ref("communityData").child(COMMUNITY);
  const isolatedRoot = database.ref("communityData").child("fixture-other");
  const ledger = database.ref("sparkDelivery").child(COMMUNITY);
  const messages = [];
  const events = [];
  const cutoff = Date.now();
  const pending = { createdAt: cutoff, community: COMMUNITY, status: "active", delivery: "pending" };
  const messaging = fakeMessaging();
  const subscribers = [];
  const supervisors = [];
  let mosquitto;

  async function stopBroker() {
    if (!mosquitto || mosquitto.exitCode !== null || mosquitto.signalCode !== null) return;
    const exited = once(mosquitto, "exit");
    mosquitto.kill();
    await exited;
  }

  context.after(async () => {
    await Promise.all(supervisors.map(stop => stop()));
    await Promise.all(subscribers.map(subscriber => subscriber.endAsync(true)));
    await stopBroker();
    await Promise.all([root.remove(), isolatedRoot.remove(), ledger.remove(),
      database.ref("sparkDelivery/fixture-other").remove()]);
    database.goOffline();
    getDatabase(concurrentApp).goOffline();
    await Promise.all([deleteApp(app), deleteApp(concurrentApp)]);
  });

  async function startBroker() {
    mosquitto = spawn(process.env.CC_TEST_MOSQUITTO_EXE, ["-c", process.env.CC_TEST_MOSQUITTO_CONFIG],
      { windowsHide: true, stdio: "ignore" });
    await once(mosquitto, "spawn");
    await until(() => probeBroker(broker).then(() => true, () => false), "Mosquitto TLS no inició");
  }

  async function subscribe() {
    const subscriber = await connectAsync(broker.endpoint, {
      username: process.env.CC_TEST_MQTT_SUBSCRIBER_USERNAME,
      password: process.env.CC_TEST_MQTT_SUBSCRIBER_PASSWORD, protocolVersion: 5,
      reconnectPeriod: 0, connectTimeout: 2000, rejectUnauthorized: true
    });
    subscribers.push(subscriber);
    subscriber.on("message", (topic, payload, packet) => {
      messages.push({ topic, payload: JSON.parse(payload.toString()), qos: packet.qos, retain: packet.retain });
    });
    await subscriber.subscribeAsync(TOPIC, { qos: 1 });
  }

  function startSupervisor(options = {}, watchedRoot = root) {
    const stop = supervise(watchedRoot, broker, {
      backoff: () => 1000, report: (event, detail) => events.push({ event, ...detail }), ...options
    });
    supervisors.push(stop);
    return stop;
  }

  async function checkPublished(recordId) {
    const record = root.child("alerts").child(recordId);
    await until(async () => (await record.get()).val()?.delivery === "published", recordId + " quedó pendiente");
    assert.ok((await record.get()).val().brokerAcknowledgedAt >= cutoff);
  }

  await startBroker();
  await subscribe();
  await root.child("alerts").set({
    backlog: pending, mismatch: { ...pending, community: "fixture-other" },
    inactive: { ...pending, status: "closed" }, alreadyPublished: { ...pending, delivery: "published" },
    confirmed: pending
  });
  await ledger.child("alerts/confirmed").set({ generation: cutoff, brokerAcknowledgedAt: cutoff });
  await root.child("announcements/old-announcement").set({ createdAt: cutoff - 1, status: "published" });
  const stopPrimary = startSupervisor({ since: cutoff, messaging });
  const concurrentRoot = getDatabase(concurrentApp).ref("communityData").child(COMMUNITY);
  const stopConcurrent = startSupervisor({}, concurrentRoot);
  await checkPublished("backlog");
  await checkPublished("confirmed");
  await root.child("alerts/fresh").set(pending);
  await checkPublished("fresh");
  await stopConcurrent();

  await stopPrimary();
  await root.child("alerts/wrong-password").set(pending);
  await assert.rejects(publishAlert(root, "wrong-password", { ...broker, password: broker.password + "-wrong" }));
  assert.equal((await root.child("alerts/wrong-password/delivery").get()).val(), "pending");
  await root.child("alerts/wrong-password").remove();
  await isolatedRoot.child("alerts/denied").set({ ...pending, community: isolatedRoot.key });
  await assert.rejects(publishAlert(isolatedRoot, "denied", broker));
  assert.equal((await isolatedRoot.child("alerts/denied/delivery").get()).val(), "pending");

  const stopBeforeRestart = startSupervisor({ since: cutoff, messaging });
  await stopBroker();
  await root.child("alerts/during-outage").set(pending);
  await until(() => events.some(entry => entry.event === "delivery-retry" &&
    entry.recordId === "during-outage"), "El supervisor no registró la caída del broker");
  assert.equal((await root.child("alerts/during-outage/delivery").get()).val(), "pending");
  await startBroker();
  await subscribe();
  await checkPublished("during-outage");

  await root.child("members").set({ neighbor: { active: true }, inactive: { active: false } });
  await root.child("devices").set({
    neighbor: {
      accepted: { token: "fictitious-accepted", updatedAt: cutoff },
      duplicate: { token: "fictitious-accepted", updatedAt: cutoff - 1 },
      retry: { token: "fictitious-retry", updatedAt: cutoff },
      invalid: { token: "fictitious-invalid", updatedAt: cutoff }
    },
    inactive: { ignored: { token: "fictitious-inactive", updatedAt: cutoff } }
  });
  const announcement = root.child("announcements/new-announcement");
  await announcement.set({ createdAt: cutoff, status: "published", priority: "important",
    title: "Aviso importante", description: "Resumen comprobado" });
  await until(() => events.some(entry => entry.event === "fcm-batch" && entry.accepted === 1 && entry.retry === 1),
    "FCM ficticio no devolvió el resultado parcial");
  assert.equal((await announcement.child("notificationDispatchedAt").get()).val(), null);
  await stopBeforeRestart();
  messaging.recover();
  startSupervisor({ since: cutoff, messaging });
  await until(async () => (await announcement.child("notificationDispatchedAt").get()).exists(),
    "El anuncio no se completó después de reiniciar");
  assert.equal(messaging.attempts.flat().filter(token => token === "fictitious-accepted").length, 1);
  assert.equal(messaging.attempts.flat().filter(token => token === "fictitious-invalid").length, 1);
  assert.ok(messaging.attempts.flat().filter(token => token === "fictitious-retry").length >= 2);
  assert.equal((await root.child("devices/neighbor/invalid").get()).exists(), false);
  assert.deepEqual(messages.map(message => message.payload.id).sort(), ["backlog", "during-outage", "fresh"]);
  for (const message of messages) {
    assert.deepEqual(message, { topic: TOPIC, payload: { id: message.payload.id }, qos: 1, retain: false });
  }
});
