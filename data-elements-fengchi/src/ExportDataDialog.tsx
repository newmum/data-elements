import { useId, useRef, useState } from 'react';
import { DownloadOutlined } from '@ant-design/icons';
import DraggableModal from './DraggableModal';
import { buildExportFilename, createExportSnapshot, prepareTableExport, validateExportCount, type ExportFormat, type ExportSource, type PreparedExport } from './tableExport';
import './exportDataDialog.css';

export interface ExportDataDialogProps {
  open: boolean;
  title: string;
  columns: readonly string[];
  sources: readonly ExportSource[];
  initialScope?: string;
  onCancel: () => void;
  onConfirm: (file: PreparedExport) => void;
}

function ExportDataDialogSession({ title, columns, sources, initialScope, onCancel, onConfirm }: Omit<ExportDataDialogProps, 'open'>) {
  const [snapshot] = useState(() => createExportSnapshot({ title, columns, sources }));
  const [scope, setScope] = useState(() => snapshot.sources.find(source => source.key === initialScope)?.key ?? snapshot.sources[0]?.key ?? '');
  const [format, setFormat] = useState<ExportFormat>('csv');
  const [quantityMode, setQuantityMode] = useState<'all' | 'custom'>('all');
  const [quantity, setQuantity] = useState('1');
  const [error, setError] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const submitted = useRef(false);
  const id = useId();
  const source = snapshot.sources.find(item => item.key === scope);
  const available = source?.rows.length ?? 0;
  const selection = validateExportCount(quantityMode === 'all' ? 'all' : quantity, available);
  const validationError = !selection.ok ? selection.error : !snapshot.columns.length ? '没有可导出的字段。' : '';
  const filename = buildExportFilename(snapshot.title, format);

  function confirm() {
    if (!source || validationError || submitted.current) return;
    try {
      const file = prepareTableExport({ title: snapshot.title, columns: snapshot.columns, source, format, quantity: quantityMode === 'all' ? 'all' : quantity });
      submitted.current = true;
      setConfirmed(true);
      onConfirm(file);
    } catch (failure) {
      submitted.current = false;
      setConfirmed(false);
      setError(failure instanceof Error ? failure.message : '导出失败，请重试。');
    }
  }

  return <DraggableModal className="native-dialog export-data-dialog" open width={500}
    title={<span className="native-window-title"><DownloadOutlined />导出数据</span>}
    onCancel={onCancel} onOk={confirm} okText="确认导出" cancelText="取消"
    okButtonProps={{ disabled: Boolean(validationError) || confirmed }}>
    <div className="export-data-content">
      <div className="export-data-target"><span>数据来源</span><strong title={snapshot.title}>{snapshot.title}</strong></div>
      <div className="export-data-field">
        <label htmlFor={`${id}-scope`}>导出范围</label>
        <select id={`${id}-scope`} value={scope} onChange={event => { setScope(event.target.value); setError(''); }}>
          {snapshot.sources.map(item => <option key={item.key} value={item.key}>{item.label}（{item.rows.length} 条）</option>)}
        </select>
      </div>
      <fieldset className="export-data-quantity">
        <legend>导出数量</legend>
        <div className="export-data-quantity-controls">
          <label><input type="radio" name={`${id}-quantity-mode`} checked={quantityMode === 'all'} onChange={() => { setQuantityMode('all'); setError(''); }} />全部 {available} 条</label>
          <label><input type="radio" name={`${id}-quantity-mode`} checked={quantityMode === 'custom'} onChange={() => { setQuantityMode('custom'); setError(''); }} />前 N 条</label>
          <input aria-label="导出条数" aria-invalid={quantityMode === 'custom' && !selection.ok} aria-describedby={`${id}-feedback`} type="number" min={1} max={available} step={1}
            value={quantity} disabled={quantityMode !== 'custom' || !available} onChange={event => { setQuantity(event.target.value); setError(''); }} />
        </div>
      </fieldset>
      <div className="export-data-field">
        <label htmlFor={`${id}-format`}>文件格式</label>
        <select id={`${id}-format`} value={format} onChange={event => { setFormat(event.target.value as ExportFormat); setError(''); }}>
          <option value="csv">CSV（逗号分隔）</option>
          <option value="tsv">TSV（制表符分隔）</option>
          <option value="json">JSON（保留数据类型）</option>
        </select>
      </div>
      <div className="export-data-summary" role="status" aria-live="polite">
        <strong>将导出 {selection.ok ? selection.count : 0} 条记录 · {snapshot.columns.length} 列</strong>
        <span title={filename}>{filename}</span>
      </div>
      <p className="export-data-note">UTF-8 编码，按打开窗口时的数据与顺序导出。选择“前 N 条”时，从所选范围的第一条开始。</p>
      <p className="export-data-note">使用当前本地数据，包含已确认但未应用的修改，不访问真实数据库。</p>
      <p className="export-data-note">{format === 'json' ? 'JSON 保留数字、文本与 NULL。' : 'CSV / TSV 包含表头；NULL 输出为空单元格。可能被表格软件识别为公式的文本会加单引号保护；如需保留原值与类型，请选 JSON。'}</p>
      <div id={`${id}-feedback`} className="export-data-error" role={error || validationError ? 'alert' : undefined}>{error || validationError}</div>
    </div>
  </DraggableModal>;
}

/** Unmount on close so every reopening starts with a fresh snapshot and defaults. */
export default function ExportDataDialog({ open, ...props }: ExportDataDialogProps) {
  return open ? <ExportDataDialogSession {...props} /> : null;
}
