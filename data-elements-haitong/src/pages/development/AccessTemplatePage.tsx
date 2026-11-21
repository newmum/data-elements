import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, InputNumber, Modal, Select, Space, Table, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
import { platformApi } from '../../api/platformApi';
import { Panel } from '../../components/Common';
import { useSessionRevision } from '../../nifi/api/bridgeSession';
import { useOceanTheme } from '../../design/theme';
import TemplateTargetSelect, { type TargetChoice } from './TemplateTargetSelect';
import AccessTemplatePreview from './AccessTemplatePreview';
import { buildTemplateFlow, parseTemplateFlow, type AccessTemplate as Template, type TemplateSpec as Spec } from './accessTemplateDefinition';

const kinds = [
  ['MYSQL', 'MySQL'], ['POSTGRESQL', 'PostgreSQL'], ['ORACLE', 'Oracle'],
  ['OCEANBASE_ORACLE', 'OceanBase Oracle'], ['OCEANBASE_MYSQL', 'OceanBase MySQL'],
  ['DM', '达梦'], ['KINGBASE', '金仓'], ['SQLSERVER', 'SQL Server'], ['HIVE', 'Hive'],
] as const;
const targetOptions = kinds.map(([value, label]) => ({ value: String(value), label: String(label) }));
const typeName = (value: string) => targetOptions.find(item => item.value === value)?.label || value;
const stateLabels = { DRAFT: '草稿', PUBLISHED: '已发布', RETIRED: '已停用' };
type FormValues = Omit<Spec, 'flow' | 'nativeSnapshot'> & { name: string };
type Change = { action: 'create' | 'save'; tid?: string; name: string; spec: Spec; expectedUpdatedAt?: number }
  | { action: 'publish' | 'retire' | 'default'; tid: string; expectedUpdatedAt?: number }
  | { action: 'bind'; tid: string; name: string; targetDatasourceIds: string[]; expectedUpdatedAt?: number };
type NativeProcessor = { identifier: string; type: string; schedulingPeriod?: string; properties?: Record<string, unknown> };
type NativeEdge = { source: { id: string }; destination: { id: string } };

const defaultSpec: Spec = {
  topology: 'SERIAL', sourceType: 'ANY', sourceSchedule: 'REGISTERED', targets: [], intervalSeconds: 60, fetchSize: 5000,
};
const errorText = (cause: unknown) => cause instanceof Error ? cause.message : '操作失败，请重试';

function durationSeconds(value: unknown): number {
  const interval = String(value ?? '').match(/^(\d+)\s*(sec|min|hour)s?$/i);
  const scale: Record<string, number> = { sec: 1, min: 60, hour: 3600 };
  return interval ? Number(interval[1]) * scale[interval[2].toLowerCase()] : 0;
}

function readNativePreset(file: string): { defaults: Partial<Spec>; summary: string } {
  const snapshot = JSON.parse(file);
  const flow = snapshot?.flowContents;
  if (!Array.isArray(flow?.processors) || !Array.isArray(flow?.connections)) {
    throw new Error('请选择从 NiFi 导出的流程定义 JSON');
  }
  const processors: NativeProcessor[] = flow.processors;
  const connections: NativeEdge[] = flow.connections;
  const nameOf = (item: NativeProcessor) => item.type?.split('.').at(-1);
  const source = processors.find(item => nameOf(item) === 'GenerateDateTableFetch');
  const hive = processors.find(item => nameOf(item) === 'PutHwHDFS');
  const relational = processors.filter(item => nameOf(item) === 'LinewellPutDatabaseRecord');
  if (!source || !hive || relational.length === 0 || flow.processGroups?.length) {
    throw new Error('此文件需要人工配置模板。当前可提取自定义并行流程中的读取参数与目标类型');
  }
  const adjacency = new Map<string, string[]>();
  for (const { source: from, destination: to } of connections) {
    if (!from?.id || !to?.id) continue;
    const next = adjacency.get(from.id) || [];
    next.push(to.id);
    adjacency.set(from.id, next);
  }
  const reachable = (start: string) => {
    const reached = new Set([start]);
    const pending = [start];
    for (let index = 0; index < pending.length; index++) {
      for (const target of adjacency.get(pending[index]) || []) {
        if (!reached.has(target)) {
          reached.add(target);
          pending.push(target);
        }
      }
    }
    return reached;
  };
  const reached = reachable(source.identifier);
  const jdbc = relational.find(item => reached.has(item.identifier));
  if (!jdbc || !reached.has(hive.identifier)) throw new Error('未找到从同一来源到达 Hive 和关系库的流程路径');
  const seconds = durationSeconds(source.schedulingPeriod);
  const fetch = Number(source.properties?.['gen-table-fetch-partition-size']);
  const merge = processors.find(item => nameOf(item) === 'MergeRecord'
    && reached.has(item.identifier) && reachable(item.identifier).has(hive.identifier));
  const mergeAge = durationSeconds(merge?.properties?.['Max Bin Age']);
  const external = Object.keys(snapshot.externalControllerServices || {}).length;
  const defaults: Partial<Spec> = { topology: 'PARALLEL', targets: ['OCEANBASE_ORACLE', 'HIVE'] };
  if (seconds >= 10 && seconds <= 86400) defaults.intervalSeconds = seconds;
  if (Number.isInteger(fetch) && fetch >= 1 && fetch <= 50000) defaults.fetchSize = fetch;
  if (mergeAge >= 1 && mergeAge <= 86400) defaults.hiveMergeMaxAgeSeconds = mergeAge;
  return {
    defaults,
    summary: '读取到 ' + processors.length + ' 个处理器、' + connections.length + ' 条连接和 ' + external
      + ' 个外部服务引用。请核对 OceanBase Oracle 和 Hive 类型。原生作业中的字段规则、组件属性和外部服务需在画布及部署节点上分别核对。',
  };
}

