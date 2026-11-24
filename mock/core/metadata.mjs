import { assert, requireSources, paginate, visibleSources } from "./data.mjs";

function sourceSnapshot(database, session, sourceId) {
  assert(/^[A-Za-z0-9_-]{1,64}$/.test(sourceId), 500, "dbId 格式不正确");
  assert(visibleSources(database, session).some((source) => source.tid === sourceId),
    500, "数据源不存在或无访问权限");
  return database.tables.filter((row) => row.tenantId === session.tenantId
    && row.datasourceId === sourceId && !row.isDel);
}

function collectionSort(rows, fields, input) {
  const field = String(input.sortField ?? "").trim() || "tableName";
  const direction = String(input.sortDir ?? "").trim().toLowerCase() || "asc";
  assert(Object.hasOwn(fields, field), 500, `不支持的排序字段：${field}`);
  assert(["asc", "desc"].includes(direction), 500, "排序方向只能是 asc 或 desc");
  return rows.toSorted((left, right) => {
    const a = fields[field](left), b = fields[field](right);
    const compared = compareFields(a, b, direction);
    return compared || String(left.tid).localeCompare(String(right.tid));
  });
}

function compareFields(a, b, direction) {
  if (a == null || b == null) return (a == null) - (b == null);
  const compared = typeof a === "number" && typeof b === "number" ? a - b : String(a).localeCompare(String(b), "zh-CN");
  return direction === "asc" ? compared : -compared;
}

const collectionFields = { tableName: (row) => row.tableName, fieldCount: (row) => row.fieldCount };

function matchesCollectionKeyword(value, keyword) {
  let offset = 0;
  const text = String(value ?? "").toLowerCase();
  for (const segment of keyword.split("%").filter(Boolean)) {
    const characters = segment.split("");
    while (offset + segment.length <= text.length &&
      !characters.every((char, index) => char === "_" || char === text[offset + index])) offset++;
    if (offset + segment.length > text.length) return false;
    offset += segment.length;
  }
  return true;
}

export function collectionHistory(database, session, operation, query) {
  const values = (key) => query[key] ?? query[`${key}[]`];
  const integer = (key, fallback, type = "int") => {
    const input = values(key);
    const first = Array.isArray(input) ? input[0] : input;
    if (first == null || first === "") return fallback;
    const text = String(first).replace(/\s/g, "");
    const value = Number.parseInt(text, /^[+-]?0x/i.test(text) ? 16 : 10);
    assert(/^[+-]?(?:\d+|0x[0-9a-f]+)$/i.test(text) && value >= -2147483648 && value <= 2147483647, 20002,
      `参数[${key}]类型错误，期望类型: ${type}`);
    return value;
  };
  const snapshot = operation === "snapshot";
  assert(values("dbId") != null, 20001, "缺少必要参数: dbId");
  const pageNo = Math.max(1, integer("pageNo", 1));
  const pageSize = snapshot ? Math.max(1, Math.min(1000, integer("pageSize", 100)))
    : Math.max(1, Math.min(100, integer("pageSize", 20)));
  const offset = snapshot ? Math.max(0, integer("offset", (pageNo - 1) * pageSize, "Integer")) : 0;
  const existing = sourceSnapshot(database, session, String(values("dbId")).trim());
  const fields = { ...collectionFields, name: collectionFields.tableName,
    tableNameCn: (row) => row.tableNameCn || row.tableName, recordCount: (row) => row.recordCount };
  if (!snapshot) {
    // 当前场景只包含共享表快照，尚未记录历史缺失表。
    collectionSort([], fields, { sortField: values("sortField"), sortDir: values("sortDir") });
    return { pageNo, pageSize, total: 0, rows: [], list: [] };
  }
  fields.createdAt = fields.createdTime = (row) => row.createdTime;
  fields.updatedAt = fields.updatedTime = (row) => row.updatedTime;
  const keyword = String(values("keyword") ?? "").toLowerCase();
  const type = String(values("businessType") ?? "").trim();
  const candidates = existing.filter((row) => !Number(row.annotated) &&
    (!type || type === "全部" || row.businessType === type) &&
    (!keyword.trim() || [row.tableName, row.tableComment ?? row.tableNameCn]
      .some((value) => matchesCollectionKeyword(value, keyword))));
  const sorted = collectionSort(candidates, fields, { sortField: values("sortField"), sortDir: values("sortDir") });
  const rows = sorted.slice(offset, offset + pageSize).map((row) => {
    const name = row.tableName, comment = row.tableComment || row.tableNameCn || name;
    return { tid: row.tid, tableId: row.tid, dbId: row.datasourceId, datasourceId: row.datasourceId,
      tableName: name, tableNameEn: row.tableNameEn || name, sourceTableName: name, tableNameCn: row.tableNameCn || comment,
      tableComment: comment, physicalTableComment: comment, savedResourceName: comment, tableType: row.tableType || "",
      businessType: row.businessType || "", businessTypeReason: row.businessTypeReason || "",
      recordCount: row.recordCount || 0, fieldCount: row.fieldCount || 0, storageSize: row.storageSize || "",
      totalSizeFormatted: row.totalSizeFormatted || row.storageSize || "", totalSizeBytes: row.totalSizeBytes || 0,
      annotated: false, registered: false };
  });
  const total = sorted.length;
  return { pageNo, pageSize, offset: Math.min(offset, total), total, unmarkedTotal: total,
    physicalTotal: total + existing.filter((row) => Number(row.annotated) === 1).length, rows, list: rows };
}

