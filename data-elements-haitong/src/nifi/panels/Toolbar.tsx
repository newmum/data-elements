import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Tooltip,
  Modal,
  Input,
  App as AntdApp,
  Dropdown,
  List,
  Empty,
  type MenuProps,
} from 'antd';
import {
  SaveOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  DownOutlined,
  EditOutlined,
  // DeleteOutlined,
  LoadingOutlined,
  MoreOutlined,
  FileAddOutlined,
  FolderOpenOutlined,
  CopyOutlined,
  ImportOutlined,
  ExportOutlined,
  ClearOutlined,
  EditFilled,
  LineChartOutlined,
  CloseOutlined,
  LinkOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import { useCanvasStore } from '@/stores/canvasStore';
import { notifyClose, requestIntegratedCanvasClose, getTid } from '@/api/iframeBridge';
import {
  useSavePipeline,
  useStartPipeline,
  useStopPipeline,
  // useDeletePipeline,
  useDeployPipeline,
  useFlowStatus,
  usePipelineList,
  usePipeline,
  type PipelineStatus,
  getNifiUiLink,
} from '@/api/pipelines';
import { apiClient } from '@/api/client';
import { resolveNifiNativeUiUrl } from '@/api/nifiNativeUrl';
import { useErrorPanelStore } from '@/stores/errorPanelStore';
import type { FlowStatusPayload } from '@/api/pipelines';
import { appConfig, isChengtianIntegration } from '@/config/appConfig';
import { getNifiOverlayContainer } from './overlayContainer';
import { getSessionRevision, useSessionRevision } from '@/api/bridgeSession';
import CanvasThemeToggle from './CanvasThemeToggle';

interface StatusViz {
  color: string;
  label: string;
  clickable: boolean;
  spinning?: boolean;
}

interface StartOptions {
  syncMode: string;
  fullSyncStrategy?: string;
  deleteTargetData: boolean;
}

function vizFor(status: PipelineStatus | undefined, errorCount: number): StatusViz {
  switch (status) {
    case 'DRAFT':         return { color: '#bfbfbf', label: '草稿',     clickable: false };
    case 'SAVED':         return { color: '#bfbfbf', label: '已保存',   clickable: false };
    case 'DEPLOYING':     return { color: '#1677ff', label: '部署中…',  clickable: false, spinning: true };
    case 'STOPPING':      return { color: '#fa8c16', label: '停止中…',  clickable: false, spinning: true };
    case 'RUNNING':       return { color: '#52c41a', label: '运行中',   clickable: false };
    case 'STOPPED':       return { color: '#8c8c8c', label: '已停止',   clickable: false };
    case 'DEPLOY_FAILED': return { color: '#ff4d4f', label: errorCount > 0 ? `部署失败 (${errorCount})` : '部署失败', clickable: true };
    case 'RUN_ERROR':     return { color: '#ff4d4f', label: errorCount > 0 ? `运行异常 (${errorCount})` : '运行异常', clickable: true };
    case 'WAITING_AUTHORIZATION': return { color: '#722ed1', label: '待执行授权', clickable: false };
    default:              return { color: '#bfbfbf', label: '未保存',   clickable: false };
  }
}

function numericMetric(value: unknown): number {
  if (typeof value === 'number') return value;
  if (typeof value !== 'string') return 0;
  const n = Number(value.replace(/,/g, ''));
  return Number.isFinite(n) ? n : 0;
}

function aggregateRoot(status: unknown): any {
  const raw = status as any;
  return raw?.processGroupStatus?.aggregateSnapshot ?? raw?.aggregateSnapshot ?? raw;
}

function walkStatusGroups(root: any, visit: (group: any) => void) {
  if (!root) return;
  visit(root);
  for (const group of root.processGroupStatusSnapshots ?? []) {
    walkStatusGroups(group?.processGroupStatusSnapshot ?? group, visit);
  }
}

function statusWorkScore(payload?: FlowStatusPayload): number {
  const root = aggregateRoot(payload?.nifiStatus);
  let score = numericMetric(root?.flowFilesTransferred)
    + numericMetric(root?.flowFilesReceived)
    + numericMetric(root?.flowFilesSent);
  walkStatusGroups(root, (group) => {
    for (const processor of group?.processorStatusSnapshots ?? []) {
      const snapshot = processor?.processorStatusSnapshot ?? processor;
      score += numericMetric(snapshot?.taskCount);
      score += numericMetric(snapshot?.flowFilesIn);
      score += numericMetric(snapshot?.flowFilesOut);
    }
  });
  return score;
}

function statusIsSettled(payload?: FlowStatusPayload) {
  if (!payload || payload.status !== 'RUNNING' || payload.errors?.length) return false;
  const root = aggregateRoot(payload.nifiStatus);
  if (!root) return false;
  let activeThreads = numericMetric(root.activeThreadCount);
  let queuedCount = numericMetric(root.queuedCount ?? root.flowFilesQueued);
  walkStatusGroups(root, (group) => {
    activeThreads += numericMetric(group?.activeThreadCount);
    queuedCount += numericMetric(group?.queuedCount ?? group?.flowFilesQueued);
    for (const connection of group?.connectionStatusSnapshots ?? []) {
      const snapshot = connection?.connectionStatusSnapshot ?? connection;
      queuedCount += numericMetric(snapshot?.queuedCount ?? snapshot?.flowFilesQueued);
    }
    for (const processor of group?.processorStatusSnapshots ?? []) {
      const snapshot = processor?.processorStatusSnapshot ?? processor;
      activeThreads += numericMetric(snapshot?.activeThreadCount);
    }
  });
  return activeThreads === 0 && queuedCount === 0;
}

export default function Toolbar() {
  const renderSessionRevision = useSessionRevision();
  const isCurrentSession = () => renderSessionRevision === getSessionRevision();
  const toDsl = useCanvasStore((s) => s.toDsl);
  const loadDsl = useCanvasStore((s) => s.loadDsl);
  const clear = useCanvasStore((s) => s.clear);
  const currentPipelineId = useCanvasStore((s) => s.currentPipelineId);
  const currentPipelineName = useCanvasStore((s) => s.currentPipelineName);
  const setCurrentPipeline = useCanvasStore((s) => s.setCurrentPipeline);
  const canvasMode = useCanvasStore((s) => s.canvasMode);
  const setCanvasMode = useCanvasStore((s) => s.setCanvasMode);
  const syncCompletion = useCanvasStore((s) => s.syncCompletion);
  const setSyncCompletion = useCanvasStore((s) => s.setSyncCompletion);
  const { message, modal, notification } = AntdApp.useApp();

  const save = useSavePipeline();
  const start = useStartPipeline();
  const stop = useStopPipeline();
  const deploy = useDeployPipeline();
  // const del = useDeletePipeline();
  const openErrorPanel = useErrorPanelStore((s) => s.open);

  const statusQuery = useFlowStatus(currentPipelineId, 3000);
  const liveStatus = statusQuery.data?.status;
  const errorCount = statusQuery.data?.errors?.length ?? 0;
  const [optimisticStatus, setOptimisticStatus] = useState<PipelineStatus | null>(null);
  const effectiveStatus: PipelineStatus = optimisticStatus ?? liveStatus ?? (currentPipelineId ? 'SAVED' : 'DRAFT');
  const viz = useMemo(() => vizFor(effectiveStatus, errorCount), [effectiveStatus, errorCount]);

  const isRunning = effectiveStatus === 'RUNNING' || effectiveStatus === 'RUN_ERROR';
  const isTransitioning = effectiveStatus === 'DEPLOYING' || effectiveStatus === 'STOPPING';
  const nativeStatus = statusQuery.data;
  const canOpenNative = Boolean(currentPipelineId
    && nativeStatus?.id === currentPipelineId
    && nativeStatus.deployed
    && nativeStatus.processGroupId
    && nativeStatus.lastDeployedHash
    && nativeStatus.currentHash === nativeStatus.lastDeployedHash
    && nativeStatus.status !== 'DEPLOYING'
    && nativeStatus.status !== 'DEPLOY_FAILED');

  useEffect(() => {
    const nextMode = isRunning ? 'MONITOR' : 'EDIT';
    if (canvasMode !== nextMode) setCanvasMode(nextMode);
  }, [canvasMode, isRunning, setCanvasMode]);

  const [editingName, setEditingName] = useState(false);
  const [nameDraft, setNameDraft] = useState('');

  const [openSaveAs, setOpenSaveAs] = useState(false);
  const [saveAsDraft, setSaveAsDraft] = useState('');
  const [openOpener, setOpenOpener] = useState(false);
  const [pendingOpenId, setPendingOpenId] = useState<string | null>(null);
  const list = usePipelineList(openOpener);
  const pendingOpen = usePipeline(pendingOpenId);
  const currentPipelineQuery = usePipeline(currentPipelineId);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const completionWatchRef = useRef<{ pipelineId: string; baseline: number; armedAt: number; syncMode: string } | null>(null);
  const completionNotifiedRef = useRef<string | null>(null);
  const completionErrorNotifiedRef = useRef<string | null>(null);

  useEffect(() => {
    if (statusQuery.data?.errors?.length
      && syncCompletion?.pipelineId === statusQuery.data.id) {
      setSyncCompletion(null);
    }
  }, [statusQuery.data, syncCompletion, setSyncCompletion]);

  useEffect(() => {
    if (!optimisticStatus || !liveStatus) return;
    if (liveStatus === optimisticStatus) {
      setOptimisticStatus(null);
      return;
    }
    if (optimisticStatus === 'RUNNING' && (liveStatus === 'RUN_ERROR' || liveStatus === 'DEPLOY_FAILED')) {
      setOptimisticStatus(null);
    }
    if (optimisticStatus === 'STOPPING' && liveStatus !== 'RUNNING') {
      setOptimisticStatus(null);
    }
    if (optimisticStatus === 'STOPPED' && liveStatus !== 'RUNNING' && liveStatus !== 'STOPPING') {
      setOptimisticStatus(null);
    }
  }, [optimisticStatus, liveStatus]);

  const deploySavedPipeline = async (id: string, name: string, catalogTid: string | null = getTid()) => {
    await deploy.mutateAsync({ id, catalogTid });
    message.success(isChengtianIntegration ? `设计已保存并发布：${name}。外部执行需另行授权。` : `已保存并部署:${name}`);
  };

  // ---- Save + deploy in-place ----
  const doSaveInPlace = async () => {
    if (!isCurrentSession()) return;
    if (save.isPending || deploy.isPending) return;
    const dsl = toDsl();
    if (currentPipelineId && currentPipelineName) {
      try {
        const saved = await save.mutateAsync({ id: currentPipelineId, name: currentPipelineName, dsl });
        setCurrentPipeline(saved.id, saved.name);
        await deploySavedPipeline(saved.id, saved.name);
      } catch (e: any) {
        message.error(`保存或部署失败: ${e?.response?.data?.error ?? e?.message ?? e}`);
        openErrorPanel();
      }
    } else {
      setSaveAsDraft(currentPipelineName ?? '');
      setOpenSaveAs(true);
    }
  };

  // ---- Ctrl+S / Cmd+S ----
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && (e.key === 's' || e.key === 'S')) {
        e.preventDefault();
        doSaveInPlace();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPipelineId, currentPipelineName, renderSessionRevision]);

  const startOptionsForDsl = (dsl = toDsl()): StartOptions => {
    const source = dsl.nodes.find((node) => node.category === 'source');
    const config = source?.config ?? {};
    return {
      syncMode: String(config.syncMode ?? 'FULL'),
      fullSyncStrategy: String(config.fullSyncStrategy ?? 'UPSERT'),
      deleteTargetData: Boolean(config.deleteTargetData),
    };
  };

  const syncModeText = (mode: string) => {
    if (mode === 'INCREMENTAL') return '增量同步';
    if (mode === 'FULL_THEN_INCR') return '首次全量后增量';
    if (mode === 'PERIODIC_FULL') return '定时全量同步';
    return '全量同步';
  };

  const startDeployedPipeline = async (pipelineId: string, options: StartOptions) => {
    const forceRebuild = options.deleteTargetData;
    message.loading({
      content: forceRebuild ? '正在按清空目标数据的设置重新部署并启动...' : '正在启动 NiFi 任务...',
      key: 'start-flow',
    });
    const res: any = await start.mutateAsync({ id: pipelineId, force: forceRebuild });
    setOptimisticStatus('RUNNING');
    completionWatchRef.current = options.syncMode === 'FULL'
      ? {
          pipelineId,
          baseline: statusWorkScore(statusQuery.data),
          armedAt: Date.now(),
          syncMode: options.syncMode,
        }
      : null;
    completionNotifiedRef.current = null;
    completionErrorNotifiedRef.current = null;
    void res;
    setSyncCompletion(null);
    message.destroy('start-flow');
    message.success(`已启动：${syncModeText(options.syncMode)}`);
  };

  const handleStart = async () => {
    if (!isCurrentSession()) return;
    if (!currentPipelineId) {
      message.warning('当前流程尚未部署，请先点击“保存并部署”。');
      return;
    }
    try {
      // The initial status poll can still be in flight when the toolbar first
      // renders. Resolve it on this click so a deployed flow never needs a
      // second click merely because the polling cache is not ready yet.
      const status = statusQuery.data ?? (await statusQuery.refetch()).data;
      const deployedVersionIsCurrent = Boolean(status?.deployed
        && status.currentHash
        && status.currentHash === status.lastDeployedHash);

      if (!status?.deployed) {
        message.warning('当前流程尚未部署，请先点击“保存并部署”。');
        return;
      }

      if (!deployedVersionIsCurrent) {
        message.warning('当前保存版本尚未部署，请先点击“保存并部署”，再启动流程。');
        return;
      }
      // Start exactly the deployed version. Unsaved canvas edits remain local
      // and do not turn a single click into a save/deploy confirmation chain.
      await startDeployedPipeline(currentPipelineId, startOptionsForDsl(currentPipelineQuery.data?.dsl));
    } catch (e: any) {
      message.destroy('start-flow');
      message.error(`启动失败: ${e?.response?.data?.error ?? e?.message ?? e}`);
      openErrorPanel();
    }
  };

  useEffect(() => {
    const watch = completionWatchRef.current;
    const data = statusQuery.data;
    if (!watch || !data || data.id !== watch.pipelineId) return;

    if (data.errors?.length && completionErrorNotifiedRef.current !== watch.pipelineId) {
      completionErrorNotifiedRef.current = watch.pipelineId;
      notification.error({
        message: '本次同步出现异常',
        description: '流程运行时产生错误，请打开错误详情查看具体节点与日志。',
        placement: 'topRight',
        duration: 8,
      });
      openErrorPanel();
      return;
    }

    if (Date.now() - watch.armedAt < 2000) return;
    const currentScore = statusWorkScore(data);
    if (
      completionNotifiedRef.current !== watch.pipelineId
      && currentScore > watch.baseline
      && statusIsSettled(data)
    ) {
      completionNotifiedRef.current = watch.pipelineId;
      completionWatchRef.current = null;
      const root = aggregateRoot(data.nifiStatus);
      const sent = numericMetric(root?.flowFilesSent);
      const out = numericMetric(root?.flowFilesOut);
      const transferred = numericMetric(root?.flowFilesTransferred);
      const flowFiles = Math.max(sent, out, transferred);
      const completedAt = Date.now();
      setSyncCompletion({
        pipelineId: watch.pipelineId,
        at: completedAt,
        flowFiles: flowFiles > 0 ? flowFiles : undefined,
        bytes: typeof root?.bytesSent === 'string' ? root.bytesSent : undefined,
        message: `本次全量同步已完成，完成时间 ${new Date(completedAt).toLocaleString()}${flowFiles > 0 ? `，处理 ${flowFiles} 个流文件` : ''}`,
      });
      notification.success({
        message: '本次全量同步已完成',
        description: flowFiles > 0
          ? `${new Date(completedAt).toLocaleString()}，已处理 ${flowFiles} 个流文件。`
          : `${new Date(completedAt).toLocaleString()}，当前队列 ${root?.queued ?? '0 (0 bytes)'}。`,
        placement: 'topRight',
        duration: 10,
      });
      if (watch.syncMode === 'FULL') {
        stop.mutateAsync(watch.pipelineId).catch(() => undefined);
      }
    }
  }, [statusQuery.data, notification, openErrorPanel, setSyncCompletion, stop]);

  const handleStop = async () => {
    if (!isCurrentSession()) return;
    if (!currentPipelineId) return;
    try {
      setOptimisticStatus('STOPPING');
      await stop.mutateAsync(currentPipelineId);
      setOptimisticStatus('STOPPED');
      message.success('已停止');
    } catch (e: any) {
      setOptimisticStatus(null);
      message.error(`停止失败: ${e?.response?.data?.error ?? e?.message ?? e}`);
    }
  };

  const handleOpenNifi = async () => {
    if (!currentPipelineId || !canOpenNative) {
      message.warning('当前版本尚未成功部署到 NiFi，请先保存并部署。');
      return;
    }
    // The native NiFi URL is resolved asynchronously. Open a blank tab while
    // the click still has user activation; otherwise Chromium treats the later
    // window.open call as a popup and silently blocks it.
    const nativeWindow = window.open('', '_blank');
    if (!nativeWindow) {
      message.error('浏览器拦截了原生 NiFi 页面，请允许本页面打开新窗口后重试');
      return;
    }
    try {
      const target = await getNifiUiLink(currentPipelineId);
      // The canvas is embedded below /haitong/, whereas the native UI is
      // served by the same public gateway's /nifi-ui proxy. Do not resolve
      // this browser navigation against VITE_APP_API_URL: that variable is a
      // build-time Vite dev-server target and may point at localhost or a
      // development service after the bundle reaches production.
      const nifiUiUrl = resolveNifiNativeUiUrl(
        target.url,
        String(apiClient.defaults.baseURL ?? ''),
        window.location.origin,
      );
      nativeWindow.opener = null;
      nativeWindow.location.replace(nifiUiUrl);
    } catch (error: any) {
      nativeWindow.close();
      message.error(error?.response?.data?.error ?? '当前流程尚未部署到 NiFi');
    }
  };

  // ---- Name dropdown ----
  const renameInline = () => {
    if (!isCurrentSession()) return;
    const name = nameDraft.trim();
    if (!name) {
      message.warning('名称不能为空');
      return;
    }
    if (currentPipelineId) {
      const dsl = toDsl();
      save
        .mutateAsync({ id: currentPipelineId, name, dsl })
        .then(async (saved) => {
          setCurrentPipeline(saved.id, saved.name);
          await deploySavedPipeline(saved.id, saved.name);
        })
        .catch((e) => {
          message.error(`重命名或部署失败: ${e?.response?.data?.error ?? e?.message ?? e}`);
          openErrorPanel();
        });
    } else {
      setCurrentPipeline(currentPipelineId, name);
    }
    setEditingName(false);
  };

  // const handleDelete = () => {
  //   if (!currentPipelineId) {
  //     modal.confirm({
  //       title: '清空画布?',
  //       content: '当前画布尚未保存,所有节点与连线将被移除。',
  //       okType: 'danger',
  //       onOk: () => clear(),
  //     });
  //     return;
  //   }
  //   modal.confirm({
  //     title: '删除流程?',
  //     content: '将永久删除该流程及其所有错误记录(部署到 NiFi 的资源也会一并撤除)。',
  //     okType: 'danger',
  //     okText: '删除',
  //     onOk: async () => {
  //       try {
  //         await del.mutateAsync(currentPipelineId);
  //         setCurrentPipeline(null, null);
  //         clear();
  //         message.success('已删除');
  //       } catch (e: any) {
  //         message.error(`删除失败: ${e?.message ?? e}`);
  //       }
  //     },
  //   });
  // };

  const nameMenu: MenuProps['items'] = [
    { key: 'rename', icon: <EditOutlined />, label: '重命名', onClick: () => {
      setNameDraft(currentPipelineName ?? '未命名流程');
      setEditingName(true);
    }},
    // { type: 'divider' },
    // { key: 'delete', icon: <DeleteOutlined />, danger: true, label: '删除流程', onClick: handleDelete },
  ];

  // ---- Open existing ----
  useEffect(() => {
    if (pendingOpenId && pendingOpen.data) {
      const p: any = pendingOpen.data;
      loadDsl(p.dsl);
      setCurrentPipeline(p.id, p.name);
      setOptimisticStatus(null);
      message.success(`已打开:${p.name}`);
      setPendingOpenId(null);
      setOpenOpener(false);
    }
    // Show error if pipeline load failed
    if (pendingOpenId && pendingOpen.isError && !pendingOpen.isLoading) {
      message.error('打开流程失败，请重试');
      setPendingOpenId(null);
    }
  }, [pendingOpenId, pendingOpen.data, pendingOpen.isError, pendingOpen.isLoading, loadDsl, setCurrentPipeline, message]);

  // ---- More menu actions ----
  const handleNew = () => {
    const proceed = () => {
      setOptimisticStatus(null);
      clear();
      message.info('已新建空白画布');
    };
    if ((toDsl().nodes ?? []).length > 0) {
      modal.confirm({
        title: '新建流程?',
        content: '将清空当前画布(未保存的修改会丢失)。',
        onOk: proceed,
      });
    } else {
      proceed();
    }
  };

  const handleSaveAs = () => {
    setSaveAsDraft((currentPipelineName ?? '未命名流程') + ' 副本');
    setOpenSaveAs(true);
  };

  const handleExport = () => {
    const dsl = toDsl();
    const payload = { name: currentPipelineName ?? '未命名流程', dsl };
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${payload.name}.json`;
    a.click();
    URL.revokeObjectURL(url);
    message.success('已导出 JSON');
  };

  const handleImportClick = () => fileInputRef.current?.click();
  const handleImportFile = async (file: File) => {
    try {
      const text = await file.text();
      const obj = JSON.parse(text);
      const dsl = obj.dsl ?? obj;
      if (!dsl || !Array.isArray(dsl.nodes)) {
        throw new Error('JSON 缺少 dsl.nodes');
      }
      loadDsl(dsl);
      if (obj.name) setCurrentPipeline(null, obj.name);
      message.success('已导入 JSON(未保存)');
    } catch (e: any) {
      message.error(`导入失败: ${e?.message ?? e}`);
    }
  };

  const handleClearCanvas = () => {
    modal.confirm({
      title: '清空画布?',
      content: '所有节点与连线将被移除(后端流程不会被删除)。',
      okType: 'danger',
      onOk: () => clear(),
    });
  };

  const handleCloseTab = () => {
    if (window.location.hash.slice(1).split('?')[0] === '/development/canvas') {
      requestIntegratedCanvasClose();
      return;
    }
    notifyClose(); // 通知父窗口即将关闭
    window.open('', '_self');
    window.close();
    window.setTimeout(() => {
      if (!window.closed) message.info('浏览器限制了脚本关闭当前页签，可直接使用浏览器页签上的关闭按钮');
    }, 120);
  };

  const moreMenu: MenuProps['items'] = [
    { key: 'new',     icon: <FileAddOutlined />,    label: '新建流程',     onClick: handleNew },
    { key: 'open',    icon: <FolderOpenOutlined />, label: '打开…',        onClick: () => setOpenOpener(true) },
    { key: 'saveAs',  icon: <CopyOutlined />,       label: '另存为…',      onClick: handleSaveAs },
    { type: 'divider' },
    { key: 'import',  icon: <ImportOutlined />,     label: '导入 JSON',    onClick: handleImportClick },
    { key: 'export',  icon: <ExportOutlined />,     label: '导出 JSON',    onClick: handleExport },
    { type: 'divider' },
    { key: 'clear',   icon: <ClearOutlined />, danger: true, label: '清空画布', onClick: handleClearCanvas },
  ];

  return (
    <div className="coze-toolbar">
      <Tooltip title="返回">
        <Button
          type="text"
          aria-label="返回并关闭流程画布"
          icon={<ArrowLeftOutlined />}
          className="coze-toolbar__back"
          onClick={handleCloseTab}
        />
      </Tooltip>
      <span className="coze-toolbar__brand">
        <span className="coze-toolbar__logo" aria-hidden="true">
          <svg viewBox="0 0 36 36" focusable="false">
            <path className="coze-toolbar__logo-sail" d="M8 25.5c6.2-9 12.5-13.3 19.8-14.8-1.8 7.2-6.6 12.2-14.3 15.1-2.2.8-4 .7-5.5-.3Z" />
            <path className="coze-toolbar__logo-current" d="M7.5 22.8c4.8-2.2 8.9-2 12.6.5 3 2 5.9 2 8.9-.1" />
            <path className="coze-toolbar__logo-current is-second" d="M8.2 27.3c3.2-1.1 6.1-.7 8.7 1 2.5 1.6 5.9 1.2 10.3-1.1" />
            <circle cx="12.8" cy="15" r="2.1" />
            <circle cx="21.6" cy="13.1" r="2.1" />
            <circle cx="18.4" cy="23.7" r="2.1" />
            <path className="coze-toolbar__logo-link" d="M14.8 14.6l4.7-.9M19 21.8l2-6.7" />
          </svg>
        </span>
        <span>{appConfig.brandName}</span>
      </span>
      <span style={{ width: 1, height: 20, background: '#f0f0f0', margin: '0 8px' }} />

      <div className="coze-toolbar__pipeline">
        {editingName ? (
          <Input
            autoFocus
            size="small"
            style={{ width: 220 }}
            value={nameDraft}
            onChange={(e) => setNameDraft(e.target.value)}
            onPressEnter={renameInline}
            onBlur={renameInline}
          />
        ) : (
          <Dropdown menu={{ items: nameMenu }} trigger={['click']}>
            <span className="coze-toolbar__name" title={currentPipelineName ?? '未命名流程'}>
              <span className="coze-toolbar__name-label">{currentPipelineName ?? '未命名流程'}</span>
              <DownOutlined className="coze-toolbar__name-arrow" />
            </span>
          </Dropdown>
        )}
        <Tooltip title={viz.clickable ? '点击查看错误详情' : viz.label}>
          <span
            onClick={() => { if (viz.clickable) openErrorPanel(); }}
            style={{
              fontSize: 12,
              color: viz.color,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              cursor: viz.clickable ? 'pointer' : 'default',
              marginTop: 2,
              userSelect: 'none',
            }}
          >
            {viz.spinning
              ? <LoadingOutlined style={{ fontSize: 10, color: viz.color }} spin />
              : <span style={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%', background: viz.color }} />}
            <span>{viz.label}</span>
            {viz.clickable && <span style={{ fontSize: 11, opacity: 0.7 }}>· 查看详情</span>}
          </span>
        </Tooltip>
      </div>

      <div style={{ flex: 1 }} />

      <span className={`coze-toolbar__state${isRunning ? ' is-running' : ''}`}>
        {isRunning ? <LineChartOutlined /> : <EditFilled />}
        {isRunning ? '监控状态' : '编辑状态'}
      </span>

      <div style={{ display: 'inline-flex', gap: 8 }}>
        <CanvasThemeToggle />
        <Tooltip title={isChengtianIntegration ? '外部运行端尚未配置到当前租户，画布只保存和发布设计版本' : statusQuery.isPending ? '正在确认 NiFi 部署状态' : statusQuery.isError ? '无法确认 NiFi 部署状态，请稍后重试' : canOpenNative ? '在 NiFi 原生页面中查看当前流程' : '当前版本尚未成功部署到 NiFi，请先保存并部署'}>
          <span><Button icon={<LinkOutlined />} disabled={isChengtianIntegration || !canOpenNative} onClick={handleOpenNifi}>{isChengtianIntegration ? '运行端' : 'NiFi'}</Button></span>
        </Tooltip>
        <Tooltip title={isChengtianIntegration ? "保存并发布设计版本 (Ctrl+S)" : "保存并部署 (Ctrl+S)"}>
          <Button
            icon={<SaveOutlined />}
            loading={save.isPending || deploy.isPending}
            disabled={isTransitioning || isRunning}
            onClick={doSaveInPlace}
          >
            {isChengtianIntegration ? '保存并发布' : '保存并部署'}
          </Button>
        </Tooltip>

        {isRunning ? (
          <Button
            danger
            icon={<PauseCircleOutlined />}
            loading={stop.isPending || (effectiveStatus as string) === 'STOPPING'}
            disabled={isTransitioning}
            onClick={handleStop}
          >
            停止
          </Button>
        ) : (
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            loading={start.isPending || effectiveStatus === 'DEPLOYING'}
            disabled={isChengtianIntegration || isTransitioning}
            onClick={handleStart}
          >
            {isChengtianIntegration ? '等待授权' : '启动'}
          </Button>
        )}

        <Dropdown menu={{ items: moreMenu }} trigger={['click']} placement="bottomRight">
          <Button icon={<MoreOutlined />} />
        </Dropdown>

        <Tooltip title="关闭页签">
          <Button icon={<CloseOutlined />} onClick={handleCloseTab} />
        </Tooltip>
      </div>

      <Modal
        getContainer={getNifiOverlayContainer}
        title={currentPipelineId ? '另存为' : '保存流程'}
        open={openSaveAs}
        onOk={async () => {
          if (!isCurrentSession()) return;
          const name = saveAsDraft.trim();
          if (!name) { message.warning('请填写流程名称'); return; }
          try {
            const isCopy = Boolean(currentPipelineId);
            const saved = await save.mutateAsync({ id: undefined, name, dsl: toDsl(), copy: isCopy });
            setCurrentPipeline(saved.id, saved.name);
            if (!isCopy) {
              window.dispatchEvent(new CustomEvent('haitong:nifi-pipeline-saved', {
                detail: { pipelineId: saved.id },
              }));
            }
            await deploySavedPipeline(saved.id, saved.name, isCopy ? null : getTid());
            setOpenSaveAs(false);
          } catch (e: any) {
            message.destroy('start-flow');
            message.error(`保存或部署失败: ${e?.response?.data?.error ?? e?.message ?? e}`);
            openErrorPanel();
          }
        }}
        onCancel={() => {
          setOpenSaveAs(false);
        }}
        confirmLoading={save.isPending || deploy.isPending}
      >
        <Input
          placeholder="流程名称"
          value={saveAsDraft}
          onChange={(e) => setSaveAsDraft(e.target.value)}
          autoFocus
        />
      </Modal>

      <Modal
        getContainer={getNifiOverlayContainer}
        title="打开流程"
        open={openOpener}
        onCancel={() => setOpenOpener(false)}
        footer={null}
        width={520}
        styles={{ body: { maxHeight: 400, overflowY: 'auto' } }}
      >
        {list.isLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}><LoadingOutlined /> 加载中...</div>
        ) : !list.data || list.data.length === 0 ? (
          <Empty description="暂无已保存流程" />
        ) : (
          <List
            size="small"
            dataSource={list.data as any[]}
            renderItem={(p: any) => (
              <List.Item
                actions={[
                  <Button
                    key="open"
                    type="link"
                    loading={pendingOpenId === p.id && pendingOpen.isLoading}
                    disabled={!!pendingOpenId}
                    onClick={() => setPendingOpenId(p.id)}
                  >
                    {pendingOpenId === p.id && pendingOpen.isLoading ? '加载中' : '打开'}
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={p.name}
                  description={
                    <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                      {p.id} · {p.status ?? '-'}{p.updatedAt ? ` · ${new Date(p.updatedAt).toLocaleString()}` : ''}
                    </span>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Modal>

      <input
        ref={fileInputRef}
        type="file"
        accept="application/json,.json"
        style={{ display: 'none' }}
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) handleImportFile(f);
          e.target.value = '';
        }}
      />
    </div>
  );
}
