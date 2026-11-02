import assert from "node:assert/strict";
import fs from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import vm from "node:vm";

const require = createRequire(new URL("../../data-elements-chengtian/package.json", import.meta.url));
const ts = require("typescript");
const source = fs.readFileSync(new URL("../../lowcode/06.数据服务/数据服务(data-serve).vue", import.meta.url), "utf8");
const script = source.match(/<script[^>]*>([\s\S]*?)<\/script>/)[1];
const ast = ts.createSourceFile("service-list.ts", script, ts.ScriptTarget.Latest, true);
const declarations = ["parseMaybeJson", "extractPayload", "normalizeServiceRow", "fetchServices"].map((name) => {
  for (const statement of ast.statements) {
    if (!ts.isVariableStatement(statement)) continue;
    const declaration = statement.declarationList.declarations.find((item) => item.name.getText(ast) === name);
    if (declaration) return `const ${declaration.getText(ast)};`;
  }
  throw new Error(`Missing service-list function: ${name}`);
}).join("\n");

for (const count of [20, 100]) test(`${count} displayed services need one department-scoped request and no row enrichment`, async () => {
  let requests = 0;
  const scope = {
    filters: { keyword: "验证", status: "draft" }, currentGroup: { value: { id: "group-a" } },
    $common: { post: async (url, body) => {
      requests++;
      assert.equal(url, "/dws/flowserve/gateway/api/list");
      assert.equal(body.departmentOnly, true);
      assert.equal(body.pageNum, 2);
      assert.equal(body.pageSize, count);
      assert.equal(body.groupId, "group-a");
      assert.equal(body.keyword, "验证");
      assert.equal(body.status, "draft");
      assert.equal(body.sortField, "updatedTime");
      assert.equal(Object.hasOwn(body, "method"), false);
      assert.equal(Object.hasOwn(body, "tenantId"), false);
      assert.equal(Object.hasOwn(body, "orgId"), false);
      return { data: JSON.stringify({ total: count, list: Array.from({ length: count }, (_, index) => ({
        tid: `service-${index}`, service_name: `服务${index}`, service_code: `validation_${index}`,
        method: "POST", publish_address_full: `/validation/${index}`, status: "draft", updated_time: "2026-11-01 08:00:00",
      })) }) };
    } },
  };
  vm.createContext(scope);
  vm.runInContext(`${declarations}\nthis.fetch = fetchServices;`, scope);
  const result = await scope.fetch({ pageNo: 2, pageSize: count, sortField: "updatedTime", sortDir: "desc" });
  assert.equal(requests, 1);
  assert.equal(result.list.length, count);
  assert.equal(result.total, count);
  assert.equal(result.list[0].serviceName, "服务0");
  assert.equal(result.list[0].serviceCode, "validation_0");
  assert.equal(result.list[0].publishAddressFull, "/validation/0");
  assert.equal(result.list[0].method, "POST");
  assert.equal(result.list[0].status, "draft");
  assert.equal(result.list[0].updatedTime, "2026-11-01 08:00:00");
});
