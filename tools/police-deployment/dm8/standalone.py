"""Run the police platform on Linux aarch64 against an existing DM8 instance."""
import argparse
import base64
import fcntl
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import secrets
import socket
import subprocess
import sys
import runtime as rt

ROOT, STATE, RUNTIME, LOGS = rt.ROOT, rt.STATE, rt.RUNTIME, rt.LOGS
SERVICES = ('web','backend','search','redis')


def clean_environment():
    # A separate deployment must not inherit another Spring/Nacos instance's bindings.
    prefixes=('SPRING_','SERVER_','MAGIC_API_','DATA_ELEMENT_','DATA_ELEMENTS_','NACOS_',
              'NIFI_','IDAAS_','ELASTICSEARCH_','ES_')
    java_options={'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'}
    return {key:value for key,value in os.environ.items() if not key.startswith(prefixes) and key not in java_options}


def database_configuration():
    path=ROOT/'config/database.json'
    if not path.exists():
        rt.private(path,(ROOT/'config/database.json.example').read_text(encoding='utf8'))
        raise ValueError('已创建config/database.json，请填写现有达梦地址、两个独立空schema的账号密码后重试')
    path.chmod(0o600)
    data=json.loads(path.read_text(encoding='utf8'))
    if not re.fullmatch(r'jdbc:dm://[^?;\s@]+',str(data.get('jdbcUrl',''))):
        raise ValueError('jdbcUrl须为不含schema参数和账号口令的达梦连接地址')
    for name in ('control','tenant'):
        row=data.get(name) or {}
        for key in ('username','schema','password'):
            if not isinstance(row.get(key),str) or not row[key]:raise ValueError('请填写database.json的'+name+'.'+key)
        if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,127}',row['schema']):raise ValueError('schema名称不合法')
    if data['control']['schema'].lower()==data['tenant']['schema'].lower():
        raise ValueError('控制库和公安业务库必须使用不同的独立schema')
    identity={key:data[key] for key in ('jdbcUrl',)}
    identity.update({name:{key:data[name][key] for key in ('username','schema')} for name in ('control','tenant')})
    return data,hashlib.sha256(json.dumps(identity,sort_keys=True).encode()).hexdigest()


def provision():
    for name,asset,strip in [('java','java21.tar.gz',1),('redis','redis.tar.gz',0),
                             ('caddy','caddy.tar.gz',0),('elasticsearch','elasticsearch.tar.gz',1)]:
        directory=RUNTIME/name
        if not (directory/'.ready').exists():
            directory.mkdir(parents=True,exist_ok=True)
            with (LOGS/'extract.log').open('ab') as log:
                subprocess.run(['tar','-xf',str(ROOT/'vendor'/asset),'-C',str(directory),
                                '--strip-components='+str(strip)],stdout=log,stderr=log,check=True)
            (directory/'.ready').touch()
    # Reject mislabeled vendor files before attempting to execute any native service.
    for path in [RUNTIME/'java/bin/java',RUNTIME/'caddy/caddy',RUNTIME/'redis/usr/bin/valkey-server',
                 RUNTIME/'redis/lib/ld-musl-aarch64.so.1']:
        with path.open('rb') as file:header=file.read(20)
        if header[:6]!=b'\x7fELF\x02\x01' or int.from_bytes(header[18:20],'little')!=183:
            raise ValueError('包内运行文件不是ARM64：'+path.name)


def java_command(action):
    return [str(RUNTIME/'java/bin/java'),'-Dfile.encoding=UTF-8','-cp',str(ROOT/'tools/lib/*'),
            str(ROOT/'tools/Dm8Bootstrap.java'),action]


def database(action,credentials):
    with (LOGS/'dm-initialize.log').open('ab') as log:
        result=subprocess.run(java_command(action),env=dict(clean_environment(),
            POLICE_DM_CONFIG=str(ROOT/'config/database.json'),POLICE_ADMIN_PASSWORD=credentials['admin']),
            stdout=log,stderr=log)
    if result.returncode:raise RuntimeError('达梦'+action+'未通过，请查看logs/dm-initialize.log；不会清空已有库')


def initialize(config,identity,credentials):
    marker=STATE/'initialized.json'
    if marker.exists():
        if json.loads(marker.read_text())['target']!=identity:
            raise ValueError('数据库连接目标已改变，请使用独立部署目录，避免复用其他实例的初始化状态')
        database('check',credentials)
        return
    if (STATE/'initializing.json').exists():
        raise RuntimeError('上次初始化未完成，已保留数据库；请先核查日志和目标schema，禁止对已有库重复执行全量初始化')
    if config.get('createUsers') is True:database('create-users',credentials)
    print('正在检查达梦连接和空schema...',flush=True)
    database('check-empty',credentials)
    rt.private(STATE/'initializing.json',json.dumps({'target':identity}))
    print('首次导入结构和初始化数据，请等待；进度见logs/dm-initialize.log',flush=True)
    database('init',credentials)
    rt.private(marker,json.dumps({'target':identity}))
    (STATE/'initializing.json').unlink()


def redis_ready(port,password):
    with socket.create_connection(('127.0.0.1',port),timeout=2) as connection:
        value=password.encode()
        connection.sendall(b'*2\r\n$4\r\nAUTH\r\n$'+str(len(value)).encode()+b'\r\n'+value+b'\r\n')
        return connection.recv(64).startswith(b'+OK')


