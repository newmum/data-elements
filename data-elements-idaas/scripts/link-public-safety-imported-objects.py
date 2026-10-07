"""Link verified imported IDAAS orgs/roles to existing public-safety objects.

Dry run by default. Requires IDAAS_DB_PASSWORD and explicit --apply.
This never guesses by a display name: source and target IDs and names must match,
and role codes and the receiver binding must agree.
"""

import argparse
import json
import os

import pymysql


APP_ID = "2084109831682699264"
CONFIG_ID = "0ba83000fd0344c28c3de98407da7030"
INSTANCE = "ga-local-v2"
CONTROL = "baseline"
TENANT_DB = "baseline_ga_old"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    password = os.environ.get("IDAAS_DB_PASSWORD")
    if not password:
        raise SystemExit("Set IDAAS_DB_PASSWORD before running.")
    connection = pymysql.connect(
        host=os.environ.get("IDAAS_DB_HOST", "192.168.175.86"),
        port=int(os.environ.get("IDAAS_DB_PORT", "3306")),
        user=os.environ.get("IDAAS_DB_USER", "root"),
        password=password,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                f"SELECT app_id,receiver_instance_id,enabled,last_test_result,mapping_json "
                f"FROM {CONTROL}.iam_sync_config_t WHERE tid=%s AND is_del=0 FOR UPDATE",
                (CONFIG_ID,),
            )
            config = cursor.fetchone()
            if not config or config[:4] != (APP_ID, INSTANCE, 1, "SUCCESS"):
                raise RuntimeError("Target receiver configuration is absent or not ready.")
            cursor.execute(
                f"SELECT tenant_id,local_permission_app_id,enabled "
                f"FROM {TENANT_DB}.idaas_receiver_binding_t "
                f"WHERE app_id=%s AND instance_id=%s FOR UPDATE",
                (APP_ID, INSTANCE),
            )
            binding = cursor.fetchone()
            if not binding or binding[0] != APP_ID or binding[2] != 1:
                raise RuntimeError("Public-safety receiver binding does not match.")
            local_app_id = binding[1]
            cursor.execute(
                f"SELECT tid,name,parent_id FROM {CONTROL}.iam_org_t "
                "WHERE identity_domain='workforce' AND is_del=0"
            )
            central_orgs = cursor.fetchall()
            cursor.execute(
                f"SELECT id,name,parent_id FROM {TENANT_DB}.rm_org_t "
                "WHERE tenant_id=%s AND deleted=0",
                (APP_ID,),
            )
            local_orgs = {row[0]: row for row in cursor.fetchall()}
            missing_orgs = [
                source_id for source_id, name, _ in central_orgs
                if source_id not in local_orgs or local_orgs[source_id][1] != name
            ]
            if missing_orgs:
                raise RuntimeError(f"Unverified organization IDs: {missing_orgs}")
            parent_differences = sum(
                parent_id != local_orgs[source_id][2]
                for source_id, _, parent_id in central_orgs
            )
            cursor.execute(
                f"SELECT tid,code,name FROM {CONTROL}.iam_role_t "
                "WHERE app_id=%s AND identity_domain='workforce' "
                "AND status='ACTIVE' AND is_del=0",
                (APP_ID,),
            )
            central_roles = cursor.fetchall()
            cursor.execute(
                f"SELECT id,code_num,name FROM {TENANT_DB}.rm_role_t "
                "WHERE tenant_id=%s AND app_id=%s AND status=0 AND deleted=0",
                (APP_ID, local_app_id),
            )
            local_roles = {row[0]: row for row in cursor.fetchall()}
            missing_roles = [
                source_id for source_id, code, name in central_roles
                if local_roles.get(source_id) != (source_id, code, name)
            ]
            if missing_roles:
                raise RuntimeError(f"Unverified role IDs: {missing_roles}")
            role_ids = [source_id for source_id, _, _ in central_roles]
            if role_ids:
                placeholders = ",".join(["%s"] * len(role_ids))
                cursor.execute(
                    f"SELECT relation.role_id,COUNT(*) FROM {TENANT_DB}.rm_role_menu_rela_t relation "
                    f"JOIN {TENANT_DB}.rm_menu_t menu ON menu.id=relation.app_resource_id "
                    "AND menu.tenant_id=relation.tenant_id AND menu.deleted=0 AND menu.status=0 "
                    f"WHERE relation.tenant_id=%s AND relation.role_id IN ({placeholders}) "
                    "GROUP BY relation.role_id",
                    (APP_ID, *role_ids),
                )
                menu_counts = dict(cursor.fetchall())
                without_menu = [role_id for role_id in role_ids if menu_counts.get(role_id, 0) == 0]
                if without_menu:
                    raise RuntimeError(f"Local roles without an enabled menu: {without_menu}")
            cursor.execute(
                f"SELECT source_id,object_type,local_id,instance_id,management_state "
                f"FROM {TENANT_DB}.idaas_receiver_object_t "
                "WHERE app_id=%s AND object_type IN ('ORG','ROLE') FOR UPDATE",
                (APP_ID,),
            )
            existing = {(row[1], row[0]): row for row in cursor.fetchall()}
            pairs = [("ORG", row[0]) for row in central_orgs] + [
                ("ROLE", row[0]) for row in central_roles
            ]
            for kind, source_id in pairs:
                row = existing.get((kind, source_id))
                if row and row[2:] != (source_id, INSTANCE, "MANAGED"):
                    raise RuntimeError(f"Conflicting receiver link: {kind}/{source_id}")
            to_insert = [
                (APP_ID, INSTANCE, kind, source_id, source_id)
                for kind, source_id in pairs if (kind, source_id) not in existing
            ]
            report = {
                "organization_links": len(central_orgs),
                "role_links": len(central_roles),
                "new_links": len(to_insert),
                "organization_parent_differences": parent_differences,
                "mode": "EXISTING_LOCAL",
                "applied": args.apply,
            }
            if args.apply:
                if to_insert:
                    cursor.executemany(
                        f"INSERT INTO {TENANT_DB}.idaas_receiver_object_t"
                        "(app_id,instance_id,object_type,source_id,local_id,"
                        "source_version,source_enabled,management_state) "
                        "VALUES (%s,%s,%s,%s,%s,0,1,'MANAGED')",
                        to_insert,
                    )
                desired_policy = {"mode": "EXISTING_LOCAL"}
                desired = json.dumps(desired_policy, separators=(",", ":"))
                existing_policy = json.loads(config[4]) if config[4] else None
                if existing_policy not in (None, desired_policy):
                    raise RuntimeError("An existing mapping policy would be overwritten.")
                if existing_policy != desired_policy:
                    cursor.execute(
                        f"UPDATE {CONTROL}.iam_sync_config_t SET mapping_json=%s,"
                        "version=version+1,updated_time=utc_timestamp(6) "
                        "WHERE tid=%s AND app_id=%s",
                        (desired, CONFIG_ID, APP_ID),
                    )
                connection.commit()
            else:
                connection.rollback()
            print(json.dumps(report, ensure_ascii=False))
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    main()
