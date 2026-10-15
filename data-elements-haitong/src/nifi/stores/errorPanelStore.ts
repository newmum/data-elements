import { create } from 'zustand';
import type { FlowError } from '../api/pipelines';
import type { FlowAction } from '../utils/flowActionFailure';
import { onNifiSessionChange } from '../api/bridgeSession';

interface ActionFailure { pipelineId: string | null; action: FlowAction; error: FlowError }

interface ErrorPanelState {
  visible: boolean;
  open: () => void;
  close: () => void;
  toggle: () => void;
  failures: ActionFailure[];
  failureSequence: number;
  record: (pipelineId: string | null, action: FlowAction, error: FlowError, reveal?: boolean) => void;
  clearFailures: (pipelineId: string | null, action?: FlowAction) => void;
}

export const useErrorPanelStore = create<ErrorPanelState>((set) => ({
  visible: false,
  open: () => set({ visible: true }),
  close: () => set({ visible: false }),
  toggle: () => set((s) => ({ visible: !s.visible })),
  failures: [],
  failureSequence: 0,
  record: (pipelineId, action, error, reveal = true) => set(s => ({
    visible: s.visible || reveal, failureSequence: s.failureSequence + 1,
    failures: [{ pipelineId, action, error }, ...s.failures.filter(row => row.pipelineId !== pipelineId || row.action !== action)].slice(0, 100),
  })),
  clearFailures: (pipelineId, action) => set(s => ({
    failures: s.failures.filter(row => row.pipelineId !== pipelineId || (action && row.action !== action)),
  })),
}));

// A reused iframe must not display diagnostics from another account/tenant session.
onNifiSessionChange(() => useErrorPanelStore.setState({ visible: false, failures: [], failureSequence: 0 }));
