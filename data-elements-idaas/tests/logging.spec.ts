import {expect,test} from '@playwright/test';
import {installContract,login} from './helpers/backend-contract';
test.beforeEach(async({page})=>{await installContract(page);});
for(const width of [1440,390])test(`@logging 单一低代码日志页面 ${width}`,async({page},info)=>{
 await page.setViewportSize({width,height:900});await login(page);await page.goto('/#/console/workforce/audit/logs');
 await expect(page.getByRole('tab',{name:'登录日志',exact:true})).toBeVisible();
 await expect(page.locator('.log-summary,.log-heading,.log-page .page-title')).toHaveCount(0);
 await expect(page.locator('.log-page .el-tabs__item')).toHaveCount(6);
 await expect(page.getByRole('tab',{name:'安全日志',exact:true})).toBeVisible();
 await expect(page.getByRole('tab',{name:'归档日志',exact:true})).toBeVisible();
 await expect(page.locator('.log-context,.log-range-hint')).toHaveCount(0);
 await expect(page.locator('.log-page .el-table__header th').filter({hasText:'序号'})).toBeVisible();
 const headers=await page.locator('.log-page .el-table__header th').allTextContents();
 expect(headers.slice(0,5).map(x=>x.trim().replace(/\s+/g,''))).toEqual(['序号','操作人员','操作时间','功能路径','具体操作']);
 await expect(page.getByText('后台管理 → 应用管理',{exact:true})).toBeVisible();
 await page.getByRole('tab',{name:'导出日志',exact:true}).click();await expect(page.getByRole('columnheader',{name:'导出格式',exact:false})).toBeVisible();
 await page.getByRole('button',{name:'日志策略',exact:true}).click();
 await expect(page.getByRole('dialog',{name:'日志策略',exact:true})).toBeVisible();
 await expect(page.getByRole('spinbutton',{name:'在线保留天数',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'取消',exact:true}).click();
 await expect(page.getByRole('tab',{name:'导出日志',exact:true})).toHaveAttribute('aria-selected','true');
 const geometry=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));expect(geometry.scroll).toBeLessThanOrEqual(geometry.width+1);
 await page.screenshot({path:info.outputPath('logging.png'),fullPage:true});
});
test('@logging 20与100行只发一次列表请求',async({page})=>{
 let reads=0;await page.route('**/api/idaas/logs/query?*',async route=>{
  const p=new URL(route.request().url()).searchParams;if(p.get('operation')!=='page')return route.fallback();
  reads++;const size=Number(p.get('size')||20);await route.fulfill({json:{code:0,data:{list:Array.from({length:size},(_,i)=>({tid:String(i),actor_name:'人员'+i,actor_account:'account'+i,event_time:new Date().toISOString(),menu_path:'后台管理 → 用户管理',operation_name:'保存用户',result:'SUCCESS'})),total:200,page:1,size}}});
 });
 await login(page);await page.goto('/#/console/workforce/audit/logs');await expect(page.locator('.log-page .el-table__body tbody tr')).toHaveCount(20);expect(reads).toBe(1);
 await page.locator('.log-pager .el-select').click();await page.getByRole('option',{name:'100条/页',exact:true}).click();await expect(page.locator('.log-page .el-table__body tbody tr')).toHaveCount(100);expect(reads).toBe(2);
});
