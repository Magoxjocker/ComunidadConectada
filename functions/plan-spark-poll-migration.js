const { readFileSync } = require("node:fs");
const { isDeepStrictEqual } = require("node:util");

function planPollMigration(community, snapshot) {
  if (!/^[A-Za-z0-9_-]{1,120}$/.test(community) || !snapshot || typeof snapshot !== "object") {
    throw new Error("Indica el código y la copia JSON de una comunidad.");
  }
  const changes = {};
  for (const [id, poll] of Object.entries(snapshot.polls || {})) {
    if (poll.sparkVersion === 1) continue;
    if (!/^[A-Za-z0-9_-]{1,170}$/.test(id)) throw new Error("Identificador de encuesta inválido.");
    const ledger = snapshot.pollLedger?.[id];
    if (!ledger || !Array.isArray(ledger.options) || ledger.options.length < 2 || ledger.options.length > 6 ||
        !ledger.options.every(option => typeof option === "string" && option.length > 0 && option.length <= 100) ||
        !Number.isSafeInteger(ledger.closesAt) || typeof ledger.showResults !== "boolean" ||
        typeof ledger.closed !== "boolean" || ledger.title !== poll.title || ledger.closesAt !== poll.closesAt ||
        ledger.showResults !== poll.showResults || ledger.closed !== poll.closed ||
        !isDeepStrictEqual(ledger.options, poll.options) || ledger.revision !== poll.revision ||
        snapshot.pollTotals?.[id] != null) {
      throw new Error("Revisa el respaldo y concilia la encuesta " + id + " antes de migrar.");
    }
    const totals = Object.fromEntries(ledger.options.map((option, optionIndex) => [optionIndex, 0]));
    for (const choice of Object.values(ledger.votes || {})) {
      if (!Number.isInteger(choice) || choice < 0 || choice >= ledger.options.length) {
        throw new Error("La encuesta " + id + " contiene un voto inválido.");
      }
      totals[choice] += 1;
    }
    const path = "communityData/" + community;
    changes[path + "/pollTotals/" + id] = totals;
    changes[path + "/polls/" + id + "/sparkVersion"] = 1;
  }
  return changes;
}

if (require.main === module) {
  try {
    const [community, path] = process.argv.slice(2);
    if (!path) throw new Error("Uso: node plan-spark-poll-migration.js COMUNIDAD RESPALDO_LOCAL.json");
    const snapshot = JSON.parse(readFileSync(path, "utf8").replace(/^\uFEFF/, ""));
    console.log(JSON.stringify(planPollMigration(community, snapshot), null, 2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

module.exports = { planPollMigration };
