import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);const p=require('../.test-build/src/shared/presentation.js');const {palette,typeScale}=require('../.test-build/src/design/tokens.js');
for(const v of [null,undefined,'',{},true])test(`missing/invalid counts do not become success numbers: ${String(v)}`,()=>assert.equal(p.formatCount(v),'—'));
test('large exact count strings never go through Number',()=>assert.equal(p.formatCount('9007199254740993123'),'9,007,199,254,740,993,123'));
test('zero is a real count',()=>assert.equal(p.formatCount(0),'0'));
for(const v of [null,undefined,'',NaN,-1,101])test(`coverage rejects invalid/unknown percentage ${String(v)}`,()=>assert.equal(p.finitePercent(v),null));
test('valid coverage stays exact',()=>assert.equal(p.finitePercent('78.6'),78.6));
test('zero denominator never shows 100%',()=>assert.equal(p.jobProgress(0,0),null));
test('missing completed count stays unknown',()=>assert.equal(p.jobProgress(null,10),null));
test('valid task progress uses the actual numerator',()=>assert.equal(p.jobProgress('5','20'),25));
test('recent run keeps its saved Chinese task name and adds the Chinese table context',()=>assert.deepEqual(p.recentRunPresentation({task_name:'人口质量检查',task_kind:'QUALITY',table_name_cn:'人口基本信息',id:'opaque-run-id'}),{name:'人口质量检查',context:'质量检查 · 人口基本信息'}));
test('recent run does not invent a Chinese translation for an English task name',()=>assert.deepEqual(p.recentRunPresentation({task_name:'DM registration smoke quality check',task_kind:'QUALITY',datasource_name:'达梦业务库',id:'opaque-run-id'}),{name:'DM registration smoke quality check',context:'质量检查 · 达梦业务库'}));
test('recent run never exposes the instance ID when task and subject names are absent',()=>assert.deepEqual(p.recentRunPresentation({task_kind:'PROFILE',id:'opaque-run-id'}),{name:'字段探查',context:'字段探查'}));
test('empty source list has no invented categories',()=>assert.deepEqual(p.sourceDistribution([]),[]));
test('source type distribution counts only supplied rows',()=>{const r=p.sourceDistribution([{engine:'MYSQL'},{engine:'mysql'},{engine:'DM8'}]);assert.equal(r[0].count,2);assert.equal(r[1].count,1);assert.ok(Math.abs(r.reduce((a,b)=>a+b.percent,0)-100)<1e-8);});
test('engine css class cannot contain an injected class',()=>assert.equal(p.engineClass('mysql evil'),'other'));
test('switching engine replaces its former default port',()=>assert.equal(p.nextEnginePort('DM8','POSTGRESQL',5236),5432));
test('a user-configured port is not overwritten',()=>assert.equal(p.nextEnginePort('DM8','MYSQL',15236),15236));
test('compact preference defaults to 14px-independent small spacing',()=>assert.deepEqual(p.compactPreference(null),{size:15,density:'small',hidden:[]}));
test('invalid saved page size is ignored',()=>assert.equal(p.compactPreference({size:999999}).size,15));
test('hidden columns contain only string keys',()=>assert.deepEqual(p.compactPreference({hidden:['a',null,3,'b']}).hidden,['a','b']));
test('font hierarchy is page > section > body > helper',()=>assert.ok(typeScale.page>typeScale.section&&typeScale.section>typeScale.body&&typeScale.body>typeScale.micro));
function lum(hex){const c=hex.replace('#','').match(/../g).map(v=>parseInt(v,16)/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);return c[0]*.2126+c[1]*.7152+c[2]*.0722;}
function ratio(a,b){const x=lum(a),y=lum(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
for(const mode of ['light','dark']){
 test(`${mode}: normal text contrast >=4.5`,()=>assert.ok(ratio(palette[mode].text,palette[mode].surface)>=4.5));
 test(`${mode}: secondary text contrast >=4.5`,()=>assert.ok(ratio(palette[mode].secondary,palette[mode].surface)>=4.5));
 test(`${mode}: purple links contrast >=4.5`,()=>assert.ok(ratio(palette[mode].primary,palette[mode].surface)>=4.5));
}
const source=f=>fs.readFileSync(f,'utf8');
test('refined stylesheet is imported last',()=>assert.ok(source('src/main.tsx').lastIndexOf('refined.css')>source('src/main.tsx').lastIndexOf('platform.css')));
test('compact theme preserves 14px body size',()=>assert.match(source('src/design/theme.ts'),/fontSize: typeScale.body/));
test('no UI zoom shortcut',()=>assert.doesNotMatch(source('src/styles/refined.css'),/\bzoom\s*:/));
test('ER viewport modal and leave guards remain',()=>{assert.match(source('src/app/FullscreenWorkspace.tsx'),/100dvh/);assert.match(source('src/app/App.tsx'),/useCanvasLeaveGuard/);});
test('source editor uses the same shared side drawer',()=>assert.match(source('src/features/platform/SourcesPage.tsx'),/presentation="drawer"/));
test('source browsing rejects outdated responses',()=>{const drawer=source('src/features/platform/SourceCollectionDrawer.tsx');assert.match(drawer,/current\s*===\s*sequence\.current/);assert.match(drawer,/return \(\) => \{ live = false; \}/);});
test('dashboard never hardcodes the concept-image trend values',()=>assert.doesNotMatch(source('src/features/platform/OverviewPage.tsx'),/12,560|1,284|78\.6|297\s*\/\s*300/));
test('dashboard recent-task text uses business presentation, not raw run IDs',()=>{const overview=source('src/features/platform/OverviewPage.tsx');assert.match(overview,/recentRunPresentation\(r\)/);assert.doesNotMatch(overview,/<small>\{r\.phase\s*\|\|\s*r\.id\}/);});
test('dashboard rows share thirds with two-plus-one panels',()=>{const css=source('src/styles/refined.css');assert.match(css,/\.wx-dashboard-charts\{display:grid;grid-template-columns:1fr 1fr 1fr/);assert.match(css,/\.wx-dashboard-work,\.wx-dashboard-bottom\{grid-template-columns:repeat\(3,minmax\(0,1fr\)\)\}/);assert.match(css,/\.wx-dashboard-work>\.wx-insight-panel:first-child,\.wx-dashboard-bottom>\.wx-insight-panel:first-child\{grid-column:span 2\}/);});
test('model previews do not fabricate relationship edges',()=>assert.doesNotMatch(source('src/design/Visuals.tsx'),/sourceEntityId|targetEntityId|curveTo/));
test('forms use explicit edit state rather than programmatic touched state',()=>{assert.match(source('src/features/platform/common.tsx'),/dirty.current = false/);assert.doesNotMatch(source('src/features/platform/common.tsx'),/isFieldsTouched/);});
test('ER canvas is accessed through models rather than a separate menu',()=>{const routes=source('src/app/routes.ts');assert.equal((routes.match(/key:'\/governance/g)||[]).length,18);assert.doesNotMatch(routes,/key:'\/governance\/metadata\/er'/);});
