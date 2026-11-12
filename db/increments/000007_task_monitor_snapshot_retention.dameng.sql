-- 适用广电达梦行业库已有监控快照表和 tid 主键的结构。
-- 核对目标 schema 和现有索引，备份结构，低峰期通过带查询超时的连接单独执行 DDL。
-- 检查索引顺序和到期查询计划后启用清理任务。现有同等索引先核对。
-- 回退时先停清理任务，再按原 DDL 移除本索引。
SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS target_schema FROM DUAL;
SELECT INDEX_NAME, COLUMN_NAME, COLUMN_POSITION FROM DBA_IND_COLUMNS
  WHERE TABLE_OWNER='baseline_beijing_gd' AND TABLE_NAME='data_access_task_monitor_snap_t';
CREATE INDEX "idx_task_monitor_snap_retention"
  ON "baseline_beijing_gd"."data_access_task_monitor_snap_t" ("tenant_id", "monitor_time", "tid");
SELECT COLUMN_NAME, COLUMN_POSITION FROM DBA_IND_COLUMNS
  WHERE INDEX_OWNER='baseline_beijing_gd' AND INDEX_NAME='idx_task_monitor_snap_retention'
  ORDER BY COLUMN_POSITION;
