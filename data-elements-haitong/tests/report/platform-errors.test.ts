import test from 'node:test';
import type {TestContext} from 'node:test';
import assert from 'node:assert/strict';
import {platformApi,PlatformApiError} from '../../src/api/platformApi';
import {diagnosticText,platformResponseError,safeDiagnostic} from '../../src/api/platformError';
import {uploadOriginalFiles} from '../../src/report/services/uploadWorkbench';
import {setBridgeToken} from '../../src/nifi/api/bridgeSession';

const response={code:500,success:false,msg:'接口脚本执行失败，请联系管理员并提供错误码和跟踪号。',error:{
  code:'MAGIC-SCRIPT-001',message:'接口脚本执行失败，请联系管理员并提供错误码和跟踪号。',
  detail:'org.ssssssss.script.exception.MagicScriptException: upload failed\n at script.file(upload.ms:50)\nCaused by: java.lang.IllegalStateException: 请配置 data-element.legacy-integration.minio-password',
  traceId:'validation-upload-trace',type:'org.ssssssss.script.exception.MagicScriptException',method:'POST',path:'/data/file',
}};

function setup(t:TestContext){
  const keys=['window','localStorage','sessionStorage','fetch'] as const;
  const previous=keys.map(key=>[key,Object.getOwnPropertyDescriptor(globalThis,key)] as const);
  const location={origin:'http://validation.test',href:'http://validation.test/#/report',hash:'#/report',replace(){}};
  const windowValue:{parent?:unknown;location:typeof location}={location};windowValue.parent=windowValue;
  Object.defineProperty(globalThis,'window',{configurable:true,value:windowValue});
  const storage={getItem:(key:string)=>key==='token'?'validation-session-value':null,setItem(){}};
  for(const key of ['localStorage','sessionStorage'])Object.defineProperty(globalThis,key,{configurable:true,value:storage});
  setBridgeToken('validation-session-value');
  t.after(()=>{for(const[key,descriptor]of previous){if(descriptor)Object.defineProperty(globalThis,key,descriptor);else Reflect.deleteProperty(globalThis,key);}setBridgeToken(null);});
}

test('HTTP 200 business errors and HTTP 500 both retain the actual cause, trace and request',async t=>{
  setup(t);
  for(const status of [200,500]){
    globalThis.fetch=async()=>new Response(JSON.stringify(response),{status});
    await assert.rejects(platformApi('/data/file?action=upload',new FormData()),cause=>{
      assert.ok(cause instanceof PlatformApiError);assert.equal(cause.status,status);
      assert.equal(cause.info.code,'MAGIC-SCRIPT-001');assert.equal(cause.info.traceId,'validation-upload-trace');
      assert.match(cause.info.reason!,/请配置 data-element\.legacy-integration\.minio-password/);
      assert.equal(cause.info.detail,response.error.detail);assert.equal(cause.info.method,'POST');assert.equal(cause.info.path,'/data/file');
      assert.doesNotMatch(diagnosticText(cause.info),/validation-session-value/);return true;
    });
  }
});

test('proxy HTML 413 is actionable and 502/invalid JSON never replaces a structured error with a parse error',async t=>{
  setup(t);
  globalThis.fetch=async()=>new Response('<html>Request Entity Too Large</html>',{status:413});
  await assert.rejects(platformApi('/data/file?action=upload',new FormData()),cause=>{
    assert.ok(cause instanceof PlatformApiError);assert.equal(cause.status,413);assert.match(cause.message,/服务端或网关/);assert.doesNotMatch(cause.info.detail||'',/<html>/);return true;
  });
  globalThis.fetch=async()=>new Response('<html>Bad Gateway</html>',{status:502});
  await assert.rejects(platformApi('/validation'),/502/);
  globalThis.fetch=async()=>new Response('not JSON');await assert.rejects(platformApi('/validation'),/返回格式/);
});

test('details and copy text redact secrets while preserving error codes and root causes',()=>{
  const raw='Caused by: AccessDenied: object storage rejected upload\nAuthorization: AWS4-HMAC-SHA256 Credential=validation-credential, Signature=validation-signature\n{"password":"validation-password","token":"validation-token","secretKey":"validation-secret"}\nhttps://validation-user:validation-pass@storage.test/path?X-Amz-Credential=validation-signed-credential&X-Amz-Signature=validation-signed-value';
  const error=platformResponseError({...response,error:{...response.error,detail:raw}},'/data/file','上传失败',500,'POST');
  const text=diagnosticText(error.info);assert.match(text,/AccessDenied/);assert.match(text,/validation-upload-trace/);
  for(const secret of ['validation-credential','validation-signature','validation-password','validation-token','validation-secret','validation-user','validation-pass','validation-signed-credential','validation-signed-value'])assert.equal(text.includes(secret),false,secret);
  assert.match(text,/已隐藏/);assert.equal(safeDiagnostic({token:'validation-value'}),'');
});

test('a concrete business message takes priority over a generic wrapper; no stack is invented',()=>{
  const error=platformResponseError({msg:'文件为空，请选择有内容的数据文件',error:{message:'接口处理失败',code:'FILE-EMPTY'}},'/data/file','失败',400,'POST');
  assert.equal(error.message,'文件为空，请选择有内容的数据文件');assert.equal(error.info.reason,undefined);assert.equal(error.info.detail,error.message);
});

test('all failed original uploads produce individual errors and no parse/save requests',async t=>{
  setup(t);const calls:string[]=[],progress:number[]=[];
  globalThis.fetch=async input=>{calls.push(String(input));return new Response(JSON.stringify(response));};
  const files=['validation_a.csv','validation_b.json'].map(name=>new File(['id\n1'],name));
  const result=await uploadOriginalFiles(files,value=>progress.push(value),()=>{});
  assert.equal(result.uploaded.length,0);assert.equal(result.failures.length,2);assert.deepEqual(progress,[1,2]);
  assert.deepEqual(result.failures.map(item=>item.fileName),files.map(file=>file.name));
  assert.ok(result.failures.every(item=>item.cause instanceof PlatformApiError&&item.cause.info.traceId==='validation-upload-trace'));
  assert.equal(calls.length,2);assert.ok(calls.every(path=>path.endsWith('/data/file?action=upload')));
});

test('mixed uploads retain only successful originals, local validation makes no request, auth expiry stops subsequent uploads',async t=>{
  setup(t);let calls=0;
  globalThis.fetch=async()=>new Response(JSON.stringify(++calls===1?{code:0,data:{tid:'validation-file'}}:response));
  const result=await uploadOriginalFiles([new File(['id\n1'],'validation_good.csv'),new File(['id\n1'],'validation_bad.csv'),new File(['text'],'validation_invalid.pdf')],()=>{},()=>{});
  assert.equal(calls,2);assert.equal(result.uploaded.length,1);assert.equal(result.uploaded[0].fileId,'validation-file');assert.equal(result.failures.length,2);
  calls=0;globalThis.fetch=async()=>{calls++;return new Response(JSON.stringify({code:401,msg:'登录已失效'}),{status:401});};
  await assert.rejects(uploadOriginalFiles([new File(['id\n1'],'validation_a.csv'),new File(['id\n1'],'validation_b.csv')],()=>{},()=>{}));assert.equal(calls,1);
});
