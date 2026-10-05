import { Alert, Button, Tag } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { integrationNodesApi, type IntegrationNode } from '../../api/integrationNodes';
import PageHero from '../../components/PageHero';
import { DataTable, InsightStrip, Panel } from '../../components/Common';

/** Cross-network execution/receipts need a real transport. Never project ingress runs into them. */
export default function CrossNetworkPage() {
  const navigate = useNavigate();
  const query = useQuery({ queryKey: ['platform', 'cross-network', 'nodes'], queryFn: integrationNodesApi.accessOptions, refetchInterval: 60000 });
  const nodes = query.data?.nodes || [];
  const networks = query.data?.networks || [];
  return <div className="ht-page">
    <PageHero kicker="任务运维" title="跨网传输" description="核查不同网络的数据接入节点，为跨网发送、签收和目标落地准备真实链路。" kind="cross" tags={['网络分区', '接入节点', '回执待接入']} />
    <Alert type="warning" showIcon message="跨网执行与回执服务尚未提供" description="目前没有实际跨网传输服务，也没有发送、签收或目标写入回执来源。本页仅展示已登记的 NiFi 节点及其网络归属；普通接入任务不会被计作跨网传输。" action={<Button size="small" onClick={() => navigate('/ops/clusters')}>维护节点</Button>} />
    {query.isError && <Alert type="error" showIcon message="网络节点读取失败" description={query.error instanceof Error ? query.error.message : '请检查数据中台接口与会话'} action={<Button size="small" onClick={() => void query.refetch()}>重试</Button>} />}
    <InsightStrip items={[
      { label: '可选网络', value: networks.length, icon: 'cross', hint: '来自平台网络选项' },
      { label: '已启用节点', value: nodes.filter(node => node.enabled).length, icon: 'clusters', hint: '当前租户接入节点', tone: 'cyan' },
      { label: '跨网签收', value: '—', icon: 'warning', hint: '尚无实际回执来源', tone: 'amber' },
    ]} />
    <Panel title="网络节点" extra={<Button onClick={() => void query.refetch()} loading={query.isFetching}>刷新</Button>}>
      <DataTable<IntegrationNode> rowKey="tid" loading={query.isLoading} dataSource={nodes} pagination={false} locale={{ emptyText: '当前租户暂无已登记的 NiFi 节点' }} columns={[
        { title: '节点名称', width: 230, render: (_, node) => node.nodeName },
        { title: '所属网络', width: 180, render: (_, node) => node.networkName || node.networkCode || '未指定' },
        { title: '节点编码', width: 170, render: (_, node) => node.nodeCode || '—' },
        { title: '状态', width: 110, render: (_, node) => <Tag color={node.enabled ? 'success' : 'default'}>{node.enabled ? '已启用' : '未启用'}</Tag> },
        { title: '网络默认', width: 110, render: (_, node) => node.isDefault ? '是' : '否' },
        { title: '操作', fixed: 'right', width: 100, render: () => <Button type="link" onClick={() => navigate('/ops/clusters')}>节点详情</Button> },
      ]} />
      <p className="helper">接入节点可作为跨网链路的配置基础；真正的传输批次、签收和目标提交，需要后续接入执行服务及可信回执。</p>
    </Panel>
  </div>;
}
