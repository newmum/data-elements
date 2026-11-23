"""External-services launcher: manage this directory's backend and web gateway only."""
import argparse
import base64
import fcntl
import hashlib
import json
import os
from pathlib import Path
import platform
import pwd
import re
import signal
import socket
import subprocess
import sys
import time
from urllib.parse import parse_qsl, urlsplit
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / 'data/local'
LOGS = ROOT / 'logs'
RUNTIME = ROOT / 'runtime'
SERVICES = ('web', 'backend')


def pid(name):
    # Read the legacy PID format, including process start time to reject reused PIDs.
    try:
        process, started = (STATE / (name + '.pid')).read_text().split()
        fields = Path('/proc/' + process + '/stat').read_text().rsplit(')', 1)[1].split()
        return int(process) if fields[0] != 'Z' and fields[19] == started else None
    except (OSError, ValueError, IndexError):
        return None


def stop(name):
    if name not in SERVICES:
        raise ValueError('启动器只管理web和backend')
    process = pid(name)
    if process:
        os.kill(process, signal.SIGTERM)
        deadline = time.monotonic() + 60
        while pid(name):
            if time.monotonic() > deadline:
                raise RuntimeError(name + '未完成停止，请查看日志')
            time.sleep(.5)
    path = STATE / (name + '.pid')
    if path.exists():
        path.unlink()


def spawn(name, command, env):
    if name not in SERVICES:
        raise ValueError('启动器只管理web和backend')
    with (LOGS / (name + '.log')).open('ab') as log:
        process = subprocess.Popen(command, cwd=str(ROOT), env=env, stdout=log, stderr=log,
                                   stdin=subprocess.DEVNULL, start_new_session=True)
    started = Path('/proc/' + str(process.pid) + '/stat').read_text().rsplit(')', 1)[1].split()[19]
    (STATE / (name + '.pid')).write_text(str(process.pid) + ' ' + started)


def wait(name, port, path, seconds=180):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline and pid(name):
        try:
            with urlopen('http://127.0.0.1:' + str(port) + path, timeout=5) as response:
                if json.load(response).get('status') == 'UP':
                    return
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise RuntimeError(name + '启动检查失败，请查看logs/' + name + '.log')


def service_user():
    # Reuse the original deployment account; never create users or recursively chown data.
    if os.getuid() != 0:
        return
    name = 'police_' + hashlib.sha256(str(ROOT).encode()).hexdigest()[:10]
    try:
        account = pwd.getpwnam(name)
    except KeyError:
        return
    for path in (STATE, LOGS, ROOT / 'config/external-services.json'):
        if path.exists():
            os.chown(str(path), account.pw_uid, account.pw_gid)
    os.setgroups([])
    os.setgid(account.pw_gid)
    os.setuid(account.pw_uid)


def clean_environment():
    prefixes = ('POLICE_', 'SPRING_', 'SERVER_', 'MAGIC_API_', 'DATA_ELEMENT_',
                'DATA_ELEMENTS_', 'NACOS_', 'NIFI_', 'IDAAS_', 'ELASTICSEARCH_', 'ES_')
    options = {'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'}
    return {k: v for k, v in os.environ.items() if not k.startswith(prefixes) and k not in options}


def required(data, key, label):
    value = data.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError('请填写config/external-services.json的' + label + '.' + key)
    return value


def integer(value, low, high, label):
    if type(value) is not int or not low <= value <= high:
        raise ValueError(label + '必须是有效整数')
    return value


