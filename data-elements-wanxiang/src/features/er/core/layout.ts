import type { DiagramNode, Position } from '../types/domain';
import { FIELD_HEIGHT, NODE_FOOTER, NODE_HEADER, NODE_WIDTH } from './model';
export type Box = { id: string; x: number; y: number; width: number; height: number };
export function nodeHeight(fields: number, summary = false): number { return NODE_HEADER + 8 + (summary ? 28 : fields * FIELD_HEIGHT) + NODE_FOOTER + 2; }
function overlaps(a: Box, b: Box, gap = 32): boolean {
  return a.x < b.x+b.width+gap && a.x+a.width+gap > b.x && a.y < b.y+b.height+gap && a.y+a.height+gap > b.y;
}
/** Locked nodes are obstacles, not suggestions to the layout engine. */
export function preserveLockedNodes(nodes: DiagramNode[], proposals: Record<string, Position>, heights: Record<string, number>): DiagramNode[] {
  const placed: Box[] = nodes.filter(n => n.locked).map(n => ({ id:n.entityId, ...n.position, width:NODE_WIDTH, height:heights[n.entityId] ?? 250 }));
  return nodes.map(n => {
    if (n.locked) return n;
    const pos = proposals[n.entityId] ?? n.position;
    const box: Box = { id:n.entityId, ...pos, width:NODE_WIDTH, height:heights[n.entityId] ?? 250 };
    let tries = 0;
    while (placed.some(p => overlaps(box,p)) && tries++ < 10000) box.y += 40;
    placed.push(box);
    return { ...n, position: { x:box.x, y:box.y } };
  });
}
export function fallbackLayout(ids: string[], direction: 'RIGHT' | 'DOWN'): Record<string, Position> {
  const columns = Math.max(1, Math.ceil(Math.sqrt(ids.length)));
  return Object.fromEntries(ids.map((id,i) => [id, direction === 'RIGHT' ? { x:(i%columns)*404,y:Math.floor(i/columns)*440 } : { x:Math.floor(i/columns)*404,y:(i%columns)*440 }]));
}
