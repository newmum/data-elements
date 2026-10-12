import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";
import { parse } from "vue/compiler-sfc";
import sm from "sm-crypto";

const project = new URL("../", import.meta.url);
const read = (path) => readFileSync(new URL(path, project), "utf8");
const compile = (source) => ts.transpileModule(source.replace(/^import .*;\r?$/gm, ""), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;

function memoryStorage(values = []) {
  const data = new Map(values);
  return {
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, String(value)),
    removeItem: (key) => data.delete(key),
  };
}

function loadStorage(localStorage) {
  const context = { exports: {}, localStorage, isString: (value) => typeof value === "string" };
  vm.runInNewContext(compile(read("src/utils/storage.ts")), context);
  return context.exports.Storage;
}

// These values previously became Number/Boolean/null through JSON.parse. They
// must remain text, including leading zeroes and IDs beyond JS safe integers.
const localStorage = memoryStorage();
const Storage = loadStorage(localStorage);
for (const text of ["246810", "001234", "2084109831682699264", "1e10", "true", "null", "12345678901234567890123456789012"]) {
  Storage.set("account", text);
  assert.equal(Storage.getString("account"), text);
}
assert.equal(Storage.getString("missing"), "");
assert.equal(Storage.getString("missing", "fallback"), "fallback");
Storage.set("flag", false);
assert.equal(Storage.get("flag"), false); // General structured storage remains compatible.

const requests = [];
const expectedPassword = "Fixture!Pass9";
const settings = { appId: "app-a", brandTenantId: "tenant-a", loadingSettings: async () => {} };
const authContext = {
  exports: {}, sm, useSettingStore: () => settings,
  request: async (request) => {
    requests.push(request.data);
    assert.equal(typeof request.data.account, "string");
    if (request.data.password !== sm.sm3(expectedPassword)) throw new Error("用户名或密码错误");
    return { token: "fixture-session", tenantId: "tenant-a", url: "/home" };
  },
};
vm.runInNewContext(compile(read("src/api/auth-api.ts")), authContext);
const AuthAPI = authContext.exports.default;

// API callers from an old cached page can still supply a numeric account.
await AuthAPI.login({ username: 246810, password: expectedPassword, tenantId: "tenant-a" });
assert.equal(requests.at(-1).account, "246810");
await AuthAPI.login({ username: " 001234 ", password: expectedPassword, tenantId: "tenant-a" });
assert.equal(requests.at(-1).account, "001234");

const AUTH_KEYS = {
  REMEMBER_ME: "remember_me", REMEMBERED_ACCOUNT: "remembered_login_account",
  REMEMBERED_PASSWORD: "remembered_login_password", LAST_SELECTED_TENANT: "last_selected_tenant",
};
const credentialKey = sm.sm3("localhost:3000:data-elements-login").slice(0, 32);
Storage.set(AUTH_KEYS.REMEMBER_ME, true);
Storage.set(AUTH_KEYS.REMEMBERED_ACCOUNT, "246810");
Storage.set(AUTH_KEYS.REMEMBERED_PASSWORD, sm.sm4.encrypt(expectedPassword, credentialKey));
const mounted = [];
const navigations = [];
const errorLogs = [];
const { descriptor } = parse(read("src/views/login/components/Login.vue"));
const context = vm.createContext({
  exports: {}, Storage, sm, AUTH_KEYS, localStorage, URL, Set,
  window: { location: { host: "localhost:3000", href: "http://localhost:3000/#/login" } },
  capabilityCenterBases: {}, defineProps: () => ({}),
  ref: (value) => ({ value }), computed: (callback) => ({ value: callback() }),
  onMounted: (callback) => mounted.push(callback), watch: () => {},
  useSettingStore: () => settings,
  useUserStore: () => ({ login: async (data) => (await AuthAPI.login(data)).url }),
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ resolve: (path) => ({ path, query: {}, hash: "" }), push: async (target) => navigations.push(target) }),
  TenantAPI: { loginOptions: async () => [{ tid: "2084109831682699264", name: "Test tenant" }] },
  console: { error: (...args) => errorLogs.push(args) },
});
vm.runInContext(compile(descriptor.scriptSetup.content), context);
for (const callback of mounted) await callback();
assert.equal(vm.runInContext("typeof loginFormData.value.username", context), "string");
assert.equal(vm.runInContext("loginFormData.value.username", context), "246810");
assert.equal(vm.runInContext("loginFormData.value.tenantId", context), "2084109831682699264");
assert.equal(vm.runInContext("loginFormData.value.password", context), expectedPassword);
vm.runInContext("loginFormRef.value={validate:async()=>true};loginFormData.value.password += ' '", context);
await vm.runInContext("handleLoginSubmit()", context);
assert.equal(vm.runInContext("loading.value", context), false);
assert.equal(navigations.length, 0);
assert.equal(requests.at(-1).password, sm.sm3(expectedPassword + " "));
assert.equal(Storage.getString(AUTH_KEYS.REMEMBERED_ACCOUNT), "246810");

// Correct the password in the same form after failure; no reload/cache clearing.
vm.runInContext("loginFormData.value.password=loginFormData.value.password.slice(0,-1)", context);
await vm.runInContext("handleLoginSubmit()", context);
assert.equal(vm.runInContext("loading.value", context), false);
assert.equal(navigations.length, 1);
assert.equal(navigations[0].path, "/home");
assert.equal(requests.at(-1).account, "246810");
assert.equal(requests.at(-1).password, sm.sm3(expectedPassword));
assert.equal(sm.sm4.decrypt(Storage.getString(AUTH_KEYS.REMEMBERED_PASSWORD), credentialKey), expectedPassword);
assert.equal(errorLogs.length, 0);

// A transport failure can carry the SM3 digest/token in Axios config. The
// request layer must still reject and notify without logging those credentials.
const handlers = {};
const transportLogs = [];
const notices = [];
const transportContext = {
  exports: {},
  axios: { create: () => ({ interceptors: {
    request: { use: (_success, failure) => { handlers.requestError = failure; } },
    response: { use: (_success, failure) => { handlers.responseError = failure; } },
  } }) },
  qs: { stringify: () => "" },
  console: { error: (...args) => transportLogs.push(args) },
  createApiRequestError: (_response, options) => new Error(options.message),
  showApiError: (error) => notices.push(error),
};
vm.runInNewContext(compile(read("src/utils/request.ts").replace("import.meta.env.VITE_APP_BASE_API", '"/fixture-api"')), transportContext);
const transportError = {
  code: "ERR_NETWORK", message: "fixture transport failure", stack: "fixture stack",
  config: { method: "post", url: "/portal/login", data: { password: sm.sm3(expectedPassword) }, headers: { token: "fixture-private-token" } },
};
await assert.rejects(() => handlers.requestError(transportError));
await assert.rejects(() => handlers.responseError(transportError), /无法连接服务器/);
await assert.rejects(() => handlers.responseError({ ...transportError, response: { status: 500, data: { msg: "服务暂时不可用" } } }), /服务暂时不可用/);
assert.equal(notices.length, 2);
assert.equal(JSON.stringify(transportLogs).includes(sm.sm3(expectedPassword)), false);
assert.equal(JSON.stringify(transportLogs).includes("fixture-private-token"), false);

console.log("PASS: text credential storage, numeric/leading-zero account transport, remembered-login restoration, failed password with space then successful retry, credential-safe failure logging");
