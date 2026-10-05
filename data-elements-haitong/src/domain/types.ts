export type Scenario = '接入' | '同步' | '分发' | '跨网' | '多表同步';
export type Row = Record<string, string | number | boolean | null>;
export interface Field { id: string; name: string; label: string; type: 'String' | 'Int64' | 'Decimal' | 'Timestamp'; nullable: boolean; primary?: boolean; }
export interface DataTable { id: string; sourceId: string; name: string; label: string; fields: Field[]; rows: Row[]; revision: number; }
export interface Source { id: string; name: string; engine: string; namespace: string; zone: string; }
export type Transform = 'EXACT' | 'TRIM' | 'UPPER' | 'LOWER';
export interface FieldMapping { sourceField: string; targetField: string; transform: Transform; }
export interface TableMapping { id: string; sourceTableId: string; targetTableId: string; fields: FieldMapping[]; writeMode: 'UPSERT' | 'APPEND'; }
export type NodeKind = 'read' | 'transform' | 'write' | 'send' | 'receive';
export interface FlowNode { id: string; kind: NodeKind; name: string; x: number; y: number; config: Record<string, string>; }
export interface FlowEdge { id: string; source: string; target: string; outlet: 'success' | 'failure'; }
export interface Flow { nodes: FlowNode[]; edges: FlowEdge[]; }
export interface Schedule { kind: 'MANUAL' | 'DAILY'; phaseMinute: number; timezone: 'Asia/Shanghai'; nextAt?: string; }
export interface Task { id: string; code: string; name: string; scenario: Scenario; owner: string; clusterId: string; mappings: TableMapping[]; schedule: Schedule; flow: Flow; revision: number; publishedRevision?: number; published?: TaskDefinition; nifiPipelineId?: string; paused: boolean; archived: boolean; createdAt: string; updatedAt: string; fault: 'NONE' | 'MISSING' | 'CONTENT' | 'TRANSPORT'; }
export type TaskDefinition = Pick<Task, 'id' | 'name' | 'scenario' | 'clusterId' | 'mappings' | 'flow' | 'revision' | 'fault'>;
export interface Cluster { id: string; name: string; zone: string; state: 'HEALTHY' | 'MAINTENANCE'; nodes: Array<{id: string; name: string; cpu: number; memory: number; state: 'ONLINE' | 'OFFLINE'}>; bulletins: string[]; concurrency: number; }
export interface RunItem { id: string; mapping: TableMapping; sourceRows: Row[]; expectedRows: Row[]; targetRows: Row[]; status: 'PENDING' | 'SUCCEEDED' | 'FAILED'; input: number; expected: number; written: number; rejected: number; error?: string; }
export interface Log { at: string; level: 'INFO' | 'WARN' | 'ERROR'; message: string; objectId?: string; }
export interface Run { id: string; taskId: string; task: TaskDefinition; status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'; stage: number; startedAt: string; finishedAt?: string; items: RunItem[]; logs: Log[]; retryOf?: string; nextTick: number; cancelRequested?: boolean; }
export interface Difference { key: string; kind: 'MISSING' | 'EXTRA' | 'CONTENT' | 'DUPLICATE' | 'NULL_KEY'; field?: string; expected?: string; actual?: string; }
export interface Bill { id: string; runId: string; taskId: string; mappingId: string; sourceTableId: string; targetTableId: string; createdAt: string; input: number; expected: number; received: number; status: 'MATCH' | 'DIFFERENT' | 'INCOMPLETE'; differences: Difference[]; writeoff?: { at: string; by: string; reason: string; evidenceId?: string }; evidenceHash: string; }
export interface InventoryStrategy { id: string; name: string; sourceTableId: string; targetTableId: string; fields: FieldMapping[]; keys: string[]; timeField: string; start: string; end: string; frequency: 'ONCE' | 'WEEKLY' | 'MONTHLY'; enabled: boolean; revision: number; mode: 'COUNT' | 'SHA1'; }
export interface InventoryStatement { id: string; strategyId: string; strategy: InventoryStrategy; createdAt: string; status: 'MATCH' | 'DIFFERENT' | 'BLOCKED'; sourceCount: number; targetCount: number; differences: Difference[]; hashAlgorithm: 'SHA-1'; reason?: string; }
export interface BatchJob { id: string; name: string; clusterId: string; createdAt: string; status: 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'CANCELLED'; items: Array<{mapping: TableMapping; phaseMinute: number; status: 'PENDING' | 'SUCCEEDED' | 'FAILED'; taskId?: string; error?: string}>; logs: Log[]; nextTick: number; }
export interface Registration { id: string; flowId: string; taskId: string; at: string; revision: number; }
export interface Workspace { schemaVersion: 2; revision: number; sources: Source[]; tables: DataTable[]; tasks: Task[]; runs: Run[]; bills: Bill[]; clusters: Cluster[]; strategies: InventoryStrategy[]; statements: InventoryStatement[]; batches: BatchJob[]; registrations: Registration[]; logs: Log[]; }
export interface Check { level: 'error' | 'warning'; message: string; path: string; }
export const STAGES = ['排队等待', '读取来源', '字段转换', '传输与写入', '生成对账'];
