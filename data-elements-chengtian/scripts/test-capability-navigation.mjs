import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import { parse } from "vue/compiler-sfc";
import ts from "typescript";

const project = new URL("../", import.meta.url);
const source = readFileSync(new URL("../data/working/global-nav.vue", project), "utf8");
const { descriptor } = parse(source);
const script = descriptor.scriptSetup.content.replace(/^import .*;$/gm, "");
const compiled = readFileSync(new URL("../data/working/global-nav.compile.js", project), "utf8");
const registry = readFileSync(new URL("src/utils/capabilityCenters.ts", project), "utf8");
const routes = {
  "数据资产中心": "/assets/my",
  "数据集成中心": "/integration/overview",
  "数据资源中心": "/resource/overview",
  "数据计算中心": "/compute/overview",
  "数据治理中心": "/governance/overview",
  "数据服务中心": "/services/catalog",
  "知识服务中心": "/graph/datasets",
  "数据可视化中心": "/visualization/screens",
  "人工智能中心": "/ai/robots",
  "数据运行监控中心": "/ops/health",
  "数据安全中心": "/security/discovery/models",
};
const devPorts = { qizhi: "3001", wanxiang: "3010", haitong: "3002", search: "3300", report: "3004" };
const prefixes = { qizhi: "/qizhi/", wanxiang: "/wanxiang-governance/", haitong: "/haitong/", search: "/search/", report: "/report/" };
const owner = (route) => /^\/(assets|resource|governance)(\/|$)/.test(route) ? "wanxiang" : route.startsWith("/integration/") ? "haitong" : "qizhi";
let checks = 0;

const groups = [...descriptor.template.content.matchAll(/<div class="grid">([\s\S]*?)<\/div>\s*<\/div>/g)];
const expectedGroups = [
  ["大数据能力产品", ["数据集成中心", "数据资源中心", "数据计算中心", "数据治理中心", "数据资产中心", "数据服务中心"]],
  ["数据应用产品", ["智能搜索中心", "知识服务中心", "数据可视化中心", "人工智能中心"]],
  ["数据安全产品", ["统一身份管理中心", "数据安全中心", "数据运行监控中心"]],
];
assert.equal(groups.length, expectedGroups.length);
for (const [index, [title, items]] of expectedGroups.entries()) {
  assert.ok(groups[index][1].includes(title));
  assert.deepEqual([...groups[index][1].matchAll(/<h5\b[^>]*>([^<]+)<\/h5>/g)].map((match) => match[1]), items);
  checks += 2;
}
assert.ok(!source.includes("平台支撑"));
checks++;

