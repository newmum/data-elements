import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Drawer, Form, Input, Select, Space, Table, Tag } from 'antd';
import { ArrowRightOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { SourceIcon } from '../../design/Visuals';
import { sourceTablesPage, type SourceTablePage } from '../../services/datasources';
import { goCenter } from '../../shared/centers/navigation';
import { resourceGet } from '../services/transport';
import { Hero, Panel, Metric, PTable, StateTag, NameCell, EditDialog, More, required, PsIcon, stackArt, timeText, useCommand } from '../components/ui';
import { useResource } from '../services/context';
import type { Binding, Layer, Warehouse } from '../domain/types';

const layerDescriptions: Record<string, string> = {
  DST: '登记业务系统与原始数据来源，保留源头结构。',
  ODS: '汇集贴源数据，为后续清洗和加工提供输入。',
  DWD: '沉淀明细数据，统一字段和业务口径。',
  DWM: '按主题整合明细，形成可复用的数据模型。',
  DWS: '提供汇总和服务数据，支持业务使用。',
  ODAS: '组织分析与应用服务所需的数据。',
};
const layerPurpose = (layer: Layer) => layer.purpose || layerDescriptions[layer.code] || '按业务用途规划数据层级。';
const tableCount = (value: number | undefined) => Number(value || 0).toLocaleString('zh-CN');

export function LayersPage() {
  const { data, act, refresh } = useResource();
  const state = data.state;
  const navigate = useNavigate();
  const { modal } = App.useApp();
  const { run } = useCommand();
  const [edit, setEdit] = useState<Layer | { id: '' } | null>(null);
  const sorted = [...state.layers].sort((a, b) => a.order - b.order);
  return <div className="ps-page">
    <Hero page="layers" actions={<Button type="primary" icon={<PlusOutlined />} onClick={() => setEdit({ id: '' })}>新增分层</Button>} />
    <Panel title="数仓分层体系"><div className="ps-layer-cards">{sorted.map((layer, index) =>
      <button className={`layer-${index % 5}`} key={layer.id} onClick={() => navigate(`/resource/planning/databases?layer=${layer.id}`)}>
        <span className="ps-layer-symbol"><PsIcon name="layers" /></span>
        <div><strong>{layer.name} <Tag bordered={false}>{layer.code}</Tag></strong>
          <p>{layerPurpose(layer)}</p>
          {layer.sourceId && <small>{layer.sourceName || '已关联数据源'} · {tableCount(layer.tableCount)} 张已采集表</small>}
        </div>
      </button>)}</div></Panel>
    <div className="ps-metrics">
      <Metric label="已规划分层" value={sorted.length} note="共享字典中的数仓层" icon="layers" />
      <Metric label="层下分库" value={state.databases.length} note="已维护的逻辑分域" icon="warehouse" tone="blue" />
      <Metric label="已绑定数据源" value={state.bindings.length + sorted.filter(layer => layer.sourceId).length} note="分层与分库的数据源关联" icon="bindings" />
      <Metric label="已登记仓库资源" value={state.resources.filter(resource => resource.state === 'ACTIVE').length} note="已挂接的技术资源" icon="warehouse" tone="blue" />
    </div>
    <div className="ps-planning-grid">
      <Panel title="分层架构视图"><div className="ps-architecture"><div className="ps-flow-caption">数据消费 ↑<span>业务应用</span><i /><span>数据生产</span>源头采集</div><img src={stackArt} alt="数仓分层示意" /><div>{sorted.map((layer, index) =>
        <button key={layer.id} onClick={() => navigate(`/resource/planning/databases?layer=${layer.id}`)}><i className={`layer-dot layer-${index % 5}`} /><b>{layer.name}</b><span>{state.databases.filter(domain => domain.layerId === layer.id).length} 个分库</span></button>)}</div></div></Panel>
      <Panel title="层级说明"><div className="ps-layer-descriptions">{sorted.map(layer =>
        <article key={layer.id}><b>{layer.name} <small>({layer.code})</small></b><p>{layerPurpose(layer)}</p></article>)}</div></Panel>
      <Panel title="规划建议"><ol className="ps-advice"><li><b>先明确层级用途</b><p>每一层负责不同的数据加工阶段。</p></li><li><b>在层下维护分库</b><p>分库是逻辑分域，可关联已有数据源。</p></li><li><b>核对已采集表</b><p>绑定数据源后查看实际表清单，再开展建模。</p></li></ol></Panel>
    </div>
    <PTable title="分层定义列表" rows={sorted} onRefresh={refresh} columns={[
      { title: '分层名称', dataIndex: 'name', render: (value, row) => <NameCell name={value} code={row.code} /> },
      { title: '功能定位', key: 'purpose', width: 260, render: (_, row) => layerPurpose(row) },
      { title: '分库数量', key: 'count', render: (_, row) => state.databases.filter(domain => domain.layerId === row.id).length },
      { title: '关联数据源', key: 'source', sorter: (left, right) => (left.sourceName || '').localeCompare(right.sourceName || '', 'zh-CN'), render: (_, row) => row.sourceName || '未配置' },
      { title: '已采集表', key: 'tables', render: (_, row) => row.sourceId ? tableCount(row.tableCount) : '—' },
      { title: '状态', key: 'state', render: (_, row) => <StateTag state={row.state} /> },
      { title: '最近更新', dataIndex: 'updatedAt', render: timeText },
      { title: '操作', key: 'ops', render: (_, row) => <Space>
        <Button type="link" onClick={() => navigate(`/resource/planning/bindings?layer=${row.id}`)}>配置</Button>
        <More items={[
          { label: '编辑分层', onClick: () => setEdit(row) },
          { label: '查看层下分库', onClick: () => navigate(`/resource/planning/databases?layer=${row.id}`) },
          { label: '上移分层', disabled: row.order === 0, onClick: () => void run(() => act(`layers/${row.id}/move`, { direction: 'up' }, row.version)) },
          { label: '下移分层', disabled: row.order === sorted.length - 1, onClick: () => void run(() => act(`layers/${row.id}/move`, { direction: 'down' }, row.version)) },
          { label: '删除自定义层', disabled: row.builtIn, danger: true, onClick: () => modal.confirm({ title: '删除无引用的自定义层？', onOk: () => act(`layers/${row.id}`, {}, row.version, 'DELETE') }) },
        ]} />
      </Space> },
    ]} />
    <EditDialog open={!!edit} title={edit?.id ? '编辑分层' : '新增分层'} initial={edit ?? undefined} onClose={() => setEdit(null)}
      onSave={values => act(`layers${edit?.id ? `/${edit.id}` : ''}`, values, edit && 'version' in edit ? edit.version : undefined, edit?.id ? 'PUT' : 'POST')}>
      <div className="ps-form-grid"><Form.Item name="name" label="分层名称" rules={required}><Input maxLength={40} /></Form.Item><Form.Item name="code" label="技术代码" rules={required}><Input maxLength={32} disabled={!!edit?.id && 'builtIn' in edit && edit.builtIn} /></Form.Item></div>
      <Form.Item name="purpose" label="功能定位"><Input.TextArea rows={3} /></Form.Item><Form.Item name="owner" label="责任方"><Input /></Form.Item>
    </EditDialog>
  </div>;
}

export function DatabasesPage() {
  const { data, act, refresh } = useResource();
  const state = data.state;
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [layerId, setLayerId] = useState(params.get('layer') ?? '');
  const [edit, setEdit] = useState<Warehouse | { id: ''; layerId: string } | null>(null);
  const [dstInfo, setDstInfo] = useState<{ dbCount: number; dstTableCount: number; dst: Array<{ orgId: string; name: string; appCount: number; dbCount: number; tableCount: number }> } | null>(null);
  const [dstError, setDstError] = useState('');
  const [dstRetry, setDstRetry] = useState(0);
  const { run } = useCommand();
  const selected = state.databases.filter(domain => !layerId || domain.layerId === layerId);
  const dstSelected = state.layers.some(layer => layer.id === layerId && layer.code === 'DST');
  useEffect(() => {
    if (!dstSelected) return;
    let active = true;
    setDstError('');
    resourceGet<typeof dstInfo>('/dwm/center/dst-info').then(result => {
      if (!result || !Array.isArray(result.dst) || !Number.isFinite(Number(result.dbCount)) || !Number.isFinite(Number(result.dstTableCount))) throw new Error('来源层资源返回格式不正确');
      if (active) setDstInfo(result);
    }).catch(error => {
      if (active) setDstError(error instanceof Error ? error.message : '来源层资源读取失败');
    });
    return () => { active = false; };
  }, [dstSelected, dstRetry]);
  return <div className="ps-page">
    <Hero page="databases" actions={<Button type="primary" icon={<PlusOutlined />} disabled={!state.layers.length} onClick={() => setEdit({ id: '', layerId: layerId || state.layers[0].id })}>新增分库</Button>} />
    <div className="ps-split-page">
      <Panel title="数仓分层"><div className="ps-tree-list"><button className={!layerId ? 'selected' : ''} onClick={() => setLayerId('')}>全部分库 <b>{state.databases.length}</b></button>{state.layers.map(layer =>
        <button key={layer.id} className={layerId === layer.id ? 'selected' : ''} onClick={() => setLayerId(layer.id)}><PsIcon name="layers" />{layer.name}<b>{state.databases.filter(domain => domain.layerId === layer.id).length}</b></button>)}</div></Panel>
      <PTable rows={selected} onRefresh={refresh} columns={[
        { title: '业务分库', key: 'name', width: 210, render: (_, domain) => <NameCell name={domain.name} code={domain.code} /> },
        { title: '所属分层', key: 'layer', render: (_, domain) => state.layers.find(layer => layer.id === domain.layerId)?.name },
        { title: '关联数据源', key: 'source', sorter: (left, right) => (left.sourceName || '').localeCompare(right.sourceName || '', 'zh-CN'), render: (_, domain) => domain.sourceName || '未配置' },
        { title: '已采集表', key: 'tables', render: (_, domain) => domain.sourceId ? tableCount(domain.tableCount) : '—' },
        { title: '业务域', dataIndex: 'domain' },
        { title: '管理模式', key: 'mode', render: (_, domain) => <StateTag state={domain.mode} /> },
        { title: '仓库资源', key: 'count', render: (_, domain) => state.resources.filter(resource => resource.databaseId === domain.id).length },
        { title: '状态', key: 'state', render: (_, domain) => <StateTag state={domain.state} /> },
        { title: '操作', key: 'ops', render: (_, domain) => <Space><Button type="link" onClick={() => navigate(`/resource/planning/bindings?database=${domain.id}`)}>配置</Button><More items={[
          { label: '编辑分库', onClick: () => setEdit(domain) },
          { label: '查看仓库资源', onClick: () => navigate(`/resource/warehouse?database=${domain.id}`) },
          { label: '归档分库', danger: true, onClick: () => void run(() => act(`databases/${domain.id}/archive`, {}, domain.version)) },
        ]} /></Space> },
      ]} />
    </div>
    {dstSelected && <>
      {dstError && <Alert type="error" showIcon title={dstError} action={<Button size="small" onClick={() => setDstRetry(value => value + 1)}>重试</Button>} />}
      {dstInfo && !dstError && <><div className="ps-metrics">
        <Metric label="来源单位" value={dstInfo.dst.length} note="既有业务系统的来源分组" icon="layers" />
        <Metric label="已登记业务库" value={dstInfo.dbCount} note="元数据平台注册的数据源" icon="warehouse" tone="blue" />
        <Metric label="已采集表" value={tableCount(dstInfo.dstTableCount)} note="来源层已登记的数据表" icon="bindings" />
      </div>
      <PTable title="来源层现有资源" rows={dstInfo.dst.map(group => ({ ...group, id: group.orgId }))} columns={[
        { title: '来源单位', dataIndex: 'name', sorter: (left, right) => left.name.localeCompare(right.name, 'zh-CN') },
        { title: '应用系统', dataIndex: 'appCount' },
        { title: '业务库', dataIndex: 'dbCount' },
        { title: '已采集表', dataIndex: 'tableCount', render: tableCount },
      ]} /></>}
    </>}
    <EditDialog title={edit?.id ? '编辑分库' : '新增分库'} open={!!edit} initial={edit ?? undefined} onClose={() => setEdit(null)}
      onSave={values => act(`databases${edit?.id ? `/${edit.id}` : ''}`, values, edit && 'version' in edit ? edit.version : undefined, edit?.id ? 'PUT' : 'POST')}>
      <div className="ps-form-grid"><Form.Item name="name" label="分库名称" rules={required}><Input /></Form.Item><Form.Item name="code" label="规划代码" rules={required}><Input /></Form.Item>
        <Form.Item name="layerId" label="所属分层" rules={required}><Select options={state.layers.map(layer => ({ label: layer.name, value: layer.id }))} /></Form.Item>
        <Form.Item name="mode" label="管理模式" initialValue="MANAGED"><Select options={[{ value: 'MANAGED', label: '管理型' }, { value: 'REFERENCE', label: '外部引用型' }]} /></Form.Item>
        <Form.Item name="domain" label="业务域"><Input /></Form.Item><Form.Item name="owner" label="负责人"><Input /></Form.Item></div>
      <Form.Item name="purpose" label="用途说明"><Input.TextArea rows={3} /></Form.Item>
    </EditDialog>
  </div>;
}

interface PlanningNode {
  id: string;
  type: 'layer' | 'domain';
  targetId: string;
  layerId: string;
  name: string;
  code: string;
  sourceId: string;
  sourceName: string;
  tableCount: number;
  binding?: Binding;
  bindingVersion?: number;
  mode: 'MANAGED' | 'REFERENCE';
}

export function BindingsPage() {
  const { data, act, refresh } = useResource();
  const state = data.state;
  const [params] = useSearchParams();
  const { run } = useCommand();
  const [edit, setEdit] = useState<PlanningNode | null>(null);
  const [physical, setPhysical] = useState<PlanningNode | null>(null);
  const [preview, setPreview] = useState<PlanningNode | null>(null);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [tablePage, setTablePage] = useState<SourceTablePage | null>(null);
  const [tablesLoading, setTablesLoading] = useState(false);
  const [tableError, setTableError] = useState('');
  const [tableRetry, setTableRetry] = useState(0);
  const [form] = Form.useForm();
  const [physicalForm] = Form.useForm();
  const watchedSourceId = Form.useWatch('sourceId', form);
  const physicalCategory = Form.useWatch('category', physicalForm);
  const selectedSource = data.sources.find(source => source.id === watchedSourceId);
  const rows = useMemo<PlanningNode[]>(() => {
    const layerRows: PlanningNode[] = state.layers.map(layer => ({
      id: `layer:${layer.id}`, type: 'layer', targetId: layer.id, layerId: layer.id,
      name: layer.name, code: layer.code, sourceId: layer.sourceId || '', sourceName: layer.sourceName || '',
      tableCount: layer.tableCount || 0, bindingVersion: layer.bindingVersion,
      mode: 'REFERENCE',
    }));
    const domainRows: PlanningNode[] = state.databases.map(domain => ({
      id: `domain:${domain.id}`, type: 'domain', targetId: domain.id, layerId: domain.layerId,
      name: domain.name, code: domain.code, sourceId: domain.sourceId || '', sourceName: domain.sourceName || '',
      tableCount: domain.tableCount || 0, binding: state.bindings.find(binding => binding.databaseId === domain.id),
      bindingVersion: domain.bindingVersion, mode: domain.mode,
    }));
    const databaseId = params.get('database');
    const layerId = params.get('layer');
    if (databaseId) return domainRows.filter(row => row.targetId === databaseId);
    if (layerId) return [...layerRows, ...domainRows].filter(row => row.layerId === layerId);
    return [...layerRows, ...domainRows];
  }, [state.layers, state.databases, state.bindings, params]);

  useEffect(() => {
    if (!preview?.sourceId) { setTablePage(null); setTableError(''); return; }
    let active = true;
    setTablesLoading(true);
    setTableError('');
    sourceTablesPage(preview.sourceId, page, 10, keyword).then(result => {
      if (active) setTablePage(result);
    }).catch(error => {
      if (active) { setTablePage(null); setTableError(error instanceof Error ? error.message : '已采集表读取失败'); }
    }).finally(() => { if (active) setTablesLoading(false); });
    return () => { active = false; };
  }, [preview?.sourceId, page, keyword, tableRetry]);

  const showTables = (row: PlanningNode) => { setPage(1); setKeyword(''); setTablePage(null); setTableError(''); setPreview(row); };
  const openEdit = (row: PlanningNode) => setEdit(row);
  const saveBinding = async (values: Record<string, unknown>) => {
    if (!edit) return;
    await act('bindings', {
      targetType: edit.type, targetId: edit.targetId, sourceId: values.sourceId || '',
    }, edit.bindingVersion);
  };
  const savePhysical = async (values: Record<string, unknown>) => {
    if (!physical?.sourceId) return;
    const materialize = values.category === 'RELATIONAL';
    await act('bindings', {
      targetType: 'domain', targetId: physical.targetId, sourceId: physical.sourceId,
      environment: values.environment, category: values.category,
      catalog: materialize ? values.catalog || '' : '',
      schema: materialize ? values.schema || '' : '',
      prefix: materialize ? values.prefix || '' : '',
    }, physical.bindingVersion);
  };
  return <div className="ps-page">
    <Hero page="bindings" actions={<Button type="primary" icon={<ArrowRightOutlined />} onClick={() => goCenter('wanxiang', '/governance/metadata/sources')}>管理共享数据源</Button>} />
    <PTable title="分库与数据源配置" rows={rows} onRefresh={refresh} columns={[
      { title: '层级 / 分库', key: 'name', width: 220, render: (_, row) => <NameCell name={row.name} code={`${row.type === 'layer' ? '分层' : state.layers.find(layer => layer.id === row.layerId)?.code || '分库'} · ${row.code}`} /> },
      { title: '关联数据源', key: 'source', width: 260, sorter: (left, right) => left.sourceName.localeCompare(right.sourceName, 'zh-CN'), render: (_, row) => row.sourceId ? <div className="ps-source-cell"><SourceIcon engine={data.sources.find(source => source.id === row.sourceId)?.engine} small /><NameCell name={row.sourceName || data.sources.find(source => source.id === row.sourceId)?.name || '数据源已不可用'} code={data.sources.find(source => source.id === row.sourceId)?.engine} /></div> : <span className="ps-warning">未配置</span> },
      { title: '已采集表', key: 'tableCount', render: (_, row) => row.sourceId ? tableCount(row.tableCount) : '—' },
      { title: '配置方式', key: 'mode', render: (_, row) => row.type === 'layer' ? <Tag>整层关联</Tag> : <StateTag state={row.mode} /> },
      { title: '关联状态', key: 'association', render: (_, row) => !row.sourceId ? <Tag>未关联</Tag> : data.sources.some(source => source.id === row.sourceId) ? <Tag color="green">已关联</Tag> : <Tag color="error">数据源不可用</Tag> },
      { title: '物化准备', key: 'materialization', render: (_, row) => {
        if (row.type === 'layer' || row.mode === 'REFERENCE' || !row.sourceId) return '—';
        if (!row.binding || row.binding.category === 'REFERENCE') return <Tag>未启用</Tag>;
        if (!row.binding.catalog && !row.binding.schema) return <Tag>待设置目标位置</Tag>;
        return row.binding.state === 'VALID' ? <Tag color="green">已核对</Tag> : row.binding.state === 'INVALID' ? <Tag color="error">来源不可用</Tag> : <Tag color="orange">待核对</Tag>;
      } },
      { title: '操作', key: 'ops', render: (_, row) => <Space>
        <Button type="link" onClick={() => openEdit(row)}>配置</Button>
        {row.sourceId && <Button type="link" onClick={() => showTables(row)}>查看表</Button>}
        {row.type === 'domain' && row.mode === 'MANAGED' && row.sourceId && <More items={[
          { label: '设置物化目标', onClick: () => setPhysical(row) },
          ...(row.binding?.category === 'RELATIONAL' && (row.binding.catalog || row.binding.schema) ? [{ label: '核对物化配置', onClick: () => void run(() => act(`bindings/${row.binding!.id}/check`, {}, row.binding!.version), '目标位置配置已核对；未执行连接测试') }] : []),
        ]} />}
      </Space> },
    ]} />
    <EditDialog open={!!edit} title={edit ? `关联数据源 · ${edit.name}` : '关联数据源'} initial={edit ? {
      id: edit.id, sourceId: edit.sourceId || undefined,
    } : undefined} form={form} onClose={() => setEdit(null)} onSave={saveBinding}>
      <Form.Item name="sourceId" label="共享数据源"><Select allowClear showSearch optionFilterProp="label" placeholder="选择已有数据源；清空后保存可解除关联"
        options={data.sources.map(source => ({ value: source.id, label: source.name }))} /></Form.Item>
      {selectedSource && <div className="ps-source-summary"><SourceIcon engine={selectedSource.engine} /><div><b>{selectedSource.name}</b><p>{selectedSource.engine} · 已登记数据源</p></div></div>}
    </EditDialog>
    <EditDialog open={!!physical} title={physical ? `设置物化目标 · ${physical.name}` : '设置物化目标'} initial={physical ? {
      id: physical.id, environment: physical.binding?.environment || 'DEV',
      category: physical.binding?.category || 'REFERENCE', catalog: physical.binding?.catalog || '',
      schema: physical.binding?.schema || '', prefix: physical.binding?.prefix || '',
    } : undefined} form={physicalForm} onClose={() => setPhysical(null)} onSave={savePhysical}>
      <p className="ps-muted">仅设置建表时的目标位置；连接信息沿用已登记的数据源。</p>
      <div className="ps-form-grid">
        <Form.Item name="environment" label="环境" rules={required}><Select options={['DEV', 'TEST', 'PROD'].map(value => ({ value, label: value }))} /></Form.Item>
        <Form.Item name="category" label="物化用途" rules={required}><Select options={[{ label: '仅关联来源', value: 'REFERENCE' }, { label: '可物化建表', value: 'RELATIONAL' }]} /></Form.Item>
        <Form.Item name="catalog" label="目标数据库 / Catalog"><Input disabled={physicalCategory === 'REFERENCE'} placeholder="可选" /></Form.Item>
        <Form.Item name="schema" label="目标 Schema / Namespace"><Input disabled={physicalCategory === 'REFERENCE'} placeholder="可选" /></Form.Item>
        <Form.Item name="prefix" label="目标表前缀"><Input disabled={physicalCategory === 'REFERENCE'} placeholder="可选" /></Form.Item>
      </div>
    </EditDialog>
    <Drawer open={!!preview} width={620} title={preview ? `${preview.name} · 已采集数据表` : '已采集数据表'} onClose={() => setPreview(null)} destroyOnHidden>
      <Input.Search placeholder="搜索表名" allowClear onSearch={value => { setPage(1); setKeyword(value.trim()); }} />
      {tableError && <Alert style={{ marginTop: 16 }} type="error" showIcon title={tableError} action={<Button size="small" onClick={() => setTableRetry(value => value + 1)}>重试</Button>} />}
      {!tableError && <Table className="ps-planning-tables" style={{ marginTop: 16 }} rowKey="id" loading={tablesLoading} dataSource={tablePage?.rows || []} size="middle"
        columns={[{ title: '数据表', key: 'name', render: (_, row) => <NameCell name={row.displayName} code={row.name} /> },
          { title: '类型', dataIndex: 'kind', width: 80, render: value => value === 'view' ? '视图' : '表' },
          { title: '字段数', dataIndex: 'fieldCount', width: 88 }]}
        pagination={{ current: page, pageSize: 10, total: tablePage?.total || 0, showSizeChanger: false, onChange: next => setPage(next) }} />}
    </Drawer>
  </div>;
}
