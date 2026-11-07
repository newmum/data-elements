import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import vm from "node:vm";
import { nextTick, ref, shallowReactive, watch } from "vue";

const source = await readFile(
  new URL("../src/plugins/compiler/runtime-sfc.js", import.meta.url),
  "utf8"
);
const executable =
  source
    .replace(/^import .*;\r?$/gm, "")
    .replace(/^export default .*;\r?$/gm, "")
    .replace(/^export /gm, "") +
  "\nObject.assign(globalThis, { ensureRuntimeSfc, loadAllRuntimeSfc, hasRuntimeSfc });";

function component(name, version = "initial") {
  return { name, compileJs: `return { version: ${JSON.stringify(version)} };`, compileCss: "" };
}

function shell(version) {
  return [component("global-nav", version), component("shopping-car", version)];
}

function taskBundle(shellVersion) {
  return [
    ...shell(shellVersion),
    ...["data-convergence", "data-convergence-hive", "access-apply", "schedule-setting"].map(
      (name) => component(name)
    ),
  ];
}

function complete(scope, components) {
  return { complete: true, scope, components };
}

function harness() {
  const registry = new Map();
  const styles = [];
  const requests = [];
  const events = [];
  const revision = ref(0);
  let token = "session-a";

  const app = {
    component(name, definition) {
      if (arguments.length > 1) registry.set(name, definition);
      return registry.get(name);
    },
  };
  const request = (action, root) =>
    new Promise((resolve, reject) => requests.push({ action, root, resolve, reject }));
  const context = vm.createContext({
    shallowReactive,
    AuthStorage: { getAccessToken: () => token, getSessionRevision: () => revision.value },
    ComponentAPI: {
      getList: () => request("list"),
      getRuntimeBatch: (root) => request("batch", root),
    },
    console: { error() {} },
    window: { dispatchEvent: (event) => events.push(event) },
    CustomEvent: class {
      constructor(type, { detail }) {
        this.type = type;
        this.detail = detail;
      }
    },
    document: {
      createElement: () => ({ dataset: {} }),
      head: {
        querySelectorAll: () => styles,
        appendChild: (style) => {
          style.isConnected = true;
          styles.push(style);
        },
      },
    },
  });
  vm.runInContext(executable, context);

  return {
    app,
    requests,
    events,
    registry,
    ensure: (name, options) => context.ensureRuntimeSfc(app, name, options),
    loadAll: (options) => context.loadAllRuntimeSfc(app, options),
    has: (name) => context.hasRuntimeSfc(app, name),
    changeSession(newToken = token) {
      token = newToken;
      revision.value++;
    },
  };
}

test("layout and workbench share one root batch and shell readiness is reactive", async () => {
  const f = harness();
  const readiness = [];
  const stop = watch(
    () => f.has("global-nav") && f.has("shopping-car"),
    (ready) => readiness.push(ready),
    {
      immediate: true,
    }
  );
  const layout = f.ensure("my-workbench");
  const page = f.ensure("my-workbench");
  assert.deepEqual(
    f.requests.map(({ action, root }) => [action, root]),
    [["batch", "my-workbench"]]
  );
  f.requests[0].resolve(complete("root", [...shell(), component("my-workbench")]));
  assert.equal(await layout, await page);
  assert.equal(f.registry.size, 3);
  assert.equal(await f.ensure("app-shell"), undefined);
  assert.equal(f.requests.length, 1);
  await nextTick();
  assert.deepEqual(readiness, [false, true]);
  stop();
});

test("an unscoped direct route loads one compatibility bundle for all consumers", async () => {
  const f = harness();
  const layout = f.ensure("detail-page");
  const detail = f.ensure("detail-page");
  assert.deepEqual(f.requests.map(({ action }) => action), ["list"]);
  f.requests[0].resolve([...shell(), component("detail-page"), component("table-col")]);
  assert.equal(await layout, await detail);
  assert.equal(f.has("table-col"), true);
  assert.equal(f.has("global-nav"), true);
  assert.equal(await f.loadAll(), 4);
  assert.equal(f.requests.length, 1);
});

for (const directEntry of ["data-convergence", "data-convergence-hive"]) {
  test(`${directEntry} shares one request with its layout and the other task entry`, async () => {
    const f = harness();
    const first = f.ensure(directEntry);
    const layout = f.ensure(directEntry);
    const sibling = directEntry === "data-convergence" ? "data-convergence-hive" : "data-convergence";
    const simultaneous = f.ensure(sibling);
    assert.deepEqual(
      f.requests.map(({ action, root }) => [action, root]),
      [["batch", "data-convergence"]]
    );
    f.requests[0].resolve(complete("root", taskBundle()));
    assert.equal(await first, await layout);
    assert.notEqual(await first, await simultaneous);
    assert.equal(await f.ensure(sibling), await simultaneous);
    assert.equal(f.has("access-apply"), true);
    assert.equal(f.has("schedule-setting"), true);
    assert.equal(f.requests.length, 1);

    const detail = f.ensure("detail-page");
    assert.equal(f.requests[1].action, "list");
    f.requests[1].resolve([...taskBundle(), component("detail-page"), component("table-col")]);
    await detail;
    assert.equal(f.has("table-col"), true);
  });
}

