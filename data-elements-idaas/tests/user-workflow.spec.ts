import { expect, test } from '@playwright/test';
import { installContract, login } from './helpers/backend-contract';

for (const count of [20, 100]) test(`已有应用独立账号可下发，${count}条分配只读取一次`, async ({ page }) => {
    const fixture = await installContract(page);
    fixture.session.capabilities!.push('provisioning', 'assignments');
    fixture.session.editableTables!.push('tasks', 'assignments');
    fixture.session.appAssignableIds = count === 20 ? [] : ['a1'];
    fixture.receiverConfigs.push({ id: 'receiver-a1', name: '业务接收端', appId: 'a1', enabled: true, lastTestResult: 'SUCCESS' });
    const applicationAccount = 'application.user001';
    fixture.assignments.push({ id: 'assignment-existing', subject_id: 'u1', app_id: 'a1',
        account_alias: applicationAccount, directory_assigned: count === 20 ? 1 : 0, ack_version: count === 20 ? 2 : 0, version: 3 });
    for (let index = 1; index < count; index++) fixture.assignments.push({ id: `assignment-${index}`, subject_id: `other-${index}`,
        app_id: 'a1', account_alias: `other.${index}`, directory_assigned: 1, version: 1 });
    fixture.assignments.push({ id: 'other-app', subject_id: 'u1', app_id: 'a2', account_alias: 'different.app', directory_assigned: 1, version: 5 });
    let reads = 0;
    const writes: Array<{ record: { subjectId: string; appId: string; accountAlias: string } }> = [];
    page.on('request', request => {
        if (new URL(request.url()).pathname.endsWith('/idaas/assignments/list')) reads++;
        if (new URL(request.url()).pathname.endsWith('/idaas/assignments/save')) writes.push(request.postDataJSON());
    });
    await login(page);
    await page.goto('/#/console/workforce/organization?user=u1');
    const drawer = page.getByRole('dialog');
    await drawer.getByRole('checkbox', { name: '保存后立即下发此用户' }).check();
    await drawer.getByLabel('下发应用').click();
    await page.locator('.ant-select-dropdown:visible').getByText(fixture.database.apps.find(app => app.id === 'a1')!.name, { exact: true }).click();
    await drawer.getByLabel('接收配置').click();
    await drawer.getByLabel('接收配置').press('ArrowDown');
    await drawer.getByLabel('接收配置').press('Enter');
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(drawer).toHaveCount(0);
    expect(reads).toBe(1);
    expect(writes).toEqual(count === 20 ? [] : [expect.objectContaining({ record: expect.objectContaining({
        subjectId: 'u1', appId: 'a1', accountAlias: applicationAccount,
    }) })]);
    expect(fixture.assignments.find(row => row.id === 'assignment-existing')!.account_alias).toBe(applicationAccount);
    expect(fixture.assignments.find(row => row.id === 'other-app')).toEqual(expect.objectContaining({ account_alias: 'different.app', version: 5 }));
    expect(fixture.provisionCreates).toEqual([expect.objectContaining({ selectedIds: ['u1'] })]);
    expect(fixture.provisionTasks[0].status).toBe('success');
});

