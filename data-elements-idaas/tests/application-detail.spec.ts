import { expect, test, type Page } from '@playwright/test';
import { installConsoleContract, login } from './helpers/backend-contract';

test.use({ trace: 'off' });

async function installApplicationContract(page: Page, options: { writable?: boolean; denyOnce?: boolean } = {}) {
    const { session } = await installConsoleContract(page);
    session.capabilities!.push('clients', 'assignments');
    session.appAssignableIds = options.writable === false ? [] : ['a1'];
    session.appBindIds = options.writable === false ? [] : ['a1'];
    const saved: Array<Record<string, any>> = [];
    const rows = {
        subjects: [{ id: 'subject-assignment', subject_id: 'u1', name: '应用人员甲', account_alias: 'person.one', directory_assigned: 1, assignment_mode: 'DIRECTORY_ONLY', desired_version: 1, ack_version: 0, sync_status: 'NOT_CONNECTED', version: 1 }],
        orgs: [{ id: 'org-assignment', org_id: 'o1', name: '应用机构甲', include_children: 1, status: 'ACTIVE', version: 1 }],
    };
    const localInventory = {
        available: true, source: 'TENANT_LOCAL',
        counts: { users: 2, orgs: 1, roles: 1, resources: 1, authorizedUsers: 1 },
        users: [
            { id: 'local-u1', name: '本地人员甲', account: 'local.one', status: 'enabled', orgNames: ['本地机构甲'], roleNames: ['本地管理员'], authorized: true },
            { id: 'local-u2', name: '本地人员乙', account: 'local.two', status: 'enabled', orgNames: [], roleNames: [], authorized: false },
        ],
        orgs: [{ id: 'local-o1', name: '本地机构甲', code: 'GA-01', parentName: '根机构', status: 'enabled' }],
        roles: [{ id: 'local-r1', name: '本地管理员', code: 'ADMIN', status: 'enabled', userCount: 1, resourceCount: 1 }],
        resources: [{ id: 'local-m1', name: '本地用户菜单', code: 'USER_MENU', path: '/users', status: 'enabled' }],
        authorizedUsers: [{ id: 'local-u1', name: '本地人员甲', account: 'local.one', status: 'enabled', orgNames: ['本地机构甲'], roleNames: ['本地管理员'], authorized: true }],
    };
    await page.route('**/api/idaas/applications/local-inventory**', async route => {
        expect(route.request().url()).toContain('local-inventory');
        await route.fulfill({ json: { code: 0, data: localInventory } });
    });
    let deny = options.denyOnce;
    await page.route('**/api/idaas/assignments/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        if (url.pathname.endsWith('/list')) {
            expect(url.searchParams.get('appId')).toBe('a1');
            expect(url.searchParams.get('domain')).toBe('workforce');
            if (deny) {
                return route.fulfill({ json: { code: 503, msg: '应用资料暂时无法读取', data: null } });
            }
            return route.fulfill({ json: { code: 0, data: rows } });
        }
        saved.push(request.postDataJSON());
        await route.fulfill({ json: { code: 0, data: { id: 'saved-assignment' } } });
    });
    return { saved, allow: () => { deny = false; } };
}

