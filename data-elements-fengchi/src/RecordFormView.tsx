import {useEffect, useId, useRef, useState} from 'react';
import {KeyOutlined, LeftOutlined, RightOutlined, StepBackwardOutlined, StepForwardOutlined} from '@ant-design/icons';
import type {Cell, Column, Row, TableDef} from './data';
import './recordForm.css';
import {FieldTypeIcon, getColumnLabel} from './ColumnHeader';

export type RecordFormViewProps = {
  table: TableDef;
  rows: Row[];
  selectedId: Cell | undefined;
  onSelect: (id: Cell) => void;
  onChange: (id: Cell, column: string, value: Cell) => void;
  isModified?: (row: Row, columnName: string) => boolean;
  onDraftChange?: (dirty: boolean) => void;
};

const numericType = /^(?:tinyint|smallint|mediumint|bigint|int|integer|decimal|numeric|float|double|real|number|boolean|bool|year)\b/i;
const integerType = /^(?:tinyint|smallint|mediumint|bigint|int|integer)\b/i;
const displayValue = (value: Cell) => value === null ? '' : String(value);

/** Raw drafts stay dirty even when parsing would fail or normalize the text. */
export function isRecordDraftDirty(value: Cell, draft: string, isNull: boolean): boolean {
  return isNull !== (value === null) || (!isNull && draft !== displayValue(value));
}

/** Report only aggregate transitions, so one clean field cannot hide another draft. */
export function createRecordDraftTracker(notify: (dirty: boolean) => void) {
  const fields = new Set<string>();
  return {
    update(column: string, dirty: boolean) {
      const before = fields.size > 0;
      if (dirty) fields.add(column);
      else fields.delete(column);
      if (before !== (fields.size > 0)) notify(fields.size > 0);
    },
    clear() {
      const dirty = fields.size > 0;
      fields.clear();
      if (dirty) notify(false);
    },
  };
}

export function getRecordIndex(rows: Row[], selectedId: Cell | undefined): number {
  if (!rows.length) return -1;
  const index = rows.findIndex(row => row.id === selectedId);
  return index < 0 ? 0 : index;
}

export function parseRecordValue(column: Column, text: string, isNull: boolean): {value: Cell; error?: never} | {error: string; value?: never} {
  if (isNull) return column.nullable ? {value: null} : {error: '此字段不允许 NULL'};
  if (!numericType.test(column.type.trim())) return {value: text};
  const trimmed = text.trim();
  if (!trimmed) return column.nullable ? {value: null} : {error: '请输入数值'};
  if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(trimmed)) return {error: '请输入有效数值'};
  const value = Number(trimmed);
  if (!Number.isFinite(value)) return {error: '数值超出有效范围'};
  if (integerType.test(column.type.trim()) && !Number.isSafeInteger(value)) return {error: '请输入有效范围内的整数'};
  if (/\bunsigned\b/i.test(column.type) && value < 0) return {error: '请输入大于或等于 0 的数值'};
  return {value};
}

