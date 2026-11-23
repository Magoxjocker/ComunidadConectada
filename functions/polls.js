const { HttpsError } = require("firebase-functions/v2/https");
const { requireAdmin } = require("./access");
const { recordId, attribution } = require("./records");
const validation = require("./validation");

const MAX_POLL_DURATION_MS = 90 * 24 * 60 * 60 * 1000;

function tally(poll) {
  const counts = poll.options.map(() => 0);
  for (const vote of Object.values(poll.votes || {})) counts[vote] += 1;
  return counts;
}

function summary(poll) {
  const { votes, ...metadata } = poll;
  return metadata.showResults ? { ...metadata, counts: tally(poll) } : metadata;
}

async function publishSummary(context, id, poll) {
  await context.root.child(`polls/${id}`).transaction(current =>
    current && current.revision >= poll.revision ? current : summary(poll));
}

async function createPoll(context, input) {
  requireAdmin(context);
  const id = recordId(context, input);
  const now = Date.now();
  const poll = {
    ...attribution(context),
    title: validation.text(input.title, "pregunta", 150),
    options: validation.choices(input.options),
    closesAt: validation.integer(input.closesAt, "fecha de cierre", now + 60000, now + MAX_POLL_DURATION_MS),
    showResults: validation.boolean(input.showResults, "visibilidad de resultados"),
    closed: false,
    revision: 0,
    createdAt: now
  };
  const saved = await context.root.child(`pollLedger/${id}`).transaction(current => current || poll);
  await publishSummary(context, id, saved.snapshot.val());
  return { id };
}

function addVote(poll, uid, choice, now) {
  if (!poll || poll.closed || now >= poll.closesAt || choice >= poll.options.length) return undefined;
  if (Object.hasOwn(poll.votes || {}, uid)) return undefined;
  return { ...poll, votes: { ...poll.votes, [uid]: choice }, revision: poll.revision + 1 };
}

async function vote(context, input) {
  const id = validation.key(input.id);
  const choice = validation.integer(input.choice, "alternativa", 0, 5);
  const result = await context.root.child(`pollLedger/${id}`).transaction(current =>
    current ? addVote(current, context.uid, choice, Date.now()) : null);
  const poll = result.snapshot.val();
  if (!result.committed || !poll) {
    if (poll?.votes?.[context.uid] !== choice) {
      throw new HttpsError("failed-precondition", "La encuesta cerró o ya registraste un voto.");
    }
  }
  await publishSummary(context, id, poll);
  return { id };
}

async function closePoll(context, input) {
  requireAdmin(context);
  const id = validation.key(input.id);
  const result = await context.root.child(`pollLedger/${id}`).transaction(current => {
    if (!current) return null;
    return current.closed ? current : { ...current, closed: true, revision: current.revision + 1 };
  });
  if (!result.committed || !result.snapshot.exists()) throw new HttpsError("not-found", "La encuesta ya no está disponible.");
  await publishSummary(context, id, result.snapshot.val());
}

async function pollResults(context, input) {
  requireAdmin(context);
  const id = validation.key(input.id);
  const poll = (await context.root.child(`pollLedger/${id}`).get()).val();
  if (!poll) throw new HttpsError("not-found", "La encuesta ya no está disponible.");
  return { counts: tally(poll) };
}

module.exports = { createPoll, vote, closePoll, pollResults, addVote, summary, tally };
