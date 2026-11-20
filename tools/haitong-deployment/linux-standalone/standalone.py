"""Manage the bundled Haitong services without modifying system services."""
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
import secrets
import signal
import socket
import subprocess
import sys
import time
from urllib.request import Request, urlopen
import zipfile


ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / "data" / "local"
RUNTIME = ROOT / "runtime"
LOGS = ROOT / "logs"
TENANT = "8fb3fdba44d54a729098000000000001"
APP = "8fb3fdba44d54a729098000000000002"


def private_file(path, content):
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8") as output:
        os.chmod(temporary, 0o600)
        output.write(content)
    temporary.replace(path)


def get_credentials():
    path = STATE / "credentials.json"
    if not path.exists():
        values = {key: secrets.token_hex(24) for key in ("mysql", "mysqlRoot", "redis", "admin", "nifi")}
        values["transport"] = base64.b64encode(secrets.token_bytes(32)).decode("ascii")
        private_file(path, json.dumps(values))
    return json.loads(path.read_text(encoding="utf-8"))


def provision_files():
    for directory, archive, binary, strip in (
        ("java", "java21.tar.gz", "bin/java", 1),
        ("mysql", "mysql.tar.xz", "bin/mysqld", 1),
        ("redis", "redis.tar.gz", "usr/bin/valkey-server", 0),
        ("caddy", "caddy.tar.gz", "caddy", 0),
        ("mysql-libs", "mysql-libs.tar.gz", "libaio.so.1", 0),
    ):
        destination = RUNTIME / directory
        marker = destination / ".extracted"
        if not marker.exists():
            print(f"正在解包 {directory}…", flush=True)
            destination.mkdir(parents=True, exist_ok=True)
            subprocess.run(["tar", "-xf", str(ROOT / "vendor" / archive), "-C", str(destination),
                            f"--strip-components={strip}"], check=True)
            if not (destination / binary).exists():
                raise RuntimeError(f"包内运行组件不完整: {directory}/{binary}")
            marker.touch()
    nifi = RUNTIME / "nifi-2.9.0"
    if not (nifi / ".extracted").exists():
        print("正在解包 NiFi…", flush=True)
        with zipfile.ZipFile(ROOT / "vendor/nifi.zip") as archive:
            if not all(name.startswith("nifi-2.9.0/") for name in archive.namelist()):
                raise RuntimeError("NiFi 发行包目录不匹配")
            archive.extractall(RUNTIME)
        (nifi / ".extracted").touch()


def process_info(pid):
    try:
        values = Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()
        return values[19] if values[0] != "Z" else None
    except (FileNotFoundError, ProcessLookupError):
        return None


def service_pid(service):
    file = STATE / f"{service}.pid"
    if file.exists():
        pid, timestamp = file.read_text().split()
        if process_info(pid) == timestamp:
            return int(pid)
    return None


def spawn(service, command, cwd=ROOT, env=None):
    existing = service_pid(service)
    if existing:
        return existing
    with (LOGS / f"{service}.log").open("ab") as log:
        worker = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                  stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    timestamp = process_info(worker.pid)
    if not timestamp:
        raise RuntimeError(f"{service} 未启动，请查看 logs/{service}.log")
    (STATE / f"{service}.pid").write_text(f"{worker.pid} {timestamp}\n")
    return worker.pid


def stop_service(service):
    pid = service_pid(service)
    if pid:
        os.kill(pid, signal.SIGTERM)
        deadline = time.monotonic() + 45
        while service_pid(service):
            if time.monotonic() >= deadline:
                raise RuntimeError(f"{service} 尚未停止，请查看日志")
            time.sleep(0.25)
    (STATE / f"{service}.pid").unlink(missing_ok=True)


def require_free_port(port, service, host="127.0.0.1"):
    if not service_pid(service):
        with socket.socket() as listener:
            try:
                listener.bind((host, port))
            except OSError as failure:
                raise RuntimeError(f"{service} 端口 {port} 已占用，请调整 config/ports.json") from failure


def wait_for(service, ready, seconds=120):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline and service_pid(service):
        try:
            if ready():
                return
        except (OSError, ValueError, KeyError):
            pass
        time.sleep(0.75)
    raise RuntimeError(f"{service} 未就绪，请查看 logs/{service}.log")


