import test from 'node:test';
import assert from 'node:assert/strict';
import {getBridgeToken,getSessionRevision,setBridgeToken} from '../../src/nifi/api/bridgeSession';
import {reconcilePlatformSessionToken} from '../../src/services/platformSession';
import {platformApi} from '../../src/api/platformApi';
import {readUploadedOriginal} from '../../src/report/services/uploadWorkbench';

test('standalone platform credential changes invalidate the old workspace; embedded parent identity is preserved',t=>{
  const descriptor=Object.getOwnPropertyDescriptor(globalThis,'window');
  const context:{parent?:unknown}={};context.parent=context;Object.defineProperty(globalThis,'window',{configurable:true,value:context});
  t.after(()=>{if(descriptor)Object.defineProperty(globalThis,'window',descriptor);else Reflect.deleteProperty(globalThis,'window');setBridgeToken(null);});
  setBridgeToken('validation-old-session');const previous=getSessionRevision();
  assert.equal(reconcilePlatformSessionToken('validation-current-session'),false);
  assert.equal(getBridgeToken(),'validation-current-session');assert.ok(getSessionRevision()>previous);
  const stable=getSessionRevision();assert.equal(reconcilePlatformSessionToken('validation-current-session'),true);assert.equal(getSessionRevision(),stable);
  assert.equal(reconcilePlatformSessionToken('validation-old-session',previous),false);assert.equal(getBridgeToken(),'validation-current-session');assert.equal(getSessionRevision(),stable);
  context.parent={};assert.equal(reconcilePlatformSessionToken('validation-parent-mismatch'),false);assert.equal(getBridgeToken(),'validation-current-session');assert.equal(getSessionRevision(),stable);
});
test('neither a business write nor an original-file read is sent using a newly changed session',async t=>{
  const descriptors=['window','localStorage','sessionStorage'].map(key=>[key,Object.getOwnPropertyDescriptor(globalThis,key)] as const),oldFetch=globalThis.fetch;
  const context:{parent?:unknown;location:unknown}={location:{href:'http://validation.test/#/report',origin:'http://validation.test'}};context.parent=context;
  Object.defineProperty(globalThis,'window',{configurable:true,value:context});
  const storage={getItem:(key:string)=>key==='token'?'validation-current-session':null};
  for(const key of ['localStorage','sessionStorage'])Object.defineProperty(globalThis,key,{configurable:true,value:storage});
  t.after(()=>{globalThis.fetch=oldFetch;for(const [key,descriptor] of descriptors){if(descriptor)Object.defineProperty(globalThis,key,descriptor);else Reflect.deleteProperty(globalThis,key);}setBridgeToken(null);});
  let requests=0;globalThis.fetch=async()=>{requests++;throw new Error('Changed-session request must not reach transport');};
  setBridgeToken('validation-old-session');await assert.rejects(platformApi('/validation/write',{}),/登录会话已切换/);assert.equal(requests,0);
  setBridgeToken('validation-old-session');await assert.rejects(readUploadedOriginal('validation-file','validation.csv'),/登录会话已切换/);assert.equal(requests,0);
});
