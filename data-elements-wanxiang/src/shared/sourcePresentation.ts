import type { Row } from '../services/api';
/** Counts are calculated from local entities, never from random statistics. */
export function sourceStatistics(sourceId: string, entities: Row[], catalog: Row[] = [], models: Row[] = []) {
  const own = entities.filter(e => e.sourceId === sourceId);
  const ids = new Set(own.map(e => e.id));
  return {
    entityCount: own.length, fieldCount: own.reduce((n,e) => n + (e.fields?.length ?? 0),0),
    tableCount: own.filter(e => e.kind === 'table').length,
    collectionCount: own.filter(e => e.kind === 'collection').length,
    indexCount: own.filter(e => e.kind === 'index').length,
    viewCount: own.filter(e => e.kind === 'view').length,
    catalogCount: catalog.length,
    modelCount: models.filter(m => (m.layout?.nodes ?? []).some((n: Row) => ids.has(n.entityId))).length,
  };
}
export function sourceEndpoint(source: Row): string {
  const c = source.connection ?? source.connection_json ?? {};
  if (!c.host) return '未登记地址';
  const raw=String(c.host);
  const host = raw.includes(':') && !raw.startsWith('[') ? `[${raw}]` : raw;
  return `${host}${c.port ? ':'+c.port : ''}`;
}
export function sourceTypeDescription(engine: string): string {
  const type=String(engine).toUpperCase();
  if(type==='MONGODB')return '文档型数据库';
  if(type==='ELASTICSEARCH')return '检索与分析引擎';
  if(type==='API')return '接口数据源';
  if(type==='FTP'||type==='SFTP')return '文件服务';
  if(type==='KAFKA')return '消息流';
  if(type==='MINIO')return '对象存储';
  if(type==='MAXCOMPUTE')return '云数据仓库';
  if(type==='OTHER')return '未分类数据源';
  return '关系型数据库';
}
export function sourceScope(source: Row): string {
  const c = source.connection ?? source.connection_json ?? {};
  return [c.database,c.schema].filter(Boolean).join(' / ') || '未指定命名空间';
}

export function objectKindLabel(kind: unknown): string { const labels: Record<string,string> = {table:'数据表',view:'视图',collection:'集合',index:'索引',embedded:'嵌套对象'}; return labels[String(kind)] ?? String(kind ?? '未知'); }
