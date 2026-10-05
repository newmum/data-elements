/** Local fixtures only. No database hosts are contacted. Business counts are derived from this dataset. */
import type { Entity, MetadataSnapshot, Relationship } from '../features/er/types/domain';
import { discoverRelationships } from '../features/er/core/discovery';
import { fingerprint } from '../features/er/core/relations';
import { OWNER, REVIEWER, STEWARD, WORKSPACE, stableId, type LocalDatabase, type LocalWorkspace, type MockRow } from './types';
const fieldTypes: Record<string, [string, any]> = { i: ['BIGINT','integer'], s: ['VARCHAR(128)','string'], d: ['DECIMAL(18,2)','decimal'], t: ['TIMESTAMP','timestamp'], b:['BOOLEAN','boolean'], a:['ARRAY','array'], o:['OBJECT','object'] };
export function makeEntity(source: MockRow, name: string, displayName: string, definition: string, key: string[] = ['id']): Entity {
  const id = stableId(`${source.id}/${name}`);
  const fields = definition.split(',').map((entry, ordinal) => { const [n, type = 's', comment = ''] = entry.split(':'); const [nativeType, typeFamily] = fieldTypes[type] ?? fieldTypes.s; return { id: stableId(`${id}/${n}`), name: n, path: n, ordinal, nativeType, typeFamily, nullable: (key.includes(n) ? 'false' : 'true') as 'false'|'true', comment, ...(type === 'd' ? { precision:18,scale:2 } : {}), ...(type === 'a' ? { arrayDepth: 1 } : {}) }; });
  return { id, sourceId: source.id, catalog: source.connection.database, schemaName: source.connection.schema, name, displayName, kind: source.engine === 'MONGODB' ? 'collection' : source.engine === 'ELASTICSEARCH' ? 'index' : 'table', shapeBasis: source.engine === 'MONGODB' ? 'observed_sample' : 'declared_complete', constraintsAvailability: source.engine === 'ELASTICSEARCH' ? 'unavailable':'available', fields, keys: key.length ? [{ id:stableId(id+'/primary'),kind:'primary',fieldIds:key.map(n=>fields.find(f=>f.name===n)!.id),enforced:'true',scope:'full',nullSemantics:'not_allowed' }] : [] };
}
export function makeCatalog(source: MockRow): Entity[] {
  // New registrations get a local starter structure, never a fabricated response from the entered address.
  return [makeEntity(source,'business_records','业务记录','id:i:记录标识,tenant_id:i:租户编号,customer_id:i:客户编号,name:s:业务名称,status:s:状态,created_at:t:创建时间'),makeEntity(source,'customers','客户资料','id:i:客户编号,tenant_id:i:租户编号,name:s:客户名称,email:s:联系邮箱,status:s:状态',['tenant_id','id']),makeEntity(source,'record_items','业务明细','id:i:明细编号,business_record_id:i:业务记录,amount:d:金额,created_at:t:创建时间')];
}
export function makeRecords(e: Entity, count = 36): MockRow[] {
  return Array.from({length:count},(_,i)=>Object.fromEntries(e.fields.map(f=>{
    let value: unknown;
    if (f.name==='tenant_id') value=String(i%2+1);
    else if (f.name==='id'||f.name==='_id') value=String(Math.floor(i/2)+1);
    else if (f.name.endsWith('_id')) value=String(i%10+1);
    else if(f.typeFamily==='integer')value=String(i%20+1);
    else if(f.typeFamily==='decimal')value=((i+1)*28.5).toFixed(2);
    else if(f.typeFamily==='timestamp')value=`2026-09-${String(i%25+1).padStart(2,'0')}T08:30:00.000Z`;
    else if(f.typeFamily==='boolean')value=i%2===0;
    else if(f.typeFamily==='array')value=['business','online'];
    else if(f.typeFamily==='object')value={channel:'portal'};
    else if(f.name==='email')value=`contact${i+1}@example.com`;
    else if(f.name==='phone')value=`1380000${String(i+1).padStart(4,'0')}`;
    else if(f.name==='status')value=['ACTIVE','ACTIVE','PENDING'][i%3];
    else if(f.name==='name')value=`${e.displayName} ${String(i+1).padStart(3,'0')}`;
    else value=`${f.name.toUpperCase()}-${String(i+1).padStart(4,'0')}`;
    return [f.name,value];
  })));
}
export function createSeed(now = new Date().toISOString()): LocalDatabase {
  const definitions: Array<[string,string,string,string,string,number]> = [
    ['DM8','核心业务中心','CORE','CORE_BIZ','WANXIANG',5236],['MYSQL','交易订单库','TRADE','trade_db','',3306],
    ['POSTGRESQL','客户数据中心','CRM','customer_center','public',5432],['ORACLE','合同资产库','ASSET','ASSETDB','ASSET',1521],
    ['SQLSERVER','组织人事库','HR','HR_CENTER','dbo',1433],['MONGODB','业务事件库','EVENT','events','',27017],
    ['ELASTICSEARCH','数据检索索引','SEARCH','search_assets','',9200],['MARIADB','仓储业务库','WMS','warehouse','',3306]
  ];
  const sources = definitions.map(([engine,name,code,database,schema,port],index)=>({ id:stableId(`source-${code}`),name,code,code_norm:code,engine,status:'READY',connection:{host:`10.20.${index+1}.10`,port,database,schema,tlsMode:'VERIFY'},credentialConfigured:false,last_test_at:now,lastCollectedAt:now,version:1,created_at:now,updated_at:now,capabilities:{catalogConstraints:engine!=='ELASTICSEARCH',fieldProfiles:true,referenceValidation:true,crossSourceValidation:true,sqlEvidence:false} }));
  const entities:Entity[]=[]; const add=(s:number,n:string,d:string,f:string,k?:string[])=>{const e=makeEntity(sources[s],n,d,f,k);entities.push(e);return e;};
  add(0,'tenants','租户信息','id:i:租户标识,name:s:租户名称,code:s:统一代码,status:s:使用状态,created_at:t:创建时间');
  add(0,'organizations','组织机构','id:i:机构编号,parent_id:i:上级机构,name:s:机构名称,code:s:机构代码,region:s:所属区域,status:s:有效状态');
  add(0,'business_systems','业务系统','id:i:系统编号,name:s:系统名称,owner_id:i:负责人,organization_id:i:所属机构,status:s:运行状态');
  add(1,'orders','交易订单','id:i:订单编号,tenant_id:i:租户编号,customer_id:i:客户编号,order_no:s:业务订单号,order_date:t:下单时间,total_amount:d:订单金额,status:s:订单状态,created_at:t:创建时间',['tenant_id','id']);
  add(1,'order_items','订单明细','id:i:明细编号,tenant_id:i:租户编号,order_id:i:订单编号,product_id:i:产品编号,quantity:i:数量,unit_price:d:单价,amount:d:明细金额',['tenant_id','id']);
  add(1,'payments','支付记录','id:i:支付编号,tenant_id:i:租户编号,order_id:i:订单编号,amount:d:支付金额,channel:s:支付渠道,paid_at:t:支付时间,status:s:支付状态');
  add(1,'products','产品资料','id:i:产品编号,name:s:产品名称,category_id:i:分类编号,product_code:s:产品代码,price:d:标准售价,status:s:在售状态');
  add(2,'customers','客户主数据','id:i:客户编号,tenant_id:i:租户编号,name:s:客户名称,customer_type:s:客户类型,email:s:联系邮箱,phone:s:联系电话,status:s:有效状态',['tenant_id','id']);
  add(2,'customer_addresses','客户地址','id:i:地址编号,tenant_id:i:租户编号,customer_id:i:客户编号,province:s:省份,city:s:城市,address:s:详细地址');
  add(2,'customer_segments','客户分群','id:i:分群编号,name:s:分群名称,description:s:分群说明,created_at:t:创建时间');
  add(3,'contracts','合同档案','id:i:合同编号,tenant_id:i:租户编号,customer_id:i:客户编号,contract_no:s:合同号,amount:d:合同金额,sign_date:t:签订日期,status:s:合同状态');
  add(3,'contract_items','合同条款','id:i:条款编号,contract_id:i:合同编号,name:s:条款名称,amount:d:条款金额,status:s:履行状态');
  add(3,'assets','资产登记','id:i:资产编号,contract_id:i:合同编号,name:s:资产名称,asset_code:s:资产代码,amount:d:账面价值,status:s:资产状态');
  add(4,'departments','部门组织','id:i:部门编号,parent_id:i:上级部门,name:s:部门名称,code:s:部门代码,status:s:有效状态');
  add(4,'employees','员工资料','id:i:员工编号,department_id:i:所属部门,name:s:员工姓名,email:s:工作邮箱,employee_no:s:工号,status:s:在职状态');
  add(4,'positions','岗位信息','id:i:岗位编号,department_id:i:所属部门,name:s:岗位名称,level:s:岗位职级,status:s:有效状态');
  add(5,'business_events','业务事件','id:s:事件标识,tenant_id:i:租户编号,order_id:i:关联订单,event_type:s:事件类型,payload:o:事件内容,tags:a:标签,created_at:t:发生时间',[]);
  add(5,'customer_feedback','客户反馈','id:s:反馈标识,tenant_id:i:租户编号,customer_id:i:客户编号,content:s:反馈内容,rating:i:评分,created_at:t:反馈时间',[]);
  add(6,'asset_search','资产检索','id:s:检索标识,asset_id:i:资产编号,title:s:标题,keywords:a:关键词,updated_at:t:更新时间',[]);
  add(6,'knowledge_search','知识索引','id:s:知识标识,title:s:标题,category:s:知识分类,content:s:正文,updated_at:t:更新时间',[]);
  add(7,'warehouses','仓库信息','id:i:仓库编号,name:s:仓库名称,code:s:仓库代码,address:s:地址,status:s:启用状态');
  add(7,'stock_items','库存台账','id:i:台账编号,warehouse_id:i:仓库编号,product_id:i:产品编号,quantity:i:可用数量,updated_at:t:更新时间');
  add(7,'stock_movements','出入库流水','id:i:流水编号,warehouse_id:i:仓库编号,product_id:i:产品编号,quantity:i:变更数量,type:s:变更类型,created_at:t:发生时间');
  add(7,'suppliers','供应商资料','id:i:供应商编号,name:s:供应商名称,code:s:统一代码,email:s:联系邮箱,status:s:合作状态');
  const byName=(name:string)=>entities.find(e=>e.name===name)!;
  const records:Record<string,MockRow[]>={}; for(const e of entities) records[e.id]=makeRecords(e,e.name==='orders'?80:e.name==='order_items'?120:e.name==='customers'?40:36);
  for(const r of records[byName('customers').id]) r.customer_type='ENTERPRISE';
  records[byName('customers').id][3].email='';records[byName('customers').id][8].email=null;records[byName('customers').id][11].email='invalid-email';
  records[byName('orders').id][5].customer_id='999';
  records[byName('orders').id][7].total_amount='-15.00';
  // Parent self-reference and IDs are deterministic, without accidental duplicate simple primary keys.
  for(const e of entities)if(e.keys?.[0]?.fieldIds.length===1&&e.fields.some(f=>f.name==='id')) records[e.id].forEach((r,i)=>{r.id=String(i+1);if('parent_id'in r)r.parent_id=i?String(Math.floor((i+1)/3)+1):null;});
  const manifest=Object.fromEntries(sources.map(s=>[s.id,stableId(s.id+'/snapshot/1')]));
  const snapshot:MetadataSnapshot={id:'local-initial',sources,entities,capturedAt:now};
  const candidates=discoverRelationships(snapshot,entities.map(e=>e.id),[]).candidates;
  const catalogPairs=new Set(['order_items/orders','payments/orders','contract_items/contracts','employees/departments','positions/departments']);
  const confirmedPairs=new Set(['stock_items/products','stock_items/warehouses','customer_addresses/customers']);
  const relationships:Relationship[]=candidates.map((r,i)=>{const pair=`${entities.find(e=>e.id===r.sourceEntityId)!.name}/${entities.find(e=>e.id===r.targetEntityId)!.name}`;const catalog=catalogPairs.has(pair),confirmed=confirmedPairs.has(pair);return {...r,id:stableId('relationship/'+fingerprint(r)),workspaceId:WORKSPACE,origin:catalog?'catalog':confirmed?'manual':'inference',reviewStatus:catalog?'observed':confirmed?'confirmed':'suggested',description:catalog?'由已载入的表结构定义。':confirmed?'业务负责人确认的逻辑引用。':'根据字段名称、类型及完整键结构发现的候选关系。',...(catalog?{recommendation:null,constraintName:`FK_${i+1}`,enforced:'true' as const,validated:'unknown' as const}:{}),createdAt:now,updatedAt:now};});
  const makeModel=(name:string,code:string,domain:string,names:string[])=>({id:stableId('model/'+code),name,code,domain,description:`${domain}的数据结构与跨来源关系`,version:1,created_at:now,updated_at:now,updatedAt:now,modelInfo:{code,domain,layer:'逻辑模型',status:'ACTIVE'},manifest:Object.fromEntries([...new Set(names.map(n=>byName(n).sourceId))].map(id=>[id,manifest[id]])),layout:{nodes:names.map((n,i)=>({entityId:byName(n).id,position:{x:110+(i%3)*420,y:80+Math.floor(i/3)*450},locked:false,collapsed:false,pinnedFieldIds:[]})),hiddenRelationshipIds:[],fieldMode:'key',relationFilter:['observed','confirmed','suggested'],viewport:{x:0,y:0,zoom:0.8},showGrid:true,snapToGrid:true,showEdgeLabels:true}});
  const models=[makeModel('客户与交易域模型','MODEL_TRADE','交易域',['customers','orders','products','customer_addresses','order_items','contracts']),makeModel('组织与资产域模型','MODEL_ORG','组织域',['organizations','departments','employees','positions','assets','contracts']),makeModel('仓储履约关系模型','MODEL_WMS','履约域',['products','warehouses','stock_items','stock_movements','suppliers'])];
  const elements=[['客户编号','DE_CUSTOMER_ID','客户在同一租户内的唯一标识','integer',null,'客户域'],['客户名称','DE_CUSTOMER_NAME','客户主体的规范名称','string',128,'客户域'],['联系邮箱','DE_EMAIL','用于业务联系的电子邮箱地址','string',128,'通用域'],['订单金额','DE_ORDER_AMOUNT','订单按约定币种计算的含税金额','decimal',null,'交易域'],['组织代码','DE_ORG_CODE','组织机构的统一业务代码','string',64,'组织域'],['合同编号','DE_CONTRACT_NO','合同的业务标识代码','string',64,'资产域'],['数据创建时间','DE_CREATED_AT','业务记录首次创建时间','timestamp',null,'通用域'],['产品代码','DE_PRODUCT_CODE','产品目录中的业务代码','string',64,'产品域']].map(([name,code,definition,type,length,domain],i)=>{const id=stableId('element/'+code),rev=stableId(id+'/revision');return {id,name,code,code_norm:code,definition,type,length,domain,revisionId:rev,publishedRevisionId:i<5?rev:null,status:i<5?'PUBLISHED':i<7?'IN_REVIEW':'DRAFT',version:1,created_at:now,updated_at:now};});
  const reviews=elements.filter(e=>e.status==='IN_REVIEW').map(e=>({id:stableId('review/'+e.id),name:e.name,element_id:e.id,revision_id:e.revisionId,definition:e,status:'PENDING',applicant_id:STEWARD,opinion_text:'',version:1,created_at:now}));
  const rules=[['客户名称完整性','NOT_NULL','customers',['name'],{}],['联系邮箱格式','REGEX','customers',['email'],{preset:'EMAIL',nullPolicy:'VIOLATION'}],['客户联合编号唯一性','UNIQUE','customers',['tenant_id','id'],{}],['订单金额合理范围','RANGE','orders',['total_amount'],{min:'0',max:'10000000'}],['订单状态有效值','ENUM','orders',['status'],{values:['ACTIVE','PENDING','CLOSED']}],['组织代码长度','LENGTH','organizations',['code'],{min:'3',max:'64'}]].map(([name,type,entity,fields,extra],i)=>{const id=stableId('rule/'+name);return {id,name,type,code_norm:`RULE_${String(i+1).padStart(3,'0')}`,definition:{entityId:byName(String(entity)).id,fieldIds:(fields as string[]).map(n=>byName(String(entity)).fields.find(f=>f.name===n)!.id),nullPolicy:'IGNORE',...extra as object},revisionId:stableId(id+'/revision'),status:'ACTIVE',version:1,created_at:now,updated_at:now};});
  const plans=[{id:stableId('plan/customer'),name:'客户主数据质量检查',code_norm:'PLAN_CUSTOMER',rules:rules.slice(0,3).map(r=>({ruleId:r.id,revisionId:r.revisionId,ruleSnapshot:structuredClone(r)})),budget:{mode:'SAMPLE',maxRows:1000},status:'ACTIVE',version:1,created_at:now},{id:stableId('plan/trade'),name:'交易数据质量检查',code_norm:'PLAN_TRADE',rules:rules.slice(3,5).map(r=>({ruleId:r.id,revisionId:r.revisionId,ruleSnapshot:structuredClone(r)})),budget:{mode:'SAMPLE',maxRows:1000},status:'ACTIVE',version:1,created_at:now}];
  const collections=sources.map(s=>({id:stableId('collection/'+s.id),name:`${s.name} · 结构采集`,sourceId:s.id,selectionTokens:entities.filter(e=>e.sourceId===s.id).map(e=>e.id),status:'ACTIVE',version:1,created_at:now,updated_at:now}));
  const mappings:MockRow[]=[];for(const e of entities)for(const f of e.fields){const el=elements.find(s=>(f.name==='email'&&s.code==='DE_EMAIL')||(f.name==='name'&&s.code==='DE_CUSTOMER_NAME')||(f.name==='total_amount'&&s.code==='DE_ORDER_AMOUNT')||(f.name==='customer_id'&&s.code==='DE_CUSTOMER_ID'));if(el)mappings.push({id:stableId(`mapping/${f.id}`),entity_id:e.id,field_id:f.id,mapping_mode:'STANDARD',target_id:el.publishedRevisionId,status:'ACTIVE',note_text:'已建立业务标准对应关系',version:1,created_at:now});}
  const codes=[{id:stableId('codes/status'),name:'业务状态代码',code_norm:'STATUS',status:'PUBLISHED',draft_version:1,published_version:1,version:1,description:'业务记录生命周期状态',items:[{value:'ACTIVE',label:'有效',parent:''},{value:'PENDING',label:'待处理',parent:''},{value:'CLOSED',label:'已关闭',parent:''}]},{id:stableId('codes/customer-type'),name:'客户类型代码',code_norm:'CUSTOMER_TYPE',status:'DRAFT',draft_version:1,published_version:null,version:1,items:[{value:'ENTERPRISE',label:'企业客户',parent:''},{value:'PERSONAL',label:'个人客户',parent:''}]}];
  const jobs:MockRow[]=collections.slice(0,6).map((c,i)=>({id:stableId('job/collection/'+i),name:c.name,type:'COLLECTION',status:'SUCCEEDED',phase:'结构已归档',done_count:c.selectionTokens.length,total_count:c.selectionTokens.length,result_json:{entities:c.selectionTokens.length,scope:'本地结构目录'},version:1,created_at:new Date(Date.parse(now)-(i+1)*2400000).toISOString(),finished_at:now}));
  const jobEvents=jobs.map(j=>({id:stableId(j.id+'/event'),job_id:j.id,phase:'结构采集完成',message_text:`归档 ${j.total_count} 个对象`,created_at:j.created_at}));
  const members=[{principalId:OWNER,username:'admin',name:'管理员',role:'OWNER'},{principalId:STEWARD,username:'steward',name:'数据管理员',role:'STEWARD'},{principalId:REVIEWER,username:'reviewer',name:'标准审核员',role:'REVIEWER'}];
  const lineageNodes=[{id:'input',kind:'entity',entityId:byName('orders').id,label:'交易订单'},{id:'process',kind:'process',label:'订单数据标准化'},{id:'output',kind:'entity',entityId:byName('contracts').id,label:'合同数据'}];
  const w:LocalWorkspace={id:WORKSPACE,name:'万象数据治理',sources,catalog:Object.fromEntries(sources.map(s=>[s.id,entities.filter(e=>e.sourceId===s.id)])),entities,records,manifest,models,relationships,evidence:[],elements,reviews,codes,encoding:[{id:stableId('encoding/order'),name:'订单业务编码',code_norm:'ENC_ORDER',status:'ACTIVE',definition_json:{segments:[{type:'LITERAL',value:'WX-'},{type:'DIGITS',length:8}]},description_text:'固定前缀与8位数字组成',version:1}],mappings,rules,plans,collections,profiles:[],results:[],issues:[],orders:[],orderEvents:[],lineage:[{id:stableId('lineage/trade'),name:'交易数据加工链路',version:1,graph_json:{nodes:lineageNodes,edges:[{id:'edge1',source:'input',target:'process'},{id:'edge2',source:'process',target:'output'}]},layout_json:{positions:Object.fromEntries(lineageNodes.map((n,i)=>[n.id,{x:80+i*340,y:140}]))},created_at:now}],jobs,jobEvents,schedules:[],members,grants:[],audit:[{id:stableId('audit/initial'),action:'工作区建立',object_type:'工作区',object_id:WORKSPACE,actor_id:OWNER,created_at:now}],imports:[]};
  return {format:'wanxiang-local',schemaVersion:1,revision:1,principalId:OWNER,authenticated:true,workspaces:[w]};
}
