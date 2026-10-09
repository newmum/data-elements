export type SyncMode = 'FULL' | 'INCREMENTAL' | 'FULL_THEN_INCR' | 'PERIODIC_FULL';
export interface SyncSettings {
  syncMode: SyncMode;
  fullSyncStrategy: 'UPSERT' | 'TRUNCATE_RELOAD';
  deleteTargetData: boolean;
}
export const syncModes = [
  { value: 'FULL', label: '全量同步', description: '抽取全部数据，本轮处理完成后停止。' },
  { value: 'INCREMENTAL', label: '增量同步', description: '按主增量字段持续抽取新增或变更数据。' },
  { value: 'FULL_THEN_INCR', label: '首次全量后增量', description: '首次从最早数据开始，后续沿用增量水位。' },
  { value: 'PERIODIC_FULL', label: '定时全量同步', description: '按周期或 Cron 重复抽取全表；上一轮结束后再处理下一轮。' },
] as const;
export function syncSettings(config: Record<string, unknown> = {}): SyncSettings {
  const raw = String(config.syncMode ?? '').trim().toUpperCase();
  const explicit = raw === 'INCR' ? 'INCREMENTAL' : raw;
  const inferred = String(config.incrementalColumn ?? '').trim() ? 'INCREMENTAL' : 'FULL';
  const syncMode = (explicit || inferred) as SyncMode;
  return {
    syncMode,
    fullSyncStrategy: (String(config.fullSyncStrategy ?? '').trim().toUpperCase() || 'UPSERT') as SyncSettings['fullSyncStrategy'],
    deleteTargetData: (syncMode === 'FULL' || syncMode === 'FULL_THEN_INCR')
      && (config.deleteTargetData === true || String(config.deleteTargetData).toLowerCase() === 'true'),
  };
}
export function syncModeLabel(mode: string) {
  return syncModes.find(item => item.value === mode)?.label ?? `未知同步方式：${mode}`;
}
export function syncSettingsPatch(config: Record<string, unknown>, settings: SyncSettings) {
  return {
    ...settings,
    deleteTargetData: ['FULL', 'FULL_THEN_INCR'].includes(settings.syncMode) && settings.deleteTargetData,
    ...(settings.syncMode === 'FULL_THEN_INCR' ? { initialStrategy: 'START_AT_BEGINNING' }
      : settings.syncMode === 'INCREMENTAL' && !config.initialStrategy ? { initialStrategy: 'START_AT_BEGINNING' } : {}),
  };
}
export function syncSettingsError(config: Record<string, unknown>, settings: SyncSettings): string | undefined {
  if (!syncModes.some(item => item.value === settings.syncMode)) return '请选择有效的同步方式';
  if (['INCREMENTAL', 'FULL_THEN_INCR'].includes(settings.syncMode) && !String(config.incrementalColumn ?? '').trim()) {
    return '请先在来源节点配置主增量字段；也可以选择全量同步或定时全量同步';
  }
  if (settings.syncMode === 'PERIODIC_FULL' && !['UPSERT', 'TRUNCATE_RELOAD'].includes(settings.fullSyncStrategy)) {
    return '请选择有效的全量写入策略';
  }
}
export function needsCleanupConfirmation(settings: SyncSettings) {
  return settings.deleteTargetData || (settings.syncMode === 'PERIODIC_FULL' && settings.fullSyncStrategy === 'TRUNCATE_RELOAD');
}

/** Compare persisted content independently of object key order. */
export function sameSavedContent(left: unknown, right: unknown): boolean {
  const canonical = (value: unknown): unknown => Array.isArray(value) ? value.map(canonical)
    : value && typeof value === 'object' ? Object.fromEntries(Object.entries(value)
      .filter(([, item]) => item !== undefined).sort(([a], [b]) => a.localeCompare(b))
      .map(([key, item]) => [key, canonical(item)])) : value;
  return JSON.stringify(canonical(left)) === JSON.stringify(canonical(right));
}
