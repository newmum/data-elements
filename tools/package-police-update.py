"""Package the police-only DM8 deployment defaults and selected backend repair."""
import argparse
import hashlib
from pathlib import Path
import zipfile


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/police-deployment"
INSTRUCTIONS = """源数据管理平台 公安租户 DM8 / Linux x86_64 更新包

适用原包 source-management-police-dm8-linux-x86_64.zip。
包含公安门户公开品牌配置、组织根配置、资源数据范围配置、102个有效组织机构，
以及未配置默认NiFi节点时实时状态接口的修复。

在已有部署上更新
1. 将本ZIP上传到服务器。进入旧部署包解压目录，即有platform.sh的目录。
2. 先将原platform.sh和tools/Bootstrap.java备份到部署目录的logs中。
3. 将本ZIP直接解压到该目录，允许覆盖同名文件。
   示例 unzip -o /上传目录/source-management-police-dm-update.zip -d .
4. 使用原部署目录的服务运行账号执行以下命令。
   bash apply-update.sh --plan
   bash apply-update.sh --apply
5. 更新后重新登录网页。

后端JAR先放在updates/police-defaults/，应用脚本在停止旧进程后替换正式JAR。
脚本将旧JAR保存在logs/police-update-*/，新后端启动失败时自动恢复旧JAR并重新启动。
补齐数据按租户和自然键读取，仅新增缺少的配置及组织。
固定初始演示管理员的占位组织关联会更新为公安配置中的根机构。
导入记录位于logs/police-defaults-*/，重复执行plan应为零新增。
配置缓存会在写入时和平台启动时刷新。

尚未初始化的新部署
先解压原包，再覆盖本更新包，然后按原包说明配置私有runtime.env，执行
   bash platform.sh create-users
   bash platform.sh init-db
   bash platform.sh start
init-db将同时导入公安配置、组织机构并安装修复后的后端。
已有库请使用apply-update.sh的补齐入口，完整结构初始化要求空schema。

NiFi需要在目标环境的节点管理中配置并启用对应节点。
初始化提供正常浏览页面所需的配置和组织信息，实际接入任务仍依赖有效的NiFi连接。
目标环境数据库、缓存、NiFi、SSO等账号及密钥继续使用自己的受限运行配置。

文件清单和校验
updates/police-defaults/data-element.jar  修复后的后端
platform.sh                             启停及首次初始化入口
apply-update.sh                         已有部署的计划、补齐及换包入口
tools/Bootstrap.java                    原结构及资源初始化入口
tools/PoliceDefaults.java               公安配置及组织导入器
db/police-defaults-config.tsv            公安配置与全局公安品牌缺省值
db/police-defaults-org.tsv               公安有效组织机构层级
update-manifest.sha256                  上述文件的SHA-256校验清单
"""


def digest_file(path):
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(ROOT) and not args.output.resolve().is_relative_to(ROOT / "logs"):
        raise ValueError("Keep deployment artifacts in logs/ or outside the repository")
    sources = {
        "updates/police-defaults/data-element.jar": args.backend,
        "platform.sh": SOURCE / "platform.sh",
        "apply-update.sh": SOURCE / "apply-update.sh",
        "tools/Bootstrap.java": SOURCE / "Bootstrap.java",
        "tools/PoliceDefaults.java": SOURCE / "PoliceDefaults.java",
        "db/police-defaults-config.tsv": SOURCE / "db/police-defaults-config.tsv",
        "db/police-defaults-org.tsv": SOURCE / "db/police-defaults-org.tsv",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, path in sources.items():
            archive.write(path, name)
        archive.writestr("更新说明.txt", INSTRUCTIONS)
        manifest = "".join(digest_file(path) + "  " + name + "\n" for name, path in sources.items())
        archive.writestr("update-manifest.sha256", manifest)
    with zipfile.ZipFile(args.output) as archive:
        assert archive.testzip() is None
        for name, path in sources.items():
            assert hashlib.sha256(archive.read(name)).hexdigest() == digest_file(path), name
    checksum = args.output.with_suffix(".zip.sha256")
    checksum.write_text(digest_file(args.output) + "  " + args.output.name + "\n", encoding="ascii")
    print(f"Verified {len(sources)} payload files, {args.output.stat().st_size} bytes")
    print(args.output)


if __name__ == "__main__":
    main()
