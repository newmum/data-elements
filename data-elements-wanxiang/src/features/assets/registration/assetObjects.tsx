import { ApartmentOutlined, ApiOutlined, DatabaseOutlined, FileOutlined, FolderOpenOutlined, TableOutlined } from '@ant-design/icons';
import type { ReactNode } from 'react';

export type AssetKind = 'APPLICATION' | 'DATASOURCE' | 'TABLE' | 'CATALOG' | 'API' | 'FILE';
export type RegistrationType = 'applications' | 'databases' | 'catalogs' | 'apis';
export interface AssetRef { kind: AssetKind; id: string; name: string; code?: string; versionId?: string; available?: boolean }
export interface AssetSummary {
  object: AssetRef; departmentId?: string; departmentName?: string; applicationId?: string; applicationName?: string;
  tags?: string[]; updatedAt: string; favorite?: boolean; allowedActions: string[]; publishedVersionId?: string;
  listingStatus?: string; channels?: string[]; governance?: { qualityStatus: string; qualityScore?: number | null; standardBoundFields?: number; eligibleFields?: number; checkedAt?: string; structureChanged?: boolean };
}
export interface AssetPageResult { items: AssetSummary[]; page: { pageNo: number; pageSize: number; total: number; hasMore: boolean }; projectionStatus: string }
export interface DataItem { id?: string; name: string; code: string; dataType: string; length?: number; precision?: number; scale?: number; nullable: boolean; primaryKey?: boolean; definition?: string; standardId?: string; standardCode?: string; ordinal: number }
export interface AssetAttachment { id?: string; resource: AssetRef; resourceType: string; bindingRole: string; channel?: string; resourceAvailability?: string; status?: string }
export interface ApiParameter { name: string; location: string; dataType: string; required: boolean; description?: string; example?: string }
export interface ObjectDetail {
  summary: AssetSummary; revision: string; description?: string; relatedObjects: AssetRef[]; relationsHasMore: boolean;
  details: Record<string, unknown> & { fields?: DataItem[]; items?: DataItem[]; attachments?: AssetAttachment[]; parameters?: ApiParameter[]; technical?: Record<string, unknown>; fieldPage?: { total: number }; itemPage?: { total: number } };
}
export const assetKindLabels: Record<AssetKind, string> = { APPLICATION: '应用系统', DATASOURCE: '业务库', TABLE: '数据表', CATALOG: '资源目录', API: 'API 服务', FILE: '文件资源' };
export const registrationKinds: Record<RegistrationType, AssetKind> = { applications: 'APPLICATION', databases: 'DATASOURCE', catalogs: 'CATALOG', apis: 'API' };
export const kindRoutes: Record<AssetKind, string> = { APPLICATION: 'applications', DATASOURCE: 'databases', TABLE: 'tables', CATALOG: 'catalogs', API: 'apis', FILE: 'files' };
export function assetKind(value?: string): AssetKind | undefined {
  if (!value) return undefined;
  const upper = value.toUpperCase();
  if (upper in assetKindLabels) return upper as AssetKind;
  return (Object.entries(kindRoutes).find(([, route]) => route === value)?.[0]) as AssetKind | undefined;
}
export function assetDetailPath(object: AssetRef): string { return `/assets/detail/${kindRoutes[object.kind]}/${encodeURIComponent(object.id)}`; }
export const assetIcon = (kind: AssetKind): ReactNode => ({ APPLICATION: <ApartmentOutlined />, DATASOURCE: <DatabaseOutlined />, TABLE: <TableOutlined />, CATALOG: <FolderOpenOutlined />, API: <ApiOutlined />, FILE: <FileOutlined /> })[kind];
export const asText = (value: unknown): string => value == null || value === '' ? '—' : typeof value === 'object' ? '—' : String(value);
export function formatAssetTime(value: unknown): string {
  if (value == null || value === '') return '—';
  const parsed = new Date(typeof value === 'number' ? value : String(value));
  return Number.isNaN(parsed.getTime()) ? asText(value) : parsed.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false });
}
export function dataTypeLabel(item: DataItem): string {
  const type = item.dataType || '—';
  if (type.includes('(')) return type;
  const size = item.precision ?? item.length;
  return size == null ? type : `${type}(${size}${item.scale == null ? '' : `,${item.scale}`})`;
}
export function assertAssetPage(value: AssetPageResult): AssetPageResult {
  if (!Array.isArray(value?.items) || !value.page || !Number.isFinite(value.page.total) || value.items.some(item => !item.object?.id || !item.object.kind)) throw new Error('资产列表返回不完整，请刷新重试');
  return value;
}
export function assertAssetDetail(value: ObjectDetail, kind: AssetKind, id: string): ObjectDetail {
  if (value?.summary?.object?.id !== id || value.summary.object.kind !== kind || !value.details || !Array.isArray(value.relatedObjects)) throw new Error('资产详情与当前对象不一致，请刷新重试');
  return value;
}
