import { test, expect, type Page } from '@playwright/test';
import { productGroups } from '../src/app/platformNavigation';
import { pages } from '../src/app/navigation-data';
import { consolePages } from '../src/app/console-navigation';
import { installConsoleContract } from './helpers/backend-contract';

test.beforeEach(async ({ page }) => { await installConsoleContract(page); });

async function login(page: Page) {
    await page.goto('/#/login');
    await page.getByLabel('管理账号').fill('admin');
    await page.getByLabel('登录密码').fill('Review@2026');
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page).toHaveURL(/console\/workforce\/overview/);
}

for (const width of [1920, 1440, 1360, 390]) test(`系统切换菜单可读、可滚动 ${width}px`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: width === 1360 ? 582 : width === 390 ? 844 : 1000 });
    await login(page);
    if (width === 390) await page.getByRole('button', { name: '展开导航' }).click();
    const trigger = page.getByRole('button', { name: '切换工作中心', exact: true }).filter({ visible: true });
    await expect(trigger).toContainText('统一身份管理');
    await expect(trigger).not.toContainText('卓鉴');
    await trigger.press('Enter');
    const menu = page.locator('.iam-center-popover .ant-dropdown-menu');
    await expect(menu).toBeVisible();
    await expect(menu.getByRole('menuitem')).toHaveCount(13);
    await expect(menu.getByText('平台支撑', { exact: true })).toBeVisible();
    await menu.getByText('统一身份管理平台', { exact: true }).scrollIntoViewIfNeeded();
    await expect(menu.locator('.iam-center-item.current')).toContainText('当前');
    const bounds = await menu.boundingBox();
    expect(bounds!.x).toBeGreaterThanOrEqual(0);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width);
    expect(bounds!.height).toBeLessThanOrEqual((width === 1360 ? 582 : width === 390 ? 844 : 1000) - 90);
    await page.screenshot({ path: info.outputPath(`switch-${width}.png`) });
    await menu.getByRole('menuitem', { name: /统一身份管理/ }).click();
    await expect(menu).not.toBeVisible();
    await expect(page).toHaveURL(/console\/workforce\/overview/);
});

test('折叠侧栏保留可访问的切换入口', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 900 });
    await login(page);
    const trigger = page.getByRole('button', { name: '切换工作中心' });
    await expect(trigger).toBeVisible();
    await trigger.press('Enter');
    await expect(page.getByRole('menuitem', { name: /数据治理中心/ })).toBeVisible();
});

const desktopGroup = (page: Page, group: string) => page.locator('.app-sider .ant-menu-submenu-title').filter({ hasText: group });
const visibleSubmenu = (page: Page) => page.locator('.business-navigation-popup').filter({ visible: true });

for (const group of ['应用管理', '组织与用户', '授权管理', '同步管理', '审计日志', '系统管理']) {
    test(`折叠侧栏悬停${group}可进入二级功能`, async ({ page }, info) => {
        await page.setViewportSize({ width: 1039, height: 582 });
        await login(page);
        const title = desktopGroup(page, group);
        await title.hover();
        const popup = visibleSubmenu(page);
        await expect(popup).toBeVisible();
        await expect(popup).not.toHaveClass(/ant-(?:slide|zoom).*-(?:enter|appear|leave)/);
        const discovery = { permissions: ['apps','users','orgs','directory','operators','platform-roles','grants','sync','audit','settings','public','auth-accounts'].map(key => key + ':read') };
        const children = consolePages({ realm: 'platform', userId: 'operator', username: 'admin', name: '管理人员', role: 'admin', domain: 'workforce', ...discovery, globalPermissions: discovery.permissions }).filter(p => p.group === group);
        await expect(popup.getByRole('menuitem')).toHaveCount(children.length);
        const destination = children[children.length - 1];
        const item = popup.getByRole('menuitem', { name: destination.label, exact: true });
        await item.hover();
        await expect(title).toHaveAttribute('aria-expanded', 'true');
        await expect(item).toHaveCSS('font-size', '14px');
        const bounds = await popup.boundingBox();
        expect(bounds!.x).toBeGreaterThanOrEqual(56);
        expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(1039);
        expect(bounds!.y).toBeGreaterThanOrEqual(0);
        expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(582);
        if (group === '组织与用户') await page.screenshot({ path: info.outputPath('collapsed-organization-1039.png') });
        await item.click();
        await expect(page).toHaveURL(new RegExp(`/console/workforce/${destination.key}$`));
        await expect(visibleSubmenu(page)).toHaveCount(0);
        await expect(page.locator('.app-sider')).toHaveClass(/ant-layout-sider-collapsed/);
    });
}

