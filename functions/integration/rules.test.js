const assert = require("node:assert/strict");
const { after, before, test } = require("node:test");
const { readFileSync } = require("node:fs");
const path = require("node:path");
const { initializeTestEnvironment, assertSucceeds, assertFails } = require("@firebase/rules-unit-testing");
const { get, ref, set, query, orderByChild, orderByKey, limitToFirst, limitToLast, equalTo, startAt, endAt, serverTimestamp } = require("firebase/database");

const projectId = process.env.GCLOUD_PROJECT || "demo-comunidad";
let environment;

before(async () => {
  environment = await initializeTestEnvironment({
    projectId,
    database: { rules: readFileSync(path.join(__dirname, "../../database.rules.json"), "utf8") }
  });
  await environment.withSecurityRulesDisabled(async context => {
    await set(ref(context.database()), {
      accounts: {
        rulesNeighbor: { community: "rulesNorth", role: "neighbor" }, rulesAdmin: { community: "rulesNorth", role: "admin" },
        blocked: { community: "rulesNorth", role: "neighbor" }, rulesOther: { community: "rulesSouth", role: "neighbor" }
      },
      communityData: {
        rulesNorth: {
          metadata: { displayName: "Comunidad Norte", internal: "fixture privado" },
          members: {
            rulesNeighbor: { role: "neighbor", active: true }, rulesAdmin: { role: "admin", active: true, name: "Admin fixture", unit: "101" },
            blocked: { role: "neighbor", active: false }
          },
          messages: { message: { createdAt: 10, owner: "rulesNeighbor" } },
          announcements: { announcement: { createdAt: 10, title: "Aviso" } },
          reports: { own: { owner: "rulesNeighbor" }, private: { owner: "rulesAdmin" } },
          pollLedger: { poll: { votes: { rulesNeighbor: 0, rulesAdmin: 1 } } }
        },
        rulesSouth: { metadata: { displayName: "Comunidad Sur" }, members: { rulesOther: { role: "neighbor", active: true } }, messages: { message: { createdAt: 10 } } }
      }
    });
  });
});

after(async () => { if (environment) await environment.cleanup(); });

function database(uid, claims = {}) {
  return environment.authenticatedContext(uid, { email_verified: true, ...claims }).database();
}
function messages(database, community = "rulesNorth") {
  return query(ref(database, `communityData/${community}/messages`), orderByChild("createdAt"), limitToLast(50));
}

test("aísla comunidades y permite únicamente consultas acotadas", async () => {
  const neighbor = database("rulesNeighbor");
  await assertSucceeds(get(messages(neighbor)));
  await assertFails(get(messages(neighbor, "rulesSouth")));
  await assertFails(get(messages(database("blocked"))));
  await assertFails(get(messages(environment.unauthenticatedContext().database())));
  await assertFails(get(messages(database("rulesNeighbor", { email_verified: false }))));
  await assertFails(get(ref(neighbor, "communityData/rulesNorth/messages")));
  await assertFails(get(query(ref(neighbor, "communityData/rulesNorth/messages"), orderByChild("createdAt"))));
});

test("impide escalamiento, publicación directa y lecturas administrativas", async () => {
  const neighbor = database("rulesNeighbor");
  const admin = database("rulesAdmin", { role: "admin", community: "rulesNorth" });
  const forged = database("rulesNeighbor", { role: "admin", community: "rulesNorth" });
  const members = connection => query(ref(connection, "communityData/rulesNorth/members"), orderByKey(), limitToFirst(100));
  await assertFails(get(members(neighbor)));
  await assertFails(get(members(forged)));
  await assertSucceeds(get(members(admin)));
  await assertFails(set(ref(neighbor, "communityData/rulesNorth/members/rulesNeighbor/role"), "admin"));
  await assertFails(set(ref(neighbor, "accounts/rulesAdmin"), { community: "rulesSouth" }));
  await assertFails(set(ref(admin, "communityData/rulesNorth/announcements/new"), { title: "Escritura directa" }));
});

test("limita los metadatos de comunidad a sus miembros activos y a escrituras administrativas externas", async () => {
  const neighbor = database("rulesNeighbor");
  const admin = database("rulesAdmin", { role: "admin", community: "rulesNorth" });
  const north = "communityData/rulesNorth/metadata/displayName";
  const south = "communityData/rulesSouth/metadata/displayName";
  assert.equal((await assertSucceeds(get(ref(neighbor, north)))).val(), "Comunidad Norte");
  assert.equal((await assertSucceeds(get(ref(database("rulesOther"), south)))).val(), "Comunidad Sur");
  await assertFails(get(ref(neighbor, south)));
  await assertFails(get(ref(neighbor, "communityData/rulesNorth/metadata")));
  await assertFails(get(ref(neighbor, "communityData/rulesNorth/metadata/internal")));
  await assertFails(get(ref(database("blocked"), north)));
  await assertFails(get(ref(database("rulesNeighbor", { email_verified: false }), north)));
  await assertFails(get(ref(environment.unauthenticatedContext().database(), north)));
  await assertFails(set(ref(neighbor, north), "Cambio del vecino"));
  await assertFails(set(ref(admin, north), "Cambio del administrador móvil"));
});

test("protege reportes privados y boletas individuales", async () => {
  const neighbor = database("rulesNeighbor");
  const base = "communityData/rulesNorth";
  const reports = query(ref(neighbor, `${base}/reports`), orderByChild("owner"), equalTo("rulesNeighbor"), limitToLast(50));
  assert.deepEqual(Object.keys((await assertSucceeds(get(reports))).val()), ["own"]);
  const history = query(ref(neighbor, `${base}/reports`), orderByChild("owner"), startAt("rulesNeighbor"),
    endAt("rulesNeighbor", "zzzz"), limitToLast(50));
  assert.deepEqual(Object.keys((await assertSucceeds(get(history))).val()), ["own"]);
  const wrongRange = query(ref(neighbor, `${base}/reports`), orderByChild("owner"), startAt("rulesAdmin"),
    endAt("rulesNeighbor", "zzzz"), limitToLast(50));
  await assertFails(get(wrongRange));
  await assertFails(get(ref(neighbor, `${base}/reports/private`)));
  await assertFails(get(ref(neighbor, `${base}/pollLedger/poll/votes`)));
  await assertFails(get(ref(neighbor, `${base}/pollLedger/poll/votes/rulesAdmin`)));
  assert.equal((await assertSucceeds(get(ref(neighbor, `${base}/pollLedger/poll/votes/rulesNeighbor`)))).val(), 0);
  await assertFails(set(ref(neighbor, `${base}/pollLedger/poll/votes/rulesNeighbor`), 1));
});


test("publica avisos normales e importantes y autoriza la apertura solo dentro de su comunidad", async () => {
  const admin = database("rulesAdmin", { role: "admin", community: "rulesNorth" });
  const neighbor = database("rulesNeighbor");
  const outsider = database("rulesOther");
  const base = "communityData/rulesNorth/announcements";
  for (const priority of ["info", "important", "urgent"]) {
    const destination = base + "/rulesAdmin_" + priority;
    await assertSucceeds(set(ref(admin, destination), { owner: "rulesAdmin", author: "Admin fixture",
      unit: "101", createdAt: serverTimestamp(), title: "Aviso de prueba", description: "Contenido",
      priority, status: "published" }));
    assert.equal((await assertSucceeds(get(ref(neighbor, destination)))).child("priority").val(), priority);
    await assertFails(get(ref(outsider, destination)));
  }
  assert.equal((await assertSucceeds(get(ref(neighbor, base + "/announcement")))).child("title").val(), "Aviso");
});
