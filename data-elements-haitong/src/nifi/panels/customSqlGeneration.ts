import { findUniqueCaseInsensitiveFieldMatches, identifierMatchKey } from '../utils/fieldNameMatching.ts';

export interface SqlColumn { name: string; comment?: string; dataType?: string }
export interface SqlRecommendation { from: string; to: string; confidence: number }

/** SELECT runs in the source database, so its identifier syntax also belongs to that database. */
export function quoteSqlIdentifier(name: string, sourceType: string): string {
  if (/mysql|mariadb|hive|spark|doris|starrocks|clickhouse/i.test(sourceType)
      || (/oceanbase/i.test(sourceType) && !/oracle/i.test(sourceType))) {
    return '`' + name.replace(/`/g, '``') + '`';
  }
  if (/sqlserver|mssql/i.test(sourceType)) return '[' + name.replace(/]/g, ']]') + ']';
  return '"' + name.replace(/"/g, '""') + '"';
}

function quoteTable(table: string, sourceType: string): string {
  // Split schema/table qualifiers without splitting dots inside quoted identifiers.
  const parts = table.match(/(?:`(?:``|[^`])*`|"(?:""|[^"])*"|\[(?:\]\]|[^\]])*\]|[^.])+/g) ?? [];
  return parts.map((part) => {
    const name = part.trim();
    const unquoted = /^`.*`$/.test(name) ? name.slice(1, -1).replace(/``/g, '`')
      : /^".*"$/.test(name) ? name.slice(1, -1).replace(/""/g, '"')
      : /^\[.*\]$/.test(name) ? name.slice(1, -1).replace(/\]\]/g, ']') : name;
    return quoteSqlIdentifier(unquoted, sourceType);
  }).join('.');
}

export function generateSelectSql(table: string, sources: SqlColumn[], targets: SqlColumn[], sourceType: string,
  recommendations: SqlRecommendation[] = [], recordInput = false) {
  if (!table.trim() || !sources.length) throw new Error('来源表或来源字段为空，无法生成语句');
  const fields = (columns: SqlColumn[]) => columns.map((col) => ({ name: col.name, path: `/${col.name}` }));
  const mappings = new Map<string, string>();
  const usedTargets = new Set<string>();
  findUniqueCaseInsensitiveFieldMatches(fields(sources), fields(targets)).forEach(({ source, target }) => {
    mappings.set(source.name, target.name);
    usedTargets.add(target.name);
  });
  const uniqueName = (columns: SqlColumn[], name: string) =>
    columns.filter((col) => identifierMatchKey(col.name) === identifierMatchKey(name)).length === 1;
  const candidates = recommendations.flatMap((item) => {
    const source = sources.find((col) => `/${col.name}` === item.from);
    const target = targets.find((col) => `/${col.name}` === item.to);
    if (item.confidence < 0.85 || !source || !target || mappings.has(source.name) || usedTargets.has(target.name)
        || !uniqueName(sources, source.name) || !uniqueName(targets, target.name)) return [];
    return [{ from: source.name, to: target.name }];
  }).filter((item, index, all) => all.findIndex((other) => other.from === item.from && other.to === item.to) === index);
  candidates.forEach((item) => {
    if (candidates.filter((other) => other.from === item.from).length !== 1
        || candidates.filter((other) => other.to === item.to).length !== 1) return;
    // An alias must not collide with an unmapped source column in the generated projection.
    if (sources.some((col) => col.name !== item.from && identifierMatchKey(col.name) === identifierMatchKey(item.to))) return;
    mappings.set(item.from, item.to);
    usedTargets.add(item.to);
  });
  const quote = (name: string) => quoteSqlIdentifier(name, recordInput ? 'calcite' : sourceType);
  // A source field without a trustworthy target match remains a plain projection.
  // Giving it an alias identical to itself obscures which columns were actually mapped.
  const projection = sources.map((col) => {
    const target = mappings.get(col.name);
    return target ? `  ${quote(col.name)} AS ${quote(target)}` : `  ${quote(col.name)}`;
  });
  return {
    sql: `SELECT\n${projection.join(',\n')}\nFROM ${recordInput ? 'FLOWFILE' : quoteTable(table, sourceType)};`,
    mappedCount: mappings.size,
    unmatched: sources.filter((col) => !mappings.has(col.name)).map((col) => col.name),
  };
}
