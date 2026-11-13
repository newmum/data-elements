-- 000008：行业日志安全参数快照（金仓），仅交付结构，生产执行必须审阅。
-- 适用旧结构：已存在 log_request_t、log_system_t 及其 tid 主键、tenant_id、operation_name；
-- 两表均未包含 parameter_snapshot、parameter_search_text、parameter_capture_status 三列。
-- 四行业真实绑定：公安 baseline_ga / MySQL；海渔 baseline_sea_fishery / MySQL；
-- 澄天 baseline_ct.public / KingbaseES；广电 baseline_beijing_gd / 达梦（配置绑定 BASELINE_BEIJING_GD）。
-- 本文件只用于澄天 baseline_ct 的 public schema；公安、海渔、广电分别使用同编号 MySQL、达梦文件。
-- 演示环境四行业为 baseline_demo_* MySQL，本次未应用，不能执行本金仓文件。
-- 不适用控制库 baseline、任何 iam/IDAAS 表、独立海通库或 chengtian 旧恢复源。
--
-- 执行顺序与恢复：
-- 1. 先单独执行前置结构检查，确认数据库、schema、两张基础表、6 个旧标识列和主键/索引；新列查询必须为 0 行。
--    任一新列已存在、旧结构不符或目标不明即停止，不以 IF NOT EXISTS 掩盖差异，不整份盲跑。
-- 2. 在仓库外受限位置备份原 DDL、注释、索引及两表数据并验证恢复；不得将含日志内容的备份入 Git/普通日志。
--    审阅目标金仓版本、锁表/空间开销及停写窗口，客户端遇错即停，低峰期执行。
-- 3. 执行事务内 DDL 与注释并完成后置检查；全部目标行业 DDL 核验后才部署依赖新列的后端和 Magic/低代码资源。
-- 4. 提交前失败在原连接 ROLLBACK 并核对实际结构；提交后的回退先停新字段读写并回退后端/资源，
--    优先保留可空新列。确需移除时先备份新增快照，再独立审阅逐列移除或按受限备份恢复，不能重建表覆盖日志。
-- 两个长文本沿用原生 TEXT 且可空；状态使用与现有原生定义一致的 24 字符可空字符串。
-- 旧行三列保持 NULL；状态 NULL 表示历史未采集，不冒充 EMPTY、CAPTURED 或 LEGACY。
-- 禁止从 req_param、post_params 等原始历史回填；检索投影只能来自同一安全参数快照，无业务数据脚本。
-- 无经验证合适的全文索引，本次不新增索引；普通 B-tree 索引不能优化任意子串检索。

-- 前置结构检查（先单独执行并审阅，基础表查询预期 2 行，旧标识列预期 6 行）。
SELECT current_database(), current_schema();
SELECT table_schema, table_name, table_type FROM information_schema.tables
WHERE table_schema = 'public' AND table_name IN ('log_request_t', 'log_system_t')
ORDER BY table_name;
SELECT table_name, column_name, data_type, character_maximum_length, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name IN ('log_request_t', 'log_system_t')
  AND column_name IN ('tid', 'tenant_id', 'operation_name')
ORDER BY table_name, ordinal_position;
-- 以下查询预期 0 行；非 0 行须停止并对账。
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name IN ('log_request_t', 'log_system_t')
  AND column_name IN ('parameter_snapshot', 'parameter_search_text', 'parameter_capture_status');
SELECT tablename, indexname, indexdef FROM pg_indexes
WHERE schemaname = 'public' AND tablename IN ('log_request_t', 'log_system_t')
ORDER BY tablename, indexname;

-- DDL（前置审阅、备份完成后执行；只新增本次三列，不修改原字段、主键或索引）。
BEGIN;
SET LOCAL lock_timeout = '5s';
ALTER TABLE "public"."log_request_t"
  ADD COLUMN "parameter_snapshot" TEXT NULL,
  ADD COLUMN "parameter_search_text" TEXT NULL,
  ADD COLUMN "parameter_capture_status" character varying(24 char) NULL DEFAULT NULL;
ALTER TABLE "public"."log_system_t"
  ADD COLUMN "parameter_snapshot" TEXT NULL,
  ADD COLUMN "parameter_search_text" TEXT NULL,
  ADD COLUMN "parameter_capture_status" character varying(24 char) NULL DEFAULT NULL;
COMMENT ON COLUMN "public"."log_request_t"."parameter_snapshot" IS '日志管理-安全参数快照，仅保存脱敏限长后的内容';
COMMENT ON COLUMN "public"."log_request_t"."parameter_search_text" IS '日志管理-由同一安全参数快照生成的参数检索投影';
COMMENT ON COLUMN "public"."log_request_t"."parameter_capture_status" IS '日志管理-参数采集状态：EMPTY/CAPTURED/PARTIAL/OMITTED/FAILED/LEGACY；NULL表示历史未采集';
COMMENT ON COLUMN "public"."log_system_t"."parameter_snapshot" IS '日志管理-安全参数快照，仅保存脱敏限长后的内容';
COMMENT ON COLUMN "public"."log_system_t"."parameter_search_text" IS '日志管理-由同一安全参数快照生成的参数检索投影';
COMMENT ON COLUMN "public"."log_system_t"."parameter_capture_status" IS '日志管理-参数采集状态：EMPTY/CAPTURED/PARTIAL/OMITTED/FAILED/LEGACY；NULL表示历史未采集';
COMMIT;

-- 后置检查：预期恰好 6 行，两个长文本为 TEXT，状态长度 24，均可空，默认值为 NULL。
-- 对照备份确认中文注释、原列、租户默认值、主键和索引未变。
SELECT C.table_name, C.column_name, C.data_type, C.character_maximum_length, C.is_nullable,
       C.column_default, col_description(T.oid, A.attnum) AS column_comment
FROM information_schema.columns C
JOIN pg_namespace N ON N.nspname = C.table_schema
JOIN pg_class T ON T.relnamespace = N.oid AND T.relname = C.table_name
JOIN pg_attribute A ON A.attrelid = T.oid AND A.attname = C.column_name AND NOT A.attisdropped
WHERE C.table_schema = 'public' AND C.table_name IN ('log_request_t', 'log_system_t')
  AND C.column_name IN ('parameter_snapshot', 'parameter_search_text', 'parameter_capture_status')
ORDER BY C.table_name, C.ordinal_position;
SELECT tablename, indexname, indexdef FROM pg_indexes
WHERE schemaname = 'public' AND tablename IN ('log_request_t', 'log_system_t')
ORDER BY tablename, indexname;
-- 获授权完成全部实库 DDL 后：运行 tools/check-db.py，再运行 tools/export-db.py 只读重新导出。
-- 本文件与完整结构目前仅为版本源变更，不代表实库已应用或已验证。