import { expect, test } from '@playwright/test';
import { installConsoleContract } from './helpers/backend-contract';

for (const width of [1360, 390]) test(`总览审计摘要显示业务名称 ${width}px`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 900 });
    const { database } = await installConsoleContract(page);
    const app = database.apps.find(item => item.domain === 'workforce')!;
    const date = new Date().toISOString();
    database.logs = [
        { ...database.logs[0], id: 'audit-app', domain: 'workforce', appId: app.id, type: 'operation', action: 'applications:bind', target: app.id, actor: 'operator-opaque-id', createdAt: date, updatedAt: date },
        { ...database.logs[0], id: 'audit-setting', domain: 'workforce', appId: app.id, type: 'operation', action: 'settings:save', target: '2084109831682699264', actor: 'operator-opaque-id', createdAt: date, updatedAt: date },
        { ...database.logs[0], id: 'audit-domain', domain: 'workforce', appId: app.id, type: 'operation', action: 'profile:domain', target: '2084109831682699264', actor: 'operator-opaque-id', createdAt: date, updatedAt: date },
    ];
    const auditRows = [
        { id: 'audit-app', event_type: 'OPERATION', event_time: date, actor_id: 'operator-opaque-id', actor_name: '张管理员', action: 'applications:bind', object_id: app.id, result: 'SUCCESS', trace_id: 'trace-app' },
        { id: 'audit-setting', event_type: 'OPERATION', event_time: date, actor_id: 'operator-opaque-id', actor_name: '张管理员', action: 'settings:save', object_id: '2084109831682699264', result: 'SUCCESS', trace_id: 'trace-setting' },
    ];
    await page.route('**/api/idaas/audit/list*', route => {
        const params = new URL(route.request().url()).searchParams;
        const list = params.get('mode') === 'trend' ? [{ date: date.slice(0, 10), count: 1, people: 1, failures: 0 }] : auditRows;
        return route.fulfill({ json: { code: 200, data: { list, total: list.length } } });
    });
    await page.route('**/api/idaas/logs/query?*', route => {
        const params = new URL(route.request().url()).searchParams;
        if (params.get('operation') !== 'page' || params.get('kind') !== 'operations') return route.fallback();
        const list = [{ tid: auditRows[0].id, event_time: date, actor_name: '张管理员', operation_name: '核验应用连接', target_name: app.name, result: 'SUCCESS' }];
        return route.fulfill({ json: { code: 200, data: { list, total: list.length, page: 1, size: 20 } } });
    });

    await page.goto('/#/login');
    await page.getByLabel('管理账号').fill('admin');
    await page.getByLabel('登录密码').fill('Contract-pass-2026');
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page).toHaveURL(/console\/workforce\/overview/);
    await expect(page.locator('.page-title')).toHaveCount(0);
    await expect(page.locator('.dashboard-bottom')).toContainText('身份分布');
    await expect(page.locator('.dashboard-bottom')).toContainText('核验应用连接');
    await expect(page.locator('.dashboard-bottom')).toContainText('保存平台设置');
    await expect(page.locator('.dashboard-bottom')).toContainText(app.name);
    await expect(page.locator('.dashboard-bottom')).toContainText('张管理员');
    await expect(page.locator('.dashboard-three')).toContainText('核验应用连接');
    await expect(page.locator('.dashboard-three')).toContainText('相关平台设置');
    await expect(page.locator('.dashboard-bottom')).not.toContainText(/applications:bind|settings:save|profile:domain|2084109831682699264|operator-opaque-id/);
    if (width === 390) {
        await expect(page.locator('.dashboard-audit-list')).toBeVisible();
        await expect(page.locator('.dashboard-audit-table')).toBeHidden();
        await expect(page.locator('.dashboard-audit-item-top time').first()).toHaveCSS('font-size', '14px');
        await expect(page.locator('.dashboard-audit-item').first()).toContainText(app.name);
    } else {
        await expect(page.locator('.dashboard-audit-table')).toBeVisible();
        await expect(page.locator('.dashboard-audit-time').first()).toHaveCSS('font-size', '14px');
    }
    await expect(page.locator('.identity-distribution [role="meter"]')).toHaveCount(3);
    await page.screenshot({ path: info.outputPath(`overview-${width}.png`), fullPage: true, animations: 'disabled' });
    if (width === 390) expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(391);

    await page.locator('.dashboard-bottom').getByRole('button', { name: /查看全部/ }).click();
    await page.getByRole('tab', { name: '操作日志', exact: true }).click();
    const operations = page.locator('.log-page .el-table__body');
    await expect(operations).toContainText('核验应用连接');
    await expect(operations).toContainText(app.name);
});
