import { useMemo, useState } from 'react';
import { Button, Checkbox, Input, Popconfirm, Popover, Select, Table, Tooltip } from 'antd';
import type { Dictionary, Resource, DataRow, Value } from '../core/model';
import { display, colName, SYNC_LABEL } from '../core/model';
import { prepareValue, matchDictionary } from '../core/engine';
import Icon from './Icon';

export default function PreviewPanel({ resource: r, dictionaries, onEdit, onDelete }: {
    resource: Resource;
    dictionaries: Dictionary[];
    onEdit: (row: DataRow) => void;
    onDelete: (row: DataRow) => void;
}) {
    const [raw, setRaw] = useState(false), [search, setSearch] = useState(''), [status, setStatus] = useState('all'), [hiddenCols, setHiddenCols] = useState<string[]>([]), [page, setPage] = useState(1), [pageSize, setPageSize] = useState(5);
    const filtered = useMemo(() => r.rows.filter(row => {
        if (status !== 'all' && (r.sync[row.id] || 'idle') !== status) return false;
        if (!search) return true;
        const text = r.schema.fields.map(f => {
            let label = '';
            try { label = matchDictionary(row.values[f.id], f, dictionaries)?.label || ''; } catch { /* unresolved dictionaries are shown by validation */ }
            return `${display(row.values[f.id])} ${label}`;
        }).join(' ').toLowerCase();
        return text.includes(search.toLowerCase());
    }), [r, search, status, dictionaries]);
    function prepared(row: DataRow, f: Resource['schema']['fields'][number]): Value {
        try { return prepareValue(row.values[f.id], f, r.schema, dictionaries); } catch { return row.values[f.id]; }
    }
    const columns = [
        { title: '源行', key: 'sourceRow', dataIndex: 'sourceRow', width: 65, fixed: 'left' as const, align: 'center' as const, sorter: (a: DataRow, b: DataRow) => a.sourceRow - b.sourceRow, render: (v: number) => <span className="source-row-number">{v}</span> },
        ...r.schema.fields.filter(f => !hiddenCols.includes(f.id)).map(f => ({
            title: raw ? <div className="table-col-title"><span>{f.headers.join(' / ') || '空表头'}</span><small>来源列 {colName(f.column)}</small></div> : <div className="table-col-title"><span>{f.pk && <Icon name="key" size={11}/>} {f.zh || '待补中文名'}{[r.schema.timestamp, r.schema.eventTime].includes(f.id) && <Icon name="clock" size={12}/>}</span><small>{f.en || '待补英文名'}</small></div>,
            key: f.id,
            width: f.type === 'datetime' ? 200 : f.en === 'remark' ? 230 : 165,
            sorter: (a: DataRow, b: DataRow) => display(a.values[f.id]).localeCompare(display(b.values[f.id]), 'zh-CN', { numeric: true }),
            render: (_: unknown, row: DataRow) => {
                const value = raw ? row.values[f.id] : prepared(row, f);
                let label = '';
                try { label = matchDictionary(row.values[f.id], f, dictionaries)?.label || ''; } catch { /* Dictionary labels are optional display information. */ }
                const changed = !raw && String(value) !== String(row.values[f.id]);
                return <Tooltip title={changed ? `原值：${display(row.values[f.id])}；同步值：${display(value)}` : display(value)}><span tabIndex={0} className={`data-cell ${changed ? 'changed-cell' : ''}`}><span>{display(value)}</span>{label && <small className="dictionary-label">{label}</small>}</span></Tooltip>;
            },
        })),
        { title: '同步状态', key: 'sync', width: 140, fixed: 'right' as const, render: (_: unknown, row: DataRow) => {
            const state = r.sync[row.id] || 'idle';
            return <span className={`status-pill ${state === 'success' ? 'success' : state === 'failed' ? 'error' : state === 'idle' ? 'neutral' : 'blue'}`}><i />{SYNC_LABEL[state]}</span>;
        } },
        { title: '操作', key: 'op', width: 108, fixed: 'right' as const, render: (_: unknown, row: DataRow) => <span className="data-row-actions"><Button type="link" size="small" disabled={r.locked} onClick={() => onEdit(row)}>编辑</Button><Popconfirm title="删除这条数据？" description="仅从本次上报数据中移除，原始文件不会被删除。" okText="删除" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => onDelete(row)}><Button type="link" danger size="small" disabled={r.locked}>删除</Button></Popconfirm></span> },
    ];
    return <div className="preview-panel">
  <div className="preview-toolbar"><div className="view-switch"><button className={!raw ? 'active' : ''} onClick={() => setRaw(false)}>同步预览</button><button className={raw ? 'active' : ''} onClick={() => setRaw(true)}>原始数据</button></div><Input allowClear prefix={<Icon name="search" size={16}/>} aria-label="搜索数据" placeholder="搜索数据" value={search} onChange={e => { setSearch(e.target.value); setPage(1); }} className="data-search"/><span className="preview-record-count">当前数据表共 <strong>{r.rows.length}</strong> 条{filtered.length !== r.rows.length && <> · 筛选结果 <strong>{filtered.length}</strong> 条</>}</span><Select aria-label="同步状态筛选" value={status} style={{ width: 132 }} options={[{ value: 'all', label: '全部同步状态' }, ...Object.entries(SYNC_LABEL).map(([value, label]) => ({ value, label }))]} onChange={v => { setStatus(v); setPage(1); }}/><Popover trigger="click" placement="bottomRight" content={<div className="column-options"><strong>显示字段</strong><p>仅改变显示，不改变整表上报字段范围</p>{r.schema.fields.map(f => <Checkbox key={f.id} checked={!hiddenCols.includes(f.id)} onChange={e => setHiddenCols(e.target.checked ? hiddenCols.filter(x => x !== f.id) : [...hiddenCols, f.id])}>{f.zh || f.headers.join(' / ')}</Checkbox>)}</div>}><Button aria-label="列显示设置" icon={<Icon name="columns" size={17}/>}>列设置</Button></Popover></div>
  <div className="preview-table"><Table<DataRow> rowKey="id" size="middle" dataSource={filtered} columns={columns} scroll={{ x: 'max-content', ...(pageSize > 10 ? { y: 460 } : {}) }} pagination={{ current: page, pageSize, onChange: (p, s) => { setPage(p); setPageSize(s); }, showSizeChanger: true, pageSizeOptions: [5, 10, 20, 50, 100], showTotal: total => `共 ${total} 条`, size: 'small' }} locale={{ emptyText: '没有符合条件的数据，请调整筛选条件' }}/></div>
  {raw && <div className="preview-caption"><Icon name="info" size={13}/>当前显示原始表头及原始值；源文件内容只读。</div>}
 </div>;
}
