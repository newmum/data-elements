const credentialKey = /^(?:token|access[_-]?token|refresh[_-]?token|id[_-]?token|authorization|password|secret|credential|ticket|authCode|code)$/i;

function removeCredentials(params: URLSearchParams): URLSearchParams {
  for (const key of [...params.keys()]) if (credentialKey.test(key)) params.delete(key);
  return params;
}

/** Navigation metadata only; never an authority for source or tenant access. */
export function safeReportReturn(candidate: unknown, currentHref: string, portalHref: string): string | undefined {
  if (typeof candidate !== 'string' || !candidate.trim() || candidate.length > 4096) return;
  try {
    const current = new URL(currentHref), portal = new URL(portalHref, current), target = new URL(candidate, current);
    if (!['http:', 'https:'].includes(target.protocol) || target.username || target.password) return;
    const loopback = new Set(['localhost', '127.0.0.1', '[::1]']);
    const localPortal = loopback.has(current.hostname) && loopback.has(portal.hostname) && loopback.has(target.hostname) &&
      target.protocol === portal.protocol && target.port === portal.port && target.pathname === portal.pathname;
    const trustedApp = (target.origin === current.origin && target.pathname === current.pathname) ||
      (target.origin === portal.origin && target.pathname === portal.pathname) || localPortal;
    if (!trustedApp) return;
    const hash = target.hash.slice(1), queryAt = hash.indexOf('?'), route = queryAt < 0 ? hash : hash.slice(0, queryAt);
    if (/^\/(?:report|login|auth)(?:\/|$)/i.test(decodeURIComponent(route))) return;
    target.search = removeCredentials(target.searchParams).toString();
    if (queryAt >= 0) {
      const params = removeCredentials(new URLSearchParams(hash.slice(queryAt + 1)));
      target.hash = route + (params.size ? '?' + params : '');
    }
    return target.href;
  } catch { return; }
}

export function reportReturnTarget(currentHref: string, portalHref: string, state?: unknown, referrer = ''): string {
  const current = new URL(currentHref), hash = current.hash.slice(1), queryAt = hash.indexOf('?');
  const explicit = new URLSearchParams(queryAt < 0 ? '' : hash.slice(queryAt + 1)).get('returnTo');
  const routeState = state && typeof state === 'object' ? (state as { reportReturnTo?: unknown }).reportReturnTo : undefined;
  for (const candidate of [explicit, routeState, referrer]) {
    const target = safeReportReturn(candidate, currentHref, portalHref);
    if (target) return target;
  }
  current.search = ''; current.hash = '/overview';
  return current.href;
}

export function reportSourceLocation(currentHref: string, datasourceId: string, returnTo: string): string {
  const target = new URL(currentHref);
  target.search = '';
  target.hash = '/report?' + new URLSearchParams({ datasourceId, returnTo });
  return target.href;
}
