import type { CanvasEdge, CanvasNode } from '../types/dsl';
import type { ComponentManifest } from '../types/manifest';
import type { MappingTableRef } from '../api/fieldMapping';
import { resolveDatasourceId } from '../api/datasourceIdentity.ts';
import { isHiveSink } from '../utils/hiveSinkPolicy.ts';

/**
 * 仅数据库组件读取物理表契约；文件/API/Kafka 的登记数据集及探查字段不能当作 JDBC 表。
 * 输入为相邻节点、当前授权数据源和组件清单；无写入和网络请求。
 * 登记 ID 与数据源成对提交，仍由服务端校验租户、数据范围及表归属；旧画布无表 ID 时兼容名称定位。
 */
export function mappingTableReference(node: CanvasNode | undefined, side: 'source' | 'target',
  databases: Parameters<typeof resolveDatasourceId>[1], manifests: ComponentManifest[]): MappingTableRef | undefined {
  if (!node || node.category !== (side === 'source' ? 'source' : 'sink')) return undefined;
  const manifest = manifests.find(item => item.key === node.manifestKey);
  const jdbc = manifest?.compile?.controllerServices?.some(service =>
    service.type === 'org.apache.nifi.dbcp.DBCPConnectionPool');
  if (!jdbc) return undefined;
  const datasourceId = resolveDatasourceId(node.config, databases);
  const table = String(node.config.table ?? node.config.tableName ?? node.config.targetTable ?? '').trim();
  if (!datasourceId || !table) return undefined;
  const tableId = String(node.config[side === 'source' ? 'sourceTableId' : 'targetTableId'] ?? '').trim();
  return { datasourceId, table, ...(tableId ? { tableId } : {}) };
}

/** 换表时丢弃旧登记身份，避免新名称继续锁定上一张表；仅返回当前节点的配置补丁。 */
export function mappingTableSelectionPatch(node: CanvasNode, table: string): Record<string, unknown> {
  const previous = String(node.config.table ?? node.config.tableName ?? node.config.targetTable ?? '');
  if (table === previous) return { table };
  return { table, [node.category === 'source' ? 'sourceTableId' : 'targetTableId']: undefined };
}

export function columnLabels(columns: unknown): Record<string, string> {
  return Object.fromEntries((Array.isArray(columns) ? columns : []).flatMap((column) => {
    const name = String(column?.columnName ?? '').trim();
    // A physical field exists even when the database has no comment for it.
    return name ? [[name, String(column?.columnComment ?? '').trim()]] : [];
  }));
}

export function mappingColumnNodes(id: string, nodes: Record<string, CanvasNode>, edges: Record<string, CanvasEdge>) {
  // Success branches may contain transport adapters (timestamps, merging, etc.).
  // Stop at another governance node: its output has a separate rule contract.
  const forwards = new Map<string, string[]>(), backwards = new Map<string, string[]>();
  for (const edge of Object.values(edges)) {
    if (edge.source === edge.target || ['failure', 'retry', 'invalid'].includes(String(edge.outlet ?? '').toLowerCase())) continue;
    forwards.set(edge.source, [...forwards.get(edge.source) ?? [], edge.target]);
    backwards.set(edge.target, [...backwards.get(edge.target) ?? [], edge.source]);
  }
  function boundaries(side: 'source' | 'target') {
    const graph = side === 'source' ? backwards : forwards;
    const pending = [...graph.get(id) ?? []], visited = new Set<string>([id]), found: CanvasNode[] = [];
    for (let index = 0; index < pending.length; index++) {
      const key = pending[index];
      if (visited.has(key)) continue;
      visited.add(key);
      const candidate = nodes[key];
      if (!candidate) continue;
      const governance = ['transform.field-mapping', 'transform.field-enrichment'].includes(candidate.manifestKey);
      const changesSchema = ['transform.sql', 'transform.java', 'transform.python', 'transform.shell'].includes(candidate.manifestKey);
      if (side === 'target' && changesSchema) continue;
      if (candidate.category === (side === 'source' ? 'source' : 'sink')
        || side === 'source' && (governance || changesSchema || columnsForMapping(candidate, 'source').length > 0)) {
        found.push(candidate);
        continue;
      }
      if (!governance) pending.push(...graph.get(key) ?? []);
    }
    return found;
  }
  const sources = boundaries('source');
  // Keep a stable logical output based on the JDBC target; Hive formatting is a
  // branch transport concern, never a reason to change the shared rule types.
  const targets = boundaries('target').sort((left, right) => Number(isHiveSink(left)) - Number(isHiveSink(right)) || left.id.localeCompare(right.id));
  return {
    source: sources[0], target: targets[0], sources, targets,
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

export function missingMappingColumnProbes(context: { source?: CanvasNode; target?: CanvasNode; sources?: CanvasNode[]; targets?: CanvasNode[] }) {
  return (['source', 'target'] as const).flatMap((side) => {
    const candidates = side === 'source' ? context.sources : context.targets;
    return (candidates ?? (context[side] ? [context[side]!] : [])).flatMap(node => {
      if (node.category !== (side === 'source' ? 'source' : 'sink')) return [];
      if (Object.keys(columnLabels(columnsForMapping(node, side))).length) return [];
      const table = String(node.config.table ?? node.config.tableName ?? node.config.targetTable ?? '').trim();
      if (!table) return [];
      return [{ nodeId: node.id, manifestKey: node.manifestKey, config: node.config, table, side }];
    });
  });
}
