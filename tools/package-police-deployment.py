"""Merge a verified police DM8 base package and its selected repair into a full Linux package."""
import argparse
import hashlib
from io import BytesIO
from pathlib import Path
import shutil
import tarfile
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED, ZIP_STORED


ROOT = Path(__file__).resolve().parents[1]
DEPLOYMENT = """# 源数据管理平台 · 公安租户 · Linux x86_64 / 达梦 DM8

本完整包已合并公安配置、Logo、组织机构初始化数据和接入页面实时状态修复。
包含前端、无 Nacos 后端、JDK 21、Caddy、Valkey、达梦 JDBC 驱动、
控制库与公安租户库的完整初始化结构、低代码及 Magic 资源。
初始化导入公安租户当前7项有效配置、公安品牌全局缺省值、102个有效组织机构，
并将初始演示管理员关联到公安配置中的根机构。

## 环境与首次部署

系统使用 Linux x86_64、glibc 2.17 或更新版本，提供 Bash、GNU tar、flock、curl 和 UTF-8 环境。
达梦 DM8 服务端使用官方介质安装。应用包提供 JDBC 驱动及初始化脚本。
使用独立 DM8 实例，配置 UTF-8、大小写不敏感、MySQL兼容模式 COMPATIBLE_MODE=4，
数据库实例时区使用 UTC，表空间使用 MAIN。

1. 解压本包到固定应用目录。
2. 将 config/runtime.env.example 复制为 config/runtime.env，权限设为0600。
3. 填写无schema参数的 POLICE_DM_URL、控制库及公安库密码、缓存密码、
   新管理员初始密码和运行密钥。配置模板中给出各项变量。
4. 使用服务运行账号在应用目录执行以下命令。

```bash
bash platform.sh create-users
bash platform.sh init-db
bash platform.sh start
```

create-users用于创建baseline与baseline_ga账号，需要SYSDBA密码。
若管理员已创建这两个账号并授予对象创建权限，可直接从init-db开始执行。
init-db要求两个schema均为空。已有部署的数据补齐使用之前提供的更新包入口。
初始化完成后可从私有运行配置移除SYSDBA密码和初始管理员密码。
网页登录用户名为admin，密码使用首次初始化时自己设置的值。

默认网页监听127.0.0.1:18191，后端18188，缓存18179。
远程使用时通过HTTPS网关转发网页，或按runtime.env模板设置POLICE_WEB_BIND。
支持根路径及以斜杠结尾的站点子路径，子路径反向代理应去掉该前缀。
14演示实例现用地址为 https://14.103.233.56/police-dm/ 。

```bash
bash platform.sh status
bash platform.sh stop
```

配置及组织导入记录保存在logs/police-defaults-*/，运行日志也保存在logs/。
配置缓存会在平台启动时刷新，兼容缓存持久化后离线导入的情况。

## NiFi对接

NiFi服务使用单独的配套包部署，并在平台节点管理中登记地址、认证及驱动路径。
新库尚未配置NiFi节点时，接入任务页面显示状态待确认，悬停可查看节点配置说明。
数据库连接、节点凭据、密钥和现场业务数据由实际部署环境提供。

## 校验

在解压目录执行 sha256sum -c package-manifest.sha256 核对包内文件。
下载ZIP的整体摘要在同目录SHA256SUMS.txt。
"""


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--update", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.is_relative_to(ROOT) and not output.is_relative_to(ROOT / "logs"):
        raise ValueError("Deployment artifacts belong outside the repository or in logs/")
    replacements = {
        "backend/data-element.jar": "updates/police-defaults/data-element.jar",
        "platform.sh": "platform.sh",
        "tools/Bootstrap.java": "tools/Bootstrap.java",
        "tools/PoliceDefaults.java": "tools/PoliceDefaults.java",
        "db/police-defaults-config.tsv": "db/police-defaults-config.tsv",
        "db/police-defaults-org.tsv": "db/police-defaults-org.tsv",
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(".zip.partial")
    manifest = {}
    with ZipFile(args.base) as base, ZipFile(args.update) as update:
        for archive in (base, update):
            if len(archive.namelist()) != len(set(archive.namelist())):
                raise ValueError("Duplicate package entries")
        for line in update.read("update-manifest.sha256").decode("ascii").splitlines():
            expected, name = line.split("  ", 1)
            if hashlib.sha256(update.read(name)).hexdigest() != expected:
                raise ValueError("Repair checksum mismatch: " + name)
        with ZipFile(BytesIO(update.read(replacements["backend/data-element.jar"]))) as backend:
            if any("nacos" in name.lower() for name in backend.namelist() if name.startswith("BOOT-INF/lib/")):
                raise ValueError("Expected a no-Nacos backend")
        for archive_name, executable in (("java21.tar.gz", "bin/java"), ("redis.tar.gz", "usr/bin/valkey-server"), ("caddy.tar.gz", "caddy")):
            with tarfile.open(fileobj=BytesIO(base.read("vendor/" + archive_name))) as archive:
                member = next(item for item in archive if item.name == executable or item.name.endswith("/" + executable))
                header = archive.extractfile(member).read(20)
                if header[:6] != b"\x7fELF\x02\x01" or int.from_bytes(header[18:20], "little") != 62:
                    raise ValueError("Expected Linux x86_64 runtime: " + archive_name)
        entries = sorted(set(base.namelist()) | set(replacements))
        with ZipFile(temporary, "x", allowZip64=True) as result:
            for name in entries:
                if name.endswith("/") or name == "部署说明.md":
                    continue
                archive = update if name in replacements else base
                source_name = replacements.get(name, name)
                if "private" in name.lower() or name.endswith("/runtime.env"):
                    raise ValueError("Private runtime material in package: " + name)
                info = ZipInfo(name)
                info.create_system = 3
                info.external_attr = (0o100755 if name.endswith(".sh") else 0o100644) << 16
                info.compress_type = ZIP_STORED if name.endswith((".jar", ".tar.gz")) else ZIP_DEFLATED
                hasher = hashlib.sha256()
                with archive.open(source_name) as source, result.open(info, "w", force_zip64=True) as target:
                    for block in iter(lambda: source.read(1024 * 1024), b""):
                        target.write(block)
                        hasher.update(block)
                manifest[name] = hasher.hexdigest()
            document = DEPLOYMENT.encode("utf8")
            result.writestr("部署说明.md", document, compress_type=ZIP_DEFLATED)
            manifest["部署说明.md"] = hashlib.sha256(document).hexdigest()
            result.writestr("package-manifest.sha256", "".join(value + "  " + name + "\n" for name, value in sorted(manifest.items())))
    with ZipFile(temporary) as archive:
        if archive.testzip() is not None:
            raise ValueError("Corrupt full package")
        for name, expected in manifest.items():
            with archive.open(name) as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                    raise ValueError("Merged content mismatch: " + name)
    if output.exists():
        raise FileExistsError(output)
    temporary.replace(output)
    (output.parent / "SHA256SUMS.txt").write_text(digest(output) + "  " + output.name + "\n", encoding="ascii")
    (output.parent / "部署说明.md").write_text(DEPLOYMENT, encoding="utf8")
    print(f"Verified {len(manifest)} files, {output.stat().st_size} bytes")
    print(output)


if __name__ == "__main__":
    main()
