import { shallowReactive } from "vue";
import ComponentAPI from "@/api/system/component-api";
import { compileCode } from "@/plugins/compiler/sfc-compiler";
import { AuthStorage } from "@/utils/auth";

const runtimeStyles = new Map();
const runtimeSessions = shallowReactive(new WeakMap());
const shellComponents = ["global-nav", "shopping-car"];
const dataTaskRoot = {
  name: "data-convergence",
  required: [
    ...shellComponents,
    "data-convergence",
    "data-convergence-hive",
    "access-apply",
    "schedule-setting",
  ],
};
const scopedRoots = new Map([
  ["AppShell", { name: "app-shell", required: shellComponents }],
  ["MyWorkbench", { name: "my-workbench", required: ["my-workbench", ...shellComponents] }],
  ["DataConvergence", dataTaskRoot],
  ["DataConvergenceHive", dataTaskRoot],
]);

export function toRuntimeComponentName(name) {
  return String(name || "")
    .replace(/-(\w)/g, (_, character) => character.toUpperCase())
    .replace(/^(\w)/, (character) => character.toUpperCase());
}

function evaluateComponent(name, compileJs) {
  try {
    return (0, eval)(`(function(){\n${compileJs}\n})()`);
  } catch (error) {
    const wrapped = new Error(`组件“${name}”运行时代码解析失败：${error?.message || error}`);
    wrapped.cause = error;
    throw wrapped;
  }
}

function styleElement(name) {
  const cached = runtimeStyles.get(name);
  if (cached?.isConnected) return cached;

  const existing = Array.from(document.head.querySelectorAll("style[data-runtime-sfc]")).find(
    (element) => element.dataset.runtimeSfc === name
  );
  if (existing) {
    runtimeStyles.set(name, existing);
    return existing;
  }

  const element = document.createElement("style");
  element.dataset.runtimeSfc = name;
  document.head.appendChild(element);
  runtimeStyles.set(name, element);
  return element;
}

/** Compile one low-code SFC without exposing compiler details to callers. */
export async function compileRuntimeSfc(sourceCode) {
  return compileCode(sourceCode);
}

/** Register or replace one runtime component and its stylesheet atomically. */
export function registerRuntimeSfc(app, item) {
  if (!app) throw new Error("缺少 Vue 应用实例，无法注册低代码组件");
  if (!item?.name) throw new Error("缺少组件名称，无法注册低代码组件");

  const name = String(item.name);
  const componentName = toRuntimeComponentName(name);
  const component = evaluateComponent(name, item.compileJs || "");
  if (!component || !["object", "function"].includes(typeof component)) {
    throw new Error(`组件“${name}”缺少有效的运行定义`);
  }
  const style = styleElement(name);
  style.textContent = item.compileCss || "";
  app.component(componentName, component);
  return { componentName, component };
}

function matchesSession(session) {
  return (
    session?.revision === AuthStorage.getSessionRevision() &&
    session.token === AuthStorage.getAccessToken()
  );
}

function getRuntimeSession(app) {
  if (!app) throw new Error("缺少 Vue 应用实例，无法加载低代码组件");
  const existing = runtimeSessions.get(app);
  if (matchesSession(existing)) return existing;

  const session = {
    revision: AuthStorage.getSessionRevision(),
    token: AuthStorage.getAccessToken(),
    registered: shallowReactive(new Set()),
    scopedItems: new Map(),
    fullCount: null,
    fullRequest: null,
    batchRequest: null,
  };
  runtimeSessions.set(app, session);
  return session;
}

function assertCurrentSession(session) {
  if (!matchesSession(session)) throw new Error("登录会话已切换，请重新打开页面");
}

export function hasRuntimeSfc(app, name) {
  const session = app && runtimeSessions.get(app);
  return matchesSession(session) && session.registered.has(toRuntimeComponentName(name));
}

function registerBundle(app, session, components, { full = false, force = false } = {}) {
  const failures = [];
  if (full) session.registered.clear();
  components.forEach((item) => {
    try {
      const name = toRuntimeComponentName(item?.name);
      const previous = session.scopedItems.get(name);
      if (
        force ||
        !previous ||
        previous.compileJs !== item.compileJs ||
        previous.compileCss !== item.compileCss
      ) {
        registerRuntimeSfc(app, item);
      }
      session.registered.add(name);
      if (!full) session.scopedItems.set(name, item);
    } catch (error) {
      failures.push({ name: item?.name, error });
      console.error(error);
    }
  });
  if (full) session.scopedItems.clear();
  if (failures.length) {
    const aggregateError = new AggregateError(
      failures.map((item) => item.error),
      `${failures.length} 个低代码组件加载失败：${failures.map((item) => item.name).join("、")}`
    );
    console.error(aggregateError);
    window.dispatchEvent(
      new CustomEvent("runtime-sfc-load-failures", {
        detail: failures.map((item) => ({
          name: item.name,
          message: item.error?.message || String(item.error),
        })),
      })
    );
  }
  return components.length;
}

