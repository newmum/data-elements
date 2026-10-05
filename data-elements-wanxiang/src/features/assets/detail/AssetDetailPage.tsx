import { useEffect, useRef, useState } from 'react';
import { App, Button, Descriptions, Empty, Form, Input, Skeleton, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import { ArrowLeftOutlined, ArrowRightOutlined, EditOutlined, KeyOutlined, ReloadOutlined, StarFilled, StarOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { assetOperation, assetsApi, type ApiDebugResult, type PreviewResult } from '../../../services/assets';
import { AssetError, AssetName, AssetPage, AssetPanel, AssetStatus, useAssetQuery } from '../shared';
import { assetDetailPath, assetIcon, assetKind, assetKindLabels, asText, assertAssetDetail, dataTypeLabel, formatAssetTime, kindRoutes, type ApiParameter, type AssetAttachment, type AssetKind, type AssetRef, type DataItem, type ObjectDetail } from '../registration/assetObjects';
import { CatalogLifecycle } from './CatalogLifecycle';
import './detail.css';

const can = (detail: ObjectDetail, ...actions: string[]) => detail.summary.allowedActions?.some(action => actions.includes(action.toUpperCase()));
const propertyLabels: Record<string, string> = { systemCode: '系统编码', businessScope: '业务范围', serviceAudience: '服务对象', operatorName: '运营单位', managementDepartmentId: '管理部门编码', systemType: '系统类型', deploymentMode: '部署方式', constructionStatus: '建设状态', publicServiceAddress: '服务地址', engine: '数据库类型', databaseName: '数据库名称', lastCollectedAt: '最近采集时间', tableCount: '已采集表', physicalName: '物理表名', schemaName: 'Schema', fieldCount: '字段数', structureVersionId: '当前结构版本', catalogCode: '目录编码', versionId: '当前发布版本', serviceCode: '服务编码', version: '服务版本', method: '请求方法', publicEndpoint: '服务地址', responseDescription: '返回说明', authScheme: '鉴权方式', filename: '文件名称', mimeType: '文件类型', sizeBytes: '文件大小（字节）' };
const detailKeys: Record<AssetKind, string[]> = { APPLICATION: ['systemCode', 'businessScope', 'serviceAudience', 'operatorName'], DATASOURCE: ['engine', 'databaseName', 'tableCount', 'lastCollectedAt'], TABLE: ['physicalName', 'schemaName', 'fieldCount', 'structureVersionId'], CATALOG: ['catalogCode', 'versionId'], API: ['serviceCode', 'version', 'method', 'publicEndpoint', 'authScheme', 'responseDescription'], FILE: ['filename', 'mimeType', 'sizeBytes'] };

function FieldsTable({ items, total, page, onPage }: { items: DataItem[]; total: number; page: number; onPage: (page: number) => void }) {
  return <Table<DataItem> className="hy-asset-table" rowKey={item => item.id || item.code} tableLayout="fixed" dataSource={items} scroll={{ x: 760 }} pagination={{ current: page, pageSize: 30, total, onChange: onPage, showSizeChanger: false, showTotal: value => `共 ${value} 个数据项` }} columns={[
    { title: '#', width: 52, align: 'center', render: (_value, _row, index) => (page - 1) * 30 + index + 1 },
    { title: '字段名', key: 'name', width: 230, ellipsis: true, sorter: (a, b) => a.name.localeCompare(b.name, 'zh-CN'), showSorterTooltip: { title: '按当前页名称排序' }, render: (_value, item) => <div className="hy-detail-field"><strong title={item.name}>{item.name}{item.primaryKey && <Tooltip title="主键"><KeyOutlined className="hy-primary-key"/></Tooltip>}</strong><code title={item.code}>{item.code}</code></div> },
    { title: '类型 / 长度', width: 180, ellipsis: true, render: (_value, item) => <span title={dataTypeLabel(item)}>{dataTypeLabel(item)}</span> },
    { title: '可空', width: 72, align: 'center', render: (_value, item) => item.nullable ? '是' : '否' },
    { title: '数据标准', width: 180, ellipsis: true, render: (_value, item) => item.standardCode ? <span className="hy-standard-bound" title={item.standardCode}>{item.standardCode}</span> : '未关联标准' },
    { title: '业务定义', width: 200, ellipsis: true, render: (_value, item) => asText(item.definition) },
  ]}/>;
}

function AssetPreview({ object, versionId, attachments }: { object: AssetRef; versionId?: string; attachments: AssetAttachment[] }) {
  const [attachmentId, setAttachmentId] = useState(attachments.find(item => item.resource.kind === 'TABLE')?.id);
  const operation = useRef(assetOperation());
  const query = useAssetQuery(`asset-preview:${object.kind}:${object.id}:${versionId || ''}:${attachmentId || ''}`, signal => {
    const body = { object, ...(versionId ? { catalogVersionId: versionId } : {}), ...(attachmentId ? { attachmentId } : {}), limit: 10 };
    return assetsApi.post<PreviewResult>('/preview', body, { signal, ...operation.current(body) });
  });
  return <div className="hy-detail-preview"><AssetError error={query.error} retry={query.refresh}/>{attachments.filter(item => item.resource.kind === 'TABLE').length > 1 && <Space className="hy-preview-resources" wrap>{attachments.filter(item => item.resource.kind === 'TABLE').map(item => <Button key={item.id || item.resource.id} type={attachmentId === item.id ? 'primary' : 'default'} onClick={() => setAttachmentId(item.id)}>{item.resource.name}</Button>)}</Space>}<Table<Record<string, unknown>> className="hy-asset-table" size="middle" loading={query.loading} rowKey="_row" dataSource={query.data?.rows.map((row, index) => ({ _row: String(index), ...Object.fromEntries(row.cells.map(cell => [cell.itemId, cell.value])) })) || []} scroll={{ x: 'max-content' }} pagination={false} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={query.error ? '暂时无法预览数据' : '暂无可预览记录'}/> }} columns={query.data?.columns.map(column => ({ title: column.name || column.code, dataIndex: column.id || column.code, width: 160, ellipsis: true, render: (value: unknown) => value === null ? <span className="hy-value-null">NULL</span> : asText(value) })) || []}/>{query.data && <div className="hy-preview-time">{query.data.rows.length} 条数据 · {formatAssetTime(query.data.sampledAt)}{query.data.masked && <Tag color="green">已脱敏</Tag>}</div>}</div>;
}

