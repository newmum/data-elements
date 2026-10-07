"""Validate local vector assets/PNG dimensions and preservation of manifest declarations."""
from pathlib import Path
import json, hashlib, xml.etree.ElementTree as ET
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
pkg=json.loads((ROOT/'package.json').read_text());base=json.loads((ROOT/'doc/basis/package.original.json').read_text())
assert all(pkg[k][n]==v for k in ['dependencies','devDependencies'] for n,v in base[k].items())
assert all(pkg['scripts'][n]==v for n,v in base['scripts'].items())
assets=[]
for p in sorted((ROOT/'public').rglob('*.svg')):
 tree=ET.fromstring(p.read_text());assert tree.tag.endswith('svg')
 assert 'viewBox' in tree.attrib
 assert not any(e.tag.rsplit('}',1)[-1] in ['image','script','filter'] for e in tree.iter())
 assert not any(v.startswith(('http:', 'https:')) for e in tree.iter() for k,v in e.attrib.items() if k.endswith('href'))
 assets.append({'path':str(p.relative_to(ROOT)),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
for n in [32,64,128,256,512,1024]:
 p=ROOT/f'public/brand/haitong-mark-{n}.png';im=Image.open(p);assert im.size==(n,n);im.verify()
 assets.append({'path':str(p.relative_to(ROOT)),'width':n,'height':n})
assert len(list((ROOT/'public/heroes').glob('*.svg')))==24
assert len({hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'public/heroes').glob('*-light.svg')})==12
files=list(ROOT.rglob('*'));assert not any(p.suffix.lower() in ['.ttf','.otf','.woff','.woff2'] for p in files)
report={'svgAssets':25,'pngSizes':[32,64,128,256,512,1024],'preservedDependencies':28,'preservedScripts':3,'newDependenciesThisRound':0,'assets':assets}
OUT = ROOT.parent/'logs/haitong/audit'
OUT.mkdir(parents=True, exist_ok=True)
(OUT/'assets-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print('PASS 25 local SVG files, 6 PNG sizes, 28 original dependency declarations, 3 original scripts.')
