import { useMemo, useState } from 'react';
import {Button,Checkbox,Input,Table} from 'antd';
import {Popconfirm,Popover,Select,Tooltip} from './ReportControls';
import type { Dictionary, Resource, DataRow, Value } from '../core/model';
import { display, colName, SYNC_LABEL } from '../core/model';
import { prepareValue, matchDictionary } from '../core/engine';
import Icon from './Icon';
import {DELIVERY_STATE_LABEL} from '../services/reportDelivery';

export default function PreviewPanel({ resource: r, dictionaries, onEdit, onDelete,view='normalized',readOnly=false,compact=false,sampleLimit,showDeliveryStatus=false }: {
    resource: Resource;
    dictionaries: Dictionary[];
    onEdit: (row: DataRow) => void;
    onDelete: (row: DataRow) => void;
    view?: 'normalized'|'original';
    readOnly?: boolean;
    compact?:boolean;
    sampleLimit?:number;
    showDeliveryStatus?:boolean;
}) {
    const raw=view==='original',worksheet=compact&&view==='original',statusLabels=showDeliveryStatus?DELIVERY_STATE_LABEL:SYNC_LABEL;
    const [search, setSearch] = useState(''), [status, setStatus] = useState('all'), [hiddenCols, setHiddenCols] = useState<string[]>([]), [page, setPage] = useState(1), [pageSize, setPageSize] = useState(5);
    const filtered = useMemo(() => r.rows.filter(row => {
        if (status !== 'all' && (r.sync[row.id] || 'idle') !== status) return false;
        if (!search) return true;
        const text = r.schema.fields.map(f => {
            let label = '';
            try { label = matchDictionary(row.values[f.id], f, dictionaries)?.label || ''; } catch { /* unresolved dictionaries are shown by validation */ }
            return `${display(raw?row.values[f.id]:prepared(row,f))} ${label}`;
        }).join(' ').toLowerCase();
        return text.includes(search.toLowerCase());
    }), [r, search, status, dictionaries, raw]);
    const visibleRows=sampleLimit?filtered.slice(0,sampleLimit):filtered;
    const stateCounts=Object.keys(statusLabels).map(state=>({state,count:r.rows.filter(row=>(r.sync[row.id]||'idle')===state).length})).filter(item=>item.count>0);
    function prepared(row: DataRow, f: Resource['schema']['fields'][number]): Value {
        try { return prepareValue(row.values[f.id], f, r.schema, dictionaries); } catch { return '格式不符合规范'; }
    }
    const columns = [
        { title: raw?'源行':'行号', key: 'sourceRow', dataIndex: 'sourceRow', width: 65, fixed: 'left' as const, align: 'center' as const, sorter: (a: DataRow, b: DataRow) => a.sourceRow - b.sourceRow, render: (v: number) => <span className="source-row-number">{v}</span> },
        ...r.schema.fields.filter(f => !hiddenCols.includes(f.id)).map(f => ({
            title: compact?<span className="spreadsheet-field-title" title={raw?f.headers.join(' / '):f.en}>{raw?f.headers.join(' / ')||'空表头':f.zh||f.en||'待补字段名'}</span>:raw ? <div className="table-col-title"><span>{f.headers.join(' / ') || '空表头'}</span><small>来源列 {colName(f.column)}</small></div> : <div className="table-col-title"><span>{f.pk && <Icon name="key" size={11}/>} {f.zh || '待补中文名'}{[r.schema.timestamp, r.schema.eventTime].includes(f.id) && <Icon name="clock" size={12}/>}</span><small>{f.en || '待补英文名'}</small></div>,
            key: f.id,
            width: f.type === 'datetime' ? 200 : f.en === 'remark' ? 230 : 165,
            sorter: (a: DataRow, b: DataRow) => display(raw?a.values[f.id]:prepared(a,f)).localeCompare(display(raw?b.values[f.id]:prepared(b,f)), 'zh-CN', { numeric: true }),
            render: (_: unknown, row: DataRow) => {
                const value = raw ? row.values[f.id] : prepared(row, f);
                let label = '';
                try { label = matchDictionary(row.values[f.id], f, dictionaries)?.label || ''; } catch { /* Dictionary labels are optional display information. */ }
                const changed = !raw && String(value) !== String(row.values[f.id]);
                return <Tooltip title={display(value)}><span tabIndex={0} className={`data-cell ${changed ? 'changed-cell' : ''}`}><span>{display(value)}</span>{label && <small className="dictionary-label">{label}</small>}</span></Tooltip>;
            },
        })),
        ...(!readOnly||showDeliveryStatus?[{ title: showDeliveryStatus?<Tooltip title="按整份上报文件的真实投递回执展示；目标系统尚未回传逐行入库结果">上报结果</Tooltip>:'同步状态', key: 'sync', width: showDeliveryStatus ? 108 : 120, align: 'center' as const, fixed: 'right' as const, render: (_: unknown, row: DataRow) => {
            const state = r.sync[row.id] || 'idle';
            return <span className={`status-pill ${state === 'success' ? 'success' : state === 'failed' ? 'error' : state === 'idle' ? 'neutral' : 'blue'}`}><i />{statusLabels[state]}</span>;
        } }]:[]),
        ...(!readOnly?[{ title: '操作', key: 'op', width: 108, fixed: 'right' as const, render: (_: unknown, row: DataRow) => <span className="data-row-actions"><Button type="link" size="small" disabled={r.locked} onClick={() => onEdit(row)}>编辑</Button><Popconfirm title="删除这条数据？" description="仅从本次上报数据中移除，原始文件不会被删除。" okText="删除" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => onDelete(row)}><Button type="link" danger size="small" disabled={r.locked}>删除</Button></Popconfirm></span> }]:[]),
    ];
    const visibleFields=r.schema.fields.filter(field=>!hiddenCols.includes(field.id));
    const headerRow=r.source.range.match(/^[A-Z]+(\d+)/)?.[1]||'1';
    const displayColumns=worksheet?columns.map((column,index)=>index<=visibleFields.length?{
      title:index===0?<span className="spreadsheet-corner"/>:<span className="spreadsheet-column-letter">{colName(visibleFields[index-1].column)}</span>,
      key:'sheet-'+column.key,children:[index===0?{...column,title:headerRow,sorter:undefined}:column],
    }:column):compact?columns.slice(1):columns;
    return <div className={`preview-panel${compact?' preview-panel--compact':''}${worksheet?' preview-panel--sheet':''}`}>
  <div className="preview-toolbar">{showDeliveryStatus&&<div className="preview-delivery-summary">{stateCounts.map(({state,count})=><span key={state} className={`preview-state-count is-${state}`}><i/>{statusLabels[state as keyof typeof statusLabels]} <b>{count.toLocaleString()}</b></span>)}</div>}{!compact&&<strong className="preview-view-title">{raw?'原始样例':'规范后数据'}</strong>}<Input allowClear prefix={<Icon name="search" size={16}/>} aria-label="搜索数据" placeholder={compact&&raw?'搜索样例':'搜索数据'} value={search} onChange={e => { setSearch(e.target.value); setPage(1); }} className="data-search"/>
    {!compact&&<span className="preview-record-count">当前数据表共 <strong>{r.rows.length}</strong> 条{filtered.length!==r.rows.length&&<> · 筛选 {filtered.length} 条</>}</span>}
    {!compact&&<Select aria-label="每页条数" value={pageSize} options={[5,10,20,50,100].map(value=>({value,label:`${value} 条/页`}))} onChange={value=>{setPageSize(value);setPage(1);}}/>}
    {(!readOnly||showDeliveryStatus)&&<Select aria-label={showDeliveryStatus?'上报结果筛选':'同步状态筛选'} value={status} style={{ width: 132 }} options={[{ value: 'all', label: showDeliveryStatus?'全部上报结果':'全部同步状态' }, ...Object.entries(statusLabels).map(([value, label]) => ({ value, label }))]} onChange={v => { setStatus(v); setPage(1); }}/>}<Popover trigger="click" placement="bottomRight" content={<div className="column-options"><strong>显示字段</strong><p>仅改变显示，不改变整表上报字段范围</p>{r.schema.fields.map(f => <Checkbox key={f.id} checked={!hiddenCols.includes(f.id)} onChange={e => setHiddenCols(e.target.checked ? hiddenCols.filter(x => x !== f.id) : [...hiddenCols, f.id])}>{f.zh || f.headers.join(' / ')}</Checkbox>)}</div>}><Button aria-label="列显示设置" icon={<Icon name="columns" size={17}/>}>列设置</Button></Popover></div>
  <div className="preview-table"><Table<DataRow> rowKey="id" size={compact?'small':'middle'} dataSource={visibleRows} columns={displayColumns} scroll={{ x: 'max-content', ...(!compact&&pageSize > 10 ? { y: 460 } : {}) }} pagination={compact?false:{ current: page, pageSize, onChange: (p, s) => { setPage(p); setPageSize(s); }, showSizeChanger: false, pageSizeOptions: [5, 10, 20, 50, 100], showTotal: total => `共 ${total} 条`, size: 'small' }} locale={{ emptyText: raw?'样例中没有符合条件的数据':'没有符合条件的数据，请调整筛选条件' }}/></div>
  {raw&&!compact&&<div className="preview-caption"><Icon name="info" size={13}/>当前显示原始表头及原始值；源文件内容只读。</div>}
 </div>;
}
