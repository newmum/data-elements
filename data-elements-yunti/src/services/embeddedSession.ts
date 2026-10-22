let embeddedToken: string | null = null;

/** Embedded credentials live only in this iframe instance, not in URLs or persistent storage. */
export function setEmbeddedToken(token: string | null) { embeddedToken = token; }

export function requestToken(): string {
  if (window.parent !== window) return embeddedToken ?? '';
  return localStorage.getItem('token') ?? '';
}

export function resolveParentOrigin(href: string, referrer: string): string | null {
  try {
    const current = new URL(href);
    const candidate = new URL(referrer || current.searchParams.get('parentOrigin') || '');
    if (!['http:', 'https:'].includes(candidate.protocol)) return null;
    if (candidate.origin === current.origin) return candidate.origin;
    const loopback = (host: string) => ['localhost', '127.0.0.1', '[::1]'].includes(host);
    if (loopback(current.hostname) && loopback(candidate.hostname) && current.protocol === candidate.protocol)
      return candidate.origin;
  } catch { /* Missing/invalid parent metadata must not permit a wildcard credential bridge. */ }
  return null;
}
