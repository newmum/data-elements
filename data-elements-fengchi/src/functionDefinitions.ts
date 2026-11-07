export interface FunctionDefinition {
  id: string;
  name: string;
  parameters: string;
  returnType: string;
  comment: string;
  body: string;
}

export const initialFunctions: FunctionDefinition[] = [
  { id: 'fn-total', name: 'calculate_total', parameters: 'p_price DECIMAL(12,2), p_quantity INT', returnType: 'DECIMAL(12,2)', comment: '计算商品合计金额', body: 'BEGIN\n  RETURN ROUND(p_price * p_quantity, 2);\nEND' },
  { id: 'fn-name', name: 'format_customer_name', parameters: 'p_first_name VARCHAR(100), p_last_name VARCHAR(100)', returnType: 'VARCHAR(201)', comment: '格式化客户姓名', body: "BEGIN\n  RETURN TRIM(CONCAT_WS(' ', p_first_name, p_last_name));\nEND" },
  { id: 'fn-status', name: 'order_status_label', parameters: 'p_status INT', returnType: 'VARCHAR(20)', comment: '订单状态显示名称', body: "BEGIN\n  RETURN CASE p_status\n    WHEN 0 THEN '待付款'\n    WHEN 1 THEN '已付款'\n    WHEN 2 THEN '已完成'\n    ELSE '未知状态'\n  END;\nEND" },
];

export function functionSql(fn: FunctionDefinition): string {
  const comment = fn.comment.replace(/\\/g, '\\\\').replace(/'/g, "''");
  return `CREATE FUNCTION \`${fn.name.replace(/`/g, '``')}\`(${fn.parameters})\nRETURNS ${fn.returnType}\nCOMMENT '${comment}'\n${fn.body.replace(/;\s*$/, '')};`;
}

export function validateFunction(fn: FunctionDefinition, existing: FunctionDefinition[]): string | null {
  if (!/^[A-Za-z_][A-Za-z0-9_]{0,63}$/.test(fn.name)) return '名称须以字母或下划线开头，且仅含字母、数字或下划线（最多 64 个字符）。';
  if (existing.some(item => item.id !== fn.id && item.name.toLowerCase() === fn.name.toLowerCase())) return '已存在同名函数，请使用其他名称。';
  if (!fn.returnType.trim()) return '请输入返回类型。';
  if (!fn.body.trim()) return '请输入函数定义。';
  return null;
}

export function nextFunctionName(name: string, existing: FunctionDefinition[]): string {
  const names = new Set(existing.map(fn => fn.name.toLowerCase()));
  let candidate = name.slice(0, 64);
  let index = 1;
  while (names.has(candidate.toLowerCase())) {
    const suffix = `_${index++}`;
    candidate = name.slice(0, 64 - suffix.length) + suffix;
  }
  return candidate;
}

export function parseFunctionDefinitions(raw: string | null): FunctionDefinition[] {
  if (raw === null) return initialFunctions.map(fn => ({ ...fn }));
  const parsed: unknown = JSON.parse(raw);
  if (!Array.isArray(parsed) || !parsed.every(value => value && typeof value === 'object' && ['id', 'name', 'parameters', 'returnType', 'comment', 'body'].every(key => typeof value[key] === 'string'))) throw new Error('函数定义格式不正确');
  const items = parsed as FunctionDefinition[];
  if (new Set(items.map(fn => fn.id)).size !== items.length) throw new Error('函数标识重复');
  return items;
}
