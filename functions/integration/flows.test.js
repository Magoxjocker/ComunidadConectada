const assert = require("node:assert/strict");
const { randomUUID } = require("node:crypto");
const { planPollMigration } = require("../plan-spark-poll-migration");
const { after, before, test } = require("node:test");
const { initializeApp, deleteApp } = require("firebase/app");
const { connectAuthEmulator, createUserWithEmailAndPassword, getAuth, signOut,
  signInWithEmailAndPassword, sendEmailVerification, applyActionCode } = require("firebase/auth");
const { connectDatabaseEmulator, getDatabase, get, goOffline, increment, ref, serverTimestamp, set, update } = require("firebase/database");
const { initializeApp: initializeAdmin, deleteApp: deleteAdmin } = require("firebase-admin/app");
const { getAuth: adminAuth } = require("firebase-admin/auth");
const { getDatabase: adminDatabase } = require("firebase-admin/database");

const projectId = process.env.GCLOUD_PROJECT;
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const databaseHost = process.env.FIREBASE_DATABASE_EMULATOR_HOST;
if (!projectId?.startsWith("demo-") ||
    !/^127\.0\.0\.1:\d+$/.test(authHost || "") || !/^127\.0\.0\.1:\d+$/.test(databaseHost || "")) {
  throw new Error("Ejecuta la suite con los emuladores locales Auth y Database de un proyecto demo.");
}

const databaseURL = "https://" + projectId + "-default-rtdb.firebaseio.com";
const apps = [];
const community = "sparkNorth";
const otherCommunity = "sparkSouth";
const base = "communityData/" + community;
const password = "FixtureSpark_" + randomUUID();
let server;
let administrator;
let neighbor;
let anotherNeighbor;
let outsider;
let verifiedPending;

function client() {
  const app = initializeApp({ projectId, databaseURL, apiKey: "demo-firebase-api-key" }, randomUUID());
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://" + authHost, { disableWarnings: true });
  const database = getDatabase(app);
  connectDatabaseEmulator(database, "127.0.0.1", Number(databaseHost.split(":")[1]));
  apps.push({ app, database });
  return { auth, database };
}

function profile(name, assignedCommunity = "", role = "neighbor") {
  return { name, unit: "A-10", community: assignedCommunity, role };
}
function memberProfile(account, email, active = true) {
  return { name: account.name, unit: account.unit, role: account.role, email, active };
}
function dbRef(actor, path) {
  return ref(actor.database, path);
}
const denied = async operation => assert.rejects(operation, /permission.denied/i);
async function provisionFixture(name, assignedCommunity, role = "neighbor") {
  const email = randomUUID() + "@example.test";
  const account = profile(name, assignedCommunity, role);
  const user = await adminAuth(server).createUser({ email, password, emailVerified: true });
  const prefix = "communityData/" + assignedCommunity;
  if (role === "admin") await adminAuth(server).setCustomUserClaims(user.uid, { role, community: assignedCommunity });
  await adminDatabase(server).ref().update({
    ["accounts/" + user.uid]: account,
    ...(assignedCommunity ? { [prefix + "/members/" + user.uid]: memberProfile(account, email) } : {})
  });
  const actor = client();
  await signInWithEmailAndPassword(actor.auth, email, password);
  return { ...actor, email, account, uid: user.uid };
}
function attribution(actor) {
  return { owner: actor.uid, author: actor.account.name, unit: actor.account.unit, createdAt: serverTimestamp() };
}
function newRecord(actor, collection) {
  return base + "/" + collection + "/" + actor.uid + "_" + randomUUID();
}
function approve(actor, communityCode = community) {
  return {
    ["accounts/" + actor.uid + "/community"]: communityCode,
    ["communityData/" + communityCode + "/members/" + actor.uid]: memberProfile(actor.account, actor.email)
  };
}
function request(actor, target = community) {
  return set(dbRef(actor, "joinRequests/" + actor.uid), {
    name: actor.account.name, unit: actor.account.unit, email: actor.email, community: target
  });
}

before(async () => {
  server = initializeAdmin({ projectId, databaseURL });
  administrator = await provisionFixture("Administración Norte", community, "admin");
  neighbor = await provisionFixture("Vecina Norte", community);
  anotherNeighbor = await provisionFixture("Segundo vecino", community);
  outsider = await provisionFixture("Administración Sur", otherCommunity, "admin");
  verifiedPending = await provisionFixture("Vecina pendiente", "");
});

after(async () => {
  for (const actor of apps) goOffline(actor.database);
  await Promise.all(apps.map(actor => deleteApp(actor.app)));
  if (server) {
    adminDatabase(server).goOffline();
    await deleteAdmin(server);
  }
});

