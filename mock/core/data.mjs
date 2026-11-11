/** mock 数据范围先过滤再分页；与真实接口一致，不接受请求体扩大当前会话范围。 */
export class MockError extends Error {
  constructor(status, message, code = String(status)) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function assert(condition, status, message) {
  if (!condition) throw new MockError(status, message);
}

export function visibleSources(database, session) {
  assert(
    session?.tenantId === database.tenantId,
    401,
    "缺少有效的本地场景会话，请重新登录",
  );
  return database.sources.filter(
    (row) =>
      !row.isDel &&
      row.tenantId === session.tenantId &&
      (session.scope === "ALL" ||
        (session.scope === "ORG" && Boolean(session.orgId) && row.orgId === session.orgId) ||
        (session.scope === "OWNER" && row.createdBy === session.userId)),
  );
}

export function requireSources(database, session, values) {
  const ids = Array.isArray(values)
    ? [...new Set(values.map(String))]
    : [String(values || "")];
  assert(
    ids.length > 0 && ids.length <= 100 && ids.every(Boolean),
    400,
    "数据源标识须为 1–100 项",
  );
  const allowed = new Map(
    visibleSources(database, session).map((row) => [row.tid, row]),
  );
  assert(
    ids.every((id) => allowed.has(id)),
    404,
    "数据源不存在或不在当前数据范围",
  );
  return ids.map((id) => allowed.get(id));
}

export function paginate(
  rows,
  params = {},
  allowedSort = [
    "dbName",
    "updatedTime",
    "tableName",
    "tableComment",
    "columnNum",
  ],
) {
  const pageNum = Number(params.pageNum ?? params.pageNo ?? 1),
    pageSize = Number(params.pageSize ?? 20);
  assert(
    Number.isInteger(pageNum) &&
      pageNum >= 1 &&
      Number.isInteger(pageSize) &&
      pageSize >= 1 &&
      pageSize <= 100,
    400,
    "分页参数无效，单页最多 100 条",
  );
  const field = params.sortField || "updatedTime",
    direction = String(params.sortDir || "desc").toLowerCase();
  assert(allowedSort.includes(field), 400, "不支持的排序字段");
  assert(
    ["asc", "desc"].includes(direction),
    400,
    "排序方向必须为 asc 或 desc",
  );
  const sorted = [...rows].sort((a, b) => {
    const left = a[field],
      right = b[field];
    if (left == null && right != null) return 1;
    if (right == null && left != null) return -1;
    const compared =
      typeof left === "number" && typeof right === "number"
        ? left - right
        : String(left ?? "").localeCompare(String(right ?? ""), "zh-CN");
    return (
      (direction === "asc" ? compared : -compared) ||
      String(a.tid).localeCompare(String(b.tid))
    );
  });
  return {
    list: sorted.slice((pageNum - 1) * pageSize, pageNum * pageSize),
    total: rows.length,
    pageNum,
    pageSize,
  };
}

export function sourceTypeKey(row) {
  const mode = String(row.accessMode || "").toLowerCase();
  if (mode === "proxy") return "mode:proxy";
  if (["receive", "push"].includes(mode)) return "mode:receive";
  if (["upload", "report"].includes(mode) ||
      (row.dbType === "api" && String(row.showConnect) === "0" && !["capture", "pull"].includes(mode))) {
    return "mode:upload";
  }
  return row.dbType;
}

export function filterSources(rows, params = {}) {
  // 数据探查沿用 conditions 契约；卡片页使用平铺参数，两者只缩小授权结果。
  params = { ...params };
  if (params.conditions !== undefined) {
    assert(
      Array.isArray(params.conditions) && params.conditions.length <= 30,
      400,
      "筛选条件无效",
    );
    for (const condition of params.conditions) {
      assert(
        condition &&
          [
            "assetType",
            "keyword",
            "dbType",
            "registrationState",
            "appId",
            "orgId",
          ].includes(condition.field),
        400,
        "不支持的筛选字段",
      );
      if (condition.field === "assetType") {
        assert(condition.value === "db", 400, "仅支持数据源查询");
        continue;
      }
      if (condition.field === "orgId" && condition.type === "in") {
        assert(
          Array.isArray(condition.value) && condition.value.length <= 1000,
          400,
          "组织筛选无效",
        );
        params.orgIds = condition.value;
      } else params[condition.field] = condition.value;
    }
  }
  let result = rows;
  const keyword = String(params.keyword || params.keywords || "")
    .trim()
    .toLowerCase();
  if (keyword)
    result = result.filter((row) =>
      [row.dbName, row.appName, row.database, row.assetDesc].some((value) =>
        String(value || "")
          .toLowerCase()
          .includes(keyword),
      ),
    );
  if (params.appId)
    result = result.filter((row) => row.appId === String(params.appId));
  if (params.dbType || params.datasourceType)
    result = result.filter(
      (row) => row.dbType === String(params.dbType || params.datasourceType),
    );
  if (params.typeKey)
    result = result.filter((row) => sourceTypeKey(row) === params.typeKey);
  if (params.orgIds?.length)
    result = result.filter((row) => params.orgIds.includes(row.orgId));
  if (params.orgId) result = result.filter((row) => row.orgId === params.orgId);
  if (params.filter === "changed")
    result = result.filter((row) => row.changedTableNum > 0);
  if (params.filter === "unannotated")
    result = result.filter((row) => row.unannotatedTableNum > 0);
  if (params.filter === "pending" || params.registrationState === "pending")
    result = result.filter((row) => row.assetStatus === 0);
  if (
    params.filter === "incomplete" ||
    params.registrationState === "incomplete"
  )
    result = result.filter((row) => row.assetStatus === 1);
  if (params.filter === "completed" || params.registrationState === "completed")
    result = result.filter((row) => row.assetStatus === 2);
  return result;
}
