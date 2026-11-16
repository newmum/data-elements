-- 适用：已应用 000010 的现有行业库（baseline_beijing_gd）。
-- 前置：核对实际租户库，确认 data_access_native_flow_t 尚不存在，并保存结构快照。
-- 执行：先建表和索引，再注册 /ods/registry/native，最后发布页面。
-- 后置：检查表、注释、主键、唯一键、分页索引，并运行 tools/check-db.py、tools/export-db.py。
-- 回退：先停用调用入口；有记录时须备份，是否移除表另行确认。

CREATE TABLE "baseline_beijing_gd"."data_access_native_flow_t" (
  "tid"       VARCHAR(32) NOT NULL,
  "tenant_id"       VARCHAR(32) NOT NULL,
  "source_table_id"       VARCHAR(32) NOT NULL,
  "node_id"       VARCHAR(32) NOT NULL,
  "node_name"       VARCHAR(128) NOT NULL,
  "process_group_id"       VARCHAR(36) NOT NULL,
  "group_name"       VARCHAR(512) NOT NULL,
  "created_by"       VARCHAR(32) NOT NULL,
  "created_at"       BIGINT NOT NULL,
  CONSTRAINT "pk_data_access_native_flow_t" PRIMARY KEY ("tid"),
  CONSTRAINT "uk_native_flow_tenant_group" UNIQUE ("tenant_id","node_id","process_group_id")
);
CREATE INDEX "idx_native_flow_tenant_time" ON "baseline_beijing_gd"."data_access_native_flow_t" ("tenant_id","created_at","tid");
COMMENT ON TABLE "baseline_beijing_gd"."data_access_native_flow_t" IS '数据接入-已登记来源表与NiFi原生流程只读关联';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_native_flow_t"."tid" IS '关联记录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_native_flow_t"."tenant_id" IS '所属行业租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_native_flow_t"."source_table_id" IS '已登记来源表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_native_flow_t"."node_id" IS 'NiFi节点标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_native_flow_t"."node_name" IS '建立关联时的节点名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_native_flow_t"."process_group_id" IS 'NiFi原生流程组UUID';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_native_flow_t"."group_name" IS '建立关联时的原生流程组名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_native_flow_t"."created_by" IS '创建关联的用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_native_flow_t"."created_at" IS '关联创建时间戳，Unix毫秒';
