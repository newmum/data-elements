import { useEffect, useMemo, useRef, useState } from 'react';
import { Graph, Shape } from '@antv/x6';
import { useQueryClient } from '@tanstack/react-query';
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
import { useFlowStatus, useLiveFlowBulletins } from '@/api/pipelines';
import { useLineageStore } from '@/stores/lineageStore';
import { buildFlowRuntimeMetrics, type EdgeRuntimeMetric } from '@/runtime/runtimeMetrics';
import { useOceanTheme } from '../../design/theme';
import BaseNode from './BaseNode';
import NodeSelector from '@/panels/NodeSelector';
import { useTaskChainStore } from '../stores/taskChainStore';
import { buildCompositeCanvas } from '../utils/compositeCanvas';
import { selectTaskNode } from '../utils/taskSelection';
import { groupActiveFlowErrors } from '../runtime/activeFlowErrors';

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

function labelsForEdge(metric: EdgeRuntimeMetric | undefined, outlet?: string, distance = 0.5) {
  const text = metric?.label ?? outlet;
  if (!text) return [];
  return [{
    position: { distance },
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
  const queryClient = useQueryClient();
  const { dark } = useOceanTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const syncingGraphRef = useRef(false);
  const initialGraphFitRef = useRef(false);
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
  const { data: manifests } = useComponentManifests();
  const manifestsByKey = useMemo(() => Object.fromEntries((manifests ?? []).map(item => [item.key, item])), [manifests]);
  const statusQuery = useFlowStatus(currentPipelineId, canvasMode === 'EDIT' ? 5000 : 3000);
  const liveBulletins = useLiveFlowBulletins(currentPipelineId, statusQuery.data?.deployed === true);
  const nodeErrors = useMemo(() => groupActiveFlowErrors(statusQuery.data, liveBulletins.data ?? [], new Set(Object.keys(nodes))),
    [statusQuery.data, liveBulletins.data, nodes]);
  const runtimeMetrics = useMemo(
    () => buildFlowRuntimeMetrics(statusQuery.data, nodes, edges),
    [statusQuery.data, nodes, edges],
  );
  const taskChain = useTaskChainStore(state => state.chain);
  const drafts = useTaskChainStore(state => state.drafts);
  const composite = useMemo(() => buildCompositeCanvas(taskChain, currentPipelineId,
    { version: 1, nodes: Object.values(nodes), edges: Object.values(edges) }, drafts), [taskChain, currentPipelineId, nodes, edges, drafts]);
  const compositeRef = useRef(composite);
  compositeRef.current = composite;

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
        validateConnection: ({ sourceCell, targetCell }) => useCanvasStore.getState().canvasMode === 'EDIT'
          && !sourceCell?.getData()?.readonly && !targetCell?.getData()?.readonly,
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
      interacting: (cellView) => ({
        nodeMovable: useCanvasStore.getState().canvasMode === 'EDIT' && !cellView.cell.getData()?.readonly,
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
    const selectClickedNode = (node: import('@antv/x6').Node) => {
      const display = compositeRef.current.nodes[node.id] ?? node.getData();
      return selectTaskNode(display, useCanvasStore.getState(), useTaskChainStore.getState(), Boolean(queryClient.isMutating()));
    };
    graph.on('node:click', ({ node }) => {
      if (!selectClickedNode(node)) return;
      setSelectedEdgeId(null);
    });
    graph.on('node:dblclick', ({ node }) => {
      if (!selectClickedNode(node)) return;
      const display = compositeRef.current.nodes[node.id] ?? node.getData();
      openDrawer(display.originalNodeId);
    });
    graph.on('node:contextmenu', ({ e, node }) => {
      e.preventDefault?.();
      if (node.getData()?.readonly) return;
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
      if (edge.getData()?.readonly) return;
      selectNode(null);
      setSelectedEdgeId(edge.id);
    });

    graph.on('node:moved', ({ node }) => {
      if (syncingGraphRef.current) return;
      const display = compositeRef.current.nodes[node.id];
      if (!display || display.readonly) return;
      const { x, y } = node.position();
      moveNode(display.originalNodeId, x - display.offsetX, y - display.offsetY);
    });

    const computeAnchor = (node: import('@antv/x6').Node) => {
      const bbox = node.getBBox();
      const point = graph.localToGraph({ x: bbox.x + bbox.width, y: bbox.y + 16 });
      return { x: point.x, y: point.y, nodeId: node.id };
    };
    graph.on('node:mouseenter', ({ node }) => {
      if (node.getData()?.readonly) return;
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
      if (syncingGraphRef.current) return;
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
        if (c.getData()?.readonly) return;
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
      initialGraphFitRef.current = false;
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

    syncingGraphRef.current = true;
    const existingNodeIds = new Set(g.getNodes().map((n) => n.id));
    const desiredNodeIds = new Set(Object.keys(composite.nodes));

    // remove gone
    existingNodeIds.forEach((id) => {
      if (!desiredNodeIds.has(id)) g.removeNode(id);
    });

    // add / update
    let addedAny = false;
    Object.values(composite.nodes).forEach((display) => {
      const n = display.node;
      const metric = runtimeMetrics.nodeMetrics[n.id];
      const runtime = liveBulletins.isError && metric ? { ...metric, state: 'warning' as const } : metric;
      const data = { node: n, runtime: display.readonly ? undefined : runtime, mode: canvasMode,
        manifest: manifestsByKey[n.manifestKey], errors: display.readonly ? [] : nodeErrors[n.id] ?? [],
        readonly: display.readonly, taskName: display.taskName, pipelineId: display.pipelineId, originalNodeId: display.originalNodeId };
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
          data,
        });
        addedAny = true;
      } else if (existing.isNode()) {
        const pos = existing.position();
        if (pos.x !== n.x || pos.y !== n.y) existing.position(n.x, n.y);
        existing.setData(data, { overwrite: true });
      }
    });

    // When nodes are loaded asynchronously (e.g. via accessTaskId), the initial
    // centerContent() runs on an empty canvas. Re-center once nodes appear.
    if (addedAny && !initialGraphFitRef.current) {
      initialGraphFitRef.current = true;
      setTimeout(() => taskChain && taskChain.items.length > 1 ? g.zoomToFit({ maxScale: 1, padding: 60 }) : g.centerContent(), 0);
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
    syncingGraphRef.current = false;
  }, [composite.nodes, selectedNodeId, selectedEdgeId, runtimeMetrics.nodeMetrics, canvasMode, taskChain, manifestsByKey, nodeErrors, liveBulletins.isError]);

  // ------------------------------------------------------------
  // Sync store edges -> graph
  // ------------------------------------------------------------
  useEffect(() => {
    const g = graphRef.current;
    if (!g) return;

    syncingGraphRef.current = true;
    const existingEdgeIds = new Set(g.getEdges().map((e) => e.id));
    const desiredEdgeIds = new Set(Object.keys(composite.edges));

    existingEdgeIds.forEach((id) => {
      if (!desiredEdgeIds.has(id)) g.removeEdge(id);
    });

    Object.values(composite.edges).forEach((e) => {
      const metric = e.readonly ? undefined : runtimeMetrics.edgeMetrics[e.id];
      const attrs = e.relationship ? { line: { stroke: 'var(--text-secondary)', strokeWidth: 2, strokeDasharray: '7 6',
        targetMarker: { name: 'block', width: 8, height: 8 } } } : attrsForEdge(metric, !e.readonly && canvasMode !== 'EDIT', selectedEdgeId === e.id);
      const labels = labelsForEdge(metric, e.relationship ? e.outlet : e.readonly ? undefined : edgeOutletLabel(e.outlet, e.source), e.relationship ? 0.65 : 0.5);
      const existing = g.getCellById(e.id);
      if (!existing) {
        const edge = g.addEdge({
          id: e.id,
          source: e.relationship ? { cell: e.source } : { cell: e.source, port: 'out' },
          target: e.relationship ? { cell: e.target } : { cell: e.target, port: 'in' },
          attrs,
          labels,
          router: 'normal',
          connector: { name: 'smooth' },
          zIndex: 0,
        });
        edge.setData({ metric, mode: canvasMode, readonly: e.readonly });
      } else if (existing.isEdge()) {
        existing.setAttrs(attrs);
        existing.setLabels(labels);
        existing.setData({ metric, mode: canvasMode, readonly: e.readonly });
      }
    });
    syncingGraphRef.current = false;
  }, [composite.edges, runtimeMetrics.edgeMetrics, canvasMode, selectedEdgeId]);

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
    const activeDisplay = Object.values(compositeRef.current.nodes).find(item => !item.readonly);
    const n = addNodeFromManifest(m, cx - (activeDisplay?.offsetX ?? 0), cy - (activeDisplay?.offsetY ?? 0));
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
    <div className="canvas-shell" style={{ position: 'relative', display: 'flex', flexDirection: 'column', width: '100%', height: '100%', overflow: 'hidden' }}>
      <div className="canvas-viewport" style={{ position: 'relative', flex: 1, minHeight: 0, overflow: 'hidden' }}>
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
          <button
            type="button"
            className="canvas-empty"
            aria-label="添加接入数据源"
            aria-haspopup="dialog"
            aria-expanded={emptyOpen}
          >
            <div className="canvas-empty__plus">
              <PlusOutlined />
            </div>
            <div className="canvas-empty__text">点击添加接入数据源</div>
          </button>
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
    </div>
  );
}
