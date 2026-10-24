import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
const root=new URL('../../',import.meta.url);
const source=await fs.readFile(new URL('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/03.登记数据表(register-dbTable-catalog).vue',root),'utf8');
const script=source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1], babel={};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js',root),'utf8'),babel);
const ast=babel.Babel.transform(script,{ast:true,code:false}).ast;
const functions=['openSchemaSync','compareSchema','applySchemaSync'].map(name=>{const node=ast.program.body.find(n=>n.type==='FunctionDeclaration'&&n.id.name===name);return script.slice(node.start,node.end);}).join('\n');
for(const count of [20,100]) {
  const calls=[], messages=[];
  const context=vm.createContext({props:{detailTableId:'table'},activeTable:{value:{tableName:'t'}},detailSavedSnapshot:{value:'saved'},fieldExportRows:()=> 'saved',schemaSync:{requestId:0,applying:false,loading:false},ElMessageBox:{confirm:async()=>{}},saveDetailFields:async()=>true,$message:{success:v=>messages.push(v)},$common:{postSilently:async(route,body)=>{calls.push({route,body});return body.schemaAction==='compare'?{tableName:'t',canApply:true,fingerprint:'server-version',changes:Array.from({length:count},(_,i)=>({field:`f${i}`}))}:{applied:true,verified:true,appliedCount:count,changes:[],canApply:false};}}});
  // Set the exact same signature as the saved snapshot, without a UI-only save.
  context.detailSavedSnapshot.value=JSON.stringify('saved');
  vm.runInContext(functions,context);
  await context.openSchemaSync(); assert.equal(calls.length,1); assert.equal(calls[0].body.schemaAction,'compare');
  assert.equal(calls.length,1,'opening the comparison must not execute DDL');
  await context.applySchemaSync(); assert.equal(calls.length,2,'one explicit button action submits the reviewed plan');
  assert.deepEqual(Object.keys(calls[1].body).sort(),['acknowledged','confirmation','fingerprint','schemaAction','tableId']);
  assert.equal(calls[1].body.fingerprint,'server-version');
  assert.equal(calls[1].body.confirmation,'t'); assert.equal(calls[1].body.acknowledged,true);
  assert.equal(messages.length,1);
  await context.applySchemaSync(); assert.equal(calls.length,2,'completed plan cannot be submitted twice');
  context.schemaSync.plan={canApply:true,tableName:'t',fingerprint:'pending'};
  let resolveApply;
  context.$common.postSilently=async(route,body)=>{calls.push({route,body});return new Promise(resolve=>{resolveApply=resolve;});};
  const pending=context.applySchemaSync();
  await context.applySchemaSync(); assert.equal(calls.length,3,'pending operation suppresses repeated clicks');
  resolveApply({applied:true,verified:true,appliedCount:count,canApply:false,changes:[]}); await pending;
  context.schemaSync.plan={canApply:true,tableName:'t',fingerprint:'outdated'};
  context.$common.postSilently=async()=>{throw new Error('快照已变化，请重新比较');};
  await context.applySchemaSync(); assert.equal(context.schemaSync.plan.canApply,false);
  assert.match(context.schemaSync.error,/重新比较/);

  context.$common.postSilently=async()=>{throw new Error('连接失败，请重试');}; await context.compareSchema();
  assert.equal(context.schemaSync.plan,null); assert.match(context.schemaSync.error,/连接失败/); assert.equal(context.schemaSync.loading,false);
  console.log(`PASS: ${count} diffs, one compare request, one-click apply, duplicate-click guard, stale-plan rejection and unchanged server-owned payload`);
}
