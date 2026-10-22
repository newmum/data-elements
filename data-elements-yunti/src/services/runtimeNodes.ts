import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { request } from './client';
import type { RuntimeNode, RuntimeNodeHealth } from './types';

export interface SaveRuntimeNodeInput {
  name: string;
  businessDomain: string;
  serviceType: string;
  baseUrl: string;
  managementUrl?: string;
  deployToken?: string;
  localNode?: boolean;
  defaultNode?: boolean;
  enabled?: boolean;
}

export const runtimeNodeKeys = {
  all: ['runtime-nodes'] as const,
};

export function useRuntimeNodes() {
  return useQuery({
    queryKey: runtimeNodeKeys.all,
    queryFn: () => request<RuntimeNode[]>('GET', '/runtime-nodes'),
  });
}

export function useCreateRuntimeNode() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: SaveRuntimeNodeInput) => request<RuntimeNode>('POST', '/runtime-nodes', { ...input }),
    onSuccess: () => qc.invalidateQueries({ queryKey: runtimeNodeKeys.all }),
  });
}

export function useUpdateRuntimeNode() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; input: SaveRuntimeNodeInput }) =>
      request<RuntimeNode>('PUT', `/runtime-nodes/${vars.id}`, { ...vars.input }),
    onSuccess: () => qc.invalidateQueries({ queryKey: runtimeNodeKeys.all }),
  });
}

export function useDeleteRuntimeNode() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => request<{ ok: boolean }>('DELETE', `/runtime-nodes/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: runtimeNodeKeys.all }),
  });
}

export function useCheckRuntimeNodeHealth() {
  return useMutation({
    mutationFn: (id: string) => request<RuntimeNodeHealth>('GET', `/runtime-nodes/${id}/health`),
  });
}
