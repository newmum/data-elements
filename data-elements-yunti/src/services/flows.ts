import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { request } from './client';
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
    mutationFn: (flow: Flow) => {
      const result = compile(flow);
      if (!result.ok) {
        throw new Error(result.problems.map((item) => item.message).join('\n') || '流程编译失败');
      }
      return request<DebugResult>('POST', `/flows/${flow.flowId}/debug`, {
        flow,
        script: result.script,
      });
    },
  });
}

export function usePublish() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { id: string; remark: string; targetNodeId?: string }) => {
      const detail = await request<FlowDetail>('GET', `/flows/${vars.id}`);
      const result = compile(detail.flow);
      if (!result.ok) {
        throw new Error(result.problems.map((item) => item.message).join('\n') || '流程编译失败');
      }
      return request<PublishResult>('POST', `/flows/${vars.id}/publish`, {
        remark: vars.remark,
        targetNodeId: vars.targetNodeId,
        script: result.script,
      });
    },
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: flowKeys.all });
      qc.invalidateQueries({ queryKey: flowKeys.versions(vars.id) });
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
