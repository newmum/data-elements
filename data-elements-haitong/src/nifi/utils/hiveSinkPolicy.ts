import type { CanvasDsl, CanvasEdge, CanvasNode } from '../types/dsl';

export function isHiveSink(node: CanvasNode | undefined): boolean {
  return !!node && (node.manifestKey === 'sink.hive'
    || (node.category === 'sink' && String(node.config?.dbType ?? '').trim().toUpperCase() === 'HIVE'));
}

export function hiveHdfsMergeUnavailable(node: CanvasNode): string | undefined {
  if (node.manifestKey !== 'sink.hive') return '当前使用通用 JDBC 写入组件。使用 Hive 的 PutHwHDFS 或 PutHDFS 写入时才会生成合并节点。';
  if (String((node.config.partitionPlan as { enabled?: unknown } | undefined)?.enabled).toLowerCase() === 'true') {
    return '已启用分区计划，请在 NiFi 原生流程中按分区配置合并。平台部署时不会自动插入合并节点。';
  }
  const mode = String(node.config.hiveWriteMode ?? '').trim().toUpperCase();
  if (!['', 'LINEWELL_HDFS', 'LINEWELL', 'HDFS_BATCH', 'BATCH'].includes(mode)) {
    return '当前写入组件不使用 HDFS 文件合并。选择 PutHwHDFS 或 PutHDFS 后生效。';
  }
}

/** Apply the default on save so the user can deploy it without editing old nodes. */
export function withHiveHdfsMergeDefaults(dsl: CanvasDsl): CanvasDsl {
  let changed = false;
  const nodes = dsl.nodes.map(node => {
    if (node.manifestKey !== 'sink.hive' || typeof node.config.hiveHdfsMerge === 'boolean' || hiveHdfsMergeUnavailable(node)) return node;
    changed = true;
    return { ...node, config: { ...node.config, hiveHdfsMerge: true } };
  });
  return changed ? { ...dsl, nodes } : dsl;
}
/** A first/direct Hive target always keeps governance; missing legacy context never bypasses it. */
export function bypassesHiveGovernance(node: CanvasNode | undefined): boolean {
  if (!isHiveSink(node)) return false;
  const value = node?.config?.accessChain;
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const chain = value as Record<string, unknown>;
  const count = chain.targetCount, datasources = chain.targetDatasourceCount, index = chain.targetIndex;
  const root = String(chain.rootSourceTableId ?? '').trim(), source = String(chain.sourceTableId ?? '').trim();
  const target = String(chain.targetTableId ?? '').trim();
  if (chain.downstreamHive === true && source && target && source !== target && target === String(node?.config?.targetTableId ?? '').trim()) return true;
  return typeof count === 'number' && Number.isInteger(count) && count > 1 && count <= 100
    && typeof datasources === 'number' && Number.isInteger(datasources) && datasources > 0 && datasources <= count
    && typeof index === 'number' && Number.isInteger(index) && index > 0 && index < count
    && !!root && !!source && root !== source && !!target && target === String(node?.config?.targetTableId ?? '').trim();
}
/** Preserve the old generator's serial context before normalization drops unknown top-level keys. */
export function legacyHiveContext(dsl: CanvasDsl, context: unknown): CanvasDsl {
  if (!context || typeof context !== 'object' || Array.isArray(context)) return dsl;
  const value = context as Record<string, unknown>;
  if (!Array.isArray(value.targetTableIds) || value.targetTableIds.length < 2 || value.targetTableIds.length > 100) return dsl;
  const targets = value.targetTableIds.map(id => String(id ?? '').trim()), root = String(value.rootSourceTableId ?? '').trim();
  if (!root || targets.includes('') || targets.includes(root) || new Set(targets).size !== targets.length) return dsl;
  const sources = [...new Set(dsl.nodes.filter(n => n.category === 'source').map(n => String(n.config?.sourceTableId ?? '')))];
  if (sources.length !== 1 || !sources[0] || sources[0] === root) return dsl;
  const previous = targets.indexOf(sources[0]), target = targets[previous + 1];
  if (previous < 0 || !target) return dsl;
  return { ...dsl, nodes: dsl.nodes.map(node => isHiveSink(node) && String(node.config?.targetTableId ?? '') === target
    ? { ...node, config: { ...node.config, accessChain: { downstreamHive: true, sourceTableId: sources[0], targetTableId: target } } } : node) };
}
const mapping = (node: CanvasNode | undefined) => !!node && ['transform.field-mapping', 'transform.field-enrichment'].includes(node.manifestKey);
const success = (edge: CanvasEdge) => !edge.outlet?.trim() || ['default', 'success'].includes(edge.outlet);
const signature = (source: string, target: string, outlet?: string) => JSON.stringify([source, target, outlet ?? 'default']);