test('新建失败后修改账号，更新未确认分配并完成下发，不重复创建人员', async ({ page }) => {
    const fixture = await installContract(page);
    fixture.session.capabilities!.push('provisioning', 'assignments');
    fixture.session.editableTables!.push('tasks', 'assignments');
    fixture.session.appAssignableIds = ['a1'];
    fixture.receiverConfigs.push({ id: 'receiver-a1', name: '业务接收端', appId: 'a1', enabled: true, lastTestResult: 'SUCCESS' });
    let executions = 0;
    await page.route('**/api/idaas/provision/execute', async route => {
        if (executions++ > 0) return route.fallback();
        const task = fixture.provisionTasks[0];
        task.status = 'failed'; task.items[0] = { status: 'failed', result: { message: '账号已存在' } };
        await route.fulfill({ json: { code: 200, data: { status: 'failed' } } });
    });
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await page.getByRole('button', { name: /新建用户$/ }).click();
    const drawer = page.getByRole('dialog');
    await drawer.getByLabel('账号', { exact: true }).fill('draft.old');
    await drawer.getByLabel('姓名', { exact: true }).fill('新用户');
    await drawer.getByRole('checkbox', { name: '保存后立即下发此用户' }).check();
    await drawer.getByLabel('下发应用').click();
    await page.locator('.ant-select-dropdown:visible').getByText(fixture.database.apps.find(app => app.id === 'a1')!.name, { exact: true }).click();
    await drawer.getByLabel('接收配置').click();
    await drawer.getByLabel('接收配置').press('ArrowDown');
    await drawer.getByLabel('接收配置').press('Enter');
    await drawer.getByLabel('初始密码', { exact: true }).fill('ContractUser#2026');
    await drawer.getByLabel('确认初始密码').fill('ContractUser#2026');
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(page.locator('.ant-message-notice-error')).toContainText('账号已存在');
    expect(fixture.assignments[0].account_alias).toBe('draft.old');
    await drawer.getByLabel('账号', { exact: true }).fill('draft.new');
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(drawer).toHaveCount(0);
    const people = fixture.database.users.filter(user => user.account === 'draft.new' || user.account === 'draft.old');
    expect(people).toHaveLength(1);
    expect(people[0].account).toBe('draft.new');
    expect(fixture.assignments).toHaveLength(1);
    expect(fixture.assignments[0]).toEqual(expect.objectContaining({ subject_id: people[0].id, account_alias: 'draft.new' }));
    expect(fixture.provisionCreates.map(row => row.selectedIds)).toEqual([[people[0].id], [people[0].id]]);
    expect(fixture.provisionTasks[1].status).toBe('success');
    expect(fixture.initialCredentialRequests).toHaveLength(1);
});

test('新建用户时预览角色权限、授权并明确下发同一用户', async ({ page }, testInfo) => {
    const fixture = await installContract(page);
    const app = fixture.database.apps.find(item => item.id === 'a1')!;
    fixture.session.capabilities!.push('grants', 'provisioning', 'assignments');
    fixture.session.editableTables!.push('grants', 'tasks', 'assignments');
    fixture.session.appAssignableIds = [app.id];
    fixture.session.globalPermissions = ['grants:write'];
    fixture.receiverConfigs.push({ id: 'receiver-a1', name: '公安元数据管理平台', appId: app.id, enabled: true, lastTestResult: 'SUCCESS' });
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await page.getByRole('button', { name: /新建用户$/ }).click();
    const drawer = page.getByRole('dialog');
    await expect(drawer.getByRole('heading', { name: '基本信息' })).toBeVisible();
    await drawer.getByLabel('账号', { exact: true }).fill('ga.new.person');
    await drawer.getByLabel('姓名', { exact: true }).fill('公安新用户');
    await drawer.getByRole('checkbox', { name: '同时添加应用授权' }).check();
    await drawer.getByLabel('授权应用').fill(app.name);
    await drawer.getByLabel('授权应用').press('Enter');
    await expect(drawer.getByLabel('应用角色')).toBeEnabled();
    await drawer.getByLabel('应用角色').fill('业务查看者');
    await drawer.getByLabel('应用角色').press('Enter');
    const menuGroup = drawer.locator('.user-permission-preview .user-permission-group').filter({ hasText: '菜单' });
    await expect(menuGroup.getByText('工作台', { exact: true })).toBeVisible();
    await expect(drawer.getByText('菜单 · 工作台')).toHaveCount(0);
    await expect(drawer.getByText('填写目标应用拟用账号', { exact: false })).toHaveCount(0);
    await expect(drawer.getByText('组织机构始终保留', { exact: false })).toHaveCount(0);
    await expect(drawer.getByText('下发由本次勾选', { exact: false })).toHaveCount(0);
    await expect(drawer.locator('label.wide .form-required-mark')).toBeVisible();
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(page.locator('.ant-message-notice-error')).toContainText('请填写应用授权原因。');
    await expect(drawer.locator('.ant-alert-error')).toHaveCount(0);
    expect(fixture.saved).toHaveLength(0);
    await page.screenshot({ path: testInfo.outputPath('user-grant-error-toast.png') });
    await drawer.getByLabel('授权原因').fill('新用户开通公安元数据业务');
    await drawer.getByRole('checkbox', { name: '保存后立即下发此用户' }).check();
    await drawer.getByLabel('接收配置').click();
    await drawer.getByLabel('接收配置').press('ArrowDown');
    await drawer.getByLabel('接收配置').press('Enter');
    await drawer.getByLabel('初始密码', { exact: true }).fill('NewUser#2026');
    await drawer.getByLabel('确认初始密码').fill('NewUser#2026');
    await expect(drawer.getByRole('checkbox', { name: '同时添加应用授权' })).toBeChecked();
    await expect(drawer.getByRole('checkbox', { name: '保存后立即下发此用户' })).toBeChecked();
    await expect(drawer.getByText(app.name, { exact: true })).toBeVisible();
    await expect(drawer.getByText('业务查看者', { exact: true })).toBeVisible();
    await drawer.getByRole('heading', { name: '下发到应用' }).scrollIntoViewIfNeeded();
    await page.screenshot({ path: testInfo.outputPath('user-grant-and-dispatch.png'), animations: 'disabled' });
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(drawer).toHaveCount(0, { timeout: 20000 });
    expect(fixture.saved).toHaveLength(2);
    const user = fixture.database.users.find(item => item.account === 'ga.new.person')!;
    const grant = fixture.database.grants.find(item => item.subjectId === user.id && item.appId === app.id)!;
    expect(grant.roleIds).toEqual(['a1-r1']);
    expect(fixture.assignments).toEqual([expect.objectContaining({ subject_id: user.id, app_id: app.id, directory_assigned: 1 })]);
    expect(fixture.provisionCreates).toEqual([expect.objectContaining({ configId: 'receiver-a1', kind: 'user', selectedIds: [user.id] })]);
    expect(fixture.provisionTasks[0].status).toBe('success');
    expect(fixture.initialCredentialRequests).toEqual([expect.objectContaining({
        taskId: fixture.provisionTasks[0].id, subjectId: user.id, initialPassword: 'NewUser#2026',
    })]);
    expect(fixture.saved[0].record).not.toHaveProperty('initialPassword');
});

