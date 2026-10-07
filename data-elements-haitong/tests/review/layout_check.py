"""Isolated TSX-generated DOM/CSS review. No React/Ant/X6 event runtime is exercised."""
import asyncio, json
from pathlib import Path
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[2]
OUTPUT=ROOT.parent/'logs/haitong/review'
PAGES=['overview','tasks','batch','registry','multi','ingress','distribution','clusters','cross','instant','inventory','statements','canvas']
SIZES=[(1920,1080),(1670,940),(1440,900),(1366,768),(768,1024),(390,844)]
JS='''() => {
 const rect=e=>e.getBoundingClientRect();
 const root=document.documentElement;
 const sidebar=document.querySelector('.ht-sidebar');
 const hero=document.querySelector('.ht-hero');
 const copy=document.querySelector('.ht-hero-copy');
 const art=document.querySelector('.ht-hero-art');
 const links=Array.from(document.querySelectorAll('.is-collapsed .nav-link'));
 const navDelta=links.map(l=>{const i=l.querySelector('svg');if(!i)return 0;const a=rect(l),b=rect(i);return Math.abs(a.left+a.width/2-b.left-b.width/2);});
 const badImages=Array.from(document.images).filter(i=>!i.complete||!i.naturalWidth).map(i=>i.getAttribute('src'));
 const overlap=copy&&art&&getComputedStyle(art).display!=='none'&&rect(copy).right>rect(art).left+1;
 const smallText=Array.from(document.querySelectorAll('.nav-link')).filter(e=>getComputedStyle(e).fontSize.replace('px','')<14).length;
 return {width:innerWidth,scrollWidth:root.scrollWidth,scrollHeight:root.scrollHeight,navDelta:Math.max(0,...navDelta),badImages,heroOverlap:!!overlap,smallMenuText:smallText,theme:root.dataset.theme,primary:getComputedStyle(root).getPropertyValue('--primary').trim(),svgCount:document.querySelectorAll('svg').length};
}'''
async def main():
 out=OUTPUT/'screenshots';out.mkdir(parents=True,exist_ok=True)
 results=[]
 async with async_playwright() as p:
  browser=await p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
  page=await browser.new_page()
  for mode in ['light','dark']:
   for navigation in ['expanded','collapsed']:
    for width,height in SIZES if navigation=='collapsed' else SIZES[:4]:
     await page.set_viewport_size({'width':width,'height':height})
     for name in PAGES:
      file=OUTPUT/'dom-review'/f'{name}-{mode}-{navigation}.html'
      await page.set_content(file.read_text(),wait_until='load')
      m=await page.evaluate(JS)
      m.update(page=name,viewport=f'{width}x{height}',navigation=navigation)
      m['passed']=m['scrollWidth']<=width+1 and m['navDelta']<=1 and not m['badImages'] and not m['heroOverlap'] and m['smallMenuText']==0
      results.append(m)
      if width==1670 and navigation=='expanded' and name in ['overview','tasks','canvas','instant']:
       await page.screenshot(path=str(out/f'{name}-{mode}-1670.png'),full_page=True)
      if width==390 and name in ['overview','canvas']:
       await page.screenshot(path=str(out/f'{name}-{mode}-390.png'),full_page=True)
  await browser.close()
 report={'kind':'isolated-dom-not-application-e2e','count':len(results),'passed':sum(r['passed'] for r in results),'failed':[r for r in results if not r['passed']],'results':results}
 (OUTPUT/'layout-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
 print(json.dumps({k:v for k,v in report.items() if k!='results'},ensure_ascii=False,indent=2))
 if report['failed']:raise SystemExit(1)
asyncio.run(main())
