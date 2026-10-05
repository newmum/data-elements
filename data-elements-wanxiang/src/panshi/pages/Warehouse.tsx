import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Table, Tag } from 'antd';
import { DatabaseOutlined, DownOutlined, FolderOpenOutlined, RightOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { DataTable } from '../../features/platform/common';
import type { Row } from '../../services/api';
import { SourceIcon } from '../../design/Visuals';
import { Hero, NameCell, timeText } from '../components/ui';
import type { Layer, ResourceState, Warehouse } from '../domain/types';
import { useResource } from '../services/context';
import {
  centerTableById, centerTableColumns, centerTablePage,
  type CenterColumn, type CenterTable, type CenterTablePage,
} from '../services/catalogApi';

type WarehouseScope = { kind: 'all' | 'layer' | 'database'; id: string };
type WarehouseRow = CenterTable & { id: string };
const emptyPage = (pageNo = 1, pageSize = 15): CenterTablePage => ({
  rows: [], catalogs: [], total: 0, pageNo, pageSize,
});

function sourceIdsForScope(scope: WarehouseScope, layers: Layer[], databases: Warehouse[]): string[] {
  const activeLayers = layers.filter(layer => layer.state === 'ACTIVE');
  const activeDatabases = databases.filter(database =>
    database.state === 'ACTIVE' && activeLayers.some(layer => layer.id === database.layerId));
  const linked = scope.kind === 'all'
    ? [...activeLayers, ...activeDatabases]
    : scope.kind === 'layer'
      ? [...activeLayers.filter(layer => layer.id === scope.id),
        ...activeDatabases.filter(database => database.layerId === scope.id)]
      : activeDatabases.filter(database => database.id === scope.id);
  return [...new Set(linked.map(item => item.sourceId).filter((id): id is string => !!id))];
}

function WarehouseTree({ selected, onSelect, layers, databases, sourceCounts }: {
  selected: WarehouseScope;
  onSelect: (scope: WarehouseScope) => void;
  layers: Layer[];
  databases: Warehouse[];
  sourceCounts: Record<string, number>;
}) {
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());
  const count = (scope: WarehouseScope) => sourceIdsForScope(scope, layers, databases)
    .reduce((total, id) => total + (sourceCounts[id] ?? 0), 0);
  const toggle = (id: string) => setCollapsed(previous => {
    const next = new Set(previous);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
  return <aside className="ps-filter-tree ps-warehouse-tree" aria-label="按分层和分库筛选数据表">
    <h3>分层 / 分库</h3>
    <nav aria-label="数据仓库结构">
      <button type="button" className={`ps-warehouse-tree-all${selected.kind === 'all' ? ' active' : ''}`}
        aria-current={selected.kind === 'all' ? 'true' : undefined}
        onClick={() => onSelect({ kind: 'all', id: '' })}>
        <FolderOpenOutlined aria-hidden="true"/><span>全部数据表</span>
        <b>{count({ kind: 'all', id: '' })}</b>
      </button>
      <ul className="ps-warehouse-tree-layers">
        {layers.filter(layer => layer.state === 'ACTIVE').map(layer => {
          const children = databases.filter(database =>
            database.state === 'ACTIVE' && database.layerId === layer.id);
          const expanded = !collapsed.has(layer.id);
          const active = selected.kind === 'layer' && selected.id === layer.id;
          const label = layer.name.toUpperCase().endsWith(` ${layer.code.toUpperCase()}`)
            ? layer.name.slice(0, -layer.code.length).trim() : layer.name;
          return <li key={layer.id}>
            <div className={`ps-warehouse-layer-row${active ? ' active' : ''}`}>
              <button type="button" className="ps-warehouse-layer-select"
                aria-current={active ? 'true' : undefined}
                onClick={() => onSelect({ kind: 'layer', id: layer.id })}
                title={`${label} · ${layer.code}`}>
                <span className="ps-warehouse-layer-name">{label}<small>{layer.code}</small></span>
                <b>{count({ kind: 'layer', id: layer.id })}</b>
              </button>
              <button type="button" className="ps-warehouse-layer-toggle"
                aria-label={`${expanded ? '收起' : '展开'}${layer.name}下的分库`}
                aria-expanded={expanded} aria-controls={`warehouse-layer-${layer.id}`}
                onClick={() => toggle(layer.id)}>{expanded ? <DownOutlined/> : <RightOutlined/>}</button>
            </div>
            <ul id={`warehouse-layer-${layer.id}`} className="ps-warehouse-tree-databases" hidden={!expanded}>
              {children.map(database => {
                const databaseActive = selected.kind === 'database' && selected.id === database.id;
                return <li key={database.id}>
                  <button type="button" className={`ps-warehouse-database${databaseActive ? ' active' : ''}`}
                    aria-current={databaseActive ? 'true' : undefined}
                    onClick={() => onSelect({ kind: 'database', id: database.id })}
                    title={database.name}>
                    <DatabaseOutlined aria-hidden="true"/><span>{database.name}</span>
                    <small>{count({ kind: 'database', id: database.id })}</small>
                  </button>
                </li>;
              })}
            </ul>
          </li>;
        })}
      </ul>
    </nav>
  </aside>;
}

function placementForSource(state: ResourceState, sourceId: string): string {
  const labels = state.databases.filter(database =>
    database.state === 'ACTIVE' && database.sourceId === sourceId)
    .map(database => {
      const layer = state.layers.find(item => item.id === database.layerId);
      return [layer?.code, database.name].filter(Boolean).join(' / ');
    });
  for (const layer of state.layers) {
    if (layer.state === 'ACTIVE' && layer.sourceId === sourceId) labels.unshift(layer.name);
  }
  return [...new Set(labels)].join('、') || '—';
}

export function WarehousePage() {
  const { data, refresh } = useResource();
  const state = data.state;
  const nav = useNavigate();
  const [params, setParams] = useSearchParams();
  const selected: WarehouseScope = params.get('database')
    ? { kind: 'database', id: params.get('database')! }
    : params.get('layer')
      ? { kind: 'layer', id: params.get('layer')! }
      : { kind: 'all', id: '' };
  const sourceIds = sourceIdsForScope(selected, state.layers, state.databases);
  const allSourceIds = sourceIdsForScope({ kind: 'all', id: '' }, state.layers, state.databases);
  const sourceKey = sourceIds.join(',');
  const allSourceKey = allSourceIds.join(',');
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(15);
  const [keyword, setKeyword] = useState('');
  const [revision, setRevision] = useState(0);
  const [page, setPage] = useState<CenterTablePage>(() => emptyPage());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState<CenterTable>();
  const [columns, setColumns] = useState<CenterColumn[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const tableId = params.get('table');
  const legacyResourceId = params.get('resource');
  const detailOpen = !!(tableId || legacyResourceId);

  const sourceCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const item of [...state.layers, ...state.databases]) {
      if (item.state !== 'ACTIVE' || !item.sourceId) continue;
      counts[item.sourceId] = Math.max(counts[item.sourceId] ?? 0, Number(item.tableCount) || 0);
    }
    return counts;
  }, [state.layers, state.databases]);

  useEffect(() => {
    let active = true;
    if (!sourceIds.length) {
      setPage(emptyPage(pageNo, pageSize));
      setError('');
      setLoading(false);
      return;
    }
    setPage(emptyPage(pageNo, pageSize));
    setLoading(true);
    setError('');
    void centerTablePage(sourceIds, pageNo, keyword, '', pageSize)
      .then(result => { if (active) setPage(result); })
      .catch(cause => {
        if (active) { setPage(emptyPage(pageNo, pageSize)); setError(cause instanceof Error ? cause.message : String(cause)); }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [sourceKey, pageNo, pageSize, keyword, revision]);

  useEffect(() => {
    let active = true;
    if (!detailOpen) { setDetail(undefined); setColumns([]); setDetailError(''); return; }
    setDetail(undefined);
    setColumns([]);
    setDetailError('');
    setDetailLoading(true);
    const load = async () => {
      let table: CenterTable | undefined;
      if (tableId) {
        table = await centerTableById(allSourceIds, tableId);
      } else {
        const resource = state.resources.find(item => item.id === legacyResourceId);
        if (!resource) throw new Error('这项资源已不存在');
        const sourceId = resource.structure.sourceId;
        if (!sourceId || !allSourceIds.includes(sourceId)) throw new Error('输出表未关联到当前数仓规划的数据源');
        const matches = await centerTablePage([sourceId], 1, resource.structure.name, '', 100);
        table = matches.rows.find(item =>
          item.table_name.toLocaleLowerCase() === resource.structure.name.toLocaleLowerCase());
        if (!table) table = await centerTableById([sourceId], resource.entityId.replace(/^shared-table:/, ''));
      }
      if (!table) throw new Error('未找到对应的已采集数据表，请刷新数据源的元数据');
      const fields = await centerTableColumns(table.tid);
      if (active) { setDetail(table); setColumns(fields); }
    };
    void load().catch(cause => {
      if (active) setDetailError(cause instanceof Error ? cause.message : String(cause));
    }).finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [tableId, legacyResourceId, allSourceKey, state.resources]);

  const select = (scope: WarehouseScope) => {
    setPageNo(1);
    setKeyword('');
    setParams(current => {
      const next = new URLSearchParams(current);
      next.delete('database');
      next.delete('layer');
      if (scope.kind !== 'all') next.set(scope.kind === 'database' ? 'database' : 'layer', scope.id);
      return next;
    });
  };
  const open = (row: CenterTable) => setParams(current => {
    const next = new URLSearchParams(current);
    next.delete('resource');
    next.set('table', row.tid);
    return next;
  });
  const close = () => setParams(current => {
    const next = new URLSearchParams(current);
    next.delete('table');
    next.delete('resource');
    return next;
  });
  const reload = () => { setRevision(value => value + 1); void refresh(); };
  const rows: WarehouseRow[] = page.rows.map(row => ({ ...row, id: row.tid }));
  const catalogFor = (row: CenterTable) =>
    page.catalogs.find(catalog => catalog.source_table_id === row.tid);
  const sourceName = (id: string) =>
    data.sources.find(source => source.id === id)?.name || '数据源已不可用';

  return <>
    <Hero page="warehouse"/>
    <div className="ps-workspace-split">
      <WarehouseTree selected={selected} onSelect={select} layers={state.layers}
        databases={state.databases} sourceCounts={sourceCounts}/>
      <section className="ps-table-panel">
        <DataTable key={`${selected.kind}:${selected.id}`} titleKey="resource:warehouse-tables"
          rows={rows as Row[]} loading={loading} error={error} onRefresh={reload}
          searchPlaceholder="搜索数据表中文名、技术名…" onSearch={value => { setKeyword(value); setPageNo(1); }}
          emptyTitle={sourceIds.length ? '该范围暂无已采集数据表' : '尚未关联数据源'}
          emptyDescription={sourceIds.length
            ? '请确认关联数据源已完成表结构采集。'
            : '先在分库配置中关联共享数据源，即可在此查看其中的数据表。'}
          serverPagination={{ page: pageNo, size: pageSize, total: page.total,
            onChange: (nextPage, nextSize) => { setPageNo(nextPage); setPageSize(nextSize); } }}
          columns={[
            { title: '数据表', key: 'table_name', width: 250, ellipsis: true,
              render: (_value, row) => <button className="ps-text-button ps-warehouse-table-name"
                onClick={() => open(row as WarehouseRow)}>
                <NameCell name={row.table_name_cn || row.table_comment || row.table_name}
                  code={row.table_name}/>
              </button> },
            { title: '分层 / 分库', key: 'placement', width: 190, ellipsis: true,
              render: (_value, row) => <span title={placementForSource(state, row.datasource_id)}>
                {placementForSource(state, row.datasource_id)}</span> },
            { title: '所属数据源', key: 'source', width: 180, ellipsis: true,
              render: (_value, row) => <div className="ps-source-cell">
                <SourceIcon engine={data.sources.find(source => source.id === row.datasource_id)?.engine} small/>
                <span title={sourceName(row.datasource_id)}>{sourceName(row.datasource_id)}</span>
              </div> },
            { title: '表类型', dataIndex: 'table_type', width: 100,
              render: value => value || '数据表' },
            { title: '字段数', dataIndex: 'field_count', width: 92,
              render: value => value ?? '—' },
            { title: '采集更新时间', dataIndex: 'updated_time', width: 165,
              render: value => timeText(value) },
            { title: '目录状态', key: 'catalog', width: 110,
              render: (_value, row) => catalogFor(row as WarehouseRow)
                ? <Tag color="blue">已编目</Tag> : <Tag>未编目</Tag> },
            { title: '操作', key: 'action', width: 164, fixed: 'right', align: 'center',
              render: (_value, row) => <div className="ps-warehouse-actions">
                <Button type="link" onClick={() => open(row as WarehouseRow)}>查看字段</Button>
                <Button type="link" onClick={() => {
                  const catalog = catalogFor(row as WarehouseRow);
                  nav(catalog
                    ? `/resource/catalog/entries/${catalog.tid}`
                    : `/resource/catalog/entries/new?resource=${row.tid}`);
                }}>{catalogFor(row as WarehouseRow) ? '查看编目' : '目录编目'}</Button>
              </div> },
          ]}/>
      </section>
    </div>
    <Drawer open={detailOpen} width={860} title={detail?.table_name_cn || detail?.table_name || '数据表详情'}
      onClose={close} loading={detailLoading}
      extra={detail && <Button type="primary" onClick={() => {
        const catalog = page.catalogs.find(item => item.source_table_id === detail.tid);
        nav(catalog
          ? `/resource/catalog/entries/${catalog.tid}`
          : `/resource/catalog/entries/new?resource=${detail.tid}`);
      }}>目录编目</Button>}>
      {detailError && <Alert type="error" showIcon title={detailError}/>}
      {detail && <>
        <div className="ps-resource-identity">
          <SourceIcon engine={data.sources.find(source => source.id === detail.datasource_id)?.engine}/>
          <NameCell name={detail.table_name_cn || detail.table_comment || detail.table_name}
            code={detail.table_name}/>
        </div>
        <Descriptions bordered size="small" column={2} items={[
          { key: 'source', label: '所属数据源', children: sourceName(detail.datasource_id) },
          { key: 'placement', label: '分层 / 分库', children: placementForSource(state, detail.datasource_id) },
          { key: 'type', label: '表类型', children: detail.table_type || '数据表' },
          { key: 'fields', label: '字段数', children: columns.length },
          { key: 'updated', label: '采集更新时间', children: timeText(detail.updated_time) },
          { key: 'comment', label: '表说明', children: detail.table_comment || '—', span: 2 },
        ]}/>
        <h3 className="ps-section-title">表字段 <small>{columns.length} 个</small></h3>
        <Table rowKey="tid" size="small" dataSource={columns} pagination={{ pageSize: 15 }}
          scroll={{ x: 720 }} columns={[
            { title: '字段名', dataIndex: 'column_name', width: 190, ellipsis: true },
            { title: '中文名 / 说明', dataIndex: 'column_comment', width: 220, ellipsis: true,
              render: value => value || '—' },
            { title: '字段类型', width: 155,
              render: (_value, field) => field.column_type || field.data_type || '—' },
            { title: '主键', width: 70,
              render: (_value, field) => field.primary_key ? '是' : '否' },
            { title: '可空', width: 70,
              render: (_value, field) => field.nullable == null ? '—' : field.nullable ? '是' : '否' },
          ]}/>
      </>}
    </Drawer>
  </>;
}
