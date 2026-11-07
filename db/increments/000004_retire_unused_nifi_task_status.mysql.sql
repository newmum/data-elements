-- 数据接入-退役海渔未使用的旧任务状态空表。
-- 适用范围：已完成引用审计的海渔行业业务库；其他行业没有该表。
-- 执行前核对实际连接、当前SHOW CREATE与受限备份摘要、COUNT(*)=0，
-- 入/出外键、视图、例程、触发器、事件、登记资产、NiFi DSL、当前Magic/
-- 低代码源码及编译内容、四前端和Java均无引用；任一变化立即停止。
-- 旧表无业务记录，无tenant_id，不再由现行任务运行链路写入。
-- MySQL DROP隐式提交；先保留受限SHOW CREATE恢复材料，不复制或删除其他业务数据。
-- 后置：该表不存在，check-db与五库export-db成功，四行业176表一致。
-- 回退：仅在仍无引用/无写入的条件下审阅受限原始结构重建空表；
-- 不从历史初始化文件重建整个库，不恢复旧运行脚本。

SELECT DATABASE();
SET SESSION lock_wait_timeout=5;
-- 运行引用先核对，再锁定旧表及本库登记/目录/流程定义防止检查后新写入。
LOCK TABLES `nifi_task_status_t` WRITE, `db_table_t` READ, `da_catalog_t` READ, `nifi_pipeline_t` READ;
SELECT COUNT(*) AS must_be_zero FROM `nifi_task_status_t`;
-- 未完成全部引用核对或不为0行时停止。
DROP TABLE `nifi_task_status_t`;
UNLOCK TABLES;
SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='nifi_task_status_t';
-- 后置存在表查询必须返回0行。
