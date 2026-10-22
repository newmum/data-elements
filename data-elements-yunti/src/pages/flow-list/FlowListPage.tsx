import { CopyOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Card, Input, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCopyFlow, useCreateFlow, useDeleteFlow, useFlows } from '@/services/flows';
import type { FlowSummary } from '@/services/types';

const statusColor: Record<FlowSummary['status'], string> = {
  draft: 'default',
  published: 'green',
  offline: 'orange',
  deleted: 'red',
};

export default function FlowListPage() {
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();
  const [keyword, setKeyword] = useState('');
  const { data = [], isLoading } = useFlows();
  const createFlow = useCreateFlow();
  const copyFlow = useCopyFlow();
  const deleteFlow = useDeleteFlow();

  const filtered = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    return data.filter((flow) => !q || flow.name.toLowerCase().includes(q) || flow.group.toLowerCase().includes(q));
  }, [data, keyword]);

  return (
    <div style={{ padding: 24 }}>
      <Card
        title={<Typography.Title level={3} style={{ margin: 0 }}>编排管理</Typography.Title>}
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            loading={createFlow.isPending}
            onClick={async () => {
              const flow = await createFlow.mutateAsync(`新建编排 ${data.length + 1}`);
              navigate(`/editor/${flow.flow.flowId}`);
            }}
          >
            新建编排
          </Button>
        }
      >
        <Space style={{ marginBottom: 16 }}>
          <Input.Search allowClear placeholder="搜索名称或分组" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        </Space>
        <Table
          rowKey="flowId"
          loading={isLoading}
          dataSource={filtered}
          columns={[
            { title: '名称', dataIndex: 'name' },
            { title: '分组', dataIndex: 'group' },
            {
              title: '状态',
              dataIndex: 'status',
              render: (status: FlowSummary['status']) => <Tag color={statusColor[status]}>{status}</Tag>,
            },
            { title: '版本', dataIndex: 'version', width: 80 },
            { title: '调用量', dataIndex: 'calls', width: 100 },
            { title: '更新时间', dataIndex: 'updatedAt' },
            {
              title: '操作',
              width: 220,
              render: (_, record) => (
                <Space>
                  <Button size="small" icon={<EditOutlined />} onClick={() => navigate(`/editor/${record.flowId}`)}>
                    编辑
                  </Button>
                  <Button
                    size="small"
                    icon={<CopyOutlined />}
                    onClick={async () => {
                      await copyFlow.mutateAsync(record.flowId);
                      void message.success('已复制');
                    }}
                  />
                  <Popconfirm
                    title="删除编排"
                    description="删除后会同步移除已发布的运行服务。"
                    onConfirm={() => deleteFlow.mutate(record.flowId)}
                  >
                    <Button size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}
