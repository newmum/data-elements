const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1']);

export function resolveReturnTarget(search: string, currentUrl: string): string | null {
  const returnTo = new URLSearchParams(search).get('returnTo');
  if (!returnTo) return null;

  try {
    const current = new URL(currentUrl);
    const target = new URL(returnTo, current.origin);
    const sameOrigin = target.origin === current.origin;
    const localSibling =
      LOCAL_HOSTS.has(current.hostname) &&
      LOCAL_HOSTS.has(target.hostname) &&
      target.hostname === current.hostname;

    return sameOrigin || localSibling ? target.href : null;
  } catch {
    return null;
  }
}
