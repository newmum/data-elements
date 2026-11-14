import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Alert, App, Button, Drawer, Form, Input, InputNumber, Select, Space, Spin, Switch, Table, Tag } from 'antd';
import { ApartmentOutlined, ArrowLeftOutlined, LogoutOutlined, PlusOutlined } from '@ant-design/icons';
import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from '@tanstack/react-query';
import { HashRouter, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import { OceanTheme } from '../design/theme';
import { getBridgeToken, setBridgeToken, useTokenReady } from '@/api/bridgeSession';
import { useComponentManifests } from '@/api/manifests';
import { useFlowStatus } from '@/api/pipelines';
import { useCanvasStore } from '@/stores/canvasStore';
import Canvas from '@/canvas/Canvas';
import ScriptProcessEditor, { scriptProcessError } from '@/panels/ScriptProcessEditor';
import { EMPTY_CANVAS_DSL, type CanvasDsl } from '@/types/dsl';
import type { FieldSchema } from '@/types/manifest';
import '../styles/global.css';
import '@/styles/globals.css';
import '@/styles/haitong-integration.css';
import './styles.css';

const SESSION = 'haitong-flow-lite:session';
const query = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const listPath = '/development/nifi-flows';
const editorPath = '/development/canvas';
setBridgeToken(sessionStorage.getItem(SESSION));

function session(token: string | null) {
  query.clear();
  if (token) sessionStorage.setItem(SESSION, token);
  else sessionStorage.removeItem(SESSION);
  setBridgeToken(token);
}

async function request<T>(path: string, method = 'GET', body?: object): Promise<T> {
  const token = getBridgeToken();
  const response = await fetch('/nifi/api' + path, {
    method,
    headers: {
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const payload = await response.json();
  if (!response.ok) {
    if (response.status === 401 && token && token === getBridgeToken()) session(null);
    throw new Error(payload.message || '操作失败');
  }
  return payload as T;
}

type FlowSummary = { id: string; name: string; updatedAt: number; nifiProcessGroupId: string | null };
type Flow = FlowSummary & { designVersion: number; deployedVersion: number | null; dsl: CanvasDsl; retiredGroupPending: boolean };
type FlowAction = 'save' | 'deploy' | 'start' | 'stop' | 'delete';
const description = (error: unknown) => error instanceof Error ? error.message : '请求失败，请重试';

function Login() {
  const [failure, setFailure] = useState('');
  const [pending, setPending] = useState(false);
  async function submit(values: { password: string }) {
    setFailure('');
    setPending(true);
    try { session((await request<{ token: string }>('/session', 'POST', values)).token); }
    catch (error) { setFailure(description(error)); }
    finally { setPending(false); }
  }
  return <main className="lite-login">
    <section className="lite-login-card">
      <div className="lite-mark"><ApartmentOutlined /></div>
      <h1>海通数据集成中心</h1>
      <p>管理员登录</p>
      {failure && <Alert message={failure} type="error" showIcon />}
      <Form layout="vertical" onFinish={submit} initialValues={{ username: 'admin' }}>
        <Form.Item name="username" label="账号"><Input readOnly autoComplete="username" /></Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password autoFocus autoComplete="current-password" />
        </Form.Item>
        <Button htmlType="submit" type="primary" loading={pending} block>登录</Button>
      </Form>
    </section>
  </main>;
}

function FlowList() {
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const flows = useQuery({
    queryKey: ['lite-flow-list', page, size, search],
    queryFn: () => request<{ list: FlowSummary[]; total: number }>('/pipelines/page?' + new URLSearchParams({
      page: String(page), size: String(size), search,
    })),
  });
  return <main className="lite-list">
    <div className="lite-list-heading">
      <h1>NiFi 流程</h1>
      <Space wrap>
        <Input.Search allowClear aria-label="搜索流程名称" placeholder="搜索流程名称" onSearch={value => { setSearch(value); setPage(1); }} />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(editorPath)}>新建流程</Button>
      </Space>
    </div>
    {flows.isError && <Alert type="error" showIcon message={description(flows.error)} action={<Button onClick={() => void flows.refetch()}>重试</Button>} />}
    <Table rowKey="id" tableLayout="fixed" scroll={{x:480}} loading={flows.isFetching} dataSource={flows.data?.list ?? []}
      pagination={{ current: page, pageSize: size, pageSizeOptions: [20, 50, 100], showSizeChanger: true,
        total: flows.data?.total ?? 0, onChange: (current, nextSize) => { setPage(nextSize === size ? current : 1); setSize(nextSize); } }}
      columns={[
        { title: '流程名称', key: 'name', width: 220, ellipsis: { showTitle: false }, render: (_, item) =>
          <Button type="link" title={item.name} style={{maxWidth:'100%',minWidth:0,paddingInline:0}} onClick={() => navigate(editorPath + '/' + item.id)}>
            <span style={{overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{item.name}</span>
          </Button> },
        { title: '部署状态', key: 'deployment', render: (_, item) => item.nifiProcessGroupId ? <Tag color="blue">已部署</Tag> : <Tag>草稿</Tag> },
        { title: '更新时间', key: 'time', render: (_, item) => new Date(item.updatedAt).toLocaleString('zh-CN') },
      ]} />
  </main>;
}

function ValueInput({ field, value, onChange }: { field: FieldSchema; value: unknown; onChange: (value: unknown) => void }) {
  const text = String(value ?? '');
  switch (field.type) {
    case 'switch': return <Switch checked={Boolean(value)} onChange={onChange} aria-label={field.label} />;
    case 'number': return <InputNumber value={typeof value === 'number' ? value : undefined} onChange={onChange} style={{ width: '100%' }} />;
    case 'select': return <Select options={field.options} value={value as string | undefined} onChange={onChange} />;
    case 'password': return <Input.Password value={text} onChange={event => onChange(event.target.value)} autoComplete="new-password" />;
    case 'sql-editor':
    case 'json-editor': return <Input.TextArea value={text} onChange={event => onChange(event.target.value)} autoSize={{ minRows: 3, maxRows: 9 }} />;
    default: return <Input value={text} placeholder={field.placeholder} onChange={event => onChange(event.target.value)} />;
  }
}

function NodeSettings() {
  const node = useCanvasStore(state => state.drawerNodeId ? state.nodes[state.drawerNodeId] : null);
  const store = useCanvasStore.getState();
  const manifests = useComponentManifests();
  const manifest = manifests.data?.find(item => item.key === node?.manifestKey);
  const scriptError = node?.manifestKey === 'transform.java' ? scriptProcessError(node.manifestKey, node.config) : undefined;
  return <Drawer title={manifest?.label ?? '节点配置'} placement="right" width="min(520px, 100vw)"
    open={!!node} onClose={() => store.openDrawer(null)}
    footer={<Button type="primary" onClick={() => store.openDrawer(null)}>完成配置</Button>}>
    {node && manifest && <Form layout="vertical" requiredMark="optional">
      <Form.Item label="节点名称"><Input value={node.label} onChange={event => store.setNodeLabel(node.id, event.target.value)} maxLength={120} /></Form.Item>
      {node.manifestKey === 'transform.java'
        ? <><ScriptProcessEditor manifestKey={node.manifestKey} config={node.config}
            onChange={patch => store.updateNodeConfig(node.id, patch)} />
          {scriptError && <Alert type="warning" message={scriptError} showIcon />}</>
        : (manifest.fields ?? []).filter(field => (!field.visibleWhen || node.config[field.visibleWhen.key] === field.visibleWhen.equals)
            && (field.key !== 'intervalSeconds' || node.config.runOnce === false))
            .map(field => <Form.Item key={field.key} label={field.label} required={field.required} extra={field.help}>
              <ValueInput field={field} value={node.config[field.key]}
                onChange={value => store.updateNodeConfig(node.id, { [field.key]: value })} />
            </Form.Item>)}
    </Form>}
  </Drawer>;
}

function FlowEditor({ id }: { id?: string }) {
  const navigate = useNavigate();
  const cache = useQueryClient();
  const { message, modal } = App.useApp();
  const [flow, setFlow] = useState<Flow>();
  const [name, setName] = useState('');
  const [ready, setReady] = useState(false);
  const [failure, setFailure] = useState('');
  const [pending, setPending] = useState('');
  const activeId = flow?.id ?? null;
  const runtime = useFlowStatus(activeId, 3000);
  const { nodes, edges } = useCanvasStore();
  const isDirty = ready && (name !== (flow?.name ?? '') || (flow
    ? JSON.stringify(useCanvasStore.getState().toDsl()) !== JSON.stringify(flow.dsl)
    : Object.keys(nodes).length > 0 || Object.keys(edges).length > 0));

  useEffect(() => {
    let live = true;
    const store = useCanvasStore.getState();
    store.clear();
    store.setCanvasMode('EDIT');
    setFailure('');
    setReady(false);
    const setup = (current?: Flow) => {
      if (!live) return;
      store.loadDsl(current?.dsl ?? EMPTY_CANVAS_DSL);
      store.setCurrentPipeline(current?.id ?? null, current?.name ?? null);
      setName(current?.name ?? '');
      setFlow(current);
      setReady(true);
    };
    if (id) void request<Flow>('/pipelines/' + id).then(setup).catch(error => { if (live) setFailure(description(error)); });
    else setup();
    return () => { live = false; store.clear(); };
  }, [id]);

  useEffect(() => {
    if (!isDirty) return;
    const protect = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', protect);
    return () => window.removeEventListener('beforeunload', protect);
  }, [isDirty]);

  function back() {
    if (isDirty) modal.confirm({ title: '画布有未保存的修改', content: '返回列表会丢失这些修改。', onOk: () => navigate(listPath) });
    else navigate(listPath);
  }

  async function action(task: FlowAction) {
    setPending(task);
    setFailure('');
    try {
      if (task === 'save') {
        const store = useCanvasStore.getState();
        const saved = await request<Flow>('/pipelines' + (flow ? '/' + flow.id : ''), 'POST',
          { name, designVersion: flow?.designVersion, dsl: store.toDsl() });
        setFlow(saved);
        setName(saved.name);
        store.setCurrentPipeline(saved.id, saved.name);
        if (!flow) navigate(editorPath + '/' + saved.id, { replace: true });
        message.success('流程已保存');
      } else if (flow) {
        const path = '/pipelines/' + flow.id;
        if (task === 'delete') {
          await request(path, 'DELETE');
          navigate(listPath);
        } else {
          const updated = await request<Flow>(path + '/' + task, 'POST');
          setFlow(updated);
          void cache.invalidateQueries({ queryKey: ['pipeline-status', flow.id] });
          message.success({ deploy: '流程已部署', start: '已向 NiFi 发出启动请求', stop: '已向 NiFi 发出停止请求' }[task]);
        }
      }
      void cache.invalidateQueries({ queryKey: ['lite-flow-list'] });
    } catch (error) { setFailure(description(error)); }
    finally { setPending(''); }
  }

  const deployment = flow?.deployedVersion;
  const groupId = flow?.nifiProcessGroupId;
  const flowIsCurrent = Boolean(groupId && deployment === flow?.designVersion);
  const state = runtime.isError ? '状态读取失败' : runtime.data?.status === 'RUNNING' ? '运行中'
    : groupId ? runtime.data?.runtimeState === 'MISSING' ? 'NiFi 流程组不存在' : '已部署' : '草稿';
  const openNifi = () => {
    const tab = window.open('about:blank', '_blank');
    if (!tab || !flow) { tab?.close(); return; }
    tab.opener = null;
    void request<{ url: string }>('/pipelines/' + flow.id + '/ui-link').then(data => tab.location.replace(data.url))
      .catch(error => { tab.close(); setFailure(description(error)); });
  };
  return <main className="lite-editor">
    <div className="lite-toolbar">
      <Space><Button icon={<ArrowLeftOutlined />} onClick={back} aria-label="返回流程列表" />
        <Input aria-label="流程名称" placeholder="请输入流程名称" value={name} maxLength={120}
          disabled={!!pending} onChange={event => setName(event.target.value)} />
        <Tag>{state}</Tag></Space>
      <Space wrap>
        <Button type="primary" onClick={() => void action('save')} loading={pending === 'save'} disabled={!ready || !name.trim() || !!pending}>保存</Button>
        <Button onClick={() => void action('deploy')} loading={pending === 'deploy'} disabled={!flow || isDirty || !!pending}>部署</Button>
        <Button onClick={() => void action('start')} loading={pending === 'start'} disabled={!flowIsCurrent || isDirty || !!pending}>启动</Button>
        <Button onClick={() => void action('stop')} loading={pending === 'stop'} disabled={!groupId || !!pending}>停止</Button>
        <Button onClick={openNifi} disabled={!groupId}>打开 NiFi</Button>
        {flow && <Button danger disabled={!!pending} onClick={() => modal.confirm({
          title: '删除此流程？', content: '已部署的流程须先停止并清空队列。', onOk: () => action('delete'),
        })}>删除</Button>}
      </Space>
    </div>
    {failure && <Alert type="error" message={failure} showIcon closable onClose={() => setFailure('')} />}
    {flow?.retiredGroupPending && <Alert type="warning" showIcon message="上一个流程组清理待重试，请在 NiFi 查看旧组状态。" />}
    <div className="lite-graph haitong-nifi-canvas nifi-workspace">
      {ready ? <><Canvas /><NodeSettings /></> : failure ? null : <Spin className="lite-loading" />}
    </div>
  </main>;
}

function EditorRoute() {
  const { id } = useParams();
  return <FlowEditor key={id ?? 'new'} id={id} />;
}

function Workspace() {
  const loggedIn = useTokenReady();
  if (!loggedIn) return <Login />;
  return <div className="lite-workspace">
    <header className="lite-header">
      <div className="lite-brand"><ApartmentOutlined /> 海通数据集成中心</div>
      <Button type="text" icon={<LogoutOutlined />}
        onClick={() => void request('/session', 'DELETE').catch(() => undefined).finally(() => session(null))}>退出登录</Button>
    </header>
    <Routes>
      <Route path={editorPath} element={<EditorRoute />} />
      <Route path={editorPath + '/:id'} element={<EditorRoute />} />
      <Route path="*" element={<FlowList />} />
    </Routes>
  </div>;
}

createRoot(document.getElementById('root')!).render(
  <QueryClientProvider client={query}><OceanTheme><HashRouter><Workspace /></HashRouter></OceanTheme></QueryClientProvider>,
);
