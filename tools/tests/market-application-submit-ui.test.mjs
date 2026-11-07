import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import test from "node:test";

const requireApp = createRequire(
  new URL("../../data-elements-chengtian/package.json", import.meta.url),
);
const ts = requireApp("typescript");
const vue = requireApp("vue");
const { compileScript, parse } = requireApp("vue/compiler-sfc");
const source = new URL(
  "../../lowcode/02.数据盘点/02.登记流程组件/登记组件(register-modal)/登记订阅申请单(register-apply)/订阅申请单(car-register-apply).vue",
  import.meta.url,
);
const { descriptor, errors } = parse(await readFile(source, "utf8"), {
  filename: "car-register-apply.vue",
});
assert.deepEqual(errors, []);
const { content } = compileScript(descriptor, {
  id: "market-application",
  inlineTemplate: true,
});
const { outputText } = ts.transpileModule(content, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
  },
});
const createComponent = new Function(
  "require",
  "exports",
  "$common",
  "crypto",
  outputText,
);

function node() {
  return { parent: null, children: [] };
}

function remove(child) {
  if (!child.parent) return;
  child.parent.children.splice(child.parent.children.indexOf(child), 1);
  child.parent = null;
}

const renderer = vue.createRenderer({
  createElement: node,
  createText: node,
  createComment: node,
  insert(child, parent, anchor) {
    remove(child);
    parent.children.splice(
      anchor ? parent.children.indexOf(anchor) : parent.children.length,
      0,
      child,
    );
    child.parent = parent;
  },
  remove,
  parentNode: (child) => child.parent,
  nextSibling: (child) =>
    child.parent?.children[child.parent.children.indexOf(child) + 1] ?? null,
  setText() {},
  setElementText() {},
  patchProp() {},
});

