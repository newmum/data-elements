-- 适用：已核实现行结构的行业库。
-- 前置：确认目标为行业租户数据库，核对当前版本、表名和索引，并保存结构快照。
-- 执行：先创建表和索引，再注册模板 API 和组件，最后验证租户隔离。
-- 后置：核对主键、版本唯一键和列表索引，并执行 tools/check-db.py 与 tools/export-db.py。
-- 回退：先停用入口；有版本记录或任务引用时保留表，另行审阅备份和清理范围。

CREATE TABLE `nifi_access_template_t` (
  `tid` varchar(32) NOT NULL COMMENT '模板版本标识',
  `tenant_id` varchar(32) NOT NULL COMMENT '所属行业租户标识',
  `family_id` varchar(32) NOT NULL COMMENT '模板标识',
  `revision_no` int NOT NULL COMMENT '模板内递增版本号',
  `display_name` varchar(96) NOT NULL COMMENT '模板显示名称',
  `status` varchar(16) NOT NULL COMMENT '版本状态：DRAFT草稿、PUBLISHED已发布、RETIRED已停用',
  `definition` longtext NOT NULL COMMENT '接入拓扑、目标类型及处理器默认配置，不含连接凭据',
  `created_by` varchar(64) NOT NULL COMMENT '创建人标识',
  `created_at` bigint NOT NULL COMMENT '创建时间戳，Unix毫秒',
  `updated_at` bigint NOT NULL COMMENT '更新时间戳，Unix毫秒',
  PRIMARY KEY (`tid`),
  CONSTRAINT `uk_access_template_version` UNIQUE (`tenant_id`,`family_id`,`revision_no`),
  KEY `idx_access_template_list` (`tenant_id`,`status`,`updated_at`,`tid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据接入-NiFi接入模板版本';
