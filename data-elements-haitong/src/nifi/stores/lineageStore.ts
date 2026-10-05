import { create } from 'zustand';

interface LineageState {
  nodeId: string | null;
  pipelineId: string | null;
  open: (pipelineId: string, nodeId: string) => void;
  close: () => void;
}

export const useLineageStore = create<LineageState>((set) => ({
  nodeId: null,
  pipelineId: null,
  open: (pipelineId, nodeId) => set({ pipelineId, nodeId }),
  close: () => set({ pipelineId: null, nodeId: null }),
}));
