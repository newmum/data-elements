import { useMemo, useState } from 'react';
import { ApartmentOutlined, AppstoreOutlined, ArrowRightOutlined, PlusOutlined, TableOutlined } from '@ant-design/icons';
import { App, Button, Empty, Form, Input, Radio, Select, Space, Tag } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { TechnicalEntitySelect } from '../components/TechnicalEntitySelect';
import { EditDialog, Hero, More, NameCell, PTable, required, timeText, useCommand } from '../components/ui';
import type { LogicalModel } from '../domain/types';
import { useResource } from '../services/context';
import { getSession } from '../../services/api';

const methodLabel: Record<string, string> = {
  MANUAL: '手工建模', REVERSE: '逆向解析', STANDARD: '依标建模', COPY: '派生副本',
};

export function ModelPreview({ model }: { model: LogicalModel }) {
  const box = useMemo(() => {
    const nodes = model.nodes.length ? model.nodes : model.entities.map((entity, index) => ({
      entityId: entity.id, position: { x: index % 3 * 330, y: Math.floor(index / 3) * 230 },
    }));
    const maxX = Math.max(1, ...nodes.map(node => node.position.x + 260));
    const minX = Math.min(0, ...nodes.map(node => node.position.x));
    const maxY = Math.max(1, ...nodes.map(node => node.position.y + 180));
    const minY = Math.min(0, ...nodes.map(node => node.position.y));
    return { nodes, minX, minY, width: maxX - minX, height: maxY - minY };
  }, [model]);
  const fusionLinks = [
    ...(model.fusion?.joins ?? []).map(join => ({ id: join.id, source: join.leftEntityId, target: join.rightEntityId, mapping: false })),
    ...Array.from(new Set((model.fusion?.mappings ?? []).map(mapping => mapping.sourceEntityId))).map(source => ({ id: `mapping-${source}`, source, target: model.outputEntityId ?? '', mapping: true })),
  ];

  if (!box.nodes.length) return <div className="ps-model-preview-empty"><ApartmentOutlined /><span>空白设计稿</span></div>;
  return <svg className="ps-model-preview" viewBox={`${box.minX - 20} ${box.minY - 20} ${box.width + 40} ${box.height + 40}`} preserveAspectRatio="xMidYMid meet" aria-label={`${model.name}已保存布局预览`}>
    {model.relationships.map(relation => {
      const source = box.nodes.find(node => node.entityId === relation.sourceEntityId);
      const target = box.nodes.find(node => node.entityId === relation.targetEntityId);
      return source && target ? <path key={relation.id} d={`M ${source.position.x + 260} ${source.position.y + 88} C ${source.position.x + 310} ${source.position.y + 88}, ${target.position.x - 55} ${target.position.y + 88}, ${target.position.x} ${target.position.y + 88}`} stroke="currentColor" strokeWidth="4" fill="none" opacity=".55" /> : null;
    })}
    {fusionLinks.map(link => {
      const source = box.nodes.find(node => node.entityId === link.source);
      const target = box.nodes.find(node => node.entityId === link.target);
      return source && target ? <path key={link.id} d={`M ${source.position.x + 260} ${source.position.y + 88} C ${source.position.x + 310} ${source.position.y + 88}, ${target.position.x - 55} ${target.position.y + 88}, ${target.position.x} ${target.position.y + 88}`} stroke={link.mapping ? '#4ecbc5' : '#6faaf8'} strokeWidth={link.mapping ? 3 : 4} strokeDasharray={link.mapping ? '8 5' : undefined} fill="none" opacity=".8" /> : null;
    })}
    {box.nodes.map((node, index) => <g key={node.entityId} transform={`translate(${node.position.x},${node.position.y})`}>
      <rect width="260" height="168" rx="13" fill="var(--ps-surface)" stroke={model.entities.find(entity => entity.id === node.entityId)?.role === 'OUTPUT' ? '#4ecbc5' : '#6faaf8'} strokeWidth="3" />
      <rect width="260" height="35" rx="10" fill={model.entities.find(entity => entity.id === node.entityId)?.role === 'OUTPUT' ? '#d9f5f0' : index % 2 ? '#d9f5f0' : '#e2efff'} />
      {[62, 88, 114, 140].map(y => <g key={y}>
        <rect x="16" y={y} width="10" height="8" rx="2" fill="#38b8c3" />
        <rect x="40" y={y} width="100" height="7" rx="3" fill="#cad8e6" />
        <rect x="192" y={y} width="40" height="7" rx="3" fill="#e1e9f0" />
      </g>)}
    </g>)}
  </svg>;
}

