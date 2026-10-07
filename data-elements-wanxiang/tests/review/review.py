"""Geometry/typography review of source-driven DOM snapshots.
These use explicit React-hook / Ant-Design test adapters, NOT a full application build.
"""
import json, os, shutil, sys
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT.parent / 'logs/wanxiang/review'
SHOTS = OUT / 'shots'
SHOTS.mkdir(exist_ok=True)
manifest = json.loads((OUT / 'manifest.json').read_text())
requested=next((a.split('=',1)[1].split(',') for a in sys.argv if a.startswith('--modules=')),[])
if requested: manifest=[m for m in manifest if m['id'] in requested]
sizes = [(1920,1080),(1670,1060),(1440,1000),(1366,900),(768,1024),(390,844)]
results=[]
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH') or shutil.which('chromium'),headless=True,args=['--no-sandbox'])
    page=browser.new_page(device_scale_factor=1,reduced_motion='reduce')
    for item in manifest:
        # Expanded desktop shell; narrow use collapsed or the app's overlay navigation.
        cases = sizes if item['collapsed'] else sizes[:4]
        for theme in ['light','dark']:
            for width,height in cases:
                page.set_viewport_size({'width':width,'height':height})
                html=(OUT/item['file']).read_text().replace('data-theme="light"',f'data-theme="{theme}"',1)
                page.set_content(html,wait_until='load')
                page.evaluate('document.fonts.ready')
                page.wait_for_timeout(25)
                metrics=page.evaluate(r'''async()=>{
 const rect=e=>{const r=e.getBoundingClientRect();return{x:r.x,y:r.y,w:r.width,h:r.height,right:r.right,bottom:r.bottom}};
 const nav=document.querySelector('.wx-product-nav');const rail=document.querySelector('.side-nav');
 const icons=[...nav.querySelectorAll('.wx-nav-link .wx-module-icon')].map(e=>({rect:rect(e),center:rect(e).x+rect(e).w/2-(rect(rail).x+rect(rail).w/2)}));
 const selected=nav.querySelector('[aria-current=page]');
 const hero=document.querySelector('.platform-page-heading');const copy=document.querySelector('.page-heading-copy');const actions=document.querySelector('.page-heading-actions');
 const h=document.querySelector('.platform-page-heading h1');const desc=document.querySelector('.page-heading-copy>p');
 const nodes=[...document.querySelectorAll('.platform-page-heading,.platform-panel,.wx-insight-panel,.wx-source-kpi,.wx-metric,.wx-model-card,.wx-model-toolbar,.ant-drawer-content-wrapper')];
 const bad=nodes.map(e=>({class:e.className,...rect(e)})).filter(r=>r.w>0&&(r.x<-1||r.right>innerWidth+1));
 const activeArt=[...document.querySelectorAll('.module-hero-art img')].filter(e=>getComputedStyle(e).display!=='none');
 const oldArt=document.querySelector('.page-atmosphere');
 let legacyLoaded=false;
 if(oldArt){const bg=getComputedStyle(oldArt).backgroundImage;const match=bg.match(/^url\(["']?(.*?)["']?\)$/);
  if(match)legacyLoaded=await new Promise(resolve=>{const img=new Image();img.onload=()=>resolve(img.naturalWidth>0);img.onerror=()=>resolve(false);img.src=match[1];});}

 const drawer=document.querySelector('.ant-drawer-content-wrapper');const footer=document.querySelector('.ant-drawer-footer');
 return{documentWidth:document.documentElement.scrollWidth,viewport:innerWidth,overflow:bad,icons,collapsed:nav.dataset.collapsed,
 marker:selected?getComputedStyle(selected,'::before').content:null,topBackground:getComputedStyle(document.querySelector('.topbar')).backgroundColor,
 titleSize:h?parseFloat(getComputedStyle(h).fontSize):null,descriptionSize:desc?parseFloat(getComputedStyle(desc).fontSize):null,
 hero:hero?rect(hero):null,copy:copy?rect(copy):null,actions:actions?rect(actions):null,
 artworkLoaded:activeArt.length>0?activeArt.every(e=>e.complete&&e.naturalWidth>0):legacyLoaded,
 drawer:drawer?rect(drawer):null,footer:footer?rect(footer):null};
}''')
                # Compact hero can stack actions; only overlapping rectangles are forbidden.
                c,a=metrics['copy'],metrics['actions']
                overlap=bool(c and a and c['w']>0 and a['w']>0 and min(c['right'],a['right'])-max(c['x'],a['x'])>1 and min(c['bottom'],a['bottom'])-max(c['y'],a['y'])>1)
                checks={
                    'documentFits':metrics['documentWidth']<=width+1,
                    'containersFit':not metrics['overflow'],
                    'titleHierarchy':metrics['titleSize'] is None or metrics['titleSize']>=22,
                    'descriptionReadable':metrics['descriptionSize'] is None or metrics['descriptionSize']>=13,
                    'artworkLoaded':metrics['artworkLoaded'],
                    'heroTextActionsSeparated':not overlap,
                    'collapsedIconsCentered':not item['collapsed'] or all(abs(i['center'])<=1 for i in metrics['icons']),
                    'selectedMarkerCorrect':metrics['marker'] in ('none','normal',None) if item['collapsed'] else metrics['marker'] not in ('none','normal',None),
                    'darkHeader':theme!='dark' or metrics['topBackground']!='rgb(255, 255, 255)',
                    'drawerWithinViewport':not metrics['drawer'] or metrics['drawer']['right']<=width+1,
                    'drawerFooterVisible':not metrics['footer'] or metrics['footer']['bottom']<=height+1,
                }
                results.append({'module':item['id'],'collapsed':item['collapsed'],'theme':theme,'width':width,'height':height,'checks':checks,'metrics':metrics})
                if '--screenshots' in sys.argv and (width==1670 or (width in (390,768) and item['id'] in ['sources','sources-editor','sources-details','catalog','models','encoding'])):
                    page.screenshot(path=str(SHOTS/f"{item['id']}-{'collapsed' if item['collapsed'] else 'expanded'}-{theme}-{width}.png"),animations='disabled')
                if len(results)%30==0:
                    (OUT/'review-progress.json').write_text(json.dumps(results,ensure_ascii=False))
                    print(f'Measured {len(results)} cases',flush=True)
    browser.close()
bad=[r for r in results if not all(r['checks'].values())]
report={'scope':'Source-driven DOM + local Mock data; React hook / Ant Design adapters. Not production React runtime/E2E.', 'cases':len(results),'passed':len(results)-len(bad),'failed':len(bad),'snapshotCount':len(manifest),'results':results}
(OUT/('review-selected.json' if requested else 'review-results.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!='results'},ensure_ascii=False,indent=2))
for r in bad[:30]: print(r['module'],r['theme'],r['width'],r['collapsed'],{k:v for k,v in r['checks'].items() if not v},r['metrics']['overflow'])
sys.exit(1 if bad else 0)
