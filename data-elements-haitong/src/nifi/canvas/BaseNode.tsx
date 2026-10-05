import { useMemo } from 'react';
import type { Node as X6Node } from '@antv/x6';
import {
  DatabaseOutlined,
  FunctionOutlined,
  BranchesOutlined,
  CloudUploadOutlined,
  AppstoreOutlined,
  FilterOutlined,
  SwapOutlined,
  ConsoleSqlOutlined,
  ExclamationCircleFilled,
  WarningFilled,
} from '@ant-design/icons';
import { Tooltip } from 'antd';
import type { CanvasNode } from '@/types/dsl';
import type { ComponentCategory, ComponentManifest, FieldSchema } from '@/types/manifest';
import { useCanvasStore } from '@/stores/canvasStore';
import { useComponentManifests } from '@/api/manifests';
import { useFlowErrors, type FlowError } from '@/api/pipelines';
import { useErrorPanelStore } from '@/stores/errorPanelStore';
import type { NodeRuntimeMetric } from '@/runtime/runtimeMetrics';
import { COMPONENT_ICONS } from '@/panels/databaseIcons';

const CATEGORY_ICON: Record<ComponentCategory, React.ReactNode> = {
  source: <DatabaseOutlined />,
  transform: <FunctionOutlined />,
  branch: <BranchesOutlined />,
  sink: <CloudUploadOutlined />,
};

const MANIFEST_ICON: Record<string, React.ReactNode> = {
  'transform.filter': <FilterOutlined />,
  'transform.field-mapping': <SwapOutlined />,
  'transform.field-enrichment': <SwapOutlined />,
  'transform.sql': <ConsoleSqlOutlined />,
};

interface NodeStatus {
  state: 'idle' | 'ready' | 'running' | 'success' | 'warning' | 'error';
}

function pickIcon(manifest: ComponentManifest | undefined, cat: ComponentCategory) {
  if (!manifest) return COMPONENT_ICONS[cat] ?? CATEGORY_ICON[cat] ?? <AppstoreOutlined />;
  return COMPONENT_ICONS[manifest.key] ?? COMPONENT_ICONS[cat] ?? MANIFEST_ICON[manifest.key] ?? CATEGORY_ICON[cat] ?? <AppstoreOutlined />;
}

function getCanvasPopupContainer(trigger: HTMLElement) {
  return (trigger.closest('.canvas-shell') as HTMLElement | null) ?? document.body;
}

function summarizeValue(v: unknown, field: FieldSchema): string {
  if (v === undefined || v === null || v === '') return '';
  if (field.type === 'password') return '••••••';
  if (field.type === 'switch') return v ? '是' : '否';
  if (field.type === 'select') {
    const option = field.options?.find((item) => String(item.value) === String(v));
    if (option) return option.label;
  }
  if (typeof v === 'object') {
    try {
      const s = JSON.stringify(v);
      return s.length > 40 ? s.slice(0, 40) + '…' : s;
    } catch {
      return String(v);
    }
  }
  const s = String(v);
  return s.length > 40 ? s.slice(0, 40) + '…' : s;
}

/**
 * Best-effort translation of common NiFi/Java error messages into a short Chinese reason.
 * The original message is still available in the ErrorPanel for full details.
 */
