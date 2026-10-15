import { lazy, Suspense, useState, useEffect, useRef } from 'react';
import { Alert, Result, Button, App } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import {
  ApartmentOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  LoadingOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import Toolbar from '@/panels/Toolbar';
import Canvas from '@/canvas/Canvas';
import { useComponentManifests } from '@/api/manifests';
import { useCanvasStore } from '@/stores/canvasStore';
import { useErrorPanelStore } from '@/stores/errorPanelStore';
import { useLineageStore } from '@/stores/lineageStore';
import { FIELD_MAPPING_DEFAULT } from '@/stores/canvasStore';
import {
  arrangeInitialPipelineLayout,
  arrangeLegacyGeneratedPipelineLayout,
  normalizeCanvasDsl,
} from '@/types/dsl';
import {
  getTid,
  getAccessTaskId,
  getPipelineId,
  notifyCanvasContentError,
  notifyCanvasContentReady,
} from '@/api/iframeBridge';
import { fetchPipelineTemplate, fetchTaskChain, fetchPipelineById } from '@/api/pipelines';
import { getSessionRevision } from '@/api/bridgeSession';
import { useTaskChainStore } from '../stores/taskChainStore';

// These tools are only needed after the canvas is interactive. Keep their
// editors, CodeMirror languages and metadata forms out of the first paint.
const ConfigDrawer = lazy(() => import('@/panels/ConfigDrawer'));
const ErrorPanel = lazy(() => import('@/panels/ErrorPanel'));
const LineageModal = lazy(() => import('@/panels/LineageModal'));

export default function EditorPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const manifestQuery = useComponentManifests();
  // Allow user to skip the error screen and continue with empty manifests
  const [forceContinue, setForceContinue] = useState(false);
  const [canvasDataLoading, setCanvasDataLoading] = useState(() => Boolean(getPipelineId() || getAccessTaskId() || getTid()));
  const loadDsl = useCanvasStore((s) => s.loadDsl);
  const setCurrentPipeline = useCanvasStore((s) => s.setCurrentPipeline);
  const currentPipelineId = useCanvasStore((s) => s.currentPipelineId);
  const setPendingAutoRecommend = useCanvasStore((s) => s.setPendingAutoRecommend);
  const setChain = useTaskChainStore((s) => s.setChain);
  const chain = useTaskChainStore((s) => s.chain);
  const drawerNodeId = useCanvasStore((s) => s.drawerNodeId);
  const errorPanelVisible = useErrorPanelStore((s) => s.visible);
  const lineageNodeId = useLineageStore((s) => s.nodeId);
  // useRef 的 .current 不会被 HMR / StrictMode 重置，防重请求
  const fetchStartedRef = useRef(false);

  useEffect(() => {
    const accessTaskId = getAccessTaskId();
    const tid = getTid();
    const pipelineId = getPipelineId();

    // accessTaskId 可能是字符串 "null"，需排除这种情况
    const hasAccessTask = !!accessTaskId && accessTaskId !== 'null';

    if ((!pipelineId && !hasAccessTask && !tid) || fetchStartedRef.current) {
      if (!pipelineId && !hasAccessTask && !tid) setCanvasDataLoading(false);
      return;
    }
    fetchStartedRef.current = true;
    const revision = getSessionRevision();
    let active = true;
    const isCurrent = () => active && revision === getSessionRevision();
    const loadChain = () => queryClient.fetchQuery({
      queryKey: ['task-chain', hasAccessTask ? accessTaskId : pipelineId, revision],
      queryFn: async () => {
        try { return await fetchTaskChain(hasAccessTask ? { accessTaskId } : { pipelineId: pipelineId! }); }
        catch (error: any) {
          // Generic independent flows have no access-task relationship; keep their single canvas.
          if (hasAccessTask || !pipelineId || error?.response?.status !== 404) throw error;
          const pipeline = await fetchPipelineById(pipelineId);
          return { rootSourceTableId: '', selectedPipelineId: pipeline.id, links: [], nativeCanvas: { mode: 'NONE' as const },
            items: [{ taskId: '', taskName: pipeline.name, sourceTableId: '', sourceDbId: '', sourceDbName: '', sourceTableName: '', pipeline }] };
        }
      },
      staleTime: 5_000,
    });

    if (pipelineId || hasAccessTask) {
      // Related task designs arrive in one authorized read. Never load a pipeline per lane.
      loadChain()
        .then((chain) => {
          if (!isCurrent()) return;
          if (!Array.isArray(chain?.items) || !chain.items.length || chain.items.length > 100) {
            throw new Error('任务链路返回格式无效或超过100项，请刷新重试');
          }
          const normalized = { ...chain, items: chain.items.map(item => ({ ...item,
            pipeline: { ...item.pipeline, dsl: arrangeLegacyGeneratedPipelineLayout(normalizeCanvasDsl(item.pipeline.dsl)) } })) };
          const selected = normalized.items.find(item => item.pipeline.id === chain.selectedPipelineId);
          if (!selected) throw new Error('任务链路缺少当前流程，请刷新重试');
          setChain(normalized);
          normalized.items.forEach(item => {
            queryClient.setQueryData(['pipeline', item.pipeline.id, revision], item.pipeline);
          });
          loadDsl(selected.pipeline.dsl);
          setCurrentPipeline(selected.pipeline.id, selected.pipeline.name);
        })
        .catch((e: any) => {
          if (!isCurrent()) return;
          const errorMessage = e?.response?.data?.error ?? e?.message ?? '画布数据加载失败';
          message.error('画布数据加载失败，请刷新重试');
          notifyCanvasContentError(String(errorMessage));
        })
        .finally(() => {
          if (isCurrent()) setCanvasDataLoading(false);
        });
    } else {
      // 未配置场景：直接用模板初始化画布
      const templateTid = tid || '';
      fetchPipelineTemplate(templateTid)
        .then((template) => {
          if (!isCurrent()) return;
          if (template?.id) {
            loadDsl(template.dsl);
            setCurrentPipeline(template.id, template.name);
            message.success('已加载已保存的任务流程');
            return;
          }
          // A template has not been saved or manually positioned yet.  Repair
          // legacy responses that put all initial nodes at the same coordinate.
          const dsl = arrangeInitialPipelineLayout(normalizeCanvasDsl(template?.dsl));
          // 清空模板中可能携带的字段映射信息，改为自动推荐
          const cleaned = {
            ...dsl,
            nodes: dsl.nodes.map((n) =>
              ['transform.field-mapping', 'transform.field-enrichment'].includes(n.manifestKey)
                ? { ...n, config: { ...n.config, mappings: FIELD_MAPPING_DEFAULT } }
                : n,
            ),
          };
          loadDsl(cleaned);
          setPendingAutoRecommend(true);
          message.success('画布模板加载成功');
        })
        .catch((e: any) => {
          if (!isCurrent()) return;
          const errorMessage = e?.response?.data?.error ?? e?.message ?? '画布模板加载失败';
          console.warn('加载画布模板失败:', errorMessage);
          notifyCanvasContentError(String(errorMessage));
        })
        .finally(() => {
          if (isCurrent()) setCanvasDataLoading(false);
        });
    }
    return () => { active = false; fetchStartedRef.current = false; };
  }, [loadDsl, setCurrentPipeline, setChain, message, queryClient]);

  useEffect(() => {
    if (!manifestQuery.isLoading && !canvasDataLoading && !manifestQuery.isError) {
      notifyCanvasContentReady();
    }
  }, [manifestQuery.isLoading, manifestQuery.isError, canvasDataLoading]);

  useEffect(() => {
    if (manifestQuery.isError && !manifestQuery.data) {
      notifyCanvasContentError('组件清单加载失败，请检查后端服务后重试');
    }
  }, [manifestQuery.isError, manifestQuery.data]);

  // Keep the parent-to-canvas transition visually continuous while manifests and DSL load.
  if ((manifestQuery.isLoading || canvasDataLoading) && !forceContinue) {
    return (
      <div className="nifi-loading-page">
        <div className="nifi-page-skeleton" aria-label="正在读取流程画布数据">
          <div className="nifi-page-skeleton__toolbar"><i /><span /><span /><b /></div>
          <div className="nifi-page-skeleton__workspace">
            <div className="nifi-page-skeleton__copy">
              <span className="nifi-page-skeleton__main-icon"><LoadingOutlined /></span>
              <div>
                <strong>正在准备数据接入画布</strong>
                <small>{canvasDataLoading ? '正在读取任务配置并回填流程节点' : '正在加载可用的数据处理组件'}</small>
              </div>
            </div>
            <div className="nifi-page-skeleton__pipeline" aria-hidden="true">
              <div className="nifi-page-skeleton__step is-active"><span><DatabaseOutlined /></span><b>读取任务配置</b></div>
              <div className="nifi-page-skeleton__track"><i /></div>
              <div className="nifi-page-skeleton__step"><span><ApartmentOutlined /></span><b>装配处理节点</b></div>
              <div className="nifi-page-skeleton__track is-later"><i /></div>
              <div className="nifi-page-skeleton__step"><span><CloudServerOutlined /></span><b>生成流程画布</b></div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Manifests error with no data — show retry UI
  if (manifestQuery.isError && !manifestQuery.data && !forceContinue) {
    return (
      <div style={{
        height: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--canvas-bg, #f5f7fa)',
      }}>
        <Result
          status="warning"
          title="组件清单加载失败"
          subTitle="无法获取组件清单数据，部分功能可能不可用。请检查网络连接后重试。"
          extra={[
            <Button
              key="retry"
              type="primary"
              icon={<ReloadOutlined />}
              onClick={() => manifestQuery.refetch()}
              loading={manifestQuery.isFetching}
            >
              重新加载
            </Button>,
            <Button
              key="continue"
              onClick={() => setForceContinue(true)}
            >
              继续使用（功能受限）
            </Button>,
          ]}
        />
      </div>
    );
  }

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', background: 'var(--canvas-bg)' }}>
      <Toolbar />
      {chain?.relationshipUnavailable && !chain.readOnly && chain.selectedPipelineId === currentPipelineId && <Alert type="info" showIcon message="已打开当前任务保存的画布，可继续编辑"
        description="任务关联链路暂不可用。可修改组件、属性和连线；保存时保留你的配置，不自动恢复原来源或目标。" />}
      {chain?.readOnly && chain.selectedPipelineId === currentPipelineId && <Alert type="warning" showIcon message="已打开当前任务保存的画布（只读）"
        description={`关联链路暂不可用：${chain.warnings?.join('；') || '任务关联需要核对'}。请修复任务与登记记录的关联后重新打开；当前不会自动改写流程配置。`} />}
      <div className="canvas-workarea">
        <div className="canvas-workarea__graph"><Canvas /></div>
        <Suspense fallback={null}>{drawerNodeId && <ConfigDrawer />}</Suspense>
        <Suspense fallback={null}>{errorPanelVisible && <ErrorPanel />}</Suspense>
        <Suspense fallback={null}>{lineageNodeId && <LineageModal />}</Suspense>
      </div>
    </div>
  );
}
