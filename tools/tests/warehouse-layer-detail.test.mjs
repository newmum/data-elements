import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import test from "node:test";
import vm from "node:vm";
import { compileCode } from "../../data-elements-chengtian/src/plugins/compiler/sfc-compiler.js";
import { resolveMagicImport } from "../../data-elements-chengtian/src/plugins/compiler/magic-import-resolver.js";

const root = new URL("../../", import.meta.url);
const requireApp = createRequire(new URL("data-elements-chengtian/package.json", root));
const vue = requireApp("vue");
const { renderToString } = requireApp("vue/server-renderer");
const { get } = requireApp("lodash-es");
const warehouse = new URL("lowcode/05.数据治理/2.5.1 数据中心(data-warehouse)/", root);

const compiler = {};
vm.runInNewContext(
  await readFile(new URL("data-elements-chengtian/public/babel.min.js", root), "utf8"),
  compiler
);
globalThis.window = { Babel: compiler.Babel };

const compile = async (file) => {
  const { compileJs, compileCss } = await compileCode(await readFile(new URL(file, warehouse), "utf8"));
  return { createComponent: new Function(compileJs), css: compileCss };
};
const dwd = await compile("基础层详情(dwd-detail).vue");
const dws = await compile("服务层详情(dws-detail).vue");

const dictionary = ["dwd", "dws"].map((layer) => ({
  tid: layer,
  label: layer.toUpperCase(),
  value: layer,
  children: [{ tid: layer + "-domain", label: "分域", value: layer + "-domain" }],
}));

const renderNode = (type, text = "") => ({ type, text, parent: null, children: [] });
function removeNode(node) {
  if (!node.parent) return;
  node.parent.children.splice(node.parent.children.indexOf(node), 1);
  node.parent = null;
}

const renderer = vue.createRenderer({
  createElement: renderNode,
  createText: (text) => renderNode("#text", text),
  createComment: () => renderNode("#comment"),
  insert(node, parent, anchor) {
    removeNode(node);
    const position = anchor ? parent.children.indexOf(anchor) : parent.children.length;
    parent.children.splice(position, 0, node);
    node.parent = parent;
  },
  remove: removeNode,
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  setText: (node, text) => (node.text = text),
  setElementText: (node, text) => (node.text = text),
  patchProp() {},
});

async function renderLayer(layer, { roots = dictionary, layerCode = "dwd-domain", interactive = false } = {}) {
  const { createSSRApp, h, nextTick, ref, resolveComponent } = vue;
  const calls = [];
  const state = {};
  const library = {
    vue: { ...vue, onMounted(callback) { state.mount = callback; } },
    "vue-router": { useRouter: () => ({ currentRoute: ref({ query: { layerCode } }) }) },
    "lodash-es": { get },
  };
  globalThis.___magic__import__ = (name, key) => resolveMagicImport(library[name], key);
  globalThis.$common = {
    async get(url, params) {
      calls.push({ url, params });
      return roots;
    },
    async post(url, params) {
      calls.push({ url, params });
      if (url === "/dst/statistics/common") return {};
      if (url === "/sym/dictSaveOrUpdate") return { tid: "new-directory" };
      return { list: Array.from({ length: params.pageSize }, (_, tid) => ({ tid })), total: 100 };
    },
    deepTree(node, visit) {
      visit(node);
      node.children?.forEach((child) => this.deepTree(child, visit));
    },
  };
  globalThis.$message = { success() {} };

  const page = { render: () => h(resolveComponent(layer + "-detail")) };
  const app = interactive ? renderer.createApp(page) : createSSRApp(page);
  app.component("DwdDetail", dwd.createComponent());
  app.component("DwsDetail", dws.createComponent());
  app.component("DataTable", {
    props: ["columns", "data"],
    setup(props, { slots, expose }) {
      state.table = props;
      expose({ refresh() {} });
      return () => h("section", slots.toolbar?.());
    },
  });
  app.component("ElInput", {
    props: ["placeholder"],
    setup(props, { attrs }) {
      if (props.placeholder.startsWith("搜索")) state.search = attrs["onUpdate:modelValue"];
      return () => h("input", { placeholder: props.placeholder });
    },
  });
  app.component("UTree", {
    setup(_props, { attrs }) {
      state.selectNode = attrs.onNodeClick;
      return () => h("div");
    },
  });
  app.component("ElButton", {
    props: { disabled: Boolean, link: Boolean },
    setup(props, { attrs, slots }) {
      state[props.link ? "rootAdd" : "toolbarAdd"] = { props, click: attrs.onClick };
      return () => h("button", { disabled: props.disabled }, slots.default?.());
    },
  });
  app.component("UModal", {
    props: ["modelValue", "title"],
    setup(props, { attrs, slots }) {
      state.modal = { props, confirm: attrs.onConfirm };
      return () => h("div", props.modelValue ? slots.default?.() : []);
    },
  });
  app.component("ElForm", {
    props: ["model", "rules"],
    setup(props, { slots, expose }) {
      state.form = props;
      expose({
        async validate() {
          state.validated = true;
          if (state.validationError) throw state.validationError;
        },
      });
      return () => h("form", slots.default?.());
    },
  });
  for (const name of [
    "UTreeNode", "Icon", "ElDropdown", "ElDropdownItem", "ElDropdownMenu",
    "ElText", "DictSelect", "ElFormItem", "ElInputNumber",
  ]) {
    app.component(name, { render() { return h("span", this.$slots.default?.()); } });
  }

  if (interactive) app.mount(renderNode("root"));
  else state.html = await renderToString(app);
  await state.mount();
  await nextTick();
  return Object.assign(state, { calls, unmount: () => app.unmount() });
}

