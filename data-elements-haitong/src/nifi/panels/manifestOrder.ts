import type { ComponentManifest } from '@/types/manifest';

// Match the database discovery cards. One OceanBase canvas manifest covers
// both registration variants through its compatibility setting.
const registrationSourceOrder = new Map([
  'source.mysql',
  'source.oracle',
  'source.oceanbase',
  'source.kingbase',
  'source.postgresql',
  'source.dameng',
  'source.hive',
  'source.gaussdb',
  'source.ftp',
  'source.kafka',
  'source.minio',
  'source.elasticsearch',
  'source.mongodb',
  'source.gbase8a',
  'source.sqlserver',
  'source.vertica',
].map((key, index) => [key, index]));

export function compareCanvasManifests(a: ComponentManifest, b: ComponentManifest) {
  if (a.category === 'source' && b.category === 'source') {
    const aOrder = registrationSourceOrder.get(a.key) ?? registrationSourceOrder.size;
    const bOrder = registrationSourceOrder.get(b.key) ?? registrationSourceOrder.size;
    if (aOrder !== bOrder) return aOrder - bOrder;
  }
  return a.label.localeCompare(b.label, 'zh-CN');
}
