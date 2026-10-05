import { useEffect, useMemo, useRef, useState } from 'react';
import { Graph, Shape } from '@antv/x6';
import { Selection } from '@antv/x6-plugin-selection';
import { Snapline } from '@antv/x6-plugin-snapline';
import { Keyboard } from '@antv/x6-plugin-keyboard';
import { History } from '@antv/x6-plugin-history';
import { register, Portal } from '@antv/x6-react-shape';
import {
  PlusOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  ExpandOutlined,
  CompressOutlined,
  UndoOutlined,
  RedoOutlined,
} from '@ant-design/icons';
import { useCanvasStore } from '@/stores/canvasStore';
import { useErrorPanelStore } from '@/stores/errorPanelStore';
import { useComponentManifests } from '@/api/manifests';
import { useFlowStatus } from '@/api/pipelines';
import { useLineageStore } from '@/stores/lineageStore';
import { buildFlowRuntimeMetrics, type EdgeRuntimeMetric } from '@/runtime/runtimeMetrics';
import { useOceanTheme } from '../../design/theme';
import BaseNode from './BaseNode';
import NodeSelector from '@/panels/NodeSelector';

const NODE_WIDTH = 360;
const NODE_HEIGHT = 220; // approximate; X6 will keep node at this size

register({
  shape: 'coze-node',
  width: NODE_WIDTH,
  height: NODE_HEIGHT,
  effect: ['data'],
  component: BaseNode as never,
});

// Portal provider required by @antv/x6-react-shape v2 to render React inside X6 nodes.
const X6PortalProvider = Portal.getProvider();

const PORT_GROUPS = {
  in: {
    position: 'left',
    attrs: { circle: { r: 6, magnet: true, stroke: 'var(--port-border)', strokeWidth: 2, fill: 'var(--port-bg)' } },
  },
  out: {
    position: 'right',
    attrs: { circle: { r: 6, magnet: true, stroke: 'var(--port-border)', strokeWidth: 2, fill: 'var(--port-bg)' } },
  },
} as const;

function portsForCategory(cat: string) {
  const items: Array<{ id: string; group: 'in' | 'out' }> = [];
  if (cat !== 'source') items.push({ id: 'in', group: 'in' });
  if (cat !== 'sink') items.push({ id: 'out', group: 'out' });
  return items;
}

function flowStatusLabel(status: string) {
  const labels: Record<string, string> = {
    UNKNOWN: '未部署',
    DRAFT: '草稿',
    SAVED: '已保存',
    DEPLOYING: '部署中',
    RUNNING: '运行中',
    STOPPING: '停止中',
    STOPPED: '已停止',
    DEPLOY_FAILED: '部署失败',
    RUN_ERROR: '运行错误',
  };
  return labels[status] ?? status;
}

function attrsForEdge(metric: EdgeRuntimeMetric | undefined, emphasized: boolean, selected = false) {
  const color = metric?.state === 'error'
    ? 'var(--status-error)'
    : metric?.state === 'warning'
      ? 'var(--status-warning)'
      : emphasized || metric?.state === 'running'
        ? 'var(--edge-color-running)'
        : 'var(--edge-color)';
  return {
    line: {
      stroke: selected ? 'var(--node-border-selected)' : color,
      strokeWidth: selected ? 4 : emphasized || metric?.state === 'warning' ? 3 : 2,
      strokeDasharray: metric?.state === 'running' ? '8 5' : '',
      style: {
        animation: metric?.state === 'running' ? 'edgeFlow 1.1s linear infinite' : '',
        filter: selected ? 'drop-shadow(0 0 5px rgba(77, 83, 232, 0.45))' : '',
      },
      targetMarker: { name: 'block', width: 8, height: 8 },
    },
  };
}

function labelsForEdge(metric: EdgeRuntimeMetric | undefined, outlet?: string) {
  const text = metric?.label ?? outlet;
  if (!text) return [];
  return [{
    attrs: {
      label: {
        text,
        fill: 'var(--text-secondary)',
        fontSize: 11,
        fontWeight: 500,
        lineHeight: 16,
      },
      body: {
        ref: 'label',
        refX: -10,
        refY: -7,
        refWidth: '100%',
        refHeight: '100%',
        refWidth2: 20,
        refHeight2: 14,
        fill: 'var(--node-bg)',
        stroke: 'var(--node-border)',
        strokeWidth: 1,
        rx: 6,
        ry: 6,
      },
    },
  }];
}

