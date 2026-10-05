import type { Entity } from '../../features/er/types/domain';
import type { Row } from '../../services/api';
import type { ResourceState } from './types';
import { organizationOptions, type SourceOption } from '../../services/datasourceRegistrationApi';
import { resourceGet, resourcePost, resourceObject, resourceList, ResourceValidationError, type ResourceRequestOptions } from '../services/transport';

export interface ResourceCapabilities { write: boolean; review: boolean; manage: boolean; execute: boolean; scheduledDelivery?: boolean; manualDelivery?: boolean; crossNetworkSync?: boolean; autoApprove?: boolean; }
export interface ResourceData {
  state: ResourceState; sources: Row[]; entities: Entity[]; standards: Row[]; mappings: Row[];
  manifest: Record<string, string>; members: Row[]; issues: Row[]; results: Row[];
  capabilities: ResourceCapabilities;
  organizations: SourceOption[];
  entityPagination?: { total: number; pageNo: number; pageSize: number };
}
export interface ResourceCommandOptions extends ResourceRequestOptions { body?: unknown; version?: number; method?: string; }
const stateCollections = ['layers', 'databases', 'bindings', 'models', 'materializations', 'resources', 'catalogs', 'publications', 'subscriptions', 'deliveries', 'classifications', 'itemSets', 'logs', 'ignoredMatches', 'technicalRevisions'] as const;
const commands: Record<string, readonly string[]> = {
  layers: ['', 'move'], databases: ['', 'archive'], bindings: ['', 'check'],
  models: ['', 'validate', 'freeze', 'copy', 'reference', 'reverse', 'archive', 'items', 'bind'],
  itemsets: ['submit'], materializations: ['', 'precheck', 'execute', 'retry', 'cancel', 'load'],
  resources: ['', 'archive'], classifications: [''],
  catalogs: ['', 'check', 'submit', 'withdraw', 'review', 'new-version'],
  policy: [''],
};

/** Failed backend reads must never become an empty or demonstration workspace. */
export function normalizeResourceData(value: unknown): ResourceData {
  const data = resourceObject(value, '资源中心');
  const state = resourceObject(data.state, '资源状态');
  if (state.schemaVersion !== 1) throw new Error('资源中心数据版本不受支持，请更新前端或联系维护人员');
  for (const key of stateCollections) if (!Array.isArray(state[key])) throw new Error(`资源状态缺少 ${key} 集合`);
  for (const key of ['sources', 'entities', 'standards', 'mappings', 'members', 'issues', 'results']) resourceList(data[key], key);
  resourceObject(data.manifest, '元数据版本');
  const capabilities = resourceObject(data.capabilities, '资源操作权限');
  for (const key of ['write', 'review', 'manage', 'execute']) if (typeof capabilities[key] !== 'boolean') throw new Error(`资源操作权限缺少 ${key}`);
  for (const entity of data.entities as Entity[]) if (!entity.id || !entity.sourceId || !Array.isArray(entity.fields)) throw new Error('共享技术元数据缺少实体标识、来源或字段结构');
  return data as ResourceData;
}
export async function readResourceData(options: ResourceRequestOptions = {}): Promise<ResourceData> {
  const [raw, organizations] = await Promise.all([
    resourceGet('/dwm/center/resource-state', {}, { timeoutMs: 60000, ...options }),
    resourcePost('/sym/org/getOrgTree', { parentId: 'ROOT' }, options),
  ]);
  const data = normalizeResourceData(raw);
  const flatten = (items: SourceOption[]): SourceOption[] => items.flatMap(({ children, ...item }) => [item, ...flatten(children || [])]);
  return { ...data, organizations: flatten(organizationOptions(organizations)) };
}
/** Shared Magic orchestrator validates transitions and reuses authoritative platform tables. */
export async function resourceCommand<T = Row>(path: string, options: ResourceCommandOptions = {}): Promise<T> {
  const parts = path.replace(/^\/+|\/+$/g, '').split('/');
  const [kind, id = '', action = ''] = parts;
  if (parts.length > 3 || !commands[kind]?.includes(action)) throw new ResourceValidationError(400, 'UNSUPPORTED_RESOURCE_ACTION', '不支持此资源操作');
  const method = (options.method || 'POST').toUpperCase();
  if (!['GET', 'POST', 'PUT', 'DELETE'].includes(method)) throw new ResourceValidationError(400, 'INVALID_METHOD', '资源请求方法不正确');
  if (['execute', 'retry'].includes(action) && kind === 'materializations') {
    const body = resourceObject(options.body, '物化确认');
    if (body.confirmExecution !== true) throw new ResourceValidationError(422, 'EXECUTION_CONFIRMATION_REQUIRED', '请核对目标数据源与 SQL 后明确确认执行');
  }
  if (action === 'load' && kind === 'materializations') {
    const body = resourceObject(options.body, '融合装载确认');
    if (body.confirmExecution !== true) throw new ResourceValidationError(422, 'EXECUTION_CONFIRMATION_REQUIRED', '请核对目标表与冻结映射后确认装载');
  }
  return resourcePost<T>('/dwm/center/resource-command', {
    kind, id, action, method, body: options.body ?? {}, version: options.version,
  }, { signal: options.signal, timeoutMs: options.timeoutMs ?? (kind === 'materializations' ? action === 'load' ? 660000 : 120000 : 60000) });
}
export function readResource<T = Row>(path: string, options: ResourceRequestOptions = {}): Promise<T> {
  return resourceCommand<T>(path, { ...options, method: 'GET' });
}
