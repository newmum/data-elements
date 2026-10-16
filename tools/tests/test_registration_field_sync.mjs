import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
async function extract(file, names) {
  const source = await fs.readFile(new URL(file, root), 'utf8');
  const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
  const ast = babel.Babel.transform(script, {ast:true, code:false, parserOpts:{sourceType:'module',plugins:['typescript']}}).ast;
  return names.map(name => {
    const node=ast.program.body.find(n=>n.type==='FunctionDeclaration'?n.id.name===name:n.type==='VariableDeclaration'&&n.declarations.some(d=>d.id.name===name));
    assert.ok(node,name);return script.slice(node.start,node.end);
  }).join('\n');
}
const catalog = await extract('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/03.登记数据表(register-dbTable-catalog).vue',['normalizeFieldRows','syncActiveTableFields']);
for (const count of [20,100,260]) {
  const rows=Array.from({length:count},(_,i)=>({columnName:`field_${i}`,columnComment:i===1?'':`新中文名${i}`,dataType:'varchar'}));
  rows.push({columnName:'new_field',columnComment:'新增字段',dataType:'int'});
  const relation={enabled:true,mode:'enum'};
  const previous=Array.from({length:count},(_,i)=>({columnName:i===0?'FIELD_0':`field_${i}`,columnComment:'旧中文名',dictionaryRelation:relation,timeRoles:i===0?['timestamp']:[],standardField:'DATE'}));
  const calls=[];let invalidations=0;
  const context=vm.createContext({fieldMap:{wide:previous},activeTable:{value:{tid:'table-a',tableName:'wide'}},syncingFields:{value:false},fieldErrorMap:{},connectionErrorMap:{},dictionaryPreviewRequestVersion:{value:0},dictionaryPreview:{loaded:true,initialPage:{rows:[]}},
    savedCatalogFieldMap:()=>new Map(),suggestField:()=>({}),normalizeStandardField:v=>v||'',autoStandardField:()=>'',normalizeTimeRoles:v=>Array.isArray(v)?v:[],isTimestampEnabled:v=>v===true,normalizePrimaryKey:v=>Boolean(v),normalizeFieldBase:()=>'',buildColumnType:r=>r.dataType,
    ElMessageBox:{confirm:async()=>{}},$common:{postSilently:async(route,payload)=>{calls.push({route,payload});return rows}},$message:{success:()=>{},warning:()=>{}},unwrapList:v=>v,reportConnectionFailure:()=>false,resetBusinessPreview:()=>invalidations++});
  vm.runInContext(catalog+'\nglobalThis.sync=syncActiveTableFields;',context);
  await context.sync();
  assert.equal(calls.length,1,`${count} fields use one synchronization request`);assert.equal(calls[0].payload.force,true);
  const actual=context.fieldMap.wide;assert.equal(actual.length,count+1);assert.equal(actual[0].columnComment,'新中文名0');assert.equal(actual[1].columnComment,'');assert.equal(actual.at(-1).columnName,'new_field');
  assert.deepEqual(actual[0].dictionaryRelation,relation);assert.equal(actual[0].timeRoles[0],'timestamp');assert.equal(actual[0].standardField,'DATE');assert.equal(context.activeTable.value.fieldCount,count+1);assert.equal(invalidations,1);assert.equal(context.dictionaryPreview.loaded,false);
}
const discovery = await extract('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/02.探查数据表(register-dbTable).vue',['syncPreviewFields']);
const state={currentPreviewTableId:'a',currentTableName:'wide',syncingFields:false,fieldRows:[]};let resolve;
const context=vm.createContext({state,ElMessageBox:{confirm:async()=>{}},$common:{post:async()=>new Promise(r=>{resolve=r})},normalizeFieldRows:v=>v,normalizePreviewFieldsForDisplay:v=>v,$message:{success:()=>{},warning:()=>{}}});
vm.runInContext(discovery+'\nglobalThis.sync=syncPreviewFields;',context);
const pending=context.sync();await new Promise(setImmediate);state.currentPreviewTableId='b';resolve([{columnName:'old_response'}]);await pending;assert.equal(state.fieldRows.length,0,'Switching tables must not display the old table response');
console.log('PASS: 20/100/260 fields with one request, latest and cleared comments, added fields, case-insensitive governance preservation, preview invalidation and stale-table protection');
