import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { request, ApiClientError } from './client';
import type { FlowDetail, FlowSummary, FlowVersion, DebugResult, PublishResult } from './types';
import type { Flow } from '@/domain/flow-dsl';
import type { CompileResult } from '@/compiler';
import { compile } from '@/compiler';

export const flowKeys = {
  all: ['flows'] as const,
  detail: (id: string) => ['flow', id] as const,
  versions: (id: string) => ['flow', id, 'versions'] as const,
};

export function useFlows() {
  return useQuery({ queryKey: flowKeys.all, queryFn: () => request<FlowSummary[]>('GET', '/flows') });
}

export function useFlow(id: string | undefined) {
  return useQuery({
    queryKey: flowKeys.detail(id ?? ''),
    queryFn: () => request<FlowDetail>('GET', `/flows/${id}`),
    enabled: !!id,
    retry: (attempt, error) => attempt < 1 && (!(error instanceof ApiClientError) || error.code === 0 || error.code === 408 || error.code === 429 || error.code >= 500),
  });
}

export function useCreateFlow() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => request<FlowDetail>('POST', '/flows', { name }),
    onSuccess: () => qc.invalidateQueries({ queryKey: flowKeys.all }),
  });
}

export function useSaveFlow() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; flow: Flow; lockVersion: number }) =>
      request<{ lockVersion: number }>('PUT', `/flows/${vars.id}`, {
        flow: vars.flow,
        lockVersion: vars.lockVersion,
      }),
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: flowKeys.all });
      qc.invalidateQueries({ queryKey: flowKeys.detail(vars.id) });
    },
  });
}

export function useDeleteFlow() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => request<{ ok: boolean }>('DELETE', `/flows/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: flowKeys.all }),
  });
}

export function useCopyFlow() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => request<FlowDetail>('POST', `/flows/${id}/copy`),
    onSuccess: () => qc.invalidateQueries({ queryKey: flowKeys.all }),
  });
}

export function useCompile() {
  return useMutation({
    mutationFn: async (flow: Flow) => {
      const result = compile(flow);
      if (!result.ok) return result;
      return request<CompileResult>('POST', `/flows/${flow.flowId}/compile`, {
        flow,
        script: result.script,
      });
    },
  });
}

export function useDebug() {
  return useMutation({
    mutationFn: ({ flow, input }: { flow: Flow; input: Record<string, unknown> }) => {
      const result = compile(flow);
      if (!result.ok) {
        throw new Error(result.problems.map((item) => item.message).join('\n') || '流程编译失败');
      }
      return request<DebugResult>('POST', `/flows/${flow.flowId}/debug`, {
        flow,
        input,
      });
    },
  });
}

export function usePublish() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; lockVersion: number; remark: string; targetNodeId?: string }) => {
      return request<PublishResult>('POST', `/flows/${vars.id}/publish`, {
        remark: vars.remark,
        targetNodeId: vars.targetNodeId,
        lockVersion: vars.lockVersion,
      });
    },
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: flowKeys.all });
      qc.invalidateQueries({ queryKey: flowKeys.versions(vars.id) });
      qc.invalidateQueries({ queryKey: flowKeys.detail(vars.id) });
    },
  });
}

export function useRegisteredServices(enabled: boolean, keyword: string) {
  return useQuery({
    queryKey: ['registered-services', keyword],
    enabled,
    queryFn: async () => {
      const data = await request<{ list: Array<{ id: string; serviceName: string; method: string; uri: string; upstreamAddress?: string }> }>(
        'POST', '/gateway/api/list', { pageNum: 1, pageSize: 30, keyword },
      );
      return data.list.filter((row) => row.upstreamAddress || row.uri);
    },
  });
}

export function useVersions(id: string | undefined) {
  return useQuery({
    queryKey: flowKeys.versions(id ?? ''),
    queryFn: () => request<FlowVersion[]>('GET', `/flows/${id}/versions`),
    enabled: !!id,
  });
}

export function useRollback() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; version: number }) =>
      request<{ lockVersion: number }>('POST', `/flows/${vars.id}/rollback`, {
        version: vars.version,
      }),
    onSuccess: (_d, vars) => qc.invalidateQueries({ queryKey: flowKeys.detail(vars.id) }),
  });
}

export function useOffline() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => request<{ status: string }>('POST', `/flows/${id}/offline`),
    onSuccess: () => qc.invalidateQueries({ queryKey: flowKeys.all }),
  });
}