test("registra un perfil mínimo, verifica el correo y vuelve a iniciar sesión", async () => {
  const actor = client();
  const email = randomUUID() + "@example.test";
  const { user } = await createUserWithEmailAndPassword(actor.auth, email, password);
  const path = "accounts/" + user.uid;
  const account = profile("Registro Spark");
  await denied(set(dbRef(actor, path), { ...account, role: "admin" }));
  await denied(set(dbRef(actor, path), { ...account, community }));
  await denied(set(dbRef(actor, path), { ...account, active: true }));
  await set(dbRef(actor, path), account);
  await denied(get(dbRef(actor, path)));
  await denied(request({ ...actor, uid: user.uid, account, email }));
  await sendEmailVerification(user);
  const response = await fetch("http://" + authHost + "/emulator/v1/projects/" + projectId + "/oobCodes");
  assert.equal(response.status, 200);
  const { oobCodes } = await response.json();
  const verification = oobCodes.find(code => code.email === email && code.requestType === "VERIFY_EMAIL");
  assert.ok(verification);
  await applyActionCode(actor.auth, verification.oobCode);
  await signOut(actor.auth);
  const signedIn = await signInWithEmailAndPassword(actor.auth, email, password);
  assert.equal(signedIn.user.emailVerified, true);
  assert.deepEqual((await get(dbRef(actor, path))).val(), account);
  await signOut(actor.auth);
  await denied(get(dbRef(actor, path)));
});

test("exige consentimiento y aprobación atómica con administrador de la comunidad", async () => {
  const subject = verifiedPending;
  const changes = approve(subject);
  const accountPath = "accounts/" + subject.uid + "/community";
  const memberPath = base + "/members/" + subject.uid;
  await denied(update(dbRef(administrator), changes));
  await denied(request(subject, "missingCommunity"));
  await request(subject, otherCommunity);
  await denied(update(dbRef(administrator), changes));
  await request(subject);
  await denied(get(dbRef(outsider, "joinRequests/" + subject.uid)));
  assert.equal((await get(dbRef(administrator, "joinRequests/" + subject.uid))).val().email, subject.email);
  await denied(update(dbRef(neighbor), changes));
  await denied(update(dbRef(outsider), approve(subject, otherCommunity)));
  await denied(set(dbRef(administrator, accountPath), community));
  await denied(set(dbRef(administrator, memberPath), changes[memberPath]));
  await denied(update(dbRef(administrator), { ...changes, [memberPath]: { ...changes[memberPath], role: "admin" } }));
  await update(dbRef(administrator), changes);
  assert.equal((await get(dbRef(subject, accountPath))).val(), community);
  assert.equal((await get(dbRef(subject, memberPath + "/active"))).val(), true);
  await denied(set(dbRef(subject, memberPath + "/role"), "admin"));
  await denied(set(dbRef(subject, "accounts/" + subject.uid + "/role"), "admin"));
  await denied(set(dbRef(administrator, base + "/members/" + administrator.uid + "/active"), false));
  await denied(set(dbRef(subject, memberPath + "/active"), false));
  await set(dbRef(administrator, memberPath + "/active"), false);
  await denied(set(dbRef(subject, newRecord(subject, "messages")),
    { ...attribution(subject), text: "Suspendido", community }));
  await set(dbRef(administrator, memberPath + "/active"), true);
  await denied(set(dbRef(subject, "accounts/" + subject.uid + "/name"), "Nombre inconsistente"));
  await update(dbRef(subject), {
    ["accounts/" + subject.uid + "/name"]: "Nombre actualizado",
    [memberPath + "/name"]: "Nombre actualizado"
  });
  assert.equal((await get(dbRef(subject, memberPath + "/name"))).val(), "Nombre actualizado");
});

