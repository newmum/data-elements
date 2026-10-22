import type { Pipeline } from '../api/pipelines';

export interface TaskChainItem {
  taskId: string;
  taskName: string;
  sourceTableId: string;
  targetTableId?: string | null;
  sourceDbId: string;
  targetDbId?: string | null;
  sourceDbName: string;
  targetDbName?: string | null;
  sourceTableName: string;
  targetTableName?: string | null;
  pipeline: Pipeline;
  nativeUrl?: string | null;
  processGroupId?: string | null;
  unavailableReason?: string | null;
}

export interface TaskChain {
  standalone?: boolean;
  readOnly?: boolean;
  relationshipUnavailable?: boolean;
  warnings?: string[];
  rootSourceTableId: string;
  selectedPipelineId: string;
  selectedTaskId?: string;
  items: TaskChainItem[];
  links: Array<{ fromTaskId: string; toTaskId: string; tableId: string }>;
  nativeCanvas: { mode: 'COMMON_PARENT' | 'SELECT_TASK' | 'NONE'; url?: string; reason?: string };
}
