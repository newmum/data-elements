import type { FieldSchema } from '../types/manifest.ts';

/** Capability-specific controls; unsupported settings never appear on another protocol. */
export function sourceReadFieldVisible(key: string, config: Record<string, unknown>): boolean {
  const ftps = String(config.ftpProtocol ?? config.protocol ?? '').toLowerCase() === 'ftps';
  if (key === 'maxFilesPerRun') return ftps || config.fileConsumptionPolicy !== 'DELETE_AFTER_DATABASE_COMMIT';
  if (key === 'remotePollBatchSize') return !ftps && config.fileConsumptionPolicy !== 'DELETE_AFTER_DATABASE_COMMIT';
  return true;
}

/** Validate local source controls without making per-node API requests. */
export function sourceReadConfigurationError(fields: FieldSchema[], config: Record<string, unknown>): string | undefined {
  for (const field of fields) {
    if (field.group !== 'read' || field.type !== 'number' || !sourceReadFieldVisible(field.key, config)) continue;
    const value = config[field.key] ?? field.default;
    if (value == null || String(value).trim() === '') continue;
    const count = Number(value), minimum = ['maxRowsPerRun', 'defaultFetchSize'].includes(field.key) ? 0 : 1;
    const maximum = field.key === 'maxFilesPerRun' && String(config.ftpProtocol ?? config.protocol ?? '').toLowerCase() === 'ftps' ? 100 : 2147483647;
    if (!Number.isInteger(count) || count < minimum || count > maximum) return `${field.label}必须为${minimum}～${maximum} 的整数`;
  }
}
