import type {Column,Row,TableDef} from './data';
import {createModelNode,type NodeKind} from './modeling';
import type {ModelDraft} from './modelCatalogStore';

export const INVENTORY_EXAMPLE_ID='builtin-inventory-replenishment-v1';
export const SALES_TREND_EXAMPLE_ID='builtin-monthly-sales-trend-v1';
export const DEVICE_ALARM_EXAMPLE_ID='builtin-device-alarm-summary-v1';
export const DATA_QUALITY_EXAMPLE_ID='builtin-customer-data-quality-v1';
type Field=[name:string,type:string,comment:string];

/** Each model owns a small synthetic snapshot; no shared workspace or network data is read. */
function example(id:string,name:string,category:string){
 const draft:ModelDraft={id,name,category,nodes:[],executionSettings:{engine:'flink',mode:'batch'},importedTables:{}};
 function add(key:string,kind:NodeKind,label:string,inputs:string[],config:Record<string,string>,column:number,row=0){
  const node=createModelNode(kind,id+'-'+key,draft.nodes.length);
  draft.nodes.push({...node,name:label,inputs:inputs.map(input=>id+'-'+input),config:{...node.config,...config},x:260+column*280,y:100+row*240});
 }
 function source(key:string,label:string,fields:Field[],rows:Row[],row=0){
  const tableKey='sample.'+id+'.'+key;
  const columns:Column[]=fields.map(([name,type,comment])=>({name,type,comment}));
  const table:TableDef={name:key,comment:label+' · 内置合成样本',columns,rows,source:{databaseId:id,databaseLabel:name+'样本库',objectLabel:label,kind:'table'}};
  draft.importedTables[tableKey]=table;
  add(key,'source',label,[],{table:tableKey},0,row);
 }
 return {draft,add,source};
}

export function createInventoryExample():ModelDraft{
 const {draft,add,source}=example(INVENTORY_EXAMPLE_ID,'库存补货预警示例','库存与供应');
 source('inventory','仓库库存快照',[['sku','varchar','商品编码'],['warehouse','varchar','仓库名称'],['stock_quantity','int','当前库存数量（件）']],[
  {sku:'SKU-101',warehouse:'华东仓',stock_quantity:8},
  {sku:'SKU-102',warehouse:'华南仓',stock_quantity:15},
  {sku:'SKU-103',warehouse:'华东仓',stock_quantity:35},
  {sku:'SKU-104',warehouse:'华北仓',stock_quantity:0},
  {sku:'SKU-105',warehouse:'华南仓',stock_quantity:20},
 ]);
 source('products','商品及供应商',[['sku','varchar','商品编码'],['product_name','varchar','商品名称'],['supplier','varchar','供应商名称']],[
  {sku:'SKU-101',product_name:'温度传感器',supplier:'华东供应商'},
  {sku:'SKU-102',product_name:'压力变送器',supplier:'华南供应商'},
  {sku:'SKU-103',product_name:'工业网关',supplier:'华东供应商'},
  {sku:'SKU-104',product_name:'振动探头',supplier:'华北供应商'},
  {sku:'SKU-105',product_name:'信号线缆',supplier:'华南供应商'},
 ],1);
 // All products use a 20-piece replenishment target, expressed with supported constant arithmetic.
 add('negative-stock','derived','计算库存相反数',['inventory'],{field:'stock_quantity',operation:'multiply',operand:'-1',name:'negative_stock'},1);
 add('shortage','derived','补货缺口：20 减库存',['negative-stock'],{field:'negative_stock',operation:'add',operand:'20',name:'replenish_quantity'},2);
 add('shortages-only','filter','仅保留需要补货的商品',['shortage'],{field:'replenish_quantity',operator:'gt',value:'0'},3);
 add('supplier','lookup','补充商品与供应商',['shortages-only','products'],{left:'sku',right:'sku'},4);
 add('fields','select','整理补货清单',['supplier'],{fields:'sku,right.product_name,warehouse,stock_quantity,replenish_quantity,right.supplier'},5);
 add('priority','sort','按补货缺口降序',['fields'],{field:'replenish_quantity',direction:'desc'},6);
 add('output','output','补货预警清单',['priority'],{},7);
 return draft;
}

