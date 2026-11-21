import {describe,it,expect} from 'vitest';
import {ensureModelExamples,ORDER_EXAMPLE_ID} from '../src/modelCatalogExample';
import {BUSINESS_MODEL_EXAMPLES,INVENTORY_EXAMPLE_ID,SALES_TREND_EXAMPLE_ID,DEVICE_ALARM_EXAMPLE_ID,DATA_QUALITY_EXAMPLE_ID} from '../src/modelBusinessExamples';
import {loadModelDocument,listModelDocuments,saveModelDocument,type ModelStorage} from '../src/modelCatalogStore';
import {modelCatalogCategories,filterModelCatalog} from '../src/modelCatalogView';
import {isConfigurationOnly,previewModel,type ModelNode} from '../src/modeling';
import {validateExecution,createMockRun,advanceMockRun} from '../src/modelingExecution';

function memory(){
 const values=new Map<string,string>();let writes=0;
 const store:ModelStorage={get length(){return values.size},key:i=>[...values.keys()][i]??null,getItem:key=>values.get(key)??null,setItem:(key,value)=>{writes++;values.set(key,value)}};
 return {store,values,get writes(){return writes}};
}
function seeded(id:string){
 const {store}=memory();ensureModelExamples(store);
 return loadModelDocument(id,store)!;
}
function result(id:string,output='output'){
 const doc=seeded(id);
 return previewModel(doc.nodes,id+'-'+output,doc.importedTables);
}

describe('business examples in the ordinary catalog',()=>{
 it('seeds five stable models and five business categories, then refreshes without writes or duplicates',()=>{
  const memoryStore=memory();const docs=ensureModelExamples(memoryStore.store);
  expect(docs.map(doc=>doc.id)).toEqual([ORDER_EXAMPLE_ID,...BUSINESS_MODEL_EXAMPLES.map(example=>example.id)]);
  expect(modelCatalogCategories(listModelDocuments(memoryStore.store))).toHaveLength(5);
  const before=[...memoryStore.values],writes=memoryStore.writes;
  for(let refresh=0;refresh<4;refresh++)ensureModelExamples(memoryStore.store);
  expect(memoryStore.writes).toBe(writes);
  expect([...memoryStore.values]).toEqual(before);
  expect(listModelDocuments(memoryStore.store)).toHaveLength(5);
 });
 it('preserves user models and every edit to an existing example when adding missing examples',()=>{
  const {store}=memory();const original=ensureModelExamples(store).find(doc=>doc.id===INVENTORY_EXAMPLE_ID)!;
  original.nodes[0].name='我的库存来源';
  const table=Object.values(original.importedTables)[0];table.rows[0].stock_quantity=11;
  const edited=saveModelDocument({...original,name:'自定义补货计划',category:'采购部专用',executionSettings:{engine:'spark',mode:'batch',scheduleEnabled:true,intervalMinutes:90}},original.revision,store);
  const mine=saveModelDocument({...original,id:'existing-user-model',name:'原有核对模型',category:'用户原有分类',sourceId:'existing-source',sourceLabel:'原有数据源'},null,store);
  const before=listModelDocuments(store);
  ensureModelExamples(store);
  expect(loadModelDocument(INVENTORY_EXAMPLE_ID,store)).toEqual(edited);
  expect(loadModelDocument('existing-user-model',store)).toEqual(mine);
  expect(listModelDocuments(store)).toEqual(before);
  expect(modelCatalogCategories(before)).toContain('用户原有分类');
 });
 it('keeps renamed order examples and fills only missing stable IDs in an older catalog',()=>{
  const {store}=memory();
  const order=ensureModelExamples(store)[0];
  const older=memory();
  const renamed=saveModelDocument({...order,name:'我的跨库报表',category:'原有分类'},null,older.store);
  ensureModelExamples(older.store);
  expect(loadModelDocument(ORDER_EXAMPLE_ID,older.store)).toEqual(renamed);
  expect(listModelDocuments(older.store)).toHaveLength(5);
 });
 it('does not overwrite a corrupt stable-ID record',()=>{
  const {store,values}=memory();ensureModelExamples(store);
  const key='fengchi:model-catalog:v1:'+encodeURIComponent(INVENTORY_EXAMPLE_ID);
  values.set(key,'broken-user-record');
  expect(()=>ensureModelExamples(store)).toThrow('损坏');
  expect(values.get(key)).toBe('broken-user-record');
 });
 it('reports a partial storage failure and safely completes only missing seeds on retry',()=>{
  const {store,values}=memory();let attempts=0;
  const limited:ModelStorage={get length(){return store.length},key:i=>store.key(i),getItem:key=>store.getItem(key),setItem:(key,value)=>{if(++attempts===3)throw Error('quota');store.setItem(key,value)}};
  expect(()=>ensureModelExamples(limited)).toThrow('空间不足');
  const saved=[...values];
  expect(saved).toHaveLength(2);
  ensureModelExamples(store);
  for(const [key,value] of saved)expect(values.get(key)).toBe(value);
  expect(listModelDocuments(store)).toHaveLength(5);
 });
 it('filters the same catalog records by category and model name without dropping existing categories',()=>{
  const {store}=memory();const docs=ensureModelExamples(store);
  saveModelDocument({...docs[0],id:'existing-user-model',name:'自有核对模型',category:'用户原有分类'},null,store);
  const all=listModelDocuments(store);
  expect(modelCatalogCategories(all)).toContain('用户原有分类');
  for(const [id,category,query] of [
   [INVENTORY_EXAMPLE_ID,'库存与供应',' 补货 '],
   [SALES_TREND_EXAMPLE_ID,'销售经营分析','销售'],
   [DEVICE_ALARM_EXAMPLE_ID,'设备运维','告警'],
   [DATA_QUALITY_EXAMPLE_ID,'数据质量','质量'],
   ['existing-user-model','用户原有分类','核对'],
  ]){
   expect(filterModelCatalog(all,category,query).map(doc=>doc.id)).toEqual([id]);
  }
  expect(filterModelCatalog(all,undefined,'示例')).toHaveLength(5);
  expect(filterModelCatalog(all,'数据质量','销售')).toEqual([]);
  expect(filterModelCatalog(all,undefined,'不存在的模型')).toEqual([]);
  expect(listModelDocuments(store)).toEqual(all);
 });
});

