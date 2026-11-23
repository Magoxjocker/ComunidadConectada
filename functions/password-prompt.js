const { createInterface } = require("node:readline/promises");

const PROMPT = "Contraseña inicial de 14 a 128 caracteres (oculta): ";
const CONFIRM_PROMPT = "Confirma la contraseña (oculta): ";
const REQUIREMENTS = "Usa entre 14 y 128 caracteres, con mayúscula, minúscula, número y símbolo.";
const MULTILINE_INPUT = "Llegó texto adicional antes del siguiente aviso. Se descartó todo el bloque. Ejecuta el comando por separado e introduce una sola línea en cada aviso.";
const INPUT_CLOSED = "La entrada se cerró antes de confirmar la contraseña. Abre una consola interactiva de CMD o Google Cloud SDK Shell e inténtalo de nuevo.";
const INPUT_ERROR = "No se pudo leer la contraseña desde la consola. Abre una sesión interactiva nueva e inténtalo de nuevo.";
const MAX_ATTEMPTS = 3;
const PASSWORD_TIMEOUT_MS = 180_000;
const INPUT_SETTLE_MS = 250;

class PasswordPromptError extends Error {
  constructor(message, exitCode = 1) {
    super(message);
    this.exitCode = exitCode;
  }
}

function validPassword(password) {
  return password.length >= 14 && password.length <= 128 && password.trim() === password &&
    /[a-z]/.test(password) && /[A-Z]/.test(password) && /\d/.test(password) &&
    /[^A-Za-z0-9]/.test(password) && !/\p{Cc}/u.test(password);
}

function observeSubmissions(terminal, input, onSubmission, onEnd) {
  let pending;
  let previousKeyWasReturn = false;

  function markExtraInput() {
    pending.hasExtraInput = true;
    pending.timer.refresh();
  }

  function onData(chunk) {
    if (!pending) return;
    const lineFeed = typeof chunk === "string" ? chunk === "\n" : chunk.length === 1 && chunk[0] === 0x0a;
    if (!(pending.expectsLineFeed && lineFeed)) markExtraInput();
  }

  function onKeypress(_character, key) {
    if (key?.ctrl && key.name === "z") {
      onEnd();
      return;
    }
    if (pending) {
      if (pending.expectsLineFeed && key?.name === "enter") pending.expectsLineFeed = false;
      else markExtraInput();
    }
    previousKeyWasReturn = key?.name === "return";
  }

  function onLine(value) {
    if (pending) {
      markExtraInput();
      return;
    }
    // A paste can continue after Enter in the same read or across multiple reads.
    pending = { expectsLineFeed: previousKeyWasReturn, hasExtraInput: false };
    pending.timer = setTimeout(() => {
      const { hasExtraInput } = pending;
      pending = undefined;
      onSubmission(value, hasExtraInput);
    }, INPUT_SETTLE_MS);
  }

  input.prependListener("data", onData);
  input.prependListener("keypress", onKeypress);
  terminal.on("line", onLine);
  return () => {
    if (pending) clearTimeout(pending.timer);
    input.off("data", onData);
    input.off("keypress", onKeypress);
    terminal.off("line", onLine);
  };
}

function waitForPassword(terminal, input, output) {
  return new Promise((resolve, reject) => {
    let settled = false;
    let rejectedAttempts = 0;
    let candidate;

    function fail(message, exitCode) {
      if (settled) return;
      settled = true;
      candidate = undefined;
      reject(new PasswordPromptError(message, exitCode));
    }

    function retry(message) {
      candidate = undefined;
      if (++rejectedAttempts >= MAX_ATTEMPTS) {
        fail(message + " Se agotaron los intentos. Ejecuta nuevamente el comando.");
        return;
      }
      output.write("\n" + message + "\n" + PROMPT);
    }

    function onSubmission(password, hasExtraInput) {
      if (settled) return;
      if (hasExtraInput) fail(MULTILINE_INPUT);
      else if (!validPassword(password)) retry(REQUIREMENTS);
      else if (candidate === undefined) {
        candidate = password;
        output.write("\n" + CONFIRM_PROMPT);
      } else if (password !== candidate) retry("Las contraseñas no coinciden.");
      else {
        settled = true;
        resolve(candidate);
        candidate = undefined;
      }
    }

    const onEnd = () => fail(INPUT_CLOSED);
    const stopSubmissions = observeSubmissions(terminal, input, onSubmission, onEnd);
    const deadline = setTimeout(() => fail("Se agotó el tiempo para confirmar la contraseña."), PASSWORD_TIMEOUT_MS);

    // Readline can emit a partial line on EOF. Reject it before that line is considered.
    input.prependOnceListener("end", onEnd);
    input.once("close", onEnd);
    terminal.once("close", () => {
      clearTimeout(deadline);
      stopSubmissions();
      input.off("end", onEnd);
      input.off("close", onEnd);
      onEnd();
    });
    terminal.on("error", () => fail(INPUT_ERROR));
    terminal.on("SIGINT", () => fail("Aprovisionamiento cancelado.", 130));
    terminal.on("SIGTSTP", onEnd);
    output.write(PROMPT);
  });
}

async function passwordPrompt(input = process.stdin, output = process.stdout) {
  if (!input.isTTY || typeof input.setRawMode !== "function") {
    throw new PasswordPromptError("Ejecuta este comando en una terminal interactiva, sin redirigir la entrada estándar.");
  }
  if (input.destroyed || input.readableEnded) throw new PasswordPromptError(INPUT_CLOSED);

  let terminal;
  try {
    // The separate output stream is only used for fixed prompts, never for readline echo.
    terminal = createInterface({ input, terminal: true, historySize: 0, crlfDelay: INPUT_SETTLE_MS });
  } catch {
    throw new PasswordPromptError(INPUT_ERROR);
  }
  try {
    if (!input.isRaw) throw new PasswordPromptError(INPUT_ERROR);
    if (terminal.closed || input.destroyed || input.readableEnded) throw new PasswordPromptError(INPUT_CLOSED);
    return await waitForPassword(terminal, input, output);
  } finally {
    terminal.close();
    output.write("\n");
  }
}

module.exports = { PasswordPromptError, passwordPrompt };
