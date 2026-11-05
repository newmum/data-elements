export const DAILY_STAGGERED = 'DAILY_STAGGERED';

export function isBatchSource(key: string): boolean {
  return key.startsWith('source.') && key !== 'source.kafka';
}

/** Only applied on creation or an explicit policy switch, never when loading saved nodes. */
export function dailyScheduleDefaults(config: Record<string, unknown>): Record<string, unknown> {
  return { ...config, batchSchedulePolicy: DAILY_STAGGERED, batchScheduleZone: 'Asia/Shanghai',
    batchScheduleTime: undefined, schedulingStrategy: 'CRON_DRIVEN', schedulingPeriod: '' };
}

export function automaticSchedule(config: Record<string, unknown>): boolean {
  return config.batchSchedulePolicy === DAILY_STAGGERED;
}

export function scheduleFieldVisible(key: string, config: Record<string, unknown>): boolean {
  return !automaticSchedule(config) || !['schedulingStrategy', 'schedulingPeriod'].includes(key);
}
