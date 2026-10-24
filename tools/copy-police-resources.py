#!/usr/bin/env python3
"""将已授权公安资源复制到澄天。plan/menu/prepare/apply/index/verify 均固定目标；默认只读。"""
from __future__ import annotations
import argparse,collections,copy,datetime as dt,gzip,hashlib,importlib.util,json,pathlib,re,sys,traceback,uuid

ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('chengtian_maintenance',ROOT/'tools/chengtian-maintenance.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
SRC='2084109831682699264';DST=m.TENANT;SOURCE='baseline_ga';TARGET=m.SCHEMA
OUT=ROOT/'logs/police-copy';PRIVATE=m.PRIVATE/'police-copy';SHADOW='ct_police_copy_targets'
CAP=50000;TOTAL_CAP=160000;PAGE=1000
TABLES=('sym_application_t','db_datasource_t','db_table_t','db_table_column_t','da_catalog_t','da_catalog_item_t','da_metadata_t','da_metadata_code_t','res_logical_model','res_logical_model_field','dwm_metadata_relation_t','data_access_agg_task_t','data_access_field_mapping','nifi_pipeline_t','data_access_mapping_plan_t','data_access_mapping_publication_t','data_access_mapping_resource_t','app_workshop_repository','app_workshop_repository_tab','app_workshop_big_screen','dq_quality_task_t','dq_quality_task_rule_t','da_prop_t')
KEYS={'nifi_pipeline_t':'id'}
BAD=re.compile(r'布控|surveillance|validation[-_ ]|race[-_ ]|registry[-_ ]check',re.I)
REFS={'app_id':'sym_application_t','datasource_id':'db_datasource_t','table_id':'db_table_t','catalog_id':'da_catalog_t','task_id':'data_access_agg_task_t','model_id':'res_logical_model','source_table_id':'db_table_t','target_table_id':'db_table_t','resource_model_id':'res_logical_model'}

def uid(value):return uuid.uuid5(uuid.NAMESPACE_URL,'chengtian-copy-police-v1/'+str(value)).hex
def key(table):return KEYS.get(table,'tid')
def dump(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2,default=str)+'\n',encoding='utf-8')
def load(path):return json.loads(path.read_text(encoding='utf-8'))
def metadata(db):
 rows=m.query(db,'select table_name,column_name,is_nullable,column_default,extra,column_type,data_type from information_schema.columns where table_schema=%s order by table_name,ordinal_position',(TARGET,));result=collections.defaultdict(list)
 for row in rows:result[row['TABLE_NAME']].append(row)
 return result
def read_rows(db,table,meta):
 fields={r['COLUMN_NAME'].lower() for r in meta[table]};active=' and is_del=0' if 'is_del' in fields else ' and deleted=0' if 'deleted' in fields else ''
 result=[];cursor='';pk=key(table)
 while True:
  rows=m.query(db,f'select * from `{SOURCE}`.`{table}` where tenant_id=%s and `{pk}`>%s'+active+f' order by `{pk}` limit {PAGE}',(SRC,cursor))
  if not rows:break
  result.extend(rows);cursor=str(rows[-1][pk])
  if len(result)>CAP:raise RuntimeError('单表复制规模超过上限：'+table)
 return result
def snapshot(db):
 m.check_session();exp=m.load_module('copy_binding',ROOT/'tools/export-db.py')
 bindings={name:schema for name,dialect,schema,opts in exp.connections(m.config())}
 if bindings.get('public-security')!=SOURCE or bindings.get('chengtian')!=TARGET:raise RuntimeError('当前运行库绑定不符')
 meta=metadata(db);data={table:read_rows(db,table,meta) for table in TABLES}
 if sum(map(len,data.values()))>TOTAL_CAP:raise RuntimeError('本批复制总规模超过上限')
 return meta,data
def selected(data):
 data=copy.deepcopy(data);skipped=collections.Counter();by=lambda t:{str(r[key(t)]):r for r in data[t]}
 def keep(table,predicate):
  before=len(data[table]);data[table]=[r for r in data[table] if predicate(r)];skipped[table]+=before-len(data[table])
 keep('sym_application_t',lambda r:not BAD.search(r.get('app_name') or ''))
 apps=by('sym_application_t');sources=by('db_datasource_t')
 keep('db_table_t',lambda r:str(r.get('datasource_id')) in sources and not BAD.search((r.get('table_name') or '')+' '+(r.get('table_name_cn') or '')))
 tables=by('db_table_t');keep('db_table_column_t',lambda r:str(r.get('table_id')) in tables);columns=by('db_table_column_t')
 keep('da_catalog_t',lambda r:str(r.get('source_table_id')) in tables or str(r.get('target_table_id')) in tables)
 catalogs=by('da_catalog_t');keep('da_catalog_item_t',lambda r:str(r.get('catalog_id')) in catalogs)
 keep('res_logical_model_field',lambda r:str(r.get('model_id')) in by('res_logical_model'))
 keep('dwm_metadata_relation_t',lambda r:str(r.get('source_table_id')) in tables and str(r.get('target_table_id')) in tables)
 pipelines=by('nifi_pipeline_t')
 keep('data_access_agg_task_t',lambda r:not BAD.search(r.get('task_name') or '') and str(r.get('pipeline_id')) in pipelines and str(r.get('source_db_id')) in sources and str(r.get('source_table_id')) in tables)
 tasks=by('data_access_agg_task_t');pipelineids={str(r['pipeline_id']) for r in tasks.values()}
 keep('nifi_pipeline_t',lambda r:str(r['id']) in pipelineids and not BAD.search(r.get('name') or ''))
 pipelineids=set(by('nifi_pipeline_t'));keep('data_access_agg_task_t',lambda r:str(r['pipeline_id']) in pipelineids);tasks=by('data_access_agg_task_t')
 keep('data_access_field_mapping',lambda r:str(r.get('task_id')) in tasks)
 # Immutable mapping publications describe the original runtime; only live task field mappings are imported.
 for t in ('data_access_mapping_plan_t','data_access_mapping_publication_t','data_access_mapping_resource_t'):
  skipped[t]+=len(data[t]);data[t]=[]
 keep('dq_quality_task_t',lambda r:str(r.get('table_id')) in tables)
 quality=by('dq_quality_task_t');keep('dq_quality_task_rule_t',lambda r:str(r.get('task_id')) in quality)
 parents=set().union(*(set(by(t)) for t in TABLES if t!='da_prop_t'))
 keep('da_prop_t',lambda r:str(r.get('parent_id')) in parents)
 return data,dict(skipped)

def plan(db):
 meta,raw=snapshot(db);data,skipped=selected(raw)
 report={'sourceTenant':SRC,'sourceDatabase':SOURCE,'targetTenant':DST,'targetDatabase':TARGET,'selected':{t:len(v) for t,v in data.items()},'excluded':skipped,'maxRowsPerTable':CAP,'readPageSize':PAGE,'targetRuntimeState':'SAVED; no police process group or execution history copied','existingTargetData':'preserved'}
 dump(OUT/'plan.json',report);print(m.jd(report));return meta,data

