"""Install only the reviewed HaiTong frontend/gateway delta into a police MySQL R3 directory."""
import argparse
from datetime import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from zipfile import ZipFile


def stop_platform(root, package):
    # Use the supplied external-only launcher, never the target's older middleware launcher.
    spec = importlib.util.spec_from_file_location('external_launcher', str(package / 'payload/tools/standalone.py'))
    runtime = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runtime)
    runtime.ROOT = root
    runtime.STATE = root / 'data/local'
    for name in ('web', 'backend'):
        runtime.stop(name)


def prepare_component(root, package, manifest):
    if not manifest.get('componentFiles'):
        return None
    for relative, expected in manifest['componentFiles'].items():
        path = package / relative
        if package.resolve() not in path.resolve().parents or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError('组件更新文件校验失败')
    control = json.loads((root / 'config/external-services.json').read_text(encoding='utf-8-sig')).get('control') or {}
    if not all(isinstance(control.get(key), str) and control[key] for key in ('jdbcUrl', 'username', 'password')):
        raise ValueError('请先在原config/external-services.json填写控制库连接')
    java = root / 'runtime/java/bin/java'
    if not java.is_file():
        raise ValueError('缺少原包已解压的JDK21，请确认目标为已经运行过的完整部署目录')
    work = root / 'logs' / ('haitong-component-update-' + datetime.now().strftime('%Y%m%d%H%M%S%f'))
    work.mkdir(parents=True, exist_ok=True)
    with ZipFile(root / 'backend/data-element.jar') as archive:
        driver = next((n for n in archive.namelist() if n.startswith('BOOT-INF/lib/mysql-connector-') and n.endswith('.jar')), None)
        if driver is None: raise ValueError('原后端包缺少MySQL JDBC驱动')
        (work / 'mysql-driver.jar').write_bytes(archive.read(driver))
    environment = dict(os.environ, CANVAS_CONTROL_URL=control['jdbcUrl'],
                       CANVAS_CONTROL_USER=control['username'], CANVAS_CONTROL_PASSWORD=control['password'])
    for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'):
        environment.pop(key, None)
    context = (java, work, environment)
    run_component(package, context, 'check')
    return context


def run_component(package, context, action):
    java, work, environment = context
    command = [str(java), '-Xmx128m', '-Dfile.encoding=UTF-8', '-cp', str(work / 'mysql-driver.jar'),
               str(package / 'ApplyCanvasComponent.java'), action, str(package / 'component'), str(work / 'before')]
    result = subprocess.run(command, env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90)
    if result.returncode:
        # JDBC messages may contain endpoints. Only known resource conflict messages are passed through.
        detail = result.stderr.decode('utf8', errors='replace')
        if '组件有其他修改' in detail:
            raise RuntimeError('任务配置组件有其他修改，已拒绝覆盖；请先核对生产版本')
        raise RuntimeError('任务配置组件' + action + '失败；请检查控制库连接、读写权限和原JDK21，未输出凭据')
    print(result.stdout.decode('utf8', errors='replace').strip())


def install(root, package):
    root = root.resolve()
    manifest = json.loads((package / 'manifest.json').read_text(encoding='utf8'))
    if not (root / 'backend/data-element.jar').is_file() or not (root / 'www/index.html').is_file():
        raise ValueError('请指定原MySQL R3完整部署根目录，目录中须有backend和www')
    for relative, expected in manifest['expectedOriginals'].items():
        path = root / relative
        if not path.is_file():
            raise ValueError('缺少R3原文件：' + relative)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        accepted = manifest.get('acceptedOriginals', {}).get(relative, [expected])
        if digest not in accepted + [manifest['files'][relative]]:
            raise ValueError('检测到自定义或其他版本文件，未覆盖：' + relative + '；请先核对或合并网关配置')
    for relative, expected in manifest['files'].items():
        path = package / 'payload' / relative
        if (package / 'payload').resolve() not in path.resolve().parents or root not in (root / relative).resolve().parents:
            raise ValueError('补丁路径不合法')
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError('补丁文件校验失败：' + relative)
    component = prepare_component(root, package, manifest)
    stop_platform(root, package)
    backup = root / 'logs' / ('haitong-overlay-backup-' + datetime.now().strftime('%Y%m%d%H%M%S%f'))
    changed = []
    try:
        for relative in manifest['files']:
            target = root / relative
            if target.is_file():
                saved = backup / relative
                saved.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(str(target), str(saved))
            else:
                saved = None
            missing = []
            directory = target.parent
            while not directory.exists():
                missing.append(directory)
                directory = directory.parent
            target.parent.mkdir(parents=True, exist_ok=True)
            for directory in missing:
                directory.chmod(0o755)
            changed.append((target, saved))
            shutil.copyfile(str(package / 'payload' / relative), str(target))
            target.chmod(0o755 if target.suffix == '.sh' else 0o644)
        if component:
            run_component(package, component, 'apply')
    except Exception:
        if component:
            run_component(package, component, 'restore')
        for target, saved in reversed(changed):
            if saved is not None:
                shutil.copyfile(str(saved), str(target))
            elif target.exists():
                target.unlink()
        raise
    print('海通前端和网关增补已完成。请执行bash start.sh启动平台，并强制刷新浏览器。')
    print('保留原后端、外部连接、账号和业务数据；备份目录：' + str(backup))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', type=Path, required=True)
    args = parser.parse_args()
    try:
        import fcntl
        root = args.target.resolve()
        if not (root / 'backend/data-element.jar').is_file():
            raise ValueError('目标不是原完整部署根目录')
        state = root / 'data/local'
        state.mkdir(parents=True, exist_ok=True)
        with (state / 'launcher.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            install(root, Path(__file__).resolve().parent)
    except (OSError, ValueError, RuntimeError) as error:
        print('安装失败：' + str(error), file=sys.stderr)
        sys.exit(1)
