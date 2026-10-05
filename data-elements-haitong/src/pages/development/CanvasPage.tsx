import { App, Button, ConfigProvider, Result, theme as antTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useWorkbench } from '../../app/Workbench';
import { useOceanTheme } from '../../design/theme';
import { useCanvasSelectionRevision, useSessionRevision, useTokenReady } from '../../nifi/api/bridgeSession';
import { setCanvasSelection } from '../../nifi/api/iframeBridge';
import { useCanvasStore } from '../../nifi/stores/canvasStore';
import EditorPage from '../../nifi/pages/EditorPage';
import { NifiSessionPrompt } from './NifiFlowsPage';
import '../../nifi/styles/globals.css';
import '../../nifi/styles/haitong-integration.css';

// Keep the original editor layout while following the workbench color mode.
const nifiTheme = (dark: boolean) => ({
 algorithm: dark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
 token: {
  colorPrimary: dark ? '#177ddc' : '#1677ff', colorSuccess: '#52c41a', colorWarning: '#faad14',
  colorError: '#ff4d4f', colorInfo: '#1677ff', borderRadius: 8,
  colorBgBase: dark ? '#141414' : '#ffffff',
  colorBgContainer: dark ? '#1f1f1f' : '#ffffff',
  colorBgElevated: dark ? '#262626' : '#ffffff',
  colorText: dark ? '#f0f0f0' : '#262626',
  colorBorder: dark ? '#434343' : '#d9d9d9',
  fontFamily: "'Inter', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif",
  controlHeight: 36,
 },
 components: {
  Button: {fontWeight: 500, controlHeight: 36, paddingInline: 16},
  Drawer: {paddingLG: 0}, Input: {controlHeight: 36}, Select: {controlHeight: 36},
 },
});

/** The local task id is never sent to the NiFi backend as a pipeline id. */
export default function CanvasPage() {
 const {dark} = useOceanTheme();
 const {state, act} = useWorkbench();
 const {message} = App.useApp();
 const navigate = useNavigate();
 const location = useLocation();
 const [params] = useSearchParams();
 const engine = params.get('engine')?.trim() || new URLSearchParams(window.location.search).get('engine')?.trim() || '';
 const unsupportedEngine = Boolean(engine);
 const tokenReady = useTokenReady();
 const sessionRevision = useSessionRevision();
 const selectionRevision = useCanvasSelectionRevision();
 const taskId = params.get('task')?.trim() || null;
 const explicitPipelineId = params.get('pipelineId')?.trim() || params.get('jobId')?.trim() || null;
 const accessTaskId = params.get('accessTaskId')?.trim() || null;
 const tid = params.get('tid')?.trim() || null;
 const localTask = taskId ? state.tasks.find(task => task.id === taskId) : undefined;
 const hasMixedSelection = Boolean(taskId && (explicitPipelineId || accessTaskId || tid));
 const hasRouteSelection = Boolean(taskId || explicitPipelineId || accessTaskId || tid);
 // Remount the editor whenever the authenticated session changes. Otherwise a
 // previous tenant's unsaved DSL could remain in the shared canvas store.
 const routeKey = [sessionRevision, hasRouteSelection ? 0 : selectionRevision, taskId, explicitPipelineId, accessTaskId, tid].map(value=>value||'').join(':');
 const [preparedKey, setPreparedKey] = useState<string | null>(null);

 useEffect(() => {
  document.body.classList.add('haitong-nifi-active');
  return () => document.body.classList.remove('haitong-nifi-active');
 }, []);

 const back = () => {
  const preferred = (location.state as {returnTo?: string} | null)?.returnTo;
  const fallback = taskId ? '/development/tasks' : '/development/nifi-flows';
  navigate(preferred?.startsWith('/') && !preferred.startsWith('//') && !preferred.includes('/development/canvas') ? preferred : fallback);
 };

 useEffect(() => {
  if (unsupportedEngine || (taskId && !localTask) || hasMixedSelection) return;
  // Changing routes starts a fresh editor session, so the previous flow cannot leak into it.
  useCanvasStore.getState().clear();
  const pipelineId = explicitPipelineId || (!accessTaskId && !tid ? localTask?.nifiPipelineId : null) || null;
  // An unqualified embedded canvas keeps the parent INIT/URL selection.
  // A local task, including a new empty task, deliberately overrides it.
  setCanvasSelection(hasRouteSelection
   ? {pipelineId, accessTaskId, tid}
   : {});
  if (localTask && !pipelineId && !accessTaskId && !tid) useCanvasStore.getState().setCurrentPipeline(null, localTask.name);
  setPreparedKey(routeKey);
  return () => {
   setCanvasSelection({});
   useCanvasStore.getState().clear();
  };
 // The linked id is read when a route opens. Linking after first save must not remount the active deploy mutation.
 // eslint-disable-next-line react-hooks/exhaustive-deps
 }, [routeKey, unsupportedEngine]);

 useEffect(() => {
  const close = () => back();
  window.addEventListener('haitong:nifi-close-requested', close);
  return () => window.removeEventListener('haitong:nifi-close-requested', close);
 // eslint-disable-next-line react-hooks/exhaustive-deps
 }, [location.key, taskId]);

 useEffect(() => {
  if (!taskId || !localTask || localTask.nifiPipelineId || explicitPipelineId) return;
  const linkedTaskId = taskId;
  const onSaved = (event: Event) => {
   const pipelineId = String((event as CustomEvent<{pipelineId?: string}>).detail?.pipelineId || '').trim();
   if (!pipelineId) return;
   void act({type:'LINK_NIFI_PIPELINE', id:linkedTaskId, pipelineId})
    .then(() => message.success('NiFi 流程已关联当前任务'))
    .catch(error => message.error(`流程已保存，但关联本地任务失败：${error instanceof Error ? error.message : String(error)}`));
  };
  window.addEventListener('haitong:nifi-pipeline-saved', onSaved);
  return () => window.removeEventListener('haitong:nifi-pipeline-saved', onSaved);
 }, [act, explicitPipelineId, localTask, message, taskId]);

 if (taskId && !localTask) return <Result status="404" title="本地任务不存在" extra={<Button onClick={back}>返回任务列表</Button>}/>;
 if (hasMixedSelection) return <Result status="error" title="画布地址包含冲突的流程参数" subTitle="本地任务与 NiFi 流程选择参数不能同时指定。请从任务列表或 NiFi 流程列表重新打开。" extra={<Button onClick={back}>返回列表</Button>}/>;
 if (unsupportedEngine) return <Result status="warning" title="该专用画布未迁入海通" subTitle={`当前链接指定了 ${engine} 专用引擎。海通只接入指定 9 月 29 日基线的普通 NiFi 画布，不能把专用配置当作普通流程打开。`} extra={<Button onClick={back}>返回列表</Button>}/>;

 return <div className="haitong-nifi-canvas nifi-workspace" style={{position:'fixed', inset:0, zIndex:60, background:'var(--canvas-bg)'}}>
  <ConfigProvider locale={zhCN} theme={nifiTheme(dark)}>
   <App style={{height:'100%'}}>
    {!tokenReady ? <div style={{padding:32, maxWidth:680, margin:'9vh auto'}}><NifiSessionPrompt redirectOnMissing/></div>
     : preparedKey===routeKey ? <EditorPage key={routeKey}/> : <div role="status" style={{padding:32}}>正在准备 NiFi 画布…</div>}
   </App>
  </ConfigProvider>
 </div>;
}
