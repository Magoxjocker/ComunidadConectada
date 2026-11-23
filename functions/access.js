const { getAuth } = require("firebase-admin/auth");
const { getDatabase } = require("firebase-admin/database");
const { HttpsError } = require("firebase-functions/v2/https");
const validation = require("./validation");

function accountRef(uid) {
  return getDatabase().ref(`accounts/${uid}`);
}

function communityRef(community) {
  return getDatabase().ref(`communityData/${community}`);
}

function denied() {
  return new HttpsError("permission-denied", "Tu cuenta no tiene acceso a esta operación.");
}

async function actor(request, requireVerified = true) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Inicia sesión nuevamente.");
  if (requireVerified && request.auth.token.email_verified !== true) {
    throw new HttpsError("failed-precondition", "Verifica tu correo primero.");
  }
  const identity = await getAuth().getUser(request.auth.uid);
  if (identity.disabled || !identity.email || (requireVerified && !identity.emailVerified)) throw denied();
  return { uid: identity.uid, email: identity.email, claims: identity.customClaims || {} };
}

async function memberContext(request) {
  const identity = await actor(request);
  const account = (await accountRef(identity.uid).get()).val();
  if (!account?.community) throw denied();
  const root = communityRef(account.community);
  const member = (await root.child(`members/${identity.uid}`).get()).val();
  if (!member?.active) throw denied();
  const administrator = member.role === "admin" &&
    request.auth.token.role === "admin" && request.auth.token.community === account.community &&
    identity.claims.role === "admin" && identity.claims.community === account.community;
  return { ...identity, account, root, member, administrator };
}

function requireAdmin(context) {
  if (!context.administrator) throw denied();
}

async function registerProfile(request) {
  const identity = await actor(request, false);
  const name = validation.text(request.data.name, "nombre", 100, 2);
  const unit = validation.text(request.data.unit, "vivienda", 30);
  await accountRef(identity.uid).transaction(current => current || { name, unit, community: "", role: "neighbor" });
}

async function renameProfile(context, input) {
  const name = validation.text(input.name, "nombre", 100, 2);
  await getDatabase().ref().update({
    [`accounts/${context.uid}/name`]: name,
    [`communityData/${context.account.community}/members/${context.uid}/name`]: name
  });
}

async function addNeighbor(context, input) {
  requireAdmin(context);
  const email = validation.email(input.email);
  let neighbor;
  try {
    neighbor = await getAuth().getUserByEmail(email);
  } catch (error) {
    if (error.code !== "auth/user-not-found") throw error;
    throw new HttpsError("not-found", "La cuenta aún no está lista. Pide al vecino que verifique el correo y complete el registro.");
  }
  if (!neighbor.emailVerified || neighbor.disabled) throw denied();
  const result = await accountRef(neighbor.uid).transaction(account => {
    if (!account) return null;
    if (account.role !== "neighbor") return undefined;
    if (account.community && account.community !== context.account.community) return undefined;
    return { ...account, community: context.account.community };
  });
  if (!result.committed || !result.snapshot.exists()) {
    throw new HttpsError("failed-precondition", "La cuenta no se puede incorporar a esta comunidad.");
  }
  const profile = result.snapshot.val();
  await context.root.child(`members/${neighbor.uid}`).transaction(member => member || {
    name: profile.name, unit: profile.unit, email, role: "neighbor", active: true
  });
}

async function setNeighborAccess(context, input) {
  requireAdmin(context);
  const uid = validation.key(input.uid, "vecino");
  const active = validation.boolean(input.active, "acceso");
  const changed = await context.root.child(`members/${uid}`).transaction(member => {
    if (!member) return null;
    if (member.role !== "neighbor") return undefined;
    return { ...member, active };
  });
  if (!changed.committed || !changed.snapshot.exists()) throw denied();
}

module.exports = { accountRef, communityRef, denied, memberContext, requireAdmin, registerProfile, renameProfile, addNeighbor, setNeighborAccess };
