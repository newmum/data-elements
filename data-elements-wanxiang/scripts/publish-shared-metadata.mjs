/** Publish only reviewed shared metadata scripts. Back up the exact live resource first. */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{sm3}=require('sm-crypto');
assert(process.env.METADATA_TEST_PASSWORD,'Development credentials must come from the invoking environment');
const base='http://localhost:8088';
const login=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:process.env.METADATA_TEST_ACCOUNT||'manager',password:sm3(process.env.METADATA_TEST_PASSWORD),appId:'1995678661281710081'})}).then(r=>r.json());assert.equal(login.code,0);
async function call(route,body){const r=await fetch(base+route,{method:body===undefined?'GET':'POST',headers:{token:login.data.token,...(body===undefined?{}:{'Content-Type':'application/json'})},...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(30000)}).then(r=>r.json());assert([0,1,200].includes(r.code),route+': '+String(r.msg||r.message));return r.data;}
const backup=await fs.mkdtemp(path.join(os.tmpdir(),'wx-metadata-script-before-'));
for(const filename of await fs.readdir(new URL('../backend/metadata/',import.meta.url))){if(!filename.endsWith('.ms'))continue;const text=await fs.readFile(new URL('../backend/metadata/'+filename,import.meta.url),'utf8'),split=text.indexOf('================================'),meta=JSON.parse(text.slice(0,split)),script=text.slice(split+32).trim();const live=await call('/api/web/resource/file/'+meta.id);if(live){assert.equal(live.id,meta.id);assert.equal(live.groupId,meta.groupId);assert.equal(live.path,meta.path);}else assert(meta.id.startsWith('dwm_metadata_governance_')&&meta.groupId==='dwm_metadata_governance_group_01','Only reviewed extensions in the existing shared group may be created');await fs.writeFile(path.join(backup,meta.id+'.json'),JSON.stringify(live??{previouslyAbsent:true},null,2));await call('/api/web/resource/file/api/save',{...(live||meta),script,description:meta.description});assert.equal((await call('/api/web/resource/file/'+meta.id)).script.trim(),script);}
console.log('Shared metadata scripts published; backup='+backup);
