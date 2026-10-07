import { expect, test, type Page } from '@playwright/test';
import { installConsoleContract } from './helpers/backend-contract';

test.setTimeout(60000);
test.use({ trace: 'off' });

for (const width of [390, 1440, 1920]) test(`首次进入应用目录时展示业务加载状态 ${width}px`, async ({ page }, info) => {
    await installConsoleContract(page);
    await page.setViewportSize({ width, height: width === 390 ? 844 : 582 });
    await page.addInitScript(() => sessionStorage.setItem('iam.frontend.backend.token', 'console-contract-token'));
    let releaseBootstrap!: () => void;
    const waiting = new Promise<void>(resolve => { releaseBootstrap = resolve; });
    await page.route('**/api/idaas/workspace/bootstrap', async route => { await waiting; await route.fallback(); });
    await page.goto('/#/console/workforce/apps');
    await expect(page.getByRole('status', { name: '正在载入应用目录' })).toBeVisible();
    await expect(page.locator('.workspace-loading-preview')).toBeVisible();
    await expect(page.locator('.workspace-loading')).not.toContainText('统一身份管理平台');
    await expect(page.locator('.ant-skeleton, .app-sider')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: info.outputPath(`workspace-loading-${width}.png`) });
    releaseBootstrap();
    await expect(page.getByText('应用数量', { exact: true })).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('status', { name: '正在载入应用目录' })).toHaveCount(0);
});

test('组织与用户加载画面不显示平台标识', async ({ page }, info) => {
    await installConsoleContract(page);
    await page.setViewportSize({ width: 1039, height: 582 });
    await page.addInitScript(() => sessionStorage.setItem('iam.frontend.backend.token', 'console-contract-token'));
    let releaseBootstrap!: () => void;
    const waiting = new Promise<void>(resolve => { releaseBootstrap = resolve; });
    await page.route('**/api/idaas/workspace/bootstrap', async route => { await waiting; await route.fallback(); });
    await page.goto('/#/console/workforce/organization');
    const loading = page.getByRole('status', { name: '正在载入组织与用户' });
    await expect(loading).toBeVisible();
    await expect(loading).not.toContainText('统一身份管理平台');
    await expect(loading.locator('.workspace-loading-preview')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: info.outputPath('organization-loading-1039.png') });
    releaseBootstrap();
    await expect(page.locator('.organization-layout .table-card')).toBeVisible({ timeout: 15000 });
});