def menu(db):
 m.check_session();ids=('2068931869225340928','2081000000000000001','2081000000000000002')
 rows=m.query(db,'select * from rm_menu_t where tenant_id=%s and deleted=0 and id in (%s,%s,%s)',[DST]+list(ids))
 if len(rows)!=3:raise RuntimeError('菜单身份不完整')
 root=next(r for r in rows if r['id']==ids[1]);child=next(r for r in rows if r['id']==ids[2])
 if root['url']!='/datasource-manage' or child['sno']!='department-datasource':raise RuntimeError('数据源管理菜单身份不符')
 grants=m.query(db,'select role_id,app_resource_id from rm_role_menu_rela_t where tenant_id=%s and app_resource_id in (%s,%s,%s)',[DST]+list(ids))
 roles={r['role_id'] for r in grants if r['app_resource_id']==ids[0]};existing={(r['role_id'],r['app_resource_id']) for r in grants}
 if not roles:raise RuntimeError('数据盘点没有可复用的现有角色授权')
 additions=[(DST,uid('menu-grant/'+role+'/'+resource),role,resource) for role in roles for resource in ids[1:] if (role,resource) not in existing]
 dump(OUT/'menu-before.json',[{k:r[k] for k in ('id','name','sort_num','status','data_scope_enabled')} for r in rows])
 changed=m.execute(db,'update rm_menu_t set name=%s,status=0,sort_num=4,data_scope_enabled=1,update_time=current_timestamp(6) where tenant_id=%s and id=%s and name=%s and status=%s',('数据登记',DST,ids[1],root['name'],root['status']))
 m.execute(db,'update rm_menu_t set data_scope_enabled=1 where tenant_id=%s and id=%s',(DST,ids[2]));db.commit()
 if additions:
  with db.cursor() as c:c.executemany('insert into rm_role_menu_rela_t(tenant_id,id,role_id,app_resource_id) values(%s,%s,%s,%s)',additions)
 db.commit();print(m.jd({'menu':'数据登记','route':'/datasource-manage/index','position':'数据盘点右侧','updated':changed,'menuBindingsAdded':len(additions),'roles':'existing inventory roles only'}))

ID_TOKEN=re.compile(r'(?<![A-Za-z0-9])(?:[a-fA-F0-9]{32}|[0-9]{16,20})(?![A-Za-z0-9])')
SECRET=re.compile(r'password|passwd|pwd|secret|token|credential|private.?key',re.I)
def replace_refs(value,mapping,field=''):
 if SECRET.search(field):return value
 if isinstance(value,dict):return {mapping.get(str(k),k):replace_refs(v,mapping,str(k)) for k,v in value.items()}
 if isinstance(value,list):return [replace_refs(v,mapping,field) for v in value]
 if isinstance(value,str):
  if value in mapping:return mapping[value]
  if value[:1] in ('{','['):
   try:return m.jd(replace_refs(json.loads(value),mapping))
   except (ValueError,TypeError):pass
  return ID_TOKEN.sub(lambda match:mapping.get(match.group(),match.group()),value)
 return value

def qname(value):
 if not value or '\0' in value or len(value)>64:raise ValueError('无效实验表字段名')
 return '`'+value.replace('`','``')+'`'
def literal(value):return "'"+str(value).replace('\\','\\\\').replace("'","''")+"'"
def mysql_type(column):
 raw=str(column.get('column_type') or column.get('data_type') or '').lower().strip();base=re.split(r'[ (]',raw)[0]
 length=column.get('length')
 if base in ('varchar','varchar2','nvarchar','nvarchar2','character','char','nchar','string'):
  parsed=re.search(r'\((\d+)',raw);n=int(parsed.group(1)) if parsed else int(length or 255)
  return 'varchar('+str(max(1,min(n,16000)))+')' if n<=16000 else 'longtext'
 if base in ('text','longtext','mediumtext','clob','nclob','xml','json'):return 'longtext'
 if base in ('binary','varbinary','blob','longblob','raw','bytea','image'):return 'longblob'
 if base in ('date','datetime','timestamp','timestamptz','datetime2'):return 'datetime(6)'
 if base in ('time','timetz'):return 'time(6)'
 if base in ('bigint','int','integer','smallint','tinyint','bit','boolean','bool','int2','int4','int8'):return 'bigint'
 if base in ('float','double','real','binary_float','binary_double','float4','float8'):return 'double'
 if base in ('decimal','numeric','number','dec'):
  parsed=re.search(r'\((\d+)(?:\s*,\s*(\d+))?\)',raw)
  if parsed:
   precision,scale=int(parsed.group(1)),int(parsed.group(2) or 0)
   if 1<=precision<=65 and 0<=scale<=min(30,precision):return f'decimal({precision},{scale})'
  # Unknown vendor precision is kept losslessly as text in this isolated experiment target.
  return 'varchar(128)'
 raise ValueError('尚未审阅的实验目标类型：'+base)

def rebind_shadow_source(node,source,datasource,source_id):
 """将迁入链路的实验表来源重绑为MySQL；纯内存修改，不查询、不部署，保留节点及治理配置。"""
 cfg=node.setdefault('config',{})
 if cfg.get('sourceTableId') and str(cfg['sourceTableId'])!=str(source['id']):
  raise RuntimeError('迁入画布的来源表与任务不一致，拒绝自动重绑')
 # 物理来源已经切换到独立MySQL实验库，组件类型、登记别名与物理定位必须一起切换。
 # 不能只更新可见连接选项而遗留达梦/Oracle驱动、owner或sourceDbId。
 for field in ('driver','driverClass','driverClassName','jdbcDriverClassName','schema','defaultSchema','sourceSchema','catalog','sid','serviceName','connectionType','url','host','port'):
  cfg.pop(field,None)
 cfg.update(registeredDatasourceId=source_id,selectedDatabaseId=source_id,sourceDbId=source_id,
            sourceTableId=source['id'],sourceTableName=source['name'],database=SHADOW,table=source['name'],
            dbType='MySQL',jdbcUrl=datasource['jdbc_url'],jdbcURL=datasource['jdbc_url'],
            username=datasource['username'],password=datasource['password'])
 node['manifestKey']='source.mysql'

