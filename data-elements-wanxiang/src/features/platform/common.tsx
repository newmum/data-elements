import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Alert, App, Button, Checkbox, Drawer, Empty, Form, Input, Modal, Popover, Segmented, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { ReloadOutlined, SearchOutlined, DownloadOutlined, SettingOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api, canWrite, versionOf, type Row } from '../../services/api';
import { useWorkspaceQuery } from '../../services/useWorkspaceQuery';
import { getModuleVisual } from '../../design/moduleVisuals';
import { heroAsset } from '../../design/heroAssets';
import { ModuleIcon } from '../../design/ModuleIcon';
import { compactPreference } from '../../shared/presentation';
export const text = (v: unknown) => v == null || v === '' ? '—' : typeof v === 'object' ? JSON.stringify(v) : String(v);
export const date = (v: unknown) => {
    if (!v)
        return '—';
    const d = new Date(typeof v === 'number' ? v : /^\d{13}$/.test(String(v)) ? Number(v) : String(v));
    return Number.isNaN(d.getTime()) ? '—' : new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(d);
};
const statuses: Record<string, string> = { CONNECTED: '连接正常', DRAFT: '草稿', READY: '配置可用', ERROR: '异常', DISABLED: '已停用', ARCHIVED: '已归档', ACTIVE: '启用', IMPORTED: '导入结构', REGISTERED: '已登记', QUEUED: '排队中', RUNNING: '执行中', CANCEL_REQUESTED: '取消中', SUCCEEDED: '已完成', PARTIAL: '部分完成', FAILED: '执行失败', CANCELLED: '已取消', PENDING: '待审核', IN_REVIEW: '审核中', PUBLISHED: '已发布', APPROVED: '已通过', RETURNED: '已退回', CONFIRMED: '已确认', SUGGESTED: '待确认', OPEN: '待处理', CLOSED: '已关闭', UNASSIGNED: '待分派', REMEDIATING: '整改中', RETEST_PENDING: '待复检', PASSED: '全量通过', SAMPLE_PASSED: '样本内通过', VIOLATIONS_FOUND: '发现违例', NO_DATA: '无可验证数据', INCOMPLETE: '未完整检查', SAMPLED: '抽样观测', COMPLETE: '完整观测' };
export function Status({ value }: {
    value?: unknown;
}) {
    const v = String(value ?? 'UNKNOWN').toUpperCase();
    const tone = ['FAILED', 'ERROR', 'VIOLATIONS_FOUND'].includes(v) ? 'danger' : ['SUCCEEDED', 'PUBLISHED', 'APPROVED', 'ACTIVE', 'PASSED', 'CLOSED', 'READY', 'CONNECTED', 'CONFIRMED', 'COMPLETE'].includes(v) ? 'success' : ['RUNNING', 'QUEUED', 'IN_REVIEW', 'PENDING', 'SAMPLE_PASSED'].includes(v) ? 'info' : ['PARTIAL', 'RETEST_PENDING', 'SUGGESTED', 'INCOMPLETE', 'OPEN', 'UNASSIGNED'].includes(v) ? 'warning' : 'neutral';
    return <span className={`wx-status tone-${v==='STALE'?'warning':tone}`}><i aria-hidden="true"/>{v==='UNBOUND'?'未绑定':v==='STALE'?'引用未发布 / 不可用':statuses[v] ?? (v === 'UNKNOWN' ? '未知' : v)}</span>;
}
export function ErrorNotice({ error, retry }: {
    error: unknown;
    retry?: () => void;
}) { return error ? <Alert className="server-error" showIcon type="error" title={error instanceof Error ? error.message : String(error)} action={retry ? <Button onClick={retry}>重试</Button> : undefined}/> : null; }
export function Page({ title, description, actions, children }: {
    title: string;
    description?: string;
    actions?: ReactNode;
    children: ReactNode;
}) {
    const visual = getModuleVisual(title);
    // Only the overview uses the approved 3.1 header. Other modules keep their 3.2 art/copy.
    if (visual?.id === 'overview') {
        return <section className="platform-page" data-page={title} data-module="overview">
            <header className="platform-page-heading">
                <div className="page-heading-copy">
                    <span className="platform-eyebrow">万象数据治理 <i /> WANXIANG</span>
                    <h1>{title}</h1>{description && <p>{description}</p>}
                </div>
                <Space className="page-heading-actions" wrap>{actions}</Space>
                <div className="page-atmosphere" aria-hidden="true" />
            </header>{children}
        </section>;
    }
    return <section className="platform-page" data-page={title} data-module={visual?.id}><header className={`platform-page-heading ${visual ? 'has-module-art' : ''}`}><div className="page-heading-copy"><span className="platform-eyebrow">{visual?.category ?? '万象数据治理'} <i /> {visual?.english ?? 'WANXIANG'}</span><h1>{title}</h1><p>{visual?.description ?? description}</p>{visual && <div className="module-highlights">{visual.highlights.map(label => <span key={label}><i aria-hidden="true"/>{label}</span>)}</div>}</div>{visual && <><picture className="module-hero-art" aria-hidden="true"><img className="hero-light" src={heroAsset(visual.id,'light')} alt=""/><img className="hero-dark" src={heroAsset(visual.id,'dark')} alt=""/></picture><div className="module-promise" aria-hidden="true"><ModuleIcon name={visual.id}/><span>{visual.promise}</span></div></>}<Space className="page-heading-actions" wrap>{actions}</Space></header>{children}</section>;
}
const tableNameTitle = /名称|姓名|字段名|数据元|数据表|数据源|数据来源|关系图|表\s*\/\s*视图|任务|标题/;
const tableTimeTitle = /时间|日期|触发|最近更新|最近采集/;
const tableSortCollator = new Intl.Collator('zh-CN', { numeric: true, sensitivity: 'base' });
function tableSortValue(row: Row, title: string, dataIndex: unknown): string {
    if (typeof dataIndex === 'string') {
        const value = row[dataIndex];
        if (value != null && typeof value !== 'object') return String(value);
    }
    const keys = tableTimeTitle.test(title)
      ? title.includes('创建') ? ['createdAt', 'created_at', 'created_time']
        : title.includes('开始') ? ['startedAt', 'started_at', 'started_time']
        : title.includes('完成') || title.includes('结束') ? ['finishedAt', 'finished_at', 'finished_time']
        : title.includes('采集') ? ['lastCollectedAt', 'last_collected_at', 'collected_at']
        : ['updatedAt', 'updated_at', 'updatedTime', 'updated_time', 'created_at']
      : ['displayName', 'name', 'task_name', 'table_name_cn', 'table_name', 'db_name', 'title', 'column_name'];
    for (const key of keys) { const value = row[key]; if (value != null && typeof value !== 'object') return String(value); }
    const source = row.source;
    return source && typeof source === 'object' && 'name' in source ? String(source.name ?? '') : '';
}
function compareTableValues(left: string, right: string, time: boolean): number {
    if (!left || !right) return left ? -1 : right ? 1 : 0;
    if (time) {
        const timestamp = (value: string) => /^\d{13}$/.test(value) ? Number(value) : /^\d{10}$/.test(value) ? Number(value) * 1000 : Date.parse(value);
        const a = timestamp(left), b = timestamp(right);
        if (Number.isFinite(a) && Number.isFinite(b)) return a - b;
    }
    return tableSortCollator.compare(left, right);
}
export function DataTable({ rows = [], columns, loading, error, onRefresh, actions, rowSelection, onRow, titleKey, serverPagination, onSearch, onExport, searchPlaceholder, initialSearch = '', showSearch = true, showExport = true, emptyTitle, emptyDescription }: {
    rows?: Row[] | null;
    columns: ColumnsType<Row>;
    loading?: boolean;
    error?: unknown;
    onRefresh?: () => void;
    actions?: ReactNode;
    rowSelection?: any;
    onRow?: any;
    titleKey?: string;
    serverPagination?: { page: number; size: number; total: number; onChange: (page: number, size: number) => void };
    onSearch?: (keyword: string) => void;
    onExport?: () => Promise<void>;
    searchPlaceholder?: string;
    initialSearch?: string;
    emptyTitle?: string;
    emptyDescription?: string;
    showSearch?: boolean;
    showExport?: boolean;
}) {
    const preferenceKey = 'wanxiang:compact-table:' + encodeURIComponent(titleKey ?? location.hash.split('?')[0] + ':' + columns.map(c => String(c.key ?? ('dataIndex' in c ? c.dataIndex : c.title))).join('|'));
    const readPrefs = () => {
        try {
            return compactPreference(JSON.parse(localStorage.getItem(preferenceKey) ?? '{}'));
        }
        catch {
            return compactPreference(null);
        }
    };
    const [prefs, setPrefs] = useState(readPrefs), [q, setQ] = useState(initialSearch), [page, setPage] = useState(1);
    const [exporting, setExporting] = useState(false);
    const { message } = App.useApp();
    useEffect(() => { setPrefs(readPrefs()); setQ(initialSearch); setPage(1); }, [preferenceKey, initialSearch]);
    const updatePrefs = (next: typeof prefs) => {
        setPrefs(next);
        try {
            localStorage.setItem(preferenceKey, JSON.stringify(next));
        }
        catch { /* Preference persistence is optional, never business persistence. */ }
    };
    const filtered = serverPagination ? rows ?? [] : (rows ?? []).filter(r => JSON.stringify(r).toLocaleLowerCase().includes(q.trim().toLocaleLowerCase()));
    useEffect(() => { if (!serverPagination) setPage(p => Math.min(p, Math.max(1, Math.ceil(filtered.length / prefs.size)))); }, [filtered.length, prefs.size, serverPagination]);
    const named = columns.map((col, i) => ({ col, key: String(col.key ?? ('dataIndex' in col ? col.dataIndex : undefined) ?? i), mandatory: i === 0 || col.fixed === 'right' || col.title === '操作' }));
    const visible = named.filter(c => c.mandatory || !prefs.hidden.includes(c.key)).map((c, index) => ({ ...c.col,
      ...(typeof c.col.title === 'string' && c.col.title !== '操作' && (tableNameTitle.test(c.col.title) || tableTimeTitle.test(c.col.title)) && !c.col.sorter && filtered.some(row=>tableSortValue(row,c.col.title as string,'dataIndex' in c.col ? c.col.dataIndex : undefined))
        ? { sorter: (a: Row, b: Row) => compareTableValues(tableSortValue(a, c.col.title as string, 'dataIndex' in c.col ? c.col.dataIndex : undefined), tableSortValue(b, c.col.title as string, 'dataIndex' in c.col ? c.col.dataIndex : undefined), tableTimeTitle.test(c.col.title as string)), showSorterTooltip: { title: serverPagination ? '仅对当前页排序' : '按全部记录排序' } }
        : {}),
      ...(c.col.title === '操作' ? { className: 'wx-action-column', fixed: 'right' as const, align: 'center' as const } : {}),
      ...(index === 0 && !c.col.render ? { className: 'wx-primary-column' } : {}),
      ellipsis: c.col.ellipsis ?? false }));
    const exportRows = () => {
        const keys = [...new Set(filtered.flatMap(r => Object.keys(r)))];
        const safe = (v: unknown) => {
            let s = text(v);
            if (/^[=+@\-\t\r]/.test(s))
                s = "'" + s;
            return '"' + s.replaceAll('"', '""') + '"';
        };
        download('\uFEFF' + [keys.map(safe).join(','), ...filtered.map(r => keys.map(k => safe(r[k])).join(','))].join('\r\n'), '万象-当前筛选.csv', 'text/csv;charset=utf-8');
    };
    const exportResult = async () => { if (exporting) return; if (!onExport) { exportRows(); return; } setExporting(true); try { await onExport(); } catch (cause) { message.error(cause instanceof Error ? cause.message : String(cause)); } finally { setExporting(false); } };
    const settings = <div className="wx-table-settings"><h4>表格显示</h4><Segmented aria-label="表格密度" block value={prefs.density} options={[{ value: 'small', label: '紧凑' }, { value: 'middle', label: '舒适' }]} onChange={value => updatePrefs({ ...prefs, density: value as 'small' | 'middle' })}/><p>显示列</p>{named.map(c => <Checkbox key={c.key} disabled={c.mandatory} checked={c.mandatory || !prefs.hidden.includes(c.key)} onChange={e => updatePrefs({ ...prefs, hidden: e.target.checked ? prefs.hidden.filter(k => k !== c.key) : [...prefs.hidden, c.key] })}>{typeof c.col.title === 'string' ? c.col.title : c.key}</Checkbox>)}<Button block onClick={() => updatePrefs(compactPreference(null))}>恢复默认</Button><small>隐藏列仅影响显示，不改变导出权限或数据范围。</small></div>;
    return <div className="platform-panel"><ErrorNotice error={error} retry={onRefresh}/><div className="platform-table-toolbar">{showSearch && <Input className="wx-table-search" aria-label="搜索当前表格" prefix={<SearchOutlined />} allowClear value={q} placeholder={searchPlaceholder || '搜索名称、代码或字段…'} onChange={e => { setQ(e.target.value); setPage(1); if (!e.target.value) onSearch?.(''); }} onPressEnter={() => onSearch?.(q.trim())} suffix={onSearch ? <Button type="text" aria-label="查询当前表格" icon={<SearchOutlined />} onClick={() => onSearch(q.trim())}/> : undefined}/>}<span className="table-count">{error && !rows?.length ? '读取失败' : loading && !rows?.length ? '读取中…' : `${serverPagination?.total ?? filtered.length} 条记录`}</span><span className="grow"/><div className="table-toolbar-actions">{actions}{onRefresh && <Tooltip title="刷新"><Button aria-label="刷新工作区数据" icon={<ReloadOutlined spin={!!loading}/>} onClick={onRefresh}/></Tooltip>}<Popover trigger="click" placement="bottomRight" content={settings}><Button aria-label="列设置与密度" icon={<SettingOutlined />}/></Popover>{showExport&&<Tooltip title={serverPagination?'导出当前页':'导出筛选后的全部记录'}><Button aria-label={serverPagination?'导出当前页':'导出当前筛选结果'} loading={exporting} disabled={loading || !filtered.length} icon={<DownloadOutlined />} onClick={() => void exportResult()}/></Tooltip>}</div></div>
 {rowSelection?.selectedRowKeys?.length > 0 && <div className="wx-selection-bar"><span>已选择 <b>{rowSelection.selectedRowKeys.length}</b> 项</span><Button type="link" size="small" onClick={() => rowSelection.onChange?.([], [], { type: 'none' })}>清除选择</Button></div>}
 <Table<Row> rowKey="id" loading={loading && !(rows?.length)} dataSource={filtered} size={prefs.density} scroll={{ x: 'max-content' }} columns={[{ title: '#', key: '__row_number', width: 54, align: 'center', render: (_v, _r, i) => <span className="row-index">{((serverPagination?.page ?? page) - 1) * (serverPagination?.size ?? prefs.size) + i + 1}</span> }, ...visible]} rowSelection={rowSelection} onRow={onRow} pagination={{ current: serverPagination?.page ?? page, pageSize: serverPagination?.size ?? prefs.size, total: serverPagination?.total ?? filtered.length, showSizeChanger: true, pageSizeOptions: [15, 30, 50, 100], showTotal: n => `共 ${n} 条`, onChange: (p, size) => { if (serverPagination) serverPagination.onChange(size !== serverPagination.size ? 1 : p, size); else setPage(size !== prefs.size ? 1 : p); updatePrefs({ ...prefs, size }); } }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<><strong>{q ? '没有匹配的记录' : error ? '暂时无法读取数据' : (emptyTitle ?? '暂无业务记录')}</strong><p>{q ? '调整关键词，或清除搜索查看全部。' : error ? '页面配置已保留，请检查连接后重试。' : (emptyDescription ?? '登记数据或创建业务对象后，内容将在此显示。')}</p>{q && <Button size="small" onClick={() => { setQ(''); onSearch?.(''); }}>清除搜索</Button>}</>}/> }}/></div>;
}
export function download(value: string, name: string, type = 'application/json') { const url = URL.createObjectURL(new Blob([value], { type })); const a = document.createElement('a'); a.href = url; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
const resultLabels: Record<string, string> = { status: '状态', eligibleRows: '参与检查行数', violationRows: '违例行数', matchedRows: '匹配行数', sourceDistinctTuples: '源去重元组', targetDistinctTuples: '目标去重元组', sampleRows: '样本行数', sourceRows: '源行数', coverage: '覆盖情况', message: '结果说明', method: '统计方法', observedAt: '观测时间', created_at: '创建时间', name: '名称', kind: '类型', fieldIds: '字段标识', nullable: '可空', enforced: '是否强制', validated: '是否验证', metrics: '统计指标', results: '检查结果', count: '数量', scope: '检查范围', warnings: '注意事项', error: '执行异常', code: '代码', version: '版本', entityId: '实体标识', fieldId: '字段标识' };
function ResultTree({ value, depth = 0 }: {
    value: unknown;
    depth?: number;
}) {
    if (value === null || typeof value !== 'object')
        return <span>{text(value)}</span>;
    if (depth >= 4)
        return <pre className="json-detail">{JSON.stringify(value, null, 2)}</pre>;
    const entries = Array.isArray(value) ? value.map((v, i) => [String(i + 1), v] as const) : Object.entries(value);
    if (!entries.length)
        return <span className="muted">暂无记录</span>;
    return <div className="wx-result-list">{entries.slice(0, 100).map(([k, v]) => v !== null && typeof v === 'object' ? <details key={k} open={depth === 0 && entries.length <= 4}><summary>{resultLabels[k] ?? k} <small>· {Array.isArray(v) ? v.length + ' 项' : '详细信息'}</small></summary><ResultTree value={v} depth={depth + 1}/></details> : <div className="wx-result-line" key={k}><span>{resultLabels[k] ?? k}</span><strong>{k === 'status' ? <Status value={v}/> : text(v)}</strong></div>)}{entries.length > 100 && <p className="muted">此处展示前 100 项，完整内容请切换「原始 JSON」。</p>}</div>;
}
export function JsonDetail({ value }: {
    value: unknown;
}) { const [raw, setRaw] = useState(false); return <section className="wx-result"><header className="wx-result-toolbar"><span>结果详情</span><Button type="text" onClick={() => setRaw(!raw)}>{raw ? '结构化查看' : '原始 JSON'}</Button></header>{raw ? <pre className="json-detail">{JSON.stringify(value, null, 2)}</pre> : <ResultTree value={value}/>}</section>; }
export function useAction() {
    const { message } = App.useApp();
    const [busy, setBusy] = useState(false);
    const lock = useRef(false);
    const run = async <T,>(action: () => Promise<T>, success = '操作已保存到当前浏览器'): Promise<T | undefined> => {
        if (lock.current)
            return;
        lock.current = true;
        setBusy(true);
        try {
            const v = await action();
            if (success)
                message.success(success);
            return v;
        }
        catch (e) {
            message.error(e instanceof Error ? e.message : String(e));
            return;
        }
        finally {
            lock.current = false;
            setBusy(false);
        }
    };
    return { busy, run };
}
export function EditorModal({ open, title, initial, onCancel, onSave, children, width = 620, presentation = 'modal', saveMessage = '已保存到当前浏览器', storageHint = '配置仅保存在当前浏览器', onSaved, onTest }: {
    open: boolean;
    title: string;
    initial?: Row;
    onCancel: () => void;
    onSave: (v: Row) => Promise<unknown>;
    children: ReactNode;
    width?: number;
    presentation?: 'modal' | 'drawer';
    saveMessage?: string;
    storageHint?: string;
    onSaved?: (result: unknown) => void;
    onTest?: (values: Row) => Promise<void>;
}) {
    const [form] = Form.useForm();
    const [busy, setBusy] = useState(false);
    const [testing, setTesting] = useState(false);
    const lock = useRef(false);
    const dirty = useRef(false);
    const { message, modal } = App.useApp();
    useEffect(() => {
        if (open) {
            form.resetFields();
            form.setFieldsValue(initial ?? {});
            dirty.current = false;
        }
    }, [open, initial?.id, form]);
    const close = () => {
        if (lock.current)
            return;
        if (dirty.current)
            modal.confirm({ title: '放弃未保存的修改？', content: '本次表单内容尚未提交。', okText: '放弃修改', cancelText: '继续编辑', onOk: onCancel });
        else
            onCancel();
    };
    const test = async () => {
        if (lock.current || !onTest) return;
        lock.current=true;
        try { const values=await form.validateFields();setBusy(true);setTesting(true);await onTest(values);message.success('数据库连接测试通过'); }
        catch(error) { if(error instanceof Error)message.error(error.message);else if(error&&typeof error==='object'&&'errorFields' in error){const fields=(error as {errorFields:{name:(string|number)[]}[]}).errorFields;if(fields[0])form.scrollToField(fields[0].name,{block:'center'});} }
        finally {setTesting(false);setBusy(false);lock.current=false;}
    };
    const save = async () => {
        if (lock.current)
            return;
        lock.current = true;
        try {
            const v = await form.validateFields();
            setBusy(true);
            const result = await onSave(v);
            message.success(saveMessage);
            onCancel();
            onSaved?.(result);
        }
        catch (e) {
            if (e instanceof Error)
                message.error(e.message);
            else if (e && typeof e === 'object' && 'errorFields' in e) {
                const fields = (e as {
                    errorFields: {
                        name: (string | number)[];
                    }[];
                }).errorFields;
                if (fields[0])
                    form.scrollToField(fields[0].name, { block: 'center' });
            }
        }
        finally {
            setBusy(false);
            lock.current = false;
        }
    };
    const content = <Form form={form} initialValues={initial} layout="vertical" preserve={false} requiredMark className="wx-editor-form" onValuesChange={() => { dirty.current = true; }}>{children}</Form>;
    return presentation === 'drawer' ? <Drawer destroyOnHidden open={open} title={title} width={Math.min(width, 620)} onClose={close} maskClosable={false} rootClassName="wx-editor-drawer" footer={<div className="editor-footer"><span>{storageHint}</span><Space><Button onClick={close} disabled={busy}>取消</Button>{onTest&&<Button loading={testing} disabled={busy&&!testing} onClick={test}>测试连接</Button>}<Button type="primary" loading={busy&&!testing} disabled={testing} onClick={save}>保存配置</Button></Space></div>}>{content}</Drawer> : <Modal destroyOnHidden open={open} title={title} width={width} onCancel={close} okText="保存" cancelText="取消" confirmLoading={busy} maskClosable={false} onOk={save}>{content}</Modal>;
}
export const required = [{ required: true, message: '请填写此项' }];
export const opts = (rows: Row[], key = 'id', name = 'name') => rows.map(r => ({ value: r[key], label: String(r[name] ?? r.code ?? r.id) }));
export const statusColumn: any = { title: '状态', dataIndex: 'status', width: 126, render: (v: unknown) => <Status value={v}/> };
export const timeColumn: any = { title: '更新时间', key: 'updated', width: 180, render: (_: unknown, r: Row) => date(r.updatedAt ?? r.updated_at ?? r.created_at) };
export function JobDrawer({ id, onClose }: {
    id: string | null;
    onClose: () => void;
}) { const q = useWorkspaceQuery(`job-${id}`, async (signal) => id ? Promise.all([api(`jobs/${id}`, { signal }), api(`jobs/${id}/events`, { signal })]) : null, 1500); const { busy, run } = useAction(); const job = q.data?.[0], events = q.data?.[1]?.items ?? []; return <Drawer open={!!id} title="任务执行详情" width={660} onClose={onClose}><ErrorNotice error={q.error} retry={q.refresh}/>{job && <><Space wrap><Status value={job.status}/><Tag>{job.type}</Tag><Typography.Text copyable>{id}</Typography.Text></Space><p>{job.phase || '等待执行'}</p><p className="muted">{job.done_count ?? 0} / {job.total_count ?? '尚未确定'} · {date(job.created_at)}</p><Space>{['QUEUED', 'RUNNING'].includes(job.status) && <Button loading={busy} danger onClick={() => run(() => api(`jobs/${id}/cancel`, { method: 'POST', version: versionOf(job), body: {} }), '任务已取消，未提交的结果不会保存')}>请求取消</Button>}{['FAILED', 'PARTIAL', 'CANCELLED'].includes(job.status) && canWrite() && <Button loading={busy} onClick={() => run(() => api(`jobs/${id}/retry`, { method: 'POST', body: {} }), '已创建新的重试任务')}>重试</Button>}</Space>{job.error_message && <Alert type="error" title={job.error_message}/>}<h3>执行事件</h3>{events.length ? events.map((e: Row) => <div key={e.id} className="job-event"><small>{date(e.created_at)}</small><strong>{e.phase ?? e.event_type ?? e.message_text}</strong><span>{e.message_text ?? e.message}</span></div>) : <Empty description="尚无执行事件"/>}<h3>结果摘要</h3><JsonDetail value={job.result_json ?? job.result ?? {}}/></>}</Drawer>; }
