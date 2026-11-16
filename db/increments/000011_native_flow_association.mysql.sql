-- 适用：已应用 000010 的现有行业库（baseline_ga、baseline_sea_fishery）。
-- 前置：核对实际租户库，确认 data_access_native_flow_t 尚不存在，并保存结构快照。
-- 执行：先建表和索引，再注册 /ods/registry/native，最后发布页面。
-- 后置：检查表、注释、主键、唯一键、分页索引，并运行 tools/check-db.py、tools/export-db.py。
-- 回退：先停用调用入口；有记录时须备份，是否移除表另行确认。

CREATE TABLE `data_access_native_flow_t` (
  `tid` varchar(32) NOT NULL COMMENT '关联记录标识',
  `tenant_id` varchar(32) NOT NULL COMMENT '所属行业租户标识',
  `source_table_id` varchar(32) NOT NULL COMMENT '已登记来源表标识',
  `node_id` varchar(32) NOT NULL COMMENT 'NiFi节点标识',
  `node_name` varchar(128) NOT NULL COMMENT '建立关联时的节点名称',
  `process_group_id` varchar(36) NOT NULL COMMENT 'NiFi原生流程组UUID',
  `group_name` varchar(512) NOT NULL COMMENT '建立关联时的原生流程组名称',
  `created_by` varchar(32) NOT NULL COMMENT '创建关联的用户标识',
  `created_at` bigint NOT NULL COMMENT '关联创建时间戳，Unix毫秒',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_native_flow_tenant_group` (`tenant_id`,`node_id`,`process_group_id`),
  KEY `idx_native_flow_tenant_time` (`tenant_id`,`created_at`,`tid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据接入-已登记来源表与NiFi原生流程只读关联';
