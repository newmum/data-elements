import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createRequire} from 'node:module';
import test from 'node:test';
const require=createRequire(new URL('../../data-elements-chengtian/package.json',import.meta.url));
const ts=require('typescript');
const source=fs.readFileSync(new URL('../../data-elements-chengtian/src/utils/api-error.ts',import.meta.url),'utf8');
const start=source.indexOf('export function createApiRequestError(');
const end=source.indexOf('\nfunction detailRow(',start);
const js=ts.transpileModule(source.slice(start,end),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText;
const exported={};
const create=new Function('exports','ApiRequestError','readableJson',js+'; return exports.createApiRequestError;')(
  exported,class extends Error {constructor(info){super(info.message);this.info=info;}},JSON.stringify);
test('the concrete Magic business cause is displayed instead of its generic wrapper',()=>{
 const msg='推送登记同步服务尚未配置，请检查平台 data-element.push-registry 的 relay-base-url 和 standalone-sync-key';
 const error=create({code:503,msg,error:{code:'MAGIC-RUNTIME-001',message:'接口处理失败，请稍后重试；如仍失败，请查看详情并联系管理员。'}},{code:'HTTP-503',message:'失败'});
 assert.equal(error.message,msg);assert.equal(error.info.code,'MAGIC-RUNTIME-001');
});
test('specific structured errors retain their priority and ordinary Error fallback still works',()=>{
 assert.equal(create({msg:'通用错误',error:{message:'当前调用方无此数据源权限'}},{code:'403',message:'失败'}).message,'当前调用方无此数据源权限');
 assert.equal(create(null,{code:'HTTP-TIMEOUT',message:'请求超时'}).message,'请求超时');
});
