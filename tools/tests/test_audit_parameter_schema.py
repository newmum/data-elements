"""行业日志参数的离线结构契约；只读版本源，不加载连接配置、不导入数据库工具。"""

import hashlib
import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
DB = ROOT / "db"
INCREMENT = "000008_audit_parameter_snapshot"
TABLES = ("log_request_t", "log_system_t")
SCHEMAS = (
    ("baseline_ga", "mysql", None, "2084109831682699264"),
    ("baseline_sea_fishery", "mysql", None, "2084109831682699266"),
    ("baseline_ct", "kingbase", "public", "2084109831682699267"),
    ("baseline_beijing_gd", "dameng", "baseline_beijing_gd", "2084109831682699265"),
)
COMMENTS = {
    "parameter_snapshot": "日志管理-安全参数快照，仅保存脱敏限长后的内容",
    "parameter_search_text": "日志管理-由同一安全参数快照生成的参数检索投影",
    "parameter_capture_status": (
        "日志管理-参数采集状态：EMPTY/CAPTURED/PARTIAL/OMITTED/FAILED/LEGACY；NULL表示历史未采集"
    ),
}


def table_reference(table, schema):
    return f'"{schema}"."{table}"' if schema else f"`{table}`"


def table_body(sql, table, schema):
    reference = re.escape(table_reference(table, schema))
    matches = re.findall(rf"\bCREATE TABLE\s+{reference}\s*\((.*?);", sql, re.S | re.I)
    if len(matches) != 1:
        raise AssertionError(f"Expected exactly one CREATE TABLE for {table}: {len(matches)}")
    return matches[0]


def column_definition(body, column):
    matches = re.findall(rf'(?mi)^\s*(?:ADD\s+COLUMN\s+)?[`"]{column}[`"]\s+([^\n]+)', body)
    if len(matches) != 1:
        raise AssertionError(f"Expected exactly one definition for {column}: {len(matches)}")
    return matches[0].rstrip(",")


def column_comment(sql, body, table, schema, column):
    if schema:
        target = re.escape(f'{table_reference(table, schema)}."{column}"')
        matches = re.findall(rf"COMMENT ON COLUMN {target} IS '([^']*)';", sql)
    else:
        matches = re.findall(r"\bCOMMENT '([^']*)'", column_definition(body, column))
    if len(matches) != 1:
        raise AssertionError(f"Expected exactly one comment for {table}.{column}: {len(matches)}")
    return matches[0]


def executable_sql(sql):
    # 增量说明均为整行注释；去掉说明后只检查实际 SQL，不把“禁止回填”等说明误判为数据脚本。
    return re.sub(r"(?m)^\s*--[^\n]*", "", sql)


