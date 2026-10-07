"""Targeted real-CSS checks for branding/rail/banner; HTML shell only, not React."""
import base64,json,re,os,shutil
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[2]
fixture=root/'tests/visual/fixtures/overview.html'
markup=fixture.read_text()
def inline(match):
 p=(fixture.parent/match.group(1)).resolve();css=p.read_text()
 def image(m):
  href=m.group(1).strip("\"'")
  if href.startswith(('data:','http:','https:','#')):return m.group(0)
  a=(p.parent/href).resolve()
  return 'url("data:image/svg+xml;base64,'+base64.b64encode(a.read_bytes()).decode()+'")' if a.exists() else m.group(0)
 return '<style>'+re.sub(r'url\(([^)]+)\)',image,css)+'</style>'
markup=re.sub(r'<link rel="stylesheet" href="([^"]+)">',inline,markup)
results=[]
with sync_playwright() as p:
 executable=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium')
 b=p.chromium.launch(**({'executable_path':executable} if executable else {}),headless=True)
 page=b.new_page(device_scale_factor=1,reduced_motion='reduce')
 for theme in ['light','dark']:
  for collapsed in [False,True]:
   for width,height in [(1440,1000),(1920,1080),(1024,768)]:
    page.set_viewport_size({'width':width,'height':height});page.set_content(markup,wait_until='domcontentloaded')
    page.evaluate('''({theme,collapsed})=>{document.documentElement.dataset.theme=theme;const side=document.querySelector('.side-nav');side.classList.toggle('ant-layout-sider-collapsed',collapsed);side.style.cssText=`width:${collapsed?64:232}px;min-width:${collapsed?64:232}px;max-width:${collapsed?64:232}px;flex:0 0 ${collapsed?64:232}px`;if(collapsed){document.querySelector('.wanxiang-wordmark>div').remove();document.querySelector('.side-nav-footer>span').remove();document.querySelectorAll('.ant-menu-item-group-title').forEach(e=>e.textContent='');}}''',{'theme':theme,'collapsed':collapsed})
    page.wait_for_timeout(60)
    data=page.evaluate('''()=>{const side=document.querySelector('.side-nav').getBoundingClientRect(),item=document.querySelector('.ant-menu-item-selected'),icon=item.querySelector('.anticon').getBoundingClientRect(),pseudo=getComputedStyle(item,'::before'),brand=document.querySelector('.wx-brand-mark').getBoundingClientRect(),hero=document.querySelector('.platform-page-heading').getBoundingClientRect(),art=getComputedStyle(document.querySelector('.page-atmosphere'));return{bar:{content:pseudo.content,display:pseudo.display,width:pseudo.width},iconCenter:icon.x+icon.width/2,railCenter:side.x+side.width/2,brandCenter:brand.x+brand.width/2,heroHeight:hero.height,hasArtwork:art.backgroundImage.startsWith('url("data:image/svg+xml'),artOpacity:art.opacity,documentWidth:document.documentElement.scrollWidth,viewportWidth:innerWidth,topBackground:getComputedStyle(document.querySelector('.topbar')).backgroundColor}}''')
    checks={'barStateCorrect':data['bar']['display']=='none' if collapsed else data['bar']['display']!='none' and data['bar']['content']=='""','collapsedIconCentered':not collapsed or abs(data['iconCenter']-data['railCenter'])<=1,'collapsedBrandCentered':not collapsed or abs(data['brandCenter']-data['railCenter'])<=1,'bannerAssetPresent':data['hasArtwork'],'bannerHeightReadable':data['heroHeight']>=126,'noPageOverflow':data['documentWidth']<=width+1,'darkTopNotWhite':theme!='dark' or data['topBackground']!='rgb(255, 255, 255)'}
    results.append({'theme':theme,'collapsed':collapsed,'width':width,'height':height,'checks':checks,'measurements':data})
    if width==1440:
     out=root.parent/'logs/wanxiang/visual-review/shots';out.mkdir(parents=True,exist_ok=True)
     page.screenshot(path=str(out/f'brand-{theme}-{"collapsed" if collapsed else "expanded"}.png'))
 b.close()
report={'scope':'Production CSS and SVG assets in a simplified HTML shell. No full React/Ant Design rendering.','cases':len(results),'passed':sum(all(x['checks'].values()) for x in results),'results':results}
out=root.parent/'logs/wanxiang/audit';out.mkdir(parents=True,exist_ok=True);(out/'brand-rail-banner.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps(report,ensure_ascii=False,indent=2));raise SystemExit(0 if report['passed']==len(results) else 1)
