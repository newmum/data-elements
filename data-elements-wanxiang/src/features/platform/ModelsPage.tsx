import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Empty, Form, Input, Pagination, Segmented, Select, Skeleton, Space, Tag } from 'antd';
import { AppstoreOutlined, CopyOutlined, DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { versionOf, type Row } from '../../services/api';
import { metadataWritable as canWrite } from '../../services/metadata';
import { listModelViews, modelViewApi as api } from '../../services/metadataViews';
import { useWorkspaceQuery } from '../../services/useWorkspaceQuery';
import { useStudio } from '../er/store';
import { ModuleIcon } from '../../design/ModuleIcon';
import { ModelThumbnail } from '../../design/Visuals';
import { DataTable, EditorModal, ErrorNotice, Page, date, required, timeColumn, useAction } from './common';
import { refreshStudio } from '../../services/studioRefresh';
export function ModelsPage() {
    const q = useWorkspaceQuery('shared-metadata-model-views', async () => listModelViews());
    const [edit, setEdit] = useState<Row | null>();
    const [view, setView] = useState<'cards' | 'list'>(() => {
        try {
            return localStorage.getItem('wanxiang:model-view') === 'list' ? 'list' : 'cards';
        }
        catch {
            return 'cards';
        }
    });
    const [search, setSearch] = useState(''), [sort, setSort] = useState('updated'), [page, setPage] = useState(1);
    const { busy, run } = useAction();
    const { modal } = App.useApp();
    const models = useMemo(() => { const term = search.trim().toLocaleLowerCase(); return [...(q.data ?? [])].filter(r => !term || [r.name, r.code ?? r.code_norm, r.domain ?? r.domain_name].join(' ').toLocaleLowerCase().includes(term)).sort((a, b) => sort === 'name' ? String(a.name).localeCompare(String(b.name), 'zh-CN') : String(b.updated_at ?? b.updatedAt ?? b.created_at ?? '').localeCompare(String(a.updated_at ?? a.updatedAt ?? a.created_at ?? ''))); }, [q.data, search, sort]);
    useEffect(() => setPage(p => Math.min(p, Math.max(1, Math.ceil(models.length / 9)))), [models.length]);
    const changeView = (v: string | number) => {
        const next = v === 'list' ? 'list' : 'cards';
        setView(next);
        try {
            localStorage.setItem('wanxiang:model-view', next);
        }
        catch { }
    };
    const open = async (r: Row) => { await refreshStudio(); await useStudio.getState().switchDiagram(r.id); };
    const copy = (r: Row) => run(() => api('models', { method: 'POST', body: { name: r.name + ' 副本', code: `model-${crypto.randomUUID().slice(0, 8)}`, domain: r.domain ?? r.domain_name, layout: r.layout ?? r.layout_json, manifest: r.manifest ?? r.snapshotManifest ?? r.snapshot_manifest_json } }), '模型已复制，关系语义仍在工作区共享');
    const remove = (r: Row) => modal.confirm({ title: '删除这个模型视图？', content: '只移除当前视图，不删除元数据、字段和工作区关系。', okText: '删除视图', okButtonProps: { danger: true }, onOk: () => api(`models/${r.id}`, { method: 'DELETE', version: versionOf(r) }) });
    const actions = (r: Row) => <Space wrap className="row-actions"><Button type="link" disabled={busy} onClick={() => run(() => open(r), '')}>全屏设计</Button>{canWrite() && <><Button type="text" aria-label={`编辑模型 ${r.name}`} icon={<EditOutlined />} onClick={() => setEdit(r)}/><Button type="text" aria-label={`复制模型 ${r.name}`} disabled={busy} icon={<CopyOutlined />} onClick={() => copy(r)}/><Button type="text" danger aria-label={`删除模型 ${r.name}`} icon={<DeleteOutlined />} onClick={() => remove(r)}/></>}</Space>;
    return <Page title="数据模型" description="用清晰的关系图连接业务。跨来源组织数据表，在全屏工作台中维护共享关系；模型布局保存在当前账号的本地浏览器。" actions={<><Segmented aria-label="模型显示方式" value={view} onChange={changeView} options={[{ value: 'cards', icon: <AppstoreOutlined />, label: '卡片' }, { value: 'list', icon: <UnorderedListOutlined />, label: '列表' }]}/>{canWrite() && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEdit(null)}>创建模型</Button>}</>}>
 {view === 'list' ? <DataTable rows={q.data} loading={q.loading} error={q.error} onRefresh={q.refresh} columns={[{ title: '模型名称', dataIndex: 'name', render: (n, r) => <Button type="link" onClick={() => run(() => open(r), '')}>{n}</Button> }, { title: '模型代码', render: (_, r) => r.code ?? r.code_norm }, { title: '业务域', render: (_, r) => r.domain ?? r.domain_name ?? '未分类' }, { title: '实体数', render: (_, r) => (r.layout?.nodes ?? r.layout_json?.nodes ?? []).length }, timeColumn, { title: '操作', fixed: 'right', width: 220, render: (_, r) => actions(r) }]}/> : <>
 <ErrorNotice error={q.error} retry={q.refresh}/><div className="wx-model-toolbar"><Input allowClear aria-label="搜索模型" prefix={<SearchOutlined />} placeholder="搜索模型名称、代码或业务域…" value={search} onChange={e => { setSearch(e.target.value); setPage(1); }}/><span className="table-count">{models.length} 个模型</span><span className="grow"/><Select aria-label="模型排序" value={sort} onChange={setSort} options={[{ value: 'updated', label: '最近更新' }, { value: 'name', label: '名称顺序' }]}/><Button onClick={q.refresh} loading={q.loading}>刷新</Button></div>
 {q.loading && !q.data ? <div className="wx-model-grid">{[1, 2, 3].map(k => <div key={k} className="wx-model-card"><Skeleton active/></div>)}</div> : models.length ? <div className="wx-model-grid">{models.slice((page - 1) * 9, page * 9).map(r => { const nodes = r.layout?.nodes ?? r.layout_json?.nodes ?? []; return <article key={r.id} className="wx-model-card"><button className="model-preview-button" aria-label={`全屏打开 ${r.name}`} onClick={() => run(() => open(r), '')} disabled={busy}><ModelThumbnail model={r}/></button><div className="model-card-body"><div className="model-card-title"><ModuleIcon name="models"/><button onClick={() => run(() => open(r), '')}>{r.name}</button></div><p className="model-code" title={r.code ?? r.code_norm}>{r.code ?? r.code_norm ?? '—'}</p><div className="model-card-meta"><Tag>{r.domain ?? r.domain_name ?? '未分类'}</Tag><span>{nodes.length} 个实体</span></div></div><footer><small>{date(r.updated_at ?? r.updatedAt ?? r.created_at)}</small>{actions(r)}</footer></article>; })}</div> : <div className="wx-model-empty"><Empty description={search ? '没有匹配的模型' : '还没有数据模型'} image={Empty.PRESENTED_IMAGE_SIMPLE}/>{search ? <Button onClick={() => setSearch('')}>清除搜索</Button> : canWrite() && <Button type="primary" onClick={() => setEdit(null)}>创建第一个模型</Button>}</div>}
 {models.length > 9 && <Pagination className="model-pagination" current={page} pageSize={9} total={models.length} showSizeChanger={false} onChange={setPage}/>}</>}
 <EditorModal open={edit !== undefined} title={edit ? '编辑模型信息' : '创建数据模型'} initial={edit ? { ...edit, code: edit.code ?? edit.code_norm, domain: edit.domain ?? edit.domain_name } : { code: `model-${crypto.randomUUID().slice(0, 8)}`, domain: '未分类' }} onCancel={() => setEdit(undefined)} onSave={v => api(edit ? `models/${edit.id}` : 'models', { method: edit ? 'PUT' : 'POST', version: edit ? versionOf(edit) : undefined, body: edit ? { ...edit, name: v.name, code: v.code, domain: v.domain, description: v.description } : v })}><Form.Item name="name" label="模型名称" rules={required}><Input maxLength={128}/></Form.Item><Form.Item name="code" label="唯一代码" rules={required}><Input maxLength={64}/></Form.Item><Form.Item name="domain" label="业务域"><Input maxLength={128}/></Form.Item><Form.Item name="description" label="说明"><Input.TextArea rows={3}/></Form.Item><Alert type="info" showIcon title="在模型中关联数据，不修改源库" description="创建后选择已采集的表。关系定义使用共享后端；模型名称、业务域及布局按租户和账号保存在本地浏览器，不支持跨设备同步。"/></EditorModal>
 </Page>;
}
