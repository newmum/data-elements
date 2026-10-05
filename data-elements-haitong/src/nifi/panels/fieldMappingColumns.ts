import type { CanvasEdge, CanvasNode } from '../types/dsl';

export function columnLabels(columns: unknown): Record<string, string> {
  return Object.fromEntries((Array.isArray(columns) ? columns : []).flatMap((column) => {
    const name = String(column?.columnName ?? '').trim();
    // A physical field exists even when the database has no comment for it.
    return name ? [[name, String(column?.columnComment ?? '').trim()]] : [];
  }));
}

export function mappingColumnNodes(id: string, nodes: Record<string, CanvasNode>, edges: Record<string, CanvasEdge>) {
  const incoming = Object.values(edges).find((edge) => edge.target === id);
  const outgoing = Object.values(edges).find((edge) => edge.source === id);
  return {
    source: incoming ? nodes[incoming.source] : undefined,
    target: outgoing ? nodes[outgoing.target] : undefined,
  };
}

export function columnsForMapping(node: CanvasNode | undefined, side: 'source' | 'target'): unknown[] {
  if (!node) return [];
  const config = node.config;
  const columns = side === 'target' ? config.targetColumns
    : node.category === 'source' ? config.sourceColumns : config.outputColumns;
  if (Array.isArray(columns) && Object.keys(columnLabels(columns)).length) return columns;
  if (side === 'source' && node.category === 'source') {
    const table = String(config.table ?? config.tableName ?? '').trim();
    const byTable = config.sourceColumnsByTable as Record<string, unknown[]> | undefined;
    const entry = byTable && Object.entries(byTable).find(([name]) => name.toLowerCase() === table.toLowerCase());
    if (Array.isArray(entry?.[1])) return entry[1];
  }
  return [];
}

export function missingMappingColumnProbes(context: ReturnType<typeof mappingColumnNodes>) {
  return (['source', 'target'] as const).flatMap((side) => {
    const node = context[side];
    if (!node || node.category !== (side === 'source' ? 'source' : 'sink')) return [];
    if (Object.keys(columnLabels(columnsForMapping(node, side))).length) return [];
    const table = String(node.config.table ?? node.config.tableName ?? node.config.targetTable ?? '').trim();
    if (!table) return [];
    return [{ nodeId: node.id, manifestKey: node.manifestKey, config: node.config, table, side }];
  });
}
