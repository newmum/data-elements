import { platformApi as request } from '../../api/platformApi';
import { getPlatformSessionToken } from '../../services/platformSession';

// Reporting shares Haitong's identity bridge, expiry handling and session-change guard.
export const platformSessionToken = getPlatformSessionToken;

export function platformApi<T>(path: string, options: { method?: 'GET' | 'POST'; body?: unknown } = {}): Promise<T> {
  return request<T>(path, options.body, { method: options.method, timeoutMs: 30_000 });
}