function RecordField({column, value, modified, onDraftChange, onCommit}: {column: Column; value: Cell; modified: boolean; onDraftChange: (column: string, dirty: boolean) => void; onCommit: (value: Cell) => void}) {
  const inputId = useId();
  const [draft, setDraft] = useState(() => displayValue(value));
  const [isNull, setIsNull] = useState(value === null);
  const [error, setError] = useState('');
  const lastCommitted = useRef(value);
  const multiline = /(?:text|json)\b/i.test(column.type) || (typeof value === 'string' && value.includes('\n'));
  const isNumeric = numericType.test(column.type.trim());

  useEffect(() => {
    setDraft(displayValue(value));
    setIsNull(value === null);
    setError('');
    lastCommitted.current = value;
    onDraftChange(column.name, false);
  }, [value, column.name, onDraftChange]);

  useEffect(() => () => onDraftChange(column.name, false), [column.name, onDraftChange]);

  function commit() {
    if (column.primary) return;
    const parsed = parseRecordValue(column, draft, isNull);
    if (parsed.error !== undefined) {
      setError(parsed.error);
      return;
    }
    setError('');
    setDraft(displayValue(parsed.value));
    setIsNull(parsed.value === null);
    onDraftChange(column.name, false);
    if (parsed.value !== lastCommitted.current) {
      lastCommitted.current = parsed.value;
      onCommit(parsed.value);
    }
  }

  const inputProps = {
    id: inputId,
    value: isNull ? '' : draft,
    readOnly: !!column.primary || isNull,
    placeholder: isNull ? 'NULL' : '',
    'aria-invalid': !!error,
    'aria-describedby': [`${inputId}-type`, modified && `${inputId}-modified`, error && `${inputId}-error`].filter(Boolean).join(' '),
    title: modified ? '未应用更改' : undefined,
    className: `record-form-input${isNull ? ' is-null' : ''}`,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setDraft(event.target.value);
      onDraftChange(column.name, isRecordDraftDirty(value, event.target.value, isNull));
      setError('');
    },
  };

  return <div className={`record-form-field${column.primary ? ' is-primary' : ''}${modified ? ' is-modified' : ''}${error ? ' has-error' : ''}`}>
    <label className="record-form-label" htmlFor={inputId} title={column.comment}>
      <span>{column.primary && <KeyOutlined aria-label="主键"/>}{column.name}</span>
      <span className="record-form-display-name">{getColumnLabel(column)}</span>
      <small className="record-form-type" id={`${inputId}-type`}><FieldTypeIcon type={column.type}/><span>{column.type}{column.primary ? ' · 主键' : ''}</span></small>
    </label>
    <div className="record-form-editor" onBlur={event => {
      if (!event.currentTarget.contains(event.relatedTarget as Node | null)) commit();
    }} onKeyDown={event => {
      if (event.nativeEvent.isComposing) return;
      if (event.key === 'Enter' && !(multiline && event.shiftKey)) {
        event.preventDefault();
        event.stopPropagation();
        commit();
      } else if (event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        setDraft(displayValue(value));
        setIsNull(value === null);
        onDraftChange(column.name, false);
        setError('');
      }
    }}>
      <div className="record-form-control">
        {multiline ? <textarea {...inputProps} rows={3}/> : <input {...inputProps} type="text" inputMode={isNumeric ? (integerType.test(column.type.trim()) ? 'numeric' : 'decimal') : undefined}/>}
        {column.primary ? <span className="record-form-readonly">只读</span> : column.nullable && <label className="record-form-null" title="将此字段设为 NULL">
          <input type="checkbox" aria-label={`${column.name} 设为 NULL`} checked={isNull} onChange={event => {
            setIsNull(event.target.checked);
            onDraftChange(column.name, isRecordDraftDirty(value, draft, event.target.checked));
            setError('');
          }}/><span>NULL</span>
        </label>}
      </div>
      {modified && <span id={`${inputId}-modified`} className="record-form-modified-hint">未应用更改</span>}
      {error && <span id={`${inputId}-error`} className="record-form-error" role="alert">{error}</span>}
    </div>
  </div>;
}

export default function RecordFormView({table, rows, selectedId, onSelect, onChange, isModified, onDraftChange}: RecordFormViewProps) {
  const index = getRecordIndex(rows, selectedId);
  const row = index < 0 ? undefined : rows[index];
  const activeId = row?.id;
  const draftChangeRef = useRef(onDraftChange);
  draftChangeRef.current = onDraftChange;
  const [draftTracker] = useState(() => createRecordDraftTracker(dirty => draftChangeRef.current?.(dirty)));
  useEffect(() => () => draftTracker.clear(), [draftTracker, table.name, activeId]);
  useEffect(() => {
    if (activeId !== undefined && activeId !== selectedId) onSelect(activeId);
  }, [activeId, selectedId, onSelect]);

  function selectRecord(nextIndex: number) {
    const next = rows[nextIndex];
    if (next) onSelect(next.id);
  }

  return <section className="record-form-view" aria-label={`${table.name} 表单视图`}>
    <div className="record-form-heading"><span>字段</span><span>值</span></div>
    <div className="record-form-scroll">
      {row ? <div className="record-form-fields" key={`${table.name}:${typeof row.id}:${String(row.id)}`}>
        {table.columns.map(column => <RecordField key={column.name} column={column} value={row[column.name] ?? null} modified={isModified?.(row, column.name) ?? false} onDraftChange={draftTracker.update} onCommit={value => onChange(row.id, column.name, value)}/>)}
      </div> : <div className="record-form-empty"><span>没有可显示的记录</span><small>请调整筛选条件或新增记录</small></div>}
    </div>
    <div className="record-form-footer">
      <nav className="record-form-navigator" aria-label="记录导航">
        <button type="button" aria-label="第一条记录" title="第一条记录" disabled={index <= 0} onClick={() => selectRecord(0)}><StepBackwardOutlined/></button>
        <button type="button" aria-label="上一条记录" title="上一条记录" disabled={index <= 0} onClick={() => selectRecord(index - 1)}><LeftOutlined/></button>
        <span className="record-form-position" role="status" aria-live="polite">第 <b>{index + 1}</b> / {rows.length.toLocaleString('en-US')} 条</span>
        <button type="button" aria-label="下一条记录" title="下一条记录" disabled={index < 0 || index >= rows.length - 1} onClick={() => selectRecord(index + 1)}><RightOutlined/></button>
        <button type="button" aria-label="最后一条记录" title="最后一条记录" disabled={index < 0 || index >= rows.length - 1} onClick={() => selectRecord(rows.length - 1)}><StepForwardOutlined/></button>
      </nav>
      <span className="record-form-hint">Enter 确认，Esc 取消</span>
    </div>
  </section>;
}
