const assert = require("node:assert/strict");
const { EventEmitter, once } = require("node:events");
const { PassThrough } = require("node:stream");
const { test } = require("node:test");
const { passwordPrompt, provisionAdministrator } = require("../provision-admin");

const TEST_TIMEOUT_MS = 5000;
const INITIAL_PROMPT = "Contraseña inicial";
const CONFIRM_PROMPT = "Confirma la contraseña";
const validFixture = "SoloPruebas_AbC123!";
const invalidFixture = "sin requisitos";

function consoleFixture() {
  const input = new PassThrough();
  input.isTTY = true;
  input.isRaw = false;
  input.setRawMode = enabled => {
    input.isRaw = enabled;
    return input;
  };
  let transcript = "";
  const events = new EventEmitter();
  const output = { write: message => {
    transcript += message;
    events.emit("write");
  } };
  async function waitFor(text, count = 1) {
    while (transcript.split(text).length - 1 < count) await once(events, "write");
  }
  return { input, output, transcript: () => transcript, waitFor };
}

test("una línea vacía o inválida permite volver a intentar sin mostrar ningún carácter ingresado",
  { timeout: TEST_TIMEOUT_MS }, async () => {
    const fixture = consoleFixture();
    const pending = passwordPrompt(fixture.input, fixture.output);
    assert.equal(fixture.input.isRaw, true);

    fixture.input.write("\r");
    await fixture.waitFor(INITIAL_PROMPT, 2);
    fixture.input.write(invalidFixture + "\r");
    await fixture.waitFor(INITIAL_PROMPT, 3);

    fixture.input.write(validFixture + "X\b\r\n");
    await fixture.waitFor(CONFIRM_PROMPT);
    fixture.input.write(validFixture + "\r");
    assert.equal(await pending === validFixture, true);
    assert.equal(fixture.transcript().includes(validFixture), false);
    assert.equal(fixture.transcript().includes(invalidFixture), false);
    assert.equal(fixture.input.isRaw, false);
  });

test("descarta un bloque con argumentos aunque una línea cumpla todos los requisitos",
  { timeout: TEST_TIMEOUT_MS }, async () => {
    const fixture = consoleFixture();
    const commandFixture = 'npm --prefix functions run admin -- "fixture-22" "fixture@example.test" "Unidad A-1"';
    const pending = passwordPrompt(fixture.input, fixture.output);
    const checked = assert.rejects(pending, { message: /Se descartó todo el bloque/, exitCode: 1 });
    fixture.input.write("\rfixture@example.test\r" + commandFixture + "\r");
    await checked;
    assert.equal(fixture.transcript().split(INITIAL_PROMPT).length - 1, 1);
    assert.doesNotMatch(fixture.transcript(), /Confirma la contraseña|fixture@example\.test/);
    assert.equal(fixture.input.isRaw, false);
  });

test("dos líneas pegadas no confirman la contraseña aunque coincidan y lleguen en lecturas distintas",
  { timeout: TEST_TIMEOUT_MS }, async () => {
    const fixture = consoleFixture();
    const pending = passwordPrompt(fixture.input, fixture.output);
    const checked = assert.rejects(pending, { message: /Se descartó todo el bloque/ });
    fixture.input.write(validFixture + "\r");
    await new Promise(resolve => setImmediate(resolve));
    fixture.input.write(validFixture + "\r");
    await checked;
    assert.equal(fixture.transcript().includes(validFixture), false);
  });

test("preserva caracteres Unicode y CRLF cuando ambos se dividen entre lecturas",
  { timeout: TEST_TIMEOUT_MS }, async () => {
    const fixture = consoleFixture();
    const pending = passwordPrompt(fixture.input, fixture.output);
    const unicodeFixture = "SoloPruebas_22ñ€🙂";
    for (const byte of Buffer.from(unicodeFixture + "\r\n")) fixture.input.write(Buffer.from([byte]));
    await fixture.waitFor(CONFIRM_PROMPT);
    fixture.input.write(Buffer.from(unicodeFixture + "\r"));
    fixture.input.write(Buffer.from("\n"));
    assert.equal(await pending === unicodeFixture, true);
    assert.equal(fixture.transcript().includes(unicodeFixture), false);
  });

test("una confirmación diferente reinicia el intento y ninguna entrada aparece en la salida",
  { timeout: TEST_TIMEOUT_MS }, async () => {
    const fixture = consoleFixture();
    const pending = passwordPrompt(fixture.input, fixture.output);
    const differentFixture = validFixture + "X";
    fixture.input.write(validFixture + "\r");
    await fixture.waitFor(CONFIRM_PROMPT);
    fixture.input.write(differentFixture + "\r");
    await fixture.waitFor("Las contraseñas no coinciden.");
    fixture.input.write(validFixture + "\r");
    await fixture.waitFor(CONFIRM_PROMPT, 2);
    fixture.input.write(validFixture + "\r");
    assert.equal(await pending === validFixture, true);
    assert.equal(fixture.transcript().includes(validFixture), false);
    assert.equal(fixture.transcript().includes(differentFixture), false);
  });

