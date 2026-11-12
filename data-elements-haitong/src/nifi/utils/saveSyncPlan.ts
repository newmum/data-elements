import type { CanvasDsl } from '../types/dsl.ts';
import { sameExecutableDsl } from './pipelineVersion.ts';

type Mapping = { primaryProcessorIds?: Record<string, string>; processorIdToCanvasNode?: Record<string, string> } | null | undefined;
type Change = { nodeId: string; field: string; currentValue: string | null; status: string };

/** 已有组但缺少映射时不能当成首次部署，否则可能破坏历史流程。 */
export function saveSyncMode(group: string | null | undefined, mapping: Mapping, deployedHash?: string | null): 'deploy' | 'missing-mapping' | 'sync' {
  if (!group?.trim()) return mapping || deployedHash ? 'missing-mapping' : 'deploy';
  if (!mapping?.primaryProcessorIds || !Object.keys(mapping.primaryProcessorIds).length || !mapping.processorIdToCanvasNode
      || !Object.keys(mapping.processorIdToCanvasNode).length) return 'missing-mapping';
  return 'sync';
}

/** 仅后端明确返回的节点属性可认为有增量同步能力。 */
export function planSaveSync<T extends Change>(previous: CanvasDsl | undefined, current: CanvasDsl, changes: T[]) {
  const supported = new Set(changes.map(row => `${row.nodeId}\u0000${row.field}`));
  const strip = (dsl: CanvasDsl): CanvasDsl => ({ ...dsl, nodes: dsl.nodes.map(node => ({
    ...node, config: Object.fromEntries(Object.entries(node.config || {}).filter(([key]) => !supported.has(`${node.id}\u0000${key}`))),
  })) });
  const priorNodes = new Map(previous?.nodes.map(node => [node.id, node]) || []);
  const automatic = changes.filter(row => row.status === 'READY' || row.status === 'MATCHED'
    || (row.status === 'REVIEW'
      && row.currentValue != null && priorNodes.get(row.nodeId)?.config?.[row.field] != null
      && String(priorNodes.get(row.nodeId)?.config?.[row.field]) === row.currentValue));
  return { automatic, unresolved: changes.filter(row => !automatic.includes(row)),
    needsRedeploy: !previous || !sameExecutableDsl(strip(previous), strip(current)) };
}