test("both published identities retain their layer-specific columns and paged queries", async () => {
  assert.match(dwd.css, /\.warehouse-layer-detail \.left-panel\[data-v-/);
  for (const layer of ["dwd", "dws"]) {
    const base = layer === "dwd";
    const { table, search, calls, html } = await renderLayer(layer);
    assert.match(html, new RegExp(layer + "-detail"));
    assert.ok(html.includes(base ? "基础层分类" : "专题库分类"));
    assert.ok(html.includes(base ? "搜索目录名称/表名" : 'placeholder="搜索目录名称"'));
    assert.ok(html.includes(base ? "新建基础目录" : "新建专题库目录"));
    const columns = Object.fromEntries(table.columns.map((column) => [column.prop, column]));
    assert.equal(columns.catalogName.label, base ? "基础表名称" : "服务表名称");
    assert.equal(columns.fieldCount.width, base ? undefined : 120);
    assert.ok(columns[base ? "sourceDir" : "sourceCatalogName"]);
    assert.equal(Boolean(columns.processLogic), base);
    assert.equal(Boolean(columns.status), base);
    assert.equal(columns.operation, undefined);
    assert.deepEqual(calls.map(({ url }) => url), ["/sym/dict", "/dst/statistics/common"]);

    for (const [pageSize, keyword] of [[20, "目录"], [100, "source_table"]]) {
      search(keyword);
      const before = calls.length;
      const result = await table.data({ pageNo: 1, pageSize, sortField: "catalogName", sortDir: "asc" });
      assert.equal(result.list.length, pageSize);
      assert.equal(result.total, 100);
      assert.equal(calls.length, before + 1);
      assert.deepEqual(calls.at(-1), {
        url: "/dwm/center/" + layer + "-list",
        params: {
          sortField: "catalogName",
          sortDir: "asc",
          pageNum: 1,
          pageSize,
          conditions: [
            { field: "dataSourceType", value: base ? ["dwd-domain"] : ["dws", "dws-domain"], type: "in" },
            { field: base ? "catalogOrTableName" : "catalogName", value: keyword, type: "like" },
          ],
        },
      });
    }
    search("");
    await table.data({ pageNo: 2, pageSize: 20 });
    assert.equal(calls.at(-1).params.sortField, "updatedTime");
    assert.equal(calls.at(-1).params.sortDir, "desc");
    assert.equal(calls.at(-1).params.conditions.length, 1);
  }
});

test("missing dictionary root does not query another layer or attempt an unscoped read", async () => {
  const { table, calls } = await renderLayer("dws", { roots: [dictionary[0]] });
  assert.deepEqual(await table.data({ pageNo: 1, pageSize: 20 }), { list: [], total: 0 });
  assert.deepEqual(calls.map(({ url }) => url), ["/sym/dict"]);
});

test("the toolbar creates a directory under the selected node in each layer", async () => {
  for (const layer of ["dwd", "dws"]) {
    const root = dictionary.find((node) => node.value === layer);
    const selected = root.children[0];
    const state = await renderLayer(layer, { interactive: true, layerCode: "" });
    const writes = () => state.calls.filter(({ url }) => url === "/sym/dictSaveOrUpdate");
    try {
      assert.equal(state.toolbarAdd.props.disabled, false);
      state.rootAdd.click();
      await vue.nextTick();
      assert.equal(state.form.model.parentId, root.tid);

      state.selectNode(selected);
      await vue.nextTick();
      state.toolbarAdd.click();
      await vue.nextTick();
      assert.equal(state.modal.props.modelValue, true);
      assert.equal(state.modal.props.title, "新增");
      assert.equal(state.form.model.parentId, selected.tid);
      assert.equal(state.form.rules.dictName[0].required, true);
      assert.equal(state.form.rules.dictCode[0].required, true);
      assert.deepEqual(writes(), []);

      state.validationError = new Error("字典名称必填");
      await assert.rejects(state.modal.confirm(), state.validationError);
      assert.equal(state.validated, true);
      assert.deepEqual(writes(), []);

      state.validationError = null;
      state.form.model.dictName = "验证目录";
      state.form.model.dictCode = "validation_directory";
      await state.modal.confirm();
      await vue.nextTick();
      assert.equal(state.modal.props.modelValue, false);
      assert.deepEqual(writes(), [{
        url: "/sym/dictSaveOrUpdate",
        params: {
          parentId: selected.tid,
          dictName: "验证目录",
          dictCode: "validation_directory",
          dictDesc: "",
          sortNo: 0,
        },
      }]);
    } finally {
      state.unmount();
    }
  }
});

test("a layer with no valid dictionary parent cannot open the add dialog", async () => {
  const state = await renderLayer("dws", { roots: [dictionary[0]], interactive: true });
  try {
    assert.equal(state.toolbarAdd.props.disabled, true);
    assert.equal(state.rootAdd.props.disabled, true);
    state.toolbarAdd.click();
    state.rootAdd.click();
    await vue.nextTick();
    assert.equal(state.modal.props.modelValue, false);
    assert.deepEqual(state.calls.map(({ url }) => url), ["/sym/dict"]);
  } finally {
    state.unmount();
  }
});
