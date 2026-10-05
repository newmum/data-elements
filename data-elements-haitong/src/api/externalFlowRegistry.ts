import { platformApi } from './platformApi';
import { apiClient } from '../nifi/api/client';
import type { CanvasDsl } from '../nifi/types/dsl';
import type { ComponentManifest } from '../nifi/types/manifest';
import type { Pipeline } from '../nifi/api/pipelines';

export interface ExternalFlowRegistration {
  tid: string;
  externalFlowId: string;
  pipelineId: string;
  pipelineName: string;
  pipelineStatus: string;
  processGroupId?: string | null;
  sourceFormat: 'CANVAS_DSL_V1';
  createdTime: string;
}

export interface ExternalFlowValidation {
  externalFlowId: string;
  exists: boolean;
  pipelineId: string | null;
  sameBinding: boolean;
  pipelineExists: boolean;
  pipelineAssignedElsewhere: boolean;
  sourceFormat: 'CANVAS_DSL_V1';
}

export interface RegistryPipelineOption {
  id: string;
  name: string;
  status: string;
  processGroupId?: string | null;
}

export interface RegistryPipelineOptionsPage {
  pageNum: number;
  pageSize: number;
  total: number;
  list: RegistryPipelineOption[];
}

export const externalFlowRegistry = {
  pipelineOptions: (pageNum = 1, pageSize = 20, keyword = '') =>
    platformApi<RegistryPipelineOptionsPage>('/ods/task/registry/pipeline-options', { pageNum, pageSize, keyword }),
  validate: (externalFlowId: string, pipelineId?: string) =>
    platformApi<ExternalFlowValidation>('/ods/task/registry/validate', { externalFlowId, pipelineId }),
  save: (externalFlowId: string, pipelineId: string) =>
    platformApi<{ tid: string; externalFlowId: string; pipelineId: string; created: boolean }>(
      '/ods/task/registry/save', { externalFlowId, pipelineId, sourceFormat: 'CANVAS_DSL_V1' },
    ),
  page: (pageNum = 1, pageSize = 20) =>
    platformApi<{ pageNum: number; pageSize: number; total: number; list: ExternalFlowRegistration[] }>(
      '/ods/task/registry/page', { pageNum, pageSize },
    ),
  createDraft: async (name: string, description: string, dsl: CanvasDsl) =>
    (await apiClient.post<Pipeline>('/pipelines', { name, description, dsl })).data,
};

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

/** Reject the older local Flow JSON; it has no component manifest or deployable configuration. */
export function parseImportableCanvasDsl(raw: string, manifests: ComponentManifest[]): CanvasDsl {
  let input: unknown;
  try { input = JSON.parse(raw); } catch { throw new Error('JSON 格式无效'); }
  if (!record(input)) throw new Error('请选择 NiFi Canvas DSL v1 JSON');
  const dsl: unknown = record(input.dsl) ? input.dsl : input;
  if (!record(dsl) || dsl.version !== 1 || !Array.isArray(dsl.nodes) || !Array.isArray(dsl.edges)) {
    throw new Error('仅支持 NiFi Canvas DSL v1 的 version、nodes、edges 结构');
  }
  if (dsl.nodes.length < 1 || dsl.nodes.length > 200 || dsl.edges.length > 500) {
    throw new Error('流程应包含 1～200 个组件且连接不超过 500 条');
  }
  const manifestMap = new Map(manifests.map(item => [item.key, item]));
  const nodeIds = new Set<string>();
  for (const node of dsl.nodes) {
    if (!record(node) || typeof node.id !== 'string' || !node.id.trim()
      || typeof node.manifestKey !== 'string' || typeof node.label !== 'string'
      || !Number.isFinite(node.x) || !Number.isFinite(node.y) || !record(node.config)) {
      throw new Error('组件缺少 NiFi 画布所需的 id、manifestKey、label、坐标或配置');
    }
    if (nodeIds.has(node.id)) throw new Error(`组件 ID 重复：${node.id}`);
    nodeIds.add(node.id);
    const manifest = manifestMap.get(node.manifestKey);
    if (!manifest || manifest.category !== node.category) {
      throw new Error(`当前 NiFi 不支持组件 ${node.manifestKey} 或其类型不匹配`);
    }
  }
  const edgeIds = new Set<string>();
  for (const edge of dsl.edges) {
    if (!record(edge) || typeof edge.id !== 'string' || !edge.id.trim()
      || typeof edge.source !== 'string' || typeof edge.target !== 'string'
      || !nodeIds.has(edge.source) || !nodeIds.has(edge.target)
      || (edge.outlet !== undefined && typeof edge.outlet !== 'string')) {
      throw new Error('连接缺少 ID、来源、目标或引用了不存在的组件');
    }
    if (edgeIds.has(edge.id)) throw new Error(`连接 ID 重复：${edge.id}`);
    edgeIds.add(edge.id);
  }
  return dsl as unknown as CanvasDsl;
}
