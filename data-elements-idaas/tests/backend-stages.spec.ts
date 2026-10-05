import { expect, test } from '@playwright/test';

test.skip(process.env.IDAAS_LIVE_TEST !== '1', '显式启用真实后端回归');
test.use({ trace: 'off' });
test.afterEach(async ({ page }) => {
    const password = page.getByLabel('登录密码', { exact: true });
    if (await password.count()) await password.fill('');
});

for (const width of [1440, 390, 1920]) test(`真实 IAM 工作区与认证页面 ${width}px`, async ({ page }, info) => {
    test.setTimeout(360000);
    const errors: string[] = [];
    page.on('pageerror', e => errors.push(e.message));
    page.on('response', async response => {
        const path = new URL(response.url()).pathname.replace(/^\/api/, '');
        if (!['/idaas/auth/login', '/idaas/workspace/bootstrap'].includes(path)) return;
        try { const body = await response.json(); console.log('Live response', path, response.status(), body.code, body.msg); } catch { console.log('Live response', path, response.status()); }
    });
    await page.setViewportSize({ width, height: 1000 });
    await page.goto('/#/login');
    await page.getByLabel('管理账号', { exact: true }).fill(process.env.IDAAS_OPERATOR_USERNAME || 'manager');
    await page.getByLabel('登录密码', { exact: true }).fill(process.env.IDAAS_OPERATOR_PASSWORD!);
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page).toHaveURL(/console\/workforce\/overview/, { timeout: 60000 });
    const routes = ['overview', 'apps', 'organization', 'grants/apps', 'sync/tasks', 'system/settings/sso', 'system/settings/encryption', 'system/settings/api', 'system/access/admins'];
    for (const route of routes) {
        await page.goto('/#/console/workforce/' + route);
        if (route === 'overview') await expect(page.getByText('身份用户总数', { exact: true })).toBeVisible({ timeout: 45000 });
        else await expect(page.locator('.page-heading')).toBeVisible({ timeout: 45000 });
        await expect(page.getByText('工作区加载失败', { exact: true })).toHaveCount(0);
        await expect(page.locator('.ant-spin-spinning')).toHaveCount(0, { timeout: 45000 });
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
        expect(overflow, route).toBeLessThanOrEqual(1);
        await page.screenshot({ path: info.outputPath(`live-${route.replaceAll('/', '-')}-${width}.png`), fullPage: true, animations: 'disabled' });
    }
    await page.goto('/#/auth/identity/login');
    await expect(page.getByRole('button', { name: /登\s*录/ })).toBeVisible();
    await page.screenshot({ path: info.outputPath(`live-identity-login-${width}.png`), fullPage: true, animations: 'disabled' });
    expect(errors).toEqual([]);
});
