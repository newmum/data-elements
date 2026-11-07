import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import test from "node:test";

const requireApp = createRequire(
  new URL("../../data-elements-chengtian/package.json", import.meta.url),
);
const vue = requireApp("vue");
const ts = requireApp("typescript");
const { compileScript, parse } = requireApp("vue/compiler-sfc");
const source = new URL(
  "../../lowcode/02.数据盘点/02.登记流程组件/登记组件(register-modal)/登记订阅申请单(register-apply)/购物车列表(car-list).vue",
  import.meta.url,
);
const { descriptor, errors } = parse(await readFile(source, "utf8"), { filename: "car-list.vue" });
assert.deepEqual(errors, []);
const { content } = compileScript(descriptor, { id: "cart-list-test", inlineTemplate: true });
const { outputText } = ts.transpileModule(content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});
const createComponent = new Function("require", "exports", "$common", "$message", "$dialog", outputText);

const cartPath = "/dws/market/shoppingCardList";
const draftPath = "/dws/market/applyForm/list";
const removePath = "/dws/market/shoppingCardRemove";
const cartRow = (tid, available = true) => ({ tid, shoppingCarId: `cart-${tid}`, catalogName: tid, available });

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
    parent.children.splice(anchor ? parent.children.indexOf(anchor) : parent.children.length, 0, child);
    child.parent = parent;
  },
  remove,
  parentNode: (child) => child.parent,
  nextSibling: (child) => child.parent?.children[child.parent.children.indexOf(child) + 1] ?? null,
  setText() {},
  setElementText() {},
  patchProp() {},
});

const settle = async () => {
  await Promise.resolve();
  await vue.nextTick();
};

function readText(content) {
  if (typeof content === "string") return content;
  if (Array.isArray(content)) return content.map(readText).join("");
  if (!content || content.type === vue.Comment) return "";
  return readText(content.children?.default?.() ?? content.children);
}

function mountCart(t) {
  const revision = vue.ref(0);
  const store = { data: null, handleAction() {} };
  const calls = [];
  const committedCounts = [];
  const warnings = [];
  const view = { buttons: new Map() };
  const libraries = {
    vue,
    "element-plus": { ElCheckbox: {} },
    "@/store": { useRegisterStore: () => store },
    "@/composables": {
      useCartCount: () => ({
        sessionRevision: revision,
        beginRead: () => (count) => committedCounts.push(count),
      }),
    },
  };
  const common = {
    post(path, body) {
      if (path === draftPath) {
        calls.push({ path, body });
        return Promise.resolve([]);
      }
      return new Promise((resolve, reject) => calls.push({ path, body, resolve, reject }));
    },
  };
  const exported = {};
  createComponent(
    (name) => libraries[name],
    exported,
    common,
    { success() {}, error() {}, warning: (value) => warnings.push(value) },
    () => Promise.reject(new Error("Dialog is outside these cases")),
  );
  const app = renderer.createApp(exported.default);
  const component = (render) => vue.defineComponent({
    inheritAttrs: false,
    setup(_, { attrs, slots }) { return () => render(attrs, slots); },
  });
  const passthrough = component((_, slots) => vue.h("div", slots.default?.()));
  for (const name of [
    "ElSpace", "ElBadge", "ElTag", "ElCheckbox", "ElDropdown", "ElDropdownMenu", "ElDropdownItem", "Icon", "Empty",
  ]) {
    app.component(name, passthrough);
  }
  app.component("UTitle", component((_, slots) => vue.h("div", [...slots.default(), ...slots.right()])));
  app.component("ElDrawer", component((attrs, slots) =>
    vue.h("div", attrs.modelValue ? slots.default?.() : []),
  ));
  app.component("ElInput", component((attrs) => {
    view.input = { ...attrs };
    return vue.h("input");
  }));
  app.component("ElButton", component((attrs, slots) => {
    const label = slots.default?.().map((child) => child.children).join("").trim();
    view.buttons.set(label, attrs.onClick);
    return vue.h("button");
  }));
  app.component("ElResult", component((attrs, slots) => {
    view.result = attrs.title;
    return vue.h("div", slots.extra?.());
  }));
  app.component("PageList", component((attrs, slots) => {
    view.page = { ...attrs };
    view.rows = (attrs.list || []).map((row) => ({
      row,
      checkbox: slots.icon({ row })[0].props,
      remove: slots.actions({ row })[0].props,
      status: readText(slots.status({ row })),
    }));
    return vue.h("div");
  }));
  app.directive("loading", {});
  const host = app.mount(node());
  t.after(() => app.unmount());
  return { calls, view, store, revision, committedCounts, warnings, host };
}

