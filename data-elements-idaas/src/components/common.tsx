import { useEffect, useState } from 'react';
import type { ReactNode, Key } from 'react';
import { App, Alert, Button, Card, Col, Descriptions, Drawer, Empty, Form, Input, InputNumber, Row, Select, Space, Switch, Table, Tag, Typography, DatePicker, theme, TreeSelect, Segmented } from 'antd';
import type { TableProps } from 'antd';
import { DownloadOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, RightOutlined, InfoCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { encodeCsv } from '../domain/csv';
import { api, useSession } from '../mock/store';
import { ImageValueInput } from './ImageValueInput';
import { canEdit } from '../domain/engine';
import type { Base } from '../domain/types';
export const { Text, Title, Paragraph } = Typography;
export const dateText = (value: string | undefined | null) => value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';
export const options = (items: {
    id: string;
    name: string;
}[]) => items.map(x => ({ value: x.id, label: x.name }));
const labels: Record<string, string> = { enabled: '已启用', disabled: '已停用', pending: '待完善', running: '执行中', success: '成功', partial: '部分成功', failed: '失败', cancelled: '已中止', revoked: '已撤销', expired: '已到期', locked: '已锁定', unconfigured: '未配置' };
export function StatusTag({ value }: {
    value: string;
}) { const color = ({ enabled: 'success', success: 'success', disabled: 'default', pending: 'warning', running: 'processing', partial: 'warning', failed: 'error', cancelled: 'default', revoked: 'default', expired: 'warning', locked: 'error', '成功': 'success', '失败': 'error' } as Record<string, string>)[value] || 'default'; return <Tag color={color}>{labels[value] || value}</Tag>; }
export function PageTitle({ title, description, extra, eyebrow }: {
    title: string;
    description?: string;
    extra?: ReactNode;
    eyebrow?: string;
}) { return <div className="page-title"><div>{eyebrow && <Text type="secondary" className="eyebrow">{eyebrow}</Text>}<Title level={1} className="page-heading">{title}</Title>{description && <Paragraph type="secondary" className="page-description">{description}</Paragraph>}</div><div className="page-title-actions">{extra}</div></div>; }

export function useAction() {
    const { message } = App.useApp();
    const [busy, setBusy] = useState(false);
    const run = async (fn: () => Promise<unknown>, success = '已保存') => {
        setBusy(true);
        try {
            await fn();
            if (success)
                message.success(success);
            return true;
        }
        catch (e) {
            message.error(e instanceof Error ? e.message : '操作失败，请重试');
            return false;
        }
        finally {
            setBusy(false);
        }
    };
    return { run, busy };
}
export function useEditable(section: string) { return canEdit(useSession(), section); }
export function exportCsv(filename: string, headers: string[], rows: unknown[][]) { const blob = new Blob([encodeCsv([headers, ...rows])], { type: 'text/csv;charset=utf-8' }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = filename.endsWith('.csv') ? filename : `${filename}.csv`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 500); }
export function DataTable<T extends Base>({ data, columns, title, searchPlaceholder = '搜索名称或编码', searchFields = ['name'], actions, selection, onSelection, loading = false, extraFilters, hideStatus = false, exporter }: {
    data: T[];
    columns: TableProps<T>['columns'];
    title?: string;
    searchPlaceholder?: string;
    searchFields?: string[];
    actions?: ReactNode;
    selection?: Key[];
    onSelection?: (keys: Key[]) => void;
    loading?: boolean;
    extraFilters?: ReactNode;
    hideStatus?: boolean;
    exporter?: (rows: T[]) => void;
}) {
    const [input, setInput] = useState('');
    const [query, setQuery] = useState('');
    const [status, setStatus] = useState('all');
    const [chosen, setChosen] = useState('all');
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const filtered = data.filter(r => (status === 'all' || r.status === status) && (!query || searchFields.some(k => String((r as unknown as Record<string, unknown>)[k] ?? '').toLowerCase().includes(query.toLowerCase()))));
    useEffect(() => {
        if (page > Math.max(1, Math.ceil(filtered.length / pageSize)))
            setPage(1);
    }, [filtered.length, page, pageSize]);
    return <Card size="small" className="table-card" styles={{ body: { padding: 0 } }}>
 <div className="table-filter"><Input aria-label={searchPlaceholder} prefix={<SearchOutlined />} placeholder={searchPlaceholder} value={input} onChange={e => setInput(e.target.value)} onPressEnter={() => { setQuery(input); setStatus(chosen); setPage(1); onSelection?.([]); }} allowClear style={{ width: 260, maxWidth: '100%' }}/>{!hideStatus && <Select aria-label="状态筛选" value={chosen} onChange={setChosen} style={{ width: 132, maxWidth: '100%' }} options={[{ value: 'all', label: '全部状态' }, ...Array.from(new Set(data.map(r => r.status))).map(s => ({ value: s, label: labels[s] || s }))]}/>} {extraFilters}
 <Button type="primary" onClick={() => { setQuery(input); setStatus(chosen); setPage(1); onSelection?.([]); }}>查询</Button><Button onClick={() => { setInput(''); setQuery(''); setStatus('all'); setChosen('all'); setPage(1); onSelection?.([]); }}>重置</Button></div>
 <div className="table-toolbar"><Space wrap><Text strong className="table-title">{title || '数据列表'}</Text><span className="table-count">共 {filtered.length.toLocaleString()} 项</span>{!!selection?.length && <Tag color="blue">已选 {selection.length} 项</Tag>}</Space><Space wrap>{exporter && <Button icon={<DownloadOutlined />} onClick={() => exporter(filtered)}>导出</Button>}{actions}</Space></div>
 <Table<T> rowKey="id" dataSource={filtered} columns={columns} size="small" loading={loading} rowSelection={onSelection ? { selectedRowKeys: selection, onChange: keys => onSelection(keys), preserveSelectedRowKeys: false } : undefined} pagination={{ current: page, onChange: (p, size) => { setPage(p); setPageSize(size); onSelection?.([]); }, pageSize, pageSizeOptions: [10, 20, 50, 100], showSizeChanger: true, showTotal: n => `共 ${n} 项` }} scroll={{ x: 'max-content' }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={query || status !== 'all' ? '未找到符合条件的结果' : '暂无数据'}/> }}/>
 </Card>;
}
export interface Field {
    name: string;
    label: string;
    type?: 'text' | 'textarea' | 'number' | 'select' | 'multi' | 'switch' | 'date' | 'password' | 'tree' | 'image';
    required?: boolean;
    options?: {
        label: string;
        value: string;
    }[];
    treeData?: unknown[];
    span?: 12 | 24;
    help?: string;
    disabled?: boolean;
    max?: number;
    min?: number;
    pattern?: RegExp;
    patternMessage?: string;
}
export type FormValues = Record<string, unknown>;
export function RecordEditor({ open, title, initial, fields, onClose, onSave, width = 560, children }: {
    open: boolean;
    title: string;
    initial: FormValues;
    fields: Field[];
    onClose: () => void;
    onSave: (values: FormValues) => Promise<void>;
    width?: number;
    children?: ReactNode;
}) {
    const [form] = Form.useForm();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const { modal, message } = App.useApp();
    useEffect(() => {
        if (open) {
            form.resetFields();
            const v = { ...initial };
            fields.filter(f => f.type === 'date').forEach(f => {
                if (v[f.name])
                    v[f.name] = dayjs(String(v[f.name]));
            });
            form.setFieldsValue(v);
            setError('');
        }
    }, [open]);
    const close = () => {
        if (busy)
            return;
        if (form.isFieldsTouched())
            modal.confirm({ title: '放弃未保存的修改？', content: '关闭后，本次输入不会保存。', okText: '放弃修改', cancelText: '继续编辑', onOk: onClose });
        else
            onClose();
    };
    const submit = async () => {
        try {
            const v = await form.validateFields();
            fields.filter(f => f.type === 'date').forEach(f => { v[f.name] = v[f.name] ? v[f.name].toISOString() : null; });
            setBusy(true);
            setError('');
            await onSave(v);
            message.success('已保存');
            onClose();
        }
        catch (e) {
            if (e instanceof Error)
                setError(e.message);
        }
        finally {
            setBusy(false);
        }
    };
    return <Drawer title={title} open={open} size={width} onClose={close} destroyOnHidden footer={<div className="drawer-footer"><Button onClick={close}>取消</Button><Button type="primary" loading={busy} onClick={submit}>保存</Button></div>}>
 {error && <Alert title={error} type="error" showIcon style={{ marginBottom: 16 }}/>}{children}<div className="editor-intro">请填写以下信息。标记 * 的为必填项，保存后将更新当前身份域的业务信息。</div><Form size="small" form={form} initialValues={initial} layout="vertical" requiredMark onFinish={submit} preserve={false} scrollToFirstError><Row gutter={12}>{fields.map(f => <Col key={f.name} xs={24} sm={f.span || 24}><Form.Item name={f.name} label={f.label} valuePropName={f.type === 'switch' ? 'checked' : 'value'} extra={f.help} rules={[{ required: f.required, message: `请${['select', 'multi', 'tree', 'date'].includes(f.type || '') ? '选择' : '填写'}${f.label}` }, ...(f.pattern ? [{ pattern: f.pattern, message: f.patternMessage || `${f.label}格式不正确` }] : []), ...(!['number', 'switch', 'multi', 'date', 'tree'].includes(f.type || '') ? [{ max: f.max || 500, message: `最多${f.max || 500}个字符` }] : [])]}>
 {f.type === 'textarea' ? <Input.TextArea rows={3} disabled={f.disabled} showCount maxLength={f.max || 500}/> : f.type === 'image' ? <ImageValueInput disabled={f.disabled}/> : f.type === 'select' || f.type === 'multi' ? <Select disabled={f.disabled} mode={f.type === 'multi' ? 'multiple' : undefined} allowClear showSearch optionFilterProp="label" options={f.options} placeholder={`请选择${f.label}`}/> : f.type === 'switch' ? <Switch disabled={f.disabled}/> : f.type === 'number' ? <InputNumber disabled={f.disabled} min={f.min ?? 0} max={f.max ?? 999999} style={{ width: '100%' }}/> : f.type === 'date' ? <DatePicker showTime disabled={f.disabled} style={{ width: '100%' }}/> : f.type === 'tree' ? <TreeSelect treeData={f.treeData as never} treeDefaultExpandAll showSearch treeNodeFilterProp="title" disabled={f.disabled} allowClear/> : f.type === 'password' ? <Input.Password autoComplete="new-password" disabled={f.disabled}/> : <Input disabled={f.disabled} maxLength={f.max || 500} placeholder={`请输入${f.label}`}/>}</Form.Item></Col>)}</Row></Form>
 </Drawer>;
}
export function DetailDrawer({ open, title, items, onClose, children }: {
    open: boolean;
    title: string;
    items: {
        label: string;
        children: ReactNode;
    }[];
    onClose: () => void;
    children?: ReactNode;
}) { return <Drawer title={title} open={open} size={736} onClose={onClose} destroyOnHidden><Descriptions size="small" column={{ xs: 1, sm: 2 }} layout="vertical" items={items.map((v, i) => ({ ...v, key: i }))}/>{children}</Drawer>; }
export function ConfirmDelete({ label = '删除', target, onConfirm, disabled = false }: {
    label?: string;
    target: string;
    onConfirm: () => Promise<unknown>;
    disabled?: boolean;
}) {
    const { modal } = App.useApp();
    const { run } = useAction();
    return <Button type="link" danger disabled={disabled} style={{ paddingInline: 0 }} onClick={() => modal.confirm({ title: `${label}“${target}”？`, content: '请确认操作对象及其关联影响，提交后将更新对应记录。', okText: label, cancelText: '取消', okButtonProps: { danger: true }, onOk: async () => {
                const ok = await run(onConfirm, `已${label}`);
                if (!ok)
                    throw new Error('操作未完成');
            } })}>{label}</Button>;
}
export function CardMetric({ label, value, description, icon, onClick, tone = 'blue' }: {
    label: string;
    value: number | string;
    description: string;
    icon: ReactNode;
    onClick?: () => void;
    tone?: 'blue' | 'purple' | 'cyan' | 'green' | 'orange' | 'red';
}) {
    const colors = { blue: ['#5267F5', '#EBEFFF'], purple: ['#8654EB', '#F1EBFF'], cyan: ['#159FE5', '#E8F5FF'], green: ['#12AF86', '#E7F8F2'], orange: ['#F49130', '#FFF1E5'], red: ['#EF5A73', '#FFECEF'] };
    const [color, bg] = colors[tone];
    return <Card size="small" className="metric-card" onClick={onClick} styles={{ body: { padding: '12px 14px' } }} style={{ cursor: onClick ? 'pointer' : undefined }}>
    <div className="metric-card-inner"><span className="metric-icon" style={{ background: `linear-gradient(140deg,${bg},${bg}AA)`, color }}>{icon}</span><div className="metric-main"><div className="metric-top"><Text className="metric-label">{label}</Text></div><div className="metric-value">{typeof value === 'number' ? value.toLocaleString() : value}</div><div className="metric-desc" title={description}>{description}</div></div></div>{onClick && <RightOutlined className="metric-chevron"/>}
  </Card>;
}
