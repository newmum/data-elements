-- 数据服务-补齐湖仓分层和服务网关15张公共业务表。
-- 适用起始结构：161表行业统一基线，且本增量列出的全部15表均不存在；
-- 任一表已存在即停止，逐表核对现行结构，不允许重复执行或DROP后重建。
-- 目标范围：当前环境实际绑定的行业业务库；不用于控制库、恢复来源库或独立海通库。
-- 本机公安/海渔/广电已经具有15表，仅澄天金仓缺表；部署时必须重新确认，
-- 不以本机库名推断生产库，也不复制其他租户的业务数据或tenant_id默认值。
-- 顺序：核对连接/缺表集合/161旧结构 -> 受限备份 -> 本增量 -> 结构检查和完整导出。
-- 后置：15表240列、49索引含15主键；无外键/生成列/自动更新时间触发器。
-- 回退：先停止新功能写入，核对新增表仍为空且无登记/外键/程序引用，
-- 再使用单独审阅的恢复材料处理；已有数据不得通过删表回退。
-- MySQL/达梦DDL可能隐式提交，不承诺事务回滚；执行前确认无长期事务并设置有界等锁。

-- 先由部署人员SET SCHEMA为已核对的行业业务schema；以下对象使用该schema。
SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS target_schema FROM DUAL;
SELECT TABLE_NAME FROM DBA_TABLES WHERE OWNER=SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AND TABLE_NAME IN ('lake_zone_object_t','lake_zone_rule_t','lake_zone_t','svc_feedback_t','svc_gateway_app_t','svc_gateway_instance_t','svc_gateway_log_t','svc_gateway_policy_t','svc_gateway_region_t','svc_gateway_route_t','svc_gray_release_t','svc_market_package_item_t','svc_market_package_t','svc_metric_hour_t','svc_subscription_t');
-- 上述存在表查询必须返回0行，否则停止；执行端设置有界JDBC查询超时，不改全局参数。

