"""Run the police-only MySQL platform from one extracted directory."""
import argparse
import base64
import fcntl
import hashlib
import json
import os
from pathlib import Path
import platform
import pwd
import secrets
import signal
import socket
import subprocess
import sys
import time
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / 'data/local'
RUNTIME = ROOT / 'runtime'
LOGS = ROOT / 'logs'


def private(path, content):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(content, encoding='utf8')
    temporary.chmod(0o600)
    temporary.replace(path)


def pid(name):
    file = STATE / (name + '.pid')
    try:
        process, started = file.read_text().split()
        fields = Path('/proc/' + process + '/stat').read_text().rsplit(')', 1)[1].split()
        return int(process) if fields[0] != 'Z' and fields[19] == started else None
    except (OSError, ValueError):
        return None


def spawn(name, command, env=None, cwd=ROOT):
    if pid(name):
        return
    with (LOGS / (name + '.log')).open('ab') as log:
        process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log, stderr=log,
                                   stdin=subprocess.DEVNULL, start_new_session=True)
    started = Path('/proc/' + str(process.pid) + '/stat').read_text().rsplit(')', 1)[1].split()[19]
    (STATE / (name + '.pid')).write_text(str(process.pid) + ' ' + started)


def stop(name):
    process = pid(name)
    if process:
        os.kill(process, signal.SIGTERM)
        deadline = time.monotonic() + 60
        while pid(name):
            if time.monotonic() > deadline:
                raise RuntimeError(name + '未完成停止，请查看日志')
            time.sleep(.5)
    
    if (STATE / (name + '.pid')).exists():
        (STATE / (name + '.pid')).unlink()


def wait(name, probe, seconds=180):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline and pid(name):
        try:
            if probe():
                return
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise RuntimeError(name + '启动失败，请查看logs/' + name + '.log')