test("aplica separación y permisos en chat, ventas y anuncios", async () => {
  const messagePath = newRecord(neighbor, "messages");
  const message = { ...attribution(neighbor), text: "Mensaje de la comunidad", community };
  await denied(set(dbRef(neighbor, messagePath), { ...message, author: "Otra persona" }));
  await set(dbRef(neighbor, messagePath), message);
  assert.equal((await get(dbRef(anotherNeighbor, messagePath))).val().text, message.text);
  await denied(get(dbRef(outsider, messagePath)));
  await denied(set(dbRef(outsider, messagePath), { ...message, text: "Inyectado" }));
  await denied(set(dbRef(neighbor, messagePath + "/text"), "Edición prohibida"));

  const salePath = newRecord(neighbor, "marketplace");
  await set(dbRef(neighbor, salePath), { ...attribution(neighbor),
    title: "Silla", description: "Silla plegable", price: 5000, category: "home", status: "available" });
  await denied(update(dbRef(anotherNeighbor, salePath), { price: 2 }));
  await denied(update(dbRef(neighbor, salePath), { owner: outsider.uid }));
  await denied(update(dbRef(neighbor, salePath), { price: 5.5 }));
  await update(dbRef(neighbor, salePath), { status: "sold" });
  await denied(set(dbRef(administrator, salePath), null));
  await set(dbRef(neighbor, salePath), null);

  const notice = { title: "Corte de agua", description: "Aviso municipal", priority: "important", status: "published" };
  await denied(set(dbRef(neighbor, newRecord(neighbor, "announcements")), { ...attribution(neighbor), ...notice }));
  const noticePath = newRecord(administrator, "announcements");
  await set(dbRef(administrator, noticePath), { ...attribution(administrator), ...notice });
  await denied(update(dbRef(administrator, noticePath), { notificationDispatchedAt: serverTimestamp() }));
  await update(dbRef(administrator, noticePath), { title: "Aviso actualizado" });
  await denied(set(dbRef(neighbor, noticePath), null));
  await set(dbRef(administrator, noticePath), null);
});

test("cuenta votos concurrentes de forma atómica y conserva la privacidad", async () => {
  const pollPath = newRecord(administrator, "polls");
  const id = pollPath.split("/").at(-1);
  const ledger = base + "/pollLedger/" + id + "/votes";
  const counts = base + "/pollTotals/" + id;
  const poll = { title: "Fecha de reunión", options: ["Lunes", "Martes"], closesAt: Date.now() + 86400000,
    showResults: false, closed: false, sparkVersion: 1 };
  await denied(set(dbRef(neighbor, newRecord(neighbor, "polls")), { ...attribution(neighbor), ...poll }));
  await set(dbRef(administrator, pollPath), { ...attribution(administrator), ...poll });
  const vote = (actor, choice) => update(dbRef(actor), {
    [ledger + "/" + actor.uid]: choice, [counts + "/" + choice]: increment(1)
  });
  await denied(set(dbRef(neighbor, ledger + "/" + neighbor.uid), 0));
  await denied(set(dbRef(neighbor, counts + "/0"), increment(1)));
  await denied(vote(neighbor, 2));
  await denied(update(dbRef(neighbor), { [ledger + "/" + neighbor.uid]: 0, [counts + "/0"]: 10 }));
  await denied(vote(outsider, 0));
  const concurrent = await Promise.allSettled([vote(neighbor, 0), vote(anotherNeighbor, 0), vote(neighbor, 0)]);
  assert.equal(concurrent.filter(attempt => attempt.status === "fulfilled").length, 2);
  await denied(vote(neighbor, 1));
  await denied(set(dbRef(neighbor, ledger + "/" + neighbor.uid), null));
  await denied(get(dbRef(neighbor, counts)));
  await denied(get(dbRef(administrator, ledger)));
  await denied(get(dbRef(neighbor, ledger + "/" + anotherNeighbor.uid)));
  assert.equal((await get(dbRef(neighbor, ledger + "/" + neighbor.uid))).val(), 0);
  assert.equal((await get(dbRef(administrator, counts + "/0"))).val(), 2);
  await denied(update(dbRef(administrator, pollPath), { showResults: true }));
  await denied(set(dbRef(neighbor, pollPath + "/closed"), true));
  await set(dbRef(administrator, pollPath + "/closed"), true);
  await denied(vote(verifiedPending, 1));
  await denied(set(dbRef(administrator, pollPath + "/closed"), false));

  const publicPath = newRecord(administrator, "polls");
  await set(dbRef(administrator, publicPath), { ...attribution(administrator), ...poll, showResults: true });
  const publicId = publicPath.split("/").at(-1);
  await get(dbRef(neighbor, base + "/pollTotals/" + publicId));
});