export function collectionStart(database, session, body) {
  const sourceId = String(body.dbId ?? "").trim();
  const tables = sourceSnapshot(database, session, sourceId);
  const mode = ["REFRESH", "REEXPLORE"].includes(String(body.mode ?? "").trim().toUpperCase()) ? "REFRESH" : "INITIAL";
  if (body.dryRun === true) return { dryRun: true, datasourceId: sourceId, mode };
  const jobId = `demo-collection-${sourceId}`;
  const job = { exists: true, jobId, datasourceId: sourceId, mode, status: "SUCCEEDED", phase: "COMPLETED",
    scannedCount: tables.length, persistedCount: 0, totalCount: tables.length, addedCount: 0, unchangedCount: tables.length,
    deletedCount: 0, startedAt: null, finishedAt: null, savedBeforeCount: tables.length,
    previousSuccessfulAt: null, error: "", progressPercent: 100 };
  database.collectionJobs = { ...database.collectionJobs, [sourceId]: job };
  return { ...job, status: "RUNNING", phase: "QUEUED", scannedCount: 0, totalCount: 0, unchangedCount: 0,
    savedBeforeCount: 0, progressPercent: 8 };
}

export function collectionRename(database, session, body) {
  const sourceId = String(body.dbId ?? "").trim();
  const tableId = String(body.tableId ?? "").trim();
  const tableName = String(body.tableName ?? "").trim();
  assert(/^[A-Za-z0-9_-]{1,64}$/.test(sourceId), 500, "dbId 格式不正确");
  assert(tableId, 500, "缺少登记表标识");
  assert(tableName, 500, "数据表名不能为空");
  assert(tableName.length <= 255, 500, "数据表名不能超过255个字符");
  const visible = visibleSources(database, session).some((source) => source.tid === sourceId);
  assert(visible && sourceSnapshot(database, session, sourceId).some((row) => row.tid === tableId),
    500, "登记表不存在或无访问权限");
  // 当前场景的采集复用已有表快照，没有已确认的历史缺失项。
  assert(false, 500, "该表不在当前变更列表中，请重新探查后再修改");
}

export function collectionReviewOrder(database, session, body) {
  const sourceId = String(body.dbId ?? "").trim();
  const existing = sourceSnapshot(database, session, sourceId);
  const source = database.sources.find((row) => row.tid === sourceId && row.tenantId === session.tenantId && !row.isDel);
  const oracle = ["oracle", "oceanbaseoracle"].includes(String(source?.dbType ?? "").replaceAll(/[_\s-]/g, "").toLowerCase());
  const pageNo = Number(body.pageNo ?? 1), size = Number(body.pageSize ?? 20);
  assert(Number.isInteger(pageNo) && pageNo >= 1 && pageNo <= 100000 && Number.isInteger(size) && size >= 1 && size <= 100,
    500, "分页参数无效，每页最多100张表");
  const ranked = existing.filter((row) => row.tableName?.trim()).sort((left, right) =>
    (Number(right.annotated || 0) - Number(left.annotated || 0)) ||
    compareFields(left.updatedTime, right.updatedTime, "desc") || String(right.tid).localeCompare(String(left.tid)));
  const distinct = new Map();
  for (const row of ranked) {
    const key = oracle ? oracleObjectIdentity(row.tableName) : row.tableName.toLowerCase();
    if (!distinct.has(key)) distinct.set(key, row);
  }
  const fields = { ...collectionFields, name: (row) => row.tableNameCn || row.tableComment || row.tableName,
    createdTime: (row) => row.createdTime, updatedTime: (row) => row.updatedTime };
  const rows = collectionSort([...distinct.values()], fields, body);
  const start = (pageNo - 1) * size;
  if (body.allTables === true) {
    assert(!Object.hasOwn(body, "names"), 500, "完整范围与筛选范围不能同时指定");
    return { keys: rows.slice(start, start + size).map((row) => row.tableName.trim()), total: rows.length };
  }
  assert(Array.isArray(body.names), 500, "请选择有效的数据表审查范围");
  const names = body.names.map((name) => String(name ?? "").trim());
  const requested = new Set(names);
  assert(names.every((name) => name && name.length <= 256) && requested.size === names.length,
    500, "数据表名称为空或重复");
  const matched = rows.filter((row) => requested.has(row.tableName));
  assert(matched.length === names.length, 500, "审查范围包含不存在或无权访问的数据表，请刷新清单");
  return { keys: matched.slice(start, start + size).map((row) => row.tableName), total: matched.length };
}

function oracleObjectIdentity(reference) {
  const text = String(reference).trim(), parts = []; let cursor = 0;
  while (cursor < text.length) {
    while (/\s/u.test(text[cursor] ?? "") && cursor < text.length) cursor += 1;
    let value = "";
    if (text[cursor] === '"') {
      cursor += 1; let closed = false;
      while (cursor < text.length) {
        const character = text[cursor++];
        if (character !== '"') value += character;
        else if (text[cursor] === '"') { value += '"'; cursor += 1; }
        else { closed = true; break; }
      }
      assert(closed, 500, "物理表名引号未闭合");
    } else {
      while (cursor < text.length && text[cursor] !== ".") value += text[cursor++];
      value = value.trim().toUpperCase(); assert(/^[\p{L}_][\p{L}\p{N}_$#]*$/u.test(value), 500, "物理表名格式不正确");
    }
    assert(value && !/[\u0000-\u001f]/u.test(value), 500, "物理表名格式不正确"); parts.push(value);
    while (/\s/u.test(text[cursor] ?? "") && cursor < text.length) cursor += 1;
    if (cursor < text.length) { assert(text[cursor++] === "." && cursor < text.length, 500, "物理表名格式不正确"); }
  }
  assert(parts.length > 0 && parts.length <= 2, 500, "仅支持表名或模式.表名");
  return JSON.stringify(parts);
}

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
      created_at: `2026-11-${String((index % 7) + 1).padStart(2, "0")} 09:30:00${index % 3 === 0 ? ".986" : ""}`,
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
