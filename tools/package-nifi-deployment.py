"""Package NiFi 2.9.0 with the complete local vendor NAR set, mapping extension and JDBC drivers."""
import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path
import re
import tarfile
from zipfile import ZipFile, ZipInfo, ZIP_STORED, ZIP_DEFLATED


ROOT = Path(__file__).resolve().parents[1]
TEMPLATES = ROOT / "tools/nifi-deployment"
REQUIRED = {
    "datadevs-avro-nar", "datadevs-database-nar", "datadevs-encrypt-nar", "datadevs-record-nar",
    "datadevs-record-redis-service-nar", "datadevs-record-service-api-nar",
    "linewell-hadoop-libraries-nar", "linewell-hadoop-nar", "haitong-mapping-nar",
}
SERVICES = {
    "META-INF/services/org.apache.nifi.processor.Processor": "processors",
    "META-INF/services/org.apache.nifi.controller.ControllerService": "controllerServices",
}
DEPLOYMENT = """# NiFi 2.9.0 / Linux x86_64 完整配套包

本包包含Apache NiFi 2.9.0完整发行文件、9个自定义及父依赖NAR、JDK 21和JDBC驱动。
自定义组件覆盖本地保存的公司节点清单中的全部组件类型，字段治理扩展使用1.1.30。
组件名称、版本、父依赖及摘要见extensions.json，驱动版本和摘要见drivers/manifest.json。
交付使用本机保存的厂商原件，公司10.231.176.56节点本次网页与SSH均连接超时。

## 启动与停止

使用Linux x86_64，提供Bash、tar、sha256sum和常用coreutils，建议至少提供2GB可用内存。
由专用服务账号解压到固定目录，进入解压目录后执行以下命令。

```bash
bash nifi.sh start
bash nifi.sh status
bash nifi.sh stop
```

start自动校验组件和驱动、解开同目录JDK并生成绝对路径驱动映射。
首次启动需要加载NAR，请查看nifi-2.9.0/logs/nifi-app.log确认初始化完成。
默认沿用官方HTTPS单用户模式，地址为 https://localhost:8443/nifi/ 。
初始用户名和随机密码由NiFi生成，可在该服务器的nifi-app.log查询。
修改监听地址、端口、代理地址及证书时编辑nifi-2.9.0/conf/nifi.properties。
跨机器访问需配置实际HTTPS监听地址及证书，并放通相应端口。

## 对接源数据管理平台

在公安租户的节点管理中登记NiFi地址、API认证、根流程组及驱动映射。
后端使用可从平台服务器访问的NiFi根地址，例如 https://nifi.example.internal:8443 。
驱动映射在config/jdbc-driver-locations.conf，路径属于NiFi所在服务器。
MySQL、MariaDB、Oracle、OceanBase、PostgreSQL、SQL Server、DB2、金仓、达梦、
Vertica、GBase、ClickHouse、Apache Hive及华为MRS驱动随包提供。
GaussDB同时提供gaussdbjdbc.jar、opengaussjdbc.jar、openGauss独立驱动和DWS兼容驱动。
华为MRS的现场站点配置、Kerberos认证及业务连接信息由实际环境配置。

## 自定义组件

extensions/中已放入以下9个NAR，NiFi启动时自动加载。

- haitong-mapping-nar-1.1.30.nar
- datadevs-database-nar-2.9.0.nar
- datadevs-record-nar-2.9.0.nar
- datadevs-record-service-api-nar-2.9.0.nar
- datadevs-record-redis-service-nar-2.9.0.nar
- datadevs-avro-nar-2.9.0.nar
- datadevs-encrypt-nar-2.9.0.nar
- linewell-hadoop-libraries-nar-2.9.0.nar
- linewell-hadoop-nar-2.9.0.nar

包括GenerateDateTableFetch、LinewellPutDatabaseRecord、HaitongTransformRecord、
JdbcHaitongDictionaryService、RedisCodeTypeService、PutHwHDFS、PutHw2HDFS及记录处理组件。
optional/hadoop/保留厂商提供的JDK兼容agent，当前随包JDK21使用默认启动配置。

## 校验与数据位置

sha256sum -c artifact-manifest.sha256校验发行二进制、组件、驱动和JDK。
配置目录允许按部署环境修改。运行后生成的流程、仓库、证书、账号、状态和日志保存在
nifi-2.9.0目录，请按NiFi的备份方式保管该目录。
这份安装包使用干净的官方配置，首次运行生成本实例的身份与安全配置。
"""


