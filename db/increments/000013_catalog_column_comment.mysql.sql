-- 适用：行业库 da_catalog_item_t.col_comment 为可空 VARCHAR(128)、无索引/外键/生成表达式的现行结构。
-- 范围：分别在实际绑定的公安、海渔 MySQL 行业库执行；不在控制库或外部来源库执行。
-- 前置：保存 SHOW CREATE TABLE、相关视图定义及 tid/col_comment 完整数据；暂停登记/目录字段写入。
-- 核对字符集 utf8mb4、排序规则 utf8mb4_general_ci、默认 NULL、注释及索引，差异先审阅。
-- 执行：先审阅本方言完整 init.sql，再执行此增量；不能在已有库执行完整初始化文件。
ALTER TABLE `da_catalog_item_t`
  MODIFY COLUMN `col_comment` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '字段注释';
-- 后置：逐 tid 核对原注释/NULL、总行数及相关视图；验证超过128字的登记与读取。
-- 运行 tools/check-db.py 和 tools/export-db.py，同步五库结构及清单；既有行业差异单独记录。
-- 回退：有超过128字的新值时禁止缩回；先保存新增全文，停写后按备份审阅恢复，不截断数据。
