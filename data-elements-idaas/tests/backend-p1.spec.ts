import { test, expect } from '@playwright/test';
import { installContract, login } from './helpers/backend-contract';
test.setTimeout(90_000);

test('P1 HTTP 登录、真实会话恢复与不可用能力边界', async ({ page }) => {
    await installContract(page); await login(page);
    await expect(page.locator('.account-realm-badge')).toHaveCount(0);
    await page.reload();
    await expect(page.getByText('身份用户总数', { exact: true })).toBeVisible();
    await page.goto('/#/console/workforce/sync/tasks');
    await expect(page.getByText('此功能尚未开通', { exact: true })).toBeVisible();
    await page.goto('/#/auth/public/register/person');
    await expect(page.getByRole('heading', { name: '自然人注册' })).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('iam.frontend.mock.v2'))).toBeNull();
});
test('新建中央人员只提交资料与任职，不提交登录凭据', async ({ page }) => {
    const { saved } = await installContract(page); await login(page);
    await page.goto('/#/console/workforce/organization');
    await page.getByRole('button', { name: /新建用户$/ }).click();
    const drawer = page.getByRole('dialog');
    await drawer.getByLabel('姓名', { exact: true }).fill('契约新增人员');
    await drawer.getByLabel('账号', { exact: true }).fill('contract.created');
    await expect(drawer.getByLabel('初始密码', { exact: true })).toHaveCount(0);
    await expect(drawer.getByText('创建新账号', { exact: true })).toHaveCount(0);
    await drawer.getByLabel('账户状态', { exact: true }).click();
    const statuses = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden):not(.ant-slide-up-leave)');
    await expect(statuses).not.toContainText('待完善');
    await statuses.getByText('已启用', { exact: true }).click();
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(drawer).toHaveCount(0);
    await page.getByRole('textbox', { name: '搜索姓名、账号或邮箱' }).fill('contract.created');
    await expect(page.getByRole('button', { name: '契约新增人员', exact: true })).toBeVisible();
    expect(saved[0].creating).toBe(true);
    const record = saved[0].record as Record<string, unknown> & { appointments: unknown[] };
    expect(Object.hasOwn(record, 'initialPassword')).toBe(false);
    expect(Object.hasOwn(record, 'password')).toBe(false);
    expect(Object.hasOwn(record, 'existingAccount')).toBe(false);
    expect(record.appointments).toHaveLength(1);
});
test('新建中央人员必须填写姓名，账号不代表登录资格', async ({ page }) => {
    const { saved } = await installContract(page); await login(page);
    await page.goto('/#/console/workforce/organization');
    await page.getByRole('button', { name: /新建用户$/ }).click();
    const drawer = page.getByRole('dialog');
    await expect(drawer.getByLabel('初始密码', { exact: true })).toHaveCount(0);
    await drawer.getByLabel('账号', { exact: true }).fill('existing.account');
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(drawer.getByText('请填写姓名', { exact: true })).toBeVisible();
    expect(saved).toHaveLength(0);
});
test('P1 保存冲突保留输入，重试沿用同一个请求号', async ({ page }) => {
    await installContract(page);
    const requests: Array<{ requestId: string }> = [];
    await page.route('**/api/idaas/orgs/save', async route => {
        requests.push(route.request().postDataJSON());
        if (requests.length === 1) await route.fulfill({ json: { code: 409, message: '机构资料已更新，请核对后重试' } });
        else await route.fallback();
    });
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await page.getByRole('button', { name: '新增机构', exact: true }).click();
    const drawer = page.getByRole('dialog');
    await drawer.getByLabel('机构名称', { exact: true }).fill('待保存机构');
    await drawer.getByLabel('机构编码', { exact: true }).fill('CONTRACT_ORG');
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(page.locator('.ant-message-notice-error')).toContainText('机构资料已更新，请核对后重试');
    await expect(drawer.getByLabel('机构名称', { exact: true })).toHaveValue('待保存机构');
    await drawer.getByRole('button', { name: /保\s*存/ }).click();
    await expect(drawer).toHaveCount(0);
    expect(requests[0].requestId).toBe(requests[1].requestId);
});
test('P1 未完成人员操作可继续处理，读取失败保留清单与人员列表', async ({ page }) => {
    await installContract(page);
    let pending = true;
    let unavailable = false;
    let retryBody: unknown;
    await page.route('**/api/idaas/users/operations', async route => {
        await route.fulfill({ json: unavailable ? { code: 503, message: '待恢复记录暂时无法读取' } : { code: 200, data: pending ? [{ requestId: 'recover-original-request', userId: 'u1', operationType: 'CREATE_USER', status: 'PENDING', errorCode: 'TEMPORARY_FAILURE', createdTime: '2026-09-27T12:00:00' }] : [] } });
    });
    await page.route('**/api/idaas/users/retry', async route => {
        retryBody = route.request().postDataJSON(); pending = false; unavailable = false;
        await route.fulfill({ json: { code: 200, data: { status: 'COMPLETED' } } });
    });
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await expect(page.getByText('待恢复的人员操作（1）', { exact: true })).toBeVisible();
    unavailable = true;
    await page.getByRole('button', { name: /刷\s*新/ }).click();
    await expect(page.getByText('待恢复记录暂时无法读取', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '继续处理', exact: true })).toBeVisible();
    await expect(page.getByRole('textbox', { name: '搜索姓名、账号或邮箱' })).toBeVisible();
    await page.getByRole('button', { name: '继续处理', exact: true }).click();
    await expect(page.getByText('待恢复的人员操作（1）', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('textbox', { name: '搜索姓名、账号或邮箱' })).toBeVisible();
    expect(retryBody).toEqual({ requestId: 'recover-original-request' });
});
test('P1 登录页舒展控件和工作区在 390/1440/1920px 可用', async ({ page }, testInfo) => {
    test.setTimeout(150_000);
    await installContract(page);
    for (const width of [390, 1440, 1920]) {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
        await page.goto('/#/login');
        expect(await page.getByLabel('管理账号').locator('..').evaluate(element => element.getBoundingClientRect().height)).toBeGreaterThanOrEqual(44);
        await login(page);
        for (const path of ['overview', 'organization', 'apps', 'apps/a1', 'audit/operations', 'audit/logins']) {
            await page.goto(`/#/console/workforce/${path}`);
            if (path === 'overview') await expect(page.getByText('身份用户总数', { exact: true })).toBeVisible();
            else if (path === 'apps') await expect(page.getByText('应用数量', { exact: true })).toBeVisible();
            else if (path === 'organization') await expect(page.locator('.organization-layout .table-card')).toBeVisible();
            else await expect(page.locator('.page-heading, .application-detail-hero').first()).toBeVisible();
            expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width + 1);
        }
        await page.screenshot({ path: testInfo.outputPath(`p1-${width}.png`), fullPage: true });
    }
});
