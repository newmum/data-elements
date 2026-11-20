"""Export reviewed police presentation settings and organization hierarchy for DM8 installs."""
import argparse
from base64 import b64encode
from decimal import Decimal
import json
from pathlib import Path
import pymysql


ROOT = Path(__file__).resolve().parents[1]
TENANT = "2084109831682699264"
PUBLIC_CODES = {
    "appId", "loginUrl", "logo", "needLogin", "showLogo", "showShoppingCar", "showTagsView",
    "showWatermark", "sidebarOpen", "systemCode", "systemName", "themeColor", "version", "watermarkContent",
}
CONFIG_COLUMNS = (
    "tid", "config_code", "config_name", "config_value", "is_use", "sort_no", "is_encrypt", "is_del",
    "tenant_id", "config_group", "value_type", "config_scope", "cache_ttl_seconds", "version_no",
)
ORG_COLUMNS = (
    "id", "tenant_id", "name", "parent_id", "serial_number", "org_path", "nature", "status", "deleted", "sort_num",
)


def write_rows(path, columns, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="ascii", newline="\n") as output:
        output.write("\t".join(columns) + "\n")
        for row in rows:
            fields = []
            for column in columns:
                value = row.get(column)
                if isinstance(value, Decimal):
                    value = int(value)
                fields.append("-" if value is None else "+" + b64encode(str(value).encode("utf-8")).decode("ascii"))
            output.write("\t".join(fields) + "\n")


def config_rows(connection, tenant):
    with connection.cursor() as cursor:
        cursor.execute("SELECT " + ",".join(CONFIG_COLUMNS) + " FROM sym_config_t "
                       "WHERE tenant_id=%s AND (is_del=0 OR is_del IS NULL) AND is_use=1 AND is_encrypt=0",
                       (TENANT if tenant else "GLOBAL",))
        rows = [{key.lower(): value for key, value in row.items()} for row in cursor.fetchall()]
    selected = []
    for row in rows:
        public = row["config_group"] == "PORTAL" and row["config_scope"] == "PUBLIC" and row["config_code"] in PUBLIC_CODES
        internal = tenant and (row["config_group"], row["config_code"]) in {
            ("RUNTIME", "orgRootId"), ("AUTH_DATA_SCOPE", "RESOURCE_SCOPES"),
        }
        if public or internal:
            row["tenant_id"] = TENANT if tenant else "GLOBAL"
            selected.append(row)
    keys = [(row["tenant_id"], row["config_group"], row["config_code"]) for row in selected]
    if len(keys) != len(set(keys)):
        raise ValueError("Duplicate initialization configuration keys require review")
    return selected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=Path.home()/".codex/private/data-elements/resources.local.json")
    parser.add_argument("--tenant-database", required=True)
    parser.add_argument("--output", type=Path, default=ROOT/"tools/police-deployment/db")
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8-sig"))
    control = dict(config["control"])
    allowed = {"host", "port", "user", "password", "database", "charset"}
    control = {key: value for key, value in control.items() if key in allowed}
    bindings = config["nacos_tenant_database"]
    source = bindings["data-sources"][bindings["tenant-bindings"][TENANT]]
    if f"/{args.tenant_database}?" not in source["url"]:
        raise ValueError("Requested tenant database does not match the configured police binding")
    with pymysql.connect(**control, cursorclass=pymysql.cursors.DictCursor, connect_timeout=10) as connection:
        defaults = config_rows(connection, False)
    control["database"] = args.tenant_database
    with pymysql.connect(**control, cursorclass=pymysql.cursors.DictCursor, connect_timeout=10) as connection:
        tenant = config_rows(connection, True)
        with connection.cursor() as cursor:
            cursor.execute("SELECT " + ",".join(ORG_COLUMNS) +
                           " FROM rm_org_t WHERE tenant_id=%s AND deleted=0 ORDER BY sort_num,id", (TENANT,))
            organizations = list(cursor.fetchall())
    root = next(row["config_value"] for row in tenant if row["config_code"] == "orgRootId")
    ids = {row["id"] for row in organizations}
    if root not in ids:
        raise ValueError("Configured police organization root is missing")
    if not any(row["config_code"] == "logo" and str(row["config_value"]).startswith("data:image/") for row in tenant):
        raise ValueError("Missing embedded police logo")
    # The police-only installation uses the same public branding before login.
    defaults_by_key = {(row["config_group"], row["config_code"]): row for row in defaults}
    for row in tenant:
        if row["config_group"] == "PORTAL" and row["config_scope"] == "PUBLIC":
            default = dict(row, tenant_id="GLOBAL", tid="police-default-" + row["config_code"])
            defaults_by_key[(row["config_group"], row["config_code"])] = default
    defaults = list(defaults_by_key.values())
    for row in organizations:
        if row["parent_id"] not in ids | {None, "", "ROOT", "linewell.ccip"}:
            raise ValueError("Organization parent is missing from the selected police hierarchy")
    write_rows(args.output/"police-defaults-config.tsv", CONFIG_COLUMNS, defaults + tenant)
    write_rows(args.output/"police-defaults-org.tsv", ORG_COLUMNS, organizations)
    print(f"Reviewed public defaults {len(defaults)}, police settings {len(tenant)}, organizations {len(organizations)}")


if __name__ == "__main__":
    main()
