import {
  AimOutlined,
  AppstoreOutlined,
  CompressOutlined,
  MinusOutlined,
  PlusOutlined,
  RedoOutlined,
  UndoOutlined,
} from "@ant-design/icons";
import { Button, Tooltip } from "antd";
import { useEffect, useRef, useState } from "react";
import { useStore } from "zustand";
import { useEditorStore } from "@/store/editorStore";
import { computeLayeredLayout } from "./layout";
import { syncEdgeRuntime, syncEdgeSelection, syncGraph } from "./syncGraph";
import { useGraph } from "./useGraph";
import "./Canvas.css";

export function Canvas() {
  const flow = useEditorStore((s) => s.flow);
  const selectedEdgeIds = useEditorStore((s) => s.selectedEdgeIds);
  const focusNodeId = useEditorStore((s) => s.focusNodeId);
  const nodeStatus = useEditorStore((s) => s.nodeStatus);
  const canUndo = useStore(
    useEditorStore.temporal,
    (s) => s.pastStates.length > 0,
  );
  const canRedo = useStore(
    useEditorStore.temporal,
    (s) => s.futureStates.length > 0,
  );
  const { containerRef, graphRef, ready } = useGraph();
  const fittedFlow = useRef("");
  const [zoom, setZoom] = useState(100);
  const fit = () =>
    graphRef.current?.zoomToFit({ padding: 64, maxScale: 1, minScale: 0.1 });
  useEffect(() => {
    if (!ready || !flow || !graphRef.current) return;
    const graph = graphRef.current;
    syncGraph(graph, flow);
    if (fittedFlow.current !== flow.flowId && flow.nodes.length) {
      const frame = requestAnimationFrame(() => {
        const container = containerRef.current;
        if (!container) return;
        graph.resize(container.clientWidth, container.clientHeight);
        graph.zoomToFit({ padding: 64, maxScale: 1, minScale: 0.1 });
        fittedFlow.current = flow.flowId;
      });
      return () => cancelAnimationFrame(frame);
    }
  }, [flow, graphRef, containerRef, ready]);
  useEffect(() => {
    const graph = graphRef.current;
    if (!ready || !graph) return;
    const updateZoom = () => setZoom(Math.round(graph.zoom() * 100));
    graph.on("scale", updateZoom);
    updateZoom();
    return () => {
      graph.off("scale", updateZoom);
    };
  }, [graphRef, ready]);
  useEffect(() => {
    if (ready && graphRef.current)
      syncEdgeSelection(graphRef.current, selectedEdgeIds);
  }, [ready, graphRef, selectedEdgeIds]);
  useEffect(() => {
    if (ready && graphRef.current && flow)
      syncEdgeRuntime(graphRef.current, flow, nodeStatus, selectedEdgeIds);
  }, [ready, graphRef, flow, nodeStatus, selectedEdgeIds]);
  useEffect(() => {
    if (!ready || !graphRef.current || !focusNodeId) return;
    const cell = graphRef.current.getCellById(focusNodeId);
    const graph = graphRef.current;
    // The config panel changes the available canvas width in the same render.
    // Wait for that layout before centering, so the selected node stays visible.
    const frame = requestAnimationFrame(() => {
      const container = containerRef.current;
      if (container)
        graph.resize(container.clientWidth, container.clientHeight);
      if (cell?.isNode()) graph.centerCell(cell);
      useEditorStore.getState().consumeFocusNode();
    });
    return () => cancelAnimationFrame(frame);
  }, [ready, graphRef, containerRef, focusNodeId]);
  return (
    <div
      className="fs-canvas"
      onDragOver={(e) => e.preventDefault()}
      onDrop={(e) => {
        e.preventDefault();
        const type = e.dataTransfer.getData("application/x-yunti-node-type");
        if (!type || !graphRef.current) return;
        const position = graphRef.current.clientToLocal(e.clientX, e.clientY);
        useEditorStore
          .getState()
          .addNode(type, { x: position.x - 107, y: position.y - 36 });
      }}
    >
      <div
        ref={containerRef}
        className="fs-canvas__graph"
        aria-label="服务编排画布"
      />
      <div className="fs-canvas__toolbar" role="toolbar" aria-label="画布工具">
        <Tooltip title="撤销（Ctrl+Z）">
          <Button
            type="text"
            size="small"
            aria-label="撤销"
            icon={<UndoOutlined />}
            disabled={!canUndo}
            onClick={() => useEditorStore.getState().undo()}
          />
        </Tooltip>
        <Tooltip title="重做（Ctrl+Shift+Z）">
          <Button
            type="text"
            size="small"
            aria-label="重做"
            icon={<RedoOutlined />}
            disabled={!canRedo}
            onClick={() => useEditorStore.getState().redo()}
          />
        </Tooltip>
        <i />
        <Tooltip title="缩小">
          <Button
            type="text"
            size="small"
            aria-label="缩小画布"
            icon={<MinusOutlined />}
            disabled={zoom <= 10}
            onClick={() =>
              graphRef.current?.zoomTo(Math.max(0.1, zoom / 100 - 0.1))
            }
          />
        </Tooltip>
        <Tooltip title="恢复 100%">
          <Button
            type="text"
            size="small"
            className="fs-canvas__zoom"
            aria-label="恢复画布比例"
            onClick={() => graphRef.current?.zoomTo(1)}
          >
            {zoom}%
          </Button>
        </Tooltip>
        <Tooltip title="放大">
          <Button
            type="text"
            size="small"
            aria-label="放大画布"
            icon={<PlusOutlined />}
            disabled={zoom >= 400}
            onClick={() =>
              graphRef.current?.zoomTo(Math.min(4, zoom / 100 + 0.1))
            }
          />
        </Tooltip>
        <i />
        <Tooltip title="适应画布">
          <Button
            type="text"
            size="small"
            aria-label="适应画布"
            icon={<CompressOutlined />}
            onClick={fit}
          />
        </Tooltip>
        <Tooltip title="居中显示">
          <Button
            type="text"
            size="small"
            aria-label="居中显示"
            icon={<AimOutlined />}
            onClick={() => graphRef.current?.centerContent()}
          />
        </Tooltip>
        <Tooltip title="整理布局（可撤销）">
          <Button
            type="text"
            size="small"
            aria-label="整理布局"
            icon={<AppstoreOutlined />}
            onClick={() => {
              if (flow)
                useEditorStore
                  .getState()
                  .applyLayout(computeLayeredLayout(flow));
              requestAnimationFrame(fit);
            }}
          />
        </Tooltip>
      </div>
      <span className="fs-canvas__hint">
        拖动画布平移 · 滚轮缩放 · Shift 多选
      </span>
    </div>
  );
}
