import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import ts from "typescript";
import * as vue from "vue";
import { compileScript, parse } from "vue/compiler-sfc";

const { createRenderer, defineComponent, h, nextTick, onMounted, reactive, ref } = vue;
const filename = "proxy-datasource-services.vue";
const source = await readFile(
  new URL(`../src/components/business/${filename}`, import.meta.url),
  "utf8"
);
const { descriptor, errors } = parse(source, { filename });
assert.deepEqual(errors, []);
const compiled = compileScript(descriptor, { id: filename, inlineTemplate: true }).content;
const { outputText } = ts.transpileModule(compiled, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
});
const loadComponent = new Function("require", "exports", outputText);

function node(type, text = "") {
  return { type, text, children: [], attrs: {}, parent: null };
}

function remove(child) {
  if (!child.parent) return;
  child.parent.children.splice(child.parent.children.indexOf(child), 1);
  child.parent = null;
}

const renderer = createRenderer({
  createElement: node,
  createText: (text) => node("#text", text),
  createComment: (text) => node("#comment", text),
  setText: (child, text) => (child.text = text),
  setElementText(child, text) {
    child.children = [];
    child.text = text;
  },
  patchProp: (child, key, _previous, value) => (child.attrs[key] = value),
  parentNode: (child) => child.parent,
  nextSibling: (child) => child.parent?.children[child.parent.children.indexOf(child) + 1],
  insert(child, parent, anchor) {
    remove(child);
    parent.children.splice(
      anchor ? parent.children.indexOf(anchor) : parent.children.length,
      0,
      child
    );
    child.parent = parent;
  },
  remove,
});

function allNodes(parent) {
  return [parent, ...parent.children.flatMap(allNodes)];
}

function content(parent) {
  return allNodes(parent)
    .map((child) => child.text)
    .join("");
}

async function flush() {
  await Promise.resolve();
  await nextTick();
}

function mount(props, { actions = false } = {}) {
  const requests = [];
  const tables = [];
  const exposed = ref();
  const state = reactive(props);
  let sourceRetries = 0;
  const modules = {
    vue,
    "@/utils/request-policy": {
      default: {
        quiet({ url, method, data: body, signal }) {
          return new Promise((resolve, reject) =>
            requests.push({ url, method, body, signal, resolve, reject })
          );
        },
      },
    },
  };
  const exports = {};
  loadComponent((module) => {
    assert.ok(modules[module], module);
    return modules[module];
  }, exports);

  const app = renderer.createApp({
    render: () =>
      h(
        exports.default,
        { ...state, ref: exposed, onRetrySource: () => sourceRetries++ },
        actions ? { actions: () => h("button", "登记服务") } : {}
      ),
  });
  app.component(
    "DataTable",
    defineComponent({
      props: { data: Function, immediate: Boolean, pageSizes: Array },
      setup(tableProps, { expose, slots }) {
        const rows = ref([]);
        let query = { pageNo: 1, pageSize: 20 };
        let generation = 0;
        const load = async (next = query) => {
          query = next;
          const request = ++generation;
          const result = await tableProps.data(query);
          if (request === generation) rows.value = result.list;
          return result;
        };
        const refresh = (resetPage) => load(resetPage ? { ...query, pageNo: 1 } : query);
        expose({ refresh });
        tables.push({ load, props: tableProps });
        onMounted(() => {
          if (tableProps.immediate) void load();
        });
        return () =>
          h(
            "div",
            rows.value.length ? rows.value.map((row) => h("p", row.serviceName)) : slots.empty?.()
          );
      },
    })
  );
  app.component(
    "ElInput",
    defineComponent({
      props: { modelValue: String },
      setup(inputProps, { attrs, slots }) {
        return () =>
          h("div", [h("input", { ...attrs, value: inputProps.modelValue }), slots.suffix?.()]);
      },
    })
  );
  app.component("ElButton", {
    render() {
      return h("button", this.$slots.default?.());
    },
  });
  app.component("UTitle", {
    props: ["name"],
    render() {
      return h("h2", this.name);
    },
  });
  app.component("LoadingState", { render: () => h("p", "正在读取") });
  app.component("Icon", { render: () => null });
  app.component("ElTableColumn", { render: () => null });
  app.component("ElTag", { render: () => null });
  const root = node("root");
  app.mount(root);
  const find = (predicate) => allNodes(root).find(predicate);
  return {
    state,
    root,
    find,
    requests,
    tables,
    get sourceRetries() {
      return sourceRetries;
    },
    refresh: (resetPage) => exposed.value.refresh(resetPage),
    search(value, event = "onKeyup") {
      const input = find((child) => child.type === "input");
      input.attrs["onUpdate:modelValue"](value);
      input.attrs[event]({ key: "Enter" });
    },
    unmount: () => app.unmount(),
  };
}

test("step one keeps the registration slot and avoids querying a draft source", async () => {
  const fixture = mount({ sourceId: "" }, { actions: true });
  await flush();
  assert.equal(fixture.requests.length, 0);
  assert.deepEqual(fixture.tables[0].props.pageSizes, [10, 20, 50, 100]);
  assert.match(content(fixture.root), /当前数据源已登记服务.*登记服务.*保存数据源后，可登记服务/);

  fixture.state.sourceId = "source-a";
  await flush();
  assert.equal(fixture.requests.length, 1);
  fixture.requests[0].resolve({ list: [], total: 0 });
  await flush();
  assert.match(content(fixture.root), /暂无已登记服务/);
  fixture.unmount();
});

