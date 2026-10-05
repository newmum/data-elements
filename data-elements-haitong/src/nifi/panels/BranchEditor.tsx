import { Button, Input, Space, Typography } from 'antd';
import { BranchesOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { CanvasNode } from '@/types/dsl';
import { ConditionBuilder, upstreamColumnOptions, type BusinessCondition } from './ConditionBuilder';

export interface BranchRoute { id: string; name: string; conditions: BusinessCondition[] }

function newRoute(index: number): BranchRoute {
  return { id: `route_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`, name: `分支${index + 1}`, conditions: [] };
}

export default function BranchEditor({ routes, onChange, sourceNodes = [], defaultRouteName, onDefaultRouteNameChange }: {
  routes: BranchRoute[]; onChange: (routes: BranchRoute[]) => void; sourceNodes?: CanvasNode[];
  defaultRouteName: string; onDefaultRouteNameChange: (value: string) => void;
}) {
  const columns = upstreamColumnOptions(sourceNodes);
  const update = (index: number, patch: Partial<BranchRoute>) => onChange(routes.map((route, i) => i === index ? { ...route, ...patch } : route));
  return (
    <div className="branch-editor">
      <div className="branch-editor__heading">
        <Typography.Title level={5}><BranchesOutlined /> 设置数据分流规则</Typography.Title>
        <Typography.Text type="secondary">每个分支独立判断；一条数据满足多个分支时会同时进入多个出口。画布连线会依次使用尚未连接的分支。</Typography.Text>
      </div>
      {routes.map((route, index) => (
        <section className="branch-editor__route" key={route.id}>
          <div className="branch-editor__route-title">
            <Space><span className="branch-editor__route-index">{index + 1}</span><Input value={route.name} placeholder="分支名称" onChange={(event) => update(index, { name: event.target.value })} /></Space>
            <Button danger type="text" icon={<DeleteOutlined />} onClick={() => onChange(routes.filter((_, i) => i !== index))}>删除分支</Button>
          </div>
          <ConditionBuilder value={route.conditions} onChange={(conditions) => update(index, { conditions })} columns={columns} />
        </section>
      ))}
      <Button icon={<PlusOutlined />} onClick={() => onChange([...routes, newRoute(routes.length)])}>添加分支</Button>
      <section className="branch-editor__default">
        <Typography.Text strong>其他数据出口</Typography.Text>
        <Input value={defaultRouteName} onChange={(event) => onDefaultRouteNameChange(event.target.value)} placeholder="例如：其他、异常、待复核" />
        <Typography.Text type="secondary">不满足任何分支条件的数据会从这里输出。</Typography.Text>
      </section>
    </div>
  );
}