function loadRegistry(env, exportName = "capabilityCenterBases") {
  const javascript = ts.transpileModule(registry.replaceAll("import.meta.env", "env"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const context = { exports: {}, env };
  vm.runInNewContext(javascript, context);
  return context.exports[exportName];
}
function sandbox({ hostname = "localhost", port = "3000", globals = {}, meta = {} } = {}) {
  const navigated = [];
  const origin = `http://${hostname}${port ? `:${port}` : ""}`;
  return {
    URL, navigated,
    window: { ...globals, location: { hostname, port, origin, assign: (url) => navigated.push(url) } },
    document: { querySelector: (selector) => {
      const key = selector.match(/name="([^"]+)"/)?.[1];
      return meta[key] ? { getAttribute: () => meta[key] } : null;
    } },
    useSettingStore: () => ({ navVisible: true }),
  };
}
function globalsFor(bases) {
  return {
    __DATA_ELEMENTS_QIZHI_BASE_URL__: bases.qizhi,
    __DATA_ELEMENTS_WANXIANG_BASE_URL__: bases.wanxiang,
    __DATA_ELEMENTS_HAITONG_BASE_URL__: bases.haitong,
    __DATA_ELEMENTS_SEARCH_BASE_URL__: bases.search,
  };
}
for (const dev of [true, false]) {
  const bases = loadRegistry({ DEV: dev });
  for (const [center, base] of Object.entries(bases)) {
    assert.equal(base, dev ? `http://localhost:${devPorts[center]}/` : prefixes[center]);
    checks++;
  }
  const context = sandbox({ hostname: dev ? "localhost" : "platform.example", port: dev ? "3000" : "", globals: globalsFor(bases) });
  vm.createContext(context);
  vm.runInContext(script, context);
  for (const [name, route] of Object.entries(routes)) {
    const handler = descriptor.template.content.match(new RegExp(`<h5 @click="([^"]+)">${name}</h5>`))?.[1];
    assert.ok(handler, `Missing menu: ${name}`);
    vm.runInContext(handler === "openAssetCenter" ? `${handler}()` : handler, context);
    const expected = new URL(bases[owner(route)], context.window.location.origin);
    expected.hash = route;
    assert.equal(context.navigated.at(-1), expected.href);
    checks++;
  }
  vm.runInContext("openSearchCenter()", context);
  assert.equal(context.navigated.at(-1), new URL(bases.search, context.window.location.origin).href);
  vm.runInContext("open('/resource/overview')", context);
  assert.equal(new URL(context.navigated.at(-1)).pathname, dev ? "/" : prefixes.wanxiang);
  checks += 2;
}
// Test the actual compiled low-code click handlers, not just source helpers.
const context = sandbox();
context.___magic__import__ = (lib, name) => name === "useSettingStore" ? context.useSettingStore : (name === "unref" ? (value) => value : (name === "withCtx" ? (fn) => fn : ((...args) => args)));
const component = new Function("window", "document", "URL", "___magic__import__", compiled)(context.window, context.document, URL, context.___magic__import__);
const tree = component.setup({})({}, []);
const clicks = new Map();
function visit(value) {
  if (!value || typeof value !== "object") return;
  if (Array.isArray(value)) {
    if (value[0] === "h5") clicks.set(value[2], value[1].onClick);
    value.forEach(visit);
  } else Object.entries(value).forEach(([key, item]) => visit(key === "default" && typeof item === "function" ? item() : item));
}
visit(tree);
assert.equal(clicks.size, 13);
for (const [name, route] of Object.entries(routes)) {
  clicks.get(name)();
  assert.equal(context.navigated.at(-1), `http://localhost:${devPorts[owner(route)]}/#${route}`);
  checks++;
}
clicks.get("统一身份管理中心")();
assert.equal(context.navigated.at(-1), "http://localhost:3005/#/console/workforce/overview");
checks++;
clicks.get("智能搜索中心")();
assert.equal(context.navigated.at(-1), "http://localhost:3300/");
checks++;
// Explicit deployment overrides beat meta tags; query/hash from a configured
// base cannot leak into a centre entry and credentials never enter the URL.
const overrides = loadRegistry({ DEV: false, VITE_WANXIANG_GOVERNANCE_URL: " https://centers.example/custom?stale=1#old " });
const custom = sandbox({ globals: globalsFor(overrides), meta: { "data-elements-wanxiang-base-url": "https://wrong.example/" } });
vm.createContext(custom);
vm.runInContext(script, custom);
vm.runInContext("open('/governance/overview')", custom);
assert.equal(custom.navigated.at(-1), "https://centers.example/custom/#/governance/overview");
checks++;
// 身份地址遵循开发、生产和覆盖配置，独立于会话接收名单。
assert.equal(loadRegistry({DEV:true}, "identityCenterBase"), "http://localhost:3005/");
assert.equal(loadRegistry({DEV:false}, "identityCenterBase"), "/idaas/");
assert.equal(loadRegistry({DEV:false,VITE_IDAAS_CENTER_URL:" https://identity.example/entry/ "}, "identityCenterBase"), "https://identity.example/entry/");
checks += 3;
// 身份导航单独配置，不扩大平台会话桥范围。
for (const dev of [true, false]) {
 const nav = sandbox({hostname: dev ? "localhost" : "platform.example", port: dev ? "3000" : ""});
 vm.createContext(nav); vm.runInContext(script, nav); vm.runInContext("openIdentityCenter()", nav);
 assert.equal(nav.navigated.at(-1), dev ? "http://localhost:3005/#/console/workforce/overview" : "http://platform.example/idaas/#/console/workforce/overview"); checks++;
}
const identityOverride = sandbox({globals:{__DATA_ELEMENTS_IDAAS_BASE_URL__: "https://identity.example/custom?stale=1#old"}});
vm.createContext(identityOverride); vm.runInContext(script,identityOverride); vm.runInContext("openIdentityCenter()",identityOverride);
assert.equal(identityOverride.navigated.at(-1), "https://identity.example/custom/#/console/workforce/overview"); checks++;
assert.ok(source.includes('height: calc(100% - 55px)')); checks++;
const main = readFileSync(new URL("src/main.ts", project), "utf8");
assert.ok(main.includes("window.__DATA_ELEMENTS_IDAAS_BASE_URL__ = identityCenterBase")); checks++;
for (const [key, center] of Object.entries({ QIZHI_BASE_URL: "qizhi", REPORT_URL: "report", WANXIANG_BASE_URL: "wanxiang", HAITONG_BASE_URL: "haitong", SEARCH_BASE_URL: "search" })) {
  assert.ok(main.includes(`window.__DATA_ELEMENTS_${key}__ = capabilityCenterBases.${center}`));
  checks++;
}
for (const path of ["src/utils/capabilitySession.ts", "src/views/login/components/Login.vue"]) {
  const file = readFileSync(new URL(path, project), "utf8");
  assert.ok(file.includes("Object.values(capabilityCenterBases)"));
  assert.ok(!file.includes("import.meta.env.VITE_"));
  checks++;
}
// Correcting navigation must not relax the session bridge's trust boundary.
const sessionSource = readFileSync(new URL("src/utils/capabilitySession.ts", project), "utf8").replace(/^import .*;$/gm, "");
const sessionJs = ts.transpileModule(sessionSource, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
const replies = [];
const parent = { postMessage: (...args) => replies.push(args) };
let receive;
const bridge = {
  URL, capabilityCenterBases: loadRegistry({ DEV: true }),
  activePlatformToken: async () => "synthetic-test-session", logoutPlatformSession: async () => {},
  window: { parent, location: { href: "http://localhost:3000/capability-session.html" }, addEventListener: (name, listener) => { assert.equal(name, "message"); receive = listener; } },
};
vm.runInNewContext(sessionJs, bridge);
for (const origin of ["http://localhost:3001", "http://localhost:3010", "http://localhost:3002", "http://localhost:3300", "http://localhost:3004"]) {
  await receive({ origin, source: parent, data: { type: "data-elements:session-request", nonce: "test-nonce" } });
  assert.equal(replies.at(-1)[1], origin);
  assert.equal(replies.at(-1)[0].nonce, "test-nonce");
  checks++;
}
const replyCount = replies.length;
for (const event of [
  { origin: "http://localhost:3005", source: parent, data: { type: "data-elements:session-request", nonce: "n" } },
  { origin: "http://localhost:5173", source: parent, data: { type: "data-elements:session-request", nonce: "n" } },
  { origin: "http://127.0.0.1:3300", source: parent, data: { type: "data-elements:session-request", nonce: "n" } },
  { origin: "https://untrusted.example", source: parent, data: { type: "data-elements:session-request", nonce: "n" } },
  { origin: "http://localhost:3010", source: {}, data: { type: "data-elements:session-request", nonce: "n" } },
  { origin: "http://localhost:3010", source: parent, data: { type: "data-elements:session-request" } },
]) {
  await receive(event);
  assert.equal(replies.length, replyCount);
  checks++;
}
console.log(`Capability navigation: ${checks} checks passed (all menu handlers, dev/production URLs, overrides and shared authentication addresses).`);