def prepare(db,raw_override=None):
 if raw_override is None:meta,raw=snapshot(db)
 else:meta,raw=metadata(db),raw_override
 data,skipped=selected(raw);now=dt.datetime.now()
 orgs={str(r['id']) for r in m.query(db,'select id from rm_org_t where tenant_id=%s and deleted=0',(DST,))}
 mapping={str(r[key(t)]):uid(r[key(t)]) for t,rows in data.items() for r in rows};mapping[SRC]=DST
 apps={str(r['tid']) for r in data['sym_application_t']};table_by={str(r['tid']):r for r in data['db_table_t']}
 target_ids={str(r['target_table_id']) for r in data['data_access_agg_task_t'] if r.get('target_table_id')}
 columns=collections.defaultdict(list)
 for row in data['db_table_column_t']:columns[str(row['table_id'])].append(row)
 shadow={};ddl=[f'CREATE DATABASE IF NOT EXISTS `{SHADOW}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;'];pending=[]
 for table in data['db_table_t']:
  old=str(table['tid']);fields=sorted(columns[old],key=lambda r:r.get('ordinal_position') or 0)
  if old not in target_ids:continue
  if not fields:pending.append(old);continue
  definitions=[];physical='ga_'+uid(old)[:16];types={str(f['tid']):mysql_type(f) for f in fields}
  def width(typ):
   match=re.fullmatch(r'varchar\((\d+)\)',typ);return int(match.group(1))*4+2 if match else 32
  budget=sum(width(t) for t in types.values())
  for field in sorted(fields,key=lambda f:width(types[str(f['tid'])]),reverse=True):
   typ=types[str(field['tid'])]
   if budget<=56000:break
   if typ.startswith('varchar(') and field.get('primary_key') not in (1,'1',True):types[str(field['tid'])]='longtext';budget-=width(typ)-32
  for field in fields:
   typ=types[str(field['tid'])];comment=field.get('column_comment') or field['column_name'];nullable=field.get('nullable') not in (0,'0',False) and field.get('primary_key') not in (1,'1',True)
   definitions.append(qname(field['column_name'])+' '+typ+(' DEFAULT NULL' if nullable else ' NOT NULL')+' COMMENT '+literal(comment))
  primary_fields=[f for f in fields if f.get('primary_key') in (1,'1',True) and not types[str(f['tid'])].endswith(('text','blob'))]
  primary=[qname(f['column_name']) for f in primary_fields];hash_key=sum(width(types[str(f['tid'])]) for f in primary_fields)>3072
  if hash_key:
   if len(primary)!=1:raise ValueError('超长复合业务键需要单独审阅')
   definitions.append('`__copy_key_hash` binary(32) GENERATED ALWAYS AS (unhex(sha2('+primary[0]+",256))) STORED COMMENT '原业务主键完整值的SHA256索引键，仅用于独立实验库唯一性'")
   definitions.append('UNIQUE KEY `ux_copy_business_key_hash` (`__copy_key_hash`)')
  elif primary:definitions.append('PRIMARY KEY ('+','.join(primary)+')')
  ddl.append(f'CREATE TABLE `{SHADOW}`.`{physical}` (\n  '+',\n  '.join(definitions)+"\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT="+literal('公安迁入实验-'+str(table.get('table_name_cn') or table['table_name']))+';')
  shadow[old]={'id':mapping[old],'name':physical,'columns':types,'keys':[f['column_name'] for f in primary_fields],'hashKey':hash_key}
 # Source snapshots and prepared rows can contain connection secrets; they only live outside the repository.
 path=PRIVATE/'prepared.json.gz'
 converted={t:[] for t in data};shadow_ds=uid('__shadow_datasource__');fallback_app='5ac26f5f7e94567d8e3c18862e083b7f';ownership_reassigned=0
 for table,rows in data.items():
  for original in rows:
   row={k:replace_refs(v,mapping,original.get('prop_name','') if k=='prop_value' else k) for k,v in original.items()}
   row['tenant_id']=DST
   for field in ('created_by','updated_by','create_by','update_by','created_user','updated_user','create_user_id','update_user_id'):
    if field in row:row[field]=m.ACTOR
   for field in ('created_time','updated_time','create_time','update_time'):
    if field in row:row[field]=now
   for field in ('org_id','manage_unit','apply_org_id'):
    if field in row and str(row[field]) not in orgs:
     row[field]=m.ORG;ownership_reassigned+=1
   if table=='sym_application_t':row['app_url']=None
   elif table=='db_datasource_t':
    if str(original.get('app_id')) not in apps:row['app_id']=fallback_app
   elif table=='db_table_t':
    if str(original['tid']) in shadow:
     row.update(datasource_id=shadow_ds,table_name=shadow[str(original['tid'])]['name'],table_name_en=shadow[str(original['tid'])]['name'],data_source_type='ODS',record_count=0)
    for field in ('resource_domain_id','resource_model_id'):
     if field in row and str(original.get(field)) not in mapping:row[field]=None
   elif table=='db_table_column_t' and str(original['table_id']) in shadow:
    typ=shadow[str(original['table_id'])]['columns'][str(original['tid'])];row['column_type']=typ;row['data_type']=typ.split('(')[0].upper()
    if row.get('primary_key') in (1,'1',True):row['nullable']=0
    if shadow[str(original['table_id'])]['hashKey'] and row.get('primary_key') in (1,'1',True):row['primary_key']=0;row['is_unique']=1
   elif table=='da_catalog_t':
    source_id=str(original.get('source_table_id'));source_id=source_id if source_id in table_by else str(original.get('target_table_id'))
    source=table_by[source_id]
    row['source_table_id']=mapping[source_id];row['db_id']=shadow_ds if source_id in shadow else mapping[str(source['datasource_id'])]
    if source_id in shadow:row.update(source_table_name=shadow[source_id]['name'],source_db_id=shadow_ds,db_name='公安迁入接入实验库',source_db_name='公安迁入接入实验库')
    if str(original.get('app_id')) not in mapping:row['app_id']=fallback_app
   elif table=='data_access_agg_task_t':
    row['task_status']=0
    for prefix in ('source','target'):
     tid=str(original.get(prefix+'_table_id'))
     if tid in shadow:row[prefix+'_db_id']=shadow_ds;row[prefix+'_table_id']=shadow[tid]['id']
    if str(original.get('target_table_id')) not in shadow:row['target_db_id']=None;row['target_table_id']=None
   elif table=='nifi_pipeline_t':
    row['status']='SAVED'
    for field in ('nifi_process_group_id','last_deployed_hash','last_deployed_at','last_stopped_at','node_mapping_json','last_bulletin_id'):row[field]=None
   elif table=='app_workshop_repository':row['publish_status']=0;row['publish_url']=None
   converted[table].append(row)
 # 迁入工具只复用已登记的独立目标连接，不再依赖已退役的合成数据源。
 template=m.query(db,'select * from db_datasource_t where tenant_id=%s and tid=%s and is_del=0',(DST,shadow_ds))
 if len(template)!=1:raise RuntimeError('请先登记公安迁入接入实验库的独立目标连接')
 ds=template[0];ds.update(tid=shadow_ds,db_name='公安迁入接入实验库',table_num=len(shadow),data_catalog_num=0,created_time=now,updated_time=now)
 pool=json.loads(ds['pool_cfg']);pool.update(database=SHADOW,jdbcURL=ds['jdbc_url']);ds['pool_cfg']=m.jd(pool)
 converted['db_datasource_t'].append(ds)
 extra_seed=m.Seed(meta)
 for target in shadow.values():
  if target['hashKey']:
   ordinal=max(r.get('ordinal_position') or 0 for r in converted['db_table_column_t'] if r['table_id']==target['id'])+1
   extra_seed.add('db_table_column_t','copy-generated-key',tid=uid(target['id']+'/generated-key'),table_id=target['id'],column_name='__copy_key_hash',column_comment='原业务主键完整值的SHA256索引键，仅用于独立实验库唯一性',data_type='BINARY',column_type='binary(32)',length=32,nullable=1,primary_key=0,auto_increment=0,is_unique=1,indexed=1,ordinal_position=ordinal,extra='STORED GENERATED',metadata_binding_version=0)
   next(r for r in converted['db_table_t'] if r['tid']==target['id'])['field_count']=ordinal
 for table,values in extra_seed.rows.items():converted[table].extend(values)
 shadow_by_new={v['id']:v for v in shadow.values()};task_by_pipeline={r['pipeline_id']:r for r in converted['data_access_agg_task_t']}
 for pipeline in converted['nifi_pipeline_t']:
  dsl=json.loads(pipeline['dsl_json']);task=task_by_pipeline[pipeline['id']]
  for node in dsl.get('nodes',[]):
   cfg=node.get('config') or {};category=node.get('category') or str(node.get('manifestKey','')).split('.')[0]
   target=shadow_by_new.get(str(task.get('target_table_id')))
   source=shadow_by_new.get(str(task.get('source_table_id')))
   if category=='sink':
    # A copied flow must never retain a JDBC writer aimed at the police tenant's sink.
    for field in ('username','password','jdbcUrl','jdbcURL','url','host','port','database','catalog','schema','targetSchema','targetTable','table'):
     cfg.pop(field,None)
    cfg.update(registeredDatasourceId=shadow_ds if target else None,targetDbId=shadow_ds if target else None,targetTableId=target['id'] if target else None)
    if target:cfg.update(dbType='MySQL',jdbcUrl=ds['jdbc_url'],username=ds['username'],password=ds['password'],table=target['name'],writerType='NIFI_PUT_DATABASE_RECORD',deleteTargetData=False,updateKeys=','.join(target['keys']))
   elif category=='source' and source:
    rebind_shadow_source(node,source,ds,shadow_ds)
  pipeline.update(dsl_json=m.jd(dsl),dsl_hash=None,dsl_version=1,created_at=int(now.timestamp()*1000),updated_at=int(now.timestamp()*1000))
 statements='\n\n'.join(ddl)+'\n';folder=ROOT/'db/fixtures/chengtian-police-import';folder.mkdir(parents=True,exist_ok=True);(folder/'init.sql').write_text('-- 澄天迁入公安接入任务的独立实验目标；不包含业务数据或连接凭据。\n'+statements,encoding='utf-8')
 with gzip.open(path,'wt',encoding='utf-8') as stream:stream.write(m.jd({'source':raw,'rows':converted,'shadow':shadow,'ddl':ddl,'shadowDatasourceId':shadow_ds}))
 dump(OUT/'prepared-summary.json',{'selected':{t:len(v) for t,v in converted.items()},'skipped':skipped,'shadowTables':len(shadow),'pendingTargetTables':len(pending),'ownershipAssignments':ownership_reassigned,'credentials':'private configuration and restricted snapshot only'})
 print((OUT/'prepared-summary.json').read_text(encoding='utf-8'))

