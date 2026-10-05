import type { CanvasEdge, CanvasNode } from '@/types/dsl';
import type { FlowStatusPayload, PipelineStatus } from '@/api/pipelines';

export type RuntimeState = 'idle' | 'ready' | 'running' | 'success' | 'warning' | 'error';

export interface NodeRuntimeMetric {
  state: RuntimeState;
  runStatus?: string;
  input?: string;
  output?: string;
  read?: string;
  written?: string;
  tasks?: string;
  activeThreads?: number;
}

export interface EdgeRuntimeMetric {
  state: RuntimeState;
  label: string;
  queued?: string;
  queuedCount?: number;
  queuedBytes?: number;
  flowFilesIn?: number;
  flowFilesOut?: number;
  percentUseCount?: number;
  percentUseBytes?: number;
}

export interface FlowRuntimeMetrics {
  status: PipelineStatus | 'UNKNOWN';
  deployed: boolean;
  nifiStatusError?: string;
  totals: {
    queued: string;
    queuedCount: number;
    activeThreads: number;
    flowFilesIn: number;
    flowFilesOut: number;
  };
  nodeMetrics: Record<string, NodeRuntimeMetric>;
  edgeMetrics: Record<string, EdgeRuntimeMetric>;
  bottleneckEdgeIds: string[];
}

function snapshotOf(item: any) {
  return item?.processorStatusSnapshot ?? item?.connectionStatusSnapshot ?? item;
}

function aggregateRoot(status: any) {
  return status?.processGroupStatus?.aggregateSnapshot ?? status?.aggregateSnapshot ?? status;
}

function walkProcessGroups(root: any, visit: (snapshot: any) => void) {
  if (!root) return;
  visit(root);
  for (const group of root.processGroupStatusSnapshots ?? []) {
    walkProcessGroups(group?.processGroupStatusSnapshot ?? group, visit);
  }
}

function numberValue(value: unknown): number {
  if (typeof value === 'number') return value;
  if (typeof value !== 'string') return 0;
  const n = Number(value.replace(/,/g, ''));
  return Number.isFinite(n) ? n : 0;
}

function stateFromProcessor(snapshot: any, flowStatus: PipelineStatus | 'UNKNOWN'): RuntimeState {
  if (!snapshot) return 'idle';
  const runStatus = String(snapshot.runStatus ?? '').toLowerCase();
  if (runStatus.includes('invalid')) return 'error';
  if (runStatus.includes('running')) return 'running';
  if (flowStatus === 'RUN_ERROR' || flowStatus === 'DEPLOY_FAILED') return 'error';
  if (flowStatus === 'RUNNING') return 'ready';
  if (flowStatus === 'STOPPED' || flowStatus === 'STOPPING') return 'idle';
  return 'ready';
}

function stateFromEdge(snapshot: any, flowStatus: PipelineStatus | 'UNKNOWN'): RuntimeState {
  const queuedCount = numberValue(snapshot?.queuedCount ?? snapshot?.flowFilesQueued);
  const percentUseCount = numberValue(snapshot?.percentUseCount);
  const percentUseBytes = numberValue(snapshot?.percentUseBytes);
  if (percentUseCount >= 80 || percentUseBytes >= 80 || queuedCount >= 1000) return 'warning';
  if (flowStatus === 'RUN_ERROR' || flowStatus === 'DEPLOY_FAILED') return 'error';
  if (flowStatus === 'RUNNING') return queuedCount > 0 ? 'warning' : 'running';
  if (snapshot) return queuedCount > 0 ? 'warning' : 'ready';
  return 'idle';
}

function formatTrafficLabel(snapshot: any, flowStatus: PipelineStatus | 'UNKNOWN') {
  if (!snapshot) {
    const queued = flowStatus === 'RUNNING' ? '等待' : '0 (0 bytes)';
    return `队列 ${queued}\n入 0\n出 0`;
  }
  const queued = snapshot.queued ?? `${numberValue(snapshot.queuedCount ?? snapshot.flowFilesQueued)} 条`;
  const input = snapshot.input ?? snapshot.flowFilesIn;
  const output = snapshot.output ?? snapshot.flowFilesOut;
  if (input || output) return `队列 ${queued}\n入 ${input ?? 0}\n出 ${output ?? 0}`;
  return `队列 ${queued}\n入 0\n出 0`;
}

