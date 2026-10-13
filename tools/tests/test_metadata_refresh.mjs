import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
async function handlers(file, names) {
  const source = await fs.readFile(new URL(file.replaceAll('%','%25'), root), 'utf8');
  const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
  const ast = babel.Babel.transform(script, {ast:true,code:false,configFile:false,babelrc:false,
    parserOpts:{sourceType:'module',plugins:['typescript']}}).ast;
  const selected=names.map(name=>{
    const node=ast.program.body.find(n=>n.type==='VariableDeclaration' && n.declarations.some(d=>d.id.name===name));
    assert.ok(node,name); return script.slice(node.start,node.end);
  }).join('\n');
  return babel.Babel.transform(selected, {presets:['typescript'],filename:'handlers.ts',configFile:false,babelrc:false}).code + '\nglobalThis.actual={'+names.join(',')+'};';
}
const register=await handlers('lowcode/01.系统门户/01.数据源管理/03.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue', ['refreshSavedDatasourceMetadata']);
for (const failed of [false,true]) {
  const calls=[];
  const ctx=vm.createContext({sourceType:{value:'mysql'},relationalSourceTypes:['mysql'],
    $common:{getSilently:async(url,body)=>{calls.push({url,body});if(failed)throw new Error('offline');return {tableNum:0};}}});
  vm.runInContext(register,ctx);
  const result=await ctx.actual.refreshSavedDatasourceMetadata('db',{showConnect:'1'});
  assert.equal(calls.length,1);assert.equal(result.warning,failed);
  if(!failed)assert.equal(result.metadata.tableNum,0);
  await ctx.actual.refreshSavedDatasourceMetadata('db',{showConnect:'0'});
  assert.equal(calls.length,1,'No physical probe for a datasource without a connection');
}
const detail=await handlers('lowcode/02.数据盘点/02.登记流程组件/详情页面(detail-page)/1. 资产【app%7Cdb%7Ctable%7Ccategory】详情(base-detail).vue',
 ['titleActions','isTitleActionDisabled','resolveActionValue','handleTitleAction','detailApi','displayDataForDetail']);
for(const size of [20,100]) {
  const calls=[],values=[],notices=[];
  const ctx=vm.createContext({detailClass:{value:'table'},props:{tid:'table'},state:{editing:false,actionLoading:{},formData:{datasourceId:'db',recordCount:99}},
    formRef:{value:{setValue:data=>values.push(data)}},store:{setData:()=>{}},
    $common:{get:async(url,body)=>{calls.push({url,body});return {recordCount:0};},
      post:async(url,body)=>{calls.push({url,body});return {detail:{recordCount:0,fieldCount:0,storageSize:'0 B'}};}},
    $message:{success:text=>notices.push(text),error:assert.fail}});
  vm.runInContext(detail,ctx);
  const rule={props:{name:'元数据信息'}},action=ctx.actual.titleActions(rule)[0];
  assert.equal(ctx.actual.titleActions(rule).length,1);
  await ctx.actual.handleTitleAction(action);
  assert.equal(calls.length,2,`Viewing ${size} list rows still refreshes only the selected table`);
  assert.equal(calls[0].body.tid,'db');assert.equal(calls[0].body.tableId,'table');
  assert.equal(values[0].recordCount,0);assert.equal(values[0].fieldCount,0);
  assert.equal(notices.length,1);assert.equal(ctx.state.actionLoading.refreshMetadata,false);
  assert.equal(rule.props.actions,undefined,'Dynamic tenant form configuration is not mutated');
}
const display=await handlers('lowcode/02.数据盘点/02.登记流程组件/详情页面(detail-page)/1. 资产【app%7Cdb%7Ctable%7Ccategory】详情(base-detail).vue',['metadataDisplayRule']);
const displayCtx=vm.createContext({detailClass:{value:'db'}});
vm.runInContext(display,displayCtx);
const totalRule={field:'totalData',title:'总数据量',props:{suffix:' 条'}};
const totalDisplay=displayCtx.actual.metadataDisplayRule(totalRule);
assert.equal(totalDisplay.field,'totalSize');assert.equal(totalDisplay.title,'总占用空间');assert.equal(totalDisplay.props.suffix,'');
assert.equal(totalRule.props.suffix,' 条','Stored tenant form remains unchanged');
assert.equal(displayCtx.actual.metadataDisplayRule({field:'procCount'}).field,'procedureCount');
console.log('Metadata refresh: one current-source request, one selected-table refresh plus detail read; 0 values, offline partial success, no-connection and accurate overview units passed.');
