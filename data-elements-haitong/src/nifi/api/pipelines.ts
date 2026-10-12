import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient as api, BACKGROUND_REQUEST_OPTIONS } from './client';
import { hiveMetadataConfigError, normalizeProbeResponse, requireListResponse } from './response';
import type { CanvasDsl } from '@/types/dsl';
import { getTid } from './iframeBridge';
import { getBridgeToken, getSessionRevision, useSessionRevision, useTokenReady, waitForToken } from './bridgeSession';
import { isPlatformAuthenticationFailure } from '../../api/platformApi';
import { redirectToPlatformLogin } from '../../services/platformSession';
import { acceptDeploymentResult } from '../utils/deploymentCache';
import type { TaskChain } from '../types/taskChain';
import { useCanvasStore } from '../stores/canvasStore';
import { sameDesignDsl, preservesIncrementalFields } from '../utils/pipelineVersion';
import { incrementalConfigurationError } from '../utils/incrementalFields';

/** One bounded, authorized read contains every related design; no per-task loading loop. */
export async function fetchTaskChain(selection: { accessTaskId?: string; pipelineId?: string }): Promise<TaskChain> {
  const path = selection.accessTaskId ? '/pipelines/task-chain' : `/pipelines/${selection.pipelineId}/chain`;
  return (await api.get<TaskChain>(path, { params: selection.accessTaskId ? { accessTaskId: selection.accessTaskId } : undefined })).data;
}

export async function fetchTaskChainNative(accessTaskId: string): Promise<TaskChain> {
  return (await api.get<TaskChain>('/pipelines/task-chain/native', { params: { accessTaskId } })).data;
}

export type PipelineStatus =
  | 'DRAFT'
  | 'SAVED'
  | 'DEPLOYING'
  | 'RUNNING'
  | 'STOPPING'
  | 'STOPPED'
  | 'DEPLOY_FAILED'
  | 'RUN_ERROR'
  | 'WAITING_AUTHORIZATION';

export type ErrorLevel = 'ERROR' | 'WARNING';
export type ErrorPhase = 'VALIDATION' | 'DEPLOYMENT' | 'RUNTIME';

export interface FlowError {
  id: string;
  level: ErrorLevel;
  phase: ErrorPhase;
  nodeId?: string | null;
  nodeLabel?: string | null;
  fieldKey?: string | null;
  message: string;
  detail?: string | null;
  suggestion?: string | null;
  occurredAt: string; // ISO instant
}

export interface FlowStatusPayload {
  id: string;
  name: string;
  status: PipelineStatus;
  deployed: boolean;
  processGroupId?: string | null;
  lastDeployedHash?: string | null;
  currentHash?: string | null;
  lastDeployedAt?: number | null;
  lastStoppedAt?: number | null;
  errors: FlowError[];
  nifiStatus?: any;
  nifiStatusError?: string;
  nodeMapping?: {
    primaryProcessorIds?: Record<string, string>;
    processorIdToCanvasNode?: Record<string, string>;
    edgeProcessorEndpoints?: Record<string, {
      sourceProcessorId?: string;
      targetProcessorId?: string;
      connectionId?: string;
    }>;
  } | null;
}

export interface PipelineSummary {
  id: string;
  name: string;
  description?: string | null;
  createdAt?: number | null;
  updatedAt?: number | null;
  nodeCount?: number;
  nifiProcessGroupId?: string | null;
}

export interface PipelineRuntimeSummary {
  seq: number;
  id: string;
  taskName: string;
  currentStatus?: PipelineStatus;
  processGroupId?: string | null;
  latestRunTime?: number | null;
  endTime?: number | null;
  executionTimeMs?: number | null;
  scheduleFrequency?: string | null;
  syncMode?: string | null;
  sourceDatabase?: string | null;
  sourceTables?: string | null;
  targetDatabase?: string | null;
  targetTable?: string | null;
  sourceNodeName?: string | null;
  targetNodeName?: string | null;
  activeThreads?: number;
  queuedCount?: number;
  queued?: string | null;
  inputCount?: number;
  outputCount?: number;
  bytesRead?: string | null;
  bytesWritten?: string | null;
  bytesSent?: string | null;
  running?: boolean;
  nifiStatusError?: string | null;
}

export interface Pipeline {
  id: string;
  name: string;
  description?: string | null;
  createdAt?: number | null;
  updatedAt?: number | null;
  dsl: CanvasDsl;
  nifiProcessGroupId?: string | null;
  status?: PipelineStatus;
  lastDeployedHash?: string | null;
  lastDeployedAt?: number | null;
}

export interface DeploymentResult {
  pipeline?: Pipeline;
  processGroupId?: string;
  started?: boolean;
  mode?: string;
}

