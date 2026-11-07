import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import ts from "typescript";
import * as vue from "vue";
import * as pinia from "pinia";
import { get, set } from "lodash-es";
import { compileScript, parse } from "vue/compiler-sfc";
import { renderToString } from "vue/server-renderer";

const directory = new URL("../src/components/business/", import.meta.url);
const sharedDirectory = new URL("../../lowcode/02.数据盘点/02.登记流程组件/", import.meta.url);
const nativeComponents = ["register-modal", "asset-register", "register-body", "register-action"];
const sharedComponents = {
  "register-modal": "登记组件(register-modal).vue",
  "asset-register": "资产登记组件(asset-register).vue",
  "register-body": "资产登记组件(asset-register)/登记中间件(register-body).vue",
  "register-action": "资产登记组件(asset-register)/登记按钮组件(register-action).vue",
};

function transpile(content) {
  return ts.transpileModule(content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
}

async function compileComponent(name, url) {
  const filename = name + ".vue";
  const source = await readFile(url, "utf8");
  const { descriptor, errors } = parse(source, { filename });
  assert.deepEqual(errors, []);
  const { content } = compileScript(descriptor, { id: name, inlineTemplate: true });
  return transpile(content);
}

const compiled = new Map(
  await Promise.all(
    nativeComponents.map(async (name) => [
      name,
      await compileComponent(name, new URL(name + ".vue", directory)),
    ])
  )
);
const sharedCompiled = new Map(
  await Promise.all(
    Object.entries(sharedComponents).map(async ([name, path]) => [
      name,
      await compileComponent(name, new URL(path, sharedDirectory)),
    ])
  )
);
const registrationFlow = {};
new Function(
  "exports",
  transpile(await readFile(new URL("registration-flow.ts", directory), "utf8"))
)(registrationFlow);
const { createRegistrationFlow } = registrationFlow;
const registrationActions = {};
new Function(
  "exports",
  transpile(await readFile(new URL("registration-actions.ts", directory), "utf8"))
)(registrationActions);
const storeModule = transpile(
  await readFile(new URL("../src/store/modules/register-store.ts", import.meta.url), "utf8")
);

function componentLoader(components, libraries, observeInitStore = () => {}, globals = {}) {
  function load(name) {
    const module = { exports: {} };
    const require = (path) => {
      if (path.startsWith("./") && path.endsWith(".vue")) {
        return { default: load(path.slice(2, -4)) };
      }
      assert.ok(Object.hasOwn(libraries, path), `unexpected component dependency ${path}`);
      return libraries[path];
    };
    new Function("require", "exports", ...Object.keys(globals), components.get(name))(
      require,
      module.exports,
      ...Object.values(globals)
    );
    const component = module.exports.default;
    if (name !== "asset-register") return component;
    return {
      ...component,
      setup(props, context) {
        observeInitStore(props.initStore);
        return component.setup(props, context);
      },
    };
  }
  return load;
}

function registerUi(app, { retainClosingContent = false } = {}) {
  for (const name of ["ElScrollbar", "ElSkeleton", "Icon"]) {
    app.component(name, {
      render() {
        return vue.h("div", this.$slots.default?.());
      },
    });
  }
  app.component("ElDialog", {
    props: ["modelValue"],
    render() {
      // Element Plus keeps its slot mounted until the leave transition ends.
      return this.modelValue || retainClosingContent ? vue.h("div", this.$slots.default?.()) : null;
    },
  });
  app.component("ElButton", {
    render() {
      return vue.h("button", this.$slots.default?.());
    },
  });
  app.component("Steps", {
    props: ["items", "onChange"],
    render() {
      return vue.h(
        "div",
        { "data-testid": "registration-steps" },
        this.items.map(({ title, clickable }, index) =>
          vue.h(
            "span",
            { "data-clickable": Boolean(clickable), onClick: () => this.onChange?.(index) },
            title
          )
        )
      );
    },
  });
  app.component("LoadingState", { render: () => vue.h("div", "正在加载登记页面") });
  app.directive("loading", {});
}

async function renderRegistration(registerClass, currentStep, component, ready = true, props = {}) {
  const store = vue.reactive({
    title: "",
    actionRef: null,
    state: { steps: [], currentStep, loadStatus: {}, hiddenStep: false, showAction: true },
    currentComponent: component,
    currentProps: {},
    isFirstStep: currentStep === 0,
    isLastStep: false,
    isSecondLastStep: true,
    loadStatus: {},
    $reset() {},
  });
  const expectedRuntimeNames = [];
  const requests = [];
  const libraries = {
    vue,
    pinia: { storeToRefs: vue.toRefs },
    "lodash-es": { set() {} },
    "./registration-flow": registrationFlow,
    "./registration-actions": registrationActions,
    "@/store": { useRegisterStore: () => store },
    "@/plugins/compiler/runtime-sfc": {
      hasRuntimeSfc(_app, name) {
        expectedRuntimeNames.push(name);
        return ready;
      },
      loadAllRuntimeSfc(_app, options) {
        requests.push(options);
        return new Promise(() => {});
      },
    },
  };
  let initStore;
  const load = componentLoader(compiled, libraries, (value) => {
    initStore = value;
    store.state.steps = value.steps;
    store.state.currentStep = value.currentStep;
    store.state.hiddenStep = value.hiddenStep ?? false;
    store.title = value.title;
    store.isFirstStep = value.currentStep === 0;
    store.isLastStep = value.currentStep === value.steps.length - 1;
    store.isSecondLastStep = value.steps.length > 1 && value.currentStep === value.steps.length - 2;
  });
  const root = load("register-modal");
  const app = vue.createSSRApp({
    render: () => vue.h(root, { modelValue: true, registerClass, currentStep, ...props }),
  });
  for (const name of ["AssetRegister", "RegisterBody", "RegisterAction"]) {
    app.component(name, { render: () => vue.h("div", "全局同名组件被错误使用") });
  }
  registerUi(app);
  app.component(component, { render: () => vue.h("div", "动态业务步骤") });

  return { html: await renderToString(app), initStore, expectedRuntimeNames, requests };
}

test("the compiled registration shell bypasses duplicate global hosts but renders dynamic steps", async () => {
  const dataSource = await renderRegistration("db", 4, "register-confirm");
  assert.match(dataSource.html, /register-container/);
  assert.match(dataSource.html, /动态业务步骤/);
  assert.match(dataSource.html, /结束登记/);
  assert.doesNotMatch(dataSource.html, /全局同名组件被错误使用/);
  assert.deepEqual(
    [...new Set(dataSource.expectedRuntimeNames)],
    [
      "register-db",
      "register-dbTable",
      "register-dbTable-catalog",
      "register-confirm",
      "register-finish",
    ]
  );
  assert.deepEqual(dataSource.requests, []);
});

test("the registration host remains behind runtime-step loading when components are missing", async () => {
  const { html, requests } = await renderRegistration("db", 0, "register-db", false);
  assert.match(html, /正在加载登记页面/);
  assert.doesNotMatch(html, /动态业务步骤|全局同名组件被错误使用/);
  assert.deepEqual(requests, [{ force: false }]);
});

test("the native explore-edit entrance keeps five visible steps and the editing title", async () => {
  const registration = await renderRegistration("db", 0, "register-db", true, {
    registerMode: "explore-edit",
  });
  assert.match(registration.html, /编辑数据源/);
  assert.match(registration.html, /data-testid="registration-steps"/);
  assert.deepEqual(
    registration.initStore.steps.filter((step) => !step.hidden).map(({ title }) => title),
    ["登记数据源", "探查数据表", "登记字典表", "登记业务/日志表", "登记内容审查"]
  );
  assert.match(registration.html, /下一步/);
  assert.doesNotMatch(registration.html, /保存并完成/);
});

function viewNode(type, text = "") {
  return { type, text, parent: null, children: [], props: {}, style: {} };
}

function removeViewNode(child) {
  if (!child.parent) return;
  child.parent.children.splice(child.parent.children.indexOf(child), 1);
  child.parent = null;
}

const renderer = vue.createRenderer({
  createElement: viewNode,
  createText: (text) => viewNode("#text", text),
  createComment: () => viewNode("#comment"),
  setText: (child, text) => (child.text = text),
  setElementText(child, text) {
    child.children = [];
    child.text = text;
  },
  patchProp: (child, key, _oldValue, value) => (child.props[key] = value),
  parentNode: (child) => child.parent,
  nextSibling: (child) => child.parent?.children[child.parent.children.indexOf(child) + 1] ?? null,
  insert(child, parent, anchor) {
    removeViewNode(child);
    parent.children.splice(
      anchor ? parent.children.indexOf(anchor) : parent.children.length,
      0,
      child
    );
    child.parent = parent;
  },
  remove: removeViewNode,
});

function viewDescendants(node) {
  return [node, ...node.children.flatMap(viewDescendants)];
}

function createTestRegisterStore() {
  const libraries = { pinia, "lodash-es": { get } };
  const exports = {};
  new Function(
    "require",
    "exports",
    "ref",
    "computed",
    "toRefs",
    "useSessionStorage",
    "sessionStorage",
    storeModule
  )(
    (name) => libraries[name],
    exports,
    vue.ref,
    vue.computed,
    vue.toRefs,
    (_key, initial) => vue.ref(initial),
    { removeItem() {} }
  );
  return exports.useRegisterStore(pinia.createPinia());
}

async function mountRegistration(hostType, props, stepComponents = {}, uiOptions = {}) {
  const native = hostType === "native";
  const store = createTestRegisterStore();
  const activeProps = vue.shallowRef(props);
  const open = vue.ref(true);
  const warnings = [];
  const successes = [];
  const scheduled = new Map();
  let timerId = 0;
  let completions = 0;
  const window = {
    setTimeout(callback) {
      const id = ++timerId;
      scheduled.set(id, callback);
      return id;
    },
    clearTimeout(id) {
      scheduled.delete(id);
    },
  };
  const libraries = {
    vue,
    pinia,
    "lodash-es": { set },
    "@/store": { useRegisterStore: () => store },
    "./registration-flow": registrationFlow,
    "./registration-actions": registrationActions,
    "@/plugins/compiler/runtime-sfc": {
      hasRuntimeSfc: () => true,
      loadAllRuntimeSfc() {
        assert.fail("mounted registration steps should already be registered");
      },
    },
  };
  const load = componentLoader(native ? compiled : sharedCompiled, libraries, undefined, {
    window,
    $message: {
      warning: (message) => warnings.push(message),
      success: (message) => successes.push(message),
    },
    console: { ...console, error() {}, log() {} },
  });
  const host = load("register-modal");
  const app = renderer.createApp({
    render: () =>
      vue.h(host, {
        ...activeProps.value,
        modelValue: open.value,
        "onUpdate:modelValue": (value) => {
          open.value = value;
        },
        onCompleted: () => {
          completions++;
        },
        onClose: () => {
          open.value = false;
        },
      }),
  });
  registerUi(app, uiOptions);
  if (!native) {
    for (const name of ["asset-register", "register-body", "register-action"]) {
      app.component(name, load(name));
    }
  }
  const steps = [
    "register-db",
    "register-dbTable",
    "register-dbTable-catalog",
    "register-confirm",
    "register-finish",
    "car-list",
    "car-register-apply",
  ];
  for (const name of steps) {
    app.component(name, stepComponents[name] ?? { render: () => vue.h("div", name) });
  }
  const root = viewNode("root");
  app.mount(root);
  await vue.nextTick();
  return {
    store,
    open,
    warnings,
    successes,
    get completions() {
      return completions;
    },
    get content() {
      return viewDescendants(root)
        .map(({ text }) => text)
        .join(" ");
    },
    get showsSteps() {
      return viewDescendants(root).some(
        (node) => node.props["data-testid"] === "registration-steps"
      );
    },
    button(label) {
      return viewDescendants(root).find(
        (node) =>
          node.type === "button" &&
          viewDescendants(node)
            .map(({ text }) => text)
            .join("")
            .trim() === label
      );
    },
    step(label) {
      return viewDescendants(root).find(
        (node) => node.type === "span" && node.text === label && "data-clickable" in node.props
      );
    },
    close() {
      const back = viewDescendants(root).find(
        (node) => node.type === "button" && node.props.title === "返回"
      );
      assert.ok(back);
      back.props.onClick();
    },
    reopen(nextProps) {
      activeProps.value = nextProps;
      open.value = true;
    },
    async settleAction() {
      await new Promise(setImmediate);
      for (const [id, callback] of [...scheduled]) {
        if (scheduled.delete(id)) callback();
      }
      await vue.nextTick();
    },
    unmount: () => app.unmount(),
  };
}

test("the mounted shared host keeps one-step explore editing and honors the table registration entry step", async (t) => {
  const quick = await mountRegistration("lowcode", {
    registerClass: "db",
    registerMode: "explore-edit",
  });
  t.after(quick.unmount);
  assert.deepEqual(
    quick.store.state.steps.map(({ component }) => component),
    ["register-db"]
  );
  assert.match(quick.content, /编辑数据源.*register-db/s);
  assert.equal(quick.showsSteps, false);

  const tableRegistration = await mountRegistration("lowcode", {
    registerClass: "dbTable",
    currentStep: 1,
    registerData: { currentStep: 3 },
  });
  t.after(tableRegistration.unmount);
  assert.equal(tableRegistration.store.state.currentStep, 3);
  assert.deepEqual(tableRegistration.store.currentProps, { catalogPhase: "business" });
  assert.equal(tableRegistration.showsSteps, true);
  assert.match(tableRegistration.content, /register-dbTable-catalog/);
  assert.ok(tableRegistration.button("保存"));
  assert.ok(tableRegistration.button("下一步"));
});

test("the shared one-step editor keeps failed edits open and completes with one commit per attempt", async (t) => {
  const commits = [];
  let finishes = 0;
  const registerDb = {
    setup(_props, { expose }) {
      expose({
        commit() {
          return new Promise((resolve, reject) => commits.push({ resolve, reject }));
        },
        finish() {
          finishes++;
        },
      });
      return () => vue.h("div", "register-db");
    },
  };
  const editor = await mountRegistration(
    "lowcode",
    { registerClass: "db", registerMode: "explore-edit" },
    { "register-db": registerDb }
  );
  t.after(editor.unmount);

  assert.ok(editor.button("取消"));
  const save = editor.button("保存并完成");
  assert.ok(save);
  save.props.onClick();
  save.props.onClick();
  await editor.settleAction();
  assert.equal(commits.length, 1, JSON.stringify(editor.warnings));
  assert.equal(editor.button("保存并完成").props.disabled, true);
  commits[0].reject(new Error("校验失败，请修正后重试"));
  await editor.settleAction();
  assert.equal(editor.open.value, true);
  assert.equal(editor.completions, 0);
  assert.deepEqual(editor.warnings, ["校验失败，请修正后重试"]);

  const retry = editor.button("保存并完成");
  assert.ok(retry);
  assert.equal(retry.props.disabled, false);
  retry.props.onClick();
  await editor.settleAction();
  assert.equal(commits.length, 2);
  commits[1].resolve([{ tid: "validation-source" }]);
  await editor.settleAction();
  assert.equal(editor.open.value, false);
  assert.equal(editor.completions, 1);
  assert.equal(commits.length, 2);
  assert.equal(finishes, 0);
});

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((finish, fail) => {
    resolve = finish;
    reject = fail;
  });
  return { promise, resolve, reject };
}