class AuditParameterSchemaTests(unittest.TestCase):
    def test_four_industries_have_nullable_native_columns_and_matching_comments(self):
        for name, dialect, schema, _ in SCHEMAS:
            sql = (DB / name / "init.sql").read_text(encoding="utf-8")
            for table in TABLES:
                body = table_body(sql, table, schema)
                for column, comment in COMMENTS.items():
                    with self.subTest(schema=name, table=table, column=column):
                        definition = column_definition(body, column)
                        ddl_type = definition.split("COMMENT", 1)[0]
                        if column == "parameter_capture_status":
                            self.assertRegex(
                                ddl_type.lower(),
                                r"^(?:varchar|character varying)\(24(?: char)?\)(?=\s|$)",
                            )
                        else:
                            self.assertRegex(ddl_type.lower(), r"^longtext\b" if dialect == "mysql" else r"^text\b")
                        self.assertNotRegex(ddl_type.upper(), r"\bNOT\s+NULL\b")
                        # 实库只读导出可能省略隐式 NULL/DEFAULT NULL，但不能出现其他默认值。
                        self.assertNotRegex(ddl_type.upper(), r"\bDEFAULT\s+(?!NULL\b)\S+")
                        self.assertEqual(column_comment(sql, body, table, schema, column), comment)

    def test_existing_tenant_defaults_primary_keys_and_legacy_columns_remain(self):
        for name, _, schema, tenant_id in SCHEMAS:
            sql = (DB / name / "init.sql").read_text(encoding="utf-8")
            for table, legacy in (("log_request_t", "req_param"), ("log_system_t", "post_params")):
                with self.subTest(schema=name, table=table):
                    body = table_body(sql, table, schema)
                    tenant = column_definition(body, "tenant_id")
                    self.assertIn(f"'{tenant_id}'", tenant)
                    self.assertIn("NOT NULL", tenant.upper())
                    self.assertRegex(body, r'PRIMARY KEY\s*\([`"]?tid[`"]?\)')
                    column_definition(body, legacy)
                    column_definition(body, "operation_name")

    def test_no_new_parameter_indexes_are_claimed_for_substring_search(self):
        for name, _, schema, _ in SCHEMAS:
            sql = (DB / name / "init.sql").read_text(encoding="utf-8")
            for table in TABLES:
                body = table_body(sql, table, schema)
                indexes = re.findall(r"(?m)^.*\b(?:KEY|INDEX)\b[^\n]*", body)
                # 原生索引位于表定义外；额外扫描独立的 CREATE INDEX 语句。
                indexes += re.findall(r"(?im)^CREATE[^;]*\bINDEX\b[^;]*;", sql)
                for index in indexes:
                    with self.subTest(schema=name, index=index[:80]):
                        self.assertNotRegex(index, r"\bparameter_(?:snapshot|search_text|capture_status)\b")

    def test_increment_files_only_add_six_nullable_columns_without_data_changes(self):
        for dialect in ("mysql", "dameng", "kingbase"):
            sql = (DB / "increments" / f"{INCREMENT}.{dialect}.sql").read_text(encoding="utf-8")
            statements = executable_sql(sql)
            with self.subTest(dialect=dialect):
                self.assertNotRegex(
                    statements,
                    r"(?i)\b(?:INSERT|UPDATE|DELETE|MERGE|REPLACE|TRUNCATE|DROP|GRANT|CALL)\b",
                )
                self.assertNotRegex(statements, r"(?im)^\s*CREATE\s+(?:UNIQUE\s+)?(?:INDEX|TABLE|DATABASE|SCHEMA)\b")
                alterations = re.findall(r"\bALTER TABLE\s+(.*?);", statements, re.S | re.I)
                self.assertEqual(len(alterations), 2)
                schema = {"mysql": None, "dameng": "baseline_beijing_gd", "kingbase": "public"}[dialect]
                for table, alteration in zip(TABLES, alterations):
                    self.assertTrue(alteration.startswith(table_reference(table, schema)))
                    self.assertNotRegex(alteration, r"(?i)\b(?:MODIFY|CHANGE|RENAME|CONSTRAINT|INDEX)\b")
                    self.assertEqual(len(re.findall(r"\bADD\b", alteration, re.I)), 1 if dialect == "dameng" else 3)
                    for column, expected_comment in COMMENTS.items():
                        definition = re.search(rf'[`"]{column}[`"]\s+([^\n]+)', alteration)
                        self.assertIsNotNone(definition, column)
                        declaration = definition[1].split("COMMENT", 1)[0].rstrip(",")
                        if column == "parameter_capture_status":
                            self.assertRegex(declaration, r"(?i)\bNULL\s+DEFAULT\s+NULL\b")
                            self.assertRegex(declaration, r"(?i)(?:VARCHAR|character varying)\(24(?: char)?\)")
                        else:
                            self.assertRegex(declaration, r"(?i)^LONGTEXT\b" if dialect == "mysql" else r"(?i)^TEXT\b")
                        self.assertNotRegex(declaration, r"(?i)\bNOT\s+NULL\b")
                        self.assertEqual(column_comment(statements, alteration, table, schema, column), expected_comment)
                self.assertNotRegex(statements, r"(?i)\b(?:iam_\w+|req_param|post_params)\b")

    def test_increment_instructions_keep_review_checks_and_release_boundaries_explicit(self):
        for dialect in ("mysql", "dameng", "kingbase"):
            sql = (DB / "increments" / f"{INCREMENT}.{dialect}.sql").read_text(encoding="utf-8")
            with self.subTest(dialect=dialect):
                for text in (
                    "生产执行必须审阅", "适用旧结构", "前置结构检查", "后置检查", "备份", "回退",
                    "历史未采集", "同一安全参数快照", "任意子串", "不新增索引", "chengtian 旧恢复源",
                    "baseline_ga", "baseline_sea_fishery", "baseline_ct.public", "baseline_beijing_gd",
                    "MySQL", "KingbaseES", "达梦", "后端和 Magic/低代码资源",
                ):
                    self.assertIn(text, sql)
                ddl = sql.index("ALTER TABLE")
                self.assertLess(sql.index("SELECT"), ddl)
                self.assertGreater(sql.rindex("SELECT"), ddl)

    def test_manifest_hashes_and_source_only_status_are_truthful(self):
        manifest = json.loads((DB / "schema-manifest.json").read_text(encoding="utf-8"))
        entries = {entry["schema"]: entry for entry in manifest["schemas"]}
        for name, dialect, _, _ in SCHEMAS:
            with self.subTest(schema=name):
                entry = entries[name]
                source = (DB / entry["file"]).read_text(encoding="utf-8")
                self.assertEqual(entry["dialect"], dialect)
                self.assertEqual(entry["sha256"], hashlib.sha256(source.encode("utf-8")).hexdigest())
                self.assertEqual(entry["objects"]["tables"], len(re.findall(r"(?m)^CREATE TABLE\b", source)))
                # 实库重新 export 后工具会重建条目、移除此临时源码状态；离线测试不伪造实库验证。
                status = entry.get("sourceStatus")
                if "000008 日志安全参数三列仅更新版本源" in source:
                    self.assertIsNotNone(status)
                if status is not None:
                    self.assertEqual(status["state"], "source-updated-unverified")
                    self.assertEqual(status["increment"], INCREMENT)
                    self.assertIs(status["databaseVerified"], False)
                    self.assertIn("未连接实库核验", status["note"])
                    self.assertNotIn("connected", status)
                    self.assertNotIn("verifiedAt", status)


if __name__ == "__main__":
    unittest.main()