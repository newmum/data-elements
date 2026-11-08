import { assert, requireSources, paginate } from "./data.mjs";

export function requireTable(database, session, id) {
  const table = database.tables.find(
    (row) => row.tid === id && row.tenantId === session.tenantId,
  );
  assert(table, 404, "数据表不存在或不在当前数据范围");
  requireSources(database, session, table.datasourceId);
  return table;
}

/** 元数据平台与风驰共用字段定义，标识只由已授权的表资产派生。 */
export function tableColumns(table) {
  return [
    ["id", "int", "主键", false],
    ["name", "varchar(100)", "业务名称", false],
    ["status", "varchar(20)", "业务状态", false],
    ["amount", "decimal(12,2)", "指标值", true],
    ["created_at", "datetime", "创建时间", false],
  ].map(([name, type, label, nullable], index) => ({
    tid: `${table.tid}-column-${index}`,
    tableId: table.tid,
    columnName: name,
    columnNameCn: label,
    columnComment: label,
    remarks: label,
    dataType: type,
    columnType: type,
    typeName: type,
    nullable: nullable ? 1 : 0,
    isNullable: nullable ? "YES" : "NO",
    primaryKey: index === 0 ? 1 : 0,
    isPrimaryKey: index === 0 ? 1 : 0,
    columnSize: name === "name" ? 100 : name === "status" ? 20 : null,
    ordinalPosition: index + 1,
    defaultValue: null,
  }));
}

export function tableRows(table) {
  return Array.from(
    { length: table.objectType === "VIEW" ? 120 : table.recordCount },
    (_, index) => ({
      id: index + 1,
      name: `${table.baseTableComment || table.tableComment} ${String(index + 1).padStart(3, "0")}`,
      status: index % 3 === 0 ? "inactive" : "active",
      amount: index % 11 === 0 ? null : (index + 1) * 12.5,
      created_at: `2026-11-${String((index % 7) + 1).padStart(2, "0")} 09:30:00`,
    }),
  ).filter((row) => table.objectType !== "VIEW" || row.status === "active");
}

export function previewTable(table, body = {}) {
  const fields = tableColumns(table),
    names = fields.map((row) => row.columnName);
  let rows = tableRows(table);
  assert(
    !body.conditions ||
      (Array.isArray(body.conditions) && body.conditions.length <= 30),
    400,
    "筛选条件格式无效",
  );
  for (const condition of body.conditions || []) {
    const field = condition.field || condition.column,
      operation = String(
        condition.type || condition.operator || "eq",
      ).toLowerCase();
    assert(names.includes(field), 400, "筛选字段不存在");
    assert(
      [
        "eq",
        "ne",
        "gt",
        "ge",
        "gte",
        "lt",
        "le",
        "lte",
        "like",
        "in",
        "isnull",
        "isnotnull",
      ].includes(operation),
      400,
      "不支持的筛选条件",
    );
    rows = rows.filter((row) => {
      const value = row[field],
        other = condition.value;
      if (operation === "isnull") return value === null;
      if (operation === "isnotnull") return value !== null;
      if (value === null) return false;
      if (operation === "like") {
        const pattern = String(other),
          text = String(value),
          part = pattern.replace(/^%|%$/g, "");
        return pattern.startsWith("%") && pattern.endsWith("%")
          ? text.includes(part)
          : pattern.startsWith("%")
            ? text.endsWith(part)
            : pattern.endsWith("%")
              ? text.startsWith(part)
              : text === part;
      }
      if (operation === "in")
        return Array.isArray(other) && other.includes(value);
      if (operation === "eq") return value === other;
      if (operation === "ne") return value !== other;
      if (operation === "gt") return value > other;
      if (["ge", "gte"].includes(operation)) return value >= other;
      if (operation === "lt") return value < other;
      return value <= other;
    });
  }
  const selected = body.columns?.length ? body.columns : names;
  assert(
    Array.isArray(selected) && selected.every((name) => names.includes(name)),
    400,
    "预览字段不存在",
  );
  const page = paginate(
    rows.map((row) => ({ ...row, tid: String(row.id) })),
    {
      ...body,
      sortField: body.sortField || "id",
      sortDir: body.sortDir || body.sortDirection || "asc",
    },
    names,
  );
  return {
    rows: page.list.map((row) =>
      Object.fromEntries(selected.map((name) => [name, row[name]])),
    ),
    columns: selected,
    columnLabels: Object.fromEntries(
      fields.map((row) => [row.columnName, row.columnNameCn]),
    ),
    columnTypes: Object.fromEntries(
      fields.map((row) => [row.columnName, row.dataType]),
    ),
    total: page.total,
    totalKnown: true,
    hasMore: page.pageNum * page.pageSize < page.total,
    pageNo: page.pageNum,
    pageSize: page.pageSize,
  };
}
