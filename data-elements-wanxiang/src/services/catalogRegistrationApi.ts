import { platformApi } from '../shared/platformApi';
import { sourceOptions, type SourceOption } from './datasourceRegistrationApi';
import type { CatalogTableRow } from './metadataCatalog';
import { catalogTableColumns, type LiveMetadataSource } from './metadata';

export type RegisteredCatalog = { tid: string; catalogName: string; sourceTableId: string; dbId?: string; catalogNameEn?: string; assetDesc?: string };
export type CatalogItem = Record<string, unknown> & { tid?: string; colName: string; colEn: string; colType: string; colLength?: number; isPk?: string; isNullable?: string; sourceTableColumnId?: string };
export type CataloguedTableRow = CatalogTableRow & { directories: RegisteredCatalog[] };
export type CatalogChoices = Record<string, SourceOption[]>;
const rows = (value: unknown): Record<string, unknown>[] => {
  if (Array.isArray(value)) return value as Record<string, unknown>[];
  if (!value || typeof value !== 'object') return [];
  const object = value as Record<string, unknown>;
  return rows(object.list ?? object.rows ?? object.data);
};
const text = (value: unknown) => value == null ? '' : String(value);

/** Associations use canonical IDs, not table/department names or source registration status.
 * Keep multiple real directories visible; never invent or auto-register a directory on read. */
