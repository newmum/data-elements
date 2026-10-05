import { lazy, Suspense, useState, useEffect, useRef } from 'react';
import { Result, Button, App } from 'antd';
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
import { fetchPipelineTemplate, fetchAccessTaskDetail, fetchPipelineById } from '@/api/pipelines';
import { getSessionRevision } from '@/api/bridgeSession';

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
  const setPendingAutoRecommend = useCanvasStore((s) => s.setPendingAutoRecommend);
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
    const loadPipeline = (id: string) => queryClient.fetchQuery({
      queryKey: ['pipeline', id, revision],
      queryFn: () => fetchPipelineById(id),
      staleTime: 5_000,
    });
    const loadAccessTask = (id: string) => queryClient.fetchQuery({
      queryKey: ['access-task-detail', id, revision],
      queryFn: () => fetchAccessTaskDetail(id),
      staleTime: 5_000,
    });

    if (pipelineId) {
      loadPipeline(pipelineId)
        .then((pipeline) => {
          if (!isCurrent()) return;
          loadDsl(arrangeLegacyGeneratedPipelineLayout(normalizeCanvasDsl(pipeline?.dsl)));
          setCurrentPipeline(pipeline.id, pipeline.name);
          message.success('已加载当前租户的集成任务设计');
        })
        .catch((e: any) => {
          if (!isCurrent()) return;
          const errorMessage = e?.response?.data?.detail ?? e?.response?.data?.error ?? e?.message ?? '画布数据加载失败';
          message.error('画布数据加载失败，请刷新重试');
          notifyCanvasContentError(String(errorMessage));
        })
        .finally(() => { if (isCurrent()) setCanvasDataLoading(false); });
    } else if (hasAccessTask) {
      // 已配置场景：先查任务详情拿到 pipelineId，再加载画布
      loadAccessTask(accessTaskId)
        .then((res) => {
          if (!isCurrent()) return;
          // The task API is deployed against both camelCase and snake_case mappings.
          // Prefer the normalized field while accepting an unchanged legacy response.
          const pipelineId = String(res.data?.pipelineId ?? res.data?.pipeline_id ?? '').trim();
          if (!pipelineId) {
            throw new Error('接入任务尚未关联流程，无法读取画布数据');
          }
          return loadPipeline(pipelineId).then((pipeline) => {
            if (!isCurrent()) return;
            loadDsl(arrangeLegacyGeneratedPipelineLayout(normalizeCanvasDsl(pipeline?.dsl)));
            setCurrentPipeline(pipeline.id, pipeline.name);
            message.success('画布数据加载成功');
          });
        })
        .catch((e: any) => {
          if (!isCurrent()) return;
          const errorMessage = e?.response?.data?.error ?? e?.message ?? '画布数据加载失败';
          console.warn('画布回填失败:', errorMessage);
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
  }, [loadDsl, setCurrentPipeline, message, queryClient]);

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
      <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        <Canvas />
        <Suspense fallback={null}>{drawerNodeId && <ConfigDrawer />}</Suspense>
        <Suspense fallback={null}>{errorPanelVisible && <ErrorPanel />}</Suspense>
        <Suspense fallback={null}>{lineageNodeId && <LineageModal />}</Suspense>
      </div>
    </div>
  );
}