function stubStep(name, methods) {
  return {
    setup(_props, { expose }) {
      expose(methods);
      return () => vue.h("div", name);
    },
  };
}

for (const host of ["native", "lowcode"]) {
  const finishLabel = host === "native" ? "结束登记" : "完成登记";

  for (const registerClass of ["apply", "car-apply"]) {
    test(`${host} ${registerClass} completes only after an explicit successful commit`, async (t) => {
      const attempts = [];
      const form = stubStep("car-register-apply", {
        commit() {
          const request = deferred();
          attempts.push(request);
          return request.promise;
        },
      });
      const registration = await mountRegistration(host, { registerClass }, {
        "car-list": stubStep("car-list", { next() {} }),
        "car-register-apply": form,
      });
      t.after(registration.unmount);

      const cart = registerClass === "car-apply";
      const step = cart ? 1 : 0;
      assert.deepEqual(
        registration.store.state.steps.map(({ component }) => component),
        cart ? ["car-list", "car-register-apply"] : ["car-register-apply"]
      );
      if (cart) {
        assert.equal(registration.button("提交申请"), undefined);
        registration.button("下一步").props.onClick();
        await registration.settleAction();
        assert.ok(registration.button("打印"));
        assert.ok(registration.button("上一步"));
      }
      assert.equal(registration.button("下一步"), undefined);
      assert.ok(registration.button("保存"));
      const submit = registration.button("提交申请");
      assert.ok(submit);
      submit.props.onClick();
      submit.props.onClick();
      await registration.settleAction();
      assert.equal(attempts.length, 1);
      assert.equal(submit.props.loading, true);
      attempts[0].reject(new Error("有申请未提交，请重试"));
      await registration.settleAction();
      assert.deepEqual(registration.warnings, ["有申请未提交，请重试"]);
      assert.equal(registration.open.value, true);
      assert.equal(registration.store.state.currentStep, step);
      assert.equal(submit.props.disabled, false);

      submit.props.onClick();
      await registration.settleAction();
      assert.equal(attempts.length, 2);
      attempts[1].resolve({ completed: false, msg: "有申请未生效" });
      await registration.settleAction();
      assert.equal(registration.open.value, true);
      assert.equal(registration.store.state.currentStep, step);
      assert.equal(registration.completions, 0);
      assert.deepEqual(registration.successes, []);

      submit.props.onClick();
      await registration.settleAction();
      assert.equal(attempts.length, 3);
      attempts[2].resolve({ completed: true, msg: "申请已直接生效" });
      await registration.settleAction();
      assert.equal(registration.open.value, false);
      assert.equal(registration.completions, 1);
      assert.deepEqual(registration.successes, ["申请已直接生效"]);
    });
  }

  test(`${host} ignores explicit application completion from a closed form`, async (t) => {
    const pending = deferred();
    const registration = await mountRegistration(
      host,
      { registerClass: "apply", registerData: { tid: "validation-old" } },
      { "car-register-apply": stubStep("car-register-apply", { commit: () => pending.promise }) },
      { retainClosingContent: true }
    );
    t.after(registration.unmount);
    registration.button("提交申请").props.onClick();
    await registration.settleAction();
    assert.equal(registration.store.state.loadStatus.commit, true);
    registration.close();
    await registration.settleAction();
    registration.reopen({ registerClass: "apply", registerData: { tid: "validation-new" } });
    await registration.settleAction();
    pending.resolve({ completed: true, msg: "旧申请已生效" });
    await registration.settleAction();
    assert.equal(registration.store.data.tid, "validation-new");
    assert.equal(registration.open.value, true);
    assert.equal(registration.completions, 0);
    assert.deepEqual(registration.successes, []);
  });

  test(`${host} finish stays locked across one preflight and commits the same payload once`, async (t) => {
    const preflight = deferred();
    const write = deferred();
    const payload = [{ tid: "validation-source", assetType: "db" }];
    let preflights = 0;
    const commits = [];
    const review = stubStep("register-confirm", {
      finish() {
        preflights++;
        return preflight.promise;
      },
      commit(items) {
        commits.push(items);
        return write.promise;
      },
    });
    const registration = await mountRegistration(
      host,
      { registerClass: "db", currentStep: 4 },
      { "register-confirm": review }
    );
    t.after(registration.unmount);

    const finish = registration.button(finishLabel);
    const previous = registration.button("上一步");
    assert.ok(finish);
    assert.ok(previous);
    if (host === "native")
      assert.equal(registration.step("登记数据源").props["data-clickable"], true);
    finish.props.onClick();
    finish.props.onClick();
    previous.props.onClick();
    await registration.settleAction();

    assert.equal(preflights, 1);
    assert.deepEqual(commits, []);
    assert.equal(finish.props.loading, true);
    assert.equal(previous.props.disabled, true);
    if (host === "native") {
      const completedStep = registration.step("登记数据源");
      assert.equal(completedStep.props["data-clickable"], false);
      completedStep.props.onClick();
    }
    assert.equal(registration.store.state.currentStep, 4);

    preflight.resolve(payload);
    await registration.settleAction();
    assert.equal(commits.length, 1);
    assert.strictEqual(commits[0], payload);
    assert.equal(finish.props.loading, true);
    assert.equal(registration.completions, 0);
    finish.props.onClick();
    write.resolve(payload);
    await registration.settleAction();
    assert.equal(preflights, 1);
    assert.equal(commits.length, 1);
    assert.equal(registration.open.value, false);
    assert.equal(registration.completions, 1);
    assert.deepEqual(registration.warnings, []);
  });

  test(`${host} next honors navigation takeover and reports only unhandled failures`, async (t) => {
    const calls = [];
    const first = stubStep("register-db", {
      next() {
        const request = deferred();
        calls.push(request);
        return request.promise;
      },
    });
    const registration = await mountRegistration(
      host,
      { registerClass: "db" },
      { "register-db": first }
    );
    t.after(registration.unmount);
    const next = registration.button("下一步");
    assert.ok(next);
    const outcomes = [
      { resolve: { navigationHandled: true } },
      { reject: Object.assign(new Error("已展示的接口错误"), { handled: true }) },
      { reject: new Error("当前步骤校验失败") },
    ];
    for (const [index, outcome] of outcomes.entries()) {
      next.props.onClick();
      await registration.settleAction();
      assert.equal(calls.length, index + 1);
      if ("reject" in outcome) calls[index].reject(outcome.reject);
      else calls[index].resolve(outcome.resolve);
      await registration.settleAction();
      assert.equal(registration.open.value, true);
      assert.equal(registration.store.state.currentStep, 0);
      assert.equal(next.props.disabled, false);
      assert.deepEqual(registration.warnings, index === 2 ? ["当前步骤校验失败"] : []);
    }

    next.props.onClick();
    await registration.settleAction();
    assert.equal(calls.length, 4);
    calls[3].resolve();
    await registration.settleAction();
    assert.equal(registration.store.state.currentStep, 1);
    assert.match(registration.content, /register-dbTable/);
    assert.equal(registration.completions, 0);
  });

  for (const phase of ["closing", "reopened success", "reopened failure"]) {
    test(`${host} ignores late preflight during ${phase}`, async (t) => {
      const requests = [];
      const commits = [];
      const review = stubStep("register-confirm", {
        finish() {
          const request = deferred();
          requests.push(request);
          return request.promise;
        },
        commit(items) {
          commits.push(items);
        },
      });
      const props = (tid) => ({ registerClass: "db", registerData: { currentStep: 4, tid } });
      const registration = await mountRegistration(
        host,
        props("validation-source-a"),
        { "register-confirm": review },
        { retainClosingContent: true }
      );
      t.after(registration.unmount);

      const previousButton = registration.button(finishLabel);
      assert.ok(previousButton);
      previousButton.props.onClick();
      await registration.settleAction();
      assert.equal(requests.length, 1);
      assert.equal(registration.store.state.loadStatus.finish, true);
      const previousStatus = registration.store.state.loadStatus;
      registration.close();
      await registration.settleAction();
      assert.equal(registration.open.value, false);
      const previousPayload = [{ tid: "validation-source-a" }];
      if (phase === "closing") {
        requests[0].resolve(previousPayload);
        await registration.settleAction();
        assert.equal(registration.completions, 0);
        assert.deepEqual(commits, []);
        assert.deepEqual(registration.successes, []);
      }

      registration.reopen(props("validation-source-b"));
      await registration.settleAction();
      assert.equal(registration.store.data.tid, "validation-source-b");
      assert.notStrictEqual(registration.store.state.loadStatus, previousStatus);
      const currentButton = registration.button(finishLabel);
      assert.ok(currentButton);
      currentButton.props.onClick();
      await registration.settleAction();
      assert.equal(requests.length, 2);
      assert.equal(currentButton.props.loading, true);

      if (phase === "reopened success") requests[0].resolve(previousPayload);
      if (phase === "reopened failure") requests[0].reject(new Error("旧登记请求失败"));
      await registration.settleAction();
      assert.equal(registration.open.value, true);
      assert.equal(registration.store.data.tid, "validation-source-b");
      assert.equal(registration.store.state.currentStep, 4);
      assert.equal(registration.store.state.loadStatus.finish, true);
      assert.equal(currentButton.props.loading, true);
      assert.equal(registration.completions, 0);
      assert.deepEqual(commits, []);
      assert.deepEqual(registration.warnings, []);
      assert.deepEqual(registration.successes, []);

      const currentPayload = [{ tid: "validation-source-b" }];
      requests[1].resolve(currentPayload);
      await registration.settleAction();
      assert.deepEqual(commits, [currentPayload]);
      assert.strictEqual(commits[0], currentPayload);
      assert.equal(registration.open.value, false);
      assert.equal(registration.completions, 1);
    });
  }
}

