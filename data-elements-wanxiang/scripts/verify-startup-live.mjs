/** Read-only first-screen payload measurement, no business rows or secrets saved. */
import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {verificationLogin} from './shared-verification-auth.mjs';
const base='http://localhost:3010/dev-api';
assert(process.env.METADATA_TEST_ACCOUNT&&process.env.METADATA_TEST_PASSWORD);
const login=await verificationLogin(base,process.env.METADATA_TEST_ACCOUNT,process.env.METADATA_TEST_PASSWORD);
const headers={token:login.data.token,'Content-Type':'application/json'};
async function get(path,body){const start=performance.now(),r=await fetch(base+path,{method:body===undefined?'GET':'POST',headers,...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(30000)}),text=await r.text(),result=JSON.parse(text);assert.equal(result.code,0,path+': '+String(result.msg||result.message||''));return {data:result.data,ms:Math.round(performance.now()-start),bytes:Buffer.byteLength(text)};}
const tenant=await get('/sym/tenant/current');assert.equal(String(tenant.data.tid),'2084109831682699264');
const summary=await get('/dwm/metadata-governance/summary',{}),catalog=await get('/dwm/metadata-governance/catalog',{});
assert.equal(summary.data.sources,catalog.data.sources.length);
assert.equal(summary.data.entities,catalog.data.tables.length);
assert.equal(summary.data.coverage.totalFields,catalog.data.columns.length);
assert(summary.bytes<10000);assert(summary.bytes<catalog.bytes/100);
const report={tenantId:String(tenant.data.tid),summary:{bytes:summary.bytes,ms:summary.ms},fullCatalog:{bytes:catalog.bytes,ms:catalog.ms},counts:{sources:summary.data.sources,entities:summary.data.entities,fields:summary.data.coverage.totalFields},note:'Single local/proxy API measurement. Not a browser cold-load timing or a production guarantee. No catalog rows saved.'};
await writeFile(new URL('../../data/working/reports/governance-startup-live-20260929.json',import.meta.url),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
