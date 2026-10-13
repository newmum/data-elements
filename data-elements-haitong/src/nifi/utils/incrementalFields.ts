import type { CanvasDsl } from '../types/dsl';
import { syncSettings, syncSettingsError } from './syncSettings.ts';

/** Keep a saved watermark editable even while full sync is selected. */
export function incrementalFieldVisible(key: string, config: Record<string, unknown>): boolean {
  if (key !== 'initialValue') return true;
  return syncSettings(config).syncMode === 'INCREMENTAL' && config.initialStrategy === 'START_AT_VALUE';
}

/** Validate only the saved/user-edited configuration; never fetch or backfill registration values. */
export function incrementalConfigurationError(dsl: CanvasDsl): string | undefined {
  for (const node of dsl.nodes) {
    if (node.category !== 'source' || ['source.api', 'source.ftp', 'source.sftp', 'source.kafka', 'source.minio',
      'source.hdfs', 'source.hbase', 'source.elasticsearch', 'source.iotdb'].includes(node.manifestKey)) continue;
    const error = syncSettingsError(node.config, syncSettings(node.config));
    if (error) return `${node.label}：${error}`;
  }
}
