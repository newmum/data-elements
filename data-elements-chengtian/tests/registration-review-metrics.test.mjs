import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import { babelParse, parse } from "vue/compiler-sfc";

const source = readFileSync(
  new URL(
    "../../lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/04.登记内容审查(register-confirm).vue",
    import.meta.url
  ),
  "utf8"
);
const { descriptor, errors } = parse(source);
assert.deepEqual(errors, []);
const script = descriptor.scriptSetup.content;
const definitions = new Map();
for (const node of babelParse(script, { sourceType: "module" }).program.body) {
  if (node.type === "FunctionDeclaration") definitions.set(node.id.name, node);
  if (node.type === "VariableDeclaration") {
    for (const declaration of node.declarations) {
      if (declaration.id.type === "Identifier") definitions.set(declaration.id.name, node);
    }
  }
}

function declaration(name) {
  const node = definitions.get(name);
  assert.ok(node, name);
  return script.slice(node.start, node.end);
}

function install(scope, names) {
  const implementation = names.map(declaration).join("\n");
  vm.runInNewContext(
    implementation + "\nObject.assign(globalThis, {" + names.join(", ") + "});",
    scope
  );
}

function context(rows = []) {
  const scope = {
    computed: (read) => ({
      get value() {
        return read();
      },
    }),
    realtimeTables: { value: rows },
    physicalTableTotal: { value: 7177 },
    fieldMap: { value: {} },
    catalogsByTableKey: { value: new Map(rows.map((row) => ["id:" + row.tid, row])) },
    tableChangeSnapshot: { value: null },
    tableChangeLoading: { value: false },
    tableChangeError: { value: "" },
  };
  install(scope, [
    "firstNonEmpty",
    "tableChineseName",
    "tableRows",
    "uniqueTables",
    "isTableAnnotated",
    "isCompletedTableRegistration",
    "annotatedTables",
    "tables",
    "catalogFor",
    "governanceConfig",
    "fieldsFor",
    "timeRoleLabels",
    "normalizeTimeRoles",
    "timeRolesFor",
    "timeRoleFieldFor",
    "missingRequiredTimeRoles",
    "businessLogTables",
    "timestampRequiredTables",
    "timeRoleCompleteTableCount",
    "standardOptions",
    "fieldStandardFormat",
    "standardFormatRows",
    "standardMatchedFieldCount",
    "markedCoreCount",
    "annotationTypeTotal",
    "physicalTableCount",
    "isPendingBusinessType",
    "uncertainTables",
    "completedRegistrationTableCount",
    "pendingRegistrationTableCount",
    "applyTableChangeSnapshot",
    "tableChangeKind",
    "tableChangeSummary",
  ]);
  return scope;
}

function row(tid, businessType, config = {}) {
  return {
    tid,
    tableName: "OWNER.T_" + tid,
    annotated: 1,
    assetStatus: 2,
    businessType,
    fieldGovernanceConfig: JSON.stringify(config),
  };
}

test("all current annotation types count and deleted rows are excluded", () => {
  const scope = context([
    row("1", "字典表"),
    row("2", "业务表"),
    row("3", "日志表"),
    row("4", "过程表"),
    row("5", "备份表"),
    { ...row("6", "业务表"), isDel: 1 },
  ]);
  assert.equal(scope.markedCoreCount.value, 5);
  assert.equal(scope.annotationTypeTotal.value, 5);
});

test("annotation and completion use separate statuses", () => {
  const scope = context([{ ...row("1", "业务表"), assetStatus: 0 }, row("2", "过程表")]);
  assert.equal(scope.markedCoreCount.value, 2);
  assert.equal(scope.completedRegistrationTableCount.value, 1);
});

test("saved field governance supplies time and format metrics before lazy field loading", () => {
  const scope = context([
    row("1", "业务表", {
      fields: [
        { columnName: "CARD_NO", standardField: "SFZH" },
        {
          columnName: "CREATE_TIME",
          timeRoles: ["timestamp", "business"],
          standardField: "DATETIME",
        },
        { columnName: "UPDATE_TIME", standardField: "DATETIME" },
        { columnName: "NOTES" },
      ],
    }),
  ]);
  assert.equal(scope.timeRoleCompleteTableCount.value, 1);
  assert.equal(scope.standardMatchedFieldCount.value, 3);
  assert.deepEqual(
    Array.from(scope.standardFormatRows.value, ({ label, count }) => [label, count]),
    [
      ["时间", 2],
      ["身份证", 1],
    ]
  );
  assert.deepEqual(Object.keys(scope.fieldMap.value), []);
});

test("the saved timestamp field satisfies the requirement and dictionary tables are exempt", () => {
  const dictionary = row("2", "字典表");
  const scope = context([{ ...row("1", "业务表"), timestampField: "UPDATE_TIME" }, dictionary]);
  assert.equal(scope.timeRoleCompleteTableCount.value, 1);
  assert.equal(scope.timestampRequiredTables.value.length, 1);
  assert.equal(scope.missingRequiredTimeRoles(dictionary).length, 0);
});