function answer(call, list, overrides = {}) {
  call.resolve({
    list,
    total: 135,
    cartTotal: 135,
    pageNum: call.body.pageNum,
    pageSize: call.body.pageSize,
    ...overrides,
  });
}

test("cart uses one read per page and retains only available selections across queries", async (t) => {
  const { calls, view, store, revision, committedCounts, host } = mountCart(t);
  const pages = () => calls.filter(({ path }) => path === cartPath);
  const select = (index) => view.rows[index].checkbox.onChange(true);
  const search = (keyword) => {
    view.input["onUpdate:modelValue"](keyword);
    view.input.onKeydown({ key: "Enter" });
  };

  assert.deepEqual(pages()[0].body, { mode: "page", pageNum: 1, pageSize: 20, keyword: "" });
  answer(pages()[0], [
    cartRow("first"), cartRow("unavailable", false), { ...cartRow("missing", false), tid: null },
    ...Array.from({ length: 17 }, (_, index) => cartRow(`initial-${index}`)),
  ]);
  await settle();
  assert.equal(view.page.rowKey ?? view.page["row-key"], "shoppingCarId");
  assert.equal(view.rows[1].checkbox.disabled, true);
  assert.equal(view.rows[2].checkbox.disabled, true);
  select(1);
  select(0);
  view.page.onPageChange({ pageNo: 2, pageSize: 20 });
  answer(pages()[1], [cartRow("second")]);
  await settle();
  select(0);
  view.page.onPageChange({ pageNo: 2, pageSize: 100 });
  assert.deepEqual(pages()[2].body, { mode: "page", pageNum: 1, pageSize: 100, keyword: "" });
  answer(pages()[2], [
    cartRow("first"), cartRow("second"),
    { ...cartRow("first", false), shoppingCarId: "unavailable-duplicate" },
    ...Array.from({ length: 97 }, (_, index) => cartRow(`large-${index}`)),
  ]);
  await settle();
  assert.equal(view.rows[0].checkbox.modelValue ?? view.rows[0].checkbox["model-value"], true);
  assert.equal(view.rows[2].checkbox.modelValue ?? view.rows[2].checkbox["model-value"], false);
  assert.equal(view.rows[2].checkbox.disabled, true);

  search("old");
  search("new");
  assert.equal(pages().length, 5);
  answer(pages()[4], [cartRow("first", false)], { total: 1, cartTotal: 135 });
  answer(pages()[3], [cartRow("stale")], { total: 1, cartTotal: 136 });
  await settle();
  assert.deepEqual(view.rows.map(({ row }) => row.tid), ["first"]);
  assert.deepEqual(committedCounts, [135, 135, 135, 135]);
  host.next();
  assert.deepEqual(store.data.apply.applyResource.map(({ tid }) => tid), ["second"]);

  view.buttons.get("草稿箱")();
  assert.equal(calls.filter(({ path }) => path === draftPath).length, 1);
  revision.value++;
  assert.throws(() => host.next(), /至少勾选/);
});

test("cart reports a failed load, retries, and prevents duplicate row removal", async (t) => {
  const { calls, view, committedCounts, store, host } = mountCart(t);
  calls[0].reject(new Error("读取暂不可用"));
  await settle();
  assert.equal(view.result, "购物车加载失败");
  view.buttons.get("重新加载")();
  answer(calls[2], [cartRow("remove"), { ...cartRow("remove"), shoppingCarId: "duplicate-cart" }]);
  await settle();
  view.rows[0].checkbox.onChange(true);
  const clickRemove = view.rows[0].remove.onClick;
  clickRemove();
  clickRemove();
  assert.deepEqual(calls.slice(3).map(({ path, body }) => [path, body]), [
    [removePath, { tid: "cart-remove" }],
  ]);
  calls[3].resolve(true);
  await settle();
  assert.equal(calls[4].path, cartPath);
  answer(calls[4], [{ ...cartRow("remove"), shoppingCarId: "duplicate-cart" }], { total: 1, cartTotal: 1 });
  await settle();
  host.next();
  assert.equal(store.data.apply.applyResource[0].shoppingCarId, "duplicate-cart");
  assert.deepEqual(committedCounts, [135, 1]);
});

