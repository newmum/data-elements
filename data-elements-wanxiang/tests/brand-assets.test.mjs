import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const read=p=>fs.readFileSync(p,'utf8');
const assets=JSON.parse(read('src/assets/databases/sources.json')).assets;
const sha256=b=>crypto.createHash('sha256').update(b).digest('hex');
for(const a of assets)test(`brand original is byte-exact upstream file: ${a.engine}`,()=>{
 const b=fs.readFileSync(`src/assets/databases/${a.file}`);
 assert.equal(crypto.createHash('sha1').update(`blob ${b.length}\0`).update(b).digest('hex'),a.upstreamGitBlobSha1);
 assert.equal(sha256(b),a.sha256);assert.equal(b.length,a.bytes);assert.equal(a.modifications,'none');
 if(a.file.endsWith('.png'))assert.equal(b.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
 else {assert.match(b.toString(),/<svg/);assert.doesNotMatch(b.toString(),/<script|<foreignObject|href="https?:/);}
});
test('all engine brand paths point to pinned local original files',()=>{
 assert.equal(assets.length,8);const code=read('src/design/Visuals.tsx');
 for(const a of assets)assert.ok(code.includes(`databases/${a.file}`));
 assert.doesNotMatch(code,/https?:\/\//);
});
test('only the overview header takes the legacy branch without highlights or promise',()=>{
 const code=read('src/features/platform/common.tsx'),start=code.indexOf("if (visual?.id === 'overview')"),end=code.indexOf('return <section className="platform-page" data-page={title} data-module={visual?.id}',start);
 assert.ok(start>0&&end>start);const branch=code.slice(start,end);
 assert.match(branch,/page-atmosphere/);assert.match(branch,/万象数据治理 <i \/> WANXIANG/);
 assert.doesNotMatch(branch,/has-module-art|module-highlights|module-promise|heroAsset/);
 assert.match(code.slice(end),/module-highlights/);assert.match(code.slice(end),/module-promise/);
 assert.match(read('src/features/platform/OverviewPage.tsx'),/description="连接多源数据，建立可信标准，让每一项治理结果清晰可见。"/);
});
test('approved overview artwork retains the exact 3.1.0 versions',()=>{
 for(const [f,hash] of Object.entries(JSON.parse(read('tests/fixtures/3.1.0-approved-overview-hashes.json')))){
  if(f==='src/styles/refined.css')continue; // Dashboard columns were intentionally redesigned.
  assert.equal(sha256(fs.readFileSync(f)),hash,f);
 }
});
test('unmigrated page visuals, navigation, artwork and mock logic retain the approved baseline',()=>{
 const migrated=new Set(['src/styles/refined.css','src/app/App.tsx','src/app/FullscreenWorkspace.tsx','src/app/AuthGate.tsx','src/main.tsx','src/services/api.ts','src/features/platform/SourcesPage.tsx','src/features/platform/common.tsx','src/features/platform/DataPages.tsx','src/features/platform/StandardPages.tsx','src/features/platform/QualityPages.tsx','src/design/moduleVisuals.ts','src/features/platform/SourceCollectionDrawer.tsx','src/features/platform/ModelsPage.tsx','src/features/platform/OverviewPage.tsx','src/app/useWorkspaceLifecycle.ts','src/features/er/store.ts','src/features/er/types/domain.ts','src/features/er/app/App.tsx','src/features/er/components/DiscoveryDialog.tsx','src/features/er/components/ERCanvas.tsx','src/features/er/components/EntityNode.tsx','src/features/er/components/RelationEditor.tsx','src/features/er/components/EntityLibrary.tsx','src/features/er/components/Inspector.tsx','src/features/er/services/workspaceService.ts']);
 migrated.add('src/shared/presentation.ts'); // Dashboard task captions now live in the shared presentation helpers.
 migrated.add('src/shared/sourcePresentation.ts'); // Source type descriptions now cover the platform's registered connector types.
 migrated.add('src/features/er/components/RelationActions.tsx'); // Shared ER relationship actions were migrated before this dashboard change.
 migrated.add('src/app/routes.ts'); // ER canvases are now opened from a chosen data model, not a standalone menu.
 migrated.add('src/features/er/styles/wanxiang.css'); // Large source models expose bounded canvas groups.
 migrated.add('src/features/er/core/model.ts'); // Shared canvas group size is part of the model-view workflow.
 migrated.add('src/features/er/workers/layout.worker.ts'); // ELK now uses an explicit browser worker and height-aware connected-component layout.
 for(const file of ['src/features/platform/LocalWorkspaceTools.tsx','src/features/platform/FieldMappingEditor.tsx','src/features/platform/CatalogEditor.tsx','src/features/er/components/DiagramList.tsx','src/features/platform/MetadataPages.tsx','src/features/platform/ProcessingLineagePage.tsx','src/features/platform/SharedLineagePage.tsx','src/features/platform/Operations.tsx'])migrated.add(file); // All operation columns now share symmetric spacing.
 for(const [f,hash] of Object.entries(JSON.parse(read('tests/fixtures/3.2.0-unchanged-source-hashes.json')))){if(migrated.has(f))continue;assert.equal(sha256(fs.readFileSync(f)),hash,f);}
 // Migrated standard/alignment and quality copy describes the real shared workflow.
 // Unrelated entries and all hero assets remain pinned to the approved design.
 const unchanged=require('../.test-build/src/design/moduleVisuals.js').moduleVisuals.filter(m=>!['elements','review','codes','encoding','mapping','profiling','profile-reports','rules','plans','collection','catalog','er','lineage'].includes(m.id));
 assert.equal(sha256(JSON.stringify(unchanged)),'3ee4540e65f8d70063600b9f4198ca6a2b8c02a437bc2fe78ab90e0853167323');
});
test('source icons share one implementation in ER nodes/library and legacy capabilities',()=>{
 for(const name of ['EntityNode','EntityLibrary','Capabilities'])assert.match(read(`src/features/er/components/${name}.tsx`),/SourceIcon/);
 assert.doesNotMatch(read('src/features/er/components/Capabilities.tsx'),/name=\{source.engine === 'mongodb'/);
});
test('PNG review data URLs use their correct image MIME',()=>assert.match(read('tests/review/render-components.cjs'),/'\.png':'image\/png'/));
test('vendored licenses accompany the eight logos',()=>{
 assert.match(read('third-party/DEVICON-LICENSE.txt'),/Copyright \(c\) 2015 konpa/);
 assert.match(read('third-party/APACHE-2.0.txt'),/Version 2.0/);
 assert.match(read('third-party/NOTICE.md'),/商标/);
});
