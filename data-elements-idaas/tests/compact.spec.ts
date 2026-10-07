import { test, expect, type Page } from '@playwright/test';
import { domainPages, authPaths } from '../src/app/navigation-data';
import { installContract, login } from './helpers/backend-contract';
test.beforeEach(async ({ page }) => { await installContract(page); });

// These are real React/Ant Design acceptance tests. They are not independent HTML tests.
// One browser case per domain and resolution; screenshots attach to Playwright's report.
const widths = [1920, 1600, 1440, 1280, 1024, 768, 390];
const forbidden = /Mock|演示|评审|模拟|本轮|测试账号|设计规范/iu;
async function checkSurface(page: Page) {
    await page.evaluate(() => document.fonts.ready);
    await expect(page.locator('body')).not.toContainText(forbidden);
    await expect(page.getByText('页面暂时无法显示', { exact: true })).toHaveCount(0);
    const geometry = await page.evaluate(() => ({
        viewport: innerWidth, document: document.documentElement.scrollWidth,
        nonSmallTables: document.querySelectorAll('.ant-table:not(.ant-table-small)').length,
        nonSmallCards: document.querySelectorAll('.ant-card:not(.ant-card-small)').length,
    }));
    expect(geometry.document).toBeLessThanOrEqual(geometry.viewport + 1);
    expect(geometry.nonSmallTables).toBe(0);
    expect(geometry.nonSmallCards).toBe(0);
}
async function checkAuthControls(page: Page) {
    const controls = await page.evaluate(() => {
        const visible = (el: Element) => el.getBoundingClientRect().width > 0 && el.getBoundingClientRect().height > 0;
        const inputs = Array.from(document.querySelectorAll('.auth-content .ant-input-affix-wrapper, .auth-content .ant-input:not(.ant-input-affix-wrapper .ant-input)')).filter(visible);
        const actions = Array.from(document.querySelectorAll('.auth-content .auth-primary-action')).filter(visible);
        const fields = Array.from(document.querySelectorAll('.auth-content .ant-form-item')).filter(visible);
        return {
            inputs: inputs.map(el => el.getBoundingClientRect().height),
            actions: actions.map(el => el.getBoundingClientRect().height),
            fieldMargins: fields.map(el => parseFloat(getComputedStyle(el).marginBottom)),
        };
    });
    const route = new URL(page.url()).hash;
    if (/login|register/.test(route)) expect(controls.inputs.length).toBeGreaterThan(0);
    else if (route.endsWith('/mfa')) await expect(page.getByText('认证流程已失效，请重新登录', { exact: true })).toBeVisible();
    else {
        await expect(page.getByText(/提交申请后，请联系对应系统的账号管理员/)).toBeVisible();
        await expect(page.getByLabel('登录账号', { exact: true })).toBeVisible();
        await expect(page.getByRole('button', { name: '提交找回申请' })).toBeVisible();
    }
    for (const height of controls.inputs) expect(height).toBeGreaterThanOrEqual(44);
    for (const height of controls.actions) expect(height).toBeGreaterThanOrEqual(48);
    for (const margin of controls.fieldMargins) expect(margin).toBeGreaterThanOrEqual(24);
}
for (const domain of ['workforce'] as const) {
    for (const width of widths) test(`@compact ${domain} 全部工作区 ${width}px`, async ({ page }, testInfo) => {
        test.setTimeout(240_000);
        const errors: string[] = [];
        page.on('pageerror', err => errors.push(err.message));
        await page.setViewportSize({ width, height: width < 768 ? 844 : 1000 });
        await login(page);
        for (const route of domainPages(domain)) {
            await page.goto(`/#/console/${domain}/${route.key}`);
            if (route.key === 'overview') await expect(page.getByText('身份用户总数', { exact: true })).toBeVisible();
            else if (route.key === 'apps') await expect(page.getByText('应用数量', { exact: true })).toBeVisible();
            else if (route.key === 'organization') await expect(page.locator('.organization-layout .table-card')).toBeVisible();
            else await expect(page.locator('.page-title')).toBeVisible();
            await checkSurface(page);
            await page.screenshot({ path: testInfo.outputPath(`${route.key.replaceAll('/', '-')}.png`), fullPage: true });
        }
        expect(errors).toEqual([]);
    });
}
for (const width of [1920, 1440, 1224, 768, 390]) test(`@compact 全部认证页 ${width}px`, async ({ page }, testInfo) => {
    test.setTimeout(90_000);
    await page.setViewportSize({ width, height: width === 390 ? 844 : width === 1224 ? 524 : 1000 });
    for (const route of authPaths) {
        await page.goto('/#' + route);
        await expect(page.locator('.auth-content')).toBeVisible();
        await expect(page.locator('.auth-brand-footer')).toHaveText(/一个身份。连接每一份信任。/);
        await expect(page.locator('.auth-footer')).toHaveCount(0);
        if (width >= 768) await expect(page.locator('.auth-story h1')).toHaveText('统一身份，安全访问。让人员、应用和访问权限高效有序协同。');
        await expect(page.locator('.auth-story p, .auth-capabilities small')).toHaveCount(0);
        if (route === '/login') await expect(page.getByText('使用平台操作账号，管理应用、身份目录与平台权限。')).toHaveCount(0);
        await checkSurface(page);
        await checkAuthControls(page);
        await page.screenshot({ path: testInfo.outputPath(route.replaceAll('/', '-') + '.png'), fullPage: true });
    }
});
test('@compact 管理登录品牌标题与能力图标在短屏幕保持对齐', async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 1039, height: 582 });
    await page.goto('/#/login');
    await expect(page.locator('.auth-story h1')).toBeVisible();
    const layout = await page.locator('.auth-story').evaluate(root => {
        const title = root.querySelector('h1')!.getBoundingClientRect();
        const shield = root.querySelector('.auth-shield')!.getBoundingClientRect();
        const centers = [...root.querySelectorAll('.auth-capabilities > div')].map(item => {
            const icon = item.querySelector('.auth-cap-icon')!.getBoundingClientRect();
            const label = item.querySelector('strong')!.getBoundingClientRect();
            return { icon: icon.left + icon.width / 2, label: label.left + label.width / 2 };
        });
        return { titleBottom: title.bottom, shieldTop: shield.top, centers };
    });
    expect(layout.titleBottom).toBeLessThan(layout.shieldTop);
    expect(layout.centers).toHaveLength(3);
    for (const item of layout.centers) expect(Math.abs(item.icon - item.label)).toBeLessThan(2);
    for (let i = 1; i < layout.centers.length; i++) expect(layout.centers[i].icon - layout.centers[i - 1].icon).toBeGreaterThan(100);
    await page.screenshot({ path: testInfo.outputPath('login-brand-1039.png'), fullPage: true });
});

