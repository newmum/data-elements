/** Publish exactly the reviewed live component, preserving concurrent edits and a rollback. */
import assert from 'node:assert/strict';import fs from 'node:fs/promises';import {createRequire} from 'node:module';import path from 'node:path';
const require=createRequire(import.meta.url),{sm3}=require('sm-crypto');assert(process.env.QUALITY_TEST_PASSWORD);
const base='http://localhost:3000/dev-api',login=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:process.env.QUALITY_TEST_ACCOUNT||'manager',password:sm3(process.env.QUALITY_TEST_PASSWORD),appId:'1995678661281710081'})}).then(r=>r.json());assert.equal(login.code,0);
async function call(route,body={}){const r=await fetch(base+route,{method:'POST',headers:{token:login.data.token,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(45000)}).then(r=>r.json());assert.equal(r.code,0,route+': '+String(r.msg||r.message));return r.data;}
const context=JSON.parse(await fs.readFile(new URL('../../data/working/quality-platform-context.json',import.meta.url),'utf8'));
const previous=await fs.readFile(path.join(context.backup,'source.vue'),'utf8'),source=await fs.readFile(new URL('../../data/working/data-quality.vue',import.meta.url),'utf8'),live=await call('/sym/component?action=getSourceCode&tid='+context.tid);
assert([previous,source].includes(live),'Concurrent low-code edits detected; nothing overwritten');
const js=await fs.readFile(new URL('../../data/working/data-quality.compile.js',import.meta.url),'utf8'),css=await fs.readFile(new URL('../../data/working/data-quality.compile.css',import.meta.url),'utf8');assert(js.includes('return __sfc__')&&js.includes('PROFILE')&&js.includes('TIMELINESS')&&js.includes('REFERENCE'));
await call('/sym/component?action=saveCode',{tid:context.tid,sourceCode:source,compileJs:js,compileCss:css});await call('/sym/component/cache/refresh');
assert.equal(await call('/sym/component?action=getSourceCode&tid='+context.tid),source);
const runtime=(await call('/sym/component?action=list')).find(r=>r.name==='data-quality');assert.equal(runtime.compileJs,js);assert.equal(runtime.compileCss,css);
console.log(JSON.stringify({sourceAndRuntimeVerified:true,tid:context.tid,backup:context.backup,jsBytes:js.length,cssBytes:css.length}));
