-- 000008：行业日志安全参数快照（达梦），仅交付结构，生产执行必须审阅。
-- 适用旧结构：已存在 log_request_t、log_system_t 及其 tid 主键、tenant_id、operation_name；
-- 两表均未包含 parameter_snapshot、parameter_search_text、parameter_capture_status 三列。
-- 四行业真实绑定：公安 baseline_ga / MySQL；海渔 baseline_sea_fishery / MySQL；
-- 澄天 baseline_ct.public / KingbaseES；广电 baseline_beijing_gd / 达梦（配置绑定 BASELINE_BEIJING_GD）。
-- 本文件只用于广电达梦；公安、海渔、澄天分别使用同编号 MySQL、金仓文件。
-- 原生结构中的真实带引号 schema 为 "baseline_beijing_gd"，必须先核对大小写及实际运行绑定，不能自动更名。
-- 演示环境四行业为 baseline_demo_* MySQL，本次未应用，不能执行本达梦文件。
-- 不适用控制库 baseline、任何 iam/IDAAS 表、独立海通库或 chengtian 旧恢复源。
--
-- 执行顺序与恢复：
-- 1. 先单独执行前置结构检查，确认 schema、两张基础表、6 个旧标识列及主键/索引；新列查询必须为 0 行。
--    任一新列已存在、旧结构不符或目标不明即停止，不跳过错误或用兼容类型掩盖差异，不整份盲跑。
-- 2. 在仓库外受限位置备份原 DDL、注释、索引及两表数据并验证恢复；不得将含日志内容的备份入 Git/普通日志。
--    审阅目标达梦版本、DDL 锁表/空间开销及停写窗口，使用已设置查询超时、遇错即停的连接低峰期执行。
-- 3. 执行 DDL 与列注释并完成后置检查；全部目标行业 DDL 核验后才部署依赖新列的后端和 Magic/低代码资源。
-- 4. 达梦 DDL 可能隐式提交，两表和注释不保证原子升级；失败先停部署并核对部分完成状态，不盲目重跑。
--    回退先停新字段读写并回退后端/资源，优先保留可空新列；确需移除时先备份新增快照，
--    再独立审阅逐列移除或按受限备份恢复，不承诺事务回滚，不重建表覆盖日志。
-- 沿用现有原生日志列的 TEXT（不替换成 MySQL LONGTEXT）；两个长文本列可空。
-- 旧行三列保持 NULL；状态 NULL 表示历史未采集，不冒充 EMPTY、CAPTURED 或 LEGACY。
-- 禁止从 req_param、post_params 等原始历史回填；检索投影只能来自同一安全参数快照，无业务数据脚本。
-- 无经验证合适的全文索引，本次不新增索引；普通 B-tree 索引不能优化任意子串检索。

-- 前置结构检查（先单独执行并审阅，基础表查询预期 2 行，旧标识列预期 6 行）。
SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS target_schema FROM DUAL;
SELECT OWNER, TABLE_NAME FROM DBA_TABLES
WHERE OWNER = 'baseline_beijing_gd' AND TABLE_NAME IN ('log_request_t', 'log_system_t')
ORDER BY TABLE_NAME;
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE, DATA_DEFAULT
FROM DBA_TAB_COLUMNS
WHERE OWNER = 'baseline_beijing_gd' AND TABLE_NAME IN ('log_request_t', 'log_system_t')
  AND COLUMN_NAME IN ('tid', 'tenant_id', 'operation_name')
ORDER BY TABLE_NAME, COLUMN_ID;
-- 以下查询预期 0 行；非 0 行须停止并对账。
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, NULLABLE, DATA_DEFAULT
FROM DBA_TAB_COLUMNS
WHERE OWNER = 'baseline_beijing_gd' AND TABLE_NAME IN ('log_request_t', 'log_system_t')
  AND COLUMN_NAME IN ('parameter_snapshot', 'parameter_search_text', 'parameter_capture_status');
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, COLUMN_POSITION FROM DBA_IND_COLUMNS
WHERE TABLE_OWNER = 'baseline_beijing_gd' AND TABLE_NAME IN ('log_request_t', 'log_system_t')
ORDER BY TABLE_NAME, INDEX_NAME, COLUMN_POSITION;

-- DDL（前置审阅、备份完成后执行；只新增本次三列，不修改原字段、主键或索引）。
ALTER TABLE "baseline_beijing_gd"."log_request_t" ADD (
  "parameter_snapshot" TEXT NULL,
  "parameter_search_text" TEXT NULL,
  "parameter_capture_status" VARCHAR(24) NULL DEFAULT NULL
);
ALTER TABLE "baseline_beijing_gd"."log_system_t" ADD (
  "parameter_snapshot" TEXT NULL,
  "parameter_search_text" TEXT NULL,
  "parameter_capture_status" VARCHAR(24) NULL DEFAULT NULL
);
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."parameter_snapshot" IS '日志管理-安全参数快照，仅保存脱敏限长后的内容';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."parameter_search_text" IS '日志管理-由同一安全参数快照生成的参数检索投影';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."parameter_capture_status" IS '日志管理-参数采集状态：EMPTY/CAPTURED/PARTIAL/OMITTED/FAILED/LEGACY；NULL表示历史未采集';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."parameter_snapshot" IS '日志管理-安全参数快照，仅保存脱敏限长后的内容';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."parameter_search_text" IS '日志管理-由同一安全参数快照生成的参数检索投影';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."parameter_capture_status" IS '日志管理-参数采集状态：EMPTY/CAPTURED/PARTIAL/OMITTED/FAILED/LEGACY；NULL表示历史未采集';

-- 后置检查：预期恰好 6 行，两个长文本为原生 TEXT，状态为 VARCHAR(24)，均可空，默认值为 NULL。
-- 对照备份/DBMS_METADATA.GET_DDL 确认中文注释、原列、租户默认值、主键和索引未变。
SELECT C.TABLE_NAME, C.COLUMN_NAME, C.DATA_TYPE, C.DATA_LENGTH, C.NULLABLE, C.DATA_DEFAULT, M.COMMENTS
FROM DBA_TAB_COLUMNS C
JOIN DBA_COL_COMMENTS M ON M.OWNER = C.OWNER AND M.TABLE_NAME = C.TABLE_NAME AND M.COLUMN_NAME = C.COLUMN_NAME
WHERE C.OWNER = 'baseline_beijing_gd' AND C.TABLE_NAME IN ('log_request_t', 'log_system_t')
  AND C.COLUMN_NAME IN ('parameter_snapshot', 'parameter_search_text', 'parameter_capture_status')
ORDER BY C.TABLE_NAME, C.COLUMN_ID;
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, COLUMN_POSITION FROM DBA_IND_COLUMNS
WHERE TABLE_OWNER = 'baseline_beijing_gd' AND TABLE_NAME IN ('log_request_t', 'log_system_t')
ORDER BY TABLE_NAME, INDEX_NAME, COLUMN_POSITION;
-- 获授权完成全部实库 DDL 后：运行 tools/check-db.py，再运行 tools/export-db.py 只读重新导出。
-- 本文件与完整结构目前仅为版本源变更，不代表实库已应用或已验证。