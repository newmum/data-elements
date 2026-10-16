import { useEffect, useState } from 'react';
import { Alert, Button, Table } from 'antd';
import type { TableProps } from 'antd';
import { platformApi } from '../../api/platformApi';
import { checkedDraftKeys, draftOrderRequest } from './mappingDraftOrder';

type Field<T> = { kind: 'text' | 'number' | 'time'; value: (row: T) => unknown };
type Props<T> = Omit<TableProps<T>, 'rowKey' | 'pagination'> & {
  rowKey: (row: T) => string; fields?: Record<string, Field<T>>; sequenceColumnKey?: string;
};
/** Mapping drafts are already complete. Viewing execution order never waits for a sorting request. */
export default function MappingDraftTable<T extends object>({ dataSource = [], columns, fields = {}, rowKey, sequenceColumnKey, ...props }: Props<T>) {
  const [sort, setSort] = useState<{ key: string; direction: 'asc' | 'desc' } | null>(null);
  const [ordered, setOrdered] = useState<{ signature: string; keys: string[] } | null>(null);
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [retry, setRetry] = useState(0);
  const definition = sort ? fields[sort.key] : undefined;
  const items = dataSource.map(row => ({ id: rowKey(row), value: definition?.value(row) ?? null }));
  const signature = sort ? JSON.stringify({ items, kind: definition?.kind, direction: sort.direction }) : '';
  useEffect(() => {
    if (!signature) { setLoading(false); setError(''); return; }
    let active = true;
    setLoading(true); setError('');
    const input = JSON.parse(signature) as { items: typeof items; kind: Field<T>['kind']; direction: 'asc' | 'desc' };
    Promise.resolve().then(() => platformApi<{ keys: string[]; total: number }>('/sym/table/preview-order', draftOrderRequest(input.items, input.kind, input.direction)))
      .then(result => { const keys = checkedDraftKeys(input.items, result); if (active) setOrdered({ signature, keys }); })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '排序暂不可用，当前仍显示全部规则'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [signature, retry]);
  const byId = new Map(dataSource.map((row, index) => [rowKey(row), { row, sequence: index + 1 }]));
  const displayed = sort && ordered?.signature === signature && !error ? ordered.keys.map(key => byId.get(key)!.row) : dataSource;
  return <>
    {error && <Alert type="warning" showIcon message={error} action={<Button size="small" onClick={() => setRetry(value => value + 1)}>重试排序</Button>} />}
    <Table<T> {...props} rowKey={rowKey} dataSource={displayed} loading={loading} pagination={false}
      columns={columns?.map(column => ({ ...column,
        ...(sequenceColumnKey && column.key === sequenceColumnKey ? { render: (_: unknown, row: T) => byId.get(rowKey(row))?.sequence } : {}),
        sorter: !!fields[String(column.key)],
        sortOrder: sort?.key === String(column.key) ? sort.direction === 'asc' ? 'ascend' : 'descend' : null,
      }))}
      onChange={(_page, _filters, sorter, extra) => {
        if (extra.action !== 'sort') return;
        const item = Array.isArray(sorter) ? sorter[0] : sorter;
        setSort(item.order ? { key: String(item.columnKey), direction: item.order === 'ascend' ? 'asc' : 'desc' } : null);
      }} />
  </>;
}
