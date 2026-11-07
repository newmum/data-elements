import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import vm from "node:vm";

const app = new URL("../../data-elements-chengtian/", import.meta.url);
const compilerModule = new URL("src/plugins/compiler/sfc-compiler.js", app);
const sandbox = {};
vm.runInNewContext(await readFile(new URL("public/babel.min.js", app), "utf8"), sandbox);

const tsSetup = '<template><span>{{ label }}</span></template><script setup lang = ts>const label: string = "验证";</script><style scoped lang="scss">span { color: blue; }</style>';
const tsScript = '<template><span>{{ label }}</span></template><script lang="ts">export default {data(){const label: string = "验证";return {label}}};</script>';
const jsScript = '<template><!-- <script lang="ts"></script> --><span>{{ label }}</span></template><script setup>const label = "验证";</script>';
let moduleVersion = 0;

function freshCompiler() {
  return import(compilerModule.href + "?verification=" + ++moduleVersion);
}

function setGlobal(t, name, value) {
  const previous = Object.getOwnPropertyDescriptor(globalThis, name);
  Object.defineProperty(globalThis, name, { configurable: true, writable: true, value });
  t.after(() => {
    if (previous) Object.defineProperty(globalThis, name, previous);
    else delete globalThis[name];
  });
}

function browser(t) {
  const scripts = [];
  setGlobal(t, "window", {});
  setGlobal(t, "document", {
    baseURI: "https://example.test/chengtian/#/register/component",
    createElement(tag) {
      assert.equal(tag, "script");
      return { remove() { this.removed = true; } };
    },
    head: { appendChild(script) { scripts.push(script); } },
  });
  return scripts;
}

test("JavaScript and invalid SFCs skip Babel; concurrent TypeScript compiles share one request", async (t) => {
  const { compileCode } = await freshCompiler();
  const scripts = browser(t);
  const js = await compileCode(jsScript);
  assert.doesNotThrow(() => new Function(js.compileJs));
  assert.equal(scripts.length, 0);
  await assert.rejects(compileCode('<script setup lang="ts">a</script><script setup lang="ts">b</script>'));
  assert.equal(scripts.length, 0);

  const first = compileCode(tsSetup);
  const second = compileCode(tsScript);
  assert.equal(scripts.length, 1);
  assert.equal(scripts[0].src, "https://example.test/chengtian/babel.min.js");
  assert.equal(scripts[0].async, true);
  window.Babel = sandbox.Babel;
  scripts[0].onload();
  const [setup, ordinary] = await Promise.all([first, second]);
  assert.doesNotThrow(() => new Function(setup.compileJs));
  assert.doesNotThrow(() => new Function(ordinary.compileJs));
  assert.match(setup.compileCss, /span\[data-v-/);
  await compileCode(tsSetup);
  assert.equal(scripts.length, 1);
});

test("failed or invalid script loads reset the shared promise and support retry", async (t) => {
  const { compileCode } = await freshCompiler();
  const scripts = browser(t);
  const first = compileCode(tsSetup);
  const concurrent = compileCode(tsScript);
  assert.equal(scripts.length, 1);
  scripts[0].onerror();
  await Promise.all([
    assert.rejects(first, /TypeScript 编译器加载失败，请重新尝试编译/),
    assert.rejects(concurrent, /TypeScript 编译器加载失败，请重新尝试编译/),
  ]);
  assert.equal(scripts[0].removed, true);
  assert.equal(scripts[0].onload, null);
  assert.equal(scripts[0].onerror, null);

  const invalid = compileCode(tsSetup);
  assert.equal(scripts.length, 2);
  scripts[1].onload();
  await assert.rejects(invalid, /TypeScript 编译器加载后未能初始化/);
  assert.equal(scripts[1].removed, true);

  const retry = compileCode(tsSetup);
  assert.equal(scripts.length, 3);
  window.Babel = sandbox.Babel;
  scripts[2].onload();
  const { compileJs } = await retry;
  assert.doesNotThrow(() => new Function(compileJs));
});

test("a load timeout releases the pending request and allows a later compile", async (t) => {
  const { compileCode } = await freshCompiler();
  const scripts = browser(t);
  const setTimeout = globalThis.setTimeout;
  let expire;
  const timer = t.mock.method(globalThis, "setTimeout", (handler, delay) => {
    expire = handler;
    return setTimeout(handler, delay);
  });
  const first = compileCode(tsSetup);
  timer.mock.restore();
  expire();
  await assert.rejects(first, /TypeScript 编译器加载超时/);
  assert.equal(scripts[0].removed, true);

  const retry = compileCode(tsSetup);
  assert.equal(scripts.length, 2);
  window.Babel = sandbox.Babel;
  scripts[1].onload();
  await retry;
});

test("the CLI's pre-injected Babel keeps both public compile paths usable", async (t) => {
  setGlobal(t, "window", { Babel: sandbox.Babel });
  const { compileCode, compileFile } = await freshCompiler();
  const output = { js: "", css: "", errors: [] };
  assert.equal(compileFile(tsSetup, output), undefined);
  assert.deepEqual(output.errors, []);
  assert.doesNotMatch(output.js, /label: string/);
  assert.match(output.css, /span\[data-v-/);
  const { compileJs } = await compileCode(tsScript);
  assert.doesNotThrow(() => new Function(compileJs));

  delete window.Babel;
  const missing = { js: "", css: "", errors: [] };
  assert.equal(compileFile(tsSetup, missing), undefined);
  assert.match(missing.errors[0], /TypeScript 编译器尚未加载/);
});
