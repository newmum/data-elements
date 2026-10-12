import type { CanvasDsl } from '../types/dsl';
import type { TaskChain } from '../types/taskChain';

interface EditableDesign {
  currentPipelineId: string | null;
  toDsl: () => CanvasDsl;
  loadDsl: (dsl: CanvasDsl) => void;
  setCurrentPipeline: (id: string | null, name: string | null) => void;
  setCanvasMode: (mode: 'EDIT') => void;
  setSyncCompletion: (value: null) => void;
}
interface ChainContext {
  chain: TaskChain | null;
  drafts: Record<string, CanvasDsl>;
  operationBusy: boolean;
  rememberDraft: (pipelineId: string, dsl: CanvasDsl) => void;
}

/** Explicit user selection is limited to the already authorized graph and never writes remotely. */
export function selectTaskDesign(pipelineId: string, canvas: EditableDesign, context: ChainContext, mutationPending = false): boolean {
  if (context.operationBusy || mutationPending || canvas.currentPipelineId === pipelineId) return false;
  const selected = context.chain?.items.find(item => item.pipeline.id === pipelineId);
  if (!selected) return false;
  if (canvas.currentPipelineId) context.rememberDraft(canvas.currentPipelineId, canvas.toDsl());
  canvas.loadDsl(context.drafts[pipelineId] ?? selected.pipeline.dsl);
  canvas.setCurrentPipeline(pipelineId, selected.pipeline.name);
  canvas.setCanvasMode('EDIT');
  canvas.setSyncCompletion(null);
  return true;
}

/** A displayed node selects only its own authorized task and original node ID. */
export function selectTaskNode(node: { pipelineId: string; originalNodeId: string } | undefined,
  canvas: EditableDesign & { selectNode: (id: string) => void }, context: ChainContext, mutationPending = false): boolean {
  if (!node) return false;
  const isCurrent = node.pipelineId === (canvas.currentPipelineId ?? '');
  const design = isCurrent ? canvas.toDsl()
    : context.drafts[node.pipelineId] ?? context.chain?.items.find(item => item.pipeline.id === node.pipelineId)?.pipeline.dsl;
  if (!design?.nodes.some(item => item.id === node.originalNodeId)) return false;
  if (!isCurrent && !selectTaskDesign(node.pipelineId, canvas, context, mutationPending)) return false;
  canvas.selectNode(node.originalNodeId);
  return true;
}
