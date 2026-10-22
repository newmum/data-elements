import type { Flow } from "@/domain/flow-dsl";

export interface FieldMeta {
  label: string;
  help?: string;
  language?: string;
}
export const fieldMeta: Record<string, FieldMeta> = {
  method: { label: "请求方法" },
  path: { label: "接口路径", help: "对外发布的服务路径，例如 /api/orders。" },
  group: { label: "接口分组" },
  request: { label: "请求参数", help: "定义查询、路径、请求头和请求体参数。" },
  auth: { label: "访问声明" },
  datasource: { label: "数据源", help: "选择主平台已登记、类型匹配的数据源。" },
  sql: {
    label: "SQL 脚本",
    language: "sql",
    help: "使用 #{param} 绑定参数；动态 SQL 片段须配置白名单。",
  },
  outputVar: {
    label: "输出变量",
    help: "后续节点可以通过该变量引用执行结果。",
  },
  sourceVar: { label: "输入变量" },
  source: { label: "来源变量" },
  paging: { label: "分页查询" },
  single: { label: "单行结果" },
  returnGeneratedKey: { label: "返回自增主键" },
  batchVar: { label: "批量输入变量" },
  dynamicWhitelist: { label: "动态 SQL 白名单" },
  mode: { label: "处理模式" },
  wrap: {
    label: "标准响应包装",
    help: "返回包含 code、message、data 的标准结构。",
  },
  statusCode: { label: "HTTP 状态码" },
  body: { label: "请求体", language: "json" },
  headers: { label: "请求头" },
  query: { label: "查询参数" },
  code: { label: "处理脚本", language: "javascript" },
  script: { label: "处理脚本", language: "javascript" },
  template: { label: "响应模板", language: "json" },
  dsl: { label: "查询 DSL", language: "json" },
  builder: { label: "查询条件" },
  aggs: { label: "聚合 DSL", language: "json" },
  index: { label: "索引名称" },
  size: { label: "每页数量" },
  from: { label: "起始位置" },
  sort: { label: "排序设置" },
  sortBy: { label: "排序字段" },
  topic: { label: "消息主题" },
  consumerGroup: { label: "消费组" },
  maxRecords: { label: "最大消息数" },
  pollTimeoutMs: { label: "拉取超时（ms）" },
  offsetReset: { label: "起始偏移量" },
  valueFormat: { label: "消息格式" },
  commit: { label: "提交消费偏移量" },
  keyExpr: { label: "消息键表达式" },
  waitAck: { label: "等待发送确认" },
  url: { label: "请求地址" },
  timeout: { label: "超时（ms）" },
  retry: { label: "重试设置" },
  follow: { label: "跟随重定向" },
  parse: { label: "响应解析" },
  extract: { label: "提取路径" },
  op: { label: "缓存操作" },
  key: { label: "缓存键" },
  ttl: { label: "有效期（秒）" },
  value: { label: "配置值" },
  listMode: { label: "逐条转换列表" },
  mappings: { label: "字段转换规则" },
  predicate: { label: "过滤表达式", language: "javascript" },
  groupBy: { label: "分组字段" },
  aggregations: { label: "聚合规则" },
  page: { label: "当前页码" },
  leftVar: { label: "左侧输入变量" },
  rightVar: { label: "右侧输入变量" },
  leftKey: { label: "左侧关联字段" },
  rightKey: { label: "右侧关联字段" },
  type: { label: "关联方式" },
  field: { label: "判断字段" },
  operator: { label: "判断方式" },
  expr: { label: "表达式", language: "javascript" },
  cases: { label: "分支规则" },
  listVar: { label: "循环列表变量" },
  itemVar: { label: "当前项变量" },
  indexVar: { label: "下标变量" },
  collectVar: { label: "汇总结果变量" },
  maxIterations: { label: "循环上限" },
  joinStrategy: { label: "汇合策略" },
  errorVar: { label: "异常变量" },
  swallow: { label: "忽略捕获的异常" },
  flowId: { label: "子编排标识" },
  inputMap: { label: "输入参数" },
  ms: { label: "等待时间（ms）" },
  name: { label: "变量名称" },
  level: { label: "日志级别" },
  message: { label: "日志内容" },
  text: { label: "注释内容" },
  rules: { label: "校验规则" },
  errorCode: { label: "错误码" },
  errorMessage: { label: "错误提示" },
  notes: { label: "节点备注" },
};

const advancedKeys = new Set([
  "timeout",
  "retry",
  "follow",
  "offsetReset",
  "commit",
  "waitAck",
  "maxIterations",
  "dynamicWhitelist",
  "auth",
]);
const outputKeys = new Set([
  "outputVar",
  "collectVar",
  "wrap",
  "statusCode",
  "errorCode",
  "errorMessage",
  "returnGeneratedKey",
]);
export function fieldGroups(keys: string[]) {
  return [
    {
      key: "business",
      label: "处理配置",
      keys: keys.filter(
        (k) =>
          !advancedKeys.has(k) &&
          !outputKeys.has(k) &&
          k !== "notes" &&
          k !== "auth",
      ),
    },
    {
      key: "output",
      label: "输出设置",
      keys: keys.filter((k) => outputKeys.has(k)),
    },
    {
      key: "advanced",
      label: "高级选项",
      keys: keys.filter((k) => advancedKeys.has(k) && k !== "auth"),
    },
  ].filter((group) => group.keys.length);
}

export function variableOptions(flow: Flow | null, selectedId: string) {
  const ancestors = new Set<string>();
  const pending = [selectedId];
  while (pending.length) {
    const id = pending.pop();
    for (const edge of flow?.edges ?? []) {
      if (
        edge.target === id &&
        edge.source !== selectedId &&
        !ancestors.has(edge.source)
      ) {
        ancestors.add(edge.source);
        pending.push(edge.source);
      }
    }
  }
  const values = new Set(["body", "query", "path", "header"]);
  for (const node of flow?.nodes ?? []) {
    if (!ancestors.has(node.id)) continue;
    for (const key of ["outputVar", "collectVar", "itemVar", "indexVar"]) {
      const value = node.data[key];
      if (typeof value === "string" && value.trim()) values.add(value);
    }
  }
  return [...values].map((value) => ({ value, label: value }));
}

export function buildConditionExpr(
  field: unknown,
  operator: unknown,
  value: unknown,
): string {
  const f = String(field ?? "").trim();
  const raw = String(value ?? "").trim();
  const v =
    /^-?\d+(\.\d+)?$/.test(raw) || raw === "true" || raw === "false"
      ? raw
      : JSON.stringify(raw);
  if (operator === "empty") return `${f} == null || ${f} == ''`;
  if (operator === "notEmpty") return `${f} != null && ${f} != ''`;
  if (operator === "contains") return `${f} != null && ${f}.contains(${v})`;
  const symbols: Record<string, string> = {
    eq: "==",
    ne: "!=",
    gt: ">",
    gte: ">=",
    lt: "<",
    lte: "<=",
  };
  return `${f} ${symbols[String(operator)] ?? "=="} ${v}`;
}

export type LogicMode = "builder" | "expression";
export function initialLogicMode(
  type: string,
  data: Record<string, unknown>,
): LogicMode {
  if (!data.expr) return "builder";
  if (!data.field) return "expression";
  const generated =
    type === "condition"
      ? buildConditionExpr(data.field, data.operator, data.value)
      : String(data.field);
  return data.expr === generated ? "builder" : "expression";
}
