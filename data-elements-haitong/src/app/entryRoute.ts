/**
 * Old NiFi iframe links used both `#/?tid=...` and `?tid=...#/`.
 * HashRouter only exposes the first form through useLocation().search, so
 * read the document search separately when choosing the consolidated canvas.
 */
const legacyCanvasKeys = [
  'communication', 'tid', 'accessTaskId', 'jobId', 'pipelineId',
  'orgId', 'orgPath', 'isAdmin', 'task',
] as const;

const canvasSelectors = new Set(['communication', 'tid', 'accessTaskId', 'jobId', 'pipelineId', 'task']);

/** Move old document-query selections into the hash route once at startup.
 * Leaving `?jobId=...` outside the hash would reopen that old job when a user
 * later navigates to a fresh canvas within the same single-page application.
 */
export function canonicalizeLegacyCanvasUrl(href: string): string | null {
  const url = new URL(href);
  // A production gateway may expose /haitong/canvas as a direct entry. Keep
  // the application's existing HashRouter and turn that entry into its canvas
  // route before React mounts, including links with no selection parameters.
  const canvasEntry = /(?:^|\/)haitong\/canvas\/?$/.test(url.pathname) || /^\/canvas\/?$/.test(url.pathname);
  if (!canvasEntry && !legacyCanvasKeys.some(key => url.searchParams.has(key))) return null;
  if (canvasEntry) url.pathname = url.pathname.replace(/canvas\/?$/, '');

  const hash = url.hash.slice(1);
  const separator = hash.indexOf('?');
  const requestedRoute = (separator >= 0 ? hash.slice(0, separator) : hash) || '/';
  const route = canvasEntry && requestedRoute === '/' ? '/development/canvas' : requestedRoute;
  const hashParams = new URLSearchParams(separator >= 0 ? hash.slice(separator + 1) : '');
  const canMoveToHash = route === '/' || route === '/development/canvas';

  for (const key of legacyCanvasKeys) {
    const value = url.searchParams.get(key);
    if (value === null) continue;
    if (canMoveToHash && !hashParams.has(key)) hashParams.set(key, value);
    url.searchParams.delete(key);
  }
  if (canMoveToHash) url.hash = `${route}${hashParams.size ? `?${hashParams.toString()}` : ''}`;
  return url.href;
}

export function entryRouteDestination(hashSearch: string, documentSearch: string): string {
  const fromHash = new URLSearchParams(hashSearch);
  const fromDocument = new URLSearchParams(documentSearch);
  const selected = new URLSearchParams();
  for (const key of legacyCanvasKeys) {
    // An explicit hash route is the closest equivalent to an in-app link.
    const value = fromHash.get(key) ?? fromDocument.get(key);
    if (value !== null) selected.set(key, value);
  }
  const isCanvas = [...canvasSelectors].some(key => selected.has(key));
  return isCanvas ? `/development/canvas?${selected.toString()}` : '/overview';
}
