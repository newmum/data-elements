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
        await checkSurface(page);
        await checkAuthControls(page);
        await page.screenshot({ path: testInfo.outputPath(route.replaceAll('/', '-') + '.png'), fullPage: true });
    }
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
    await expect(page.getByText('身份档案', { exact: true })).toBeVisible();
    await checkSurface(page);
    await page.screenshot({ path: testInfo.outputPath('user-profile.png'), fullPage: true });
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: /新建用户$/ }).click();
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
