import type {Cell, Column, Row, TableDef} from './data';

/** Deliberately conservative local validator, not a replacement for MySQL DDL execution. */
export const SCHEMA_TYPES = ['TINYINT', 'SMALLINT', 'MEDIUMINT', 'INT', 'BIGINT', 'DECIMAL', 'FLOAT', 'DOUBLE', 'BOOLEAN', 'CHAR', 'VARCHAR', 'TEXT', 'MEDIUMTEXT', 'LONGTEXT', 'DATE', 'DATETIME', 'TIMESTAMP', 'TIME', 'YEAR', 'JSON'] as const;
export type SchemaFieldDraft = {
  key: string; sourceName: string; name: string; type: string; length: string;
  unsigned: boolean; nullable: boolean; primary: boolean;
  hasDefault: boolean; defaultValue: string; comment: string;
};
export type SchemaDraft = SchemaFieldDraft[];
export type SchemaCompileResult = {ok: true; table: TableDef} | {ok: false; error: string; fieldKey?: string; property?: keyof SchemaFieldDraft};
const integerBits: Record<string, number> = {TINYINT: 8, SMALLINT: 16, MEDIUMINT: 24, INT: 32, BIGINT: 64};
const numeric = new Set([...Object.keys(integerBits), 'DECIMAL', 'FLOAT', 'DOUBLE']);
const textTypes = new Set(['CHAR', 'VARCHAR', 'TEXT', 'MEDIUMTEXT', 'LONGTEXT']);

