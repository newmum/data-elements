import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import {createRequire} from 'node:module';
import test from 'node:test';

const root=new URL('../../',import.meta.url);
const require=createRequire(new URL('data-elements-chengtian/package.json',root));
const {computed,ref}=require('vue');
const source=await fs.readFile(new URL('lowcode/09.系统管理/09.日志管理/日志管理(log-management).vue',root),'utf8');
const script=source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const sandbox={};vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js',root),'utf8'),sandbox);
const ast=sandbox.Babel.transform(script,{ast:true,code:false,configFile:false,babelrc:false,parserOpts:{sourceType:'module',plugins:['typescript']}}).ast;
const names=new Set(['browserLabel','clientOS','detailFieldText','searchPlaceholder','detailGroups']);
const nodes=ast.program.body.filter(n=>names.has(n.id?.name)||n.declarations?.some(d=>names.has(d.id?.name)));
assert.equal(nodes.length,names.size);
function fixture(kindValue){
  const kind=ref(kindValue),detail=ref(null);
  const ctx=vm.createContext({computed,kind,detail,eventMode:computed(()=>!['exports','archives'].includes(kind.value)),time:x=>String(x),results:{SUCCESS:'成功'}});
  const executable=nodes.map(n=>script.slice(n.start,n.end)).join('\n')+'\nglobalThis.actual={browserLabel,clientOS,detailFieldText,searchPlaceholder,detailGroups};';
  vm.runInContext(sandbox.Babel.transform(executable,{presets:['typescript'],filename:'log.ts'}).code,ctx);
  return {kind,detail,...ctx.actual};
}
test('Stored UA identifies Edge before Chrome, mobile browsers and non-browser clients; absent history stays absent',()=>{
  const f=fixture('auth');
  const cases=[
    ['Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0','Edge 141.0.0.0','Windows 10 / 11'],
    ['Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) Version/18.6 Mobile/15E148 Safari/604.1','Safari 18.6','iOS 18.6'],
    ['Mozilla/5.0 (Linux; Android 14) Chrome/140.0.0.0 Safari/537.36','Chrome 140.0.0.0','Android 14'],
    ['Mozilla/5.0 (X11; Linux x86_64; rv:144.0) Gecko/20100101 Firefox/144.0','Firefox 144.0','Linux'],
    ['PostmanRuntime/7.37.3','Postman 7.37.3',''],
    ['', '历史未记录',''],
    [undefined,'历史未记录',''],
  ];
  for(const [ua,browser,os] of cases){assert.equal(f.browserLabel(ua),browser);assert.equal(f.clientOS(ua),os);}
  assert.equal(f.browserLabel('validation-client'),'其他客户端');
});
test('Auth omits menu and duration, request details retain zero milliseconds and full escaped-as-text UA',()=>{
  const f=fixture('auth');
  const fields=()=>f.detailGroups.value.flatMap(g=>g.fields.map(x=>x[0]));
  assert.ok(!fields().includes('menu_path'));assert.ok(!fields().includes('duration_ms'));
  assert.ok(fields().includes('user_agent'));assert.ok(fields().includes('browser'));
  f.detail.value={user_agent:'PostmanRuntime/7.37.3',duration_ms:0};
  assert.equal(f.detailFieldText('browser'),'Postman 7.37.3');
  assert.equal(f.detailFieldText('user_agent'),'PostmanRuntime/7.37.3');
  f.kind.value='requests';assert.ok(fields().includes('menu_path'));assert.ok(fields().includes('duration_ms'));
  assert.equal(f.detailFieldText('duration_ms'),'0 ms');
  f.detail.value={};assert.equal(f.detailFieldText('duration_ms'),'历史未记录');
  f.kind.value='operations';assert.ok(!fields().includes('duration_ms'));
  assert.match(f.searchPlaceholder.value,/菜单、功能或接口/);
});