def verify(url,credentials):
    password=subprocess.check_output(java_command('sm3'),env=dict(clean_environment(),POLICE_PASSWORD_INPUT=credentials['admin'])).decode().strip()
    api=url+'/prod-api'
    login=rt.request(api+'/portal/sso/login',dict(account='admin',password=password,
                     tenantId='2084109831682699264',appId='1995678661281710081'))
    if login.get('code') not in (0,200):raise RuntimeError('初始管理员登录验证未通过')
    token=login['data']['token']
    for route,body in [('/portal/sso/session',None),('/sym/menu/routes',None),
                       ('/sym/org/tree',{}),('/dst/application/page',{'pageNum':1,'pageSize':20})]:
        result=rt.request(api+route,body,token)
        if result.get('code') not in (0,200):raise RuntimeError('安装验证未通过 '+route)
    rt.private(STATE/'installation-check.json',json.dumps({'checks':['login','session','navigation','organizations','application-search']}))


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('action',choices=['start','stop','status','init-db','check-db','create-users'])
    action=parser.parse_args().action
    if platform.system()!='Linux' or platform.machine() not in ('aarch64','arm64'):
        raise ValueError('此部署包需要麒麟等Linux aarch64/ARM64系统')
    os.umask(0o077)
    os.chdir(ROOT)
    rt.run_as_service_user()
    STATE.mkdir(parents=True,exist_ok=True);LOGS.mkdir(exist_ok=True)
    with (STATE/'launcher.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        if action=='stop':
            for name in SERVICES:rt.stop(name)
            print('平台已停止，外部达梦服务保持运行');return
        if action=='status':
            for name in SERVICES:print(name,'running' if rt.pid(name) else 'stopped')
            print('DM8 external (managed by your DBA)');return
        config,identity=database_configuration()
        print('正在准备ARM运行环境...',flush=True)
        provision()
        secret=STATE/'credentials.json'
        if not secret.exists():rt.private(secret,json.dumps(dict(redis=secrets.token_hex(24),admin=secrets.token_urlsafe(24),
                     transport=base64.b64encode(secrets.token_bytes(32)).decode())))
        credentials=json.loads(secret.read_text())
        if action in ('check-db','create-users'):
            database('check' if action=='check-db' else action,credentials);print('达梦检查/账号准备已完成');return
        initialize(config,identity,credentials)
        if action=='init-db':print('达梦结构和初始化数据已导入');return
        ports=json.loads((ROOT/'config/ports.json').read_text())
        for name in SERVICES+('searchTransport',):
            if (name=='searchTransport' and rt.pid('search')) or rt.pid(name):continue
            with socket.socket() as listener:
                listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
                listener.bind(('127.0.0.1',ports[name]))
        before={name for name in SERVICES if rt.pid(name)}
        try:
            print('正在启动缓存、搜索和平台服务...',flush=True)
            rt.private(STATE/'redis.conf','bind 127.0.0.1\nport '+str(ports['redis'])+'\nrequirepass '+credentials['redis']+'\nappendonly yes\n')
            redis=RUNTIME/'redis'
            rt.spawn('redis',[str(redis/'lib/ld-musl-aarch64.so.1'),'--library-path',str(redis/'lib')+':'+str(redis/'usr/lib'),
                             str(redis/'usr/bin/valkey-server'),str(STATE/'redis.conf')],cwd=STATE,env=clean_environment())
            rt.wait('redis',lambda:redis_ready(ports['redis'],credentials['redis']))
            rt.start_search(ports,clean_environment())
            env=dict(clean_environment(),POLICE_BACKEND_PORT=str(ports['backend']),POLICE_SEARCH_PORT=str(ports['search']),
                POLICE_REDIS_PORT=str(ports['redis']),POLICE_REDIS_PASSWORD=credentials['redis'],
                POLICE_DM_URL=config['jdbcUrl'],POLICE_CONTROL_SCHEMA=config['control']['schema'],
                POLICE_CONTROL_USERNAME=config['control']['username'],POLICE_CONTROL_PASSWORD=config['control']['password'],
                POLICE_TENANT_SCHEMA=config['tenant']['schema'],POLICE_TENANT_USERNAME=config['tenant']['username'],
                POLICE_TENANT_PASSWORD=config['tenant']['password'],IDAAS_TRANSPORT_KEY=credentials['transport'],
                DATA_ELEMENTS_LOG_DIR=str(LOGS/'backend'))
            rt.spawn('backend',[str(RUNTIME/'java/bin/java'),'-Xms256m','-Xmx1g','-Dfile.encoding=UTF-8','-jar',
                     'backend/data-element.jar','--spring.config.location=file:./config/application.yml'],env=env)
            rt.wait('backend',lambda:rt.request('http://127.0.0.1:'+str(ports['backend'])+'/actuator/health')['status']=='UP')
            rt.spawn('web',[str(RUNTIME/'caddy/caddy'),'run','--config','config/Caddyfile','--adapter','caddyfile'],
                     env=dict(clean_environment(),POLICE_WEB_PORT=str(ports['web']),POLICE_WEB_BIND=ports['webBind'],POLICE_BACKEND_PORT=str(ports['backend'])))
            url='http://127.0.0.1:'+str(ports['web'])
            rt.wait('web',lambda:rt.request(url+'/prod-api/actuator/health')['status']=='UP')
            if not (STATE/'installation-check.json').exists():
                verify(url,credentials)
                rt.private(STATE/'admin-login.txt','Username: admin\nPassword: '+credentials['admin']+'\n')
            print('平台已就绪，网页端口 '+str(ports['web'])+'；初始账号见data/local/admin-login.txt')
        except Exception:
            for name in SERVICES:
                if name not in before:rt.stop(name)
            raise


if __name__=='__main__':
    try:main()
    except Exception as error:
        print('操作失败：'+str(error),file=sys.stderr)
        sys.exit(1)