const KEY = ['pipelines'] as const;

export function usePipelinePage(page:number,size:number,sort:{sortField?:string;sortDir?:string}={}) {
  const tokenReady=useTokenReady(),sessionRevision=useSessionRevision();
  return useQuery({enabled:tokenReady,queryKey:[...KEY,'page',sessionRevision,page,size,sort],queryFn:async()=>{
    const result=(await api.get<{list:PipelineSummary[];total:number}>('/pipelines/page',{params:{page,size,...sort}})).data;
    if(!Array.isArray(result.list)||!Number.isFinite(result.total))throw new Error('流程分页响应不完整');
    return result;
  }});
}
export function usePipelineList(enabled = true) {
  const tokenReady = useTokenReady();
  const sessionRevision = useSessionRevision();
  return useQuery({
    enabled: tokenReady && enabled,
    queryKey: [...KEY, sessionRevision],
    queryFn: async () => requireListResponse<PipelineSummary>((await api.get<unknown>('/pipelines')).data, '流程列表'),
  });
}

export function usePipelineRuntimeList() {
  const tokenReady = useTokenReady();
  const sessionRevision = useSessionRevision();
  return useQuery({
    enabled: tokenReady,
    queryKey: ['pipeline-runtime-list', sessionRevision],
    queryFn: async () => requireListResponse<PipelineRuntimeSummary>((await api.get<unknown>('/pipelines/runtime-list')).data, '流程运行列表'),
  });
}

export function usePipeline(id: string | null, allowNetwork = true) {
  const tokenReady = useTokenReady();
  const sessionRevision = useSessionRevision();
  return useQuery({
    enabled: tokenReady && !!id && allowNetwork,
    queryKey: ['pipeline', id, sessionRevision],
    queryFn: async ({ signal }) => {
      // Task-chain reads seed this exact query with the authorized saved design.
      // Do not revisit the legacy detail route when selecting or focusing a related task.
      if (!allowNetwork) throw new Error('关联任务设计由只读链路快照提供，请重新打开画布刷新');
      return (await api.get<Pipeline>(`/pipelines/${id}`, { signal })).data;
    },
    staleTime: allowNetwork ? 5_000 : Infinity,
    refetchOnMount: allowNetwork,
    refetchOnWindowFocus: allowNetwork,
    refetchOnReconnect: allowNetwork,
  });
}

export function useSavePipeline() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (p: Partial<Pipeline> & { name: string; dsl: CanvasDsl; copy?: boolean }) => {
      const revision = getSessionRevision();
      const dsl = p.dsl;
      const error = incrementalConfigurationError(dsl);
      if (error) throw new Error(error);
      if (p.id) {
        const stored = qc.getQueryData<Pipeline>(['pipeline', p.id, revision]);
        if (stored && stored.name === p.name && sameDesignDsl(stored.dsl, dsl)) return stored;
        const saved = (await api.post<Pipeline>(`/pipelines/${p.id}`, p, {
          params: stored?.updatedAt ? { expectedUpdatedAt: stored.updatedAt } : undefined,
        })).data;
        if (revision !== getSessionRevision()) throw new Error('登录会话已切换，请重新打开画布');
        if (!preservesIncrementalFields(dsl, saved.dsl)) throw new Error('保存响应改变了主增量字段，画布配置已保留，请核对后端版本后重试');
        return saved;
      }
      const saved = (await api.post<Pipeline>('/pipelines', p, {
        params: p.copy ? undefined : { catalogTid: getTid() || undefined },
      })).data;
      if (revision !== getSessionRevision()) throw new Error('登录会话已切换，请重新打开画布');
      if (!preservesIncrementalFields(dsl, saved.dsl)) throw new Error('保存响应改变了主增量字段，画布配置已保留，请核对后端版本后重试');
      return saved;
    },
    onSuccess: async (data, submitted) => {
      const revision = getSessionRevision();
      await qc.cancelQueries({ queryKey: ['pipeline', data.id, revision] });
      if (revision !== getSessionRevision()) return;
      const canvas = useCanvasStore.getState();
      if (submitted.id && canvas.currentPipelineId === submitted.id) canvas.acceptDeployment(submitted.dsl, data.dsl);
      qc.setQueryData(['pipeline', data.id, revision], data);
      void qc.invalidateQueries({ queryKey: [...KEY, revision] });
    },
  });
}

