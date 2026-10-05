import { useEffect, useState } from 'react';
import type { Key } from 'react';
import { Alert, Button, Card, Descriptions, Drawer, Empty, Input, Segmented, Select, Space, Table } from 'antd';
import { PlusOutlined, SyncOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { provisionApi, useDatabase, type ReceiverConfig, type ProvisionTask, type ProvisionObject, type TenantOption } from '../mock/store';
import type { Domain } from '../domain/types';
import { PageTitle, RecordEditor, StatusTag, Text, dateText, useAction, useEditable, type FormValues } from '../components/common';

export default function Synchronization({ domain, view }: { domain: Domain; view: 'configs' | 'entities' | 'tasks' }) {
    const db = useDatabase(); const nav = useNavigate(); const { run, busy } = useAction(); const editable = useEditable('tasks');
    const [configs, setConfigs] = useState<ReceiverConfig[]>([]); const [tasks, setTasks] = useState<ProvisionTask[]>([]);
    const [targets, setTargets] = useState<TenantOption[]>([]); const [error, setError] = useState(''); const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState<ReceiverConfig | null>(null); const [open, setOpen] = useState(false);
    const [configId, setConfigId] = useState(''); const [kind, setKind] = useState('user'); const [selected, setSelected] = useState<Key[]>([]);
    const [objectPage, setObjectPage] = useState(1); const [objectSize, setObjectSize] = useState(20);
    const [objectQuery, setObjectQuery] = useState(''); const [objects, setObjects] = useState<ProvisionObject[]>([]);
    const [objectTotal, setObjectTotal] = useState(0); const [objectLoading, setObjectLoading] = useState(false);
    const [taskId, setTaskId] = useState(''); const [progress, setProgress] = useState('');
    const [submission, setSubmission] = useState<{ key: string; requestId: string } | null>(null);
    const ready = (config: ReceiverConfig) => config.enabled && config.lastTestResult === 'SUCCESS';
    const readyConfigs = configs.filter(ready);
    const selectedConfig = readyConfigs.find(config => config.id === configId);
    const load = async () => {
        setLoading(true); setError('');
        try {
            const [connections, history, options] = await Promise.all([provisionApi.configs(), provisionApi.tasks(), provisionApi.options()]);
            setConfigs(connections); setTasks(history); setTargets(options);
            setConfigId(current => connections.some(c => c.id === current && ready(c)) ? current : connections.find(ready)?.id || '');
        } catch (e) { setError(e instanceof Error ? e.message : '无法读取下发配置'); }
        finally { setLoading(false); }
    };
    useEffect(() => { if (editable) void load(); else setLoading(false); }, [view, editable]);
    useEffect(() => {
        if (view !== 'entities' || !editable || !selectedConfig) return;
        let active = true;
        setObjectLoading(true);
        void provisionApi.objects({ configId: selectedConfig.id, kind, page: objectPage, size: objectSize, q: objectQuery })
            .then(result => { if (active) { setObjects(result.list); setObjectTotal(result.total); setError(''); } })
            .catch(failure => { if (active) setError(failure instanceof Error ? failure.message : '同步对象加载失败'); })
            .finally(() => { if (active) setObjectLoading(false); });
        return () => { active = false; };
    }, [view, editable, selectedConfig?.id, kind, objectPage, objectSize, objectQuery, db]);
    const execute = async (id: string) => {
        try {
            for (let round = 0; round < 100; round++) {
                await provisionApi.execute(id);
                const history = await provisionApi.tasks(); setTasks(history);
                const current = history.find(t => t.id === id);
                if (!current) throw new Error('任务记录暂时不可读取，请通过任务编号查看结果');
                setProgress(`已处理 ${current.items.filter(i => i.status === 'success').length} / ${current.items.length} 项`);
                if (current.status !== 'pending') {
                    if (current.status !== 'success') throw new Error('下发包含失败项，请查看任务明细，修正后重试');
                    return;
                }
            }
            throw new Error('任务仍有待处理项，请在任务列表继续执行');
        } finally { setProgress(''); }
    };
    const submit = () => run(async () => {
        if (!selectedConfig) throw new Error('请先测试并启用目标接收配置。');
        const ids = selected.map(String);
        if (ids.length > 1000) throw new Error('每次最多选择 1000 项');
        const key = JSON.stringify({ configId, kind, ids });
        const requestId = submission?.key === key ? submission.requestId : crypto.randomUUID(); setSubmission({ key, requestId });
        const task = await provisionApi.create(configId, requestId, view === 'entities' ? kind : undefined, ids); setSubmission(null);
        await execute(task.id); nav(`/console/${domain}/sync/tasks`);
    }, '下发已完成');
    const task = tasks.find(t => t.id === taskId);
    const defaults = editing ? { ...editing, secret: '' } : { name: '', targetTenantId: '', receiverInstanceId: '', endpoint: '', keyId: '', secret: '', timeout: 10, enabled: false };
    const targetName = (id: string) => targets.find(t => t.tenantId === id)?.tenantName || id;
    return <>
        <PageTitle title={view === 'configs' ? '同步配置' : view === 'entities' ? '同步对象' : '同步任务'} description={view === 'configs' ? '配置各租户系统的接收地址和接入凭据；配置本身不会触发下发。' : view === 'entities' ? '选择中央目录中的人员或机构，点击下发后才向指定租户系统发送。' : '查看手动下发的逐条回执；失败可手动重试，中断可继续执行。'} extra={view === 'configs' && <Button type="primary" icon={<PlusOutlined />} disabled={!editable || busy} onClick={() => { setEditing(null); setOpen(true); }}>新建同步配置</Button>}/>
        {!editable ? <Empty description="当前账号尚未获得下发权限"/> : <>
            {error && <Alert className="section-gap" type="error" showIcon title={error} action={<Button onClick={() => void load()}>重试读取</Button>}/>}
            {progress && <Alert className="section-gap" type="info" showIcon title={progress}/>}
            {view === 'configs' ? <Card size="small" title="接收系统配置"><Table size="small" rowKey="id" loading={loading} dataSource={configs} scroll={{ x: 'max-content' }} columns={[
                { title: '配置名称', dataIndex: 'name' }, { title: '目标租户', render: (_, c) => targetName(c.targetTenantId) },
                { title: '接收地址', dataIndex: 'endpoint', ellipsis: true, width: 360 }, { title: '状态', render: (_, c) => <StatusTag value={c.enabled ? 'enabled' : 'disabled'}/> },
                { title: '操作', render: (_, c) => <Space wrap><Button type="link" disabled={busy} onClick={() => { setEditing(c); setOpen(true); }}>编辑</Button><Button type="link" disabled={busy} onClick={() => run(() => provisionApi.test(c.id), '接收接口认证与目标校验通过')}>测试连接</Button><Button type="link" disabled={busy || !ready(c)} onClick={() => { setConfigId(c.id); nav(`/console/${domain}/sync/entities`); }}>选择下发对象</Button></Space> },
            ]}/></Card> : view === 'entities' ? <>
                <Card size="small" className="section-gap"><Space wrap><Text strong>目标配置</Text><Select aria-label="目标同步配置" style={{ minWidth: 220 }} value={selectedConfig?.id} options={readyConfigs.map(c => ({ value: c.id, label: c.name }))} onChange={value => { setConfigId(value); setSelected([]); setSubmission(null); setObjectPage(1); }}/><Segmented value={kind} onChange={v => { setKind(String(v)); setSelected([]); setSubmission(null); setObjectPage(1); }} options={[{ label: '人员', value: 'user' }, { label: '机构', value: 'org' }]}/><Button type="primary" icon={<SyncOutlined />} disabled={!selectedConfig || busy || (selected.length === 0 && objectTotal === 0)} loading={busy} onClick={submit}>下发{selected.length ? `选中 ${selected.length} 项` : '全部对象'}</Button></Space></Card>
                {!loading && !readyConfigs.length && <Alert className="section-gap" type="warning" showIcon title="尚无已测试并启用的接收配置" description="请先在同步配置中测试连接并启用目标配置，再人工下发。"/>}
                <Alert className="section-gap" type="info" showIcon title="人员资料不包含登录密码" description="下发会先补齐所需机构。新账号由租户设置本地密码和角色；已有账号保留本地密码、角色及本地停用设置。"/>
                <Card size="small" title="中央目录对象" extra={<Input.Search placeholder="搜索名称或账号" allowClear onSearch={value => { setObjectQuery(value.trim()); setObjectPage(1); }} style={{ width: 240 }}/>}><Table<ProvisionObject> size="small" rowKey="id" dataSource={objects} loading={objectLoading} pagination={{ current: objectPage, pageSize: objectSize, total: objectTotal, showSizeChanger: true, pageSizeOptions: [20, 50, 100], onChange: (page, size) => { setObjectPage(page); setObjectSize(size); } }} scroll={{ x: 'max-content' }} rowSelection={{ selectedRowKeys: selected, onChange: setSelected, preserveSelectedRowKeys: true }} columns={[
                    { title: '名称', dataIndex: 'name' }, { title: '拟用账号 / 机构编码', render: (_, record) => kind === 'user' ? record.account : record.code },
                    { title: '版本', dataIndex: 'version' }, { title: '状态', dataIndex: 'status', render: value => <StatusTag value={value}/> },
                ]}/></Card>
            </> : <Card size="small" title="下发任务记录"><Space className="section-gap"><Button disabled={busy} onClick={() => void load()}>查看最新结果</Button><Text type="secondary">共 {tasks.length} 个最近任务</Text></Space><Table size="small" rowKey="id" loading={loading} dataSource={tasks} scroll={{ x: 'max-content' }} columns={[
                { title: '目标配置', dataIndex: 'name' }, { title: '创建时间', dataIndex: 'createdAt', render: dateText },
                { title: '处理结果', render: (_, t) => `${t.items.filter(i => i.status === 'success').length} 成功 / ${t.items.filter(i => i.status === 'failed').length} 失败 / ${t.items.filter(i => i.status === 'pending').length} 待处理` },
                { title: '状态', dataIndex: 'status', render: value => <StatusTag value={value}/> },
                { title: '操作', render: (_, t) => <Space><Button type="link" onClick={() => setTaskId(t.id)}>明细</Button>{!['success', 'cancelled'].includes(t.status) && <><Button type="link" disabled={busy} onClick={() => run(async () => { if (['failed', 'partial'].includes(t.status)) await provisionApi.retry(t); await execute(t.id); await load(); }, '任务已完成')}>继续执行 / 重试</Button><Button type="link" disabled={busy} onClick={() => run(async () => { await provisionApi.cancel(t); await load(); }, '任务已取消')}>取消</Button></>}</Space> },
            ]}/></Card>}
        </>}
        <RecordEditor open={open} title={editing ? '编辑同步配置' : '新建同步配置'} width={736} initial={defaults as unknown as FormValues} onClose={() => setOpen(false)} fields={[
            { name: 'name', label: '配置名称', required: true, max: 100 }, { name: 'targetTenantId', label: '目标租户', type: 'select', required: true, disabled: !!editing, options: targets.map(t => ({ value: t.tenantId, label: t.tenantName })) },
            { name: 'endpoint', label: '下游接收地址', required: true, max: 1000, help: '填写租户系统提供的完整接收接口地址。正式地址使用 HTTPS。' },
            { name: 'keyId', label: '密钥标识', required: true, max: 64, disabled: !!editing, pattern: /^[A-Za-z0-9_-]+$/, patternMessage: '使用字母、数字、下划线或短横线' },
            { name: 'receiverInstanceId', label: '接收实例标识', required: true, max: 100, help: '与接收端部署登记的实例一致。先关闭保存、测试连接，再启用配置。' },
            { name: 'secret', label: '接入密钥', type: 'password', required: !editing, max: 256, help: editing ? '留空保留现有密钥；填写新值前请先更新接收端。' : '由租户接收系统提供，至少32个字符。保存后不会回显。' },
            { name: 'timeout', label: '单条下发超时（秒）', type: 'number', min: 1, max: 30, required: true }, { name: 'enabled', label: '启用配置', type: 'switch' },
        ]} onSave={async values => { await provisionApi.save({ ...editing, ...values } as Partial<ReceiverConfig> & { secret?: string }); await load(); }}/>
        <Drawer title="下发任务明细" open={!!task} onClose={() => setTaskId('')} size={900} destroyOnHidden>{task && <>
            <Descriptions size="small" column={1} items={[{ key: 'id', label: '任务编号', children: task.id }, { key: 'target', label: '目标租户', children: targetName(task.targetTenantId) }]}/>
            <Table size="small" rowKey="eventId" dataSource={task.items} scroll={{ x: 'max-content' }} columns={[
                { title: '中央对象', render: (_, i) => (i.type === 'user' ? db.users : db.orgs).find(r => r.id === i.objectId)?.name || i.objectId },
                { title: '类型', dataIndex: 'type', render: v => ({ user: '人员', org: '机构', role: '角色', resource: '资源' } as Record<string, string>)[v] || v }, { title: '版本', dataIndex: 'version' }, { title: '尝试次数', dataIndex: 'attempt' },
                { title: '状态', dataIndex: 'status', render: v => <StatusTag value={v}/> },
                { title: '回执', render: (_, i) => i.result?.message || ({ APPLIED: '已生效', DUPLICATE: '已接收，重复请求已忽略', STALE: '已有更新版本，本次已忽略' } as Record<string, string>)[i.result?.result || ''] || '待处理' },
            ]}/>
        </>}</Drawer>
    </>;
}
