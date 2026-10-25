import type { FlowError, FlowStatusPayload, LiveFlowBulletin } from '../api/pipelines';

/** Runtime badges use the current NiFi bulletin snapshot; persisted records remain in ErrorPanel. */
export function groupActiveFlowErrors(
  status: FlowStatusPayload | undefined,
  bulletins: readonly LiveFlowBulletin[],
  nodeIds: ReadonlySet<string>,
): Record<string, FlowError[]> {
  const result: Record<string, FlowError[]> = {};
  const historicalNodeIds = new Map<string, Set<string>>();
  const add = (error: FlowError) => {
    if (error.nodeId && nodeIds.has(error.nodeId)) (result[error.nodeId] ??= []).push(error);
  };
  for (const error of status?.errors ?? []) {
    if (error.phase !== 'RUNTIME') add(error);
    else if (error.nodeId && error.nodeLabel && nodeIds.has(error.nodeId)) {
      const matches = historicalNodeIds.get(error.nodeLabel) ?? new Set<string>();
      matches.add(error.nodeId); historicalNodeIds.set(error.nodeLabel, matches);
    }
  }
  for (const bulletin of bulletins) {
    const processorId = bulletin.sourceId ?? bulletin.message.match(/\[id=([^\]\s]+)\]/)?.[1];
    let nodeId = processorId ? status?.nodeMapping?.processorIdToCanvasNode?.[processorId] : undefined;
    // Older backends return sourceName without sourceId. Only use an unambiguous, authorized label.
    if (!nodeId && !processorId) {
      const matches = historicalNodeIds.get(bulletin.sourceName);
      if (matches?.size === 1) nodeId = matches.values().next().value;
    }
    if (!nodeId || !nodeIds.has(nodeId)) continue;
    add({ id: `live-${bulletin.id}`, phase: 'RUNTIME', level: bulletin.level,
      nodeId, nodeLabel: bulletin.sourceName, message: bulletin.message, occurredAt: bulletin.timestamp });
  }
  return result;
}
