import { Button, Progress, Spin } from 'antd';

export interface ReportDeliveryProgress {
  tableKey: string;
  phase: 'preparing' | 'saving' | 'uploading' | 'waiting';
  completed: number;
  total?: number;
}

export default function DeliveryProgress({ progress, onRefresh, disabled = false }: {
  progress?: ReportDeliveryProgress;
  onRefresh?: () => void;
  disabled?: boolean;
}) {
  const phase = progress?.phase || 'waiting';
  const label = { preparing: '校验数据', saving: '保存信息', uploading: '上传文件', waiting: '等待回执' }[phase];
  const percent = progress && progress.total && (phase === 'preparing' || phase === 'uploading')
    ? Math.min(100, Math.floor(progress.completed / progress.total * 100)) : undefined;
  const detail = phase === 'preparing' && progress
    ? `已校验 ${progress.completed.toLocaleString()} / ${(progress.total || 0).toLocaleString()} 行`
    : phase === 'uploading' && progress ? `已上传 ${progress.completed.toLocaleString()} / ${(progress.total || 0).toLocaleString()} 字节`
      : phase === 'saving' ? '正在保存本次上报的登记信息'
        : '正在等待服务端文件投递回执；此阶段没有可用的百分比，成功不代表逐行入库确认';
  return <div className="report-row-progress" role="status" aria-live="polite" title={detail}>
    <div><span>{label}</span>{percent === undefined ? <Spin size="small" /> : <span>{percent}%</span>}</div>
    {percent === undefined
      ? <div className="report-row-progress-track" aria-hidden="true"><i /></div>
      : <Progress percent={percent} showInfo={false} size="small" aria-label={detail} />}
    {!progress && onRefresh && <Button type="link" size="small" disabled={disabled} onClick={onRefresh}>刷新状态</Button>}
  </div>;
}
