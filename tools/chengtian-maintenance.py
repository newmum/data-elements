#!/usr/bin/env python3
"""澄天资产维护工具的受限运行连接与批量写入辅助。

连接只从工程外配置解析，固定澄天实际绑定；不包含演示场景、模拟业务数据或资源发布入口。
api/check_session 要求有效会话，支持 RESOURCE_API_TOKEN；查询和写入由调用工具显式选择。
调用方负责事务、业务授权和批次上限，连接必须由 with 或 finally 关闭。
"""
from __future__ import annotations
import collections, datetime as dt, decimal, importlib.util, json, os, pathlib, urllib.request, urllib.parse, uuid
import pymysql

ROOT=pathlib.Path(__file__).resolve().parents[1]
PRIVATE=pathlib.Path.home()/'.codex/private/data-elements'
TENANT='2084109831682699267'
SCHEMA='chengtian'
ACTOR='1995681734511165441'
ORG='1351480104462708738'
NOW=dt.datetime.now()

def uid(kind,n=0):
 return uuid.uuid5(uuid.NAMESPACE_URL,f'chengtian-resource-maintenance-v1/{kind}/{n}').hex

def jd(v):return json.dumps(v,ensure_ascii=False,default=str,separators=(',',':'))

def load_module(name,file):
 s=importlib.util.spec_from_file_location(name,file);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m

def config():return json.loads((PRIVATE/'resources.local.json').read_text(encoding='utf-8'))

def options():
 exp=load_module('export_db_police',ROOT/'tools/export-db.py')
 matches=[x for x in exp.connections(config()) if x[0]=='chengtian' and x[2]==SCHEMA]
 if len(matches)!=1:raise RuntimeError('实际澄天绑定不符合固定目标，拒绝写入')
 return matches[0][3]

def connection():return pymysql.connect(**options(),cursorclass=pymysql.cursors.DictCursor,autocommit=False,connect_timeout=10,read_timeout=180,write_timeout=180)

def query(db,sql,args=None):
 with db.cursor() as c:c.execute(sql,args);return c.fetchall()

def execute(db,sql,args=None):
 with db.cursor() as c:c.execute(sql,args);return c.rowcount

def api(route,body=None,timeout=120):
 runtime=config().get('runtime',{})
 base=os.environ.get('RESOURCE_API_URL',runtime.get('baseUrl','http://localhost:8088')).rstrip('/')
 token=os.environ.get('RESOURCE_API_TOKEN') or runtime.get('token')
 target=urllib.parse.urlsplit(base)
 if (target.scheme,target.hostname,target.port) != ('http','localhost',8088) or target.path:
  raise ValueError('只允许本机授权后端')
 if not token:raise ValueError('请通过受限运行配置或 RESOURCE_API_TOKEN 提供有效会话')
 req=urllib.request.Request(base+route,data=jd(body).encode() if body is not None else None,headers={'token':token,'Content-Type':'application/json'})
 try:
  with urllib.request.urlopen(req,timeout=timeout) as response:
   if response.status==204:return None
   v=json.load(response)
 except urllib.error.HTTPError as e:
  try:
   value=json.load(e);safe={k:value[k] for k in ('message','msg','errorCode','code','hint','suggestion','detail') if k in value}
   if isinstance(value.get('error'),str):safe['error']=value['error'][:700]
  except Exception:safe={}
  raise RuntimeError('HTTP '+str(e.code)+' '+jd(safe)) from None
 if isinstance(v,dict) and ('code' in v or 'success' in v):
  if v.get('code',0) not in (0,200) or v.get('success') is False:
   err=v.get('error') or {};message=str(v.get('msg') or v.get('message'))
   detail=err.get('detail') or err.get('technicalDetail') or err.get('rootCause') or err.get('cause')
   if detail:message+=' | '+str(detail)[:900]
   raise RuntimeError(message[:1400])
  return v.get('data',v)
 return v

def check_session():
 v=api('/sym/tenant/current')
 if v.get('tid')!=TENANT:raise RuntimeError('会话不属于澄天，拒绝执行')
 return v