test("review uses one scoped request per 20- or 100-row page and keeps pagination sorting", async () => {
  const fixture = mount({ sourceId: "source-a", mode: "review" });
  const first = fixture.requests[0];
  assert.equal(first.url, "/dws/flowserve/gateway/api/list");
  assert.equal(first.method, "post");
  assert.deepEqual(first.body, {
    datasourceId: "source-a",
    pageNum: 1,
    pageSize: 20,
    sortField: undefined,
    sortDir: undefined,
    keyword: "",
  });
  assert.deepEqual(fixture.tables[0].props.pageSizes, [20, 50, 100]);
  const serviceRows = (count) =>
    Array.from({ length: count }, (_, index) => ({
      tid: String(index),
      serviceName: `服务 ${index + 1}`,
    }));
  first.resolve({ list: serviceRows(20), total: 200 });
  await flush();
  assert.match(content(fixture.root), /服务 20/);

  const query = { pageNo: 2, pageSize: 100, sortField: "serviceName", sortDir: "asc" };
  const page = fixture.tables[0].load(query);
  assert.equal(fixture.requests.length, 2);
  assert.deepEqual(fixture.requests[1].body, {
    datasourceId: "source-a",
    pageNum: 2,
    pageSize: 100,
    sortField: "serviceName",
    sortDir: "asc",
    keyword: "",
  });
  fixture.requests[1].resolve({ list: serviceRows(100), total: "200" });
  assert.equal((await page).total, 200);
  await flush();
  assert.match(content(fixture.root), /服务 100/);

  fixture.refresh(true);
  assert.equal(fixture.requests.length, 3);
  assert.equal(fixture.requests[2].body.pageNum, 1);
  assert.equal(fixture.requests[2].body.pageSize, 100);
  assert.equal(fixture.requests[2].body.sortField, "serviceName");
  fixture.unmount();
  assert.equal(fixture.requests[2].signal.aborted, true);
});

test("submitted searches and source switches suppress late failures", async () => {
  const fixture = mount({ sourceId: "source-a", mode: "review" });
  fixture.search("  车辆  ");
  assert.equal(fixture.requests.length, 2);
  assert.equal(fixture.requests[0].signal.aborted, true);
  assert.equal(fixture.requests[1].body.keyword, "车辆");
  fixture.requests[1].resolve({ list: [], total: 0 });
  fixture.requests[0].reject(new Error("过期错误"));
  await flush();
  assert.match(content(fixture.root), /没有匹配的数据服务/);
  assert.doesNotMatch(content(fixture.root), /过期错误/);

  fixture.search("", "onClear");
  assert.equal(fixture.requests.length, 3);
  assert.equal(fixture.requests[2].body.keyword, "");
  fixture.state.sourceId = "source-b";
  await flush();
  assert.equal(fixture.requests.length, 4);
  assert.equal(fixture.requests[2].signal.aborted, true);
  assert.equal(fixture.requests[3].body.datasourceId, "source-b");
  assert.equal(fixture.requests[3].body.keyword, "");
  fixture.requests[2].resolve({ list: [{ tid: "old", serviceName: "旧来源" }], total: 1 });
  fixture.requests[3].resolve({ list: [{ tid: "new", serviceName: "新来源" }], total: 1 });
  await flush();
  assert.match(content(fixture.root), /新来源/);
  assert.doesNotMatch(content(fixture.root), /旧来源/);
  fixture.unmount();
});

test("source errors and service list errors each retry their own operation", async () => {
  const fixture = mount({
    sourceId: "source-a",
    mode: "review",
    sourceLoading: true,
    sourceError: "",
  });
  assert.equal(fixture.requests.length, 0);
  assert.match(content(fixture.root), /正在读取/);
  fixture.state.sourceError = "数据源不在授权范围内";
  fixture.state.sourceLoading = false;
  await flush();
  assert.equal(fixture.requests.length, 0);
  assert.match(content(fixture.root), /数据源不在授权范围内/);
  const retry = () =>
    fixture.find((child) => child.type === "button" && content(child) === "重新读取");
  retry().attrs.onClick();
  assert.equal(fixture.sourceRetries, 1);

  fixture.state.sourceError = "";
  await flush();
  fixture.requests[0].reject(new Error("无法连接服务器"));
  await flush();
  assert.match(content(fixture.root), /无法连接服务器/);
  retry().attrs.onClick();
  assert.equal(fixture.sourceRetries, 1);
  assert.equal(fixture.requests.length, 2);
  fixture.requests[1].resolve({ unexpected: true });
  await flush();
  assert.match(content(fixture.root), /服务清单响应不完整/);
  retry().attrs.onClick();
  fixture.requests[2].resolve({ list: [], total: 0 });
  await flush();
  assert.match(content(fixture.root), /尚未登记服务/);
  assert.doesNotMatch(content(fixture.root), /服务清单响应不完整/);
  fixture.unmount();
});
