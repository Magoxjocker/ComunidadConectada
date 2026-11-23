const { randomUUID } = require("node:crypto");

const LEASE_MS = 5 * 60 * 1000;
const RECORD_ID = /^[A-Za-z0-9_-]{1,170}$/;

function deliveryError(code) {
  return Object.assign(new Error(code), { code });
}

function errorCode(error) {
  return typeof error.code === "number" ||
    (typeof error.code === "string" && /^[\w/-]{1,100}$/.test(error.code))
    ? error.code : "delivery-error";
}

async function withDelivery(root, collection, recordId, eligible, deliver) {
  if (!RECORD_ID.test(recordId)) throw deliveryError("invalid-record-id");
  const record = root.child(collection).child(recordId);
  const initial = (await record.get()).val();
  if (!eligible(initial)) return;

  const generation = initial.createdAt ?? 0;
  const ledger = root.root.child("sparkDelivery").child(root.key).child(collection).child(recordId);
  const owner = randomUUID();
  const claim = await ledger.transaction(current => {
    if (current?.expiresAt > Date.now()) return undefined;
    const progress = current?.generation === generation ? current : { generation };
    return { ...progress, owner, expiresAt: Date.now() + LEASE_MS };
  }, undefined, false);
  if (!claim.committed) throw deliveryError("delivery-busy");

  async function checkpoint(fields = {}) {
    const result = await ledger.transaction(current => {
      if (current === null) return null;
      if (current.owner !== owner) return undefined;
      return { ...current, ...fields, expiresAt: Date.now() + LEASE_MS };
    }, undefined, false);
    if (!result.committed || result.snapshot.val()?.owner !== owner) throw deliveryError("delivery-lease-lost");
    return result.snapshot.val();
  }

  try {
    const current = (await record.get()).val();
    const sameRecord = candidate => eligible(candidate) && (candidate.createdAt ?? 0) === generation;
    if (sameRecord(current)) return await deliver(record, current, claim.snapshot.val(), checkpoint, sameRecord);
  } finally {
    await ledger.transaction(current => {
      if (current === null) return null;
      if (current.owner !== owner) return undefined;
      return { ...current, owner: null, expiresAt: null };
    }, undefined, false);
  }
}

async function updateDelivery(record, sameRecord, fields) {
  await record.transaction(current => {
    if (current === null) return null;
    return sameRecord(current) ? { ...current, ...fields } : undefined;
  }, undefined, false);
}

module.exports = { RECORD_ID, deliveryError, errorCode, withDelivery, updateDelivery };
