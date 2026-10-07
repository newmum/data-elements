import { expect, test } from '@playwright/test';
import { installContract, login } from './helpers/backend-contract';

for (const size of [20, 100]) test(`${size} 行用户列表与机构树保持固定的目录读取次数`, async ({ page }) => {
    const { database } = await installContract(page);
    database.users = database.users.filter(user => user.domain === 'workforce').slice(0, size);
    const reads: string[] = [];
    page.on('request', request => {
        if (new URL(request.url()).pathname.endsWith('/idaas/users/list')) reads.push(request.url());
    });
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await expect(page.locator('.table-count')).toContainText(`共 ${size} 项`);
    await page.locator('.ant-pagination-options .ant-select').click();
    await page.getByRole('option', { name: `${size} 条/页` }).click();
    await expect(page.locator('.ant-table-tbody .ant-table-row')).toHaveCount(size);
    // Initial workspace hydration reads twice; changing page size adds one read at either size.
    expect(reads).toHaveLength(3);
});

test('用户搜索在输入停顿后只发一次当前范围的分页查询', async ({ page }) => {
    const { database } = await installContract(page);
    const account = database.users.find(user => user.domain === 'workforce' && user.kind !== 'admin')!.account;
    const reads: URL[] = [];
    page.on('request', request => {
        if (new URL(request.url()).pathname.endsWith('/idaas/users/list')) reads.push(new URL(request.url()));
    });
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await expect(page.locator('.table-count')).toBeVisible();
    const before = reads.length;
    const search = page.getByLabel('搜索姓名、账号或邮箱');
    await search.fill(account.slice(0, 2));
    await search.fill(account);
    await expect.poll(() => reads.length).toBe(before + 1);
    expect(reads.at(-1)!.searchParams.get('q')).toBe(account);
    expect(reads.at(-1)!.searchParams.get('domain')).toBe('workforce');
    expect(reads.at(-1)!.searchParams.get('page')).toBe('1');
    await expect(page.locator('.table-count')).toContainText('共 1 项');
});

test('组织导航和编辑选择器显示层级且没有横向滚动', async ({ page }, testInfo) => {
    await installContract(page);
    await page.setViewportSize({ width: 1360, height: 582 });
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await expect(page.getByRole('heading', { name: '用户管理' })).toHaveCount(0);
    await expect(page.getByText('维护权威身份数据，让组织与人员关系保持一致。')).toHaveCount(0);
    await expect(page.getByRole('columnheader', { name: '岗位' })).toHaveCount(0);
    await expect(page.locator('.table-filter').getByRole('button', { name: /查询|重置/ })).toHaveCount(0);
    await expect(page.locator('.table-filter-action').getByRole('button', { name: '新建用户' })).toBeVisible();
    await expect(page.locator('.sider-version, .sider-banner small')).toHaveCount(0);
    await expect(page.getByText('统一身份，连接每一份信任', { exact: true })).toHaveCount(0);
    const navigation = page.locator('.organization-tree-card .ant-tree');
    await expect(navigation).toBeVisible();
    const treeCard = await page.locator('.organization-tree-card').boundingBox();
    const tableCard = await page.locator('.organization-layout .table-card').boundingBox();
    expect(treeCard && tableCard && Math.abs(treeCard.height - tableCard.height)).toBeLessThanOrEqual(2);
    expect(await navigation.evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(2);
    await page.screenshot({ path: testInfo.outputPath('organization-navigation.png'), fullPage: true });

    await page.getByRole('button', { name: /新建用户$/ }).click();
    await page.getByRole('dialog').getByLabel('组织机构', { exact: true }).click();
    const popup = page.locator('.org-picker-popup');
    await expect(popup).toBeVisible();
    await expect(popup.locator('.org-picker-title').first()).toBeVisible();
    const arrowDirection = (selector: string) => page.locator(selector).first().evaluate(icon => getComputedStyle(icon).transform);
    await expect.poll(() => arrowDirection('.organization-tree-card .ant-tree-switcher_open .anticon svg')).toBe('none');
    await expect.poll(() => arrowDirection('.organization-tree-card .ant-tree-switcher_close .anticon svg')).toBe('matrix(0, 1, -1, 0, 0, 0)');
    await expect.poll(() => arrowDirection('.org-picker-popup .ant-select-tree-switcher_open .anticon svg')).toBe('none');
    await expect.poll(() => arrowDirection('.org-picker-popup .ant-select-tree-switcher_close .anticon svg')).toBe('matrix(0, 1, -1, 0, 0, 0)');
    const indentWidths = await page.evaluate(() => [
        document.querySelector('.organization-tree-card .ant-tree-indent-unit'),
        document.querySelector('.org-picker-popup .ant-select-tree-indent-unit'),
    ].map(element => element ? parseFloat(getComputedStyle(element).width) : 0));
    expect(indentWidths).toEqual([14, 14]);
    expect(await popup.locator('.ant-select-tree').evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(2);
    await page.screenshot({ path: testInfo.outputPath('organization-picker.png'), fullPage: true });
});

test('有权限的管理员从用户列表删除无关联人员只发起一次删除请求', async ({ page }) => {
    const { database, removed } = await installContract(page);
    const user = database.users.find(row => row.domain === 'workforce' && row.kind !== 'admin')!;
    database.grants = database.grants.filter(grant => !(grant.source === 'user' && grant.subjectId === user.id));
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await page.getByLabel('搜索姓名、账号或邮箱').fill(user.account);
    await expect(page.locator('.table-count')).toContainText('共 1 项');
    const row = page.getByRole('row').filter({ hasText: user.account });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: /更多/ }).click();
    await page.getByRole('menuitem', { name: '删除用户' }).click();
    const confirm = page.getByRole('dialog', { name: `删除用户“${user.name}”？` });
    await expect(confirm).toContainText('如有关联授权或权限组，请先解除关联');
    await confirm.getByRole('button', { name: '删除用户' }).click();
    await expect(page.getByText('用户已删除')).toBeVisible();
    expect(removed).toEqual([{ id: user.id, version: user.version }]);
    await expect(row).toHaveCount(0);
});

test('当前管理账号在人员目录中不能删除自己', async ({ page }) => {
    const { database, removed } = await installContract(page);
    const current = database.users.find(row => row.domain === 'workforce' && row.kind !== 'admin')!;
    current.account = 'contract.user';
    await login(page);
    await page.goto('/#/console/workforce/organization');
    await page.getByLabel('搜索姓名、账号或邮箱').fill(current.account);
    await expect(page.locator('.table-count')).toContainText('共 1 项');
    const row = page.getByRole('row').filter({ hasText: current.account });
    await row.getByRole('button', { name: /更多/ }).click();
    await expect(page.getByRole('menuitem', { name: '删除用户' })).toBeDisabled();
    expect(removed).toHaveLength(0);
});
