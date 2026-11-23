const { createHash } = require("node:crypto");
const { PasswordPromptError, passwordPrompt } = require("./password-prompt");
const { initializeApp, applicationDefault, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getDatabase } = require("firebase-admin/database");
const validation = require("./validation");

const PROJECT_ID = "comunidadconectada-35c3c";
const DATABASE_URL = "https://comunidadconectada-35c3c-default-rtdb.firebaseio.com";
const SERVICE_ACCOUNT_DOMAIN = "@" + PROJECT_ID + ".iam.gserviceaccount.com";
const ADMIN_ROLE = "admin";
const EMAIL_NOT_FOUND = "auth/user-not-found";
const EMULATOR_VARIABLE = /^(?:FIREBASE_.*EMULATOR.*|FIRESTORE_EMULATOR_HOST|STORAGE_EMULATOR_HOST|PUBSUB_EMULATOR_HOST|FUNCTIONS_EMULATOR|EVENTARC_EMULATOR(?:_HOST)?)$/i;

class ProvisioningError extends Error {}

function productionConfiguration(environment) {
  if (Object.keys(environment).some(name => EMULATOR_VARIABLE.test(name))) {
    throw new ProvisioningError("Abre una sesión sin variables de Firebase Emulator Suite.");
  }
  const { CC_PROJECT_ID: projectId, CC_DATABASE_URL: databaseURL, CC_ADMIN_SERVICE_ACCOUNT: serviceAccount } = environment;
  if (projectId !== PROJECT_ID || (databaseURL !== DATABASE_URL && databaseURL !== DATABASE_URL + "/")) {
    throw new ProvisioningError("CC_PROJECT_ID y CC_DATABASE_URL deben identificar exactamente el proyecto y la instancia autorizados.");
  }
  if (typeof serviceAccount !== "string" || !serviceAccount.endsWith(SERVICE_ACCOUNT_DOMAIN) ||
      !/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(serviceAccount.slice(0, -SERVICE_ACCOUNT_DOMAIN.length))) {
    throw new ProvisioningError("Define CC_ADMIN_SERVICE_ACCOUNT con la cuenta de servicio autorizada de este proyecto.");
  }
  return { projectId, databaseURL: DATABASE_URL, serviceAccount };
}

async function requireServiceAccount(credential, serviceAccount) {
  if (typeof credential.getServiceAccountEmail !== "function" ||
      await credential.getServiceAccountEmail() !== serviceAccount) {
    throw new ProvisioningError("Las ADC deben resolver la cuenta de servicio configurada. Revisa la impersonación o la identidad federada.");
  }
}

function administratorInput({ community, email, displayName, unit, communityName }) {
  try {
    const code = validation.key(community, "comunidad");
    if (code.length > 120) throw new Error();
    return {
      community: code,
      email: validation.email(email),
      displayName: validation.text(displayName, "nombre de la persona administradora", 100, 2),
      unit: validation.text(unit, "unidad", 30),
      communityName: validation.text(communityName, "nombre de la comunidad", 100, 2)
    };
  } catch {
    throw new ProvisioningError("Revisa código de comunidad, correo, nombre de la persona, unidad y nombre visible de la comunidad.");
  }
}

async function lookupByEmail(auth, email) {
  try {
    return await auth.getUserByEmail(email);
  } catch (error) {
    if (error.code === EMAIL_NOT_FOUND) return null;
    throw error;
  }
}

function validateIdentity(user, uid, community) {
  if (!user || user.uid !== uid || user.disabled) {
    throw new ProvisioningError("El correo pertenece a otra cuenta o la cuenta administrativa está inhabilitada.");
  }
  const claims = user.customClaims || {};
  if ((Object.hasOwn(claims, "role") || Object.hasOwn(claims, "community")) &&
      (claims.role !== ADMIN_ROLE || claims.community !== community)) {
    throw new ProvisioningError("La cuenta ya tiene un rol o una comunidad incompatibles.");
  }
  return claims;
}

function validateMetadata(metadata, communityName) {
  if (metadata.displayName !== communityName) {
    throw new ProvisioningError("La comunidad ya tiene metadatos con otro nombre o formato. Revisa el registro existente.");
  }
}

function validStoredText(value, minimum, maximum) {
  return typeof value === "string" && value.trim().length >= minimum && value.length <= maximum;
}

function validateAccount(account, community) {
  if (account.community !== community || account.role !== ADMIN_ROLE ||
      !validStoredText(account.name, 2, 100) || !validStoredText(account.unit, 1, 30)) {
    throw new ProvisioningError("El perfil existente tiene datos, un rol o una comunidad incompatibles.");
  }
}

function validateMembership(membership, account, email) {
  if (membership.active === false) throw new ProvisioningError("La membresía administrativa está suspendida.");
  if (membership.active !== true || membership.role !== ADMIN_ROLE || membership.email !== email ||
      !account || membership.name !== account.name || membership.unit !== account.unit) {
    throw new ProvisioningError("La membresía existente es incompatible con el perfil administrativo.");
  }
}

async function existingRecord(reference) {
  return (await reference.get()).val();
}

