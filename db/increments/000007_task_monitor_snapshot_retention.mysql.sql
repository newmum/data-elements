-- 适用已存在 data_access_task_monitor_snap_t、tid 主键和 tenant_id / monitor_time 列的 MySQL 行业库。
-- 先核对实际租户库及索引，备份表结构，确认无长期事务和足够的磁盘空间。
-- 再执行本增量、检查索引列及顺序、核对到期查询执行计划，然后启用快照清理任务。
-- 现有同等索引先核对，避免重复创建。回退时先停清理任务，再按原 DDL 移除本索引。
SELECT DATABASE() AS target_database;
SHOW INDEX FROM `data_access_task_monitor_snap_t`;
SET SESSION lock_wait_timeout=5;
ALTER TABLE `data_access_task_monitor_snap_t`
  ADD INDEX `idx_task_monitor_snap_retention` (`tenant_id`, `monitor_time`, `tid`),
  ALGORITHM=INPLACE, LOCK=NONE;
SHOW INDEX FROM `data_access_task_monitor_snap_t` WHERE Key_name='idx_task_monitor_snap_retention';
