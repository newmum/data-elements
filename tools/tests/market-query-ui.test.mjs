import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import test from "node:test";

const requireApp = createRequire(new URL("../../data-elements-chengtian/package.json", import.meta.url));
const vue = requireApp("vue");
const ts = requireApp("typescript");
const { compileScript, parse } = requireApp("vue/compiler-sfc");
async function compileMarketComponent(file, id) {
  const source = new URL(`../../lowcode/06.数据服务/02.数据超市/${file}`, import.meta.url);
  const { descriptor, errors } = parse(await readFile(source, "utf8"), { filename: `${id}.vue` });
  assert.deepEqual(errors, []);
  const { content } = compileScript(descriptor, { id, inlineTemplate: true });
  const { outputText } = ts.transpileModule(content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  return new Function("require", "exports", "$common", "$message", outputText);
}

const createComponent = await compileMarketComponent("超市列表页(market).vue", "market-query-test");
const createDetail = await compileMarketComponent("超市详情(market-detail).vue", "market-detail-test");

function node(type) {
  return { type, parent: null, children: [], props: {}, style: {} };
}

function remove(child) {
  if (!child.parent) return;
  child.parent.children.splice(child.parent.children.indexOf(child), 1);
  child.parent = null;
}

const renderer = vue.createRenderer({
  createElement: node,
  createText: () => node("#text"),
  createComment: () => node("#comment"),
  insert(child, parent, anchor) {
    remove(child);
    parent.children.splice(anchor ? parent.children.indexOf(anchor) : parent.children.length, 0, child);
    child.parent = parent;
  },
  remove,
  parentNode: (child) => child.parent,
  nextSibling: (child) => child.parent?.children[child.parent.children.indexOf(child) + 1] ?? null,
  setText() {},
  setElementText() {},
  patchProp: (element, key, _, value) => { element.props[key] = value; },
});

const settle = () => new Promise((resolve) => setImmediate(resolve));
const listPath = "/dws/market/list";
const countPath = "/dws/market/count";
const appPath = "/sym/dict";
const collectPath = "/dws/market/collect";
const addPath = "/dws/market/shoppingCard";
const detailPath = "/dws/market/detail";
const viewPath = "/dws/market/view";
const org = { id: "org-1", value: "org-1", label: "部门一", serialNumber: "00010001" };
const row = (tid, fields = {}) => ({ tid, catalogName: tid, assetType: "catalog", ...fields });

function mount(t) {
  const calls = [];
  const messages = [];
  const view = { buttons: new Map() };
  const revision = vue.ref(1);
  const visible = vue.ref(true);
  const completedVersion = vue.ref(0);
  let cartReads = 0;
  const exported = {};
  const libraries = {
    vue,
    "@/store": { useDictStore: () => ({ getDictItems: (code) => code === "org" ? [{ children: [org] }, { children: [] }] : [] }) },
    "vue-router": { useRouter: () => ({ push() {} }) },
    "@/composables": {
      useCartCount: () => ({ sessionRevision: revision, refresh: async () => { cartReads += 1; } }),
      useRegisterModal: () => ({ completedVersion }),
    },
  };
  const request = (path, body) => new Promise((resolve, reject) => {
    calls.push({ path, body: JSON.parse(JSON.stringify(body)), resolve, reject });
  });
  createComponent(
    (name) => libraries[name],
    exported,
    { getSilently: request, postSilently: request, post: request },
    { success: (message) => messages.push(message), warning: (message) => messages.push(message) },
  );

  const component = (render) => vue.defineComponent({
    inheritAttrs: false,
    setup(_, { attrs, slots }) { return () => render(attrs, slots); },
  });
  const app = renderer.createApp({
    render: () => vue.h(vue.KeepAlive, null, { default: () => visible.value ? [vue.h(exported.default)] : [] }),
  });
  for (const name of ["Icon", "UTreeNode"]) app.component(name, component(() => vue.h("span")));
  for (const [name, key] of [["ElInput", "input"], ["ElPagination", "pagination"], ["CheckCard", "filters"], ["MarketList", "list"]]) {
    app.component(name, component((attrs) => {
      view[key] = { ...attrs };
      return vue.h("div");
    }));
  }
  app.component("UTree", component((attrs) => {
    if (attrs.data.length) view.tree = { ...attrs };
    return vue.h("div");
  }));
  app.component("ElButton", component((attrs, slots) => {
    const label = slots.default?.().map((child) => child.children).join("").trim();
    view.buttons.set(label, attrs.onClick);
    return vue.h("button");
  }));
  const root = node("root");
  app.mount(root);
  t.after(() => app.unmount());
  return {
    view, requests: (path) => calls.filter((call) => call.path === path),
    messages, revision, visible, cartReads: () => cartReads,
  };
}

test("market queries once on KeepAlive mount, then uses structured filters and one read per page change", async (t) => {
  const { view, requests } = mount(t);
  await settle();
  assert.equal(requests(listPath).length, 1);
  assert.deepEqual(requests(listPath)[0].body, {
    pageNum: 1, pageSize: 20, keyword: "", sortField: "createdTime", sortDir: "desc",
  });
  assert.equal(requests(countPath).length, 1);
  assert.deepEqual(requests(countPath)[0].body, {});
  assert.deepEqual(requests(appPath)[0].body, { code: "app" });
  requests(appPath)[0].resolve([
    { label: "系统一", value: "app-1", orgId: "org-1" },
    { label: "系统二", value: "app-2", orgId: "org-2" },
  ]);
  requests(countPath)[0].resolve({ orgPath: { [org.serialNumber]: 200 } });
  requests(listPath)[0].resolve({ list: [row("first")], total: 200 });
  await settle();

  view.pagination["onUpdate:currentPage"](2);
  await settle();
  assert.equal(requests(listPath).at(-1).body.pageNum, 2);
  view.pagination["onUpdate:pageSize"](100);
  view.pagination["onUpdate:currentPage"](1);
  await settle();
  assert.equal(requests(listPath).length, 3);
  assert.equal(requests(listPath)[2].body.pageSize, 100);
  assert.equal(requests(listPath)[2].body.pageNum, 1);

  view.tree.onNodeClick(view.tree.data[0]);
  await settle();
  assert.deepEqual(view.filters.filters[0].options.map(({ value }) => value), ["app-1"]);
  view.filters["onUpdate:modelValue"]({ 业务系统: ["app-1"], 共享类型: [1], 加工程度: [2], 发布时间: ["1M"] });
  view.filters.onChange();
  view.input["onUpdate:modelValue"]("  城市  ");
  view.buttons.get("搜索")();
  await settle();
  assert.deepEqual(requests(listPath).at(-1).body, {
    pageNum: 1, pageSize: 100, keyword: "城市", orgPath: "00010001", appId: "app-1",
    shareType: 1, dataProcessLevel: 2, publishedWithin: "1M", sortField: "createdTime", sortDir: "desc",
  });
  assert.equal(requests(listPath).length, 5);
  assert.equal(requests(countPath).length, 1);
  assert.equal(requests(appPath).length, 1);

  requests(listPath).at(-1).resolve({ list: [row("new")], total: 1 });
  requests(listPath)[1].resolve({ list: [row("stale")], total: 200 });
  await settle();
  assert.deepEqual(view.list["data-list"].map(({ tid }) => tid), ["new"]);
});

test("market retries category counts independently and discards results from the previous session", async (t) => {
  const { view, requests, revision, visible } = mount(t);
  await settle();
  requests(countPath)[0].reject(new Error("暂时不可用"));
  requests(listPath)[0].resolve({ list: [row("old")], total: 1 });
  requests(appPath)[0].resolve([]);
  await settle();
  view.buttons.get("重试")();
  await settle();
  assert.equal(requests(countPath).length, 2);
  assert.equal(requests(listPath).length, 1);
  requests(countPath)[1].resolve({ orgPath: {} });
  await settle();
  visible.value = false;
  await settle();
  visible.value = true;
  await settle();
  assert.equal(requests(listPath).length, 2);
  assert.equal(requests(countPath).length, 2);

  revision.value += 1;
  await settle();
  requests(listPath)[1].resolve({ list: [row("stale-scope")], total: 1 });
  requests(listPath)[2].resolve({ list: [row("current-scope")], total: 1 });
  await settle();
  assert.equal(requests(countPath).length, 3);
  assert.equal(requests(appPath).length, 2);
  assert.deepEqual(view.list["data-list"].map(({ tid }) => tid), ["current-scope"]);
});

test("market sends one explicit favorite operation and requires the confirmed boolean", async (t) => {
  const { view, requests, messages } = mount(t);
  await settle();
  requests(listPath)[0].resolve({ list: [row("one", { collected: false })], total: 1 });
  await settle();
  const item = view.list["data-list"][0];
  view.list.onCollect(item);
  view.list.onCollect(item);
  assert.equal(requests(collectPath).length, 1);
  assert.deepEqual(requests(collectPath)[0].body, { catalogId: "one", collected: true });
  requests(collectPath)[0].resolve("true");
  await settle();
  assert.equal(item.collected, false);
  assert.deepEqual(messages, ["收藏状态暂未确认，请重试"]);

  view.list.onCollect(item);
  requests(collectPath)[1].resolve(true);
  await settle();
  assert.equal(item.collected, true);
  view.list.onCollect(item);
  assert.deepEqual(requests(collectPath)[2].body, { catalogId: "one", collected: false });
  requests(collectPath)[2].resolve(false);
  await settle();
  assert.equal(item.collected, false);
  assert.equal(requests(listPath).length, 1);
});

test("cart add refreshes once on a current domain rejection and leaves transport failures retryable", async (t) => {
  const { view, requests, messages, cartReads, revision } = mount(t);
  await settle();
  const catalog = row("one", { canApply: true });
  requests(listPath)[0].resolve({ list: [catalog], total: 1 });
  await settle();

  for (const errorCode of ["HTTP-NETWORK-001", "MAGIC-BUSINESS-503", "MAGIC-BUSINESS-409", "MAGIC-BUSINESS-404"]) {
    const expectedRefresh = /40[49]$/.test(errorCode) ? 1 : 0;
    const listBefore = requests(listPath).length;
    const addBefore = requests(addPath).length;
    view.list.onApply(catalog);
    view.list.onApply(catalog);
    assert.equal(requests(addPath).length, addBefore + 1);
    assert.deepEqual(requests(addPath).at(-1).body, { catalogId: "one" });
    requests(addPath).at(-1).reject(Object.assign(new Error("拒绝"), { errorCode }));
    await settle();
    assert.equal(requests(listPath).length, listBefore + expectedRefresh);
    if (expectedRefresh) requests(listPath).at(-1).resolve({ list: [catalog], total: 1 });
    await settle();
    assert.deepEqual(messages, []);
    assert.equal(cartReads(), 0);
  }

  view.list.onApply(catalog);
  revision.value += 1;
  await settle();
  const listAfterScopeChange = requests(listPath).length;
  requests(addPath).at(-1).reject(Object.assign(new Error("旧会话拒绝"), { errorCode: "MAGIC-BUSINESS-409" }));
  await settle();
  assert.equal(requests(listPath).length, listAfterScopeChange);
  assert.equal(cartReads(), 0);
});

function mountDetail(t) {
  const route = vue.reactive({ path: "/market/detail", query: { id: "A" }, matched: [] });
  const revision = vue.ref(1);
  const modalOpen = vue.ref(false);
  const opened = [];
  const calls = [];
  const messages = [];
  const buttons = new Map();
  let shownId;
  const libraries = {
    vue,
    "vue-router": { useRoute: () => route },
    "@/store": { usePermissionStore: () => ({ mixLayoutTopMenus: [] }) },
    "@/composables": {
      useCartCount: () => ({ sessionRevision: revision }),
      useRegisterModal: () => ({
        open: modalOpen,
        openRegisterModal: (options) => { opened.push(options); modalOpen.value = true; },
      }),
    },
  };
  const exported = {};
  createDetail(
    (name) => libraries[name], exported,
    { postSilently: (path, body) => new Promise((resolve, reject) => calls.push({ path, body, resolve, reject })) },
    { success: (message) => messages.push(message), error: (message) => messages.push(message) },
  );
  const app = renderer.createApp(exported.default);
  const component = (render) => vue.defineComponent({
    inheritAttrs: false,
    setup(_, { attrs, slots }) { return () => render(attrs, slots); },
  });
  for (const name of ["DetailBreadcrumb", "Icon", "LoadingState", "DictLabel", "ElTag"]) {
    app.component(name, component(() => vue.h("div")));
  }
  app.component("ElResult", component((_, slots) => vue.h("div", slots.extra?.())));
  app.component("DetailPage", component((attrs) => {
    shownId = attrs.id;
    return vue.h("div");
  }));
  app.component("ElButton", component((attrs, slots) => {
    const label = slots.default?.().map((child) => child.children).join("").trim();
    buttons.set(label, attrs.onClick);
    return vue.h("button");
  }));
  app.mount(node("root"));
  t.after(() => app.unmount());
  return { route, revision, modalOpen, opened, messages, buttons, shownId: () => shownId,
    requests: (path) => calls.filter((call) => call.path === path) };
}

const detail = (id, fields = {}) => row(id, {
  canApply: true, canCollect: true, collected: false, resType: ["table"], applicationState: "available", ...fields,
});

test("detail records one quiet browse after a valid open and never repeats it on application close", async (t) => {
  const { requests, buttons, modalOpen, opened, messages, shownId } = mountDetail(t);
  requests(detailPath)[0].reject(new Error("目录暂未上架"));
  await settle();
  assert.equal(requests(viewPath).length, 0);
  buttons.get("重新加载")();
  requests(detailPath)[1].resolve(detail("A"));
  await settle();
  assert.equal(shownId(), "A");
  assert.deepEqual(requests(viewPath).map(({ body }) => body), [{ tid: "A" }]);
  requests(viewPath)[0].reject(new Error("计数暂不可用"));
  await settle();
  assert.deepEqual(messages, []);
  buttons.get("申请")();
  await settle();
  assert.equal(opened[0].registerClass, "apply");
  modalOpen.value = false;
  await settle();
  assert.equal(requests(detailPath).length, 3);
  requests(detailPath)[2].resolve(detail("A"));
  await settle();
  assert.equal(requests(viewPath).length, 1);
  assert.equal(shownId(), "A");
});

test("detail discards delayed route and session reads before they can record a browse", async (t) => {
  const { route, revision, requests, shownId } = mountDetail(t);
  route.query.id = "B";
  await settle();
  revision.value += 1;
  await settle();
  requests(detailPath)[2].resolve(detail("B"));
  requests(detailPath)[1].resolve(detail("B", { catalogName: "旧会话" }));
  requests(detailPath)[0].resolve(detail("A"));
  await settle();
  assert.equal(shownId(), "B");
  assert.deepEqual(requests(viewPath).map(({ body }) => body), [{ tid: "B" }]);
});

test("detail accepts the exact favorite response and ignores replies for the preceding visit", async (t) => {
  const { route, requests, buttons, messages } = mountDetail(t);
  requests(detailPath)[0].resolve(detail("A"));
  await settle();
  buttons.get("收藏")();
  assert.deepEqual(requests(collectPath)[0].body, { catalogId: "A", collected: true });
  requests(collectPath)[0].resolve(false);
  await settle();
  assert.deepEqual(messages, ["收藏状态暂未确认，请重新加载。"]);
  buttons.get("收藏")();

  route.query.id = "B";
  await settle();
  requests(detailPath)[1].resolve(detail("B"));
  await settle();
  buttons.get("收藏")();
  requests(collectPath)[1].resolve(true);
  await settle();
  buttons.get("收藏")();
  assert.equal(requests(collectPath).length, 3);
  assert.deepEqual(requests(collectPath)[2].body, { catalogId: "B", collected: true });
  requests(collectPath)[2].resolve(true);
  await settle();
  assert.deepEqual(messages, ["收藏状态暂未确认，请重新加载。", "已收藏"]);
  buttons.get("已收藏")();
  assert.deepEqual(requests(collectPath)[3].body, { catalogId: "B", collected: false });
});