test('角色权限在手机宽度按菜单、按钮和接口分组展示', async ({ page }, testInfo) => {
    const fixture = await installContract(page);
    await page.setViewportSize({ width: 390, height: 844 });
    fixture.session.capabilities!.push('grants');
    fixture.session.editableTables!.push('grants');
    fixture.session.globalPermissions = ['grants:write'];
    fixture.database.roles.find(role => role.id === 'a1-r2')!.resourceIds.push('a1-res4');
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await page.getByRole('button', { name: /新建用户$/ }).click();
    const drawer = page.getByRole('dialog');
    await drawer.getByRole('checkbox', { name: '同时添加应用授权' }).check();
    await drawer.getByLabel('授权应用').fill('万象数据治理');
    await drawer.getByLabel('授权应用').press('Enter');
    await drawer.getByLabel('应用角色').fill('业务维护者');
    await drawer.getByLabel('应用角色').press('Enter');
    const groups = drawer.locator('.user-permission-preview .user-permission-group');
    await expect(groups).toHaveCount(3);
    await expect(groups.nth(0)).toContainText('菜单');
    await expect(groups.nth(0)).toContainText('工作台');
    await expect(groups.nth(1)).toContainText('按钮');
    await expect(groups.nth(1)).toContainText('新增记录');
    await expect(groups.nth(2)).toContainText('接口');
    await expect(groups.nth(2)).toContainText('导出记录');
    await page.keyboard.press('Escape');
    await groups.nth(2).scrollIntoViewIfNeeded();
    await expect(groups.nth(2)).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath('grouped-permissions-mobile.png'), fullPage: true });
});

test('用户资料可直接在编辑抽屉查看，不出现独立身份档案', async ({ page }) => {
    await installContract(page);
    await login(page);
    await page.goto('/#/console/workforce/organization?user=u1');
    const drawer = page.getByRole('dialog');
    await expect(drawer).toContainText('用户信息');
    await expect(drawer.locator('.user-profile-hero')).toHaveCount(0);
    await expect(drawer.getByLabel('账号', { exact: true })).toHaveValue('user001');
    await expect(page.getByText('身份档案', { exact: true })).toHaveCount(0);
});

