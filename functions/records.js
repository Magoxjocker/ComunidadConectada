const { HttpsError } = require("firebase-functions/v2/https");
const { denied, requireAdmin } = require("./access");
const validation = require("./validation");

const saleCategories = ["home", "technology", "clothing", "services", "other"];
const saleStates = ["available", "reserved", "sold"];
const announcementPriorities = ["info", "important", "urgent"];
const alertCategories = ["security", "fire", "accident", "medical", "other"];
const reportCategories = ["lighting", "cleaning", "leak", "elevator", "garden", "security", "other"];
const reportStates = ["pending", "working", "resolved"];

function recordId(context, input) {
  return `${context.uid}_${validation.requestId(input.requestId)}`;
}

function attribution(context) {
  return { owner: context.uid, author: context.member.name, unit: context.member.unit };
}

async function create(context, collection, input, fields) {
  const id = recordId(context, input);
  const reference = context.root.child(`${collection}/${id}`);
  await reference.transaction(current => current || {
    ...attribution(context), ...fields, createdAt: Date.now()
  });
  return { id };
}

async function update(context, collection, input, modify, { administratorOnly = false, deletion = false } = {}) {
  const id = validation.key(input.id);
  if (administratorOnly) requireAdmin(context);
  const result = await context.root.child(`${collection}/${id}`).transaction(current => {
    if (!current) return null;
    if (!administratorOnly && current.owner !== context.uid) return undefined;
    return modify(current);
  });
  if (!result.committed || (!result.snapshot.exists() && !deletion)) throw denied();
  return { id };
}

function saleFields(input) {
  return {
    title: validation.text(input.title, "producto", 100),
    description: validation.text(input.description, "descripción", 1500),
    price: validation.integer(input.price, "precio", 0, 1000000000),
    category: validation.option(input.category, saleCategories, "categoría"),
    status: validation.option(input.status, saleStates, "estado")
  };
}

function announcementFields(input) {
  return {
    title: validation.text(input.title, "título", 100),
    description: validation.text(input.description, "descripción", 3000),
    priority: validation.option(input.priority, announcementPriorities, "prioridad"),
    status: "published"
  };
}

const handlers = {
  sendMessage: (context, input) => create(context, "messages", input, {
    text: validation.text(input.text, "mensaje", 1000), community: context.account.community
  }),
  saveSale: (context, input) => {
    const fields = saleFields(input);
    return input.id ? update(context, "marketplace", input, current => ({ ...current, ...fields })) :
      create(context, "marketplace", input, fields);
  },
  deleteSale: (context, input) => update(context, "marketplace", input, () => null, { deletion: true }),
  saveAnnouncement: (context, input) => {
    requireAdmin(context);
    const fields = announcementFields(input);
    return input.id ? update(context, "announcements", input, current => ({ ...current, ...fields }), { administratorOnly: true }) :
      create(context, "announcements", input, fields);
  },
  deleteAnnouncement: (context, input) => update(context, "announcements", input, () => null, { administratorOnly: true, deletion: true }),
  createAlert: (context, input) => create(context, "alerts", input, {
    category: validation.option(input.category, alertCategories, "tipo de emergencia"),
    description: validation.text(input.description, "descripción", 1000, 0),
    community: context.account.community, status: "active", delivery: "pending"
  }),
  setAlertStatus: (context, input) => {
    const status = validation.option(input.status, ["attended", "closed"], "estado");
    return update(context, "alerts", input, current => {
      if (current.status === status) return current;
      if (current.status === "closed") return undefined;
      return { ...current, status, statusChangedAt: Date.now(), statusChangedBy: context.uid };
    }, { administratorOnly: true });
  },
  createReport: (context, input) => create(context, "reports", input, {
    title: validation.text(input.title, "título", 100),
    description: validation.text(input.description, "descripción", 2000),
    category: validation.option(input.category, reportCategories, "categoría"),
    location: validation.text(input.location, "ubicación", 120),
    status: "pending"
  }),
  updateReport: (context, input) => {
    const status = validation.option(input.status, reportStates, "estado");
    const note = validation.text(input.note, "observación", 1500, status === "resolved" ? 1 : 0);
    const changeId = validation.requestId(input.requestId);
    return update(context, "reports", input, current => {
      const history = current.history || {};
      if (history[changeId]) return current;
      if (Object.keys(history).length >= 50) {
        throw new HttpsError("resource-exhausted", "El reporte alcanzó el máximo de cambios.");
      }
      return { ...current, status, history: {
        ...history, [changeId]: { status, note, author: context.member.name, createdAt: Date.now() }
      } };
    }, { administratorOnly: true });
  }
};

module.exports = { handlers, recordId, attribution };
