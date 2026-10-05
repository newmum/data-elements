import { useQuery } from '@tanstack/react-query';
import { apiClient } from './client';
import { getApiErrorMessage } from './response';
import type { ComponentManifest } from '@/types/manifest';
import { useSessionRevision, useTokenReady } from './bridgeSession';

/**
 * Normalize the manifests response to always return an array.
 * Handles both direct array responses and wrapped responses like { data: [...] }.
 */
function normalizeManifests(payload: unknown): ComponentManifest[] {
  if (Array.isArray(payload)) return payload;
  if (payload && typeof payload === 'object') {
    // Handle common wrapper structures: { data: [...] } or { result: [...] } or { list: [...] }
    const obj = payload as Record<string, unknown>;
    if (obj.success === false || (typeof obj.code === 'number' && obj.code !== 0 && obj.code !== 200)) {
      throw new Error(getApiErrorMessage(obj, '组件清单加载失败'));
    }
    if (Array.isArray(obj.data)) return obj.data as ComponentManifest[];
    if (Array.isArray(obj.result)) return obj.result as ComponentManifest[];
    if (Array.isArray(obj.list)) return obj.list as ComponentManifest[];
  }
  // A gateway HTML fallback or malformed response must not masquerade as an
  // empty palette; EditorPage can then offer retry or limited operation.
  throw new Error('组件清单接口返回格式无效，请检查后端代理与登录状态');
}

export async function fetchComponentManifests(): Promise<ComponentManifest[]> {
  const { data } = await apiClient.get<unknown>('/manifests');
  return normalizeManifests(data);
}

export function useComponentManifests() {
  const tokenReady = useTokenReady();
  const sessionRevision = useSessionRevision();
  return useQuery({
    enabled: tokenReady,
    queryKey: ['manifests', sessionRevision],
    queryFn: fetchComponentManifests,
    staleTime: Infinity,
  });
}