async function login(page: Page) {
    await page.goto('/#/login');
    await expect(page.locator('.login-content .ant-segmented')).toHaveCount(0);
    await page.getByLabel('管理账号').fill('admin');
    await page.getByLabel('登录密码').fill('Contract-pass-2026');
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page.getByText('身份用户总数', { exact: true })).toBeVisible({ timeout: 15000 });
}
async function publicItem(page: Page, width: number, name: string) {
    if (width < 768) await page.getByRole('button', { name: '展开导航', exact: true }).click();
    const menu = width < 768 ? page.getByRole('dialog', { name: '导航', exact: true }) : page.locator('.app-sider');
    await menu.locator('.ant-menu-submenu-title').filter({ hasText: '公众身份' }).click();
    return menu.getByRole('menuitem', { name, exact: true });
}
for (const width of [390, 1440, 1920]) test(`统一控制台公众入口、搜索和返回组织管理 ${width}px`, async ({ page }, info) => {
    const fixture = await installConsoleContract(page);
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    await login(page);
    await expect(page.locator('.workspace-label, .domain-switch')).toHaveCount(0);
    await expect(page.locator('.account-realm-badge')).toHaveCount(0);
    await (await publicItem(page, width, '自然人管理')).click();
    await expect(page).toHaveURL(/console\/public\/persons$/);
    await expect(page.getByRole('heading', { name: '自然人管理', exact: true })).toBeVisible();
    expect(fixture.transitions).toEqual(['public']);
    if (width === 390) await page.getByRole('button', { name: '展开导航', exact: true }).click();
    const surface = width === 390 ? page.getByRole('dialog', { name: '导航', exact: true }) : page.locator('.app-sider');
    await expect(surface.locator('.ant-menu-submenu-title').filter({ hasText: '组织与用户' })).toBeVisible();
    await expect(surface.locator('.ant-menu-item-selected')).toHaveText('自然人管理');
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: info.outputPath(`console-public-menu-${width}.png`), fullPage: true });
    if (width === 390) await page.keyboard.press('Escape');
    await page.keyboard.press('Control+k');
    await page.getByLabel('全局搜索内容').fill('公众认证');
    await page.locator('.command-result').filter({ hasText: '公众认证账号' }).click();
    await expect(page).toHaveURL(/console\/public\/auth-accounts$/);
    await expect(page.getByRole('heading', { name: '认证账号', exact: true })).toBeVisible();
    await page.keyboard.press('Control+k');
    await page.getByLabel('全局搜索内容').fill('组织与用户');
    await page.locator('.command-result').filter({ hasText: '组织与用户' }).click();
    await expect(page).toHaveURL(/console\/workforce\/organization$/);
    await expect(page.locator('.organization-layout .table-card')).toBeVisible();
    expect(fixture.transitions).toEqual(['public', 'workforce']);
    await expect(page.locator('.workspace-label, .domain-switch')).toHaveCount(0);
});
test('无公众管理范围时隐藏入口，深链接不能触发跨域加载', async ({ page }) => {
    const fixture = await installConsoleContract(page, { publicAllowed: false });
    await login(page);
    await expect(page.locator('.app-sider')).not.toContainText('公众身份');
    await page.goto('/#/console/public/persons');
    await expect(page.getByText('当前账号无此页面访问权限', { exact: true })).toBeVisible();
    expect(fixture.transitions).toEqual([]);
});
test('后台撤销公众范围后显示失败、支持重试，不展示旧身份域资料', async ({ page }) => {
    const fixture = await installConsoleContract(page, { denyPublicOnce: true });
    await login(page);
    await (await publicItem(page, 1440, '自然人管理')).click();
    await expect(page.getByText('此功能加载失败', { exact: true })).toBeVisible();
    await expect(page.locator('.organization-layout')).toHaveCount(0);
    expect(fixture.transitions).toEqual(['public']);
    await page.getByRole('button', { name: '重新加载', exact: true }).click();
    await expect(page.getByRole('heading', { name: '自然人管理', exact: true })).toBeVisible();
    expect(fixture.transitions).toEqual(['public', 'public']);
});
test('公众深链接刷新及浏览器后退自动加载所需身份资料', async ({ page }) => {
    const fixture = await installConsoleContract(page);
    await login(page);
    await page.goto('/#/console/public/entities');
    await expect(page.getByRole('heading', { name: '法人管理', exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('heading', { name: '法人管理', exact: true })).toBeVisible();
    await page.goto('/#/console/workforce/organization');
    await expect(page.locator('.organization-layout .table-card')).toBeVisible();
    await page.goBack();
    await expect(page.getByRole('heading', { name: '法人管理', exact: true })).toBeVisible();
    expect(fixture.transitions).toEqual(['public', 'workforce', 'public']);
});
test('加载期间快速返回会串行恢复目标上下文', async ({ page }) => {
    const fixture = await installConsoleContract(page, { delayPublic: 500 });
    await login(page);
    await (await publicItem(page, 1440, '自然人管理')).click();
    await expect.poll(() => fixture.transitions.length).toBe(1);
    await page.goto('/#/console/workforce/organization');
    await expect(page.locator('.organization-layout .table-card')).toBeVisible();
    await expect.poll(() => fixture.session.domain).toBe('workforce');
    expect(fixture.transitions).toEqual(['public', 'workforce']);
});

test('公众资料被拒绝时即使改选同域其他功能也显示失败并能重试', async ({ page }) => {
    const fixture = await installConsoleContract(page, { denyPublicOnce: true, delayPublic: 500 });
    await login(page);
    await (await publicItem(page, 1440, '自然人管理')).click();
    await expect.poll(() => fixture.transitions.length).toBe(1);
    await page.goto('/#/console/public/entities');
    await expect(page.getByText('此功能加载失败', { exact: true })).toBeVisible();
    expect(fixture.transitions).toEqual(['public']);
    await page.getByRole('button', { name: '重新加载', exact: true }).click();
    await expect(page.getByRole('heading', { name: '法人管理', exact: true })).toBeVisible();
    expect(fixture.transitions).toEqual(['public', 'public']);
});
