import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Checkbox, Descriptions, Drawer, Form, Input, InputNumber, Modal, Select, Skeleton, Space, Table, Tag, TreeSelect } from 'antd';
import { DeleteOutlined, LinkOutlined, PlusOutlined, SafetyCertificateOutlined, SendOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { assetOperation, assetsApi, type Attachment, type MutationResult, type PrecheckResult, type PublishRequest, type ResourceMapping, type SharingPolicy, type VersionDetail, type VersionPage, type VersionSummary } from '../../../services/assets';
import { datasourceRegistrationApi, type SourceOption } from '../../../services/datasourceRegistrationApi';
import { AssetError, AssetName, AssetStatus, useAssetQuery } from '../shared';
import { assetDetailPath, assetIcon, assetKindLabels, asText, dataTypeLabel, formatAssetTime, type AssetPageResult, type AssetRef, type DataItem, type ObjectDetail } from '../registration/assetObjects';

const canManage = (detail: ObjectDetail) => detail.summary.allowedActions.some(action => ['EDIT', 'UPDATE', 'MANAGE', 'MANAGE_ATTACHMENTS', 'CREATE_VERSION', 'PUBLISH'].includes(action.toUpperCase()));
const attachmentKey = (attachment: Attachment) => `${attachment.resource.kind}:${attachment.resource.id}:${attachment.bindingRole}`;
const roleLabel: Record<string, string> = { SOURCE: '来源资源', DELIVERY: '交付资源', REFERENCE: '参考目录' };
const channelLabel: Record<string, string> = { API: 'API 调用', RESTRICTED_QUERY: '受限查询', TABLE_DISTRIBUTION: '库表分发', FILE: '文件交付' };
function departmentTree(options: SourceOption[]): { value: string; title: string; children: ReturnType<typeof departmentTree> }[] {
  return options.flatMap(option => option.value === 'ROOT' ? departmentTree(option.children || []) : [{ value: option.value, title: option.label, children: departmentTree(option.children || []) }]);
}

async function allCatalogItems(id: string): Promise<DataItem[]> {
  const first = await assetsApi.get<ObjectDetail>(`/objects/CATALOG/${encodeURIComponent(id)}`, { fieldsPage: 1, fieldsPageSize: 100 });
  const items = [...first.details.items || []]; const total = first.details.itemPage?.total ?? items.length;
  for (let page = 2; items.length < total; page++) {
    const next = await assetsApi.get<ObjectDetail>(`/objects/CATALOG/${encodeURIComponent(id)}`, { fieldsPage: page, fieldsPageSize: 100 });
    const chunk = next.details.items || []; const seen = new Set(items.map(item => item.id || item.code)); if (!chunk.length || chunk.some(item => seen.has(item.id || item.code))) throw new Error('目录数据项未完整读取，请刷新后重试'); items.push(...chunk);
  }
  return items;
}

function MappingEditor({ attachment, catalogId, onClose, onSave }: { attachment: Attachment; catalogId: string; onClose: () => void; onSave: (mappings: ResourceMapping[]) => void }) {
  const [mappings, setMappings] = useState(attachment.mappings || []);
  const query = useAssetQuery(`attachment-map:${catalogId}:${attachmentKey(attachment)}`, async () => {
    const [catalog, target] = await Promise.all([allCatalogItems(catalogId), assetsApi.get<ObjectDetail>(`/objects/${attachment.resource.kind}/${encodeURIComponent(attachment.resource.id)}`, { fieldsPage: 1, fieldsPageSize: 100 })]);
    const fields = [...target.details.fields || []]; const total = target.details.fieldPage?.total ?? fields.length;
    for (let page = 2; fields.length < total; page++) { const next = await assetsApi.get<ObjectDetail>(`/objects/${attachment.resource.kind}/${encodeURIComponent(attachment.resource.id)}`, { fieldsPage: page, fieldsPageSize: 100 }); const chunk = next.details.fields || []; const seen = new Set(fields.map(item => item.id || item.code)); if (!chunk.length || chunk.some(item => seen.has(item.id || item.code))) throw new Error('资源字段未完整读取，请刷新后重试'); fields.push(...chunk); }
    const choices = attachment.resource.kind === 'API' ? (target.details.parameters || []).filter(item => item.location === 'RESPONSE').map(item => ({ id: '', code: item.name, name: item.description || item.name })) : fields.map(item => ({ id: item.id || '', code: item.code, name: item.name }));
    return { catalog, choices };
  });
  const assign = (itemId: string, path?: string) => {
    const field = query.data?.choices.find(choice => choice.code === path);
    setMappings(previous => [...previous.filter(mapping => mapping.catalogItemId !== itemId), ...(field ? [{ catalogItemId: itemId, ...(field.id ? { resourceFieldId: field.id } : {}), resourceFieldPath: field.code, mappingKind: 'DIRECT' as const, mappingVersion: 1 }] : [])]);
  };
  return <Modal open width={850} title={`数据项映射 · ${attachment.resource.name}`} onCancel={onClose} onOk={() => onSave(mappings)} okText="保存映射" cancelText="取消" okButtonProps={{ disabled: query.loading || Boolean(query.error) }}>
    <AssetError error={query.error} retry={query.refresh}/><div className="hy-lifecycle-toolbar"><span>{query.data?.catalog.length ?? 0} 个目录数据项</span><Button disabled={!query.data} onClick={() => { const data = query.data; if (!data) return; const generated: ResourceMapping[] = []; for (const item of data.catalog) { const matches = data.choices.filter(field => field.code.toLowerCase() === item.code.toLowerCase()); if (item.id && matches.length === 1) generated.push({ catalogItemId: item.id, ...(matches[0].id ? { resourceFieldId: matches[0].id } : {}), resourceFieldPath: matches[0].code, mappingKind: 'DIRECT', mappingVersion: 1 }); } setMappings(generated); }}>按英文代码匹配</Button></div>
    <Table<DataItem> className="hy-asset-table" rowKey={item => item.id || item.code} dataSource={query.data?.catalog || []} loading={query.loading} tableLayout="fixed" pagination={{ pageSize: 10, showSizeChanger: false }} scroll={{ x: 650 }} columns={[
      { title: '目录数据项', width: 220, ellipsis: true, render: (_value, item) => <span title={`${item.name} · ${item.code}`}>{item.name}<small className="hy-name-code">{item.code}</small></span> },
      { title: '类型 / 长度', width: 145, ellipsis: true, render: (_value, item) => dataTypeLabel(item) },
      { title: '资源字段', width: 265, render: (_value, item) => <Select style={{ width: '100%' }} value={mappings.find(mapping => mapping.catalogItemId === item.id)?.resourceFieldPath} disabled={!item.id} allowClear showSearch optionFilterProp="label" placeholder="选择关联字段" options={query.data?.choices.map(field => ({ value: field.code, label: `${field.name} · ${field.code}` }))} onChange={path => assign(item.id || '', path)}/> },
    ]}/>
  </Modal>;
}

function AttachmentEditor({ detail, onClose, onSaved }: { detail: ObjectDetail; onClose: () => void; onSaved: () => void }) {
  const { message } = App.useApp(); const [attachments, setAttachments] = useState<Attachment[]>(() => (detail.details.attachments || []) as Attachment[]);
  const [kind, setKind] = useState('TABLE'), [keyword, setKeyword] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [mapping, setMapping] = useState<string>(); const operation = useRef(assetOperation());
  const options = useAssetQuery(`attachment-options:${kind}:${keyword}`, signal => assetsApi.get<AssetPageResult>('/search', { view: 'MAP', kinds: kind, keyword, pageNo: 1, pageSize: 30, sortBy: 'name', sortOrder: 'asc' }, { signal }));
  const save = async () => {
    if (busy) return;
    if (attachments.some(item => ['FILE', 'DIRECTORY'].includes(item.resourceType) && !item.resourceVersionId)) { setError('文件和参考目录必须选择固定版本'); return; }
    if (attachments.some(item => item.bindingRole === 'DELIVERY' && !item.channel)) { setError('请为交付资源选择交付方式'); return; }
    setBusy(true); setError('');
    try { const body = { expectedRevision: detail.revision, attachments: attachments.map((item, index) => ({ ...item, ordinal: index + 1 })) }; await assetsApi.put<MutationResult>(`/catalogs/${encodeURIComponent(detail.summary.object.id)}/attachments`, body, operation.current(body)); message.success('资源挂接已保存'); onSaved(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  };
  const change = (key: string, patch: Partial<Attachment>) => setAttachments(previous => previous.map(item => attachmentKey(item) === key ? { ...item, ...patch } : item));
  const add = (id: string) => {
    const row = options.data?.items.find(item => item.object.id === id); if (!row || row.object.id === detail.summary.object.id) return;
    if (attachments.some(item => item.resource.kind === row.object.kind && item.resource.id === row.object.id)) { message.info('该资源已在挂接列表中'); return; }
    setAttachments(previous => [...previous, { resource: row.object, resourceType: row.object.kind === 'CATALOG' ? 'DIRECTORY' : row.object.kind as Attachment['resourceType'], bindingRole: row.object.kind === 'CATALOG' ? 'REFERENCE' : 'SOURCE', primary: !previous.length && row.object.kind !== 'CATALOG', ordinal: previous.length + 1, resourceVersionId: row.publishedVersionId || row.object.versionId, mappings: [], status: 'ACTIVE', mappingVersion: 1 }]);
  };
  const selectedMapping = attachments.find(item => attachmentKey(item) === mapping);
  return <Drawer open width="min(980px, 100vw)" title="管理挂接资源" onClose={busy ? undefined : onClose} maskClosable={!busy} footer={<div className="hy-editor-footer"><span/><Space size={12}><Button disabled={busy} onClick={onClose}>取消</Button><Button type="primary" loading={busy} onClick={() => void save()}>保存挂接</Button></Space></div>}>
    <AssetError error={error || options.error}/><div className="hy-attachment-options"><Select aria-label="挂接资源类型" value={kind} style={{ flex: '0 0 125px' }} onChange={value => { setKind(value); setKeyword(''); }} options={['TABLE', 'API', 'CATALOG'].map(value => ({ value, label: assetKindLabels[value as AssetRef['kind']] }))}/><Select value={undefined} showSearch filterOption={false} onSearch={setKeyword} loading={options.loading} onChange={add} disabled={busy} placeholder="搜索并添加资源" options={options.data?.items.filter(item => item.object.id !== detail.summary.object.id).map(item => ({ value: item.object.id, label: `${item.object.name} · ${item.object.code || ''}` }))}/></div>
    <div className="hy-catalog-attachment-list">{attachments.map(item => <div key={attachmentKey(item)} className="hy-catalog-attachment-card"><AssetName name={item.resource.name} code={item.resource.code} icon={assetIcon(item.resource.kind)}/><Select aria-label={`资源用途 ${item.resource.name}`} value={item.bindingRole} disabled={busy || item.resourceType === 'DIRECTORY'} options={[{ value: 'SOURCE', label: '来源资源' }, { value: 'DELIVERY', label: '交付资源' }, { value: 'REFERENCE', label: '参考资源' }]} onChange={value => change(attachmentKey(item), { bindingRole: value, ...(value !== 'DELIVERY' ? { channel: undefined } : { channel: item.resourceType === 'API' ? 'API' : item.resourceType === 'FILE' ? 'FILE' : 'TABLE_DISTRIBUTION' }) })}/>{item.bindingRole === 'DELIVERY' && <Select aria-label={`交付方式 ${item.resource.name}`} value={item.channel} disabled={busy} options={(item.resourceType === 'TABLE' ? ['TABLE_DISTRIBUTION', 'RESTRICTED_QUERY'] : item.resourceType === 'API' ? ['API'] : ['FILE']).map(value => ({ value, label: channelLabel[value] }))} onChange={value => change(attachmentKey(item), { channel: value })}/>}<Checkbox checked={item.primary} disabled={busy || item.resourceType === 'DIRECTORY'} onChange={event => setAttachments(previous => previous.map(row => ({ ...row, primary: attachmentKey(row) === attachmentKey(item) ? event.target.checked : false })))}>主要</Checkbox>{['TABLE', 'API'].includes(item.resourceType) && <Button disabled={busy} onClick={() => setMapping(attachmentKey(item))}>字段映射{item.mappings.length ? ` ${item.mappings.length}` : ''}</Button>}{['FILE', 'DIRECTORY'].includes(item.resourceType) && <Tag title={item.resourceVersionId}>{item.resourceVersionId ? '已锁定版本' : '无可用版本'}</Tag>}<Button aria-label={`移除挂接 ${item.resource.name}`} danger type="text" icon={<DeleteOutlined/>} disabled={busy} onClick={() => setAttachments(previous => previous.filter(row => attachmentKey(row) !== attachmentKey(item)))}/></div>)}</div>
    {selectedMapping && <MappingEditor attachment={selectedMapping} catalogId={detail.summary.object.id} onClose={() => setMapping(undefined)} onSave={mappings => { change(mapping!, { mappings }); setMapping(undefined); }}/>} 
  </Drawer>;
}

function VersionDrawer({ catalogId, versionId, onClose }: { catalogId: string; versionId: string; onClose: () => void }) {
  const { message } = App.useApp(); const navigate = useNavigate(); const [busy, setBusy] = useState(false), [error, setError] = useState(''), [check, setCheck] = useState<PrecheckResult>(), [reason, setReason] = useState('');
  const operation = useRef(assetOperation());
  const query = useAssetQuery(`catalog-version:${catalogId}:${versionId}`, signal => assetsApi.get<VersionDetail>(`/catalogs/${encodeURIComponent(catalogId)}/versions/${encodeURIComponent(versionId)}`, {}, { signal }));
  const version = query.data?.version;
  const precheck = async () => {
    if (!version || busy) return; setBusy(true); setError(''); setCheck(undefined);
    try { const body = { expectedRevision: version.revision }; const result = await assetsApi.post<PrecheckResult>(`/versions/${encodeURIComponent(versionId)}/precheck`, body, operation.current(body)); setCheck(result); message[result.passed ? 'success' : 'warning'](result.passed ? '发布预检通过' : '发布预检未通过，请处理检查项'); query.refresh(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  };
  const publish = async () => {
    if (!version || busy || !check?.passed) return;
    if (!reason.trim()) { setError('请填写发布说明'); return; }
    if (new Date(check.expiresAt).getTime() <= Date.now()) { setError('预检结果已过期，请重新执行预检'); return; }
    setBusy(true); setError('');
    try { const body = { expectedRevision: check.versionRevision, checkId: check.checkId, checkHash: check.checkHash, reason: reason.trim() }; const result = await assetsApi.post<PublishRequest>(`/versions/${encodeURIComponent(versionId)}/publish-requests`, body, operation.current(body)); message.success(result.status === 'APPROVED' ? '发布申请已审批通过' : '发布申请已提交'); setCheck(undefined); query.refresh(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  };
  const editable = version && ['DRAFT', 'VALIDATED'].includes(version.status) && ['NOT_SUBMITTED', 'REJECTED', 'WITHDRAWN'].includes(version.publicationRequestStatus) && query.data?.allowedActions.some(action => ['PRECHECK', 'PUBLISH'].includes(action.toUpperCase()));
  return <Drawer open title={version ? `目录版本 V${version.versionNo}` : '目录版本'} width="min(850px, 100vw)" onClose={busy ? undefined : onClose} maskClosable={!busy} footer={<div className="hy-editor-footer"><Button onClick={() => navigate('/assets/workbench?tab=todos')}>查看审批进度</Button><span/><Space size={12}><Button disabled={busy} onClick={onClose}>关闭</Button>{editable && <Button icon={<SafetyCertificateOutlined/>} loading={busy} onClick={() => void precheck()}>发布预检</Button>}{editable && check?.passed && <Button type="primary" icon={<SendOutlined/>} loading={busy} onClick={() => void publish()}>提交发布申请</Button>}</Space></div>}>
    <AssetError error={query.error || error} retry={query.refresh}/>{query.loading && !query.data ? <Skeleton active/> : query.data && <div className="hy-version-detail"><Descriptions column={2} items={[{ key: 'catalog', label: '资源目录', children: query.data.catalogName, span: 2 }, { key: 'status', label: '版本状态', children: <AssetStatus value={version?.status}/> }, { key: 'request', label: '审批状态', children: version?.publicationRequestStatus === 'NOT_SUBMITTED' ? '未提交' : <AssetStatus value={version?.publicationRequestStatus}/> }, { key: 'created', label: '创建时间', children: formatAssetTime(version?.createdAt) }, { key: 'published', label: '发布时间', children: formatAssetTime(version?.publishedAt) }, { key: 'items', label: '数据项', children: query.data.items.length }, { key: 'resources', label: '挂接资源', children: query.data.attachments.length }]}/><div><h3>版本变更</h3><Table className="hy-asset-table" size="small" rowKey="path" dataSource={query.data.changes} tableLayout="fixed" pagination={{ pageSize: 10 }} columns={[{ title: '变更内容', dataIndex: 'path', width: 220, ellipsis: true }, { title: '原值', dataIndex: 'before', width: 180, ellipsis: true, render: asText }, { title: '新值', dataIndex: 'after', width: 180, ellipsis: true, render: asText }, { title: '影响', dataIndex: 'compatibility', width: 130, render: (value: string) => ({ COMPATIBLE: '兼容变更', BREAKING: '存在不兼容', REVIEW_REQUIRED: '需要核对' })[value] || value }]}/></div>{check && <div className="hy-version-checks"><Alert type={check.passed ? 'success' : 'warning'} showIcon title={check.passed ? '发布预检通过' : '以下内容需要处理'}/>{check.issues.map((issue, index) => <Alert key={`${issue.code}:${index}`} type={issue.severity === 'ERROR' ? 'error' : issue.severity === 'WARNING' ? 'warning' : 'info'} title={issue.message} action={issue.repairRoute?.startsWith('/assets/') || issue.repairRoute?.startsWith('/governance/') ? <Button onClick={() => navigate(issue.repairRoute!)}>前往处理</Button> : undefined}/>)}</div>}{editable && <Form layout="vertical"><Form.Item required label="发布说明"><Input.TextArea value={reason} onChange={event => setReason(event.target.value)} maxLength={2000} rows={3} placeholder="说明首次发布或本次升级的业务变化"/></Form.Item></Form>}</div>}
  </Drawer>;
}

export function CatalogLifecycle({ mode, detail, refresh }: { mode: 'attachments' | 'versions'; detail: ObjectDetail; refresh: () => void }) {
  const { message } = App.useApp(); const navigate = useNavigate(); const [openAttachments, setOpenAttachments] = useState(false), [openVersion, setOpenVersion] = useState<string>(), [createOpen, setCreateOpen] = useState(false);
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [page, setPage] = useState(1), [listingAction, setListingAction] = useState<'LIST' | 'OFFLINE'>(), [listingReason, setListingReason] = useState('');
  const [form] = Form.useForm(); const operation = useRef(assetOperation()); const id = detail.summary.object.id;
  const visibility = Form.useWatch(['sharingPolicy', 'visibility'], form);
  const departments = useAssetQuery(`publication-departments:${createOpen}`, () => createOpen ? datasourceRegistrationApi.choices().then(value => value.organizations) : Promise.resolve(null));
  const versions = useAssetQuery(`catalog-versions:${id}:${page}:${mode}`, signal => mode === 'versions' ? assetsApi.get<VersionPage>(`/catalogs/${encodeURIComponent(id)}/versions`, { pageNo: page, pageSize: 10, sortBy: 'createdAt', sortOrder: 'desc' }, { signal }) : Promise.resolve(null));
  useEffect(() => { if (createOpen) form.setFieldsValue({ changeReason: '', sharingPolicy: { visibility: 'DEPARTMENT', shareType: 'CONDITIONAL', previewMode: 'NONE', maxUseDays: 365, ...(detail.details.sharingPolicy && typeof detail.details.sharingPolicy === 'object' ? detail.details.sharingPolicy : {}) } }); }, [createOpen, form, detail.details.sharingPolicy]);
  const create = async () => {
    if (busy) return; let values: { changeReason: string; sharingPolicy: SharingPolicy }; try { values = await form.validateFields(); } catch { return; } setBusy(true); setError('');
    try { const body = { expectedRevision: detail.revision, ...(detail.summary.publishedVersionId ? { baseVersionId: detail.summary.publishedVersionId } : {}), ...values }; const result = await assetsApi.post<VersionSummary>(`/catalogs/${encodeURIComponent(id)}/versions`, body, operation.current(body)); if (!result.id) throw new Error('未返回目录版本，请刷新版本列表核对'); message.success('目录版本已创建'); setCreateOpen(false); setOpenVersion(result.id); versions.refresh(); refresh(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  };
  const changeListing = async () => {
    if (!listingAction || busy) return; if (!listingReason.trim()) { setError('请填写操作原因'); return; } setBusy(true); setError('');
    try { const body = { expectedRevision: detail.revision, action: listingAction, reason: listingReason.trim() }; await assetsApi.post<MutationResult>(`/catalogs/${encodeURIComponent(id)}/listing-transition`, body, operation.current(body)); message.success(listingAction === 'LIST' ? '目录已上架' : '目录已下架'); setListingAction(undefined); refresh(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  };
  if (mode === 'attachments') return <><div className="hy-lifecycle-toolbar"><span>{detail.details.attachments?.length || 0} 项资源</span>{canManage(detail) && <Button icon={<LinkOutlined/>} type="primary" onClick={() => setOpenAttachments(true)}>管理挂接</Button>}</div><Table<Attachment> className="hy-asset-table" rowKey={attachmentKey} tableLayout="fixed" dataSource={(detail.details.attachments || []) as Attachment[]} scroll={{ x: 690 }} columns={[{ title: '资源名称', width: 280, ellipsis: true, render: (_value, item) => <AssetName name={item.resource.name} code={item.resource.code} icon={assetIcon(item.resource.kind)} onClick={() => navigate(assetDetailPath(item.resource))}/> }, { title: '用途', width: 105, render: (_value, item) => roleLabel[item.bindingRole] }, { title: '交付方式', width: 115, render: (_value, item) => item.channel ? channelLabel[item.channel] : '—' }, { title: '资源状态', width: 110, render: (_value, item) => <AssetStatus value={item.resourceAvailability || 'AVAILABLE'}/> }, { title: '操作', width: 100, align: 'center', className: 'wx-action-column', fixed: 'right', render: (_value, item) => <Button type="link" onClick={() => navigate(assetDetailPath(item.resource))}>详情</Button> }]}/>{openAttachments && <AttachmentEditor detail={detail} onClose={() => setOpenAttachments(false)} onSaved={() => { setOpenAttachments(false); refresh(); }}/>}</>;
  return <><AssetError error={versions.error || (!createOpen && !listingAction ? error : undefined)} retry={versions.refresh}/><div className="hy-lifecycle-toolbar"><span>版本发布后保留历史快照</span>{canManage(detail) && <Space size={12}>{detail.summary.publishedVersionId && <Button onClick={() => { setError(''); setListingReason(''); setListingAction(detail.summary.listingStatus === 'LISTED' ? 'OFFLINE' : 'LIST'); }}>{detail.summary.listingStatus === 'LISTED' ? '下架目录' : '上架目录'}</Button>}<Button type="primary" icon={<PlusOutlined/>} onClick={() => { setError(''); setCreateOpen(true); }}>{detail.summary.publishedVersionId ? '创建升级版本' : '创建发布版本'}</Button></Space>}</div><Table<VersionSummary> rowKey="id" className="hy-asset-table" tableLayout="fixed" loading={versions.loading} dataSource={versions.data?.items || []} scroll={{ x: 700 }} pagination={{ current: page, pageSize: 10, total: versions.data?.page.total || 0, onChange: setPage, showSizeChanger: false }} columns={[
    { title: '版本', width: 110, render: (_value, row) => <Space>V{row.versionNo}{row.isCurrent && <Tag color="gold">当前</Tag>}</Space> },
    { title: '版本状态', width: 120, render: (_value, row) => <AssetStatus value={row.status}/> },
    { title: '发布申请', width: 135, render: (_value, row) => row.publicationRequestStatus === 'NOT_SUBMITTED' ? '未提交' : <AssetStatus value={row.publicationRequestStatus}/> },
    { title: '创建时间', width: 160, sorter: (a, b) => a.createdAt.localeCompare(b.createdAt), showSorterTooltip: { title: '按当前页时间排序' }, render: (_value, row) => formatAssetTime(row.createdAt) },
    { title: '发布时间', width: 160, sorter: (a, b) => (a.publishedAt || '').localeCompare(b.publishedAt || ''), showSorterTooltip: { title: '按当前页时间排序' }, render: (_value, row) => formatAssetTime(row.publishedAt) },
    { title: '操作', width: 110, fixed: 'right', align: 'center', className: 'wx-action-column', render: (_value, row) => <Button type="link" onClick={() => setOpenVersion(row.id)}>查看版本</Button> },
  ]}/>
    <Modal open={createOpen} title="创建目录发布版本" width={650} onCancel={() => !busy && setCreateOpen(false)} onOk={() => void create()} confirmLoading={busy} okText="创建版本" cancelText="取消"><AssetError error={error}/><Form form={form} layout="vertical"><Form.Item name="changeReason" label="版本说明" rules={[{ required: true, whitespace: true, message: '请填写版本说明' }]}><Input.TextArea rows={3} maxLength={2000}/></Form.Item><Form.Item name={['sharingPolicy', 'visibility']} label="可发现范围"><Select options={[{ value: 'DEPARTMENT', label: '本部门' }, { value: 'AUTHORIZED_ORGS', label: '指定部门' }, { value: 'TENANT', label: '当前工作空间' }]}/></Form.Item>{visibility === 'AUTHORIZED_ORGS' && <><AssetError error={departments.error} retry={departments.refresh}/><Form.Item name={['sharingPolicy', 'allowedDepartmentIds']} label="可见部门" rules={[{ required: true, message: '请选择可见部门' }]}><TreeSelect treeData={departmentTree(departments.data || [])} treeCheckable showSearch treeNodeFilterProp="title" loading={departments.loading}/></Form.Item></>}<Form.Item name={['sharingPolicy', 'shareType']} label="共享类型"><Select options={[{ value: 'CONDITIONAL', label: '有条件共享' }, { value: 'UNCONDITIONAL', label: '无条件共享' }, { value: 'PROHIBITED', label: '不予共享' }]}/></Form.Item><Form.Item name={['sharingPolicy', 'previewMode']} label="数据预览"><Select options={[{ value: 'NONE', label: '不开放预览' }, { value: 'MASKED_SAMPLE', label: '允许脱敏预览' }]}/></Form.Item><Form.Item name={['sharingPolicy', 'maxUseDays']} label="最长使用天数"><InputNumber min={1} max={3650} precision={0} style={{ width: '100%' }}/></Form.Item><Form.Item name={['sharingPolicy', 'useConditions']} label="使用条件"><Input.TextArea rows={2} maxLength={2000}/></Form.Item></Form></Modal>
    <Modal open={Boolean(listingAction)} title={listingAction === 'LIST' ? '上架目录' : '下架目录'} onCancel={() => !busy && setListingAction(undefined)} onOk={() => void changeListing()} confirmLoading={busy} okText="确认" cancelText="取消"><AssetError error={error}/><Form layout="vertical"><Form.Item label="操作原因" required><Input.TextArea rows={3} value={listingReason} onChange={event => setListingReason(event.target.value)} maxLength={2000}/></Form.Item></Form></Modal>
    {openVersion && <VersionDrawer catalogId={id} versionId={openVersion} onClose={() => setOpenVersion(undefined)}/>} 
  </>;
}
