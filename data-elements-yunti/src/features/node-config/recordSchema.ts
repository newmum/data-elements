export interface Column {
  key: string;
  label: string;
  options?: { value: string; label: string }[];
  boolean?: boolean;
}
const types = ["string", "integer", "number", "boolean", "object", "array"].map(
  (value) => ({ value, label: value }),
);
export const recordColumns: Record<string, Column[]> = {
  mappings: [
    { key: "target", label: "目标字段" },
    { key: "expr", label: "来源表达式" },
    { key: "type", label: "字段类型", options: types },
  ],
  sortBy: [
    { key: "field", label: "排序字段" },
    {
      key: "order",
      label: "排序方式",
      options: [
        { value: "asc", label: "升序" },
        { value: "desc", label: "降序" },
      ],
    },
  ],
  aggregations: [
    { key: "field", label: "计算字段" },
    {
      key: "op",
      label: "聚合方式",
      options: [
        { value: "count", label: "计数" },
        { value: "sum", label: "求和" },
        { value: "avg", label: "平均值" },
        { value: "min", label: "最小值" },
        { value: "max", label: "最大值" },
      ],
    },
    { key: "as", label: "结果字段" },
  ],
  rules: [
    { key: "field", label: "校验字段" },
    {
      key: "rule",
      label: "校验方式",
      options: [
        { value: "required", label: "必填" },
        { value: "range", label: "范围" },
        { value: "regex", label: "正则表达式" },
        { value: "enum", label: "枚举值" },
      ],
    },
    { key: "arg", label: "规则参数" },
    { key: "message", label: "错误提示" },
  ],
  inputMap: [
    { key: "param", label: "子流程参数" },
    { key: "expr", label: "来源表达式" },
  ],
  cases: [
    { key: "value", label: "匹配值" },
    { key: "port", label: "分支端口" },
    { key: "label", label: "分支名称" },
  ],
};
export const parameterColumns: Column[] = [
  { key: "name", label: "参数名称" },
  { key: "type", label: "参数类型", options: types },
  { key: "required", label: "必填", boolean: true },
  { key: "desc", label: "参数说明" },
];