class Seed:
 def __init__(self,cols):self.cols=cols;self.rows=collections.defaultdict(list)
 def add(self,table,kind,n=0,**data):
  fields={c['COLUMN_NAME']:c for c in self.cols[table]}
  common={'tid':uid(kind,n),'tenant_id':TENANT,'created_by':ACTOR,'updated_by':ACTOR,'created_time':NOW,'updated_time':NOW,'is_del':0,'revision':1}
  row={k:v for k,v in common.items() if k in fields};row.update(data)
  if set(row)-set(fields):raise ValueError(f'{table} unknown columns {set(row)-set(fields)}')
  for k,c in fields.items():
   if c['IS_NULLABLE']=='NO' and c['COLUMN_DEFAULT'] is None and 'auto_increment' not in c['EXTRA'] and row.get(k) is None:raise ValueError(f'{table}.{k} requires a value')
  self.rows[table].append(row);return row.get('tid',row.get('id'))
 def insert(self,db):
  for table,rows in self.rows.items():
   keys=list(rows[0])
   if any(set(r)!=set(keys) for r in rows):raise ValueError(f'{table} inconsistent insert fields')
   sql=f'insert into `{table}` ('+','.join('`'+k+'`' for k in keys)+') values ('+','.join(['%s']*len(keys))+')'
   with db.cursor() as c:c.executemany(sql,[tuple(jd(r[k]) if isinstance(r[k],(dict,list)) else r[k] for k in keys) for r in rows])

def runtime_config():
 import yaml
 c=config();runtime=json.loads((PRIVATE/'local-runtime.private.json').read_text(encoding='utf-8'));env=runtime.get('environment',{});os.environ.update(env)
 origin=c['origin'];base=origin['nacosUrl'];helpers=load_module('configure_police',ROOT/'tools/configure-resources.py')
 req=urllib.request.Request(base+'/v1/auth/login',data=urllib.parse.urlencode({'username':env['NACOS_USER'],'password':env['NACOS_PASSWORD']}).encode())
 token=json.load(urllib.request.urlopen(req,timeout=15))['accessToken'];result={}
 for name in ['data-element.yml','data-element-'+origin['profile']+'.yml']:
  q=urllib.parse.urlencode({'dataId':name,'group':'DEFAULT_GROUP','tenant':origin['namespace'],'accessToken':token})
  text=urllib.request.urlopen(base+'/v1/cs/configs?'+q,timeout=15).read().decode();helpers.merge(result,yaml.safe_load(text) or {})
 if env.get('SPRING_APPLICATION_JSON'):helpers.merge(result,json.loads(env['SPRING_APPLICATION_JSON']))
 return result

def es_connection():
 c=runtime_config();spring=c.get('spring',{});es=spring.get('elasticsearch',{});rest=es.get('rest',{})
 hosts=es.get('uris') or rest.get('uris') or es.get('hosts') or c.get('elasticsearch',{}).get('hosts')
 if not hosts:raise RuntimeError('未找到当前运行Elasticsearch连接；配置字段：'+jd({'springKeys':list(spring),'esKeys':list(es),'topKeys':list(c)}))
 host=hosts[0] if isinstance(hosts,list) else str(hosts).split(',')[0]
 if not host.startswith(('http://','https://')):host='http://'+host
 headers={'Content-Type':'application/json'};username=es.get('username') or rest.get('username');password=es.get('password') or rest.get('password')
 if username:
  import base64
  helpers=load_module('configure_es_police',ROOT/'tools/configure-resources.py')
  headers['Authorization']='Basic '+base64.b64encode((helpers.resolve(username)+':'+helpers.resolve(password)).encode()).decode()
 prefix=c.get('es',{}).get('prefix','');tenant=c.get('es',{}).get('tenant',{});default=c.get('data-element',{}).get('tenant',{}).get('default-tenant-id','2084109831682699264')
 index=prefix+'dataassets'
 if str(default)!=TENANT:index+=(tenant.get('separator') or '__tenant_')+TENANT
 return host.rstrip('/'),headers,index

def es_call(conn,method,path,body=None,ndjson=False):
 host,headers,_=conn;headers=dict(headers)
 if ndjson:headers['Content-Type']='application/x-ndjson'
 data=body.encode() if isinstance(body,str) else jd(body).encode() if body is not None else None
 req=urllib.request.Request(host+path,data=data,headers=headers,method=method)
 with urllib.request.urlopen(req,timeout=120) as r:return json.load(r)
