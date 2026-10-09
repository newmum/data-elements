import { Alert, Checkbox, Radio, Select, Space, Typography } from 'antd';
import { syncModes, type SyncSettings } from '../utils/syncSettings';

export default function SyncSettingsEditor({ value, onChange, targetNames, acknowledged, onAcknowledge }: {
  value: SyncSettings;
  onChange: (value: SyncSettings) => void;
  targetNames: string[];
  acknowledged: boolean;
  onAcknowledge: (value: boolean) => void;
}) {
  const full = ['FULL', 'FULL_THEN_INCR'].includes(value.syncMode);
  const truncate = value.syncMode === 'PERIODIC_FULL' && value.fullSyncStrategy === 'TRUNCATE_RELOAD';
  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Radio.Group value={value.syncMode} onChange={event => {
      onAcknowledge(false);
      onChange({ ...value, syncMode: event.target.value, deleteTargetData: false });
    }}>
      <Space direction="vertical" size={12}>
        {syncModes.map(item => <Radio key={item.value} value={item.value}>
          <strong>{item.label}</strong><div style={{ paddingLeft: 0, color: 'var(--ant-color-text-secondary, #888)', fontSize: 12 }}>{item.description}</div>
        </Radio>)}
      </Space>
    </Radio.Group>
    {value.syncMode === 'PERIODIC_FULL' && <>
      <Typography.Text>全量写入策略</Typography.Text>
      <Select style={{ width: '100%' }} value={value.fullSyncStrategy} onChange={strategy => {
        onAcknowledge(false); onChange({ ...value, fullSyncStrategy: strategy });
      }} options={[
        { value: 'UPSERT', label: '有则更新、无则新增（UPSERT）' },
        { value: 'TRUNCATE_RELOAD', label: '每轮清空目标表再重载（MySQL / 达梦）' },
      ]} />
      <Alert type="info" showIcon message="执行时间在来源节点的“调度策略”中配置；UPSERT 需要目标表具有业务主键或唯一键。" />
    </>}
    {full && <Checkbox checked={value.deleteTargetData} onChange={event => {
      onAcknowledge(false); onChange({ ...value, deleteTargetData: event.target.checked });
    }}>本次启动前删除目标表存量数据</Checkbox>}
    {(value.deleteTargetData || truncate) && <Alert type="warning" showIcon message={truncate ? '每轮同步都会清空目标表' : '本次启动会清空目标表'} description={<>
      <div>目标表：{targetNames.join('、') || '未配置目标表'}</div>
      <div>已有数据将被删除；清理失败时不会继续抽取。</div>
      <Checkbox checked={acknowledged} onChange={event => onAcknowledge(event.target.checked)}>我确认上述目标表及清空范围</Checkbox>
    </>} />}
  </Space>;
}
