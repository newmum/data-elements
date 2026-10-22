import type { Graph } from '@antv/x6';
import type { Flow, FlowEdge, FlowNode } from '@/domain/flow-dsl';
import { requireNodeDef } from '@/domain/node-registry';
import { activeEdgeAttrs, edgeAttrs, selectedEdgeAttrs } from './edges';
import { toX6Ports } from './ports';

function syncNode(graph: Graph, node: FlowNode): void {
  const existing = graph.getCellById(node.id);
  const def = requireNodeDef(node.type);
  const metadata = {
    id: node.id,
    shape: 'flowserve-node',
    x: node.position.x,
    y: node.position.y,
    width: 214,
    height: 72,
    ports: toX6Ports(def, node),
    data: {
      flowNodeId: node.id,
      type: node.type,
    },
  };

  if (existing?.isNode()) {
    existing.position(node.position.x, node.position.y, { silent: true });
    existing.setData(metadata.data, { silent: true });
    existing.prop('ports', metadata.ports, { silent: true });
    return;
  }
  graph.addNode(metadata);
}

function syncEdge(graph: Graph, edge: FlowEdge): void {
  const existing = graph.getCellById(edge.id);
  const metadata = {
    id: edge.id,
    shape: 'edge',
    source: { cell: edge.source, port: edge.sourcePort },
    target: { cell: edge.target, port: edge.targetPort },
    labels: edge.label ? [{ attrs: { label: { text: edge.label } } }] : [],
    attrs: edgeAttrs,
    router: { name: 'manhattan', args: { padding: 16 } },
    connector: { name: 'rounded', args: { radius: 8 } },
    zIndex: 0,
  };

  if (existing?.isEdge()) {
    existing.setSource(metadata.source, { silent: true });
    existing.setTarget(metadata.target, { silent: true });
    existing.setLabels(metadata.labels, { silent: true });
    existing.attr(metadata.attrs, { silent: true });
    return;
  }
  graph.addEdge(metadata);
}

export function syncGraph(graph: Graph, flow: Flow): void {
  const ids = new Set([...flow.nodes.map((n) => n.id), ...flow.edges.map((e) => e.id)]);
  graph.getCells().forEach((cell) => {
    if (!ids.has(cell.id)) graph.removeCell(cell);
  });
  flow.nodes.forEach((node) => syncNode(graph, node));
  flow.edges.forEach((edge) => syncEdge(graph, edge));
  graph.resize();
}

export function syncEdgeSelection(graph: Graph, selectedEdgeIds: string[]): void {
  const selected = new Set(selectedEdgeIds);
  graph.getEdges().forEach((edge) => {
    const isSelected = selected.has(edge.id);
    edge.attr(isSelected ? selectedEdgeAttrs : edgeAttrs, { silent: true });
  });
}

export function syncEdgeRuntime(
  graph: Graph,
  flow: Flow,
  nodeStatus: Record<string, string>,
  selectedEdgeIds: string[],
): void {
  const selected = new Set(selectedEdgeIds);
  for (const edge of flow.edges) {
    const cell = graph.getCellById(edge.id);
    if (!cell?.isEdge()) continue;
    const active = ['running', 'success'].includes(nodeStatus[edge.source] ?? '')
      && ['running', 'success'].includes(nodeStatus[edge.target] ?? '');
    const isSelected = selected.has(edge.id);
    cell.attr(isSelected ? selectedEdgeAttrs : active ? activeEdgeAttrs : edgeAttrs, { silent: true });
  }
}
