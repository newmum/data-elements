import { useCallback, useEffect, useRef, useState } from 'react';
import type { Key } from 'react';
import { App, Alert, Avatar, Button, Card, Checkbox, Descriptions, Drawer, Dropdown, Empty, Input, Segmented, Space, Switch, Table, Tabs, Tag, Timeline, Tree, theme } from 'antd';
import { ApartmentOutlined, DownOutlined, EditOutlined, EllipsisOutlined, PlusOutlined, UploadOutlined, UserOutlined, TeamOutlined, SolutionOutlined, UserDeleteOutlined, SafetyCertificateOutlined, LaptopOutlined, MailOutlined, PhoneOutlined, CheckCircleFilled, ArrowRightOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api, subscribeUserOperations, useDatabase, useSession, type UserOperation } from '../mock/store';
import { allowedUser, belongsToOrg, descendants, effectiveAccess, makeBase } from '../domain/engine';
import type { Domain, Org, User } from '../domain/types';
import { CardMetric, ConfirmDelete, DataTable, dateText, exportCsv, options, PageTitle, RecordEditor, StatusTag, Text, useAction, useEditable, type Field, type FormValues } from '../components/common';
import { maskEmail, maskPhone } from '../domain/csv';
import ImportUsers from '../components/ImportUsers';
import { extensionFields, extensionInitial, extensionValues } from '../components/directory-fields';
import { AppIcon, UserAvatar, SectionIntro } from '../components/visuals';
export interface OrgTreeNode {
    key: string;
    title: string;
    value: string;
    children: OrgTreeNode[];
}
export function organizationTree(orgs: Org[], parentId: string | null = null): OrgTreeNode[] { return orgs.filter(o => o.parentId === parentId).map(o => ({ key: o.id, title: o.name, value: o.id, children: organizationTree(orgs, o.id) })); }
function UserOperationRecovery() {
    const db = useDatabase();
    const [operations, setOperations] = useState<UserOperation[]>([]);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [retrying, setRetrying] = useState('');
    const mounted = useRef(false);
    const { message } = App.useApp();
    const load = useCallback(async () => {
        if (!mounted.current) return;
        setLoading(true);
        try {
            const rows = await api.userOperations();
            if (mounted.current) { setOperations(rows); setError(''); }
        } catch (failure) {
            if (mounted.current) setError(failure instanceof Error ? failure.message : '待恢复的人员操作暂时无法读取');
        } finally { if (mounted.current) setLoading(false); }
    }, []);
    useEffect(() => {
        mounted.current = true;
        void load();
        const unsubscribe = subscribeUserOperations(() => { void load(); });
        return () => { mounted.current = false; unsubscribe(); };
    }, [load]);
    const retry = async (operation: UserOperation) => {
        setRetrying(operation.requestId); setError('');
        try { await api.retryUserOperation(operation.requestId); message.success('人员操作已恢复'); }
        catch (failure) { if (mounted.current) setError(failure instanceof Error ? failure.message : '人员操作尚未完成，请稍后重试'); }
        finally { if (mounted.current) setRetrying(''); }
    };
    if (!operations.length && !error) return null;
    return <Card size="small" title={operations.length ? `待恢复的人员操作（${operations.length}）` : '人员操作恢复'} style={{ marginBottom: 16 }} extra={<Button loading={loading} disabled={Boolean(retrying)} onClick={() => { void load(); }}>刷新</Button>}>
        {error && <Alert type="warning" showIcon title={error} description="当前人员列表仍可查看。" style={{ marginBottom: operations.length ? 12 : 0 }}/>}
        {operations.length > 0 && <><Text type="secondary">以下操作尚未完成，可继续处理，无需重新填写开户资料。</Text><Table size="small" rowKey="requestId" dataSource={operations} pagination={operations.length > 5 ? { pageSize: 5, size: 'small' } : false} style={{ marginTop: 12 }} scroll={{ x: 'max-content' }} columns={[
            { title: '人员', dataIndex: 'userId', render: (id, operation) => <div>{db.users.find(user => user.id === id)?.name || '待处理账号'}<div><Text type="secondary" title={operation.requestId}>编号 {operation.requestId.slice(-12)}</Text></div></div> },
            { title: '操作', dataIndex: 'operationType', render: value => /remove|delete/i.test(String(value)) ? '移出租户' : '开通身份' },
            { title: '提交时间', dataIndex: 'createdTime', render: value => dateText(value) },
            { title: '状态', render: () => <Tag color="warning">待恢复</Tag> },
            { title: '操作', key: 'actions', render: (_, operation) => <Button type="link" loading={retrying === operation.requestId} disabled={Boolean(retrying) && retrying !== operation.requestId} onClick={() => { void retry(operation); }}>继续处理</Button> },
        ]}/></>}
    </Card>;
}
export function UserDetail({ user, onClose, onEdit }: {
    user: User | null;
    onClose: () => void;
    onEdit?: (user: User) => void;
}) {
    const db = useDatabase();
    const grantsAvailable = useSession()?.capabilities?.includes('grants') === true;
    if (!user)
        return null;
    const current = db.users.find(u => u.id === user.id) || user;
    const access = grantsAvailable ? effectiveAccess(db, current.id) : [];
    const org = db.orgs.find(o => o.id === current.orgId);
    const loginLogs = db.logs.filter(l => (l.actor === current.account || l.actor === current.id) && l.domain === current.domain && l.type === 'login').slice(0, 5);
    const activeRoles = [...new Set(access.flatMap(a => a.roleIds))].map(id => db.roles.find(r => r.id === id)).filter(Boolean);
    const typeName = current.kind === 'admin' ? '平台管理员' : current.kind === 'citizen' ? '自然人' : current.employmentType === 'partner' ? '外部合作伙伴' : current.employmentType === 'temporary' ? '临时账号' : '内部员工';
    const profile = <><div className="detail-section"><h4>基本信息</h4><Descriptions column={{ xs: 1, sm: 2 }} layout="vertical" size="small" items={[
            { key: 'account', label: '用户名', children: current.account }, { key: 'employeeId', label: '工号 / 身份编号', children: current.employeeId || current.id },
            { key: 'org', label: '所属组织', children: org?.name || '—' }, { key: 'post', label: '任职岗位', children: current.post || '—' },
            { key: 'email', label: '邮箱', children: maskEmail(current.email) }, { key: 'phone', label: '手机', children: maskPhone(current.phone) },
            { key: 'created', label: '创建时间', children: dateText(current.createdAt) }, { key: 'verified', label: '实名状态', children: <Tag color={current.verified ? 'success' : 'default'}>{current.verified ? '已核验' : '未核验'}</Tag> }
        ]}/></div><div className="detail-section"><h4>角色权限 <span className="count-pill">{grantsAvailable ? activeRoles.length : '—'}</span></h4><Space wrap size={[6, 8]}>{activeRoles.length ? activeRoles.slice(0, 8).map(r => <Tag key={r!.id} color="blue" icon={<SafetyCertificateOutlined />}>{r!.name}</Tag>) : <Text type="secondary">{grantsAvailable ? '暂无有效业务角色' : '应用授权服务尚未开通'}</Text>}</Space></div>
    <div className="detail-section"><h4>已授权应用 <span className="count-pill">{grantsAvailable ? access.length : '—'}</span></h4><div className="user-app-chips">{access.length ? access.slice(0, 6).map(a => { const app = db.apps.find(v => v.id === a.appId)!; return <span key={a.appId}><AppIcon app={app} size={26}/>{app.name}</span>; }) : <Text type="secondary">{grantsAvailable ? '暂无有效应用授权' : '应用授权服务尚未开通'}</Text>}</div></div>
    <div className="detail-section"><h4>最近登录设备</h4>{loginLogs.length ? loginLogs.slice(0, 3).map(log => <div className="device-row" key={log.id}><LaptopOutlined /><div><strong>{log.device || '未记录设备'}</strong><small>{log.ip} · {dateText(log.createdAt)}</small></div><StatusTag value={log.result}/></div>) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无登录记录"/>}</div>
    <div className="account-health"><SafetyCertificateOutlined /><div><strong>{current.locked ? '当前租户身份已锁定，请核实登录行为' : current.status === 'disabled' ? '当前租户身份已停用' : '当前租户身份处于可用状态'}</strong><p>二次认证服务尚未开通。</p></div></div></>;
    return <Drawer title="身份档案" open onClose={onClose} size={560} destroyOnHidden extra={onEdit && <Button icon={<EditOutlined />} onClick={() => { onClose(); onEdit(current); }}>编辑资料</Button>}><div className="user-profile-hero"><UserAvatar user={current} size={68}/><div><h2>{current.name} <StatusTag value={current.status}/></h2><p>{current.post || typeName}{org ? ` · ${org.name}` : ''}</p><Space size={10}><Tag color="blue">{typeName}</Tag>{current.locked && <StatusTag value="locked"/>}</Space></div></div><Tabs size="small" items={[
            { key: 'profile', label: '基本信息', children: profile },
            { key: 'access', label: grantsAvailable ? `应用授权 (${access.length})` : '应用授权', children: grantsAvailable ? <>{current.kind === 'admin' && <Alert style={{ marginBottom: 16 }} type="info" showIcon title="管理权限不等于业务使用权限。平台管理账号没有业务应用访问资格。"/>}<Table rowKey="appId" size="small" pagination={false} dataSource={access} columns={[{ title: '应用', dataIndex: 'appId', render: id => { const a = db.apps.find(v => v.id === id); return a ? <Space><AppIcon app={a} size={30}/>{a.name}</Space> : '—'; } }, { title: '角色 / 授权来源', render: (_, a) => <><div>{a.roleIds.map(id => <Tag color="blue" key={id}>{db.roles.find(r => r.id === id)?.name}</Tag>)}</div><small className="muted">{a.sources.map(s => s.label).join('、')}</small></> }]} scroll={{ x: 'max-content' }}/></> : <Alert type="info" showIcon title="应用授权服务尚未开通"/> },
            { key: 'appointments', label: '任职信息', children: <Table size="small" rowKey="orgId" pagination={false} dataSource={current.appointments} columns={[{ title: '机构', dataIndex: 'orgId', render: id => db.orgs.find(o => o.id === id)?.name || '—' }, { title: '岗位', dataIndex: 'post' }, { title: '任职类型', dataIndex: 'primary', render: v => <Tag color={v ? 'blue' : 'default'}>{v ? '主职' : '兼任'}</Tag> }]} scroll={{ x: 'max-content' }}/> },
            { key: 'history', label: '变更履历', children: <div className="detail-section"><Timeline items={current.history.map((h, i) => ({ key: i, title: dateText(h.time), content: h.text }))}/></div> }
        ]}/></Drawer>;
}
export default function Organization({ domain, persons = false, lockedOnly = false }: {
    domain: Domain;
    persons?: boolean;
    lockedOnly?: boolean;
}) {
    const db = useDatabase();
    const session = useSession()!;
    const grantsAvailable = session.capabilities?.includes('grants') === true;
    const { token } = theme.useToken();
    const nav = useNavigate();
    const [params, setParams] = useSearchParams();
    const [orgId, setOrgId] = useState('all');
    const [children, setChildren] = useState(true);
    const [treeQuery, setTreeQuery] = useState('');
    const [userEdit, setUserEdit] = useState<User | null>(null);
    const [userOpen, setUserOpen] = useState(false);
    const [linkExisting, setLinkExisting] = useState(false);
    const [orgEdit, setOrgEdit] = useState<Org | null>(null);
    const [orgOpen, setOrgOpen] = useState(false);
    const [detail, setDetail] = useState<User | null>(null);
    const [selected, setSelected] = useState<Key[]>([]);
    const [importOpen, setImportOpen] = useState(false);
    const { run, busy } = useAction();
    const { modal, message } = App.useApp();
    useEffect(() => { const id = params.get('user'); if (id) {
        const u = db.users.find(u => u.id === id && allowedUser(db, session, u));
        if (u)
            setDetail(u);
    } }, [params.get('user')]);
    const editable = useEditable('users');
    const canOrg = useEditable('orgs');
    useEffect(() => { setSelected([]); }, [orgId, children]);
    const orgs = db.orgs.filter(o => o.domain === domain && (session.scopeMode === 'tenant' || (session.orgIds || []).some(id => descendants(db, id).has(o.id))));
    const data = db.users.filter(u => allowedUser(db, session, u) && (!lockedOnly || u.locked) && (orgId === 'all' || belongsToOrg(db, u, orgId, children)));
    const businessUsers = data.filter(u => u.kind !== 'admin');
    const userDefault: User = userEdit || { ...makeBase('', domain, 'user'), account: '', email: '', phone: '', orgId: persons ? '' : orgId === 'all' ? (orgs.find(o => o.status === 'enabled')?.id || '') : orgId, post: '', employmentType: 'employee', mfaEnabled: false, kind: persons ? 'citizen' : 'person', locked: false, appointments: [], verified: false, history: [] };
    const orgDefault: Org = orgEdit || { ...makeBase('', domain, 'org'), code: '', parentId: orgId === 'all' ? null : orgId, leader: '', line: '' };
    const fields: Field[] = [{ name: 'name', label: '姓名', required: !linkExisting, max: 50, span: 12, disabled: linkExisting, help: '中央人员档案不授予平台登录资格。' }, { name: 'account', label: session.realm === 'platform' ? '拟用租户账号' : '账号', required: true, max: userEdit || linkExisting ? 50 : 32, span: 12, disabled: !!userEdit, pattern: !userEdit && !linkExisting ? /^[A-Za-z][A-Za-z0-9_.-]{2,31}$/ : undefined, patternMessage: '3–32位，以字母开头' }, ...(session.realm !== 'platform' && !userEdit && !linkExisting ? [{ name: 'initialPassword', label: '初始密码', type: 'password' as const, required: true, pattern: /^.{8,128}$/, patternMessage: '初始密码为8–128个字符', max: 128, help: '为新账号设置独立的初始密码。' }] : []), { name: 'email', label: '邮箱', span: 12 }, { name: 'phone', label: '手机号', span: 12 }, ...(!persons ? [{ name: 'orgId', label: '主职机构', type: 'select' as const, required: true, options: options(orgs.filter(o => o.status === 'enabled')), span: 12 as const }, { name: 'post', label: '岗位', type: 'select' as const, options: db.catalog.filter(c => c.category === 'dictionary' && c.value === 'POST' && c.status === 'enabled').map(c => ({ value: c.code, label: c.name })), span: 12 as const }, { name: 'employmentType', label: '身份类型', type: 'select' as const, span: 12 as const, options: [{ value: 'employee', label: '内部员工' }, { value: 'partner', label: '外部合作伙伴' }, { value: 'temporary', label: '临时账号' }] }, { name: 'additionalOrgIds', label: '兼任机构', type: 'multi' as const, options: options(orgs), help: '主职机构始终保留；兼任不产生新账号。' }] : []), { name: 'status', label: '账户状态', type: 'select', required: true, options: [{ value: 'enabled', label: '已启用' }, { value: 'disabled', label: '已停用' }] }];
    const toggle = (u: User) => modal.confirm({ title: `${u.status === 'enabled' ? '停用' : '启用'}用户“${u.name}”？`, content: u.status === 'enabled' ? '停用后，该账号的有效应用访问将暂停；已有授权来源仍会保留。' : '恢复启用不会解除登录锁定，也不会增加新的权限。', okText: u.status === 'enabled' ? '停用用户' : '启用用户', cancelText: '取消', onOk: () => run(() => api.save('users', { ...u, status: u.status === 'enabled' ? 'disabled' : 'enabled' }, '调整用户状态'), '账户状态已更新') });
    const reset = (u: User) => modal.info({ title: '密码重置申请', content: '请核对账号信息。确认后将记录该账号的密码重置申请。', okText: '提交申请', onOk: () => run(() => api.recordEvent(domain, '申请重置密码', u.name, { userId: u.id }), '已提交申请') });
    const unlock = (u: User) => modal.confirm({ title: `解锁“${u.name}”？`, content: '仅解除账号锁定，不改变启停状态和应用权限。', okText: '解锁账号', cancelText: '取消', onOk: () => run(() => api.save('users', { ...u, locked: false }, '解除账号锁定'), '已解除锁定') });
    const batch = () => modal.confirm({ title: `停用选中的 ${selected.length} 个账号？`, content: '将逐项处理并反馈失败原因。平台内置管理账号不会被停用。', okText: '批量停用', cancelText: '取消', okButtonProps: { danger: true }, onOk: async () => {
            let count = 0;
            const errors: string[] = [];
            for (const id of selected) {
                const u = db.users.find(v => v.id === id);
                if (!u || u.kind === 'admin') {
                    errors.push('内置管理账号不可停用');
                    continue;
                }
                try {
                    await api.save('users', { ...u, status: 'disabled' }, '批量停用用户');
                    count++;
                }
                catch (e) {
                    errors.push(`${u.name}：${e instanceof Error ? e.message : '失败'}`);
                }
            }
            setSelected([]);
            modal.info({ title: `已停用 ${count} 项，失败 ${errors.length} 项`, content: errors.join('；') || '全部处理完成。' });
        } });
    const treeData = organizationTree(orgs.map(org => ({ ...org, parentId: org.parentId && orgs.some(parent => parent.id === org.parentId) ? org.parentId : null })));
    const filterTree = (nodes: typeof treeData): typeof treeData => nodes.map(n => ({ ...n, children: filterTree(n.children) })).filter(n => n.title.includes(treeQuery) || n.children.length > 0);
    const showTree = !persons && !lockedOnly;
    return <><PageTitle title={lockedOnly ? '账号解锁' : persons ? '自然人管理' : '用户管理'} description={lockedOnly ? '解除当前租户身份锁定；密码失败产生的临时登录限制会按认证策略到期。' : persons ? '统一管理自然人账户，分别查看启用与实名核验状态。' : '维护权威身份数据，让组织与人员关系保持一致。'} extra={!lockedOnly && <Button type="primary" icon={<PlusOutlined />} disabled={!editable} onClick={() => { setLinkExisting(false); setUserEdit(null); setUserOpen(true); }}>{persons ? '新建自然人' : '新建用户'}</Button>}/>
 {editable && !persons && !lockedOnly && <UserOperationRecovery/>}
 <div className="metric-grid four"><CardMetric label={persons ? '自然人账户' : lockedOnly ? '锁定账号' : '内部员工'} value={persons || lockedOnly ? businessUsers.length : businessUsers.filter(u => u.employmentType === 'employee').length} icon={<TeamOutlined />} description={persons ? '当前身份域' : '当前机构范围'}/><CardMetric label={persons ? '已完成核验' : '外部合作伙伴'} value={businessUsers.filter(u => persons ? u.verified : u.employmentType === 'partner').length} tone="purple" icon={<SolutionOutlined />} description={persons ? '实名状态' : '协同与外部服务身份'}/><CardMetric label={persons ? '待完成核验' : '临时账号'} value={businessUsers.filter(u => persons ? !u.verified : u.employmentType === 'temporary').length} tone="orange" icon={<UserOutlined />} description={persons ? '待补充身份资料' : '实习、临时项目与协作'}/><CardMetric label="已停用账号" value={businessUsers.filter(u => u.status === 'disabled').length} icon={<UserDeleteOutlined />} tone="red" description="当前租户身份已停用"/></div>
 {lockedOnly && <Alert style={{ marginBottom: 16 }} title="解除身份锁定不会启用停用账号，也不会恢复已撤销的授权；临时登录限制仍按认证策略解除。" type="info" showIcon/>}
 <div className={showTree ? 'organization-layout' : 'single-layout'}>{showTree && <Card size="small" className="organization-tree-card" title={<Space><ApartmentOutlined />组织架构</Space>} extra={<TooltipIcon label="新增机构" disabled={!canOrg} onClick={() => { setOrgEdit(null); setOrgOpen(true); }}/>} styles={{ body: { padding: '16px 12px' } }}><Input.Search placeholder="搜索机构" aria-label="搜索机构" allowClear value={treeQuery} onChange={e => setTreeQuery(e.target.value)} style={{ marginBottom: 16 }}/><Button type={orgId === 'all' ? 'primary' : 'text'} block onClick={() => setOrgId('all')} style={{ marginBottom: 8 }}>全部用户</Button><Tree blockNode defaultExpandAll showLine switcherIcon={<DownOutlined />} treeData={filterTree(treeData)} titleRender={node => <span className="org-tree-label"><span>{node.title}</span><small>{db.users.filter(u => u.kind !== 'admin' && belongsToOrg(db, u, node.key, true)).length}</small></span>} selectedKeys={orgId === 'all' ? [] : [orgId]} onSelect={keys => setOrgId(String(keys[0] || 'all'))}/><div className="tree-bottom"><Checkbox checked={children} onChange={e => setChildren(e.target.checked)}>包含下级机构用户</Checkbox>{orgId !== 'all' && <Space style={{ marginTop: 12 }}><Button size="small" disabled={!canOrg} onClick={() => { setOrgEdit(db.orgs.find(o => o.id === orgId) || null); setOrgOpen(true); }}>编辑机构</Button><ConfirmDelete target={db.orgs.find(o => o.id === orgId)?.name || ''} disabled={!canOrg} onConfirm={async () => { await api.remove('orgs', orgId); setOrgId('all'); }}/></Space>}</div></Card>}
 <div style={{ minWidth: 0 }}><DataTable data={data} searchFields={['name', 'account', 'email']} searchPlaceholder="搜索姓名、账号或邮箱" title={orgId === 'all' ? (persons ? '自然人列表' : lockedOnly ? '锁定账号' : '用户列表') : db.orgs.find(o => o.id === orgId)?.name} selection={selected} onSelection={!persons && !lockedOnly ? setSelected : undefined} loading={busy} exporter={rows => { exportCsv(persons ? '自然人账户' : '用户清单', ['姓名', '账号', '邮箱（脱敏）', '手机号（脱敏）', '机构', '状态'], rows.map(u => [u.name, u.account, maskEmail(u.email), maskPhone(u.phone), db.orgs.find(o => o.id === u.orgId)?.name || '', u.status]));  }} actions={<>{selected.length > 0 && <Button danger disabled={!editable} onClick={batch}>批量停用</Button>}{!persons && !lockedOnly && <Button icon={<UploadOutlined />} disabled title="批量开户服务尚未开通">导入用户</Button>}</>} columns={[{ title: '姓名 / 账号', key: 'name', width: 180, render: (_, u) => <Space><UserAvatar user={u}/><div><Button type="link" style={{ padding: 0, height: 'auto' }} onClick={() => setDetail(u)}>{u.name}</Button><div><Text type="secondary" style={{ fontSize: 'var(--iam-font-secondary)' }}>{u.account}</Text></div></div></Space> }, ...(!persons ? [{ title: '身份类型', key: 'type', width: 120, render: (_: unknown, u: User) => <Tag color={u.kind === 'admin' ? 'geekblue' : u.employmentType === 'partner' ? 'purple' : u.employmentType === 'temporary' ? 'orange' : 'blue'}>{u.kind === 'admin' ? '平台管理账号' : u.employmentType === 'partner' ? '外部合作伙伴' : u.employmentType === 'temporary' ? '临时账号' : '内部员工'}</Tag> }, { title: '所属机构', key: 'org', width: 150, render: (_: unknown, u: User) => db.orgs.find(o => o.id === u.orgId)?.name || '—' }, { title: '岗位', dataIndex: 'post', width: 110 }] : [{ title: '实名状态', key: 'verified', render: (_: unknown, u: User) => <Tag color={u.verified ? 'success' : 'warning'}>{u.verified ? '已核验' : '未核验'}</Tag> }]), { title: '已授权应用', key: 'apps', width: 110, render: (_, u) => <Tag color='blue'>{grantsAvailable ? effectiveAccess(db, u.id).length : '—'}</Tag> }, { title: '联系方式', key: 'contact', width: 190, render: (_, u) => <div><div>{maskPhone(u.phone)}</div><Text type="secondary" style={{ fontSize: 'var(--iam-font-secondary)' }}>{maskEmail(u.email)}</Text></div> }, { title: '状态', key: 'status', width: 140, render: (_, u) => <Space size={0} wrap><StatusTag value={u.status}/>{u.locked && <StatusTag value="locked"/>}</Space> }, { title: '二次认证', key: 'mfa', width: 110, render: (_, u) => <Tag>尚未开通</Tag> }, { title: '最近登录', key: 'lastLogin', width: 150, render: (_, u) => dateText(db.logs.find(l => l.type === 'login' && l.domain === domain && (l.actor === u.account || l.actor === u.id))?.createdAt) }, { title: '操作', key: 'action', fixed: 'right', width: 160, render: (_, u) => <Space><Button type="link" style={{ padding: 0 }} disabled={!editable} onClick={() => lockedOnly ? unlock(u) : (setUserEdit(u), setUserOpen(true))}>{lockedOnly ? '解锁' : '编辑'}</Button><Dropdown menu={{ items: [{ key: 'detail', label: '查看详情', onClick: () => setDetail(u) }, { key: 'toggle', label: u.status === 'enabled' ? '停用账号' : '启用账号', disabled: !editable || u.kind === 'admin', danger: u.status === 'enabled', onClick: () => toggle(u) }, { key: 'reset', label: '申请重置密码', disabled: true, onClick: () => reset(u) }, ...(u.locked ? [{ key: 'unlock', label: '解锁账号', disabled: !editable, onClick: () => unlock(u) }] : []), { key: 'grants', label: '查看应用授权', disabled: !grantsAvailable, onClick: () => persons ? setDetail(u) : nav(`/console/${domain}/grants/users?user=${u.id}`) }] }}><Button type="link" style={{ padding: 0 }}>更多 <DownOutlined /></Button></Dropdown></Space> }]}/></div></div>
 <RecordEditor open={userOpen} title={persons ? (userEdit ? '编辑自然人' : '新建自然人') : (userEdit ? '编辑用户' : '新建用户')} width={736} initial={{ ...userDefault, ...extensionInitial(userDefault.ext), additionalOrgIds: userDefault.appointments.filter(a => !a.primary).map(a => a.orgId) } as unknown as FormValues} fields={[...fields, ...extensionFields(db.catalog, 'SUBJECT')]} onClose={() => setUserOpen(false)} onSave={async (v) => { const { additionalOrgIds, initialPassword, ...rest } = v; const org = persons ? '' : String(v.orgId || ''); const post = String(v.post || ''); const appointments = persons ? [] : [{ orgId: org, post, primary: true }, ...((additionalOrgIds as string[] | undefined) || []).filter(id => id !== org).map(id => ({ orgId: id, post, primary: false }))]; await api.save('users', { ...userDefault, ...rest, ext: extensionValues(db.catalog, 'SUBJECT', v), appointments, existingAccount: !userEdit && linkExisting, ...(session.realm !== 'platform' && !userEdit && !linkExisting ? { initialPassword } : {}) } as User, userEdit ? '编辑用户' : linkExisting ? '关联已有账号' : '创建用户'); }}>{!userEdit && session.realm !== 'platform' && <div style={{ marginBottom: 16 }}><Segmented block aria-label="开户方式" value={linkExisting ? 'existing' : 'new'} onChange={value => setLinkExisting(value === 'existing')} options={[{ label: '创建新账号', value: 'new' }, { label: '关联已有账号', value: 'existing' }]}/>{linkExisting && <Alert style={{ marginTop: 12 }} type="info" showIcon title="请输入已有账号的登录名" description="将为该账号建立当前租户的身份与任职关系，保留原有姓名和登录密码。"/>}</div>}</RecordEditor>
 <RecordEditor open={orgOpen} title={orgEdit ? '编辑机构' : '新建机构'} initial={{ ...orgDefault, ...extensionInitial(orgDefault.ext) } as unknown as FormValues} fields={[{ name: 'name', label: '机构名称', required: true, max: 60 }, { name: 'code', label: '机构编码', required: true, max: 48 }, { name: 'parentId', label: '上级机构', type: 'select', options: options(orgs.filter(o => o.id !== orgEdit?.id)) }, { name: 'leader', label: '负责人', type: 'select', options: options(db.users.filter(u => u.domain === domain && u.status === 'enabled')) }, { name: 'line', label: '业务条线', type: 'select', options: db.catalog.filter(c => c.category === 'line' && c.status === 'enabled').map(c => ({ value: c.code, label: c.name })) }, { name: 'status', label: '机构状态', type: 'select', options: [{ value: 'enabled', label: '启用' }, { value: 'disabled', label: '停用' }] }, ...extensionFields(db.catalog, 'ORG')]} onClose={() => setOrgOpen(false)} onSave={v => api.save('orgs', { ...orgDefault, ...v, ext: extensionValues(db.catalog, 'ORG', v), parentId: v.parentId || null } as Org, '维护机构')}/>
 <UserDetail user={detail} onClose={() => { setDetail(null); if (params.has('user')) {
        params.delete('user');
        setParams(params, { replace: true });
    } }} onEdit={editable ? u => { setUserEdit(u); setUserOpen(true); } : undefined}/>{importOpen && <ImportUsers open domain={domain} onClose={() => setImportOpen(false)}/>}</>;
}
function TooltipIcon({ label, disabled, onClick }: {
    label: string;
    disabled: boolean;
    onClick: () => void;
}) { return <Button type="text" size="small" aria-label={label} title={label} disabled={disabled} icon={<PlusOutlined />} onClick={onClick}/>; }
