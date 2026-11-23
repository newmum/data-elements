import type { CanvasDsl, CanvasNode } from '../../nifi/types/dsl';

export type TemplateSpec = {
  topology: 'SERIAL' | 'PARALLEL';
  sourceType: string;
  sourceSchedule?: 'REGISTERED' | 'TEMPLATE';
  targets: string[];
  intervalSeconds: number;
  fetchSize: number;
  hiveMergeMaxAgeSeconds?: number;
  flow?: CanvasDsl;
  nativeSnapshot?: Record<string, unknown>;
  targetDatasourceIds?: string[];
  previewImage?: string;
  engine?: 'CANVAS' | 'GENERATED';
};

export type AccessTemplate = {
  tid: string;
  familyId: string;
  name: string;
  version: number;
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED';
  spec: TemplateSpec;
  updatedAt: number;
  isDefault?: boolean;
};

export const datasourceKinds = [
  ['MYSQL', 'MySQL'], ['POSTGRESQL', 'PostgreSQL'], ['ORACLE', 'Oracle'],
  ['OCEANBASE_ORACLE', 'OceanBase Oracle'], ['OCEANBASE_MYSQL', 'OceanBase MySQL'],
  ['DM', '达梦'], ['KINGBASE', '金仓'], ['SQLSERVER', 'SQL Server'], ['HIVE', 'Hive'],
] as const;

export const datasourceTypeName = (value: string) =>
  datasourceKinds.find(([kind]) => kind === value)?.[1] || value;

export function buildTemplateFlow(spec: TemplateSpec): CanvasDsl {
  const shared = spec.engine !== 'GENERATED' && spec.topology === 'PARALLEL' && spec.targets.length > 1;
  const sharedY = (spec.targets.length - 1) * 300;
  const nodes: CanvasNode[] = [{
    id: 'source', manifestKey: 'source.mysql', label: shared ? '登记来源' : '登记来源（自动适配）',
    category: 'source', x: 0, y: shared ? sharedY : 120,
    config: { templateBinding: 'source', ...(shared ? { manualBackfill: true } : {}) },
  }];
  const edges: CanvasDsl['edges'] = [];
  if (shared) {
    // 多目标画布先治理一次再分流；补数仅由后端在原生 NiFi 画布派生。
    nodes.push({
      id: 'governance', manifestKey: 'transform.field-enrichment', label: '字段治理',
      category: 'transform', x: 680, y: sharedY,
      config: { templateBinding: 'governance:0', useRegisteredMappings: true,
        executionMode: 'AUTO', sharedGovernance: true },
    });
    edges.push({ id: 'source-governance', source: 'source', target: 'governance' });
  }
  spec.targets.forEach((kind, index) => {
    const serial = spec.engine === 'GENERATED' && spec.topology === 'SERIAL';
    const offset = serial ? index * 640 : 0;
    const y = shared ? index * 600 : serial ? 120 : 50 + index * 180;
    const map = shared ? 'governance' : 'governance-' + index;
    const target = 'target-' + index;
    if (!shared) nodes.push({
      id: map, manifestKey: 'transform.field-enrichment', label: '字段治理',
      category: 'transform', x: 320 + offset, y,
      config: { templateBinding: 'governance:' + index, useRegisteredMappings: true, executionMode: 'AUTO' },
    });
    nodes.push({
      id: target, manifestKey: kind === 'HIVE' ? 'sink.hive' : 'sink.jdbc',
      label: datasourceTypeName(kind), category: 'sink', x: shared ? 1360 : 640 + offset, y,
      config: {
        templateBinding: 'target:' + index,
        ...(kind === 'HIVE' ? {
          // 并行模板不固定厂商写入模式，由已授权目标生成 PutHwHDFS 或 PutHDFS。
          ...(!shared ? { hiveWriteMode: 'LINEWELL_HDFS' } : {}),
          hiveHdfsMerge: true, statementType: 'INSERT',
          ...(shared ? { hiveTimestampFormat: true } : {}),
          ...(spec.hiveMergeMaxAgeSeconds ? { mergeMaxBinAge: spec.hiveMergeMaxAgeSeconds + ' sec' } : {}),
        } : shared ? { statementType: 'INSERT', writerType: 'LINEWELL_PUT_DATABASE_RECORD', insertThenMerge: true } : {}),
      },
    });
    const upstream = spec.engine === 'GENERATED' && spec.topology === 'SERIAL' && index > 0
      ? 'target-' + (index - 1) : 'source';
    if (!shared) edges.push({ id: 'source-' + map, source: upstream, target: map });
    edges.push({ id: map + '-' + target, source: map, target });
  });
  return { version: 1, nodes, edges };
}

export function parseTemplateFlow(input: unknown): CanvasDsl {
  const raw = input as { dsl?: CanvasDsl; nodes?: unknown };
  const flow = raw?.dsl || raw as CanvasDsl;
  if (!Array.isArray(flow?.nodes) || !Array.isArray(flow?.edges)) {
    throw new Error('接入画布定义须包含 nodes 和 edges，可导入海通导出的画布 JSON');
  }
  if (flow.nodes.length > 100 || flow.edges.length > 300) throw new Error('接入画布最多100个组件、300条连线');
  return { version: 1, nodes: flow.nodes, edges: flow.edges };
}
