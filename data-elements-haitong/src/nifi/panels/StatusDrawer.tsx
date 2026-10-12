import { useServerSort } from '../../components/serverSort';
import { useEffect, useState } from 'react';
import { Alert, Drawer, Button, Tag, Table, Space, Empty, Tooltip } from 'antd';
import { LineChartOutlined, ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { fetchProcessorStatusPage } from '@/api/pipelines';
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
  const [page,setPage]=useState(1),[size,setSize]=useState(20);const order=useServerSort(()=>setPage(1));
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
    queryKey: ['pipeline-status-page', currentPipelineId, sessionRevision,page,size,order.sort],
    queryFn: () => fetchProcessorStatusPage(currentPipelineId!,{page,size,...order.sort}),
    refetchInterval: open ? 4000 : false,
  });

  const rows:ProcessorStatRow[]=q.data?.list||[];

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
        {q.error && <Alert type="error" showIcon message={q.error instanceof Error?q.error.message:"读取处理器状态失败"}/> }
        {rows.length === 0 ? (
          <Empty description={q.data?.deployed === false ? '尚未部署到 NiFi' : '暂无处理器'} />
        ) : (
          <Table
            size="small"
            rowKey="key" loading={q.isFetching} onChange={(_p,_f,sorter,extra)=>{if(extra.action==='sort')order.change(sorter);}} pagination={{current:page,pageSize:size,total:q.data?.total||0,showSizeChanger:true,pageSizeOptions:[20,50,100],onChange:(p,n)=>{setPage(p);setSize(n);}}}
            dataSource={rows}
            scroll={{ y: 240 }}
            columns={[
              { title: '处理器', dataIndex: 'name', width: 220 },
              {
                title: '状态',
                dataIndex: 'state',
                width: 100,
                render: (v:string) => <Tag color={stateColor[v] ?? 'default'}>{v}</Tag>,
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
            ].map(column=>({...column,key:column.dataIndex,sorter:column.dataIndex!=='state'&&column.dataIndex!=='type',sortOrder:order.sort.sortField===column.dataIndex?(order.sort.sortDir==='asc'?'ascend' as const:'descend' as const):null}))}
          />
        )}
      </Drawer>
    </>
  );
}
