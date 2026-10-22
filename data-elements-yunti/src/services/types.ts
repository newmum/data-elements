import type { Flow } from '@/domain/flow-dsl';

/** Standard response envelope (SRS §12). */
export interface Envelope<T> {
  code: number;
  message: string;
  data: T;
}

export type FlowStatus = 'draft' | 'published' | 'offline' | 'deleted';

export interface FlowSummary {
  flowId: string;
  name: string;
  group: string;
  status: FlowStatus;
  version: number;
  updatedAt: string;
  calls: number;
}

export interface FlowDetail {
  flow: Flow;
  status: FlowStatus;
  /** Optimistic-lock counter; bumped on every save. */
  lockVersion: number;
  updatedAt: string;
}

export interface FlowVersion {
  version: number;
  dsl: Flow;
  script: string;
  meta: Record<string, unknown>;
  publishTime: string;
  remark: string;
  status: 'published' | 'offline';
}

export interface TraceEntry {
  nodeId: string;
  nodeType: string;
  name: string;
  status: 'success' | 'fail' | 'skipped';
  ms: number;
  input: Record<string, unknown>;
  output: Record<string, unknown>;
  log: string[];
}

export interface DebugResult {
  success: boolean;
  totalMs: number;
  trace: TraceEntry[];
  response: unknown;
  error: string | null;
}

export interface PublishResult {
  version: number;
  url: string;
  status: FlowStatus;
  runtimeNodeId: string;
  runtimeNodeName: string;
}

export interface RuntimeNode {
  id: string;
  name: string;
  businessDomain: string;
  serviceType: string;
  baseUrl: string;
  managementUrl: string | null;
  deployTokenSet: boolean;
  localNode: boolean;
  defaultNode: boolean;
  enabled: boolean;
  createdAt: string;
}

export interface RuntimeNodeHealth {
  nodeId: string;
  ok: boolean;
  statusCode: number;
  latencyMs: number;
  message: string;
}

export interface MetricPoint {
  ts: string;
  qps: number;
  p95: number;
  success: number;
  error: number;
}

export interface MonitorOverview {
  serviceCount: number;
  todayCalls: number;
  activeDataSources: number;
  errorTop: { name: string; errors: number }[];
  series: MetricPoint[];
}

export interface AuditLog {
  id: number;
  action: string;
  target: string;
  detail: string;
  createdAt: string;
}

export interface CallLog {
  id: number;
  flowId: string | null;
  serviceName: string | null;
  method: string;
  path: string;
  status: number;
  success: boolean;
  durationMs: number;
  errorMessage: string | null;
  createdAt: string;
}

export interface AlertRule {
  id: string;
  name: string;
  metric: string;
  operator: string;
  thresholdValue: number;
  durationMinutes: number;
  notifyTarget: string;
  enabled: boolean;
  createdAt: string;
}

export interface AlertEvent {
  id: number;
  ruleId: string;
  ruleName: string;
  metric: string;
  actualValue: number;
  thresholdValue: number;
  message: string;
  notifyTarget: string | null;
  status: string;
  triggeredAt: string;
}
