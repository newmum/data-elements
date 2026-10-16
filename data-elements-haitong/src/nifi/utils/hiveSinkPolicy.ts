import type { CanvasDsl, CanvasEdge, CanvasNode } from '../types/dsl';

export function isHiveSink(node: CanvasNode | undefined): boolean {
  return !!node && (node.manifestKey === 'sink.hive'
    || (node.category === 'sink' && String(node.config?.dbType ?? '').toUpperCase() === 'HIVE'));
}
const mapping = (node: CanvasNode | undefined) => !!node && ['transform.field-mapping', 'transform.field-enrichment'].includes(node.manifestKey);
const success = (edge: CanvasEdge) => !edge.outlet?.trim() || ['default', 'success'].includes(edge.outlet);
const signature = (source: string, target: string, outlet?: string) => JSON.stringify([source, target, outlet ?? 'default']);

/** Same Hive branch projection as HiveSinkPublicationPolicy. Pure graph work; no metadata/API reads. */
export function hiveSinkCanvas(dsl: CanvasDsl): CanvasDsl {
  if (!dsl.nodes.some(isHiveSink)) return dsl;
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
      if (!isHiveSink(nodes.get(edge.target)) || !mapping(nodes.get(edge.source)) || !success(edge)) { edges.push(edge); continue; }
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
