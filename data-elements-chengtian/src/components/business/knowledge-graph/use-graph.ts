import { ref, onMounted, onBeforeUnmount, type Ref } from "vue";
import { Graph, register, ExtensionCategory, Circle } from "@antv/g6";
import type { GraphData, GraphNode, GraphEdge } from "./types";
import { ICON_DATA_URL } from "./icons";

/** 自定义实体节点：支持双行标签（中文名 + 英文名），各自独立样式 */
class EntityNode extends Circle {
  override render(attributes: any, container: any): void {
    super.render(attributes, container);
    const r = attributes.r ?? 30;
    const mainFontSize = attributes.labelFontSize ?? 14;
    const offsetY = attributes.labelOffsetY ?? 4;
    this.upsert(
      "sub-label",
      "text",
      {
        x: 0,
        y: r + offsetY + mainFontSize - 10,
        text: attributes.subLabelText ?? "",
        fontSize: attributes.subLabelFontSize ?? 12,
        fill: attributes.subLabelFill ?? "#999",
        textAlign: "center",
        textBaseline: "top",
      },
      container
    );
  }
}

register(ExtensionCategory.NODE, "entity-node", EntityNode);

const deepClone = <T>(obj: T): T => JSON.parse(JSON.stringify(obj));

export interface GraphCallbacks {
  onContextMenu?: (
    type: "canvas" | "node" | "edge",
    targetId: string,
    clientX: number,
    clientY: number,
    canvasPos?: { x: number; y: number }
  ) => void;
}

/** 将业务数据转为 G6 v5 所需的节点格式，x/y 放在 style 内 */
const toG6Nodes = (nodes: GraphNode[]): any[] =>
  nodes.map((n) => ({
    id: n.id,
    data: { ...n },
    style: {
      fill: n.color,
      stroke: n.color,
      r: 30,
      ...(n.x !== undefined ? { x: n.x } : {}),
      ...(n.y !== undefined ? { y: n.y } : {}),
      labelText: n.nameCn,
      labelFontSize: 14,
      labelFontWeight: "bold",
      labelFill: "#262626",
      labelPlacement: "bottom",
      labelOffsetY: 4,
      subLabelText: n.nameEn,
      subLabelFontSize: 12,
      subLabelFill: "#999",
      iconSrc: ICON_DATA_URL[n.icon as keyof typeof ICON_DATA_URL],
      iconWidth: 22,
      iconHeight: 22,
    },
  }));

/** 将业务数据转为 G6 v5 所需的边格式 */
const toG6Edges = (edges: GraphEdge[]) =>
  edges.map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    data: { ...e },
    style: {
      stroke: "#b0b8c1",
      labelText: `${e.relNameEn} ${e.relNameCn ? `(${e.relNameCn})` : ""}`,
      labelFontSize: 12,
      labelFill: "#8c8c8c",
      labelOffsetY: -12,
      labelBackground: true,
      labelBackgroundFill: "rgba(255,255,255,0.85)",
      labelBackgroundRadius: 4,
      labelPadding: [2, 6],
      endArrow: true,
    },
  }));