test('折叠菜单点击、离开关闭与展开后当前分组保持一致', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await login(page);
    await desktopGroup(page, '组织与用户').click();
    await page.getByRole('button', { name: '折叠导航', exact: true }).click();
    await expect(visibleSubmenu(page)).toHaveCount(0);
    await desktopGroup(page, '组织与用户').click();
    await expect(visibleSubmenu(page)).toBeVisible();
    await page.locator('.header-breadcrumb').hover();
    await expect(visibleSubmenu(page)).toHaveCount(0);
    await desktopGroup(page, '组织与用户').click();
    await expect(visibleSubmenu(page)).toBeVisible();
    await visibleSubmenu(page).hover();
    await visibleSubmenu(page).getByRole('menuitem', { name: '分级管理员', exact: true }).click();
    await expect(page).toHaveURL(/console\/workforce\/delegates$/);
    await page.getByRole('button', { name: '展开导航', exact: true }).click();
    await expect(desktopGroup(page, '组织与用户')).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('.app-sider .ant-menu-item-selected')).toHaveText('分级管理员');
    await expect(visibleSubmenu(page)).toHaveCount(0);
});

test('折叠菜单支持键盘进入二级功能', async ({ page }) => {
    await page.setViewportSize({ width: 1039, height: 582 });
    await login(page);
    await desktopGroup(page, '组织与用户').focus();
    await desktopGroup(page, '组织与用户').press('ArrowRight');
    await expect(visibleSubmenu(page)).toBeVisible();
    const first = visibleSubmenu(page).getByRole('menuitem', { name: '组织与用户', exact: true });
    await expect(first).toBeFocused();
    await first.press('Enter');
    await expect(page).toHaveURL(/console\/workforce\/organization$/);
    await expect(visibleSubmenu(page)).toHaveCount(0);
});

test('当前分组曾手动关闭时，折叠选择同组功能后展开可见', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await expect(desktopGroup(page, '组织与用户')).toHaveAttribute('aria-expanded', 'true');
    await desktopGroup(page, '组织与用户').click();
    await expect(desktopGroup(page, '组织与用户')).toHaveAttribute('aria-expanded', 'false');
    await page.getByRole('button', { name: '折叠导航', exact: true }).click();
    await desktopGroup(page, '组织与用户').click();
    await visibleSubmenu(page).getByRole('menuitem', { name: '分级管理员', exact: true }).click();
    await expect(page).toHaveURL(/console\/workforce\/delegates$/);
    await page.getByRole('button', { name: '展开导航', exact: true }).click();
    await expect(desktopGroup(page, '组织与用户')).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('.app-sider .ant-menu-item-selected')).toHaveText('分级管理员');
});

test('公众身份折叠菜单可直接进入功能并保留组织菜单', async ({ page }) => {
    await page.setViewportSize({ width: 1039, height: 582 });
    await login(page);
    await expect(page.locator('.workspace-label, .domain-switch')).toHaveCount(0);
    await expect(desktopGroup(page, '组织与用户')).toBeVisible();
    await desktopGroup(page, '公众身份').hover();
    await expect(visibleSubmenu(page).getByRole('menuitem')).toHaveCount(8);
    await visibleSubmenu(page).getByRole('menuitem', { name: '法人管理', exact: true }).click();
    await expect(page).toHaveURL(/console\/public\/entities$/);
    await expect(page.getByRole('heading', { name: '法人管理', exact: true })).toBeVisible();
    await expect(desktopGroup(page, '组织与用户')).toBeVisible();
    await expect(visibleSubmenu(page)).toHaveCount(0);
});

test('手机导航抽屉仍以内联菜单选择功能', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await login(page);
    await page.getByRole('button', { name: '展开导航', exact: true }).click();
    const drawer = page.getByRole('dialog', { name: '导航', exact: true });
    await drawer.locator('.ant-menu-submenu-title').filter({ hasText: '组织与用户' }).click();
    await expect(visibleSubmenu(page)).toHaveCount(0);
    await drawer.getByRole('menuitem', { name: '分级管理员', exact: true }).click();
    await expect(page).toHaveURL(/console\/workforce\/delegates$/);
    await expect(drawer).not.toBeVisible();
    await page.getByRole('button', { name: '展开导航', exact: true }).click();
    await expect(drawer.locator('.ant-menu-item-selected')).toHaveText('分级管理员');
});

// 拦截导航请求仅核对点击后的目标；不将替代响应作为目标系统运行证据。
for (const center of productGroups.flatMap(group => [...group.centers]).filter(center => center.id !== 'idaas')) {
    test(`点击${center.name}进入所属系统并使用当前标签页`, async ({ page, context }) => {
        await login(page);
        const port = center.owner === 'wanxiang' ? 3010 : center.owner === 'haitong' ? 3002 : 3001;
        const destination = `http://localhost:${port}/#${center.home}`;
        await context.route(`http://localhost:${port}/**`, route => route.fulfill({ contentType: 'text/html', body: '<p>navigation target</p>' }));
        await page.getByRole('button', { name: '切换工作中心' }).click();
        await page.getByRole('menuitem', { name: new RegExp(center.name) }).click();
        await expect(page).toHaveURL(destination);
        expect(context.pages()).toHaveLength(1);
    });
}
