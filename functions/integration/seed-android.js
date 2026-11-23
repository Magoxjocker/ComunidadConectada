const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getDatabase } = require("firebase-admin/database");

async function seed() {
  if (!process.env.FIREBASE_AUTH_EMULATOR_HOST || !process.env.FIREBASE_DATABASE_EMULATOR_HOST ||
      process.env.GCLOUD_PROJECT !== "demo-comunidad") throw new Error("Usa los emuladores del proyecto demo-comunidad.");
  const app = initializeApp({ projectId: "demo-comunidad",
    databaseURL: "https://demo-comunidad-default-rtdb.firebaseio.com" });
  try {
    const password = "SoloFixture_2026!";
    const adminUid = "androidAdmin";
    const neighborUid = "androidNeighbor";
    const community = "androidQa";
    const accounts = {
      [adminUid]: { name: "Administración QA", unit: "A-1", community, role: "admin" },
      [neighborUid]: { name: "Vecina QA", unit: "A-2", community, role: "neighbor" }
    };
    const auth = getAuth(app);
    await auth.createUser({ uid: adminUid, email: "android-admin@example.test", password, emailVerified: true });
    await auth.setCustomUserClaims(adminUid, { role: "admin", community });
    await auth.createUser({ uid: neighborUid, email: "android-vecino@example.test", password, emailVerified: true });
    await auth.createUser({ uid: "androidUnverified", email: "android-verificar@example.test", password });
    await auth.createUser({ uid: "androidPending", email: "android-pendiente@example.test", password, emailVerified: true });
    const reports = {};
    for (let reportNumber = 1; reportNumber <= 50; reportNumber++) {
      const key = neighborUid + "_z" + String(reportNumber).padStart(2, "0");
      reports[key] = { owner: neighborUid, author: accounts[neighborUid].name, unit: accounts[neighborUid].unit,
        createdAt: 1700000000000 + Math.min(reportNumber, 49), title: "Reporte QA " + reportNumber,
        description: "Datos ficticios", location: "Pasillo", category: "lighting", status: "pending" };
    }
    reports[neighborUid + "_0newest"] = { ...reports[neighborUid + "_z50"], title: "Reporte QA reciente", createdAt: 1700000000051 };
    await getDatabase(app).ref().update({
      [`communityData/${community}/reports`]: reports,
      [`communityData/${community}/metadata`]: { displayName: "Comunidad QA" },
      "accounts/androidPending": { name: "Vecino pendiente", unit: "B-1", community: "", role: "neighbor" },
      [`accounts/${adminUid}`]: accounts[adminUid], [`accounts/${neighborUid}`]: accounts[neighborUid],
      [`communityData/${community}/members/${adminUid}`]: { name: accounts[adminUid].name, unit: accounts[adminUid].unit, role: "admin", email: "android-admin@example.test", active: true },
      [`communityData/${community}/members/${neighborUid}`]: { name: accounts[neighborUid].name, unit: accounts[neighborUid].unit, role: "neighbor", email: "android-vecino@example.test", active: true }
    });
  } finally {
    getDatabase(app).goOffline();
    await deleteApp(app);
  }
}

seed().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