export function ModelsPage() {
  const { data, act, refresh } = useResource();
  const state = data.state;
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { modal } = App.useApp();
  const { run, busy } = useCommand();
  const [creating, setCreating] = useState(() => params.get('create') === '1');
  const [form] = Form.useForm();
  const method = Form.useWatch('method', form) ?? 'REVERSE';
  const targetLayerId = Form.useWatch('layerId', form) as string | undefined;
  const sourceId = Form.useWatch('sourceId', form) as string | undefined;
  const [layout, setLayout] = useState<'cards' | 'list'>('cards');
  const [search, setSearch] = useState('');
  const [layerFilter, setLayerFilter] = useState<string>();
  const [databaseFilter, setDatabaseFilter] = useState<string | undefined>(params.get('database') || undefined);

  const activeLayers = state.layers.filter(layer => layer.state === 'ACTIVE');
  const activeDatabases = state.databases.filter(database => database.state === 'ACTIVE' && activeLayers.some(layer => layer.id === database.layerId));
  const databasesById = new Map(state.databases.map(database => [database.id, database]));
  const layersById = new Map(state.layers.map(layer => [layer.id, layer]));
  const initialDatabase = activeDatabases.find(database => database.id === params.get('database'));
  const models = state.models.filter(model => {
    if (model.state !== 'ACTIVE' || databaseFilter && model.databaseId !== databaseFilter) return false;
    const database = model.databaseId ? databasesById.get(model.databaseId) : undefined;
    if (layerFilter && database?.layerId !== layerFilter) return false;
    return `${model.name} ${model.code}`.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase());
  });
  const sourceOptions = data.sources.filter(source => source.id && source.name).map(source => ({
    value: String(source.id), label: `${source.name} · ${source.engine || '数据源'}`,
  }));
  const actionItems = (model: LogicalModel) => [
    { label: '模型标准化', onClick: () => navigate('/resource/models/standardization?model=' + model.id) },
    { label: '查看过程日志', onClick: () => navigate('/resource/models/logs?model=' + model.id) },
    { label: '创建副本', onClick: () => void run(() => act(`models/${model.id}/copy`, { name: `${model.name} 副本`, code: `${model.code.slice(0, 48)}_COPY_${Date.now().toString(36).toUpperCase()}` }, model.version)) },
    { label: '物化建表', disabled: !model.frozen.length, onClick: () => navigate('/resource/materializations?model=' + model.id) },
    { label: '归档模型', danger: true, onClick: () => modal.confirm({ title: '归档此设计模型？', content: '保留历史版本与已建资源，不删除技术表。', onOk: () => act(`models/${model.id}/archive`, {}, model.version) }) },
  ];
  const locationOf = (model: LogicalModel) => {
    const database = model.databaseId ? databasesById.get(model.databaseId) : undefined;
    const layer = database ? layersById.get(database.layerId) : undefined;
    return { database, layer };
  };

  return <>
    <Hero page="models" actions={<Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>新建逻辑模型</Button>} />
    <div className="ps-model-controls">
      <Input.Search placeholder="搜索模型名称或代码" allowClear value={search} onChange={event => setSearch(event.target.value)} />
      <Select placeholder="全部分层" allowClear showSearch optionFilterProp="label" value={layerFilter} options={state.layers.map(layer => ({ value: layer.id, label: `${layer.name} · ${layer.code}` }))} onChange={value => { setLayerFilter(value); setDatabaseFilter(undefined); }} />
      <Select placeholder="全部分库" allowClear showSearch optionFilterProp="label" value={databaseFilter} options={state.databases.filter(database => !layerFilter || database.layerId === layerFilter).map(database => ({ value: database.id, label: database.name }))} onChange={setDatabaseFilter} />
      <span className="ps-muted">{models.length} 个模型</span><span className="ps-grow" />
      <Radio.Group value={layout} onChange={event => setLayout(event.target.value)} optionType="button"><Radio.Button value="cards"><AppstoreOutlined /></Radio.Button><Radio.Button value="list"><TableOutlined /></Radio.Button></Radio.Group>
    </div>
    {layout === 'cards' ? <div className="ps-model-grid">
      {models.map(model => {
        const { layer, database } = locationOf(model);
        return <article className="ps-model-card" key={model.id}>
          <header><div><h2>{model.name}</h2><small>{model.code}</small></div><More items={actionItems(model)} /></header>
          <button className="ps-model-open" onClick={() => navigate(`/resource/models/${model.id}/designer`)}><ModelPreview model={model} /><span>打开全屏设计器 <ArrowRightOutlined /></span></button>
          <div className="ps-model-info"><Tag color="blue">{layer?.name || '未指定分层'}</Tag><Tag>{database?.name || '未指定分库'}</Tag><Tag>{methodLabel[model.method]}</Tag><small>{model.entities.length} 实体 · {model.entities.reduce((count, entity) => count + entity.fields.length, 0)} 字段</small></div>
          <footer><span>{model.frozen.length ? `冻结 v${model.frozen.at(-1)!.number}` : '未冻结'}{model.frozen.length && JSON.stringify({ entities: model.entities, relationships: model.relationships, outputEntityId: model.outputEntityId, fusion: model.fusion }) !== JSON.stringify({ entities: model.frozen.at(-1)!.entities, relationships: model.frozen.at(-1)!.relationships, outputEntityId: model.frozen.at(-1)!.outputEntityId, fusion: model.frozen.at(-1)!.fusion }) ? ' · 有新草稿' : ''}</span><small>{timeText(model.updatedAt)}</small></footer>
        </article>;
      })}
      {!models.length && <Empty description="暂无模型，请先选择分层和分库并新建逻辑模型" />}
    </div> : <PTable rows={models} onRefresh={refresh} columns={[
      { title: '模型', render: (_, model) => <NameCell name={model.name} code={model.code} /> },
      { title: '目标分层', render: (_, model) => locationOf(model).layer?.name || '未指定' },
      { title: '目标分库', render: (_, model) => locationOf(model).database?.name || '未指定' },
      { title: '实体数', render: (_, model) => model.entities.length },
      { title: '来源', render: (_, model) => methodLabel[model.method] },
      { title: '冻结版本', render: (_, model) => model.frozen.length ? `v${model.frozen.at(-1)!.number}` : '未冻结' },
      { title: '更新时间', render: (_, model) => timeText(model.updatedAt) },
      { title: '操作', render: (_, model) => <Space><Button type="link" onClick={() => navigate(`/resource/models/${model.id}/designer`)}>全屏设计</Button><More items={actionItems(model)} /></Space> },
    ]} />}
    <EditDialog title="新建逻辑模型" open={creating} form={form} initial={{ method: 'REVERSE', layerId: initialDatabase?.layerId, databaseId: initialDatabase?.id, owner: getSession()?.name || '' }} onClose={() => setCreating(false)} onSave={async values => {
      const model = await act<LogicalModel>('models', values);
      navigate(`/resource/models/${model.id}/designer`);
    }} width={760}>
      <Form.Item name="method" label="创建方式"><Radio.Group options={[{ value: 'REVERSE', label: '逆向解析' }, { value: 'STANDARD', label: '依标建模' }, { value: 'MANUAL', label: '手工建模' }]} optionType="button" /></Form.Item>
      <div className="ps-form-two">
        <Form.Item name="name" label="模型名称" rules={required}><Input maxLength={255} placeholder="如：节目播出主题模型" /></Form.Item>
        <Form.Item name="code" label="模型代码" rules={required}><Input maxLength={63} placeholder="PROGRAM_BROADCAST" /></Form.Item>
      </div>
      <div className="ps-form-two">
        <Form.Item name="layerId" label="目标分层" rules={required}><Select showSearch optionFilterProp="label" placeholder="搜索并选择分层" options={activeLayers.map(layer => ({ value: layer.id, label: `${layer.name} · ${layer.code}` }))} onChange={() => form.setFieldsValue({ databaseId: undefined })} /></Form.Item>
        <Form.Item name="databaseId" label="目标分库" rules={required}><Select showSearch optionFilterProp="label" placeholder={targetLayerId ? '搜索当前分层中的分库' : '请先选择目标分层'} disabled={!targetLayerId} options={activeDatabases.filter(database => database.layerId === targetLayerId).map(database => ({ value: database.id, label: `${database.name} · ${database.code}` }))} /></Form.Item>
      </div>
      <Form.Item name="description" label="业务说明"><Input.TextArea rows={2} /></Form.Item>
      {method === 'REVERSE' && <>
        <Form.Item name="sourceId" label="来源数据库" rules={required}><Select showSearch optionFilterProp="label" placeholder="搜索已登记数据库" options={sourceOptions} onChange={() => form.setFieldsValue({ entityIds: undefined })} /></Form.Item>
        <Form.Item name="entityIds" label="来源数据表" rules={required}>{sourceId ? <TechnicalEntitySelect key={sourceId} sourceId={sourceId} mode="multiple" placement="topLeft" placeholder="搜索该数据库中的表，可选择多张" /> : <Select disabled placeholder="请先选择来源数据库" />}</Form.Item>
      </>}
      {method === 'STANDARD' && <Form.Item name="standardIds" label="已发布数据标准" rules={required}><Select mode="multiple" showSearch optionFilterProp="label" options={data.standards.filter(standard => standard.status === 'PUBLISHED').map(standard => ({ value: standard.id, label: `${standard.name} · ${standard.code}` }))} /></Form.Item>}
    </EditDialog>
    <span hidden>{String(busy)}</span>
  </>;
}
