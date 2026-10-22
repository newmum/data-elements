import { ApiOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Card, Form, Input, Modal, Popconfirm, Space, Switch, Table, Tag, Typography } from 'antd';
import { useState } from 'react';
import {
  useCreateRuntimeNode,
  useDeleteRuntimeNode,
  useCheckRuntimeNodeHealth,
  useRuntimeNodes,
  useUpdateRuntimeNode,
  type SaveRuntimeNodeInput,
} from '@/services/runtimeNodes';
import type { RuntimeNode } from '@/services/types';

export default function RuntimeNodePage() {
  const { message } = AntdApp.useApp();
  const { data = [], isLoading } = useRuntimeNodes();
  const createNode = useCreateRuntimeNode();
  const updateNode = useUpdateRuntimeNode();
  const deleteNode = useDeleteRuntimeNode();
  const checkHealth = useCheckRuntimeNodeHealth();
  const [editing, setEditing] = useState<RuntimeNode | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<SaveRuntimeNodeInput>();

  const openEditor = (node?: RuntimeNode) => {
    setEditing(node ?? null);
    form.setFieldsValue(
      node ? {
        name: node.name,
        businessDomain: node.businessDomain,
        serviceType: node.serviceType,
        baseUrl: node.baseUrl,
        managementUrl: node.managementUrl ?? '',
        deployToken: '',
        enabled: node.enabled,
        defaultNode: node.defaultNode,
        localNode: node.localNode,
      } : {
        name: '',
        businessDomain: '',
        serviceType: '',
        baseUrl: 'http://127.0.0.1:8088',
        managementUrl: 'http://127.0.0.1:8088',
        deployToken: '',
        enabled: true,
        defaultNode: false,
        localNode: false,
      },
    );
    setOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing) await updateNode.mutateAsync({ id: editing.id, input: values });
    else await createNode.mutateAsync(values);
    setOpen(false);
    void message.success('发布节点已保存');
  };

  return (
    <div style={{ padding: 24 }}>
      <Card
        title={<Typography.Title level={3} style={{ margin: 0 }}>发布节点管理</Typography.Title>}
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor()}>新增节点</Button>}
      >
        <Table
          rowKey="id"
          loading={isLoading}
          dataSource={data}
          columns={[
            { title: '节点名称', dataIndex: 'name' },
            { title: '业务域', dataIndex: 'businessDomain', width: 140 },
            { title: '服务类型', dataIndex: 'serviceType', width: 140 },
            { title: '服务地址', dataIndex: 'baseUrl' },
            { title: '管理地址', dataIndex: 'managementUrl', render: (v: string | null) => v || '-' },
            { title: '部署令牌', dataIndex: 'deployTokenSet', width: 100, render: (v: boolean) => <Tag color={v ? 'purple' : 'default'}>{v ? '已配置' : '未配置'}</Tag> },
            {
              title: '状态',
              width: 170,
              render: (_, record) => (
                <Space>
                  {record.defaultNode && <Tag color="blue">默认</Tag>}
                  {record.localNode && <Tag color="green">本机</Tag>}
                  <Tag color={record.enabled ? 'success' : 'default'}>{record.enabled ? '启用' : '停用'}</Tag>
                </Space>
              ),
            },
            {
              title: '操作',
              width: 190,
              render: (_, record) => (
                <Space>
                  <Button
                    size="small"
                    icon={<ApiOutlined />}
                    loading={checkHealth.isPending}
                    onClick={async () => {
                      const result = await checkHealth.mutateAsync(record.id);
                      if (result.ok) void message.success(`${record.name} 健康，${result.latencyMs}ms`);
                      else void message.error(`${record.name} 异常：${result.message}`);
                    }}
                  />
                  <Button size="small" icon={<EditOutlined />} onClick={() => openEditor(record)} />
                  <Popconfirm title="删除发布节点" disabled={record.localNode} onConfirm={() => deleteNode.mutate(record.id)}>
                    <Button size="small" danger disabled={record.localNode} icon={<DeleteOutlined />} />
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </Card>
      <Modal title={editing ? '编辑发布节点' : '新增发布节点'} open={open} onCancel={() => setOpen(false)} onOk={save}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="节点名称" rules={[{ required: true }]}>
            <Input placeholder="华东订单服务节点" />
          </Form.Item>
          <Form.Item name="businessDomain" label="业务域" rules={[{ required: true }]}>
            <Input placeholder="订单域 / 会员域 / 风控域" />
          </Form.Item>
          <Form.Item name="serviceType" label="服务类型" rules={[{ required: true }]}>
            <Input placeholder="查询服务 / 集成服务 / 报表服务" />
          </Form.Item>
          <Form.Item name="baseUrl" label="服务访问地址" rules={[{ required: true }]}>
            <Input placeholder="http://10.0.0.10:8088" />
          </Form.Item>
          <Form.Item name="managementUrl" label="管理部署地址">
            <Input placeholder="http://10.0.0.10:8088" />
          </Form.Item>
          <Form.Item name="deployToken" label="部署令牌">
            <Input.Password placeholder={editing?.deployTokenSet ? '已配置，留空表示不修改' : '远程节点部署令牌'} />
          </Form.Item>
          <Space>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="defaultNode" label="默认发布节点" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="localNode" label="本机节点" valuePropName="checked">
              <Switch disabled={editing?.localNode} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  );
}
