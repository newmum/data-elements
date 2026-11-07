import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import test from "node:test";

const require = createRequire(import.meta.url);
const ts = require("typescript");
const vue = require("vue");
const source = await readFile(new URL("../src/composables/useCartCount.ts", import.meta.url), "utf8");
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});

function fixture() {
  const revision = vue.ref(0);
  let token = "validation-account-a";
  const calls = [];
  const modules = {
    vue,
    "@/utils/auth": {
      AuthStorage: { getSessionRevision: () => revision.value, getAccessToken: () => token },
    },
    "@/utils/request-policy": {
      requestPolicy: {
        quiet(config) {
          return new Promise((resolve) => calls.push({ config, resolve }));
        },
      },
    },
  };
  const exports = {};
  new Function("require", "exports", outputText)((name) => modules[name], exports);
  return {
    calls,
    cart: exports.useCartCount(),
    switchAccount(value) {
      token = value;
      revision.value++;
    },
  };
}

test("the navbar requests only the count and ignores an earlier page response", async () => {
  const { cart, calls } = fixture();
  const olderPage = cart.beginRead();
  const refreshing = cart.refresh();
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0].config, {
    url: "/dws/market/shoppingCardList",
    method: "post",
    data: { mode: "count" },
  });
  calls[0].resolve({ total: 107 });
  await refreshing;
  olderPage(20);
  assert.equal(cart.cartCount.value, 107);
});

test("a later page can update the badge and a tenant/session change clears it", async () => {
  const { cart, calls, switchAccount } = fixture();
  const olderCount = cart.refresh();
  const latestPage = cart.beginRead();
  latestPage(105);
  calls[0].resolve({ total: 20 });
  await olderCount;
  assert.equal(cart.cartCount.value, 105);

  const previousAccount = cart.beginRead();
  switchAccount("validation-account-b");
  previousAccount(102);
  assert.equal(cart.cartCount.value, 0);
  cart.beginRead()(-1);
  assert.equal(cart.cartCount.value, 0);

  switchAccount("");
  await cart.refresh();
  assert.equal(calls.length, 1);
});

test("closing a pending cart page still allows the navbar to receive its initial count", async () => {
  const { cart, calls } = fixture();
  const initialCount = cart.refresh();
  cart.beginRead();
  calls[0].resolve({ total: 24 });
  await initialCount;
  assert.equal(cart.cartCount.value, 24);
});
