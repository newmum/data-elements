"""Build the police MySQL standalone package from current canonical resources."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED
import pymysql
from backend_hive_assets import verify_bundled_hive_client

ROOT=Path(__file__).resolve().parents[1]
TENANT='2084109831682699264'
APP='1995678661281710081'
ROLE='police-demo-admin-role'


def literal(value):
    if value is None:return 'NULL'
    return 'CONVERT(0x'+str(value).encode('utf8').hex()+' USING utf8mb4)' if str(value) else "''"


def insert(table,row):
    return 'INSERT INTO `'+table+'` ('+','.join('`'+k+'`' for k in row)+') VALUES ('+','.join(literal(v) for v in row.values())+');\n'


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--backend',type=Path,required=True)
    p.add_argument('--components',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--mysql-vendor',type=Path,required=True)
    p.add_argument('--config',type=Path,default=Path.home()/'.codex/private/data-elements/resources.local.json')
    a=p.parse_args()
    hive_client=verify_bundled_hive_client(a.backend)
    stage=ROOT/'logs/police-mysql-release/stage'
    for name in ('db','tools','config','backend','www','vendor'):(stage/name).mkdir(parents=True,exist_ok=True)
    cfg=json.loads(a.config.read_text(encoding='utf-8-sig'))
    options={k:v for k,v in cfg['control'].items() if k in ('host','port','user','password','database','charset')}
    binding=cfg['nacos_tenant_database'];source=binding['data-sources'][binding['tenant-bindings'][TENANT]]
    schema=source['url'].split('?',1)[0].rsplit('/',1)[-1]
    options['database']=schema
    db=pymysql.connect(**options,cursorclass=pymysql.cursors.DictCursor)
    defaults=[]
    with db,db.cursor() as cur:
        def rows(table,columns,where):
            cur.execute('SELECT '+columns+' FROM '+table+' WHERE tenant_id=%s AND '+where,(TENANT,));return cur.fetchall()
        menus=rows('rm_menu_t','id,tenant_id,name,parent_id,static_icon,status,resource_type,sno,url_type,url,method_name,deleted,sort_num,code,open_type,top_id,app_id,data_scope_enabled',"deleted=0 AND app_id='"+APP+"'")
        retired={row['id'] for row in menus if row['name'] in ('租户管理','菜单管理','开发线上','本地测试','本地测试2')
                 or row['sno'] in ('tenant-manage','menu-manage') or re.match(r'https?://',str(row['url'] or ''))}
        while True:
            descendants={row['id'] for row in menus if row['parent_id'] in retired}
            if descendants.issubset(retired):break
            retired.update(descendants)
        menus=[row for row in menus if row['id'] not in retired]
        for row in menus:
            defaults.append(insert('rm_menu_t',row))
            defaults.append(insert('rm_role_menu_rela_t',dict(id=hashlib.md5((ROLE+row['id']).encode()).hexdigest(),tenant_id=TENANT,role_id=ROLE,app_resource_id=row['id'])))
        for table in ('sym_dict_type_t','sym_dict_t'):
            for row in rows(table,'*','is_del=0'):defaults.append(insert(table,row))
        forms=rows('sym_form','tid,form_name,form_json,form_config,env,is_del,tenant_id','is_del=0')
        orgs=rows('rm_org_t','id,tenant_id,name,parent_id,serial_number,org_path,nature,status,deleted,sort_num','deleted=0')
        configs=rows('sym_config_t','tid,config_code,config_name,config_value,is_use,sort_no,is_encrypt,is_del,tenant_id,config_group,value_type,config_scope,cache_ttl_seconds,version_no',"is_use=1 AND is_encrypt=0 AND (is_del=0 OR is_del IS NULL) AND (config_group='PORTAL' OR (config_group='RUNTIME' AND config_code='orgRootId') OR (config_group='AUTH_DATA_SCOPE' AND config_code='RESOURCE_SCOPES'))")
    root=next(row['config_value'] for row in configs if row['config_code']=='orgRootId')
    assert root in {row['id'] for row in orgs}
    for name,schema_name in [('01-control.sql','baseline'),('02-police.sql','baseline_ga')]:
        sql=(ROOT/'db'/schema_name/'init.sql').read_text(encoding='utf8')
        assert not re.search(r'(?im)^\s*(INSERT|DROP|TRUNCATE)\b',sql)
        (stage/'db'/name).write_text(sql,encoding='utf8')
    control=[insert('sym_tenant_t',dict(tid=TENANT,code='PUBLIC_SECURITY',name='公安',init_flag=1,json_config=json.dumps({'accountSource':'LOCAL','appId':APP},ensure_ascii=False),sort_no=1,is_del=0,status=1,isolation_mode='DATABASE'))]
    defaults.insert(0,insert('sym_application_t',dict(tid=APP,tenant_id=TENANT,app_name='源数据管理平台',client_id='police-demo',is_use=1,is_del=0)))
    defaults.insert(1,insert('rm_role_t',dict(id=ROLE,tenant_id=TENANT,name='系统管理员',code_num='admin',app_id=APP,data_scope='ALL',status=0,deleted=0)))
    defaults.extend(insert('rm_org_t',row) for row in orgs)
    for row in configs:
        defaults.append(insert('sym_config_t',row))
        if row['config_group']=='PORTAL':control.append(insert('sym_config_t',dict(row,tid='police-default-'+row['config_code'],tenant_id='GLOBAL')))
    # Logical index names are deployment defaults, never copied from a shared environment.
    for code,value in [('es_dataassets','dataassets'),('es_dataassets_apply','dataassets_apply_form')]:
        defaults.append(insert('sym_config_t',dict(tid='police-runtime-'+code,config_code=code,
            config_name='资产索引' if code=='es_dataassets' else '资源申请索引',config_value=value,
            is_use=1,is_encrypt=0,is_del=0,tenant_id=TENANT,config_group='RUNTIME',value_type='STRING',
            config_scope='INTERNAL',cache_ttl_seconds=300,version_no=1,sort_no=1)))
    # Public application identity is needed before the initial tenant session exists.
    for code,value,kind in [('appId',APP,'STRING'),('showLogo','true','BOOLEAN'),('needLogin','true','BOOLEAN'),('loginUrl','/login','STRING')]:
        if not any(row['config_code']==code and row['config_group']=='PORTAL' for row in configs):
            control.append(insert('sym_config_t',dict(tid='police-default-'+code,config_code=code,config_name=code,config_value=value,is_use=1,is_encrypt=0,is_del=0,tenant_id='GLOBAL',config_group='PORTAL',value_type=kind,config_scope='PUBLIC',cache_ttl_seconds=300,version_no=1,sort_no=1)))
    resource=['SET NAMES utf8mb4;\nUSE baseline;\n']
    manifest=json.loads((ROOT/'magic/.manifest.json').read_text(encoding='utf8'))
    magic_count=0
    for item in manifest['resources']:
        path=item['path']
        if item.get('publicationBlocked') or not (path in ('','api','function','task') or path.startswith(('api/','function/','task/'))):continue
        content='this is directory' if item['kind']=='directory' else (ROOT/'magic'/path).read_text(encoding='utf8')
        resource.append(insert('api_file_t',dict(tid=next((v for v in item['rowIds'] if v),hashlib.md5(item['databasePath'].encode()).hexdigest()),file_path=item['databasePath'],file_content=content,is_del=0)))
        magic_count+=1
    components=json.loads(a.components.read_text(encoding='utf8'))
    for x in components:
        resource.append(insert('ui_component_t',dict(tid=x['id'],pid=x.get('parentId'),name=x['name'],remark=x.get('displayName'),source_code=x.get('source'),compile_js=x.get('compileJs'),compile_css=x.get('compileCss'),type=x['type'],sort_order=x.get('sortOrder'),is_del=0,tenant_id='0'*32)))
    resource.append('USE baseline_ga;\n')
    resource.extend(insert('sym_form',row) for row in forms)
    (stage/'db/03-resources.sql').write_text(''.join(resource),encoding='utf8')
    (stage/'db/04-police-defaults.sql').write_text('SET NAMES utf8mb4;\nUSE baseline;\n'+''.join(control)+'USE baseline_ga;\n'+''.join(defaults),encoding='utf8')
    template=ROOT/'tools/police-deployment/mysql'
    for name in ('start.sh','stop.sh','status.sh'):shutil.copy2(template/name,stage/name)
    shutil.copy2(template/'standalone.py',stage/'tools/standalone.py')
    account=(template/'init-account.py').read_text(encoding='utf8').replace('2026503859392086017',root)
    (stage/'db/init-account.py').write_text(account,encoding='utf8')
    shutil.copy2(template/'PasswordCodec.java',stage/'db/PasswordCodec.java')
    (stage/'tools/lib').mkdir(exist_ok=True)
    with ZipFile(a.backend) as backend:
        name=next(n for n in backend.namelist() if n.startswith('BOOT-INF/lib/bcprov-jdk18on-'))
        (stage/'tools/lib'/Path(name).name).write_bytes(backend.read(name))
    config=(template/'application.yml').read_text(encoding='utf8')
    (stage/'config/application.yml').write_text(config,encoding='utf8')
    shutil.copy2(template/'Caddyfile',stage/'config/Caddyfile')
    (stage/'config/ports.json').write_text(json.dumps(dict(mysql=13316,redis=18279,backend=18288,web=18291,search=19210,searchTransport=19310,webBind='0.0.0.0')),encoding='ascii')
    assert (stage/'www').resolve().parent==stage.resolve()
    shutil.rmtree(stage/'www')
    shutil.copytree(ROOT/'data-elements-chengtian/dist',stage/'www')
    shutil.copy2(a.backend,stage/'backend/data-element.jar')
    for name in ('java21.tar.gz','mysql.tar.xz','mysql-libs.tar.gz','redis.tar.gz','caddy.tar.gz','elasticsearch.tar.gz'):
        source=a.mysql_vendor/name if name.startswith(('mysql','elasticsearch')) else ROOT/'logs/haitong-linux-standalone/vendor'/name
        shutil.copy2(source,stage/'vendor'/name)
    report=dict(magic=magic_count,lowcode=len(components),forms=len(forms),organizations=len(orgs),menus=len(menus),tenantSchema=schema,hiveClient=hive_client)
    (stage/'release.json').write_text(json.dumps(report,indent=2),encoding='utf8')
    notes='源数据管理平台 公安单租户 MySQL8 / Linux x86_64 完整包\n\n解压后执行 bash start.sh，首次启动自动初始化数据库和账号。\n停止 bash stop.sh，状态 bash status.sh。\n账号admin，随机密码在data/local/admin-login.txt。默认网页18291端口。\n需要系统Python3.6或更新版本；MySQL、JDK21、Valkey、Caddy、Elasticsearch7.17.29均内置。\nroot运行时自动创建本目录专用服务用户；普通用户须有部署目录读写权限。\n端口设置在config/ports.json，数据位于data/local，日志位于logs。ES19210/19310、数据库及缓存仅本机访问。\nMagic、低代码、动态表单、业务导航、字典和公安组织机构均自动导入。\n首次启动自动创建独立的公安资产与申请索引；不会连接旧环境或复制旧资产。\n不初始化独立的租户管理和菜单管理菜单入口；权限管理中的菜单维护和全部相关组件、接口均保留。\n数据库初始化仅用于包内新实例，禁止用全量初始化覆盖已有库。\nNiFi及其他外部业务连接通过节点配置等对应页面配置。\n'
    (stage/'部署说明.txt').write_text(notes,encoding='utf8')
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with ZipFile(a.output,'w',ZIP_DEFLATED) as archive:
        for path in stage.rglob('*'):
            if path.is_file():archive.write(path,path.relative_to(stage).as_posix(),compress_type=ZIP_STORED if path.suffix in ('.gz','.xz','.jar') else ZIP_DEFLATED)
    with ZipFile(a.output) as archive:assert archive.testzip() is None
    with a.output.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
    (a.output.parent/'SHA256SUMS.txt').write_text(sha+'  '+a.output.name+'\n',encoding='ascii')
    print(json.dumps(report),str(a.output),sha)


if __name__=='__main__':main()
