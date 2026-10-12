import { create } from 'zustand';
import type { CanvasDsl } from '../types/dsl';
import type { TaskChain } from '../types/taskChain';

interface TaskChainState {
  chain: TaskChain | null;
  drafts: Record<string, CanvasDsl>;
  operationBusy: boolean;
  setOperationBusy: (busy: boolean) => void;
  setChain: (chain: TaskChain) => void;
  rememberDraft: (pipelineId: string, dsl: CanvasDsl) => void;
  clear: () => void;
}

/** Context DSL is kept outside the editable store: no context node can be saved into another job. */
export const useTaskChainStore = create<TaskChainState>((set) => ({
  chain: null,
  drafts: {},
  operationBusy: false,
  setOperationBusy: (operationBusy) => set({ operationBusy }),
  setChain: (chain) => set({ chain, drafts: {} }),
  rememberDraft: (pipelineId, dsl) => set(state => ({ drafts: { ...state.drafts, [pipelineId]: dsl } })),
  clear: () => set({ chain: null, drafts: {}, operationBusy: false }),
}));
