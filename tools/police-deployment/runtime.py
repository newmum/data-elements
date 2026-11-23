"""Process and local search helpers for native police deployment packages."""
import hashlib
import json
import os
from pathlib import Path
import pwd
import signal
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

def start_search(ports, environment=None):
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
    env = dict(os.environ if environment is None else environment, ES_JAVA_HOME=str(RUNTIME/'java'), ES_PATH_CONF=str(config),
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
