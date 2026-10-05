import type { CanvasNode, CanvasEdge } from '../types/dsl';

export interface MaterializeSource {
  value: string;
  label: string;
  manifestKey: string;
  config: Record<string, unknown>;
  table: string;
  /** Registered table identity used to load the governed materialization template. */
  sourceTableId?: string;
}

export function upstreamTableSources(targetId: string, nodes: Record<string, CanvasNode>, edges: Record<string, CanvasEdge>): MaterializeSource[] {
  const seen = new Set<string>([targetId]);
  const pending = [targetId];
  const sources: MaterializeSource[] = [];
  while (pending.length) {
    const target = pending.shift();
    for (const edge of Object.values(edges)) {
      if (edge.target !== target || seen.has(edge.source)) continue;
      seen.add(edge.source);
      const node = nodes[edge.source];
      if (!node) continue;
      if (node.category === 'source') {
        const tables = node.config.table || node.config.tableName || node.config.tables || node.config.sourceTables;
        const names = Array.isArray(tables) ? tables : String(tables ?? '').split(/[,;\n]/);
        for (const name of names) {
          const table = String(name).trim();
          if (table) sources.push({
            value: `${node.id}:${table}`,
            label: `${node.label} · ${table}`,
            manifestKey: node.manifestKey,
            config: node.config,
            table,
            sourceTableId: String(node.config.sourceTableId ?? node.config.source_table_id ?? '').trim() || undefined,
          });
        }
      } else pending.push(node.id);
    }
  }
  return sources;
}
