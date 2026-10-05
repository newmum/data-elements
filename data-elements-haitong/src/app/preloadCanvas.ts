import type { QueryClient } from '@tanstack/react-query';

let pending: Promise<typeof import('../pages/development/CanvasPage')> | null = null;

/** Warm the integrated editor while the user is viewing a task list. */
export function preloadCanvas() {
  if (!pending) {
    pending = import('../pages/development/CanvasPage').catch((error: unknown) => {
      pending = null;
      throw error;
    });
  }
  return pending;
}

/** Reuse the session-scoped manifest query that would otherwise block first paint. */
export function preloadCanvasReadiness(queryClient: QueryClient, sessionRevision: number): void {
  void preloadCanvas().catch(() => {});
  void import('../nifi/api/manifests').then(({ fetchComponentManifests }) =>
    queryClient.prefetchQuery({
      queryKey: ['manifests', sessionRevision],
      queryFn: fetchComponentManifests,
      staleTime: Infinity,
    }),
  ).catch(() => {});
}

export function preloadCanvasWhenIdle(onIdle: () => void = () => { void preloadCanvas().catch(() => {}); }): () => void {
  if (typeof window.requestIdleCallback === 'function') {
    const id = window.requestIdleCallback(onIdle, { timeout: 2500 });
    return () => window.cancelIdleCallback(id);
  }
  const id = setTimeout(onIdle, 1500);
  return () => clearTimeout(id);
}
