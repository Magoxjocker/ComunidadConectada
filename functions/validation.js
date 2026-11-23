const { HttpsError } = require("firebase-functions/v2/https");

function invalid(field) {
  throw new HttpsError("invalid-argument", `Revisa el campo ${field}.`);
}

function text(value, field, maximum, minimum = 1) {
  if (typeof value !== "string") invalid(field);
  const normalized = value.normalize("NFC").trim();
  if (normalized.length < minimum || normalized.length > maximum) invalid(field);
  return normalized;
}

function key(value, field = "identificador") {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]{1,170}$/.test(value)) invalid(field);
  return value;
}

function requestId(value) {
  if (typeof value !== "string" || !/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(value)) invalid("solicitud");
  return value;
}

function option(value, choices, field) {
  if (!choices.includes(value)) invalid(field);
  return value;
}

function integer(value, field, minimum, maximum) {
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) invalid(field);
  return value;
}

function boolean(value, field) {
  if (typeof value !== "boolean") invalid(field);
  return value;
}

function email(value) {
  const normalized = text(value, "correo", 254).toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized)) invalid("correo");
  return normalized;
}

function choices(value) {
  if (!Array.isArray(value) || value.length < 2 || value.length > 6) invalid("alternativas");
  const options = value.map(choice => text(choice, "alternativas", 100));
  const folded = options.map(choice => choice.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase());
  if (new Set(folded).size !== options.length) invalid("alternativas repetidas");
  return options;
}

module.exports = { text, key, requestId, option, integer, boolean, email, choices };
