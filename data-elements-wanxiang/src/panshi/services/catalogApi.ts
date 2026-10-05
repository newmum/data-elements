import { resourceGet, resourcePost, resourceSessionScope } from './transport';
import type { ResourceState } from '../domain/types';

export interface CenterTable {
  tid: string;
  datasource_id: string;
  table_name: string;
  table_name_cn?: string;
  table_comment?: string;
  table_type?: string;
  field_count?: number;
  updated_time?: string;
}

export interface CenterTablePage {
  rows: CenterTable[];
  catalogs: Array<{ tid: string; catalog_name: string; source_table_id: string }>;
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface CenterColumn {
  tid: string;
  table_id: string;
  column_name: string;
  column_comment?: string;
  data_type?: string;
  column_type?: string;
  length?: number;
  nullable?: number | boolean;
  primary_key?: number | boolean;
  ordinal_position?: number;
}

export interface DirectoryItem {
  tid?: string;
  sourceTableColumnId?: string;
  colName: string;
  colEn: string;
  colType: string;
  colLength?: number;
  colComment?: string;
  isPk?: string;
  isNullable?: string;
  dataStandardId?: string;
  qualityRule?: string;
  enableCodeTable?: number;
  codeTableId?: string;
  sortNo?: number;
}

export interface DirectoryDetail extends Record<string, unknown> {
  tid: string;
  catalogName: string;
  catalogNameEn?: string;
  assetDesc?: string;
  sourceTableId?: string;
  sourceTableName?: string;
  dbId?: string;
  orgId?: string;
  assetStatus?: number;
  flowStatus?: number;
  props?: Record<string, unknown>;
}

export interface DirectoryDictionary { tid: string; dictName: string; dictCode: string; parentId?: string; }

export function centerSourceIds(state: ResourceState): string[] {
  return [...new Set([...state.layers, ...state.databases]
    .filter(item => item.state === 'ACTIVE' && item.sourceId)
    .map(item => String(item.sourceId)))];
}

export async function centerTablePage(sourceIds: string[], pageNo: number, keyword = '', sourceId = '', pageSize = 15): Promise<CenterTablePage> {
  if (!sourceIds.length) return { rows: [], catalogs: [], total: 0, pageNo, pageSize };
  const eligibleIds = sourceId && sourceIds.includes(sourceId) ? [sourceId] : sourceIds;
  return resourceGet('/dwm/metadata-governance/tables/page', {
    sourceIds: eligibleIds.join(','), pageNo, pageSize, keyword,
  });
}

export async function centerTableById(sourceIds: string[], tableId: string): Promise<CenterTable | undefined> {
  if (!sourceIds.length || !tableId) return undefined;
  const page = await resourceGet<CenterTablePage>('/dwm/metadata-governance/tables/page', {
    sourceIds: sourceIds.join(','), tableId, pageNo: 1, pageSize: 1,
  });
  return page.rows[0];
}

export const centerTableColumns = (tableId: string) => resourceGet<CenterColumn[]>(
  '/dwm/metadata-governance/tables/columns', { tableId }, { timeoutMs: 30000 },
);

export const directoryDetail = (tid: string) => resourcePost<DirectoryDetail>('/dst/catalog/detail', { tid });
export const directoryItems = (tid: string) => resourcePost<DirectoryItem[]>('/dst/catalog/catalog-items/list', { tid });
/** Load the tenant's dictionary tree once instead of issuing a request for each form field. */
export async function directoryDictionaries(codes: readonly string[]): Promise<Record<string, DirectoryDictionary[]>> {
  const all = await resourceGet<Record<string, { options?: DirectoryDictionary[] | null }>>('/sym/dict');
  if (!all || typeof all !== 'object' || Array.isArray(all)) throw new Error('数据目录字典返回格式不正确');
  return Object.fromEntries(codes.map(code => [code, Array.isArray(all[code]?.options) ? all[code].options : []]));
}

/** Keep an asynchronous detail response inside the session in which it was requested. */
export async function loadDirectory(tid: string) {
  const scope = resourceSessionScope();
  const [detail, items] = await Promise.all([directoryDetail(tid), directoryItems(tid)]);
  if (scope !== resourceSessionScope()) throw new Error('登录或租户已切换，请重新加载目录');
  return { detail, items };
}

export function itemsFromColumns(columns: CenterColumn[]): DirectoryItem[] {
  return columns.map((column, index) => ({
    sourceTableColumnId: column.tid,
    colName: column.column_comment || column.column_name,
    colEn: column.column_name,
    colType: column.column_type || column.data_type || 'VARCHAR',
    colLength: column.length,
    colComment: column.column_comment,
    isPk: column.primary_key ? '1' : '0',
    isNullable: column.nullable === false || column.nullable === 0 ? '0' : '1',
    enableCodeTable: 0,
    sortNo: index + 1,
  }));
}
