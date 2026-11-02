import test from 'node:test';
import assert from 'node:assert/strict';
import {createReportSourceLoader} from '../../src/report/services/reportSource';
import type {UploadManifest} from '../../src/report/services/uploadWorkbench';

const manifest=(id:string):UploadManifest=>({revision:id,files:[{fileId:id,fileName:'validation.csv'}],tables:[]});
const sources=Array.from({length:100},(_,i)=>({id:String(i),name:'验证源'+i}));
function deferred(){let resolve!:(value:UploadManifest)=>void;let reject!:(cause:Error)=>void;const promise=new Promise<UploadManifest>((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};}

test('only the latest source response can replace the workspace',async()=>{
  const first=deferred(),second=deferred();const loader=createReportSourceLoader(id=>id==='0'?first.promise:second.promise);
  const a=loader.load('0',sources),b=loader.load('1',sources);second.resolve(manifest('1'));assert.equal((await b)?.source.id,'1');
  first.resolve(manifest('0'));assert.equal(await a,undefined);
});
test('switching identity or unmounting discards pending results without revealing old workspaces',async()=>{
  let revision=1;const pending=deferred();const loader=createReportSourceLoader(()=>pending.promise,()=>revision);
  const response=loader.load('0',sources);revision++;pending.resolve(manifest('0'));assert.equal(await response,undefined);
  const next=deferred();const canceled=createReportSourceLoader(()=>next.promise);const result=canceled.load('0',sources);canceled.cancel();next.resolve(manifest('0'));assert.equal(await result,undefined);
});
test('a failed source load does not produce a replacement; late failures are ignored',async()=>{
  const pending=deferred();const loader=createReportSourceLoader(id=>id==='0'?pending.promise:Promise.resolve(manifest(id)));
  const older=loader.load('0',sources);await loader.load('1',sources);pending.reject(new Error('旧源读取失败'));assert.equal(await older,undefined);
  const failing=createReportSourceLoader(async()=>{throw new Error('目标源读取失败');});await assert.rejects(failing.load('0',sources),/目标源读取失败/);
});
test('20/100 candidate sources require one manifest read per selection and foreign IDs never trigger a read',async()=>{
  for(const count of [20,100]){
    let reads=0;const loader=createReportSourceLoader(async id=>{reads++;return manifest(id);});const allowed=sources.slice(0,count);
    const loaded=await loader.load(String(count-1),allowed);assert.equal(reads,1);assert.equal(loaded?.manifest.files[0].fileId,String(count-1));
    await assert.rejects(loader.load('foreign',allowed),/可选范围/);assert.equal(reads,1);
  }
});