def bundle():
 with gzip.open(PRIVATE/'prepared.json.gz','rt',encoding='utf-8') as stream:return json.load(stream)

def copied_source_repair_candidates(prepared,live,datasource):
 """仅规划本批复制且未经人工修改/部署的来源修复；输入已批量预取，返回旧行及新DSL，不做I/O。"""
 shadow={v['id']:v for v in prepared['shadow'].values()}
 originals={r['id']:r for r in prepared['rows']['nifi_pipeline_t']}
 tasks={r['pipeline_id']:r for r in prepared['rows']['data_access_agg_task_t']}
 source_id=prepared['shadowDatasourceId'];changes=[]
 for row in live:
  task=tasks.get(row['id']);source=shadow.get(str((task or {}).get('source_table_id')))
  if not source:continue
  for field in ('source_db_id','target_db_id','source_table_id','target_table_id'):
   if row.get(field)!=task.get(field):raise RuntimeError('迁入任务关联已变化，拒绝覆盖：'+row['id'])
  if row.get('actual_source_db_id')!=row.get('source_db_id') or row.get('actual_target_db_id')!=row.get('target_db_id'):
   raise RuntimeError('迁入任务的登记关联不完整，拒绝自动修复：'+row['id'])
  original=json.loads(originals[row['id']]['dsl_json']);current=json.loads(row['dsl_json']);updated=copy.deepcopy(original)
  for node in updated.get('nodes',[]):
   if (node.get('category') or str(node.get('manifestKey','')).split('.')[0])=='source':
    rebind_shadow_source(node,source,datasource,source_id)
  if current==updated:continue
  if current!=original:raise RuntimeError('迁入画布已人工修改，保留当前配置，请单独核对：'+row['id'])
  if row.get('status')!='SAVED' or any(row.get(k) is not None for k in ('nifi_process_group_id','last_deployed_hash','last_deployed_at','task_process_group_id')):
   raise RuntimeError('迁入任务已经部署或运行，拒绝自动修改：'+row['id'])
  changes.append((row,updated))
 return changes

def repair_chain_sources(db,apply_changes=False,pipeline_ids=None):
 """本批迁入配置的有界修复。默认只读；逐ID租户/角色校验、受限备份、摘要外的原文CAS，无NiFi操作。"""
 m.check_session();prepared=bundle();applied=load(PRIVATE/'applied.json')
 applied_ids=applied['rows']['nifi_pipeline_t']
 if set(applied_ids)!={r['id'] for r in prepared['rows']['nifi_pipeline_t']}:raise RuntimeError('已应用批次与准备快照不一致')
 ids=list(pipeline_ids) if pipeline_ids is not None else applied_ids
 if not ids or len(ids)>100 or len(ids)!=len(set(ids)):raise RuntimeError('迁入画布批次须为1至100个不同ID')
 if not set(ids).issubset(applied_ids):raise RuntimeError('仅可修复本批已迁入的明确画布ID')
 live=m.query(db,"""select p.*,a.tid task_id,a.source_db_id,a.target_db_id,a.source_table_id,a.target_table_id,
 a.process_group_id task_process_group_id,s.datasource_id actual_source_db_id,t.datasource_id actual_target_db_id
 from nifi_pipeline_t p join data_access_agg_task_t a on a.pipeline_id=p.id and a.tenant_id=p.tenant_id and a.is_del=0
 left join db_table_t s on s.tid=a.source_table_id and s.tenant_id=p.tenant_id and s.is_del=0
 left join db_table_t t on t.tid=a.target_table_id and t.tenant_id=p.tenant_id and t.is_del=0
 where p.tenant_id=%s and p.is_del=0 and p.id in ("""+','.join(['%s']*len(ids))+')',[DST]+ids)
 if len(live)!=len(ids) or {r['id'] for r in live}!=set(ids):raise RuntimeError('本批画布或任务缺失、重复或属于其他租户')
 sources=m.query(db,'select tid,jdbc_url,username,password from db_datasource_t where tenant_id=%s and tid=%s and is_del=0',(DST,prepared['shadowDatasourceId']))
 if len(sources)!=1:raise RuntimeError('本批独立实验连接不存在或不属于当前租户')
 changes=copied_source_repair_candidates(prepared,live,sources[0])
 scope_ids=set()
 for row,_ in changes:
  scope_ids.update(filter(None,(row.get('source_db_id'),row.get('target_db_id'))))
  for node in json.loads(row['dsl_json']).get('nodes',[]):
   cfg=node.get('config') or {}
   scope_ids.update(str(cfg[k]) for k in ('registeredDatasourceId','selectedDatabaseId','sourceDbId','targetDbId','dbId','datasourceId','dataSourceId') if cfg.get(k))
 if len(scope_ids)>100:raise RuntimeError('本批修复引用数据源超过100个')
 # 与组合画布相同的角色数据范围：服务端解析当前用户，数据源主档按租户一次预取，逐ID判断但不逐项查询。
 if scope_ids:
  decision=m.api('/sym/auth/currentDataScope',{'resourceCode':'MENU:2081000000000000002'})
  scope=decision.get('scope');actor=decision.get('userId');organizations=set(decision.get('organizationIds') or [])
  if scope not in ('ALL','ORG','OWNER') or not actor:raise RuntimeError('无法确认当前角色的数据范围，拒绝修复')
  masters=m.query(db,'select tid,created_by,org_id from db_datasource_t where tenant_id=%s and is_del=0 and tid in ('+','.join(['%s']*len(scope_ids))+')',[DST]+sorted(scope_ids))
  if len(masters)!=len(scope_ids) or {str(r['tid']) for r in masters}!=scope_ids:raise RuntimeError('画布引用的数据源缺失或不属于当前租户，拒绝修复')
  for source in masters:
   allowed=scope=='ALL' or scope=='OWNER' and str(source.get('created_by') or '')==str(actor) or scope=='ORG' and str(source.get('org_id') or '') in organizations
   if not allowed:raise RuntimeError('当前角色无权访问画布引用的数据源，拒绝修复：'+str(source['tid']))
 report={'tenantId':DST,'selectedPipelines':len(ids),'plannedChanges':len(changes),'pipelineIds':[row['id'] for row,_ in changes],
         'businessSqlReads':3 if scope_ids else 2,'roleScopeBatchRequests':1 if scope_ids else 0,'applied':False,'nifiOperations':0}
 if apply_changes and changes:
  backup=PRIVATE/('chain-source-before-'+uuid.uuid4().hex+'.json.gz')
  with gzip.open(backup,'wt',encoding='utf-8') as stream:stream.write(m.jd([row for row,_ in changes]))
  try:
   now=int(dt.datetime.now().timestamp()*1000)
   for row,dsl in changes:
    changed=m.execute(db,"""update nifi_pipeline_t p
     join data_access_agg_task_t a on a.pipeline_id=p.id and a.tenant_id=p.tenant_id and a.is_del=0
     join db_table_t s on s.tid=a.source_table_id and s.tenant_id=p.tenant_id and s.is_del=0 and s.datasource_id=a.source_db_id
     join db_table_t t on t.tid=a.target_table_id and t.tenant_id=p.tenant_id and t.is_del=0 and t.datasource_id=a.target_db_id
     set p.dsl_json=%s,p.dsl_hash=null,p.dsl_version=coalesce(p.dsl_version,0)+1,p.updated_at=%s
     where p.tenant_id=%s and p.id=%s and p.is_del=0 and p.status='SAVED' and p.nifi_process_group_id is null and p.last_deployed_hash is null
       and p.last_deployed_at is null and p.updated_at<=>%s and binary p.dsl_json=binary %s and a.process_group_id is null
       and a.tid=%s and a.source_db_id=%s and a.target_db_id=%s and a.source_table_id=%s and a.target_table_id=%s""",
     (m.jd(dsl),now,DST,row['id'],row.get('updated_at'),row['dsl_json'],row['task_id'],row['source_db_id'],row['target_db_id'],row['source_table_id'],row['target_table_id']))
    if changed!=1:raise RuntimeError('画布发生并发修改，整批回退：'+row['id'])
   db.commit();report['applied']=True;report['backupOutsideRepository']=True
  except:db.rollback();raise
 dump(OUT/('chain-source-repair-result.json' if apply_changes else 'chain-source-repair-plan.json'),report)
 print(m.jd(report));return report

