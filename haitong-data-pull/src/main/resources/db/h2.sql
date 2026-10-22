-- haitong 海通 V1.1.0 重建版：新库初始化脚本，不是旧数据迁移脚本。
-- 应用不自动执行生产DDL。所有运行时间为UTC Unix毫秒，非数据库本地时间。
-- ht_exchange_log追加写；应用运行账号不要拥有其UPDATE/DELETE权限。
CREATE TABLE IF NOT EXISTS ht_push_api_client (
  client_id VARCHAR(128) PRIMARY KEY,
  api_key_sha256 CHAR(64) NOT NULL,
  is_enable SMALLINT NOT NULL DEFAULT 1,
  is_del SMALLINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  CONSTRAINT uq_push_api_client_hash UNIQUE(api_key_sha256),
  CONSTRAINT ck_push_api_client_enable CHECK(is_enable IN (0,1)),
  CONSTRAINT ck_push_api_client_del CHECK(is_del IN (0,1))
);

-- Magic API database resource store. MySQL uses the equivalent table in
-- V1.1.3__magic_api_resource.mysql.sql.
CREATE TABLE IF NOT EXISTS ht_magic_api_file (
  file_path VARCHAR(512) PRIMARY KEY,
  file_content CLOB NOT NULL
);

CREATE TABLE IF NOT EXISTS ht_file_checkpoint (
  id VARCHAR(36) PRIMARY KEY,
  owner VARCHAR(100) NOT NULL,
  source_type VARCHAR(20) NOT NULL,
  source_scope VARCHAR(200) NOT NULL,
  source_key VARCHAR(300) NOT NULL,
  route_code VARCHAR(100) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  payload_ref CLOB NOT NULL,
  content_sha256 VARCHAR(64) NOT NULL,
  file_size BIGINT NOT NULL,
  content_format VARCHAR(20) NOT NULL,
  config_snapshot CLOB NOT NULL,
  status VARCHAR(30) NOT NULL,
  last_record_no BIGINT DEFAULT 0 NOT NULL,
  expected_records BIGINT,
  total_records BIGINT DEFAULT 0 NOT NULL,
  eof_reached SMALLINT DEFAULT 0 NOT NULL,
  source_path VARCHAR(1000),
  source_metadata CLOB,
  lease_token VARCHAR(36),
  lease_until BIGINT DEFAULT 0 NOT NULL,
  attempts INTEGER DEFAULT 0 NOT NULL,
  next_attempt_at BIGINT DEFAULT 0 NOT NULL,
  last_error_code VARCHAR(80),
  retry_audit CLOB NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  CONSTRAINT uq_file_source UNIQUE(source_scope,route_code,source_key),
  CONSTRAINT ck_file_eof CHECK(eof_reached IN (0,1))
);

CREATE TABLE IF NOT EXISTS ht_message (
  id VARCHAR(36) PRIMARY KEY,
  owner VARCHAR(100) NOT NULL,
  source_type VARCHAR(20) NOT NULL,
  source_scope VARCHAR(200) NOT NULL,
  source_key VARCHAR(300) NOT NULL,
  route_code VARCHAR(100) NOT NULL,
  payload_sha256 VARCHAR(64) NOT NULL,
  output_sha256 VARCHAR(64),
  payload_bytes BIGINT,
  output_bytes BIGINT,
  payload CLOB,
  output_payload CLOB,
  payload_ref CLOB,
  original_name VARCHAR(255),
  file_id VARCHAR(36),
  record_no BIGINT,
  business_key VARCHAR(200),
  batch_key VARCHAR(200),
  business_time VARCHAR(100),
  source_metadata CLOB,
  status VARCHAR(30) NOT NULL,
  error_code VARCHAR(80),
  config_snapshot CLOB NOT NULL,
  created_at BIGINT NOT NULL,
  payload_purged_at BIGINT,
  CONSTRAINT uq_message_source UNIQUE(source_scope,route_code,source_key),
  CONSTRAINT uq_file_record UNIQUE(file_id,record_no),
  CONSTRAINT fk_message_file FOREIGN KEY(file_id) REFERENCES ht_file_checkpoint(id)
);

