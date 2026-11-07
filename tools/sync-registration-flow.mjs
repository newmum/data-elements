import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";

const args = process.argv.slice(2);
assert.ok(
  args.length === 0 || (args.length === 1 && args[0] === "--check"),
  "Usage: node tools/sync-registration-flow.mjs [--check]",
);

const components = [
  {
    sourceFile: "registration-flow.ts",
    componentFile: "登记组件(register-modal).vue",
    marker: "REGISTRATION FLOW",
    factory: "createRegistrationFlow",
  },
  {
    sourceFile: "registration-actions.ts",
    componentFile: "资产登记组件(asset-register)/登记中间件(register-body).vue",
    marker: "REGISTRATION ACTIONS",
    factory: "createRegistrationActionRunner",
  },
];

for (const { sourceFile, componentFile, marker, factory } of components) {
  const canonical = new URL(
    `../data-elements-chengtian/src/components/business/${sourceFile}`,
    import.meta.url,
  );
  const lowcode = new URL(
    `../lowcode/02.数据盘点/02.登记流程组件/${componentFile}`,
    import.meta.url,
  );
  const begin = `// BEGIN GENERATED ${marker}`;
  const end = `// END GENERATED ${marker}`;
  const declaration = `function ${factory}(`;
  const exportedFunction = `export ${declaration}`;
  const [source, component] = (
    await Promise.all([readFile(canonical, "utf8"), readFile(lowcode, "utf8")])
  ).map((content) => content.replace(/\r\n/g, "\n"));

  assert.equal(
    source.split(exportedFunction).length,
    2,
    `Expected one export for ${factory}`,
  );
  const embedded = source.replace(exportedFunction, declaration).trimEnd();
  assert.ok(
    !/^\s*(?:import|export)\b/m.test(embedded),
    `${sourceFile} must run inside old low-code hosts`,
  );
  assert.equal(
    component.split(begin).length,
    2,
    `Expected one start marker for ${componentFile}`,
  );
  assert.equal(
    component.split(end).length,
    2,
    `Expected one end marker for ${componentFile}`,
  );
  const startIndex = component.indexOf(begin);
  const endIndex = component.indexOf(end);
  assert.ok(
    startIndex < endIndex,
    `Generated markers are reversed in ${componentFile}`,
  );

  const updated = [
    component.slice(0, startIndex),
    `${begin}\n// 源文件 ${sourceFile}。修改后运行 node tools/sync-registration-flow.mjs。\n`,
    `${embedded}\n`,
    component.slice(endIndex),
  ].join("");
  if (args[0] === "--check") {
    assert.equal(
      component,
      updated,
      `Run node tools/sync-registration-flow.mjs and recompile ${componentFile}`,
    );
  } else if (component !== updated) {
    await writeFile(lowcode, updated, "utf8");
  }
}
