import { useEffect, useState } from 'react';
import ApplicationAssignments from '../components/ApplicationAssignments';
import { ApplicationLocalInventory, useLocalInventory } from '../components/ApplicationLocalInventory';
import { ApplicationDirectorySync } from '../components/ApplicationDirectorySync';
import { ApplicationRuntimeConnection } from '../components/ApplicationRuntimeConnection';
import { AuthClients } from '../components/AuthClients';
import { App, Alert, Avatar, Button, Card, Checkbox, Col, Descriptions, Dropdown, Empty, Input, Modal, Radio, Row, Segmented, Select, Space, Steps, Switch, Table, Tabs, Tag, Tooltip, theme } from 'antd';
import { AppstoreOutlined, ArrowLeftOutlined, CheckCircleOutlined, CopyOutlined, EllipsisOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, SettingOutlined, UnorderedListOutlined, SafetyCertificateOutlined, TeamOutlined, UserOutlined, ApiOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { useDatabase, useSession, api, foundationApi } from '../mock/store';
import { accessibleAppIds, effectiveAccess, makeBase } from '../domain/engine';
import type { Application, Domain, PortalConfig, Resource, Role } from '../domain/types';
import { CardMetric, ConfirmDelete, DataTable, dateText, options, RecordEditor, StatusTag, Text, useAction, useEditable, type Field, type FormValues } from '../components/common';
import { AppIcon, UserAvatar } from '../components/visuals';
const portalFormKeys = { logo: 'portalLogo', systemName: 'portalSystemName', systemCode: 'portalSystemCode', themeColor: 'portalThemeColor', watermarkContent: 'portalWatermarkContent', needLogin: 'portalNeedLogin', showShoppingCar: 'portalShowShoppingCar', showTagsView: 'portalShowTagsView', showWatermark: 'portalShowWatermark', version: 'portalVersion' } as const;
type LocalInventoryCounts = { available: boolean; counts: { users: number; authorizedUsers: number } };
type LocalInventorySummary = LocalInventoryCounts & { appId: string };
/** The receiver permits at most 50 apps and 10 distinct runtime tenants per summary request. */
function localInventoryBatches(apps: Application[]): string[][] {
    const batches: string[][] = [];
    let batch: string[] = [];
    let tenants = new Set<string>();
    for (const app of apps) {
        // Missing bindings are treated as separate tenants so the client never
        // assumes that two applications share a receiver it cannot identify.
        const tenant = String(app.runtimeTenantId ?? '').trim() || `unbound:${app.id}`;
        if (batch.length === 50 || (tenants.size === 10 && !tenants.has(tenant))) {
            batches.push(batch);
            batch = [];
            tenants = new Set<string>();
        }
        batch.push(app.id);
        tenants.add(tenant);
    }
    if (batch.length) batches.push(batch);
    return batches;
}
function NewApplication({ open, domain, onClose }: { open: boolean; domain: Domain; onClose: () => void }) {
    const db = useDatabase();
    const initial: Application = { ...makeBase('', domain, 'app'), code: '', group: db.catalog.find(c => c.category === 'app-group')?.name || '未分组', description: '', protocol: '未配置', environment: 'production', homepage: '', redirectUris: '', logoutUri: '', owner: '', clientId: '', clientType: 'confidential', mfa: false, accessTtl: 0, scopes: [], secretVersion: 0 };
    return <RecordEditor open={open} title="新建应用" initial={initial as unknown as FormValues} onClose={onClose} fields={[
        { name: 'name', label: '应用名称', required: true, max: 50 },
        { name: 'code', label: '应用编码', required: true, max: 48, pattern: /^[a-z][a-z0-9-]{2,47}$/, patternMessage: '3–48位小写字母、数字或短横线' },
        { name: 'group', label: '应用分组', type: 'select', options: [{ value: '未分组', label: '未分组' }, ...db.catalog.filter(c => c.category === 'app-group').map(c => ({ value: c.name, label: c.name }))] }, { name: 'owner', label: '应用负责人' },
        { name: 'homepage', label: '应用首页' }, { name: 'description', label: '应用说明', type: 'textarea', max: 200 },
        { name: 'status', label: '登记状态', type: 'select', options: [{ value: 'enabled', label: '启用' }, { value: 'disabled', label: '停用' }] },
    ]} onSave={values => api.save('apps', { ...initial, ...values } as Application, '创建应用')}/>;
}
export default function Applications({ domain }: {
    domain: Domain;
}) {
    const db = useDatabase();
    const session = useSession()!;
    const grantsAvailable = session.capabilities?.includes('grants') === true;
    const { token } = theme.useToken();
    const nav = useNavigate();
    const [view, setView] = useState('cards');
    const [query, setQuery] = useState('');
    const [group, setGroup] = useState('all');
    const [create, setCreate] = useState(false);
    const [editing, setEditing] = useState<Application | null>(null);
    const [localCounts, setLocalCounts] = useState<Record<string, LocalInventoryCounts>>({});
    const { run } = useAction();
    const { modal } = App.useApp();
    const editable = useEditable('apps');
    const canWriteApp = (a: Application) => session.realm === 'platform' ? session.appWriteIds?.includes(a.id) === true : editable;
    const canCreate = session.realm === 'platform' ? session.globalPermissions?.includes('apps:write') === true : editable;
    const roleEditable = useEditable('roles');
    const resourceEditable = useEditable('resources');
    const all = db.apps.filter(a => a.domain === domain && accessibleAppIds(db, session).includes(a.id))
        .sort((a, b) => (a.sortNo ?? 1000) - (b.sortNo ?? 1000) || a.code.localeCompare(b.code));
    const localCountScope = all.map(a => `${a.id}:${a.runtimeTenantId ?? ''}`).join(',');
    useEffect(() => {
        if (session.realm !== 'platform') { setLocalCounts({}); return; }
        let active = true;
        setLocalCounts({});
        const batches = localInventoryBatches(all);
        const unavailable: LocalInventoryCounts = { available: false, counts: { users: 0, authorizedUsers: 0 } };
        void (async () => {
            const counts: Record<string, LocalInventoryCounts> = {};
            for (const batch of batches) {
                if (!active) break;
                try {
                    const query = new URLSearchParams({ appIds: batch.join(','), domain, summary: '1' });
                    const response = await foundationApi.read<{ items: LocalInventorySummary[] }>(`/idaas/applications/local-inventory?${query}`);
                    if (!Array.isArray(response?.items)) throw new Error('本地应用汇总返回格式不正确');
                    const expected = new Set(batch);
                    const byId = new Map<string, LocalInventorySummary>();
                    for (const item of response.items) {
                        if (!expected.has(item.appId) || byId.has(item.appId)) throw new Error('本地应用汇总包含重复或越界的应用');
                        byId.set(item.appId, item);
                    }
                    if (batch.some(id => !byId.has(id))) throw new Error('本地应用汇总缺少应用');
                    for (const id of batch) counts[id] = byId.get(id)!;
                } catch {
                    for (const id of batch) counts[id] = unavailable;
                }
                if (active) setLocalCounts({ ...counts });
            }
        })();
        return () => { active = false; };
    }, [localCountScope, domain, session.realm]);
    const data = all.filter(a => (group === 'all' || a.group === group) && (`${a.name} ${a.code}`).toLowerCase().includes(query.toLowerCase()));
    const appIds = new Set(all.map(a => a.id));
    const rolesCount = db.roles.filter(r => appIds.has(r.appId)).length;
    const usersCount = db.users.filter(u => u.domain === domain && u.kind !== 'admin').length;
    const resourcesCount = db.resources.filter(r => appIds.has(r.appId)).length;
    const editValues = { ...(editing || {}), sortPosition: editing ? String(all.findIndex(app => app.id === editing.id) + 1) : undefined,
        ...Object.fromEntries(Object.entries(portalFormKeys).map(([key, form]) => [form, editing?.portalConfig?.[key as keyof PortalConfig]])) } as FormValues;
    const saveEditing = async (values: FormValues) => {
        if (!editing) return;
        const portal: Record<string, string | boolean | undefined> = { ...editing.portalConfig };
        for (const [key, form] of Object.entries(portalFormKeys)) {
            const value = values[form];
            if ((typeof value === 'string' || typeof value === 'boolean') && (value !== '' || key in portal || key === 'logo')) portal[key] = value;
        }
        const fields = Object.fromEntries(Object.entries(values).filter(([key]) => !key.startsWith('portal')));
        await api.save('apps', { ...editing, ...fields, portalConfig: portal } as Application, '编辑应用');
    };
    const detail = (a: Application) => nav(`/console/${domain}/apps/${a.id}`);
    const toggle = (a: Application) => modal.confirm({ title: `${a.status === 'enabled' ? '停用' : '启用'}“${a.name}”？`, content: a.status === 'enabled' ? '停用当前身份应用登记，保留角色与资源记录。' : '启用当前身份应用登记。认证接入和访问授权须另行配置。', okText: a.status === 'enabled' ? '停用应用' : '启用应用', cancelText: '取消', okButtonProps: { danger: a.status === 'enabled' }, onOk: () => run(() => api.save('apps', { ...a, status: a.status === 'enabled' ? 'disabled' : 'enabled' }, '调整应用状态'), '应用状态已更新') });
    return <>
 <div className="metric-grid four"><CardMetric label="应用数量" value={all.length} icon={<AppstoreOutlined />} description="当前可管理的应用"/><CardMetric label="角色数量" value={rolesCount} icon={<TeamOutlined />} tone="cyan" description="这些应用的平台角色"/><CardMetric label="用户数量" value={usersCount} icon={<UserOutlined />} tone="green" description="当前身份域可见人员"/><CardMetric label="权限数量" value={resourcesCount} tone="purple" icon={<SafetyCertificateOutlined />} description="这些应用的资源权限"/></div>
 <Card size="small" className="section-gap" styles={{ body: { padding: '12px 16px' } }}><div className="inline-toolbar"><Space wrap><Input.Search aria-label="搜索应用" placeholder="搜索应用名称或编码" allowClear style={{ width: 300, maxWidth: '100%' }} value={query} onChange={e => setQuery(e.target.value)}/><Select aria-label="应用分组" value={group} onChange={setGroup} style={{ width: 160 }} options={[{ value: 'all', label: '全部分组' }, ...Array.from(new Set(all.map(a => a.group))).map(x => ({ value: x, label: x }))]}/><Text type="secondary">共 {data.length} 个应用</Text></Space><Space wrap><Segmented aria-label="应用视图" value={view} onChange={v => setView(String(v))} options={[{ value: 'cards', label: <Tooltip title="卡片视图"><AppstoreOutlined /></Tooltip> }, { value: 'table', label: <Tooltip title="表格视图"><UnorderedListOutlined /></Tooltip> }]}/><Button type="primary" icon={<PlusOutlined />} disabled={!canCreate} onClick={() => setCreate(true)}>新建应用</Button></Space></div></Card>
 {view === 'cards' ? <Row gutter={[12, 12]}>{data.map(a => <Col key={a.id} xs={24} lg={12} xl={8}><Card size="small" className="application-card" styles={{ body: { padding: 16 } }}><div className="application-card-top"><AppIcon app={a} size={40}/><div style={{ flex: 1, minWidth: 0 }}><Button type="link" className="application-title" onClick={() => detail(a)}><span className="application-title-text" title={a.name}>{a.name}</span></Button><div><Text type="secondary" style={{ fontSize: 'var(--iam-font-secondary)' }}>{a.code}</Text></div></div><Dropdown menu={{ items: [{ key: 'edit', label: '编辑信息', disabled: !canWriteApp(a), onClick: () => setEditing(a) }, { key: 'toggle', label: a.status === 'enabled' ? '停用应用' : '启用应用', danger: a.status === 'enabled', disabled: !canWriteApp(a), onClick: () => toggle(a) }] }}><Button type="text" aria-label={`${a.name}更多操作`} icon={<EllipsisOutlined />}/></Dropdown></div><Text type="secondary" className="application-description">{a.description || '尚未填写应用说明'}</Text><Space wrap><Tag>{a.group}</Tag><Tag>{a.protocol}</Tag><StatusTag value={a.status}/></Space><div className="app-card-metrics"><div><span>本地授权用户</span><strong>{localCounts[a.id]?.available ? localCounts[a.id].counts.authorizedUsers : '—'}</strong><small>{localCounts[a.id]?.available ? `本地用户 ${localCounts[a.id].counts.users} · 平台授权 ${grantsAvailable ? db.users.filter(u => effectiveAccess(db, u.id).some(v => v.appId === a.id)).length : '—'}` : '本地数据暂不可读'}</small></div><div><span>应用角色</span><strong>{db.roles.filter(r => r.appId === a.id).length}</strong></div><div><span>资源权限</span><strong>{db.resources.filter(r => r.appId === a.id).length}</strong></div></div><div className="application-card-footer"><Text type="secondary" style={{ fontSize: 'var(--iam-font-secondary)' }}>{a.mfa ? <><SafetyCertificateOutlined /> 已配置多因子认证</> : <>负责人：{a.owner}</>}</Text><Button type="link" style={{ padding: 0 }} onClick={() => detail(a)}>管理应用 <SettingOutlined /></Button></div></Card></Col>)}{!data.length && <Col span={24}><Card size="small"><Empty description="未找到符合条件的应用"/></Card></Col>}</Row> : <DataTable data={data} title="应用列表" searchFields={['name', 'code']} columns={[{ title: '应用名称', dataIndex: 'name', render: (_, r) => <Space><AppIcon app={r} size={36}/><Button type="link" style={{ padding: 0 }} onClick={() => detail(r)}>{r.name}</Button></Space> }, { title: '应用编码', dataIndex: 'code' }, { title: '分组', dataIndex: 'group' }, { title: '协议', dataIndex: 'protocol' }, { title: '状态', dataIndex: 'status', render: v => <StatusTag value={v}/> }, { title: '负责人', dataIndex: 'owner' }, { title: '操作', key: 'actions', render: (_, r) => <Space><Button type="link" onClick={() => detail(r)}>管理</Button><Button type="link" disabled={!canWriteApp(r)} onClick={() => setEditing(r)}>编辑</Button></Space> }]}/>}
 <NewApplication open={create} domain={domain} onClose={() => setCreate(false)}/><RecordEditor open={!!editing} title="编辑应用信息" initial={editValues} onClose={() => setEditing(null)} fields={[{ name: 'name', label: '应用名称', required: true, max: 50 }, { name: 'group', label: '分组', type: 'select', options: [{ value: '未分组', label: '未分组' }, ...db.catalog.filter(c => c.category === 'app-group').map(c => ({ value: c.name, label: c.name }))] }, { name: 'owner', label: '负责人' }, { name: 'description', label: '应用说明', type: 'textarea', max: 200 }, ...(session.globalPermissions?.includes('apps:write') && all.length > 1 ? [{ name: 'sortPosition', label: '显示位置', type: 'select' as const, options: all.map((app, index) => ({ value: String(index + 1), label: `${index + 1} · ${app.name}` })), help: '保存后卡片和列表按此顺序展示。' }] : []), { name: 'portalLogo', label: '应用标识', type: 'image', max: 350000 }, { name: 'portalSystemName', label: '租户系统名称', max: 100 }, { name: 'portalSystemCode', label: '租户系统代码', max: 64, pattern: /^[A-Za-z0-9_-]+$/ }, { name: 'portalThemeColor', label: '主题颜色', max: 7, pattern: /^#[0-9a-fA-F]{6}$/ }, { name: 'portalWatermarkContent', label: '水印内容', type: 'textarea', max: 200 }, { name: 'portalVersion', label: '版本号', max: 40 }, { name: 'portalNeedLogin', label: '需要登录', type: 'switch' }, { name: 'portalShowShoppingCar', label: '显示购物车', type: 'switch' }, { name: 'portalShowTagsView', label: '显示标签页', type: 'switch' }, { name: 'portalShowWatermark', label: '显示水印', type: 'switch' }]} onSave={saveEditing}/>
 </>;
}
export function ApplicationDetail({ domain, appId }: {
    domain: Domain;
    appId: string;
}) {
    const db = useDatabase();
    const session = useSession()!;
    const grantsAvailable = session.capabilities?.includes('grants') === true;
    const app = db.apps.find(a => a.id === appId && a.domain === domain && accessibleAppIds(db, session).includes(a.id));
    const nav = useNavigate();
    const mayEdit = useEditable('apps');
    const editable = session.realm === 'platform' ? !!app && session.appWriteIds?.includes(app.id) === true : mayEdit;
    const roleEditable = useEditable('roles') && (session.realm !== 'platform' || (session.appPermissions?.[appId] || []).includes('roles:write'));
    const resourceEditable = useEditable('resources') && (session.realm !== 'platform' || (session.appPermissions?.[appId] || []).includes('resources:write'));
    const [tab, setTab] = useState('overview');
    const [editRole, setEditRole] = useState<Role | null>(null);
    const [editRes, setEditRes] = useState<Resource | null>(null);
    const [roleOpen, setRoleOpen] = useState(false);
    const [resOpen, setResOpen] = useState(false);
    const [assignedCounts, setAssignedCounts] = useState<{ key: string; users: number; orgs: number } | null>(null);
    const local = useLocalInventory(appId, domain, session.realm === 'platform' && !!app);
    useEffect(() => {
        if (session.realm !== 'platform' || !app || !session.capabilities?.includes('assignments')) return;
        let active = true;
        const key = `${domain}:${appId}`;
        void foundationApi.read<{ subjects: { directory_assigned?: number }[]; orgs: { status?: string }[] }>(`/idaas/assignments/list?appId=${encodeURIComponent(appId)}&domain=${domain}`)
            .then(result => { if (active) setAssignedCounts({ key, users: result.subjects.filter(row => row.directory_assigned !== 0).length, orgs: result.orgs.filter(row => row.status === 'ACTIVE').length }); })
            .catch(() => { if (active) setAssignedCounts(null); });
        return () => { active = false; };
    }, [appId, domain, session.realm, !!app, session.capabilities?.includes('assignments')]);
    if (!app)
        return <Empty description="应用不存在或不在当前管理范围"/>;
    const roles = db.roles.filter(r => r.appId === app.id);
    const resources = db.resources.filter(r => r.appId === app.id);
    const users = db.users.filter(u => u.domain === domain && effectiveAccess(db, u.id).some(v => v.appId === app.id));
    const localCounts = local.inventory?.available ? local.inventory.counts : null;
    const platformCounts = assignedCounts?.key === `${domain}:${appId}` ? assignedCounts : null;
    const localPanel = (kind: 'users' | 'orgs' | 'roles' | 'resources' | 'authorizedUsers') => session.realm === 'platform' && <ApplicationLocalInventory inventory={local.inventory} loading={local.loading} error={local.error} kind={kind} onLoad={() => void local.load()}/>;
    const roleDefault: Role = editRole || { ...makeBase('', domain, 'role'), appId: app.id, code: '', description: '', resourceIds: [] };
    const resourceDefault: Resource = editRes || { ...makeBase('', domain, 'resource'), appId: app.id, parentId: null, code: '', type: 'menu', path: '', method: '' };
    const items = [
        { key: 'overview', label: '概览', children: <div className="application-overview"><Row gutter={[16, 16]}>
            <Col xs={24} lg={12}><Card size="small" title="基本信息">
                <Descriptions size="small" column={{ xs: 1, sm: 2 }} layout="vertical" items={[
                    { key: 'code', label: '应用编码', children: app.code },
                    { key: 'group', label: '所属分组', children: app.group },
                    { key: 'owner', label: '负责人', children: app.owner },
                    { key: 'status', label: '状态', children: <StatusTag value={app.status}/> },
                    { key: 'homepage', label: '应用首页', children: app.homepage ? <Text copyable>{app.homepage}</Text> : '未填写', span: 2 },
                    { key: 'desc', label: '应用说明', children: app.description || '未填写', span: 2 },
                    { key: 'created', label: '创建时间', children: dateText(app.createdAt) },
                    { key: 'updated', label: '最近更新', children: dateText(app.updatedAt) },
                ]}/>
            </Card></Col>
            <Col xs={24} lg={12}><Card size="small" title="人数与权限">
                <Table size="small" rowKey="key" pagination={false} dataSource={[
                    { key: 'users', label: '人员资料', local: localCounts?.users, platform: platformCounts?.users },
                    { key: 'orgs', label: '机构资料', local: localCounts?.orgs, platform: platformCounts?.orgs },
                    { key: 'roles', label: '应用角色', local: localCounts?.roles, platform: roles.length },
                    { key: 'resources', label: '资源权限', local: localCounts?.resources, platform: resources.length },
                    { key: 'authorizedUsers', label: '已授权用户', local: localCounts?.authorizedUsers, platform: grantsAvailable ? users.length : undefined },
                ]} columns={[
                    { title: '类别', dataIndex: 'label' },
                    { title: '租户系统', dataIndex: 'local', align: 'center', render: value => value ?? '—' },
                    { title: '统一身份平台', dataIndex: 'platform', align: 'center', render: value => value ?? '—' },
                ]}/>
                <div className="application-overview-login"><BadgeLine text={app.authMode === 'FEDERATED' ? '登录方式：统一认证' : '登录方式：应用本地账号'}/></div>
            </Card></Col>
        </Row>{session.realm === 'platform' && <ApplicationRuntimeConnection app={app}/>}</div> },
        ...(session.capabilities?.includes('assignments') ? [
            { key: 'subjects', label: localCounts ? `人员 (${localCounts.users})` : '人员', children: <>{localPanel('users')}<Card size="small" className="application-directory-card" title="平台人员资料分配"><ApplicationAssignments key={`${app.id}:subject`} appId={app.id} domain={domain} kind="subject"/></Card></> },
            { key: 'orgs', label: localCounts ? `机构 (${localCounts.orgs})` : '机构', children: <>{localPanel('orgs')}<Card size="small" className="application-directory-card" title="平台机构资料分配"><ApplicationAssignments key={`${app.id}:org`} appId={app.id} domain={domain} kind="org"/></Card></> },
        ] : []),
        { key: 'roles', label: `角色 (${localCounts?.roles ?? roles.length})`, children: <>{localPanel('roles')}<DataTable data={roles} title="平台应用角色" searchFields={['name', 'code']} actions={<Button type="primary" disabled={!roleEditable} icon={<PlusOutlined />} onClick={() => { setEditRole(null); setRoleOpen(true); }}>新建角色</Button>} columns={[{ title: '角色名称', dataIndex: 'name' }, { title: '角色编码', dataIndex: 'code' }, { title: '资源数', align: 'right', render: (_, r) => r.resourceIds.length }, { title: '状态', dataIndex: 'status', render: v => <StatusTag value={v}/> }, { title: '操作', render: (_, r) => <Space><Button type="link" disabled={!roleEditable} onClick={() => { setEditRole(r); setRoleOpen(true); }}>编辑</Button><ConfirmDelete target={r.name} disabled={!roleEditable} onConfirm={() => api.remove('roles', r.id)}/></Space> }]}/></> },
        { key: 'resources', label: `资源 (${localCounts?.resources ?? resources.length})`, children: <>{localPanel('resources')}<DataTable data={resources} title="平台应用资源" searchFields={['name', 'code', 'path']} actions={<Button type="primary" icon={<PlusOutlined />} disabled={!resourceEditable} onClick={() => { setEditRes(null); setResOpen(true); }}>新建资源</Button>} columns={[{ title: '资源名称', dataIndex: 'name' }, { title: '权限标识', dataIndex: 'code' }, { title: '类型', dataIndex: 'type', render: v => <Tag>{({ menu: '菜单', button: '按钮', api: 'API' } as Record<string, string>)[v]}</Tag> }, { title: '上级资源', dataIndex: 'parentId', render: v => resources.find(r => r.id === v)?.name || '根节点' }, { title: '状态', dataIndex: 'status', render: v => <StatusTag value={v}/> }, { title: '操作', render: (_, r) => <Space><Button type="link" disabled={!resourceEditable} onClick={() => { setEditRes(r); setResOpen(true); }}>编辑</Button><ConfirmDelete target={r.name} disabled={!resourceEditable} onConfirm={() => api.remove('resources', r.id)}/></Space> }]}/></> },
        { key: 'users', label: localCounts ? `授权用户 (${localCounts.authorizedUsers})` : grantsAvailable ? `授权用户 (${users.length})` : '授权用户', children: <>{localPanel('authorizedUsers')}<Alert title="平台有效授权单独统计；停用、锁定或到期的授权不计入。" type="info" showIcon style={{ marginBottom: 16 }}/><DataTable data={users} searchFields={['name', 'account']} title="平台有效授权用户" actions={<Button type="primary" disabled={!grantsAvailable || !(session.appPermissions?.[app.id] || []).includes('grants:write')} onClick={() => nav(`/console/${domain}/grants/apps?app=${app.id}`)}>配置应用授权</Button>} columns={[{ title: '用户姓名', dataIndex: 'name', render: (_, u) => <Space><UserAvatar user={u}/><Text strong>{u.name}</Text></Space> }, { title: '账号', dataIndex: 'account' }, { title: '应用角色', render: (_, u) => effectiveAccess(db, u.id).find(p => p.appId === app.id)?.roleIds.map(id => <Tag key={id}>{roles.find(r => r.id === id)?.name}</Tag>) }, { title: '授权来源', render: (_, u) => effectiveAccess(db, u.id).find(p => p.appId === app.id)?.sources.map(s => <Tag key={s.grantId}>{s.label}</Tag>) }]}/></> },
        ...(session.realm === 'platform' ? [{ key: 'directory-sync', label: '资料同步', children: <ApplicationDirectorySync app={app} domain={domain} canSync={(session.appPermissions?.[app.id] || []).includes('sync:write') && (session.appPermissions?.[app.id] || []).includes('sync:execute')} canPushConfig={(session.appPermissions?.[app.id] || []).includes('sync:write')} canImport={(session.appPermissions?.[app.id] || []).includes('users:write') && (session.appPermissions?.[app.id] || []).includes('orgs:write')}/> }] : []),
        ...(session.capabilities?.includes('clients') ? [{ key: 'connection', label: '接入配置', children: <AuthClients appId={app.id} writable={(session.appPermissions?.[app.id] || []).includes('clients:write')}/> }] : []),
    ];
    return <><Button type="text" icon={<ArrowLeftOutlined />} onClick={() => nav(`/console/${domain}/apps`)} style={{ paddingLeft: 0, marginBottom: 8 }}>返回应用列表</Button><div className="application-detail-hero"><AppIcon app={app} size={48}/><div><h1>{app.name} <StatusTag value={app.status}/></h1><p>{app.description}</p><Space wrap><Tag color="blue">{app.group}</Tag><Tag>{app.protocol}</Tag><Text type="secondary">{app.code}</Text></Space></div></div><Tabs size="small" destroyOnHidden activeKey={tab} onChange={setTab} items={items}/>
 <RecordEditor open={roleOpen} title={editRole ? '编辑角色' : '新建角色'} initial={roleDefault as unknown as FormValues} onClose={() => setRoleOpen(false)} fields={[{ name: 'name', label: '角色名称', required: true }, { name: 'code', label: '角色编码', required: true }, { name: 'description', label: '角色说明', type: 'textarea' }, { name: 'resourceIds', label: '资源权限', type: 'multi', options: options(resources) }, { name: 'status', label: '角色状态', type: 'select', options: [{ value: 'enabled', label: '启用' }, { value: 'disabled', label: '停用' }] }]} onSave={v => api.save('roles', { ...roleDefault, ...v } as Role, '维护应用角色')}/>
 <RecordEditor open={resOpen} title={editRes ? '编辑资源' : '新建资源'} initial={resourceDefault as unknown as FormValues} onClose={() => setResOpen(false)} fields={[{ name: 'name', label: '资源名称', required: true }, { name: 'code', label: '权限标识', required: true, help: '例如：records:view，必须在当前应用内唯一。' }, { name: 'type', label: '资源类型', required: true, type: 'select', options: [{ value: 'menu', label: '菜单' }, { value: 'button', label: '按钮' }, { value: 'api', label: 'API' }] }, { name: 'parentId', label: '上级资源', type: 'select', options: options(resources.filter(r => r.id !== editRes?.id)) }, { name: 'path', label: '路径' }, { name: 'method', label: 'HTTP方法', type: 'select', options: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map(v => ({ value: v, label: v })) }, { name: 'status', label: '状态', type: 'select', options: [{ value: 'enabled', label: '启用' }, { value: 'disabled', label: '停用' }] }]} onSave={v => api.save('resources', { ...resourceDefault, ...v, parentId: v.parentId || null } as Resource, '维护应用资源')}/>
 </>;
}
function BadgeLine({ text }: {
    text: string;
}) { return <Space style={{ marginBottom: 12 }}><CheckCircleOutlined style={{ color: 'var(--iam-success)' }}/><Text>{text}</Text></Space>; }