def request(url, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['token'] = token
    with urlopen(Request(url, data=json.dumps(body).encode() if body is not None else None, headers=headers), timeout=10) as response:
        return json.load(response)


def provision():
    for name, archive, strip in [('java','java21.tar.gz',1),('mysql','mysql.tar.xz',1),
                                 ('mysql-libs','mysql-libs.tar.gz',0),('redis','redis.tar.gz',0),('caddy','caddy.tar.gz',0),
                                 ('elasticsearch','elasticsearch.tar.gz',1)]:
        directory = RUNTIME / name
        if not (directory / '.ready').exists():
            directory.mkdir(parents=True, exist_ok=True)
            subprocess.run(['tar','-xf',str(ROOT/'vendor'/archive),'-C',str(directory),'--strip-components='+str(strip)],check=True)
            (directory / '.ready').touch()


def start_search(ports):
    """Use a private loopback search instance; never reuse another deployment's indices."""
    config = STATE / 'elasticsearch-config'
    config.mkdir(exist_ok=True)
    source = RUNTIME / 'elasticsearch/config'
    for name in ('jvm.options', 'log4j2.properties'):
        if not (config/name).exists():
            (config/name).write_bytes((source/name).read_bytes())
    (config/'jvm.options.d').mkdir(exist_ok=True)
    private(config/'elasticsearch.yml',
            'cluster.name: police-standalone\nnode.name: police-local\n'
            'network.host: 127.0.0.1\nhttp.port: '+str(ports['search'])+'\n'
            'transport.port: '+str(ports['searchTransport'])+'\n'
            'discovery.type: single-node\nxpack.security.enabled: false\nxpack.ml.enabled: false\n'
            'node.store.allow_mmap: false\n'
            'path.data: '+json.dumps(str(STATE/'elasticsearch'))+'\n'
            'path.logs: '+json.dumps(str(LOGS/'elasticsearch'))+'\n')
    env = dict(os.environ, ES_JAVA_HOME=str(RUNTIME/'java'), ES_PATH_CONF=str(config),
               ES_JAVA_OPTS='-Xms512m -Xmx512m')
    spawn('search',[str(RUNTIME/'elasticsearch/bin/elasticsearch')],env=env)
    wait('search',lambda:request('http://127.0.0.1:'+str(ports['search'])+
                                '/_cluster/health?wait_for_status=yellow&timeout=5s')['status'] in ('green','yellow'))


def run_as_service_user():
    # ES refuses root. A root invocation creates a user scoped to this extracted directory.
    if os.getuid() != 0:
        return
    if ROOT in (Path('/'),Path('/opt'),Path('/home'),Path('/usr')) or not (ROOT/'backend/data-element.jar').is_file():
        raise RuntimeError('请在独立的完整部署目录运行')
    name = 'police_' + hashlib.sha256(str(ROOT).encode()).hexdigest()[:10]
    try:
        account = pwd.getpwnam(name)
    except KeyError:
        subprocess.run(['useradd','--system','--home-dir',str(ROOT),'--shell','/sbin/nologin',name],check=True)
        account = pwd.getpwnam(name)
    subprocess.run(['chown','-R',str(account.pw_uid)+':'+str(account.pw_gid),str(ROOT)],check=True)
    os.setgroups([])
    os.setgid(account.pw_gid)
    os.setuid(account.pw_uid)


def mysql_env():
    return dict(os.environ, LD_LIBRARY_PATH=str(RUNTIME/'mysql-libs'))


def start_mysql(ports):
    data = STATE / 'mysql'
    command = [str(RUNTIME/'mysql/bin/mysqld'),'--no-defaults','--basedir='+str(RUNTIME/'mysql'),
               '--datadir='+str(data),'--user='+pwd.getpwuid(os.getuid()).pw_name,
               '--plugin-dir='+str(RUNTIME/'mysql/lib/plugin'),'--lc-messages-dir='+str(RUNTIME/'mysql/share')]
    if not (data/'mysql').exists():
        data.mkdir(exist_ok=True)
        with (LOGS/'mysql-initialize.log').open('ab') as log:
            subprocess.run(command+['--initialize-insecure'],env=mysql_env(),stdout=log,stderr=log,check=True)
    spawn('mysql',command+['--mysqlx=OFF','--local-infile=OFF','--bind-address=127.0.0.1',
          '--port='+str(ports['mysql']),'--socket='+str(STATE/'mysql.sock'),
          '--max-allowed-packet=128M','--default-time-zone=+00:00'],env=mysql_env())
    wait('mysql',lambda:subprocess.run([str(RUNTIME/'mysql/bin/mysqladmin'),'--no-defaults','--protocol=socket',
         '--socket='+str(STATE/'mysql.sock'),'ping'],env=mysql_env(),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0)


def initialize(credentials):
    if (STATE/'initialized').exists():
        return
    defaults = STATE/'mysql-root.cnf'
    if not defaults.exists():
        private(defaults,'[client]\nuser=root\n')
    cli = [str(RUNTIME/'mysql/bin/mysql'),'--defaults-file='+str(defaults),'--protocol=socket',
           '--socket='+str(STATE/'mysql.sock'),'--default-character-set=utf8mb4','--max-allowed-packet=128M']
    with (LOGS/'database-import.log').open('ab') as log:
        for filename in ('01-control.sql','02-police.sql','03-resources.sql','04-police-defaults.sql'):
            marker=STATE/('import-'+filename+'.sha256')
            fingerprint=hashlib.sha256((ROOT/'db'/filename).read_bytes()).hexdigest()
            if marker.exists():
                if marker.read_text()!=fingerprint:raise RuntimeError('已导入脚本发生变化 '+filename)
                continue
            with (ROOT/'db'/filename).open('rb') as sql:
                subprocess.run(cli,stdin=sql,env=mysql_env(),stdout=log,stderr=log,check=True)
            private(marker,fingerprint)
        subprocess.run([sys.executable,str(ROOT/'db/init-account.py'),'--mysql',cli[0],
                       '--socket',str(STATE/'mysql.sock'),'--user','root','--defaults-extra-file',str(defaults)],
                       env=dict(mysql_env(),HAITONG_ADMIN_PASSWORD=credentials['admin']),stdout=log,stderr=log,check=True)
        sql = ("CREATE USER 'police'@'127.0.0.1' IDENTIFIED BY '"+credentials['mysql']+"';\n"
               "GRANT ALL ON baseline.* TO 'police'@'127.0.0.1';\nGRANT ALL ON baseline_ga.* TO 'police'@'127.0.0.1';\n"
               "ALTER USER 'root'@'localhost' IDENTIFIED BY '"+credentials['root']+"';\n")
        subprocess.run(cli,input=sql.encode(),env=mysql_env(),stdout=log,stderr=log,check=True)
    private(defaults,'[client]\nuser=root\npassword='+credentials['root']+'\n')
    (STATE/'initialized').touch()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action',choices=['start','stop','status','init-db'])
    action = parser.parse_args().action
    if platform.system()!='Linux' or platform.machine()!='x86_64':
        raise RuntimeError('需要Linux x86_64')
    run_as_service_user()
    os.umask(0o077)
    os.chdir(ROOT)
    STATE.mkdir(parents=True,exist_ok=True)
    LOGS.mkdir(exist_ok=True)
    with (STATE/'launcher.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        if action=='stop':
            for name in ('web','backend','search','redis','mysql'):stop(name)
            print('平台已停止')
            return
        if action=='status':
            for name in ('web','backend','search','redis','mysql'):print(name, 'running' if pid(name) else 'stopped')
            return
        ports=json.loads((ROOT/'config/ports.json').read_text())
        for name in ('mysql','redis','backend','web','search','searchTransport'):
            if name=='searchTransport' and pid('search'):
                continue
            if not pid(name):
                with socket.socket() as listener:
                    listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
                    listener.bind(('127.0.0.1',ports[name]))
        provision()
        file=STATE/'credentials.json'
        if not file.exists():
            private(file,json.dumps(dict({key:secrets.token_hex(24) for key in ('root','mysql','redis','admin')},
                  transport=base64.b64encode(secrets.token_bytes(32)).decode())))
        credentials=json.loads(file.read_text())
        before={name for name in ('web','backend','search','redis','mysql') if pid(name)}
        try:
            start_mysql(ports)
            initialize(credentials)
            if action=='init-db':
                stop('mysql')
                print('数据库初始化完成')
                return
            private(STATE/'redis.conf','bind 127.0.0.1\nport '+str(ports['redis'])+'\nrequirepass '+credentials['redis']+'\nappendonly yes\n')
            redis=RUNTIME/'redis'
            spawn('redis',[str(redis/'lib/ld-musl-x86_64.so.1'),'--library-path',str(redis/'lib')+':'+str(redis/'usr/lib'),
                  str(redis/'usr/bin/valkey-server'),str(STATE/'redis.conf')],cwd=STATE)
            start_search(ports)
            env=dict(os.environ,POLICE_MYSQL_PORT=str(ports['mysql']),POLICE_BACKEND_PORT=str(ports['backend']),
                     POLICE_SEARCH_PORT=str(ports['search']),
                     POLICE_REDIS_PORT=str(ports['redis']),POLICE_REDIS_PASSWORD=credentials['redis'],
                     POLICE_DB_PASSWORD=credentials['mysql'],IDAAS_TRANSPORT_KEY=credentials['transport'],
                     DATA_ELEMENTS_LOG_DIR=str(LOGS/'backend'))
            spawn('backend',[str(RUNTIME/'java/bin/java'),'-Xms256m','-Xmx1g','-Dfile.encoding=UTF-8','-jar',
                  'backend/data-element.jar','--spring.config.location=file:./config/application.yml'],env=env)
            wait('backend',lambda:request('http://127.0.0.1:'+str(ports['backend'])+'/actuator/health')['status']=='UP')
            spawn('web',[str(RUNTIME/'caddy/caddy'),'run','--config','config/Caddyfile','--adapter','caddyfile'],
                  env=dict(os.environ,POLICE_WEB_PORT=str(ports['web']),POLICE_WEB_BIND=ports['webBind'],POLICE_BACKEND_PORT=str(ports['backend'])))
            url='http://127.0.0.1:'+str(ports['web'])
            wait('web',lambda:request(url+'/prod-api/actuator/health')['status']=='UP')
            if not (STATE/'first-login-verified').exists():
                verify(url,credentials)
                private(STATE/'admin-login.txt','Username: admin\nPassword: '+credentials['admin']+'\n')
                (STATE/'first-login-verified').touch()
            print('平台已就绪，网页端口 '+str(ports['web'])+'，初始账号见data/local/admin-login.txt')
        except Exception:
            for name in ('web','backend','search','redis','mysql'):
                if name not in before:stop(name)
            raise


def verify(url, credentials):
    """Fail initial startup if navigation, tenant initialization or search is incomplete."""
    api=url+'/prod-api'
    login=request(api+'/portal/sso/login',{'account':'admin','password':subprocess.check_output(
        [str(RUNTIME/'java/bin/java'),'-cp',str(ROOT/'tools/lib/*'),str(ROOT/'db/PasswordCodec.java'),'sm3'],
        env=dict(os.environ,POLICE_PASSWORD_INPUT=credentials['admin'])).decode().strip(),
        'tenantId':'2084109831682699264','appId':'1995678661281710081'})
    if login.get('code') not in (0,200):raise RuntimeError('管理员登录验证失败')
    data=login.get('data') or {}
    token=data.get('token') if isinstance(data,dict) else data
    if not token:raise RuntimeError('管理员会话未建立')
    for route,body in [('/portal/sso/session',None),('/sym/menu/routes',None),
                       ('/sym/org/tree',{}),('/dst/application/page',{'pageNum':1,'pageSize':20})]:
        result=request(api+route,body,token)
        if result.get('code') not in (0,200):raise RuntimeError('初始化验证失败 '+route)
    # Credentials/tokens and response bodies are intentionally excluded from the report.
    private(STATE/'installation-check.json',json.dumps({'checkedAt':time.time(),
        'checks':['login','session','navigation','organizations','application-search']}))


if __name__=='__main__':
    try:main()
    except Exception as error:
        print('操作失败 '+str(error),file=sys.stderr)
        sys.exit(1)