export function buildFlowRuntimeMetrics(
  statusPayload: FlowStatusPayload | undefined,
  nodes: Record<string, CanvasNode>,
  edges: Record<string, CanvasEdge>,
): FlowRuntimeMetrics {
  const status = statusPayload?.status ?? 'UNKNOWN';
  const root = aggregateRoot(statusPayload?.nifiStatus);
  const totals = {
    queued: root?.queued ?? '0 (0 bytes)',
    queuedCount: numberValue(root?.queuedCount),
    activeThreads: numberValue(root?.activeThreadCount),
    flowFilesIn: numberValue(root?.flowFilesIn),
    flowFilesOut: numberValue(root?.flowFilesOut),
  };

  const processorById: Record<string, any> = {};
  const connectionById: Record<string, any> = {};
  const connectionByEndpoint = new Map<string, any>();
  walkProcessGroups(root, (pg) => {
    for (const processor of pg?.processorStatusSnapshots ?? []) {
      const snapshot = snapshotOf(processor);
      if (snapshot?.id) processorById[snapshot.id] = snapshot;
    }
    for (const connection of pg?.connectionStatusSnapshots ?? []) {
      const snapshot = snapshotOf(connection);
      if (!snapshot) continue;
      const connectionId = snapshot.id ?? connection.id;
      if (connectionId) connectionById[connectionId] = snapshot;
      const sourceId = snapshot.sourceId ?? snapshot.sourceComponentId;
      const destinationId = snapshot.destinationId ?? snapshot.destinationComponentId;
      if (sourceId && destinationId) connectionByEndpoint.set(`${sourceId}->${destinationId}`, snapshot);
    }
  });

  const nodeMapping = statusPayload?.nodeMapping as any;
  const primaryProcessorIds = nodeMapping?.primaryProcessorIds ?? {};
  const processorIdToCanvasNode = nodeMapping?.processorIdToCanvasNode ?? {};
  const edgeProcessorEndpoints = nodeMapping?.edgeProcessorEndpoints ?? {};
  const nodeMetrics: Record<string, NodeRuntimeMetric> = {};

  for (const nodeId of Object.keys(nodes)) {
    const processorId = primaryProcessorIds[nodeId];
    const snapshot = processorId ? processorById[processorId] : undefined;
    nodeMetrics[nodeId] = snapshot
      ? {
          state: stateFromProcessor(snapshot, status),
          runStatus: snapshot.runStatus,
          input: snapshot.input,
          output: snapshot.output,
          read: snapshot.read,
          written: snapshot.written,
          tasks: snapshot.tasks,
          activeThreads: numberValue(snapshot.activeThreadCount),
        }
      : { state: status === 'RUNNING' ? 'ready' : 'idle' };
  }

  for (const [processorId, nodeId] of Object.entries(processorIdToCanvasNode) as Array<[string, string]>) {
    const snapshot = processorById[processorId];
    if (!snapshot || !nodes[nodeId]) continue;
    const current = nodeMetrics[nodeId];
    if (!current || current.state === 'idle' || current.state === 'ready') {
      nodeMetrics[nodeId] = {
        state: stateFromProcessor(snapshot, status),
        runStatus: snapshot.runStatus,
        input: snapshot.input,
        output: snapshot.output,
        read: snapshot.read,
        written: snapshot.written,
        tasks: snapshot.tasks,
        activeThreads: numberValue(snapshot.activeThreadCount),
      };
    }
  }

  const edgeMetrics: Record<string, EdgeRuntimeMetric> = {};
  const bottleneckEdgeIds: string[] = [];
  for (const edge of Object.values(edges)) {
    const endpoint = edgeProcessorEndpoints[edge.id];
    const sourceProcessorId = endpoint?.sourceProcessorId ?? primaryProcessorIds[edge.source];
    const targetProcessorId = endpoint?.targetProcessorId ?? primaryProcessorIds[edge.target];
    const connection = endpoint?.connectionId && connectionById[endpoint.connectionId]
      ? connectionById[endpoint.connectionId]
      : sourceProcessorId && targetProcessorId
        ? connectionByEndpoint.get(`${sourceProcessorId}->${targetProcessorId}`)
        : undefined;
    const queuedCount = numberValue(connection?.queuedCount ?? connection?.flowFilesQueued);
    const percentUseCount = numberValue(connection?.percentUseCount);
    const percentUseBytes = numberValue(connection?.percentUseBytes);
    const metric: EdgeRuntimeMetric = {
      state: stateFromEdge(connection, status),
      label: formatTrafficLabel(connection, status),
      queued: connection?.queued,
      queuedCount,
      queuedBytes: numberValue(connection?.bytesQueued),
      flowFilesIn: numberValue(connection?.flowFilesIn),
      flowFilesOut: numberValue(connection?.flowFilesOut),
      percentUseCount,
      percentUseBytes,
    };
    edgeMetrics[edge.id] = metric;
    if (metric.state === 'warning') bottleneckEdgeIds.push(edge.id);
  }

  return {
    status,
    deployed: !!statusPayload?.deployed,
    nifiStatusError: statusPayload?.nifiStatusError,
    totals,
    nodeMetrics,
    edgeMetrics,
    bottleneckEdgeIds,
  };
}
