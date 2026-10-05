/** Pull the live low-code source before editing; keep source/runtime backups. */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{sm3}=require('../../data-elements-front/node_modules/sm-crypto');
assert(process.env.QUALITY_TEST_PASSWORD);
const base='http://localhost:3000/dev-api';
const login=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:process.env.QUALITY_TEST_ACCOUNT||'manager',password:sm3(process.env.QUALITY_TEST_PASSWORD),appId:'1995678661281710081'})}).then(r=>r.json());assert.equal(login.code,0);
async function call(route,body={}){const r=await fetch(base+route,{method:'POST',headers:{token:login.data.token,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(45000)}).then(r=>r.json());assert.equal(r.code,0,route+': '+String(r.msg||r.message));return r.data;}
const runtime=(await call('/sym/component?action=list')).find(r=>r.name==='data-quality');assert(runtime&&runtime.compileJs?.includes('return __sfc__'));
const flatten=nodes=>(nodes||[]).flatMap(r=>[r,...flatten(r.children)]);
const component=flatten((await call('/sym/component?action=tree')).list).find(r=>(r.realName||r.name)==='data-quality');assert(component?.tid);
const source=await call('/sym/component?action=getSourceCode&tid='+component.tid);assert.equal(typeof source,'string');
const backup=await fs.mkdtemp(path.join(os.tmpdir(),'wx-quality-platform-before-'));await fs.writeFile(path.join(backup,'source.vue'),source);await fs.writeFile(path.join(backup,'runtime.json'),JSON.stringify(runtime));
const local=new URL('../../data/working/data-quality.vue',import.meta.url);
const previous=await fs.readFile(local,'utf8');if(previous.replace(/\r\n/g,'\n')!==source.replace(/\r\n/g,'\n')&&!process.argv.includes('--inspect'))throw Error('Live source differs from working copy; inspect '+backup+' before edits');
await fs.writeFile(new URL('../../data/working/quality-platform-context.json',import.meta.url),JSON.stringify({backup,tid:component.tid}));console.log(JSON.stringify({liveMatchesWorking:previous.replace(/\r\n/g,'\n')===source.replace(/\r\n/g,'\n'),backup,tid:component.tid,sourceBytes:source.length}));
