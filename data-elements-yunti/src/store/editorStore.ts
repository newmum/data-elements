import { create } from 'zustand';
import { temporal } from 'zundo';
import type { Flow, FlowEdge, FlowNode, Position } from '@/domain/flow-dsl';
import { requireNodeDef } from '@/domain/node-registry';
import { validateFlow, type Problem } from '@/domain/validators';
import { genId } from '@/utils/id';
import type { TraceEntry } from '@/services/types';

export type NodeRunStatus = 'idle' | 'running' | 'success' | 'fail';

interface EditorState {
  flow: Flow | null;
  lockVersion: number;
  selectedNodeIds: string[];
  selectedEdgeIds: string[];
  dirty: boolean;
  problems: Problem[];
  runTrace: TraceEntry[] | null;
  running: boolean;
  nodeStatus: Record<string, NodeRunStatus>;
  focusNodeId: string | null;

  setFlow: (flow: Flow, lockVersion: number) => void;
  addNode: (type: string, position: Position) => string | null;
  updateNode: (id: string, patch: Partial<FlowNode>) => void;
  updateNodeData: (id: string, patch: Record<string, unknown>) => void;
  moveNode: (id: string, position: Position) => void;
  addEdge: (edge: Omit<FlowEdge, 'id' | 'sourcePort' | 'targetPort'> & Partial<Pick<FlowEdge, 'id' | 'sourcePort' | 'targetPort'>>) => void;
  removeEdge: (id: string) => void;
  deleteSelection: () => void;
  setSelection: (nodeIds: string[], edgeIds: string[]) => void;
  consumeFocusNode: () => string | null;
  applyLayout: (positions: Record<string, Position>) => void;
  updateTrigger: (patch: Record<string, unknown>) => void;

  markSaved: (lockVersion: number) => void;
  recompute: () => void;

  setRunning: (running: boolean) => void;
  setRunTrace: (trace: TraceEntry[] | null) => void;
  setNodeStatus: (id: string, status: NodeRunStatus) => void;
  clearRunStatus: () => void;

  undo: () => void;
  redo: () => void;
}

let validationTimer: ReturnType<typeof setTimeout> | undefined;

function withFlowDeferred(flow: Flow): Partial<EditorState> {
  if (validationTimer) globalThis.clearTimeout(validationTimer);
  validationTimer = globalThis.setTimeout(() => {
    useEditorStore.setState((state) => (state.flow === flow ? { problems: validateFlow(flow) } : {}));
  }, 160);
  return { flow, dirty: true };
}

