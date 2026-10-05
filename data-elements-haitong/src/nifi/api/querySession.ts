import type { QueryClient, Query } from '@tanstack/react-query';
import { onNifiSessionChange } from './bridgeSession';
import { useCanvasStore } from '@/stores/canvasStore';

// The NiFi canvas and live platform pages both use the same tenant session.
// Keep unrelated local workbench queries, but never retain another tenant's data.
const NIFI_QUERY_ROOTS = new Set([
  'manifests',
  'pipelines',
  'pipeline-runtime-list',
  'pipeline',
  'pipeline-status',
  'pipeline-errors',
  'access-task-detail',
]);

const isSessionQuery = (query: Query) =>
  NIFI_QUERY_ROOTS.has(String(query.queryKey[0] ?? '')) || query.queryKey[0] === 'platform';

export function registerNifiQueryClient(queryClient: QueryClient): () => void {
  return onNifiSessionChange(() => {
    // Clear the previously loaded DSL synchronously with the credential change.
    // The route wrapper then remounts and fetches under the new session.
    useCanvasStore.getState().clear();
    void queryClient.cancelQueries({ predicate: isSessionQuery });
    queryClient.removeQueries({ predicate: isSessionQuery });
  });
}
