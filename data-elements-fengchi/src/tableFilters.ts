import type {Cell, Column, Row} from './data';

export type FilterKind = 'text' | 'number' | 'date' | 'datetime' | 'time';
export type FilterOperator = 'contains' | 'equals' | 'notEquals' | 'startsWith' | 'endsWith' | 'gt' | 'gte' | 'lt' | 'lte' | 'isNull' | 'isNotNull';
export type FilterRule = {id: string; field: string; operator: FilterOperator; value: string};
export type FilterValidationError = {ruleId: string; message: string};
export type FilterOperatorOption = {value: FilterOperator; label: string};

const textOperators: readonly FilterOperatorOption[] = [
  {value: 'contains', label: '包含'}, {value: 'equals', label: '等于'},
  {value: 'notEquals', label: '不等于'}, {value: 'startsWith', label: '开头是'}, {value: 'endsWith', label: '结尾是'},
];
const comparisonOperators: readonly FilterOperatorOption[] = [
  {value: 'equals', label: '等于'}, {value: 'notEquals', label: '不等于'},
  {value: 'gt', label: '大于'}, {value: 'gte', label: '大于或等于'},
  {value: 'lt', label: '小于'}, {value: 'lte', label: '小于或等于'},
];
const nullOperators: readonly FilterOperatorOption[] = [
  {value: 'isNull', label: '为 NULL'}, {value: 'isNotNull', label: '不为 NULL'},
];

export function getFilterKind(column: Column): FilterKind {
  const type = column.type.trim();
  if (/^(?:datetime|timestamp)\b/i.test(type)) return 'datetime';
  if (/^date\b/i.test(type)) return 'date';
  if (/^time\b/i.test(type)) return 'time';
  if (/^(?:tinyint|smallint|mediumint|bigint|int|integer|decimal|numeric|float|double|real|number|boolean|bool|bit|year)\b/i.test(type)) return 'number';
  return 'text';
}

export function getFilterOperators(column: Column): readonly FilterOperatorOption[] {
  return [...(getFilterKind(column) === 'text' ? textOperators : comparisonOperators), ...nullOperators];
}

export function filterNeedsValue(operator: FilterOperator): boolean {
  return operator !== 'isNull' && operator !== 'isNotNull';
}

export function createFilterRule(columns: readonly Column[], id: string): FilterRule {
  const column = columns[0];
  return {id, field: column?.name ?? '', operator: column ? getFilterOperators(column)[0].value : 'contains', value: ''};
}

export function getFilterValueHint(column: Column): string {
  switch (getFilterKind(column)) {
    case 'number': return '输入数值，如 100 或 3.5';
    case 'date': return 'YYYY-MM-DD';
    case 'datetime': return 'YYYY-MM-DD HH:mm:ss';
    case 'time': return 'HH:mm:ss';
    default: return '输入文本；“等于”时可留空';
  }
}

// Keep decimal comparisons exact, including BIGINT values supplied as strings.
// Scientific notation is normalized without rounding through Number().
type Decimal = {sign: number; digits: string; magnitude: number};
function parseDecimal(value: string): Decimal | undefined {
  const text = value.trim();
  const match = /^([+-]?)(?:(\d+)(?:\.(\d*))?|\.(\d+))(?:[eE]([+-]?\d+))?$/.exec(text);
  if (!match || !Number.isFinite(Number(text))) return undefined;
  const exponent = Number(match[5] ?? 0);
  // Bound exponent work even for values that underflow the JS number range.
  if (!Number.isInteger(exponent) || Math.abs(exponent) > 1000) return undefined;
  const integer = match[2] ?? '';
  const combined = integer + (match[3] ?? match[4] ?? '');
  const leading = combined.match(/^0*/)?.[0].length ?? 0;
  const digits = combined.slice(leading).replace(/0+$/, '');
  return {sign: digits ? (match[1] === '-' ? -1 : 1) : 0, digits, magnitude: integer.length + exponent - leading};
}

function compareDecimals(left: Decimal, right: Decimal): number {
  if (left.sign !== right.sign) return left.sign < right.sign ? -1 : 1;
  if (!left.sign) return 0;
  if (left.magnitude !== right.magnitude) return (left.magnitude < right.magnitude ? -1 : 1) * left.sign;
  const width = Math.max(left.digits.length, right.digits.length);
  const a = left.digits.padEnd(width, '0');
  const b = right.digits.padEnd(width, '0');
  return (a === b ? 0 : a < b ? -1 : 1) * left.sign;
}

function clockMicros(hour: string, minute: string, second = '0', fraction = ''): bigint | undefined {
  const h = Number(hour), m = Number(minute), s = Number(second);
  if (h > 23 || m > 59 || s > 59) return undefined;
  return BigInt(h * 3600 + m * 60 + s) * 1_000_000n + BigInt(fraction.padEnd(6, '0'));
}

