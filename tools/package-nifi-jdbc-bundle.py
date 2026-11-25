#!/usr/bin/env python3
"""Build a driver-only NiFi bundle from a hash-checked archive and reviewed local originals."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
from zipfile import ZipFile, ZIP_STORED


ROOT = Path(__file__).resolve().parents[1]
TEMPLATES = ROOT / "tools/nifi-deployment/jdbc"
NOTES = """NiFi 2.9 / JDK 21 JDBC 驱动合集

一、安装（Linux；使用 NiFi 的运行账号）
解压本 ZIP，进入目录，执行：
  bash install-jdbc.sh /home/linewell/application/nifi-2.9.0
脚本需要 Python 3.6+，先校验全部 SHA-256，再拷贝到 NiFi 的 drivers/{bundle}。
不会改 lib、extensions、conf、流程或队列，不会启停 NiFi。重复执行校验相同文件；遇到同名不同内容则停止。
没有 Python 时，可手动复制整个 drivers 目录到 NiFi 可读的固定目录，并按驱动清单配置绝对路径。
NiFi 集群须把相同文件放到每个执行节点的相同绝对路径。

二、必须配置连接池，拷贝文件不会自动修复已有连接池
安装后打开 NiFiHome/drivers/{bundle}/jdbc-driver-profiles.csv：
  Driver Class Name：对应行的 driverClass。
  Database Driver Location(s)：对应行的 driverLocations（完整绝对路径，多个 JAR 用逗号分隔）。
海通的接入节点驱动路径配置也需要按实际使用的数据库键更新，保留其他配置项。
这些是 NiFi 服务器路径，不是数据中台后端或浏览器所在电脑的路径。
已生成的 DBCP 服务需要停用后修改上述属性再启用，或在平台中更新节点配置后按业务安排重新部署。
只放到 lib/extensions 或只重启平台后端，不能证明已有连接池已使用新路径。
不要将所有厂商和不同版本的 JAR 一起填到同一个连接池，尤其不能混用两套 HiveDriver。

三、重要版本选择
CLICKHOUSE 默认包含 0.6.5 主 JAR + 0.6.5-all 两个文件，必须一起配置，不能只填 all。
CLICKHOUSE_0_11_1 是独立可选版本，不能与 0.6.5 混用；support 中 SLF4J API 用于离线自检。
GAUSSDB_JDBC 是华为 GaussDB 原生驱动；GAUSSDB_NATIVE 是华为 openGauss 驱动；
GAUSSDB_OPENGAUSS 是社区 openGauss，GAUSSDB_DWS 是 PostgreSQL/DWS 路线，按真实产品和 URL 选择。
HIVE 是归档华为 MRS 客户端及依赖；HIVE_OPEN_SOURCE 是独立开源 Hive 客户端。
HIVE_COMPAT 和 ORACLE_COMPAT 是近期验证的可选兼容驱动，已配上相应厂商原件；不能单独使用兼容 JAR。
本包不改变平台现有驱动类选择，是否启用兼容配置应与所用平台版本保持一致。
MRS 站点 XML、Kerberos、证书及厂商认证配置由现场提供，不包含任何私有连接、密码、keytab。
ODPS/MaxCompute、Trino、ES SQL 等作为可选 JDBC 原件提供，不代表自动新增海通组件或适配任意服务端版本。
IoTDB 使用官方 Maven 发布的 1.3.4-1 完整依赖客户端，已对照仓库 SHA-1 校验；现场须核对服务端版本。
Elasticsearch、MongoDB、Kafka、FTP、MinIO 的常规画布组件不走 JDBC，缺 JDBC 不是这些组件所有错误的原因。

