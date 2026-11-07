import type { Cell, Row, TableDef } from './data';
type Token = { kind: 'word' | 'string' | 'number' | 'symbol'; value: string };
export type SQLResult = { columns: string[]; rows: Row[]; message: string; affectedRows: number };
function tokenize(sql: string): Token[] {
  const tokens: Token[] = [];
  let rest = sql;
  while (rest.length) {
    const whitespace = /^(?:\s+|--[^\n]*(?:\n|$)|\/\*[\s\S]*?\*\/)/.exec(rest);
    if (whitespace) { rest = rest.slice(whitespace[0].length); continue; }
    const match = /^('(?:''|[^'])*'|`[^`]+`|-?\d+(?:\.\d+)?|[a-zA-Z_][\w]*|>=|<=|<>|!=|[=><*(),;])/.exec(rest);
    if (!match) throw new Error(`无法识别 SQL：${rest.slice(0, 24)}。请使用支持的 SELECT 语法。`);
    const raw = match[0];
    tokens.push({ kind: raw.startsWith("'") ? 'string' : /^-?\d/.test(raw) ? 'number' : /^[a-zA-Z_`]/.test(raw) ? 'word' : 'symbol', value: raw.startsWith("'") ? raw.slice(1, -1).replace(/''/g, "'") : raw.startsWith('`') ? raw.slice(1, -1) : raw });
    rest = rest.slice(raw.length);
  }
  return tokens;
}
export function executeSQL(sql: string, tables: Record<string, TableDef>): SQLResult {
  const tokens = tokenize(sql); let index = 0;
  const peek = (value: string) => tokens[index]?.kind !== 'string' && tokens[index]?.value.toUpperCase() === value;
  const take = (value: string) => { if (peek(value)) { index++; return true; } return false; };
  const expect = (value: string) => { if (!take(value)) throw new Error(`SQL 语法错误：此处需要 ${value}。`); };
  const identifier = () => { const t = tokens[index++]; if (!t || t.kind !== 'word') throw new Error('SQL 语法错误：需要表名或字段名。'); return t.value; };
  const finish = () => { take(';'); if (index < tokens.length) throw new Error(`不支持的 SQL 子句或多个语句：${tokens[index].value}。一次只能执行一条只读查询。`); };
  const result = (columns: string[], rows: Row[]): SQLResult => ({ columns, rows, affectedRows: rows.length, message: `查询成功，返回 ${rows.length} 行。` });
  const table = () => { const name = identifier(); const key = Object.keys(tables).find(k => k.toLowerCase() === name.toLowerCase()); if (!key) throw new Error(`数据表「${name}」不存在。`); return tables[key]; };
  if (!tokens.length) throw new Error('请输入 SQL 查询。');
  if (['UPDATE', 'DELETE', 'INSERT', 'DROP', 'ALTER', 'CREATE', 'TRUNCATE'].some(peek)) throw new Error('SQL 控制台仅支持只读查询，不支持 UPDATE、DELETE 等写入语句。请使用 SELECT。');
  if (tokens.some(t => t.kind === 'word' && ['JOIN', 'GROUP', 'UNION', 'HAVING'].includes(t.value.toUpperCase()))) throw new Error('SQL 引擎暂不支持 JOIN、GROUP BY、UNION 或 HAVING。请使用单表 SELECT 查询。');
  if (take('SHOW')) { expect('TABLES'); finish(); return result(['Tables_in_commerce_db'], Object.keys(tables).map(name => ({ Tables_in_commerce_db: name }))); }
  if (take('DESCRIBE') || take('DESC')) { const t = table(); finish(); return result(['Field', 'Type', 'Null', 'Key', 'Default', 'Comment'], t.columns.map(c => ({ Field: c.name, Type: c.type, Null: c.primary || c.nullable === false ? 'NO' : 'YES', Key: c.primary ? 'PRI' : '', Default: c.default ?? null, Comment: c.comment ?? '' }))); }
  expect('SELECT');
  let count = false; let alias = 'COUNT(*)'; let selected: string[] = [];
  if (take('COUNT')) { expect('('); expect('*'); expect(')'); count = true; if (take('AS')) alias = identifier(); }
  else if (take('*')) selected = ['*'];
  else { selected.push(identifier()); while (take(',')) selected.push(identifier()); }
  expect('FROM'); const t = table();
  const column = (name: string) => { const c = t.columns.find(c => c.name.toLowerCase() === name.toLowerCase()); if (!c) throw new Error(`数据表「${t.name}」中不存在字段「${name}」。`); return c.name; };
  selected = selected[0] === '*' ? t.columns.map(c => c.name) : selected.map(column);
  if (new Set(selected).size !== selected.length) throw new Error('请勿重复选择同一字段。');
  let rows = [...t.rows];
  if (take('WHERE')) {
    do {
      const field = column(identifier()); const operator = tokens[index++];
      if (!operator || !['=', '!=', '<>', '>', '<', '>=', '<='].includes(operator.value)) throw new Error('WHERE 仅支持 =、!=、<>、>、<、>=、<= 比较和 AND 连接。');
      const literal = tokens[index++];
      if (!literal || !['string', 'number'].includes(literal.kind) && literal.value.toUpperCase() !== 'NULL') throw new Error('WHERE 比较值必须是数字、单引号字符串或 NULL。');
      const value: Cell = literal.kind === 'number' ? Number(literal.value) : literal.kind === 'string' ? literal.value : null;
      rows = rows.filter(row => {
        const actual = row[field];
        // SQL comparisons involving NULL are unknown and do not match WHERE.
        if (actual === null || value === null) return false;
        if (typeof actual !== typeof value) throw new Error(`字段「${field}」与比较值的类型不同，请使用正确的数字或字符串。`);
        switch (operator.value) {
          case '=': return actual === value;
          case '!=': case '<>': return actual !== value;
          case '>': return actual > value;
          case '<': return actual < value;
          case '>=': return actual >= value;
          case '<=': return actual <= value;
          default: return false;
        }
      });
    } while (take('AND'));
  }
  if (take('ORDER')) {
    expect('BY'); const field = column(identifier()); const descending = take('DESC'); if (!descending) take('ASC');
    if (count) throw new Error('COUNT(*) 聚合查询不支持 ORDER BY；请移除排序子句。');
    rows.sort((a, b) => { const x = a[field], y = b[field]; const comparison = x === y ? 0 : x === null ? -1 : y === null ? 1 : x < y ? -1 : 1; return descending ? -comparison : comparison; });
  }
  let limit: number | undefined;
  if (take('LIMIT')) { const n = tokens[index++]; if (!n || n.kind !== 'number' || !/^\d+$/.test(n.value) || !Number.isSafeInteger(Number(n.value))) throw new Error('LIMIT 必须是非负安全整数。'); limit = Number(n.value); }
  finish();
  const output = count ? [{ [alias]: rows.length }] : rows.map(row => Object.fromEntries(selected.map(key => [key, row[key]])));
  return result(count ? [alias] : selected, limit === undefined ? output : output.slice(0, limit));
}