export function createSalesTrendExample():ModelDraft{
 const {draft,add,source}=example(SALES_TREND_EXAMPLE_ID,'月度销售趋势示例','销售经营分析');
 const fields:Field[]=[['sale_date','date','销售日期（2026 年）'],['sales_amount','decimal','销售金额（元）']];
 source('online_sales','线上销售快照',fields,[
  {sale_date:'2026-08-02',sales_amount:500},{sale_date:'2026-09-03',sales_amount:600},
  {sale_date:'2026-10-04',sales_amount:800},{sale_date:'2026-11-05',sales_amount:900},
 ]);
 source('store_sales','门店销售快照',fields,[
  {sale_date:'2026-08-12',sales_amount:250},{sale_date:'2026-09-13',sales_amount:400},
  {sale_date:'2026-10-14',sales_amount:450},{sale_date:'2026-11-15',sales_amount:600},
 ],1);
 add('channels','union','合并线上与门店销售',['online_sales','store_sales'],{},1);
 add('month','datePart','提取销售月份',['channels'],{field:'sale_date',part:'month',name:'sales_month'},2);
 add('monthly','aggregate','按月汇总销售金额',['month'],{group:'sales_month',field:'sales_amount',operation:'sum'},3);
 add('amount','rename','命名月度销售额',['monthly'],{field:'_sum',name:'monthly_sales'},4);
 add('chronological','sort','按月份先后排序',['amount'],{field:'sales_month',direction:'asc'},5);
 add('cumulative','runningSum','计算累计销售额',['chronological'],{field:'monthly_sales',name:'cumulative_sales'},6);
 add('previous','lagLead','提取上月销售额',['cumulative'],{field:'monthly_sales',operation:'lag',offset:'1',name:'previous_month_sales'},7);
 add('year','constant','标记统计年度 2026',['previous'],{name:'report_year',value:'2026',type:'number'},8);
 add('output','output','月度销售趋势报表',['year'],{},9);
 return draft;
}

export function createDeviceAlarmExample():ModelDraft{
 const {draft,add,source}=example(DEVICE_ALARM_EXAMPLE_ID,'设备告警汇总示例','设备运维');
 const event=(event_id:string,device_id:string,severity:string,status='open'):Row=>({event_id,device_id,status,payload:JSON.stringify({severity})});
 source('alarm_events','设备告警记录',[['event_id','varchar','告警事件编号（重复上报需去重）'],['device_id','varchar','设备编号'],['status','varchar','处理状态：open 未处理，closed 已处理'],['payload','json','告警 JSON，severity 为严重程度']],[
  event('E001','PUMP-01','critical'),event('E001','PUMP-01','critical'),event('E002','PUMP-01','critical'),
  event('E003','MOTOR-02','warning'),event('E004','MOTOR-02','critical','closed'),
  event('E005','FAN-03','critical'),event('E006','FAN-03','critical'),event('E007','FAN-03','critical'),
  event('E008','PUMP-01','info'),event('E009','MOTOR-02','critical'),
 ]);
 source('devices','设备台账',[['device_id','varchar','设备编号'],['device_name','varchar','设备名称'],['workshop','varchar','所在车间']],[
  {device_id:'PUMP-01',device_name:'循环水泵',workshop:'动力车间'},
  {device_id:'MOTOR-02',device_name:'输送电机',workshop:'装配车间'},
  {device_id:'FAN-03',device_name:'排风机',workshop:'喷涂车间'},
 ],1);
 add('severity','jsonScalar','提取 JSON 告警级别',['alarm_events'],{field:'payload',path:'severity',name:'severity'},1);
 add('critical','filter','只统计严重告警',['severity'],{field:'severity',operator:'eq',value:'critical'},2);
 add('open','filter','排除已处理告警',['critical'],{field:'status',operator:'eq',value:'open'},3);
 add('unique-events','distinct','按事件编号去重',['open'],{fields:'event_id'},4);
 add('counts','aggregate','按设备统计告警数',['unique-events'],{group:'device_id',field:'',operation:'count'},5);
 add('count-name','rename','命名未处理告警数',['counts'],{field:'_count',name:'alarm_count'},6);
 add('device-info','lookup','关联设备与车间',['count-name','devices'],{left:'device_id',right:'device_id'},7);
 add('priority','sort','按未处理告警数降序',['device-info'],{field:'alarm_count',direction:'desc'},8);
 add('fields','select','整理设备告警报表',['priority'],{fields:'device_id,right.device_name,right.workshop,alarm_count'},9);
 add('output','output','设备告警汇总结果',['fields'],{},10);
 return draft;
}

