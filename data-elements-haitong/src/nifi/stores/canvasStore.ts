import { create } from 'zustand';
import { nanoid } from 'nanoid';
import { normalizeCanvasDsl, type CanvasDsl, type CanvasEdge, type CanvasMode, type CanvasNode } from '@/types/dsl';
import type { ComponentManifest } from '@/types/manifest';

export const FIELD_MAPPING_DEFAULT = JSON.stringify({
  version: '1.0',
  passthroughUnmapped: false,
  passthroughCaseSensitive: true,
  onMissingSource: 'NULL',
  onTypeMismatch: 'CAST',
  mappings: [],
}, null, 2);

interface CanvasState {
  nodes: Record<string, CanvasNode>;
  edges: Record<string, CanvasEdge>;
  /** Currently selected node id (for property panel — used in stage 4). */
  selectedNodeId: string | null;
  /** Node id whose drawer is open (double-click). */
  drawerNodeId: string | null;
  openDrawer: (id: string | null) => void;
  /** Node id for which the user clicked the right-side + button (open NodeSelector). */
  addAfterTarget: string | null;
  requestAddAfter: (id: string | null) => void;
  /** Node the user wants Canvas.tsx to scroll-to + flash. */
  focusNodeId: string | null;
  focusNodeNonce: number;
  focusNode: (id: string | null) => void;
  /** Currently loaded pipeline metadata (null = unsaved). */
  currentPipelineId: string | null;
  currentPipelineName: string | null;
  setCurrentPipeline: (id: string | null, name: string | null) => void;
  /** Canvas interaction / observability mode. */
  canvasMode: CanvasMode;
  setCanvasMode: (mode: CanvasMode) => void;
  syncCompletion: { pipelineId: string; at: number; flowFiles?: number; bytes?: string; message: string } | null;
  setSyncCompletion: (completion: CanvasState['syncCompletion']) => void;

  /** 未配置场景下，模板加载完后自动触发一次字段映射推荐 */
  pendingAutoRecommend: boolean;
  setPendingAutoRecommend: (v: boolean) => void;

  addNodeFromManifest: (manifest: ComponentManifest, x: number, y: number) => CanvasNode;
  addNodeAfter: (sourceId: string, manifest: ComponentManifest) => CanvasNode | null;
  removeNode: (id: string) => void;
  moveNode: (id: string, x: number, y: number) => void;
  updateNodeConfig: (id: string, patch: Record<string, unknown>) => void;
  switchNodeManifest: (id: string, manifest: ComponentManifest) => void;
  setNodeLabel: (id: string, label: string) => void;
  selectNode: (id: string | null) => void;

  addEdge: (source: string, target: string, outlet?: string) => CanvasEdge | null;
  removeEdge: (id: string) => void;
  setEdgeOutlet: (id: string, outlet: string) => void;

  loadDsl: (dsl: CanvasDsl) => void;
  toDsl: () => CanvasDsl;
  clear: () => void;
}

function defaultsFromManifest(m: ComponentManifest): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  m.fields?.forEach((f) => {
    if (f.default !== undefined) {
      out[f.key] = ['transform.field-mapping', 'transform.field-enrichment'].includes(m.key) && f.key === 'mappings'
        ? FIELD_MAPPING_DEFAULT
        : f.default;
    }
  });
  return out;
}

