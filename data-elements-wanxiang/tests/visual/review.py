import json, time, sys, re, os, shutil, base64
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[2]
qa=root.parent/'logs/wanxiang/visual-review';qa.mkdir(parents=True,exist_ok=True);shots=qa/'shots';shots.mkdir(exist_ok=True)
manifest=json.loads((root/'tests/visual/fixtures/manifest.json').read_text())
viewport_cases=[(1440,1000),(1366,900),(1920,1080),(768,1024),(390,844)]
results=[];bad=[]
with sync_playwright() as p:
 executable=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium') or shutil.which('chromium-browser')
 browser=p.chromium.launch(**({'executable_path':executable} if executable else {}),headless=True)
 page=browser.new_page(device_scale_factor=1,reduced_motion="reduce")
 for item in manifest:
  for theme in ['light','dark']:
   for w,h in viewport_cases:
    page.set_viewport_size({'width':w,'height':h})
    fixture=root/'tests/visual/fixtures'/item['file']
    markup=fixture.read_text()
    def style_tag(m):
     css_path=(fixture.parent/m.group(1)).resolve()
     css=css_path.read_text()
     def embed(u):
      href=u.group(1).strip("\"'")
      if href.startswith(('data:','http:','https:','#')):return u.group(0)
      path=(css_path.parent/href).resolve()
      if not path.exists():return u.group(0)
      mime='image/svg+xml' if path.suffix=='.svg' else 'image/png'
      return 'url("data:'+mime+';base64,'+base64.b64encode(path.read_bytes()).decode()+'")'
     css=re.sub(r'url\(([^)]+)\)',embed,css)
     return '<style>'+css+'</style>'
    markup=re.sub(r'<link rel="stylesheet" href="([^"]+)">',style_tag,markup)
    page.set_content(markup,wait_until='domcontentloaded')
    page.evaluate('(theme)=>document.documentElement.dataset.theme=theme',theme)
    page.evaluate('document.fonts.ready')
    page.wait_for_timeout(10)
    r=page.evaluate('''()=>{const rect=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,right:r.right,bottom:r.bottom}};const main=document.querySelector('.main-content');const title=document.querySelector('.platform-page-heading h1');const top=document.querySelector('.topbar,.fullscreen-workspace-header');const scan=[...document.querySelectorAll('.platform-page-heading,.platform-panel,.wx-insight-panel,.wx-metric,.source-summary-strip>div,.wx-model-grid>.wx-model-card,.wx-model-toolbar,.ant-drawer-content-wrapper,.ant-form-item,.encoding-segment')].map(e=>({class:e.className,...rect(e)})).filter(r=>r.w>0&&(r.x< -1||r.right>innerWidth+1));return{documentWidth:document.documentElement.scrollWidth,viewportWidth:innerWidth,overflow:scan,main:main?rect(main):null,titleSize:title?parseFloat(getComputedStyle(title).fontSize):null,tableSize:document.querySelector('.platform-panel .ant-table-tbody td')?parseFloat(getComputedStyle(document.querySelector('.platform-panel .ant-table-tbody td')).fontSize):null,topBackground:top?getComputedStyle(top).backgroundColor:null,bodyText:getComputedStyle(document.body).color,fullscreen:document.querySelector('.fullscreen-workspace')?rect(document.querySelector('.fullscreen-workspace')):null,fonts:getComputedStyle(document.body).fontFamily};}''')
    checks={'documentFits':r['documentWidth']<=w+1,'containerFits':len(r['overflow'])==0,'pageTitleReadable':r['titleSize'] is None or r['titleSize']>=22,'tableTextReadable':r['tableSize'] is None or r['tableSize']>=13,'darkTopNotWhite':theme!='dark' or r['topBackground']!='rgb(255, 255, 255)','fullscreenFillsViewport':r['fullscreen'] is None or (abs(r['fullscreen']['w']-w)<1 and abs(r['fullscreen']['h']-h)<1)}
    out={'page':item['key'],'theme':theme,'width':w,'height':h,'checks':checks,'measurements':r}
    results.append(out)
    if len(results)%50==0:print('Measured',len(results),'cases',flush=True)
    if not all(checks.values()):bad.append(out)
    # All page patterns have both theme captures; select narrow and wide captures.
    if '--screenshots' in sys.argv and (w==1440 or (item['key'] in ['overview','sources','source-editor','encoding-editor','models','er','login'] and w in [390,768,1920])):
     page.wait_for_timeout(120)
     page.screenshot(path=str(shots/f'{item["key"]}-{theme}-{w}.png'),animations='disabled')
 browser.close()
summary={'scope':'ISOLATED production CSS + simplified HTML patterns; not full React/Ant Design or backend E2E','browser':'system Chromium via Playwright','fixtures':len(manifest),'themes':2,'sizes':viewport_cases,'cases':len(results),'passed':len(results)-len(bad),'failed':len(bad),'results':results}
(qa/'visual-results.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
print(json.dumps({k:v for k,v in summary.items() if k!='results'},ensure_ascii=False,indent=2))
for b in bad[:20]:print(b['page'],b['theme'],b['width'],b['checks'],b['measurements']['overflow'][:2])

raise SystemExit(1 if bad else 0)
