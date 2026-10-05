import { Button, Empty, Input, Select, Tag, Tooltip, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { CanvasNode } from '@/types/dsl';

export type ConditionOperator = 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE' | 'CONTAINS' | 'STARTS_WITH' | 'IN' | 'IS_NULL' | 'NOT_NULL';
export interface BusinessCondition { id: string; field: string; operator: ConditionOperator; value?: string; connector?: 'AND' | 'OR' }
export interface ColumnOption { name: string; comment?: string; dataType?: string; source?: string }

const OPERATORS = [
  { value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' },
  { value: 'GT', label: '大于' }, { value: 'GE', label: '大于等于' },
  { value: 'LT', label: '小于' }, { value: 'LE', label: '小于等于' },
  { value: 'CONTAINS', label: '包含' }, { value: 'STARTS_WITH', label: '开头是' },
  { value: 'IN', label: '属于其中之一' }, { value: 'IS_NULL', label: '为空' },
  { value: 'NOT_NULL', label: '不为空' },
];

export function nodeOutputColumns(node?: CanvasNode | null): ColumnOption[] {
  if (!node) return [];
  const cfg = node.config ?? {};
  const raw = Array.isArray(cfg.outputColumns) && cfg.outputColumns.length ? cfg.outputColumns : cfg.sourceColumns;
  if (!Array.isArray(raw)) return [];
  return raw.map((col: any) => ({
    name: String(col?.columnName ?? col?.name ?? '').trim(),
    comment: String(col?.columnComment ?? col?.comment ?? '').trim(),
    dataType: String(col?.dataType ?? col?.typeName ?? '').trim(),
    source: node.label,
  })).filter((col) => col.name);
}

export function upstreamColumnOptions(nodes: CanvasNode[]) {
  const seen = new Set<string>();
  return nodes.flatMap(nodeOutputColumns).filter((col) => {
    const key = col.name.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function newCondition(): BusinessCondition {
  return { id: `condition_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`, field: '', operator: 'EQ', value: '', connector: 'AND' };
}

export function ConditionBuilder({ value, onChange, columns, emptyText = '请先在上游节点探查字段' }: {
  value: BusinessCondition[]; onChange: (value: BusinessCondition[]) => void; columns: ColumnOption[]; emptyText?: string;
}) {
  const update = (index: number, patch: Partial<BusinessCondition>) => onChange(value.map((item, i) => i === index ? { ...item, ...patch } : item));
  return (
    <div className="condition-builder">
      {columns.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />}
      {value.map((item, index) => {
        const noValue = item.operator === 'IS_NULL' || item.operator === 'NOT_NULL';
        return (
          <div className="condition-builder__row" key={item.id}>
            <div className="condition-builder__index">{index + 1}</div>
            <Select
              showSearch
              value={item.field || undefined}
              placeholder="选择字段"
              optionFilterProp="label"
              options={columns.map((col) => ({ value: col.name, label: col.comment ? `${col.name}（${col.comment}）` : col.name }))}
              onChange={(field) => update(index, { field })}
            />
            <Select value={item.operator} options={OPERATORS} onChange={(operator) => update(index, { operator })} />
            {noValue ? <Tag color="blue">无需填写值</Tag> : (
              <Input
                value={item.value}
                placeholder={item.operator === 'IN' ? '多个值用逗号分隔' : '填写比较值'}
                onChange={(event) => update(index, { value: event.target.value })}
              />
            )}
            <Tooltip title="删除条件"><Button type="text" danger icon={<DeleteOutlined />} onClick={() => onChange(value.filter((_, i) => i !== index))} /></Tooltip>
            {index < value.length - 1 && (
              <div className="condition-builder__connector">
                <Select size="small" value={item.connector ?? 'AND'} options={[{ value: 'AND', label: '并且' }, { value: 'OR', label: '或者' }]} onChange={(connector) => update(index, { connector })} />
              </div>
            )}
          </div>
        );
      })}
      <Button icon={<PlusOutlined />} onClick={() => onChange([...value, newCondition()])}>添加条件</Button>
      {value.length > 0 && <Typography.Text type="secondary">按顺序判断条件，“并且”表示都要满足，“或者”表示满足任意一个。</Typography.Text>}
    </div>
  );
}

function quoteIdentifier(value: string) { return `"${value.replaceAll('"', '""')}"`; }
function quoteLiteral(value: string) { return `'${value.replaceAll("'", "''")}'`; }

export function compileConditions(conditions: BusinessCondition[]) {
  const usable = conditions.filter((item) => item.field && (['IS_NULL', 'NOT_NULL'].includes(item.operator) || String(item.value ?? '').trim() !== ''));
  if (!usable.length) return '';
  return usable.map((item, index) => {
    const field = quoteIdentifier(item.field);
    const raw = String(item.value ?? '').trim();
    const numeric = /^-?\d+(\.\d+)?$/.test(raw);
    const literal = numeric ? raw : quoteLiteral(raw);
    const expression = item.operator === 'EQ' ? `${field} = ${literal}`
      : item.operator === 'NE' ? `${field} <> ${literal}`
      : item.operator === 'GT' ? `${field} > ${literal}`
      : item.operator === 'GE' ? `${field} >= ${literal}`
      : item.operator === 'LT' ? `${field} < ${literal}`
      : item.operator === 'LE' ? `${field} <= ${literal}`
      : item.operator === 'CONTAINS' ? `${field} LIKE ${quoteLiteral(`%${raw}%`)}`
      : item.operator === 'STARTS_WITH' ? `${field} LIKE ${quoteLiteral(`${raw}%`)}`
      : item.operator === 'IN' ? `${field} IN (${raw.split(/[,，]/).map((v) => quoteLiteral(v.trim())).filter((v) => v !== "''").join(', ')})`
      : item.operator === 'IS_NULL' ? `(${field} IS NULL OR ${field} = '')`
      : `${field} IS NOT NULL AND ${field} <> ''`;
    if (index === 0) return expression;
    return `${usable[index - 1].connector ?? 'AND'} ${expression}`;
  }).join(' ');
}
