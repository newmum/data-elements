#!/usr/bin/env python3
"""Package one freshly built Haitong JAR for native Linux and Windows deployment.

Usage: python tools/package-haitong.py --jar <reviewed-clean-build.jar>
Requires PyYAML. Runtime credentials and current config/data directories are never copied.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import io
import json
from pathlib import Path
import re
import tarfile
import xml.etree.ElementTree as ET
import zipfile
import yaml

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'haitong-data-pull'


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def deployment_config() -> dict:
    # Explicit publication defaults: never copy a developer's filled connection
    # template, private configuration, registered business records or secrets.
    return {
        'server': {'address': '0.0.0.0', 'port': 18083},
        'haitong': {'validation': {'enabled': False, 'api-key': 'env:HAITONG_VALIDATION_API_KEY'}},
        'magic-api': {
            'web': '/api/web',
            'resource': {'type': 'database', 'table-name': 'ht_magic_api_file', 'prefix': '/magic-api'},
            'security': {'username': 'admin', 'password': '${HAITONG_MAGIC_EDITOR_PASSWORD}'},
        },
        'db': {'type': 'mysql', 'host': 'REPLACE_WITH_MYSQL_HOST', 'port': 3306,
               'name': 'haitong', 'username': 'REPLACE_WITH_MYSQL_USER',
               'password': 'env:DB_PASSWORD', 'url': '', 'pool-size': 10, 'init-schema': False},
        'relay': {
            'work-dir': './data',
            'registration-registry': {'enabled': False, 'base-url': '', 'shared-key': ''},
            'standalone-registry': {'sync-enabled': True, 'sync-key': 'env:HAITONG_STANDALONE_REGISTRY_SYNC_KEY'},
            'standalone': {'datasources': {}},
            'security': {'api-key': '', 'admin-key': '', 'requests-per-minute': 600,
                         'clients': {'standalone-publisher': {
                             'api-key': 'env:HAITONG_STANDALONE_PUSH_KEY',
                             'channels': ['table-to-ftp'], 'actions': []}}},
            'limits': {'max-message-bytes': 1048576, 'max-file-bytes': 104857600,
                       'max-pending-deliveries': 100000, 'max-pending-files': 1000,
                       'min-free-disk-bytes': 536870912, 'max-work-bytes': 10737418240},
            'ftp': {'enabled': True, 'protocol': 'FTP', 'host': 'REPLACE_WITH_FTP_HOST',
                    'port': 21, 'username': 'REPLACE_WITH_FTP_USER',
                    'password': 'env:HAITONG_FTP_PASSWORD', 'passive': True,
                    'implicit-tls': False, 'encoding': 'UTF-8', 'timeout-seconds': 30,
                    'output-dir': '/haitong-push', 'scan': {'enabled': False}},
            'kafka': {'enabled': False, 'consumer': {'enabled': False}},
            'targets': {'ftp': {'type': 'FTP'}, 'kafka': {'type': 'KAFKA'}},
            'routes': {
                'demo': {'enabled': False}, 'from-kafka': {'enabled': False}, 'from-ftp': {'enabled': False},
                'table-to-ftp': {'enabled': True, 'registration-managed': False, 'targets': ['ftp'],
                                 'file-format': 'JSONL', 'required-fields': ['tableName', 'records'],
                                 'batch-key-field': 'batchId'},
                'table-to-kafka': {'enabled': False, 'registration-managed': False, 'targets': ['kafka'],
                                   'file-format': 'JSONL', 'required-fields': ['tableName', 'records'],
                                   'batch-key-field': 'batchId'},
            },
            'actions': {'inspect': {'enabled': False}},
            'gateway': {'public-base-url': ''},
            'worker': {'enabled': True, 'threads-per-type': 2, 'max-attempts': 10,
                       'poll-ms': 500, 'lease-seconds': 300, 'file-batch-size': 100,
                       'payload-retention-days': 0},
            'files': {'enabled': True, 'single-upload-max-bytes': 20971520,
                      'part-size-bytes': 16777216, 'max-file-bytes': 10737418240,
                      'storage-limit-bytes': 107374182400, 'collections': {}},
        },
        'logging': {'file': {'name': './logs/haitong.log'}, 'level': {'org.ssssssss.magicapi': 'INFO'}},
    }


def config_bytes() -> bytes:
    header = '''# 海通原生部署配置：修改后重启生效。
# 1. 替换全部 REPLACE_WITH_ 主机与账号；密码/密钥可直接填入本机文件，或保留 env: 引用。
# 2. 编辑器密码支持 Spring ${...} 引用；env:NAME 用于数据库、FTP、业务与同步密钥。
# 3. 新空库执行 db/init.sql；已有库禁止重跑完整结构。db.init-schema 保持 false。
# 4. 本服务独立运行，不回调元数据平台；FTP/Kafka 目的地只由本文件确定。
# 5. FTP/SFTP 配置应与元数据平台 ODS 非结构化数据配置一致。
# 6. 附件集合按真实登记表/字段合并 application-files.yml.example 后重启。
# 7. db.url 为空时 MySQL 默认校验证书与主机名；自有 CA 需导入 Java 信任库。
'''
    return (header + yaml.safe_dump(deployment_config(), allow_unicode=True, sort_keys=False)).encode('utf-8')


def instructions(system: str, version: str) -> bytes:
    start = ('Windows：解压到 D:\\apps\\haitong-data-pull，打开命令提示符后执行：\r\n'
             '  cd /d D:\\apps\\haitong-data-pull\r\n  scripts\\run.cmd\r\n'
             '前台运行时保持窗口打开，Ctrl+C 停止。\r\n') if system == 'windows' else (
             'Linux：解压到 /opt/haitong-data-pull 后执行：\n'
             '  cd /opt/haitong-data-pull\n  bash scripts/start-background.sh\n'
             '  bash scripts/status.sh\n  bash scripts/stop-background.sh\n'
             '前台运行：bash scripts/run.sh；长期服务可安装 scripts/install-systemd.sh。\n')
    text = f'''海通数据推送服务 {version} — {system} 原生部署

运行条件：Java 21、可访问的 MySQL 8、已准备的 FTP/FTPS/SFTP；无需 Maven、源码或 Docker。
两种系统使用同一个 JAR，仅启动脚本和压缩格式不同。

一、数据库
新建空的 haitong 数据库后，由 DBA 执行 db/init.sql（15 张表，只有结构）。
已有数据库只升级经审查的差异，不能重跑此初始化文件，也不能覆盖已有业务数据。
Magic API 资源保存于独立 haitong.ht_magic_api_file，首次启动加载 JAR 内置脚本；已有脚本保留。

二、编辑 config/application.yml
填写 db.host / port / name / username / password。
填写 relay.ftp.protocol / host / port / username / password / output-dir；SFTP 使用 22 端口。
模板中的 REPLACE_WITH_ 都必须替换。密码和密钥只保存在部署环境，不随包分发。
以下五项可在 YAML 中直接填写真实值；若保留模板引用，则在启动进程设置同名环境变量：
  DB_PASSWORD                          MySQL 密码
  HAITONG_MAGIC_EDITOR_PASSWORD       Magic API 编辑器密码
  HAITONG_FTP_PASSWORD                文件服务器密码
  HAITONG_STANDALONE_PUSH_KEY         业务 X-Api-Key，至少 24 字符的独立随机值
  HAITONG_STANDALONE_REGISTRY_SYNC_KEY 登记配置同步密钥，至少 24 字符，必须与业务密钥不同
编辑器默认账号 admin，密码由你在部署环境设置，没有随包默认密码。
standalone-publisher 是模板内的静态调用方；平台另行签发的数据源密钥按已同步授权校验。
09.数据推送验证是 Postman 式示例。新环境先替换登记源与返回 ID，按需启用 haitong.validation.enabled，
并设置 haitong.validation.api-key（支持 env:HAITONG_VALIDATION_API_KEY）。示例默认停用，正常业务接口不受影响。
如平台签发接口使用独立发行密钥，在 relay.registration-registry.shared-key 配置相同密钥。
文件传输启用但默认未登记附件集合，需按 config/application-files.yml.example 配置真实集合。
业务数据推送前，先通过节点管理同步登记配置，或维护本地独立登记契约；表字段与权限须齐全。
Kafka 默认停用；启用时同时配置 Kafka 连接、目标 Topic、路由及调用方 channels。

三、启动
{start}
服务默认端口 18083，日志 logs/haitong.log，运行文件 data/。
健康检查：http://服务器IP:18083/actuator/health，成功返回 status=UP。
Magic API 编辑器：http://服务器IP:18083/api/web/index.html。
统一表数据推送：POST http://服务器IP:18083/api/v1/data-push。
单附件入口：POST http://服务器IP:18083/api/v1/file-uploads/single。

四、链路与升级
推送目的地来自本包自己的 YAML。结构化表批次写到 FTP：
  <output-dir>/<datasourceId>/<tableName>/<messageId>.json
  <output-dir>/<datasourceId>/<tableName>/<messageId>.json.done
需要后续接入汇聚库/原始库时，元数据平台 ODS 非结构化数据应配置为同一个 FTP。
升级前停止旧实例，保留并备份已有 config/ 和 data/，替换 JAR 与所需脚本后再启动。
部署包不包含当前环境密码、访问密钥、运行数据库、业务登记镜像、业务文件或历史日志。
完整接口契约见 openapi.yaml；SHA256SUMS.txt 校验包内文件；release.json 记录构建身份。
'''
    if system == 'windows':
        text = text.replace('\r\n','\n').replace('\n','\r\n')
    return text.encode('utf-8-sig')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'logs/haitong-data-pull/releases')
    parser.add_argument('--label', default=datetime.now(timezone(timedelta(hours=8))).strftime('%Y%m%d-%H%M%S'))
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,48}', args.label):
        raise ValueError('Invalid release label')
    output = args.output.resolve()
    if not output.is_relative_to(ROOT / 'logs'):
        raise ValueError('Release artifacts must be written under ignored logs/')
    output.mkdir(parents=True, exist_ok=True)
    pom = ET.parse(APP / 'pom.xml')
    version = pom.findtext('{*}version')
    jar_data = args.jar.read_bytes()
    with zipfile.ZipFile(io.BytesIO(jar_data)) as jar:
        manifest = jar.read('META-INF/MANIFEST.MF').decode()
        if 'Start-Class: com.haitong.HaitongApplication' not in manifest:
            raise ValueError('Not a Haitong executable JAR')
        if 'BOOT-INF/classes/com/haitong/registry/RegistrationSchemaClient.class' in jar.namelist():
            raise ValueError('The JAR still contains the removed platform callback client')
        magic_files = []
        for source in sorted((ROOT / 'magic/haitong').rglob('*')):
            if source.is_file() and source.suffix in ('.ms','.json') and source.name != '.manifest.json':
                relative = source.relative_to(ROOT / 'magic/haitong').as_posix()
                if jar.read('BOOT-INF/classes/magic-api-bootstrap/' + relative) != source.read_bytes():
                    raise ValueError('JAR Magic resource differs from canonical source: ' + relative)
                magic_files.append(relative)
        for driver in ('mysql-connector-j-', 'jsch-', 'commons-net-'):
            if not any(n.startswith('BOOT-INF/lib/' + driver) for n in jar.namelist()):
                raise ValueError('Missing runtime driver: ' + driver)
    common = {
        'target/haitong-data-pull.jar': jar_data,
        'config/application.yml': config_bytes(),
        'config/application-files.yml.example': (APP / 'config/application-files.yml.example').read_bytes(),
        'db/init.sql': (ROOT / 'db/haitong/init.sql').read_bytes(),
        'openapi.yaml': (APP / 'src/main/resources/openapi.yaml').read_bytes(),
        'scripts/upload_file.py': (APP / 'scripts/upload_file.py').read_bytes(),
        'scripts/make_done.py': (APP / 'scripts/make_done.py').read_bytes(),
    }
    artifacts = []
    root_name = 'haitong-data-pull'
    for system in ('linux','windows'):
        files = dict(common)
        scripts = ('run.cmd','sync-registry.ps1') if system == 'windows' else (
            'run.sh','start-background.sh','stop-background.sh','status.sh','install-systemd.sh')
        for script in scripts:
            text = (APP / 'scripts' / script).read_text(encoding='utf-8-sig').replace('\r\n','\n')
            if system == 'windows':
                text = text.replace('\n','\r\n')
            encoding = 'utf-8-sig' if system == 'windows' and script.endswith('.ps1') else 'utf-8'
            files['scripts/' + script] = text.encode(encoding)
        metadata = {'application':'haitong-data-pull','version':version,'build':args.label,
                    'platform':system,'java':21,'port':18083,'jarSha256':sha256(jar_data),
                    'magicResourceCount':len(magic_files),'databaseSchemaSha256':sha256(common['db/init.sql']),
                    'containsRuntimeCredentials':False,'containsBusinessData':False}
        files['release.json'] = (json.dumps(metadata,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
        files['部署说明.txt'] = instructions(system,version)
        files['SHA256SUMS.txt'] = ''.join(f'{sha256(data)}  {name}\n' for name,data in sorted(files.items())).encode('utf-8')
        base = output / f'haitong-data-pull-{version}-{args.label}-{system}'
        if system == 'windows':
            target = Path(str(base) + '.zip')
            with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED) as archive:
                for name,data in sorted(files.items()):
                    archive.writestr(root_name + '/' + name,data)
            with zipfile.ZipFile(target) as archive:
                if archive.testzip() is not None:
                    raise ValueError('ZIP integrity verification failed')
        else:
            target = Path(str(base) + '.tar.gz')
            with tarfile.open(target,'x:gz') as archive:
                for name,data in sorted(files.items()):
                    entry = tarfile.TarInfo(root_name + '/' + name)
                    entry.size=len(data)
                    entry.mode=0o755 if name.endswith('.sh') else 0o644
                    archive.addfile(entry,io.BytesIO(data))
            with tarfile.open(target,'r:gz') as archive:
                if sorted(m.name for m in archive.getmembers()) != sorted(root_name+'/'+n for n in files):
                    raise ValueError('TAR manifest verification failed')
        artifacts.append({'platform':system,'file':str(target),'bytes':target.stat().st_size,
                          'sha256':sha256(target.read_bytes()),'fileCount':len(files),**metadata})
    (output / ('release-' + args.label + '.json')).write_text(json.dumps(artifacts,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(artifacts,ensure_ascii=True))


if __name__ == '__main__':
    main()