四、验证边界
manifest.json 记录每个文件的原始来源、大小、SHA-256 和各配置的完整文件列表。
本次对清单全部配置在 JDK21 运行隔离加载、驱动构造和 acceptsURL 检查，不连接任何业务数据库。
近期验证中实际使用的 ClickHouse 配对、openGauss RC3、Oracle/Hive 兼容原件保留已记录摘要。
历史数据库验收不等于本包每个驱动与任意服务端已实库认证；现场还需验证 URL、网络、账号和版本。
神通 OSCAR 未找到可核验厂商原件，本包不冒充包含它；需要另取该数据库配套 JDBC 驱动。
“所有 JDBC 驱动”没有固定全集；本包覆盖本项目可核验的主要数据库原件及其必要依赖。
JDBC JAR 通常为 Java 字节码；本包验证环境 JDK21，Linux x86_64/aarch64 均可使用纯 Java 部分。
不包含操作系统认证用本地 DLL/SO，不包含 NiFi 发行版、NAR 扩展和 JDK。
保留 JAR 中原有 LICENSE/NOTICE；厂商驱动按已有授权范围使用，不作为公开镜像分发。

五、当前“控制器服务未在30秒内全部就绪”排查
缺驱动时常见 ClassNotFoundException、NoClassDefFoundError 或无法加载 Driver Locations。
装好并引用驱动后仍未就绪，需查看 NiFi 对应两个 Controller Service 的 validationErrors 与 nifi-app.log；
也可能是数据库网络、账号、JDBC URL、Kerberos、依赖服务或启用耗时，不能仅凭该截图断定缺驱动。
"""


def sha(data):
    return hashlib.sha256(data).hexdigest()


def text_file(path, content, encoding="utf-8"):
    with path.open("w", encoding=encoding, newline="\n") as stream:
        stream.write(content)


def build(args):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", args.bundle_id):
        raise ValueError("bundle-id must be a single directory name")
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    stage = out / args.bundle_id
    if stage.exists():
        raise ValueError("Output directory already exists; choose a fresh destination")
    stage.mkdir()
    files, profiles = {}, {}

    def put(relative, data, origin, expected=None):
        actual = sha(data)
        if expected and actual != expected:
            raise ValueError("Unexpected artifact digest: " + relative)
        target = stage / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        with ZipFile(target) as jar:
            if jar.testzip():
                raise ValueError("Broken JAR: " + relative)
            maximum = 0
            for entry in jar.infolist():
                if entry.filename.endswith(".class") and not entry.filename.startswith("META-INF/versions/"):
                    with jar.open(entry) as stream:
                        header = stream.read(8)
                    if header[:4] == b"\xca\xfe\xba\xbe":
                        maximum = max(maximum, struct.unpack(">H", header[6:8])[0])
            if maximum > 65:
                raise ValueError("Base bytecode requires a newer JDK: " + relative)
        files[relative] = {"sha256": actual, "bytes": len(data), "origin": origin,
                           "maximumBaseClassMajor": maximum}
        return relative

    def local(relative, path, expected=None):
        return put("drivers/" + relative, path.read_bytes(),
                   "Reviewed local original: " + path.name, expected)

    def profile(key, driver, paths, url, note=""):
        for relative in paths:
            if relative not in files:
                raise ValueError("Profile references a missing artifact: " + relative)
        profiles[key] = {"class": driver, "files": paths, "urlExample": url,
                         "note": note, "verification": "JDK21 isolated class/constructor/acceptsURL"}

    baseline = json.loads((args.baseline / "manifest.json").read_text(encoding="utf-8"))
    for relative, item in baseline["files"].items():
        put("drivers/" + relative, (args.baseline / relative).read_bytes(),
            item["origin"] + ": " + item["artifact"], item["sha256"])
    examples = {
        "dm": ("dm.jdbc.driver.DmDriver", "jdbc:dm://localhost:5236/validation"),
        "mysql": ("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/validation"),
        "mariadb": ("org.mariadb.jdbc.Driver", "jdbc:mariadb://localhost:3306/validation"),
        "postgresql": ("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/validation"),
        "oracle": ("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//localhost:1521/validation"),
        "sqlserver": ("com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://localhost:1433;databaseName=validation"),
        "oceanbase": ("com.oceanbase.jdbc.Driver", "jdbc:oceanbase://localhost:2881/validation"),
        "kingbase": ("com.kingbase8.Driver", "jdbc:kingbase8://localhost:54321/validation"),
        "vertica": ("com.vertica.jdbc.Driver", "jdbc:vertica://localhost:5433/validation"),
        "clickhouse": ("com.clickhouse.jdbc.ClickHouseDriver", "jdbc:clickhouse://localhost:8123/validation"),
        "db2": ("com.ibm.db2.jcc.DB2Driver", "jdbc:db2://localhost:50000/validation"),
        "open-source-hive": ("org.apache.hive.jdbc.HiveDriver", "jdbc:hive2://localhost:10000/validation"),
        "huawei-mrs": ("org.apache.hive.jdbc.HiveDriver", "jdbc:hive2://localhost:10000/validation"),
    }
    special = {
        "GBASE8A": ("com.gbase.jdbc.Driver", "jdbc:gbase://localhost:5258/validation"),
        "GBASE8S": ("com.gbasedbt.jdbc.IfxDriver", "jdbc:gbasedbt-sqli://localhost:9088/validation:GBASEDBTSERVER=validation"),
        "GAUSSDB": ("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/validation"),
        "GAUSSDB_DWS": ("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/validation"),
        "GAUSSDB_JDBC": ("com.huawei.gaussdb.jdbc.Driver", "jdbc:gaussdb://localhost:8000/validation"),
        "GAUSSDB_NATIVE": ("com.huawei.opengauss.jdbc.Driver", "jdbc:opengauss://localhost:5432/validation"),
        "GAUSSDB_OPENGAUSS": ("org.opengauss.Driver", "jdbc:opengauss://localhost:5432/validation"),
    }
    for key, paths in baseline["mappings"].items():
        expanded = []
        for path in paths:
            if path in baseline["files"]:
                expanded.append("drivers/" + path)
            else:
                expanded.extend("drivers/" + p for p in baseline["files"] if p.startswith(path + "/"))
        kind = paths[0].split("/")[0]
        driver, url = special[key] if key in special else examples[kind]
        profile(key, driver, expanded, url)
    profiles["CLICKHOUSE_0_11_1"] = dict(profiles["CLICKHOUSE"], note="Optional; keep separate from 0.6.5")
    pair = []
    for name, expected in [
        ("clickhouse-jdbc-0.6.5.jar", "bca3d947d0a77dd6b5fc7b2db3a46ca4422ed0dcd7191aa0fff54f5100f74ff6"),
        ("clickhouse-jdbc-0.6.5-all.jar", "de34f73b2774a5fa8dcc011ef5646fb7785079f9c7ce9f9064f27fe2710122c1"),
    ]:
        pair.append(local("clickhouse-0.6.5/" + name, args.validation / "defect-025-driver-bundle" / name, expected))
    profile("CLICKHOUSE", examples["clickhouse"][0], pair, examples["clickhouse"][1],
            "Validated managed pair: main JAR then all JAR; both required")
    additions = [
        ("HIGHGO", "highgo/HgdbJdbc-6.2.3.jar", "com/highgo/HgdbJdbc/6.2.3/HgdbJdbc-6.2.3.jar", "com.highgo.jdbc.Driver", "jdbc:highgo://localhost:5866/validation"),
        ("TRINO", "trino/trino-jdbc-403.jar", "io/trino/trino-jdbc/403/trino-jdbc-403.jar", "io.trino.jdbc.TrinoDriver", "jdbc:trino://localhost:8080/validation"),
        ("ES_SQL_7_17", "es-sql/x-pack-sql-jdbc-7.17.4.jar", "org/elasticsearch/plugin/x-pack-sql-jdbc/7.17.4/x-pack-sql-jdbc-7.17.4.jar", "org.elasticsearch.xpack.sql.jdbc.EsDriver", "jdbc:es://http://localhost:9200"),
        ("MYSQL_8_0_33", "mysql-8.0.33/mysql-connector-j-8.0.33.jar", "com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar", examples["mysql"][0], examples["mysql"][1]),
        ("ORACLE_12_2", "oracle-12.2/ojdbc8-12.2.0.1.jar", "com/github/noraui/ojdbc8/12.2.0.1/ojdbc8-12.2.0.1.jar", examples["oracle"][0], examples["oracle"][1]),
        ("ORACLE_JDK21", "oracle-jdk21/ojdbc11-23.5.0.24.07.jar", "com/oracle/database/jdbc/ojdbc11/23.5.0.24.07/ojdbc11-23.5.0.24.07.jar", examples["oracle"][0], examples["oracle"][1]),
    ]
    for key, relative, cached, driver, url in additions:
        profile(key, driver, [local(relative, args.maven / cached)], url, "Optional original; server version compatibility requires onsite verification")
    rc3 = local("opengauss-rc3/opengauss-jdbc-7.0.0-RC3-og.jar", args.validation / "opengauss-jdbc-7.0.0-RC3-og.jar",
                "a80bc5b50b8af012d8f99d20546885aa218673dab9c44aa64833abed65745a69")
    profile("OPENGAUSS_RC3", "org.opengauss.Driver", [rc3], "jdbc:opengauss://localhost:5432/validation",
            "Original used in documented Java21 read-only openGauss RC3 verification")
    for key, directory, artifact, expected, driver, base in [
        ("HIVE_COMPAT", "defect-031-driver", "haitong-hive-jdbc-compat-1.0.0.jar", "fa3d9cd9984518731be1012fca05398b0f4ae0a492926eb804d522dce3f4f62c", "com.linewell.dataelement.metautil.jdbc.NifiHiveJdbcDriver", "HIVE_OPEN_SOURCE"),
        ("ORACLE_COMPAT", "defect-036-followup-final-driver", "haitong-oracle-jdbc-compat-1.0.4.jar", "ac7da15246257c4b34aaf763d935af1e505085de3a2015e22e9dc3b1b7021c1c", "com.linewell.dataelement.metautil.jdbc.NifiOracleJdbcDriver", "ORACLE_12_2"),
    ]:
        shim = local("compat/" + artifact, args.validation / directory / artifact, expected)
        compat_url = profiles[base]["urlExample"].replace("jdbc:", "jdbc:haitong:", 1)
        profile(key, driver, [shim] + profiles[base]["files"], compat_url,
                "Optional tested shim with original vendor driver; platform must select the matching driver class")
    for alias, base in [("DAMENG", "DM"), ("TDSQL_PG", "POSTGRESQL"), ("OPENGAUSS", "GAUSSDB"),
                        ("HETU", "TRINO"), ("PRESTO", "TRINO")]:
        profiles[alias] = dict(profiles[base], note="Platform compatibility profile; verify actual server protocol; same artifacts as " + base)
    profiles["OCEANBASE_ORACLE_JDBC"] = dict(profiles["OCEANBASE_ORACLE"], note="Optional jdbc:oracle protocol only")
    profiles["OCEANBASE_ORACLE"] = dict(profiles["OCEANBASE"], note="OceanBase native driver; verify Oracle-mode URL and server version onsite")
    if args.iotdb:
        if not args.iotdb_sha256:
            raise ValueError("IoTDB original requires its verified SHA-256")
        iotdb = local("iotdb/" + args.iotdb.name, args.iotdb, args.iotdb_sha256)
        files[iotdb]["origin"] = "Apache IoTDB official Maven release org.apache.iotdb:iotdb-jdbc:1.3.4-1:jar-with-dependencies"
        profile("IOTDB", "org.apache.iotdb.jdbc.IoTDBDriver", [iotdb], "jdbc:iotdb://localhost:6667/",
                "Optional 1.3.4-1 client; do not use with an incompatible older server")
    if args.reference_backend:
        names = ["odps-jdbc-3.2.9.jar", "odps-sdk-core-0.36.4-public-shaded.jar", "odps-sdk-commons-0.36.4-public.jar",
                 "druid-1.2.20.jar", "gson-2.10.1.jar", "commons-codec-1.16.1.jar", "commons-logging-1.2.jar",
                 "httpclient-4.5.13.jar", "httpcore-4.4.16.jar", "commons-cli-1.5.0.jar"]
        paths = []
        with ZipFile(args.reference_backend) as backend:
            for name in names:
                paths.append(put("drivers/maxcompute/" + name, backend.read("BOOT-INF/lib/" + name),
                                 "Frozen reviewed backend BOOT-INF/lib/" + name))
        profile("MAXCOMPUTE", "com.aliyun.odps.jdbc.OdpsDriver", paths, "jdbc:odps:http://localhost:80/api?project=validation", "Optional MaxCompute JDBC client and dependencies")
    support = "support/slf4j-api-2.0.16.jar"
    put(support, (args.baseline / "clickhouse/slf4j-api-2.0.16.jar").read_bytes(), "Framework logging API for isolated probe")
    manifest = {"bundleId": args.bundle_id, "target": "NiFi 2.9 / JDK21", "files": files,
                "profiles": profiles, "notIncluded": ["OSCAR proprietary original unavailable"] +
                    ([] if args.iotdb else ["IoTDB vendor distribution unavailable locally"]),
                "databaseConnectionsMadeByThisBuild": 0}
    text_file(stage / "manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    for name in ("install-jdbc.py", "install-jdbc.sh", "DriverProbe.java"):
        shutil.copyfile(TEMPLATES / name, stage / name)
    text_file(stage / "使用说明.txt", NOTES.format(bundle=args.bundle_id), "utf-8-sig")
    with (stage / "驱动清单.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["配置键", "驱动类", "相对JAR路径（按本行组合）", "URL示例", "说明", "验证范围"])
        for key, p in sorted(profiles.items()):
            writer.writerow([key, p["class"], ",".join(p["files"]), p["urlExample"], p["note"], p["verification"]])
    probe = out / "driver-probe-input.txt"
    probe.write_text("".join("|".join([key, p["class"], ",".join(str(stage / f) for f in p["files"]), p["urlExample"]])
                             + "\n" for key, p in sorted(profiles.items())), encoding="utf-8")
    result = subprocess.run([str(args.java), "-cp", str(stage / support), str(stage / "DriverProbe.java"), str(probe)],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, encoding="utf-8", errors="replace", timeout=180)
    (out / "driver-probe.log").write_text(result.stdout, encoding="utf-8")
    print(result.stdout)
    if result.returncode:
        raise ValueError("Driver verification failed; no ZIP produced")
    # The probe input has build-host paths and is intentionally not shipped.
    text_file(stage / "验证结果.txt", "JDK21; no database connections.\n" + result.stdout, "utf-8-sig")
    sums = "".join(sha(p.read_bytes()) + "  " + p.relative_to(stage).as_posix() + "\n"
                   for p in sorted(stage.rglob("*")) if p.is_file())
    text_file(stage / "SHA256SUMS.txt", sums)
    archive = out / (args.bundle_id + ".zip")
    with ZipFile(archive, "w", compression=ZIP_STORED) as zipout:
        for path in sorted(stage.rglob("*")):
            if path.is_file():
                zipout.write(path, args.bundle_id + "/" + path.relative_to(stage).as_posix())
    with ZipFile(archive) as zipped:
        if zipped.testzip():
            raise ValueError("ZIP CRC verification failed")
    text_file(out / "SHA256SUMS.txt", sha(archive.read_bytes()) + "  " + archive.name + "\n")
    print(json.dumps({"archive": str(archive), "bytes": archive.stat().st_size,
                      "sha256": sha(archive.read_bytes()), "jarFiles": len(files), "profiles": len(profiles)}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--maven", type=Path, required=True)
    parser.add_argument("--validation", type=Path, required=True)
    parser.add_argument("--reference-backend", type=Path)
    parser.add_argument("--iotdb", type=Path)
    parser.add_argument("--iotdb-sha256")
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--bundle-id", default="nifi-2.9-jdbc-complete-20261125")
    build(parser.parse_args())