export function associateCatalogs(tables: CatalogTableRow[], catalogs: RegisteredCatalog[]): CataloguedTableRow[] {
  const byTable = new Map<string, RegisteredCatalog[]>();
  for (const catalog of catalogs) {
    if (!catalog.tid || !catalog.sourceTableId) continue;
    const associated = byTable.get(catalog.sourceTableId) || [];
    if (!associated.some(item => item.tid === catalog.tid)) associated.push(catalog);
    byTable.set(catalog.sourceTableId, associated);
  }
  return tables.map(table => ({ ...table, directories: (byTable.get(table.tid) || []).filter(catalog => !catalog.dbId || catalog.dbId === table.datasource_id) }));
}
export function catalogDefaults(table: CatalogTableRow, source?: LiveMetadataSource): Record<string, unknown> {
  return {
    catalogName: table.displayName, catalogNameEn: table.table_name, assetDesc: table.table_comment || '',
    sourceTableId: table.tid, sourceTableName: table.table_name, dbId: table.datasource_id,
    dbName: source?.db_name || '', appId: source?.app_id || '', appName: source?.app_name || '',
    orgId: source?.org_id || '', dataSourceType: 'ods',
  };
}
export function catalogItemsFromColumns(columns: unknown): CatalogItem[] {
  return rows(columns).map((column, index) => {
    const name = text(column.column_name ?? column.columnName ?? column.columnNameEn);
    const length = Number(column.length ?? column.columnLength ?? column.precisionLength);
    const pk = column.primary_key ?? column.primaryKey;
    const nullable = column.nullable;
    return {
      colName: text(column.column_comment ?? column.columnComment ?? column.columnNameCn) || name || `字段${index + 1}`,
      colEn: name, colType: text(column.column_type ?? column.columnType ?? column.data_type ?? column.dataType) || 'varchar',
      ...(Number.isFinite(length) && length >= 0 ? { colLength: length } : {}),
      isPk: pk === true || pk === 1 || pk === '1' ? '1' : '0',
      isNullable: nullable === false || nullable === 0 || nullable === '0' ? '0' : '1',
      sourceTableColumnId: text(column.tid ?? column.id ?? column.columnId), sortNo: index + 1, enableCodeTable: 0,
    };
  });
}
export function catalogSaveBody(values: Record<string, unknown>, items: CatalogItem[], table: CatalogTableRow, tid?: string) {
  // Ignore browser-supplied ownership/status. The existing Magic save route resolves
  // the tenant and applies direct registration (no approval or submission endpoint).
  const { tenantId: _tenantId, tenant_id: _tenant, assetStatus: _status, flowStatus: _flow, tid: _tid, id: _id, ...props } = values;
  return {
    ...(tid ? { tid } : {}), assetType: 'catalog',
    propList: { ...props, catalogName: text(props.catalogName).trim(), sourceTableId: table.tid, sourceTableName: table.table_name, dbId: table.datasource_id },
    catalogItems: items.map(({ tenantId: _itemTenant, tenant_id: _itemTenantSnake, catalogId: _catalog, catalog_id: _catalogSnake, clientKey: _key, ...item }, index) => ({ ...item, sortNo: index + 1 })),
  };
}
export const catalogRegistrationApi = {
  async list(signal?: AbortSignal): Promise<RegisteredCatalog[]> {
    const catalogs = new Map<string, RegisteredCatalog>();
    const page = (pageNum:number) => {
      // The platform caps catalog pages at 100. Follow total with real pagination;
      // /list is a legacy projection and /syncRegisteredTables would mutate on read.
      return platformApi<{ list: unknown[]; total: number }>('/dst/catalog/page', {
        pageNum, pageSize: 100, sortField: 'updatedTime', sortDir: 'desc',
        conditions: [{ field: 'assetType', value: 'catalog', type: 'match' }],
      }, {signal});
    };
    const append = (result:{list:unknown[];total:number}) => {
      if(!Array.isArray(result?.list) && !(result?.list===null && Number(result.total)===0))throw new Error('数据目录列表返回格式不正确');
      const batch = rows(result).map(row => ({
        tid: text(row.tid ?? row.id), catalogName: text(row.catalogName), catalogNameEn: text(row.catalogNameEn),
        sourceTableId: text(row.sourceTableId), dbId: text(row.dbId), assetDesc: text(row.assetDesc),
      })).filter(catalog => catalog.tid);
      const size = catalogs.size;
      batch.forEach(catalog => catalogs.set(catalog.tid, catalog));
      if(batch.length && size===catalogs.size)throw new Error('数据目录分页没有前进，请刷新后重试');
    };
    const first=await page(1),total=Number(first.total);if(!Number.isInteger(total)||total<0||total>100000)throw new Error('数据目录数量超出可加载范围或总数不正确');append(first);
    let next=2;const last=Math.ceil(total/100);
    const worker=async()=>{while(next<=last){signal?.throwIfAborted();append(await page(next++));}};
    await Promise.all(Array.from({length:Math.min(4,Math.max(0,last-1))},worker));
    if(catalogs.size<total)throw new Error('数据目录在加载期间发生变化或分页不完整，请刷新后重试');
    return [...catalogs.values()];
  },
  async choices(): Promise<CatalogChoices> {
    const dict = await platformApi<Record<string, { options?: unknown }>>('/sym/dict');
    return Object.fromEntries(Object.entries(dict).map(([key, value]) => [key, sourceOptions(value.options)]));
  },
  async detail(tid: string) {
    const [detail, items] = await Promise.all([
      platformApi<Record<string, unknown>>('/dst/catalog/detail', { tid }),
      platformApi<CatalogItem[]>('/dst/catalog/catalog-items/list', { catalogId: tid }),
    ]);
    return { detail, items: items.map(item => ({ ...item, colName: text(item.colName), colEn: text(item.colEn), colType: text(item.colType), isPk: text(item.isPk || '0'), isNullable: text(item.isNullable ?? '1') })) };
  },
  columns: (tableId: string) => catalogTableColumns(tableId),
  save: (values: Record<string, unknown>, items: CatalogItem[], table: CatalogTableRow, tid?: string) => platformApi<{ tid: string; catalogName?: string }>('/dst/catalog/saveOrUpdate', catalogSaveBody(values, items, table, tid)),
  remove: (tid: string) => platformApi<{ tid: string; deleted: boolean; indexRefreshPending?: boolean }>('/dst/catalog/delete', { tid }),
};
