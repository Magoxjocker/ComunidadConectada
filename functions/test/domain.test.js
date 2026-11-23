const assert = require("node:assert/strict");
const { test } = require("node:test");
const { randomUUID } = require("node:crypto");
const validation = require("../validation");
const { addVote, summary, tally } = require("../polls");
const { requireAdmin } = require("../access");

test("normaliza textos, correo y alternativas y rechaza entradas inválidas", () => {
  assert.equal(validation.text("  Ñandú  ", "nombre", 10), "Ñandú");
  assert.equal(validation.email(" VECINA@COMUNIDAD.CL "), "vecina@comunidad.cl");
  assert.deepEqual(validation.choices(["Árbol", " Iluminación "]), ["Árbol", "Iluminación"]);
  assert.throws(() => validation.choices(["Árbol", "ARBOL"]), { code: "invalid-argument" });
  assert.throws(() => validation.text(null, "título", 10), { code: "invalid-argument" });
  const id = randomUUID();
  assert.equal(validation.requestId(id), id);
  assert.throws(() => validation.requestId("-".repeat(36)), { code: "invalid-argument" });
});

test("exige la autorización que resolvió el servidor", () => {
  assert.throws(() => requireAdmin({ role: "admin", administrator: false }), { code: "permission-denied" });
  assert.doesNotThrow(() => requireAdmin({ administrator: true }));
});

test("rechaza votos duplicados, alternativos y fuera del plazo y protege identidades", () => {
  const poll = { options: ["Sí", "No"], showResults: false, closed: false, closesAt: 100, revision: 0 };
  const voted = addVote(poll, "first", 0, 99);
  assert.equal(addVote(voted, "first", 1, 99), undefined);
  assert.equal(addVote(voted, "another", 2, 99), undefined);
  assert.equal(addVote(voted, "another", 1, 100), undefined);
  assert.equal(addVote({ ...voted, closed: true }, "another", 1, 99), undefined);
  assert.deepEqual(tally(addVote(voted, "another", 1, 99)), [1, 1]);
  assert.equal("votes" in summary(voted), false);
  assert.equal("counts" in summary(voted), false);
  assert.deepEqual(summary({ ...voted, showResults: true }).counts, [1, 0]);
});
