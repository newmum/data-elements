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

test('20 和 100 行同步对象分别只读取一次对象分页', async ({ page }) => {
    const { session } = await installConsoleContract(page);
    session.editableTables = ['tasks'];
    const count = 100;
    const pageSizes: number[] = [];
    const summaryReads: string[] = [];
    await page.route(/\/api\/idaas\/provision\//, async route => {
        const { pathname, searchParams } = new URL(route.request().url());
        let data: unknown;
        if (pathname.endsWith('/objects')) {
            expect(searchParams.get('configId')).toBe('receiver-ready');
            expect(searchParams.get('kind')).toBe('user');
            const size = Number(searchParams.get('size'));
            pageSizes.push(size);
            const list = Array.from({ length: size }, (_, index) => ({ id: `person-${index + 1}`, name: `验证人员 ${index + 1}`, version: 1, status: 'ACTIVE' }));
            data = { list, total: count, page: 1, size };
        } else {
            summaryReads.push(pathname.split('/').at(-1)!);
            data = pathname.endsWith('/configs') ? [{ id: 'receiver-ready', name: '已核验接收配置', enabled: true, lastTestResult: 'SUCCESS' }] : [];
        }
        await route.fulfill({ json: { code: 200, data } });
    });
    await login(page);
    await page.goto('/#/console/workforce/sync/entities');
    const rows = page.getByRole('row').filter({ hasText: /验证人员 \d+/ });
    await expect(rows).toHaveCount(20);
    expect(pageSizes).toEqual([20]);
    expect([...new Set(summaryReads)].sort()).toEqual(['configs', 'options']);
    const initialSummaryReads = summaryReads.length;
    await page.locator('.ant-pagination-options').getByRole('combobox').click();
    await page.getByRole('option', { name: /100\s*条/ }).click();
    await expect(rows).toHaveCount(100);
    expect(pageSizes).toEqual([20, 100]);
    expect(summaryReads).toHaveLength(initialSummaryReads);
});
