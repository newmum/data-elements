import { platformApi as request, type PlatformUploadProgress } from '../../api/platformApi';
import { getPlatformSessionToken } from '../../services/platformSession';

// Reporting shares Haitong's identity bridge, expiry handling and session-change guard.
export const platformSessionToken = getPlatformSessionToken;

export function platformApi<T>(path: string, options: { method?: 'GET' | 'POST'; body?: unknown; timeoutMs?: number; signal?: AbortSignal; onUploadProgress?: (progress: PlatformUploadProgress) => void } = {}): Promise<T> {
  return request<T>(path, options.body, { method: options.method, timeoutMs: options.timeoutMs ?? 30_000, signal: options.signal, onUploadProgress: options.onUploadProgress });
}