function ApiDebug({ detail }: { detail: ObjectDetail }) {
  const { message } = App.useApp(); const [form] = Form.useForm(); const [busy, setBusy] = useState(false), [error, setError] = useState(''), [result, setResult] = useState<ApiDebugResult>();
  const operation = useRef(assetOperation()); const parameters = (detail.details.parameters || []).filter(parameter => parameter.location !== 'RESPONSE' && !/^(authorization|cookie|host)$/i.test(parameter.name));
  const execute = async () => {
    if (busy) return; let values: Record<string, string>; try { values = await form.validateFields(); } catch { return; }
    setBusy(true); setError(''); setResult(undefined);
    try { const body = { serviceVersionId: detail.summary.object.versionId || textValue(detail.details.version), parameters: parameters.map((parameter, index) => ({ location: parameter.location, name: parameter.name, value: values[`p${index}`] || '' })).filter(parameter => parameter.value), timeoutSeconds: 10 }; const response = await assetsApi.post<ApiDebugResult>(`/apis/${encodeURIComponent(detail.summary.object.id)}/debug`, body, operation.current(body)); setResult(response); message.success('接口调试已完成'); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  };
  return <div className="hy-api-debug"><Form form={form} layout="vertical">{parameters.map((parameter, index) => <Form.Item key={`${parameter.location}:${parameter.name}`} name={`p${index}`} label={`${parameter.name} · ${parameter.location}`} rules={parameter.required ? [{ required: true, message: `请输入 ${parameter.name}` }] : undefined}><Input placeholder={parameter.description || parameter.dataType}/></Form.Item>)}<Button type="primary" loading={busy} onClick={() => void execute()}>发送请求</Button></Form><AssetError error={error}/>{result && <div className="hy-api-debug-result"><Space><Tag color={result.httpStatus < 400 ? 'green' : 'red'}>HTTP {result.httpStatus}</Tag><span>{result.durationMs} ms</span><span>{formatAssetTime(result.executedAt)}</span></Space><pre>{result.responseBody}</pre></div>}</div>;
}
const textValue = (value: unknown) => value == null ? '' : String(value);

export function AssetDetailPage() {
  const { type, id = '' } = useParams(); const [params] = useSearchParams(); const navigate = useNavigate(); const { message } = App.useApp();
  const kind = assetKind(type); const [tab, setTab] = useState('info'), [fieldPage, setFieldPage] = useState(1), [favoriteBusy, setFavoriteBusy] = useState(false);
  const operation = useRef(assetOperation()); const visitKey = useRef('');
  const query = useAssetQuery(`asset-detail:${kind || ''}:${id}:${fieldPage}`, async signal => { if (!kind || !id) throw new Error('资产地址无效，请从资产地图重新进入'); return assertAssetDetail(await assetsApi.get<ObjectDetail>(`/objects/${kind}/${encodeURIComponent(id)}`, { fieldsPage: fieldPage, fieldsPageSize: 30, versionId: params.get('versionId') || undefined }, { signal }), kind, id); });
  useEffect(() => { setTab('info'); setFieldPage(1); }, [kind, id]);
  useEffect(() => {
    if (!query.data) return; const key = `${query.data.summary.object.kind}:${query.data.summary.object.id}`; if (visitKey.current === key) return; visitKey.current = key;
    void assetsApi.post('/visits', { action: 'RECORD', object: query.data.summary.object }).catch(() => { /* Browsing remains usable if optional visit history cannot be recorded. */ });
  }, [query.data]);
  const detail = query.data; const object = detail?.summary.object; const values = detail?.details;
  const favorite = async () => {
    if (!detail || favoriteBusy) return; setFavoriteBusy(true);
    try { const body = { favorited: !detail.summary.favorite }; await assetsApi.put(`/favorites/${detail.summary.object.kind}/${encodeURIComponent(detail.summary.object.id)}`, body, operation.current(body)); message.success(body.favorited ? '已收藏该资产' : '已取消收藏'); query.refresh(); }
    catch (cause) { message.error(cause instanceof Error ? cause.message : String(cause)); }
    finally { setFavoriteBusy(false); }
  };
  const related = <Table<AssetRef> rowKey={row => `${row.kind}:${row.id}`} className="hy-asset-table" tableLayout="fixed" dataSource={detail?.relatedObjects || []} scroll={{ x: 620 }} pagination={{ pageSize: 10, showSizeChanger: false }} columns={[
    { title: '资产名称', width: 300, sorter: (a, b) => a.name.localeCompare(b.name, 'zh-CN'), ellipsis: true, render: (_value, row) => <AssetName name={row.name} code={row.code} icon={assetIcon(row.kind)} onClick={() => navigate(assetDetailPath(row))}/> },
    { title: '资产类型', width: 130, render: (_value, row) => assetKindLabels[row.kind] },
    { title: '操作', width: 100, fixed: 'right', className: 'wx-action-column', align: 'center', render: (_value, row) => <Button type="link" onClick={() => navigate(assetDetailPath(row))}>查看详情</Button> },
  ]}/>;
  const infoItems = detail && kind ? [
    { key: 'name', label: `${assetKindLabels[kind]}名称`, children: detail.summary.object.name },
    { key: 'department', label: '提供部门', children: detail.summary.departmentName || '—' },
    ...(detail.summary.applicationName ? [{ key: 'application', label: '所属应用系统', children: detail.summary.applicationName }] : []),
    ...detailKeys[kind].filter(key => values?.[key] !== undefined).map(key => ({ key, label: propertyLabels[key], children: key.toLowerCase().includes('at') && key === 'lastCollectedAt' ? formatAssetTime(values?.[key]) : asText(values?.[key]) })),
    ...Object.entries(values?.technical || {}).filter(([key]) => key in propertyLabels).map(([key, value]) => ({ key, label: propertyLabels[key], children: asText(value) })),
    { key: 'updatedAt', label: '更新时间', children: formatAssetTime(detail.summary.updatedAt) },
    { key: 'description', label: '业务说明', children: detail.description || '—', span: 2 },
  ] : [];
  const fields = values?.fields || values?.items || [];
  const totalFields = values?.fieldPage?.total ?? values?.itemPage?.total ?? fields.length;
  const governancePath = kind === 'DATASOURCE' ? `/governance/metadata/sources?sourceId=${encodeURIComponent(id)}` : kind === 'TABLE' ? `/governance/metadata/catalog?tableId=${encodeURIComponent(id)}` : '/governance/overview';
  return <AssetPage title={object?.name || '资产详情'} description={object ? `${assetKindLabels[object.kind]}${object.code ? ` · ${object.code}` : ''}` : '读取资产信息'} actions={<><Button icon={<ArrowLeftOutlined/>} onClick={() => navigate('/assets/map')}>返回资产地图</Button>{detail && <Button icon={detail.summary.favorite ? <StarFilled/> : <StarOutlined/>} loading={favoriteBusy} onClick={() => void favorite()}>{detail.summary.favorite ? '已收藏' : '收藏'}</Button>}{detail && kind && ['APPLICATION', 'DATASOURCE', 'CATALOG', 'API'].includes(kind) && can(detail, 'EDIT', 'UPDATE', 'MANAGE') && <Button icon={<EditOutlined/>} onClick={() => navigate(`/assets/register/${kindRoutes[kind]}?edit=${encodeURIComponent(id)}`)}>编辑登记</Button>}{detail && kind === 'CATALOG' && detail.summary.publishedVersionId && <Button type="primary" onClick={() => navigate(`/assets/market/${encodeURIComponent(id)}`)}>查看超市目录</Button>}</>}>
    <AssetError error={query.error} retry={query.refresh}/>
    {!detail ? query.loading ? <Skeleton active paragraph={{ rows: 14 }}/> : !query.error && <Empty description="资产不存在或当前不可访问"/> : <>
      <div className="hy-detail-topline"><div>{assetIcon(detail.summary.object.kind)}<span>{detail.summary.departmentName || '未设置提供部门'}</span>{detail.summary.tags?.map(tag => <Tag key={tag}>{tag}</Tag>)}</div><span>更新于 {formatAssetTime(detail.summary.updatedAt)}</span></div>
      <div className="hy-detail-layout"><AssetPanel className="hy-detail-main"><Tabs activeKey={tab} onChange={setTab} destroyOnHidden items={[
        { key: 'info', label: '基本信息', children: <Descriptions column={{ xs: 1, sm: 2 }} layout="vertical" items={infoItems}/> },
        ...(['TABLE', 'CATALOG'].includes(kind || '') ? [{ key: 'fields', label: `${kind === 'TABLE' ? '字段结构' : '数据项'} ${totalFields}`, children: <FieldsTable items={fields} total={totalFields} page={fieldPage} onPage={setFieldPage}/> }] : []),
        { key: 'related', label: `关联资产 ${detail.relatedObjects.length}${detail.relationsHasMore ? '+' : ''}`, children: <>{related}{detail.relationsHasMore && <div className="hy-detail-related-more"><Button onClick={() => navigate(`/assets/map?kind=${kind}&id=${encodeURIComponent(id)}`)}>在地图展开全部关联</Button></div>}</> },
        ...(kind === 'CATALOG' ? [{ key: 'attachments', label: `挂接资源 ${values?.attachments?.length || 0}`, children: <CatalogLifecycle mode="attachments" detail={detail} refresh={query.refresh}/> }, { key: 'versions', label: '发布与版本', children: <CatalogLifecycle mode="versions" detail={detail} refresh={query.refresh}/> }] : []),
        ...(kind === 'API' ? [{ key: 'parameters', label: '请求与响应参数', children: <><Table<ApiParameter> className="hy-asset-table" rowKey={row => `${row.location}:${row.name}`} tableLayout="fixed" dataSource={values?.parameters || []} scroll={{ x: 720 }} columns={[
          { title: '参数名称', dataIndex: 'name', width: 200, sorter: (a, b) => a.name.localeCompare(b.name), ellipsis: true }, { title: '位置', dataIndex: 'location', width: 100 }, { title: '类型', dataIndex: 'dataType', width: 120 }, { title: '必填', width: 70, align: 'center', render: (_value, parameter) => parameter.required ? '是' : '否' }, { title: '说明', dataIndex: 'description', width: 240, ellipsis: true },
        ]}/>{values?.responseExample != null && <div className="hy-response-example"><h3>响应示例</h3><pre>{textValue(values.responseExample)}</pre></div>}</> }, ...(can(detail, 'DEBUG') ? [{ key: 'debug', label: '在线调试', children: <ApiDebug detail={detail}/> }] : [])] : []),
        ...(can(detail, 'PREVIEW') && ['TABLE', 'CATALOG'].includes(kind || '') ? [{ key: 'preview', label: '数据预览', children: <AssetPreview object={detail.summary.object} versionId={textValue(values?.versionId) || undefined} attachments={values?.attachments || []}/> }] : []),
      ]}/></AssetPanel>
      <div className="hy-detail-aside"><AssetPanel title="资产状态"><div className="hy-detail-facts"><div><span>登记状态</span><AssetStatus value="REGISTERED"/></div>{kind === 'CATALOG' && <div><span>超市状态</span><AssetStatus value={detail.summary.listingStatus || 'NOT_LISTED'}/></div>}{kind === 'DATASOURCE' && <div><span>连接状态</span><AssetStatus value={values?.connectionStatus}/></div>}{kind === 'TABLE' && <div><span>字段采集</span><AssetStatus value={values?.fieldCollectionStatus}/></div>}</div><Button block icon={<ArrowRightOutlined/>} onClick={() => navigate(`/assets/map?kind=${kind}&id=${encodeURIComponent(id)}`)}>查看资产关系</Button></AssetPanel>
        {detail.summary.governance && <AssetPanel title="治理情况" extra={<Tag color="purple">万象</Tag>}><div className="hy-detail-facts"><div><span>标准关联</span><strong>{detail.summary.governance.standardBoundFields == null ? '—' : `${detail.summary.governance.standardBoundFields} / ${detail.summary.governance.eligibleFields ?? '—'}`}</strong></div><div><span>质量检查</span><AssetStatus value={detail.summary.governance.qualityStatus}/></div>{detail.summary.governance.qualityScore != null && <div><span>质量评分</span><strong>{detail.summary.governance.qualityScore}</strong></div>}{detail.summary.governance.checkedAt && <div><span>检查时间</span><span>{formatAssetTime(detail.summary.governance.checkedAt)}</span></div>}</div>{['TABLE', 'DATASOURCE'].includes(kind || '') && <Button block onClick={() => navigate(`${governancePath}&returnTo=${encodeURIComponent(assetDetailPath(detail.summary.object))}`)}>进入数据治理 <ArrowRightOutlined/></Button>}</AssetPanel>}
        {kind === 'API' && <AssetPanel title="服务使用"><div className="hy-detail-facts">{values?.statisticsAvailable ? <><div><span>调用次数</span><strong>{asText(values.calls)}</strong></div><div><span>成功率</span><strong>{values.successRate == null ? '—' : `${values.successRate}%`}</strong></div></> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无调用统计"/>}</div></AssetPanel>}
        <Button icon={<ReloadOutlined spin={query.loading}/>} block onClick={query.refresh}>刷新资产信息</Button>
      </div></div>
    </>}
  </AssetPage>;
}
