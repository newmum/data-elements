import { expect, test } from '@playwright/test';
import { installConsoleContract, login } from './helpers/backend-contract';

test('未测试通过或未启用的接收配置不能从同步对象页下发', async ({ page }) => {
    const { session } = await installConsoleContract(page);
    session.editableTables = ['tasks'];
    let createRequests = 0;
    await page.route(/\/api\/idaas\/provision\//, async route => {
        const path = new URL(route.request().url()).pathname;
        let data: unknown;
        if (path.endsWith('/configs')) data = [
            { id: 'disabled', name: '未启用配置', enabled: false, lastTestResult: 'SUCCESS' },
            { id: 'untested', name: '未通过测试配置', enabled: true, lastTestResult: 'FAILED' },
        ];
        else if (path.endsWith('/tasks') || path.endsWith('/options')) data = [];
        else if (path.endsWith('/create')) { createRequests++; data = { id: 'unexpected' }; }
        else return route.fulfill({ status: 404, json: { code: 404, message: '接口未开通' } });
        await route.fulfill({ json: { code: 200, data } });
    });

    await login(page);
    await page.goto('/#/console/workforce/sync/entities');
    await expect(page.getByText('尚无已测试并启用的接收配置')).toBeVisible();
    await expect(page.getByRole('button', { name: /下发全部对象/ })).toBeDisabled();
    expect(createRequests).toBe(0);
});

for (const count of [20, 100]) test(`${count} 行同步对象只读取固定三个下发摘要接口`, async ({ page }) => {
    const { session, database } = await installConsoleContract(page);
    session.editableTables = ['tasks'];
    database.users = database.users.filter(user => user.domain === 'workforce').slice(0, count);
    expect(database.users).toHaveLength(count);
    const reads: string[] = [];
    await page.route(/\/api\/idaas\/provision\//, async route => {
        const path = new URL(route.request().url()).pathname;
        if (!['/api/idaas/provision/configs', '/api/idaas/provision/tasks', '/api/idaas/provision/options'].includes(path)) {
            return route.fulfill({ status: 404, json: { code: 404, message: '接口未开通' } });
        }
        reads.push(path);
        await route.fulfill({ json: { code: 200, data: [] } });
    });
    await login(page);
    await page.goto('/#/console/workforce/sync/entities');
    await expect(page.getByText('尚无已测试并启用的接收配置')).toBeVisible();
    await expect(page.getByText('接口未开通', { exact: true })).toHaveCount(0);
    expect(reads.sort()).toEqual([
        '/api/idaas/provision/configs', '/api/idaas/provision/configs',
        '/api/idaas/provision/options', '/api/idaas/provision/options',
        '/api/idaas/provision/tasks', '/api/idaas/provision/tasks',
    ]);
});
