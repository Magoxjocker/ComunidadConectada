const assert = require("node:assert/strict");
const { createHash, randomUUID } = require("node:crypto");
const { test, before, after } = require("node:test");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getDatabase } = require("firebase-admin/database");
const { initializeApp: initializeClient, deleteApp: deleteClient } = require("firebase/app");
const { getAuth: getClientAuth, connectAuthEmulator, signInWithEmailAndPassword, signOut } = require("firebase/auth");
const { connectDatabaseEmulator, getDatabase: getClientDatabase, get, goOffline,
  limitToFirst, orderByKey, query, ref, set } = require("firebase/database");
const { provisionAdministrator } = require("../provision-admin");

const projectId = process.env.GCLOUD_PROJECT;
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const databaseHost = process.env.FIREBASE_DATABASE_EMULATOR_HOST;

if (projectId !== "demo-comunidad" || !/^127\.0\.0\.1:\d+$/.test(authHost || "") ||
    !/^127\.0\.0\.1:\d+$/.test(databaseHost || "")) {
  throw new Error("La suite se ejecuta únicamente con Auth y Database locales en demo-comunidad.");
}

const databaseURL = "https://demo-comunidad-default-rtdb.firebaseio.com";
const password = "SoloFixture_" + randomUUID();
let app;
let auth;
let database;

before(() => {
  app = initializeApp({ projectId, databaseURL });
  auth = getAuth(app);
  database = getDatabase(app);
});
after(async () => {
  database?.goOffline();
  if (app) await deleteApp(app);
});

function administrator(community, communityName = "Comunidad de pruebas") {
  return { community, communityName, email: randomUUID() + "@example.test", displayName: "Persona de Pruebas", unit: "A-01" };
}
function uidFor(input) {
  return "cc_admin_" + createHash("sha256").update(input.email).digest("hex");
}
function paths(input, uid = uidFor(input)) {
  return { metadata: "communityData/" + input.community + "/metadata",
    membership: "communityData/" + input.community + "/members/" + uid, account: "accounts/" + uid };
}
function accountOf(input, role = "admin") {
  return { name: input.displayName, unit: input.unit, community: input.community, role };
}
function membershipOf(input, role = "admin", active = true) {
  return { name: input.displayName, unit: input.unit, email: input.email, role, active };
}
async function read(path) {
  return (await database.ref(path).get()).val();
}
const askPassword = async () => password;
const unexpectedPassword = async () => { throw new Error("El reintento no debe solicitar ni cambiar la contraseña."); };

test("crea el administrador, confirma el modelo y preserva cambios legítimos al repetir", async () => {
  const input = administrator("comuna-centro", "Comunidad Central");
  const { uid, emailVerified } = await provisionAdministrator(auth, database, input, askPassword);
  assert.equal(uid, uidFor(input));
  assert.equal(emailVerified, false);
  const path = paths(input);
  assert.deepEqual(await read(path.metadata), { displayName: input.communityName });
  assert.deepEqual(await read(path.account), accountOf(input));
  assert.deepEqual(await read(path.membership), membershipOf(input));
  assert.deepEqual((await auth.getUser(uid)).customClaims, { role: "admin", community: input.community });

  const client = initializeClient({ projectId, databaseURL, apiKey: "solo-emulador" }, "provision-client");
  const clientAuth = getClientAuth(client);
  connectAuthEmulator(clientAuth, "http://" + authHost, { disableWarnings: true });
  const clientDatabase = getClientDatabase(client);
  connectDatabaseEmulator(clientDatabase, "127.0.0.1", Number(databaseHost.split(":")[1]));
  try {
    const metadata = ref(clientDatabase, path.metadata + "/displayName");
    await signInWithEmailAndPassword(clientAuth, input.email, password);
    await assert.rejects(get(metadata), /permission.denied/i);
    await signOut(clientAuth);

    await auth.updateUser(uid, { emailVerified: true });
    await auth.setCustomUserClaims(uid, { role: "admin", community: input.community, invariant: "conservar" });
    await database.ref().update({ [path.account + "/name"]: "Nombre actualizado", [path.membership + "/name"]: "Nombre actualizado",
      [path.metadata + "/retained"]: true });
    const repeat = await provisionAdministrator(auth, database, { ...input, displayName: "Ignorar reemplazo", unit: "B-03" },
      unexpectedPassword);
    assert.deepEqual(repeat, { uid, emailVerified: true });
    assert.deepEqual(await read(path.account), { ...accountOf(input), name: "Nombre actualizado" });
    assert.deepEqual(await read(path.membership), { ...membershipOf(input), name: "Nombre actualizado" });
    assert.deepEqual(await read(path.metadata), { displayName: input.communityName, retained: true });
    assert.deepEqual((await auth.getUser(uid)).customClaims, { role: "admin", community: input.community, invariant: "conservar" });

    await signInWithEmailAndPassword(clientAuth, input.email, password);
    assert.equal((await get(metadata)).val(), "Comunidad Central");
    const members = query(ref(clientDatabase, "communityData/" + input.community + "/members"), orderByKey(), limitToFirst(10));
    assert.equal((await get(members)).child(uid).child("active").val(), true);
    await assert.rejects(set(metadata, "Nombre desde Android"), /permission.denied/i);
  } finally {
    goOffline(clientDatabase);
    await deleteClient(client);
  }
});