export function useDeletePipeline() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await api.post(`/pipelines/delete/${id}`);
      return id;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDeployPipeline() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, catalogTid }: { id: string; catalogTid?: string | null }) =>
      (await api.post<DeploymentResult>(`/pipelines/${id}/deploy`, { catalogTid })).data,
    onSuccess: async (data, { id }) => {
      const revision = getSessionRevision();
      await acceptDeploymentResult(qc, data, id, revision, () => revision === getSessionRevision());
    },
    onError: (_, { id }) => {
      qc.invalidateQueries({ queryKey: ['pipeline-errors', id] });
      qc.invalidateQueries({ queryKey: ['pipeline-status', id] });
    },
  });
}

export interface ConnectionTestResult {
  success: boolean;
  url?: string;
  product?: string;
  version?: string;
  elapsedMs?: number;
  error?: string;
}

export async function getNifiUiLink(id: string) {
  return (await api.get<{ url: string; processGroupId: string; reusesBrowserSession: boolean }>(`/pipelines/${id}/ui-link`)).data;
}

export interface ProbedColumn {
  columnName: string;
  columnComment?: string;
  dataType?: string;
  typeName?: string;
  columnSize?: number;
  decimalDigits?: number;
  nullable?: boolean | null;
  primaryKey?: boolean | null;
  ordinalPosition?: number;
  defaultValue?: string | null;
}

export interface ColumnsProbeResult {
  success: boolean;
  table?: string;
  url?: string;
  columns: ProbedColumn[];
  error?: string;
}

export interface MetadataTable {
  tableName: string;
  tableComment?: string;
  schemaName?: string;
  tableType?: string;
  rowCount?: number;
  columnCount?: number;
}

export interface TablesProbeResult {
  success: boolean;
  url?: string;
  tables: MetadataTable[];
  total?: number;
  error?: string;
}

export async function testConnection(
  manifestKey: string,
  config: Record<string, unknown>,
): Promise<ConnectionTestResult> {
  const { data } = await api.post<ConnectionTestResult>('/connection-test', { manifestKey, config }, { timeout: 45000 });
  return normalizeProbeResponse<ConnectionTestResult>(data, '连接测试');
}

export async function probeColumns(
  manifestKey: string,
  config: Record<string, unknown>,
  table: string,
): Promise<ColumnsProbeResult> {
  const configError = hiveMetadataConfigError(manifestKey, config);
  if (configError) return { success: false, error: configError, columns: [] };
  const { data } = await api.post<ColumnsProbeResult>(
    '/columns-probe',
    { manifestKey, config, table },
    { timeout: 45000 },
  );
  return normalizeProbeResponse<ColumnsProbeResult>(data, '字段探查', 'columns');
}

export async function probeTables(
  manifestKey: string,
  config: Record<string, unknown>,
  keyword = '',
  limit = 200,
): Promise<TablesProbeResult> {
  const configError = hiveMetadataConfigError(manifestKey, config);
  if (configError) return { success: false, error: configError, tables: [] };
  const { data } = await api.post<TablesProbeResult>(
    '/tables-probe',
    { manifestKey, config, keyword, limit },
    { timeout: 45000 },
  );
  return normalizeProbeResponse<TablesProbeResult>(data, '表元数据探查', 'tables');
}

export function useStopPipeline() {
  return useMutation({
    mutationFn: async (id: string) => (await api.post(`/pipelines/${id}/stop`)).data,
  });
}

export function fetchProcessorStatusPage(id:string,query:Record<string,unknown>) {return api.get(`/pipelines/${id}/status/page`,{params:query}).then(r=>r.data);}

export function fetchPipelineStatus(id: string) {
  return api.get(`/pipelines/${id}/status`).then((r) => r.data);
}

export function useStartPipeline() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, force = false, confirmCleanup = false }: { id: string; force?: boolean; confirmCleanup?: boolean }) =>
      (await api.post(`/pipelines/${id}/start`, undefined, { params: { force, confirmCleanup } })).data,
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: ['pipeline-status', id] });
      qc.invalidateQueries({ queryKey: KEY });
    },
  });
}

export function useFlowStatus(id: string | null, intervalMs = 3000) {
  const tokenReady = useTokenReady();
  const sessionRevision = useSessionRevision();
  return useQuery<FlowStatusPayload>({
    enabled: tokenReady && !!id,
    queryKey: ['pipeline-status', id, sessionRevision],
    queryFn: async ({ signal }) => (await api.get<FlowStatusPayload>(`/pipelines/${id}/status`, { ...BACKGROUND_REQUEST_OPTIONS, signal })).data,
    refetchInterval: (query) => {
      const data = query.state.data;
      if (!data) return intervalMs;
      return ['RUNNING', 'DEPLOYING', 'STOPPING'].includes(data.status) ? intervalMs : 30000;
    },
  });
}