async function ensureRecord(reference, existing, initialValue, check) {
  if (existing !== null) return existing;
  const result = await reference.transaction(current => current === null ? initialValue : undefined);
  const saved = result.snapshot.val();
  if (saved === null) throw new ProvisioningError("No se pudo confirmar un registro del aprovisionamiento.");
  check(saved);
  return saved;
}

async function prepare(auth, database, input, uid) {
  const { community, communityName, email } = input;
  const metadata = database.ref("communityData/" + community + "/metadata");
  const account = database.ref("accounts/" + uid);
  const membership = database.ref("communityData/" + community + "/members/" + uid);
  const [user, savedMetadata, savedAccount, savedMembership] = await Promise.all([
    lookupByEmail(auth, email), existingRecord(metadata), existingRecord(account), existingRecord(membership)
  ]);
  if (user) validateIdentity(user, uid, community);
  else if (savedAccount !== null || savedMembership !== null) {
    throw new ProvisioningError("Hay registros administrativos para un UID sin su cuenta de Authentication. Revisa el estado antes de continuar.");
  }
  if (savedMetadata !== null) validateMetadata(savedMetadata, communityName);
  if (savedAccount !== null) validateAccount(savedAccount, community);
  if (savedMembership !== null) validateMembership(savedMembership, savedAccount, email);
  return { metadata, account, membership, user, savedMetadata, savedAccount, savedMembership };
}

async function confirmDatabase(state, input) {
  const [metadata, account, membership] = await Promise.all([
    existingRecord(state.metadata), existingRecord(state.account), existingRecord(state.membership)
  ]);
  if (metadata === null || account === null || membership === null) {
    throw new ProvisioningError("El aprovisionamiento quedó incompleto. Revisa los registros antes de continuar.");
  }
  validateMetadata(metadata, input.communityName);
  validateAccount(account, input.community);
  validateMembership(membership, account, input.email);
}

async function provisionAdministrator(auth, database, options, readPassword = passwordPrompt) {
  const input = administratorInput(options);
  const { community, communityName, email, displayName, unit } = input;
  const uid = "cc_admin_" + createHash("sha256").update(email).digest("hex");
  const state = await prepare(auth, database, input, uid);
  if (!state.user) {
    const created = await auth.createUser({ uid, email, displayName, password: await readPassword() });
    validateIdentity(created, uid, community);
  }

  await ensureRecord(state.metadata, state.savedMetadata, { displayName: communityName },
    metadata => validateMetadata(metadata, communityName));
  const profile = await ensureRecord(state.account, state.savedAccount,
    { name: displayName, unit, community, role: ADMIN_ROLE }, account => validateAccount(account, community));
  await ensureRecord(state.membership, state.savedMembership,
    { name: profile.name, unit: profile.unit, email, role: ADMIN_ROLE, active: true },
    membership => validateMembership(membership, profile, email));

  await confirmDatabase(state, input);
  const identity = await auth.getUserByEmail(email);
  const claims = validateIdentity(identity, uid, community);
  if (claims.role === ADMIN_ROLE && claims.community === community) return { uid, emailVerified: identity.emailVerified };
  await auth.setCustomUserClaims(uid, { ...claims, role: ADMIN_ROLE, community });

  const confirmed = await auth.getUserByEmail(email);
  const confirmedClaims = validateIdentity(confirmed, uid, community);
  if (confirmedClaims.role !== ADMIN_ROLE || confirmedClaims.community !== community) {
    throw new ProvisioningError("No se pudieron confirmar los permisos administrativos en Authentication.");
  }
  await confirmDatabase(state, input);
  return { uid, emailVerified: confirmed.emailVerified };
}

async function main() {
  const configuration = productionConfiguration(process.env);
  const args = process.argv.slice(2);
  if (args.length !== 5) {
    throw new ProvisioningError("Uso: npm --prefix functions run admin -- CODIGO CORREO NOMBRE_PERSONA UNIDAD NOMBRE_COMUNIDAD");
  }
  const [community, email, displayName, unit, communityName] = args;
  const input = administratorInput({ community, email, displayName, unit, communityName });
  const credential = applicationDefault();
  await requireServiceAccount(credential, configuration.serviceAccount);
  const app = initializeApp({ credential, projectId: configuration.projectId, databaseURL: configuration.databaseURL });
  try {
    const result = await provisionAdministrator(getAuth(app), getDatabase(app), input);
    console.log(result.emailVerified
      ? "Perfil, membresía activa y claims confirmados. Inicia sesión nuevamente para actualizar el token."
      : "Perfil, membresía activa y claims confirmados. El acceso sigue pendiente hasta verificar el correo.");
  } finally {
    await deleteApp(app);
  }
}

if (require.main === module) {
  main().catch(error => {
    const code = typeof error.code === "string" && /^[a-z][a-z-]*\/[a-z][a-z-]*$/.test(error.code)
      ? " (" + error.code + ")" : "";
    console.error(error instanceof ProvisioningError || error instanceof PasswordPromptError ? error.message
      : "Aprovisionamiento incompleto" + code + ". Revisa ADC, IAM y el estado de los registros antes de repetirlo.");
    process.exitCode = error instanceof PasswordPromptError ? error.exitCode : 1;
  });
}

module.exports = { productionConfiguration, requireServiceAccount, provisionAdministrator, passwordPrompt };
