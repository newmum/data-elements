-- 适用：已核实现行结构的行业库。
-- 前置：确认目标为行业租户数据库，核对当前版本、表名和索引，并保存结构快照。
-- 执行：先创建表和索引，再注册模板 API 和组件，最后验证租户隔离。
-- 后置：核对主键、版本唯一键和列表索引，并执行 tools/check-db.py 与 tools/export-db.py。
-- 回退：先停用入口；有版本记录或任务引用时保留表，另行审阅备份和清理范围。

CREATE TABLE "public"."nifi_access_template_t" (
  "tid" VARCHAR(32) NOT NULL,
  "tenant_id" VARCHAR(32) NOT NULL,
  "family_id" VARCHAR(32) NOT NULL,
  "revision_no" INT NOT NULL,
  "display_name" VARCHAR(96) NOT NULL,
  "status" VARCHAR(16) NOT NULL,
  "definition" text NOT NULL,
  "created_by" VARCHAR(64) NOT NULL,
  "created_at" BIGINT NOT NULL,
  "updated_at" BIGINT NOT NULL,
  PRIMARY KEY ("tid"),
  CONSTRAINT "uk_access_template_version" UNIQUE ("tenant_id","family_id","revision_no")
);
CREATE INDEX "idx_access_template_list" ON "public"."nifi_access_template_t" ("tenant_id","status","updated_at","tid");
COMMENT ON TABLE "public"."nifi_access_template_t" IS '数据接入-NiFi接入模板版本';
COMMENT ON COLUMN "public"."nifi_access_template_t"."tid" IS '模板版本标识';
COMMENT ON COLUMN "public"."nifi_access_template_t"."tenant_id" IS '所属行业租户标识';
COMMENT ON COLUMN "public"."nifi_access_template_t"."family_id" IS '模板标识';
COMMENT ON COLUMN "public"."nifi_access_template_t"."revision_no" IS '模板内递增版本号';
COMMENT ON COLUMN "public"."nifi_access_template_t"."display_name" IS '模板显示名称';
COMMENT ON COLUMN "public"."nifi_access_template_t"."status" IS '版本状态：DRAFT草稿、PUBLISHED已发布、RETIRED已停用';
COMMENT ON COLUMN "public"."nifi_access_template_t"."definition" IS '接入拓扑、目标类型及处理器默认配置，不含连接凭据';
COMMENT ON COLUMN "public"."nifi_access_template_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "public"."nifi_access_template_t"."created_at" IS '创建时间戳，Unix毫秒';
COMMENT ON COLUMN "public"."nifi_access_template_t"."updated_at" IS '更新时间戳，Unix毫秒';