/** Same Hive branch projection as HiveSinkPublicationPolicy. Pure graph work; no metadata/API reads. */
export function hiveSinkCanvas(dsl: CanvasDsl): CanvasDsl {
  if (!dsl.nodes.some(bypassesHiveGovernance)) return dsl;
  const nodes = new Map(dsl.nodes.map(node => [node.id, node]));
  const incoming = new Map<string, CanvasEdge[]>();
  for (const edge of dsl.edges) incoming.set(edge.target, [...incoming.get(edge.target) ?? [], edge]);
  const candidates = new Set<string>(), memo = new Map<string, CanvasEdge[]>();
  function rawInputs(id: string, visiting: Set<string>): CanvasEdge[] {
    if (memo.has(id)) return memo.get(id)!;
    if (visiting.has(id)) throw new Error('incomplete mapping chain');
    visiting.add(id); candidates.add(id);
    const result = new Map<string, CanvasEdge>();
    for (const edge of incoming.get(id) ?? []) {
      if (!success(edge)) throw new Error('exception branch');
      const inputs = mapping(nodes.get(edge.source)) ? rawInputs(edge.source, visiting) : [edge];
      for (const input of inputs) result.set(signature(input.source, '', input.outlet), input);
    }
    visiting.delete(id);
    if (!result.size) throw new Error('missing input');
    const inputs = [...result.values()]; memo.set(id, inputs); return inputs;
  }
  const edges: CanvasEdge[] = [], emitted = new Set<string>();
  try {
    for (const edge of dsl.edges) {
      if (!bypassesHiveGovernance(nodes.get(edge.target)) || !mapping(nodes.get(edge.source)) || !success(edge)) { edges.push(edge); continue; }
      let index = 0;
      for (const input of rawInputs(edge.source, new Set())) {
        const key = signature(input.source, edge.target, input.outlet);
        if (!emitted.has(key)) {
          emitted.add(key);
          edges.push({ id: `${edge.id ?? `${edge.source}-${edge.target}`}-hive-raw-${index++}`,
            source: input.source, target: edge.target, outlet: input.outlet });
        }
      }
    }
  } catch {
    // Keep incomplete/error drafts editable. The backend rejects them before publication.
    return dsl;
  }
  if (!candidates.size) return dsl;
  const retained = new Set<string>(), parents = new Map<string, string[]>();
  for (const edge of edges) if (candidates.has(edge.source)) {
    if (!candidates.has(edge.target)) retained.add(edge.source);
    else parents.set(edge.target, [...parents.get(edge.target) ?? [], edge.source]);
  }
  const queue = [...retained];
  for (let i = 0; i < queue.length; i++) for (const parent of parents.get(queue[i]) ?? []) {
    if (!retained.has(parent)) { retained.add(parent); queue.push(parent); }
  }
  const removed = new Set([...candidates].filter(id => !retained.has(id)));
  return { ...dsl, nodes: dsl.nodes.filter(node => !removed.has(node.id)),
    edges: edges.filter(edge => !removed.has(edge.source) && !removed.has(edge.target)) };
}
