import type { FlowStatusPayload } from '../api/pipelines';

type CanvasRuntimeStatus = Pick<FlowStatusPayload, 'deployed' | 'runtimeFresh' | 'runtimeState' | 'status'>;

export function canvasLifecycle(status?: CanvasRuntimeStatus) {
  // INVALID means no processor is scheduled. RUN_ERROR means a group is still running with invalid components.
  const stoppedWithInvalidProcessors = status?.deployed === true && status.runtimeFresh === true && status.runtimeState === 'INVALID';
  return { status: stoppedWithInvalidProcessors ? 'STOPPED' as const : status?.status, stoppedWithInvalidProcessors };
}

export function runtimeReflectsStopReceipt(
  status: Pick<FlowStatusPayload, 'status' | 'runtimeFresh' | 'runtimeObservedAt'> | undefined,
  stoppedAt: number,
) {
  if (status?.runtimeObservedAt == null) return status?.status === 'STOPPED';
  return status.runtimeFresh === true && status.runtimeObservedAt >= stoppedAt;
}
