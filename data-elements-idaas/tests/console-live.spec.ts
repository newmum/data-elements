import { expect, test } from '@playwright/test';

test.skip(process.env.IDAAS_LIVE_TEST !== '1', '显式启用真实后端回归');
test.use({ trace: 'off' });

for (const width of [390, 1440, 1920]) test(`真实统一控制台公众菜单与政企资料 ${width}px`, async ({ page }, info) => {
    test.setTimeout(360000);
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    await page.goto('/#/login');
    await expect(page.locator('.login-content .ant-segmented')).toHaveCount(0);
    await page.getByLabel('管理账号', { exact: true }).fill(process.env.IDAAS_OPERATOR_USERNAME || 'manager');
    await page.getByLabel('登录密码', { exact: true }).fill(process.env.IDAAS_OPERATOR_PASSWORD!);
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page).toHaveURL(/console\/workforce\/overview/, { timeout: 60000 });
    await expect(page.getByText('身份用户总数', { exact: true })).toBeVisible({ timeout: 60000 });
    await expect(page.locator('.workspace-label, .domain-switch')).toHaveCount(0);
    await page.screenshot({ path: info.outputPath(`live-console-overview-${width}.png`), fullPage: true, animations: 'disabled' });
    if (width === 390) await page.getByRole('button', { name: '展开导航', exact: true }).click();
    const navigation = width === 390 ? page.getByRole('dialog', { name: '导航', exact: true }) : page.locator('.app-sider');
    await navigation.locator('.ant-menu-submenu-title').filter({ hasText: '公众身份' }).click();
    await expect(navigation.getByRole('menuitem', { name: '自然人管理', exact: true })).toBeVisible();
    await expect(navigation.locator('.ant-menu-submenu-title').filter({ hasText: '组织与用户' })).toBeVisible();
    await page.screenshot({ path: info.outputPath(`live-console-navigation-${width}.png`), fullPage: true, animations: 'disabled' });
    await navigation.getByRole('menuitem', { name: '自然人管理', exact: true }).click();
    await expect(page.getByRole('heading', { name: '自然人管理', exact: true })).toBeVisible({ timeout: 60000 });
    for (const route of ['persons', 'entities', 'auth-accounts', 'verifications', 'apps', 'app-groups', 'audit/statistics']) {
        if (route !== 'persons') await page.goto('/#/console/public/' + route);
        await expect(page.locator('.page-heading')).toBeVisible({ timeout: 60000 });
        await expect(page.locator('.ant-skeleton')).toHaveCount(0, { timeout: 60000 });
        await expect(page.locator('.ant-alert-error')).toHaveCount(0, { timeout: 60000 });
        await expect(page.getByText('此功能加载失败', { exact: true })).toHaveCount(0);
        expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth), route).toBeLessThanOrEqual(1);
        await page.screenshot({ path: info.outputPath(`live-console-${route.replaceAll('/', '-')}-${width}.png`), fullPage: true, animations: 'disabled' });
    }
    await page.keyboard.press('Control+k');
    await page.getByLabel('全局搜索内容').fill('组织与用户');
    await page.locator('.command-result').filter({ hasText: '组织与用户' }).click();
    await expect(page).toHaveURL(/console\/workforce\/organization$/);
    await expect(page.locator('.organization-layout .table-card')).toBeVisible({ timeout: 60000 });
    await page.screenshot({ path: info.outputPath(`live-console-organization-${width}.png`), fullPage: true, animations: 'disabled' });
    expect(errors).toEqual([]);
});
