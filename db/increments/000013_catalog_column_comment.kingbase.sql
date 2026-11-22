-- 适用：实际澄天行业库 public.da_catalog_item_t.col_comment 为可空 VARCHAR(128)、默认NULL、无列索引。
-- 前置：备份列定义/注释、相关视图及 tid/col_comment 全文；暂停登记/目录字段写入。
-- 核对依赖视图；如有新增依赖，先按真实定义/授权审阅恢复计划，不能CASCADE删除未知对象。
BEGIN;
ALTER TABLE "public"."da_catalog_item_t" ALTER COLUMN "col_comment" TYPE TEXT;
COMMENT ON COLUMN "public"."da_catalog_item_t"."col_comment" IS '字段注释';
COMMIT;
-- 后置：逐tid比较原值/NULL及行数，验证长注释登记；check-db.py与export-db.py同步五库结构。
-- 回退：已有长于128字的值时禁止缩回；停写并备份全部新值后审阅恢复，不截断来源注释。