test("conserva una cuenta con el mismo correo y UID distinto", async () => {
  const input = administrator("correo-existente");
  const existing = await auth.createUser({ email: input.email, password });
  await auth.setCustomUserClaims(existing.uid, { role: "neighbor" });
  await assert.rejects(provisionAdministrator(auth, database, input, unexpectedPassword), /otra cuenta/);
  assert.deepEqual((await auth.getUser(existing.uid)).customClaims, { role: "neighbor" });
  const path = paths(input);
  assert.equal(await read(path.account), null);
  assert.equal(await read(path.metadata), null);
});

test("rechaza cuentas deshabilitadas, roles ajenos y comunidades incompatibles", async () => {
  const cases = [
    { code: "cuenta-deshabilitada", disabled: true, message: /inhabilitada/ },
    { code: "claims-vecino", claims: { role: "neighbor" }, message: /incompatibles/ },
    { code: "claims-otra-comunidad", claims: { role: "admin", community: "otra-comunidad" }, message: /incompatibles/ }
  ];
  for (const scenario of cases) {
    const input = administrator(scenario.code);
    const uid = uidFor(input);
    await auth.createUser({ uid, email: input.email, password, disabled: scenario.disabled ?? false });
    if (scenario.claims) await auth.setCustomUserClaims(uid, scenario.claims);
    await assert.rejects(provisionAdministrator(auth, database, input, unexpectedPassword), scenario.message);
    assert.deepEqual((await auth.getUser(uid)).customClaims, scenario.claims);
    assert.equal(await read(paths(input).metadata), null);
  }
});

test("rechaza membresías suspendidas, con otro rol o sin perfil; conserva los registros", async () => {
  const cases = [
    { code: "membresia-suspendida", role: "admin", active: false, account: true, message: /suspendida/ },
    { code: "membresia-vecino", role: "neighbor", active: true, account: true, message: /incompatible/ },
    { code: "membresia-incompleta", role: "admin", active: true, account: false, message: /incompatible/ }
  ];
  for (const scenario of cases) {
    const input = administrator(scenario.code);
    const uid = uidFor(input);
    const path = paths(input);
    await auth.createUser({ uid, email: input.email, password });
    const membership = membershipOf(input, scenario.role, scenario.active);
    await database.ref().update({ [path.membership]: membership, [path.account]: scenario.account ? accountOf(input) : null });
    await assert.rejects(provisionAdministrator(auth, database, input, unexpectedPassword), scenario.message);
    assert.deepEqual(await read(path.membership), membership);
    assert.equal(await read(path.metadata), null);
    assert.equal((await auth.getUser(uid)).customClaims, undefined);
  }
});

test("rechaza un perfil asignado a otra comunidad y un nombre de comunidad en conflicto", async () => {
  const input = administrator("conflicto-de-nombre");
  const path = paths(input);
  await database.ref(path.metadata).set({ displayName: "Nombre legítimo" });
  await assert.rejects(provisionAdministrator(auth, database, input, unexpectedPassword), /otro nombre/);
  await assert.rejects(auth.getUserByEmail(input.email), { code: "auth/user-not-found" });
  assert.deepEqual(await read(path.metadata), { displayName: "Nombre legítimo" });

  const assigned = administrator("conflicto-de-perfil");
  await auth.createUser({ uid: uidFor(assigned), email: assigned.email, password });
  const previous = { ...accountOf(assigned), community: "otra-comunidad" };
  await database.ref(paths(assigned).account).set(previous);
  await assert.rejects(provisionAdministrator(auth, database, assigned, unexpectedPassword), /perfil existente/);
  assert.deepEqual(await read(paths(assigned).account), previous);
});

test("añade el nombre a un administrador anterior sin reemplazar sus permisos", async () => {
  const input = administrator("administracion-anterior");
  const uid = uidFor(input);
  const path = paths(input);
  const claims = { role: "admin", community: input.community, permisoExistente: true };
  await auth.createUser({ uid, email: input.email, password, emailVerified: true });
  await auth.setCustomUserClaims(uid, claims);
  await database.ref().update({ [path.account]: accountOf(input), [path.membership]: membershipOf(input) });
  assert.deepEqual(await provisionAdministrator(auth, database, input, unexpectedPassword), { uid, emailVerified: true });
  assert.deepEqual(await read(path.metadata), { displayName: input.communityName });
  assert.deepEqual(await read(path.account), accountOf(input));
  assert.deepEqual((await auth.getUser(uid)).customClaims, claims);
});

test("rechaza registros huérfanos y una solicitud sin nombre visible", async () => {
  const input = administrator("registro-huerfano");
  const path = paths(input);
  await assert.rejects(provisionAdministrator(auth, database, { ...input, communityName: " " }, unexpectedPassword),
    /nombre visible/);
  await database.ref(path.account).set(accountOf(input));
  await assert.rejects(provisionAdministrator(auth, database, input, unexpectedPassword), /sin su cuenta/);
  assert.deepEqual(await read(path.account), accountOf(input));
  assert.equal(await read(path.metadata), null);
});

test("un reintento completa un fallo de claims sin recrear la cuenta ni modificar los datos", async () => {
  const input = administrator("reintento-claims");
  const failingAuth = {
    getUserByEmail: auth.getUserByEmail.bind(auth), createUser: auth.createUser.bind(auth),
    setCustomUserClaims: async () => { throw new Error("Fallo de prueba al asignar claims"); }
  };
  await assert.rejects(provisionAdministrator(failingAuth, database, input, askPassword), /Fallo de prueba/);
  const path = paths(input);
  const expected = await read(path.membership);
  const { uid } = await provisionAdministrator(auth, database, input, unexpectedPassword);
  assert.deepEqual(await read(path.membership), expected);
  assert.deepEqual((await auth.getUser(uid)).customClaims, { role: "admin", community: input.community });
});
