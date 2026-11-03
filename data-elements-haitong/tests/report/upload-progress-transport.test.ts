import test from 'node:test';
import assert from 'node:assert/strict';
import {platformApi,type PlatformUploadProgress} from '../../src/api/platformApi';
import {setBridgeToken} from '../../src/nifi/api/bridgeSession';

class FakeUpload {
  static requests:FakeUpload[]=[];
  upload:{onprogress?: (event:{loaded:number;total:number;lengthComputable:boolean})=>void;onload?: (event:{loaded:number;total:number;lengthComputable:boolean})=>void}={};
  headers:Record<string,string>={};status=200;responseText='{"code":0,"data":{"status":"success"}}';timeout=0;
  onload?:()=>void;onerror?:()=>void;ontimeout?:()=>void;onabort?:()=>void;
  open(_method:string,_url:string){}setRequestHeader(name:string,value:string){this.headers[name]=value;}
  send(body:unknown){assert.ok(body instanceof FormData);FakeUpload.requests.push(this);}
  abort(){this.onabort?.();}
}

test('one byte-progress transport for 20/100 items, upload completion waits for delivery acknowledgment, session changes are guarded',async t=>{
  const keys=['window','localStorage','sessionStorage','XMLHttpRequest'] as const;
  const before=keys.map(key=>[key,Object.getOwnPropertyDescriptor(globalThis,key)] as const);
  const context:{parent?:unknown;location:unknown}={location:{origin:'http://validation.test',href:'http://validation.test/#/report',hash:'#/report',replace(){}}};context.parent=context;
  Object.defineProperty(globalThis,'window',{configurable:true,value:context});
  const storage={getItem:(key:string)=>key==='token'?'validation-upload-session':null,setItem(){}};
  for(const key of ['localStorage','sessionStorage'])Object.defineProperty(globalThis,key,{configurable:true,value:storage});
  Object.defineProperty(globalThis,'XMLHttpRequest',{configurable:true,value:FakeUpload});setBridgeToken('validation-upload-session');
  t.after(()=>{for(const [key,value] of before){if(value)Object.defineProperty(globalThis,key,value);else Reflect.deleteProperty(globalThis,key);}setBridgeToken(null);});
  for(const count of [20,100]){
    const events:PlatformUploadProgress[]=[];const body=new FormData();body.append('file',new Blob([JSON.stringify(Array.from({length:count},(_,i)=>i))]));
    let settled=false;const start=FakeUpload.requests.length;
    const pending=platformApi<{status:string}>('/validation/upload',body,{timeoutMs:120_000,onUploadProgress:value=>events.push(value)}).then(result=>{settled=true;return result;});
    await new Promise(resolve=>setTimeout(resolve,0));assert.equal(FakeUpload.requests.length-start,1);
    const request=FakeUpload.requests.at(-1)!;assert.equal(request.timeout,120_000);assert.equal(request.headers['Content-Type'],undefined);
    request.upload.onprogress?.({loaded:25,total:100,lengthComputable:true});request.upload.onload?.({loaded:100,total:100,lengthComputable:true});
    assert.deepEqual(events,[{loaded:25,total:100,complete:false},{loaded:100,total:100,complete:true}]);assert.equal(settled,false);
    request.onload?.();assert.equal((await pending).status,'success');
  }
  const controller=new AbortController();const canceled=platformApi('/validation/cancel',new FormData(),{signal:controller.signal,onUploadProgress(){}});
  await new Promise(resolve=>setTimeout(resolve,0));controller.abort();await assert.rejects(canceled);
  const events:PlatformUploadProgress[]=[];const stale=platformApi('/validation/stale',new FormData(),{onUploadProgress:value=>events.push(value)});
  await new Promise(resolve=>setTimeout(resolve,0));const request=FakeUpload.requests.at(-1)!;setBridgeToken('validation-other-session');
  request.upload.onprogress?.({loaded:50,total:100,lengthComputable:true});assert.deepEqual(events,[]);request.onload?.();await assert.rejects(stale,/登录会话已切换/);
});