test("cart retries unconfirmed qualification on the same search and ignores stale retry results", async (t) => {
  const { calls, view, store, host } = mountCart(t);
  const pages = () => calls.filter(({ path }) => path === cartPath);
  const unconfirmed = { ...cartRow("checking", false), availabilityStatus: "unconfirmed" };
  const withdrawn = {
    ...cartRow("withdrawn", false),
    availabilityStatus: "unavailable",
    availabilityReason: "目录已下架或尚未发布",
  };

  answer(pages()[0], [cartRow("first"), cartRow("checking")]);
  await settle();
  view.rows[0].checkbox.onChange(true);
  view.rows[1].checkbox.onChange(true);

  view.input["onUpdate:modelValue"]("路网");
  view.input.onKeydown({ key: "Enter" });
  answer(pages()[1], [unconfirmed, withdrawn]);
  await settle();
  assert.equal(view.rows[0].checkbox.disabled, true);
  assert.equal(view.rows[0].remove.disabled, false);
  assert.match(view.rows[0].status, /暂时无法确认资格/);
  assert.match(view.rows[1].status, /暂不可申请目录已下架或尚未发布/);
  host.next();
  assert.deepEqual(store.data.apply.applyResource.map(({ tid }) => tid), ["first"]);

  const retry = view.buttons.get("重试资格校验");
  retry();
  retry();
  assert.equal(pages().length, 3);
  assert.deepEqual(pages()[2].body, pages()[1].body);
  assert.throws(() => host.next(), /请先完成购物车列表操作/);
  view.page.onPageChange({ pageNo: 2, pageSize: 20 });
  answer(pages()[3], [cartRow("later-page")]);
  answer(pages()[2], [unconfirmed]);
  await settle();
  assert.deepEqual(view.rows.map(({ row }) => row.tid), ["later-page"]);
  assert.equal(view.page.pageNo ?? view.page["page-no"], 2);
  retry();
  assert.equal(pages().length, 4);

  view.page.onPageChange({ pageNo: 1, pageSize: 20 });
  answer(pages()[4], [unconfirmed, withdrawn]);
  await settle();
  view.buttons.get("重试资格校验")();
  answer(pages()[5], [cartRow("checking"), withdrawn]);
  await settle();
  assert.equal(view.rows[0].checkbox.disabled, false);
  assert.equal(view.rows[0].status, "");
  view.rows[0].checkbox.onChange(true);
  host.next();
  assert.deepEqual(store.data.apply.applyResource.map(({ tid }) => tid), ["first", "checking"]);
});

test("removal keeps the requested page when the user moves while it is in flight", async (t) => {
  const { calls, view } = mountCart(t);
  const pending = { ...cartRow("checking", false), availabilityStatus: "unconfirmed" };
  answer(calls[0], [pending]);
  await settle();
  view.rows[0].remove.onClick();
  assert.deepEqual([calls[2].path, calls[2].body], [removePath, { tid: pending.shoppingCarId }]);
  view.page.onPageChange({ pageNo: 3, pageSize: 20 });
  answer(calls[3], [cartRow("current")]);
  await settle();
  calls[2].resolve(true);
  await settle();
  assert.deepEqual(calls[4].body, { mode: "page", pageNum: 3, pageSize: 20, keyword: "" });
  answer(calls[4], [cartRow("last-page")], { pageNum: 2, total: 21, cartTotal: 21 });
  await settle();
  assert.equal(view.page.pageNo ?? view.page["page-no"], 2);
  assert.deepEqual(view.rows.map(({ row }) => row.tid), ["last-page"]);
});
