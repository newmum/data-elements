import { useEffect, useMemo, useState } from 'react';
import { Drawer, Button, Tag, Table, Space, Empty, Tooltip } from 'antd';
import { LineChartOutlined, ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { fetchPipelineStatus } from '@/api/pipelines';
import { useCanvasStore } from '@/stores/canvasStore';
import { getNifiOverlayContainer } from './overlayContainer';
import { useSessionRevision, useTokenReady } from '@/api/bridgeSession';

interface ProcessorStatRow {
  key: string;
  name: string;
  type: string;
  state: string;
  flowFilesIn: string;
  flowFilesOut: string;
  bytesIn: string;
  bytesOut: string;
  tasks: string;
}

function flatProcessors(status: any): ProcessorStatRow[] {
  if (!status) return [];
  const rows: ProcessorStatRow[] = [];
  const walk = (pg: any) => {
    if (!pg) return;
    const procs = pg.processorStatusSnapshots ?? [];
    for (const p of procs) {
      const s = p.processorStatusSnapshot ?? p;
      rows.push({
        key: s.id,
        name: s.name,
        type: s.type ?? '',
        state: s.runStatus ?? '',
        flowFilesIn: s.input ?? '0',
        flowFilesOut: s.output ?? '0',
        bytesIn: s.read ?? '0 B',
        bytesOut: s.written ?? '0 B',
        tasks: s.tasks ?? '0',
      });
    }
    const groups = pg.processGroupStatusSnapshots ?? [];
    for (const g of groups) walk(g.processGroupStatusSnapshot ?? g);
  };
  // top-level: { processGroupStatus: { aggregateSnapshot: { ... } } }
  const root = status?.processGroupStatus?.aggregateSnapshot ?? status?.aggregateSnapshot ?? status;
  walk(root);
  return rows;
}

const stateColor: Record<string, string> = {
  Running: 'green',
  Stopped: 'default',
  Disabled: 'default',
  Invalid: 'red',
};

export default function StatusDrawer(props: {
  open?: boolean;
  onClose?: () => void;
  pipelineId?: string | null;
} = {}) {
  const [internalOpen, setInternalOpen] = useState(false);
  const currentPipelineIdFromStore = useCanvasStore((s) => s.currentPipelineId);

  const controlled = props.open !== undefined;
  const open = controlled ? !!props.open : internalOpen;
  const setOpen = (v: boolean) => {
    if (controlled) {
      if (!v) props.onClose?.();
    } else {
      setInternalOpen(v);
    }
  };
  const currentPipelineId = props.pipelineId !== undefined ? props.pipelineId : currentPipelineIdFromStore;
  const tokenReady = useTokenReady();
  const sessionRevision = useSessionRevision();

  const q = useQuery({
    enabled: tokenReady && open && !!currentPipelineId,
    queryKey: ['pipeline-status', currentPipelineId, sessionRevision],
    queryFn: () => fetchPipelineStatus(currentPipelineId!),
    refetchInterval: open ? 4000 : false,
  });

  const rows = useMemo(() => flatProcessors(q.data?.status), [q.data]);

  // Close drawer if pipeline pointer is cleared.
  useEffect(() => {
    if (!currentPipelineId) setOpen(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPipelineId]);

  return (
    <>
      {!controlled && (
        <Tooltip title="查看运行状态">
          <Button
            icon={<LineChartOutlined />}
            onClick={() => setOpen(true)}
            disabled={!currentPipelineId}
          >
            运行状态
          </Button>
        </Tooltip>
      )}
      <Drawer
        getContainer={getNifiOverlayContainer}
        title={
          <Space>
            <span>运行状态</span>
            {q.data?.deployed === false && <Tag>未部署</Tag>}
            {q.data?.deployed && <Tag color="blue">已部署</Tag>}
            <Button
              size="small"
              type="text"
              icon={<ReloadOutlined />}
              loading={q.isFetching}
              onClick={() => q.refetch()}
            />
          </Space>
        }
        placement="bottom"
        height={360}
        open={open}
        onClose={() => setOpen(false)}
      >
        {rows.length === 0 ? (
          <Empty description={q.data?.deployed === false ? '尚未部署到 NiFi' : '暂无处理器'} />
        ) : (
          <Table
            size="small"
            pagination={false}
            dataSource={rows}
            scroll={{ y: 240 }}
            columns={[
              { title: '处理器', dataIndex: 'name', width: 220 },
              {
                title: '状态',
                dataIndex: 'state',
                width: 100,
                render: (v) => <Tag color={stateColor[v] ?? 'default'}>{v}</Tag>,
              },
              { title: '输入', dataIndex: 'flowFilesIn', width: 120 },
              { title: '输出', dataIndex: 'flowFilesOut', width: 120 },
              { title: '读', dataIndex: 'bytesIn', width: 100 },
              { title: '写', dataIndex: 'bytesOut', width: 100 },
              { title: '任务数', dataIndex: 'tasks', width: 100 },
              {
                title: '类型',
                dataIndex: 'type',
                ellipsis: true,
                render: (v: string) => v.split('.').pop(),
              },
            ]}
          />
        )}
      </Drawer>
    </>
  );
}
