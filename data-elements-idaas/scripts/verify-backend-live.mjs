import { chromium } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';

const output = process.env.IDAAS_LIVE_OUTPUT;
if (!output || !process.env.IDAAS_TEST_USERNAME || !process.env.IDAAS_TEST_PASSWORD) throw new Error('Provide live verification environment');
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const errors = [];
page.on('pageerror', e => errors.push(e.message));
const checks = [];
let syntheticUser;
let loginToken;
try {
    await page.goto('http://localhost:3005/#/login');
    await page.getByLabel('管理账号').fill(process.env.IDAAS_TEST_USERNAME);
    await page.getByLabel('登录密码').fill(process.env.IDAAS_TEST_PASSWORD);
    await page.getByLabel('所属租户').click();
    await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').getByText('公安行业场景', { exact: true }).click();
    const loginResponse = page.waitForResponse(r => r.url().endsWith('/api/idaas/auth/login'));
    await page.getByRole('button', { name: /登录控制台/ }).click();
    const login = await (await loginResponse).json();
    assert.equal(login.code, 0);
    loginToken=login.data.token;
    await page.waitForURL(/console\/workforce\/overview/);
    await page.locator('.page-heading').waitFor();
    checks.push('real_ui_login');
    await page.reload();
    await page.locator('.page-heading').waitFor();
    checks.push('real_session_restore');
    if (process.env.IDAAS_LIVE_WRITE === '1') {
        await page.goto('http://localhost:3005/#/console/workforce/organization');
        await page.getByRole('button', { name: /新建用户$/ }).click();
        const drawer=page.getByRole('dialog');
        const account='p1ui'+crypto.randomUUID().replaceAll('-','').slice(0,12);
        await drawer.getByLabel('姓名', {exact:true}).fill('P1页面验收'+account);
        await drawer.getByLabel('账号', {exact:true}).fill(account);
        await drawer.getByLabel('初始密码', {exact:true}).fill(crypto.randomUUID());
        await drawer.getByLabel('岗位', {exact:true}).fill('验收岗位');
        const savedResponse=page.waitForResponse(r=>r.url().endsWith('/api/idaas/users/save'));
        await drawer.getByRole('button', {name:/保\s*存/}).click();
        const saved=await (await savedResponse).json();
        assert.equal(saved.code,0);
        syntheticUser={id:saved.data.id,version:saved.data.version,account};
        await drawer.waitFor({state:'hidden'});
        await fs.writeFile(path.join(output,'synthetic-user.json'),JSON.stringify(syntheticUser));
        checks.push('real_ui_user_creation');
    }
    const appId = login.data.session.allowedAppIds[0];
    for (const width of [390, 1440, 1920]) {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
        for (const route of ['overview', 'organization', 'apps', `apps/${appId}`, 'audit/operations', 'audit/logins']) {
            await page.goto(`http://localhost:3005/#/console/workforce/${route}`);
            await page.locator(route.startsWith('apps/') ? '.application-detail-hero' : '.page-heading').waitFor();
            await page.evaluate(() => document.fonts.ready);
            const surface = await page.evaluate(() => ({ viewport: innerWidth, documentWidth: document.documentElement.scrollWidth,
                failed: document.body.innerText.includes('页面暂时无法显示') }));
            assert.equal(surface.failed, false);
            assert.ok(surface.documentWidth <= surface.viewport + 1, `${route} document overflows ${width}`);
            await page.screenshot({ path: path.join(output, `${width}-${route.replaceAll('/', '-')}.png`), fullPage: true });
            checks.push(`${width}:${route}`);
        }
    }
    assert.deepEqual(errors, []);
    await fs.writeFile(path.join(output, 'result.json'), JSON.stringify({ status: 'passed', source: 'real shared backend / baseline_ga_old', checks, pageErrors: errors }, null, 2));
    console.log(JSON.stringify({ status: 'passed', total: checks.length, pageErrors: errors }));
} finally {
    if (syntheticUser && loginToken) {
        const result=await page.request.post('http://localhost:3005/api/idaas/users/remove', {
            headers:{token:loginToken},data:{id:syntheticUser.id,version:syntheticUser.version,requestId:crypto.randomUUID()}
        });
        const removed=await result.json();
        assert.equal(removed.code,0,'synthetic UI account tenant cleanup');
    }
    await context.close();await browser.close();
}
