-- 适用：广电实际模式 BASELINE_BEIJING_GD，col_comment为可空VARCHAR(128)、默认NULL、无列索引/约束。
-- 达梦不支持VARCHAR直接MODIFY为CLOB，必须复制原值后换列；DDL有提交边界，不能依赖事务回滚。
-- 前置：停登记/目录字段写入；保存完整表DDL、全部tid/col_comment原文、依赖视图DDL及授权。
-- 确认col_comment_lob/col_comment_v128不存在，逐项核查列默认/可空/注释；视图新增依赖须先审阅。
-- 确认该表无启用的DML触发器；存在时先审阅，避免复制注释触发其它字段或外部写入。
ALTER TABLE "BASELINE_BEIJING_GD"."da_catalog_item_t" ADD "col_comment_lob" CLOB;
UPDATE "BASELINE_BEIJING_GD"."da_catalog_item_t" SET "col_comment_lob"="col_comment";
COMMIT;
-- 检查两列逐行相等（原VARCHAR最长128），并比较NULL/长度；不一致必须停止，不进行换名。
SELECT COUNT(*) AS "unequal_rows" FROM "BASELINE_BEIJING_GD"."da_catalog_item_t"
WHERE ("col_comment" IS NULL AND "col_comment_lob" IS NOT NULL)
   OR ("col_comment" IS NOT NULL AND "col_comment_lob" IS NULL)
   OR ("col_comment" IS NOT NULL AND "col_comment"<>CAST("col_comment_lob" AS VARCHAR(128)));
ALTER TABLE "BASELINE_BEIJING_GD"."da_catalog_item_t" RENAME COLUMN "col_comment" TO "col_comment_v128";
ALTER TABLE "BASELINE_BEIJING_GD"."da_catalog_item_t" RENAME COLUMN "col_comment_lob" TO "col_comment";
COMMENT ON COLUMN "BASELINE_BEIJING_GD"."da_catalog_item_t"."col_comment" IS '字段注释';
-- 依照前置备份原样重新CREATE OR REPLACE依赖视图（现有da_asset_catalog_item_t），明确引用新的col_comment。
CREATE OR REPLACE   VIEW BASELINE_BEIJING_GD.da_asset_catalog_item_t AS  SELECT baseline_beijing_gd.da_catalog_item_t.tid,baseline_beijing_gd.da_catalog_item_t.catalog_id,baseline_beijing_gd.da_catalog_item_t.tenant_id,baseline_beijing_gd.da_catalog_item_t.revision,baseline_beijing_gd.da_catalog_item_t.created_by,baseline_beijing_gd.da_catalog_item_t.created_time,baseline_beijing_gd.da_catalog_item_t.updated_by,baseline_beijing_gd.da_catalog_item_t.updated_time,baseline_beijing_gd.da_catalog_item_t.is_del,baseline_beijing_gd.da_catalog_item_t.col_name,baseline_beijing_gd.da_catalog_item_t.col_en,baseline_beijing_gd.da_catalog_item_t.col_type,baseline_beijing_gd.da_catalog_item_t.col_comment,baseline_beijing_gd.da_catalog_item_t.col_length,baseline_beijing_gd.da_catalog_item_t.is_pk,baseline_beijing_gd.da_catalog_item_t.is_masking,baseline_beijing_gd.da_catalog_item_t.is_fk,baseline_beijing_gd.da_catalog_item_t.is_nullable,baseline_beijing_gd.da_catalog_item_t.date_format,baseline_beijing_gd.da_catalog_item_t.mark_lvl,baseline_beijing_gd.da_catalog_item_t.col_unit,baseline_beijing_gd.da_catalog_item_t.col_precision,baseline_beijing_gd.da_catalog_item_t.masking_id,baseline_beijing_gd.da_catalog_item_t.sort_no,baseline_beijing_gd.da_catalog_item_t.share_type,baseline_beijing_gd.da_catalog_item_t.share_condition,baseline_beijing_gd.da_catalog_item_t.is_dict,baseline_beijing_gd.da_catalog_item_t.dict_name,baseline_beijing_gd.da_catalog_item_t.col_max_length,baseline_beijing_gd.da_catalog_item_t.default_value,baseline_beijing_gd.da_catalog_item_t.data_standard_id,baseline_beijing_gd.da_catalog_item_t.quality_rule,baseline_beijing_gd.da_catalog_item_t.enable_code_table,baseline_beijing_gd.da_catalog_item_t.code_table_id,baseline_beijing_gd.da_catalog_item_t.source_table_column_id,baseline_beijing_gd.da_catalog_item_t.target_table_column_id FROM BASELINE_BEIJING_GD.da_catalog_item_t;
-- 先确认原tid/全文/NULL和行数一致、所有视图可读，再执行以下最后清理；不能提前DROP原列。
ALTER TABLE "BASELINE_BEIJING_GD"."da_catalog_item_t" DROP COLUMN "col_comment_v128";
-- 后置：长注释绑定/读取回归、check-db.py及export-db.py；列位置变化以实际完整结构为准。
-- 回退：清理前可按备份换回原列并恢复视图；清理后须使用受限完整原值备份。
-- 已写入长于128字的新值时禁止直接缩回，不截断；保留全文另行审阅恢复。
