"""Checks optional independent HTML only. This is NOT React/Ant Design acceptance.
Requirements: Python playwright + installed Chromium. No OCR or image generation.
"""
import json, re, os, shutil
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT.parent / 'logs/idaas/companion'; OUT.mkdir(parents=True, exist_ok=True)
RULES = [('.preview-nav',14),('.p-crumb',14),('.page-title h1',24),('.page-title p',13),
 ('.p-card-head strong',16),('.metric-top',14),('.metric-value',28),('.metric-desc',13),
 ('.p-table th',14),('.p-table td',14),('.p-btn',14),('.p-form-label',14),
 ('.application-card h3',16),('.application-description',13),('.p-drawer-head strong',18),
 ('.detail-section h4',16),('.user-profile-hero h2',24),('.login-heading h2',24),
 ('.donut-center strong',24)]
results=[]; errors=[]
with sync_playwright() as p:
 executable=os.environ.get('CHROMIUM_EXECUTABLE') or shutil.which('chromium')
 browser=p.chromium.launch(executable_path=executable,args=['--no-sandbox'])
 page=browser.new_page();page.on('pageerror',lambda error:errors.append(str(error)))
 page.set_content((ROOT/'preview/index.html').read_text(),wait_until='load')
 def inspect(route,width):
  page.wait_for_timeout(220)
  data=page.evaluate('''(rules)=>{
   const visible=e=>{const r=e.getBoundingClientRect();return r.width>0&&r.height>0&&getComputedStyle(e).visibility!=='hidden';};
   const fonts=rules.flatMap(([selector,expected])=>Array.from(document.querySelectorAll(selector)).filter(visible).map(e=>({selector,expected,actual:parseFloat(getComputedStyle(e).fontSize),text:e.textContent.trim().slice(0,50)})));
   const svg=Array.from(document.querySelectorAll('.chart-wrap text')).map(e=>{const m=e.getScreenCTM();return {text:e.textContent,actual:parseFloat(getComputedStyle(e).fontSize)*(m?Math.hypot(m.a,m.b):1)}});
   const drawer=document.querySelector('#drawer.open');
   const bar=document.querySelector('.settings-footer');
   const overlaps=bar?Array.from(document.querySelectorAll('.settings-layout input,.settings-layout textarea,.settings-layout select')).filter(visible).filter(e=>{
     const a=bar.getBoundingClientRect(),b=e.getBoundingClientRect();return Math.min(a.right,b.right)>Math.max(a.left,b.left)+1&&Math.min(a.bottom,b.bottom)>Math.max(a.top,b.top)+1;
   }).map(e=>e.tagName):[];
   return {width:innerWidth,document:document.documentElement.scrollWidth,text:document.body.innerText,fonts,svg,overlaps,drawerWidth:drawer?drawer.getBoundingClientRect().width:0};
  }''',RULES)
  banned=re.findall(r'Mock|演示|评审|模拟|本轮|测试账号|设计规范',data.pop('text'),re.I)
  mismatches=[x for x in data['fonts'] if x['actual']!=x['expected']]
  small_svg=[x for x in data['svg'] if x['actual']<11.9]
  result={'route':route,'width':width,'overflow':data['document']-width,'drawerWidth':data['drawerWidth'],'banned':banned,'fontMismatches':mismatches,'smallChartLabels':small_svg,'coveredInputs':data['overlaps'],'fontMeasurements':data['fonts']}
  failed=result['overflow']>1 or banned or mismatches or small_svg or data['drawerWidth']>width+1 or data['overlaps']
  result['passed']=not bool(failed)
  if width in [1440,390] or failed:
   target=OUT/f'{width}-{route}.png';page.screenshot(path=str(target),full_page=True);result['screenshot']=str(target.relative_to(ROOT.parent))
  results.append(result)
 for width in [1920,1600,1440,1280,1024,768,390]:
  page.set_viewport_size({'width':width,'height':1000 if width>768 else 844})
  for route in ['overview','users','apps','grants','audit','settings','login']:
   page.evaluate('(route)=>{location.hash=route;render();}',route);inspect(route,width)
  for route,action,name in [('users',"userDrawer('u1')",'user-details'),('grants',"grantDrawer('a1')",'grant-editor'),('audit',"auditDrawer(logs.find(l=>l.type==='operation').id)",'audit-details')]:
   page.evaluate('(route)=>{location.hash=route;render();}',route)
   page.wait_for_timeout(60);page.evaluate(action);page.wait_for_timeout(250);inspect(name,width)
 browser.close()
report={'kind':'independent-html-only-NOT-React','method':'Chromium, shared product stylesheets + explicitly separate plain-HTML bridge','checks':len(results),'browserErrors':errors,'failed':[r for r in results if not r['passed']],'results':results}
(OUT/'companion-browser-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps({'checks':len(results),'browserErrors':errors,'failed':[{k:v for k,v in r.items() if k!='fontMeasurements'} for r in report['failed']]},ensure_ascii=False,indent=2))
raise SystemExit(1 if report['failed'] or errors else 0)