test("detail breadcrumb can mount before an authorized detail initializes its tabs", async (t) => {
  const source = new URL(
    "../../lowcode/02.数据盘点/02.登记流程组件/详情页面(detail-page)/0. 详情面包屑(detail-breadcrumb).vue",
    import.meta.url,
  );
  const { descriptor } = parse(await readFile(source, "utf8"));
  const { content } = compileScript(descriptor, { id: "detail-breadcrumb", inlineTemplate: true });
  const { outputText } = ts.transpileModule(content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const module = {};
  const store = vue.reactive({
    config: {},
    history: [],
    currentTab: "",
    findHistory: () => -1,
    initTab() {
      this.currentTab = this.config.tabs[0].title;
    },
  });
  const route = { path: "/market/detail", matched: [], query: { id: "validation", type: "catalog" } };
  const libraries = {
    vue,
    "@/store": { useDetailStore: () => store },
    pinia: {},
    "vue-router": {
      useRoute: () => route,
      useRouter: () => ({}),
      onBeforeRouteUpdate() {},
      onBeforeRouteLeave() {},
    },
  };
  new Function("require", "exports", outputText)((name) => libraries[name], module);
  const app = renderer.createApp(module.default);
  app.component("Breadcrumb", { render: () => vue.h("div") });
  app.mount(node());
  t.after(() => app.unmount());
  assert.equal(store.currentTab, "");
  assert.deepEqual(store.history.map(({ query }) => query.id), ["validation"]);
});

const savePath = "/dws/market/applyForm/saveOrUpdate";
const submitPath = "/dws/market/submit";
const resources = (size) =>
  Array.from({ length: size }, (_, index) => ({
    tid: `validation-catalog-${index + 1}`,
    catalogName: `验证目录 ${index + 1}`,
    resType: ["table"],
  }));
const draftReceipt = ({ orders }) => ({
  items: orders.map((order) => ({
    tid: order.tid ?? order.draftKey,
    catalogIds: order.propList.applyResource,
  })),
});
const submitReceipt = ({ flowOrderIds }) => ({
  items: flowOrderIds.map((flowOrderId) => ({
    flowOrderId,
    flowStatus: 2,
    directGranted: true,
  })),
});

function mountApplication(
  t,
  {
    selected = resources(2),
    initialData = {},
    saveResponse = draftReceipt,
    submitResponse = submitReceipt,
  } = {},
) {
  const store = vue.reactive({ data: initialData, state: { loadStatus: {} } });
  const calls = [];
  const properties = {
    applyName: "验证申请",
    applyBasis: "验证用途",
    appId: "validation-app",
  };
  const handlers = { [savePath]: saveResponse, [submitPath]: submitResponse };
  let cartRefreshes = 0;
  let formProps;
  const libraries = {
    vue,
    "@/store": { useRegisterStore: () => store },
    "@/composables": {
      useCartCount: () => ({
        async refresh() {
          cartRefreshes++;
        },
      }),
    },
  };
  const module = {};
  createComponent(
    (name) => {
      assert.ok(
        Object.hasOwn(libraries, name),
        `unexpected component dependency ${name}`,
      );
      return libraries[name];
    },
    module,
    {
      async post(path, body) {
        assert.ok(Object.hasOwn(handlers, path), `unexpected request ${path}`);
        calls.push({ path, body: structuredClone(body) });
        return handlers[path](body);
      },
    },
    webcrypto,
  );

  const app = renderer.createApp(module.default);
  app.component("ApplyJsonForm", {
    props: { copy: Boolean, initData: Object },
    setup(props, { expose }) {
      formProps = props;
      expose({
        async getFormData() {
          return structuredClone({ ...properties, applyResource: selected });
        },
      });
      return () => vue.h("div");
    },
  });
  const api = app.mount(node());
  let mounted = true;
  const unmount = () => {
    if (!mounted) return;
    mounted = false;
    app.unmount();
  };
  t.after(unmount);
  return {
    api,
    calls,
    properties,
    formProps,
    unmount,
    get cartRefreshes() {
      return cartRefreshes;
    },
  };
}

for (const size of [20, 100]) {
  test(`${size} selected resources use one draft batch and one submission batch`, async (t) => {
    const selected = resources(size);
    const fixture = mountApplication(t, { selected });
    if (size === 20)
      assert.deepEqual(await fixture.api.save(), {
        success: true,
        msg: "申请草稿已保存",
      });
    assert.deepEqual(await fixture.api.commit(), {
      completed: true,
      msg: "申请已直接生效",
    });
    assert.deepEqual(
      fixture.calls.map(({ path }) => path),
      [savePath, submitPath],
    );

    const { orders } = fixture.calls[0].body;
    assert.equal(orders.length, size);
    assert.equal(new Set(orders.map(({ draftKey }) => draftKey)).size, size);
    for (const [index, order] of orders.entries()) {
      assert.equal(order.type, "catalogSubscribe");
      assert.match(order.draftKey, /^[0-9a-f]{32}$/);
      assert.ok(!Object.hasOwn(order, "tid"));
      assert.deepEqual(order.propList, {
        ...fixture.properties,
        applyResource: [selected[index].tid],
      });
    }
    assert.deepEqual(fixture.calls[1].body, {
      flowOrderIds: orders.map(({ draftKey }) => draftKey),
    });
    assert.equal(fixture.cartRefreshes, 1);
  });
}

test("reapplying creates new draft keys while editing retains the historical order ID", async (t) => {
  const originalId = "validation-original-order";
  const copy = mountApplication(t, {
    initialData: { editType: "再次申请", apply: { tid: originalId } },
  });
  assert.equal(copy.formProps.copy, true);
  assert.equal(copy.formProps.initData.tid, originalId);
  await copy.api.save();
  assert.equal(copy.calls[0].body.orders.length, 2);
  for (const order of copy.calls[0].body.orders) {
    assert.match(order.draftKey, /^[0-9a-f]{32}$/);
    assert.ok(!Object.hasOwn(order, "tid"));
  }

  const edit = mountApplication(t, {
    initialData: { apply: { tid: originalId } },
  });
  assert.equal(edit.formProps.copy, false);
  await edit.api.save();
  assert.deepEqual(edit.calls[0].body, {
    orders: [
      {
        tid: originalId,
        type: "catalogSubscribe",
        propList: {
          ...edit.properties,
          applyResource: resources(2).map(({ tid }) => tid),
        },
      },
    ],
  });
  assert.equal(edit.cartRefreshes, 0);
});

test("a network error while saving reuses the original draft keys on retry", async (t) => {
  let requests = 0;
  const fixture = mountApplication(t, {
    saveResponse(body) {
      if (++requests === 1) throw new Error("网络连接失败");
      return draftReceipt(body);
    },
  });
  await assert.rejects(fixture.api.commit(), /网络连接失败/);
  assert.deepEqual(
    fixture.calls.map(({ path }) => path),
    [savePath],
  );
  assert.equal(fixture.cartRefreshes, 0);
  assert.deepEqual(await fixture.api.commit(), {
    completed: true,
    msg: "申请已直接生效",
  });
  assert.deepEqual(
    fixture.calls.map(({ path }) => path),
    [savePath, savePath, submitPath],
  );
  assert.deepEqual(fixture.calls[0].body, fixture.calls[1].body);
  assert.equal(fixture.cartRefreshes, 1);
});

test("incomplete or mismatched draft receipts prevent submission and are never cached", async (t) => {
  const invalidReceipts = [
    [(items) => items.slice(1), /保存结果不完整/],
    [
      (items) => [{ ...items[0], tid: "validation-foreign-order" }, items[1]],
      /保存结果与当前申请不一致/,
    ],
    [
      (items) => [{ ...items[0], catalogIds: items[1].catalogIds }, items[1]],
      /保存结果与当前申请不一致/,
    ],
  ];
  for (const [makeInvalid, expected] of invalidReceipts) {
    let saveCalls = 0;
    const fixture = mountApplication(t, {
      saveResponse(body) {
        const { items } = draftReceipt(body);
        return { items: ++saveCalls === 1 ? makeInvalid(items) : items };
      },
    });
    await assert.rejects(fixture.api.commit(), expected);
    assert.deepEqual(
      fixture.calls.map(({ path }) => path),
      [savePath],
    );
    assert.equal(fixture.cartRefreshes, 0);
    assert.equal((await fixture.api.commit()).completed, true);
    assert.deepEqual(
      fixture.calls.map(({ path }) => path),
      [savePath, savePath, submitPath],
    );
    assert.deepEqual(fixture.calls[0].body, fixture.calls[1].body);
  }
});

test("failed or malformed submissions reuse the validated draft IDs until every item is granted", async (t) => {
  let requests = 0;
  const fixture = mountApplication(t, {
    submitResponse(body) {
      const { items } = submitReceipt(body);
      switch (++requests) {
        case 1:
          throw new Error("提交响应丢失");
        case 2:
          return { items: items.slice(1) };
        case 3:
          return { items: [items[0], items[0]] };
        case 4:
          return {
            items: [
              items[0],
              { ...items[1], flowStatus: 1, directGranted: false },
            ],
          };
        default:
          return { items: items.reverse() };
      }
    },
  });
  for (const expected of [
    /提交响应丢失/,
    /提交结果不完整/,
    /申请尚未全部生效/,
    /申请尚未全部生效/,
  ]) {
    await assert.rejects(fixture.api.commit(), expected);
  }
  assert.deepEqual(await fixture.api.commit(), {
    completed: true,
    msg: "申请已直接生效",
  });
  assert.deepEqual(
    fixture.calls.map(({ path }) => path),
    [savePath, ...Array(5).fill(submitPath)],
  );
  const ids = fixture.calls[0].body.orders.map(({ draftKey }) => draftKey);
  for (const { body } of fixture.calls.slice(1))
    assert.deepEqual(body, { flowOrderIds: ids });
  assert.equal(fixture.cartRefreshes, 1);
});

test("closing while saving lets the draft finish without submitting the closed application", async (t) => {
  let finishSave;
  const fixture = mountApplication(t, {
    saveResponse(body) {
      return new Promise((resolve) => {
        finishSave = () => resolve(draftReceipt(body));
      });
    },
  });
  const pending = fixture.api.commit();
  await new Promise(setImmediate);
  assert.deepEqual(
    fixture.calls.map(({ path }) => path),
    [savePath],
  );
  fixture.unmount();
  finishSave();
  assert.equal(await pending, undefined);
  assert.deepEqual(
    fixture.calls.map(({ path }) => path),
    [savePath],
  );
  assert.equal(fixture.cartRefreshes, 1);
});

test("oversized, missing, duplicate and unavailable selections are rejected before any write", async (t) => {
  const [one] = resources(1);
  const cases = [
    [resources(101), /一次最多申请 100 个数据目录/],
    [[], /请选择申请资源/],
    [[one, one], /申请资源中有重复的数据目录/],
    [[{ ...one, unavailable: true }], /申请包含不可用的数据目录/],
  ];
  for (const [selected, expected] of cases) {
    const fixture = mountApplication(t, { selected });
    await assert.rejects(fixture.api.commit(), expected);
    assert.deepEqual(fixture.calls, []);
  }
});
