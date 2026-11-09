-- 14 演示环境结构补齐（MySQL）；仅用于经过现场核对的旧版演示库。
-- 控制库与四个租户库分别执行各自文件；此文件不删除既有表、列或数据。
-- 前置：确认 DATABASE()、表集合、列和索引与生成计划完全一致，磁盘及元数据锁充足。
-- 与 000002/000003 中相同的目标结构已包含在此升级中，不得重复执行。
-- DDL 隐式提交；失败时立即停止，逐条核对后再继续，不支持事务整批回滚。
-- 本次演示环境按用户明确要求不备份；其他环境执行前须审阅适用范围、备份和恢复方案。
SET SESSION lock_wait_timeout=5;
SET SESSION innodb_lock_wait_timeout=5;
SELECT DATABASE();

-- 补充缺失表：lake_zone_object_t
CREATE TABLE `lake_zone_object_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `zone_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '湖仓分层标识',
  `object_type` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象类型：TABLE/VIEW/FILE/API',
  `object_ref` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象引用：表标识、视图名、文件路径或接口编码',
  `object_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '对象名称',
  `source_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源数据源或来源对象标识',
  `lifecycle_status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'REGISTERED' COMMENT '生命周期：REGISTERED已登记、ONLINE在线、OFFLINE下线、ARCHIVED归档',
  `last_sync_at` datetime(6) DEFAULT NULL COMMENT '最近同步时间',
  `registered_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '登记人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_lake_zone_object` (`tenant_id`,`zone_id`,`object_type`,`object_ref`,`is_del`),
  KEY `idx_lake_zone_object_zone` (`tenant_id`,`zone_id`,`lifecycle_status`,`is_del`),
  KEY `idx_lake_zone_object_ref` (`object_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据资源（湖仓底座）-已纳管对象清单';

-- 补充缺失表：lake_zone_rule_t
CREATE TABLE `lake_zone_rule_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `zone_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '湖仓分层标识',
  `rule_code` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则编号，拒绝时返回该编号',
  `rule_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则名称',
  `rule_type` varchar(8) COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则方向：IN入湖、OUT出湖',
  `object_scope` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ALL' COMMENT '适用对象：ALL/TABLE/VIEW/FILE/API',
  `file_format` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '允许的文件格式，逗号分隔',
  `compression` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '允许的压缩方式，逗号分隔',
  `partition_rule` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分区规则说明',
  `retention_days` int NOT NULL DEFAULT '0' COMMENT '保留天数，0表示不限制',
  `approval_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要审批：0否，1是',
  `interaction_mode` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'API' COMMENT '交互方式：API/FILE/JDBC/STREAM',
  `priority` int NOT NULL DEFAULT '0' COMMENT '规则优先级，数值越大越先校验',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用、DISABLED停用',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '规则说明',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_lake_zone_rule` (`tenant_id`,`zone_id`,`rule_type`,`rule_code`,`is_del`),
  KEY `idx_lake_zone_rule_zone` (`tenant_id`,`zone_id`,`rule_type`,`status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据资源（湖仓底座）-入出湖标准与交互规则';

-- 补充缺失表：lake_zone_t
CREATE TABLE `lake_zone_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `zone_code` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分层编码：ODS原始库、BASE基础库、SUBJECT主题库、TOPIC专题库、LAKE_DW数据湖仓库区、LAKE_COLLECT数据湖汇聚区',
  `zone_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分层名称',
  `zone_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分层类型：ORIGIN/BASE/SUBJECT/TOPIC/LAKE',
  `datasource_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '承载数据源标识',
  `schema_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '物理模式名称',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用、DISABLED停用',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分层说明',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_lake_zone_code` (`tenant_id`,`zone_code`,`is_del`),
  KEY `idx_lake_zone_tenant_status` (`tenant_id`,`status`,`is_del`),
  KEY `idx_lake_zone_datasource` (`datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据资源（湖仓底座）-湖仓分层实例';

-- 补充缺失表：log_archive_batch_t
CREATE TABLE `log_archive_batch_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '稳定主键，由应用生成',
  `scope_kind` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属范围：TENANT或PLATFORM',
  `scope_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属租户或PLATFORM，由可信服务生成',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行业租户标识；平台记录为空',
  `created_time` datetime(6) NOT NULL COMMENT '创建时间，UTC',
  `event_store` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '归档权威事件类型',
  `range_start` datetime(6) NOT NULL COMMENT 'UTC半开范围起点',
  `range_end` datetime(6) NOT NULL COMMENT 'UTC半开范围终点',
  `generation` int NOT NULL DEFAULT '1' COMMENT '同范围归档或迟到增补代次',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'PREPARING、WRITTEN、VERIFIED、PURGING、COMPLETE、FAILED',
  `record_count` bigint NOT NULL DEFAULT '0' COMMENT '归档记录数量',
  `byte_count` bigint NOT NULL DEFAULT '0' COMMENT '归档字节数',
  `object_key` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '受控归档文件键',
  `manifest_key` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归档校验清单键',
  `sha256` char(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归档文件完整性摘要',
  `retention_until` datetime(6) NOT NULL COMMENT '允许保留截止时间，UTC',
  `hold_status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '保留冻结状态',
  `hold_reason` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '保留冻结必要原因',
  `verified_at` datetime(6) DEFAULT NULL COMMENT '回读校验时间，UTC',
  `purge_completed_at` datetime(6) DEFAULT NULL COMMENT '热库清理完成时间，UTC',
  `operator_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发起主体标识',
  `updated_time` datetime(6) NOT NULL COMMENT '更新时间，UTC',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '乐观锁版本',
  `identity_domain` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'global' COMMENT '所属身份域；行业固定global，平台为workforce或public',
  `worker_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前工作实例标识',
  `lease_until` datetime(6) DEFAULT NULL COMMENT '工作租约截止时间，UTC',
  `actor_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理操作发生时的人员姓名快照',
  `actor_account` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理操作发生时的账号快照',
  `actor_org_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理操作发生时的所属部门名称快照',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `ux_log_archive_range` (`scope_kind`,`scope_id`,`identity_domain`,`event_store`,`range_start`,`range_end`,`generation`),
  KEY `ix_log_archive_status` (`status`,`updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日志管理-日志归档校验与保留记录';

-- 补充缺失表：log_audit_outbox_t
CREATE TABLE `log_audit_outbox_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '稳定主键，由应用生成',
  `scope_kind` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属范围：TENANT或PLATFORM',
  `scope_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属租户或PLATFORM，由可信服务生成',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行业租户标识；平台记录为空',
  `created_time` datetime(6) NOT NULL COMMENT '创建时间，UTC',
  `event_store` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '事件存储类型',
  `event_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '本库事件标识',
  `destination_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '受控目标编码，不允许任意URL',
  `payload_version` int NOT NULL DEFAULT '1' COMMENT '信封版本',
  `payload_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '已经脱敏的事件信封',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、PROCESSING、RETRY、DONE、DEAD',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '实际投递尝试次数',
  `next_retry_at` datetime(6) NOT NULL COMMENT '下次可取时间，UTC',
  `lease_owner` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前工作实例',
  `lease_until` datetime(6) DEFAULT NULL COMMENT '租约截止时间，UTC',
  `last_error_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '脱敏错误编码',
  `last_error_summary` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '脱敏投递失败摘要',
  `updated_time` datetime(6) NOT NULL COMMENT '更新时间，UTC',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '乐观锁版本',
  `identity_domain` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'global' COMMENT '所属身份域；行业固定global，平台为workforce或public',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `ux_log_outbox_event` (`scope_kind`,`scope_id`,`event_store`,`event_id`,`destination_code`),
  KEY `ix_log_outbox_due` (`status`,`next_retry_at`,`lease_until`,`tid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日志管理-审计事件可靠投递';

-- 补充缺失表：log_batch_item_t
CREATE TABLE `log_batch_item_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '稳定主键，由应用生成',
  `scope_kind` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属范围：TENANT或PLATFORM',
  `scope_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属租户或PLATFORM，由可信服务生成',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行业租户标识；平台记录为空',
  `created_time` datetime(6) NOT NULL COMMENT '创建时间，UTC',
  `event_store` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '父事件存储类型',
  `event_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '当前范围内父事件标识',
  `batch_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '稳定业务批次',
  `item_no` int NOT NULL COMMENT '批次内序号，从1开始',
  `target_type` varchar(48) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标对象类型',
  `target_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标对象标识',
  `target_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标名称快照',
  `target_app_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标应用标识',
  `target_org_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标部门范围标识',
  `action_code` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '动作编码',
  `result` varchar(24) COLLATE utf8mb4_general_ci NOT NULL COMMENT '单项执行结果',
  `reason_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败或拒绝原因编码',
  `summary` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '脱敏业务说明',
  `detail_json` longtext COLLATE utf8mb4_general_ci COMMENT '白名单脱敏详情',
  `event_time` datetime(6) NOT NULL COMMENT '事实发生时间，UTC',
  `identity_domain` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'global' COMMENT '所属身份域；行业固定global，平台为workforce或public',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `ux_log_batch_event` (`scope_kind`,`scope_id`,`event_store`,`event_id`,`item_no`),
  KEY `ix_log_batch_target` (`tenant_id`,`target_type`,`target_id`,`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日志管理-批量操作结果明细';

-- 补充缺失表：log_export_job_t
CREATE TABLE `log_export_job_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '稳定主键，由应用生成',
  `scope_kind` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属范围：TENANT或PLATFORM',
  `scope_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属租户或PLATFORM，由可信服务生成',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行业租户标识；平台记录为空',
  `created_time` datetime(6) NOT NULL COMMENT '创建时间，UTC',
  `owner_kind` varchar(24) COLLATE utf8mb4_general_ci NOT NULL COMMENT '发起主体类别',
  `owner_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '发起主体标识',
  `query_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '有界查询条件，不含SQL或凭据',
  `scope_snapshot_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建时授权范围快照',
  `permission_version` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限变更检测依据',
  `cutoff_time` datetime(6) NOT NULL COMMENT '采集截止时间，UTC',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'PENDING、RUNNING、SUCCESS、FAILED、CANCELLED、EXPIRED',
  `format_code` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'CSV或JSONL',
  `row_limit` bigint NOT NULL COMMENT '允许导出数量上限',
  `row_count` bigint NOT NULL DEFAULT '0' COMMENT '实际导出行数',
  `object_key` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '受控文件键，不允许任意路径',
  `sha256` char(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件完整性摘要',
  `expires_at` datetime(6) DEFAULT NULL COMMENT '文件失效时间，UTC',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '任务完成时间，UTC',
  `last_error_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '脱敏失败原因编码',
  `updated_time` datetime(6) NOT NULL COMMENT '更新时间，UTC',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '乐观锁版本',
  `identity_domain` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'global' COMMENT '所属身份域；行业固定global，平台为workforce或public',
  `worker_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前工作实例标识',
  `lease_until` datetime(6) DEFAULT NULL COMMENT '工作租约截止时间，UTC',
  `actor_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理操作发生时的人员姓名快照',
  `actor_account` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理操作发生时的账号快照',
  `actor_org_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理操作发生时的所属部门名称快照',
  PRIMARY KEY (`tid`),
  KEY `ix_log_export_owner` (`scope_kind`,`scope_id`,`owner_id`,`created_time`,`tid`),
  KEY `ix_log_export_status` (`status`,`created_time`,`tid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日志管理-受控日志导出任务';

-- 补充缺失表：log_policy_t
CREATE TABLE `log_policy_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '稳定主键，由应用生成',
  `scope_kind` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属范围：TENANT或PLATFORM',
  `scope_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属租户或PLATFORM，由可信服务生成',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行业租户标识；平台记录为空',
  `created_time` datetime(6) NOT NULL COMMENT '创建时间，UTC',
  `category` varchar(24) COLLATE utf8mb4_general_ci NOT NULL COMMENT '日志类别',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否采集；强制审计类别始终启用',
  `hot_days` int NOT NULL COMMENT '在线保留天数',
  `total_retention_days` int NOT NULL COMMENT '含在线期的总保留天数',
  `sample_rate` decimal(6,5) NOT NULL DEFAULT '1.00000' COMMENT '普通成功请求采样比例',
  `detail_limit_bytes` int NOT NULL DEFAULT '32768' COMMENT '脱敏详情字节上限',
  `field_policy_json` longtext COLLATE utf8mb4_general_ci COMMENT '字段允许及隐藏策略',
  `archive_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否归档',
  `updated_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新主体标识',
  `updated_time` datetime(6) NOT NULL COMMENT '更新时间，UTC',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '乐观锁版本',
  `identity_domain` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'global' COMMENT '所属身份域；行业固定global，平台为workforce或public',
  `actor_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理操作发生时的人员姓名快照',
  `actor_account` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理操作发生时的账号快照',
  `actor_org_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理操作发生时的所属部门名称快照',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `ux_log_policy_scope` (`scope_kind`,`scope_id`,`identity_domain`,`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日志管理-采集保留与脱敏策略';

-- 补充缺失表：push_node_t
CREATE TABLE `push_node_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属行业租户标识',
  `network_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属网络字典编码，对应SSWL',
  `node_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '推送节点显示名称',
  `node_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户内推送节点业务编码',
  `base_url` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'haitong-data-pull服务根地址，不含凭据',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：0停用、1启用',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否为所属网络默认节点：0否、1是',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '节点用途及部署说明',
  `last_sync_time` datetime(6) DEFAULT NULL COMMENT '最近一次配置同步尝试时间',
  `last_sync_status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近配置同步结果：SUCCESS成功、FAILED失败',
  `last_sync_source_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近同步的数据源标识',
  `created_time` datetime(6) NOT NULL COMMENT '节点创建时间',
  `updated_time` datetime(6) NOT NULL COMMENT '节点配置更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志：0正常、1已删除',
  PRIMARY KEY (`tid`),
  KEY `ix_push_node_network` (`tenant_id`,`network_code`,`is_del`,`enabled`),
  KEY `ix_push_node_code` (`tenant_id`,`node_code`,`is_del`),
  KEY `ix_push_node_default` (`tenant_id`,`is_del`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据接入-数据推送节点配置';

-- 补充缺失表：surveillance_control_item_t
CREATE TABLE `surveillance_control_item_t` (
  `tid` varchar(32) NOT NULL COMMENT '记录唯一标识',
  `rule_definition_tid` varchar(32) NOT NULL COMMENT '关联布控规则定义标识',
  `snapshot_tid` varchar(32) NOT NULL COMMENT '关联规则快照标识',
  `snapshot_version` bigint NOT NULL DEFAULT '0' COMMENT '规则快照版本号',
  `tenant_id` varchar(32) NOT NULL COMMENT '所属认证租户标识',
  `engine_code` varchar(128) NOT NULL COMMENT '布控引擎编码',
  `channel_code` varchar(128) NOT NULL COMMENT '布控通道编码',
  `control_item_id` varchar(128) NOT NULL COMMENT '布控数据项业务标识',
  `rule_code` varchar(128) NOT NULL COMMENT '布控规则编码',
  `subject_type` varchar(32) NOT NULL COMMENT '布控对象类型',
  `identifier_type` varchar(64) DEFAULT NULL COMMENT '对象标识类型',
  `identifier_value` varchar(512) DEFAULT NULL COMMENT '对象标识值',
  `identifier_hash` varchar(128) DEFAULT NULL COMMENT '对象标识值摘要',
  `identifier_ciphertext` longtext COMMENT '对象标识值加密内容',
  `identifiers_json` longtext COMMENT '对象多标识信息JSON',
  `attributes_json` longtext COMMENT '对象业务属性JSON',
  `source_rule_no` varchar(128) DEFAULT NULL COMMENT '来源布控单编号',
  `control_reason` varchar(512) DEFAULT NULL COMMENT '布控原因',
  `effective_from` datetime(6) DEFAULT NULL COMMENT '布控生效时间',
  `effective_to` datetime(6) DEFAULT NULL COMMENT '布控失效时间',
  `status` varchar(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '记录业务状态',
  `metadata_json` longtext COMMENT '扩展元数据JSON',
  `created_time` datetime(6) NOT NULL COMMENT '记录创建时间，精确到微秒',
  `updated_time` datetime(6) NOT NULL COMMENT '记录更新时间，精确到微秒',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志：0有效，1已删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_surveillance_control_item` (`tenant_id`,`engine_code`,`channel_code`,`control_item_id`,`is_del`),
  KEY `idx_surveillance_control_item_rule` (`rule_definition_tid`,`status`,`effective_from`,`effective_to`,`is_del`),
  KEY `idx_surveillance_control_item_runtime` (`tenant_id`,`engine_code`,`channel_code`,`identifier_type`,`identifier_hash`,`status`,`is_del`),
  KEY `idx_surveillance_control_item_direct_identifier` (`tenant_id`,`engine_code`,`channel_code`,`identifier_type`,`identifier_value`(128),`status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-具体布控数据项';

-- 补充缺失表：surveillance_kafka_connection_t
CREATE TABLE `surveillance_kafka_connection_t` (
  `tid` varchar(64) NOT NULL COMMENT '记录唯一标识',
  `tenant_id` varchar(64) NOT NULL COMMENT '所属认证租户标识',
  `engine_code` varchar(64) NOT NULL COMMENT '布控引擎编码',
  `connection_role` varchar(16) DEFAULT NULL COMMENT '消息连接用途角色',
  `connection_name` varchar(128) NOT NULL COMMENT '消息连接名称',
  `bootstrap_servers` varchar(1024) NOT NULL COMMENT 'Kafka消息代理连接地址',
  `security_protocol` varchar(32) NOT NULL DEFAULT 'PLAINTEXT' COMMENT 'Kafka连接安全协议',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '记录业务状态',
  `created_time` datetime(6) NOT NULL COMMENT '记录创建时间，精确到微秒',
  `updated_time` datetime(6) NOT NULL COMMENT '记录更新时间，精确到微秒',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志：0有效，1已删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_surv_kafka_connection_tenant_engine_role` (`tenant_id`,`engine_code`,`connection_role`),
  KEY `idx_surv_kafka_connection_status` (`tenant_id`,`status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-Kafka消息连接';

-- 补充缺失表：surveillance_kafka_topic_t
CREATE TABLE `surveillance_kafka_topic_t` (
  `tenant_id` varchar(64) NOT NULL COMMENT '所属认证租户标识',
  `connection_id` varchar(32) NOT NULL COMMENT '所属消息连接标识',
  `topic_name` varchar(249) NOT NULL COMMENT 'Kafka主题名称',
  `business_name` varchar(128) NOT NULL DEFAULT '' COMMENT '主题业务名称',
  `schema_json` text COMMENT '数据结构及连接映射配置JSON',
  `event_time_field` varchar(128) NOT NULL DEFAULT '' COMMENT '事件时间字段名称',
  `description` varchar(512) NOT NULL DEFAULT '' COMMENT '业务说明',
  `updated_time` datetime(6) NOT NULL COMMENT '记录更新时间，精确到微秒',
  PRIMARY KEY (`tenant_id`,`connection_id`,`topic_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-Kafka主题业务定义';

-- 补充缺失表：surveillance_metric_batch_t
CREATE TABLE `surveillance_metric_batch_t` (
  `tid` varchar(32) NOT NULL COMMENT '记录唯一标识',
  `tenant_id` varchar(32) NOT NULL COMMENT '所属认证租户标识',
  `engine_code` varchar(128) NOT NULL COMMENT '布控引擎编码',
  `resource_code` varchar(128) NOT NULL COMMENT '布控资源编码',
  `batch_id` varchar(256) NOT NULL COMMENT '统计批次幂等标识',
  `stat_date` date NOT NULL COMMENT '统计归属日期',
  `input_count` bigint NOT NULL DEFAULT '0' COMMENT '输入记录数量，单位条',
  `matched_count` bigint NOT NULL DEFAULT '0' COMMENT '匹配命中记录数量，单位条',
  `unmatched_count` bigint NOT NULL DEFAULT '0' COMMENT '未匹配记录数量，单位条',
  `failure_count` bigint NOT NULL DEFAULT '0' COMMENT '处理失败记录数量，单位条',
  `created_time` datetime(6) NOT NULL COMMENT '记录创建时间，精确到微秒',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_surveillance_metric_batch` (`tenant_id`,`engine_code`,`resource_code`,`batch_id`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-统计批次幂等记录';

-- 补充缺失表：surveillance_resource_topic_t
CREATE TABLE `surveillance_resource_topic_t` (
  `tid` varchar(32) NOT NULL COMMENT '记录唯一标识',
  `tenant_id` varchar(32) NOT NULL COMMENT '所属认证租户标识',
  `engine_code` varchar(128) NOT NULL COMMENT '布控引擎编码',
  `channel_code` varchar(128) NOT NULL COMMENT '布控通道编码',
  `resource_code` varchar(128) NOT NULL COMMENT '布控资源编码',
  `source_table` varchar(256) NOT NULL COMMENT '来源数据表标识',
  `input_topic` varchar(256) NOT NULL COMMENT '输入消息主题名称',
  `result_topic` varchar(256) NOT NULL COMMENT '匹配结果消息主题名称',
  `identifier_type` varchar(64) NOT NULL COMMENT '对象标识类型',
  `key_field` varchar(128) NOT NULL COMMENT '业务对象标识字段名称',
  `schema_json` longtext COMMENT '数据结构及连接映射配置JSON',
  `status` varchar(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '记录业务状态',
  `created_time` datetime(6) NOT NULL COMMENT '记录创建时间，精确到微秒',
  `updated_time` datetime(6) NOT NULL COMMENT '记录更新时间，精确到微秒',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志：0有效，1已删除',
  `input_connection_ref` varchar(32) GENERATED ALWAYS AS (coalesce(json_unquote(json_extract(`schema_json`,_utf8mb4'$.connectionIds.input')),json_unquote(json_extract(`schema_json`,_utf8mb4'$.connectionRoles.input')),_utf8mb4'INPUT')) STORED COMMENT '输入消息连接引用，优先连接标识、其次角色、缺省INPUT',
  `output_connection_ref` varchar(32) GENERATED ALWAYS AS (coalesce(json_unquote(json_extract(`schema_json`,_utf8mb4'$.connectionIds.output')),json_unquote(json_extract(`schema_json`,_utf8mb4'$.connectionRoles.output')),_utf8mb4'OUTPUT')) STORED COMMENT '结果消息连接引用，优先连接标识、其次角色、缺省OUTPUT',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_surveillance_resource_code` (`tenant_id`,`engine_code`,`resource_code`,`is_del`),
  UNIQUE KEY `uk_surveillance_resource_topic` (`tenant_id`,`engine_code`,`channel_code`,`input_connection_ref`,`input_topic`,`is_del`),
  UNIQUE KEY `uk_surveillance_result_topic` (`tenant_id`,`engine_code`,`output_connection_ref`,`result_topic`,`is_del`),
  KEY `idx_surveillance_resource_topic_status` (`tenant_id`,`engine_code`,`channel_code`,`status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-资源输入与结果主题注册';

-- 补充缺失表：surveillance_result_t
CREATE TABLE `surveillance_result_t` (
  `result_id` char(64) NOT NULL COMMENT '布控匹配结果唯一标识',
  `tenant_id` varchar(64) NOT NULL COMMENT '所属认证租户标识',
  `engine_code` varchar(64) NOT NULL COMMENT '布控引擎编码',
  `resource_code` varchar(255) NOT NULL COMMENT '布控资源编码',
  `channel_code` varchar(32) NOT NULL COMMENT '布控通道编码',
  `event_id` varchar(1024) NOT NULL COMMENT '来源事件标识',
  `snapshot_version` bigint NOT NULL COMMENT '规则快照版本号',
  `subject_name` varchar(255) NOT NULL COMMENT '命中的布控对象名称',
  `identifier_value` varchar(1024) NOT NULL COMMENT '对象标识值',
  `occurred_at` datetime(6) NOT NULL COMMENT '业务事件发生时间',
  `archived_at` datetime(6) NOT NULL COMMENT '匹配结果归档时间',
  `result_topic` varchar(249) NOT NULL COMMENT '匹配结果消息主题名称',
  `source_stream` char(64) NOT NULL COMMENT '来源消息流标识',
  `kafka_partition` int NOT NULL COMMENT '来源Kafka消息分区编号',
  `kafka_offset` bigint NOT NULL COMMENT '来源Kafka消息偏移量',
  `record_index` int NOT NULL COMMENT '消息内记录序号',
  `payload_json` json NOT NULL COMMENT '匹配结果业务内容JSON',
  PRIMARY KEY (`result_id`),
  KEY `ix_result_resource_time` (`tenant_id`,`engine_code`,`resource_code`,`occurred_at`),
  KEY `ix_result_delivery` (`source_stream`,`kafka_partition`,`kafka_offset`,`record_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-匹配结果归档';

-- 补充缺失表：surveillance_rule_definition_t
CREATE TABLE `surveillance_rule_definition_t` (
  `tid` varchar(32) NOT NULL COMMENT '记录唯一标识',
  `snapshot_tid` varchar(32) NOT NULL COMMENT '关联规则快照标识',
  `tenant_id` varchar(32) NOT NULL COMMENT '所属认证租户标识',
  `engine_code` varchar(128) NOT NULL COMMENT '布控引擎编码',
  `channel_code` varchar(128) NOT NULL COMMENT '布控通道编码',
  `rule_code` varchar(128) NOT NULL COMMENT '布控规则编码',
  `rule_name` varchar(256) NOT NULL COMMENT '布控规则名称',
  `subject_type` varchar(32) NOT NULL COMMENT '布控对象类型',
  `match_mode` varchar(16) NOT NULL DEFAULT 'AND' COMMENT '匹配条件组合方式',
  `match_fields_json` longtext NOT NULL COMMENT '匹配字段规则JSON',
  `priority` int NOT NULL DEFAULT '0' COMMENT '规则优先级',
  `status` varchar(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '记录业务状态',
  `source_system` varchar(256) DEFAULT NULL COMMENT '规则来源系统',
  `reason` varchar(512) DEFAULT NULL COMMENT '规则业务原因',
  `effective_from` datetime(6) DEFAULT NULL COMMENT '布控生效时间',
  `effective_to` datetime(6) DEFAULT NULL COMMENT '布控失效时间',
  `metadata_json` longtext COMMENT '扩展元数据JSON',
  `created_time` datetime(6) NOT NULL COMMENT '记录创建时间，精确到微秒',
  `updated_time` datetime(6) NOT NULL COMMENT '记录更新时间，精确到微秒',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志：0有效，1已删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_surveillance_rule_definition` (`snapshot_tid`,`rule_code`,`is_del`),
  KEY `idx_surveillance_rule_definition_channel` (`tenant_id`,`engine_code`,`channel_code`,`status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-通道匹配规则定义';

-- 补充缺失表：surveillance_rule_snapshot_t
CREATE TABLE `surveillance_rule_snapshot_t` (
  `tid` varchar(32) NOT NULL COMMENT '记录唯一标识',
  `tenant_id` varchar(32) NOT NULL COMMENT '所属认证租户标识',
  `engine_code` varchar(128) NOT NULL COMMENT '布控引擎编码',
  `channel_code` varchar(128) NOT NULL COMMENT '布控通道编码',
  `snapshot_version` bigint NOT NULL COMMENT '规则快照版本号',
  `digest` varchar(128) DEFAULT NULL COMMENT '规则快照内容摘要',
  `source_type` varchar(32) DEFAULT NULL COMMENT '规则快照来源类型',
  `source_ref` varchar(512) DEFAULT NULL COMMENT '规则快照来源引用',
  `rules_json` longtext NOT NULL COMMENT '规范化规则集合JSON',
  `generated_at` datetime(6) NOT NULL COMMENT '规则快照生成时间',
  `expires_at` datetime(6) DEFAULT NULL COMMENT '有效期截止时间',
  `snapshot_status` varchar(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '规则快照业务状态',
  `created_time` datetime(6) NOT NULL COMMENT '记录创建时间，精确到微秒',
  `updated_time` datetime(6) NOT NULL COMMENT '记录更新时间，精确到微秒',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志：0有效，1已删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_surveillance_snapshot_version` (`tenant_id`,`engine_code`,`channel_code`,`snapshot_version`,`is_del`),
  KEY `idx_surveillance_snapshot_current` (`tenant_id`,`engine_code`,`channel_code`,`snapshot_status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-规范化规则快照与版本';

-- 补充缺失表：surveillance_service_identity_t
CREATE TABLE `surveillance_service_identity_t` (
  `tid` varchar(64) NOT NULL COMMENT '记录唯一标识',
  `tenant_id` varchar(64) NOT NULL COMMENT '所属认证租户标识',
  `engine_code` varchar(64) NOT NULL COMMENT '布控引擎编码',
  `channel_code` varchar(32) NOT NULL COMMENT '布控通道编码',
  `resource_code` varchar(128) NOT NULL COMMENT '布控资源编码',
  `pipeline_id` varchar(64) NOT NULL COMMENT '关联画布流程标识',
  `token_hash` char(64) NOT NULL COMMENT '服务身份令牌摘要，不保存明文令牌',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '记录业务状态',
  `expires_at` datetime(6) DEFAULT NULL COMMENT '有效期截止时间',
  `created_time` datetime(6) NOT NULL COMMENT '记录创建时间，精确到微秒',
  `updated_time` datetime(6) NOT NULL COMMENT '记录更新时间，精确到微秒',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志：0有效，1已删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_surv_service_identity_token` (`token_hash`),
  KEY `idx_surv_service_identity_scope` (`tenant_id`,`engine_code`,`pipeline_id`,`status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-流程服务身份令牌摘要';

-- 补充缺失表：surveillance_task_deployment_t
CREATE TABLE `surveillance_task_deployment_t` (
  `tenant_id` varchar(64) NOT NULL COMMENT '所属认证租户标识',
  `resource_code` varchar(128) NOT NULL COMMENT '布控资源编码',
  `pipeline_id` varchar(64) NOT NULL COMMENT '关联画布流程标识',
  `process_group_id` varchar(64) NOT NULL COMMENT '已部署NiFi流程组标识',
  `input_connection_id` varchar(32) NOT NULL COMMENT '已部署输入消息连接标识',
  `output_connection_id` varchar(32) NOT NULL COMMENT '已部署结果消息连接标识',
  `deployment_hash` varchar(128) NOT NULL COMMENT '已部署配置内容摘要',
  `resource_json` text NOT NULL COMMENT '已部署资源配置JSON',
  `output_bootstrap_servers` varchar(1024) NOT NULL COMMENT '结果消息Kafka代理连接地址',
  `output_security_protocol` varchar(32) NOT NULL COMMENT '结果消息Kafka安全协议',
  `deployed_time` datetime(6) NOT NULL COMMENT '任务部署时间',
  PRIMARY KEY (`tenant_id`,`resource_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-任务部署配置';

-- 补充缺失表：surveillance_task_history_t
CREATE TABLE `surveillance_task_history_t` (
  `tid` varchar(32) NOT NULL COMMENT '记录唯一标识',
  `tenant_id` varchar(64) NOT NULL COMMENT '所属认证租户标识',
  `resource_code` varchar(256) NOT NULL COMMENT '布控资源编码',
  `action` varchar(32) NOT NULL COMMENT '任务生命周期操作',
  `state` varchar(32) NOT NULL COMMENT '任务生命周期状态',
  `message` varchar(512) DEFAULT NULL COMMENT '任务操作结果说明',
  `created_time` datetime(6) NOT NULL COMMENT '记录创建时间，精确到微秒',
  PRIMARY KEY (`tid`),
  KEY `ix_task_history` (`tenant_id`,`resource_code`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-任务生命周期操作记录';

-- 补充缺失表：surveillance_task_lifecycle_t
CREATE TABLE `surveillance_task_lifecycle_t` (
  `tenant_id` varchar(64) NOT NULL COMMENT '所属认证租户标识',
  `resource_code` varchar(256) NOT NULL COMMENT '布控资源编码',
  `state` varchar(32) NOT NULL COMMENT '任务生命周期状态',
  `pipelines_json` longtext COMMENT '任务关联流程配置JSON',
  `message` varchar(512) DEFAULT NULL COMMENT '任务操作结果说明',
  `updated_time` datetime(6) NOT NULL COMMENT '记录更新时间，精确到微秒',
  PRIMARY KEY (`tenant_id`,`resource_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-任务当前生命周期状态';

-- 补充缺失表：surveillance_topic_daily_stat_t
CREATE TABLE `surveillance_topic_daily_stat_t` (
  `tid` varchar(32) NOT NULL COMMENT '记录唯一标识',
  `tenant_id` varchar(32) NOT NULL COMMENT '所属认证租户标识',
  `engine_code` varchar(128) NOT NULL COMMENT '布控引擎编码',
  `channel_code` varchar(128) NOT NULL COMMENT '布控通道编码',
  `resource_code` varchar(128) NOT NULL COMMENT '布控资源编码',
  `input_topic` varchar(256) NOT NULL COMMENT '输入消息主题名称',
  `result_topic` varchar(256) NOT NULL COMMENT '匹配结果消息主题名称',
  `stat_date` date NOT NULL COMMENT '统计归属日期',
  `input_count` bigint NOT NULL DEFAULT '0' COMMENT '输入记录数量，单位条',
  `matched_count` bigint NOT NULL DEFAULT '0' COMMENT '匹配命中记录数量，单位条',
  `unmatched_count` bigint NOT NULL DEFAULT '0' COMMENT '未匹配记录数量，单位条',
  `failure_count` bigint NOT NULL DEFAULT '0' COMMENT '处理失败记录数量，单位条',
  `last_event_time` datetime(6) DEFAULT NULL COMMENT '最近业务事件时间',
  `updated_time` datetime(6) NOT NULL COMMENT '记录更新时间，精确到微秒',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_surveillance_topic_daily_stat` (`tenant_id`,`engine_code`,`resource_code`,`stat_date`),
  KEY `idx_surveillance_topic_daily_stat_query` (`tenant_id`,`engine_code`,`channel_code`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布控管理-资源主题每日增量统计';

-- 补充缺失表：svc_feedback_t
CREATE TABLE `svc_feedback_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `service_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务编码',
  `app_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '反馈应用标识',
  `feedback_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ISSUE' COMMENT '反馈类型：ISSUE问题、SUGGEST建议、OTHER其他',
  `content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '反馈内容',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN待处理、PROCESSING处理中、CLOSED已关闭',
  `handler` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '处理人',
  `handle_result` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '处理结果',
  `closed_time` datetime(6) DEFAULT NULL COMMENT '闭环时间',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  KEY `idx_svc_feedback_status` (`tenant_id`,`status`,`created_time`),
  KEY `idx_svc_feedback_service` (`tenant_id`,`service_code`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-服务反馈';

-- 补充缺失表：svc_gateway_app_t
CREATE TABLE `svc_gateway_app_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `app_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用编码',
  `app_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用名称',
  `auth_type` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'API_KEY' COMMENT '鉴权类型：NONE/API_KEY/HMAC/OAUTH2',
  `client_id` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户端标识',
  `credential_ref` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '凭证引用，禁止保存明文密钥',
  `contact` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系人',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用、DISABLED停用',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用说明',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_svc_gateway_app` (`tenant_id`,`app_code`,`is_del`),
  KEY `idx_svc_gateway_app_status` (`tenant_id`,`status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-接入应用';

-- 补充缺失表：svc_gateway_instance_t
CREATE TABLE `svc_gateway_instance_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `service_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务编码',
  `version` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'v1' COMMENT '服务版本',
  `instance_url` varchar(512) COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例地址',
  `weight` int NOT NULL DEFAULT '100' COMMENT '负载权重',
  `health_status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'UNKNOWN' COMMENT '健康状态：UNKNOWN/UP/DOWN',
  `last_heartbeat` datetime(6) DEFAULT NULL COMMENT '最近心跳时间',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用、DISABLED停用',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '实例说明',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_svc_gateway_instance` (`tenant_id`,`service_code`,`version`,`instance_url`),
  KEY `idx_svc_gateway_instance_health` (`tenant_id`,`service_code`,`health_status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-服务实例与健康状态';

-- 补充缺失表：svc_gateway_log_t
CREATE TABLE `svc_gateway_log_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `trace_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '链路追踪标识',
  `service_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务编码',
  `app_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '调用应用标识',
  `log_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '日志类型：REQUEST/EVENT/ERROR/FLOW_LIMIT/HEALTH/IP_LIMIT/CIRCUIT/PROTOCOL/AUTH',
  `uri` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求路径',
  `method` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'HTTP 方法',
  `status_code` int DEFAULT NULL COMMENT '响应状态码',
  `cost_ms` int DEFAULT NULL COMMENT '耗时毫秒',
  `error_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误编码',
  `client_ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户端 IP',
  `region_code` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '网络区域编码',
  `request_hash` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求摘要',
  `response_size` int DEFAULT NULL COMMENT '响应字节数',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  KEY `idx_svc_gateway_log_trace` (`tenant_id`,`trace_id`),
  KEY `idx_svc_gateway_log_service` (`tenant_id`,`service_code`,`created_time`),
  KEY `idx_svc_gateway_log_type` (`tenant_id`,`log_type`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-请求与治理日志';

-- 补充缺失表：svc_gateway_policy_t
CREATE TABLE `svc_gateway_policy_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `route_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属路由标识',
  `policy_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '策略编码',
  `policy_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '策略名称',
  `priority` int NOT NULL DEFAULT '0' COMMENT '生效优先级，数值越大越先应用',
  `auth_mode` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'TOKEN' COMMENT '鉴权方式：NONE/TOKEN/HMAC/OAUTH2',
  `ip_allow` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'IP 白名单，逗号分隔，为空表示不限制',
  `ip_deny` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'IP 黑名单，逗号分隔',
  `qps` int NOT NULL DEFAULT '0' COMMENT '每秒请求上限，0 表示不限制',
  `burst` int NOT NULL DEFAULT '0' COMMENT '令牌桶突发容量',
  `circuit_threshold` int NOT NULL DEFAULT '0' COMMENT '熔断失败阈值，0 表示关闭熔断',
  `circuit_window_ms` int NOT NULL DEFAULT '60000' COMMENT '熔断统计窗口毫秒数',
  `retry_backoff_ms` int NOT NULL DEFAULT '200' COMMENT '重试退避毫秒数',
  `param_rules_json` text COLLATE utf8mb4_general_ci COMMENT '参数校验规则 JSON',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用、DISABLED停用',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '策略说明',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_svc_gateway_policy` (`tenant_id`,`route_id`,`policy_code`,`is_del`),
  KEY `idx_svc_gateway_policy_route` (`tenant_id`,`route_id`,`status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-路由治理策略';

-- 补充缺失表：svc_gateway_region_t
CREATE TABLE `svc_gateway_region_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `region_code` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '区域编码',
  `region_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '区域名称',
  `network_zone` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '网络分区：INTERNET互联网区、GOV政务外网区、CROSS跨网区',
  `endpoint` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '区域入口地址',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用、DISABLED停用',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '区域说明',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_svc_gateway_region` (`tenant_id`,`region_code`,`is_del`),
  KEY `idx_svc_gateway_region_status` (`tenant_id`,`status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-网络区域';

-- 补充缺失表：svc_gateway_route_t
CREATE TABLE `svc_gateway_route_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `service_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务编码',
  `version` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'v1' COMMENT '服务版本',
  `path_prefix` varchar(256) COLLATE utf8mb4_general_ci NOT NULL COMMENT '网关路径前缀',
  `target_type` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'FLOW' COMMENT '目标类型：FLOW/HTTP/DB/KAFKA/ES',
  `target_ref` varchar(512) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标引用：FlowServe 服务标识、URL、表名或主题',
  `region_code` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '网络区域编码',
  `timeout_ms` int NOT NULL DEFAULT '3000' COMMENT '请求超时毫秒数',
  `retry_times` int NOT NULL DEFAULT '0' COMMENT '失败重试次数，仅幂等方法生效',
  `lb_strategy` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'WEIGHT' COMMENT '负载均衡策略：WEIGHT/ROUND_ROBIN',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿、PUBLISHED已发布、DISABLED停用',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由说明',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_svc_gateway_route` (`tenant_id`,`service_code`,`version`,`is_del`),
  KEY `idx_svc_gateway_route_status` (`tenant_id`,`status`,`is_del`),
  KEY `idx_svc_gateway_route_prefix` (`tenant_id`,`path_prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-服务路由与目标配置';

-- 补充缺失表：svc_gray_release_t
CREATE TABLE `svc_gray_release_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `service_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务编码',
  `from_version` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '原版本',
  `to_version` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '灰度版本',
  `strategy` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'WEIGHT' COMMENT '灰度策略：WEIGHT/HEADER/IP',
  `match_value` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '匹配值：Header 值或 IP 段',
  `weight` int NOT NULL DEFAULT '0' COMMENT '灰度流量权重百分比',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿、RUNNING运行中、STOPPED已停止',
  `start_time` datetime(6) DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime(6) DEFAULT NULL COMMENT '结束时间',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '灰度说明',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_svc_gray_release` (`tenant_id`,`service_code`,`from_version`,`to_version`,`is_del`),
  KEY `idx_svc_gray_release_status` (`tenant_id`,`service_code`,`status`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-灰度发布规则';

-- 补充缺失表：svc_market_package_item_t
CREATE TABLE `svc_market_package_item_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `package_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '合集标识',
  `service_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务编码',
  `version` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'v1' COMMENT '服务版本',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_svc_market_package_item` (`tenant_id`,`package_id`,`service_code`,`version`,`is_del`),
  KEY `idx_svc_market_package_item_pkg` (`tenant_id`,`package_id`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-服务合集明细';

-- 补充缺失表：svc_market_package_t
CREATE TABLE `svc_market_package_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `package_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '合集编码',
  `package_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '合集名称',
  `subscription_mode` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PACKAGE' COMMENT '订阅模式：SINGLE单服务、PACKAGE整套',
  `approval_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要审批：0否，1是',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿、ON_SHELF上架、OFF_SHELF下架',
  `description` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '合集说明',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_svc_market_package` (`tenant_id`,`package_code`,`is_del`),
  KEY `idx_svc_market_package_status` (`tenant_id`,`status`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-服务合集';

-- 补充缺失表：svc_metric_hour_t
CREATE TABLE `svc_metric_hour_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `stat_hour` datetime(6) NOT NULL COMMENT '统计小时',
  `service_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务编码',
  `app_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '调用应用标识',
  `call_count` bigint NOT NULL DEFAULT '0' COMMENT '调用总次数',
  `success_count` bigint NOT NULL DEFAULT '0' COMMENT '成功次数',
  `fail_count` bigint NOT NULL DEFAULT '0' COMMENT '失败次数',
  `p95_ms` int NOT NULL DEFAULT '0' COMMENT 'P95 耗时毫秒',
  `active_users` int NOT NULL DEFAULT '0' COMMENT '活跃应用数',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_svc_metric_hour` (`tenant_id`,`stat_hour`,`service_code`,`app_id`,`is_del`),
  KEY `idx_svc_metric_hour_service` (`tenant_id`,`service_code`,`stat_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-小时级调用指标';

-- 补充缺失表：svc_subscription_t
CREATE TABLE `svc_subscription_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `app_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订阅应用标识',
  `service_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务编码',
  `version` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'v1' COMMENT '服务版本',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待审批、ACTIVE生效、EXPIRED过期、REVOKED撤销',
  `valid_from` datetime(6) DEFAULT NULL COMMENT '生效时间',
  `valid_until` datetime(6) DEFAULT NULL COMMENT '失效时间',
  `quota` bigint NOT NULL DEFAULT '0' COMMENT '调用配额，0 表示不限制',
  `apply_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批单标识',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订阅说明',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_svc_subscription` (`tenant_id`,`app_id`,`service_code`,`version`,`is_del`),
  KEY `idx_svc_subscription_status` (`tenant_id`,`status`,`valid_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务网关-服务订阅';

-- 补充字段：api_info_t
ALTER TABLE `api_info_t`
  ADD COLUMN `datasource_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据服务-所属代理访问数据源标识，关联db_datasource_t.tid';

-- 补充索引：api_info_t
ALTER TABLE `api_info_t`
  ADD KEY `idx_api_source_page` (`tenant_id`,`datasource_id`,`is_del`,`updated_time`,`tid`);

-- 补充字段：da_catalog_t
ALTER TABLE `da_catalog_t`
  ADD COLUMN `market_view_count` bigint NOT NULL DEFAULT '0' COMMENT '数据超市目录累计浏览次数';

-- 补充字段：db_table_t
ALTER TABLE `db_table_t`
  ADD COLUMN `access_request_status` int NOT NULL DEFAULT '0' COMMENT '接入申请状态：0未申请，1待处理，2已受理',
  ADD COLUMN `access_requested_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '接入申请人标识',
  ADD COLUMN `access_requested_at` datetime(6) DEFAULT NULL COMMENT '接入申请时间';

-- 补充字段：log_request_t
ALTER TABLE `log_request_t`
  ADD COLUMN `category` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '事件类别：AUTH、OPERATION、REQUEST、SECURITY、TASK',
  ADD COLUMN `source_app` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '事件来源应用标识',
  ADD COLUMN `actor_kind` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '可信主体类别',
  ADD COLUMN `target_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作目标名称快照',
  ADD COLUMN `received_time` datetime(6) DEFAULT NULL COMMENT '事件接收时间，UTC',
  ADD COLUMN `request_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '本次请求关联标识',
  ADD COLUMN `operation_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '稳定业务操作关联标识',
  ADD COLUMN `session_ref` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '独立随机会话引用，不含登录凭据',
  ADD COLUMN `origin_key` varchar(160) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原始事件幂等标识',
  ADD COLUMN `module_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '功能模块编码',
  ADD COLUMN `function_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '功能资源编码',
  ADD COLUMN `user_agent` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '经限长的终端描述',
  ADD COLUMN `business_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '独立业务结果编码',
  ADD COLUMN `batch_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联业务批次',
  ADD COLUMN `task_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联执行任务',
  ADD COLUMN `total_count` bigint DEFAULT NULL COMMENT '操作目标总数，未知时为空',
  ADD COLUMN `success_count` bigint DEFAULT NULL COMMENT '成功目标数量',
  ADD COLUMN `failure_count` bigint DEFAULT NULL COMMENT '失败目标数量',
  ADD COLUMN `skipped_count` bigint DEFAULT NULL COMMENT '跳过目标数量',
  ADD COLUMN `action_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '稳定业务动作编码',
  ADD COLUMN `target_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标对象类型',
  ADD COLUMN `target_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标对象稳定标识',
  ADD COLUMN `result` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务结果：SUCCESS、FAILED、DENIED、PARTIAL等',
  ADD COLUMN `reason_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '脱敏原因编码',
  ADD COLUMN `event_time` datetime(6) DEFAULT NULL COMMENT '事实发生时间，UTC；历史未知时为空',
  ADD COLUMN `trace_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '统一请求链路标识',
  ADD COLUMN `route_path` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求路由，不含查询参数',
  ADD COLUMN `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时，毫秒',
  ADD COLUMN `http_status` int DEFAULT NULL COMMENT '实际HTTP响应状态',
  ADD COLUMN `detail_json` longtext COLLATE utf8mb4_general_ci COMMENT '白名单脱敏事件摘要',
  ADD COLUMN `request_bytes` bigint DEFAULT NULL COMMENT '请求字节数，未知时为空',
  ADD COLUMN `response_bytes` bigint DEFAULT NULL COMMENT '响应字节数，未知时为空',
  ADD COLUMN `sampled` tinyint DEFAULT NULL COMMENT '是否按策略采样',
  ADD COLUMN `sample_rate` decimal(6,5) DEFAULT NULL COMMENT '实际采样比例',
  ADD COLUMN `downstream_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '下游结果编码',
  ADD COLUMN `error_ref` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '受控错误引用',
  ADD COLUMN `menu_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作时已核验的菜单资源标识',
  ADD COLUMN `menu_path` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作时菜单名称层级快照，箭头分隔',
  ADD COLUMN `page_route` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '已核验的页面路由，不含查询参数',
  ADD COLUMN `operation_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作时Magic接口或显式动作的中文名称';

-- 扩大日志字段长度，保留现有记录：log_request_t
ALTER TABLE `log_request_t`
  MODIFY COLUMN `account` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '账号',
  MODIFY COLUMN `full_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '完整名称',
  MODIFY COLUMN `org_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组织名称',
  MODIFY COLUMN `ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'IP地址';

-- 补充索引：log_request_t
ALTER TABLE `log_request_t`
  ADD UNIQUE KEY `ux_request_t_origin` (`tenant_id`,`origin_key`),
  ADD KEY `ix_request_t_event` (`tenant_id`,`event_time`,`tid`);

-- 补充字段：log_system_t
ALTER TABLE `log_system_t`
  ADD COLUMN `category` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '事件类别：AUTH、OPERATION、REQUEST、SECURITY、TASK',
  ADD COLUMN `source_app` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '事件来源应用标识',
  ADD COLUMN `actor_kind` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '可信主体类别',
  ADD COLUMN `target_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作目标名称快照',
  ADD COLUMN `received_time` datetime(6) DEFAULT NULL COMMENT '事件接收时间，UTC',
  ADD COLUMN `request_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '本次请求关联标识',
  ADD COLUMN `operation_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '稳定业务操作关联标识',
  ADD COLUMN `session_ref` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '独立随机会话引用，不含登录凭据',
  ADD COLUMN `origin_key` varchar(160) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原始事件幂等标识',
  ADD COLUMN `module_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '功能模块编码',
  ADD COLUMN `function_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '功能资源编码',
  ADD COLUMN `user_agent` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '经限长的终端描述',
  ADD COLUMN `business_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '独立业务结果编码',
  ADD COLUMN `batch_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联业务批次',
  ADD COLUMN `task_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联执行任务',
  ADD COLUMN `total_count` bigint DEFAULT NULL COMMENT '操作目标总数，未知时为空',
  ADD COLUMN `success_count` bigint DEFAULT NULL COMMENT '成功目标数量',
  ADD COLUMN `failure_count` bigint DEFAULT NULL COMMENT '失败目标数量',
  ADD COLUMN `skipped_count` bigint DEFAULT NULL COMMENT '跳过目标数量',
  ADD COLUMN `action_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '稳定业务动作编码',
  ADD COLUMN `target_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标对象类型',
  ADD COLUMN `target_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标对象稳定标识',
  ADD COLUMN `result` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务结果：SUCCESS、FAILED、DENIED、PARTIAL等',
  ADD COLUMN `reason_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '脱敏原因编码',
  ADD COLUMN `event_time` datetime(6) DEFAULT NULL COMMENT '事实发生时间，UTC；历史未知时为空',
  ADD COLUMN `trace_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '统一请求链路标识',
  ADD COLUMN `route_path` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求路由，不含查询参数',
  ADD COLUMN `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时，毫秒',
  ADD COLUMN `http_status` int DEFAULT NULL COMMENT '实际HTTP响应状态',
  ADD COLUMN `org_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当时所属部门名称',
  ADD COLUMN `ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '直接连接或可信代理确认的客户端地址',
  ADD COLUMN `request_method` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'HTTP请求方法',
  ADD COLUMN `change_json` longtext COLLATE utf8mb4_general_ci COMMENT '白名单脱敏字段变更',
  ADD COLUMN `origin_record_type` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原领域记录类型',
  ADD COLUMN `origin_record_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原领域记录标识',
  ADD COLUMN `menu_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作时已核验的菜单资源标识',
  ADD COLUMN `menu_path` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作时菜单名称层级快照，箭头分隔',
  ADD COLUMN `page_route` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '已核验的页面路由，不含查询参数',
  ADD COLUMN `operation_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作时Magic接口或显式动作的中文名称';

-- 扩大日志字段长度，保留现有记录：log_system_t
ALTER TABLE `log_system_t`
  MODIFY COLUMN `user_account` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户账号',
  MODIFY COLUMN `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户标识',
  MODIFY COLUMN `org_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组织标识';

-- 补充索引：log_system_t
ALTER TABLE `log_system_t`
  ADD UNIQUE KEY `ux_system_t_origin` (`tenant_id`,`origin_key`),
  ADD KEY `ix_system_t_event` (`tenant_id`,`event_time`,`tid`);

-- 补充字段：log_template_t
ALTER TABLE `log_template_t`
  ADD COLUMN `action_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '声明式业务动作编码',
  ADD COLUMN `module_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '模块编码',
  ADD COLUMN `function_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '功能资源编码',
  ADD COLUMN `audit_level` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审计等级',
  ADD COLUMN `field_policy_json` longtext COLLATE utf8mb4_general_ci COMMENT '脱敏白名单字段策略',
  ADD COLUMN `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用规则',
  ADD COLUMN `version` bigint NOT NULL DEFAULT '1' COMMENT '规则乐观锁版本';

-- 补充字段：log_user_login_t
ALTER TABLE `log_user_login_t`
  ADD COLUMN `category` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '事件类别：AUTH、OPERATION、REQUEST、SECURITY、TASK',
  ADD COLUMN `source_app` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '事件来源应用标识',
  ADD COLUMN `actor_kind` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '可信主体类别',
  ADD COLUMN `target_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作目标名称快照',
  ADD COLUMN `received_time` datetime(6) DEFAULT NULL COMMENT '事件接收时间，UTC',
  ADD COLUMN `request_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '本次请求关联标识',
  ADD COLUMN `operation_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '稳定业务操作关联标识',
  ADD COLUMN `session_ref` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '独立随机会话引用，不含登录凭据',
  ADD COLUMN `origin_key` varchar(160) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原始事件幂等标识',
  ADD COLUMN `module_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '功能模块编码',
  ADD COLUMN `function_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '功能资源编码',
  ADD COLUMN `user_agent` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '经限长的终端描述',
  ADD COLUMN `business_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '独立业务结果编码',
  ADD COLUMN `batch_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联业务批次',
  ADD COLUMN `task_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联执行任务',
  ADD COLUMN `total_count` bigint DEFAULT NULL COMMENT '操作目标总数，未知时为空',
  ADD COLUMN `success_count` bigint DEFAULT NULL COMMENT '成功目标数量',
  ADD COLUMN `failure_count` bigint DEFAULT NULL COMMENT '失败目标数量',
  ADD COLUMN `skipped_count` bigint DEFAULT NULL COMMENT '跳过目标数量',
  ADD COLUMN `action_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '稳定业务动作编码',
  ADD COLUMN `target_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标对象类型',
  ADD COLUMN `target_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标对象稳定标识',
  ADD COLUMN `result` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务结果：SUCCESS、FAILED、DENIED、PARTIAL等',
  ADD COLUMN `reason_code` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '脱敏原因编码',
  ADD COLUMN `event_time` datetime(6) DEFAULT NULL COMMENT '事实发生时间，UTC；历史未知时为空',
  ADD COLUMN `trace_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '统一请求链路标识',
  ADD COLUMN `route_path` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求路由，不含查询参数',
  ADD COLUMN `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时，毫秒',
  ADD COLUMN `http_status` int DEFAULT NULL COMMENT '实际HTTP响应状态',
  ADD COLUMN `full_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当时姓名快照',
  ADD COLUMN `org_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当时所属部门标识',
  ADD COLUMN `org_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当时所属部门名称',
  ADD COLUMN `request_method` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'HTTP请求方法',
  ADD COLUMN `detail_json` longtext COLLATE utf8mb4_general_ci COMMENT '白名单脱敏事件摘要',
  ADD COLUMN `auth_realm` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话认证域',
  ADD COLUMN `auth_step` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '认证或会话阶段',
  ADD COLUMN `termination_reason` varchar(48) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话终止原因',
  ADD COLUMN `account_fingerprint` char(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败账号带密钥HMAC指纹，不含口令',
  ADD COLUMN `fingerprint_key_version` int DEFAULT NULL COMMENT '账号HMAC密钥版本',
  ADD COLUMN `expires_at` datetime(6) DEFAULT NULL COMMENT '会话实际失效时间，UTC',
  ADD COLUMN `observed_at` datetime(6) DEFAULT NULL COMMENT '服务端观察到失效的时间，UTC',
  ADD COLUMN `menu_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作时已核验的菜单资源标识',
  ADD COLUMN `menu_path` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作时菜单名称层级快照，箭头分隔',
  ADD COLUMN `page_route` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '已核验的页面路由，不含查询参数',
  ADD COLUMN `operation_name` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作时Magic接口或显式动作的中文名称';

-- 扩大日志字段长度，保留现有记录：log_user_login_t
ALTER TABLE `log_user_login_t`
  MODIFY COLUMN `account` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '账号',
  MODIFY COLUMN `ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'IP地址';

-- 补充索引：log_user_login_t
ALTER TABLE `log_user_login_t`
  ADD UNIQUE KEY `ux_user_logi_origin` (`tenant_id`,`origin_key`),
  ADD KEY `ix_user_logi_event` (`tenant_id`,`event_time`,`tid`);
