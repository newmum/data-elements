-- 适用：现行 nifi_node_t、push_node_t、service_node_t 尚无 last_health_* 列的行业业务库。
-- 范围：实际绑定的公安、海渔、广电、澄天对应方言行业库；不在共享控制库或外部 NiFi 数据库执行。
-- 前置：备份三表完整结构，核对六列均不存在、tenant_id/tid/is_del/updated_time 完整；如已有同名列先比较后停止重复执行。
-- 顺序：先审阅并执行本方言增量，再发布节点快照 Function81、两个节点列表接口与节点配置页面。
-- 本增量仅增加快照列，不改账号、口令、地址、默认节点、任务、队列或来源水位，不回填历史检测为成功。
ALTER TABLE "nifi_node_t" ADD "last_health_status" VARCHAR(16) DEFAULT 'unknown' NOT NULL;

ALTER TABLE "nifi_node_t" ADD "last_health_at" TIMESTAMP(6);

ALTER TABLE "nifi_node_t" ADD "last_health_message" VARCHAR(1000);

ALTER TABLE "nifi_node_t" ADD "last_health_latency_ms" BIGINT;

ALTER TABLE "nifi_node_t" ADD "last_health_revision" VARCHAR(64);

ALTER TABLE "nifi_node_t" ADD "last_health_check_id" VARCHAR(32);

COMMENT ON COLUMN "nifi_node_t"."last_health_status" IS '最近连通检测状态：unknown未检测、reachable可连通、unreachable不可连通';

COMMENT ON COLUMN "nifi_node_t"."last_health_at" IS '最近连通检测完成时间';

COMMENT ON COLUMN "nifi_node_t"."last_health_message" IS '最近连通检测结果说明，凭据已脱敏';

COMMENT ON COLUMN "nifi_node_t"."last_health_latency_ms" IS '最近连通检测耗时，单位毫秒';

COMMENT ON COLUMN "nifi_node_t"."last_health_revision" IS '检测对应的节点配置版本，修改配置后旧结果失效';

COMMENT ON COLUMN "nifi_node_t"."last_health_check_id" IS '检测发起序号，防止旧检测覆盖新结果';

ALTER TABLE "push_node_t" ADD "last_health_status" VARCHAR(16) DEFAULT 'unknown' NOT NULL;

ALTER TABLE "push_node_t" ADD "last_health_at" TIMESTAMP(6);

ALTER TABLE "push_node_t" ADD "last_health_message" VARCHAR(1000);

ALTER TABLE "push_node_t" ADD "last_health_latency_ms" BIGINT;

ALTER TABLE "push_node_t" ADD "last_health_revision" VARCHAR(64);

ALTER TABLE "push_node_t" ADD "last_health_check_id" VARCHAR(32);

COMMENT ON COLUMN "push_node_t"."last_health_status" IS '最近连通检测状态：unknown未检测、reachable可连通、unreachable不可连通';

COMMENT ON COLUMN "push_node_t"."last_health_at" IS '最近连通检测完成时间';

COMMENT ON COLUMN "push_node_t"."last_health_message" IS '最近连通检测结果说明，凭据已脱敏';

COMMENT ON COLUMN "push_node_t"."last_health_latency_ms" IS '最近连通检测耗时，单位毫秒';

COMMENT ON COLUMN "push_node_t"."last_health_revision" IS '检测对应的节点配置版本，修改配置后旧结果失效';

COMMENT ON COLUMN "push_node_t"."last_health_check_id" IS '检测发起序号，防止旧检测覆盖新结果';

ALTER TABLE "service_node_t" ADD "last_health_status" VARCHAR(16) DEFAULT 'unknown' NOT NULL;

ALTER TABLE "service_node_t" ADD "last_health_at" TIMESTAMP(6);

ALTER TABLE "service_node_t" ADD "last_health_message" VARCHAR(1000);

ALTER TABLE "service_node_t" ADD "last_health_latency_ms" BIGINT;

ALTER TABLE "service_node_t" ADD "last_health_revision" VARCHAR(64);

ALTER TABLE "service_node_t" ADD "last_health_check_id" VARCHAR(32);

COMMENT ON COLUMN "service_node_t"."last_health_status" IS '最近连通检测状态：unknown未检测、reachable可连通、unreachable不可连通';

COMMENT ON COLUMN "service_node_t"."last_health_at" IS '最近连通检测完成时间';

COMMENT ON COLUMN "service_node_t"."last_health_message" IS '最近连通检测结果说明，凭据已脱敏';

COMMENT ON COLUMN "service_node_t"."last_health_latency_ms" IS '最近连通检测耗时，单位毫秒';

COMMENT ON COLUMN "service_node_t"."last_health_revision" IS '检测对应的节点配置版本，修改配置后旧结果失效';

COMMENT ON COLUMN "service_node_t"."last_health_check_id" IS '检测发起序号，防止旧检测覆盖新结果';
-- 后置：核对三表原行数不变、六列类型/默认/注释正确；验证检测后刷新、重新登录与重启回显，配置修改和并发旧结果保护。
-- 执行 tools/check-db.py 与 tools/export-db.py，核对全部行业结构；既有差异原位报告，不以本增量修复无关历史结构。
-- 回退：先退回调用方并保存新增检测快照，再审阅删除这六列；不能执行完整初始化文件重建已有表。