def configuration():
    file = ROOT / 'config/external-services.json'
    if not file.exists():
        raise ValueError('请复制config/external-services.json.example为config/external-services.json并填写外部服务连接')
    file.chmod(0o600)
    try:
        data = json.loads(file.read_text(encoding='utf-8-sig'))
    except (ValueError, OSError):
        raise ValueError('external-services.json无法读取或JSON格式错误') from None
    if not isinstance(data, dict):
        raise ValueError('external-services.json顶层必须是JSON对象')
    endpoints = []
    identities = []
    for name in ('control', 'tenant'):
        row = data.get(name)
        if not isinstance(row, dict):
            raise ValueError('请填写' + name + '数据库连接')
        url = required(row, 'jdbcUrl', name)
        required(row, 'username', name)
        required(row, 'password', name)
        try:
            uri = urlsplit(url[5:]) if url.startswith('jdbc:mysql://') else None
            if (uri is None or not uri.hostname or not uri.path.strip('/') or uri.username or uri.password
                    or uri.fragment or any(k.lower() in ('user', 'username', 'password', 'passwd')
                                           for k, _ in parse_qsl(uri.query))):
                raise ValueError()
            port = uri.port or 3306
        except ValueError:
            raise ValueError(name + '.jdbcUrl须为不含账号口令的jdbc:mysql://主机:端口/库名连接') from None
        endpoints.append((name, uri.hostname, port))
        identities.append((uri.hostname.lower(), port, uri.path.lower()))
    if identities[0] == identities[1]:
        raise ValueError('control和tenant须连接不同的控制库和公安业务库')
    redis = data.get('redis')
    if not isinstance(redis, dict):
        raise ValueError('请填写redis连接')
    host = required(redis, 'host', 'redis')
    if '/' in host or '@' in host or host.strip() != host:
        raise ValueError('redis.host只填写主机名或IP')
    port = integer(redis.get('port'), 1, 65535, 'redis.port')
    integer(redis.get('database'), 0, 2147483647, 'redis.database')
    for key in ('username', 'password'):
        if not isinstance(redis.get(key), str):
            raise ValueError('redis.' + key + '须为字符串，无认证时填空字符串')
    if redis['username']:
        raise ValueError('R3的Redis客户端仅支持默认用户的密码认证；redis.username须留空，命名ACL用户需要升级后端')
    endpoints.append(('redis', host, port))
    search = data.get('elasticsearch')
    if not isinstance(search, dict) or not isinstance(search.get('hosts'), list) or not search['hosts']:
        raise ValueError('请填写elasticsearch.hosts，例如["es.example:9200"]')
    for host in search['hosts']:
        # The R3 Java custom client supports HTTP host:port, not HTTPS or IPv6.
        if not isinstance(host, str) or not re.fullmatch(r'[A-Za-z0-9_.-]+:[0-9]{1,5}', host):
            raise ValueError('R3的elasticsearch.hosts只支持HTTP主机:端口，不带协议、路径或账号；HTTPS需要升级后端')
        address, port = host.rsplit(':', 1)
        endpoints.append(('elasticsearch', address, integer(int(port), 1, 65535, 'ES端口')))
    for key in ('username', 'password'):
        if not isinstance(search.get(key), str):
            raise ValueError('elasticsearch.' + key + '须为字符串，无认证时填空字符串')
    if bool(search['username']) != bool(search['password']):
        raise ValueError('ES认证账号与密码须同时填写，或同时留空')
    return data, endpoints


def transport_key(data):
    identity = data.get('identity') or {}
    if not isinstance(identity, dict):
        raise ValueError('identity须为JSON对象')
    key = identity.get('transportKey')
    if not key:
        try:
            key = json.loads((STATE / 'credentials.json').read_text(encoding='utf8'))['transport']
        except (OSError, ValueError, KeyError):
            raise ValueError('缺少原IDAAS密钥：保留data/local/credentials.json，或在identity.transportKey填写原值；不会生成新密钥') from None
    try:
        if len(base64.b64decode(key, validate=True)) != 32:
            raise ValueError()
    except (ValueError, TypeError):
        raise ValueError('原IDAAS transportKey须为32字节密钥的Base64编码') from None
    return key


def environment(data, ports):
    env = clean_environment()
    env.update(POLICE_BACKEND_PORT=str(ports['backend']), POLICE_WEB_PORT=str(ports['web']),
               POLICE_WEB_BIND=ports.get('webBind', '0.0.0.0'),
               IDAAS_TRANSPORT_KEY=transport_key(data), DATA_ELEMENTS_LOG_DIR=str(LOGS / 'backend'))
    # Resolve the R3 YAML's historical placeholders while the properties below select
    # the actual external hosts, schemas and separate credentials at higher priority.
    env.update(POLICE_MYSQL_PORT=str(urlsplit(data['control']['jdbcUrl'][5:]).port or 3306),
               POLICE_REDIS_PORT=str(data['redis']['port']),
               POLICE_SEARCH_PORT=search_port(data),
               POLICE_DB_PASSWORD=data['control']['password'],
               POLICE_REDIS_PASSWORD=data['redis']['password'])
    properties = {}
    for name, prefix in [('control', 'spring.datasource'),
                         ('tenant', 'data-element.tenant.database.data-sources.police')]:
        for key, prop in [('jdbcUrl', 'url'), ('username', 'username'), ('password', 'password')]:
            properties[prefix + '.' + prop] = data[name][key]
    for key in ('host', 'port', 'database', 'username', 'password'):
        properties['spring.redis.' + key] = data['redis'][key]
    for key in ('cluster.nodes', 'sentinel.master', 'sentinel.nodes', 'sentinel.password'):
        properties['spring.redis.' + key] = ''
    search = data['elasticsearch']
    properties.update({'elasticsearch.host': ','.join(search['hosts']),
                       'elasticsearch.auth': bool(search['username']),
                       'elasticsearch.username': search['username'],
                       'elasticsearch.password': search['password'],
                       'spring.elasticsearch.uris': ['http://' + host for host in search['hosts']],
                       'spring.elasticsearch.username': search['username'],
                       'spring.elasticsearch.password': search['password']})
    for key in ('config.enabled', 'discovery.enabled', 'discovery.register-enabled', 'config.import-check.enabled'):
        properties['spring.cloud.nacos.' + key] = False
    env['SPRING_APPLICATION_JSON'] = json.dumps(properties, ensure_ascii=False)
    return env


