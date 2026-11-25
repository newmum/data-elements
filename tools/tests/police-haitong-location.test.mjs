import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire } from 'node:module';
const require=createRequire(new URL('../../data-elements-haitong/package.json',import.meta.url));
const ts=require('typescript');
const input=fs.readFileSync(new URL('../../data-elements-chengtian/src/utils/capabilityCenters.ts',import.meta.url),'utf8');
function registry(href,env={DEV:false}){
 const compiled=ts.transpileModule(input,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText.replaceAll('import.meta.env','viteEnv');
 const module={exports:{}};new Function('module','exports','viteEnv','window',compiled)(module,module.exports,env,{location:{href}});return module.exports.capabilityCenterBases;
}
test('source navigation opens HaiTong under the same root or reverse-proxy prefix',()=>{
 for(const prefix of ['/','/wanxiang/','/custom/platform/']){
  const platform='https://validation.test'+prefix+'#/home';
  assert.equal(registry(platform).haitong,prefix+'haitong/');
  const destination=new URL(registry(platform).haitong,platform);destination.hash='/integration/overview';
  assert.equal(destination.href,'https://validation.test'+prefix+'haitong/#/integration/overview');
  assert.equal(new URL('../prod-api/nifi/flow/page',destination).pathname,prefix+'prod-api/nifi/flow/page');
  assert.equal(new URL('../#/',destination).href,'https://validation.test'+prefix+'#/');
 }
});
test('index.html, explicit addresses and original local development retain their meanings',()=>{
 assert.equal(registry('https://validation.test/wanxiang/index.html#/home').haitong,'/wanxiang/haitong/');
 assert.equal(registry('https://validation.test/wanxiang/',{DEV:false,VITE_HAITONG_BASE_URL:'https://haitong.internal/'}).haitong,'https://haitong.internal/');
 assert.equal(registry('http://localhost:3000/',{DEV:true}).haitong,'http://localhost:3002/');
});