test("limita los intentos de validación sin truncar ni revelar una entrada larga",
  { timeout: TEST_TIMEOUT_MS }, async () => {
    const fixture = consoleFixture();
    const pending = passwordPrompt(fixture.input, fixture.output);
    const checked = assert.rejects(pending, { message: /Se agotaron los intentos/, exitCode: 1 });
    const oversizedFixture = validFixture + "z".repeat(129 - validFixture.length);
    fixture.input.write(oversizedFixture + "\r");
    await fixture.waitFor(INITIAL_PROMPT, 2);
    fixture.input.write("\r");
    await fixture.waitFor(INITIAL_PROMPT, 3);
    fixture.input.write(invalidFixture + "\r");
    await checked;
    assert.doesNotMatch(fixture.transcript(), /Confirma la contraseña/);
    assert.equal(fixture.transcript().includes(oversizedFixture), false);
  });

test("límite de tiempo libera el modo sin eco aunque la entrada quede abierta", async context => {
  context.mock.timers.enable({ apis: ["setTimeout"] });
  const fixture = consoleFixture();
  const pending = passwordPrompt(fixture.input, fixture.output);
  const checked = assert.rejects(pending, { message: /Se agotó el tiempo/, exitCode: 1 });
  context.mock.timers.tick(180000);
  await checked;
  assert.equal(fixture.input.isRaw, false);
});

test("termina con diagnóstico al recibir EOF o perder el flujo de entrada",
  { timeout: TEST_TIMEOUT_MS }, async () => {
    for (const closeInput of [input => input.end(), input => input.destroy(), input => input.write("\u001a\r")]) {
      const fixture = consoleFixture();
      const pending = passwordPrompt(fixture.input, fixture.output);
      const checked = assert.rejects(pending, { message: /entrada se cerró/, exitCode: 1 });
      closeInput(fixture.input);
      await checked;
      assert.equal(fixture.input.isRaw, false);
    }
  });

test("EOF descarta una contraseña parcial aunque cumpla la política", { timeout: TEST_TIMEOUT_MS }, async () => {
  const fixture = consoleFixture();
  const pending = passwordPrompt(fixture.input, fixture.output);
  const checked = assert.rejects(pending, { message: /entrada se cerró/, exitCode: 1 });
  fixture.input.end(validFixture);
  await checked;
  assert.equal(fixture.transcript().includes(validFixture), false);
  assert.equal(fixture.input.isRaw, false);
});

test("un error de stdin se comunica sin revelar los datos ingresados", { timeout: TEST_TIMEOUT_MS }, async () => {
  const fixture = consoleFixture();
  const pending = passwordPrompt(fixture.input, fixture.output);
  const checked = assert.rejects(pending, error =>
    error.exitCode === 1 && /No se pudo leer/.test(error.message) && !error.message.includes(validFixture));
  fixture.input.write(validFixture);
  fixture.input.destroy(Object.assign(new Error(validFixture), { code: "EIO" }));
  await checked;
  assert.equal(fixture.transcript().includes(validFixture), false);
  assert.equal(fixture.input.isRaw, false);
});

test("rechaza stdin redirigida, una terminal cerrada o una consola que no activa el modo sin eco", async () => {
  const fixture = consoleFixture();
  const nonTerminal = new PassThrough();
  await assert.rejects(passwordPrompt(nonTerminal, fixture.output), /sin redirigir/);
  fixture.input.setRawMode = () => fixture.input;
  await assert.rejects(passwordPrompt(fixture.input, fixture.output), /No se pudo leer/);
  fixture.input.destroy();
  await assert.rejects(passwordPrompt(fixture.input, fixture.output), /entrada se cerró/);
  assert.doesNotMatch(fixture.transcript(), /Contraseña inicial/);
});

test("Ctrl+C cancela con código 130 y evita la creación del usuario o escrituras",
  { timeout: TEST_TIMEOUT_MS }, async () => {
    const fixture = consoleFixture();
    let enteredPrompt;
    const promptStarted = new Promise(resolve => { enteredPrompt = resolve; });
    const auth = {
      getUserByEmail: async () => { throw Object.assign(new Error(), { code: "auth/user-not-found" }); },
      createUser: async () => { assert.fail("No debe invocar Auth para crear un usuario."); }
    };
    const database = {
      ref: () => ({
        get: async () => ({ val: () => null }),
        transaction: async () => { assert.fail("No debe intentar una escritura de RTDB."); }
      })
    };
    const pending = provisionAdministrator(auth, database, {
      community: "fixture", email: "fixture@example.test", displayName: "Fixture Administrador",
      unit: "A-1", communityName: "Comunidad Fixture"
    }, () => {
      const password = passwordPrompt(fixture.input, fixture.output);
      enteredPrompt();
      return password;
    });
    const checked = assert.rejects(pending, { message: /cancelado/, exitCode: 130 });
    await promptStarted;
    fixture.input.write("\u0003");
    await checked;
    assert.equal(fixture.input.isRaw, false);
  });
