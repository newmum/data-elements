-- 当前完整结构基线：仅适用于新空库，不含业务数据、账号或授权。
-- 不得直接在现有数据库执行；现有库变更须单独审阅差异并备份。
SET NAMES utf8mb4;
CREATE DATABASE `haitong` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `haitong`;
SET FOREIGN_KEY_CHECKS=0;

-- TABLE ht_delivery
CREATE TABLE `ht_delivery` (
  `id` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '稳定deliveryId下游幂等依据',
  `message_id` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '所属逻辑消息',
  `target_code` varchar(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '逻辑目标编码',
  `target_type` varchar(20) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'KAFKA FTP LOCAL ACTION',
  `target_plan` longtext COLLATE utf8mb4_0900_bin NOT NULL COMMENT '冻结的目标配置不含密钥',
  `status` varchar(30) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'PENDING RUNNING RETRY_WAIT SUCCEEDED FAILED UNKNOWN',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已执行次数不等于业务条数',
  `max_attempts` int NOT NULL COMMENT '自动尝试上限',
  `retry_safe` smallint NOT NULL COMMENT '可安全重试标记0或1',
  `next_attempt_at` bigint NOT NULL COMMENT '下一次执行UTC毫秒',
  `lease_token` varchar(36) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '本次attemptId及数据库围栏令牌',
  `lease_until` bigint NOT NULL DEFAULT '0' COMMENT '租约截止UTC毫秒',
  `attempt_started_at` bigint DEFAULT NULL COMMENT '最近一次执行开始UTC毫秒',
  `result_metadata` longtext COLLATE utf8mb4_0900_bin COMMENT '协议回执或后端响应JSON受访问控制',
  `last_error_code` varchar(80) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '最近错误码',
  `last_error_message` varchar(255) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '脱敏错误说明',
  `retry_audit` longtext COLLATE utf8mb4_0900_bin NOT NULL COMMENT '人工重试及核实摘要JSON',
  `created_at` bigint NOT NULL COMMENT '任务创建UTC毫秒',
  `updated_at` bigint NOT NULL COMMENT '最后修改UTC毫秒',
  `completed_at` bigint DEFAULT NULL COMMENT '目标已确认UTC毫秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_delivery_target` (`message_id`,`target_code`),
  KEY `ix_delivery_ready` (`target_type`,`status`,`next_attempt_at`),
  KEY `ix_delivery_lease` (`status`,`lease_until`),
  KEY `ix_delivery_message` (`message_id`,`status`),
  CONSTRAINT `fk_delivery_message` FOREIGN KEY (`message_id`) REFERENCES `ht_message` (`id`),
  CONSTRAINT `ck_delivery_safe` CHECK ((`retry_safe` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='每个目标的当前投递任务';

-- TABLE ht_exchange_log
CREATE TABLE `ht_exchange_log` (
  `id` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '不可变日志UUID',
  `protocol` varchar(16) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'HTTP KAFKA FTP LOCAL',
  `direction` varchar(16) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'IN OUT ADMIN',
  `event_type` varchar(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'MESSAGE_RECEIVE DELIVERY HTTP_ACCESS FILE_DOWNLOAD等',
  `phase` varchar(16) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'START FINISH RESULT',
  `outcome` varchar(30) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '本次事件结果不是整体业务结果',
  `attempt_id` varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '一次尝试ID非稳定投递ID',
  `request_id` varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT 'HTTP服务端请求ID',
  `owner` varchar(100) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '经过认证的来源或操作者',
  `route_code` varchar(100) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '逻辑路由',
  `message_id` varchar(36) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '关联消息可空',
  `delivery_id` varchar(36) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '关联稳定目标任务可空',
  `file_id` varchar(36) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '关联文件可空',
  `target_code` varchar(100) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '逻辑目标',
  `source_ref` varchar(1000) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '来源路径或协议坐标不得含凭证',
  `destination_ref` varchar(1000) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '目标路径Topic或动作编码',
  `business_key` varchar(200) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '源业务编号可空',
  `batch_key` varchar(200) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '源批次编号可空',
  `payload_sha256` varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '本阶段输入或发送字节摘要',
  `payload_bytes` bigint DEFAULT NULL COMMENT '对应阶段实际字节数或清单声明值见事件类型',
  `record_count` bigint DEFAULT NULL COMMENT '逻辑记录数BINARY文件为空',
  `http_status` int DEFAULT NULL COMMENT '实际HTTP响应状态可空',
  `started_at` bigint DEFAULT NULL COMMENT '本次尝试开始UTC毫秒',
  `duration_ms` bigint DEFAULT NULL COMMENT '本次尝试耗时毫秒',
  `error_code` varchar(80) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '脱敏错误码',
  `metadata` longtext COLLATE utf8mb4_0900_bin NOT NULL COMMENT '协议回执与诊断信息不含请求正文及认证头',
  `created_at` bigint NOT NULL COMMENT '日志追加UTC毫秒',
  PRIMARY KEY (`id`),
  KEY `ix_exchange_time` (`created_at`),
  KEY `ix_exchange_message` (`message_id`,`created_at`),
  KEY `ix_exchange_delivery` (`delivery_id`,`created_at`),
  KEY `ix_exchange_file` (`file_id`,`created_at`),
  KEY `ix_exchange_attempt` (`attempt_id`,`created_at`),
  KEY `ix_exchange_protocol` (`protocol`,`direction`,`created_at`),
  KEY `ix_exchange_route` (`route_code`,`created_at`),
  KEY `ix_exchange_batch` (`batch_key`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='追加写的HTTP Kafka FTP交换历史日志';

-- TABLE ht_file_checkpoint
CREATE TABLE `ht_file_checkpoint` (
  `id` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '海通文件UUID',
  `owner` varchar(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '经过认证的来源归属',
  `source_type` varchar(20) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'HTTP_FILE或FTP_FILE',
  `source_scope` varchar(200) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '去重来源范围',
  `source_key` varchar(300) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '来源幂等键或清单身份摘要',
  `route_code` varchar(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '接收路由编码',
  `original_name` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '原始文件名仅作显示',
  `payload_ref` longtext COLLATE utf8mb4_0900_bin NOT NULL COMMENT '持久化工作文件引用',
  `content_sha256` varchar(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '完整文件SHA256',
  `file_size` bigint NOT NULL COMMENT '完整文件字节数',
  `content_format` varchar(20) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'JSONL CSV BINARY',
  `config_snapshot` longtext COLLATE utf8mb4_0900_bin NOT NULL COMMENT '冻结的无密钥路由快照',
  `status` varchar(30) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'READY PROCESSING RETRY_WAIT INGESTED FAILED',
  `last_record_no` bigint NOT NULL DEFAULT '0' COMMENT '已提交的逻辑记录号',
  `expected_records` bigint DEFAULT NULL COMMENT '源完成清单声明的记录数可空',
  `total_records` bigint NOT NULL DEFAULT '0' COMMENT '实际解析记录数 BINARY以一个文件单元处理',
  `eof_reached` smallint NOT NULL DEFAULT '0' COMMENT '是否到达文件末尾0或1',
  `source_path` varchar(1000) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '远端来源文件路径',
  `source_metadata` longtext COLLATE utf8mb4_0900_bin COMMENT '源清单和文件元数据不含密钥',
  `lease_token` varchar(36) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '本次领取令牌',
  `lease_until` bigint NOT NULL DEFAULT '0' COMMENT '租约截止UTC毫秒',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '解析执行尝试次数',
  `next_attempt_at` bigint NOT NULL DEFAULT '0' COMMENT '下次允许执行UTC毫秒',
  `last_error_code` varchar(80) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '最近错误码',
  `retry_audit` longtext COLLATE utf8mb4_0900_bin NOT NULL COMMENT '人工处理摘要JSON',
  `created_at` bigint NOT NULL COMMENT '首次接收UTC毫秒',
  `updated_at` bigint NOT NULL COMMENT '最后修改UTC毫秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_file_source` (`source_scope`,`route_code`,`source_key`),
  KEY `ix_file_ready` (`status`,`next_attempt_at`),
  KEY `ix_file_created` (`created_at`),
  CONSTRAINT `ck_file_eof` CHECK ((`eof_reached` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='文件接收与解析检查点';

-- TABLE ht_file_receipt
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

-- TABLE ht_file_upload
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

-- TABLE ht_file_upload_part
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

-- TABLE ht_magic_api_file
CREATE TABLE `ht_magic_api_file` (
  `file_path` varchar(512) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Magic API 资源完整路径',
  `file_content` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Magic API 资源内容',
  PRIMARY KEY (`file_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='haitong-data-pull Magic API 资源库';

-- TABLE ht_message
CREATE TABLE `ht_message` (
  `id` varchar(36) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '海通messageId',
  `owner` varchar(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '经过认证的来源归属',
  `source_type` varchar(20) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'HTTP KAFKA KAFKA_TOMBSTONE HTTP_FILE FTP_FILE ACTION',
  `source_scope` varchar(200) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '来源范围包含Kafka来源代次',
  `source_key` varchar(300) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '来源幂等键或Kafka坐标',
  `route_code` varchar(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '路由编码不含版本',
  `payload_sha256` varchar(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '原始输入字节SHA256',
  `output_sha256` varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '规范化输出内容SHA256',
  `payload_bytes` bigint DEFAULT NULL COMMENT '原始输入字节数',
  `output_bytes` bigint DEFAULT NULL COMMENT '规范化输出字节数',
  `payload` longtext COLLATE utf8mb4_0900_bin COMMENT '原始字节Base64不可用于业务条数统计',
  `output_payload` longtext COLLATE utf8mb4_0900_bin COMMENT '规范化输出字节Base64',
  `payload_ref` longtext COLLATE utf8mb4_0900_bin COMMENT 'BINARY文件持久化引用',
  `original_name` varchar(255) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '原文件名',
  `file_id` varchar(36) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '关联文件检查点',
  `record_no` bigint DEFAULT NULL COMMENT '文件内逻辑记录号',
  `business_key` varchar(200) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '配置提取的业务唯一编号',
  `batch_key` varchar(200) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '配置提取的业务批次编号',
  `business_time` varchar(100) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '源业务时间原始文本不伪造时区',
  `source_metadata` longtext COLLATE utf8mb4_0900_bin COMMENT '协议来源坐标及清单元数据不含密钥',
  `status` varchar(30) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'ACCEPTED或QUARANTINED不复制投递终态',
  `error_code` varchar(80) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '隔离原因',
  `config_snapshot` longtext COLLATE utf8mb4_0900_bin NOT NULL COMMENT '受理时路由快照不含密钥',
  `created_at` bigint NOT NULL COMMENT '接收UTC毫秒对账窗口以此为准',
  `payload_purged_at` bigint DEFAULT NULL COMMENT '原文清理时间UTC毫秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_message_source` (`source_scope`,`route_code`,`source_key`),
  UNIQUE KEY `uq_file_record` (`file_id`,`record_no`),
  KEY `ix_message_owner` (`owner`,`created_at`),
  KEY `ix_message_route` (`route_code`,`created_at`),
  KEY `ix_message_business` (`business_key`,`created_at`),
  KEY `ix_message_batch` (`batch_key`,`created_at`),
  CONSTRAINT `fk_message_file` FOREIGN KEY (`file_id`) REFERENCES `ht_file_checkpoint` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='逻辑消息接收账本';

-- TABLE ht_push_api_client
CREATE TABLE `ht_push_api_client` (
  `client_id` varchar(128) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '平台签发的 push-{datasourceId} 调用方身份',
  `api_key_sha256` char(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'API Key SHA-256 摘要，不保存明文',
  `is_enable` smallint NOT NULL DEFAULT '1',
  `is_del` smallint NOT NULL DEFAULT '0',
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`client_id`),
  UNIQUE KEY `uq_push_api_client_hash` (`api_key_sha256`),
  CONSTRAINT `ck_push_api_client_del` CHECK ((`is_del` in (0,1))),
  CONSTRAINT `ck_push_api_client_enable` CHECK ((`is_enable` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='数据源级推送 API Key 摘要';

-- TABLE ht_standalone_datasource
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

-- TABLE ht_standalone_datasource_client
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

-- TABLE ht_standalone_registry_sync
CREATE TABLE `ht_standalone_registry_sync` (
  `idempotency_key` varchar(200) COLLATE utf8mb4_0900_bin NOT NULL,
  `payload_sha256` char(64) COLLATE utf8mb4_0900_bin NOT NULL,
  `datasource_id` varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  `applied_at` bigint NOT NULL,
  PRIMARY KEY (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- TABLE ht_standalone_table
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

-- TABLE ht_standalone_table_column
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

-- TABLE ht_storage_quota
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

SET FOREIGN_KEY_CHECKS=1;