for (const width of [390, 1440, 1920]) test(`应用详情人员与机构独立页签 ${width}px`, async ({ page }, info) => {
    test.setTimeout(90000);
    await installApplicationContract(page);
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    await login(page);
    await page.goto('/#/console/workforce/apps/a1');
    await expect(page.getByRole('tab', { name: /^角色/ })).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole('tab', { name: '人员 (2)' })).toBeVisible({ timeout: 20000 });
    const tabs = page.getByRole('tab');
    const names = await tabs.allTextContents();
    await expect(page.getByRole('tab', { name: '客户端', exact: true })).toHaveCount(0);
    await expect(page.getByRole('tab', { name: '认证设置', exact: true })).toHaveCount(0);
    await expect(page.getByRole('tab', { name: '接入配置', exact: true })).toHaveCount(1);
    expect(names.at(-1)).toBe('接入配置');
    const roles = names.findIndex(name => name.startsWith('角色'));
    expect(names.slice(roles - 2, roles)).toEqual(['人员 (2)', '机构 (1)']);
    await expect(page.getByRole('tab', { name: '运行绑定', exact: true })).toHaveCount(0);
    await expect(page.getByRole('tab', { name: '人员与机构资料', exact: true })).toHaveCount(0);
    await page.getByRole('tab', { name: '概览', exact: true }).click();
    await expect(page.getByText('租户库标识', { exact: false })).toHaveCount(0);
    await expect(page.getByText('见资料分配', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('row', { name: /人员资料/ }).locator('td').nth(2)).toHaveText('1');
    await page.getByText('应用连接信息', { exact: true }).click();
    await expect(page.getByText('租户库标识', { exact: false })).toBeVisible();
    await expect(page.getByText('关联已部署的行业系统', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /检查关联|检查并保存关联/ })).toBeEnabled();
    await page.screenshot({ path: info.outputPath(`application-connection-${width}.png`), fullPage: true });
    await page.getByRole('tab', { name: /^人员/ }).click();
    await expect(page.getByRole('tab', { name: /^人员/ })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByText('应用人员甲', { exact: true })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('本地人员甲', { exact: true })).toBeVisible();
    await expect(page.getByText('数据来源：应用租户库', { exact: true })).toHaveCount(0);
    await expect(page.getByText('资料分配与访问授权分别管理；接收方确认后才更新同步状态。', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '刷新列表' }).first()).toBeVisible();
    await expect(page.getByRole('columnheader', { name: '资料分配' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: '平台授权' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: '期望/确认版本' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '核对到控制库', exact: true })).toHaveCount(0);
    await expect(page.getByText('平台人员资料分配', { exact: true })).toBeVisible();
    await expect(page.getByText('应用机构甲', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '分配人员', exact: true })).toBeEnabled();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: info.outputPath(`application-persons-${width}.png`), fullPage: true });
    await page.getByRole('tab', { name: /^机构/ }).click();
    await expect(page.getByText('应用机构甲', { exact: true })).toBeVisible();
    await expect(page.getByText('本地机构甲', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('平台机构资料分配', { exact: true })).toBeVisible();
    await expect(page.getByText('应用人员甲', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '分配机构', exact: true })).toBeEnabled();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: info.outputPath(`application-orgs-${width}.png`), fullPage: true });
    await page.getByRole('tab', { name: '角色 (1)' }).click();
    await expect(page.getByRole('tab', { name: '角色 (1)' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByText('本地管理员', { exact: true })).toBeVisible();
    await page.getByRole('tab', { name: '资源 (1)' }).click();
    await expect(page.getByRole('tab', { name: '资源 (1)' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByText('本地用户菜单', { exact: true })).toBeVisible();
    const usersTab = page.getByRole('tab', { name: '授权用户 (1)' });
    await usersTab.click();
    // 窄屏时 Ant Tabs 首次点击会先把视口外的页签滚入可见区。
    if (width === 390 && await usersTab.getAttribute('aria-selected') !== 'true') await usersTab.click();
    await expect(usersTab).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByText('本地人员甲', { exact: true })).toBeVisible();
    await page.getByRole('tab', { name: '接入配置', exact: true }).click();
    await expect(page.getByText('当前应用使用独立本地账号', { exact: true })).toHaveCount(0);
    await expect(page.getByText('客户端登记与认证接入分别管理', { exact: true })).toHaveCount(0);
});

test('人员与机构编辑提交各自对象类型和原始标识', async ({ page }) => {
    const { saved } = await installApplicationContract(page);
    await login(page);
    await page.goto('/#/console/workforce/apps/a1');
    await page.getByRole('tab', { name: /^人员/ }).click();
    await page.getByRole('row').filter({ hasText: '应用人员甲' }).getByRole('button', { name: '编辑', exact: true }).click();
    await expect(page.getByLabel('平台人员', { exact: true })).toBeDisabled();
    await page.getByLabel('账号', { exact: true }).fill('person.updated');
    await page.getByRole('dialog').getByRole('button', { name: /保\s*存/ }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.getByRole('tab', { name: /^机构/ }).click();
    await page.getByRole('row').filter({ hasText: '应用机构甲' }).getByRole('button', { name: '编辑', exact: true }).click();
    await expect(page.getByLabel('平台机构', { exact: true })).toBeDisabled();
    await page.getByLabel('包含下级机构', { exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: /保\s*存/ }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(saved.map(body => body.record)).toMatchObject([
        { id: 'subject-assignment', version: 1, appId: 'a1', domain: 'workforce', type: 'subject', subjectId: 'u1', accountAlias: 'person.updated' },
        { id: 'org-assignment', version: 1, appId: 'a1', domain: 'workforce', type: 'org', orgId: 'o1', includeChildren: false },
    ]);
});

test('只读应用仍可看两类资料，分配与连接核验保持禁用', async ({ page }) => {
    await installApplicationContract(page, { writable: false });
    await login(page);
    await page.goto('/#/console/workforce/apps/a1');
    await page.getByRole('tab', { name: /^人员/ }).click();
    await expect(page.getByText('应用人员甲', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '分配人员', exact: true })).toBeDisabled();
    await page.getByRole('tab', { name: /^机构/ }).click();
    await expect(page.getByText('应用机构甲', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '分配机构', exact: true })).toBeDisabled();
    await page.getByRole('tab', { name: '概览', exact: true }).click();
    await page.getByText('应用连接信息', { exact: true }).click();
    await expect(page.getByRole('button', { name: /检查关联|检查并保存关联/ })).toBeDisabled();
});

test('应用资料读取失败可刷新重试', async ({ page }) => {
    const { allow } = await installApplicationContract(page, { denyOnce: true });
    await login(page);
    await page.goto('/#/console/workforce/apps/a1');
    await page.getByRole('tab', { name: /^机构/ }).click();
    await expect(page.getByText('应用资料暂时无法读取', { exact: true })).toBeVisible();
    allow();
    await page.getByRole('button', { name: '刷新分配' }).click();
    await expect(page.getByText('应用机构甲', { exact: true })).toBeVisible();
    await expect(page.getByText('应用资料暂时无法读取', { exact: true })).toHaveCount(0);
});

test('应用目录区分本地与平台授权，并在编辑中保存排序和界面配置', async ({ page }) => {
    const { session, database } = await installConsoleContract(page);
    const application = database.apps.find(app => app.domain === 'workforce')!;
    application.portalConfig = {
        logo: `data:image/svg+xml;base64,${Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="#5267f5"/></svg>').toString('base64')}`,
        systemName: '公安元数据管理平台', systemCode: 'PUBLIC_SECURITY', themeColor: '#5267f5',
    };
    application.logoUrl = application.portalConfig.logo;
    session.globalPermissions!.push('apps:write');
    session.editableTables!.push('apps');
    session.appWriteIds = [application.id];
    session.appPermissions = { [application.id]: ['sync:write'] };
    const saves: Array<Record<string, any>> = [];
    const pushes: Array<Record<string, any>> = [];
    await page.route('**/api/idaas/applications/local-inventory**', async route => {
        const params = new URL(route.request().url()).searchParams;
        const item = (appId: string) => ({ appId, available: appId === application.id,
            counts: appId === application.id ? { users: 5, authorizedUsers: 3 } : { users: 0, authorizedUsers: 0 } });
        const data = params.get('summary') === '1'
            ? { items: (params.get('appIds') || '').split(',').filter(Boolean).map(item) }
            : item(params.get('appId') || '');
        await route.fulfill({ json: { code: 0, data } });
    });
    await page.route('**/api/idaas/applications/save', async route => {
        const body = route.request().postDataJSON();
        saves.push(body);
        Object.assign(application, body.record, { version: application.version + 1 });
        application.logoUrl = application.portalConfig?.logo;
        await route.fulfill({ json: { code: 0, data: { id: application.id } } });
    });
    await page.route('**/api/idaas/applications/push-portal-config', async route => {
        pushes.push(route.request().postDataJSON());
        await route.fulfill({ json: { code: 0, data: { synced: true } } });
    });
    await login(page);
    await page.goto('/#/console/workforce/apps');
    await expect(page.locator('.page-title, .app-footer')).toHaveCount(0);
    await expect(page.getByRole('button', { name: '调整顺序' })).toHaveCount(0);
    const card = page.locator('.application-card').filter({ hasText: application.name });
    await expect(card.locator('.application-card-top img')).toBeVisible();
    await expect(card.locator('.app-card-metrics')).toContainText('本地授权用户3');
    await expect(card.locator('.app-card-metrics')).toContainText('本地用户 5');
    await expect(card.locator('.app-card-metrics')).toContainText('平台授权');
    await card.getByRole('button', { name: `${application.name}更多操作` }).click();
    await page.getByText('编辑信息', { exact: true }).click();
    const editor = page.getByRole('dialog', { name: '编辑应用信息' });
    await expect(editor.getByText('显示位置', { exact: true })).toBeVisible();
    await expect(editor.getByText('应用标识', { exact: true })).toBeVisible();
    await editor.getByLabel('租户系统名称').fill('公安数据管理平台');
    await editor.getByRole('button', { name: /保\s*存/ }).click();
    await expect(editor).toHaveCount(0);
    expect(saves.at(-1)?.record.portalConfig.systemName).toBe('公安数据管理平台');
    expect(saves.at(-1)?.record.sortPosition).toBeTruthy();
    await card.getByRole('button', { name: `${application.name}更多操作` }).click();
    await expect(page.getByText('下发界面配置', { exact: true })).toHaveCount(0);
    await page.goto(`/#/console/workforce/apps/${application.id}`);
    await page.getByRole('tab', { name: '资料同步' }).click();
    await page.getByRole('button', { name: '下发界面配置' }).click();
    await page.getByRole('button', { name: '确认下发' }).click();
    await expect.poll(() => pushes.length).toBe(1);
    expect(pushes[0].appId).toBe(application.id);
    expect(pushes[0].requestId).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
});

for (const width of [390, 1440, 1920]) test(`应用目录调整后页面布局 ${width}px`, async ({ page }, info) => {
    const { database } = await installConsoleContract(page);
    const application = database.apps.find(app => app.domain === 'workforce')!;
    application.portalConfig = { logo: `data:image/svg+xml;base64,${Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="#5267f5"/></svg>').toString('base64')}` };
    application.logoUrl = application.portalConfig.logo;
    await page.route('**/api/idaas/applications/local-inventory**', async route => {
        const params = new URL(route.request().url()).searchParams;
        const item = (appId: string) => ({ appId, available: appId === application.id,
            counts: appId === application.id ? { users: 5, authorizedUsers: 3 } : { users: 0, authorizedUsers: 0 } });
        const data = params.get('summary') === '1'
            ? { items: (params.get('appIds') || '').split(',').filter(Boolean).map(item) }
            : item(params.get('appId') || '');
        await route.fulfill({ json: { code: 0, data } });
    });
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    await login(page);
    await page.goto('/#/console/workforce/apps');
    await expect(page.getByText('应用数量', { exact: true })).toBeVisible();
    await expect(page.locator('.application-card').filter({ hasText: application.name }).locator('.app-card-metrics')).toContainText('本地用户 5');
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: info.outputPath(`applications-${width}.png`), fullPage: true });
});
