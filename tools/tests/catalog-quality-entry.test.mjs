import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import test from "node:test";
import vm from "node:vm";
import { compileCode } from "../../data-elements-chengtian/src/plugins/compiler/sfc-compiler.js";
import { resolveMagicImport } from "../../data-elements-chengtian/src/plugins/compiler/magic-import-resolver.js";

const root = new URL("../../", import.meta.url);
const requireApp = createRequire(
  new URL("data-elements-chengtian/package.json", root),
);
const vue = requireApp("vue");
const routing = requireApp("vue-router");
const { ElButton } = requireApp("element-plus");
const { renderToString } = requireApp("vue/server-renderer");

const babel = {};
vm.runInNewContext(
  await readFile(
    new URL("data-elements-chengtian/public/babel.min.js", root),
    "utf8",
  ),
  babel,
);
globalThis.window = { Babel: babel.Babel };
const sourcePath =
  "lowcode/05.数据治理/2.5.4 数据质量(data-quality)/质检报告(quality-report).vue";
const { compileJs } = await compileCode(
  await readFile(new URL(sourcePath, root), "utf8"),
);
delete globalThis.window;

async function renderEntry(qualityPath, routesReady = true) {
  const routePage = { render: () => vue.h("div") };
  const routes = [{ path: "/catalog", component: routePage }];
  if (qualityPath) {
    routes.push({
      path: qualityPath,
      component: routePage,
      meta: { hidden: true, runtimeSfcRoot: "data-quality" },
    });
  }
  const router = routing.createRouter({
    history: routing.createMemoryHistory(),
    routes,
  });
  await router.push("/catalog");
  const imports = {
    vue,
    "vue-router": routing,
    "@/store": {
      usePermissionStore: () => ({ isDynamicRoutesGenerated: routesReady }),
    },
  };
  globalThis.___magic__import__ = (name, key) =>
    resolveMagicImport(imports[name], key);
  const entry = new Function(compileJs)();
  const app = vue.createSSRApp(entry);
  app.use(router);
  app.component("ElButton", ElButton);
  app.component("Icon", {
    render: () => vue.h("span", { "aria-hidden": "true" }),
  });
  return renderToString(app);
}

test("catalog quality links to each tenant's granted hidden route", async () => {
  for (const path of ["/zl/zl-sjzl/list", "/register/zl-sjzl/list"]) {
    const html = await renderEntry(path);
    assert.ok(html.includes(`href="${path}"`));
    assert.match(html, /前往数据质量/);
    assert.doesNotMatch(html, /去质检|质检结果暂无|未开通/);
  }
});

test("catalog quality offers no navigation without an authorized initialized route", async () => {
  for (const html of [
    await renderEntry(),
    await renderEntry("/zl/zl-sjzl/list", false),
  ]) {
    assert.match(html, /当前账号未开通数据质量入口/);
    assert.doesNotMatch(html, /href=|前往数据质量/);
  }
});