def search_port(data):
    return data['elasticsearch']['hosts'][0].rsplit(':', 1)[1]


def check_connections(endpoints):
    for name, host, port in endpoints:
        try:
            with socket.create_connection((host, port), timeout=5):
                pass
        except OSError:
            # Do not echo config values or exception strings containing credentials.
            raise RuntimeError(name + '连接不可达，请检查外部服务地址、端口和防火墙') from None


def provision():
    # Only Java and the static web gateway belong to the application package.
    for name, asset, strip, executable in [('java', 'java21.tar.gz', 1, 'bin/java'),
                                          ('caddy', 'caddy.tar.gz', 0, 'caddy')]:
        folder = RUNTIME / name
        if not (folder / executable).is_file():
            folder.mkdir(parents=True, exist_ok=True)
            with (LOGS / 'extract.log').open('ab') as log:
                subprocess.run(['tar', '-xf', str(ROOT / 'vendor' / asset), '-C', str(folder),
                                '--strip-components=' + str(strip)], stdout=log, stderr=log, check=True)
        with (folder / executable).open('rb') as file:
            header = file.read(20)
        if header[:6] != b'\x7fELF\x02\x01' or int.from_bytes(header[18:20], 'little') != 62:
            raise RuntimeError('Java或网页网关不是Linux x86_64运行文件')


def start(data, ports):
    before = {name for name in SERVICES if pid(name)}
    if before:
        raise RuntimeError('平台进程仍在运行，请先用新版bash stop.sh停止web/backend，再启动以应用外部连接配置')
    env = environment(data, ports)
    for name in ('backend', 'web'):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                listener.bind(('127.0.0.1' if name == 'backend' else ports.get('webBind', '0.0.0.0'), ports[name]))
            except OSError:
                raise RuntimeError(name + '监听端口不可用，请检查config/ports.json和端口占用') from None
    provision()
    try:
        spawn('backend', [str(RUNTIME / 'java/bin/java'), '-Xms256m', '-Xmx1g', '-Dfile.encoding=UTF-8',
                         '-jar', 'backend/data-element.jar',
                         '--spring.config.location=file:./config/application.yml'], env)
        wait('backend', ports['backend'], '/actuator/health')
        spawn('web', [str(RUNTIME / 'caddy/caddy'), 'run', '--config', 'config/Caddyfile',
                      '--adapter', 'caddyfile'], clean_web_environment(ports))
        wait('web', ports['web'], '/prod-api/actuator/health')
    except Exception:
        for name in SERVICES:
            if name not in before:
                stop(name)
        raise
    print('平台已启动，网页端口 ' + str(ports['web']) + '；数据库、缓存、ES均使用外部服务')


def clean_web_environment(ports):
    env = clean_environment()
    env.update(POLICE_BACKEND_PORT=str(ports['backend']), POLICE_WEB_PORT=str(ports['web']),
               POLICE_WEB_BIND=ports.get('webBind', '0.0.0.0'))
    return env


def main():
    parser = argparse.ArgumentParser(description='只管理平台web/backend；外部服务由部署者维护')
    parser.add_argument('action', choices=['start', 'stop', 'status', 'check'])
    action = parser.parse_args().action
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise RuntimeError('此补丁适用于Linux x86_64的公安MySQL R3部署目录')
    if not (ROOT / 'backend/data-element.jar').is_file():
        raise RuntimeError('请将补丁覆盖到原完整部署目录，不能单独运行补丁目录')
    os.umask(0o077)
    os.chdir(str(ROOT))
    STATE.mkdir(parents=True, exist_ok=True)
    LOGS.mkdir(exist_ok=True)
    service_user()
    with (STATE / 'launcher.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if action == 'stop':
            for name in SERVICES:
                stop(name)
            print('平台web/backend已停止；未操作MySQL、Redis或ES')
            return
        if action == 'status':
            for name in SERVICES:
                print(name, 'running' if pid(name) else 'stopped')
            return
        data, endpoints = configuration()
        ports = json.loads((ROOT / 'config/ports.json').read_text(encoding='utf-8-sig'))
        for name in ('web', 'backend'):
            integer(ports.get(name), 1, 65535, name + '端口')
        if ports['web'] == ports['backend']:
            raise ValueError('web和backend端口不能相同')
        transport_key(data)
        check_connections(endpoints)
        if action == 'check':
            print('配置格式、原登录密钥和外部端口连通检查通过；认证及已有数据由平台启动后确认，未执行SQL或数据导入')
            return
        start(data, ports)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Do not print JSON values, JDBC URLs, process environments or traceback locals.
        message = str(error) if isinstance(error, (ValueError, RuntimeError)) else '操作失败，请检查文件权限、配置和logs目录'
        print(message, file=sys.stderr)
        sys.exit(1)
