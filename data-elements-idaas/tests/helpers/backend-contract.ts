import { expect, type Page } from '@playwright/test';
import { createSeed } from '../../src/domain/seed';
import type { Database, EntityTable, Session } from '../../src/domain/types';

/** Browser contract fixtures exercise React over HTTP. They do not certify a live database. */
export async function installContract(page: Page) {
    const database: Database = createSeed();
    const session: Session = { userId: 'u-admin', tenantId: 'tenant-contract', tenantName: '公安', username: 'contract.user', name: '管理人员', role: 'admin', domain: 'workforce', capabilities: ['workspace', 'users', 'orgs', 'apps', 'roles', 'resources', 'audit'], editableTables: ['users', 'orgs', 'apps', 'roles', 'resources'], allowedAppIds: database.apps.filter(app => app.domain === 'workforce').map(app => app.id), orgIds: [], scopeMode: 'tenant' };
    for (const key of ['groups', 'syncConfigs', 'tasks', 'catalog', 'legalEntities'] as const) database[key] = [];
    const saved: Array<Record<string, unknown>> = [];
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
        else if (pathname.endsWith('/idaas/users/operations')) data = [];
        else if (/\/idaas\/(users|orgs|applications|roles|resources)\/save$/.test(pathname)) {
            const body = route.request().postDataJSON(); saved.push(body);
            const segment = pathname.split('/').at(-2);
            const key = (segment === 'applications' ? 'apps' : segment) as EntityTable;
            const record = { ...body.record, id: body.creating ? `saved-${saved.length}` : body.record.id, version: body.record.version + 1 };
            const list = database[key] as typeof record[];
            const index = list.findIndex(row => row.id === record.id);
            if (index === -1) list.push(record); else list[index] = record;
            if (key === 'apps') session.allowedAppIds!.push(record.id);
            data = { id: record.id, version: record.version };
        } else return route.fulfill({ status: 404, json: { code: 404, message: '接口未开通' } });
        await route.fulfill({ json: { code: 200, data } });
    });
    return { database, saved };
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