export function createDataQualityExample():ModelDraft{
 const {draft,add,source}=example(DATA_QUALITY_EXAMPLE_ID,'客户数据质量检查示例','数据质量');
 source('customer_contacts','客户联系信息',[['customer_id','varchar','客户编号'],['customer_name','varchar','客户姓名'],['email','varchar','电子邮箱（可缺失）']],[
  {customer_id:'C001',customer_name:' 林岚 ',email:' linlan@example.com '},
  {customer_id:'C001',customer_name:' 林岚 ',email:' linlan@example.com '},
  {customer_id:'C002',customer_name:null,email:'chen@example.com'},
  {customer_id:'C003',customer_name:' 周晴 ',email:null},
  {customer_id:'C004',customer_name:' 陈越 ',email:'qinyue@example.com'},
  {customer_id:'C005',customer_name:'何川',email:'hechuan@example.com'},
 ]);
 add('names','trim','去除姓名首尾空格',['customer_contacts'],{field:'customer_name'},1);
 add('missing-names','fillNull','补齐缺失姓名',['names'],{field:'customer_name',value:'未填写',type:'string'},2);
 add('unique-customers','distinct','按客户编号去重',['missing-names'],{fields:'customer_id'},3);
 add('valid','conditional','分出邮箱非空记录',['unique-customers'],{field:'email',operator:'notNull',value:'',branch:'matched'},4);
 add('missing','conditional','分出邮箱缺失记录',['unique-customers'],{field:'email',operator:'notNull',value:'',branch:'unmatched'},4,2);
 add('emails','trim','去除邮箱首尾空格',['valid'],{field:'email'},5);
 add('unique-check','assert','校验客户编号唯一',['emails'],{field:'customer_id',rule:'unique'},6);
 add('email-check','regex','校验样本邮箱格式',['unique-check'],{field:'email',pattern:'\\w+@\\w+\\.\\w+'},7);
 add('masked','mask','脱敏邮箱，仅保留末四位',['email-check'],{field:'email',keep:'4'},8);
 add('passed','constant','标记校验通过',['masked'],{name:'quality_status',value:'通过',type:'string'},9);
 add('output','output','清洗及校验通过记录',['passed'],{},10);
 add('issue','constant','标记缺少邮箱问题',['missing'],{name:'quality_issue',value:'缺少邮箱',type:'string'},5,2);
 add('issue-fields','select','整理待补充客户清单',['issue'],{fields:'customer_id,customer_name,quality_issue'},6,2);
 add('issues-output','output','待补充邮箱记录',['issue-fields'],{},7,2);
 return draft;
}

export const BUSINESS_MODEL_EXAMPLES=[
 {id:INVENTORY_EXAMPLE_ID,create:createInventoryExample},
 {id:SALES_TREND_EXAMPLE_ID,create:createSalesTrendExample},
 {id:DEVICE_ALARM_EXAMPLE_ID,create:createDeviceAlarmExample},
 {id:DATA_QUALITY_EXAMPLE_ID,create:createDataQualityExample},
] as const;