test("pending registration uses the physical snapshot total and excludes deferred tables", () => {
  const rows = Array.from({ length: 301 }, (_, index) =>
    row(String(index), index < 105 ? "业务表" : "过程表", {
      fields: [
        {
          columnName: "UPDATE_TIME",
          standardField: "DATETIME",
          timeRoles: index < 105 ? ["timestamp"] : [],
        },
        ...(index < 170 ? [{ columnName: "CARD_NO", standardField: "SFZH" }] : []),
      ],
    })
  );
  const scope = context(rows);
  assert.equal(scope.physicalTableCount.value, 7177);
  assert.equal(scope.pendingRegistrationTableCount.value, 6876);
  assert.equal(scope.markedCoreCount.value, 301);
  assert.equal(scope.completedRegistrationTableCount.value, 301);
  assert.equal(scope.timestampRequiredTables.value.length, 105);
  assert.equal(scope.timeRoleCompleteTableCount.value, 105);
  assert.equal(scope.standardMatchedFieldCount.value, 471);

  rows.push({ ...row("deferred", "暂不处理"), assetStatus: 0 });
  assert.equal(scope.uncertainTables.value.length, 1);
  assert.equal(scope.pendingRegistrationTableCount.value, 6875);
});

test("collection changes distinguish verified additions from suspected deletions", () => {
  const scope = context();
  scope.applyTableChangeSnapshot({ deletedCount: 302, addedCount: 1 });
  const summary = scope.tableChangeSummary.value;
  assert.equal(summary.total, 303);
  assert.equal(summary.added, 1);
  assert.equal(summary.pending, 302);
  assert.equal(summary.deleted, 0);
  assert.equal(summary.modified, 0);
  assert.match(summary.hint, /疑似删除 302 张（待核对）/);
});

test("review reads persisted registration, collection totals and changes independently", async () => {
  const calls = [];
  let resolveStatus;
  const statusResponse = new Promise((resolve) => {
    resolveStatus = resolve;
  });
  const savedRows = [
    row("completed", "业务表"),
    { ...row("pending", "业务表"), annotated: 0, assetStatus: 0 },
  ];
  const scope = Object.assign(context(), {
    sourceDetailLoading: { value: false },
    tableListLoading: { value: false },
    reviewSummaryError: { value: "" },
    sources: { value: [{ tid: "source-a", accessMode: "explore" }] },
    sourceDetail: { value: null },
    sourceTid: { value: "source-a" },
    sourceDetailRequestId: 1,
    requestWithTimeout: (read) => read(),
    writeReviewSummaryCache: () => {},
    console: { warn: (...args) => assert.fail(args.join(" ")) },
    $common: {
      post: async (url, params) => {
        calls.push({ url, params: { ...params } });
        assert.equal(url, "/dst/database/detail");
        return { tid: "source-a" };
      },
      get: async (url, params) => {
        calls.push({ url, params: { ...params } });
        if (url === "/dst/database/metadata/tables") return { list: savedRows };
        if (url === "/dst/database/metadata/tables/snapshot")
          return { physicalTotal: 7177, total: 17 };
        if (url === "/dst/database/metadata/tables/collection/status") return statusResponse;
        assert.fail("unexpected read " + url);
      },
    },
  });
  install(scope, [
    "parseObject",
    "isProxyAccessSource",
    "isUnstructuredPushSource",
    "refreshReviewSummary",
    "refreshTableChangeSummary",
  ]);
  await scope.refreshReviewSummary("source-a", 1, true);
  assert.deepEqual(calls, [
    { url: "/dst/database/detail", params: { tid: "source-a" } },
    { url: "/dst/database/metadata/tables", params: { dbId: "source-a", includeGovernance: true } },
    {
      url: "/dst/database/metadata/tables/snapshot",
      params: { dbId: "source-a", pageNo: 1, pageSize: 1 },
    },
    { url: "/dst/database/metadata/tables/collection/status", params: { dbId: "source-a" } },
  ]);
  assert.equal(scope.physicalTableCount.value, 7177);
  assert.equal(scope.realtimeTables.value.length, 2);
  assert.equal(scope.completedRegistrationTableCount.value, 1);
  assert.equal(scope.sourceDetailLoading.value, false);
  assert.equal(scope.tableListLoading.value, false);
  assert.equal(scope.reviewSummaryError.value, "");
  assert.equal(scope.tableChangeLoading.value, true);
  assert.match(
    declaration("drainFieldLoadQueue"),
    /tid:\s*ids\.join\(["'],["']\),\s*snapshotOnly:\s*true/
  );

  resolveStatus({ deletedCount: 2, addedCount: 1 });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(scope.tableChangeLoading.value, false);
  assert.equal(scope.tableChangeSummary.value.total, 3);
});
