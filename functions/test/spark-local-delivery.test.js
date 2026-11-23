const assert = require("node:assert/strict");
const { test } = require("node:test");
const { announcementsSince } = require("../spark-local-delivery");
const { mqttConfiguration, pendingAlert, pendingAnnouncement, announcementMessage } = require("../spark-delivery");

test("valida la comunidad, el estado SOS y el puerto MQTT", () => {
  const alert = { community: "comuna-centro", status: "active", delivery: "pending" };
  assert.equal(pendingAlert(alert, "comuna-centro"), true);
  assert.equal(pendingAlert(alert, "otra-comuna"), false);
  assert.equal(pendingAlert({ ...alert, status: "attended" }, "comuna-centro"), false);
  assert.equal(pendingAlert({ ...alert, delivery: "published" }, "comuna-centro"), false);
  assert.throws(() => mqttConfiguration({
    CC_MQTT_HOST: "localhost:65536", CC_MQTT_PUBLISHER_USERNAME: "fixture", CC_MQTT_PUBLISHER_PASSWORD: "fixture"
  }));
});

test("activa FCM solo desde una fecha fija y filtra anuncios ya procesados", () => {
  assert.equal(announcementsSince({}), undefined);
  assert.throws(() => announcementsSince({ CC_ANNOUNCEMENTS_SINCE: "2026-02-30T00:00:00Z" }));
  const since = announcementsSince({ CC_ANNOUNCEMENTS_SINCE: "2026-11-23T00:00:00Z" });
  assert.equal(pendingAnnouncement({ status: "published", createdAt: since }, since), true);
  assert.equal(pendingAnnouncement({ status: "published", createdAt: since - 1 }, since), false);
  assert.equal(pendingAnnouncement({ status: "published", createdAt: since, notificationDispatchedAt: since }, since), false);
});

test("construye mensajes data-only por comunidad con prioridad y resumen", () => {
  const recipient = { uid: "neighbor", token: "fake-token" };
  const notice = { title: "Asamblea", description: "📣".repeat(161) };
  const normal = announcementMessage("comuna-centro", "announcement", { ...notice, priority: "info" }, recipient);
  assert.deepEqual(normal.data, { uid: "neighbor", community: "comuna-centro", id: "announcement",
    title: "Asamblea", summary: "📣".repeat(160) });
  assert.equal(normal.notification, undefined);
  assert.equal(normal.android.priority, "normal");
  for (const priority of ["important", "urgent"]) {
    const message = announcementMessage("comuna-centro", "announcement", { ...notice, priority }, recipient);
    assert.equal(message.android.priority, "high");
  }
  assert.equal(announcementMessage("comuna-centro", "legacy", {}, recipient).android.priority, "normal");
});
