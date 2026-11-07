import type {Column, TableDef} from './data';

export const quoteSqlIdentifier = (value: string) => '`' + value.replaceAll('`', '``') + '`';
export const quoteSqlString = (value: string) => "'" + value.replaceAll('\\', '\\\\').replaceAll("'", "''") + "'";

function columnDefault(column: Column): string {
  if (column.default === undefined) return '';
  const value = column.default;
  const numeric = /^(?:tinyint|smallint|mediumint|bigint|int|integer|decimal|numeric|float|double|real|number|boolean|bool|year)\b/i.test(column.type);
  // Only recognize supported expressions in compatible types. Text defaults stay literal.
  if (/^NULL$/i.test(value) && column.nullable && !column.primary) return ' DEFAULT NULL';
  if (/^CURRENT_TIMESTAMP$/i.test(value) && /^(?:datetime|timestamp)\b/i.test(column.type)) return ' DEFAULT CURRENT_TIMESTAMP';
  if (/^(?:TRUE|FALSE)$/i.test(value) && numeric && !/^year\b/i.test(column.type)) return ` DEFAULT ${value.toUpperCase()}`;
  if (numeric && /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?$/i.test(value)) return ` DEFAULT ${value}`;
  return ` DEFAULT ${quoteSqlString(column.default)}`;
}

/** One source of truth for sidebar, main DDL view, and the copied SQL. */
export function getTableDdl(table: TableDef): string {
  const definitions = table.columns.map(column => `  ${quoteSqlIdentifier(column.name)} ${column.type.toUpperCase()}${column.nullable && !column.primary ? ' NULL' : ' NOT NULL'}${columnDefault(column)}${column.comment ? ` COMMENT ${quoteSqlString(column.comment)}` : ''}`);
  const primary = table.columns.filter(column => column.primary);
  if (primary.length) definitions.push(`  PRIMARY KEY (${primary.map(column => quoteSqlIdentifier(column.name)).join(', ')})`);
  return `CREATE TABLE ${quoteSqlIdentifier(table.name)} (\n${definitions.join(',\n')}\n) ENGINE=InnoDB\n  DEFAULT CHARSET=utf8mb4${table.comment ? `\n  COMMENT=${quoteSqlString(table.comment)}` : ''};`;
}
