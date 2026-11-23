const { initializeApp } = require("firebase-admin/app");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { memberContext, registerProfile, renameProfile, addNeighbor, setNeighborAccess } = require("./access");
const { handlers } = require("./records");
const { createPoll, vote, closePoll, pollResults } = require("./polls");
const delivery = require("./delivery");

initializeApp();

const actions = {
  ...handlers, renameProfile, addNeighbor, setNeighborAccess, createPoll, vote, closePoll, pollResults,
  notificationDevice: delivery.notificationDevice
};

exports.communityAction = onCall({ region: delivery.REGION }, async request => {
  const input = request.data;
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new HttpsError("invalid-argument", "La solicitud no es válida.");
  }
  if (input.action === "registerProfile") {
    await registerProfile(request);
    return {};
  }
  if (typeof input.action !== "string" || !Object.hasOwn(actions, input.action)) {
    throw new HttpsError("invalid-argument", "La acción no está disponible.");
  }
  const context = await memberContext(request);
  return (await actions[input.action](context, input)) || {};
});

exports.mqttSubscription = onCall({ region: delivery.REGION, secrets: [delivery.mqttSecret] }, async request =>
  delivery.mqttSubscription(await memberContext(request)));
exports.publishAlert = delivery.publishAlert;
exports.notifyAnnouncement = delivery.notifyAnnouncement;