TASK_RUNTIME={'task_status','schedule_running','last_running','last_status_value','end_running','process_group_id','process_group_version','monitor_time','delay_level','delay_ms_estimate','is_timeout','queued_count','queued_bytes','active_thread_count','flow_files_in','flow_files_out','bytes_in','bytes_out','monitor_status','monitor_msg'}
PIPELINE_RUNTIME={'status','last_deployed_hash','last_deployed_at','last_stopped_at','node_mapping_json','last_bulletin_id','nifi_process_group_id'}
def configuration_rows(table,rows):
 ignored=TASK_RUNTIME|{'updated_time'} if table=='data_access_agg_task_t' else PIPELINE_RUNTIME|{'updated_at'} if table=='nifi_pipeline_t' else set()
 return [{k:v for k,v in row.items() if k not in ignored} for row in rows]

def finalize_rows(prepared,meta):
 rows=prepared['rows'];sets={t:{str(r[key(t)]) for r in rr} for t,rr in rows.items()}
 tables={r['tid']:r for r in rows['db_table_t']};sources={r['tid']:r for r in rows['db_datasource_t']};apps={r['tid']:r for r in rows['sym_application_t']};by_table=collections.defaultdict(list)
 for c in rows['db_table_column_t']:by_table[c['table_id']].append(c)
 repairs=collections.Counter();catalog_by_table={r['source_table_id']:r['tid'] for r in rows['da_catalog_t']};seed=m.Seed(meta)
 for table in rows['db_table_t']:
  for field,target in [('source_table_id','db_table_t'),('source_catalog_id','da_catalog_t'),('resource_model_id','res_logical_model')]:
   if table.get(field) and str(table[field]) not in sets[target]:table[field]=None;repairs[field]+=1
 for catalog in rows['da_catalog_t']:
  source=tables[catalog['source_table_id']];source_ds=sources[source['datasource_id']]
  catalog.update(db_id=source['datasource_id'],db_name=source_ds['db_name'],source_db_id=source['datasource_id'],source_db_name=source_ds['db_name'],source_table_name=source['table_name'])
  if catalog.get('target_table_id') not in tables:
   catalog.update(target_table_id=source['tid'],target_table_name=source['table_name'],target_db_id=source['datasource_id'],target_db_type=source_ds.get('db_type'));repairs['catalogPreviewBinding']+=1
  else:
   target=tables[catalog['target_table_id']];catalog.update(target_db_id=target['datasource_id'],target_table_name=target['table_name'],target_db_type=sources[target['datasource_id']].get('db_type'))
 for item in rows['da_catalog_item_t']:
  for field in ('source_table_column_id','target_table_column_id'):
   if item.get(field) and str(item[field]) not in sets['db_table_column_t']:item[field]=None;repairs[field]+=1
 for task in rows['data_access_agg_task_t']:
  for field in TASK_RUNTIME:
   if field in task:task[field]=None
  task['task_status']=0
  source=tables[task['source_table_id']];task['source_db_id']=source['datasource_id']
  if task.get('data_catalog_id') not in sets['da_catalog_t']:
   catid=catalog_by_table.get(source['tid'])
   if not catid:
    catid=uid('task-catalog/'+source['tid']);ds=sources[source['datasource_id']];app=apps.get(ds.get('app_id'),{})
    seed.add('da_catalog_t','copied-task-catalog',tid=catid,catalog_name=source.get('table_name_cn') or source['table_name'],catalog_name_en='ga_copy_'+source['tid'][:12],asset_desc='随公安接入任务迁入，补齐来源目录关联；不伪造审批记录。',app_id=ds['app_id'],app_name=app.get('app_name') or '公安共享数仓与基础服务',db_id=ds['tid'],db_name=ds['db_name'],source_db_id=ds['tid'],source_db_name=ds['db_name'],source_table_id=source['tid'],source_table_name=source['table_name'],target_db_id=task.get('target_db_id'),target_table_id=task.get('target_table_id'),target_table_name=tables.get(task.get('target_table_id'),{}).get('table_name'),target_db_type='mysql',org_id=source.get('org_id') or m.ORG,manage_unit=source.get('org_id') or m.ORG,business_type=source.get('business_type') or 'business',asset_status=source.get('asset_status') or 0,flow_status=source.get('flow_status') or 0,reg_time=dt.datetime.now())
    for c in by_table[source['tid']]:seed.add('da_catalog_item_t','copy-task-item/'+catid,tid=uid(catid+'/'+c['tid']),catalog_id=catid,col_name=c.get('column_comment') or c['column_name'],col_en=c['column_name'],col_type=c.get('column_type') or c.get('data_type'),col_comment=c.get('column_comment'),col_length=c.get('length'),is_pk=str(c.get('primary_key') or 0),is_nullable=str(c.get('nullable') or 0),sort_no=c.get('ordinal_position') or 0,source_table_column_id=c['tid'])
    catalog_by_table[source['tid']]=catid;repairs['taskCatalogCreated']+=1
   task['data_catalog_id']=catid
 for table,added in seed.rows.items():rows[table].extend(added)
 catalogs={r['tid']:r for r in rows['da_catalog_t']}
 fieldmap={'appId':'app_id','appName':'app_name','dbId':'db_id','dbName':'db_name','sourceDbId':'source_db_id','sourceDbName':'source_db_name','sourceTableId':'source_table_id','sourceTableName':'source_table_name','targetDbId':'target_db_id','targetTableId':'target_table_id','targetTableName':'target_table_name','orgId':'org_id','manageUnit':'manage_unit','catalogName':'catalog_name','catalogNameEn':'catalog_name_en'}
 for prop in rows['da_prop_t']:
  parent=catalogs.get(prop['parent_id']) or sources.get(prop['parent_id']) or apps.get(prop['parent_id']);field=fieldmap.get(prop.get('prop_name'))
  if parent and field and field in parent:prop['prop_value']='' if parent[field] is None else str(parent[field])
 for cat in seed.rows.get('da_catalog_t',[]):
  for propname,field in fieldmap.items():
   if cat.get(field) is not None:seedrow={'tid':uid(cat['tid']+'/prop/'+propname),'tenant_id':DST,'parent_id':cat['tid'],'prop_name':propname,'prop_value':str(cat[field]),'prop_type':'String','data_type':'catalog','is_del':0,'created_time':dt.datetime.now(),'updated_time':dt.datetime.now(),'created_by':m.ACTOR,'updated_by':m.ACTOR};rows['da_prop_t'].append({k:v for k,v in seedrow.items() if k in {x['COLUMN_NAME'] for x in meta['da_prop_t']}})
 return dict(repairs)

def normalize_insert_rows(rows,meta):
 for table,values in rows.items():
  for row in values:
   for column in meta[table]:
    field=column['COLUMN_NAME']
    if field in row:continue
    if column['IS_NULLABLE']=='YES':row[field]=None
    elif column['COLUMN_DEFAULT'] is not None:row[field]=column['COLUMN_DEFAULT']
    elif 'auto_increment' in column['EXTRA']:row[field]=None
    else:raise ValueError('新关联记录缺少必填字段：'+table+'.'+field)

