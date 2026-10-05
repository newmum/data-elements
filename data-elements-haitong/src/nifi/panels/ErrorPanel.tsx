import { useMemo, useState } from 'react';
import { Drawer, Segmented, Empty, Button, App as AntdApp, Tag, Tooltip } from 'antd';
import { CopyOutlined, AimOutlined, ClearOutlined, ReloadOutlined, CloseOutlined } from '@ant-design/icons';
import { useErrorPanelStore } from '@/stores/errorPanelStore';
import { useCanvasStore } from '@/stores/canvasStore';
import { getNifiOverlayContainer } from './overlayContainer';
import {
  useFlowErrors,
  useClearFlowErrors,
  type FlowError,
  type ErrorPhase,
} from '@/api/pipelines';

type FilterValue = 'ALL' | ErrorPhase;

function relativeTime(iso: string): string {
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return iso;
  const diff = Math.floor((Date.now() - t) / 1000);
  if (diff < 5) return '刚刚';
  if (diff < 60) return `${diff}秒前`;
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`;
  return new Date(iso).toLocaleString();
}

function bandColor(e: FlowError): string {
  if (e.level === 'WARNING') return '#faad14';
  switch (e.phase) {
    case 'VALIDATION': return '#fa8c16'; // 橙
    case 'DEPLOYMENT': return '#ff4d4f'; // 红
    case 'RUNTIME':    return '#cf1322'; // 深红
  }
}

function phaseLabel(p: ErrorPhase): string {
  return p === 'VALIDATION' ? '配置错误' : p === 'DEPLOYMENT' ? '部署错误' : '运行错误';
}

export default function ErrorPanel() {
  const visible = useErrorPanelStore((s) => s.visible);
  const close = useErrorPanelStore((s) => s.close);
  const pipelineId = useCanvasStore((s) => s.currentPipelineId);
  const focusNode = useCanvasStore((s) => s.focusNode);
  const openDrawer = useCanvasStore((s) => s.openDrawer);
  const { message } = AntdApp.useApp();
  const [filter, setFilter] = useState<FilterValue>('ALL');
  const [height, setHeight] = useState(420);

  const errorsQuery = useFlowErrors(pipelineId);
  const clearMut = useClearFlowErrors();

  const list = errorsQuery.data ?? [];
  const counts = useMemo(() => {
    const c: Record<FilterValue, number> = { ALL: list.length, VALIDATION: 0, DEPLOYMENT: 0, RUNTIME: 0 };
    for (const e of list) c[e.phase]++;
    return c;
  }, [list]);
  const filtered = filter === 'ALL' ? list : list.filter((e) => e.phase === filter);

  const copyToClipboard = async (text: string) => {
    if (navigator.clipboard && window.isSecureContext) {
      try {
        await navigator.clipboard.writeText(text);
        message.success('已复制错误信息');
        return;
      } catch {
        // clipboard API failed, fall back to execCommand
      }
    }
    // Fallback: use textarea + execCommand
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
      document.execCommand('copy');
      message.success('已复制错误信息');
    } catch {
      message.error('复制失败');
    } finally {
      document.body.removeChild(textarea);
    }
  };

  const handleCopy = (e: FlowError) => {
    const txt = [
      `[${phaseLabel(e.phase)}/${e.level}] ${e.message}`,
      e.nodeLabel ? `节点: ${e.nodeLabel}` : null,
      e.fieldKey ? `字段: ${e.fieldKey}` : null,
      e.detail ? `详情: ${e.detail}` : null,
      e.suggestion ? `建议: ${e.suggestion}` : null,
      `时间: ${e.occurredAt}`,
    ].filter(Boolean).join('\n');
    copyToClipboard(txt);
  };

  const handleLocate = (e: FlowError) => {
    if (!e.nodeId) {
      message.info('该错误未关联到具体节点');
      return;
    }
    focusNode(e.nodeId);
    openDrawer(e.nodeId);
  };

  const handleClear = async () => {
    if (!pipelineId) return;
    try {
      await clearMut.mutateAsync(pipelineId);
      message.success('已清空错误记录');
    } catch (err: any) {
      message.error(`清空失败: ${err?.message ?? err}`);
    }
  };

  return (
    <Drawer
      getContainer={getNifiOverlayContainer}
      className="error-panel-drawer"
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span>错误与告警</span>
          <Tag color="default">{counts.ALL}</Tag>
          <div style={{ flex: 1 }} />
          <Button size="small" icon={<ReloadOutlined />} onClick={() => errorsQuery.refetch()} />
          <Button size="small" icon={<ClearOutlined />} danger onClick={handleClear} disabled={!pipelineId || counts.ALL === 0}>
            清空
          </Button>
        </div>
      }
      placement="bottom"
      height={height}
      open={visible}
      onClose={close}
      closeIcon={null}
      mask={false}
      extra={(
        <Tooltip title="关闭">
          <Button size="small" type="text" icon={<CloseOutlined />} onClick={close} />
        </Tooltip>
      )}
      styles={{ body: { padding: 12, display: 'flex', flexDirection: 'column', minHeight: 0 } }}
    >
      <div
        className="error-panel-resizer"
        onMouseDown={(event) => {
          event.preventDefault();
          const startY = event.clientY;
          const startHeight = height;
          const onMove = (moveEvent: MouseEvent) => {
            const next = startHeight + startY - moveEvent.clientY;
            setHeight(Math.min(Math.max(next, 260), Math.floor(window.innerHeight * 0.85)));
          };
          const onUp = () => {
            window.removeEventListener('mousemove', onMove);
            window.removeEventListener('mouseup', onUp);
          };
          window.addEventListener('mousemove', onMove);
          window.addEventListener('mouseup', onUp);
        }}
      />
      <Segmented
        value={filter}
        onChange={(v) => setFilter(v as FilterValue)}
        options={[
          { value: 'ALL',        label: `全部 (${counts.ALL})` },
          { value: 'DEPLOYMENT', label: `部署 (${counts.DEPLOYMENT})` },
          { value: 'RUNTIME',    label: `运行 (${counts.RUNTIME})` },
          { value: 'VALIDATION', label: `配置 (${counts.VALIDATION})` },
        ]}
        style={{ marginBottom: 12 }}
      />
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {filtered.length === 0 ? (
          <Empty description="暂无错误" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ marginTop: 32 }} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {filtered.map((e) => (
              <div
                key={e.id}
                style={{
                  display: 'flex',
                  borderRadius: 6,
                  background: '#fafafa',
                  border: '1px solid #f0f0f0',
                  overflow: 'hidden',
                }}
              >
                <div style={{ width: 4, background: bandColor(e) }} />
                <div style={{ flex: 1, padding: '8px 12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                    <Tag color={e.level === 'ERROR' ? 'red' : 'orange'} style={{ margin: 0 }}>
                      {e.level === 'ERROR' ? '错误' : '警告'}
                    </Tag>
                    <Tag style={{ margin: 0 }}>{phaseLabel(e.phase)}</Tag>
                    {e.nodeLabel && (
                      <Tag color="blue" style={{ margin: 0 }}>{e.nodeLabel}</Tag>
                    )}
                    {e.fieldKey && (
                      <Tag color="purple" style={{ margin: 0 }}>{e.fieldKey}</Tag>
                    )}
                    <span style={{ flex: 1 }} />
                    <Tooltip title={e.occurredAt}>
                      <span style={{ fontSize: 12, color: '#8c8c8c' }}>{relativeTime(e.occurredAt)}</span>
                    </Tooltip>
                  </div>
                  <div style={{ fontSize: 13, color: '#262626', wordBreak: 'break-word' }}>{e.message}</div>
                  {e.detail && (
                    <div style={{ fontSize: 12, color: '#595959', marginTop: 4, wordBreak: 'break-word' }}>
                      {e.detail}
                    </div>
                  )}
                  {e.suggestion && (
                    <div style={{ fontSize: 12, color: '#1677ff', marginTop: 4 }}>
                      建议: {e.suggestion}
                    </div>
                  )}
                  <div style={{ marginTop: 6, display: 'flex', gap: 8 }}>
                    <Button size="small" icon={<CopyOutlined />} onClick={() => handleCopy(e)}>
                      复制错误
                    </Button>
                    {e.nodeId && (
                      <Button size="small" type="link" icon={<AimOutlined />} onClick={() => handleLocate(e)}>
                        定位到节点
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </Drawer>
  );
}