test('@compact 管理登录服务无响应时显示清晰错误提示', async ({ page }, testInfo) => {
    await page.route('**/api/idaas/auth/login', route => route.fulfill({ status: 503, contentType: 'text/html', body: 'Service unavailable' }));
    await page.goto('/#/login');
    await page.getByLabel('管理账号').fill('idaas');
    await page.getByLabel('登录密码').fill('invalid-password');
    await page.getByRole('button', { name: /登录控制台/ }).click();
    const error = page.locator('.ant-message-notice-error');
    await expect(error).toContainText('服务器无响应，请稍后再试。');
    await expect(page.locator('.auth-login-error')).toHaveCount(0);
    await page.screenshot({ path: testInfo.outputPath('login-server-unavailable.png'), fullPage: true });
});
test('@compact 忘记密码提交后显示统一回执并允许切换账号类型', async ({ page }) => {
    const { recoveryRequests } = await installContract(page);
    await page.goto('/#/auth/workforce/forgot');
    await page.getByLabel('登录账号', { exact: true }).fill('someone.account');
    await page.getByRole('button', { name: '提交找回申请' }).click();
    await expect(page.getByText('申请已提交', { exact: true })).toBeVisible();
    expect(recoveryRequests).toEqual([{ username: 'someone.account', realm: 'platform', domain: 'workforce' }]);
    await page.getByText('统一认证账号', { exact: true }).click();
    await page.getByText('公众身份', { exact: true }).click();
    await page.getByLabel('登录账号', { exact: true }).fill('another.account');
    await page.getByRole('button', { name: '提交找回申请' }).click();
    await expect(page.getByText('申请已提交', { exact: true })).toBeVisible();
    expect(recoveryRequests[1]).toEqual({ username: 'another.account', realm: 'auth-account', domain: 'public' });
});
test('@compact 已删除的设计页面、个人菜单、帮助与搜索入口', async ({ page }) => {
    await login(page);
    await page.goto('/#/console/workforce/design-system');
    await expect(page.getByText('页面不存在', { exact: true })).toBeVisible();
    await page.goto('/#/console/workforce/overview');
    await page.getByLabel('使用帮助', { exact: true }).click();
    await expect(page.getByRole('dialog')).not.toContainText(/Mock|演示|评审|设计规范|恢复初始/iu);
    await page.keyboard.press('Escape');
    await page.keyboard.press('Control+k');
    await page.getByLabel('全局搜索内容').fill('设计规范');
    await expect(page.getByText('未找到符合条件的内容', {exact:true})).toBeVisible();
});
for (const width of [1440, 390]) test(`@compact 抽屉、弹窗、宽表及长字段 ${width}px`, async ({ page }, testInfo) => {
    test.setTimeout(90_000);
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    await login(page);
    await page.goto('/#/console/workforce/organization?user=u1');
    await expect(page.getByText('用户信息', { exact: true })).toBeVisible();
    await checkSurface(page);
    await page.screenshot({ path: testInfo.outputPath('user-profile.png'), fullPage: true });
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: /新建用户$/ }).click();
    const editor = page.getByRole('dialog');
    await expect(editor.getByLabel('账号', { exact: true })).toBeVisible();
    const labels = (await editor.locator('.ant-form-item-label label').allTextContents()).map(label => label.trim());
    expect(labels.slice(0, 6)).toEqual(['账号', '姓名', '邮箱', '手机号', '组织机构', '岗位']);
    await expect(editor.getByText('请填写以下信息。', { exact: false })).toHaveCount(0);
    await expect(editor.getByLabel('身份类型', { exact: true })).toHaveCount(0);
    await expect(editor.getByLabel('初始密码', { exact: true })).toHaveCount(0);
    await expect(editor.getByText('创建新账号', { exact: true })).toHaveCount(0);
    await expect(editor.getByText(/暂无岗位，请到/)).toHaveCount(0);
    await editor.getByLabel('岗位', { exact: true }).click();
    await expect(page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').getByText(/暂无岗位，请到/)).toBeVisible();
    await page.keyboard.press('Escape');
    await editor.getByLabel('组织机构', { exact: true }).click();
    await expect(editor.locator('.ant-tree-select .ant-select-prefix .anticon-apartment')).toHaveCount(0);
    await expect(page.locator('.ant-select-tree')).toBeVisible();
    await expect(page.locator('.ant-select-tree-treenode:not([aria-hidden="true"])').first()).toBeVisible();
    await page.keyboard.press('Escape');
    await page.getByRole('dialog').getByLabel('姓名', {exact:true}).fill('华东区域技术管理与业务协作中心负责人');
    await checkSurface(page);
    await page.screenshot({ path: testInfo.outputPath('user-editor-long-label.png'), fullPage: true });
    await page.goto('/#/console/workforce/apps/a1');
    await page.getByRole('tab', { name: /^角色/ }).click();
    await page.getByRole('button', { name: /新建角色$/ }).click();
    await checkSurface(page);
    await page.screenshot({ path: testInfo.outputPath('role-editor.png'), fullPage: true });
    await page.goto('/#/console/workforce/grants/users?user=u1');
    await expect(page.getByText('此功能尚未开通', { exact: true })).toBeVisible();
    await checkSurface(page);
});
for (const domain of ['workforce'] as const) test(`@compact ${domain} 全部应用详情页签`, async ({ page }, testInfo) => {
    test.setTimeout(180_000);
    await login(page);
    const ids = Array.from({ length: 9 }, (_, i) => `a${i + 1}`);
    for (const appId of ids) {
        await page.goto(`/#/console/${domain}/apps/${appId}`);
        await expect(page.locator('.application-detail-hero')).toBeVisible();
        const tabs = page.getByRole('tab');
        for (let i = 0; i < await tabs.count(); i++) {
            await tabs.nth(i).click();
            await checkSurface(page);
            await page.screenshot({ path: testInfo.outputPath(`${appId}-tab-${i}.png`), fullPage: true });
        }
    }
});

for (const width of [1440, 390]) test(`@compact 公众工作区保留未开通边界 ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    await login(page);
    await page.goto('/#/console/public/overview');
    await expect(page.getByText('当前账号无此页面访问权限', { exact: true })).toBeVisible();
    await checkSurface(page);
});
