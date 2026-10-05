import type { DiagramNode, Position } from '../types/domain';
/** Return proposals only for selected, unlocked nodes. The caller records one undoable operation. */
export function alignNodes(nodes: DiagramNode[], selectedIds: string[], direction: 'left' | 'top' | 'horizontal'): Record<string, Position> {
  const selected = new Set(selectedIds);
  const targets = nodes.filter(n => selected.has(n.entityId) && !n.locked);
  if (targets.length < (direction === 'horizontal' ? 3 : 2)) return {};
  const positions: Record<string, Position> = {};
  const minX = Math.min(...targets.map(n => n.position.x)), minY = Math.min(...targets.map(n => n.position.y));
  if (direction === 'horizontal') {
    const sorted = [...targets].sort((a, b) => a.position.x - b.position.x);
    const step = (sorted[sorted.length - 1].position.x - minX) / (sorted.length - 1);
    sorted.forEach((n, i) => { positions[n.entityId] = { x: minX + step * i, y: n.position.y }; });
  } else targets.forEach(n => { positions[n.entityId] = { x: direction === 'left' ? minX : n.position.x, y: direction === 'top' ? minY : n.position.y }; });
  return positions;
}