/** Strict calendar validation avoids browser-dependent Date.parse and date rollover. */
function parseTemporal(value: string, kind: 'date' | 'datetime' | 'time'): bigint | undefined {
  const text = value.trim();
  if (kind === 'time') {
    const clock = /^(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,6}))?)?$/.exec(text);
    return clock ? clockMicros(clock[1], clock[2], clock[3], clock[4]) : undefined;
  }
  const date = /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,6}))?)?(Z|[+-]\d{2}:\d{2})?)?$/.exec(text);
  if (!date || (kind === 'date' && date[4] !== undefined)) return undefined;
  const year = Number(date[1]), month = Number(date[2]), day = Number(date[3]);
  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const monthDays = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (year < 1 || month < 1 || month > 12 || day < 1 || day > monthDays[month - 1]) return undefined;
  const clock = date[4] === undefined ? 0n : clockMicros(date[4], date[5], date[6], date[7]);
  if (clock === undefined) return undefined;
  const epoch = new Date(0);
  epoch.setUTCFullYear(year, month - 1, day);
  epoch.setUTCHours(0, 0, 0, 0);
  let offset = 0;
  const zone = date[8];
  if (zone && zone !== 'Z') {
    const hour = Number(zone.slice(1, 3)), minute = Number(zone.slice(4, 6));
    if (hour > 14 || minute > 59 || (hour === 14 && minute !== 0)) return undefined;
    offset = (hour * 60 + minute) * (zone[0] === '-' ? -1 : 1);
  }
  return BigInt(epoch.getTime()) * 1000n + clock - BigInt(offset) * 60_000_000n;
}

type CompiledRule = {field: string; operator: FilterOperator; kind: FilterKind; expected: string | Decimal | bigint};
function compileRule(column: Column | undefined, rule: FilterRule): CompiledRule | string {
  if (!column) return '请选择当前表中的有效字段';
  if (!getFilterOperators(column).some(option => option.value === rule.operator)) return '此字段不支持所选条件';
  const kind = getFilterKind(column);
  const base = {field: column.name, operator: rule.operator, kind};
  if (!filterNeedsValue(rule.operator)) return {...base, expected: ''};
  if (kind === 'text') {
    if (rule.value === '' && !['equals', 'notEquals'].includes(rule.operator)) return '请输入筛选文本，空字符串请使用“等于”';
    return {...base, expected: rule.value.toLowerCase()};
  }
  if (!rule.value.trim()) return kind === 'number' ? '请输入有效数值' : '请输入有效日期或时间';
  if (kind === 'number') {
    const number = parseDecimal(rule.value);
    return number ? {...base, expected: number} : '请输入有效数值，不支持 NaN、Infinity 或超出范围的数值';
  }
  const time = parseTemporal(rule.value, kind);
  return time === undefined ? `请输入有效${kind === 'time' ? '时间' : '日期'}（${getFilterValueHint(column)}）` : {...base, expected: time};
}

function compileFilters(columns: readonly Column[], rules: readonly FilterRule[]) {
  const fields = new Map(columns.map(column => [column.name, column]));
  const errors: FilterValidationError[] = [];
  const compiled: CompiledRule[] = [];
  const ids = new Set<string>();
  for (const rule of rules) {
    if (ids.has(rule.id)) {
      errors.push({ruleId: rule.id, message: '筛选条件标识重复，请删除重复条件后重试'});
      continue;
    }
    ids.add(rule.id);
    const result = compileRule(fields.get(rule.field), rule);
    if (typeof result === 'string') errors.push({ruleId: rule.id, message: result});
    else compiled.push(result);
  }
  return {errors, compiled};
}

export function validateFilterRules(columns: readonly Column[], rules: readonly FilterRule[]): FilterValidationError[] {
  return compileFilters(columns, rules).errors;
}

function matchesCompiled(row: Row, rule: CompiledRule): boolean {
  // A missing field is not SQL NULL, and prototype properties are never data fields.
  if (!Object.prototype.hasOwnProperty.call(row, rule.field)) return false;
  const value: Cell = row[rule.field];
  if (rule.operator === 'isNull') return value === null;
  if (rule.operator === 'isNotNull') return value !== null && value !== undefined;
  // NULL never satisfies comparisons, including !=, just as in a WHERE clause.
  if (value === null || value === undefined) return false;
  if (rule.kind === 'text') {
    const actual = String(value).toLowerCase();
    const expected = rule.expected as string;
    switch (rule.operator) {
      case 'contains': return actual.includes(expected);
      case 'startsWith': return actual.startsWith(expected);
      case 'endsWith': return actual.endsWith(expected);
      case 'equals': return actual === expected;
      case 'notEquals': return actual !== expected;
      default: return false;
    }
  }
  let comparison: number;
  if (rule.kind === 'number') {
    const number = parseDecimal(String(value));
    if (!number) return false;
    comparison = compareDecimals(number, rule.expected as Decimal);
  } else {
    const time = parseTemporal(String(value), rule.kind);
    if (time === undefined) return false;
    const expected = rule.expected as bigint;
    comparison = time === expected ? 0 : time < expected ? -1 : 1;
  }
  switch (rule.operator) {
    case 'equals': return comparison === 0;
    case 'notEquals': return comparison !== 0;
    case 'gt': return comparison > 0;
    case 'gte': return comparison >= 0;
    case 'lt': return comparison < 0;
    case 'lte': return comparison <= 0;
    default: return false;
  }
}

/** AND all rules against local sample rows. Invalid rules fail closed; validate before applying. */
export function filterRows<T extends Row>(rows: readonly T[], columns: readonly Column[], rules: readonly FilterRule[]): T[] {
  const {errors, compiled} = compileFilters(columns, rules);
  if (errors.length) return [];
  return rows.filter(row => compiled.every(rule => matchesCompiled(row, rule)));
}

export function matchesRow(row: Row, columns: readonly Column[], rules: readonly FilterRule[]): boolean {
  const {errors, compiled} = compileFilters(columns, rules);
  return errors.length === 0 && compiled.every(rule => matchesCompiled(row, rule));
}
