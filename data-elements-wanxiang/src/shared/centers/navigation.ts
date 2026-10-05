export type Center = 'panshi' | 'wanxiang' | 'haoyue';
const home = (center: Center) => center === 'panshi' ? '/resource/overview' : center === 'haoyue' ? '/assets/overview' : '/governance/overview';
const allowed = (center: Center, path: string) => center === 'panshi' ? /^\/resource(?:\/|$)/.test(path) : center === 'haoyue' ? /^\/assets(?:\/|$)/.test(path) : /^\/governance(?:\/|$)/.test(path);
export function centerPath(center: Center, path?: string) {
  const desired = path ?? sessionStorage.getItem(`center:last:${center}`) ?? home(center);
  return allowed(center, desired) ? desired : home(center);
}
export function centerUrl(center: Center, path?: string) {
  const url = new URL(location.href);
  url.hash = centerPath(center, path);
  return url.href;
}
export function rememberCenter(center: Center) {
  const path = location.hash.slice(1);
  if (allowed(center, path) && !path.includes('/designer') && !path.includes('/metadata/er/')) sessionStorage.setItem(`center:last:${center}`, path);
}
/** One router, one authenticated session; never transport tokens in URLs. */
export function goCenter(center: Center, path?: string) {
  window.dispatchEvent(new CustomEvent('wanxiang:center-navigate', { detail: centerPath(center, path) }));
}
