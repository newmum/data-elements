/** Measure the complete static import graph, not just the entry chunk. */
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {gzipSync} from 'node:zlib';
import {resolve} from 'node:path';
const root=resolve(import.meta.dirname,'..'),manifest=JSON.parse(await readFile(resolve(root,'dist/.vite/manifest.json'),'utf8'));
const entry=Object.keys(manifest).find(k=>manifest[k].isEntry),overview=Object.keys(manifest).find(k=>k.endsWith('OverviewPage.tsx'));
if(!entry||!overview)throw Error('Build first: entry and overview manifest entries are required');
async function graph(roots){const seen=new Set();function visit(key){if(seen.has(key))return;const item=manifest[key];if(!item)throw Error('Missing import '+key);seen.add(key);for(const imported of item.imports||[])visit(imported);}roots.forEach(visit);const chunks=[];for(const key of seen){const file=manifest[key].file;if(!file.endsWith('.js'))continue;const data=await readFile(resolve(root,'dist',file));chunks.push({file,bytes:data.length,gzipBytes:gzipSync(data).length});}return {chunks,bytes:chunks.reduce((n,c)=>n+c.bytes,0),gzipBytes:chunks.reduce((n,c)=>n+c.gzipBytes,0)};}
const result={measuredAt:new Date().toISOString(),entry:await graph([entry]),overviewFirstScreen:await graph([entry,overview]),note:'Uncached production JS static dependency graph. Excludes CSS/images and server response timing; dynamic imports are excluded until opened.'};
const output=resolve(root,'../logs/wanxiang/audit');await mkdir(output,{recursive:true});
await writeFile(resolve(output,'governance-startup-bundle.json'),JSON.stringify(result,null,2));
console.log(JSON.stringify(result,null,2));
