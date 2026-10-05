import { useEffect, useState } from 'react';
import { App, Alert, Button, Card, Checkbox, Col, DatePicker, Descriptions, Empty, Form, Input, Drawer, Modal, Radio, Row, Select, Space, Steps, Table, Tabs, Tag, theme } from 'antd';
import { ApartmentOutlined, AppstoreOutlined, ArrowRightOutlined, KeyOutlined, PlusOutlined, SafetyCertificateOutlined, TeamOutlined, UserOutlined, ClockCircleOutlined, CheckCircleOutlined, CheckCircleFilled, StopOutlined } from '@ant-design/icons';
import { useSearchParams, useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { api, useDatabase, useSession } from '../mock/store';
import { assertGrant, belongsToOrg, effectiveAccess, grantIsCurrent, makeBase } from '../domain/engine';
import type { Domain, Grant, PermissionGroup } from '../domain/types';
import { CardMetric, ConfirmDelete, DataTable, dateText, exportCsv, options, PageTitle, StatusTag, Text, Title, useAction, useEditable } from '../components/common';
import { AppIcon, UserAvatar, ModuleSummary } from '../components/visuals';
function GrantDialog({ open, domain, mode, selected, onClose }: {
    open: boolean;
    domain: Domain;
    mode: 'apps' | 'users' | 'orgs';
    selected: string;
    onClose: () => void;
}) {
    const db = useDatabase();
    const [form] = Form.useForm();
    const [step, setStep] = useState(0);
    const [preview, setPreview] = useState<Grant[]>([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const { modal, message } = App.useApp();
    const source = Form.useWatch('source', form) || 'user';
    const appId = Form.useWatch('appId', form);
    useEffect(() => {
        if (open) {
            setStep(0);
            setPreview([]);
            setError('');
            form.resetFields();
            form.setFieldsValue({ source: mode === 'orgs' ? 'org' : 'user', subjectIds: mode === 'apps' ? [] : [selected], appId: mode === 'apps' ? selected : undefined, roleIds: [], includeChildren: true, startsAt: dayjs(), reason: '' });
        }
    }, [open]);
    const build = async () => {
        try {
            const v = await form.validateFields();
            const items = (v.subjectIds as string[]).map(id => ({ ...makeBase('应用访问授权', domain, 'grant'), source: v.source, subjectId: id, appId: v.appId, roleIds: v.roleIds || [], includeChildren: v.source === 'org' && v.includeChildren, startsAt: v.startsAt.toISOString(), expiresAt: v.expiresAt ? v.expiresAt.toISOString() : null, reason: v.reason } as Grant));
            items.forEach(g => assertGrant(db, g));
            setPreview(items);
            setStep(1);
            setError('');
        }
        catch (e) {
            if (e instanceof Error)
                setError(e.message);
        }
    };
    const subjects = source === 'org' ? db.orgs.filter(o => o.domain === domain && o.status === 'enabled') : db.users.filter(u => u.domain === domain && u.kind !== 'admin' && u.status === 'enabled');
    const affected = db.users.filter(u => u.domain === domain && u.kind !== 'admin' && preview.some(g => g.source === 'user' ? g.subjectId === u.id : belongsToOrg(db, u, g.subjectId, g.includeChildren)));
    const projected = { ...db, authoritativeAccess: undefined, grants: [...db.grants, ...preview] };
    const beforeResources = new Set(affected.flatMap(u => effectiveAccess(db, u.id).flatMap(a => a.resourceIds.map(r => `${u.id}:${r}`))));
    const afterResources = new Set(affected.flatMap(u => effectiveAccess(projected, u.id).flatMap(a => a.resourceIds.map(r => `${u.id}:${r}`))));
    const newResources = [...afterResources].filter(k => !beforeResources.has(k)).length;
    const newAppAccess = affected.reduce((n, u) => n + effectiveAccess(projected, u.id).filter(a => !effectiveAccess(db, u.id).some(b => a.appId === b.appId)).length, 0);
    const grantNames = (g: Grant) => g.source === 'user' ? db.users.find(u => u.id === g.subjectId)?.name : db.orgs.find(o => o.id === g.subjectId)?.name;
    const commit = async () => {
        setBusy(true);
        setError('');
        let count = 0;
        let skipped = 0;
        const errors: string[] = [];
        for (const grant of preview) {
            if (db.grants.some(g => g.status === 'enabled' && g.source === grant.source && g.subjectId === grant.subjectId && g.appId === grant.appId && g.roleIds.slice().sort().join(',') === grant.roleIds.slice().sort().join(',') && g.expiresAt === grant.expiresAt && grantIsCurrent(g))) {
                skipped++;
                continue;
            }
            try {
                await api.save('grants', grant, '授予应用访问权限');
                count++;
            }
            catch (e) {
                errors.push(`${grantNames(grant)}：${e instanceof Error ? e.message : '失败'}`);
            }
        }
        setBusy(false);
        if (errors.length) {
            setError(`已保存${count}项，跳过已有${skipped}项，失败：${errors.join('；')}`);
        }
        else {
            message.success(`已新增${count}项授权${skipped ? `，跳过已有${skipped}项` : ''}`);
            onClose();
        }
    };
    const close = () => form.isFieldsTouched() ? modal.confirm({ title: '放弃未提交的授权？', content: '本次预览不会产生实际授权关系。', okText: '放弃', cancelText: '继续编辑', onOk: onClose }) : onClose();
    return <Drawer title="配置应用授权" open={open} size={760} onClose={busy ? undefined : close} destroyOnHidden footer={<Space><Button onClick={close} disabled={busy}>取消</Button>{step === 1 && <Button onClick={() => setStep(0)} disabled={busy}>返回修改</Button>}<Button type="primary" loading={busy} onClick={step === 0 ? build : commit}>{step === 0 ? '预览授权影响' : '确认授权'}</Button></Space>}><Steps current={step} size="small" items={[{ title: '选择对象与权限' }, { title: '预览并确认' }]} style={{ margin: '24px 0' }}/>{error && <Alert type="error" showIcon title={error} style={{ marginBottom: 16 }}/>}<div hidden={step !== 0}><Form size="small" layout="vertical" form={form} preserve><Row gutter={12}><Col xs={24} sm={12}><Form.Item name="source" label="授权对象类型"><Radio.Group disabled={mode !== 'apps'} options={[{ value: 'user', label: '用户' }, { value: 'org', label: '机构' }]} onChange={() => form.setFieldValue('subjectIds', [])}/></Form.Item><Form.Item label={source === 'org' ? '授权机构' : '授权用户'} name="subjectIds" rules={[{ required: true, message: '请选择至少一个授权对象' }]} extra={source === 'user' ? '平台管理账号不参与业务应用授权。' : '机构授权随任职与机构范围变化计算。'}><Select mode="multiple" showSearch optionFilterProp="label" options={options(subjects)} placeholder="选择授权对象"/></Form.Item>{source === 'org' && <Form.Item name="includeChildren" valuePropName="checked"><Checkbox>包含下级机构用户</Checkbox></Form.Item>}<Form.Item label="目标应用" name="appId" rules={[{ required: true, message: '请选择目标应用' }]}><Select disabled={mode === 'apps'} showSearch optionFilterProp="label" options={options(db.apps.filter(a => a.domain === domain && a.status === 'enabled'))} placeholder="选择应用" onChange={() => form.setFieldValue('roleIds', [])}/></Form.Item><Form.Item label="应用角色" name="roleIds" extra="不选择角色时，仅授予应用访问资格，不包含业务操作权限。"><Select mode="multiple" showSearch optionFilterProp="label" disabled={!appId} options={options(db.roles.filter(r => r.appId === appId && r.status === 'enabled'))}/></Form.Item></Col><Col xs={24} sm={12}><Form.Item label="生效时间" name="startsAt" rules={[{ required: true }]}><DatePicker showTime style={{ width: '100%' }}/></Form.Item><Form.Item label="到期时间" name="expiresAt" extra="留空表示长期有效；到期后自动从有效权限中排除。"><DatePicker showTime style={{ width: '100%' }}/></Form.Item><Form.Item label="授权原因" name="reason" rules={[{ required: true, message: '请说明授权原因' }, { max: 200 }]}><Input.TextArea rows={4} showCount maxLength={200} placeholder="说明业务需要与授权范围"/></Form.Item><Alert title="授权操作可追溯" description="本次变更会记录对象、应用、角色、期限与原因。" type="info" showIcon/></Col></Row></Form></div><div hidden={step !== 1}><div className="impact-preview"><div><span>新增应用访问</span><strong>{newAppAccess}<small> 项</small></strong></div><div><span>新增用户-资源权限</span><strong>{newResources}<small> 项</small></strong></div><div><span>回收权限</span><strong>0<small> 项</small></strong></div></div><Alert type="warning" showIcon title={`涉及 ${preview.length} 个授权对象、${affected.length} 位关联用户`} description={`其中 ${affected.filter(u => u.status === 'enabled' && !u.locked).length} 位账户当前可用；停用或锁定账户不会立即获得有效访问。已有其他授权来源不会被覆盖。`} style={{ marginBottom: 12 }}/><Table size="small" rowKey="id" pagination={false} dataSource={preview} columns={[{ title: '授权对象', render: (_, g) => grantNames(g) }, { title: '目标应用', render: (_, g) => db.apps.find(a => a.id === g.appId)?.name }, { title: '角色', render: (_, g) => g.roleIds.map(id => <Tag key={id}>{db.roles.find(r => r.id === id)?.name}</Tag>) }, { title: '到期时间', render: (_, g) => g.expiresAt ? dateText(g.expiresAt) : '长期有效' }]} scroll={{ x: 'max-content' }}/></div></Drawer>;
}
export default function Authorization({ domain, mode }: {
    domain: Domain;
    mode: 'apps' | 'users' | 'orgs';
}) {
    const db = useDatabase();
    const { token } = theme.useToken();
    const session = useSession()!;
    const [params] = useSearchParams();
    const nav = useNavigate();
    const list = mode === 'apps' ? db.apps.filter(a => a.domain === domain) : mode === 'users' ? db.users.filter(u => u.domain === domain && u.kind !== 'admin') : db.orgs.filter(o => o.domain === domain);
    const queryKey = mode === 'apps' ? 'app' : mode === 'users' ? 'user' : 'org';
    const [selected, setSelected] = useState(params.get(queryKey) || list[0]?.id || '');
    const [filter, setFilter] = useState('');
    const [open, setOpen] = useState(false);
    const [tab, setTab] = useState(mode === 'users' ? 'effective' : 'ledger');
    const editable = useEditable('grants');
    const { modal } = App.useApp();
    const { run } = useAction();
    useEffect(() => { setSelected(params.get(queryKey) || list[0]?.id || ''); setFilter(''); setTab(mode === 'users' ? 'effective' : 'ledger'); }, [domain, mode, params.toString()]);
    const subject = list.find(x => x.id === selected);
    const grants = db.grants.filter(g => g.domain === domain && (mode === 'apps' ? g.appId === selected : mode === 'users' ? g.source === 'user' && g.subjectId === selected : g.source === 'org' && g.subjectId === selected));
    const viewData = grants.map(g => ({ ...g, status: g.status === 'revoked' ? 'revoked' : g.expiresAt && new Date(g.expiresAt) <= new Date() ? 'expired' : g.status }));
    const effective = mode === 'users' ? effectiveAccess(db, selected) : db.users.filter(u => u.domain === domain && (mode !== 'orgs' || belongsToOrg(db, u, selected, true))).flatMap(u => effectiveAccess(db, u.id).filter(v => mode !== 'apps' || v.appId === selected).map(v => ({ ...v, userId: u.id, userName: u.name, account: u.account })));
    const allGrants = db.grants.filter(g => g.domain === domain);
    const title = mode === 'apps' ? '应用授权' : mode === 'users' ? '用户授权' : '机构授权';
    const selectedUser = mode === 'users' ? db.users.find(u => u.id === selected) : undefined;
    const revoke = (g: Grant) => modal.confirm({ title: '撤销这一来源的应用授权？', content: '只撤销当前授权记录；其他直接、机构或权限组来源不会被移除。权限组来源请到权限组中维护。', okText: '撤销授权', cancelText: '取消', okButtonProps: { danger: true }, onOk: () => run(() => api.save('grants', { ...db.grants.find(x => x.id === g.id)!, status: 'revoked' }, '撤销应用访问权限'), '已撤销当前授权来源') });
    const nameOf = (g: Grant) => g.source === 'user' ? db.users.find(u => u.id === g.subjectId)?.name : g.source === 'org' ? db.orgs.find(o => o.id === g.subjectId)?.name : db.groups.find(p => p.id === g.subjectId)?.name;
    return <><PageTitle title={title} description="统一管理应用访问资格与角色权限，让每一次授权有依据、可追溯。" extra={<Space><Button type={mode === 'users' ? 'primary' : 'default'} onClick={() => nav(`/console/${domain}/grants/users`)}>按用户</Button><Button type={mode === 'orgs' ? 'primary' : 'default'} onClick={() => nav(`/console/${domain}/grants/orgs`)}>按机构</Button><Button type={mode === 'apps' ? 'primary' : 'default'} onClick={() => nav(`/console/${domain}/grants/apps`)}>按应用</Button></Space>}/><div className="metric-grid four"><CardMetric label="生效授权来源" value={allGrants.filter(g => grantIsCurrent(g)).length} icon={<KeyOutlined />} description="授权时间窗内的来源记录"/><CardMetric label="机构继承来源" value={allGrants.filter(g => g.source === 'org' && grantIsCurrent(g)).length} tone="purple" icon={<ApartmentOutlined />} description="随组织任职关系动态生效"/><CardMetric label="即将到期" value={allGrants.filter(g => grantIsCurrent(g) && g.expiresAt && dayjs(g.expiresAt).diff(dayjs(), 'day') <= 30).length} tone="orange" icon={<ClockCircleOutlined />} description="未来 30 天内到期"/><CardMetric label="已撤销授权" value={allGrants.filter(g => g.status === 'revoked').length} tone="red" icon={<StopOutlined />} description="保留历史，便于审计追溯"/></div><div className="grant-layout"><Card size="small" title={mode === 'apps' ? '选择应用' : mode === 'users' ? '选择用户' : '选择机构'} styles={{ body: { padding: 12 } }}><Input.Search placeholder={`搜索${mode === 'apps' ? '应用' : mode === 'users' ? '用户' : '机构'}`} value={filter} onChange={e => setFilter(e.target.value)} allowClear style={{ marginBottom: 12 }}/><div className="object-picker">{list.filter(v => v.name.includes(filter)).map(v => <button key={v.id} type="button" className={`object-choice ${v.id === selected ? 'selected' : ''}`} onClick={() => setSelected(v.id)}>{mode === 'apps' ? <AppIcon app={db.apps.find(a => a.id === v.id)!} size={36}/> : mode === 'users' ? <UserAvatar user={db.users.find(u => u.id === v.id)!}/> : <span className="org-choice-icon"><ApartmentOutlined /></span>}<span><strong>{v.name}</strong><small>{mode === 'apps' ? db.apps.find(a => a.id === v.id)?.group : mode === 'users' ? db.orgs.find(o => o.id === db.users.find(u => u.id === v.id)?.orgId)?.name : db.orgs.find(o => o.id === v.id)?.code}</small></span>{v.id === selected && <CheckCircleFilled className="choice-check"/>}</button>)}</div></Card><div style={{ minWidth: 0 }}>{subject ? <><Card size="small" className="section-gap grant-subject-card"><div className="inline-toolbar"><div><Space size={12}>{mode === 'apps' ? <AppIcon app={db.apps.find(a => a.id === selected)!} size={44}/> : selectedUser ? <UserAvatar user={selectedUser} size={44}/> : <ApartmentOutlined style={{ fontSize: 28, color: '#5267F5' }}/>}<Title level={4} style={{ margin: 0 }}>{subject.name}</Title><StatusTag value={subject.status}/></Space><div style={{ marginTop: 8 }}><Text type="secondary">{mode === 'users' ? '直接、机构和权限组来源叠加计算；不会覆盖其他来源。' : mode === 'orgs' ? '机构授权随任职关系动态计算；平台管理账号被排除。' : '授权记录保留历史状态；当前是否可访问以有效权限为准。'}</Text></div></div><Button type="primary" icon={<PlusOutlined />} disabled={!editable || selectedUser?.kind === 'admin' || subject.status === 'disabled'} onClick={() => setOpen(true)}>配置授权</Button></div></Card>{selectedUser?.kind === 'admin' && <Alert style={{ marginBottom: 16 }} type="info" showIcon title="平台管理账号不具有业务应用访问权限，也不可通过机构或权限组继承获得。"/>}<Tabs size="small" activeKey={tab} onChange={setTab} items={[{ key: 'ledger', label: `${mode === 'users' ? '直接授权' : '授权台账'} (${grants.length})`, children: <DataTable data={viewData} title="授权记录" hideStatus={false} searchFields={['name', 'reason']} exporter={rows => exportCsv('授权台账', ['对象', '应用', '来源', '状态', '原因'], rows.map(g => [nameOf(g), db.apps.find(a => a.id === g.appId)?.name, g.source, g.status, g.reason]))} columns={[{ title: '授权对象', width: 150, render: (_, g) => <div><Text>{nameOf(g) || '对象已移除'}</Text><div><Text type="secondary" style={{ fontSize: 'var(--iam-font-secondary)' }}>{({ user: '用户', org: '机构', group: '权限组' } as Record<string, string>)[g.source]}</Text></div></div> }, { title: '应用', width: 160, render: (_, g) => { const a = db.apps.find(a => a.id === g.appId); return a ? <Space><AppIcon app={a} size={30}/>{a.name}</Space> : '—'; } }, { title: '应用角色', width: 180, render: (_, g) => g.roleIds.length ? g.roleIds.map(id => <Tag key={id}>{db.roles.find(r => r.id === id)?.name || '失效角色'}</Tag>) : <Text type="secondary">仅访问资格</Text> }, { title: '有效期限', width: 170, render: (_, g) => g.expiresAt ? dateText(g.expiresAt) : '长期有效' }, { title: '状态', dataIndex: 'status', render: v => <StatusTag value={v}/>, width: 100 }, { title: '操作', fixed: 'right', width: 120, render: (_, g) => <Button type="link" danger disabled={!editable || g.status === 'revoked' || g.source === 'group'} onClick={() => revoke(g)}>撤销授权</Button> }]}/> }, { key: 'effective', label: `有效访问 (${effective.length})`, children: <Card size="small"><Alert title="有效结果综合考虑身份域、启停状态、锁定、授权期限与全部授权来源。" type="info" showIcon style={{ marginBottom: 16 }}/><Table size="small" rowKey={v => 'userId' in v ? `${v.userId}-${v.appId}` : v.appId} dataSource={effective} pagination={{ pageSize: 10 }} scroll={{ x: 600 }} columns={[...(mode === 'users' ? [] : [{ title: '用户', key: 'user', render: (_: unknown, v: unknown) => (v as {
                                        userName: string;
                                    }).userName }]), { title: '应用', dataIndex: 'appId', render: id => { const a = db.apps.find(a => a.id === id); return a ? <Space><AppIcon app={a} size={30}/>{a.name}</Space> : '—'; } }, { title: '角色', dataIndex: 'roleIds', render: (ids: string[]) => ids.map(id => <Tag key={id}>{db.roles.find(r => r.id === id)?.name}</Tag>) }, { title: '全部有效来源', render: (_, v) => v.sources.map(s => <div key={s.grantId}><Tag color={s.source === 'user' ? 'blue' : s.source === 'org' ? 'cyan' : 'purple'}>{s.label}</Tag></div>) }]}/></Card> }]}/></> : <Card size="small"><Empty description="请先选择一个对象"/></Card>}</div></div><GrantDialog open={open} domain={domain} mode={mode} selected={selected} onClose={() => setOpen(false)}/></>;
}
export function PermissionGroups({ domain }: {
    domain: Domain;
}) {
    const db = useDatabase();
    const editable = useEditable('groups');
    const [editing, setEditing] = useState<PermissionGroup | null>(null);
    const [open, setOpen] = useState(false);
    const [form] = Form.useForm();
    const [items, setItems] = useState<PermissionGroup['items']>([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const { message, modal } = App.useApp();
    const start = (g: PermissionGroup | null) => { setEditing(g); setItems(g?.items.map(i => ({ ...i, roleIds: [...i.roleIds] })) || []); setOpen(true); setError(''); form.resetFields(); form.setFieldsValue(g || { name: '', description: '', userIds: [], status: 'enabled' }); };
    const save = async () => {
        try {
            const v = await form.validateFields();
            if (!items.length || items.some(i => !i.appId))
                throw new Error('请至少配置一个有效应用');
            if (new Set(items.map(i => i.appId)).size !== items.length)
                throw new Error('同一应用只能配置一次');
            setBusy(true);
            await api.save('groups', { ...(editing || makeBase(v.name, domain, 'group')), ...v, items } as PermissionGroup, '维护权限组');
            message.success('权限组及关联授权已更新');
            setOpen(false);
        }
        catch (e) {
            if (e instanceof Error)
                setError(e.message);
        }
        finally {
            setBusy(false);
        }
    };
    const close = () => modal.confirm({ title: '放弃本次修改？', content: '尚未保存的应用组合和成员选择将被丢弃。', okText: '放弃修改', cancelText: '继续编辑', onOk: () => setOpen(false) });
    return <><PageTitle title="权限组" description="将跨应用的角色组合为权限组，统一授予业务人员。" extra={<Button type="primary" icon={<PlusOutlined />} disabled={!editable} onClick={() => start(null)}>新建权限组</Button>}/><ModuleSummary domain={domain} category="groups"/><DataTable data={db.groups.filter(g => g.domain === domain)} title="权限组列表" columns={[{ title: '权限组', dataIndex: 'name', render: (v, r) => <div><Text strong>{v}</Text><div><Text type="secondary">{r.description}</Text></div></div> }, { title: '应用组合', render: (_, g) => <Space wrap>{g.items.map(i => <Tag key={i.appId}>{db.apps.find(a => a.id === i.appId)?.name}</Tag>)}</Space> }, { title: '成员数', align: 'right', render: (_, g) => g.userIds.length }, { title: '状态', dataIndex: 'status', render: v => <StatusTag value={v}/> }, { title: '操作', render: (_, g) => <Space><Button type="link" disabled={!editable} onClick={() => start(g)}>配置</Button><ConfirmDelete target={g.name} disabled={!editable} onConfirm={() => api.remove('groups', g.id)}/></Space> }]}/><Modal title={editing ? '配置权限组' : '新建权限组'} open={open} width={900} onCancel={close} destroyOnHidden onOk={save} confirmLoading={busy} okText="保存权限组" cancelText="取消">{error && <Alert title={error} type="error" showIcon style={{ marginBottom: 16 }}/>}<Form size="small" form={form} layout="vertical"><Row gutter={12}><Col xs={24} sm={12}><Form.Item label="权限组名称" name="name" rules={[{ required: true }]}><Input /></Form.Item></Col><Col xs={24} sm={12}><Form.Item label="状态" name="status"><Select options={[{ value: 'enabled', label: '启用' }, { value: 'disabled', label: '停用' }]}/></Form.Item></Col></Row><Form.Item label="说明" name="description"><Input.TextArea rows={2}/></Form.Item><Form.Item label="组成员" name="userIds" extra="只授予业务用户，不包含平台管理账号。"><Select mode="multiple" showSearch optionFilterProp="label" options={options(db.users.filter(u => u.domain === domain && u.kind !== 'admin'))}/></Form.Item></Form><Text strong className="detail-section-title">应用与角色组合</Text><div style={{ margin: '12px 0' }}>{items.map((item, index) => <Row gutter={12} key={index} style={{ marginBottom: 12 }}><Col xs={24} sm={9}><Select aria-label={`组合${index + 1}应用`} style={{ width: '100%' }} value={item.appId || undefined} placeholder="选择应用" options={options(db.apps.filter(a => a.domain === domain && a.status === 'enabled'))} onChange={id => setItems(items.map((v, i) => i === index ? { appId: id, roleIds: [] } : v))}/></Col><Col xs={24} sm={12}><Select aria-label={`组合${index + 1}角色`} mode="multiple" style={{ width: '100%' }} value={item.roleIds} placeholder="选择该应用角色" options={options(db.roles.filter(r => r.appId === item.appId && r.status === 'enabled'))} onChange={ids => setItems(items.map((v, i) => i === index ? { ...v, roleIds: ids } : v))}/></Col><Col xs={24} sm={3}><Button danger onClick={() => setItems(items.filter((_, i) => i !== index))}>移除</Button></Col></Row>)}</div><Button type="dashed" block icon={<PlusOutlined />} onClick={() => setItems([...items, { appId: '', roleIds: [] }])}>添加应用组合</Button><Alert style={{ marginTop: 20 }} type="info" showIcon title="保存后重新生成此权限组的授权来源，不影响用户的直接或机构授权。"/></Modal></>;
}
