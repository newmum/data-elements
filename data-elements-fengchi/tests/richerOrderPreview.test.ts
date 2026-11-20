import {describe,expect,it} from 'vitest';
import {createModelingDemo} from '../src/modelingDemo';
import {previewModel} from '../src/modeling';
import {getPreviewColumns} from '../src/modelingPreviewColumns';
import {openOrderExample,ORDER_EXAMPLE_ID} from '../src/modelCatalogExample';
import {saveModelDocument,type ModelStorage} from '../src/modelCatalogStore';
function fixtureStorage():ModelStorage{
 const values=new Map<string,string>();
 return {get length(){return values.size},key:index=>[...values.keys()][index]??null,getItem:key=>values.get(key)??null,setItem:(key,value)=>{values.set(key,value)}};
}
function seedLegacy(storage:ModelStorage){
 const demo=createModelingDemo({legacy:true});
 return saveModelDocument({id:ORDER_EXAMPLE_ID,name:'跨库客户订单示例',category:'客户订单分析',nodes:demo.nodes,importedTables:demo.tables,executionSettings:{engine:'flink',mode:'batch'}},null,storage);
}
describe('richer synthetic order preview',()=>{
 it('actually runs six regional rows with observed string, decimal and datetime columns',()=>{
  const demo=createModelingDemo();
  const result=previewModel(demo.nodes,demo.outputId,demo.tables);
  expect(result.rows).toEqual(demo.expectedRows);
  expect(result.rows).toHaveLength(6);
  expect(result.rows[0]).toMatchObject({'right.region_name':'华东',total_amount:550});
  expect(result.rows[1]).toMatchObject({'right.region_name':'华南',total_amount:300});
  expect(getPreviewColumns(result,demo.nodes,demo.tables,demo.outputId)).toEqual([
   {key:'right.region_name',comment:'销售地区',type:'varchar',kind:'text'},
   {key:'right.region_code',comment:'地区编码',type:'varchar',kind:'text'},
   {key:'right.sales_hub',comment:'区域中心',type:'varchar',kind:'text'},
   {key:'right.currency',comment:'结算币种',type:'char(3)',kind:'text'},
   {key:'total_amount',comment:'明细入账金额合计',type:'decimal',kind:'number'},
   {key:'right.report_as_of',comment:'统计截止时间',type:'datetime',kind:'time'}
  ]);
 });
 it('upgrades only the untouched persisted original seed, idempotently',()=>{
  const storage=fixtureStorage();
  seedLegacy(storage);
  const upgraded=openOrderExample(storage);
  expect(upgraded.revision).toBe(2);
  expect(upgraded.nodes.some(n=>n.id==='demo-report-fields')).toBe(true);
  expect(openOrderExample(storage)).toEqual(upgraded);
 });
 it('does not overwrite an edited or other existing model',()=>{
  const storage=fixtureStorage(),seed=seedLegacy(storage);
  const edited=saveModelDocument({...seed,startPosition:{x:100,y:100}},seed.revision,storage);
  expect(openOrderExample(storage)).toEqual(edited);
  const other=saveModelDocument({...seed,id:'user-model',name:'我的模型'},null,storage);
  expect(other.revision).toBe(1);
 });
 it('falls back to values, not field names, and does not mislabel a mixed/empty column',()=>{
  const result={columns:['time_named','n','as_of','mixed','empty'],rows:[{time_named:'plain',n:2,as_of:'2026-11-10',mixed:4,empty:null},{time_named:'hello',n:3,as_of:'2026-11-11',mixed:'four',empty:null}]};
  expect(getPreviewColumns(result,[],{}).map(c=>c.kind)).toEqual(['text','number','time','other','other']);
 });
});