export function useFlowErrors(id: string | null) {
  const statusQuery = useFlowStatus(id);
  const tokenReady = useTokenReady();
  const sessionRevision = useSessionRevision();
  return useQuery<FlowError[]>({
    enabled: tokenReady && !!id,
    queryKey: ['pipeline-errors', id, sessionRevision],
    queryFn: async ({ signal }) => requireListResponse<FlowError>((await api.get<unknown>(`/pipelines/${id}/errors`, { ...BACKGROUND_REQUEST_OPTIONS, signal })).data, '流程错误列表'),
    refetchInterval: () => {
      const status = statusQuery.data?.status;
      return status === 'RUNNING' ? 3000 : 30000;
    },
  });
}

export function useClearFlowErrors() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await api.post(`/pipelines/delete/${id}/errors`);
    },
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ['pipeline-errors', id] });
      qc.invalidateQueries({ queryKey: ['pipeline-status', id] });
    },
  });
}

export interface LineageResponse {
  anchorEventId?: string;
  nodes: any[];
  links: any[];
  events?: any[];
  message?: string;
}

export async function fetchLineage(id: string, nodeId: string, maxEvents = 10): Promise<LineageResponse> {
  const { data } = await api.post<LineageResponse>(
    `/pipelines/${id}/nodes/${nodeId}/lineage?maxEvents=${maxEvents}`,
  );
  return data;
}

export interface TemplateRequest {
  tid: string;
}

export interface TemplateResponse {
  id?: string;
  name: string;
  dsl: CanvasDsl;
  status: string;
}

export async function fetchPipelineTemplate(tid: string): Promise<TemplateResponse> {
  const { data } = await api.post<TemplateResponse>('/pipelines/template', { tid });
  return data;
}

/**
 * 请求生成单组件模板并获取配置回填数据
 * POST /pipelines/template/node
 *
 * 响应拦截器已自动解包 { code, data } 包装层，data 即为业务对象。
 *
 * @param key - 来源库的 manifestKey，如 "source.mysql"
 * @param tableId - 选中数据表的 id
 * @returns { config, manifestKey } — config 可直接合并到节点配置；manifestKey 可能不同于入参 key（如达梦接口返回 source.dameng）
 */
export async function fetchNodeTemplate(
  key: string,
  tableId: string,
): Promise<{ config: Record<string, unknown>; manifestKey?: string }> {
  const { data } = await api.post<any>(
    '/pipelines/template/node',
    { key, tableId },
  );

  // 接口返回结构: { manifestKey: "source.dameng", config: { host, port, ... } }
  if (data && typeof data === 'object' && !Array.isArray(data)) {
    if ('config' in data && data.config && typeof data.config === 'object') {
      return {
        config: data.config as Record<string, unknown>,
        manifestKey: typeof data.manifestKey === 'string' ? data.manifestKey : undefined,
      };
    }
    // 兼容扁平返回值（无 config 嵌套层）
    return { config: data as Record<string, unknown> };
  }
  return { config: {} };
}

// ---------- 已配置任务回填 ----------

export interface AccessTaskDetail {
  data: {
    [key: string]: unknown;
  };
  msg: string;
  success: boolean;
}

/**
 * 查询已配置的访问任务详情（用于画布回填）
 * POST /ods/task/queryById，入参 { tid }
 */
export async function fetchAccessTaskDetail(tid: string): Promise<AccessTaskDetail> {
  const dataassetsApi = import.meta.env.VITE_APP_DATAASSETS_API || '/dev-api';
  const requestRevision = getSessionRevision();
  const token = await waitForToken();
  if ((requestRevision !== 0 && requestRevision !== getSessionRevision()) || token !== getBridgeToken()) {
    throw new Error('登录会话已切换，请重试');
  }
  const revision = getSessionRevision();
  let data: AccessTaskDetail;
  try {
    const response = await axios.post<AccessTaskDetail>(
      `${dataassetsApi}/ods/task/queryById`,
      { tid },
      {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          token,
        },
      },
    );
    data = response.data;
  } catch (cause) {
    if (axios.isAxiosError(cause) && isPlatformAuthenticationFailure(cause.response?.data, cause.response?.status)) {
      redirectToPlatformLogin();
    }
    throw cause;
  }
  if (revision !== getSessionRevision()) throw new Error('登录会话已切换，旧任务结果已丢弃');
  if (isPlatformAuthenticationFailure(data)) {
    redirectToPlatformLogin();
    throw new Error('数据中台登录已失效，正在前往登录页');
  }
  return data;
}

/**
 * 根据 pipelineId 获取画布 DSL 并回填
 * GET /nifi/api/pipelines/{pipelineId}
 */
export async function fetchPipelineById(pipelineId: string): Promise<Pipeline> {
  const { data } = await api.get<Pipeline>(`/pipelines/${pipelineId}`);
  return data;
}
