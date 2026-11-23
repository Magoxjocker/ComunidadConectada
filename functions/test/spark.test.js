const assert = require("node:assert/strict");
const { test } = require("node:test");
const { planPollMigration } = require("../plan-spark-poll-migration");
const { configuration, mqttConfiguration, notificationTargets } = require("../spark-delivery");

test("reconstruye totales a partir de boletas sin exponerlas en la actualización", () => {
  const poll = { title: "Asamblea", options: ["Lunes", "Martes"], closesAt: 1, showResults: false, closed: true, revision: 3 };
  const snapshot = { polls: { olderPoll: poll, sparkPoll: { sparkVersion: 1 } },
    pollLedger: { olderPoll: { ...poll, votes: { fixtureNeighbor: 0, anotherNeighbor: 0 } } } };
  assert.deepEqual(planPollMigration("norte", snapshot), {
    "communityData/norte/pollTotals/olderPoll": { 0: 2, 1: 0 },
    "communityData/norte/polls/olderPoll/sparkVersion": 1
  });
  assert.throws(() => planPollMigration("norte", { ...snapshot, pollLedger: {} }), /concilia/);
  snapshot.pollLedger.olderPoll.votes.fixtureNeighbor = 2;
  assert.throws(() => planPollMigration("norte", snapshot), /voto inválido/);
});

test("selecciona registros recientes de miembros activos y un destino por token", () => {
  const members = { neighbor: { active: true }, anotherNeighbor: { active: true }, suspended: { active: false } };
  const devices = { neighbor: { old: { token: "old", updatedAt: 1 }, duplicate: { token: "shared", updatedAt: 10 } },
    anotherNeighbor: { current: { token: "shared", updatedAt: 20 } },
    suspended: { ignored: { token: "ignored", updatedAt: 20 } } };
  assert.deepEqual(notificationTargets(members, devices, 5),
    [{ uid: "anotherNeighbor", deviceId: "current", token: "shared", updatedAt: 20 }]);
});

test("exige configuración explícita del proyecto y un endpoint MQTT TLS válido", () => {
  const argumentsList = ["mqtt", "norte", "fixture-alert"];
  const environment = { CC_PROJECT_ID: "comunidadconectada-35c3c",
    CC_DATABASE_URL: "https://comunidadconectada-35c3c-default-rtdb.firebaseio.com/" };
  assert.equal(configuration(argumentsList, environment).community, "norte");
  assert.throws(() => configuration(argumentsList, { ...environment, FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099" }));
  assert.throws(() => configuration(["mqtt", "../norte", "fixture-alert"], environment));
  assert.throws(() => mqttConfiguration({ CC_MQTT_HOST: "https://example.test/path" }));
});
