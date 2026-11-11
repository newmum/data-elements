import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import ts from "typescript";
import * as Vue from "vue";
import { compileTemplate, parse } from "vue/compiler-sfc";

const { createRenderer, h, nextTick, ref, shallowRef, watch } = Vue;

async function compileRender(path) {
  const source = await readFile(new URL(path, import.meta.url), "utf8");
  const { descriptor } = parse(source);
  const { code, errors } = compileTemplate({
    source: descriptor.template.content,
    filename: path,
    id: path,
    compilerOptions: { expressionPlugins: ["typescript"] },
  });
  assert.deepEqual(errors, []);
  const { outputText } = ts.transpileModule(code, {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  });
  const exports = {};
  new Function("require", "exports", outputText)(() => Vue, exports);
  return exports.render;
}

function makeHost(document) {
  function node(type, text = "") {
    const classes = new Set();
    const events = new Map();
    return {
      type,
      text,
      classes,
      events,
      children: [],
      parent: null,
      ownerDocument: document,
      classList: { add: (name) => classes.add(name), remove: (name) => classes.delete(name) },
      addEventListener: (name, callback) => events.set(name, callback),
      removeEventListener: (name) => events.delete(name),
    };
  }
  function remove(child) {
    if (!child.parent) return;
    child.parent.children.splice(child.parent.children.indexOf(child), 1);
    child.parent = null;
  }
  const renderer = createRenderer({
    createElement: (type) => node(type),
    createText: (text) => node("text", text),
    createComment: (text) => node("comment", text),
    insert(child, parent, anchor) {
      remove(child);
      const index = anchor ? parent.children.indexOf(anchor) : parent.children.length;
      parent.children.splice(index, 0, child);
      child.parent = parent;
    },
    remove,
    parentNode: (child) => child.parent,
    nextSibling: (child) => child.parent?.children[child.parent.children.indexOf(child) + 1],
    setText: (child, text) => (child.text = text),
    setElementText: (child, text) => (child.text = text),
    patchProp() {},
  });
  return { renderer, root: node("root") };
}

test("a committed menu change removes the previous page before the next page is ready", async (t) => {
  const [appRender, pageRender] = await Promise.all([
    compileRender("../src/layouts/components/AppMain/index.vue"),
    compileRender("../src/layouts/views/ShowComponent.vue"),
  ]);
  const frames = [];
  const document = { body: { offsetHeight: 0 } };
  const restore = new Map();
  for (const [name, value] of Object.entries({
    document,
    window: {
      getComputedStyle: (element) => ({
        animationDelay: "0s",
        animationDuration: element.classes.has("animate__fadeIn") ? "0.01s" : "0s",
        transitionDelay: "0s",
        transitionDuration: "0s",
        transitionProperty: "none",
      }),
    },
    requestAnimationFrame: (callback) => frames.push(callback),
  })) {
    restore.set(name, Object.getOwnPropertyDescriptor(globalThis, name));
    Object.defineProperty(globalThis, name, { configurable: true, value });
  }
  t.after(() => {
    for (const [name, descriptor] of restore) {
      if (descriptor) Object.defineProperty(globalThis, name, descriptor);
      else delete globalThis[name];
    }
  });

  const { renderer, root } = makeHost(document);
  const mountedNodes = (node) => [node, ...node.children.flatMap(mountedNodes)];
  async function finishTransitions() {
    for (let turn = 0; turn < 8; turn++) {
      await nextTick();
      for (const frame of frames.splice(0)) frame();
      for (const element of mountedNodes(root)) {
        element.events.get("animationend")?.({ target: element });
      }
    }
  }

  const active = ref("数据源管理");
  const loaded = [];
  let finishIngestion;
  const pendingIngestion = new Promise((resolve) => {
    finishIngestion = () => resolve({ render: () => h("main", "接入任务") });
  });
  const pages = new Map([
    ["数据源管理", { render: () => h("main", "数据源管理") }],
    ["接入任务", pendingIngestion],
    ["分发任务", { render: () => h("main", "分发任务") }],
  ]);
  const ShowComponent = {
    props: ["name"],
    render: pageRender,
    components: {
      WorkspaceLoading: { render: () => h("div", "正在初始化应用资源") },
      ElButton: { render: () => h("button") },
    },
    setup(props) {
      const pageComponent = shallowRef(null);
      watch(
        () => props.name,
        async (name) => {
          pageComponent.value = await Promise.resolve(pages.get(name));
          loaded.push(name);
        },
        { immediate: true }
      );
      return { pageComponent, loadError: "" };
    },
  };
  const RouterView = {
    setup(_, { slots }) {
      return () =>
        slots.default({
          Component: h(ShowComponent, { name: active.value }),
          route: { fullPath: active.value, meta: {} },
        });
    },
  };
  const wrappers = new Map();
  const AppMain = {
    render: appRender,
    components: { RouterView },
    setup() {
      return {
        appMainHeight: "100vh",
        isShowTagsView: false,
        cachedListViews: [...pages.keys()],
        currentComponent(component, route) {
          if (!wrappers.has(route.fullPath)) {
            wrappers.set(route.fullPath, { name: route.fullPath, render: () => h(component) });
          }
          return h(wrappers.get(route.fullPath));
        },
      };
    },
  };
  const app = renderer.createApp({
    render() {
      return h("div", [h("nav", active.value), h(AppMain)]);
    },
  });
  app.mount(root);

  const visibleText = (type) =>
    mountedNodes(root)
      .filter((node) => node.type === type)
      .map((node) => node.text);
  try {
    await finishTransitions();
    assert.deepEqual(visibleText("main"), ["数据源管理"]);

    active.value = "接入任务";
    await nextTick();
    // Deliberately do not advance animation frames while the destination is still loading.
    assert.deepEqual(visibleText("nav"), ["接入任务"]);
    assert.deepEqual(visibleText("main"), []);
    assert.ok(visibleText("div").includes("正在初始化应用资源"));
    finishIngestion();
    await finishTransitions();
    assert.deepEqual(visibleText("main"), ["接入任务"]);

    for (const name of ["接入任务", "分发任务", "接入任务"]) {
      active.value = name;
      await finishTransitions();
      assert.deepEqual(visibleText("main"), [name]);
      assert.ok(!visibleText("div").includes("正在初始化应用资源"));
    }
    assert.deepEqual(loaded, ["数据源管理", "接入任务", "分发任务"]);
  } finally {
    app.unmount();
    await finishTransitions();
  }
});