export const useEditorStore = create<EditorState>()(
  temporal(
    (set, get) => ({
      flow: null,
      lockVersion: 0,
      selectedNodeIds: [],
      selectedEdgeIds: [],
      dirty: false,
      problems: [],
      runTrace: null,
      running: false,
      nodeStatus: {},
      focusNodeId: null,

      setFlow: (flow, lockVersion) =>
        set({
          flow,
          lockVersion,
          dirty: false,
          problems: validateFlow(flow),
          selectedNodeIds: [],
          selectedEdgeIds: [],
          runTrace: null,
          nodeStatus: {},
          focusNodeId: null,
        }),

      addNode: (type, position) => {
        const flow = get().flow;
        if (!flow) return null;
        const def = requireNodeDef(type);
        if (def.singleton && flow.nodes.some((n) => n.type === type)) return null;
        const node: FlowNode = {
          id: genId('node'),
          type,
          name: def.label,
          position,
          data: structuredClone(def.defaultData),
        };
        set({
          ...withFlowDeferred({ ...flow, nodes: [...flow.nodes, node] }),
          selectedNodeIds: [node.id],
          selectedEdgeIds: [],
          focusNodeId: node.id,
        });
        return node.id;
      },

      updateNode: (id, patch) => {
        const flow = get().flow;
        if (!flow) return;
        const nodes = flow.nodes.map((n) => (n.id === id ? { ...n, ...patch } : n));
        set(withFlowDeferred({ ...flow, nodes }));
      },

      updateNodeData: (id, patch) => {
        const flow = get().flow;
        if (!flow) return;
        const nodes = flow.nodes.map((n) =>
          n.id === id ? { ...n, data: { ...n.data, ...patch } } : n,
        );
        set(withFlowDeferred({ ...flow, nodes }));
      },

      moveNode: (id, position) => {
        const flow = get().flow;
        if (!flow) return;
        const nodes = flow.nodes.map((n) => (n.id === id ? { ...n, position } : n));
        // position changes shouldn't re-run validation; keep problems as-is
        set({ flow: { ...flow, nodes }, dirty: true });
      },

      addEdge: (edge) => {
        const flow = get().flow;
        if (!flow) return;
        const full: FlowEdge = {
          id: edge.id ?? genId('e'),
          source: edge.source,
          target: edge.target,
          sourcePort: edge.sourcePort ?? 'out',
          targetPort: edge.targetPort ?? 'in',
          label: edge.label,
        };
        set(withFlowDeferred({ ...flow, edges: [...flow.edges, full] }));
      },

      removeEdge: (id) => {
        const flow = get().flow;
        if (!flow) return;
        set(withFlowDeferred({ ...flow, edges: flow.edges.filter((e) => e.id !== id) }));
      },

      deleteSelection: () => {
        const flow = get().flow;
        if (!flow) return;
        const { selectedNodeIds, selectedEdgeIds } = get();
        const nodeSet = new Set(selectedNodeIds);
        const edgeSet = new Set(selectedEdgeIds);
        const nodes = flow.nodes.filter((n) => !nodeSet.has(n.id));
        const edges = flow.edges.filter(
          (e) => !edgeSet.has(e.id) && !nodeSet.has(e.source) && !nodeSet.has(e.target),
        );
        set({
          ...withFlowDeferred({ ...flow, nodes, edges }),
          selectedNodeIds: [],
          selectedEdgeIds: [],
        });
      },

      setSelection: (nodeIds, edgeIds) =>
        set({ selectedNodeIds: nodeIds, selectedEdgeIds: edgeIds }),
      consumeFocusNode: () => {
        const id = get().focusNodeId;
        set({ focusNodeId: null });
        return id;
      },

      applyLayout: (positions) => {
        const flow = get().flow;
        if (!flow) return;
        const nodes = flow.nodes.map((n) =>
          positions[n.id] ? { ...n, position: positions[n.id] } : n,
        );
        set({ flow: { ...flow, nodes }, dirty: true });
      },

      updateTrigger: (patch) => {
        const flow = get().flow;
        if (!flow) return;
        const nodes = flow.nodes.map((n) =>
          n.type === 'trigger' ? { ...n, data: { ...n.data, ...patch } } : n,
        );
        set(withFlowDeferred({ ...flow, nodes }));
      },

      markSaved: (lockVersion) => set({ dirty: false, lockVersion }),
      recompute: () => {
        const flow = get().flow;
        if (flow) set({ problems: validateFlow(flow) });
      },

      setRunning: (running) => set({ running }),
      setRunTrace: (runTrace) => set({ runTrace }),
      setNodeStatus: (id, status) =>
        set((s) => ({ nodeStatus: { ...s.nodeStatus, [id]: status } })),
      clearRunStatus: () => set({ nodeStatus: {}, runTrace: null }),

      undo: () => {
        useEditorStore.temporal.getState().undo();
        get().recompute();
      },
      redo: () => {
        useEditorStore.temporal.getState().redo();
        get().recompute();
      },
    }),
    {
      limit: 50,
      // Only the flow graph participates in undo/redo (not selection or run state).
      partialize: (state) => ({ flow: state.flow }),
      equality: (a, b) => a.flow === b.flow,
    },
  ),
);
