import { Alert, Checkbox, Radio, Select, Space, Typography } from 'antd';
import { syncModes, type SyncSettings } from '../utils/syncSettings';

export default function SyncSettingsEditor({ value, onChange, targetNames }: {
  value: SyncSettings;
  onChange: (value: SyncSettings) => void;
  targetNames: string[];
}) {
  const full = ['FULL', 'FULL_THEN_INCR'].includes(value.syncMode);
  const truncate = value.syncMode === 'PERIODIC_FULL' && value.fullSyncStrategy === 'TRUNCATE_RELOAD';
  return <Space direction="vertical" className="canvas-sync-editor" size={24} style={{ width: '100%' }}>
    <Typography.Text strong>同步方式</Typography.Text>
    <Radio.Group className="canvas-sync-modes" value={value.syncMode} onChange={event => {
      onChange({ ...value, syncMode: event.target.value, deleteTargetData: false });
    }}>
        {syncModes.map(item => <Radio className="canvas-sync-mode" key={item.value} value={item.value}>
          <div className="canvas-sync-mode__title">{item.label}
            {item.value === 'FULL_THEN_INCR' && <span className="canvas-sync-mode__default">默认</span>}
          </div><div className="canvas-sync-mode__description">{item.description}</div>
        </Radio>)}
    </Radio.Group>
    {value.syncMode === 'PERIODIC_FULL' && <>
      <Typography.Text>全量写入策略</Typography.Text>
      <Select style={{ width: '100%' }} value={value.fullSyncStrategy} onChange={strategy => {
        onChange({ ...value, fullSyncStrategy: strategy });
      }} options={[
        { value: 'UPSERT', label: '有则更新、无则新增（UPSERT）' },
        { value: 'TRUNCATE_RELOAD', label: '每轮清空目标表再重载（MySQL / 达梦）' },
      ]} />
      <Typography.Text type="secondary">执行时间在来源节点的“调度策略”中配置；UPSERT 需要目标表具有业务主键或唯一键。</Typography.Text>
    </>}
    {full && <div className="canvas-sync-cleanup"><Checkbox checked={value.deleteTargetData} onChange={event => {
      onChange({ ...value, deleteTargetData: event.target.checked });
    }}>启动前删除目标表存量数据</Checkbox></div>}
    {(value.deleteTargetData || truncate) && <Alert type="warning" showIcon message={truncate ? '每轮同步都会清空目标表' : '本次启动会清空目标表'} description={<>
      <div>目标表：{targetNames.join('、') || '未配置目标表'}</div>
      <div>已有数据将被删除；清理失败时不会继续抽取。</div>
    </>} />}
  </Space>;
}
