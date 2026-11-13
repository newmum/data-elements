-- 000008：行业日志安全参数快照（MySQL），仅交付结构，生产执行必须审阅。
-- 适用旧结构：已存在 log_request_t、log_system_t 及其 tid 主键、tenant_id、operation_name；
-- 两表均未包含 parameter_snapshot、parameter_search_text、parameter_capture_status 三列。
-- 四行业真实绑定：公安 baseline_ga / MySQL；海渔 baseline_sea_fishery / MySQL；
-- 澄天 baseline_ct.public / KingbaseES；广电 baseline_beijing_gd / 达梦（配置绑定 BASELINE_BEIJING_GD）。
-- 本文件仅在已核对的公安、海渔 MySQL 目标库分别执行；澄天、广电使用同编号原生方言文件。
-- 演示 MySQL 四库 baseline_demo_ga、baseline_demo_sea_fishery、baseline_demo_chengtian、
-- baseline_demo_broadcasting 本次未应用；只有另行确认目标和授权后才能逐库审阅本文件。
-- 不适用控制库 baseline、任何 iam/IDAAS 表、独立海通库或 chengtian 旧恢复源。
--
-- 执行顺序与恢复：
-- 1. 先单独执行前置结构检查，人工确认目标、两张基础表、6 个旧标识列及原索引；新列查询必须为 0 行。
--    任一新列已存在、旧结构不符或目标不明即停止，不以 IF NOT EXISTS 掩盖差异，不整份盲跑。
-- 2. 在仓库外受限位置备份原 DDL 及两表数据并验证恢复方案；备份含日志内容，不得入 Git 或普通日志。
--    审阅目标 MySQL 版本、DDL 锁表/空间开销和停写窗口；客户端遇错立即停止，不使用 --force。
-- 3. 低峰期执行 DDL，再完成后置检查；全部目标行业 DDL 核验后才部署依赖新列的后端和 Magic/低代码资源。
-- 4. DDL 可能隐式提交，两表不保证原子升级。失败先停部署并核对部分完成状态，不盲目重跑。
--    回退先停新字段读写并回退后端/资源，优先保留可空新列；确需移除时先备份新增快照，
--    再经独立审阅逐列移除或按受限备份恢复，不能承诺事务回滚，也不能重建表覆盖日志。
-- 旧行三列保持 NULL；parameter_capture_status 的 NULL 表示历史未采集，不冒充 EMPTY、CAPTURED 或 LEGACY。
-- 禁止从 req_param、post_params 等原始历史回填快照或检索投影；本文件不执行任何业务数据脚本。
-- 两个长文本列可空且不设非空默认值；检索投影只能来自同一安全参数快照。
-- 无经验证合适的全文索引，本次不新增索引；普通 B-tree 索引不能优化任意子串检索。

-- 前置结构检查（先单独执行并审阅，不能仅因查询成功便继续）。
SELECT DATABASE() AS target_database;
SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('log_request_t', 'log_system_t')
ORDER BY TABLE_NAME;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('log_request_t', 'log_system_t')
  AND COLUMN_NAME IN ('tid', 'tenant_id', 'operation_name')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
-- 以下查询预期 0 行；非 0 行须停止并对账。
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('log_request_t', 'log_system_t')
  AND COLUMN_NAME IN ('parameter_snapshot', 'parameter_search_text', 'parameter_capture_status');
SHOW INDEX FROM `log_request_t`;
SHOW INDEX FROM `log_system_t`;

-- DDL（前置审阅、备份完成后执行；只新增本次三列，不修改原字段、主键或索引）。
SET SESSION lock_wait_timeout = 5;
ALTER TABLE `log_request_t`
  ADD COLUMN `parameter_snapshot` LONGTEXT COLLATE utf8mb4_general_ci NULL COMMENT '日志管理-安全参数快照，仅保存脱敏限长后的内容',
  ADD COLUMN `parameter_search_text` LONGTEXT COLLATE utf8mb4_general_ci NULL COMMENT '日志管理-由同一安全参数快照生成的参数检索投影',
  ADD COLUMN `parameter_capture_status` VARCHAR(24) COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '日志管理-参数采集状态：EMPTY/CAPTURED/PARTIAL/OMITTED/FAILED/LEGACY；NULL表示历史未采集';
ALTER TABLE `log_system_t`
  ADD COLUMN `parameter_snapshot` LONGTEXT COLLATE utf8mb4_general_ci NULL COMMENT '日志管理-安全参数快照，仅保存脱敏限长后的内容',
  ADD COLUMN `parameter_search_text` LONGTEXT COLLATE utf8mb4_general_ci NULL COMMENT '日志管理-由同一安全参数快照生成的参数检索投影',
  ADD COLUMN `parameter_capture_status` VARCHAR(24) COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '日志管理-参数采集状态：EMPTY/CAPTURED/PARTIAL/OMITTED/FAILED/LEGACY；NULL表示历史未采集';

-- 后置检查：预期恰好 6 行；长文本为 LONGTEXT，状态为 VARCHAR(24)，均可空，默认值为 NULL。
-- 对照两表 SHOW CREATE TABLE 及备份，确认中文注释、原列、租户默认值、主键和索引未变。
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('log_request_t', 'log_system_t')
  AND COLUMN_NAME IN ('parameter_snapshot', 'parameter_search_text', 'parameter_capture_status')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
SHOW CREATE TABLE `log_request_t`;
SHOW CREATE TABLE `log_system_t`;
-- 获授权完成全部实库 DDL 后：运行 tools/check-db.py，再运行 tools/export-db.py 只读重新导出。
-- 演示环境应另选输出目录 db/environments/demo；不得把源码摘要或离线测试当作实库已验证。