def apply(db):
 m.check_session();prepared=bundle();meta,current=snapshot(db)
 current_selected,_=selected(current);prepared_selected,_=selected(prepared['source'])
 changed=[t for t in TABLES if m.jd(configuration_rows(t,current_selected[t]))!=m.jd(configuration_rows(t,prepared_selected[t]))]
 if changed:raise RuntimeError('公安源资源在准备后发生变化，尚未写入：'+','.join(changed))
 repairs=finalize_rows(prepared,meta);rows=prepared['rows'];normalize_insert_rows(rows,meta);existing={};pending={}
 for table,values in rows.items():
  found={};pk=key(table);ids=[r[pk] for r in values]
  for offset in range(0,len(ids),PAGE):
   batch=ids[offset:offset+PAGE]
   if batch:
    for row in m.query(db,f'select * from `{table}` where `{pk}` in ('+','.join(['%s']*len(batch))+')',batch):found[str(row[pk])]=row
  if any(r['tenant_id']!=DST for r in found.values()):raise RuntimeError('目标资源ID碰撞到其他租户，拒绝覆盖')
  if found:raise RuntimeError('所选迁入ID已存在；使用 index/verify，不重复覆盖已迁入资源')
  pending[table]=values
 backup=PRIVATE/('target-before-'+dt.datetime.now().strftime('%Y%m%d-%H%M%S')+'.jsonl.gz')
 with gzip.open(backup,'wt',encoding='utf-8') as stream:
  for table in TABLES:
   pk=key(table);cursor=''
   while True:
    page=m.query(db,f'select * from `{table}` where tenant_id=%s and `{pk}`>%s order by `{pk}` limit {PAGE}',(DST,cursor))
    if not page:break
    for row in page:stream.write(m.jd({'table':table,'row':row})+'\n')
    cursor=str(page[-1][pk])
 present={r['TABLE_NAME'] for r in m.query(db,'select table_name from information_schema.tables where table_schema=%s',(SHADOW,))};expected={v['name'] for v in prepared['shadow'].values()}
 if present-set(expected):raise RuntimeError('独立实验目标库出现本批以外的表，拒绝覆盖')
 if present:
  counts=m.query(db,' union all '.join(f'select count(*) n from `{SHADOW}`.`{name}`' for name in sorted(present)))
  if any(r['n'] for r in counts):raise RuntimeError('已有实验表包含数据，拒绝恢复未完成复制')
  actual={(r['TABLE_NAME'],r['COLUMN_NAME']):r['COLUMN_TYPE'].lower() for r in m.query(db,'select table_name,column_name,column_type from information_schema.columns where table_schema=%s',(SHADOW,))}
  oldcolumns={str(r['tid']):r['column_name'] for r in prepared['source']['db_table_column_t']}
  expectedcolumns={(v['name'],oldcolumns[cid]):typ.lower() for v in prepared['shadow'].values() if v['name'] in present for cid,typ in v['columns'].items()}
  expectedcolumns.update({(v['name'],'__copy_key_hash'):'binary(32)' for v in prepared['shadow'].values() if v['name'] in present and v.get('hashKey')})
  if actual!=expectedcolumns:raise RuntimeError('已有空实验表与本批审阅结构不一致')
 for statement in prepared['ddl']:
  found=re.search(r'CREATE TABLE `'+SHADOW+r'`\.`([^`]+)`',statement)
  if not found or found.group(1) not in present:m.execute(db,statement)
 try:
  for table,values in pending.items():
   if not values:continue
   fields=list(values[0]);sql=f'insert into `{table}` ('+','.join('`'+f+'`' for f in fields)+') values ('+','.join(['%s']*len(fields))+')'
   for offset in range(0,len(values),250):
    batch=values[offset:offset+250]
    with db.cursor() as cursor:cursor.executemany(sql,[tuple(m.jd(r[f]) if isinstance(r[f],(dict,list)) else r[f] for f in fields) for r in batch])
  db.commit()
 except:db.rollback();raise
 dump(PRIVATE/'applied.json',{'rows':{t:[r[key(t)] for r in v] for t,v in rows.items()},'shadow':prepared['shadow'],'shadowDatasourceId':prepared['shadowDatasourceId']})
 report={'inserted':{t:len(v) for t,v in pending.items()},'repairedReferences':repairs,'sourceUnchanged':True,'existingTargetDataPreserved':True,'backupOutsideRepository':True,'externalProcessGroupsCopied':0,'shadowTablesCreated':len(prepared['shadow'])}
 dump(OUT/'apply-result.json',report);print(m.jd(report))
 # Reuse the canonical schema exporter after creating this dedicated source database.
 exp=m.load_module('copy_schema_export',ROOT/'tools/export-db.py');opts=m.options();opts['database']=SHADOW
 name,sql,counts,notes=exp.mysql_export(SHADOW,opts);folder=ROOT/'db/fixtures/chengtian-police-import'
 (folder/'init.sql').write_text(sql,encoding='utf-8');dump(folder/'schema-manifest.json',{'database':name,'dialect':'mysql',**counts,'notes':notes})

def check(db):
 prepared=bundle();meta=metadata(db);repairs=finalize_rows(prepared,meta);normalize_insert_rows(prepared['rows'],meta);print(m.jd({'repairs':repairs,'rows':{t:len(v) for t,v in prepared['rows'].items()},'readOnly':True}))

ASSET_TABLES={'app':'sym_application_t','db':'db_datasource_t','table':'db_table_t','catalog':'da_catalog_t'}
ASSET_FIELDS={
 'sym_application_t':'tid,app_name,app_desc,is_use,org_id,asset_status,flow_status,created_time,updated_time,reg_time',
 'db_datasource_t':"tid,db_name,db_type,app_id,org_id,node_id,storage_domain,table_num,connection_status,show_connect,asset_status,flow_status,created_time,updated_time,reg_time,JSON_UNQUOTE(JSON_EXTRACT(CASE WHEN JSON_VALID(pool_cfg) THEN pool_cfg ELSE '{}' END,'$.database')) AS source_database,JSON_UNQUOTE(JSON_EXTRACT(CASE WHEN JSON_VALID(pool_cfg) THEN pool_cfg ELSE '{}' END,'$.schema')) AS source_schema",
 'db_table_t':'tid,datasource_id,table_name,table_name_en,table_name_cn,table_comment,org_id,asset_status,flow_status,record_count,field_count,annotated,business_type,data_source_type,created_time,updated_time,reg_time',
 'da_catalog_t':'tid,catalog_name,catalog_name_en,asset_desc,app_id,db_id,source_table_id,source_table_name,target_table_id,target_table_name,target_db_id,org_id,asset_status,flow_status,created_time,updated_time,reg_time',
 'da_prop_t':'tid,parent_id,prop_name,prop_value'
}
PROP_ALLOW={'appName','applicationSystemName','assetDesc','appId','appName','dbId','dbName','catalogName','catalogNameEn','shareType','publishStatus','publishTime','updateCycle','dataLevel','dataCategory','dataProcessLevel','sourceTableId','sourceTableName','targetTableId','targetTableName','targetDbId','sourceDbId','sourceDbName','resourceType','catalogType','businessType','dataSourceType'}
def target_rows(db,table):
 rows=[];last='';pk=key(table)
 while True:
  names=ASSET_FIELDS[table];page=m.query(db,f'select {names} from `{table}` where tenant_id=%s and is_del=0 and `{pk}`>%s order by `{pk}` limit {PAGE}',(DST,last))
  if not page:break
  rows.extend(page);last=str(page[-1][pk])
  if len(rows)>CAP:raise RuntimeError('当前租户投影单表超过维护上限：'+table)
 return rows
