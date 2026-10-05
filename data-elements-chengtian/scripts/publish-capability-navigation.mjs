import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import sm from "sm-crypto";

// Explicit configuration only. Credentials and session tokens are never
// written to source, exported component artifacts or navigation URLs.
const account = process.env.NAV_PUBLISH_ACCOUNT;
const password = process.env.NAV_PUBLISH_PASSWORD;
assert.ok(account && password, "NAV_PUBLISH_ACCOUNT and NAV_PUBLISH_PASSWORD are required");
const base = process.env.NAV_PUBLISH_API || "http://localhost:3000/dev-api";
const directory = new URL("../../data/working/", import.meta.url);
const tid = "2009111573765361664";
let token;
async function request(path, body = {}, method = "POST") {
  const response = await fetch(base + path, {
    method, headers: { "Content-Type": "application/json", ...(token ? { token } : {}) },
    ...(method === "GET" ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(120_000),
  });
  assert.ok(response.ok, `HTTP ${response.status}: ${path}`);
  const result = await response.json();
  assert.equal(Number(result.code), 0, `${path}: ${result.message || result.msg}`);
  return result.data;
}
token = (await request("/portal/login", { account, password: sm.sm3(password), appId: "1995678661281710081" })).token;
assert.ok(token, "Platform login did not return a session");
const tenant = await request("/sym/tenant/current", {}, "GET");
assert.ok(tenant, "Cannot resolve the authenticated platform context");
console.log("Authenticated platform context verified; publishing only the shared global-navigation component.");
const before = readFileSync(new URL("backups/global-nav-menu-20261003/before.vue", directory), "utf8");
const sourceCode = readFileSync(new URL("global-nav.vue", directory), "utf8");
const compileJs = readFileSync(new URL("global-nav.compile.js", directory), "utf8");
const compileCss = readFileSync(new URL("global-nav.compile.css", directory), "utf8");
const onlineSource = await request(`/sym/component?action=getSourceCode&tid=${tid}`);
let updated = false;
if (onlineSource !== sourceCode) {
  assert.equal(onlineSource, before, "Online component changed after backup; stopped to preserve concurrent work");
  await request("/sym/component?action=saveCode", { tid, sourceCode, compileJs, compileCss });
  await request("/sym/component/cache/refresh");
  updated = true;
}
assert.equal(await request(`/sym/component?action=getSourceCode&tid=${tid}`), sourceCode);
const rows = await request("/sym/component?action=list");
const runtime = rows.find((row) => row.name === "global-nav");
assert.ok(runtime, "Global navigation missing from released component list");
assert.equal(runtime.compileJs, compileJs);
assert.equal(runtime.compileCss, compileCss);
console.log(`PASS: shared global navigation ${updated ? "published and cache refreshed" : "already current"}; source/compiled JS/CSS verified byte-for-byte through the platform proxy.`);
