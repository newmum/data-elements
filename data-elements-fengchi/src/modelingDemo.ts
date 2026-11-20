import {createModelNode,type ModelNode} from './modeling';
import type {TableDef,Row,Column} from './data';

export const DEMO_SOURCE_LABELS:Record<string,string>={
 'demo.DB1.customers':'客户库 / 客户表',
 'demo.DB2.orders':'交易库 / 订单表',
 'demo.DB2.order_items':'交易库 / 订单明细表',
 'demo.DB2.products':'交易库 / 商品表',
 'demo.DB3.customer_region_view':'区域库 / 客户地区视图',
};
export type ModelingDemoSource={database:'DB1'|'DB2'|'DB3';table:string;tableKey:string;nodeId:string;kind:'table'|'view'};
const LEGACY_DEMO_VIEW_SQL=`-- 溯源示意：本示例不执行 SQL，视图行由本地夹具关联生成
SELECT cr.region_customer_id, r.region_name
FROM DB3.customer_regions cr
JOIN DB3.regions r ON cr.region_id = r.region_id;`;
export const DEMO_VIEW_SQL=LEGACY_DEMO_VIEW_SQL.replace('r.region_name\n','r.region_name, r.region_code, r.sales_hub, r.currency, r.report_as_of\n');
function table(name:string,comment:string,fields:[string,string][],rows:Row[]):TableDef {
 const columns:Column[]=fields.map(([name,type])=>({name,type}));return {name,comment,columns,rows};
}
/** Isolated synthetic dataset. Never merges or reads live/shared workspace tables. */
export function createModelingDemo(options:{legacy?:boolean}={}){
 const regions=[{region_id:1,region_name:'华东'},{region_id:2,region_name:'华南'}];
 const customerRegions=[{region_customer_id:101,region_id:1},{region_customer_id:102,region_id:2},{region_customer_id:103,region_id:1}];
 const regionView:Row[]=customerRegions.flatMap(c=>regions.filter(r=>r.region_id===c.region_id).map(r=>({region_customer_id:c.region_customer_id,region_name:r.region_name})));
 const tables:Record<string,TableDef>={
  'demo.DB1.customers':table('customers','DB1 · 合成客户',[['customer_id','int'],['customer_name','varchar']], [{customer_id:101,customer_name:'客户甲'},{customer_id:102,customer_name:'客户乙'},{customer_id:103,customer_name:'客户丙'}]),
  'demo.DB2.orders':table('orders','DB2 · 合成订单',[['order_id','int'],['order_customer_id','int'],['order_status','varchar']], [{order_id:201,order_customer_id:101,order_status:'completed'},{order_id:202,order_customer_id:102,order_status:'completed'},{order_id:203,order_customer_id:103,order_status:'completed'},{order_id:204,order_customer_id:101,order_status:'completed'},{order_id:205,order_customer_id:102,order_status:'cancelled'}]),
  'demo.DB2.order_items':table('order_items','DB2 · 合成订单明细；line_amount 为已记账金额',[['detail_id','int'],['detail_order_id','int'],['detail_product_id','int'],['quantity','int'],['line_amount','decimal']], [{detail_id:301,detail_order_id:201,detail_product_id:401,quantity:2,line_amount:200},{detail_id:302,detail_order_id:201,detail_product_id:402,quantity:1,line_amount:50},{detail_id:303,detail_order_id:202,detail_product_id:401,quantity:3,line_amount:300},{detail_id:304,detail_order_id:203,detail_product_id:402,quantity:4,line_amount:200},{detail_id:305,detail_order_id:204,detail_product_id:401,quantity:1,line_amount:100},{detail_id:306,detail_order_id:205,detail_product_id:401,quantity:10,line_amount:999}]),
  'demo.DB2.products':table('products','DB2 · 合成商品',[['product_id','int'],['product_name','varchar']], [{product_id:401,product_name:'商品A'},{product_id:402,product_name:'商品B'}]),
  'demo.DB3.customer_region_view':table('customer_region_view','DB3 · 本地多表关联生成视图；SQL仅溯源说明',[['region_customer_id','int'],['region_name','varchar']],regionView),
 };
 const sources:ModelingDemoSource[]=[
  {database:'DB1',table:'customers',tableKey:'demo.DB1.customers',nodeId:'demo-customers',kind:'table'},
  {database:'DB2',table:'orders',tableKey:'demo.DB2.orders',nodeId:'demo-orders',kind:'table'},
  {database:'DB2',table:'order_items',tableKey:'demo.DB2.order_items',nodeId:'demo-items',kind:'table'},
  {database:'DB2',table:'products',tableKey:'demo.DB2.products',nodeId:'demo-products',kind:'table'},
  {database:'DB3',table:'customer_region_view',tableKey:'demo.DB3.customer_region_view',nodeId:'demo-regions',kind:'view'},
 ];
 for(const s of sources){const [databaseLabel,objectLabel]=DEMO_SOURCE_LABELS[s.tableKey].split(' / ');tables[s.tableKey].source={databaseId:s.database,databaseLabel,objectLabel,kind:s.kind}}
 const nodes:ModelNode[]=sources.map((s,i)=>({...createModelNode('source',s.nodeId,i),name:DEMO_SOURCE_LABELS[s.tableKey],x:40,y:30+i*130,config:{table:s.tableKey}}));
 const add=(kind:ModelNode['kind'],id:string,name:string,inputs:string[],config:Record<string,string>,x:number,y:number)=>nodes.push({...createModelNode(kind,id,nodes.length),name,inputs,config,x,y});
 add('join','demo-join-orders','客户 → 订单',['demo-customers','demo-orders'],{left:'customer_id',right:'order_customer_id'},360,80);
 add('filter','demo-completed','仅已完成订单',['demo-join-orders'],{field:'right.order_status',operator:'eq',value:'completed'},670,80);
 add('join','demo-join-items','关联订单明细',['demo-completed','demo-items'],{left:'right.order_id',right:'detail_order_id'},980,180);
 add('join','demo-join-products','关联商品',['demo-join-items','demo-products'],{left:'right.detail_product_id',right:'product_id'},1290,280);
 add('join','demo-join-regions','关联客户地区视图',['demo-join-products','demo-regions'],{left:'customer_id',right:'region_customer_id'},1600,380);
 add('aggregate','demo-aggregate','按地区汇总金额',['demo-join-regions'],{group:'right.region_name',field:'right.line_amount',operation:'sum'},1910,380);
 add('rename','demo-amount','命名总金额字段',['demo-aggregate'],{field:'_sum',name:'total_amount'},2220,380);
 add('output','demo-output','地区金额输出',['demo-amount'],{},2530,380);
 const expectedRows:Row[]=[{'right.region_name':'华东',total_amount:550},{'right.region_name':'华南',total_amount:300}];
 const viewSql=options.legacy?LEGACY_DEMO_VIEW_SQL:DEMO_VIEW_SQL;const sqlNode=nodes.find(n=>n.id==='demo-regions')!;sqlNode.name='区域库 / 自定义 SQL';sqlNode.config={inputMode:'sql',database:'DB3',table:'',sqlText:viewSql,mockSqlTable:'demo.DB3.customer_region_view'};tables['demo.DB3.customer_region_view'].mockSqlText=viewSql;
 if(!options.legacy){
  const customers=tables['demo.DB1.customers'],orders=tables['demo.DB2.orders'],items=tables['demo.DB2.order_items'],regionsTable=tables['demo.DB3.customer_region_view'];
  const more=[['华北','CN-N','北京',420],['华中','CN-C','武汉',260],['西南','CN-SW','成都',375],['西北','CN-NW','西安',180]] as const;
  for(const [i,[regionName,,city,amount]] of more.entries()){
   const customerId=104+i,orderId=206+i;
   customers.rows.push({customer_id:customerId,customer_name:'示例客户'+city});
   orders.rows.push({order_id:orderId,order_customer_id:customerId,order_status:'completed'});
   items.rows.push({detail_id:307+i,detail_order_id:orderId,detail_product_id:401+i%2,quantity:1,line_amount:amount});
   regionsTable.rows.push({region_customer_id:customerId,region_name:regionName});
   expectedRows.push({'right.region_name':regionName,total_amount:amount});
  }
  const info:Record<string,readonly [string,string]>={'华东':['CN-E','上海'],'华南':['CN-S','广州'],...Object.fromEntries(more.map(([region,code,city])=>[region,[code,city]]))};
  const reporting=(region:string)=>({region_code:info[region][0],sales_hub:info[region][1],currency:'CNY',report_as_of:'2026-11-20 00:00:00'});
  regionsTable.columns.find(column=>column.name==='region_name')!.comment='销售地区';
  regionsTable.columns.push({name:'region_code',type:'varchar',comment:'地区编码'},{name:'sales_hub',type:'varchar',comment:'区域中心'},{name:'currency',type:'char(3)',comment:'结算币种'},{name:'report_as_of',type:'datetime',comment:'统计截止时间'});
  regionsTable.rows=regionsTable.rows.map(row=>({...row,...reporting(String(row.region_name))}));
  for(const col of items.columns) if(col.name==='line_amount')col.comment='明细入账金额';
  const output=nodes.pop()!;
  add('select','demo-region-profile','提取地区报表字段',['demo-regions'],{fields:'region_name,region_code,sales_hub,currency,report_as_of'},980,675);
  add('distinct','demo-region-distinct','每个地区保留一条',['demo-region-profile'],{fields:'region_name,region_code,sales_hub,currency,report_as_of'},1290,675);
  add('rename','demo-region-key','设置地区关联键',['demo-region-distinct'],{field:'region_name',name:'region_key'},1600,675);
  add('join','demo-report','补充地区报表信息',['demo-amount','demo-region-key'],{left:'right.region_name',right:'region_key'},2530,380);
  add('select','demo-report-fields','整理报表字段',['demo-report'],{fields:'right.region_name,right.region_code,right.sales_hub,right.currency,total_amount,right.report_as_of'},2840,380);
  nodes.push({...output,inputs:['demo-report-fields'],x:3150});
  for(const row of expectedRows) for(const [key,value] of Object.entries(reporting(String(row['right.region_name'])))) row['right.'+key]=value;
 }
 return {tables,nodes,sources,viewTableKey:'demo.DB3.customer_region_view',outputId:'demo-output',viewSql,expectedRows,description:'独立合成数据：DB1 一张客户表、DB2 三张业务表、DB3 一个本地多表关联视图。全部内关联，仅统计已完成订单，排除取消订单；按地区汇总明细已记账金额；无真实数据库连接或 SQL 执行。'};
}
