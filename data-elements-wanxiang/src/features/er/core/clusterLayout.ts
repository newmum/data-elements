import type { Position } from '../types/domain';

export interface LayoutBox { id: string; width: number; height: number }
export interface LayoutLink { source: string; target: string }

/** Keep connected tables together and place isolated tables after them, using actual node heights. */
export function clusteredLayout(nodes: LayoutBox[], links: LayoutLink[], elkPositions?: Record<string, Position>, direction: 'RIGHT' | 'DOWN' = 'RIGHT'): Record<string, Position> {
  const byId = new Map(nodes.map(node => [node.id, node]));
  const neighbors = new Map(nodes.map(node => [node.id, new Set<string>()]));
  for (const link of links) {
    if (!byId.has(link.source) || !byId.has(link.target) || link.source === link.target) continue;
    neighbors.get(link.source)!.add(link.target); neighbors.get(link.target)!.add(link.source);
  }
  const seen = new Set<string>(), connected: LayoutBox[][] = [], isolated: LayoutBox[] = [];
  for (const node of nodes) {
    if (seen.has(node.id)) continue;
    const stack = [node.id], group: LayoutBox[] = [];
    seen.add(node.id);
    while (stack.length) {
      const id = stack.pop()!; group.push(byId.get(id)!);
      for (const next of neighbors.get(id)!) if (!seen.has(next)) { seen.add(next); stack.push(next); }
    }
    if (group.length === 1) isolated.push(group[0]); else connected.push(group);
  }
  connected.sort((a, b) => b.length - a.length);
  const result: Record<string, Position> = {};
  const area = nodes.reduce((sum, node) => sum + (node.width + 110) * (node.height + 110), 0);
  const targetWidth = Math.max(1000, Math.sqrt(area) * 1.35);
  let shelfX = 0, shelfY = 0, shelfHeight = 0;
  for (const group of connected) {
    const positioned = group.every(node => elkPositions?.[node.id]) ? group.map(node => ({ node, point: elkPositions![node.id] })) : fallbackGroup(group, direction);
    const minX = Math.min(...positioned.map(item => item.point.x)), minY = Math.min(...positioned.map(item => item.point.y));
    const width = Math.max(...positioned.map(item => item.point.x + item.node.width)) - minX;
    const height = Math.max(...positioned.map(item => item.point.y + item.node.height)) - minY;
    if (shelfX > 0 && shelfX + width > targetWidth) { shelfY += shelfHeight + 120; shelfX = 0; shelfHeight = 0; }
    for (const item of positioned) result[item.node.id] = { x: shelfX + item.point.x - minX, y: shelfY + item.point.y - minY };
    shelfX += width + 120; shelfHeight = Math.max(shelfHeight, height);
  }
  let x = 0, y = connected.length ? shelfY + shelfHeight + 180 : 0, rowHeight = 0;
  for (const node of isolated) {
    if (x > 0 && x + node.width > targetWidth) { y += rowHeight + 96; x = 0; rowHeight = 0; }
    result[node.id] = { x, y }; x += node.width + 96; rowHeight = Math.max(rowHeight, node.height);
  }
  return result;
}

function fallbackGroup(group: LayoutBox[], direction: 'RIGHT' | 'DOWN') {
  const columns = Math.max(1, Math.ceil(Math.sqrt(group.length)));
  const rows = Math.ceil(group.length / columns);
  const cell = (i: number) => direction === 'DOWN' ? { col: Math.floor(i / rows), row: i % rows } : { col: i % columns, row: Math.floor(i / columns) };
  const widths = Array.from({ length: columns }, (_, col) => Math.max(...group.filter((_, i) => cell(i).col === col).map(node => node.width)));
  const heights = Array.from({ length: rows }, (_, row) => Math.max(...group.filter((_, i) => cell(i).row === row).map(node => node.height)));
  const xs = widths.map((_, i) => widths.slice(0, i).reduce((sum, width) => sum + width + 96, 0));
  const ys = heights.map((_, i) => heights.slice(0, i).reduce((sum, height) => sum + height + 96, 0));
  return group.map((node, i) => ({ node, point: { x: xs[cell(i).col], y: ys[cell(i).row] } }));
}