export const useCanvasStore = create<CanvasState>((set, get) => ({
  nodes: {},
  edges: {},
  selectedNodeId: null,
  drawerNodeId: null,
  openDrawer: (id) => set({ drawerNodeId: id, selectedNodeId: id ?? get().selectedNodeId }),
  addAfterTarget: null,
  requestAddAfter: (id) => set({ addAfterTarget: id }),
  focusNodeId: null,
  focusNodeNonce: 0,
  focusNode: (id) => set((s) => ({
    focusNodeId: id,
    focusNodeNonce: s.focusNodeNonce + 1,
    selectedNodeId: id ?? s.selectedNodeId,
  })),
  currentPipelineId: null,
  currentPipelineName: null,
  setCurrentPipeline: (id, name) => set({ currentPipelineId: id, currentPipelineName: name }),
  canvasMode: 'EDIT',
  setCanvasMode: (mode) => set({ canvasMode: mode }),
  syncCompletion: null,
  setSyncCompletion: (completion) => set({ syncCompletion: completion }),
  pendingAutoRecommend: false,
  setPendingAutoRecommend: (v: boolean) => set({ pendingAutoRecommend: v }),

  addNodeFromManifest: (manifest, x, y) => {
    const node: CanvasNode = {
      id: `n_${nanoid(8)}`,
      manifestKey: manifest.key,
      label: defaultNodeLabel(manifest),
      category: manifest.category,
      x,
      y,
      config: defaultsFromManifest(manifest),
    };
    set((s) => ({ nodes: { ...s.nodes, [node.id]: node } }));
    return node;
  },

  addNodeAfter: (sourceId, manifest) => {
    const src = get().nodes[sourceId];
    if (!src) return null;
    const node: CanvasNode = {
      id: `n_${nanoid(8)}`,
      manifestKey: manifest.key,
      label: defaultNodeLabel(manifest),
      category: manifest.category,
      x: src.x + 460,
      y: src.y,
      config: defaultsFromManifest(manifest),
    };
    const edge: CanvasEdge = { id: `e_${nanoid(8)}`, source: sourceId, target: node.id };
    set((s) => ({
      nodes: { ...s.nodes, [node.id]: node },
      edges: { ...s.edges, [edge.id]: edge },
      selectedNodeId: node.id,
    }));
    return node;
  },

  removeNode: (id) =>
    set((s) => {
      const { [id]: _, ...nodes } = s.nodes;
      const edges = Object.fromEntries(
        Object.entries(s.edges).filter(([, e]) => e.source !== id && e.target !== id),
      );
      return {
        nodes,
        edges,
        selectedNodeId: s.selectedNodeId === id ? null : s.selectedNodeId,
        drawerNodeId: s.drawerNodeId === id ? null : s.drawerNodeId,
        addAfterTarget: s.addAfterTarget === id ? null : s.addAfterTarget,
      };
    }),

  moveNode: (id, x, y) =>
    set((s) => {
      const n = s.nodes[id];
      if (!n) return s;
      return { nodes: { ...s.nodes, [id]: { ...n, x, y } } };
    }),

  updateNodeConfig: (id, patch) =>
    set((s) => {
      const n = s.nodes[id];
      if (!n) return s;
      return { nodes: { ...s.nodes, [id]: { ...n, config: { ...n.config, ...patch } } } };
    }),

  switchNodeManifest: (id, manifest) =>
    set((s) => {
      const n = s.nodes[id];
      if (!n) return s;
      const newConfig = defaultsFromManifest(manifest);
      return {
        nodes: {
          ...s.nodes,
          [id]: {
            ...n,
            manifestKey: manifest.key,
            label: manifest.label,
            category: manifest.category,
            config: newConfig,
          },
        },
      };
    }),

  setNodeLabel: (id, label) =>
    set((s) => {
      const n = s.nodes[id];
      if (!n) return s;
      return { nodes: { ...s.nodes, [id]: { ...n, label } } };
    }),

  selectNode: (id) => set({ selectedNodeId: id }),

  addEdge: (source, target, outlet) => {
    if (source === target) return null;
    // Reject duplicate (source -> target) regardless of outlet.
    const existing = Object.values(get().edges).find(
      (e) => e.source === source && e.target === target,
    );
    if (existing) return null;
    const edge: CanvasEdge = { id: `e_${nanoid(8)}`, source, target, outlet };
    set((s) => ({ edges: { ...s.edges, [edge.id]: edge } }));
    return edge;
  },

  removeEdge: (id) =>
    set((s) => {
      const { [id]: _, ...edges } = s.edges;
      return { edges };
    }),

  setEdgeOutlet: (id, outlet) =>
    set((s) => {
      const e = s.edges[id];
      if (!e) return s;
      return { edges: { ...s.edges, [id]: { ...e, outlet } } };
    }),

  loadDsl: (dsl) => {
    const normalized = normalizeCanvasDsl(dsl);
    set({
      nodes: Object.fromEntries(normalized.nodes.map((n) => [n.id, n])),
      edges: Object.fromEntries(normalized.edges.map((e) => [e.id, e])),
      selectedNodeId: null,
      drawerNodeId: null,
      addAfterTarget: null,
    });
  },

  /** Reset everything including the current-pipeline pointer. */

  toDsl: () => ({
    version: 1,
    nodes: Object.values(get().nodes),
    edges: Object.values(get().edges),
  }),

  clear: () => set({
    nodes: {}, edges: {}, selectedNodeId: null, drawerNodeId: null,
    addAfterTarget: null, focusNodeId: null, focusNodeNonce: 0,
    currentPipelineId: null, currentPipelineName: null, canvasMode: 'EDIT',
    syncCompletion: null, pendingAutoRecommend: false,
  }),
}));

function defaultNodeLabel(manifest: ComponentManifest) {
  if (manifest.category === 'source') return '来源库';
  if (manifest.category === 'sink') return '目标库';
  return manifest.label;
}