/** Load the compatibility bundle once per session, or replace it after an editor refresh. */
export function loadAllRuntimeSfc(app, { force = false } = {}) {
  const session = getRuntimeSession(app);
  if (session.fullRequest && (!force || session.fullRequest.force)) {
    return session.fullRequest.promise;
  }
  if (!force && session.fullCount !== null) return Promise.resolve(session.fullCount);

  const pending = { force, promise: null };
  const batch = session.batchRequest;
  if (force) session.batchRequest = null;
  session.fullRequest = pending;
  pending.promise = (async () => {
    if (!force && batch) await batch.promise.catch(() => {});
    assertCurrentSession(session);
    if (session.fullRequest !== pending) return session.fullRequest?.promise ?? session.fullCount;
    if (!force && session.fullCount !== null) return session.fullCount;

    const components = await ComponentAPI.getList();
    assertCurrentSession(session);
    if (session.fullRequest !== pending) return session.fullRequest?.promise ?? session.fullCount;
    if (!Array.isArray(components)) throw new Error("低代码组件包返回格式无效");
    session.fullCount = registerBundle(app, session, components, { full: true, force });
    return session.fullCount;
  })().finally(() => {
    if (session.fullRequest === pending) session.fullRequest = null;
  });
  return pending.promise;
}

async function loadScopedRuntimeSfc(app, session, root) {
  const existing = session.batchRequest;
  if (existing?.root === root) return existing.promise;
  if (existing) {
    await existing.promise.catch(() => {});
    assertCurrentSession(session);
    if (session.fullRequest) return session.fullRequest.promise;
    if (session.fullCount !== null || root.required.every((name) => hasRuntimeSfc(app, name)))
      return;
    if (session.batchRequest) return loadScopedRuntimeSfc(app, session, root);
  }

  const pending = { root, promise: null };
  session.batchRequest = pending;
  pending.promise = (async () => {
    const result = await ComponentAPI.getRuntimeBatch(root.name);
    assertCurrentSession(session);
    // An editor refresh supersedes any older scoped response.
    if (session.batchRequest !== pending) return session.fullRequest?.promise;
    if (result?.complete !== true || !Array.isArray(result.components)) return;
    if (result.scope === "all") {
      session.fullCount = registerBundle(app, session, result.components, { full: true });
      return "all";
    }
    if (result.scope === "root") {
      const included = new Set(result.components.map((item) => toRuntimeComponentName(item?.name)));
      if (!root.required.every((name) => included.has(toRuntimeComponentName(name)))) return;
      registerBundle(app, session, result.components);
      if (root.required.every((name) => hasRuntimeSfc(app, name))) return "root";
    }
  })().finally(() => {
    if (session.batchRequest === pending) session.batchRequest = null;
  });
  return pending.promise;
}

/** Resolve a mounted route or the shared app shell without fetching individual components. */
export async function ensureRuntimeSfc(app, name, { retry = false } = {}) {
  const componentName = toRuntimeComponentName(name);
  if (!componentName) throw new Error("页面未配置低代码组件");
  const session = getRuntimeSession(app);
  const root = scopedRoots.get(componentName);
  const required = root?.required ?? [name];
  const ready = () => required.every((item) => hasRuntimeSfc(app, item));
  if (session.fullRequest) await session.fullRequest.promise;
  assertCurrentSession(session);

  if (!ready()) {
    if (session.fullCount === null) {
      const scope = root && (await loadScopedRuntimeSfc(app, session, root));
      if (!scope && !ready() && session.fullCount === null) await loadAllRuntimeSfc(app);
    } else if (retry) {
      await loadAllRuntimeSfc(app, { force: true });
    }
  }

  assertCurrentSession(session);
  if (!ready()) {
    const missing = required.filter((item) => !hasRuntimeSfc(app, item));
    throw new Error(`组件“${missing.join("、")}”未加载，请重试`);
  }
  return componentName === "AppShell" ? undefined : app.component(componentName);
}

export const runtimeSfc = {
  compile: compileRuntimeSfc,
  register: registerRuntimeSfc,
  loadAll: loadAllRuntimeSfc,
  componentName: toRuntimeComponentName,
};

export default runtimeSfc;
