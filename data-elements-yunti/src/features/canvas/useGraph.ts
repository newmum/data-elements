import { useEffect, useRef, useState } from "react";
import { Graph } from "@antv/x6";
import { useEditorStore } from "@/store/editorStore";
import { connectingOptions } from "./edges";
import { bindGraphEvents } from "./graphEvents";
import { registerFlowShapes } from "./registerShapes";
import { computeLayeredLayout } from "./layout";

export function useGraph() {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const graphRef = useRef<Graph | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!containerRef.current || graphRef.current) return;
    const container = containerRef.current;
    registerFlowShapes();

    const graph = new Graph({
      container,
      autoResize: true,
      async: true,
      grid: {
        size: 16,
        visible: true,
        type: "doubleMesh",
        args: [
          { color: "#e3e9f1", thickness: 1 },
          { color: "#ced9e6", thickness: 1, factor: 4 },
        ],
      },
      background: { color: "transparent" },
      panning: { enabled: true, eventTypes: ["leftMouseDown"] },
      mousewheel: { enabled: true, minScale: 0.1, maxScale: 4, factor: 1.08 },
      connecting: connectingOptions,
      interacting: { edgeLabelMovable: false },
    });

    let gridDetail = 0;
    const updateGridDetail = () => {
      const scale = graph.zoom();
      const detail = scale < 0.25 ? 2 : scale < 0.45 ? 1 : 0;
      if (detail === gridDetail) return;
      gridDetail = detail;
      // Keep the original snapping interval while simplifying the grid in a flow overview.
      graph.drawGrid({
        type: "doubleMesh",
        args: [
          { color: detail ? "transparent" : "#e3e9f1", thickness: 1 },
          { color: "#ced9e6", thickness: 1, factor: detail === 2 ? 8 : 4 },
        ],
      });
    };
    graph.on("scale", updateGridDetail);

    const onKeyDown = (event: KeyboardEvent) => {
      const key = event.key.toLowerCase();
      const mod = event.ctrlKey || event.metaKey;
      if (key === "delete" || key === "backspace") {
        event.preventDefault();
        useEditorStore.getState().deleteSelection();
      }
      if (mod && key === "z" && event.shiftKey)
        useEditorStore.getState().redo();
      else if (mod && key === "z") useEditorStore.getState().undo();
      if (mod && key === "z") event.preventDefault();
      if (mod && key === "a") {
        event.preventDefault();
        const flow = useEditorStore.getState().flow;
        useEditorStore
          .getState()
          .setSelection(flow?.nodes.map((node) => node.id) ?? [], []);
      }
      if (key === "l") {
        const flow = useEditorStore.getState().flow;
        if (flow)
          useEditorStore.getState().applyLayout(computeLayeredLayout(flow));
      }
    };
    container.addEventListener("keydown", onKeyDown);
    container.tabIndex = 0;

    const applyLayout = () => {
      const flow = useEditorStore.getState().flow;
      if (flow)
        useEditorStore.getState().applyLayout(computeLayeredLayout(flow));
    };
    graph.on("blank:dblclick", applyLayout);

    const unbind = bindGraphEvents(graph);
    graphRef.current = graph;
    setReady(true);

    return () => {
      container.removeEventListener("keydown", onKeyDown);
      graph.off("blank:dblclick", applyLayout);
      graph.off("scale", updateGridDetail);
      unbind();
      graph.dispose();
      graphRef.current = null;
    };
  }, []);

  return { containerRef, graphRef, ready };
}