export default function AccessTemplatePage() {
  const revision = useSessionRevision();
  return <TemplateManager key={revision} revision={revision} />;
}

function TemplateManager({ revision }: { revision: ReturnType<typeof useSessionRevision> }) {
  const { dark } = useOceanTheme();
  const [params] = useSearchParams();
  const queryClient = useQueryClient();
  const [page, setPage] = useState({ pageNum: 1, pageSize: 20 });
  const [sorting,setSorting] = useState({sortField:'updatedAt',sortDir:'desc'});
  const [keyword, setKeyword] = useState('');
  const [includeHistory, setIncludeHistory] = useState(false);
  const [draft, setDraft] = useState<{ tid?: string; action: 'create' | 'save'; updatedAt?: number }>();
  const [viewId, setViewId] = useState<string | undefined>(() => {
    const id = params.get('version') || '';
    return /^[A-Za-z0-9_-]{1,32}$/.test(id) ? id : '';
  });
  const [flowText, setFlowText] = useState('');
  const [nativeSnapshot, setNativeSnapshot] = useState<Spec['nativeSnapshot']>();
  const [previewImage, setPreviewImage] = useState<string>();
  const [engine, setEngine] = useState<Spec['engine']>('CANVAS');
  const [binding, setBinding] = useState<Template>();
  const [bindingName, setBindingName] = useState('');
  const [bindingIds, setBindingIds] = useState<string[]>([]);
  const [definitionError, setDefinitionError] = useState('');
  const [decision, setDecision] = useState<{ action: 'publish' | 'retire'; row: Template }>();
  const [inspection, setInspection] = useState<{ message: string; failed?: boolean }>();
  const [form] = Form.useForm<FormValues>();
  const selectedTargets = Form.useWatch('targets', form) || [];
  const formTargetIds: string[] = Form.useWatch('targetDatasourceIds', form) || [];
  const selectedIds = binding ? bindingIds : formTargetIds;
  const existingChoices = useQuery({
    queryKey: ['template-selected-targets', revision, selectedIds],
    queryFn: ({ signal }) => platformApi<{ list: TargetChoice[] }>('/nifi/canvas/targets', { ids: selectedIds.filter(Boolean) }, { signal }),
    enabled: Boolean((binding || draft) && selectedIds.some(Boolean)), retry: false,
  });
  useEffect(() => {
    if (selectedTargets.length > 1 && engine !== 'GENERATED') form.setFieldValue('topology', 'PARALLEL');
  }, [selectedTargets.length, form, engine]);
  const queryKey = ['access-templates', revision];
  const templates = useQuery({
    queryKey: [...queryKey, keyword, page, includeHistory,sorting],
    queryFn: ({ signal }) => platformApi<{ list: Template[]; total: number }>('/nifi/canvas/templates',
      { ...page, keyword, manage: true, includeHistory,...sorting }, { signal }),
    retry: false,
  });
  const detail = useQuery({
    queryKey: [...queryKey, 'detail', viewId],
    queryFn: ({ signal }) => platformApi<Template>('/nifi/canvas/templatedetail', { tid: viewId }, { signal }),
    enabled: Boolean(viewId), retry: false,
  });
  const mutation = useMutation({
    mutationFn: (body: Change) => platformApi('/nifi/canvas/templateversion', body),
    onSuccess: () => {
      setDraft(undefined);
      setDecision(undefined);
      setBinding(undefined);
      void queryClient.invalidateQueries({ queryKey });
    },
  });
  const editDefinition = (row?: Template, clone = false) => {
    mutation.reset();
    setInspection(undefined);
    setDefinitionError('');
    setViewId(undefined);
    setDraft({ tid: row?.tid, action: row?.status === 'DRAFT' && !clone ? 'save' : 'create', updatedAt: row?.updatedAt });
    form.resetFields();
    form.setFieldsValue({ ...(row?.spec || defaultSpec), name: row?.name || '' });
    setFlowText(row?.spec.flow ? JSON.stringify(row.spec.flow, null, 2) : '');
    setNativeSnapshot(row?.spec.nativeSnapshot);
    setPreviewImage(row?.spec.previewImage);
    setEngine(row?.spec.engine || 'CANVAS');
  };
  const screenshot = async (file?: File) => {
    if (!file) return;
    try {
      if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 500 * 1024) {
        throw new Error('请选择500KB以内的PNG或JPEG流程截图');
      }
      const data = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(new Error('截图读取失败'));
        reader.readAsDataURL(file);
      });
      setPreviewImage(data);
    } catch (cause) { setDefinitionError(errorText(cause)); }
  };
  const inspect = async (file?: File) => {
    if (!file) return;
    try {
      if (file.size > 2 * 1024 * 1024) throw new Error('流程定义须小于 2 MB');
      const text = (await file.text()).replace(/^\uFEFF/, '');
      const parsed = JSON.parse(text);
      if (parsed.flowContents) {
        let result: ReturnType<typeof readNativePreset>;
        try {
          result = readNativePreset(text);
        } catch {
          result = { defaults: {}, summary: '已读取原始 NiFi 流程。请指定来源和目标类型，再配置接入执行流程。' };
        }
        const values = { ...form.getFieldsValue(), ...result.defaults };
        form.setFieldsValue({ ...values, name: values.name || 'OB Oracle 与 Hive 并行接入' });
        setNativeSnapshot(parsed);
        setFlowText(values.targets?.length ? JSON.stringify(buildTemplateFlow(values), null, 2) : '');
        setInspection({ message: result.summary + ' 已保留原始定义，可在预览中查看全部组件和配置。' });
      } else {
        setFlowText(JSON.stringify(parseTemplateFlow(parsed), null, 2));
        setInspection({ message: '已读取完整海通画布，保存时将校验组件、连线和绑定槽位。' });
      }
    } catch (cause) {
      setInspection({ message: errorText(cause), failed: true });
    }
  };
  return <Panel title="模板管理">
    <Alert type="info" showIcon style={{marginBottom: 16}}
      message="模板按目标写入组合复用来源。自动适配可用于数据库、FTP/SFTP/FTPS、API、Kafka、MongoDB、Elasticsearch、MinIO、HDFS与HBase的结构化登记数据。"
      description="文件定位与解析、接口分页、消息消费配置沿用来源登记；文件上传和接口推送使用已配置的落地通道。非结构化文件使用数据上报入口。"/>
    <Space wrap style={{ marginBottom: 16 }}>
      <Input.Search aria-label="搜索模板" placeholder="按模板名称搜索" allowClear maxLength={96}
        onSearch={value => { setKeyword(value.trim()); setPage(previous => ({ ...previous, pageNum: 1 })); }}/>
      <Checkbox checked={includeHistory} onChange={event => {
        setIncludeHistory(event.target.checked); setPage(previous => ({ ...previous, pageNum: 1 }));
      }}>显示停用版本</Checkbox>
      <Button type="primary" onClick={() => editDefinition()}>新建模板</Button>
    </Space>
    {templates.isError && <Alert type="error" showIcon message={errorText(templates.error)}
      action={<Button onClick={() => void templates.refetch()}>重试</Button>}/>}
    {mutation.isError && !draft && !binding && !decision && <Alert type="error" showIcon message={errorText(mutation.error)}/>}
    <Table<Template> rowKey="tid" dataSource={templates.data?.list} loading={templates.isFetching}
      onChange={(_,__,sorter,extra) => {
        if (extra.action !== 'sort') return;
        const value = Array.isArray(sorter) ? sorter[0] : sorter;
        setSorting(value.order ? {sortField:String(value.field),sortDir:value.order === 'ascend' ? 'asc' : 'desc'}
          : {sortField:'updatedAt',sortDir:'desc'});
        setPage(previous=>({...previous,pageNum:1}));
      }}
      scroll={{ x: 1100 }} pagination={{ current: page.pageNum, pageSize: page.pageSize, total: templates.data?.total,
        pageSizeOptions: [20, 50, 100], showSizeChanger: true,
        onChange: (pageNum, pageSize) => setPage({ pageNum, pageSize }) }}
      columns={[
        { title: '模板名称', dataIndex:'name', sorter:true, sortOrder:sorting.sortField==='name' ? (sorting.sortDir==='asc' ? 'ascend' : 'descend') : null,
          width: 260, render: (_, row) => <Space><Button type="link" onClick={() => setViewId(row.tid)}>{row.name}</Button>
          {row.isDefault && <Tag color="blue">默认</Tag>}</Space> },
        { title: '版本', dataIndex:'version', sorter:true, sortOrder:sorting.sortField==='version' ? (sorting.sortDir==='asc' ? 'ascend' : 'descend') : null,
          width: 65, render: (_, row) => 'V' + row.version },
        { title: '目标类型', width: 180, render: (_, row) => row.spec.targets.map(typeName).join('、') },
        { title: '适用来源', width: 105, render: (_, row) => row.spec.sourceType === 'ANY' ? '自动适配' : row.spec.sourceType === 'JDBC' ? '数据库' : row.spec.sourceType },
        { title: '拓扑', width: 85, render: (_, row) => row.spec.targets.length === 1 ? '单目标写入' : row.spec.topology === 'PARALLEL' ? '并行写入' : '依次写入' },
        { title: '状态', width: 90, render: (_, row) => <Tag color={row.status === 'PUBLISHED' ? 'green' : undefined}>{stateLabels[row.status]}</Tag> },
        { title: '操作', width: 215, fixed: 'right', render: (_, row) => <Space wrap size={0} style={{width:190}}>
          <Button type="link" onClick={() => setViewId(row.tid)}>查看</Button>
          {row.status !== 'RETIRED' && <Button type="link" onClick={() => {
            mutation.reset(); setDefinitionError(''); setBinding(row); setBindingName(row.name);
            setBindingIds(row.spec.targetDatasourceIds || row.spec.targets.map(() => ''));
          }}>配置目标库</Button>}
          {row.status === 'PUBLISHED' && !row.isDefault && <Button type="link" disabled={mutation.isPending}
            onClick={() => { mutation.reset(); mutation.mutate({ action: 'default', tid: row.tid, expectedUpdatedAt: row.updatedAt }); }}>
            设为默认</Button>}
          {row.status !== 'RETIRED' && <Button type="link" onClick={() => {
            mutation.reset();
            setDecision({ action: row.status === 'DRAFT' ? 'publish' : 'retire', row });
          }}>{row.status === 'DRAFT' ? '发布' : '停用'}</Button>}
        </Space> },
      ]}/>
    <Modal title={draft?.action === 'save' ? '编辑模板草稿' : draft?.tid ? '新建模板版本' : '新建接入模板'}
      width={920} open={Boolean(draft)} forceRender
      onCancel={() => setDraft(undefined)} onOk={() => form.submit()} okText="保存草稿"
      confirmLoading={mutation.isPending} cancelButtonProps={{ disabled: mutation.isPending }}>
      <Form<FormValues> form={form} layout="vertical" onFinish={({ name, ...spec }) => {
        if (draft && !mutation.isPending) {
          try {
            setDefinitionError('');
            const flow = engine === 'GENERATED' ? undefined
              : flowText.trim() ? parseTemplateFlow(JSON.parse(flowText)) : buildTemplateFlow(spec);
            mutation.mutate({ action: draft.action, tid: draft.tid, name,
              expectedUpdatedAt: draft.action === 'save' ? draft.updatedAt : undefined, spec: {
                ...spec, flow, nativeSnapshot, previewImage, engine,
                hiveMergeMaxAgeSeconds: spec.targets.includes('HIVE') ? spec.hiveMergeMaxAgeSeconds : undefined,
              } });
          } catch (cause) { setDefinitionError(errorText(cause)); }
        }
      }}>
        <Form.Item label="导入流程定义">
          <input aria-label="选择 NiFi 流程 JSON" type="file" accept=".json,application/json" onChange={event => {
            void inspect(event.currentTarget.files?.[0]);
            event.currentTarget.value = '';
          }}/>
        </Form.Item>
        {inspection && <Alert type={inspection.failed ? 'warning' : 'info'} message={inspection.message} showIcon/>}
        {definitionError && <Alert type="error" message={definitionError} showIcon/>}
        {draft && mutation.isError && <Alert type="error" message={errorText(mutation.error)} showIcon/>}
        <Form.Item name="name" label="模板名称" rules={[{ required: true, whitespace: true }]}><Input maxLength={96}/></Form.Item>
        <Form.Item name="sourceType" label="来源类型" rules={[{ required: true }]}>
          <Select options={[{value: 'ANY', label: '自动适配已登记来源'},
            { value: 'JDBC', label: '仅数据库来源（通用 JDBC）' }, ...targetOptions,
            ...['FTP','API','KAFKA','MINIO','MONGODB','ELASTICSEARCH','HDFS','HBASE'].map(value => ({value,label:value === 'FTP' ? 'FTP / SFTP / FTPS' : value}))]}/>
        </Form.Item>
        {selectedTargets.map((kind: string, index: number) => <Form.Item key={index}
          name={['targetDatasourceIds', index]} label={typeName(kind) + ' 目标库'} rules={[{required: true, message: '请选择目标库'}]}>
          <TemplateTargetSelect kind={kind} excluded={formTargetIds}
            selected={existingChoices.data?.list.find(item => item.value === formTargetIds[index])}/>
        </Form.Item>)}
        <Form.Item name="targets" label="目标类型" rules={[{ required: true, type: 'array', min: 1, max: 8 }]}>
          <Select mode="multiple" options={targetOptions} placeholder="选择目标数据源类型"/>
        </Form.Item>
        <Form.Item name="topology" label="写入方式" rules={[{ required: true }]}>
          <Select options={selectedTargets.length > 1 && engine !== 'GENERATED' ? [{ value: 'PARALLEL', label: '并行写入' }]
            : [{ value: 'SERIAL', label: selectedTargets.length > 1 ? '依次写入' : '单目标写入' }, { value: 'PARALLEL', label: '并行写入' }]}/>
        </Form.Item>
        <Form.Item name="sourceSchedule" label="来源调度" initialValue="REGISTERED">
          <Select options={[{value:'REGISTERED',label:'沿用来源登记调度（推荐）'},{value:'TEMPLATE',label:'使用模板调度间隔'}]}/>
        </Form.Item>
        <Form.Item name="intervalSeconds" label="模板调度间隔（秒）" extra="仅在使用模板调度时生效；Kafka保持实时消费。" rules={[{ required: true }]}>
          <InputNumber min={10} max={86400} precision={0}/>
        </Form.Item>
        <Form.Item name="fetchSize" label="每批读取行数" rules={[{ required: true }]}>
          <InputNumber min={1} max={50000} precision={0}/>
        </Form.Item>
        {selectedTargets.includes('HIVE') && <Form.Item name="hiveMergeMaxAgeSeconds" label="Hive HDFS 合并等待上限（秒）"
          extra="适用于启用批次合并的 MRS Hive 目标">
          <InputNumber min={1} max={86400} precision={0} placeholder="沿用系统默认值" style={{ width: '100%' }}/>
        </Form.Item>}
        <Form.Item label="流程截图" extra="可以上传已配置流程的截图作为预览，留空时按流程配置自动绘图。">
          <input aria-label="上传流程截图" type="file" accept="image/png,image/jpeg"
            onChange={event => { void screenshot(event.currentTarget.files?.[0]); event.currentTarget.value = ''; }}/>
          {previewImage && <Space><img src={previewImage} alt="流程截图预览" style={{maxWidth: 300, maxHeight: 150}}/>
            <Button onClick={() => setPreviewImage(undefined)}>移除截图</Button></Space>}
        </Form.Item>
        {engine !== 'GENERATED' && <Form.Item label="流程定义"
          extra="绑定槽位使用 source、target:0、target:1。字段治理使用 governance:0，并默认读取登记字段映射。可导入海通导出的画布后设置槽位。">
          <Space style={{ marginBottom: 8 }}>
            <Button onClick={() => {
              const values = form.getFieldsValue();
              if (!values.targets?.length) { setDefinitionError('请先选择目标类型'); return; }
              setFlowText(JSON.stringify(buildTemplateFlow(values), null, 2));
              setDefinitionError('');
            }}>按目标生成画布</Button>
          </Space>
          <CodeMirror value={flowText} onChange={setFlowText} extensions={[json()]} theme={dark ? 'dark' : 'light'}
            height="360px" basicSetup={{lineNumbers: true, foldGutter: true}}/>
        </Form.Item>}
      </Form>
    </Modal>
    <Modal width={1120} title={detail.data ? detail.data.name : '模板详情'}
      open={Boolean(viewId)} onCancel={() => setViewId(undefined)} footer={detail.data ? <Space>
        {detail.data.status === 'DRAFT' && <Button onClick={() => editDefinition(detail.data)}>编辑草稿</Button>}
        <Button onClick={() => editDefinition(detail.data, true)}>新建版本</Button>
        <Button onClick={() => setViewId(undefined)}>关闭</Button>
      </Space> : null}>
      {detail.isFetching && <p role="status">正在读取流程定义…</p>}
      {detail.isError && <Alert type="error" message={errorText(detail.error)} action={<Button onClick={() => void detail.refetch()}>重试</Button>}/>}
      {detail.data && !detail.isFetching && <AccessTemplatePreview spec={detail.data.spec}/>}
    </Modal>
    <Modal title="配置模板目标库" open={Boolean(binding)} onCancel={() => setBinding(undefined)}
      confirmLoading={mutation.isPending} onOk={() => {
        if (!binding || mutation.isPending) return;
        if (!bindingName.trim()) { setDefinitionError('请输入模板名称'); return; }
        if (bindingIds.length !== binding.spec.targets.length || bindingIds.some(id => !id)) {
          setDefinitionError('请配置全部目标库'); return;
        }
        mutation.mutate({ action: 'bind', tid: binding.tid, name: bindingName,
          targetDatasourceIds: bindingIds, expectedUpdatedAt: binding.updatedAt });
      }}>
      <Form layout="vertical">
        <Form.Item label="模板名称" required><Input value={bindingName} maxLength={96} onChange={event => setBindingName(event.target.value)}/></Form.Item>
        {binding?.spec.targets.map((kind, index) => <Form.Item key={index} label={typeName(kind) + ' 目标库'} required>
          <TemplateTargetSelect kind={kind} value={bindingIds[index]} excluded={bindingIds}
            selected={existingChoices.data?.list.find(item => item.value === bindingIds[index])}
            onChange={id => setBindingIds(previous => binding.spec.targets.map((_, slot) => slot === index ? id : previous[slot] || ''))}/>
        </Form.Item>)}
        {definitionError && <Alert type="error" message={definitionError}/>}
        {mutation.isError && <Alert type="error" message={errorText(mutation.error)}/>}
      </Form>
    </Modal>
    <Modal title={decision?.action === 'publish' ? '发布接入模板' : '停用接入模板'} open={Boolean(decision)}
      onCancel={() => setDecision(undefined)} onOk={() => {
        if (decision && !mutation.isPending) mutation.mutate({
          action: decision.action, tid: decision.row.tid, expectedUpdatedAt: decision.row.updatedAt,
        });
      }} confirmLoading={mutation.isPending} cancelButtonProps={{ disabled: mutation.isPending }}>
      <p>{decision?.row.name} · V{decision?.row.version}</p>
      <p>{decision?.action === 'publish' ? '发布后，创建接入任务时可选用此版本。此模板的上一发布版本将停用。' : '停用后，创建接入任务时将隐藏此版本。'}</p>
      {decision && mutation.isError && <Alert type="error" message={errorText(mutation.error)} showIcon/>}
    </Modal>
  </Panel>;
}
