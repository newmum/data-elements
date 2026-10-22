import type { Graph, Edge, Node, Cell } from "@antv/x6";
import { message } from "antd";
import { hasErrors, validateFlow } from "@/domain/validators";
import { useEditorStore } from "@/store/editorStore";
import { useUiStore } from "@/store/uiStore";

function extractEndpoint(
  edge: Edge,
  end: "source" | "target",
): { cell?: string; port?: string } {
  const value = end === "source" ? edge.getSource() : edge.getTarget();
  if (typeof value === "object" && value !== null && "cell" in value) {
    return {
      cell: typeof value.cell === "string" ? value.cell : undefined,
      port: typeof value.port === "string" ? value.port : undefined,
    };
  }
  return {};
}

function wouldBreakFlow(edge: Edge): string | null {
  const flow = useEditorStore.getState().flow;
  if (!flow) return "当前没有可编辑编排";
  const source = extractEndpoint(edge, "source");
  const target = extractEndpoint(edge, "target");
  if (!source.cell || !target.cell) return "连线必须连接到节点端口";
  if (source.cell === target.cell) return "禁止节点自环";

  const candidate = {
    ...flow,
    edges: [
      ...flow.edges,
      {
        id: edge.id,
        source: source.cell,
        target: target.cell,
        sourcePort: source.port ?? "out",
        targetPort: target.port ?? "in",
      },
    ],
  };
  const errors = validateFlow(candidate).filter(
    (p) =>
      p.severity === "error" &&
      (p.message.includes("自环") ||
        p.message.includes("重复") ||
        p.message.includes("环")),
  );
  return hasErrors(errors) ? errors[0].message : null;
}

export function bindGraphEvents(graph: Graph): () => void {
  const onNodeMoved = ({ node }: { node: Node }) => {
    const position = node.position();
    useEditorStore
      .getState()
      .moveNode(node.id, { x: position.x, y: position.y });
  };

  const onCellClick = ({ cell, e }: { cell: Cell; e: MouseEvent }) => {
    const multi = e.shiftKey || e.ctrlKey || e.metaKey;
    const state = useEditorStore.getState();
    const nextNodes = new Set(multi ? state.selectedNodeIds : []);
    const nextEdges = new Set(multi ? state.selectedEdgeIds : []);
    if (cell.isNode()) {
      if (!multi) useUiStore.getState().setRightCollapsed(false);
      if (nextNodes.has(cell.id)) nextNodes.delete(cell.id);
      else nextNodes.add(cell.id);
    }
    if (cell.isEdge()) {
      if (nextEdges.has(cell.id)) nextEdges.delete(cell.id);
      else nextEdges.add(cell.id);
    }
    state.setSelection([...nextNodes], [...nextEdges]);
  };

  const onBlankClick = () => useEditorStore.getState().setSelection([], []);

  const onEdgeConnected = ({ edge, isNew }: { edge: Edge; isNew: boolean }) => {
    if (!isNew) return;
    const reason = wouldBreakFlow(edge);
    if (reason) {
      graph.removeCell(edge);
      void message.error(reason);
      return;
    }
    const source = extractEndpoint(edge, "source");
    const target = extractEndpoint(edge, "target");
    if (!source.cell || !target.cell) return;
    useEditorStore.getState().addEdge({
      id: edge.id,
      source: source.cell,
      target: target.cell,
      sourcePort: source.port ?? "out",
      targetPort: target.port ?? "in",
    });
  };

  const onCellRemoved = ({ cell }: { cell: Node | Edge }) => {
    if (cell.isEdge()) {
      useEditorStore.getState().removeEdge(cell.id);
    }
  };

  graph.on("node:moved", onNodeMoved);
  graph.on("cell:click", onCellClick);
  graph.on("blank:click", onBlankClick);
  graph.on("edge:connected", onEdgeConnected);
  graph.on("cell:removed", onCellRemoved);

  return () => {
    graph.off("node:moved", onNodeMoved);
    graph.off("cell:click", onCellClick);
    graph.off("blank:click", onBlankClick);
    graph.off("edge:connected", onEdgeConnected);
    graph.off("cell:removed", onCellRemoved);
  };
}