function branchOutletForNewEdge(sourceId: string) {
  const state = useCanvasStore.getState();
  const source = state.nodes[sourceId];
  if (source?.manifestKey !== 'branch.conditions') return undefined;
  const routes = Array.isArray(source.config.routes) ? source.config.routes as Array<{ id?: string }> : [];
  const used = new Set(Object.values(state.edges).filter((edge) => edge.source === sourceId).map((edge) => edge.outlet));
  return routes.map((route) => route.id).find((id): id is string => !!id && !used.has(id)) ?? (!used.has('otherwise') ? 'otherwise' : undefined);
}

function edgeOutletLabel(outlet: string | undefined, sourceId: string) {
  if (!outlet) return undefined;
  const source = useCanvasStore.getState().nodes[sourceId];
  if (source?.manifestKey !== 'branch.conditions') return outlet;
  if (outlet === 'otherwise') return String(source.config.defaultRouteName ?? '其他');
  const routes = Array.isArray(source.config.routes) ? source.config.routes as Array<{ id?: string; name?: string }> : [];
  return routes.find((route) => route.id === outlet)?.name ?? outlet;
}

export default function Canvas() {
  const { dark } = useOceanTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const plusHoveredRef = useRef(false);
  const [zoom, setZoom] = useState(1);
  const [canUndo, setCanUndo] = useState(false);
  const [canRedo, setCanRedo] = useState(false);
  const [emptyOpen, setEmptyOpen] = useState(false);
  const [addAnyOpen, setAddAnyOpen] = useState(false);
  const [hoverPlus, setHoverPlus] = useState<{ x: number; y: number; nodeId: string } | null>(null);
  const [plusHovered, setPlusHovered] = useState(false);
  const [plusAnchor, setPlusAnchor] = useState<{ x: number; y: number; nodeId: string } | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);

  const nodes = useCanvasStore((s) => s.nodes);
  const edges = useCanvasStore((s) => s.edges);
  const canvasMode = useCanvasStore((s) => s.canvasMode);
  const currentPipelineId = useCanvasStore((s) => s.currentPipelineId);
  const selectedNodeId = useCanvasStore((s) => s.selectedNodeId);
  const selectNode = useCanvasStore((s) => s.selectNode);
  const openDrawer = useCanvasStore((s) => s.openDrawer);
  const moveNode = useCanvasStore((s) => s.moveNode);
  const addEdge = useCanvasStore((s) => s.addEdge);
  const removeEdge = useCanvasStore((s) => s.removeEdge);
  const removeNode = useCanvasStore((s) => s.removeNode);
  const addNodeFromManifest = useCanvasStore((s) => s.addNodeFromManifest);
  const addNodeAfter = useCanvasStore((s) => s.addNodeAfter);
  const addAfterTarget = useCanvasStore((s) => s.addAfterTarget);
  const requestAddAfter = useCanvasStore((s) => s.requestAddAfter);
  const focusNodeId = useCanvasStore((s) => s.focusNodeId);
  const focusNonce = useCanvasStore((s) => s.focusNodeNonce);
  void useComponentManifests; // selector below uses it indirectly via NodeSelector
  const statusQuery = useFlowStatus(currentPipelineId, canvasMode === 'EDIT' ? 5000 : 3000);
  const runtimeMetrics = useMemo(
    () => buildFlowRuntimeMetrics(statusQuery.data, nodes, edges),
    [statusQuery.data, nodes, edges],
  );

  const isEmpty = Object.keys(nodes).length === 0;

  const canvasColors = () => {
    const scope = containerRef.current?.closest('.nifi-workspace') ?? document.documentElement;
    const styles = getComputedStyle(scope);
    return {
      background: styles.getPropertyValue('--canvas-bg').trim() || '#f7f8fa',
      dot: styles.getPropertyValue('--canvas-dot').trim() || '#e3e6eb',
    };
  };

  // ------------------------------------------------------------
  // Init graph
  // ------------------------------------------------------------
  useEffect(() => {
    if (!containerRef.current) return;
    const colors = canvasColors();
    const graph = new Graph({
      container: containerRef.current,
      autoResize: true,
      background: { color: colors.background },
      grid: {
        visible: true,
        type: 'dot',
        size: 20,
        args: { color: colors.dot, thickness: 1.5 },
      },
      panning: { enabled: true, eventTypes: ['leftMouseDown'] },
      mousewheel: { enabled: true, zoomAtMousePosition: true, modifiers: 'ctrl', minScale: 0.3, maxScale: 2 },
      connecting: {
        router: 'normal',
        connector: { name: 'smooth' },
        anchor: 'center',
        connectionPoint: 'boundary',
        snap: { radius: 24 },
        allowBlank: false,
        allowLoop: false,
        allowNode: false,
        allowMulti: 'withPort',
        highlight: true,
        validateConnection: () => useCanvasStore.getState().canvasMode === 'EDIT',
        createEdge() {
          return new Shape.Edge({
            attrs: {
              line: {
                stroke: 'var(--edge-color)',
                strokeWidth: 2,
                targetMarker: { name: 'block', width: 8, height: 8 },
              },
            },
            zIndex: 0,
          });
        },
      },
      highlighting: {
        magnetAvailable: {
          name: 'stroke',
          args: { padding: 4, attrs: { strokeWidth: 4, stroke: '#4d53e8' } },
        },
      },
      interacting: () => ({
        nodeMovable: useCanvasStore.getState().canvasMode === 'EDIT',
        edgeMovable: false,
        edgeLabelMovable: false,
      }),
    });

    graph.use(new Selection({ enabled: true, multiple: false, rubberband: false, showNodeSelectionBox: false }));
    graph.use(new Snapline({ enabled: true }));
    graph.use(new Keyboard({ enabled: true }));
    graph.use(new History({ enabled: true, ignoreChange: true }));

    graphRef.current = graph;

    // ------ events: state sync ------
    graph.on('node:click', ({ node }) => {
      setSelectedEdgeId(null);
      selectNode(node.id);
    });
    graph.on('node:dblclick', ({ node }) => {
      openDrawer(node.id);
    });
    graph.on('node:contextmenu', ({ e, node }) => {
      e.preventDefault?.();
      const st = useCanvasStore.getState();
      const pid = st.currentPipelineId;
      if (!pid) return;
      useLineageStore.getState().open(pid, node.id);
    });
    graph.on('blank:click', () => {
      selectNode(null);
      setSelectedEdgeId(null);
      setHoverPlus(null);
      setEmptyOpen(false);
      setPlusAnchor(null);
      useCanvasStore.getState().requestAddAfter(null);
      useCanvasStore.getState().openDrawer(null);
      useErrorPanelStore.getState().close();
    });

    graph.on('edge:click', ({ edge }) => {
      selectNode(null);
      setSelectedEdgeId(edge.id);
    });

    graph.on('node:moved', ({ node }) => {
      const { x, y } = node.position();
      moveNode(node.id, x, y);
    });

    const computeAnchor = (node: import('@antv/x6').Node) => {
      const bbox = node.getBBox();
      const point = graph.localToGraph({ x: bbox.x + bbox.width, y: bbox.y + 16 });
      return { x: point.x, y: point.y, nodeId: node.id };
    };
    graph.on('node:mouseenter', ({ node }) => {
      if (useCanvasStore.getState().canvasMode !== 'EDIT') return;
      setHoverPlus(computeAnchor(node));
    });
    graph.on('node:mouseleave', () => {
      // delay so the cursor can travel into the plus button without it disappearing
      window.setTimeout(() => {
        setHoverPlus((curr) => {
          if (!curr) return curr;
          if (plusHoveredRef.current) return curr;
          // keep visible if the node is currently selected
          if (useCanvasStore.getState().selectedNodeId === curr.nodeId) return curr;
          return null;
        });
      }, 80);
    });

    graph.on('edge:connected', ({ isNew, edge }) => {
      if (!isNew) return;
      const src = edge.getSourceCellId();
      const tgt = edge.getTargetCellId();
      if (!src || !tgt) {
        graph.removeEdge(edge.id);
        return;
      }
      const created = addEdge(src, tgt, branchOutletForNewEdge(src));
      graph.removeEdge(edge.id);
      // graph node will re-render edge from store
      if (!created) {
        // duplicate or invalid; drop silently
      }
    });

    graph.on('edge:removed', ({ edge }) => {
      // only if user-driven (not store-driven)
      if (edge.id.startsWith('e_')) removeEdge(edge.id);
    });

    graph.on('history:change', () => {
      setCanUndo(graph.canUndo());
      setCanRedo(graph.canRedo());
    });
    graph.on('scale', ({ sx }) => setZoom(sx));

    // Keyboard shortcuts
    graph.bindKey(['del', 'backspace'], () => {
      if (useCanvasStore.getState().canvasMode !== 'EDIT') return false;
      const sel = graph.getSelectedCells();
      sel.forEach((c) => {
        if (c.isNode()) removeNode(c.id);
        else if (c.isEdge() && c.id.startsWith('e_')) removeEdge(c.id);
      });
      return false;
    });
    graph.bindKey(['ctrl+z', 'meta+z'], () => {
      if (useCanvasStore.getState().canvasMode !== 'EDIT') return false;
      graph.undo();
      return false;
    });
    graph.bindKey(['ctrl+shift+z', 'meta+shift+z', 'ctrl+y', 'meta+y'], () => {
      if (useCanvasStore.getState().canvasMode !== 'EDIT') return false;
      graph.redo();
      return false;
    });

    // initial center
    setTimeout(() => graph.centerContent(), 0);

    return () => {
      graph.dispose();
      graphRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // X6 paints its grid to a canvas at graph creation. Repaint it when the
  // workbench theme changes; CSS variables alone cannot recolor those dots.
  useEffect(() => {
    const graph = graphRef.current;
    if (!graph) return;
    const colors = canvasColors();
    graph.drawBackground({ color: colors.background });
    graph.drawGrid({ type: 'dot', args: { color: colors.dot, thickness: 1.5 } });
  }, [dark]);

  // ------------------------------------------------------------
  // Sync store nodes -> graph
  // ------------------------------------------------------------
  useEffect(() => {
    const g = graphRef.current;
    if (!g) return;

    const existingNodeIds = new Set(g.getNodes().map((n) => n.id));
    const desiredNodeIds = new Set(Object.keys(nodes));

    // remove gone
    existingNodeIds.forEach((id) => {
      if (!desiredNodeIds.has(id)) g.removeNode(id);
    });

    // add / update
    let addedAny = false;
    Object.values(nodes).forEach((n) => {
      const existing = g.getCellById(n.id);
      if (!existing) {
        g.addNode({
          id: n.id,
          shape: 'coze-node',
          x: n.x,
          y: n.y,
          width: NODE_WIDTH,
          height: NODE_HEIGHT,
          ports: { groups: PORT_GROUPS as never, items: portsForCategory(n.category) },
          data: { node: n, runtime: runtimeMetrics.nodeMetrics[n.id], mode: canvasMode },
        });
        addedAny = true;
      } else if (existing.isNode()) {
        const pos = existing.position();
        if (pos.x !== n.x || pos.y !== n.y) existing.position(n.x, n.y);
        existing.setData({ node: n, runtime: runtimeMetrics.nodeMetrics[n.id], mode: canvasMode }, { overwrite: true });
      }
    });

    // When nodes are loaded asynchronously (e.g. via accessTaskId), the initial
    // centerContent() runs on an empty canvas. Re-center once nodes appear.
    if (addedAny) {
      setTimeout(() => g.centerContent(), 0);
    }

    // selection highlight
    if (selectedNodeId) {
      g.cleanSelection();
      const cell = g.getCellById(selectedNodeId);
      if (cell) g.select(cell);
    } else if (selectedEdgeId) {
      g.cleanSelection();
      const cell = g.getCellById(selectedEdgeId);
      if (cell) g.select(cell);
    } else {
      g.cleanSelection();
    }
  }, [nodes, selectedNodeId, selectedEdgeId, runtimeMetrics.nodeMetrics, canvasMode]);

  // ------------------------------------------------------------
  // Sync store edges -> graph
  // ------------------------------------------------------------
  useEffect(() => {
    const g = graphRef.current;
    if (!g) return;

    const existingEdgeIds = new Set(g.getEdges().map((e) => e.id));
    const desiredEdgeIds = new Set(Object.keys(edges));

    existingEdgeIds.forEach((id) => {
      if (!desiredEdgeIds.has(id)) g.removeEdge(id);
    });

    Object.values(edges).forEach((e) => {
      const metric = runtimeMetrics.edgeMetrics[e.id];
      const existing = g.getCellById(e.id);
      if (!existing) {
        const edge = g.addEdge({
          id: e.id,
          source: { cell: e.source, port: 'out' },
          target: { cell: e.target, port: 'in' },
          attrs: attrsForEdge(metric, canvasMode !== 'EDIT', selectedEdgeId === e.id),
          labels: labelsForEdge(metric, edgeOutletLabel(e.outlet, e.source)),
          router: 'normal',
          connector: { name: 'smooth' },
          zIndex: 0,
        });
        edge.setData({ metric, mode: canvasMode });
      } else if (existing.isEdge()) {
        existing.setAttrs(attrsForEdge(metric, canvasMode !== 'EDIT', selectedEdgeId === e.id));
        existing.setLabels(labelsForEdge(metric, edgeOutletLabel(e.outlet, e.source)));
        existing.setData({ metric, mode: canvasMode });
      }
    });
  }, [edges, runtimeMetrics.edgeMetrics, canvasMode, selectedEdgeId]);

  // ------------------------------------------------------------
  // When a node's plus button is clicked (BaseNode -> store), compute
  // the right-edge anchor for that node and open the NodeSelector.
  // ------------------------------------------------------------
  useEffect(() => {
    const g = graphRef.current;
    if (!g) return;
    if (!addAfterTarget) {
      setPlusAnchor(null);
      return;
    }
    const cell = g.getCellById(addAfterTarget);
    if (!cell || !cell.isNode()) return;
    const bbox = cell.getBBox();
    const point = g.localToGraph({ x: bbox.x + bbox.width + 24, y: bbox.y + 16 });
    setPlusAnchor({ x: point.x, y: point.y, nodeId: addAfterTarget });
  }, [addAfterTarget, nodes]);

  // ------------------------------------------------------------
  // Focus / flash a node when something asks (e.g. ErrorPanel "定位到节点")
  // ------------------------------------------------------------
  useEffect(() => {
    const g = graphRef.current;
    if (!g || !focusNodeId) return;
    const cell = g.getCellById(focusNodeId);
    if (!cell || !cell.isNode()) return;
    g.cleanSelection();
    g.select(cell);
    try {
      const bbox = cell.getBBox();
      g.centerPoint(bbox.x + bbox.width / 2, bbox.y + bbox.height / 2);
    } catch { /* ignore */ }
    // brief visual flash via class
    const el = (cell as any).view?.container as HTMLElement | undefined;
    if (el) {
      el.style.transition = 'filter 0.4s ease';
      el.style.filter = 'drop-shadow(0 0 12px #ff4d4f)';
      window.setTimeout(() => { el.style.filter = ''; }, 1200);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusNodeId, focusNonce]);

  // ------------------------------------------------------------
  // Sync plusHoveredRef with state
  // ------------------------------------------------------------
  useEffect(() => {
    plusHoveredRef.current = plusHovered;
  }, [plusHovered]);

  // ------------------------------------------------------------
  // Pin the floating + button next to the currently-selected node so
  // it stays visible without requiring hover.
  // ------------------------------------------------------------
  useEffect(() => {
    const g = graphRef.current;
    if (!g) return;
    if (!selectedNodeId) return;
    const cell = g.getCellById(selectedNodeId);
    if (!cell || !cell.isNode()) return;
    const n = cell;
    const bbox = n.getBBox();
    const point = g.localToGraph({ x: bbox.x + bbox.width, y: bbox.y + 16 });
    setHoverPlus({ x: point.x, y: point.y, nodeId: selectedNodeId });
  }, [selectedNodeId, nodes]);

  // ------------------------------------------------------------
  // Controls
  // ------------------------------------------------------------
  const zoomIn = () => graphRef.current?.zoom(0.1);
  const zoomOut = () => graphRef.current?.zoom(-0.1);
  const zoomReset = () => graphRef.current?.zoomTo(1);
  const fit = () => graphRef.current?.zoomToFit({ maxScale: 1, padding: 40 });
  const undo = () => graphRef.current?.undo();
  const redo = () => graphRef.current?.redo();

  const handleAddFirst = (m: import('@/types/manifest').ComponentManifest) => {
    const g = graphRef.current;
    let cx = 200;
    let cy = 200;
    if (g) {
      const c = g.getGraphArea();
      cx = c.x + c.width / 2 - NODE_WIDTH / 2;
      cy = c.y + c.height / 2 - NODE_HEIGHT / 2;
    }
    const n = addNodeFromManifest(m, cx, cy);
    setEmptyOpen(false);
    setTimeout(() => selectNode(n.id), 0);
  };

  const handleAddAny = (m: import('@/types/manifest').ComponentManifest) => {
    const g = graphRef.current;
    let cx = 200;
    let cy = 200;
    if (g) {
      const c = g.getGraphArea();
      cx = c.x + c.width / 2 - NODE_WIDTH / 2;
      cy = c.y + c.height / 2 - NODE_HEIGHT / 2;
    }
    const n = addNodeFromManifest(m, cx, cy);
    setAddAnyOpen(false);
    setTimeout(() => selectNode(n.id), 0);
  };

  const handleAddAfter = (m: import('@/types/manifest').ComponentManifest) => {
    if (!plusAnchor) return;
    const newNode = addNodeAfter(plusAnchor.nodeId, m);
    setPlusAnchor(null);
    requestAddAfter(null);
    if (newNode) setTimeout(() => selectNode(newNode.id), 0);
  };

  const sourceNode = plusAnchor ? nodes[plusAnchor.nodeId] : null;
  const defaultCatForPlus =
    sourceNode?.category === 'source' || sourceNode?.category === 'transform' || sourceNode?.category === 'branch'
      ? 'transform'
      : 'sink';

  return (
    <div className="canvas-shell" style={{ position: 'relative', width: '100%', height: '100%', overflow: 'hidden' }}>
      <X6PortalProvider />
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />

      {canvasMode === 'EDIT' && !isEmpty && (
        <NodeSelector
          open={addAnyOpen}
          onOpenChange={setAddAnyOpen}
          onSelect={handleAddAny}
          defaultCategory="source"
          placement="rightTop"
        >
          <button
            type="button"
            className="canvas-add-node"
            onClick={() => setAddAnyOpen(true)}
            title="添加节点"
          >
            <PlusOutlined />
            <span>添加节点</span>
          </button>
        </NodeSelector>
      )}

      {/* Empty state hint */}
      {isEmpty && (
        <NodeSelector
          open={emptyOpen}
          onOpenChange={setEmptyOpen}
          onSelect={handleAddFirst}
          defaultCategory="source"
          placement="rightTop"
        >
          <div className="canvas-empty" onClick={() => setEmptyOpen(true)}>
            <div className="canvas-empty__plus">
              <PlusOutlined />
            </div>
            <div className="canvas-empty__text">点击添加接入数据源</div>
          </div>
        </NodeSelector>
      )}

      {/* Hover plus button (positioned at right side of hovered node) */}
      {canvasMode === 'EDIT' && hoverPlus && (
        <div
          style={{
            position: 'absolute',
            left: hoverPlus.x + 12,
            top: hoverPlus.y - 14,
            width: 28,
            height: 28,
            borderRadius: '50%',
            background: 'var(--node-bg)',
            border: '1px solid var(--node-border)',
            color: 'var(--node-border-selected)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            boxShadow: '0 2px 6px rgba(0,0,0,0.08)',
            zIndex: 25,
            fontWeight: 600,
            transition: 'all 0.15s',
          }}
          onMouseEnter={(e) => {
            setPlusHovered(true);
            e.currentTarget.style.background = 'var(--node-border-selected)';
            e.currentTarget.style.color = '#fff';
          }}
          onMouseLeave={(e) => {
            setPlusHovered(false);
            // hide if not currently over node either
            window.setTimeout(() => {
              if (!plusHoveredRef.current) setHoverPlus((curr) => curr);
            }, 80);
            e.currentTarget.style.background = 'var(--node-bg)';
            e.currentTarget.style.color = 'var(--node-border-selected)';
          }}
          onMouseDown={(e) => e.stopPropagation()}
          onClick={(e) => {
            e.stopPropagation();
            requestAddAfter(hoverPlus.nodeId);
          }}
          title="在此后追加节点"
        >
          <PlusOutlined />
        </div>
      )}

      {/* Plus button on a node was clicked -> show NodeSelector anchored to that node's right side */}
      {canvasMode === 'EDIT' && plusAnchor && (
        <NodeSelector
          open={true}
          onOpenChange={(o) => {
            if (!o) {
              setPlusAnchor(null);
              requestAddAfter(null);
            }
          }}
          onSelect={handleAddAfter}
          defaultCategory={defaultCatForPlus}
          placement="rightTop"
        >
          <div
            style={{
              position: 'absolute',
              left: plusAnchor.x,
              top: plusAnchor.y,
              width: 1,
              height: 1,
              pointerEvents: 'none',
            }}
          />
        </NodeSelector>
      )}

      {!isEmpty && (
        <div className="runtime-overview">
          <div className="runtime-overview__header">
            <span className={`runtime-overview__dot is-${runtimeMetrics.status.toLowerCase()}`} />
            <span>{canvasMode === 'EDIT' ? '流量概览' : '运行监控'}</span>
          </div>
          <div className="runtime-overview__grid">
            <div>
              <span>状态</span>
              <strong>{flowStatusLabel(runtimeMetrics.status)}</strong>
            </div>
            <div>
              <span>队列</span>
              <strong>{runtimeMetrics.totals.queued}</strong>
            </div>
            <div>
              <span>线程</span>
              <strong>{runtimeMetrics.totals.activeThreads}</strong>
            </div>
            <div>
              <span>瓶颈</span>
              <strong>{runtimeMetrics.bottleneckEdgeIds.length}</strong>
            </div>
            <div>
              <span>输入</span>
              <strong>{runtimeMetrics.totals.flowFilesIn}</strong>
            </div>
            <div>
              <span>输出</span>
              <strong>{runtimeMetrics.totals.flowFilesOut}</strong>
            </div>
          </div>
          {runtimeMetrics.nifiStatusError && (
            <div className="runtime-overview__warning">NiFi 实时流量暂不可用：{runtimeMetrics.nifiStatusError}</div>
          )}
        </div>
      )}

      {/* Bottom-right controls */}
      <div className="canvas-controls">
        <button className="canvas-controls__btn" onClick={zoomOut} title="缩小">
          <ZoomOutOutlined />
        </button>
        <span className="canvas-controls__zoom-text" onClick={zoomReset} style={{ cursor: 'pointer' }}>
          {Math.round(zoom * 100)}%
        </span>
        <button className="canvas-controls__btn" onClick={zoomIn} title="放大">
          <ZoomInOutlined />
        </button>
        <span className="canvas-controls__divider" />
        <button className="canvas-controls__btn" onClick={fit} title="适应画布">
          <CompressOutlined />
        </button>
        <button className="canvas-controls__btn" onClick={() => graphRef.current?.centerContent()} title="居中">
          <ExpandOutlined />
        </button>
        <span className="canvas-controls__divider" />
        <button className="canvas-controls__btn" onClick={undo} disabled={!canUndo} title="撤销 (Ctrl+Z)">
          <UndoOutlined />
        </button>
        <button className="canvas-controls__btn" onClick={redo} disabled={!canRedo} title="重做 (Ctrl+Shift+Z)">
          <RedoOutlined />
        </button>
      </div>
    </div>
  );
}