def es_date(value):
 """Normalize legacy date properties without inventing a publication timestamp."""
 if value is None or str(value).strip() in ('','null','None'):return None
 try:
  text=str(value).strip()
  if isinstance(value,dt.datetime):parsed=value
  elif isinstance(value,dt.date):return value.isoformat()
  elif re.fullmatch(r'\d{10}|\d{13}',text):parsed=dt.datetime.fromtimestamp(int(text)/(1000 if len(text)==13 else 1),dt.timezone.utc)
  else:parsed=dt.datetime.fromisoformat(text.replace('Z','+00:00'))
  if parsed.tzinfo is None:parsed=parsed.replace(tzinfo=dt.timezone(dt.timedelta(hours=8)))
  return parsed.isoformat()
 except (ValueError,OverflowError,OSError):return None

def index(db):
 m.check_session();conn=m.es_connection();name=conn[2]
 if not name.endswith('__tenant_'+DST):raise RuntimeError('只能更新澄天独立索引')
 objects={kind:target_rows(db,table) for kind,table in ASSET_TABLES.items()};props=collections.defaultdict(dict)
 for r in target_rows(db,'da_prop_t'):
  if r['prop_name'] in PROP_ALLOW:props[str(r['parent_id'])][r['prop_name']]=r['prop_value']
 orgs=m.query(db,'select id,name,serial_number from rm_org_t where tenant_id=%s and deleted=0',(DST,));orgmap={r['id']:r for r in orgs}
 dbs={r['tid']:r for r in objects['db']};tables={r['tid']:r for r in objects['table']};apps={r['tid']:r for r in objects['app']}
 tablecounts=collections.Counter(r['datasource_id'] for r in objects['table']);sourcecounts=collections.Counter(r['app_id'] for r in objects['db']);catalogcounts=collections.Counter(r['app_id'] for r in objects['catalog']);catalogsourcecounts=collections.Counter(r['db_id'] for r in objects['catalog'])
 annotated=collections.Counter(r['datasource_id'] for r in objects['table'] if r.get('annotated') in (1,'1'));registered=collections.Counter(r['datasource_id'] for r in objects['table'] if r.get('asset_status') in (2,'2'))
 apis={r['catalog_id']:r['tid'] for r in m.query(db,'select tid,catalog_id from api_info_t where tenant_id=%s and is_del=0',(DST,))}
 docs=[];invalid_dates=collections.Counter()
 for kind,values in objects.items():
  for row in values:
   rid=row['tid'];org=orgmap.get(row.get('org_id'),{});doc=dict(props.get(rid,{}));doc.update(id=rid,tid=rid,assetType=kind,tenantId=DST,orgId=row.get('org_id'),orgName=org.get('name',''),orgPath=[r['serial_number'] for r in orgs if r['serial_number'] and str(org.get('serial_number') or '').startswith(r['serial_number'])],assetStatus=row.get('asset_status') or 0,flowStatus=row.get('flow_status') or 0)
   for source,dest in [('created_time','createdTime'),('updated_time','updatedTime'),('reg_time','regTime')]:
    if row.get(source):doc[dest]=row[source].isoformat() if hasattr(row[source],'isoformat') else str(row[source])
   if kind=='app':doc.update(appName=row['app_name'],applicationSystemName=row['app_name'],assetDesc=row.get('app_desc'),dbNum=sourcecounts[rid],catalogNum=catalogcounts[rid],tableNum=sum(tablecounts[d['tid']] for d in objects['db'] if d['app_id']==rid),useStatus=str(row.get('is_use') or 0))
   elif kind=='db':
    doc.update(dbName=row['db_name'],dbType=row.get('db_type'),appId=row.get('app_id'),appName=apps.get(row.get('app_id'),{}).get('app_name'),tableNum=tablecounts[rid],registeredTableNum=tablecounts[rid],annotatedTableNum=tablecounts[rid],unregisteredTableNum=0,unannotatedTableNum=0,problemTableNum=0,catalogNum=catalogsourcecounts[rid],nodeId=row.get('node_id'),storageDomain=row.get('storage_domain'),connectionStatus=row.get('connection_status'),showConnect=row.get('show_connect'),database=row.get('source_database') or row.get('source_schema') or '')
    total=max(int(row.get('table_num') or 0),tablecounts[rid]);doc.update(tableNum=total,registeredTableNum=registered[rid],annotatedTableNum=annotated[rid],unregisteredTableNum=total-registered[rid],unannotatedTableNum=total-annotated[rid],problemTableNum=tablecounts[rid]-registered[rid])
   elif kind=='table':doc.update(dbId=row['datasource_id'],datasourceId=row['datasource_id'],dbName=dbs.get(row['datasource_id'],{}).get('db_name'),tableName=row['table_name'],tableNameEn=row.get('table_name_en'),tableNameCn=row.get('table_name_cn'),tableComment=row.get('table_comment'),recordCount=row.get('record_count') or 0,fieldCount=row.get('field_count') or 0,annotated=row.get('annotated'),businessType=row.get('business_type'),dataSourceType=row.get('data_source_type'))
   else:
    doc.update(catalogName=row['catalog_name'],catalogNameEn=row.get('catalog_name_en'),assetDesc=row.get('asset_desc'),appId=row.get('app_id'),appName=apps.get(row.get('app_id'),{}).get('app_name'),dbId=row.get('db_id'),dbName=dbs.get(row.get('db_id'),{}).get('db_name'),sourceTableId=row.get('source_table_id'),sourceTableName=row.get('source_table_name'),targetTableId=row.get('target_table_id'),targetTableName=row.get('target_table_name'),targetDbId=row.get('target_db_id'),recordCount=tables.get(row.get('source_table_id'),{}).get('record_count') or 0,fieldCount=tables.get(row.get('source_table_id'),{}).get('field_count') or 0)
    doc['publishStatus']=int(doc.get('publishStatus') or 0)
    if rid in apis:doc['apiId']=apis[rid]
   for field in ('createdTime','updatedTime','regTime','publishTime'):
    if field not in doc:continue
    original=doc[field];date=es_date(original)
    if date is None:
     doc.pop(field)
     if original and str(original).strip() not in ('null','None'):invalid_dates[field]+=1
    else:doc[field]=date
   docs.append(doc)
 for offset in range(0,len(docs),200):
  batch=docs[offset:offset+200];payload=''.join(m.jd({'index':{'_index':name,'_id':r['tid']}})+'\n'+m.jd(r)+'\n' for r in batch);result=m.es_call(conn,'POST','/_bulk',payload,True)
  if result.get('errors'):
   errors=[]
   for item in result.get('items',[]):
    error=item.get('index',{}).get('error')
    if not error:continue
    reason=error.get('reason','');field=re.search(r'(?:field|mapper) \[([^]]+)\]',reason)
    errors.append({'type':error.get('type'),'field':field.group(1) if field else None,'cause':(error.get('caused_by') or {}).get('type')})
   dump(OUT/'index-errors.json',{'batchOffset':offset,'errors':errors[:10]})
   raise RuntimeError('资产投影批量更新失败；已记录错误类型及字段，未输出记录或连接字段')
 m.es_call(conn,'POST','/'+name+'/_refresh');report={'index':name,'upserted':len(docs),'counts':{kind:len(v) for kind,v in objects.items()},'omittedInvalidDates':dict(invalid_dates),'otherTenantIndexesTouched':0,'secretsIndexed':False}
 dump(OUT/'index-result.json',report);print(m.jd(report));return report

