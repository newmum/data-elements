-- 退役布控管理专用表，适用于已停用布控 API、菜单、任务和原生 NiFi 组的已有行业库。
-- 先核对部署绑定、13 张表的名称和对象类型、外键及全实例可见的视图、触发器、例程、同义词。
-- 应核对已登记服务、任务和外部使用者，结果应无外部依赖。保存原生定义、索引、注释、授权及行数。
-- 有数据时备份实际记录并校验可恢复，恢复材料保存到仓库外的受限位置。
-- 逐库执行，确认前置结果与备份一致再进入删除阶段。失败时停止，先核对实际缺失的表再从原生备份恢复。
-- 完成后使用当前绑定运行 tools/check-db.py 和 tools/export-db.py，核对完整结构。
-- MySQL 目标为 baseline_ga 和 baseline_sea_fishery，只使用当前实例的实际行业连接。
-- MySQL DDL 隐式提交，不能通过事务撤销。

-- 阶段 1，确认当前库、专用表及依赖。
SELECT DATABASE() AS target_schema;
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
  AND table_name IN (
    'surveillance_control_item_t', 'surveillance_kafka_connection_t', 'surveillance_kafka_topic_t',
    'surveillance_metric_batch_t', 'surveillance_resource_topic_t', 'surveillance_result_t',
    'surveillance_rule_definition_t', 'surveillance_rule_snapshot_t', 'surveillance_service_identity_t',
    'surveillance_task_deployment_t', 'surveillance_task_history_t', 'surveillance_task_lifecycle_t',
    'surveillance_topic_daily_stat_t'
  )
ORDER BY table_name;
SELECT table_schema, table_name, referenced_table_name
FROM information_schema.key_column_usage
WHERE referenced_table_schema = DATABASE() AND referenced_table_name IN (
    'surveillance_control_item_t', 'surveillance_kafka_connection_t', 'surveillance_kafka_topic_t',
    'surveillance_metric_batch_t', 'surveillance_resource_topic_t', 'surveillance_result_t',
    'surveillance_rule_definition_t', 'surveillance_rule_snapshot_t', 'surveillance_service_identity_t',
    'surveillance_task_deployment_t', 'surveillance_task_history_t', 'surveillance_task_lifecycle_t',
    'surveillance_topic_daily_stat_t'
  );
SELECT view_schema, view_name, table_name
FROM information_schema.view_table_usage
WHERE table_schema = DATABASE() AND table_name IN (
    'surveillance_control_item_t', 'surveillance_kafka_connection_t', 'surveillance_kafka_topic_t',
    'surveillance_metric_batch_t', 'surveillance_resource_topic_t', 'surveillance_result_t',
    'surveillance_rule_definition_t', 'surveillance_rule_snapshot_t', 'surveillance_service_identity_t',
    'surveillance_task_deployment_t', 'surveillance_task_history_t', 'surveillance_task_lifecycle_t',
    'surveillance_topic_daily_stat_t'
  );

-- 阶段 2，删除已审核的专用表。
SET SESSION lock_wait_timeout = 5;
DROP TABLE
    `surveillance_control_item_t`,
    `surveillance_kafka_connection_t`,
    `surveillance_kafka_topic_t`,
    `surveillance_metric_batch_t`,
    `surveillance_resource_topic_t`,
    `surveillance_result_t`,
    `surveillance_rule_definition_t`,
    `surveillance_rule_snapshot_t`,
    `surveillance_service_identity_t`,
    `surveillance_task_deployment_t`,
    `surveillance_task_history_t`,
    `surveillance_task_lifecycle_t`,
    `surveillance_topic_daily_stat_t`;

-- 阶段 3，专用表查询预期零行。
SELECT DATABASE() AS target_schema;
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
  AND table_name IN (
    'surveillance_control_item_t', 'surveillance_kafka_connection_t', 'surveillance_kafka_topic_t',
    'surveillance_metric_batch_t', 'surveillance_resource_topic_t', 'surveillance_result_t',
    'surveillance_rule_definition_t', 'surveillance_rule_snapshot_t', 'surveillance_service_identity_t',
    'surveillance_task_deployment_t', 'surveillance_task_history_t', 'surveillance_task_lifecycle_t',
    'surveillance_topic_daily_stat_t'
  )
ORDER BY table_name;
