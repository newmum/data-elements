import type { Diagram, DiagramNode, Evidence, MetadataSnapshot, Relationship } from '../types/domain';
export type ThemeMode = 'light' | 'dark';
export type FieldMode = 'key' | 'all' | 'summary';
export type RelationFilter = 'catalog' | 'logical' | 'suggested';
export type ViewModel = Omit<Diagram, 'nodes' | 'hiddenRelationshipIds' | 'fieldMode' | 'relationFilter'> & {
  snapshotManifest?: Record<string, string>;
  nodes: DiagramNode[];
  modelInfo?: { code: string; domain: string; layer: string; status: string; originalId?: string };
  showGrid?: boolean;
  snapToGrid?: boolean;
  showEdgeLabels?: boolean;
  hiddenRelationshipIds: string[];
  librarySourceIds?: string[];
  fieldMode: FieldMode;
  relationFilter: Array<'observed' | 'confirmed' | 'suggested'>;
};
export interface AuditEvent {
  id: string;
  at: string;
  action: string;
  detail: string;
  relationshipId?: string;
}
export interface WorkspaceData {
  schemaVersion: '1.0';
  kind: 'wanxiang-workspace';
  snapshot: MetadataSnapshot;
  relationships: Relationship[];
  evidence: Evidence[];
  diagrams: ViewModel[];
  activeDiagramId: string;
  audit: AuditEvent[];
  updatedAt: string;
  integration?: { version: number; modelIds: string[]; mergedAt: string };
}
export interface JobProgress {
  id: string;
  status: 'running' | 'completed' | 'cancelled' | 'failed';
  stage: number;
  title: string;
  processed: number;
  total: number;
  newCount: number;
  skippedCount: number;
  error?: string;
}
export type SidePanel =
  | { type: 'entity'; id: string }
  | { type: 'relationship'; id: string }
  | { type: 'discovery' }
  | { type: 'relations' }
  | { type: 'activity' }
  | null;
export type ConnectionDraft = {
  sourceEntityId?: string;
  targetEntityId?: string;
  sourceFieldId?: string;
  targetFieldId?: string;
  editingId?: string;
};
export const NODE_WIDTH = 284;
export const NODE_HEADER = 72;
export const FIELD_HEIGHT = 32;
export const NODE_FOOTER = 32;
export const MAX_NODE_FIELDS = 12;
export const CANVAS_PAGE_SIZE = 96;
export function uid(_prefix = 'id'): string { return crypto.randomUUID(); }
export const clone = <T>(value: T): T => structuredClone(value);
export function errorText(e: unknown): string { return e instanceof Error ? e.message : String(e); }
export function displayDate(value?: string | null): string {
  if (!value) return '—';
  const time = new Date(value);
  return Number.isFinite(time.getTime()) ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(time) : '—';
}

/** User-requested replacement must not trigger initial provisioning again after refresh. */
export function prepareWorkspaceReplacement(next:WorkspaceData, previous:WorkspaceData|null):WorkspaceData {
  const result=clone(next);
  if(!result.integration&&previous?.integration)result.integration={...clone(previous.integration),modelIds:[],mergedAt:new Date().toISOString()};
  return result;
}
