/** Real browser acceptance against the deployed central foundation, without fixture routes. */
import { chromium } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const base = process.env.IDAAS_FRONT_URL || 'http://localhost:3005';
const username = process.env.IDAAS_OPERATOR_USERNAME;
const password = process.env.IDAAS_OPERATOR_PASSWORD;
if (!username || !password) throw new Error('Provide platform acceptance credentials through environment variables.');
const output = resolve('docs/第一阶段实施20260928/界面验收');
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const checks = [];
function check(name, pass) { checks.push({ name, passed: !!pass }); if (!pass) throw new Error(name); }
try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(base + '/#/login');
    await page.getByPlaceholder('请输入管理账号').waitFor();
    await page.screenshot({ path: resolve(output, '登录-1440.png'), fullPage: true });
    const inputBox = await page.getByPlaceholder('请输入管理账号').evaluate(el => (el.closest('.ant-input-affix-wrapper') || el).getBoundingClientRect().toJSON());
    const buttonBox = await page.getByRole('button', { name: /登录控制台/ }).boundingBox();
    check('真实登录表单保留宽松高度', inputBox.height >= 40 && buttonBox.height >= 48);
    await page.getByPlaceholder('请输入管理账号').fill(username);
    await page.getByPlaceholder('请输入密码', { exact: true }).fill(password);
    await page.getByRole('button', { name: /登录控制台/ }).click();
    await page.waitForURL(/console\/workforce\/overview/);
    await page.locator('.page-heading').waitFor();
    check('真实平台登录成功', await page.locator('.page-heading').count() === 1);
    const routes = [
        ['overview', '总览'], ['system/access/admins', '管理员管理'], ['system/access/roles', '系统角色'],
        ['apps', '应用管理'], ['app-groups', '应用分组'], ['system/settings/security', '账号安全'],
        ['system/settings/branding', '系统品牌'], ['audit/api', 'API日志'], ['profile', '个人中心'],
    ];
    for (const width of [1440, 1920, 390]) {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
        for (const [route, name] of routes) {
            await page.goto(base + '/#/console/workforce/' + route);
            await page.locator('.page-heading').waitFor();
            if (route === 'system/access/admins') await page.locator('.ant-table-tbody tr.ant-table-row').first().waitFor();
            await page.waitForLoadState('networkidle');
            await page.waitForFunction(() => !document.querySelector('.ant-spin-spinning, .ant-btn-loading'));
            await page.waitForTimeout(250);
            const size = await page.evaluate(() => ({ viewport: innerWidth, page: document.documentElement.scrollWidth }));
            check(`${width}px ${name} 页面没有横向溢出`, size.page <= size.viewport + 2);
            await page.screenshot({ path: resolve(output, `${name}-${width}.png`), fullPage: true });
        }
        await page.goto(base + '/#/console/workforce/system/access/admins');
        await page.getByRole('button', { name: '新建操作账号' }).click();
        await page.getByRole('dialog').waitFor();
        await page.waitForTimeout(400);
        check(`${width}px 操作账号表单使用真实后端字段`, await page.getByLabel('登录账号', { exact: true }).isVisible());
        await page.screenshot({ path: resolve(output, `新建操作账号-${width}.png`), fullPage: true });
        await page.getByRole('dialog').getByRole('button', { name: /取\s*消/ }).click();
        await page.getByRole('dialog').waitFor({ state: 'hidden' });
        await page.getByRole('button', { name: '角色与范围', exact: true }).first().click();
        await page.getByRole('dialog').waitFor();
        await page.waitForTimeout(400);
        await page.getByLabel('第1项平台角色', { exact: true }).waitFor();
        check(`${width}px 角色分配读取已有授权`, await page.getByLabel('第1项生效时间', { exact: true }).inputValue() !== '');
        await page.screenshot({ path: resolve(output, `角色与管理范围-${width}.png`), fullPage: true });
        await page.getByRole('dialog').getByRole('button', { name: /取\s*消/ }).click();
        await page.getByRole('dialog').waitFor({ state: 'hidden' });
        await page.goto(base + '/#/console/workforce/apps/2084109831682699264');
        await page.getByRole('tab', { name: '运行绑定' }).click();
        await page.getByText('应用运行实例', { exact: true }).waitFor();
        await page.waitForLoadState('networkidle');
        check(`${width}px 公安运行绑定真实已核验`, await page.getByText('已核验', { exact: true }).isVisible());
        await page.screenshot({ path: resolve(output, `公安运行绑定-${width}.png`), fullPage: true });
    }
    check('真实页面无React运行错误', errors.length === 0);
    await context.close();
    await writeFile(resolve(output, '浏览器验收结果.json'), JSON.stringify({ completed: true, browser: 'Edge', fixtures: false, checks }, null, 2));
    console.log(JSON.stringify({ completed: true, checks: checks.length, screenshots: output }));
} finally { await browser.close(); }
