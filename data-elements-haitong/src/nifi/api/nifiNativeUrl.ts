/**
 * Resolve the native NiFi proxy through the same public gateway that served
 * the canvas. `VITE_APP_API_URL` is a Vite development-proxy target and must
 * never decide a browser navigation in a production bundle.
 */
export function resolveNifiNativeUiUrl(
  target: string,
  apiBaseUrl: string | undefined,
  pageOrigin: string,
): string {
  const nativeTarget = new URL(target, pageOrigin);
  if (nativeTarget.origin !== pageOrigin) {
    throw new Error('NiFi 原生页面地址必须通过当前系统的同源代理访问');
  }

  const apiBasePath = new URL(apiBaseUrl || '/', pageOrigin).pathname.replace(/\/+$/, '');
  // `/prod-api/nifi/api` and `/nifi/api` are the two supported public API
  // shapes. The native proxy is a sibling of `nifi`, not a route in the
  // embedded `/haitong/` SPA.
  const matched = apiBasePath.match(/^(.*)\/nifi\/api$/);
  const gatewayPrefix = matched?.[1] ?? '';
  const proxyPath = `${gatewayPrefix}${nativeTarget.pathname}`.replace(/\/\/{2,}/g, '/');

  return new URL(`${proxyPath}${nativeTarget.search}${nativeTarget.hash}`, pageOrigin).toString();
}