def http_json(url, data=None, method=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["token"] = token
    request = Request(url, json.dumps(data).encode("utf-8") if data is not None else None,
                      headers=headers, method=method)
    with urlopen(request, timeout=5) as response:
        return json.load(response)


def mysql_env():
    return dict(os.environ, LD_LIBRARY_PATH=str(RUNTIME / "mysql-libs"))


def start_mysql(ports, initialize):
    binary_root = RUNTIME / "mysql"
    data = STATE / "mysql"
    socket_path = STATE / "mysql.sock"
    command = [str(binary_root / "bin/mysqld"), "--no-defaults", f"--basedir={binary_root}",
               f"--datadir={data}", f"--user={pwd.getpwuid(os.getuid()).pw_name}"]
    if not (data / "mysql").is_dir():
        data.mkdir(exist_ok=True)
        with (LOGS / "mysql-initialize.log").open("ab") as log:
            subprocess.run(command + ["--initialize-insecure"], env=mysql_env(), stdout=log,
                           stderr=subprocess.STDOUT, check=True)
    args = ["--mysqlx=OFF", "--local-infile=OFF", "--bind-address=127.0.0.1",
            f"--port={ports['mysql']}", f"--socket={socket_path}", f"--pid-file={STATE / 'mysqld.native.pid'}"]
    if initialize:
        args.append("--skip-networking")
    spawn("mysql", command + args, env=mysql_env())
    admin = str(binary_root / "bin/mysqladmin")
    wait_for("mysql", lambda: subprocess.run(
        [admin, "--no-defaults", "--protocol=socket", f"--socket={socket_path}", "ping"],
        env=mysql_env(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0)


def bootstrap_database(ports, credentials):
    ready = STATE / "database-initialized"
    if ready.exists():
        return
    hashlib.new("sm3")
    print("正在初始化海通数据库…", flush=True)
    start_mysql(ports, initialize=True)
    cli = str(RUNTIME / "mysql/bin/mysql")
    socket_path = STATE / "mysql.sock"
    defaults = STATE / "mysql-root.cnf"
    private_file(defaults, "[client]\n")
    command = [cli, f"--defaults-file={defaults}", "--protocol=socket",
               f"--socket={socket_path}", "--user=root", "--default-character-set=utf8mb4"]
    env = mysql_env()
    try:
        with (LOGS / "database-import.log").open("ab") as log:
            for filename in ("01-control.sql", "02-tenant.sql"):
                with (ROOT / "db" / filename).open("rb") as sql:
                    subprocess.run(command, input=sql.read(), env=env, stdout=log, stderr=log, check=True)
            subprocess.run([sys.executable, str(ROOT / "db/init-account.py"), "--mysql", cli, "--socket",
                            str(socket_path), "--user", "root", "--defaults-extra-file", str(defaults)],
                           env=dict(env, HAITONG_ADMIN_PASSWORD=credentials["admin"]), stdout=log, stderr=log, check=True)
        drivers = "\n".join(f"{kind}={next((ROOT / 'drivers').glob(pattern))}" for kind, pattern in (
            ("MYSQL", "mysql-connector-j-*.jar"), ("MARIADB", "mariadb-java-client-*.jar")))
        driver_sql = "CONVERT(0x" + drivers.encode().hex() + " USING utf8mb4)"
        seed = (
            f"CREATE USER 'haitong'@'127.0.0.1' IDENTIFIED BY '{credentials['mysql']}';\n"
            "GRANT ALL ON baseline.* TO 'haitong'@'127.0.0.1';\n"
            "GRANT ALL ON baseline_haitong.* TO 'haitong'@'127.0.0.1';\n"
            "INSERT INTO baseline_haitong.nifi_node_t "
            "(tid,tenant_id,node_name,node_code,base_url,insecure_tls,jdbc_driver_locations,enabled,is_default,created_time,updated_time) "
            f"VALUES ('8fb3fdba44d54a729098000000000010','{TENANT}','Bundled NiFi','haitong-default',"
            f"'http://127.0.0.1:{ports['nifi']}',0,{driver_sql},1,1,NOW(6),NOW(6));\n"
            f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{credentials['mysqlRoot']}';\n"
        )
        subprocess.run(command, input=seed.encode(), env=env, stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL, check=True)
        private_file(defaults, f"[client]\nuser=root\npassword={credentials['mysqlRoot']}\nsocket={socket_path}\n")
        ready.touch()
    finally:
        stop_service("mysql")


def configure_nifi(ports, credentials):
    home = RUNTIME / "nifi-2.9.0"
    path = home / "conf/nifi.properties"
    options = {
        "nifi.web.http.host": "127.0.0.1", "nifi.web.http.port": str(ports["nifi"]),
        "nifi.web.https.host": "", "nifi.web.https.port": "",
        "nifi.security.keystore": "", "nifi.security.keystorePasswd": "", "nifi.security.keyPasswd": "",
        "nifi.security.truststore": "", "nifi.security.truststorePasswd": "",
        "nifi.security.user.login.identity.provider": "", "nifi.security.user.authorizer": "",
        "nifi.nar.library.autoload.directory": "./extensions",
        "nifi.remote.input.secure": "false", "nifi.sensitive.props.key": credentials["nifi"],
    }
    content = path.read_text(encoding="utf-8")
    for key, value in options.items():
        line = f"{key}={value}"
        content, count = re.subn(r"(?m)^" + re.escape(key) + r"=.*$", lambda _: line, content)
        if not count:
            content += f"\n{line}\n"
    private_file(path, content)
    return home


def verify_nifi_extensions(url):
    required = {
        ("haitong-mapping-nar", "com.linewell.dataelement.nifi.HaitongTransformRecord"),
        ("datadevs-database-nar", "com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"),
        ("datadevs-database-nar", "com.linewell.microservice.processors.database.GenerateDateTableFetch"),
    }

    def extensions_ready():
        response = http_json(url + "/flow/processor-types")
        loaded = {(item["bundle"]["artifact"], item["type"]) for item in response["processorTypes"]}
        return required <= loaded

    try:
        wait_for("nifi", extensions_ready, 45)
    except RuntimeError as failure:
        raise RuntimeError("NiFi 未注册海通字段映射或数据库处理器，请检查 runtime/nifi-2.9.0/logs/nifi-app.log") from failure


def initialize_record_services(url):
    root = http_json(url + "/flow/process-groups/root")["processGroupFlow"]["id"]
    types = {row["type"]: row["bundle"] for row in http_json(url + "/flow/controller-service-types")["controllerServiceTypes"]}
    existing = http_json(url + f"/flow/process-groups/{root}/controller-services")["controllerServices"]
    by_name = {row["component"]["name"]: row for row in existing}
    formats = {"Date Format": "yyyy-MM-dd", "Time Format": "HH:mm:ss",
               "Timestamp Format": "yyyy-MM-dd HH:mm:ss.SSSSSSSSS"}
    for name, kind, properties in (
        ("PUBLIC-READER-JSON", "JsonTreeReader", {}),
        ("PUBLIC-WRITER-JSON", "JsonRecordSetWriter", {}),
        ("PUBLIC-WRITER-JSON-TIME_FORMAT", "JsonRecordSetWriter", {**formats, "Schema Write Strategy": "full-schema-attribute"}),
    ):
        component_type = "org.apache.nifi.json." + kind
        service = by_name.get(name)
        if service is None:
            service = http_json(url + f"/process-groups/{root}/controller-services", {
                "revision": {"version": 0},
                "component": {"name": name, "type": component_type, "bundle": types[component_type],
                              "properties": properties},
            })
        resource = url + "/controller-services/" + service["id"]
        if service["component"]["state"] == "DISABLED":
            http_json(resource + "/run-status", {"revision": service["revision"], "state": "ENABLED"}, "PUT")
        wait_for("nifi", lambda: http_json(resource)["component"]["state"] == "ENABLED", 45)


def mysql_tcp_url(ports, database):
    return (f"jdbc:mysql://127.0.0.1:{ports['mysql']}/{database}"
            "?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true")


def start(ports, credentials, redis_loader):
    for service in ("mysql", "redis", "nifi", "backend"):
        require_free_port(ports[service], service)
    require_free_port(ports["web"], "web", ports["webBind"])
    before = {service for service in ("mysql", "redis", "nifi", "backend", "web") if service_pid(service)}
    try:
        bootstrap_database(ports, credentials)
        start_mysql(ports, initialize=False)
        redis = RUNTIME / "redis"
        redis_config = STATE / "redis.conf"
        private_file(redis_config, f"bind 127.0.0.1\nport {ports['redis']}\n"
                     f"requirepass {credentials['redis']}\nappendonly yes\n")
        spawn("redis", [str(redis / redis_loader), "--library-path",
                        f"{redis / 'lib'}:{redis / 'usr/lib'}", str(redis / "usr/bin/valkey-server"), str(redis_config)], cwd=STATE)
        home = configure_nifi(ports, credentials)
        java = str(RUNTIME / "java/bin/java")
        nifi_url = f"http://127.0.0.1:{ports['nifi']}/nifi-api"
        print("正在启动 NiFi…", flush=True)
        spawn("nifi", [java, "-Xms256m", "-Xmx1g", "-cp", f"{home / 'lib'}/*:{home / 'conf'}",
                       f"-Dnifi.properties.file.path={home / 'conf/nifi.properties'}",
                       f"-Dorg.apache.nifi.bootstrap.config.log.dir={home / 'logs'}", "org.apache.nifi.NiFi"], cwd=home,
              env=dict(os.environ, JAVA_HOME=str(RUNTIME / "java"), PATH=str(RUNTIME / "java/bin") + ":" + os.environ["PATH"]))
        # 首次启动需要展开 NiFi 自带的 NAR 并初始化 Web 应用。
        wait_for("nifi", lambda: bool(http_json(nifi_url + "/flow/process-groups/root")), 480)
        verify_nifi_extensions(nifi_url)
        initialize_record_services(nifi_url)
        print("正在启动海通后端…", flush=True)
        backend_env = dict(os.environ, HAITONG_BACKEND_PORT=str(ports["backend"]),
                           HAITONG_REDIS_PORT=str(ports["redis"]), HAITONG_REDIS_PASSWORD=credentials["redis"],
                           HAITONG_DB_PASSWORD=credentials["mysql"], HAITONG_DB_USER="haitong",
                           HAITONG_CONTROL_JDBC_URL=mysql_tcp_url(ports, "baseline"),
                           HAITONG_TENANT_JDBC_URL=mysql_tcp_url(ports, "baseline_haitong"),
                           IDAAS_TRANSPORT_KEY=credentials["transport"], IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP="true")
        spawn("backend", [java, "-Xms256m", "-Xmx1g", "-Dfile.encoding=UTF-8", "-jar", "backend/data-element.jar",
                          "--spring.config.location=file:./config/application.yml"], env=backend_env)
        wait_for("backend", lambda: http_json(f"http://127.0.0.1:{ports['backend']}/actuator/health")["status"] == "UP", 160)
        spawn("web", [str(RUNTIME / "caddy/caddy"), "run", "--config", "config/Caddyfile", "--adapter", "caddyfile"],
              env=dict(os.environ, HAITONG_HTTP_PORT=str(ports["web"]), HAITONG_HTTP_BIND=ports["webBind"],
                       HAITONG_BACKEND_PORT=str(ports["backend"])))
        web_host = "127.0.0.1" if ports["webBind"] == "0.0.0.0" else ports["webBind"]
        url = f"http://{web_host}:{ports['web']}"
        wait_for("web", lambda: http_json(url + "/prod-api/actuator/health")["status"] == "UP")
        first_login = STATE / "first-login-verified"
        if not first_login.exists():
            login = http_json(url + "/prod-api/portal/sso/login", {
                "account": "admin", "password": hashlib.new("sm3", credentials["admin"].encode()).hexdigest(),
                "tenantId": TENANT, "appId": APP,
            })
            if login.get("code") not in (0, 200):
                raise RuntimeError("初始管理员登录验证失败，请查看后端日志")
            page = http_json(url + "/prod-api/nifi/flow/page?page=1&size=20", token=login["data"]["token"])
            if not isinstance(page.get("list"), list):
                raise RuntimeError("NiFi 流程列表验证失败，请查看后端日志")
            private_file(STATE / "admin-login.txt", f"URL: {url}\nUsername: admin\nPassword: {credentials['admin']}\n")
            first_login.touch()
        print(f"海通已就绪。网页端口 {ports['web']}，初始账号请查看 data/local/admin-login.txt")
    except Exception:
        for service in ("web", "backend", "nifi", "redis", "mysql"):
            if service not in before:
                stop_service(service)
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("start", "status", "stop", "init-db"))
    options = parser.parse_args()
    runtime = json.loads((ROOT / "config/runtime.json").read_text(encoding="ascii"))
    machine = platform.machine().lower()
    architecture = {"amd64": "x86_64", "arm64": "aarch64"}.get(machine, machine)
    if platform.system() != "Linux" or architecture != runtime["architecture"]:
        raise RuntimeError(f"此部署包需要 Linux {runtime['architecture']}，当前是 {platform.system()} {machine}")
    os.umask(0o077)
    STATE.mkdir(parents=True, exist_ok=True)
    LOGS.mkdir(exist_ok=True)
    os.chdir(ROOT)
    ports = json.loads((ROOT / "config/ports.json").read_text(encoding="ascii"))
    with (STATE / "launcher.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if options.action == "status":
            for service in ("web", "backend", "nifi", "redis", "mysql"):
                pid = service_pid(service)
                print(f"{service}: {'running (PID ' + str(pid) + ')' if pid else 'stopped'}")
            return
        if options.action == "stop":
            for service in ("web", "backend", "nifi", "redis", "mysql"):
                stop_service(service)
            print("海通已停止")
            return
        provision_files()
        credentials = get_credentials()
        if options.action == "init-db":
            bootstrap_database(ports, credentials)
            print("数据库初始化完成")
        else:
            start(ports, credentials, runtime["redisLoader"])


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError, KeyError, ValueError) as error:
        print(f"启动操作失败: {error}", file=sys.stderr)
        sys.exit(1)
