import type { Flow, Position } from '@/domain/flow-dsl';
import { topoSort } from '@/domain/graph-utils';

/**
 * Lightweight layered layout used by the first frontend slice.
 * Keeps the layout deterministic and dependency-independent while covering DAGs well.
 */
export function computeLayeredLayout(flow: Flow): Record<string, Position> {
  const sorted = topoSort(flow).order.length ? topoSort(flow).order : flow.nodes.map((n) => n.id);
  const incoming = new Map<string, string[]>();
  for (const node of flow.nodes) incoming.set(node.id, []);
  for (const edge of flow.edges) incoming.get(edge.target)?.push(edge.source);

  const layer = new Map<string, number>();
  for (const id of sorted) {
    const parents = incoming.get(id) ?? [];
    const maxParentLayer = parents.reduce((max, parentId) => Math.max(max, layer.get(parentId) ?? 0), 0);
    layer.set(id, parents.length ? maxParentLayer + 1 : 0);
  }

  const byLayer = new Map<number, string[]>();
  for (const id of sorted) {
    const l = layer.get(id) ?? 0;
    byLayer.set(l, [...(byLayer.get(l) ?? []), id]);
  }

  const positions: Record<string, Position> = {};
  for (const [l, ids] of byLayer) {
    ids.forEach((id, index) => {
      positions[id] = { x: 80 + l * 280, y: 100 + index * 140 };
    });
  }
  return positions;
}