def verify(db):
 m.check_session();applied=load(PRIVATE/'applied.json');report={'copiedCounts':{},'orphanChecks':{}}
 for table,ids in applied['rows'].items():
  found=0
  for offset in range(0,len(ids),PAGE):
   batch=ids[offset:offset+PAGE]
   if batch:found+=int(m.query(db,f'select count(*) n from `{table}` where tenant_id=%s and `{key(table)}` in ('+','.join(['%s']*len(batch))+')',[DST]+batch)[0]['n'])
  if found!=len(ids):raise RuntimeError('迁入记录数不符：'+table)
  report['copiedCounts'][table]=found
 for name,left,field,right,pk in [('sourceApplication','db_datasource_t','app_id','sym_application_t','tid'),('tableSource','db_table_t','datasource_id','db_datasource_t','tid'),('columnTable','db_table_column_t','table_id','db_table_t','tid'),('catalogSourceTable','da_catalog_t','source_table_id','db_table_t','tid'),('catalogTargetTable','da_catalog_t','target_table_id','db_table_t','tid'),('itemCatalog','da_catalog_item_t','catalog_id','da_catalog_t','tid'),('taskSource','data_access_agg_task_t','source_table_id','db_table_t','tid'),('taskTarget','data_access_agg_task_t','target_table_id','db_table_t','tid'),('taskCatalog','data_access_agg_task_t','data_catalog_id','da_catalog_t','tid')]:
  value=m.query(db,f'select count(*) n from `{left}` a left join `{right}` b on b.`{pk}`=a.`{field}` and b.tenant_id=a.tenant_id and b.is_del=0 where a.tenant_id=%s and a.is_del=0 and a.`{field}` is not null and b.`{pk}` is null',(DST,))[0]['n'];report['orphanChecks'][name]=value
 if any(report['orphanChecks'].values()):raise RuntimeError('仍存在未解释的迁入关联断链')
 pipeids=applied['rows']['nifi_pipeline_t'];states=m.query(db,'select status,count(*) n,sum(nifi_process_group_id is not null) bound from nifi_pipeline_t where tenant_id=%s and id in ('+','.join(['%s']*len(pipeids))+') group by status',[DST]+pipeids)
 if len(states)!=1 or states[0]['status']!='SAVED' or states[0]['bound']:raise RuntimeError('复制的加工图携带了运行状态或旧流程组')
 report['copiedPipelines']=states
 taskids=applied['rows']['data_access_agg_task_t'];tasks=m.query(db,'select count(*) n,sum(process_group_id is not null) bound,sum(target_db_id<>%s) foreignTarget from data_access_agg_task_t where tenant_id=%s and tid in ('+','.join(['%s']*len(taskids))+')',[applied['shadowDatasourceId'],DST]+taskids)[0]
 if tasks['bound'] or tasks['foreignTarget']:raise RuntimeError('复制的接入任务仍引用外部写入目标')
 report['copiedTaskTargets']=tasks
 sample=m.query(db,'select datasource_id,count(*) n from db_table_t where tenant_id=%s and is_del=0 and datasource_id in ('+','.join(['%s']*len(applied['rows']['db_datasource_t']))+') group by datasource_id order by n desc limit 1',[DST]+applied['rows']['db_datasource_t'])[0]
 evidence=[];route='/dst/database/departmentDataSources';logfile=ROOT/'logs/backend/local-runtime/stdout.log'
 m.api(route,{'mode':'tables','datasourceId':sample['datasource_id'],'pageNum':1,'pageSize':20})
 for size in (20,100):
  offset=logfile.stat().st_size if logfile.exists() else 0;result=m.api(route,{'mode':'tables','datasourceId':sample['datasource_id'],'pageNum':1,'pageSize':size})
  if len(result['list'])!=size:raise RuntimeError('数据登记分页未返回要求条数')
  lines=[]
  if logfile.exists():
   with logfile.open('rb') as stream:stream.seek(offset);lines=stream.read(8*1024*1024).decode('utf-8',errors='replace').splitlines()
  target=[line for line in lines if '执行SQL' in line and ('db_table_t' in line or 'db_datasource_t' in line)];threads={re.search(r'\[([^]]+)\]',line).group(1) for line in target if re.search(r'\[([^]]+)\]',line)}
  count=sum('执行SQL' in line and any('['+thread+']' in line for thread in threads) for line in lines)
  evidence.append({'pageSize':size,'returnedRows':len(result['list']),'total':result['total'],'httpReadRequests':1,'requestThreadSql':count or None})
 if not evidence[0]['requestThreadSql'] or evidence[0]['requestThreadSql']!=evidence[1]['requestThreadSql']:raise RuntimeError('未取得20/100行相同且非零的SQL数，需复核')
 report['pagination']=evidence
 foreign=m.query(db,'select tid from `baseline_ga`.db_datasource_t where tenant_id=%s and is_del=0 order by tid limit 1',(SRC,))
 if not foreign:raise RuntimeError('缺少可用于异租户拒绝验证的公安有效数据源')
 foreign_id=foreign[0]['tid']
 try:
  result=m.api(route,{'mode':'tables','datasourceId':foreign_id,'pageNum':1,'pageSize':20});denied=not result.get('list')
 except RuntimeError as error:denied=any(word in str(error) for word in ('403','404','无权','不存在','不可见','未找到'))
 if not denied:raise RuntimeError('原公安数据源ID在澄天会话下未被拒绝')
 visible=m.query(db,'select tid from db_datasource_t where tenant_id=%s and is_del=0 order by tid limit 100',(DST,))
 report['mixedTenantBatchChecks']=[]
 for size in (20,100):
  ids=[r['tid'] for r in visible[:size-1]]+[foreign_id];denied=False
  try:m.api(route,{'mode':'reportTables','datasourceIds':ids,'pageNum':1,'pageSize':20})
  except RuntimeError as error:denied='数据源不存在或无权访问' in str(error)
  if not denied:raise RuntimeError('混入原公安ID的批量请求未整体拒绝')
  report['mixedTenantBatchChecks'].append({'ids':size,'foreignIds':1,'entireBatchDenied':True})
 report['foreignSourceDenied']=denied;report['menuOrder']=m.query(db,"select name,url,sort_num from rm_menu_t where tenant_id=%s and parent_id='ROOT' and deleted=0 and status=0 order by sort_num limit 3",(DST,))
 dump(OUT/'verification.json',report);print(m.jd(report))

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',nargs='?',default='plan',choices=['plan','menu','prepare','reprepare','check','apply','index','verify','repair-chain']);p.add_argument('--apply',action='store_true',help='仅repair-chain使用，明确执行已规划的迁入来源修复');p.add_argument('--pipeline-id',action='append',help='repair-chain显式选择本批迁入画布，可重复');args=p.parse_args();OUT.mkdir(parents=True,exist_ok=True);PRIVATE.mkdir(parents=True,exist_ok=True)
 if (args.apply or args.pipeline_id) and args.action!='repair-chain':raise ValueError('--apply及--pipeline-id仅用于repair-chain')
 db=m.connection()
 try:
  if args.action=='plan':plan(db)
  elif args.action=='menu':menu(db)
  elif args.action=='prepare':prepare(db)
  elif args.action=='reprepare':prepare(db,bundle()['source'])
  elif args.action=='apply':apply(db)
  elif args.action=='check':check(db)
  elif args.action=='index':index(db)
  elif args.action=='verify':verify(db)
  elif args.action=='repair-chain':repair_chain_sources(db,args.apply,args.pipeline_id)
 finally:db.close()
if __name__=='__main__':
 try:main()
 except Exception as error:
  frames=traceback.extract_tb(error.__traceback__);location=frames[-1].name+':'+str(frames[-1].lineno) if frames else ''
  if isinstance(error,(RuntimeError,ValueError)):message=str(error)[:600]
  elif isinstance(error,KeyError):message='missing metadata field' + (' '+str(error.args[0]) if re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*',str(error.args[0])) and not SECRET.search(str(error.args[0])) else '')
  elif isinstance(error,m.pymysql.MySQLError):message='database validation '+str(error.args[0])
  else:message='details omitted to protect connection credentials'
  print(type(error).__name__+': '+message+' ('+location+')',file=sys.stderr);sys.exit(1)
