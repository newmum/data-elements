import { platformApi } from './platformApi';

export type IntegrationNetwork = {
  value: string;
  label: string;
  icon?: string;
  tagType?: string;
};

export type IntegrationNode = {
  tid: string;
  nodeName: string;
  nodeCode: string;
  networkCode: string;
  networkName?: string;
  baseUrl: string;
  rootProcessGroupId?: string;
  authUsername?: string;
  insecureTls?: boolean;
  remark?: string;
  enabled: boolean;
  isDefault: boolean;
  updatedTime?: string;
};

export type NodeDiagnostics = {
  success: boolean;
  message?: string;
  heapUtilization?: string;
  usedHeapBytes?: number | null;
  maxHeapBytes?: number | null;
  processorLoadAverage?: number | null;
  availableProcessors?: number | null;
  totalThreads?: number | null;
  statsLastRefreshed?: string;
};

type NodePayload = Record<string, unknown>;

const text = (value: unknown) => value == null ? '' : String(value).trim();
const enabled = (value: unknown) => !(value === false || ['0', 'false', 'disabled', 'stop', '停用'].includes(text(value).toLowerCase()));

const normalizeNode = (row: NodePayload, networks: IntegrationNetwork[]): IntegrationNode => {
  const networkCode = text(row.networkCode ?? row.network_code ?? row.networkType ?? row.network_type);
  return {
    tid: text(row.tid ?? row.nodeId ?? row.id ?? row.value),
    nodeName: text(row.nodeName ?? row.node_name ?? row.name ?? row.label),
    nodeCode: text(row.nodeCode ?? row.node_code ?? row.code),
    networkCode,
    networkName: text(row.networkName ?? row.network_name) || networks.find(item => item.value === networkCode)?.label,
    baseUrl: text(row.baseUrl ?? row.base_url),
    rootProcessGroupId: text(row.rootProcessGroupId ?? row.root_process_group_id),
    authUsername: text(row.authUsername ?? row.auth_username),
    insecureTls: enabled(row.insecureTls ?? row.insecure_tls),
    remark: text(row.remark),
    enabled: enabled(row.enabled ?? row.isEnabled ?? row.status),
    isDefault: Number(row.isDefault ?? row.is_default ?? 0) === 1 || row.isDefault === true,
    updatedTime: text(row.updatedTime ?? row.updated_time),
  };
};

export type NodeDraft = {
  tid?: string;
  nodeName: string;
  nodeCode: string;
  networkCode: string;
  baseUrl: string;
  rootProcessGroupId?: string;
  authUsername?: string;
  authPassword?: string;
  insecureTls?: boolean;
  remark?: string;
  enabled: boolean;
  isDefault: boolean;
};

export const integrationNodesApi = {
  async page(): Promise<IntegrationNode[]> {
    const value = await platformApi<NodePayload>('/ods/nifi-node/page', {});
    const rows = Array.isArray(value?.list) ? value.list : [];
    return rows.map(item => normalizeNode(item as NodePayload, [])).filter(item => item.tid && item.nodeName);
  },
  async accessOptions(): Promise<{ networks: IntegrationNetwork[]; nodes: IntegrationNode[] }> {
    const value = await platformApi<NodePayload>('/ods/nifi-node/access-options', {});
    const payload = value && typeof value === 'object' ? value : {};
    const rawNetworks = Array.isArray(payload.networks) ? payload.networks : Array.isArray(payload.list) ? [] : [];
    const networks = rawNetworks.map((item) => ({
      value: text((item as NodePayload).value ?? (item as NodePayload).dictCode ?? (item as NodePayload).code),
      label: text((item as NodePayload).label ?? (item as NodePayload).dictName ?? (item as NodePayload).name),
      icon: text((item as NodePayload).icon),
      tagType: text((item as NodePayload).tagType ?? (item as NodePayload).tag_type),
    })).filter(item => item.value && item.label);
    const rawNodes = Array.isArray(payload.list) ? payload.list : Array.isArray(payload.records) ? payload.records : [];
    return { networks, nodes: rawNodes.map(item => normalizeNode(item as NodePayload, networks)).filter(item => item.tid && item.nodeName) };
  },
  save: (draft: NodeDraft) => platformApi<IntegrationNode>('/ods/nifi-node/saveOrUpdate', {
    tid: draft.tid || '', nodeName: draft.nodeName, nodeCode: draft.nodeCode, networkCode: draft.networkCode,
    baseUrl: draft.baseUrl.replace(/\/+$/, ''), rootProcessGroupId: draft.rootProcessGroupId || '',
    authUsername: draft.authUsername || '', authPassword: draft.authPassword || '', insecureTls: draft.insecureTls === false ? 0 : 1,
    remark: draft.remark || '', enabled: draft.enabled ? 1 : 0, isDefault: draft.isDefault ? 1 : 0,
  }),
  remove: (tid: string) => platformApi<boolean>('/ods/nifi-node/delete', { tid }),
  health: (node: Pick<IntegrationNode, 'tid' | 'baseUrl'>) => platformApi<{ success: boolean; latencyMs?: number; message?: string }>('/sym/node-config/service/health', { nodeId: node.tid, nodeType: 'sync', baseUrl: node.baseUrl }),
  diagnostics: (nodeId: string) => platformApi<NodeDiagnostics>('/sym/node-config/service/diagnostics', { nodeId }),
};
