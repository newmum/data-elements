import { resolveSelectedTableReference } from './sqlTableReference';

type Catalog = Parameters<typeof resolveSelectedTableReference>[2];
type SelectionRange = { from: number; to: number };
type TableReference = NonNullable<ReturnType<typeof resolveSelectedTableReference>>;

export type SqlTableContext = {
  queryKey: string;
  sql: string;
  ranges: SelectionRange[];
  reference: TableReference;
};

/** Capture the selected reference when the menu opens, not after it steals focus. */
export function captureSqlTableContext(
  queryKey: string,
  sql: string,
  ranges: readonly SelectionRange[],
  catalog: Catalog,
  currentDatabase: string,
): SqlTableContext | null {
  const reference = resolveSelectedTableReference(sql, ranges, catalog, currentDatabase);
  return reference ? { queryKey, sql, ranges: ranges.map(range => ({ ...range })), reference } : null;
}

/** A menu from another query, document, or catalog must never open an old target. */
export function currentSqlTableReference(
  context: SqlTableContext | null,
  queryKey: string | undefined,
  sql: string,
  catalog: Catalog,
  currentDatabase: string,
): TableReference | null {
  if (!context || context.queryKey !== queryKey || context.sql !== sql) return null;
  const current = resolveSelectedTableReference(sql, context.ranges, catalog, currentDatabase);
  return current?.database === context.reference.database && current.table === context.reference.table ? current : null;
}