test("a task package with an invalid dialog factory falls back once for both entries", async () => {
  const f = harness();
  const first = f.ensure("data-convergence");
  const sibling = f.ensure("data-convergence-hive");
  f.requests[0].resolve(
    complete("root", taskBundle().map((item) =>
      item.name === "access-apply" ? { ...item, compileJs: "return null;" } : item
    ))
  );
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(f.requests.map(({ action }) => action), ["batch", "list"]);
  assert.equal(f.events.length, 1);
  assert.equal(f.events[0].detail[0].name, "access-apply");
  f.requests[1].resolve(taskBundle());
  await Promise.all([first, sibling]);
  assert.equal(f.has("access-apply"), true);
});

test("static shell, then workbench or another route, never installs overlapping stale bundles", async () => {
  const f = harness();
  const layout = f.ensure("app-shell");
  const workbench = f.ensure("my-workbench");
  assert.deepEqual(
    f.requests.map(({ root }) => root),
    ["app-shell"]
  );
  f.requests[0].resolve(complete("root", shell("first")));
  await layout;
  assert.deepEqual(
    f.requests.map(({ root }) => root),
    ["app-shell", "my-workbench"]
  );
  f.requests[1].resolve(complete("root", [...shell("next"), component("my-workbench")]));
  await workbench;
  const workbenchDefinition = f.registry.get("MyWorkbench");

  const detail = f.ensure("detail-page");
  const other = f.ensure("data-convergence");
  assert.deepEqual(
    f.requests.map(({ action }) => action),
    ["batch", "batch", "list"]
  );
  f.requests[2].resolve([
    ...taskBundle("latest"),
    component("my-workbench"),
    component("detail-page"),
  ]);
  await Promise.all([detail, other]);
  assert.equal(f.registry.get("GlobalNav").version, "latest");
  assert.equal(f.registry.get("MyWorkbench"), workbenchDefinition);
});

test("server full fallback or legacy fallback is shared by concurrent route consumers", async () => {
  const full = harness();
  const layout = full.ensure("app-shell");
  const page = full.ensure("my-workbench");
  const unknown = full.ensure("detail-page");
  full.requests[0].resolve(
    complete("all", [...shell(), component("my-workbench"), component("detail-page")])
  );
  await Promise.all([layout, page, unknown]);
  assert.equal(full.requests.length, 1);

  const legacy = harness();
  const first = legacy.ensure("my-workbench");
  const second = legacy.ensure("my-workbench");
  legacy.requests[0].resolve(null);
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(
    legacy.requests.map(({ action }) => action),
    ["batch", "list"]
  );
  legacy.requests[1].resolve([...shell(), component("my-workbench")]);
  await Promise.all([first, second]);
  assert.equal(legacy.requests.length, 2);
});

test("component errors can be retried without silently downloading a second full package", async () => {
  const f = harness();
  const failure = f.ensure("my-workbench");
  f.requests[0].reject(new Error("network unavailable"));
  await assert.rejects(failure, /network unavailable/);
  assert.equal(f.requests.length, 1);

  const missing = f.ensure("my-workbench", { retry: true });
  f.requests[1].resolve(complete("all", shell()));
  await assert.rejects(missing, /my-workbench/);
  assert.equal(f.requests.length, 2);

  const recovered = f.ensure("my-workbench", { retry: true });
  assert.equal(f.requests[2].action, "list");
  f.requests[2].resolve([...shell(), component("my-workbench", "recovered")]);
  assert.equal((await recovered).version, "recovered");
});

test("an editor refresh replaces the bundle and older pending responses cannot overwrite it", async () => {
  const f = harness();
  const scoped = f.ensure("my-workbench");
  const refresh = f.loadAll({ force: true });
  assert.equal(f.loadAll({ force: true }), refresh);
  assert.deepEqual(
    f.requests.map(({ action }) => action),
    ["batch", "list"]
  );
  f.requests[1].resolve([...shell("new"), component("my-workbench", "new")]);
  assert.equal(await refresh, 3);
  f.requests[0].resolve(complete("root", [...shell("old"), component("my-workbench", "old")]));
  assert.equal((await scoped).version, "new");
  assert.equal(f.registry.get("GlobalNav").version, "new");

  const other = harness();
  const full = other.ensure("detail-page");
  const forced = other.loadAll({ force: true });
  other.requests[1].resolve([...shell("new"), component("detail-page", "new")]);
  await forced;
  other.requests[0].resolve([...shell("old"), component("detail-page", "old")]);
  assert.equal((await full).version, "new");
});

test("same-token tenant revision and changed credentials both discard old runtime results", async () => {
  const f = harness();
  const old = f.ensure("my-workbench");
  f.changeSession();
  const current = f.ensure("my-workbench");
  f.requests[1].resolve(
    complete("root", [...shell("tenant-b"), component("my-workbench", "tenant-b")])
  );
  assert.equal((await current).version, "tenant-b");
  f.requests[0].resolve(
    complete("root", [...shell("tenant-a"), component("my-workbench", "tenant-a")])
  );
  await assert.rejects(old, /会话已切换/);
  assert.equal(f.registry.get("GlobalNav").version, "tenant-b");

  f.changeSession("session-c");
  assert.equal(f.has("global-nav"), false);
  const fresh = f.ensure("app-shell");
  f.requests[2].resolve(complete("root", shell("session-c")));
  await fresh;
  assert.equal(f.registry.get("GlobalNav").version, "session-c");
});
