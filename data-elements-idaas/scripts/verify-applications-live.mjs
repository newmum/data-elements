/** Read the real local workspace and verify the application ordering controls. */
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { chromium } from '@playwright/test';
import { resolve } from 'node:path';

const output = resolve(import.meta.dirname, '../../logs/idaas/applications-live');

const username = process.env.IDAAS_LIVE_USER;
const password = process.env.IDAAS_LIVE_PASSWORD;
if (!username || !password) throw new Error('Set IDAAS_LIVE_USER and IDAAS_LIVE_PASSWORD');
const loginResponse = await fetch('http://localhost:8088/idaas/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password, domain: 'workforce' }),
});
const login = await loginResponse.json();
assert.equal(login.code, 0, 'Platform login failed');
const browser = await chromium.launch({ channel: 'msedge', headless: true });
try {
  await mkdir(output, { recursive: true });
  for (const width of [1360, 390]) {
    const context = await browser.newContext({ viewport: { width, height: 844 } });
    await context.addInitScript(token => sessionStorage.setItem('iam.frontend.backend.token', token), login.data.token);
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('http://localhost:3005/#/console/workforce/apps');
    await page.getByRole('heading', { name: '应用管理', exact: true }).waitFor();
    await page.getByText('公安元数据管理平台', { exact: true }).first().waitFor();
    const cards = page.locator('.application-card');
    assert.match(await cards.first().innerText(), /公安元数据管理平台/);
    const labels = await page.locator('.metric-grid.four .metric-card').allTextContents();
    assert.deepEqual(labels.map(text => ['应用数量', '角色数量', '用户数量', '权限数量'].find(label => text.includes(label))),
      ['应用数量', '角色数量', '用户数量', '权限数量']);
    assert.equal(await page.locator('.account-realm-badge').count(), 0);
    const toolbar = page.locator('.inline-toolbar');
    assert.match(await toolbar.innerText(), /共 4 个应用/);
    assert.equal(await toolbar.getByRole('button', { name: '新建应用' }).count(), 1);
    await toolbar.getByRole('button', { name: '调整顺序' }).click();
    const dialog = page.getByRole('dialog', { name: '调整应用顺序' });
    await dialog.waitFor();
    assert.match(await dialog.locator('.application-sort-item').first().innerText(), /公安元数据管理平台/);
    await dialog.screenshot({ path: resolve(output, `application-sort-dialog-${width}.png`) });
    await dialog.getByRole('button', { name: '下移公安元数据管理平台' }).click();
    assert.match(await dialog.locator('.application-sort-item').nth(1).innerText(), /公安元数据管理平台/);
    const moveToTop = dialog.locator('.application-sort-item').nth(1).locator('button').first();
    assert.match(await moveToTop.innerText(), /置\s*顶/);
    await moveToTop.click();
    assert.match(await dialog.locator('.application-sort-item').first().innerText(), /公安元数据管理平台/);
    await dialog.getByRole('button', { name: '保存顺序' }).click();
    await dialog.waitFor({ state: 'hidden' });
    await toolbar.locator('.ant-segmented-item').nth(1).click();
    assert.match(await page.locator('.table-card .ant-table-row').first().innerText(), /公安元数据管理平台/);
    await page.reload();
    await page.getByRole('heading', { name: '应用管理', exact: true }).waitFor();
    assert.match(await page.locator('.application-card').first().innerText(), /公安元数据管理平台/);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
    assert.deepEqual(errors, []);
    await page.screenshot({ path: resolve(output, `application-list-live-${width}.png`), fullPage: true });
    await context.close();
  }
  process.stdout.write('Application list, saved order, toolbar, and responsive layout passed.\n');
} finally {
  await browser.close();
}
