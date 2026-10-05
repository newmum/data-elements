import { useState } from 'react';
import { Button, Empty, Input, Select, Space, Table, Tooltip } from 'antd';
import { ArrowRightOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { assetsApi } from '../../../services/assets';
import { useWorkspaceQuery } from '../../../services/useWorkspaceQuery';
import { AssetError, AssetName, AssetPage } from '../shared';
import { RegistrationEditor } from './RegistrationEditor';
import { assetDetailPath, assetIcon, assetKindLabels, assertAssetPage, formatAssetTime, registrationKinds, type AssetPageResult, type AssetSummary, type RegistrationType } from './assetObjects';
import './registration.css';

const sections: { key: RegistrationType; hint: string }[] = [
  { key: 'applications', hint: '连接业务、系统与数据来源' }, { key: 'databases', hint: '登记数据连接，探查表结构' },
  { key: 'catalogs', hint: '编制业务目录，挂接物理资源' }, { key: 'apis', hint: '登记服务地址、请求及响应' },
];

export function AssetRegistrationPage() {
  const { type } = useParams(); const navigate = useNavigate(); const [params, setParams] = useSearchParams();
  const current: RegistrationType = type && type in registrationKinds ? type as RegistrationType : 'catalogs';
  const kind = registrationKinds[current]; const label = assetKindLabels[kind];
  const [keyword, setKeyword] = useState(''), [search, setSearch] = useState('');
  const [pageNo, setPageNo] = useState(1), [pageSize, setPageSize] = useState(15);
  const [sortBy, setSortBy] = useState('updatedAt'), [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [editor, setEditor] = useState<AssetSummary | null | undefined>(undefined);
  const editId = params.get('edit');
  const query = useWorkspaceQuery(`assets-register:${kind}:${keyword}:${pageNo}:${pageSize}:${sortBy}:${sortOrder}`, async signal => assertAssetPage(await assetsApi.get<AssetPageResult>('/search', { view: 'MAP', kinds: kind, keyword, pageNo, pageSize, sortBy, sortOrder }, { signal })));
  const editorRecord = editor === undefined && editId ? { object: { kind, id: editId, name: '' }, updatedAt: '', allowedActions: [] } as AssetSummary : editor;
  const closeEditor = () => { setEditor(undefined); if (editId) { const next = new URLSearchParams(params); next.delete('edit'); setParams(next, { replace: true }); } };
  const searchNow = () => { setPageNo(1); setKeyword(search.trim()); };
  const selectSection = (key: RegistrationType) => { setPageNo(1); setSearch(''); setKeyword(''); setEditor(undefined); navigate(`/assets/register/${key}`); };
  return <AssetPage title="资产登记" description="从业务系统到资源目录，建立清晰、完整的数据资产台账。" actions={<Button type="primary" icon={<PlusOutlined />} onClick={() => setEditor(null)}>登记{label}</Button>}>
    <div className="hy-register-sections" role="tablist" aria-label="登记资产类型">{sections.map(section => {
      const objectKind = registrationKinds[section.key];
      return <button type="button" role="tab" aria-selected={current === section.key} key={section.key} className={current === section.key ? 'is-active' : ''} onClick={() => selectSection(section.key)}><span className="hy-register-section-icon">{assetIcon(objectKind)}</span><span><strong>{assetKindLabels[objectKind]}</strong><small>{section.hint}</small></span><ArrowRightOutlined /></button>;
    })}</div>
    <div className="hy-register-table platform-panel">
      <div className="hy-register-toolbar"><Input value={search} onChange={event => { setSearch(event.target.value); if (!event.target.value) { setKeyword(''); setPageNo(1); } }} onPressEnter={searchNow} allowClear prefix={<SearchOutlined />} placeholder={`搜索${label}名称或编码`} aria-label={`搜索${label}`} suffix={<Button type="text" size="small" icon={<SearchOutlined />} aria-label="查询资产" onClick={searchNow}/>} /><span className="hy-register-count">{query.loading && !query.data ? '读取中…' : `${query.data?.page.total ?? 0} 条记录`}</span><span className="hy-toolbar-spacer"/><Select aria-label="排序方式" value={`${sortBy}:${sortOrder}`} onChange={value => { const [field, order] = value.split(':'); setSortBy(field); setSortOrder(order as 'asc' | 'desc'); setPageNo(1); }} options={[{ value: 'updatedAt:desc', label: '最近更新' }, { value: 'updatedAt:asc', label: '最早更新' }, { value: 'name:asc', label: '名称升序' }, { value: 'name:desc', label: '名称降序' }]}/><Tooltip title="刷新"><Button icon={<ReloadOutlined spin={query.loading}/>} onClick={query.refresh} aria-label="刷新资产登记"/></Tooltip></div>
      <AssetError error={query.error} retry={query.refresh}/>
      <Table<AssetSummary> rowKey={row => `${row.object.kind}:${row.object.id}`} tableLayout="fixed" className="hy-asset-table" loading={query.loading} dataSource={query.data?.items ?? []} scroll={{ x: 940 }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={query.error ? '资产列表读取失败' : keyword ? '没有匹配的资产' : `暂无已登记${label}`}/> }}
        pagination={{ current: pageNo, pageSize, total: query.data?.page.total ?? 0, showSizeChanger: true, pageSizeOptions: [15, 30, 50, 100], showTotal: total => `共 ${total} 条` }}
        onChange={(pagination, _filters, sorter, extra) => { if (extra.action === 'paginate') { setPageNo(pagination.pageSize !== pageSize ? 1 : pagination.current ?? 1); setPageSize(pagination.pageSize ?? pageSize); } if (extra.action === 'sort') { const selected = Array.isArray(sorter) ? sorter[0] : sorter; setSortBy(selected.order ? String(selected.columnKey) : 'updatedAt'); setSortOrder(selected.order === 'ascend' ? 'asc' : 'desc'); setPageNo(1); } }}
        columns={[
          { title: '#', width: 52, align: 'center', render: (_value, _row, index) => (pageNo - 1) * pageSize + index + 1 },
          { title: `${label}名称`, key: 'name', width: 270, sorter: true, sortOrder: sortBy === 'name' ? sortOrder === 'asc' ? 'ascend' : 'descend' : null, ellipsis: true, render: (_value, row) => <AssetName name={row.object.name} code={row.object.code} icon={assetIcon(kind)} onClick={() => navigate(assetDetailPath(row.object))}/> },
          { title: '提供部门', dataIndex: 'departmentName', width: 150, ellipsis: true, render: value => value || '—' },
          ...(kind === 'DATASOURCE' || kind === 'CATALOG' ? [{ title: '所属应用系统', dataIndex: 'applicationName', width: 165, ellipsis: true, render: (value: string) => value || '—' }] : []),
          { title: '更新时间', key: 'updatedAt', width: 158, sorter: true, sortOrder: sortBy === 'updatedAt' ? sortOrder === 'asc' ? 'ascend' : 'descend' : null, render: (_value, row) => formatAssetTime(row.updatedAt) },
          { title: '操作', fixed: 'right', align: 'center', className: 'wx-action-column', width: 126, render: (_value, row) => <Space size={18}><Button type="link" onClick={() => navigate(assetDetailPath(row.object))}>详情</Button><Button type="link" onClick={() => setEditor(row)} disabled={!row.allowedActions?.some(action => ['EDIT', 'MANAGE', 'UPDATE'].includes(action.toUpperCase()))}>编辑</Button></Space> },
        ]}/>
    </div>
    {editorRecord !== undefined && <RegistrationEditor type={current} id={editorRecord?.object.id} onClose={closeEditor} onSaved={() => { closeEditor(); query.refresh(); }}/>} 
  </AssetPage>;
}
