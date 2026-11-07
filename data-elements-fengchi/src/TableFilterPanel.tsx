import {useId, useRef, useState} from 'react';
import {Button} from 'antd';
import {DeleteOutlined, FilterOutlined, PlusOutlined} from '@ant-design/icons';
import DraggableModal from './DraggableModal';
import {getColumnLabel} from './ColumnHeader';
import type {Column} from './data';
import {createFilterRule, filterNeedsValue, getFilterKind, getFilterOperators, getFilterValueHint, validateFilterRules, type FilterOperator, type FilterRule, type FilterValidationError} from './tableFilters';
import './tableFilters.css';

export type TableFilterPanelProps = {
  columns: readonly Column[];
  rules: readonly FilterRule[];
  onApply: (rules: FilterRule[]) => void;
  onClear: () => void;
  disabled?: boolean;
};

type TableFilterEditorProps = {
  columns: readonly Column[];
  rules: readonly FilterRule[];
  errors?: readonly FilterValidationError[];
  onChange: (rules: FilterRule[]) => void;
};

/** Stateless editor also keeps field/operator/value labeling testable without a portal. */
export function TableFilterEditor({columns, rules, errors = [], onChange}: TableFilterEditorProps) {
  const id = useId();
  function updateRule(index: number, patch: Partial<FilterRule>) {
    onChange(rules.map((rule, current) => current === index ? {...rule, ...patch} : rule));
  }
  return <div className="table-filter-rules">
    {!rules.length && <div className="table-filter-empty">没有筛选条件，将显示全部记录</div>}
    {rules.map((rule, index) => {
      const column = columns.find(candidate => candidate.name === rule.field);
      const options = column ? getFilterOperators(column) : [];
      const needsValue = filterNeedsValue(rule.operator);
      const error = errors.find(candidate => candidate.ruleId === rule.id)?.message;
      const prefix = `${id}-rule-${index}`;
      return <div key={rule.id} className={`table-filter-rule${error ? ' has-error' : ''}`}>
        <span className="table-filter-conjunction" aria-hidden="true">{index ? '并且' : '当'}</span>
        <label className="table-filter-field" htmlFor={`${prefix}-field`}>
          <span>字段</span>
          <select id={`${prefix}-field`} aria-label={`条件 ${index + 1} 字段`} value={rule.field} onChange={event => {
            const selected = columns.find(candidate => candidate.name === event.target.value);
            if (!selected) return;
            const supported = getFilterOperators(selected);
            updateRule(index, {field: selected.name, operator: supported.some(option => option.value === rule.operator) ? rule.operator : supported[0].value, value: ''});
          }}>
            {!column && <option value={rule.field}>{rule.field ? `${rule.field}（字段已移除）` : '请选择字段'}</option>}
            {columns.map(candidate => <option key={candidate.name} value={candidate.name}>{candidate.name} · {getColumnLabel(candidate)}</option>)}
          </select>
          <small>{column?.type ?? '无有效字段'}</small>
        </label>
        <label className="table-filter-operator" htmlFor={`${prefix}-operator`}>
          <span>条件</span>
          <select id={`${prefix}-operator`} aria-label={`条件 ${index + 1} 运算符`} value={rule.operator} disabled={!column} onChange={event => updateRule(index, {operator: event.target.value as FilterOperator})}>
            {!options.some(option => option.value === rule.operator) && <option value={rule.operator}>请选择条件</option>}
            {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <label className="table-filter-value" htmlFor={`${prefix}-value`}>
          <span>值</span>
          <input id={`${prefix}-value`} aria-label={`条件 ${index + 1} 值`} type="text" inputMode={column && getFilterKind(column) === 'number' ? 'decimal' : undefined} value={needsValue ? rule.value : ''} disabled={!column || !needsValue} placeholder={needsValue && column ? getFilterValueHint(column) : '无需填写'} aria-invalid={!!error} aria-describedby={error ? `${prefix}-error` : undefined} onChange={event => updateRule(index, {value: event.target.value})}/>
        </label>
        <Button className="table-filter-remove" size="small" type="text" icon={<DeleteOutlined/>} title={`删除条件 ${index + 1}`} aria-label={`删除条件 ${index + 1}`} onClick={() => onChange(rules.filter((_, current) => current !== index))}/>
        {error && <span className="table-filter-error" id={`${prefix}-error`} role="alert">{error}</span>}
      </div>;
    })}
  </div>;
}

/** Applied rules are controlled by the caller; key this component by table identity. */
export default function TableFilterPanel({columns, rules, onApply, onClear, disabled = false}: TableFilterPanelProps) {
  const [open, setOpen] = useState(false);
  const [drafts, setDrafts] = useState<FilterRule[]>([]);
  const [submitted, setSubmitted] = useState(false);
  const nextId = useRef(0);
  const id = useId();
  const newRule = () => createFilterRule(columns, `${id}-${++nextId.current}`);
  const errors = submitted ? validateFilterRules(columns, drafts) : [];
  function show() {
    setDrafts(rules.length ? rules.map(rule => ({...rule})) : [newRule()]);
    setSubmitted(false);
    setOpen(true);
  }
  function apply() {
    setSubmitted(true);
    if (validateFilterRules(columns, drafts).length) return;
    onApply(drafts.map(rule => ({...rule, value: filterNeedsValue(rule.operator) ? rule.value : ''})));
    setOpen(false);
  }
  return <>
    <Button size="small" className={`table-filter-trigger${rules.length ? ' is-active' : ''}`} icon={<FilterOutlined/>} disabled={disabled || !columns.length} aria-haspopup="dialog" aria-expanded={open} title={rules.length ? `已应用 ${rules.length} 个筛选条件` : '按字段筛选当前表格数据'} onClick={show}>
      数据筛选{rules.length > 0 && <span className="table-filter-count">{rules.length}</span>}
    </Button>
    <DraggableModal title="数据筛选" open={open} width={780} onCancel={() => setOpen(false)} className="table-filter-modal" footer={<div className="table-filter-footer">
      <Button size="small" onClick={() => {onClear(); setDrafts([]); setOpen(false);}}>清除筛选</Button>
      <span/>
      <Button size="small" onClick={() => setOpen(false)}>取消</Button>
      <Button size="small" type="primary" htmlType="submit" form={`${id}-form`}>应用筛选</Button>
    </div>}>
      <form id={`${id}-form`} onSubmit={event => {event.preventDefault(); apply();}}>
        <p className="table-filter-description">显示同时满足以下所有条件的记录（AND）。仅筛选当前表的本地示例数据。</p>
        <TableFilterEditor columns={columns} rules={drafts} errors={errors} onChange={setDrafts}/>
        <Button className="table-filter-add" size="small" icon={<PlusOutlined/>} disabled={!columns.length} onClick={() => setDrafts(current => [...current, newRule()])}>添加条件</Button>
        <div className="table-filter-notes">
          <span>文本不区分大小写；空字符串与 NULL 分别匹配。</span>
          <span>日期须为有效年月日；日期时间可填到分钟或秒，仅填日期按零点比较。未带时区的时间按 UTC 比较，带偏移量的时间统一换算为 UTC。</span>
        </div>
      </form>
    </DraggableModal>
  </>;
}