def digest(path, algorithm="sha256"):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def inspect_nar(path):
    with ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError("Corrupt NAR: " + path.name)
        text = re.sub(r"\r?\n ", "", archive.read("META-INF/MANIFEST.MF").decode("utf8"))
        fields = dict(line.split(": ", 1) for line in text.splitlines() if ": " in line)
        types = {name: [] for name in SERVICES.values()}
        for entry in archive.namelist():
            if entry.startswith("META-INF/bundled-dependencies/") and entry.endswith(".jar"):
                with ZipFile(BytesIO(archive.read(entry))) as jar:
                    for service, category in SERVICES.items():
                        if service in jar.namelist():
                            types[category].extend(line.strip() for line in jar.read(service).decode("utf8").splitlines()
                                                   if line.strip() and not line.strip().startswith("#"))
    return fields, types


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("base", "custom-directory", "mapping", "java", "drivers", "driver-template", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--java-sha256", required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.is_relative_to(ROOT) and not output.is_relative_to(ROOT / "logs"):
        raise ValueError("Deployment artifacts belong outside the repository or in logs/")
    expected = args.base.with_name("nifi-2.9.0-bin.zip.sha512").read_text().split()[0].lower()
    if digest(args.base, "sha512") != expected or digest(args.java) != args.java_sha256:
        raise ValueError("Upstream release or JDK checksum mismatch")
    with tarfile.open(args.java) as archive:
        executable = next(item for item in archive if item.name.endswith("/bin/java"))
        header = archive.extractfile(executable).read(20)
        if header[:6] != b"\x7fELF\x02\x01" or int.from_bytes(header[18:20], "little") != 62:
            raise ValueError("Expected Linux x86_64 JDK")
    custom = list(args.custom_directory.glob("*.nar")) + [args.mapping]
    summaries = []
    coordinates = set()
    for path in custom:
        fields, types = inspect_nar(path)
        artifact, version = fields["Nar-Id"], fields["Nar-Version"]
        if artifact not in REQUIRED or version != ("1.1.30" if artifact == "haitong-mapping-nar" else "2.9.0"):
            raise ValueError("Unexpected custom bundle: " + path.name)
        coordinates.add((fields["Nar-Group"], artifact, version))
        summaries.append({"file": path.name, "coordinate": {key: value for key, value in fields.items() if key.startswith("Nar-")},
                          "sha256": digest(path), **types})
    if len(custom) != len(REQUIRED) or {row["coordinate"]["Nar-Id"] for row in summaries} != REQUIRED:
        raise ValueError("The complete custom NAR set is required")
    drivers = json.loads((args.drivers / "manifest.json").read_text(encoding="utf8"))
    for relative, item in drivers["files"].items():
        path = (args.drivers / relative).resolve()
        if not path.is_relative_to(args.drivers.resolve()) or digest(path) != item["sha256"]:
            raise ValueError("Driver checksum mismatch: " + relative)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(".zip.partial")
    checksums = {}
    with ZipFile(args.base) as base:
        names = base.namelist()
        if len(names) != len(set(names)) or not all(name.startswith("nifi-2.9.0/") for name in names):
            raise ValueError("Unexpected NiFi release layout")
        for name in names:
            if name.endswith(".nar"):
                with ZipFile(BytesIO(base.read(name))) as nar:
                    text = nar.read("META-INF/MANIFEST.MF").decode("utf8")
                    fields = dict(line.split(": ", 1) for line in re.sub(r"\r?\n ", "", text).splitlines() if ": " in line)
                    coordinates.add(tuple(fields[key] for key in ("Nar-Group", "Nar-Id", "Nar-Version")))
        for item in summaries:
            fields = item["coordinate"]
            if "Nar-Dependency-Id" in fields and tuple(fields["Nar-Dependency-" + key] for key in ("Group", "Id", "Version")) not in coordinates:
                raise ValueError("Missing NAR parent: " + item["file"])
        with ZipFile(temporary, "x", allowZip64=True) as result:
            def put(name, source=None, data=None, mutable=False):
                info = ZipInfo(name)
                info.create_system = 3
                info.external_attr = (0o100755 if name.endswith(".sh") else 0o100644) << 16
                info.compress_type = ZIP_STORED if name.endswith((".jar", ".nar", ".tar.gz")) else ZIP_DEFLATED
                hasher = hashlib.sha256()
                if data is not None:
                    result.writestr(info, data)
                    hasher.update(data)
                else:
                    with source, result.open(info, "w", force_zip64=True) as target:
                        for block in iter(lambda: source.read(1024 * 1024), b""):
                            target.write(block)
                            hasher.update(block)
                if not mutable:
                    checksums[name] = hasher.hexdigest()
            for name in names:
                if not name.endswith("/"):
                    put(name, source=base.open(name), mutable=name.startswith("nifi-2.9.0/conf/"))
            for path in custom:
                put("nifi-2.9.0/extensions/" + path.name, source=path.open("rb"))
            agent = args.custom_directory / "linewell-hadoop-jdkcompat-agent-2.9.0.jar"
            put("optional/hadoop/" + agent.name, source=agent.open("rb"))
            put("vendor/java21.tar.gz", source=args.java.open("rb"))
            for relative in ["manifest.json", *sorted(drivers["files"])]:
                put("drivers/" + relative, source=(args.drivers / relative).open("rb"))
            for name in ("nifi.sh", "install-nifi.sh"):
                put(name, data=(TEMPLATES / name).read_bytes().replace(b"\r\n", b"\n"))
            put("config/jdbc-driver-locations.template", source=args.driver_template.open("rb"))
            put("extensions.json", data=json.dumps(summaries, ensure_ascii=False, indent=2).encode("utf8"))
            put("部署说明.md", data=DEPLOYMENT.encode("utf8"))
            result.writestr("artifact-manifest.sha256", "".join(value + "  " + name + "\n" for name, value in sorted(checksums.items())))
    with ZipFile(temporary) as archive:
        if archive.testzip() is not None or len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError("Invalid completed NiFi package")
        for name, expected in checksums.items():
            with archive.open(name) as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                    raise ValueError("Packaged content mismatch: " + name)
    if output.exists():
        raise FileExistsError(output)
    temporary.replace(output)
    (output.parent / "SHA256SUMS.txt").write_text(digest(output) + "  " + output.name + "\n", encoding="ascii")
    (output.parent / "部署说明.md").write_text(DEPLOYMENT, encoding="utf8")
    print(f"Verified {len(custom)} custom NARs, {len(drivers['files'])} driver files, {len(checksums)} immutable files")
    print(output, output.stat().st_size)


if __name__ == "__main__":
    main()
