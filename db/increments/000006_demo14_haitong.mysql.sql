-- 14 演示环境独立海通库补齐 MySQL 结构，仅适用于已核对的5表起始版本。
-- 仅创建10张缺失表，不改写既有海通表或数据；按外键依赖顺序执行。
-- 前置：确认当前库为 haitong，所有目标表缺失，现有5表结构匹配基线。
-- 失败停止；DDL隐式提交，核对已完成项后再制定后续执行方案。
-- 本次演示环境按用户明确要求不备份；其他环境执行前须审阅适用范围、备份和恢复方案。
SET SESSION lock_wait_timeout=5;
SELECT DATABASE();

-- 补充缺失表：ht_file_upload
CREATE TABLE `ht_file_upload` (
  `upload_id` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '上传会话UUID，同时为海通文件版本标识',
  `tenant_id` varchar(80) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '由服务端集合契约确定的租户标识',
  `owner` varchar(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '经过认证的上传调用方标识',
  `datasource_id` varchar(80) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '服务端已配置的数据源标识',
  `collection_id` varchar(80) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '服务端已配置的文件集合标识',
  `contract_version` bigint NOT NULL COMMENT '受理时的文件契约版本',
  `contract_hash` char(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '受理时文件契约SHA256摘要',
  `contract_json` longtext COLLATE utf8mb4_0900_bin NOT NULL COMMENT '不含凭据的冻结集合策略与投递计划',
  `init_key` varchar(200) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '初始化请求幂等键',
  `init_hash` char(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '规范化初始化参数SHA256摘要',
  `file_name` varchar(240) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '仅用于显示的原始文件名，不参与路径拼接',
  `media_type` varchar(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '文件扩展名对应的预期媒体类型',
  `size_bytes` bigint NOT NULL COMMENT '声明原始文件大小，单位字节',
  `expected_sha256` char(64) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '客户端可选声明的完整原文SHA256摘要',
  `final_sha256` char(64) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '服务端流式核验的完整原文SHA256摘要',
  `part_size` bigint NOT NULL COMMENT '服务器指定的标准分片大小，单位字节',
  `part_count` int NOT NULL COMMENT '本会话预期分片数量',
  `state` varchar(24) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'CREATED UPLOADING FINALIZING READY REJECTED ABORTED EXPIRED',
  `security_state` varchar(40) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'PENDING CLEAN HEADER_VERIFIED_NOT_SCANNED REJECTED',
  `payload_path` varchar(1024) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '受限工作目录内的持久原文相对路径',
  `quota_state` varchar(16) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'RESERVED COMMITTED RELEASED，保证配额幂等转换',
  `submission_key` varchar(200) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '文件业务提交幂等键',
  `submission_hash` char(64) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '规范化业务提交内容SHA256摘要',
  `submission_json` longtext COLLATE utf8mb4_0900_bin COMMENT '冻结业务键和已校验业务元数据，不含文件原文',
  `message_id` varchar(36) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '预分配的统一业务消息UUID',
  `published_at` bigint DEFAULT NULL COMMENT '正式投递任务创建UTC毫秒，空表示尚未创建',
  `lease_token` varchar(36) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '处理者领取标识，用于防止过期写入',
  `lease_until` bigint NOT NULL DEFAULT '0' COMMENT '领取租约截止UTC毫秒',
  `next_attempt_at` bigint NOT NULL DEFAULT '0' COMMENT '后台处理下次允许执行UTC毫秒',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '后台校验或发布处理尝试次数',
  `error_code` varchar(80) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '脱敏处理失败码',
  `created_at` bigint NOT NULL COMMENT '会话创建UTC毫秒',
  `updated_at` bigint NOT NULL COMMENT '会话最后更新UTC毫秒',
  `expires_at` bigint NOT NULL COMMENT '未完成上传会话到期UTC毫秒',
  PRIMARY KEY (`upload_id`),
  UNIQUE KEY `uq_file_upload_tenant` (`tenant_id`,`upload_id`),
  UNIQUE KEY `uq_file_upload_init` (`tenant_id`,`owner`,`datasource_id`,`collection_id`,`init_key`),
  UNIQUE KEY `uq_file_upload_submit` (`tenant_id`,`owner`,`datasource_id`,`collection_id`,`submission_key`),
  UNIQUE KEY `uq_file_upload_message` (`message_id`),
  KEY `ix_file_upload_page` (`tenant_id`,`datasource_id`,`collection_id`,`owner`,`created_at`,`upload_id`),
  KEY `ix_file_upload_worker` (`state`,`next_attempt_at`,`lease_until`,`created_at`),
  KEY `ix_file_upload_expiry` (`quota_state`,`expires_at`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='非结构化接入-文件上传会话';

-- 补充缺失表：ht_magic_api_file
CREATE TABLE `ht_magic_api_file` (
  `file_path` varchar(512) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Magic API 资源完整路径',
  `file_content` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Magic API 资源内容',
  PRIMARY KEY (`file_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='haitong-data-pull Magic API 资源库';

-- 补充缺失表：ht_standalone_datasource
CREATE TABLE `ht_standalone_datasource` (
  `datasource_id` varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  `datasource_name` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `source_system_id` varchar(128) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '',
  `source_system_name` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '',
  `contract_version` varchar(100) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '',
  `is_enable` smallint NOT NULL DEFAULT '1',
  `is_del` smallint NOT NULL DEFAULT '0',
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- 补充缺失表：ht_standalone_datasource_client
CREATE TABLE `ht_standalone_datasource_client` (
  `datasource_id` varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  `client_id` varchar(128) COLLATE utf8mb4_0900_bin NOT NULL,
  `is_enable` smallint NOT NULL DEFAULT '1',
  `is_del` smallint NOT NULL DEFAULT '0',
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`datasource_id`,`client_id`),
  KEY `ix_standalone_client_active` (`datasource_id`,`is_enable`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- 补充缺失表：ht_standalone_registry_sync
CREATE TABLE `ht_standalone_registry_sync` (
  `idempotency_key` varchar(200) COLLATE utf8mb4_0900_bin NOT NULL,
  `payload_sha256` char(64) COLLATE utf8mb4_0900_bin NOT NULL,
  `datasource_id` varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  `applied_at` bigint NOT NULL,
  PRIMARY KEY (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- 补充缺失表：ht_standalone_table
CREATE TABLE `ht_standalone_table` (
  `datasource_id` varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  `table_name` varchar(200) COLLATE utf8mb4_0900_bin NOT NULL,
  `table_id` varchar(128) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '',
  `table_comment` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '',
  `schema_version` varchar(100) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '',
  `is_enable` smallint NOT NULL DEFAULT '1',
  `is_del` smallint NOT NULL DEFAULT '0',
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`datasource_id`,`table_name`),
  KEY `ix_standalone_table_active` (`datasource_id`,`is_enable`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- 补充缺失表：ht_standalone_table_column
CREATE TABLE `ht_standalone_table_column` (
  `datasource_id` varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  `table_name` varchar(200) COLLATE utf8mb4_0900_bin NOT NULL,
  `column_name` varchar(200) COLLATE utf8mb4_0900_bin NOT NULL,
  `column_comment` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '',
  `data_type` varchar(100) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '',
  `ordinal_no` int NOT NULL,
  `is_required` smallint NOT NULL DEFAULT '0',
  `is_enable` smallint NOT NULL DEFAULT '1',
  `is_del` smallint NOT NULL DEFAULT '0',
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`datasource_id`,`table_name`,`column_name`),
  KEY `ix_standalone_column_active` (`datasource_id`,`table_name`,`is_enable`,`is_del`,`ordinal_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- 补充缺失表：ht_storage_quota
CREATE TABLE `ht_storage_quota` (
  `quota_key` varchar(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'GLOBAL或租户数据源集合复合范围摘要',
  `tenant_id` varchar(80) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '集合配额的实际租户标识，全局容量行为空',
  `limit_bytes` bigint NOT NULL COMMENT '该范围允许的文件字节容量',
  `reserved_bytes` bigint NOT NULL DEFAULT '0' COMMENT '已预留上传与合并字节数',
  `committed_bytes` bigint NOT NULL DEFAULT '0' COMMENT '正式保留原文字节数',
  `active_sessions` int NOT NULL DEFAULT '0' COMMENT '尚未完成或释放的会话数量',
  `updated_at` bigint NOT NULL COMMENT '配额最后变更UTC毫秒',
  PRIMARY KEY (`quota_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='非结构化接入-全局及集合存储配额账本';

-- 补充缺失表：ht_file_receipt
CREATE TABLE `ht_file_receipt` (
  `receipt_id` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '本地接收回执UUID',
  `tenant_id` varchar(80) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '由投递文件确定的租户标识',
  `sender_id` varchar(80) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '已认证并获授权的接收方身份',
  `receipt_key` varchar(200) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '接收方提供的回执幂等键',
  `payload_hash` char(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '规范化回执内容SHA256摘要',
  `upload_id` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '固定文件版本对应上传会话UUID',
  `delivery_id` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '已核验成功的传输任务UUID',
  `target_code` varchar(80) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '契约确定的投递目标编号',
  `sha256` char(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '接收方确认的完整原文SHA256摘要',
  `business_status` varchar(16) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'ACCEPTED或REJECTED，仅表示对方业务处理结果',
  `business_ref` varchar(200) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '接收方业务落库或处理编号',
  `received_at` bigint NOT NULL COMMENT '回执接收UTC毫秒',
  PRIMARY KEY (`receipt_id`),
  UNIQUE KEY `uq_file_receipt_key` (`tenant_id`,`sender_id`,`receipt_key`),
  UNIQUE KEY `uq_file_receipt_delivery` (`delivery_id`),
  KEY `ix_file_receipt_upload` (`tenant_id`,`upload_id`,`received_at`),
  CONSTRAINT `fk_file_receipt_delivery` FOREIGN KEY (`delivery_id`) REFERENCES `ht_delivery` (`id`),
  CONSTRAINT `fk_file_receipt_upload` FOREIGN KEY (`tenant_id`, `upload_id`) REFERENCES `ht_file_upload` (`tenant_id`, `upload_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='非结构化接入-目标系统业务接收回执';

-- 补充缺失表：ht_file_upload_part
CREATE TABLE `ht_file_upload_part` (
  `upload_id` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '所属上传会话UUID',
  `tenant_id` varchar(80) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '由父上传会话确定的租户标识',
  `part_no` int NOT NULL COMMENT '分片顺序号，从1开始',
  `size_bytes` bigint NOT NULL DEFAULT '0' COMMENT '已核验的分片字节数',
  `sha256` char(64) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '服务端计算的分片SHA256摘要',
  `payload_path` varchar(1024) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '受限工作目录内不可变分片相对路径',
  `state` varchar(16) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'WRITING COMPLETE FAILED',
  `attempt_token` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '本次写入领取标识',
  `lease_until` bigint NOT NULL COMMENT '写入领取截止UTC毫秒',
  `updated_at` bigint NOT NULL COMMENT '分片最后更新UTC毫秒',
  PRIMARY KEY (`upload_id`,`part_no`),
  KEY `fk_file_part_upload` (`tenant_id`,`upload_id`),
  CONSTRAINT `fk_file_part_upload` FOREIGN KEY (`tenant_id`, `upload_id`) REFERENCES `ht_file_upload` (`tenant_id`, `upload_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='非结构化接入-已确认及在途上传分片';
