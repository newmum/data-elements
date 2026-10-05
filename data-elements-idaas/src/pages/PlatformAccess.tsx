import { useEffect, useState } from 'react';
import { Alert, App, Button, Checkbox, Form, Input, Modal, Select, Space, Table, Tag } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { PageTitle } from '../components/common';
import { foundationApi, refreshWorkspace, useDatabase, useSession } from '../mock/store';
import PasswordRecoveryRequests from '../components/PasswordRecoveryRequests';

interface Operator { id: string; username: string; display_name: string; subject_id?: string; subject_version?: number; status: string; version: number; must_change_password: number; locked_until?: string; }
interface Role { id: string; code: string; name: string; description: string; status: string; version: number; resourceIds: string[]; }
interface Resource { id: string; code: string; name: string; }
interface Assignment { role_id: string; valid_from?: string; valid_until?: string; scopes: { scope_kind: string; app_id?: string; org_id?: string; include_children?: number; identity_domain: string }[]; }
function localTime(value?: string) { if (!value) return ''; const d = new Date(value); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
interface ScopeEditor { validFrom?: string; validUntil?: string; roleId: string; scopeKind: 'ALL' | 'APPLICATION' | 'ORG'; appId?: string; orgId?: string; includeChildren?: boolean; }

export default function PlatformAccess({ kind = 'operators' }: { kind?: 'operators' | 'roles' | 'locked' }) {
    const session = useSession()!;
    const db = useDatabase();
    const [operators, setOperators] = useState<Operator[]>([]);
    const [roles, setRoles] = useState<Role[]>([]);
    const [resources, setResources] = useState<Resource[]>([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [editing, setEditing] = useState<Operator | Role | null>(null);
    const [open, setOpen] = useState(false);
    const [resetting, setResetting] = useState<Pick<Operator, 'id' | 'version'> | null>(null);
    const [recoveryVersion, setRecoveryVersion] = useState(0);
    const [assigning, setAssigning] = useState<Operator | null>(null);
    const [scopes, setScopes] = useState<ScopeEditor[]>([]);
    const [form] = Form.useForm();
    const selectedSubjectId = Form.useWatch('subjectId', form);
    const [passwordForm] = Form.useForm();
    const { message, modal } = App.useApp();
    const can = (permission: string) => session.permissions?.includes(permission) === true;
    const rolePage = kind === 'roles';
    const load = async () => {
        setBusy(true); setError('');
        try {
            if (can('operators:read')) setOperators(await foundationApi.list<Operator>('/idaas/operators/list'));
            if (can('platform-roles:read')) {
                setRoles(await foundationApi.list<Role>('/idaas/platform-roles/list'));
                setResources(await foundationApi.list<Resource>('/idaas/platform-roles/resources'));
            }
        } catch (e) { setError(e instanceof Error ? e.message : '读取失败'); }
        finally { setBusy(false); }
    };
    useEffect(() => { void load(); }, [kind]);
    const perform = async (action: () => Promise<unknown>, success: string) => {
        setBusy(true);
        try { await action(); await refreshWorkspace(); message.success(success); await load(); }
        catch (e) { message.error(e instanceof Error ? e.message : '操作失败'); throw e; }
        finally { setBusy(false); }
    };
    const edit = (row: Operator | Role | null) => {
        setEditing(row); form.resetFields();
        form.setFieldsValue(row ? { ...row, displayName: 'display_name' in row ? row.display_name : '' } : { status: 'ACTIVE', resourceIds: [] });
        setOpen(true);
    };
    const save = async () => {
        const values = await form.validateFields();
        await perform(() => foundationApi.write(`/idaas/${rolePage ? 'platform-roles' : 'operators'}/save`, {
            record: { ...values, id: editing?.id, version: editing?.version, subjectVersion: editing && 'subject_version' in editing ? editing.subject_version : undefined }, creating: !editing,
        }), '已保存'); setOpen(false);
    };
    const assign = async (row: Operator) => {
        setBusy(true);
        try {
            const existing = await foundationApi.list<Assignment>(`/idaas/platform-roles/assignments?operatorId=${encodeURIComponent(row.id)}`);
            setScopes(existing.flatMap(a => a.scopes.map(s => ({ roleId: a.role_id, validFrom: localTime(a.valid_from), validUntil: localTime(a.valid_until), scopeKind: s.scope_kind as ScopeEditor['scopeKind'], appId: s.app_id, orgId: s.org_id, includeChildren: s.include_children === 1 }))));
            setAssigning(row);
        } catch (e) { message.error(e instanceof Error ? e.message : '读取角色分配失败'); }
        finally { setBusy(false); }
    };
    const saveAssignments = async () => {
        if (!assigning) return;
        if (scopes.some(s => !s.roleId || (s.scopeKind === 'APPLICATION' && !s.appId) || (s.scopeKind === 'ORG' && !s.orgId))) { message.error('请完整选择角色和范围'); return; }
        const assignments = [...new Set(scopes.map(s => s.roleId))].map(roleId => ({ roleId, validFrom: scopes.find(s => s.roleId === roleId)?.validFrom ? new Date(scopes.find(s => s.roleId === roleId)!.validFrom!).toISOString() : undefined, validUntil: scopes.find(s => s.roleId === roleId)?.validUntil ? new Date(scopes.find(s => s.roleId === roleId)!.validUntil!).toISOString() : undefined, scopes: scopes.filter(s => s.roleId === roleId)
            .map(s => ({ identityDomain: 'workforce', scopeKind: s.scopeKind, ...(s.scopeKind === 'APPLICATION' ? { appId: s.appId } : s.scopeKind === 'ORG' ? { orgId: s.orgId, includeChildren: s.includeChildren } : {}) })) }));
        await perform(() => foundationApi.write('/idaas/platform-roles/assign', { operatorId: assigning.id, version: assigning.version, assignments }), '角色与管理范围已保存');
        setAssigning(null);
    };
    const operatorRows = kind === 'locked' ? operators.filter(o => o.locked_until && Date.parse(o.locked_until) > Date.now()) : operators;
    return <>
        <PageTitle title={rolePage ? '系统角色' : kind === 'locked' ? '账号解锁' : '管理员管理'}
            description={rolePage ? '维护统一身份管理平台的功能权限；角色分配另行限定管理范围。' : '平台操作账号独立登录统一身份管理平台，应用本地账号由所属应用管理。'}
            extra={<Space><Button icon={<ReloadOutlined />} loading={busy} onClick={() => void load()}>刷新</Button>{kind !== 'locked' && <Button type="primary" icon={<PlusOutlined />} disabled={!can(rolePage ? 'platform-roles:write' : 'operators:write')} onClick={() => edit(null)}>新建{rolePage ? '角色' : '操作账号'}</Button>}</Space>}/>
        {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 16 }}/>} 
        <Alert type="info" showIcon title="功能权限与管理范围共同决定操作资格，保存授权前请核对职责。" style={{ marginBottom: 16 }}/>
        {!rolePage && kind === 'operators' && can('operators:write') && <PasswordRecoveryRequests realm="platform" domain="workforce" refreshKey={recoveryVersion} onReset={request => {
            if (request.accountId === session.userId) { message.error('请由另一名有权限的管理员核验并重置此账号'); return; }
            passwordForm.resetFields(); setResetting({ id: request.accountId, version: request.version });
        }}/>}
        {rolePage ? <Table<Role> size="small" rowKey="id" loading={busy} dataSource={roles} scroll={{ x: 800 }} columns={[
            { title: '角色名称', dataIndex: 'name' }, { title: '编码', dataIndex: 'code' },
            { title: '功能权限', render: (_, row) => <Space wrap>{row.resourceIds.map(id => <Tag key={id}>{resources.find(r => r.id === id)?.code || id}</Tag>)}</Space> },
            { title: '状态', dataIndex: 'status', render: value => value === 'ACTIVE' ? '启用' : '停用' },
            { title: '操作', render: (_, row) => <Button type="link" disabled={!can('platform-roles:write')} onClick={() => edit(row)}>编辑</Button> },
        ]}/> : <Table<Operator> size="small" rowKey="id" loading={busy} dataSource={operatorRows} scroll={{ x: 900 }} columns={[
            { title: '姓名', dataIndex: 'display_name' }, { title: '登录账号', dataIndex: 'username' },
            { title: '状态', dataIndex: 'status', render: value => value === 'ACTIVE' ? '启用' : '停用' },
            { title: '密码状态', render: (_, row) => row.must_change_password ? '登录后需改密' : '正常' },
            { title: '锁定截止', dataIndex: 'locked_until', render: value => value || '—' },
            { title: '操作', width: 290, render: (_, row) => <Space wrap>
                <Button type="link" disabled={!can('operators:write')} onClick={() => edit(row)}>编辑</Button>
                <Button type="link" disabled={!can('operators:grant') || !can('platform-roles:read')} onClick={() => void assign(row)}>角色与范围</Button>
                <Button type="link" disabled={!can('operators:write') || row.id === session.userId} onClick={() => { passwordForm.resetFields(); setResetting(row); }}>重置密码</Button>
                <Button type="link" disabled={!can('operators:write')} onClick={() => modal.confirm({ title: `解除 ${row.username} 的账号锁定？`, okText: '解锁', cancelText: '取消', onOk: () => perform(() => foundationApi.write('/idaas/operators/unlock', { id: row.id, version: row.version }), '账号已解锁') })}>解锁</Button>
            </Space> },
        ]}/>} 
        <Modal title={`${editing ? '编辑' : '新建'}${rolePage ? '平台角色' : '操作账号'}`} open={open} onCancel={() => setOpen(false)} onOk={save} okText="保存" cancelText="取消" confirmLoading={busy}>
            <Form size="small" form={form} layout="vertical">
                {rolePage ? <><Form.Item name="name" label="角色名称" rules={[{ required: true }, { max: 100 }]}><Input/></Form.Item>
                    <Form.Item name="code" label="角色编码" rules={[{ required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,99}$/ }]}><Input disabled={!!editing}/></Form.Item>
                    <Form.Item name="description" label="职责说明"><Input.TextArea rows={3}/></Form.Item>
                    <Form.Item name="resourceIds" label="平台功能权限"><Select mode="multiple" options={resources.map(r => ({ value: r.id, label: r.code }))}/></Form.Item></> : <>
                    <Form.Item name="displayName" label="姓名" rules={[{ required: true }, { max: 100 }]}><Input disabled={!!selectedSubjectId}/></Form.Item>
                    {!editing && <Form.Item name="subjectId" label="关联已有人员" extra="选择已有人员后，姓名以人员主档为准；未选择时该账号仅保存平台显示名。"><Select allowClear showSearch optionFilterProp="label" options={db.users.filter(user => user.domain === 'workforce' && user.status === 'enabled').map(user => ({ value: user.id, label: `${user.name} · ${user.subjectCode || user.account}` }))} onChange={value => { const user = db.users.find(item => item.id === value); if (user) form.setFieldValue('displayName', user.name); }}/></Form.Item>}
                    <Form.Item name="username" label="登录账号" rules={[{ required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_.-]{2,49}$/ }]}><Input disabled={!!editing} autoComplete="off"/></Form.Item>
                    {!editing && <Form.Item name="password" label="初始密码" rules={[{ required: true }, { min: db.settings.workforce.minLength || 8 }]} extra="新账号首次登录后需要修改密码。"><Input.Password autoComplete="new-password"/></Form.Item>}</>}
                <Form.Item name="status" label="状态"><Select options={[{ value: 'ACTIVE', label: '启用' }, { value: 'DISABLED', label: '停用' }]}/></Form.Item>
            </Form>
        </Modal>
        <Modal title="重置平台密码" open={!!resetting} onCancel={() => setResetting(null)} okText="重置密码" cancelText="取消" confirmLoading={busy} onOk={async () => {
            const values = await passwordForm.validateFields(); if (!resetting) return;
            await perform(() => foundationApi.write('/idaas/operators/reset-password', { id: resetting.id, version: resetting.version, password: values.password }), '密码已重置，原会话已失效'); setResetting(null); setRecoveryVersion(value => value + 1);
        }}><Form size="small" form={passwordForm} layout="vertical"><Form.Item name="password" label="新密码" rules={[{ required: true }, { min: db.settings.workforce.minLength || 8 }]}><Input.Password autoComplete="new-password"/></Form.Item></Form></Modal>
        <Modal title={`${assigning?.display_name || ''} · 角色与管理范围`} open={!!assigning} onCancel={() => setAssigning(null)} onOk={saveAssignments} okText="保存授权" cancelText="取消" confirmLoading={busy} width={736}>
            <Alert type="info" showIcon title="每个角色分别限定范围及有效期；留空表示立即生效、长期有效。机构范围限定中央资料维护，应用范围限定所属应用。" style={{ marginBottom: 16 }}/>
            {scopes.map((s, index) => <Space key={index} wrap style={{ marginBottom: 12 }}>
                <Select aria-label={`第${index + 1}项平台角色`} style={{ width: 180 }} value={s.roleId} options={roles.filter(r => r.status === 'ACTIVE').map(r => ({ value: r.id, label: r.name }))} onChange={roleId => setScopes(scopes.map((v, i) => i === index ? { ...v, roleId } : v))}/>
                <Select aria-label={`第${index + 1}项管理范围`} style={{ width: 130 }} value={s.scopeKind} options={[{ value: 'ALL', label: '全部对象' }, { value: 'APPLICATION', label: '指定应用' }, { value: 'ORG', label: '指定机构' }]} onChange={scopeKind => setScopes(scopes.map((v, i) => i === index ? { ...v, scopeKind, appId: undefined, orgId: undefined } : v))}/>
                {s.scopeKind === 'APPLICATION' && <Select aria-label={`第${index + 1}项应用`} style={{ width: 190 }} value={s.appId} options={db.apps.map(a => ({ value: a.id, label: a.name }))} onChange={appId => setScopes(scopes.map((v, i) => i === index ? { ...v, appId } : v))}/>}
                {s.scopeKind === 'ORG' && <><Select aria-label={`第${index + 1}项机构`} style={{ width: 190 }} value={s.orgId} options={db.orgs.filter(o => o.status === 'enabled').map(o => ({ value: o.id, label: o.name }))} onChange={orgId => setScopes(scopes.map((v, i) => i === index ? { ...v, orgId } : v))}/><Checkbox checked={s.includeChildren} onChange={e => setScopes(scopes.map((v, i) => i === index ? { ...v, includeChildren: e.target.checked } : v))}>包含下级</Checkbox></>}
                <label>生效时间<Input aria-label={`第${index + 1}项生效时间`} type="datetime-local" value={s.validFrom || ''} onChange={e => setScopes(scopes.map(v => v.roleId === s.roleId ? { ...v, validFrom: e.target.value } : v))}/></label>
                <label>到期时间<Input aria-label={`第${index + 1}项到期时间`} type="datetime-local" value={s.validUntil || ''} onChange={e => setScopes(scopes.map(v => v.roleId === s.roleId ? { ...v, validUntil: e.target.value } : v))}/></label>
                <Button danger onClick={() => setScopes(scopes.filter((_, i) => i !== index))}>移除</Button>
            </Space>)}
            <Button onClick={() => setScopes([...scopes, { roleId: '', scopeKind: 'APPLICATION' }])}>添加角色范围</Button>
        </Modal>
    </>;
}
