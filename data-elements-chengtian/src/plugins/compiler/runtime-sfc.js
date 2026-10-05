import ComponentAPI from "@/api/system/component-api";
import { compileCode } from "@/plugins/compiler/sfc-compiler";

const runtimeStyles = new Map();

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
  const style = styleElement(name);
  style.textContent = item.compileCss || "";
  app.component(componentName, component);
  return { componentName, component };
}

/** Compile source and immediately register the resulting runtime component. */
export async function compileAndRegisterRuntimeSfc(app, name, sourceCode) {
  const compiled = await compileRuntimeSfc(sourceCode);
  return {
    ...compiled,
    ...registerRuntimeSfc(app, { name, ...compiled }),
  };
}

/** Load and register the active low-code component bundle. */
export async function loadAllRuntimeSfc(app) {
  const components = (await ComponentAPI.getList()) || [];
  const failures = [];
  components.forEach((item) => {
    try {
      registerRuntimeSfc(app, item);
    } catch (error) {
      failures.push({ name: item?.name, error });
      console.error(error);
    }
  });
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

export const runtimeSfc = {
  compile: compileRuntimeSfc,
  register: registerRuntimeSfc,
  compileAndRegister: compileAndRegisterRuntimeSfc,
  loadAll: loadAllRuntimeSfc,
  componentName: toRuntimeComponentName,
};

export default runtimeSfc;
