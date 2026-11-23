const assert = require("node:assert/strict");
const { test } = require("node:test");
const { spawnSync } = require("node:child_process");
const { mkdtempSync, rmdirSync, unlinkSync, writeFileSync } = require("node:fs");
const { tmpdir } = require("node:os");
const { join } = require("node:path");
const { productionConfiguration, requireServiceAccount } = require("../provision-admin");

const environment = {
  CC_PROJECT_ID: "comunidadconectada-35c3c",
  CC_DATABASE_URL: "https://comunidadconectada-35c3c-default-rtdb.firebaseio.com/",
  CC_ADMIN_SERVICE_ACCOUNT: "fixture-operator@comunidadconectada-35c3c.iam.gserviceaccount.com"
};

test("acepta únicamente el proyecto y la URL canónica de producción", () => {
  const configuration = productionConfiguration(environment);
  assert.equal(configuration.projectId, environment.CC_PROJECT_ID);
  assert.equal(configuration.databaseURL, environment.CC_DATABASE_URL.slice(0, -1));
  assert.deepEqual(productionConfiguration({ ...environment, CC_DATABASE_URL: configuration.databaseURL }), configuration);
  assert.throws(() => productionConfiguration({ ...environment, CC_PROJECT_ID: "demo-comunidad" }), /exactamente/);
  for (const databaseURL of ["http://127.0.0.1:9000", "https://different.firebaseio.com",
    configuration.databaseURL + "/path", configuration.databaseURL + "/?ns=test", configuration.databaseURL + "#test",
    configuration.databaseURL.replace("https://", "https://example@")]) {
    assert.throws(() => productionConfiguration({ ...environment, CC_DATABASE_URL: databaseURL }), /exactamente/);
  }
});

test("bloquea indicadores de emulador incluso si la variable está vacía", () => {
  for (const variable of ["FIREBASE_AUTH_EMULATOR_HOST", "FIREBASE_DATABASE_EMULATOR_HOST", "FIREBASE_EMULATOR_HUB",
    "FIRESTORE_EMULATOR_HOST", "FUNCTIONS_EMULATOR", "STORAGE_EMULATOR_HOST", "EVENTARC_EMULATOR",
    "firebase_database_emulator_host"]) {
    assert.throws(() => productionConfiguration({ ...environment, [variable]: "" }), /Emulator Suite/);
  }
});

test("exige que las ADC resuelvan la cuenta de servicio esperada", async () => {
  const identity = environment.CC_ADMIN_SERVICE_ACCOUNT;
  assert.throws(() => productionConfiguration({ ...environment, CC_ADMIN_SERVICE_ACCOUNT: "person@example.test" }),
    /cuenta de servicio/);
  assert.throws(() => productionConfiguration({ ...environment, CC_ADMIN_SERVICE_ACCOUNT: "operator@other.iam.gserviceaccount.com" }),
    /cuenta de servicio/);
  await requireServiceAccount({ getServiceAccountEmail: async () => identity }, identity);
  await assert.rejects(requireServiceAccount({ getServiceAccountEmail: async () => "" }, identity), /ADC/);
  await assert.rejects(requireServiceAccount({ getServiceAccountEmail: async () => "someone-else" }, identity), /ADC/);
});

test("resuelve la identidad de ADC impersonadas con Firebase Admin SDK sin pedir tokens", context => {
  const directory = mkdtempSync(join(tmpdir(), "cc-adc-fixture-"));
  const credentials = join(directory, "adc.json");
  context.after(() => {
    unlinkSync(credentials);
    rmdirSync(directory);
  });
  const serviceAccount = environment.CC_ADMIN_SERVICE_ACCOUNT;
  writeFileSync(credentials, JSON.stringify({
    type: "impersonated_service_account",
    service_account_impersonation_url:
      "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/" + serviceAccount + ":generateAccessToken",
    source_credentials: { type: "authorized_user", client_id: "fixture", client_secret: "fixture", refresh_token: "fixture" }
  }));
  const script = `
    const rejectNetwork = () => { throw new Error("La prueba no permite conexiones HTTP."); };
    global.fetch = rejectNetwork;
    for (const protocol of ["node:http", "node:https"]) {
      const transport = require(protocol);
      transport.request = rejectNetwork;
      transport.get = rejectNetwork;
    }
    const { applicationDefault } = require(process.argv[1]);
    const { requireServiceAccount } = require(process.argv[2]);
    requireServiceAccount(applicationDefault(), process.argv[3]).catch(error => {
      console.error(error.message);
      process.exitCode = 1;
    });
  `;
  const result = spawnSync(process.execPath, ["-e", script, require.resolve("firebase-admin/app"),
    require.resolve("../provision-admin"), serviceAccount], {
    env: { ...process.env, GOOGLE_APPLICATION_CREDENTIALS: credentials },
    encoding: "utf8",
    timeout: 10000
  });
  assert.ifError(result.error);
  assert.equal(result.status, 0, result.stderr);
});