test("reanuda una encuesta migrada sin perder votos ni publicar totales privados", async () => {
  const path = newRecord(administrator, "polls");
  const id = path.split("/").at(-1);
  const legacy = { ...attribution(administrator), title: "Encuesta anterior", options: ["Sí", "No"],
    closed: false, closesAt: Date.now() + 86400000, showResults: false, revision: 1 };
  const ledger = base + "/pollLedger/" + id + "/votes";
  const totals = base + "/pollTotals/" + id;
  const adminReference = adminDatabase(server).ref();
  await adminReference.update({
    [path]: legacy,
    [base + "/pollLedger/" + id]: { ...legacy, votes: { [neighbor.uid]: 0 } }
  });
  const vote = actor => update(dbRef(actor), { [ledger + "/" + actor.uid]: 0, [totals + "/0"]: increment(1) });
  await denied(vote(anotherNeighbor));
  const communitySnapshot = (await adminReference.child(base).get()).val();
  await adminReference.update(planPollMigration(community, communitySnapshot));
  await denied(vote(neighbor));
  await vote(anotherNeighbor);
  assert.equal((await get(dbRef(administrator, totals + "/0"))).val(), 2);
  await denied(get(dbRef(neighbor, totals)));
  await set(dbRef(administrator, path + "/closed"), true);
});

test("protege el historial de mantenimiento y los estados del SOS", async () => {
  const report = newRecord(neighbor, "reports");
  const location = { title: "Fuga", description: "Fuga del pasillo", category: "leak", location: "Piso 2", status: "pending" };
  await set(dbRef(neighbor, report), { ...attribution(neighbor), ...location });
  await denied(get(dbRef(anotherNeighbor, report)));
  await denied(get(dbRef(outsider, report)));
  await denied(set(dbRef(neighbor, report + "/status"), "working"));
  await denied(set(dbRef(administrator, report + "/status"), "working"));
  const event = { status: "working", note: "", author: administrator.account.name, createdAt: serverTimestamp() };
  await denied(set(dbRef(administrator, report + "/history/change"), event));
  await update(dbRef(administrator, report), { status: "working", lastChangeId: "change", "history/change": event });
  await denied(update(dbRef(administrator, report), { status: "pending", lastChangeId: "change",
    "history/change": { ...event, status: "pending" } }));
  await denied(set(dbRef(administrator, report + "/history/change"), null));
  assert.equal((await get(dbRef(neighbor, report))).val().status, "working");
  const resolution = note => update(dbRef(administrator, report), { status: "resolved", lastChangeId: "resolution",
    "history/resolution": { ...event, status: "resolved", note } });
  await denied(resolution(" "));
  const unchanged = (await get(dbRef(neighbor, report))).val();
  assert.equal(unchanged.status, "working");
  assert.equal(unchanged.history.resolution, undefined);
  await resolution("La fuga fue reparada.");
  const resolved = (await get(dbRef(neighbor, report))).val();
  assert.equal(resolved.status, "resolved");
  assert.equal(resolved.history.resolution.note, "La fuga fue reparada.");

  const alert = newRecord(neighbor, "alerts");
  const alertFields = { status: "active", delivery: "pending", description: "", community, category: "security" };
  await denied(set(dbRef(neighbor, alert), { ...attribution(neighbor), ...alertFields, delivery: "published" }));
  await set(dbRef(neighbor, alert), { ...attribution(neighbor), ...alertFields });
  await denied(update(dbRef(neighbor, alert), { status: "attended" }));
  await denied(update(dbRef(administrator, alert), { delivery: "published" }));
  await update(dbRef(administrator, alert), { status: "closed", statusChangedAt: serverTimestamp(),
    statusChangedBy: administrator.uid });
  await denied(update(dbRef(administrator, alert), { status: "attended", statusChangedAt: serverTimestamp(),
    statusChangedBy: administrator.uid }));
  assert.equal((await get(dbRef(anotherNeighbor, alert))).val().status, "closed");
});

test("mantiene privados tokens de FCM y credenciales MQTT; suspende la lectura", async () => {
  const path = base + "/devices/" + neighbor.uid + "/" + "a".repeat(64);
  const token = { token: "fixture-token-exclusivo", updatedAt: serverTimestamp() };
  await set(dbRef(neighbor, path), token);
  await denied(get(dbRef(neighbor, path)));
  await denied(get(dbRef(administrator, path)));
  await denied(set(dbRef(anotherNeighbor, path), token));
  await set(dbRef(neighbor, path), null);
  const mqtt = "mqttAccess/" + neighbor.uid;
  const settings = { community, host: "mqtt.example.test:8883", username: "fixture", token: "local-fixture",
    topic: "communities/" + community + "/alerts" };
  await denied(set(dbRef(neighbor, mqtt), settings));
  await adminDatabase(server).ref(mqtt).set(settings);
  assert.deepEqual((await get(dbRef(neighbor, mqtt))).val(), settings);
  await denied(get(dbRef(administrator, mqtt)));
  await set(dbRef(administrator, base + "/members/" + neighbor.uid + "/active"), false);
  await denied(get(dbRef(neighbor, mqtt)));
});
