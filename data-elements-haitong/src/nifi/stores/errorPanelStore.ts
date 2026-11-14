import { create } from 'zustand';
import type { FlowError, LiveFlowBulletin } from '../api/pipelines';
import { bulletinKey, type DismissedFlowErrors } from '../runtime/dismissedFlowErrors';
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
  dismissed: Record<string, DismissedFlowErrors>;
  dismiss: (pipelineId: string, history: readonly FlowError[], bulletins: readonly LiveFlowBulletin[]) => void;
}

export const useErrorPanelStore = create<ErrorPanelState>((set) => ({
  visible: false,
  open: () => set({ visible: true }),
  close: () => set({ visible: false }),
  toggle: () => set((s) => ({ visible: !s.visible })),
  failures: [],
  failureSequence: 0,
  dismissed: {},
  dismiss: (pipelineId, history, bulletins) => set(s => ({ dismissed: { ...s.dismissed, [pipelineId]: {
    historyIds: new Set([...(s.dismissed[pipelineId]?.historyIds ?? []), ...history.map(row => row.id)]),
    bulletinKeys: new Set([...(s.dismissed[pipelineId]?.bulletinKeys ?? []), ...bulletins.map(bulletinKey)]),
  } } })),
  record: (pipelineId, action, error, reveal = true) => set(s => ({
    visible: s.visible || reveal, failureSequence: s.failureSequence + 1,
    failures: [{ pipelineId, action, error }, ...s.failures.filter(row => row.pipelineId !== pipelineId || row.action !== action)].slice(0, 100),
  })),
  clearFailures: (pipelineId, action) => set(s => ({
    failures: s.failures.filter(row => row.pipelineId !== pipelineId || (action && row.action !== action)),
  })),
}));

// A reused iframe must not display diagnostics from another account/tenant session.
onNifiSessionChange(() => useErrorPanelStore.setState({ visible: false, failures: [], failureSequence: 0, dismissed: {} }));