function visibleActions({ initStore, actions }, currentStep) {
  const context = {
    state: { currentStep, steps: initStore.steps },
    isFirstStep: currentStep === 0,
    isSecondLastStep: currentStep === initStore.steps.length - 2,
  };
  return actions.filter((action) => !action.if || action.if(context)).map(({ code }) => code);
}

test("canonical buttons follow each workflow and keep host-specific completion labels", () => {
  const datasource = [
    ["save", "next"],
    ["pre", "next"],
    ["pre", "save", "next"],
    ["pre", "save", "next"],
    ["pre", "finish"],
  ];
  const matrix = {
    db: datasource,
    dbTable: datasource,
    app: [["save", "finish"]],
    apply: [["cancel", "save", "commit"]],
    "car-apply": [
      ["cancel", "next"],
      ["cancel", "pre", "print", "save", "commit"],
    ],
  };
  for (const host of ["native", "lowcode"]) {
    for (const [registerClass, expected] of Object.entries(matrix)) {
      const flow = createRegistrationFlow({ registerClass }, host);
      assert.deepEqual(
        expected.map((_, step) => visibleActions(flow, step)),
        expected,
        `${host}/${registerClass}`
      );
      assert.equal(flow.initStore.hiddenStep, ["apply", "car-apply"].includes(registerClass));
    }
    const flow = createRegistrationFlow({ registerClass: "db" }, host);
    assert.equal(
      flow.actions.find(({ code }) => code === "finish").name,
      host === "native" ? "结束登记" : "完成登记"
    );
    assert.equal(flow.initStore.steps.at(-1).hidden, true);
  }
  const quick = createRegistrationFlow(
    { registerClass: "db", registerMode: "explore-edit" },
    "lowcode"
  );
  assert.deepEqual(visibleActions(quick, 0), ["cancel", "finish"]);
  assert.equal(quick.actions.at(-1).name, "保存并完成");
});

