import { assert, MockError } from "./data.mjs";
import { createLogScenario, logKinds, logResults } from "../scenarios/logs.mjs";

const clone = (value) => structuredClone(value);
const storageLocks = new WeakMap();
const day = 86400000;
const eventSorts = ["event_time", "actor_name", "action_code", "duration_ms", "total_count", "success_count", "failure_count"];
const oldNames = ["id", "tid", "taskId", "sourceTableId", "targetTableId", "targetDbId", "tableId", "datasourceId", "pipelineId", "dryRun", "batchDryRun", "deleteTargetTable", "confirmUnlinkedTarget", "checkPhysicalExistence", "taskName", "deletedTaskId", "deletedTaskName"];
const booleanNames = ["dryRun", "batchDryRun", "deleteTargetTable", "confirmUnlinkedTarget", "checkPhysicalExistence"];
const text = (value, max = 100) => {
  assert(value == null || ["string", "number", "boolean"].includes(typeof value), 400, "日志查询内容格式无效");
  const result = String(value ?? "");
  assert(result.length <= max, 400, "日志查询内容过长");
  return result;
};
const integer = (value, fallback, max) => {
  const n = value == null ? fallback : Number(value);
  assert(/^\d+$/.test(String(value ?? fallback)) && Number.isSafeInteger(n) && n >= 1 && n <= max, 400, "分页或数量参数无效");
  return n;
};
const nanos = (milliseconds) => BigInt(milliseconds) * 1000000n;
function instant(value, fallback) {
  if (value == null || typeof value === "string" && value.trim() === "") return fallback;
  const parts = typeof value === "string" && /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$/i.exec(value);
  assert(parts, 400, "日志时间格式无效，请使用带时区的时间");
  const [, year, month, date, hour, minute, second, fraction = "", zone] = parts;
  const dayStart = new Date(`${year}-${month}-${date}T00:00:00Z`);
  const offset = zone.toUpperCase() === "Z" ? 0 : (Number(zone.slice(1, 3)) * 60 + Number(zone.slice(4))) * (zone[0] === "+" ? 1 : -1);
  assert(Number.isFinite(dayStart.getTime()) && dayStart.toISOString().slice(0, 10) === `${year}-${month}-${date}` &&
    Number(hour) < 24 && Number(minute) < 60 && Number(second) < 60 && Math.abs(offset) <= 1080 &&
    (zone.toUpperCase() === "Z" || Number(zone.slice(4)) < 60), 400, "日志时间格式无效，请使用带时区的时间");
  // 使用纳秒整数比较，避免 Date.parse 的无效日期归一化及亚毫秒边界丢失。
  return nanos(dayStart.getTime() + ((Number(hour) * 60 + Number(minute) - offset) * 60 + Number(second)) * 1000) + BigInt(fraction.padEnd(9, "0"));
}
function instantText(value) {
  const seconds = value < 0n ? (value - 999999999n) / 1000000000n : value / 1000000000n;
  return new Date(Number(seconds) * 1000).toISOString().replace(/\.000Z$/, `.${String(value - seconds * 1000000000n).padStart(9, "0")}Z`);
}
function timeRange(input, now) {
  const end = instant(input.endTime, nanos(now + 1000));
  const start = instant(input.startTime, end - nanos(7 * day));
  assert(start < end && end - start <= nanos(90 * day), 400, "请选择不超过90天的有效时间区间");
  return { start, end };
}
const includes = (value, keyword) => String(value ?? "").toLowerCase().includes(keyword.toLowerCase());
const eventTime = (row) => row.event_time ?? row.created_time;

