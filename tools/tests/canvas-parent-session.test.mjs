import fs from 'node:fs';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(new URL('../../data-elements-haitong/package.json',import.meta.url));const ts=require('typescript');
const source=fs.readFileSync(new URL('../../lowcode/03.数据接入/2.3.1 数据汇聚(data-convergence)/任务配置(schedule-setting).vue',import.meta.url),'utf8');
function tokenReader(window,localStorage,sessionStorage){
 const input=source.slice(source.indexOf('const storedToken ='),source.indexOf('const normalizeBaseUrl ='));
 const code=ts.transpileModule(input,{compilerOptions:{module:ts.ModuleKind.None}}).outputText;
 return new Function('window','localStorage','sessionStorage',code+'\nreturn getToken;')(window,localStorage,sessionStorage);
}
test('view-task INIT uses the authoritative host session rather than a stale unprefixed account',()=>{
 const storage={getItem:()=>{throw new Error('must not consult stale storage when host getter exists');}};
 const getToken=tokenReader({__DATA_ELEMENTS_GET_PLATFORM_TOKEN__:()=> 'validation-current-police'},storage,storage);
 assert.equal(getToken(),'validation-current-police');
 const messages=[];const window={location:{href:'https://validation.test/wanxiang/#/jr/data-convergence/list'}};
 const designerUrl={value:'https://validation.test/wanxiang/haitong/?_t=1#/development/canvas?tid=visible-table&accessTaskId=visible-task'};
 const block=source.slice(source.indexOf('const sendInitMessage ='),source.indexOf('const detectUnexpectedLogin ='));
 new Function('getToken','designerUrl','window','iframeRef','props','$user',block+'\nreturn sendInitMessage;')(
  getToken,designerUrl,window,{value:{contentWindow:{postMessage:(message,origin)=>messages.push({message,origin})}}},
  {id:'visible-table',accessTaskId:'visible-task'},{orgId:'visible-org'})();
 assert.equal(messages[0].message.payload.token,'validation-current-police');
 assert.equal(messages[0].origin,'https://validation.test');
 assert.equal(messages[0].message.payload.accessTaskId,'visible-task');
 assert.equal(designerUrl.value.includes('validation-current-police'),false);
});
test('an explicitly empty current host session never falls back to another account',()=>{
 const stale={getItem:key=>key==='remember_me'?'true':'validation-stale'};
 assert.equal(tokenReader({__DATA_ELEMENTS_GET_PLATFORM_TOKEN__:()=>''},stale,stale)(),'');
});
test('older unnamespaced source hosts retain their original remembered and per-tab behavior',()=>{
 const local={getItem:key=>key==='remember_me'?'true':'validation-legacy'};
 assert.equal(tokenReader({},local,{getItem:()=>null})(),'validation-legacy');
 assert.equal(tokenReader({},{getItem:()=>null},{getItem:()=>JSON.stringify('validation-tab')})(),'validation-tab');
});
