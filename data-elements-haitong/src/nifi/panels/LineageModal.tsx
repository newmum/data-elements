import { useEffect, useMemo, useRef, useState } from 'react';
import { Modal, Spin, Empty, Descriptions, Button, App as AntdApp } from 'antd';
import { Graph } from '@antv/x6';
import { useLineageStore } from '@/stores/lineageStore';
import { fetchLineage, type LineageResponse } from '@/api/pipelines';
import { getNifiOverlayContainer } from './overlayContainer';
import { useOceanTheme } from '../../design/theme';

/**
 * Lineage visualization (NiFi provenance lineage).
 *
 * Layout: simple left-to-right column placement based on event millisTimestamp.
 * Detail panel on the right shows the selected event.
 */
export default function LineageModal() {
  const { dark } = useOceanTheme();
  const { pipelineId, nodeId, close } = useLineageStore();
  const visible = !!nodeId && !!pipelineId;
  const { message } = AntdApp.useApp();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const graphRef = useRef<Graph | null>(null);
  const [data, setData] = useState<LineageResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<any | null>(null);

  // Fetch lineage when opened
  useEffect(() => {
    if (!visible || !pipelineId || !nodeId) return;
    setLoading(true);
    setData(null);
    setSelected(null);
    fetchLineage(pipelineId, nodeId)
      .then((res) => setData(res))
      .catch((e: any) => message.error(`查询血缘失败: ${e?.response?.data?.error ?? e?.message ?? e}`))
      .finally(() => setLoading(false));
  }, [visible, pipelineId, nodeId, message]);

  // Render graph
  useEffect(() => {
    if (!visible || !data || !containerRef.current) return;
    const lineageNodes: any[] = Array.isArray(data.nodes) ? data.nodes : [];
    const lineageLinks: any[] = Array.isArray(data.links) ? data.links : [];
    if (lineageNodes.length === 0) return;

    if (graphRef.current) {
      graphRef.current.dispose();
      graphRef.current = null;
    }
    const g = new Graph({
      container: containerRef.current,
      autoResize: true,
      panning: true,
      mousewheel: { enabled: true, modifiers: ['ctrl'] },
      background: { color: dark ? '#141414' : '#ffffff' },
      grid: { visible: true, type: 'dot', args: { color: dark ? '#434343' : '#dedede', thickness: 1.5 } },
      interacting: { nodeMovable: false },
    });
    graphRef.current = g;

    // Compute simple LR layout: x by sorted timestamp, y by index within bucket.
    const sorted = [...lineageNodes].sort((a, b) => (a.millis ?? 0) - (b.millis ?? 0));
    const colWidth = 180;
    const rowHeight = 110;
    const xByTs = new Map<number, number>();
    let col = 0;
    for (const n of sorted) {
      const ts = n.millis ?? 0;
      if (!xByTs.has(ts)) xByTs.set(ts, col++);
    }
    const rowByCol = new Map<number, number>();
    sorted.forEach((n) => {
      const c = xByTs.get(n.millis ?? 0) ?? 0;
      const r = rowByCol.get(c) ?? 0;
      rowByCol.set(c, r + 1);
      const fill = n.eventType ? colorByEvent(n.eventType) : '#1677ff';
      g.addNode({
        id: n.id,
        x: 40 + c * colWidth,
        y: 20 + r * rowHeight,
        width: 80,
        height: 80,
        shape: 'ellipse',
        attrs: {
          body: { fill, stroke: '#fff', strokeWidth: 2 },
          label: { text: shorten(n.eventType ?? n.type ?? 'EVENT'), fill: '#fff', fontSize: 11 },
        },
        data: n,
      });
    });
    for (const l of lineageLinks) {
      try {
        g.addEdge({ source: l.sourceId ?? l.source, target: l.targetId ?? l.target,
          attrs: { line: { stroke: '#8c8c8c', strokeWidth: 1.5, targetMarker: { name: 'block', size: 6 } } } });
      } catch { /* skip malformed link */ }
    }
    g.zoomToFit({ padding: 20, maxScale: 1.5 });
    g.on('node:click', ({ node }) => setSelected(node.getData()));
    g.on('node:dblclick', ({ node }) => {
      const d = node.getData();
      message.info(`双击事件: ${d?.eventType ?? d?.id}`);
    });
    return () => { g.dispose(); graphRef.current = null; };
  }, [visible, data, message, dark]);

  const empty = data && (!data.nodes || data.nodes.length === 0);

  const detailPairs = useMemo(() => {
    if (!selected) return null;
    return [
      ['事件类型', selected.eventType],
      ['节点 id', selected.id],
      ['时间', selected.millis ? new Date(selected.millis).toLocaleString() : '-'],
      ['处理器', selected.componentName ?? '-'],
      ['FlowFile UUID', selected.flowFileUuid ?? '-'],
    ];
  }, [selected]);

  return (
    <Modal
      getContainer={getNifiOverlayContainer}
      title="数据血缘"
      open={visible}
      onCancel={close}
      footer={<Button onClick={close}>关闭</Button>}
      width={1100}
      destroyOnHidden
    >
      <div style={{ display: 'flex', height: 520, gap: 12 }}>
        <div style={{ flex: 1, position: 'relative', border: '1px solid var(--node-border)', borderRadius: 6 }}>
          {loading && (
            <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Spin tip="正在向 NiFi 查询血缘..." />
            </div>
          )}
          {empty && !loading && (
            <Empty description={data?.message ?? '暂无血缘数据'} style={{ marginTop: 80 }} />
          )}
          <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
        </div>
        <div style={{ width: 320, border: '1px solid var(--node-border)', borderRadius: 6, padding: 12, overflow: 'auto' }}>
          <h4 style={{ marginTop: 0 }}>事件详情</h4>
          {selected ? (
            <Descriptions size="small" column={1} bordered>
              {detailPairs?.map(([k, v]) => (
                <Descriptions.Item key={k as string} label={k as string}>
                  {String(v ?? '-')}
                </Descriptions.Item>
              ))}
            </Descriptions>
          ) : (
            <div style={{ color: '#8c8c8c', fontSize: 13 }}>点击左侧事件节点查看详情</div>
          )}
        </div>
      </div>
    </Modal>
  );
}

function shorten(s: string): string {
  if (!s) return '';
  return s.length > 8 ? s.slice(0, 8) : s;
}

function colorByEvent(t: string): string {
  switch (t) {
    case 'CREATE':       return '#52c41a';
    case 'RECEIVE':      return '#1677ff';
    case 'SEND':         return '#722ed1';
    case 'DROP':         return '#8c8c8c';
    case 'CONTENT_MODIFIED':
    case 'ATTRIBUTES_MODIFIED': return '#fa8c16';
    case 'ROUTE':        return '#13c2c2';
    case 'CLONE':        return '#eb2f96';
    case 'FORK':         return '#2f54eb';
    case 'JOIN':         return '#a0d911';
    default:             return '#1677ff';
  }
}