describe('local execution of saved business models',()=>{
 it.each(BUSINESS_MODEL_EXAMPLES)('$id opens with independent data and runs every output using supported operators',({id})=>{
  const doc=seeded(id),before=structuredClone(doc);
  expect(validateExecution(doc.nodes,doc.executionSettings).filter(issue=>issue.level==='error')).toEqual([]);
  expect(doc.nodes.some(node=>isConfigurationOnly(node.kind))).toBe(false);
  const outputs=doc.nodes.filter(node=>node.kind==='output');
  expect(outputs.length).toBeGreaterThan(0);
  for(const output of outputs){
   const result=previewModel(doc.nodes,output.id,doc.importedTables);
   expect(result.rows.length).toBeGreaterThan(0);
   expect(result.columns.length).toBeGreaterThan(0);
  }
  const run=advanceMockRun(advanceMockRun(createMockRun(doc.nodes,doc.executionSettings),doc.importedTables),doc.importedTables);
  expect(run.state).toBe('succeeded');
  expect(doc).toEqual(before);
 });
 it('returns only positive replenishment gaps, ordered by urgency',()=>{
  expect(result(INVENTORY_EXAMPLE_ID).rows).toEqual([
   {sku:'SKU-104','right.product_name':'振动探头',warehouse:'华北仓',stock_quantity:0,replenish_quantity:20,'right.supplier':'华北供应商'},
   {sku:'SKU-101','right.product_name':'温度传感器',warehouse:'华东仓',stock_quantity:8,replenish_quantity:12,'right.supplier':'华东供应商'},
   {sku:'SKU-102','right.product_name':'压力变送器',warehouse:'华南仓',stock_quantity:15,replenish_quantity:5,'right.supplier':'华南供应商'},
  ]);
 });
 it('merges both sales channels before month, cumulative and previous-month calculations',()=>{
  expect(result(SALES_TREND_EXAMPLE_ID).rows).toEqual([
   {sales_month:8,monthly_sales:750,cumulative_sales:750,previous_month_sales:null,report_year:2026},
   {sales_month:9,monthly_sales:1000,cumulative_sales:1750,previous_month_sales:750,report_year:2026},
   {sales_month:10,monthly_sales:1250,cumulative_sales:3000,previous_month_sales:1000,report_year:2026},
   {sales_month:11,monthly_sales:1500,cumulative_sales:4500,previous_month_sales:1250,report_year:2026},
  ]);
 });
 it('deduplicates events and excludes resolved or noncritical alarms',()=>{
  expect(result(DEVICE_ALARM_EXAMPLE_ID).rows).toEqual([
   {device_id:'FAN-03','right.device_name':'排风机','right.workshop':'喷涂车间',alarm_count:3},
   {device_id:'PUMP-01','right.device_name':'循环水泵','right.workshop':'动力车间',alarm_count:2},
   {device_id:'MOTOR-02','right.device_name':'输送电机','right.workshop':'装配车间',alarm_count:1},
  ]);
 });
 it('produces separate cleaned and missing-email results without exposing unmasked emails',()=>{
  const passed=result(DATA_QUALITY_EXAMPLE_ID).rows;
  expect(passed.map(row=>[row.customer_id,row.customer_name,row.quality_status])).toEqual([
   ['C001','林岚','通过'],['C002','未填写','通过'],['C004','陈越','通过'],['C005','何川','通过'],
  ]);
  expect(passed.every(row=>/^\*+\.com$/.test(String(row.email)))).toBe(true);
  expect(result(DATA_QUALITY_EXAMPLE_ID,'issues-output').rows).toEqual([
   {customer_id:'C003',customer_name:'周晴',quality_issue:'缺少邮箱'},
  ]);
 });
 it('uses different operator combinations across the four business scenarios',()=>{
  const combinations=BUSINESS_MODEL_EXAMPLES.map(({create})=>[...new Set(create().nodes.map((node:ModelNode)=>node.kind))].sort().join(','));
  expect(new Set(combinations).size).toBe(4);
 });
});
