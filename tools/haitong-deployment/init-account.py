#!/usr/bin/env python3
"""Create the initial local administrator and optionally register its NiFi node."""
import argparse
import getpass
import hashlib
import os
import subprocess
from urllib.parse import urlsplit
from uuid import uuid4

TENANT = "8fb3fdba44d54a729098000000000001"
ORG = "8fb3fdba44d54a729098000000000003"
ROLE = "8fb3fdba44d54a729098000000000004"
ADMIN = "8fb3fdba44d54a729098000000000007"
NODE = "8fb3fdba44d54a729098000000000010"


def literal(value):
    return "CONVERT(0x" + value.encode("utf-8").hex() + " USING utf8mb4)" if value else "''"


def password_digest(password):
    def sm3(value):
        return hashlib.new("sm3", value.encode("utf-8")).hexdigest().upper()
    a, b, c, d, e = str(uuid4()).upper().split("-")
    return e + a + c + sm3(a + b + sm3(password) + d + e) + b + d


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql", default="mysql")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", required=True)
    parser.add_argument("--defaults-extra-file", help="Client option file outside the installation package")
    parser.add_argument("--nifi-url")
    parser.add_argument("--nifi-username")
    parser.add_argument("--nifi-root-group")
    parser.add_argument("--nifi-jdbc-driver", action="append", default=[], metavar="TYPE=PATH")
    parser.add_argument("--nifi-insecure-tls", action="store_true")
    args = parser.parse_args()

    command = [args.mysql]
    if args.defaults_extra_file:
        command += [f"--defaults-extra-file={args.defaults_extra_file}"]
    else:
        command += ["--password"]
    command += ["--protocol=tcp", "--host", args.host, "--port", str(args.port), "--user", args.user,
                "--default-character-set=utf8mb4", "baseline_haitong"]
    sql = "START TRANSACTION;\n"
    if not args.nifi_url:
        password = os.getenv("HAITONG_ADMIN_PASSWORD")
        if password is None:
            password = getpass.getpass("设置 admin 初始密码（至少 12 位）: ")
            if password != getpass.getpass("再次输入密码: "):
                raise SystemExit("两次输入的密码不一致")
        if len(password) < 12:
            raise SystemExit("初始密码至少 12 位")
        sql += f"""
INSERT INTO rm_user_t (id,tenant_id,user_name,real_name,password,status,deleted,create_time)
VALUES ('{ADMIN}','{TENANT}','admin','海通管理员',{literal(password_digest(password))},0,0,NOW(6));
INSERT INTO rm_user_org_rela_t (id,tenant_id,user_id,org_id,job_type,serial_number)
VALUES ('8fb3fdba44d54a729098000000000008','{TENANT}','{ADMIN}','{ORG}','1','1000');
INSERT INTO rm_user_role_rela_t (id,tenant_id,user_id,role_id)
VALUES ('8fb3fdba44d54a729098000000000009','{TENANT}','{ADMIN}','{ROLE}');
"""
    else:
        address = urlsplit(args.nifi_url)
        if address.scheme != "https" or not address.hostname or address.username or address.query or address.fragment:
            raise SystemExit("请提供可访问的 NiFi HTTPS 地址，例如 https://nifi.example.com:8443")
        if not args.nifi_username:
            raise SystemExit("请提供 --nifi-username")
        secret = os.getenv("HAITONG_NIFI_PASSWORD") or getpass.getpass("NiFi 密码: ")
        if not secret:
            raise SystemExit("NiFi 密码不能为空")
        drivers = {}
        for item in args.nifi_jdbc_driver:
            kind, separator, path = item.partition("=")
            if not separator or not kind.strip() or not path.strip() or "\n" in item or "\r" in item:
                raise SystemExit("JDBC 驱动格式为 --nifi-jdbc-driver TYPE=PATH")
            key = kind.strip().upper()
            if key in drivers:
                raise SystemExit("同一数据库类型只能登记一条驱动路径")
            drivers[key] = path.strip()
        driver_locations = "\n".join(f"{key}={path}" for key, path in drivers.items())
        sql += f"""
INSERT INTO nifi_node_t (tid,tenant_id,node_name,node_code,base_url,root_process_group_id,auth_username,
auth_password,insecure_tls,jdbc_driver_locations,enabled,is_default,created_time,updated_time)
VALUES ('{NODE}','{TENANT}','海通默认节点','haitong-default',{literal(args.nifi_url.rstrip('/'))},
{literal(args.nifi_root_group)},{literal(args.nifi_username)},{literal(secret)},{int(args.nifi_insecure_tls)},
{literal(driver_locations)},1,1,NOW(6),NOW(6));
"""
    result = subprocess.run(command, input=(sql + "COMMIT;\n").encode("utf-8"), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # Database error text can echo credential fields. Keep the output private.
        raise SystemExit("初始化失败，请检查数据库连接、库表和记录是否已初始化。")
    print("NiFi 节点已登记" if args.nifi_url else "本地管理员 admin 已初始化")


if __name__ == "__main__":
    main()
