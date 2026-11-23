const { initializeApp, applicationDefault, deleteApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const { getMessaging } = require("firebase-admin/messaging");
const { RECORD_ID, errorCode } = require("./spark-delivery-state");
const {
  firebaseConfiguration, mqttConfiguration, probeBroker,
  pendingAlert, pendingAnnouncement, publishAlert, sendAnnouncement
} = require("./spark-delivery");

const COMMUNITY = "comuna-centro";
const INITIAL_RETRY_MS = 2000;
const MAX_RETRY_MS = 60000;
const SHUTDOWN_TIMEOUT_MS = 20000;
const UPSERT_EVENTS = ["child_added", "child_changed"];

function log(event, details = {}) {
  console.log(JSON.stringify({ timestamp: new Date().toISOString(), event, ...details }));
}

function retryDelay(attempt) {
  const delay = Math.min(MAX_RETRY_MS, INITIAL_RETRY_MS * 2 ** (attempt - 1));
  return Math.round(delay * (0.75 + Math.random() * 0.25));
}

function watchDeliveries({ query, channel, eligible, deliver, report = log, backoff = retryDelay }) {
  const jobs = new Map();
  let stopped = false;
  let running;
  let timer;
  let listenerTimer;
  let listenAttempts = 0;

  function schedule() {
    clearTimeout(timer);
    if (stopped || running || !jobs.size) return;
    const now = Date.now();
    let nextDue = Infinity;
    for (const [recordId, job] of jobs) {
      if (job.due <= now) {
        running = run(recordId, job).finally(() => {
          running = undefined;
          schedule();
        });
        return;
      }
      nextDue = Math.min(job.due, nextDue);
    }
    timer = setTimeout(schedule, Math.max(0, nextDue - Date.now()));
  }

  async function run(recordId, job) {
    try {
      const result = await deliver(recordId);
      if (result) report("delivery-confirmed", { channel, recordId, result });
      if (jobs.get(recordId) === job) jobs.delete(recordId);
    } catch (error) {
      const waitMs = backoff(++job.attempts);
      job.due = Date.now() + waitMs;
      report("delivery-retry", { channel, recordId, attempt: job.attempts, waitMs, code: errorCode(error) });
    }
  }

  function observe(snapshot) {
    listenAttempts = 0;
    const recordId = snapshot.key;
    if (!RECORD_ID.test(recordId)) return;
    if (!eligible(snapshot.val())) jobs.delete(recordId);
    else if (!jobs.has(recordId)) jobs.set(recordId, { due: 0, attempts: 0 });
    schedule();
  }

  function remove(snapshot) {
    jobs.delete(snapshot.key);
    schedule();
  }

  function detach() {
    for (const event of UPSERT_EVENTS) query.off(event, observe);
    query.off("child_removed", remove);
  }

  function listenFailed(error) {
    if (stopped || listenerTimer) return;
    const waitMs = backoff(++listenAttempts);
    report("firebase-listen-retry", { channel, waitMs, code: errorCode(error) });
    listenerTimer = setTimeout(() => {
      listenerTimer = undefined;
      detach();
      attach();
    }, waitMs);
  }

  function attach() {
    for (const event of UPSERT_EVENTS) query.on(event, observe, listenFailed);
    query.on("child_removed", remove, listenFailed);
  }

  attach();
  return async () => {
    stopped = true;
    clearTimeout(timer);
    clearTimeout(listenerTimer);
    detach();
    jobs.clear();
    await running;
  };
}

function announcementsSince(environment) {
  const configured = environment.CC_ANNOUNCEMENTS_SINCE?.trim();
  if (!configured) return undefined;
  const timestamp = Date.parse(configured);
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/.test(configured) ||
      !Number.isFinite(timestamp) || timestamp <= 0 ||
      new Date(timestamp).toISOString() !== configured.replace(/(?<=:\d{2})Z$/, ".000Z")) {
    throw new Error("CC_ANNOUNCEMENTS_SINCE debe ser una fecha ISO 8601 UTC.");
  }
  return timestamp;
}

function supervise(root, broker, { messaging, since, report = log, backoff } = {}) {
  const stop = [watchDeliveries({
    query: root.child("alerts").orderByChild("createdAt"), channel: "mqtt",
    eligible: alert => pendingAlert(alert, root.key), deliver: recordId => publishAlert(root, recordId, broker),
    report, backoff
  })];
  if (messaging) stop.push(watchDeliveries({
    query: root.child("announcements").orderByChild("createdAt").startAt(since), channel: "fcm",
    eligible: announcement => pendingAnnouncement(announcement, since),
    deliver: recordId => sendAnnouncement(root, messaging, recordId, { since, log: report }),
    report, backoff
  }));
  return () => Promise.all(stop.map(stopWatching => stopWatching()));
}

async function main() {
  const firebase = firebaseConfiguration(process.env);
  const broker = mqttConfiguration(process.env);
  const since = announcementsSince(process.env);
  const app = initializeApp({ ...firebase, credential: applicationDefault() });
  const database = getDatabase(app);
  const brokerCheck = probeBroker(broker)
    .then(() => log("mqtt-ready"))
    .catch(error => log("mqtt-unavailable", { code: errorCode(error) }));
  const connection = database.ref(".info/connected");
  const observeConnection = snapshot => log(snapshot.val() ? "firebase-connected" : "firebase-disconnected");
  connection.on("value", observeConnection);
  const root = database.ref("communityData").child(COMMUNITY);
  const stop = supervise(root, broker, { since, messaging: since === undefined ? undefined : getMessaging(app) });
  log("supervisor-started", { community: COMMUNITY, announcementsEnabled: since !== undefined });

  async function shutdown() {
    process.removeListener("SIGINT", shutdown);
    process.removeListener("SIGTERM", shutdown);
    const deadline = setTimeout(() => process.exit(1), SHUTDOWN_TIMEOUT_MS);
    try {
      await Promise.all([stop(), brokerCheck]);
      connection.off("value", observeConnection);
      database.goOffline();
      await deleteApp(app);
      log("supervisor-stopped");
    } catch (error) {
      log("shutdown-error", { code: errorCode(error) });
      process.exitCode = 1;
    } finally {
      clearTimeout(deadline);
    }
  }
  process.once("SIGINT", shutdown);
  process.once("SIGTERM", shutdown);
}

if (require.main === module) {
  main().catch(error => {
    log("startup-error", { reason: error.code ? errorCode(error) : error.message });
    process.exit(1);
  });
}

module.exports = { supervise, announcementsSince };
