import { expect, test } from '@playwright/test';

test.skip(process.env.IDAAS_LIVE_TEST !== '1', '显式启用真实后端回归');
test.use({ trace: 'off' });

test.afterEach(async ({ page }) => {
    const password = page.getByLabel('登录密码', { exact: true });
    if (await password.count()) await password.fill('');
});

for (const width of [390, 1440, 1920]) test(`真实公安应用人员、机构与连接信息 ${width}px`, async ({ page }, info) => {
    test.setTimeout(300000);
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    await page.goto('/#/login');
    await page.getByLabel('管理账号', { exact: true }).fill(process.env.IDAAS_OPERATOR_USERNAME || 'manager');
    await page.getByLabel('登录密码', { exact: true }).fill(process.env.IDAAS_OPERATOR_PASSWORD!);
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page).toHaveURL(/console\/workforce\/overview/, { timeout: 120000 });
    await page.goto('/#/console/workforce/apps/2084109831682699264');
    await expect(page.locator('.application-detail-hero')).toBeVisible({ timeout: 60000 });
    await expect(page.getByRole('tab', { name: '人员 (5)' })).toBeVisible({ timeout: 45000 });
    await expect(page.getByRole('tab', { name: '运行绑定', exact: true })).toHaveCount(0);
    await expect(page.getByRole('tab', { name: '人员与机构资料', exact: true })).toHaveCount(0);
    const labels = await page.getByRole('tab').allTextContents();
    const roles = labels.findIndex(label => label.startsWith('角色'));
    expect(labels.slice(roles - 2, roles)).toEqual(['人员 (5)', '机构 (113)']);
    expect(labels.at(-1)).toBe('接入配置');
    for (const label of ['人员', '机构']) {
        const response = page.waitForResponse(response => response.url().includes('/idaas/assignments/list'));
        await page.getByRole('tab', { name: new RegExp(`^${label} \\(`) }).click();
        expect((await (await response).json()).code).toBe(0);
        await expect(page.locator('.ant-spin-spinning')).toHaveCount(0, { timeout: 45000 });
        await expect(page.locator('.ant-alert-error')).toHaveCount(0);
        await expect(page.getByRole('button', { name: label === '人员' ? '分配人员' : '分配机构', exact: true })).toBeVisible();
        if (label === '人员') await expect(page.getByText('尚未启用下发').first()).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
        await page.screenshot({ path: info.outputPath(`live-application-${label === '人员' ? 'persons' : 'orgs'}-${width}.png`), fullPage: true, animations: 'disabled' });
    }
    await page.getByRole('tab', { name: '概览', exact: true }).click();
    await page.getByText('应用连接信息', { exact: true }).click();
    await expect(page.getByText('租户库标识', { exact: false })).toBeVisible();
    await expect(page.getByText('2084109831682699264', { exact: true })).toBeVisible();
    await expect(page.getByText('1995678661281710081', { exact: true })).toBeVisible();
    await page.screenshot({ path: info.outputPath(`live-application-connection-${width}.png`), fullPage: true, animations: 'disabled' });
    await page.getByRole('tab', { name: '接入配置', exact: true }).click();
    await expect(page.getByText('当前应用使用独立本地账号', { exact: true })).toHaveCount(0);
    await expect(page.getByText('客户端登记与认证接入分别管理', { exact: true })).toHaveCount(0);
    expect(errors).toEqual([]);
});

test('真实应用资料同步仅在点击后读取差异，且不自动导入', async ({ page }, info) => {
    test.setTimeout(180000);
    await page.goto('/#/login');
    await page.getByLabel('管理账号', { exact: true }).fill(process.env.IDAAS_OPERATOR_USERNAME || 'manager');
    await page.getByLabel('登录密码', { exact: true }).fill(process.env.IDAAS_OPERATOR_PASSWORD!);
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page).toHaveURL(/console\/workforce\/overview/, { timeout: 120000 });
    await page.goto('/#/console/workforce/apps/2084109831682699264');
    await expect(page.getByRole('tab', { name: '资料同步' })).toBeVisible({ timeout: 60000 });
    let pulls = 0;
    page.on('request', request => { if (request.url().includes('/idaas/applications/directory-pull')) pulls++; });
    await page.getByRole('tab', { name: '资料同步' }).click();
    await expect(page.getByText('点击“查看差异”读取当前租户与平台资料')).toBeVisible();
    const compare = page.waitForResponse(response => response.url().includes('/idaas/applications/directory-compare'));
    await page.getByRole('button', { name: '查看差异' }).click();
    expect((await (await compare).json()).code).toBe(0);
    await expect(page.getByText('租户人员 5')).toBeVisible();
    await expect(page.getByText('平台机构 113')).toBeVisible();
    await expect(page.getByRole('button', { name: '导入选中差异' })).toBeDisabled();
    expect(pulls).toBe(0);
    for (const width of [390, 1440, 1920]) {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
        expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
        await page.screenshot({ path: info.outputPath(`directory-sync-${width}.png`), fullPage: true, animations: 'disabled' });
    }
});

test('广电本地资料可读且可列出待人工导入的差异', async ({ page }) => {
    test.setTimeout(180000);
    await page.goto('/#/login');
    await page.getByLabel('管理账号', { exact: true }).fill(process.env.IDAAS_OPERATOR_USERNAME || 'manager');
    await page.getByLabel('登录密码', { exact: true }).fill(process.env.IDAAS_OPERATOR_PASSWORD!);
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page).toHaveURL(/console\/workforce\/overview/, { timeout: 120000 });
    await page.goto('/#/console/workforce/apps/2084109831682699265');
    await page.getByRole('tab', { name: '资料同步' }).click();
    await page.getByRole('button', { name: '查看差异' }).click();
    await expect(page.getByText('租户人员 2')).toBeVisible({ timeout: 45000 });
    await expect(page.getByText('租户机构 30')).toBeVisible();
    await expect(page.getByText('平台人员 0')).toBeVisible();
    await expect(page.getByText('仅租户有').first()).toBeVisible();
    await expect(page.getByRole('button', { name: '导入选中差异' })).toBeDisabled();
});

test('四个应用卡片展示真实本地授权人数', async ({ page }) => {
    test.setTimeout(180000);
    await page.goto('/#/login');
    await page.getByLabel('管理账号', { exact: true }).fill(process.env.IDAAS_OPERATOR_USERNAME || 'manager');
    await page.getByLabel('登录密码', { exact: true }).fill(process.env.IDAAS_OPERATOR_PASSWORD!);
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page).toHaveURL(/console\/workforce\/overview/, { timeout: 120000 });
    await page.goto('/#/console/workforce/apps');
    for (const [name, count] of [['公安元数据管理平台', '3'], ['广电数据管理平台', '2'], ['海渔数据管理平台', '1'], ['澄天数据中台', '2']]) {
        const card = page.locator('.application-card').filter({ hasText: name });
        await expect(card.locator('.app-card-metrics strong').first()).toHaveText(count, { timeout: 45000 });
    }
});
