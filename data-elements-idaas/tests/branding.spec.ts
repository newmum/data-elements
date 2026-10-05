import { expect, test } from '@playwright/test';
import { installConsoleContract } from './helpers/backend-contract';

for (const width of [390, 1440, 1920]) test(`系统页面不显示产品名和工程名 ${width}px`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    const { database } = await installConsoleContract(page);
    // Old names returned by both the anonymous brand endpoint and workspace bootstrap.
    database.settings.workforce.title = '卓鉴统一身份管理平台';
    database.settings.workforce.subtitle = 'data-elements-idaas 安全访问';
    database.settings.public.title = '卓剑统一身份管理平台';

    await page.goto('/#/login');
    await expect(page.locator('.auth-logo strong')).toHaveText('统一身份管理平台');
    await expect(page).toHaveTitle('统一身份管理平台');
    await expect(page.locator('.auth-eyebrow')).toHaveCount(0);
    await expect(page.locator('body')).not.toContainText(/卓[鉴剑]|data-elements-idaas/i);
    await page.screenshot({ path: info.outputPath(`system-login-${width}.png`), fullPage: true, animations: 'disabled' });

    await page.getByLabel('管理账号').fill('admin');
    await page.getByLabel('登录密码').fill('Contract-pass-2026');
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await expect(page).toHaveURL(/console\/workforce\/overview/);
    await expect(page.locator('.page-heading')).toHaveCount(0);
    await expect(page.getByText('身份用户总数', { exact: true })).toBeVisible();
    await expect(page.locator('.app-footer')).toHaveCount(0);
    await expect(page.locator('body')).not.toContainText(/卓[鉴剑]|data-elements-idaas/i);
    await expect(page).toHaveTitle(/统一身份管理平台/);
    await page.screenshot({ path: info.outputPath(`system-console-${width}.png`), fullPage: true, animations: 'disabled' });

    await page.goto('/#/auth/public/login');
    await expect(page.locator('.auth-logo strong')).toHaveText('统一身份管理平台');
    await expect(page.locator('body')).not.toContainText(/卓[鉴剑]|data-elements-idaas/i);
});
