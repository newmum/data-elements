import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { Alert, Button, Select, Spin } from 'antd';
import { readCatalogTablesPage } from '../../services/metadata';

interface Choice { value: string; label: string; disabled?: boolean; }
interface Props {
  mode?: 'multiple'; value?: string | string[]; onChange?: (value: any) => void;
  sourceId?: string; excludeIds?: string[]; disabled?: boolean; placeholder?: string; style?: CSSProperties;
  placement?: 'topLeft' | 'topRight' | 'bottomLeft' | 'bottomRight';
}

/** Server-paged metadata picker: a large tenant is never limited to the overview snapshot. */
export function TechnicalEntitySelect({ sourceId, excludeIds = [], ...props }: Props) {
  const [query, setQuery] = useState(''), [options, setOptions] = useState<Choice[]>([]);
  const [page, setPage] = useState(0), [total, setTotal] = useState(0), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const request = useRef<AbortController | null>(null), selected = useRef(new Map<string, Choice>());
  const load = async (number: number) => {
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    setBusy(true); setError('');
    try {
      const response = await readCatalogTablesPage(sourceId ? [sourceId] : undefined, undefined, query, number, 100, controller.signal);
      if (controller.signal.aborted) return;
      const incoming = response.rows.map(row => ({ value: row.tid, label: `${row.table_name_cn || row.table_comment || row.table_name} · ${row.table_name}` }));
      setOptions(previous => number === 1 ? incoming : [...previous, ...incoming.filter(item => !previous.some(old => old.value === item.value))]);
      setPage(number); setTotal(response.total);
    } catch (cause) { if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  };
  useEffect(() => {
    const timer = window.setTimeout(() => { void load(1); }, query ? 250 : 0);
    return () => { window.clearTimeout(timer); request.current?.abort(); };
  }, [query, sourceId]);
  const current = Array.isArray(props.value) ? props.value : props.value ? [props.value] : [];
  for (const option of options) if (current.includes(option.value)) selected.current.set(option.value, option);
  const visible = [...selected.current.values()].filter(option => current.includes(option.value) && !options.some(item => item.value === option.value));
  return <Select {...props} showSearch filterOption={false} onSearch={setQuery} loading={busy}
    placeholder={props.placeholder || '搜索表中文名或技术名称'}
    options={[...visible, ...options].map(option => ({ ...option, disabled: excludeIds.includes(option.value) }))}
    notFoundContent={busy ? <Spin size="small" /> : error || '未找到符合条件的数据表'}
    onPopupScroll={event => { const node = event.currentTarget; if (!busy && options.length < total && node.scrollTop + node.clientHeight >= node.scrollHeight - 24) void load(page + 1); }}
    popupRender={menu => <>{menu}<div style={{ padding: '8px 12px', borderTop: '1px solid var(--ps-border)' }}>
      {error ? <Alert type="error" description={error} action={<Button size="small" onClick={() => void load(page || 1)}>重试</Button>} />
        : <span>已加载 {options.length} / {total} 张表{options.length < total && <Button type="link" size="small" loading={busy} onClick={() => void load(page + 1)}>加载更多</Button>}</span>}
    </div></>} />;
}
