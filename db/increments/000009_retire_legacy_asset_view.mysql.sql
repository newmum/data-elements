-- 数据盘点旧资产兼容视图退役。适用于公安 baseline_ga 和海渔 baseline_sea_fishery 的原生 MySQL。
-- 旧结构须为 da_asset_t 普通视图，唯一基础对象是同库 da_catalog_t，asset_type 固定为 catalog。
-- 逐库使用已核对的部署连接，目标与租户绑定须一致。执行人先单独执行前置查询并审阅结果。
-- 核对全实例可见的视图、程序、触发器、事件、已登记配置和外部使用者。存在引用或权限不足即停止。
-- 保存实际 SHOW CREATE VIEW、定义者和 SQL SECURITY、字符集和排序规则，以及视图和列授权，
-- 连同目录表的结构摘要和业务指纹一起保存到受限恢复材料。现有数据库中无需新建例程执行此变更。
-- MySQL DDL 隐式提交，跨库分别验证。回退按受限原始定义恢复视图，核对定义及授权，必要时补齐原授权。

-- 阶段 1，预期当前库是上述目标之一，结果包含 da_asset_t 的 VIEW 和 da_catalog_t 的 BASE TABLE。
SELECT DATABASE() AS target_schema;
SELECT TABLE_NAME, TABLE_TYPE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('da_asset_t', 'da_catalog_t')
ORDER BY TABLE_NAME;
SHOW CREATE VIEW `da_asset_t`;
-- 预期零行。也要在同连接核对定义摘要、授权及目录表指纹再执行删除。
SELECT VIEW_SCHEMA, VIEW_NAME
FROM information_schema.VIEW_TABLE_USAGE
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'da_asset_t';

-- 阶段 2，经前置审核后执行。失败停止，先回读对象状态。
SET SESSION lock_wait_timeout = 5;
DROP VIEW `da_asset_t`;

-- 阶段 3，预期只剩下 da_catalog_t 的 BASE TABLE，并对照目录表业务指纹。
SELECT TABLE_NAME, TABLE_TYPE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('da_asset_t', 'da_catalog_t')
ORDER BY TABLE_NAME;
