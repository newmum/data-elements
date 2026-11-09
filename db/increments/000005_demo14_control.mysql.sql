-- 14 演示环境结构补齐（MySQL）；仅用于经过现场核对的旧版演示库。
-- 控制库与四个租户库分别执行各自文件；此文件不删除既有表、列或数据。
-- 前置：确认 DATABASE()、表集合、列和索引与生成计划完全一致，磁盘及元数据锁充足。
-- 与 000002/000003 中相同的目标结构已包含在此升级中，不得重复执行。
-- DDL 隐式提交；失败时立即停止，逐条核对后再继续，不支持事务整批回滚。
-- 本次演示环境按用户明确要求不备份；其他环境执行前须审阅适用范围、备份和恢复方案。
SET SESSION lock_wait_timeout=5;
SET SESSION innodb_lock_wait_timeout=5;
SELECT DATABASE();

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

-- 补充字段：data_push_relay_client_t
ALTER TABLE `data_push_relay_client_t`
  ADD COLUMN `api_key_sha256` char(64) DEFAULT NULL COMMENT '平台生成的业务推送密钥SHA-256摘要；为空表示沿用海通历史签发密钥',
  ADD COLUMN `previous_api_key_sha256` char(64) DEFAULT NULL COMMENT '上一版业务推送密钥摘要，用于轮换同步的并发检查';

-- 补充字段：iam_audit_event_t
ALTER TABLE `iam_audit_event_t`
  ADD COLUMN `category` varchar(24) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '事件类别：AUTH、OPERATION、REQUEST、SECURITY、TASK',
  ADD COLUMN `source_app` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '事件来源应用标识',
  ADD COLUMN `target_name` varchar(256) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作目标名称快照',
  ADD COLUMN `received_time` datetime(6) DEFAULT NULL COMMENT '事件接收时间，UTC',
  ADD COLUMN `request_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '本次请求关联标识',
  ADD COLUMN `operation_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '稳定业务操作关联标识',
  ADD COLUMN `session_ref` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '独立随机会话引用，不含登录凭据',
  ADD COLUMN `origin_key` varchar(160) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '原始事件幂等标识',
  ADD COLUMN `module_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '功能模块编码',
  ADD COLUMN `function_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '功能资源编码',
  ADD COLUMN `user_agent` varchar(512) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '经限长的终端描述',
  ADD COLUMN `business_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '独立业务结果编码',
  ADD COLUMN `batch_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '关联业务批次',
  ADD COLUMN `task_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '关联执行任务',
  ADD COLUMN `total_count` bigint DEFAULT NULL COMMENT '操作目标总数，未知时为空',
  ADD COLUMN `success_count` bigint DEFAULT NULL COMMENT '成功目标数量',
  ADD COLUMN `failure_count` bigint DEFAULT NULL COMMENT '失败目标数量',
  ADD COLUMN `skipped_count` bigint DEFAULT NULL COMMENT '跳过目标数量',
  ADD COLUMN `actor_account` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当时主体账号快照',
  ADD COLUMN `actor_name` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当时主体姓名快照',
  ADD COLUMN `actor_org_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当时所属部门标识',
  ADD COLUMN `actor_org_name` varchar(256) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当时所属部门名称',
  ADD COLUMN `span_id` char(16) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '链路跨度标识',
  ADD COLUMN `legacy_trace_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '兼容原有错误跟踪号',
  ADD COLUMN `menu_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作时已核验的菜单资源标识',
  ADD COLUMN `menu_path` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作时菜单名称层级快照，箭头分隔',
  ADD COLUMN `page_route` varchar(512) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '已核验的页面路由，不含查询参数',
  ADD COLUMN `operation_name` varchar(256) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作时Magic接口或显式动作的中文名称';

-- 补充索引：iam_audit_event_t
ALTER TABLE `iam_audit_event_t`
  ADD UNIQUE KEY `ux_audit_eve_origin` (`origin_key`),
  ADD KEY `ix_audit_eve_event` (`identity_domain`,`event_time`,`tid`);
