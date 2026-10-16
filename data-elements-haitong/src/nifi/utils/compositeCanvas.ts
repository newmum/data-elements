import type { CanvasDsl, CanvasNode, CanvasEdge } from '../types/dsl';
import type { TaskChain, TaskChainItem } from '../types/taskChain';
import { hiveSinkCanvas } from './hiveSinkPolicy.ts';

export function taskChainPaths(chain: TaskChain): TaskChainItem[][] {
  const byId = new Map(chain.items.map(item => [item.taskId, item]));
  const downstream = new Map<string, string[]>();
  const childIds = new Set<string>();
  for (const link of chain.links ?? []) {
    if (!byId.has(link.fromTaskId) || !byId.has(link.toTaskId)) continue;
    downstream.set(link.fromTaskId, [...downstream.get(link.fromTaskId) ?? [], link.toTaskId]);
    childIds.add(link.toTaskId);
  }
  const results: TaskChainItem[][] = [];
  const walk = (item: TaskChainItem, path: TaskChainItem[]) => {
    if (results.length >= 100 || path.some(previous => previous.taskId === item.taskId)) return;
    const nextPath = [...path, item], next = downstream.get(item.taskId) ?? [];
    if (!next.length || nextPath.length >= 16) { results.push(nextPath); return; }
    for (const id of next) {
      walk(byId.get(id)!, nextPath);
      if (results.length >= 100) break;
    }
  };
  for (const item of chain.items) {
    if (!childIds.has(item.taskId)) walk(item, []);
    if (results.length >= 100) break;
  }
  return results;
}

export interface DisplayNode {
  node: CanvasNode;
  pipelineId: string;
  taskName: string;
  readonly: boolean;
  originalNodeId: string;
  offsetX: number;
  offsetY: number;
}
export interface DisplayEdge extends CanvasEdge { readonly: boolean; relationship?: boolean }

/** Transform only display coordinates/IDs. Independent executable DSLs stay intact. */
export function buildCompositeCanvas(chain: TaskChain | null, currentId: string | null,
  currentDsl: CanvasDsl, drafts: Record<string, CanvasDsl> = {}) {
  const nodes: Record<string, DisplayNode> = {};
  const edges: Record<string, DisplayEdge> = {};
  currentDsl = hiveSinkCanvas(currentDsl);
  if (!chain || chain.items.length < 2 || !chain.items.some(item => item.pipeline.id === currentId)) {
    currentDsl.nodes.forEach(node => { nodes[node.id] = { node, pipelineId: currentId ?? '', taskName: '', readonly: false, originalNodeId: node.id, offsetX: 0, offsetY: 0 }; });
    currentDsl.edges.forEach(edge => { edges[edge.id] = { ...edge, readonly: false }; });
    return { nodes, edges };
  }
  const endpoints = new Map<string, { sources: string[]; sinks: string[] }>();
  let nextY = 120;
  for (const item of chain.items) {
    const readonly = item.pipeline.id !== currentId;
    const dsl = hiveSinkCanvas(readonly ? drafts[item.pipeline.id] ?? item.pipeline.dsl : currentDsl);
    if (!dsl?.nodes?.length) continue;
    // Keep the lane origin stable while the user drags editable nodes.
    const layoutBasis = item.pipeline.dsl?.nodes?.length ? item.pipeline.dsl.nodes : dsl.nodes;
    const minX = Math.min(...layoutBasis.map(node => node.x));
    const minY = Math.min(...layoutBasis.map(node => node.y));
    const maxY = Math.max(...layoutBasis.map(node => node.y));
    const offsetX = 120 - minX;
    const offsetY = nextY - minY;
    const displayId = (id: string) => readonly ? `chain:${item.pipeline.id}:${id}` : id;
    const sourceIds: string[] = [], sinkIds: string[] = [];
    for (const node of dsl.nodes) {
      const id = displayId(node.id);
      nodes[id] = { node: { ...node, id, x: node.x + offsetX, y: node.y + offsetY },
        pipelineId: item.pipeline.id, taskName: item.taskName, readonly, originalNodeId: node.id, offsetX, offsetY };
      if (node.category === 'source') sourceIds.push(id);
      if (node.category === 'sink') sinkIds.push(id);
    }
    for (const edge of dsl.edges) {
      const id = readonly ? `chain:${item.pipeline.id}:${edge.id}` : edge.id;
      edges[id] = { ...edge, id, source: displayId(edge.source), target: displayId(edge.target), readonly };
    }
    endpoints.set(item.taskId, { sources: sourceIds, sinks: sinkIds });
    nextY += maxY - minY + 220 + 180;
  }
  for (const link of chain.links ?? []) {
    const from = endpoints.get(link.fromTaskId), to = endpoints.get(link.toTaskId);
    // Never infer a link from a label. Only the server's authorized table identity establishes it.
    if (!from || !to) continue;
    const matchingSinks = from.sinks.filter(id => String(nodes[id].node.config.targetTableId ?? nodes[id].node.config.registeredTableId ?? '') === String(link.tableId));
    const matchingSources = to.sources.filter(id => String(nodes[id].node.config.sourceTableId ?? nodes[id].node.config.registeredTableId ?? '') === String(link.tableId));
    const missingTableId = (id: string, key: string) => !String(nodes[id].node.config[key] ?? nodes[id].node.config.registeredTableId ?? '').trim();
    const sinks = matchingSinks.length ? matchingSinks : from.sinks.length === 1 && missingTableId(from.sinks[0], 'targetTableId') ? from.sinks : [];
    const sources = matchingSources.length ? matchingSources : to.sources.length === 1 && missingTableId(to.sources[0], 'sourceTableId') ? to.sources : [];
    if (sinks.length !== 1 || sources.length !== 1) continue;
    const id = `chain-link:${link.fromTaskId}:${link.toTaskId}`;
    edges[id] = { id, source: sinks[0], target: sources[0], readonly: true, relationship: true, outlet: '下游任务' };
  }
  return { nodes, edges };
}