// 旧精确条件同样不能把敏感原文掩码后命中安全值；模拟层不保留原文旁路。
function safeLegacyExactValue(value) {
  const secret = /password|passwd|\bpwd\b|secret|token|cookie|authorization|credential|private[ _-]?key|api[ _-]?key|access[ _-]?key|密码|口令|密钥|\b(?:bearer|basic)\s+\S+|-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----|\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|\bAKIA[A-Z0-9]{16}\b|[a-z][a-z0-9+.-]*:\/\/[^\s/@]+@/i;
  const personal = /(?:realName|fullName|contactName|personName|address|身份证|姓名|住址|bankAccount|passport|idCard|identityCard|phone|mobile|telephone|email)[\s"'\\]*[:=：]|(?<![0-9])(?:\+?86[- ]?)?1[3-9][0-9]{9}(?![0-9])|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|(?<![A-Za-z0-9])[1-9][0-9]{5}(?:18|19|20)[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])[0-9]{3}[0-9Xx](?![A-Za-z0-9])|(?:银行卡|银行账号|bank\s*account)[\s:=：]*[0-9]{12,19}/i;
  let probe = value;
  for (let attempt = 0; attempt < 3; attempt++) {
    if (secret.test(probe) || personal.test(probe) || /[\x00-\x1f\x7f]/.test(probe)) return false;
    if (attempt === 2 || !/%[0-9a-f]{2}/i.test(probe)) break;
    try { probe = decodeURIComponent(probe.replace(/%(?![0-9a-f]{2})/ig, "%25").replace(/\+/g, " ")); }
    catch { return false; }
  }
  return true;
}

/** 与必要采集值对象相同的路径=安全值投影；不索引脱敏占位或JSON转义字符。 */
export function parameterSearchText(snapshot) {
  if (!["CAPTURED", "PARTIAL"].includes(snapshot.status)) return "";
  const lines = [];
  const project = (path, value) => {
    if (Array.isArray(value)) value.forEach((item, index) => project(`${path}[${index}]`, item));
    else if (value !== null && typeof value === "object") Object.entries(value).forEach(([key, item]) => project(`${path}.${key}`, item));
    else if (!["[REDACTED]", "[OMITTED]"].includes(value)) lines.push(`${path}=${String(value)}\n`);
  };
  for (const section of ["path", "query", "body", "verified"])
    if (snapshot[section] !== null) project(section, snapshot[section]);
  return lines.join("");
}

/** 与现有 runtime/session 共用租户及 ALL/ORG/OWNER，不使用客户端权限字段。 */
function inScope(row, session) {
  return row.tenant_id === session.tenantId && (session.scope === "ALL" ||
    session.scope === "ORG" && Boolean(session.orgId) && row.actor_org_id === session.orgId ||
    session.scope === "OWNER" && row.actor_id === session.userId);
}
export function logCapabilities(session) {
  const supported = [...logKinds, "exports"];
  const grants = Array.isArray(session.logCapabilities) ? session.logCapabilities : supported;
  const allowed = supported.filter((kind) => grants.includes(kind));
  return allowed.some((kind) => ["operations", "requests"].includes(kind)) ? [...allowed, "requestParameters"] : allowed;
}
function requireCapability(session, kind) {
  assert(logCapabilities(session).includes(kind), 403, "当前会话没有该日志模块权限");
}
function summary(row) {
  const { tenant_id, received_time, kind, parameter_snapshot, parameter_summary, parameter_search_text, request_parameters, details, items, ...value } = row;
  if (kind === "auth") delete value.parameter_capture_status;
  return clone(value);
}
function detail(row) {
  return { ...summary(row), ...(row.kind === "auth" ? {} : { parameter_snapshot: clone(row.parameter_snapshot) }),
    ...(row.request_parameters && Object.keys(row.request_parameters).length ? { request_parameters: clone(row.request_parameters) } : {}), details: clone(row.details) };
}
function sorted(rows, input, fields = eventSorts, fallback = "event_time") {
  const field = text(input.sortField, 40) || fallback;
  const dir = text(input.sortDir, 4) || "desc";
  assert(fields.includes(field) && ["asc", "desc"].includes(dir), 400, "排序参数无效");
  return [...rows].sort((a, b) => {
    const left = field === "event_time" ? instant(eventTime(a), null) : a[field];
    const right = field === "event_time" ? instant(eventTime(b), null) : b[field];
    if (left == null && right != null) return 1;
    if (right == null && left != null) return -1;
    const cmp = typeof left === "bigint" && typeof right === "bigint" ? (left < right ? -1 : left > right ? 1 : 0)
      : typeof left === "number" && typeof right === "number" ? left - right : String(left ?? "").localeCompare(String(right ?? ""));
    return (dir === "asc" ? cmp : -cmp) || a.tid.localeCompare(b.tid);
  });
}
function page(rows, input) {
  const page = integer(input.page, 1, 10000), size = integer(input.size, 20, 100);
  return { list: rows.slice((page - 1) * size, page * size), total: rows.length, page, size };
}

/** 单完整字面包含，不拆词、不解释通配符；筛选在排序和分页之前完成。 */
export function selectLogRows(rows, session, kind, input, now) {
  const { start, end } = timeRange(input, now);
  const keyword = text(input.keyword);
  assert(input.parameterKeyword == null || typeof input.parameterKeyword === "string" && input.parameterKeyword.length <= 200, 400, "参数关键词必须是最长200字符的字符串");
  const parameterKeyword = input.parameterKeyword ?? "";
  const name = text(input.parameterName, 64).trim(), value = text(input.parameterValue, 510).trim();
  if (parameterKeyword || name || value) {
    assert(["operations", "requests"].includes(kind), 400, "参数筛选仅适用于行业操作日志和接口日志");
    assert(Boolean(name) === Boolean(value), 400, "请同时填写参数名称和参数值");
    if (name) {
      assert(oldNames.includes(name) && [...value].length <= 255, 400, "参数名称或参数值无效");
      assert(safeLegacyExactValue(value), 400, "日志参数名称或参数值无效");
      if (booleanNames.includes(name)) assert(["true", "false"].includes(value), 400, "布尔参数值无效");
      else if (!["taskName", "deletedTaskName"].includes(name)) assert(/^[a-zA-Z0-9_-]{1,64}$/.test(value), 400, "参数标识无效");
    }
  }
  const exact = Object.entries({ result: "result", actorId: "actor_id", orgId: "actor_org_id", traceId: "trace_id", requestId: "request_id" })
    .map(([key, column]) => [column, text(input[key], 128)]).filter(([, v]) => v);
  assert(!input.result || logResults.includes(input.result), 400, "日志结果无效");
  const receivedBefore = instant(input.receivedBefore, null);
  return rows.filter((row) => {
    if (row.kind !== kind || !inScope(row, session)) return false;
    const occurred = instant(eventTime(row), null);
    if (occurred == null || occurred < start || occurred >= end || receivedBefore != null && row.received_time && instant(row.received_time, null) > receivedBefore) return false;
    if (!exact.every(([key, v]) => row[key] === v)) return false;
    if (keyword && !["actor_account", "actor_name", "actor_org_name", "action_code", "target_name", "trace_id", "menu_path", "operation_name", "function_code", "page_route", "route_path"].some((key) => includes(row[key], keyword))) return false;
    if (name) {
      const values = [row.request_parameters?.[name]].flat().filter((v) => v !== undefined);
      if (!values.some((v) => String(v) === value)) return false;
    }
    if (parameterKeyword) {
      // 历史只保留旧精确白名单兼容，不伪造当时未采集的新搜索投影。
      if (!includes(parameterSearchText(row.parameter_snapshot), parameterKeyword)) return false;
    }
    return true;
  });
}

const csvFields = ["event_time", "actor_name", "actor_account", "actor_org_name", "menu_path", "operation_name", "action_code", "target_name", "result", "ip", "request_id", "trace_id"];
const csvCell = (value) => {
  let cell = String(value ?? "").replace(/[\x00-\x1f\x7f]/g, " ");
  if (/^\s*[=+@-]/.test(cell)) cell = `'${cell}`;
  return `"${cell.replaceAll('"', '""')}"`;
};
async function digest(content) {
  return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(content))), (value) => value.toString(16).padStart(2, "0")).join("");
}
const savedScope = (session) => ({ tenantId: session.tenantId, userId: session.userId, orgId: session.orgId, scope: session.scope });
function retainsRange(session, original) {
  return session.tenantId === original.tenantId && session.userId === original.userId &&
    (session.scope === "ALL" || session.scope === original.scope && (session.scope !== "ORG" || session.orgId === original.orgId));
}