CREATE TABLE IF NOT EXISTS ht_delivery (
  id VARCHAR(36) PRIMARY KEY,
  message_id VARCHAR(36) NOT NULL,
  target_code VARCHAR(100) NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_plan CLOB NOT NULL,
  status VARCHAR(30) NOT NULL,
  attempts INTEGER DEFAULT 0 NOT NULL,
  max_attempts INTEGER NOT NULL,
  retry_safe SMALLINT NOT NULL,
  next_attempt_at BIGINT NOT NULL,
  lease_token VARCHAR(36),
  lease_until BIGINT DEFAULT 0 NOT NULL,
  attempt_started_at BIGINT,
  result_metadata CLOB,
  last_error_code VARCHAR(80),
  last_error_message VARCHAR(255),
  retry_audit CLOB NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  completed_at BIGINT,
  CONSTRAINT uq_delivery_target UNIQUE(message_id,target_code),
  CONSTRAINT fk_delivery_message FOREIGN KEY(message_id) REFERENCES ht_message(id),
  CONSTRAINT ck_delivery_safe CHECK(retry_safe IN (0,1))
);

CREATE TABLE IF NOT EXISTS ht_exchange_log (
  id VARCHAR(36) PRIMARY KEY,
  protocol VARCHAR(16) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  phase VARCHAR(16) NOT NULL,
  outcome VARCHAR(30) NOT NULL,
  attempt_id VARCHAR(64),
  request_id VARCHAR(64),
  owner VARCHAR(100),
  route_code VARCHAR(100),
  message_id VARCHAR(36),
  delivery_id VARCHAR(36),
  file_id VARCHAR(36),
  target_code VARCHAR(100),
  source_ref VARCHAR(1000),
  destination_ref VARCHAR(1000),
  business_key VARCHAR(200),
  batch_key VARCHAR(200),
  payload_sha256 VARCHAR(64),
  payload_bytes BIGINT,
  record_count BIGINT,
  http_status INTEGER,
  started_at BIGINT,
  duration_ms BIGINT,
  error_code VARCHAR(80),
  metadata CLOB NOT NULL,
  created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_delivery_ready ON ht_delivery(target_type,status,next_attempt_at);
CREATE INDEX IF NOT EXISTS ix_delivery_lease ON ht_delivery(status,lease_until);
CREATE INDEX IF NOT EXISTS ix_delivery_message ON ht_delivery(message_id,status);
CREATE INDEX IF NOT EXISTS ix_message_owner ON ht_message(owner,created_at);
CREATE INDEX IF NOT EXISTS ix_message_route ON ht_message(route_code,created_at);
CREATE INDEX IF NOT EXISTS ix_message_business ON ht_message(business_key,created_at);
CREATE INDEX IF NOT EXISTS ix_message_batch ON ht_message(batch_key,created_at);
CREATE INDEX IF NOT EXISTS ix_file_ready ON ht_file_checkpoint(status,next_attempt_at);
CREATE INDEX IF NOT EXISTS ix_file_created ON ht_file_checkpoint(created_at);
CREATE INDEX IF NOT EXISTS ix_exchange_time ON ht_exchange_log(created_at);
CREATE INDEX IF NOT EXISTS ix_exchange_message ON ht_exchange_log(message_id,created_at);
CREATE INDEX IF NOT EXISTS ix_exchange_delivery ON ht_exchange_log(delivery_id,created_at);
CREATE INDEX IF NOT EXISTS ix_exchange_file ON ht_exchange_log(file_id,created_at);
CREATE INDEX IF NOT EXISTS ix_exchange_attempt ON ht_exchange_log(attempt_id,created_at);
CREATE INDEX IF NOT EXISTS ix_exchange_protocol ON ht_exchange_log(protocol,direction,created_at);
CREATE INDEX IF NOT EXISTS ix_exchange_route ON ht_exchange_log(route_code,created_at);
CREATE INDEX IF NOT EXISTS ix_exchange_batch ON ht_exchange_log(batch_key,created_at);

CREATE TABLE IF NOT EXISTS ht_standalone_datasource (
  datasource_id VARCHAR(64) PRIMARY KEY, datasource_name VARCHAR(255) NOT NULL,
  source_system_id VARCHAR(128) NOT NULL DEFAULT '', source_system_name VARCHAR(255) NOT NULL DEFAULT '',
  contract_version VARCHAR(100) NOT NULL DEFAULT '', is_enable SMALLINT NOT NULL DEFAULT 1,
  is_del SMALLINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS ht_standalone_datasource_client (
  datasource_id VARCHAR(64) NOT NULL, client_id VARCHAR(128) NOT NULL, is_enable SMALLINT NOT NULL DEFAULT 1,
  is_del SMALLINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  PRIMARY KEY(datasource_id,client_id)
);
CREATE TABLE IF NOT EXISTS ht_standalone_table (
  datasource_id VARCHAR(64) NOT NULL, table_name VARCHAR(200) NOT NULL, table_id VARCHAR(128) NOT NULL DEFAULT '',
  table_comment VARCHAR(255) NOT NULL DEFAULT '', schema_version VARCHAR(100) NOT NULL DEFAULT '',
  is_enable SMALLINT NOT NULL DEFAULT 1, is_del SMALLINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY(datasource_id,table_name)
);
CREATE TABLE IF NOT EXISTS ht_standalone_table_column (
  datasource_id VARCHAR(64) NOT NULL, table_name VARCHAR(200) NOT NULL, column_name VARCHAR(200) NOT NULL,
  column_comment VARCHAR(255) NOT NULL DEFAULT '', data_type VARCHAR(100) NOT NULL DEFAULT '', ordinal_no INTEGER NOT NULL,
  is_required SMALLINT NOT NULL DEFAULT 0, is_enable SMALLINT NOT NULL DEFAULT 1, is_del SMALLINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY(datasource_id,table_name,column_name)
);
CREATE TABLE IF NOT EXISTS ht_standalone_registry_sync (
  idempotency_key VARCHAR(200) PRIMARY KEY, payload_sha256 CHAR(64) NOT NULL,
  datasource_id VARCHAR(64) NOT NULL, applied_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_standalone_table_active ON ht_standalone_table(datasource_id,is_enable,is_del);
CREATE INDEX IF NOT EXISTS ix_standalone_column_active ON ht_standalone_table_column(datasource_id,table_name,is_enable,is_del,ordinal_no);
CREATE INDEX IF NOT EXISTS ix_standalone_client_active ON ht_standalone_datasource_client(datasource_id,is_enable,is_del);

-- 核心对账：按ht_message.created_at选入账窗口；按ht_delivery.id逐目标统计。
-- 重试次数/START/FINISH日志行数不是业务接收条数。
-- 未完成/FAILED/UNKNOWN及关联原文不能通过普通成功清理流程删除。

-- TABLE ht_file_upload
CREATE TABLE IF NOT EXISTS `ht_file_upload` (
  `upload_id` varchar(36) NOT NULL,
  `tenant_id` varchar(80) NOT NULL,
  `owner` varchar(100) NOT NULL,
  `datasource_id` varchar(80) NOT NULL,
  `collection_id` varchar(80) NOT NULL,
  `contract_version` bigint NOT NULL,
  `contract_hash` char(64) NOT NULL,
  `contract_json` longtext NOT NULL,
  `init_key` varchar(200) NOT NULL,
  `init_hash` char(64) NOT NULL,
  `file_name` varchar(240) NOT NULL,
  `media_type` varchar(100) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `expected_sha256` char(64) DEFAULT NULL,
  `final_sha256` char(64) DEFAULT NULL,
  `part_size` bigint NOT NULL,
  `part_count` int NOT NULL,
  `state` varchar(24) NOT NULL,
  `security_state` varchar(40) NOT NULL,
  `payload_path` varchar(1024) DEFAULT NULL,
  `quota_state` varchar(16) NOT NULL,
  `submission_key` varchar(200) DEFAULT NULL,
  `submission_hash` char(64) DEFAULT NULL,
  `submission_json` longtext,
  `message_id` varchar(36) DEFAULT NULL,
  `published_at` bigint DEFAULT NULL,
  `lease_token` varchar(36) DEFAULT NULL,
  `lease_until` bigint NOT NULL DEFAULT '0',
  `next_attempt_at` bigint NOT NULL DEFAULT '0',
  `attempts` int NOT NULL DEFAULT '0',
  `error_code` varchar(80) DEFAULT NULL,
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  `expires_at` bigint NOT NULL,
  PRIMARY KEY (`upload_id`),
  CONSTRAINT `uq_file_upload_tenant` UNIQUE (`tenant_id`,`upload_id`),
  CONSTRAINT `uq_file_upload_init` UNIQUE (`tenant_id`,`owner`,`datasource_id`,`collection_id`,`init_key`),
  CONSTRAINT `uq_file_upload_submit` UNIQUE (`tenant_id`,`owner`,`datasource_id`,`collection_id`,`submission_key`),
  CONSTRAINT `uq_file_upload_message` UNIQUE (`message_id`)
);

-- TABLE ht_file_upload_part
CREATE TABLE IF NOT EXISTS `ht_file_upload_part` (
  `upload_id` varchar(36) NOT NULL,
  `tenant_id` varchar(80) NOT NULL,
  `part_no` int NOT NULL,
  `size_bytes` bigint NOT NULL DEFAULT '0',
  `sha256` char(64) DEFAULT NULL,
  `payload_path` varchar(1024) DEFAULT NULL,
  `state` varchar(16) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `lease_until` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`upload_id`,`part_no`),
  CONSTRAINT `fk_file_part_upload` FOREIGN KEY (`tenant_id`,`upload_id`) REFERENCES `ht_file_upload` (`tenant_id`,`upload_id`)
);

-- TABLE ht_storage_quota
CREATE TABLE IF NOT EXISTS `ht_storage_quota` (
  `quota_key` varchar(64) NOT NULL,
  `tenant_id` varchar(80) DEFAULT NULL,
  `limit_bytes` bigint NOT NULL,
  `reserved_bytes` bigint NOT NULL DEFAULT '0',
  `committed_bytes` bigint NOT NULL DEFAULT '0',
  `active_sessions` int NOT NULL DEFAULT '0',
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`quota_key`)
);

-- TABLE ht_file_receipt
CREATE TABLE IF NOT EXISTS `ht_file_receipt` (
  `receipt_id` varchar(36) NOT NULL,
  `tenant_id` varchar(80) NOT NULL,
  `sender_id` varchar(80) NOT NULL,
  `receipt_key` varchar(200) NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `upload_id` varchar(36) NOT NULL,
  `delivery_id` varchar(36) NOT NULL,
  `target_code` varchar(80) NOT NULL,
  `sha256` char(64) NOT NULL,
  `business_status` varchar(16) NOT NULL,
  `business_ref` varchar(200) DEFAULT NULL,
  `received_at` bigint NOT NULL,
  PRIMARY KEY (`receipt_id`),
  CONSTRAINT `uq_file_receipt_key` UNIQUE (`tenant_id`,`sender_id`,`receipt_key`),
  CONSTRAINT `uq_file_receipt_delivery` UNIQUE (`delivery_id`),

  CONSTRAINT `fk_file_receipt_upload` FOREIGN KEY (`tenant_id`,`upload_id`) REFERENCES `ht_file_upload` (`tenant_id`,`upload_id`),
  CONSTRAINT `fk_file_receipt_delivery` FOREIGN KEY (`delivery_id`) REFERENCES `ht_delivery` (`id`)
);

