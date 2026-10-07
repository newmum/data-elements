import { useCallback, useEffect, useRef, useState } from 'react';
import type { Key } from 'react';
import { App, Alert, Button, Card, Checkbox, Dropdown, Input, Space, Table, Tag, Tree } from 'antd';
import { ApartmentOutlined, DownOutlined, PlusOutlined, UploadOutlined, UserOutlined, TeamOutlined, SolutionOutlined, UserDeleteOutlined, RightOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api, subscribeUserOperations, useDatabase, useSession, type UserOperation } from '../mock/store';
import { allowedUser, belongsToOrg, descendants, effectiveAccess, makeBase } from '../domain/engine';
import type { Domain, Org, User } from '../domain/types';
import { CardMetric, ConfirmDelete, DataTable, dateText, exportCsv, options, PageTitle, RecordEditor, StatusTag, Text, useAction, useEditable, type Field, type FormValues } from '../components/common';
import { maskEmail, maskPhone } from '../domain/csv';
import ImportUsers from '../components/ImportUsers';
import { extensionFields, extensionInitial, extensionValues } from '../components/directory-fields';
import { UserAvatar } from '../components/visuals';
import UserEditor from '../components/UserEditor';
export interface OrgTreeNode {
    key: string;
    title: string;
    value: string;
    children: OrgTreeNode[];
}
export function organizationTree(orgs: Org[], parentId: string | null = null): OrgTreeNode[] {
    const byParent = new Map<string | null, Org[]>();
    for (const org of orgs) {
        const siblings = byParent.get(org.parentId) || [];
        siblings.push(org);
        byParent.set(org.parentId, siblings);
    }
    const build = (parent: string | null): OrgTreeNode[] => (byParent.get(parent) || [])
        .sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0) || a.id.localeCompare(b.id))
        .map(org => ({ key: org.id, title: org.name, value: org.id, children: build(org.id) }));
    return build(parentId);
}
export function organizationExpandedKeys(orgs: Org[], selectedId: string): string[] {
    const byId = new Map(orgs.map(org => [org.id, org]));
    const keys = new Set(orgs.filter(org => !org.parentId).map(org => org.id));
    const seen = new Set<string>();
    let current = byId.get(selectedId);
    while (current?.parentId && !seen.has(current.id)) {
        seen.add(current.id);
        keys.add(current.parentId);
        current = byId.get(current.parentId);
    }
    return [...keys];
}
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
export default function Organization({ domain, persons = false, lockedOnly = false }: {
    domain: Domain;
    persons?: boolean;
    lockedOnly?: boolean;
}) {
    const db = useDatabase();
    const session = useSession()!;
    const grantsAvailable = session.capabilities?.includes('grants') === true;
    const nav = useNavigate();
    const [params, setParams] = useSearchParams();
    const [orgId, setOrgId] = useState('all');
    const [children, setChildren] = useState(true);
    const [treeQuery, setTreeQuery] = useState('');
    const [userEdit, setUserEdit] = useState<User | null>(null);
    const [userOpen, setUserOpen] = useState(false);
    const [orgEdit, setOrgEdit] = useState<Org | null>(null);
    const [orgOpen, setOrgOpen] = useState(false);
    const [selected, setSelected] = useState<Key[]>([]);
    const [importOpen, setImportOpen] = useState(false);
    const [remoteParams, setRemoteParams] = useState({ page: 1, size: 10, q: '', status: 'all' });
    const [remoteResult, setRemoteResult] = useState<{ list: User[]; total: number }>({ list: [], total: 0 });
    const [remoteLoading, setRemoteLoading] = useState(false);
    const [remoteError, setRemoteError] = useState('');
    const { run, busy } = useAction();
    const { modal, message } = App.useApp();
    useEffect(() => { const id = params.get('user'); if (id) {
        const u = db.users.find(u => u.id === id && allowedUser(db, session, u));
        if (u) { setUserEdit(u); setUserOpen(true); }
    } }, [params.get('user'), db.users, session]);
    const closeUserEditor = () => {
        setUserOpen(false);
        if (params.has('user')) { params.delete('user'); setParams(params, { replace: true }); }
    };
    const editable = useEditable('users');
    const canOrg = useEditable('orgs');
    useEffect(() => { setSelected([]); }, [orgId, children]);
    useEffect(() => {
        if (lockedOnly) return;
        let active = true;
        setRemoteLoading(true);
        void api.listUsers({ domain, ...remoteParams, orgId: orgId === 'all' ? '' : orgId, includeChildren: children })
            .then(result => { if (active) { setRemoteResult({ list: result.list, total: result.total }); setRemoteError(''); } })
            .catch(error => { if (active) setRemoteError(error instanceof Error ? error.message : '人员列表加载失败'); })
            .finally(() => { if (active) setRemoteLoading(false); });
        return () => { active = false; };
    }, [domain, orgId, children, remoteParams, db, lockedOnly]);
    const orgs = db.orgs.filter(o => o.domain === domain && (session.scopeMode === 'tenant' || (session.orgIds || []).some(id => descendants(db, id).has(o.id))));
    const data = db.users.filter(u => allowedUser(db, session, u) && (!lockedOnly || u.locked) && (orgId === 'all' || belongsToOrg(db, u, orgId, children)));
    const tableData = lockedOnly ? data : remoteResult.list;
    const businessUsers = data.filter(u => u.kind !== 'admin');
    const userDefault: User = userEdit || { ...makeBase('', domain, 'user'), account: '', email: '', phone: '', orgId: persons ? '' : orgId === 'all' ? (orgs.find(o => o.status === 'enabled')?.id || '') : orgId, post: '', employmentType: 'employee', mfaEnabled: false, kind: persons ? 'citizen' : 'person', locked: false, appointments: [], verified: false, history: [] };
    const orgDefault: Org = orgEdit || { ...makeBase('', domain, 'org'), code: '', parentId: orgId === 'all' ? null : orgId, leader: '', line: '' };
    const postOptions = db.catalog.filter(c => c.domain === domain && c.category === 'dictionary' && c.value === 'POST' && c.status === 'enabled').map(c => ({ value: c.code, label: c.name }));
    const selectableOrgs = orgs.filter(org => org.status === 'enabled');
    const selectableOrgIds = new Set(selectableOrgs.map(org => org.id));
    const selectableOrgTree = organizationTree(selectableOrgs.map(org => ({ ...org, parentId: org.parentId && selectableOrgIds.has(org.parentId) ? org.parentId : null })));
    const fields: Field[] = [
        { name: 'account', label: '账号', required: true, max: userEdit ? 50 : 32, span: 12, disabled: !!userEdit, pattern: !userEdit ? /^[A-Za-z][A-Za-z0-9_.-]{2,31}$/ : undefined, patternMessage: '3–32位，以字母开头' },
        { name: 'name', label: '姓名', required: true, max: 50, span: 12 },
        { name: 'email', label: '邮箱', span: 12 },
        { name: 'phone', label: '手机号', span: 12 },
        ...(!persons ? [
            { name: 'orgId', label: '组织机构', type: 'tree' as const, required: true, treeData: selectableOrgTree, treeExpandedKeys: organizationExpandedKeys(selectableOrgs, userDefault.orgId), span: 12 as const },
            { name: 'post', label: '岗位', type: 'select' as const, options: postOptions, span: 12 as const, emptyText: postOptions.length ? undefined : '暂无岗位，请到“组织与用户 → 职务岗位”新增。' },
        ] : []),
        ...(!persons ? [{ name: 'additionalOrgIds', label: '兼任机构', type: 'multi' as const, options: options(orgs) }] : []),
        { name: 'status', label: '账户状态', type: 'select', required: true, options: [{ value: 'enabled', label: '已启用' }, { value: 'disabled', label: '已停用' }] },
    ];
    const toggle = (u: User) => modal.confirm({ title: `${u.status === 'enabled' ? '停用' : '启用'}用户“${u.name}”？`, content: u.status === 'enabled' ? '停用后，该账号的有效应用访问将暂停；已有授权来源仍会保留。' : '恢复启用不会解除登录锁定，也不会增加新的权限。', okText: u.status === 'enabled' ? '停用用户' : '启用用户', cancelText: '取消', onOk: () => run(() => api.save('users', { ...u, status: u.status === 'enabled' ? 'disabled' : 'enabled' }, '调整用户状态'), '账户状态已更新') });
    const removeUser = (u: User) => modal.confirm({
        title: `删除用户“${u.name}”？`,
        content: '删除后，该用户不再出现在人员目录。历史审计仍会保留；如有关联授权或权限组，请先解除关联。服务端会再次核验管理范围、版本和关联关系。',
        okText: '删除用户', cancelText: '取消', okButtonProps: { danger: true },
        onOk: async () => {
            try {
                await api.remove('users', u.id);
                setSelected(keys => keys.filter(key => key !== u.id));
                setRemoteParams(value => ({ ...value, page: 1 }));
                message.success('用户已删除');
            } catch (error) {
                message.error(error instanceof Error ? error.message : '删除用户失败，请重试');
                throw error;
            }
        },
    });
    const reset = (u: User) => modal.info({ title: '密码重置申请', content: '请核对账号信息。确认后将记录该账号的密码重置申请。', okText: '提交申请', onOk: () => run(() => api.recordEvent(domain, '申请重置密码', u.name, { userId: u.id }), '已提交申请') });
    const unlock = (u: User) => modal.confirm({ title: `解锁“${u.name}”？`, content: '仅解除账号锁定，不改变启停状态和应用权限。', okText: '解锁账号', cancelText: '取消', onOk: () => run(() => api.save('users', { ...u, locked: false }, '解除账号锁定'), '已解除锁定') });
    const batch = () => modal.confirm({ title: `停用选中的 ${selected.length} 个账号？`, content: '将统一核验所选人员的身份域、版本和管理范围；其中一项不符合要求时，本次停用不会提交。', okText: '批量停用', cancelText: '取消', okButtonProps: { danger: true }, onOk: async () => {
            const entries = selected.map(id => tableData.find(user => user.id === id));
            if (entries.length > 100 || entries.some(user => !user || user.kind === 'admin')) {
                message.error('每次最多停用 100 个有效用户，且不能包含内置管理账号');
                return;
            }
            try {
                const count = await api.batchDisableUsers(entries.map(user => ({ id: user!.id, version: user!.version })), domain);
                setSelected([]);
                message.success(`已停用 ${count} 个账号`);
            } catch (error) {
                message.error(error instanceof Error ? error.message : '批量停用失败');
            }
        } });
    const treeData = organizationTree(orgs.map(org => ({ ...org, parentId: org.parentId && orgs.some(parent => parent.id === org.parentId) ? org.parentId : null })));
    const filterTree = (nodes: typeof treeData): typeof treeData => nodes.map(n => ({ ...n, children: filterTree(n.children) })).filter(n => n.title.includes(treeQuery) || n.children.length > 0);
    const showTree = !persons && !lockedOnly;
    return <>{!showTree && <PageTitle title={lockedOnly ? '账号解锁' : persons ? '自然人管理' : '用户管理'} description={lockedOnly ? '解除当前租户身份锁定；密码失败产生的临时登录限制会按认证策略到期。' : persons ? '统一管理自然人账户，分别查看启用与实名核验状态。' : '维护权威身份数据，让组织与人员关系保持一致。'} extra={!lockedOnly && <Button type="primary" icon={<PlusOutlined />} disabled={!editable} onClick={() => { setUserEdit(null); setUserOpen(true); }}>{persons ? '新建自然人' : '新建用户'}</Button>}/>}
 {editable && !persons && !lockedOnly && <UserOperationRecovery/>}
 <div className="metric-grid four"><CardMetric label={persons ? '自然人账户' : lockedOnly ? '锁定账号' : '内部员工'} value={persons || lockedOnly ? businessUsers.length : businessUsers.filter(u => u.employmentType === 'employee').length} icon={<TeamOutlined />} description={persons ? '当前身份域' : '当前机构范围'}/><CardMetric label={persons ? '已完成核验' : '外部合作伙伴'} value={businessUsers.filter(u => persons ? u.verified : u.employmentType === 'partner').length} tone="purple" icon={<SolutionOutlined />} description={persons ? '实名状态' : '协同与外部服务身份'}/><CardMetric label={persons ? '待完成核验' : '临时账号'} value={businessUsers.filter(u => persons ? !u.verified : u.employmentType === 'temporary').length} tone="orange" icon={<UserOutlined />} description={persons ? '待补充身份资料' : '实习、临时项目与协作'}/><CardMetric label="已停用账号" value={businessUsers.filter(u => u.status === 'disabled').length} icon={<UserDeleteOutlined />} tone="red" description="当前租户身份已停用"/></div>
 {lockedOnly && <Alert style={{ marginBottom: 16 }} title="解除身份锁定不会启用停用账号，也不会恢复已撤销的授权；临时登录限制仍按认证策略解除。" type="info" showIcon/>}
 <div className={showTree ? 'organization-layout' : 'single-layout'}>{showTree && <Card size="small" className="organization-tree-card" title={<Space><ApartmentOutlined />组织架构</Space>} extra={<TooltipIcon label="新增机构" disabled={!canOrg} onClick={() => { setOrgEdit(null); setOrgOpen(true); }}/>} styles={{ body: { padding: '16px 12px' } }}><Input.Search placeholder="搜索机构" aria-label="搜索机构" allowClear value={treeQuery} onChange={e => setTreeQuery(e.target.value)} style={{ marginBottom: 16 }}/><Button type={orgId === 'all' ? 'primary' : 'text'} block onClick={() => { setOrgId('all'); setRemoteParams(value => ({ ...value, page: 1 })); }} style={{ marginBottom: 8 }}>全部用户</Button><Tree key={treeQuery || domain} blockNode defaultExpandAll={!!treeQuery} defaultExpandedKeys={treeData.map(node => node.key)} switcherIcon={<RightOutlined />} treeData={filterTree(treeData)} titleRender={node => <span className="org-tree-label"><span title={node.title}>{node.title}</span><small>{db.users.filter(u => u.kind !== 'admin' && belongsToOrg(db, u, node.key, true)).length}</small></span>} selectedKeys={orgId === 'all' ? [] : [orgId]} onSelect={keys => { setOrgId(String(keys[0] || 'all')); setRemoteParams(value => ({ ...value, page: 1 })); }}/><div className="tree-bottom"><Checkbox checked={children} onChange={e => { setChildren(e.target.checked); setRemoteParams(value => ({ ...value, page: 1 })); }}>包含下级机构用户</Checkbox>{orgId !== 'all' && <Space style={{ marginTop: 12 }}><Button size="small" disabled={!canOrg} onClick={() => { setOrgEdit(db.orgs.find(o => o.id === orgId) || null); setOrgOpen(true); }}>编辑机构</Button><ConfirmDelete target={db.orgs.find(o => o.id === orgId)?.name || ''} disabled={!canOrg} onConfirm={async () => { await api.remove('orgs', orgId); setOrgId('all'); }}/></Space>}</div></Card>}
 <div style={{ minWidth: 0 }}>{remoteError && <Alert type="error" showIcon title={remoteError} style={{ marginBottom: 12 }}/>}<DataTable data={tableData} remote={lockedOnly ? undefined : { total: remoteResult.total, page: remoteParams.page, size: remoteParams.size, onChange: (page, size, q, status) => setRemoteParams({ page, size, q, status }) }} instantSearch={showTree} filterAction={showTree ? <Button type="primary" icon={<PlusOutlined />} disabled={!editable} onClick={() => { setUserEdit(null); setUserOpen(true); }}>新建用户</Button> : undefined} searchFields={['name', 'account', 'email']} searchPlaceholder="搜索姓名、账号或邮箱" title={orgId === 'all' ? (persons ? '自然人列表' : lockedOnly ? '锁定账号' : '用户列表') : db.orgs.find(o => o.id === orgId)?.name} selection={selected} onSelection={!persons && !lockedOnly ? setSelected : undefined} loading={busy || remoteLoading} exporter={rows => { exportCsv(persons ? '自然人账户' : '用户清单', ['姓名', '账号', '邮箱（脱敏）', '手机号（脱敏）', '机构', '状态'], rows.map(u => [u.name, u.account, maskEmail(u.email), maskPhone(u.phone), db.orgs.find(o => o.id === u.orgId)?.name || u.orgName || '', u.status]));  }} actions={<>{selected.length > 0 && <Button danger disabled={!editable} onClick={batch}>批量停用</Button>}{!persons && !lockedOnly && <Button icon={<UploadOutlined />} disabled={!editable} onClick={() => setImportOpen(true)}>导入用户</Button>}</>} columns={[{ title: '姓名 / 账号', key: 'name', width: 180, render: (_, u) => <Space><UserAvatar user={u}/><div><Button type="link" style={{ padding: 0, height: 'auto' }} onClick={() => { setUserEdit(u); setUserOpen(true); }}>{u.name}</Button><div><Text type="secondary" style={{ fontSize: 'var(--iam-font-secondary)' }}>{u.account}</Text></div></div></Space> }, ...(!persons ? [{ title: '身份类型', key: 'type', width: 120, render: (_: unknown, u: User) => <Tag color={u.kind === 'admin' ? 'geekblue' : u.employmentType === 'partner' ? 'purple' : u.employmentType === 'temporary' ? 'orange' : 'blue'}>{u.kind === 'admin' ? '平台管理账号' : u.employmentType === 'partner' ? '外部合作伙伴' : u.employmentType === 'temporary' ? '临时账号' : '内部员工'}</Tag> }, { title: '所属机构', key: 'org', width: 150, render: (_: unknown, u: User) => db.orgs.find(o => o.id === u.orgId)?.name || u.orgName || '—' }] : [{ title: '实名状态', key: 'verified', render: (_: unknown, u: User) => <Tag color={u.verified ? 'success' : 'warning'}>{u.verified ? '已核验' : '未核验'}</Tag> }]), { title: '已授权应用', key: 'apps', width: 110, render: (_, u) => <Tag color='blue'>{grantsAvailable ? effectiveAccess(db, u.id).length : '—'}</Tag> }, { title: '联系方式', key: 'contact', width: 190, render: (_, u) => <div><div>{maskPhone(u.phone)}</div><Text type="secondary" style={{ fontSize: 'var(--iam-font-secondary)' }}>{maskEmail(u.email)}</Text></div> }, { title: '状态', key: 'status', width: 140, render: (_, u) => <Space size={0} wrap><StatusTag value={u.status}/>{u.locked && <StatusTag value="locked"/>}</Space> }, { title: '二次认证', key: 'mfa', width: 110, render: (_, u) => <Tag>尚未开通</Tag> }, { title: '最近登录', key: 'lastLogin', width: 150, render: (_, u) => dateText(db.logs.find(l => l.type === 'login' && l.domain === domain && (l.actor === u.account || l.actor === u.id))?.createdAt) }, { title: '操作', key: 'action', fixed: 'right', width: 160, render: (_, u) => <Space><Button type="link" style={{ padding: 0 }} disabled={lockedOnly && !editable} onClick={() => lockedOnly ? unlock(u) : (setUserEdit(u), setUserOpen(true))}>{lockedOnly ? '解锁' : '编辑'}</Button><Dropdown menu={{ items: [{ key: 'toggle', label: u.status === 'enabled' ? '停用账号' : '启用账号', disabled: !editable || u.kind === 'admin', danger: u.status === 'enabled', onClick: () => toggle(u) }, { key: 'reset', label: '申请重置密码', disabled: true, onClick: () => reset(u) }, ...(u.locked ? [{ key: 'unlock', label: '解锁账号', disabled: !editable, onClick: () => unlock(u) }] : []), { key: 'grants', label: '查看应用授权', disabled: !grantsAvailable, onClick: () => persons ? (setUserEdit(u), setUserOpen(true)) : nav(`/console/${domain}/grants/users?user=${u.id}`) }, ...(!persons && !lockedOnly ? [{ key: 'remove', label: '删除用户', danger: true, disabled: !editable || u.kind === 'admin' || u.id === session.userId || u.account === session.username, onClick: () => removeUser(u) }] : [])] }}><Button type="link" style={{ padding: 0 }}>更多 <DownOutlined /></Button></Dropdown></Space> }]}/></div></div>
 {!persons ? <UserEditor open={userOpen} user={userEdit} initial={userDefault} fields={[...fields, ...extensionFields(db.catalog, 'SUBJECT')]} domain={domain} readOnly={!editable || lockedOnly} onClose={closeUserEditor}/> :
 <RecordEditor open={userOpen} title={userEdit ? '用户信息' : '新建自然人'} width={736} readOnly={!editable} formHeading="基本信息"
    initial={{ ...userDefault, ...extensionInitial(userDefault.ext) } as FormValues}
    fields={[...fields, ...extensionFields(db.catalog, 'SUBJECT')]} onClose={closeUserEditor} onSave={async values => {
        await api.save('users', { ...userDefault, ...values, ext: extensionValues(db.catalog, 'SUBJECT', values), appointments: [] } as User, userEdit ? '编辑自然人' : '创建自然人');
    }}/>}  <RecordEditor open={orgOpen} title={orgEdit ? '编辑机构' : '新建机构'} initial={{ ...orgDefault, ...extensionInitial(orgDefault.ext) } as unknown as FormValues} fields={[{ name: 'name', label: '机构名称', required: true, max: 60 }, { name: 'code', label: '机构编码', required: true, max: 48 }, { name: 'parentId', label: '上级机构', type: 'select', options: options(orgs.filter(o => o.id !== orgEdit?.id)) }, { name: 'leader', label: '负责人', type: 'select', options: options(db.users.filter(u => u.domain === domain && u.status === 'enabled')) }, { name: 'line', label: '业务条线', type: 'select', options: db.catalog.filter(c => c.category === 'line' && c.status === 'enabled').map(c => ({ value: c.code, label: c.name })) }, { name: 'status', label: '机构状态', type: 'select', options: [{ value: 'enabled', label: '启用' }, { value: 'disabled', label: '停用' }] }, ...extensionFields(db.catalog, 'ORG')]} onClose={() => setOrgOpen(false)} onSave={v => api.save('orgs', { ...orgDefault, ...v, ext: extensionValues(db.catalog, 'ORG', v), parentId: v.parentId || null } as Org, '维护机构')}/>
 {importOpen && <ImportUsers open domain={domain} onClose={() => setImportOpen(false)}/>}</>;
}
function TooltipIcon({ label, disabled, onClick }: {
    label: string;
    disabled: boolean;
    onClick: () => void;
}) { return <Button type="text" size="small" aria-label={label} title={label} disabled={disabled} icon={<PlusOutlined />} onClick={onClick}/>; }
