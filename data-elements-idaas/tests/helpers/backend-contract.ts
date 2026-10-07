import { expect, type Page } from '@playwright/test';
import { createSeed } from '../../src/domain/seed';
import type { Database, EntityTable, Session } from '../../src/domain/types';

/** Browser contract fixtures exercise React over HTTP. They do not certify a live database. */
export async function installContract(page: Page) {
    const database: Database = createSeed();
    const session: Session = { userId: 'u-admin', tenantId: 'tenant-contract', tenantName: '公安', username: 'contract.user', name: '管理人员', role: 'admin', domain: 'workforce', capabilities: ['workspace', 'users', 'orgs', 'apps', 'roles', 'resources', 'audit'], editableTables: ['users', 'orgs', 'apps', 'roles', 'resources'], allowedAppIds: database.apps.filter(app => app.domain === 'workforce').map(app => app.id), orgIds: [], scopeMode: 'tenant' };
    for (const key of ['groups', 'syncConfigs', 'tasks', 'catalog', 'legalEntities'] as const) database[key] = [];
    const saved: Array<Record<string, unknown>> = [];
    const removed: Array<{ id: string; version: number }> = [];
    const receiverConfigs: Array<{ id: string; name: string; appId: string; enabled: boolean; lastTestResult: string; mappingMode?: string }> = [];
    const assignments: Array<{ id: string; subject_id: string; app_id: string; account_alias: string; directory_assigned: number; version: number }> = [];
    const provisionCreates: Array<{ configId: string; requestId: string; kind: string; selectedIds: string[] }> = [];
    const provisionTasks: Array<{ id: string; status: string; items: Array<{ status: string; result?: { message?: string } }> }> = [];
    const initialCredentialRequests: Array<{ taskId: string; subjectId: string; initialPassword: string }> = [];
    const recoveryRequests: Array<{ username: string; realm: string; domain: string }> = [];
    await page.route('**/api/**', async route => {
        const pathname = new URL(route.request().url()).pathname;
        let data: unknown;
        if (pathname.endsWith('/sym/tenant/login-options')) data = [{ tenantId: session.tenantId, tenantName: session.tenantName }];
        else if (pathname.endsWith('/idaas/auth/login')) {
            const body = route.request().postDataJSON();
            expect(body.tenantId).toBeUndefined();
            data = { token: 'browser-contract-token', session };
        } else if (pathname.endsWith('/idaas/session/me')) data = session;
        else if (pathname.endsWith('/idaas/workspace/bootstrap')) data = { session, database, capabilities: session.capabilities };
        else if (pathname.endsWith('/idaas/auth/logout')) data = true;
        else if (pathname.endsWith('/idaas/password-recovery/request')) {
            recoveryRequests.push(route.request().postDataJSON());
            data = { accepted: true, message: '申请已提交' };
        }
        else if (pathname.endsWith('/idaas/users/operations')) data = [];
        else if (pathname.endsWith('/idaas/users/list')) {
            const params = new URL(route.request().url()).searchParams;
            const domain = params.get('domain') || session.domain;
            const query = (params.get('q') || '').trim().toLowerCase();
            const page = Math.max(1, Number(params.get('page') || 1));
            const size = Math.max(1, Number(params.get('size') || 20));
            const matches = database.users.filter(user => user.domain === domain &&
                (!query || [user.name, user.account, user.email].some(value => (value || '').toLowerCase().includes(query))));
            data = { list: matches.slice((page - 1) * size, page * size), total: matches.length, page, size };
        }
        else if (/\/idaas\/(users|orgs|applications|roles|resources|grants)\/save$/.test(pathname)) {
            const body = route.request().postDataJSON();
            const segment = pathname.split('/').at(-2);
            const key = (segment === 'applications' ? 'apps' : segment) as EntityTable;
            if (key === 'grants') {
                const grant = body.record as { subjectId: string; appId: string; roleIds: string[] };
                if (!session.editableTables?.includes('grants') || !database.users.some(user => user.id === grant.subjectId && user.domain === session.domain && user.kind !== 'admin') ||
                    !database.apps.some(app => app.id === grant.appId && app.domain === session.domain && session.allowedAppIds?.includes(app.id)) ||
                    grant.roleIds.some(id => !database.roles.some(role => role.id === id && role.appId === grant.appId)))
                    return route.fulfill({ json: { code: 403, message: '授权对象超出当前应用或人员范围', data: null } });
            }
            saved.push(body);
            const record = { ...body.record, id: body.creating ? `saved-${saved.length}` : body.record.id, version: body.record.version + 1,
                ...(key === 'grants' && !body.record.startsAt ? { startsAt: new Date(Date.now() - 1000).toISOString() } : {}) };
            const list = database[key] as typeof record[];
            const index = list.findIndex(row => row.id === record.id);
            if (index === -1) list.push(record); else list[index] = record;
            if (key === 'apps') session.allowedAppIds!.push(record.id);
            data = { id: record.id, version: record.version };
        } else if (pathname.endsWith('/idaas/assignments/list')) {
            const appId = new URL(route.request().url()).searchParams.get('appId');
            data = { subjects: assignments.filter(item => item.app_id === appId), orgs: [] };
        } else if (pathname.endsWith('/idaas/assignments/save')) {
            const body = route.request().postDataJSON() as { record: { id?: string; subjectId: string; appId: string; accountAlias: string; version?: number } };
            if (!session.appAssignableIds?.includes(body.record.appId) || !database.users.some(item => item.id === body.record.subjectId && item.domain === session.domain))
                return route.fulfill({ json: { code: 403, message: '人员资料分配超出范围', data: null } });
            const existing = assignments.find(item => item.subject_id === body.record.subjectId && item.app_id === body.record.appId);
            if (existing && (!body.record.id || body.record.version !== existing.version))
                return route.fulfill({ json: { code: 409, message: '人员资料分配版本冲突', data: null } });
            if (existing) { existing.directory_assigned = 1; existing.version++; }
            else assignments.push({ id: `assignment-${assignments.length + 1}`, subject_id: body.record.subjectId, app_id: body.record.appId,
                account_alias: body.record.accountAlias, directory_assigned: 1, version: 1 });
            data = { id: existing?.id || assignments.at(-1)!.id };
        } else if (pathname.endsWith('/idaas/provision/options')) data = [{ tenantId: 'tenant-contract', tenantName: '公安' }];
        else if (pathname.endsWith('/idaas/provision/objects')) data = { list: [], total: 0, page: 1, size: 20 };
        else if (pathname.endsWith('/idaas/provision/configs')) data = receiverConfigs;
        else if (pathname.endsWith('/idaas/provision/tasks')) data = provisionTasks;
        else if (pathname.endsWith('/idaas/provision/create')) {
            const body = route.request().postDataJSON() as { configId: string; requestId: string; kind: string; selectedIds: string[] };
            const config = receiverConfigs.find(item => item.id === body.configId && item.enabled && item.lastTestResult === 'SUCCESS');
            if (!config || body.kind !== 'user' || body.selectedIds.length !== 1 || !database.users.some(user => user.id === body.selectedIds[0] && user.domain === session.domain) ||
                !assignments.some(item => item.subject_id === body.selectedIds[0] && item.app_id === config.appId && item.directory_assigned === 1))
                return route.fulfill({ json: { code: 403, message: '下发对象超出当前范围', data: null } });
            provisionCreates.push(body);
            const task = { id: `task-${provisionTasks.length + 1}`, status: 'pending', items: [{ status: 'pending' }] };
            provisionTasks.push(task);
            data = { id: task.id };
        } else if (pathname.endsWith('/idaas/provision/execute')) {
            const body = route.request().postDataJSON() as { id: string };
            const task = provisionTasks.find(item => item.id === body.id);
            if (!task) return route.fulfill({ json: { code: 404, message: '任务不存在', data: null } });
            task.status = 'success'; task.items[0].status = 'success'; data = { status: 'success' };
        } else if (pathname.endsWith('/idaas/provision/set-initial-password')) {
            const body = route.request().postDataJSON() as { taskId: string; subjectId: string; initialPassword: string };
            if (!provisionTasks.some(task => task.id === body.taskId && task.status === 'success') ||
                !database.users.some(user => user.id === body.subjectId) || body.initialPassword.length < 6)
                return route.fulfill({ json: { code: 409, message: '该用户尚未完成下发', data: null } });
            initialCredentialRequests.push(body);
            data = { accountReady: true };
        } else if (pathname.endsWith('/idaas/users/remove')) {
            const body = route.request().postDataJSON() as { id: string; version: number };
            const user = database.users.find(row => row.id === body.id && row.version === body.version && row.domain === session.domain);
            if (!user || user.kind === 'admin' || user.id === session.userId || user.account === session.username || database.grants.some(grant => grant.source === 'user' && grant.subjectId === user.id))
                return route.fulfill({ json: { code: 409, message: '用户仍有关联或不在当前管理范围', data: null } });
            removed.push({ id: body.id, version: body.version });
            database.users = database.users.filter(row => row.id !== body.id);
            data = { deleted: true };
        } else return route.fulfill({ status: 404, json: { code: 404, message: '接口未开通' } });
        await route.fulfill({ json: { code: 200, data } });
    });
    return { database, session, saved, removed, receiverConfigs, assignments, provisionCreates, provisionTasks, initialCredentialRequests, recoveryRequests };
}
export async function login(page: Page) {
    await page.goto('/#/login');
    await page.getByLabel('管理账号').fill('contract.user');
    await page.getByLabel('登录密码').fill('Contract-pass-2026');
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page).toHaveURL(/console\/workforce\/overview/);
    await expect(page.getByText('身份用户总数', { exact: true })).toBeVisible({ timeout: 15000 });
}

