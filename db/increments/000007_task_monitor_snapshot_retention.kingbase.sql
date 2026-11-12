-- 适用 baseline_ct.public 已有监控快照表和 tid 主键的金仓行业库。
-- 核对目标与现有索引后保存结构备份，在低峰期执行。检查索引顺序和到期查询计划后启用清理任务。
-- 现有同等索引先核对。回退时先停清理任务，再按原 DDL 移除本索引。
SELECT current_database(), current_schema();
SELECT indexname, indexdef FROM pg_indexes WHERE schemaname='public' AND tablename='data_access_task_monitor_snap_t';
BEGIN;
SET LOCAL lock_timeout='5s';
CREATE INDEX idx_task_monitor_snap_retention
  ON public.data_access_task_monitor_snap_t USING btree (tenant_id, monitor_time, tid);
COMMIT;
SELECT indexdef FROM pg_indexes WHERE schemaname='public' AND indexname='idx_task_monitor_snap_retention';
