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

-- KingbaseES V8R6验证常量默认值加列和事务回退；实际锁时间仍须现场核对。
-- 当前本机安全检查限定baseline_ct.public；生产审阅时核对并显式调整。
BEGIN;
SET LOCAL lock_timeout='5s';
DO $counter$ BEGIN IF current_database()<>'baseline_ct' THEN RAISE EXCEPTION 'Unexpected target database'; END IF;
IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='da_catalog_t' AND column_name='market_view_count') THEN RAISE EXCEPTION 'Counter column already exists; stop for reconciliation'; END IF; END $counter$;
ALTER TABLE public."da_catalog_t" ADD "market_view_count" BIGINT DEFAULT 0 NOT NULL;
COMMENT ON COLUMN public."da_catalog_t"."market_view_count" IS '数据超市目录累计浏览次数';
SELECT a.attname,format_type(a.atttypid,a.atttypmod),a.attnotnull,pg_get_expr(d.adbin,d.adrelid),col_description(c.oid,a.attnum) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE n.nspname='public' AND c.relname='da_catalog_t' AND a.attname='market_view_count';
SELECT COUNT(*) AS invalid_counter_rows FROM public."da_catalog_t" WHERE "market_view_count" IS NULL OR "market_view_count" < 0;
COMMIT;
