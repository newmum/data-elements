-- 数据超市-目录权威累计浏览量结构。
-- 适用起始结构：000002已补齐公共15表的行业统一结构；da_catalog_t已有
-- 34列且尚无market_view_count，tid主键、租户/登记/审批字段和业务数据保持原状。
-- 目标范围：所有实际绑定的行业业务库；不用于控制库、恢复来源库或独立海通库。
-- 顺序：核实连接/方言/缺列/目标表现行DDL -> 受限结构备份 -> 本增量 ->
-- 核对列类型/默认值/非空/中文注释 -> check-db -> 五库完整结构导出。
-- 新列为有符号BIGINT，默认0、非空；本增量不回填ES历史、不切换浏览写入者，
-- 不修改目录revision/resource_revision_no/updated_time，不安装更新时间触发器。
-- 计数回填及写入切换另按有界批量、租户归属、停旧写入和幂等核对方案执行。
-- 执行前确认无长期事务并设置有界等锁；任何缺列/类型以外的差异先停止审查。
-- 回退：未开放新计数时可按受限备份审阅移除新列；开放后先冻结并排空写入，
-- 保存/核对关系库权威值，恢复旧索引路径前完成计数补投影，保留该列供复核。
-- 禁止以初始化文件重建已有表；不通过覆盖业务数据恢复结构。

-- 达梦8.1.3.26、ALTER_TABLE_OPT=0、有存量行已验证单步加列；不改全局参数。
-- DDL及其之前未提交业务修改会隐式提交；单独执行，不能依赖ROLLBACK回退。
-- 先由部署人员SET SCHEMA为已核对的行业业务schema；执行端设置有界JDBC查询超时。
SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS target_schema FROM DUAL;
SELECT COLUMN_NAME,DATA_TYPE,NULLABLE,DATA_DEFAULT FROM DBA_TAB_COLUMNS WHERE OWNER=SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AND TABLE_NAME='da_catalog_t' AND COLUMN_NAME='market_view_count';
-- 上述列查询必须0行；已有列先核对而不是重复执行。
ALTER TABLE "da_catalog_t" ADD "market_view_count" BIGINT DEFAULT 0 NOT NULL;
COMMENT ON COLUMN "da_catalog_t"."market_view_count" IS '数据超市目录累计浏览次数';
SELECT C.DATA_TYPE,C.NULLABLE,C.DATA_DEFAULT,M.COMMENTS FROM DBA_TAB_COLUMNS C JOIN DBA_COL_COMMENTS M ON M.OWNER=C.OWNER AND M.TABLE_NAME=C.TABLE_NAME AND M.COLUMN_NAME=C.COLUMN_NAME WHERE C.OWNER=SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AND C.TABLE_NAME='da_catalog_t' AND C.COLUMN_NAME='market_view_count';
SELECT COUNT(*) AS invalid_counter_rows FROM "da_catalog_t" WHERE "market_view_count" IS NULL OR "market_view_count" < 0;