test('授权失败时保留用户信息，重试不重复创建用户', async ({ page }) => {
    const fixture = await installContract(page);
    fixture.session.capabilities!.push('grants');
    fixture.session.editableTables!.push('grants');
    fixture.session.globalPermissions = ['grants:write'];
    let attempts = 0;
    await page.route('**/api/idaas/grants/save', async route => {
        attempts++;
        if (attempts === 1) await route.fulfill({ json: { code: 503, message: '授权服务暂不可用', data: null } });
        else await route.fallback();
    });
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await page.getByRole('button', { name: /新建用户$/ }).click();
    const drawer = page.getByRole('dialog');
    await drawer.getByLabel('账号', { exact: true }).fill('retry.person');
    await drawer.getByLabel('姓名', { exact: true }).fill('授权重试用户');
    await drawer.getByRole('checkbox', { name: '同时添加应用授权' }).check();
    await drawer.getByLabel('授权应用').fill('万象数据治理');
    await drawer.getByLabel('授权应用').press('Enter');
    await drawer.getByLabel('应用角色').fill('业务查看者');
    await drawer.getByLabel('应用角色').press('Enter');
    await drawer.getByLabel('授权原因').fill('授权失败后重试');
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(page.locator('.ant-message-notice-error')).toContainText('用户信息已保存，应用授权未完成');
    expect(fixture.saved.filter(item => item.action === '创建用户')).toHaveLength(1);
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(drawer).toHaveCount(0);
    expect(fixture.saved.filter(item => item.action === '创建用户')).toHaveLength(1);
    expect(fixture.saved.filter(item => item.action === '授予应用访问权限')).toHaveLength(1);
});

test('接收端冲突时在页面顶部提示任务的具体原因', async ({ page }) => {
    const fixture = await installContract(page);
    const app = fixture.database.apps.find(item => item.id === 'a1')!;
    fixture.session.capabilities!.push('grants', 'provisioning', 'assignments');
    fixture.session.editableTables!.push('grants', 'tasks', 'assignments');
    fixture.session.appAssignableIds = [app.id];
    fixture.session.globalPermissions = ['grants:write'];
    fixture.receiverConfigs.push({ id: 'receiver-a1', name: '公安元数据管理平台', appId: app.id, enabled: true, lastTestResult: 'SUCCESS' });
    await page.route('**/api/idaas/provision/execute', async route => {
        const task = fixture.provisionTasks.at(-1)!;
        task.status = 'failed';
        task.items[0] = { status: 'failed', result: { message: '接收实例或本地映射不一致' } };
        await route.fulfill({ json: { code: 200, data: { status: 'failed' } } });
    });
    await login(page);
    await page.goto('/#/console/workforce/organization?user=u1');
    const drawer = page.getByRole('dialog');
    await drawer.getByRole('checkbox', { name: '同时添加应用授权' }).check();
    await drawer.getByLabel('授权应用').fill(app.name);
    await drawer.getByLabel('授权应用').press('Enter');
    await drawer.getByLabel('应用角色').fill('业务查看者');
    await drawer.getByLabel('应用角色').press('Enter');
    await drawer.getByLabel('授权原因').fill('测试接收端冲突提示');
    await drawer.getByRole('checkbox', { name: '保存后立即下发此用户' }).check();
    await drawer.getByLabel('接收配置').click();
    await drawer.getByLabel('接收配置').press('ArrowDown');
    await drawer.getByLabel('接收配置').press('Enter');
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(page.getByRole('alert').filter({ hasText: '接收实例或本地映射不一致' })).toBeVisible({ timeout: 30000 });
    await expect(drawer).toBeVisible();
});

test('已有公安目录映射只允许选择人员下发并说明初始密码入口', async ({ page }) => {
    const fixture = await installContract(page);
    fixture.session.capabilities!.push('provisioning');
    fixture.session.editableTables!.push('tasks');
    fixture.receiverConfigs.push({ id: 'receiver-a1', name: '公安元数据管理平台', appId: 'a1',
        enabled: true, lastTestResult: 'SUCCESS', mappingMode: 'EXISTING_LOCAL' });
    await login(page);
    await page.goto('/#/console/workforce/sync/configs');
    await expect(page.getByText('关联已有本地角色')).toBeVisible();
    await page.goto('/#/console/workforce/sync/entities');
    await expect(page.getByText('新建用户时可在“用户信息”中填写初始密码并立即下发', { exact: false })).toBeVisible();
    await expect(page.getByRole('radio', { name: '机构' })).toHaveCount(0);
});
