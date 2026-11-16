import { PlatformApiError } from '../../api/platformError';

export const OVERVIEW_TREND_MINUTES = 360;

// The API accepts local wall-clock times and uses the same timezone as its hourly buckets.
function monitorTime(date: Date): string {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).format(date);
}

export async function loadOverviewTrend<T>(
  read: (beginTime: string, endTime: string, signal: AbortSignal) => Promise<T>,
  signal: AbortSignal,
  now = new Date(),
): Promise<{ minutes: number; snapshot: T | null }> {
  const windows = [OVERVIEW_TREND_MINUTES, 60, 15];
  const endTime = monitorTime(now);
  for (const minutes of windows) {
    if (signal.aborted) throw signal.reason;
    try {
      const snapshot = await read(monitorTime(new Date(now.getTime() - minutes * 60_000)), endTime, signal);
      return { minutes, snapshot };
    } catch (cause) {
      if (signal.aborted || !(cause instanceof PlatformApiError)
        || !(cause.status === 413 || cause.info.code === '413' || /快照超过\s*50000\s*条/.test(cause.message))) throw cause;
    }
  }
  return { minutes: windows[windows.length - 1], snapshot: null };
}