/** Unified platform navigation fixture with separate current-domain permissions/data. */
export async function installConsoleContract(page: Page, options: { publicAllowed?: boolean; denyPublicOnce?: boolean; delayPublic?: number } = {}) {
    const database = createSeed();
    const readCodes = ['apps', 'users', 'orgs', 'directory', 'operators', 'platform-roles', 'grants', 'sync', 'audit', 'settings', 'public', 'auth-accounts'].map(key => key + ':read');
    const capabilities = ['workspace', 'profile', 'apps', 'users', 'orgs', 'org-settings', 'delegates', 'grants', 'permission-groups', 'provisioning', 'audit', 'system', 'public-identity', 'auth-accounts'];
    const session: Session = { userId: 'operator-contract', realm: 'platform', username: 'admin', name: '管理人员', role: 'admin', domain: 'workforce', permissions: readCodes, globalPermissions: readCodes, navigationPermissions: { workforce: { permissions: readCodes, globalPermissions: readCodes }, ...(options.publicAllowed === false ? {} : { public: { permissions: readCodes, globalPermissions: readCodes } }) }, capabilities, editableTables: [], allowedAppIds: [], orgIds: [], scopeMode: 'tenant' };
    const transitions: string[] = [];
    let deny = options.denyPublicOnce;
    const bootstrap = () => {
        session.allowedAppIds = database.apps.filter(app => app.domain === session.domain).map(app => app.id);
        const scoped = { ...database, users: database.users.filter(user => user.domain === session.domain), orgs: database.orgs.filter(org => org.domain === session.domain), apps: database.apps.filter(app => app.domain === session.domain), legalEntities: session.domain === 'public' ? database.legalEntities : [] };
        return { session: { ...session }, database: scoped, capabilities };
    };
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        let data: unknown;
        if (path.endsWith('/idaas/auth/public-settings')) data = database.settings.workforce;
        else if (path.endsWith('/idaas/auth/login')) data = { token: 'console-contract-token', session };
        else if (path.endsWith('/idaas/session/me')) data = session;
        else if (path.endsWith('/idaas/workspace/bootstrap')) data = bootstrap();
        else if (path.endsWith('/idaas/session/domain')) {
            const domain = route.request().postDataJSON().domain;
            transitions.push(domain);
            if (domain === 'public' && options.delayPublic) await new Promise(resolve => setTimeout(resolve, options.delayPublic));
            if (domain === 'public' && (options.publicAllowed === false || deny)) {
                deny = false;
                return route.fulfill({ json: { code: 403, msg: '当前账号没有目标身份域的管理范围', data: null } });
            }
            session.domain = domain;
            data = bootstrap();
        } else if (path.endsWith('/idaas/auth/logout')) data = true;
        else if (path.endsWith('/idaas/auth-accounts/list') || path.endsWith('/idaas/users/operations') || path.endsWith('/idaas/verifications/list')) data = [];
        else if (path.endsWith('/idaas/verifications/provider')) data = { status: 'UNCONFIGURED' };
        else if (path.endsWith('/idaas/audit/list')) data = { list: [], total: 0 };
        else return route.fulfill({ json: { code: 404, msg: '接口未开通', data: null } });
        await route.fulfill({ json: { code: 200, data } });
    });
    return { transitions, session, database };
}
