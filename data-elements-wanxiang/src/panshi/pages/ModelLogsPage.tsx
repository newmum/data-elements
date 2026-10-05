import { useMemo, useState } from 'react';
import { Button, Descriptions, Drawer, Select, Tag } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getSession } from '../../services/api';
import { Hero, NameCell, PTable, timeText } from '../components/ui';
import { useResource } from '../services/context';
import { modelLogRows, type ModelLogRow, type ModelLogStage } from './modelLogView';

const stageColors: Record<ModelLogStage, string> = {
  设计: 'blue', 标准化: 'cyan', 版本: 'purple', 物化: 'geekblue',
};

export function ModelLogsPage() {
  const { data, refresh } = useResource();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const requestedModelId = params.get('model');
  const modelId = data.state.models.some(model => model.id === requestedModelId) ? requestedModelId || '' : '';
  const [stage, setStage] = useState<ModelLogStage | ''>('');
  const [selected, setSelected] = useState<ModelLogRow | null>(null);
  const rows = useMemo(() => modelLogRows(data.state), [data.state]);
  const shown = rows.filter(row => (!modelId || row.modelId === modelId) && (!stage || row.stage === stage));
  const session = getSession();
  const actorName = (row: ModelLogRow) => {
    if (row.actorName?.trim()) return row.actorName.trim();
    const member = data.members.find(item =>
      String(item.principalId ?? item.id ?? '') === row.actorId);
    if (member) return String(member.name ?? member.realName ?? member.userName ?? row.actorId);
    if (row.actorId === session?.principalId || row.actorId === session?.user?.userName)
      return session.name || String(session.user?.userName ?? row.actorId);
    if (!row.actorId || /^(system|sys|scheduler|system-operation)$/i.test(row.actorId)) return '系统操作';
    return row.actorId;
  };

  return <>
    <Hero page="logs"/>
    <div className="ps-log-filters">
      <span>查看模型过程</span>
      <Select
        aria-label="筛选模型日志"
        value={modelId || undefined}
        placeholder="全部模型（含已归档）"
        allowClear showSearch optionFilterProp="label"
        onChange={value => setParams(value ? { model: value } : {})}
        options={data.state.models.map(model => ({
          value: model.id,
          label: `${model.name} · ${model.code}${model.state === 'ARCHIVED' ? '（已归档）' : ''}`,
        }))}
      />
      <Select
        aria-label="筛选模型阶段"
        value={stage || undefined}
        placeholder="全部阶段"
        allowClear
        onChange={value => setStage(value || '')}
        options={(['设计', '标准化', '版本', '物化'] as const).map(value => ({ value, label: value }))}
      />
      <span className="ps-log-count">{shown.length} 条过程记录</span>
    </div>
    <PTable
      rows={shown}
      onRefresh={refresh}
      searchText={row => `${row.modelName} ${row.modelCode} ${row.event} ${row.stage} ${row.detail} ${actorName(row)}`}
      emptyTitle="暂无模型过程记录"
      emptyDescription="创建、设计、标准化、冻结和物化模型后，操作过程会显示在这里。"
      columns={[
        { title: '发生时间', dataIndex: 'time', width: 170, render: (_, row) => timeText(row.time) },
        { title: '模型名称', dataIndex: 'modelName', width: 220,
          render: (_, row) => <NameCell name={row.modelName || '模型记录'} code={row.modelCode || undefined}/> },
        { title: '阶段', dataIndex: 'stage', width: 96,
          render: (_, row) => <Tag color={stageColors[row.stage]} variant="filled">{row.stage}</Tag> },
        { title: '操作事件', dataIndex: 'event', width: 150 },
        { title: '操作人', width: 120, render: (_, row) => <span title={row.actorId}>{actorName(row)}</span> },
        { title: '操作', width: 88,
          render: (_, row) => <Button type="link" onClick={() => setSelected(row)}>详情</Button> },
      ]}
    />
    <Drawer title="模型过程详情" size={480} open={!!selected} onClose={() => setSelected(null)}>
      {selected && <>
        <Descriptions column={1} size="small" items={[
          { key: 'model', label: '模型', children: <>{selected.modelName || '模型记录'}{selected.modelArchived && <Tag className="ps-log-archived">已归档</Tag>}</> },
          { key: 'code', label: '模型代码', children: selected.modelCode || '—' },
          { key: 'stage', label: '阶段', children: selected.stage },
          { key: 'event', label: '操作事件', children: selected.event },
          { key: 'time', label: '发生时间', children: timeText(selected.time) },
          { key: 'actor', label: '操作人', children: actorName(selected) },
          { key: 'detail', label: '操作说明', children: selected.detail || '这条历史记录只保存了操作和时间，未记录具体变更内容。' },
        ]}/>
        {selected.modelId && !selected.modelArchived && <Button type="primary" onClick={() => navigate(`/resource/models/${selected.modelId}/designer`)}>查看当前模型</Button>}
      </>}
    </Drawer>
  </>;
}
