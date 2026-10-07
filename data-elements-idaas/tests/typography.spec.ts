import { expect, test, type Page, type TestInfo } from '@playwright/test';
import { domainPages, authPaths } from '../src/app/navigation-data';
import { installContract, login } from './helpers/backend-contract';
test.beforeEach(async ({ page }) => { await installContract(page); });

// REAL React/Ant Design regression suite. Do not report it as run until Playwright executes it.
const rules: Array<[string,number]> = [
 ['.page-heading',24], ['.page-description',13],
 ['.section-intro-title',16], ['.ant-card-head-title',16], ['.table-title',16],
 ['.application-title-text',16], ['.application-detail-hero h1',24],
 ['.metric-label',14], ['.metric-value',28], ['.metric-desc',13],
 ['.business-navigation .ant-menu-title-content',14], ['.header-breadcrumb .ant-breadcrumb-link',14],
 ['.account-copy > span',14], ['.ant-form-item-label > label',14],
 ['.setting-title',14], ['.setting-description',13], ['.iam-panel-title',18],
 ['.auth-page-title',24], ['.donut-center strong',24],
];
async function audit(page:Page, info:TestInfo, name:string) {
 await page.evaluate(()=>document.fonts.ready);
 await expect(page.locator('body')).not.toContainText(/Mock|演示|评审|模拟|设计规范|本轮/iu);
 await expect(page.getByText('页面暂时无法显示',{exact:true})).toHaveCount(0);
 const measured=await page.evaluate((roles)=>{
  const visible=(e:Element)=>{const r=e.getBoundingClientRect();return r.width>0 && r.height>0 && getComputedStyle(e).visibility!=='hidden';};
  const measurements=roles.flatMap(([selector,expected])=>Array.from(document.querySelectorAll(selector)).filter(visible).map(e=>{
    const style=getComputedStyle(e);return {selector,text:(e.textContent||'').trim().slice(0,55),expected,fontSize:parseFloat(style.fontSize),lineHeight:style.lineHeight,fontWeight:style.fontWeight};
  }));
  const chartLabels=Array.from(document.querySelectorAll<SVGTextElement>('.chart-wrap text')).filter(visible).map(e=>{
   const m=e.getScreenCTM();return {text:e.textContent,cssSize:parseFloat(getComputedStyle(e).fontSize),renderedSize:parseFloat(getComputedStyle(e).fontSize)*(m?Math.hypot(m.a,m.b):1)};
  });
  const saveBar=document.querySelector('.settings-footer');
  const coveredInputs=saveBar?Array.from(document.querySelectorAll('.settings-layout input,.settings-layout textarea,.settings-layout select')).filter(visible).filter(e=>{
    const a=saveBar.getBoundingClientRect(),b=e.getBoundingClientRect();
    return Math.min(a.right,b.right)>Math.max(a.left,b.left)+1&&Math.min(a.bottom,b.bottom)>Math.max(a.top,b.top)+1;
  }).length:0;
  const panels=Array.from(document.querySelectorAll('.ant-drawer-content-wrapper,.ant-modal')).filter(visible).map(e=>({width:e.getBoundingClientRect().width}));
  return {measurements,chartLabels,panels,coveredInputs,viewport:innerWidth,documentWidth:document.documentElement.scrollWidth};
 },rules);
 for(const m of measured.measurements) expect(m.fontSize,`${name}: ${m.selector} ${m.text}`).toBe(m.expected);
 for(const m of measured.chartLabels) expect(m.renderedSize,`${name}: SVG ${m.text}`).toBeGreaterThanOrEqual(11.9);
 expect(measured.documentWidth,`${name}: document overflow`).toBeLessThanOrEqual(measured.viewport+1);
 expect(measured.coveredInputs,`${name}: save bar covers inputs`).toBe(0);
 for(const p of measured.panels) expect(p.width).toBeLessThanOrEqual(measured.viewport+1);
 await info.attach(`${name}-type-metrics`,{body:JSON.stringify(measured,null,2),contentType:'application/json'});
 await page.screenshot({path:info.outputPath(`${name}.png`),fullPage:true});
}
for(const domain of ['workforce'] as const) {
 for(const width of [1440,1920,390]) test(`@typography ${domain}全部工作区 ${width}`,async({page},info)=>{
  test.setTimeout(240000);
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));
  await page.setViewportSize({width,height:width===390?844:1000});await login(page);
  for(const route of domainPages(domain)) {
   await page.goto(`/#/console/${domain}/${route.key}`);
   if(route.key==='overview' || route.key==='apps') await expect(page.locator('.page-heading')).toHaveCount(0);
   else if(route.key==='organization') {
    await expect(page.locator('.organization-layout .table-card')).toBeVisible();
    await expect(page.locator('.page-heading')).toHaveCount(0);
   }
   else await expect(page.locator('.page-heading')).toBeVisible();
   await audit(page,info,route.key.replaceAll('/','-'));
  }
  expect(errors).toEqual([]);
 });
 test(`@typography ${domain}全部应用与页签`,async({page},info)=>{
  test.setTimeout(240000);await login(page);
  const ids=Array.from({length:9},(_,i)=>`a${i+1}`);
  for(const id of ids){
   await page.goto(`/#/console/${domain}/apps/${id}`);
   await expect(page.locator('.application-detail-hero')).toBeVisible();
   const tabs=page.getByRole('tab');const n=await tabs.count();
   for(let i=0;i<n;i++){await tabs.nth(i).click();await audit(page,info,`${id}-tab-${i+1}`);}
  }
 });
}
for(const width of [1440,390]) test(`@typography 全部认证入口 ${width}`,async({page},info)=>{
 test.setTimeout(90000);await page.setViewportSize({width,height:width===390?844:1000});
 for(const path of authPaths){await page.goto('/#'+path);await expect(page.locator('.auth-page-title')).toBeVisible();await audit(page,info,path.replaceAll('/','-'));}
});
for(const width of [1440,390]) test(`@typography 浮层、长字段与空结果 ${width}`,async({page},info)=>{
 test.setTimeout(180000);await page.setViewportSize({width,height:width===390?844:1000});await login(page);
 await page.goto('/#/console/workforce/organization?user=u1');
 await expect(page.getByText('用户信息',{exact:true})).toBeVisible();await audit(page,info,'user-profile');
 await page.keyboard.press('Escape');
 await page.getByRole('button',{name:/新建用户$/}).click();
 await page.getByRole('dialog').getByLabel('姓名',{exact:true}).fill('华东区域技术管理与业务协作中心负责人');
 await audit(page,info,'user-editor-long');
 await page.goto('/#/console/workforce/apps/a1');
 await page.getByRole('tab',{name:/^角色/}).click();
 await page.getByRole('button',{name:/新建角色$/}).click();await audit(page,info,'role-editor');
 await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toHaveCount(0);
 await page.goto('/#/console/workforce/apps/a1');
 await page.getByRole('tab',{name:/^资源/}).click();
 await page.getByRole('button',{name:/新建资源$/}).click();await audit(page,info,'resource-editor');
 await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toHaveCount(0);
 await page.goto('/#/console/workforce/permission-groups');
 await expect(page.getByText('此功能尚未开通',{exact:true})).toBeVisible();await audit(page,info,'permission-group-unavailable');
 await page.goto('/#/console/workforce/audit/operations');
 const detail=page.getByRole('button',{name:/详\s*情/}).first();
 await expect(detail).toBeVisible();await detail.click();await audit(page,info,'audit-event-detail');
 await page.goto('/#/console/workforce/organization');
 const search=page.locator('.table-filter input').first();await search.fill('不存在的筛选对象__2026');await search.press('Enter');
 await expect(page.getByText('未找到符合条件的结果',{exact:true})).toBeVisible();await audit(page,info,'empty-search');
 await page.goto('/#/console/workforce/overview');await page.getByLabel('使用帮助',{exact:true}).click();await audit(page,info,'help-modal');
});

for(const width of [1440,390]) test(`@typography 公众工作区未开通状态 ${width}`,async({page},info)=>{
 await page.setViewportSize({width,height:width===390?844:1000});await login(page);
 await page.goto('/#/console/public/overview');
 await expect(page.getByText('当前账号无此页面访问权限',{exact:true})).toBeVisible();await audit(page,info,'public-unavailable');
});
