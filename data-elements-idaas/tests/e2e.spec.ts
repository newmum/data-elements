import { expect, test } from '@playwright/test';
import { installConsoleContract, login } from './helpers/backend-contract';

// These browser checks use the current platform-session HTTP contract. The
// fixture handles every /api request, including writes, without a live backend.
test.beforeEach(async ({ page }) => { await installConsoleContract(page); });

test('移动端管理登录页可以完整显示', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/#/login');
    await expect(page.getByRole('heading', { name: '欢迎登录' })).toBeVisible();
    await expect(page.getByLabel('管理账号')).toBeVisible();
    await expect(page.getByLabel('登录密码')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
});

test('平台会话可读取政企工作区和当前管理入口', async ({ page }) => {
    await login(page);
    for (const [route, heading] of [
        ['overview', '身份用户总数'],
        ['apps', '应用数量'],
        ['organization', '用户管理'],
        ['audit/operations', '操作日志'],
    ]) {
        await page.goto(`/#/console/workforce/${route}`);
        if (route === 'overview' || route === 'apps') await expect(page.getByText(heading, { exact: true })).toBeVisible();
        else await expect(page.getByRole('heading', { name: heading, exact: true }).first()).toBeVisible();
        await expect(page.getByText('页面暂时无法显示', { exact: true })).toHaveCount(0);
    }
});

test('公众身份入口使用分域会话并展示资料页', async ({ page }) => {
    await login(page);
    await page.goto('/#/console/public/persons');
    await expect(page.getByRole('heading', { name: '自然人管理', exact: true })).toBeVisible();
    await page.goto('/#/console/public/entities');
    await expect(page.getByRole('heading', { name: '法人管理', exact: true })).toBeVisible();
    await expect(page.getByText('页面暂时无法显示', { exact: true })).toHaveCount(0);
});

test('公众注册页说明实名核验独立于注册', async ({ page }) => {
    await page.goto('/#/auth/public/register/person');
    await expect(page.getByRole('heading', { name: '自然人注册' })).toBeVisible();
    await expect(page.getByText('注册资料与实名核验分开处理', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '提交注册' })).toBeVisible();
});

test('管理端退出撤销失败仍清除本地会话并保留提示', async ({ page }) => {
    await page.route('**/api/idaas/auth/logout', route => route.fulfill({ status: 503, json: { code: 503, message: '服务暂不可用' } }));
    await login(page);
    await page.getByRole('button', { name: /管理人员.*系统管理员/ }).click();
    await page.getByRole('menuitem', { name: '退出登录' }).click();
    await page.getByRole('dialog', { name: '退出当前账户？' }).getByRole('button', { name: '退出登录' }).click();
    await expect(page.getByRole('heading', { name: '欢迎登录' })).toBeVisible();
    await expect(page.getByText(/本地会话已清除，但身份服务未确认旧会话撤销/)).toBeVisible();
    expect(await page.evaluate(() => sessionStorage.getItem('iam.frontend.backend.token'))).toBeNull();
    await page.reload();
    await expect(page.getByText(/本地会话已清除，但身份服务未确认旧会话撤销/)).toBeVisible();
});

test('统一认证退出撤销失败仍清除本地会话并保留提示', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('iam.auth.account.token', 'contract-identity-token'));
    await page.route('**/api/idaas/auth-account/logout', route => route.fulfill({ status: 503, json: { code: 503, message: '服务暂不可用' } }));
    await page.goto('/#/auth/identity/profile');
    await page.getByRole('button', { name: '退出登录' }).click();
    await expect(page.getByRole('heading', { name: '身份登录' })).toBeVisible();
    await expect(page.getByText(/本地会话已清除，但身份服务未确认旧会话撤销/)).toBeVisible();
    expect(await page.evaluate(() => sessionStorage.getItem('iam.auth.account.token'))).toBeNull();
    await page.reload();
    await expect(page.getByText(/本地会话已清除，但身份服务未确认旧会话撤销/)).toBeVisible();
});

test('@visual 认证入口在桌面与手机宽度可见', async ({ page }, testInfo) => {
    test.setTimeout(120_000);
    const paths = ['/login', '/auth/identity/login', '/auth/public/login', '/auth/public/register/person', '/auth/public/register/company', '/auth/workforce/forgot', '/auth/public/forgot', '/auth/workforce/mfa', '/auth/public/mfa'];
    for (const width of [1440, 390]) {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
        for (const path of paths) {
            await page.goto('/#' + path);
            await expect(page.locator('.auth-content')).toBeVisible();
            await page.evaluate(() => document.fonts.ready);
            expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth), path).toBeLessThanOrEqual(1);
            await page.screenshot({ path: testInfo.outputPath(`${width}-${path.replaceAll('/', '-')}.png`), fullPage: true });
        }
    }
});
