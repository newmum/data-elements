import type { Flow, FlowEdge, FlowNode } from './flow-dsl';

export interface Adjacency {
  /** outgoing edges by source node id */
  out: Map<string, FlowEdge[]>;
  /** incoming edges by target node id */
  in: Map<string, FlowEdge[]>;
  nodeById: Map<string, FlowNode>;
}

export function buildAdjacency(flow: Flow): Adjacency {
  const out = new Map<string, FlowEdge[]>();
  const inc = new Map<string, FlowEdge[]>();
  const nodeById = new Map<string, FlowNode>();
  for (const n of flow.nodes) {
    out.set(n.id, []);
    inc.set(n.id, []);
    nodeById.set(n.id, n);
  }
  for (const e of flow.edges) {
    out.get(e.source)?.push(e);
    inc.get(e.target)?.push(e);
  }
  return { out, in: inc, nodeById };
}

/** Successor node ids of a node, optionally filtered by source port. */
export function successors(adj: Adjacency, nodeId: string, sourcePort?: string): string[] {
  const edges = adj.out.get(nodeId) ?? [];
  return edges
    .filter((e) => (sourcePort ? e.sourcePort === sourcePort : true))
    .map((e) => e.target);
}

/** Self-loop edges (source === target). */
export function selfLoops(flow: Flow): FlowEdge[] {
  return flow.edges.filter((e) => e.source === e.target);
}

/** Duplicate edges (same source+sourcePort+target+targetPort). */
export function duplicateEdges(flow: Flow): FlowEdge[] {
  const seen = new Set<string>();
  const dups: FlowEdge[] = [];
  for (const e of flow.edges) {
    const key = `${e.source}:${e.sourcePort}->${e.target}:${e.targetPort}`;
    if (seen.has(key)) dups.push(e);
    else seen.add(key);
  }
  return dups;
}

export interface TopoResult {
  order: string[];
  /** node ids that remain in a cycle (non-empty => not a DAG) */
  cyclic: string[];
}

/** Kahn topological sort. `cyclic` is non-empty when the graph has a cycle. */
export function topoSort(flow: Flow): TopoResult {
  const adj = buildAdjacency(flow);
  const indeg = new Map<string, number>();
  for (const n of flow.nodes) indeg.set(n.id, (adj.in.get(n.id) ?? []).length);
  const queue = flow.nodes.filter((n) => (indeg.get(n.id) ?? 0) === 0).map((n) => n.id);
  const order: string[] = [];
  while (queue.length) {
    const id = queue.shift()!;
    order.push(id);
    for (const e of adj.out.get(id) ?? []) {
      const d = (indeg.get(e.target) ?? 0) - 1;
      indeg.set(e.target, d);
      if (d === 0) queue.push(e.target);
    }
  }
  const cyclic = flow.nodes.map((n) => n.id).filter((id) => !order.includes(id));
  return { order, cyclic };
}

export function hasCycle(flow: Flow): boolean {
  return topoSort(flow).cyclic.length > 0;
}

/** Forward BFS reachability from a set of start node ids. */
export function reachableFrom(flow: Flow, startIds: string[]): Set<string> {
  const adj = buildAdjacency(flow);
  const seen = new Set<string>(startIds);
  const stack = [...startIds];
  while (stack.length) {
    const id = stack.pop()!;
    for (const e of adj.out.get(id) ?? []) {
      if (!seen.has(e.target)) {
        seen.add(e.target);
        stack.push(e.target);
      }
    }
  }
  return seen;
}

/** Reverse BFS: nodes that can reach any of the target node ids. */
export function canReach(flow: Flow, targetIds: string[]): Set<string> {
  const adj = buildAdjacency(flow);
  const seen = new Set<string>(targetIds);
  const stack = [...targetIds];
  while (stack.length) {
    const id = stack.pop()!;
    for (const e of adj.in.get(id) ?? []) {
      if (!seen.has(e.source)) {
        seen.add(e.source);
        stack.push(e.source);
      }
    }
  }
  return seen;
}