export function useGraph(
  containerRef: Ref<HTMLElement | null>,
  initialData: GraphData,
  callbacks?: GraphCallbacks
) {
  const graphData = ref<GraphData>(deepClone(initialData));
  let graph: Graph | null = null;

  /** 初始化 G6 图实例 */
  const initGraph = (): void => {
    if (!containerRef.value) return;
    const { clientWidth: width, clientHeight: height } = containerRef.value;
    const firstNodeId = graphData.value.nodes[0]?.id;

    graph = new Graph({
      container: containerRef.value,
      width,
      height,
      data: {
        nodes: toG6Nodes(graphData.value.nodes),
        edges: toG6Edges(graphData.value.edges),
      },
      layout: firstNodeId
        ? { type: "radial", focusNode: firstNodeId, unitRadius: 300, linkDistance: 300 }
        : { type: "force" },
      node: {
        type: "entity-node",
        style: { r: 30, cursor: "pointer" },
      },
      edge: {
        type: "line",
        style: { cursor: "pointer" },
      },
      behaviors: ["drag-canvas", "zoom-canvas", "drag-element"],
    });

    graph.render();
  };

  /** 绑定 G6 事件 */
  const bindEvents = (): void => {
    if (!graph) return;

    // 节点拖拽结束：同步位置到 graphData
    graph.on("node:dragend", (evt: any) => {
      const nodeId: string = evt.target?.id ?? "";
      if (!nodeId) return;
      const model = graph!.getNodeData(nodeId);
      if (!model) return;
      const node = graphData.value.nodes.find((n) => n.id === nodeId);
      if (node) {
        node.x = (model.style?.x as number) ?? node.x;
        node.y = (model.style?.y as number) ?? node.y;
      }
    });

    // 右键菜单事件 —— 统一处理后回调给父组件
    const handleCtxMenu = (type: "canvas" | "node" | "edge", evt: any): void => {
      // 阻止浏览器原生右键菜单
      evt.originalEvent?.preventDefault?.();
      const targetId: string = type !== "canvas" ? (evt.target?.id ?? "") : "";
      const clientX: number = evt.client?.x ?? (evt.originalEvent as MouseEvent)?.clientX ?? 0;
      const clientY: number = evt.client?.y ?? (evt.originalEvent as MouseEvent)?.clientY ?? 0;
      const canvasPos =
        type === "canvas" ? { x: evt.canvas?.x ?? 0, y: evt.canvas?.y ?? 0 } : undefined;
      callbacks?.onContextMenu?.(type, targetId, clientX, clientY, canvasPos);
    };

    graph.on("node:contextmenu", (evt: any) => handleCtxMenu("node", evt));
    graph.on("edge:contextmenu", (evt: any) => handleCtxMenu("edge", evt));
    graph.on("canvas:contextmenu", (evt: any) => handleCtxMenu("canvas", evt));
  };

  onMounted(() => {
    initGraph();
    bindEvents();
  });

  onBeforeUnmount(() => {
    graph?.destroy();
    graph = null;
  });

  // ── 增删改操作（使用 draw() 避免重新运行布局） ──────────────────────────────

  const addNode = (node: GraphNode, position?: { x: number; y: number }): void => {
    const newNode = { ...node, ...(position ?? {}) };
    graphData.value.nodes.push(newNode);
    graph?.addNodeData([toG6Nodes([newNode])[0]]);
    graph?.draw();
  };

  const updateNode = (node: GraphNode): void => {
    const idx = graphData.value.nodes.findIndex((n) => n.id === node.id);
    if (idx === -1) return;
    graphData.value.nodes[idx] = { ...graphData.value.nodes[idx], ...node };
    graph?.updateNodeData([toG6Nodes([graphData.value.nodes[idx]])[0]]);
    graph?.draw();
  };

  const removeNode = (nodeId: string): void => {
    graphData.value.nodes = graphData.value.nodes.filter((n) => n.id !== nodeId);
    // 同时移除关联的边
    graphData.value.edges = graphData.value.edges.filter(
      (e) => e.source !== nodeId && e.target !== nodeId
    );
    graph?.removeNodeData([nodeId]);
    graph?.draw();
  };

  const addEdge = (edge: GraphEdge): void => {
    graphData.value.edges.push(edge);
    graph?.addEdgeData([toG6Edges([edge])[0]]);
    graph?.draw();
  };

  const updateEdge = (edge: GraphEdge): void => {
    const idx = graphData.value.edges.findIndex((e) => e.id === edge.id);
    if (idx === -1) return;
    graphData.value.edges[idx] = { ...graphData.value.edges[idx], ...edge };
    graph?.updateEdgeData([toG6Edges([graphData.value.edges[idx]])[0]]);
    graph?.draw();
  };

  const removeEdge = (edgeId: string): void => {
    graphData.value.edges = graphData.value.edges.filter((e) => e.id !== edgeId);
    graph?.removeEdgeData([edgeId]);
    graph?.draw();
  };

  const fitView = (): void => {
    graph?.fitView();
  };
  const zoomIn = (): void => {
    graph?.zoomBy(1.2);
  };
  const zoomOut = (): void => {
    graph?.zoomBy(0.8);
  };

  return {
    graphData,
    addNode,
    updateNode,
    removeNode,
    addEdge,
    updateEdge,
    removeEdge,
    fitView,
    zoomIn,
    zoomOut,
  };
}
