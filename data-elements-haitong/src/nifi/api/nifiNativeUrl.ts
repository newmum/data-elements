/** Open the deployed node's complete native UI; NiFi owns its login and permissions. */
export function resolveNifiNativeUiUrl(target: string): string {
  const address = new URL(target);
  if (!['http:', 'https:'].includes(address.protocol) || address.username || address.password) {
    throw new Error('NiFi 浏览器访问地址须为不含凭据的 HTTP 或 HTTPS 地址');
  }
  return address.href;
}