test("canonical flow resolves initial steps and leaves directory actions to the existing defaults", () => {
  for (const host of ["native", "lowcode"]) {
    assert.equal(
      createRegistrationFlow(
        { registerClass: "db", currentStep: 4, registerData: { currentStep: 0 } },
        host
      ).initStore.currentStep,
      0
    );
    assert.equal(
      createRegistrationFlow(
        { registerClass: "db", currentStep: 4, registerData: { currentStep: null } },
        host
      ).initStore.currentStep,
      4
    );
    assert.equal(createRegistrationFlow({ registerClass: "db" }, host).initStore.currentStep, 0);
    for (const registerClass of ["catalog", "fileCatalog"]) {
      const flow = createRegistrationFlow({ registerClass }, host);
      assert.equal(flow.actions, undefined);
      assert.equal(flow.initStore.hiddenStep, false);
      assert.deepEqual(flow.initStore.assets, [{ assetClass: registerClass }]);
      assert.deepEqual(flow.initStore.updateFormRule, []);
    }
  }
});

test("the native host renders the existing fallback buttons for both directory flows", async () => {
  const catalog = await renderRegistration("catalog", 0, "register-catalog");
  assert.match(catalog.html, /取消.*保存.*完成登记/s);
  assert.doesNotMatch(catalog.html, /下一步|结束登记/);

  const fileCatalog = await renderRegistration("fileCatalog", 0, "register-fileCatalog");
  assert.match(fileCatalog.html, /取消.*保存.*下一步/s);
  assert.doesNotMatch(fileCatalog.html, /完成登记|结束登记/);

  const completed = await renderRegistration("catalog", 1, "register-finish");
  assert.match(completed.html, /动态业务步骤/);
  assert.doesNotMatch(completed.html, /class="action-btns"/);
});

test("new flows isolate skipped steps, nested step props, default rules, and button props", () => {
  const first = createRegistrationFlow({ registerClass: "db" }, "native");
  first.initStore.steps[1].skipped = true;
  first.initStore.steps[2].props.catalogPhase = "changed";
  first.initStore.assets[0].assetClass = "changed";
  first.initStore.updateFormRule.push({ field: "changed" });
  first.actions[0].props.type = "primary";
  for (const host of ["native", "lowcode"]) {
    const next = createRegistrationFlow({ registerClass: "db" }, host);
    assert.equal(next.initStore.steps[1].skipped, undefined);
    assert.equal(next.initStore.steps[2].props.catalogPhase, "dictionary");
    assert.equal(next.initStore.assets[0].assetClass, "db");
    assert.deepEqual(next.initStore.updateFormRule, []);
    assert.equal(next.actions[0].props.type, "default");
  }
});
