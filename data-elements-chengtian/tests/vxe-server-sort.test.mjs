import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {webcrypto} from 'node:crypto';
import ts from 'typescript';
import {reactive,toRaw} from 'vue';
const source=fs.readFileSync(new URL('../src/components/_utils/useVxeGrid.ts',import.meta.url),'utf8').replace(/^import .*?;\s*$/mg,'');
const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText;
function fixture(extra={}){
 const mounted=[],calls=[],events=[],errors=[];
 const scope={exports:{},crypto:webcrypto,reactive,toRaw,ref:()=>({value:undefined}),onMounted:fn=>mounted.push(fn),onBeforeUnmount:()=>{},watch:()=>{},
  cloneDeep:v=>structuredClone(v),merge:(target,value)=>Object.assign(target,value),get:(object,path,fallback)=>path.split('.').reduce((v,k)=>v?.[k],object)??fallback,
  ElMessage:{error:error=>errors.push(error)},isRequiredSortColumn:(column,first)=>first||column.prop==='count',
  request:{post:async(url,body)=>{calls.push({url,body});return{keys:body.items.map(item=>item.id).reverse(),total:body.items.length};}}};
 vm.runInNewContext(code,scope);
 const props={editable:true,options:{rowConfig:{drag:true},columns:[{field:'name',title:'名称'},{field:'count',title:'数量'}]},...extra};
 const hook=scope.exports.useVxeGrid(props,(name,data)=>events.push({name,data}));mounted.forEach(fn=>fn());
 let rows=reactive([]);hook.gridRef.value={getTableData:()=>({fullData:rows}),loadData:async data=>{rows=reactive(data.slice());},remove:row=>{rows.splice(rows.indexOf(row),1);}};
 const load=data=>{hook.setData(data);rows=reactive(data.slice());};
 const sorted=async order=>{hook.gridEvents.sortChange({field:'name',order});for(let i=0;i<8;i++)await Promise.resolve();};
 return {hook,load,sorted,calls,events,errors,rows:()=>rows};
}
test('sorting preserves original business order and row edits/deletion through Vue proxies',async()=>{
 const f=fixture();const original=Array.from({length:100},(_,i)=>({id:i,name:'Row '+i,count:i}));f.load(original);
 await f.sorted('desc');assert.equal(f.calls.length,1);assert.equal(f.calls[0].body.items.length,100);assert.equal(f.calls[0].body.wholeCollection,true);
 assert.equal(f.rows()[0].id,99);f.rows()[0].name='Changed';f.hook.removeRow(f.rows()[1]);
 const saved=f.hook.getData();assert.equal(saved.length,99);assert.deepEqual(Array.from(saved,row=>row.id),original.filter(row=>row.id!==98).map(row=>row.id));assert.equal(saved.at(-1).name,'Changed');
 assert.equal(f.hook.gridOptions.rowConfig.drag,false);await f.sorted('');assert.equal(f.rows()[0].id,0);assert.equal(f.hook.gridOptions.rowConfig.drag,true);assert.equal(f.errors.length,0);
});
test('persisted pages delegate sort and never post page rows to draft ordering',async()=>{
 const received=[];const f=fixture({editable:false,sortProvider:async sort=>{received.push(sort);return[{name:'server result'}];}});f.load([{name:'current page'}]);await f.sorted('asc');
 assert.equal(f.calls.length,0);assert.equal(received[0].sortField,'name');assert.equal(f.hook.gridOptions.data[0].name,'server result');
});
test('20 and 100 draft rows each produce one request, with no per-row reads',async()=>{
 for(const count of [20,100]){const f=fixture();f.load(Array.from({length:count},(_,i)=>({name:'Row '+i})));await f.sorted('asc');assert.equal(f.calls.length,1);assert.equal(f.calls[0].body.items.length,count);}
});
