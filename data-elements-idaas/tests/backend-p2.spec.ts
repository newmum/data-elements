import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

test.skip(process.env.IDAAS_LIVE_TEST !== '1', '显式启用本地真实后端验收；不使用接口替身');
for (const width of [1440, 390, 1920]) test(`P2 真实平台目录与下发页面 ${width}px`, async ({ page }, info) => {
    test.setTimeout(120000);
    const privateRoot = join(process.env.USERPROFILE!, '.codex/private/idaas-provision');
    const session = JSON.parse(readFileSync(join(privateRoot, 'platform-session.json'), 'utf8'));
    await page.addInitScript(token => sessionStorage.setItem('iam.frontend.backend.token', token), session.token);
    const configsResponse = await page.request.get('/api/idaas/provision/configs', { headers: { token: session.token } });
    expect(configsResponse.ok()).toBe(true);
    const configsEnvelope = await configsResponse.json();
    expect(configsEnvelope.code).toBe(0);
    const readyConfigs = (configsEnvelope.data as Array<{ enabled: boolean; lastTestResult?: string }>).filter(config => config.enabled && config.lastTestResult === 'SUCCESS');
    await page.setViewportSize({ width, height: 1000 });
    const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
    page.on('console', msg => { if (msg.type() === 'error') console.log('P2 browser error:', msg.text()); });
    let downlinkRequests = 0;
    page.on('request', request => { if (/\/idaas\/provision\/(create|execute)$/.test(new URL(request.url()).pathname)) downlinkRequests++; });
    for (const path of ['sync/configs', 'sync/entities', 'sync/tasks', 'organization']) {
        await page.goto(`/#/console/workforce/${path}`);
        if (path === 'organization') await expect(page.locator('.organization-layout .table-card')).toBeVisible({ timeout: 20000 });
        else await expect(page.locator('.page-heading')).toBeVisible({ timeout: 20000 });
        await expect(page.getByText('工作区加载失败', { exact: true })).toHaveCount(0);
        if (path === 'sync/configs') {
            await expect(page.getByText('接收系统配置', { exact: true })).toBeVisible();
        }
        if (path === 'sync/entities') {
            const downlink = page.getByRole('button', { name: /下发全部对象/ });
            if (readyConfigs.length === 0) {
                await expect(page.getByText('尚无已测试并启用的接收配置')).toBeVisible();
                await expect(downlink).toBeDisabled();
            } else {
                await expect(downlink).toBeEnabled();
            }
        }
        if (path === 'sync/tasks') await expect(page.locator('.ant-spin-spinning')).toHaveCount(0, { timeout: 20000 });
        if (path === 'organization') {
            await page.getByRole('button', { name: /新建用户/ }).click({ timeout: 10000 });
            await expect(page.getByLabel('账号', { exact: true })).toBeVisible();
            await expect(page.getByLabel('初始密码', { exact: true })).toHaveCount(0);
            await page.getByRole('button', { name: /^取\s*消$/ }).click();
        }
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
        expect(overflow).toBeLessThanOrEqual(1);
        await page.screenshot({ path: info.outputPath(`${path.replace('/', '-')}-${width}.png`), fullPage: true, animations: 'disabled' });
    }
    expect(errors).toEqual([]);
    expect(downlinkRequests).toBe(0);
});