export function createSchemaDraft(table: TableDef): SchemaDraft {
  return table.columns.map((column, index) => {
    const parsed = /^\s*([a-z]+)(?:\s*\(([^)]*)\))?\s*(unsigned)?\s*$/i.exec(column.type);
    return {
      key: `field-${index}-${column.name}`, sourceName: column.name, name: column.name,
      type: (parsed?.[1] ?? column.type).toUpperCase(), length: parsed?.[2]?.replace(/\s/g, '') ?? '',
      unsigned: !!parsed?.[3], primary: !!column.primary, nullable: !!column.nullable && !column.primary,
      hasDefault: column.default !== undefined, defaultValue: column.default ?? '', comment: column.comment ?? '',
    };
  });
}
export function schemaDraftChanged(table: TableDef, draft: SchemaDraft): boolean {
  return JSON.stringify(createSchemaDraft(table)) !== JSON.stringify(draft);
}
function fail(error: string, field?: SchemaFieldDraft, property?: keyof SchemaFieldDraft): SchemaCompileResult {
  return {ok: false, error, ...(field ? {fieldKey: field.key} : {}), ...(property ? {property} : {})};
}
function validDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match || +match[1] < 1000) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
function canonicalNumber(value: string): string {
  const [mantissa, exponent = '0'] = value.toLowerCase().replace(/^\+/, '').split('e');
  const negative = mantissa.startsWith('-');
  const [whole, fraction = ''] = mantissa.replace(/^-/, '').split('.');
  let digits = (whole + fraction).replace(/^0+/, '');
  if (!digits) return '0';
  let power = Number(exponent) - fraction.length;
  while (digits.endsWith('0')) { digits = digits.slice(0, -1); power++; }
  return `${negative ? '-' : ''}${digits}e${power}`;
}
function decimalFits(value: number, length: string): boolean {
  const [precision, scale = 0] = length.split(',').map(Number);
  // Expand scientific notation without rounding or coercing the stored value.
  const [coefficient, exponentText = '0'] = Math.abs(value).toString().split('e');
  const [whole, fraction = ''] = coefficient.split('.');
  const digits = whole + fraction;
  const point = whole.length + Number(exponentText);
  const integer = (point > 0 ? digits.slice(0, point).padEnd(point, '0') : '').replace(/^0+/, '');
  const fractional = (point < 0 ? '0'.repeat(-point) + digits : digits.slice(point)).replace(/0+$/, '');
  return integer.length <= precision - scale && fractional.length <= scale;
}
function valueError(value: Cell | undefined, field: SchemaFieldDraft): string | undefined {
  if (value === null || value === undefined) return field.nullable && !field.primary ? undefined : '不允许 NULL';
  const type = field.type;
  if (numeric.has(type) || type === 'BOOLEAN' || type === 'YEAR') {
    if (typeof value !== 'number' || !Number.isFinite(value)) return '需要有限数值，不能自动转换现有值';
    if (field.unsigned && value < 0) return '无符号字段不能包含负数';
    if (integerBits[type]) {
      const bits = integerBits[type];
      const min = field.unsigned ? 0 : -(2 ** (bits - 1));
      const max = field.unsigned ? 2 ** bits - 1 : 2 ** (bits - 1) - 1;
      if (!Number.isSafeInteger(value) || value < min || value > max) return '整数超出类型范围或安全精度';
    }
    if (type === 'BOOLEAN' && value !== 0 && value !== 1) return '布尔值必须是 0 或 1';
    if (type === 'YEAR' && (!Number.isInteger(value) || (value !== 0 && (value < 1901 || value > 2155)))) return '年份必须为 0 或 1901–2155';
    if (type === 'DECIMAL' && !decimalFits(value, field.length)) return '数值超出 DECIMAL 精度或小数位数';
    if (type === 'FLOAT' && Math.fround(value) !== value) return '数值无法由 FLOAT 精确表示，请使用 DOUBLE 或 DECIMAL';
    return;
  }
  if (typeof value !== 'string') return '需要文本，不能自动转换现有值';
  if (textTypes.has(type)) {
    if ((type === 'CHAR' || type === 'VARCHAR') && [...value].length > Number(field.length)) return '文本超过字段长度';
    const bytes = new TextEncoder().encode(value).length;
    const limit = type === 'TEXT' ? 65535 : type === 'MEDIUMTEXT' ? 16777215 : 4294967295;
    if (bytes > limit) return '文本超过类型容量';
    return;
  }
  if (type === 'JSON') { try { JSON.parse(value); return; } catch { return 'JSON 格式无效'; } }
  if (type === 'DATE') return validDate(value) ? undefined : '日期格式应为有效的 YYYY-MM-DD';
  if (type === 'DATETIME' || type === 'TIMESTAMP') {
    const match = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}):(\d{2}):(\d{2})$/.exec(value);
    if (!match || !validDate(match[1]) || +match[2] > 23 || +match[3] > 59 || +match[4] > 59) return '时间格式应为有效的 YYYY-MM-DD HH:mm:ss';
    if (type === 'TIMESTAMP') {
      const millis = Date.parse(value.replace(' ', 'T') + 'Z');
      if (millis < 1000 || millis > 2147483647000) return 'TIMESTAMP 超出本地支持范围（1970–2038，UTC）';
    }
    return;
  }
  if (type === 'TIME') return /^-?(?:\d{1,2}|[0-7]\d{2}|8[0-2]\d|83[0-8]):[0-5]\d:[0-5]\d$/.test(value) ? undefined : 'TIME 格式或范围无效';
  return '不支持的数据类型';
}
export function compileSchemaDraft(table: TableDef, draft: SchemaDraft): SchemaCompileResult {
  if (draft.length !== table.columns.length) return fail('当前仅支持编辑已有字段，不支持增删字段');
  const baseline = createSchemaDraft(table);
  const known = new Map(baseline.map(field => [field.key, field]));
  const keys = new Set<string>();
  const names = new Set<string>();
  const fields: SchemaDraft = [];
  for (const original of draft) {
    const field = {...original, type: original.type.trim().toUpperCase(), length: original.length.trim().replace(/\s/g, '')};
    const source = known.get(field.key);
    if (!source || keys.has(field.key) || source.sourceName !== field.sourceName) return fail('字段来源已失效，请重新打开表结构', field);
    keys.add(field.key);
    if (!/^[\p{L}_][\p{L}\p{N}_$]*$/u.test(field.name) || [...field.name].length > 64) return fail('字段名须为 1–64 个字母、数字、下划线或 $，且不能以数字开头', field, 'name');
    if (names.has(field.name.toLowerCase())) return fail('字段名不能重复（不区分大小写）', field, 'name');
    names.add(field.name.toLowerCase());
    if (!(SCHEMA_TYPES as readonly string[]).includes(field.type)) return fail('不支持的数据类型', field, 'type');
    if (field.sourceName === 'id' && (field.name !== 'id' || !integerBits[field.type] || field.nullable)) return fail('id 用作记录标识，必须保留名称和非空整数类型', field, 'name');
    if (field.primary && field.nullable) return fail('主键字段不能允许 NULL', field, 'nullable');
    if (field.unsigned && !numeric.has(field.type)) return fail('该类型不支持 UNSIGNED', field, 'unsigned');
    if (field.type === 'CHAR' || field.type === 'VARCHAR') {
      const limit = field.type === 'CHAR' ? 255 : 16383; // conservative utf8mb4 limit
      if (!/^\d+$/.test(field.length) || +field.length < 1 || +field.length > limit) return fail(`长度必须为 1–${limit}`, field, 'length');
    } else if (field.type === 'DECIMAL') {
      if (!/^\d+(?:,\d+)?$/.test(field.length)) return fail('DECIMAL 长度请填写精度,小数位，例如 10,2', field, 'length');
      const [precision, scale = 0] = field.length.split(',').map(Number);
      if (precision < 1 || precision > 65 || scale < 0 || scale > 30 || scale > precision) return fail('精度须为 1–65，小数位为 0–30 且不超过精度', field, 'length');
    } else if (field.length) return fail('该类型无需长度；整数显示宽度不改变取值范围', field, 'length');
    if (field.hasDefault) {
      const expression = field.defaultValue.toUpperCase();
      let error: string | undefined;
      if (expression === 'NULL') error = field.nullable && !field.primary ? undefined : 'NULL 默认值仅适用于允许 NULL 的非主键字段';
      else if ((field.type === 'DATETIME' || field.type === 'TIMESTAMP') && expression === 'CURRENT_TIMESTAMP') error = undefined;
      else if ((numeric.has(field.type) || field.type === 'BOOLEAN') && /^(TRUE|FALSE)$/.test(expression)) error = valueError(expression === 'TRUE' ? 1 : 0, field);
      else if (numeric.has(field.type) || field.type === 'BOOLEAN' || field.type === 'YEAR') {
        if (!/^[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[-+]?\d+)?$/i.test(field.defaultValue)) error = '数值默认值无效';
        else if (canonicalNumber(field.defaultValue) !== canonicalNumber(String(Number(field.defaultValue)))) error = '默认值超出本地数值精度，不能自动舍入';
        else error = valueError(Number(field.defaultValue), field);
      } else error = valueError(field.defaultValue, field);
      if (error) return fail(`默认值：${error}`, field, 'defaultValue');
    }
    for (const [index, row] of table.rows.entries()) {
      const error = valueError(row[field.sourceName], field);
      if (error) return fail(`第 ${index + 1} 行「${field.name}」：${error}`, field, 'type');
      if (field.name !== field.sourceName && Object.hasOwn(row, field.name) && !table.columns.some(column => column.name === field.name)) return fail('新字段名与记录中的额外数据冲突', field, 'name');
    }
    fields.push(field);
  }
  const primary = fields.filter(field => field.primary);
  if (primary.length) {
    const seen = new Set<string>();
    for (const row of table.rows) {
      const key = JSON.stringify(primary.map(field => row[field.sourceName]));
      if (seen.has(key)) return fail('现有数据的主键组合重复', primary[0], 'primary');
      seen.add(key);
    }
  }
  const ids = table.rows.map(row => row.id);
  if (fields.some(field => field.sourceName === 'id') && new Set(ids).size !== ids.length) return fail('id 记录标识必须唯一');
  const columns: Column[] = fields.map(field => ({name: field.name, type: `${field.type.toLowerCase()}${field.length ? `(${field.length})` : ''}${field.unsigned ? ' unsigned' : ''}`, primary: field.primary, nullable: field.nullable, ...(field.hasDefault ? {default: field.defaultValue} : {}), comment: field.comment}));
  const rows = table.rows.map(row => {
    const next: Row = {...row};
    for (const field of fields) delete next[field.sourceName];
    for (const field of fields) if (Object.hasOwn(row, field.sourceName)) Object.defineProperty(next, field.name, {value: row[field.sourceName], enumerable: true, configurable: true, writable: true});
    return next;
  });
  return {ok: true, table: {...table, columns, rows}};
}