CREATE TABLE "lake_zone_object_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"zone_id" VARCHAR(32) NOT NULL,
"object_type" VARCHAR(16) NOT NULL,
"object_ref" VARCHAR(128) NOT NULL,
"object_name" VARCHAR(128),
"source_id" VARCHAR(32),
"lifecycle_status" VARCHAR(16) DEFAULT 'REGISTERED' NOT NULL,
"last_sync_at" TIMESTAMP(6),
"registered_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_lake_zone_object_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "lake_zone_rule_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"zone_id" VARCHAR(32) NOT NULL,
"rule_code" VARCHAR(32) NOT NULL,
"rule_name" VARCHAR(100) NOT NULL,
"rule_type" VARCHAR(8) NOT NULL,
"object_scope" VARCHAR(32) DEFAULT 'ALL' NOT NULL,
"file_format" VARCHAR(64),
"compression" VARCHAR(32),
"partition_rule" VARCHAR(256),
"retention_days" INT DEFAULT 0 NOT NULL,
"approval_required" SMALLINT DEFAULT 0 NOT NULL,
"interaction_mode" VARCHAR(32) DEFAULT 'API' NOT NULL,
"priority" INT DEFAULT 0 NOT NULL,
"status" VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
"description" VARCHAR(500),
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_lake_zone_rule_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "lake_zone_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"zone_code" VARCHAR(32) NOT NULL,
"zone_name" VARCHAR(100) NOT NULL,
"zone_type" VARCHAR(32) NOT NULL,
"datasource_id" VARCHAR(32),
"schema_name" VARCHAR(128),
"status" VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
"sort_no" INT DEFAULT 0 NOT NULL,
"description" VARCHAR(500),
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_lake_zone_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_feedback_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"service_code" VARCHAR(64) NOT NULL,
"app_id" VARCHAR(32),
"feedback_type" VARCHAR(32) DEFAULT 'ISSUE' NOT NULL,
"content" CLOB NOT NULL,
"status" VARCHAR(16) DEFAULT 'OPEN' NOT NULL,
"handler" VARCHAR(32),
"handle_result" VARCHAR(1000),
"closed_time" TIMESTAMP(6),
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_feedback_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_gateway_app_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"app_code" VARCHAR(64) NOT NULL,
"app_name" VARCHAR(100) NOT NULL,
"auth_type" VARCHAR(16) DEFAULT 'API_KEY' NOT NULL,
"client_id" VARCHAR(128),
"credential_ref" VARCHAR(256),
"contact" VARCHAR(100),
"status" VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
"description" VARCHAR(500),
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_gateway_app_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_gateway_instance_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"service_code" VARCHAR(64) NOT NULL,
"version" VARCHAR(32) DEFAULT 'v1' NOT NULL,
"instance_url" VARCHAR(512) NOT NULL,
"weight" INT DEFAULT 100 NOT NULL,
"health_status" VARCHAR(16) DEFAULT 'UNKNOWN' NOT NULL,
"last_heartbeat" TIMESTAMP(6),
"status" VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
"description" VARCHAR(500),
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_gateway_instance_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_gateway_log_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"trace_id" VARCHAR(64) NOT NULL,
"service_code" VARCHAR(64) NOT NULL,
"app_id" VARCHAR(32),
"log_type" VARCHAR(32) NOT NULL,
"uri" VARCHAR(512),
"method" VARCHAR(16),
"status_code" INT,
"cost_ms" INT,
"error_code" VARCHAR(64),
"client_ip" VARCHAR(64),
"region_code" VARCHAR(32) NOT NULL,
"request_hash" VARCHAR(64),
"response_size" INT,
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_gateway_log_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_gateway_policy_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"route_id" VARCHAR(32) NOT NULL,
"policy_code" VARCHAR(64) NOT NULL,
"policy_name" VARCHAR(100) NOT NULL,
"priority" INT DEFAULT 0 NOT NULL,
"auth_mode" VARCHAR(16) DEFAULT 'TOKEN' NOT NULL,
"ip_allow" VARCHAR(1000),
"ip_deny" VARCHAR(1000),
"qps" INT DEFAULT 0 NOT NULL,
"burst" INT DEFAULT 0 NOT NULL,
"circuit_threshold" INT DEFAULT 0 NOT NULL,
"circuit_window_ms" INT DEFAULT 60000 NOT NULL,
"retry_backoff_ms" INT DEFAULT 200 NOT NULL,
"param_rules_json" CLOB,
"status" VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
"description" VARCHAR(500),
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_gateway_policy_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_gateway_region_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"region_code" VARCHAR(32) NOT NULL,
"region_name" VARCHAR(100) NOT NULL,
"network_zone" VARCHAR(32),
"endpoint" VARCHAR(512),
"status" VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
"description" VARCHAR(500),
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_gateway_region_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_gateway_route_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"service_code" VARCHAR(64) NOT NULL,
"version" VARCHAR(32) DEFAULT 'v1' NOT NULL,
"path_prefix" VARCHAR(256) NOT NULL,
"target_type" VARCHAR(16) DEFAULT 'FLOW' NOT NULL,
"target_ref" VARCHAR(512) NOT NULL,
"region_code" VARCHAR(32) NOT NULL,
"timeout_ms" INT DEFAULT 3000 NOT NULL,
"retry_times" INT DEFAULT 0 NOT NULL,
"lb_strategy" VARCHAR(16) DEFAULT 'WEIGHT' NOT NULL,
"status" VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,
"description" VARCHAR(500),
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_gateway_route_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_gray_release_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"service_code" VARCHAR(64) NOT NULL,
"from_version" VARCHAR(32) NOT NULL,
"to_version" VARCHAR(32) NOT NULL,
"strategy" VARCHAR(16) DEFAULT 'WEIGHT' NOT NULL,
"match_value" VARCHAR(256),
"weight" INT DEFAULT 0 NOT NULL,
"status" VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,
"start_time" TIMESTAMP(6),
"end_time" TIMESTAMP(6),
"description" VARCHAR(500),
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_gray_release_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_market_package_item_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"package_id" VARCHAR(32) NOT NULL,
"service_code" VARCHAR(64) NOT NULL,
"version" VARCHAR(32) DEFAULT 'v1' NOT NULL,
"sort_no" INT DEFAULT 0 NOT NULL,
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_market_package_item_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_market_package_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"package_code" VARCHAR(64) NOT NULL,
"package_name" VARCHAR(100) NOT NULL,
"subscription_mode" VARCHAR(16) DEFAULT 'PACKAGE' NOT NULL,
"approval_required" SMALLINT DEFAULT 0 NOT NULL,
"status" VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,
"description" VARCHAR(1000),
"sort_no" INT DEFAULT 0 NOT NULL,
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_market_package_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_metric_hour_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"stat_hour" TIMESTAMP(6) NOT NULL,
"service_code" VARCHAR(64) NOT NULL,
"app_id" VARCHAR(32),
"call_count" BIGINT DEFAULT 0 NOT NULL,
"success_count" BIGINT DEFAULT 0 NOT NULL,
"fail_count" BIGINT DEFAULT 0 NOT NULL,
"p95_ms" INT DEFAULT 0 NOT NULL,
"active_users" INT DEFAULT 0 NOT NULL,
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_metric_hour_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE TABLE "svc_subscription_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"app_id" VARCHAR(32),
"service_code" VARCHAR(64) NOT NULL,
"version" VARCHAR(32) DEFAULT 'v1' NOT NULL,
"status" VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
"valid_from" TIMESTAMP(6),
"valid_until" TIMESTAMP(6),
"quota" BIGINT DEFAULT 0 NOT NULL,
"apply_id" VARCHAR(32),
"description" VARCHAR(500),
"created_by" VARCHAR(32),
"updated_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_svc_subscription_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_feedback_service" ON "svc_feedback_t"("tenant_id" ASC,"service_code" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_feedback_status" ON "svc_feedback_t"("tenant_id" ASC,"status" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_gateway_app_status" ON "svc_gateway_app_t"("tenant_id" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_gateway_instance_health" ON "svc_gateway_instance_t"("tenant_id" ASC,"service_code" ASC,"health_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_gateway_log_service" ON "svc_gateway_log_t"("tenant_id" ASC,"service_code" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_gateway_log_trace" ON "svc_gateway_log_t"("tenant_id" ASC,"trace_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_gateway_log_type" ON "svc_gateway_log_t"("tenant_id" ASC,"log_type" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_gateway_policy_route" ON "svc_gateway_policy_t"("tenant_id" ASC,"route_id" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_gateway_region_status" ON "svc_gateway_region_t"("tenant_id" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_gateway_route_prefix" ON "svc_gateway_route_t"("tenant_id" ASC,"path_prefix" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_gateway_route_status" ON "svc_gateway_route_t"("tenant_id" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_gray_release_status" ON "svc_gray_release_t"("tenant_id" ASC,"service_code" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_market_package_item_pkg" ON "svc_market_package_item_t"("tenant_id" ASC,"package_id" ASC,"sort_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_market_package_status" ON "svc_market_package_t"("tenant_id" ASC,"status" ASC,"sort_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_metric_hour_service" ON "svc_metric_hour_t"("tenant_id" ASC,"service_code" ASC,"stat_hour" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_svc_subscription_status" ON "svc_subscription_t"("tenant_id" ASC,"status" ASC,"valid_until" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_lake_zone_datasource" ON "lake_zone_t"("datasource_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_lake_zone_object_ref" ON "lake_zone_object_t"("object_ref" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_lake_zone_object_zone" ON "lake_zone_object_t"("tenant_id" ASC,"zone_id" ASC,"lifecycle_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_lake_zone_rule_zone" ON "lake_zone_rule_t"("tenant_id" ASC,"zone_id" ASC,"rule_type" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_lake_zone_tenant_status" ON "lake_zone_t"("tenant_id" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_svc_gateway_app" ON "svc_gateway_app_t"("tenant_id" ASC,"app_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_svc_gateway_instance" ON "svc_gateway_instance_t"("tenant_id" ASC,"service_code" ASC,"version" ASC,"instance_url" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_svc_gateway_policy" ON "svc_gateway_policy_t"("tenant_id" ASC,"route_id" ASC,"policy_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_svc_gateway_region" ON "svc_gateway_region_t"("tenant_id" ASC,"region_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_svc_gateway_route" ON "svc_gateway_route_t"("tenant_id" ASC,"service_code" ASC,"version" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_svc_gray_release" ON "svc_gray_release_t"("tenant_id" ASC,"service_code" ASC,"from_version" ASC,"to_version" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_svc_market_package" ON "svc_market_package_t"("tenant_id" ASC,"package_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_svc_market_package_item" ON "svc_market_package_item_t"("tenant_id" ASC,"package_id" ASC,"service_code" ASC,"version" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_svc_metric_hour" ON "svc_metric_hour_t"("tenant_id" ASC,"stat_hour" ASC,"service_code" ASC,"app_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_svc_subscription" ON "svc_subscription_t"("tenant_id" ASC,"app_id" ASC,"service_code" ASC,"version" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_lake_zone_code" ON "lake_zone_t"("tenant_id" ASC,"zone_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_lake_zone_object" ON "lake_zone_object_t"("tenant_id" ASC,"zone_id" ASC,"object_type" ASC,"object_ref" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_lake_zone_rule" ON "lake_zone_rule_t"("tenant_id" ASC,"zone_id" ASC,"rule_type" ASC,"rule_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

COMMENT ON TABLE "BASELINE_BEIJING_GD"."lake_zone_object_t" IS '数据资源（湖仓底座）-已纳管对象清单';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."lake_zone_rule_t" IS '数据资源（湖仓底座）-入出湖标准与交互规则';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."lake_zone_t" IS '数据资源（湖仓底座）-湖仓分层实例';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_feedback_t" IS '服务网关-服务反馈';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_gateway_app_t" IS '服务网关-接入应用';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_gateway_instance_t" IS '服务网关-服务实例与健康状态';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_gateway_log_t" IS '服务网关-请求与治理日志';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_gateway_policy_t" IS '服务网关-路由治理策略';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_gateway_region_t" IS '服务网关-网络区域';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_gateway_route_t" IS '服务网关-服务路由与目标配置';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_gray_release_t" IS '服务网关-灰度发布规则';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_market_package_item_t" IS '服务网关-服务合集明细';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_market_package_t" IS '服务网关-服务合集';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_metric_hour_t" IS '服务网关-小时级调用指标';

COMMENT ON TABLE "BASELINE_BEIJING_GD"."svc_subscription_t" IS '服务网关-服务订阅';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."zone_code" IS '分层编码：ODS原始库、BASE基础库、SUBJECT主题库、TOPIC专题库、LAKE_DW数据湖仓库区、LAKE_COLLECT数据湖汇聚区';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."zone_name" IS '分层名称';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."zone_type" IS '分层类型：ORIGIN/BASE/SUBJECT/TOPIC/LAKE';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."datasource_id" IS '承载数据源标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."schema_name" IS '物理模式名称';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."sort_no" IS '排序号';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."description" IS '分层说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."zone_id" IS '湖仓分层标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."rule_code" IS '规则编号，拒绝时返回该编号';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."rule_name" IS '规则名称';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."rule_type" IS '规则方向：IN入湖、OUT出湖';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."object_scope" IS '适用对象：ALL/TABLE/VIEW/FILE/API';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."file_format" IS '允许的文件格式，逗号分隔';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."compression" IS '允许的压缩方式，逗号分隔';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."partition_rule" IS '分区规则说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."retention_days" IS '保留天数，0表示不限制';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."approval_required" IS '是否需要审批：0否，1是';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."interaction_mode" IS '交互方式：API/FILE/JDBC/STREAM';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."priority" IS '规则优先级，数值越大越先校验';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."description" IS '规则说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_rule_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."zone_id" IS '湖仓分层标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."object_type" IS '对象类型：TABLE/VIEW/FILE/API';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."object_ref" IS '对象引用：表标识、视图名、文件路径或接口编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."object_name" IS '对象名称';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."source_id" IS '来源数据源或来源对象标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."lifecycle_status" IS '生命周期：REGISTERED已登记、ONLINE在线、OFFLINE下线、ARCHIVED归档';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."last_sync_at" IS '最近同步时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."registered_by" IS '登记人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."lake_zone_object_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."version" IS '服务版本';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."path_prefix" IS '网关路径前缀';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."target_type" IS '目标类型：FLOW/HTTP/DB/KAFKA/ES';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."target_ref" IS '目标引用：FlowServe 服务标识、URL、表名或主题';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."region_code" IS '网络区域编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."timeout_ms" IS '请求超时毫秒数';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."retry_times" IS '失败重试次数，仅幂等方法生效';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."lb_strategy" IS '负载均衡策略：WEIGHT/ROUND_ROBIN';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."status" IS '状态：DRAFT草稿、PUBLISHED已发布、DISABLED停用';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."description" IS '路由说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_route_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."route_id" IS '所属路由标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."policy_code" IS '策略编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."policy_name" IS '策略名称';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."priority" IS '生效优先级，数值越大越先应用';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."auth_mode" IS '鉴权方式：NONE/TOKEN/HMAC/OAUTH2';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."ip_allow" IS 'IP 白名单，逗号分隔，为空表示不限制';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."ip_deny" IS 'IP 黑名单，逗号分隔';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."qps" IS '每秒请求上限，0 表示不限制';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."burst" IS '令牌桶突发容量';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."circuit_threshold" IS '熔断失败阈值，0 表示关闭熔断';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."circuit_window_ms" IS '熔断统计窗口毫秒数';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."retry_backoff_ms" IS '重试退避毫秒数';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."param_rules_json" IS '参数校验规则 JSON';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."description" IS '策略说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_policy_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."region_code" IS '区域编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."region_name" IS '区域名称';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."network_zone" IS '网络分区：INTERNET互联网区、GOV政务外网区、CROSS跨网区';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."endpoint" IS '区域入口地址';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."description" IS '区域说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_region_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."app_code" IS '应用编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."app_name" IS '应用名称';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."auth_type" IS '鉴权类型：NONE/API_KEY/HMAC/OAUTH2';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."client_id" IS '客户端标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."credential_ref" IS '凭证引用，禁止保存明文密钥';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."contact" IS '联系人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."description" IS '应用说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_app_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."version" IS '服务版本';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."instance_url" IS '实例地址';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."weight" IS '负载权重';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."health_status" IS '健康状态：UNKNOWN/UP/DOWN';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."last_heartbeat" IS '最近心跳时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."description" IS '实例说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_instance_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."from_version" IS '原版本';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."to_version" IS '灰度版本';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."strategy" IS '灰度策略：WEIGHT/HEADER/IP';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."match_value" IS '匹配值：Header 值或 IP 段';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."weight" IS '灰度流量权重百分比';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."status" IS '状态：DRAFT草稿、RUNNING运行中、STOPPED已停止';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."start_time" IS '开始时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."end_time" IS '结束时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."description" IS '灰度说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gray_release_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."trace_id" IS '链路追踪标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."app_id" IS '调用应用标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."log_type" IS '日志类型：REQUEST/EVENT/ERROR/FLOW_LIMIT/HEALTH/IP_LIMIT/CIRCUIT/PROTOCOL/AUTH';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."uri" IS '请求路径';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."method" IS 'HTTP 方法';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."status_code" IS '响应状态码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."cost_ms" IS '耗时毫秒';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."error_code" IS '错误编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."client_ip" IS '客户端 IP';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."region_code" IS '网络区域编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."request_hash" IS '请求摘要';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."response_size" IS '响应字节数';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_gateway_log_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."stat_hour" IS '统计小时';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."app_id" IS '调用应用标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."call_count" IS '调用总次数';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."success_count" IS '成功次数';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."fail_count" IS '失败次数';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."p95_ms" IS 'P95 耗时毫秒';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."active_users" IS '活跃应用数';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_metric_hour_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."app_id" IS '订阅应用标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."version" IS '服务版本';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."status" IS '状态：PENDING待审批、ACTIVE生效、EXPIRED过期、REVOKED撤销';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."valid_from" IS '生效时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."valid_until" IS '失效时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."quota" IS '调用配额，0 表示不限制';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."apply_id" IS '审批单标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."description" IS '订阅说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_subscription_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."app_id" IS '反馈应用标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."feedback_type" IS '反馈类型：ISSUE问题、SUGGEST建议、OTHER其他';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."content" IS '反馈内容';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."status" IS '状态：OPEN待处理、PROCESSING处理中、CLOSED已关闭';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."handler" IS '处理人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."handle_result" IS '处理结果';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."closed_time" IS '闭环时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_feedback_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."package_code" IS '合集编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."package_name" IS '合集名称';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."subscription_mode" IS '订阅模式：SINGLE单服务、PACKAGE整套';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."approval_required" IS '是否需要审批：0否，1是';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."status" IS '状态：DRAFT草稿、ON_SHELF上架、OFF_SHELF下架';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."description" IS '合集说明';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."sort_no" IS '排序号';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."tid" IS '主键标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."package_id" IS '合集标识';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."version" IS '服务版本';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."sort_no" IS '排序号';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."created_by" IS '创建人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "BASELINE_BEIJING_GD"."svc_market_package_item_t"."is_del" IS '删除标志：0正常，1删除';

SELECT COUNT(*) AS expected_15_tables FROM DBA_TABLES WHERE OWNER=SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AND TABLE_NAME IN ('lake_zone_object_t','lake_zone_rule_t','lake_zone_t','svc_feedback_t','svc_gateway_app_t','svc_gateway_instance_t','svc_gateway_log_t','svc_gateway_policy_t','svc_gateway_region_t','svc_gateway_route_t','svc_gray_release_t','svc_market_package_item_t','svc_market_package_t','svc_metric_hour_t','svc_subscription_t');