/** 纯本地事件查询及有界导出；任务按查询请求推进阶段，不伪装真实后台队列。 */
export class LogMockService {
  constructor({ storage, clock = Date.now } = {}) {
    this.storage = storage;
    this.clock = clock;
    this.seeds = new Map();
    this.tasks = new Map();
    if (storage && !storageLocks.has(storage)) storageLocks.set(storage, new Map());
    this.locks = storage ? storageLocks.get(storage) : new Map();
  }
  async withJobs(database, task) {
    const name = this.key(database);
    // 浏览器跨标签页用同源Web Locks；Node及不支持该API的宿主按共享Storage对象串行。
    if (this.storage && globalThis.navigator?.locks?.request)
      return navigator.locks.request(name, task);
    const previous = this.locks.get(name) || Promise.resolve();
    const current = previous.catch(() => {}).then(task);
    this.locks.set(name, current);
    try { return await current; }
    finally { if (this.locks.get(name) === current) this.locks.delete(name); }
  }
  rows(database) {
    if (!this.seeds.has(database.code)) this.seeds.set(database.code, createLogScenario(database, this.clock()));
    return this.seeds.get(database.code);
  }
  key(database) { return `data-elements:mock:chengtian:logs:v1:${database.code}`; }
  jobs(database) {
    const cached = this.storage?.getItem(this.key(database));
    if (cached) {
      let state;
      try { state = JSON.parse(cached); } catch { throw new MockError(422, "Mock 日志导出缓存损坏"); }
      assert(state.version === 1 && state.tenantId === database.tenantId && Array.isArray(state.jobs) && state.jobs.length <= 32 && state.jobs.every((job) => job.tenant_id === database.tenantId), 422, "Mock 日志导出缓存无效");
      return state.jobs;
    }
    return clone(this.tasks.get(database.code) || []);
  }
  save(database, jobs) {
    try { this.storage?.setItem(this.key(database), JSON.stringify({ version: 1, tenantId: database.tenantId, jobs })); }
    catch { throw new MockError(507, "浏览器存储不足，日志任务未保存"); }
    this.tasks.set(database.code, clone(jobs));
  }
  authorized(database, session, kind, ids) {
    assert(Array.isArray(ids) && ids.length > 0 && ids.length <= 100 && new Set(ids).size === ids.length && ids.every((id) => typeof id === "string" && id.trim().length > 0 && id.length <= 64), 400, "一次最多查询100个不同事件");
    const allowed = new Map(this.rows(database).filter((row) => row.kind === kind && inScope(row, session)).map((row) => [row.tid, row]));
    assert(ids.every((id) => allowed.has(id)), 403, "事件不存在或超出当前授权范围");
    return ids.map((id) => allowed.get(id));
  }
  async createExport(database, session, input) {
    assert(logKinds.includes(input.kind), 400, "请选择日志类别");
    requireCapability(session, input.kind);
    const format = input.format ?? "CSV", limit = integer(input.rowLimit, 100000, 100000);
    assert(["CSV", "JSONL"].includes(format), 400, "导出格式无效");
    const now = this.clock();
    selectLogRows(this.rows(database), session, input.kind, input, now);
    const requestedEnd = instant(input.endTime, nanos(now));
    const end = requestedEnd < nanos(now) ? requestedEnd : nanos(now);
    // 任务只保存已校验的选择条件，不把未知请求字段（如凭据）复制到浏览器状态。
    const query = Object.fromEntries(["kind", "result", "actorId", "orgId", "traceId", "requestId", "keyword", "parameterKeyword", "parameterName", "parameterValue", "startTime"]
      .filter((key) => input[key] != null).map((key) => [key, input[key]]));
    query.endTime = instantText(end);
    query.receivedBefore = new Date(now).toISOString();
    // 冻结默认时间窗，后续展示或下载不会随时间漂移。
    query.startTime = instantText(instant(input.startTime, end - nanos(7 * day)));
    const jobs = this.jobs(database);
    assert(jobs.length < 32, 429, "本地导出任务已达32项上限，请清理选定场景的 Mock 日志缓存");
    const id = crypto.randomUUID().replaceAll("-", "");
    jobs.push({ tid: id, tenant_id: session.tenantId, owner_id: session.userId,
      actor_name: "虚构当前人员", actor_account: "demo", actor_org_name: database.organizations.find((org) => org.id === session.orgId)?.name || "",
      format_code: format, status: "PENDING", row_limit: limit, row_count: 0,
      created_time: new Date(now).toISOString(), finished_at: null, expires_at: null, last_error_code: null,
      query, scope: savedScope(session), content: null, sha256: null });
    this.save(database, jobs);
    return { id, status: "PENDING" };
  }
  async exportPage(database, session, input) {
    const keyword = text(input.keyword), status = text(input.status, 24);
    assert(!status || ["PENDING", "RUNNING", "SUCCESS", "FAILED", "EXPIRED", "CANCELLED", "PREPARING", "WRITTEN", "VERIFIED", "PURGING", "COMPLETE"].includes(status), 400, "状态无效");
    // 先验证分页排序及过滤，再推进任务；非法请求没有状态副作用。
    page([], input);
    sorted([], input, ["created_time", "finished_at", "name", "row_count", "actor_name"], "created_time");
    const range = input.startTime != null && input.endTime != null ? timeRange(input, this.clock()) : null;
    const jobs = this.jobs(database);
    for (const job of jobs) {
      if (job.owner_id !== session.userId || job.tenant_id !== session.tenantId) continue;
      if (job.status === "SUCCESS" && Date.parse(job.expires_at) <= this.clock()) { job.status = "EXPIRED"; job.content = null; }
      else if (["PENDING", "RUNNING"].includes(job.status)) {
        if (!logCapabilities(session).includes(job.query.kind)) {
          job.status = "FAILED"; job.last_error_code = "PERMISSION_CHANGED"; continue;
        }
        if (job.status === "PENDING") { job.status = "RUNNING"; continue; }
        try {
          // 当前范围与提交范围取交集；无按行接口或数据库查询。
          const selected = selectLogRows(this.rows(database), session, job.query.kind, job.query, this.clock()).filter((row) => inScope(row, job.scope));
          const rows = sorted(selected, { sortField: "event_time", sortDir: "asc" }).slice(0, job.row_limit);
          let content = job.format_code === "CSV" ? "\ufeff发生时间,姓名,账号,部门,功能路径,具体操作,操作编码,目标,结果,来源IP,请求号,链路号\n" : "";
          for (let offset = 0; offset < rows.length; offset += 500) {
            content += rows.slice(offset, offset + 500).map((row) => job.format_code === "JSONL" ? `${JSON.stringify(detail(row))}\n` : `${csvFields.map((key) => csvCell(key === "event_time" ? eventTime(row) : row[key])).join(",")}\n`).join("");
          }
          assert(new TextEncoder().encode(content).length <= 268435456, 400, "导出文件过大");
          job.content = content; job.sha256 = await digest(content); job.row_count = rows.length;
          job.finished_at = new Date(this.clock()).toISOString(); job.expires_at = new Date(this.clock() + day).toISOString(); job.status = "SUCCESS";
        } catch { job.status = "FAILED"; job.last_error_code = "EXPORT_FAILED"; job.content = null; }
      }
    }
    this.save(database, jobs);
    const visible = jobs.filter((job) => job.tenant_id === session.tenantId && job.owner_id === session.userId &&
      (!keyword || [job.tid, job.actor_name, job.format_code].some((v) => includes(v, keyword))) &&
      (!status || job.status === status) && (!range || instant(job.created_time, null) >= range.start && instant(job.created_time, null) < range.end));
    const result = page(sorted(visible.map((job) => ({ ...job, name: job.format_code })), input, ["created_time", "finished_at", "name", "row_count", "actor_name"], "created_time"), input);
    result.list = result.list.map(({ tenant_id, owner_id, query, scope, content, sha256, name, ...job }) => job);
    return result;
  }
  async download(database, session, id) {
    const job = this.jobs(database).find((row) => row.tid === id && row.tenant_id === session.tenantId && row.owner_id === session.userId);
    assert(job, 403, "导出任务不存在或无权下载");
    requireCapability(session, job.query.kind);
    assert(retainsRange(session, job.scope), 403, "数据范围已变更，请重新导出");
    assert(job.status === "SUCCESS" && Date.parse(job.expires_at) > this.clock() && typeof job.content === "string", 409, "导出文件未完成或已失效");
    assert(job.sha256 === await digest(job.content), 409, "导出文件完整性校验失败，请重新导出");
    return { raw: true, status: 200, body: job.content, headers: {
      "Content-Type": job.format_code === "CSV" ? "text/csv;charset=UTF-8" : "application/x-ndjson;charset=UTF-8",
      "Content-Disposition": `attachment; filename="${job.tid}.${job.format_code.toLowerCase()}"`, "Cache-Control": "no-store",
    } };
  }
  async handle(key, query, body, database, session, components) {
    assert(session.tenantId === database.tenantId && ["ALL", "ORG", "OWNER"].includes(session.scope), 401, "缺少有效的本地行业会话");
    assert(query.action == null && body.action == null, 501, "本批日志 Mock 不支持 action 动作，已阻止访问真实服务");
    if (key === "GET /sym/logs/capabilities") return logCapabilities(session);
    if (key === "GET /sym/logs/components") {
      assert(logCapabilities(session).length, 403, "当前会话没有日志权限");
      return components.filter((row) => row.name === "log-management").map((row) => ({ tid: row.componentId, name: row.name, compile_js: row.compileJs, compile_css: row.compileCss }));
    }
    if (/ \/sym\/logs\/(archives|policies)(?:\/|$)/.test(key)) throw new MockError(501, "本批未实现日志归档或策略；不会虚构成功，已阻止访问真实服务");
    if (["GET /sym/logs/exports", "POST /sym/logs/exports"].includes(key)) {
      requireCapability(session, "exports");
      return this.withJobs(database, () => key.startsWith("POST") ? this.createExport(database, session, body) : this.exportPage(database, session, query));
    }
    const download = /^GET \/sym\/logs\/exports\/([a-f0-9]{32})\/download$/.exec(key);
    if (download) { requireCapability(session, "exports"); return this.withJobs(database, () => this.download(database, session, download[1])); }
    const event = /^(GET|POST) \/sym\/logs\/(auth|operations|requests|security)(?:\/([^/]+))?(?:\/(items))?$/.exec(key);
    if (event) {
      const [, method, kind, id, items] = event;
      requireCapability(session, kind);
      if (method === "POST" && id === "batch" && !items) return this.authorized(database, session, kind, body).map(summary);
      if (method === "GET" && !id) {
        const result = page(sorted(selectLogRows(this.rows(database), session, kind, query, this.clock()), query), query);
        // 投影一次携带所有列表展示字段，绝不由列表逐行读取详情。
        return { ...result, list: result.list.map(summary), timeZone: "Asia/Shanghai", appliedScopeSummary: session.scope };
      }
      if (method === "GET" && id === "statistics" && !items) {
        const rows = selectLogRows(this.rows(database), session, kind, query, this.clock());
        return Object.fromEntries([["total", rows.length], ...[["success_count", "SUCCESS"], ["failure_count", "FAILED"], ["denied_count", "DENIED"], ["partial_count", "PARTIAL"]].map(([key, value]) => [key, rows.filter((row) => row.result === value).length])]);
      }
      if (method === "GET" && id && !["batch", "summary", "statistics"].includes(id)) {
        const [row] = this.authorized(database, session, kind, [id]);
        return items ? page(row.items, query) : detail(row);
      }
    }
    throw new MockError(501, `未实现日志接口：${key}，已阻止访问真实服务`);
  }
}

/** 保持真实日志业务错误信封；未覆盖能力仍以明确 HTTP 501 拒绝。 */
export function logErrorResponse(error) {
  const parameter = error.status === 400, state = error.status === 409;
  return { status: parameter || state || error.status === 403 ? 200 : error.status,
    body: { code: parameter || state ? 500 : error.status, success: false, msg: error.message, message: error.message, data: null,
      ...(parameter || state ? { error: { code: parameter ? "API-PARAM-001" : "MAGIC-RUNTIME-001", message: error.message, detail: null, traceId: null, type: parameter ? "IllegalArgumentException" : "IllegalStateException", method: "", path: "" } } : {}) } };
}
