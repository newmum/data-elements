import { useEffect, useRef, useState } from 'react';
import { Alert, App, Checkbox, Empty, Input, Select, Tag } from 'antd';
import { AppstoreOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { api, foundationApi, getDatabase, provisionApi, useDatabase, useSession, type ReceiverConfig } from '../mock/store';
import { effectiveAccess, makeBase } from '../domain/engine';
import type { Domain, Grant, Resource, User } from '../domain/types';
import { RecordEditor, type Field, type FormValues } from './common';
import { extensionInitial, extensionValues } from './directory-fields';

interface Progress {
    userId: string;
    userHash: string;
    userVersion: number;
    savedUser: User;
    grantKey: string;
    grantDraftKey: string;
    grantDraft: Grant | null;
    assignmentKey: string;
    taskId: string;
    taskKey: string;
    dispatchKey: string;
    requestId: string;
    credentialDoneFor: string;
}

const permissionKinds = [
    { type: 'menu', label: '菜单' },
    { type: 'button', label: '按钮' },
    { type: 'api', label: '接口' },
] as const;

function PermissionGroups({ resources }: { resources: Resource[] }) {
    return <div className="user-permission-groups">{permissionKinds.map(kind => {
        const items = resources.filter(resource => resource.type === kind.type);
        return items.length ? <div className="user-permission-group" key={kind.type}>
            <span className="user-permission-group-label">{kind.label}</span>
            <div className="user-permission-group-items">{items.map(resource => <Tag key={resource.id}>{resource.name}</Tag>)}</div>
        </div> : null;
    })}</div>;
}

export default function UserEditor({ open, user, initial, fields, domain, onClose, readOnly = false }: {
    open: boolean;
    user: User | null;
    initial: User;
    fields: Field[];
    domain: Domain;
    onClose: () => void;
    readOnly?: boolean;
}) {
    const db = useDatabase();
    const session = useSession();
    const { message } = App.useApp();
    const [appId, setAppId] = useState('');
    const [roleIds, setRoleIds] = useState<string[]>([]);
    const [grantEnabled, setGrantEnabled] = useState(false);
    const [reason, setReason] = useState('');
    const [dispatch, setDispatch] = useState(false);
    const [configId, setConfigId] = useState('');
    const [initialPassword, setInitialPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [configs, setConfigs] = useState<ReceiverConfig[]>([]);
    const progress = useRef<Progress | null>(null);
    const draftId = useRef('');
    const canViewGrants = session?.capabilities?.includes('grants') === true;
    const canGrant = !readOnly && canViewGrants && session?.editableTables?.includes('grants');
    const canDispatch = !readOnly && session?.capabilities?.includes('provisioning') && session.editableTables?.includes('tasks') &&
        session.capabilities.includes('assignments') && session.editableTables.includes('assignments');
    const apps = db.apps.filter(app => app.domain === domain && app.status === 'enabled' &&
        (!session?.allowedAppIds?.length || session.allowedAppIds.includes(app.id)));
    const app = apps.find(item => item.id === appId);
    const roles = db.roles.filter(role => role.appId === appId && role.status === 'enabled');
    const resources = db.resources.filter(resource => resource.appId === appId && resource.status === 'enabled' &&
        roles.some(role => roleIds.includes(role.id) && role.resourceIds.includes(resource.id)));
    const readyConfigs = configs.filter(config => config.appId === appId && config.enabled && config.lastTestResult === 'SUCCESS');
    const access = user && canViewGrants ? effectiveAccess(db, user.id) : [];
    const grantAllowed = Boolean(canGrant && app && ((session?.appPermissions?.[app.id] || []).includes('grants:write') ||
        (session?.globalPermissions || []).includes('grants:write') || (session?.permissions || []).includes('grants:write')));

    useEffect(() => {
        if (!open) return;
        draftId.current = crypto.randomUUID().replaceAll('-', '');
        progress.current = null;
        setAppId(''); setRoleIds([]); setGrantEnabled(false); setReason('');
        setDispatch(false); setConfigId(''); setConfigs([]);
        setInitialPassword(''); setConfirmPassword('');
        if (!canDispatch) return;
        let active = true;
        void provisionApi.configs().then(value => { if (active) setConfigs(value); })
            .catch(error => { if (active) message.error(error instanceof Error ? error.message : '无法读取接收配置'); });
        return () => { active = false; };
    }, [open, user?.id, canDispatch, message]);

    const save = async (values: FormValues) => {
        if (grantEnabled && !app) throw new Error('请选择授权应用。');
        if (grantEnabled && !grantAllowed) throw new Error('当前账号没有该应用的授权权限，请选择可管理的应用。');
        if (grantEnabled && !reason.trim()) throw new Error('请填写应用授权原因。');
        if (grantEnabled && roleIds.some(id => !roles.some(role => role.id === id))) throw new Error('所选角色已失效，请重新选择。');
        if (dispatch && (!canDispatch || !readyConfigs.some(config => config.id === configId))) throw new Error('请先选择已启用且测试通过的应用接收配置。');
        if (dispatch && !user && progress.current?.credentialDoneFor !== configId) {
            if (initialPassword.length < 6 || initialPassword.length > 128) throw new Error('初始密码应为6至128个字符。');
            if (initialPassword !== confirmPassword) throw new Error('两次填写的初始密码不一致。');
        }
        const { additionalOrgIds, ...rest } = values;
        const orgId = String(values.orgId || '');
        const post = String(values.post || '');
        const appointments = [{ orgId, post, primary: true }, ...((additionalOrgIds as string[] | undefined) || [])
            .filter(id => id !== orgId).map(id => ({ orgId: id, post, primary: false }))];
        const userFields = { ...rest, appointments, ext: extensionValues(db.catalog, 'SUBJECT', values) };
        const userHash = JSON.stringify(userFields);
        let state = progress.current;
        if (!state || state.userHash !== userHash) {
            const previous = state?.userId ? getDatabase().users.find(row => row.id === state!.userId) : null;
            if (state && previous && previous.version !== state.userVersion) throw new Error('用户资料已被其他操作更新，请关闭后重新打开核对。');
            const base = previous || state?.savedUser || user || { ...initial, id: draftId.current };
            const record = { ...base, ...userFields } as User;
            const id = await api.save('users', record, user ? '编辑用户' : '创建用户');
            const saved = getDatabase().users.find(row => row.id === id) || { ...record, id, version: record.version + 1 };
            state = { userId: id, userHash, userVersion: saved.version, savedUser: saved,
                grantKey: '', grantDraftKey: '', grantDraft: null, assignmentKey: '', taskId: '', taskKey: '', dispatchKey: '', requestId: '', credentialDoneFor: '' };
            progress.current = state;
        }
        if (grantEnabled) {
            const grantKey = JSON.stringify({ userId: state.userId, appId, roleIds: [...roleIds].sort(), reason: reason.trim() });
            if (state.grantKey !== grantKey) {
                const existing = getDatabase().grants.some(grant => grant.source === 'user' && grant.subjectId === state!.userId &&
                    grant.appId === appId && grant.status === 'enabled' &&
                    grant.roleIds.slice().sort().join(',') === roleIds.slice().sort().join(','));
                if (!existing) {
                    if (state.grantDraftKey !== grantKey || !state.grantDraft) {
                        state.grantDraft = { ...makeBase('应用访问授权', domain, 'grant'), source: 'user', subjectId: state.userId,
                            appId, roleIds, includeChildren: false, startsAt: '', expiresAt: null, reason: reason.trim() };
                        state.grantDraftKey = grantKey;
                    }
                    try { await api.save('grants', state.grantDraft, '授予应用访问权限'); }
                    catch (error) { throw new Error(`用户信息已保存，应用授权未完成：${error instanceof Error ? error.message : '请重试'}`); }
                }
                state.grantKey = grantKey;
                state.grantDraft = null;
            }
        }
        if (dispatch) {
            const assignmentKey = JSON.stringify({ userId: state.userId, appId, account: state.savedUser.account });
            if (state.assignmentKey !== assignmentKey) {
                try {
                    const listed = await foundationApi.read<{ subjects: Array<{ id: string; subject_id: string; account_alias?: string; directory_assigned?: number; version: number }> }>(
                        `/idaas/assignments/list?appId=${encodeURIComponent(appId)}&domain=${domain}`);
                    const existing = listed.subjects.find(item => item.subject_id === state!.userId);
                    if (existing?.account_alias && existing.account_alias !== state.savedUser.account)
                        throw new Error('该用户在目标应用已有不同账号，请先核对应用资料分配。');
                    if (!existing?.directory_assigned) {
                        if (!session?.appAssignableIds?.includes(appId))
                            throw new Error('当前账号无权为该应用分配人员资料。');
                        await foundationApi.write('/idaas/assignments/save', {
                            record: { id: existing?.id, version: existing?.version, type: 'subject', subjectId: state.userId,
                                appId, domain, accountAlias: state.savedUser.account },
                            creating: !existing, requestId: crypto.randomUUID(),
                        });
                    }
                    state.assignmentKey = assignmentKey;
                } catch (error) { throw new Error(`用户信息和应用授权已保存，应用资料分配未完成：${error instanceof Error ? error.message : '请重试'}`); }
            }
            const dispatchKey = JSON.stringify({ userId: state.userId, configId, grantKey: state.grantKey });
            if (state.dispatchKey !== dispatchKey) {
                try {
                    if (state.taskKey && state.taskKey !== dispatchKey) { state.taskId = ''; state.requestId = ''; }
                    state.taskKey = dispatchKey;
                    if (!state.taskId) {
                        state.requestId ||= crypto.randomUUID();
                        const task = await provisionApi.create(configId, state.requestId, 'user', [state.userId]);
                        state.taskId = task.id;
                    }
                    for (let round = 0; round < 100; round++) {
                        await provisionApi.execute(state.taskId);
                        const task = (await provisionApi.tasks()).find(item => item.id === state!.taskId);
                        if (!task) throw new Error(`任务 ${state.taskId} 已创建，请在同步任务中查看结果。`);
                        if (task.status === 'success') { state.dispatchKey = dispatchKey; break; }
                        if (task.status !== 'pending') {
                            const detail = task.items.find(item => item.status !== 'success' && item.result?.message)?.result?.message;
                            throw new Error(`任务 ${state.taskId} 下发失败${detail ? `：${detail}` : ''}。请在同步任务中处理。`);
                        }
                    }
                    if (state.dispatchKey !== dispatchKey) throw new Error(`任务 ${state.taskId} 仍在处理中，请在同步任务中继续执行。`);
                } catch (error) { throw new Error(`用户信息和应用授权已保存，下发未完成：${error instanceof Error ? error.message : '请重试'}`); }
            }
            if (!user && state.credentialDoneFor !== configId) {
                try {
                    await foundationApi.write('/idaas/provision/set-initial-password', {
                        taskId: state.taskId, subjectId: state.userId, initialPassword,
                    });
                    state.credentialDoneFor = configId;
                    setInitialPassword(''); setConfirmPassword('');
                } catch (error) { throw new Error(`用户资料已下发，初始密码未设置：${error instanceof Error ? error.message : '请重试'}`); }
            }
        }
    };

    return <RecordEditor open={open} title={user ? '用户信息' : '新建用户'} width={840} className="user-editor" formHeading="基本信息" readOnly={readOnly}
        initial={{ ...initial, ...extensionInitial(initial.ext), additionalOrgIds: initial.appointments.filter(item => !item.primary).map(item => item.orgId) } as FormValues}
        fields={fields} onClose={onClose} onSave={save} afterFields={<>
            <section className="user-editor-section">
                <h3><SafetyCertificateOutlined/> 应用角色</h3>
                {user && canViewGrants && <div className="user-existing-access"><strong>当前有效授权</strong>{access.length ? access.map(item =>
                    <details key={item.appId}><summary>{db.apps.find(value => value.id === item.appId)?.name || item.appId} · {item.roleIds.map(id => db.roles.find(role => role.id === id)?.name || id).join('、') || '仅访问应用'}</summary>
                        <div>{item.resourceIds.length ? <PermissionGroups resources={db.resources.filter(resource => item.resourceIds.includes(resource.id))}/> : '暂无对应权限'}</div>
                    </details>) : <span>暂无</span>}</div>}
                {!canGrant ? <Alert type="info" showIcon title="当前账号没有应用授权权限；仍可查看和维护用户资料。"/> : <>
                    <Checkbox checked={grantEnabled} onChange={event => setGrantEnabled(event.target.checked)}>同时添加应用授权</Checkbox>
                    {grantEnabled && <div className="user-editor-access-grid">
                        <label><span><span className="form-required-mark" aria-hidden="true">*</span>应用</span><Select aria-label="授权应用" value={appId || undefined} onChange={value => { setAppId(value); setRoleIds([]); setConfigId(''); }}
                            options={apps.map(item => ({ value: item.id, label: item.name }))} placeholder="选择应用" showSearch optionFilterProp="label"/></label>
                        <label>应用角色<Select aria-label="应用角色" mode="multiple" value={roleIds} onChange={setRoleIds} disabled={!app || !grantAllowed}
                            options={roles.map(item => ({ value: item.id, label: item.name }))} placeholder="选择角色" showSearch optionFilterProp="label"/></label>
                        <label className="wide"><span><span className="form-required-mark" aria-hidden="true">*</span>授权原因</span><Input aria-label="授权原因" aria-required="true" value={reason} onChange={event => setReason(event.target.value)} maxLength={200} placeholder="填写业务授权依据"/></label>
                    </div>}
                    {grantEnabled && app && !grantAllowed && <Alert style={{ marginTop: 14 }} type="warning" showIcon title="当前账号没有该应用的授权权限，请选择可管理的应用。"/>}
                    {grantEnabled && app && <div className="user-permission-preview"><strong>所选角色包含的权限</strong>{roleIds.length ? resources.length ?
                        <PermissionGroups resources={resources}/> :
                        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="所选角色暂无已启用的权限"/> : <span>不选角色时仅授予应用访问资格</span>}</div>}
                </>}
            </section>
            <section className="user-editor-section">
                <h3><AppstoreOutlined/> 下发到应用</h3>
                {!canDispatch ? <Alert type="info" showIcon title="当前账号没有下发权限。"/> : <>
                    <Checkbox checked={dispatch} onChange={event => setDispatch(event.target.checked)}>保存后立即下发此用户</Checkbox>
                    {dispatch && !grantEnabled && <label className="user-editor-config"><span><span className="form-required-mark" aria-hidden="true">*</span>目标应用</span><Select aria-label="下发应用" value={appId || undefined} onChange={value => { setAppId(value); setConfigId(''); }}
                        options={apps.map(item => ({ value: item.id, label: item.name }))} placeholder="选择目标应用"/></label>}
                    {dispatch && <label className="user-editor-config"><span><span className="form-required-mark" aria-hidden="true">*</span>接收配置</span><Select aria-label="接收配置" value={configId || undefined} onChange={setConfigId}
                        options={readyConfigs.map(config => ({ value: config.id, label: config.mappingMode === 'EXISTING_LOCAL' ? `${config.name} · 已映射本地角色` : config.name }))} placeholder="选择已测试并启用的接收配置" notFoundContent="暂无可用接收配置，请先到同步配置测试并启用"/></label>}
                    {dispatch && !user && progress.current?.credentialDoneFor !== configId && <div className="user-editor-access-grid user-editor-password-grid">
                        <label><span><span className="form-required-mark" aria-hidden="true">*</span>初始密码</span><Input.Password aria-label="初始密码" aria-required="true" autoComplete="new-password" maxLength={128} value={initialPassword} onChange={event => setInitialPassword(event.target.value)}/></label>
                        <label><span><span className="form-required-mark" aria-hidden="true">*</span>确认密码</span><Input.Password aria-label="确认初始密码" aria-required="true" autoComplete="new-password" maxLength={128} value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)}/></label>
                    </div>}
                </>}
            </section>
        </>}/>;
}