function translateError(msg: string | undefined | null): string {
  if (!msg) return '未知错误';
  const rules: { test: RegExp; cn: string }[] = [
    { test: /connection refused|cannot assign requested address/i, cn: '无法连接到目标服务（连接被拒绝）' },
    { test: /unknown host|name or service not known|nodename nor servname/i, cn: '主机名无法解析' },
    { test: /timed?out|timeout/i, cn: '连接或操作超时' },
    { test: /access denied|authentication failed|login failed|invalid credentials/i, cn: '认证失败（用户名或密码错误）' },
    { test: /no suitable driver|classnotfound.*driver|driver.*not found/i, cn: '找不到 JDBC 驱动类' },
    { test: /driver.*location.*not.*exist|driver.*file.*not.*exist|jdbc.*驱动.*不存在/i, cn: 'JDBC 驱动文件不存在' },
    { test: /table .* doesn'?t exist|relation .* does not exist|表.*不存在/i, cn: '表不存在' },
    { test: /column .* (not found|doesn'?t exist|未找到)/i, cn: '字段不存在' },
    { test: /syntax error|sql.*error/i, cn: 'SQL 语法错误' },
    { test: /required property .* is not set|is required/i, cn: '必填属性未配置' },
    { test: /controller service .* (is not enabled|disabled)/i, cn: '关联的控制器服务未启用' },
    { test: /invalid because/i, cn: '处理器配置校验失败' },
    { test: /nullpointer/i, cn: '空指针异常' },
    { test: /out of memory/i, cn: '内存不足' },
    { test: /permission denied|access is denied/i, cn: '权限不足' },
    { test: /file not found|no such file/i, cn: '文件不存在' },
    { test: /broken pipe|connection reset/i, cn: '连接被中断' },
  ];
  for (const r of rules) if (r.test.test(msg)) return r.cn;
  // Fallback: trim to a single short line
  const oneLine = msg.split(/\r?\n/)[0];
  return oneLine.length > 60 ? oneLine.slice(0, 60) + '…' : oneLine;
}

function deriveState(node: CanvasNode, manifest: ComponentManifest | undefined): NodeStatus['state'] {
  if (!manifest) return 'idle';
  const required = (manifest.fields ?? []).filter((f) => f.required);
  const missing = required.find((f) => {
    const v = node.config?.[f.key];
    return v === undefined || v === null || v === '';
  });
  return missing ? 'idle' : 'ready';
}

function incrementalSummary(config: Record<string, unknown> | undefined): string | null {
  const mode = String(config?.syncMode ?? 'FULL');
  if (mode !== 'INCREMENTAL' && mode !== 'FULL_THEN_INCR') return null;
  const primary = String(config?.incrementalColumn ?? '').trim();
  if (!primary) return null;
  const tie = String(config?.tieBreakerColumn ?? '').trim();
  return tie ? `${primary} + ${tie}` : primary;
}

/**
 * IMPORTANT: When registered via @antv/x6-react-shape, the component receives
 * `{ node, graph }` where `node` is the x6 graph cell. Read our CanvasNode
 * payload from `node.getData().node`.
 */
interface BaseNodeProps {
  node: X6Node;
}

export default function BaseNode({ node: cell }: BaseNodeProps) {
  const data = cell.getData<{ node?: CanvasNode; runtime?: NodeRuntimeMetric; mode?: string } | undefined>();
  const cn = data?.node;
  const runtime = data?.runtime;
  const selectedNodeId = useCanvasStore((s) => s.selectedNodeId);
  const focusNodeId = useCanvasStore((s) => s.focusNodeId);
  const focusNonce = useCanvasStore((s) => s.focusNodeNonce);
  const pipelineId = useCanvasStore((s) => s.currentPipelineId);
  const syncCompletion = useCanvasStore((s) => s.syncCompletion);
  const openErrorPanel = useErrorPanelStore((s) => s.open);
  const { data: manifests } = useComponentManifests();
  const { data: flowErrors } = useFlowErrors(pipelineId);
  const manifest = useMemo(
    () => manifests?.find((m) => m.key === cn?.manifestKey),
    [manifests, cn?.manifestKey],
  );

  // Errors targeting this node
  const myErrors: FlowError[] = useMemo(
    () => (flowErrors ?? []).filter((e) => e.nodeId === cn?.id),
    [flowErrors, cn?.id],
  );
  const hasErrorBadge = myErrors.some((e) => e.level === 'ERROR');
  const hasWarningBadge = !hasErrorBadge && myErrors.some((e) => e.level === 'WARNING');

  if (!cn) {
    return null;
  }

  const cat = cn.category;
  const selected = selectedNodeId === cn.id;
  const flashing = focusNodeId === cn.id; // re-renders on focusNonce change
  void focusNonce;
  const status: NodeStatus | undefined = runtime
    ? {
        state: runtime.state === 'warning' ? 'warning' : runtime.state,
      }
    : undefined;
  const derivedState = deriveState(cn, manifest);
  const effectiveState: NodeStatus['state'] =
    hasErrorBadge ? 'error' : (status?.state ?? derivedState);
  const isError = effectiveState === 'error';
  const isRunning = effectiveState === 'running';

  const fields = manifest?.fields ?? [];
  const previewFields = fields.slice(0, 3);
  const incrSummary = incrementalSummary(cn.config);
  const showCompletion = cat === 'sink' && !hasErrorBadge && syncCompletion?.pipelineId === pipelineId;
  const completionTime = syncCompletion ? new Date(syncCompletion.at).toLocaleString() : '';

  return (
    <div
      className={[
        'coze-node',
        `coze-node--${cat}`,
        selected ? 'is-selected' : '',
        isError ? 'is-error' : '',
        isRunning ? 'is-running' : '',
        hasWarningBadge ? 'is-warning' : '',
        effectiveState === 'warning' ? 'is-warning' : '',
        flashing ? 'is-flashing' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={{
        position: 'relative',
      }}
    >
      {/* Header */}
      <div className="coze-node__header">
        <div className="coze-node__icon">{pickIcon(manifest, cat)}</div>
        <div className="coze-node__title">{cn.label || manifest?.label || '未命名'}</div>
        <span
          className={`coze-node__status-dot is-${effectiveState}`}
          title={`状态:${effectiveState}`}
        />
      </div>

      {(hasErrorBadge || hasWarningBadge) && (
        <Tooltip
          placement="leftTop"
          autoAdjustOverflow
          getPopupContainer={getCanvasPopupContainer}
          classNames={{ root: 'canvas-error-tooltip' }}
          title={
            <div style={{ maxWidth: 280 }}>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>
                {hasErrorBadge ? `错误 (${myErrors.filter((e) => e.level === 'ERROR').length})` : `警告 (${myErrors.length})`}
              </div>
              {myErrors.slice(0, 3).map((e) => (
                <div key={e.id} style={{ fontSize: 12, marginBottom: 4 }}>
                  <div>• {translateError(e.message)}</div>
                </div>
              ))}
              {myErrors.length > 3 && (
                <div style={{ fontSize: 12, opacity: 0.7 }}>...还有 {myErrors.length - 3} 条</div>
              )}
              <div style={{ fontSize: 11, opacity: 0.65, marginTop: 6 }}>点击查看完整错误详情</div>
            </div>
          }
        >
          <span
            onClick={(ev) => { ev.stopPropagation(); openErrorPanel(); }}
            style={{
              position: 'absolute',
              top: -8,
              right: -8,
              width: 18,
              height: 18,
              borderRadius: '50%',
              background: hasErrorBadge ? '#ff4d4f' : '#faad14',
              color: '#fff',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 12,
              cursor: 'pointer',
              boxShadow: '0 1px 4px rgba(0,0,0,0.25)',
              zIndex: 5,
            }}
          >
            {hasErrorBadge ? <ExclamationCircleFilled /> : <WarningFilled />}
          </span>
        </Tooltip>
      )}

      {/* Body */}
      <div className="coze-node__body">
        {showCompletion && syncCompletion && (
          <div className="coze-node__completion" title={syncCompletion.message}>
            <div className="coze-node__completion-title">本次全量同步已完成</div>
            <div className="coze-node__completion-desc">
              {completionTime}
              {typeof syncCompletion.flowFiles === 'number' ? ` · ${syncCompletion.flowFiles} 个流文件` : ''}
              {syncCompletion.bytes ? ` · ${syncCompletion.bytes}` : ''}
            </div>
          </div>
        )}
        <div>
          <div className="coze-node__section-title">⚙ 配置</div>
          <div className="coze-node__section-content">
            {previewFields.length === 0 ? (
              <div className="coze-node__empty-hint">无配置项</div>
            ) : (
              previewFields.map((f) => {
                const text = summarizeValue(cn.config?.[f.key], f);
                return (
                  <div key={f.key} className="coze-node__field-row">
                    <span className="coze-node__field-dot" />
                    {f.label ? <span className="coze-node__field-key">{f.label}</span> : null}
                    <span className="coze-node__field-value">
                      {text || <span className="coze-node__empty-hint">未填写</span>}
                    </span>
                  </div>
                );
              })
            )}
            {fields.length > 3 && (
              <div className="coze-node__empty-hint" style={{ fontSize: 11 }}>
                +{fields.length - 3} 项配置 · 双击查看
              </div>
            )}
          </div>
        </div>

        {cat !== 'source' && (
          <div>
            <div className="coze-node__section-title">📥 输入</div>
            <div className="coze-node__section-content">
              <div className="coze-node__field-row">
                <span className="coze-node__field-dot" />
                <span className="coze-node__field-value">FlowFile (records)</span>
              </div>
            </div>
          </div>
        )}
        {cat !== 'sink' && (
          <div>
            <div className="coze-node__section-title">📤 输出</div>
            <div className="coze-node__section-content">
              <div className="coze-node__field-row">
                <span className="coze-node__field-dot" />
                <span className="coze-node__field-value">FlowFile (records)</span>
              </div>
            </div>
          </div>
        )}
        {incrSummary && (
          <div className="coze-node__watermark-row" title="增量字段">
            <span>水位</span>
            <strong>{incrSummary}</strong>
          </div>
        )}
      </div>
    </div>
  );
}
