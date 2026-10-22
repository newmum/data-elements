-- haitong-data-pull 原有达梦 9 表兼容模板；未核验真实 DM 环境。
-- 不代表当前 MySQL haitong 11 表结构；不得当作已有库升级器。
-- haitong 海通 V1.1.0 重建版：新库初始化脚本，不是旧数据迁移脚本。
-- 应用不自动执行生产DDL。所有运行时间为UTC Unix毫秒，非数据库本地时间。
-- ht_exchange_log追加写；应用运行账号不要拥有其UPDATE/DELETE权限。
-- DM8：由DBA事先创建用户/Schema，并以该用户执行。不要在其他用户下误建表。
-- DM驱动及数据库版本需在现网验证；VARCHAR字节语义字段已对非ASCII值预留UTF8空间。

CREATE TABLE ht_file_checkpoint (
  id VARCHAR(36) PRIMARY KEY,
  owner VARCHAR(100) NOT NULL,
  source_type VARCHAR(20) NOT NULL,
  source_scope VARCHAR(200) NOT NULL,
  source_key VARCHAR(300) NOT NULL,
  route_code VARCHAR(100) NOT NULL,
  original_name VARCHAR(1020) NOT NULL,
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
  source_path VARCHAR(4000),
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
COMMENT ON TABLE ht_file_checkpoint IS '文件接收与解析检查点';
COMMENT ON COLUMN ht_file_checkpoint.id IS '海通文件UUID';
COMMENT ON COLUMN ht_file_checkpoint.owner IS '经过认证的来源归属';
COMMENT ON COLUMN ht_file_checkpoint.source_type IS 'HTTP_FILE或FTP_FILE';
COMMENT ON COLUMN ht_file_checkpoint.source_scope IS '去重来源范围';
COMMENT ON COLUMN ht_file_checkpoint.source_key IS '来源幂等键或清单身份摘要';
COMMENT ON COLUMN ht_file_checkpoint.route_code IS '接收路由编码';
COMMENT ON COLUMN ht_file_checkpoint.original_name IS '原始文件名仅作显示';
COMMENT ON COLUMN ht_file_checkpoint.payload_ref IS '持久化工作文件引用';
COMMENT ON COLUMN ht_file_checkpoint.content_sha256 IS '完整文件SHA256';
COMMENT ON COLUMN ht_file_checkpoint.file_size IS '完整文件字节数';
COMMENT ON COLUMN ht_file_checkpoint.content_format IS 'JSONL CSV BINARY';
COMMENT ON COLUMN ht_file_checkpoint.config_snapshot IS '冻结的无密钥路由快照';
COMMENT ON COLUMN ht_file_checkpoint.status IS 'READY PROCESSING RETRY_WAIT INGESTED FAILED';
COMMENT ON COLUMN ht_file_checkpoint.last_record_no IS '已提交的逻辑记录号';
COMMENT ON COLUMN ht_file_checkpoint.expected_records IS '源完成清单声明的记录数可空';
COMMENT ON COLUMN ht_file_checkpoint.total_records IS '实际解析记录数 BINARY以一个文件单元处理';
COMMENT ON COLUMN ht_file_checkpoint.eof_reached IS '是否到达文件末尾0或1';
COMMENT ON COLUMN ht_file_checkpoint.source_path IS '远端来源文件路径';
COMMENT ON COLUMN ht_file_checkpoint.source_metadata IS '源清单和文件元数据不含密钥';
COMMENT ON COLUMN ht_file_checkpoint.lease_token IS '本次领取令牌';
COMMENT ON COLUMN ht_file_checkpoint.lease_until IS '租约截止UTC毫秒';
COMMENT ON COLUMN ht_file_checkpoint.attempts IS '解析执行尝试次数';
COMMENT ON COLUMN ht_file_checkpoint.next_attempt_at IS '下次允许执行UTC毫秒';
COMMENT ON COLUMN ht_file_checkpoint.last_error_code IS '最近错误码';
COMMENT ON COLUMN ht_file_checkpoint.retry_audit IS '人工处理摘要JSON';
COMMENT ON COLUMN ht_file_checkpoint.created_at IS '首次接收UTC毫秒';
COMMENT ON COLUMN ht_file_checkpoint.updated_at IS '最后修改UTC毫秒';

CREATE TABLE ht_message (
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
  original_name VARCHAR(1020),
  file_id VARCHAR(36),
  record_no BIGINT,
  business_key VARCHAR(800),
  batch_key VARCHAR(800),
  business_time VARCHAR(400),
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
COMMENT ON TABLE ht_message IS '逻辑消息接收账本';
COMMENT ON COLUMN ht_message.id IS '海通messageId';
COMMENT ON COLUMN ht_message.owner IS '经过认证的来源归属';
COMMENT ON COLUMN ht_message.source_type IS 'HTTP KAFKA KAFKA_TOMBSTONE HTTP_FILE FTP_FILE ACTION';
COMMENT ON COLUMN ht_message.source_scope IS '来源范围包含Kafka来源代次';
COMMENT ON COLUMN ht_message.source_key IS '来源幂等键或Kafka坐标';
COMMENT ON COLUMN ht_message.route_code IS '路由编码不含版本';
COMMENT ON COLUMN ht_message.payload_sha256 IS '原始输入字节SHA256';
COMMENT ON COLUMN ht_message.output_sha256 IS '规范化输出内容SHA256';
COMMENT ON COLUMN ht_message.payload_bytes IS '原始输入字节数';
COMMENT ON COLUMN ht_message.output_bytes IS '规范化输出字节数';
COMMENT ON COLUMN ht_message.payload IS '原始字节Base64不可用于业务条数统计';
COMMENT ON COLUMN ht_message.output_payload IS '规范化输出字节Base64';
COMMENT ON COLUMN ht_message.payload_ref IS 'BINARY文件持久化引用';
COMMENT ON COLUMN ht_message.original_name IS '原文件名';
COMMENT ON COLUMN ht_message.file_id IS '关联文件检查点';
COMMENT ON COLUMN ht_message.record_no IS '文件内逻辑记录号';
COMMENT ON COLUMN ht_message.business_key IS '配置提取的业务唯一编号';
COMMENT ON COLUMN ht_message.batch_key IS '配置提取的业务批次编号';
COMMENT ON COLUMN ht_message.business_time IS '源业务时间原始文本不伪造时区';
COMMENT ON COLUMN ht_message.source_metadata IS '协议来源坐标及清单元数据不含密钥';
COMMENT ON COLUMN ht_message.status IS 'ACCEPTED或QUARANTINED不复制投递终态';
COMMENT ON COLUMN ht_message.error_code IS '隔离原因';
COMMENT ON COLUMN ht_message.config_snapshot IS '受理时路由快照不含密钥';
COMMENT ON COLUMN ht_message.created_at IS '接收UTC毫秒对账窗口以此为准';
COMMENT ON COLUMN ht_message.payload_purged_at IS '原文清理时间UTC毫秒';

CREATE TABLE ht_delivery (
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
  last_error_message VARCHAR(1020),
  retry_audit CLOB NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  completed_at BIGINT,
  CONSTRAINT uq_delivery_target UNIQUE(message_id,target_code),
  CONSTRAINT fk_delivery_message FOREIGN KEY(message_id) REFERENCES ht_message(id),
  CONSTRAINT ck_delivery_safe CHECK(retry_safe IN (0,1))
);
COMMENT ON TABLE ht_delivery IS '每个目标的当前投递任务';
COMMENT ON COLUMN ht_delivery.id IS '稳定deliveryId下游幂等依据';
COMMENT ON COLUMN ht_delivery.message_id IS '所属逻辑消息';
COMMENT ON COLUMN ht_delivery.target_code IS '逻辑目标编码';
COMMENT ON COLUMN ht_delivery.target_type IS 'KAFKA FTP LOCAL ACTION';
COMMENT ON COLUMN ht_delivery.target_plan IS '冻结的目标配置不含密钥';
COMMENT ON COLUMN ht_delivery.status IS 'PENDING RUNNING RETRY_WAIT SUCCEEDED FAILED UNKNOWN';
COMMENT ON COLUMN ht_delivery.attempts IS '已执行次数不等于业务条数';
COMMENT ON COLUMN ht_delivery.max_attempts IS '自动尝试上限';
COMMENT ON COLUMN ht_delivery.retry_safe IS '可安全重试标记0或1';
COMMENT ON COLUMN ht_delivery.next_attempt_at IS '下一次执行UTC毫秒';
COMMENT ON COLUMN ht_delivery.lease_token IS '本次attemptId及数据库围栏令牌';
COMMENT ON COLUMN ht_delivery.lease_until IS '租约截止UTC毫秒';
COMMENT ON COLUMN ht_delivery.attempt_started_at IS '最近一次执行开始UTC毫秒';
COMMENT ON COLUMN ht_delivery.result_metadata IS '协议回执或后端响应JSON受访问控制';
COMMENT ON COLUMN ht_delivery.last_error_code IS '最近错误码';
COMMENT ON COLUMN ht_delivery.last_error_message IS '脱敏错误说明';
COMMENT ON COLUMN ht_delivery.retry_audit IS '人工重试及核实摘要JSON';
COMMENT ON COLUMN ht_delivery.created_at IS '任务创建UTC毫秒';
COMMENT ON COLUMN ht_delivery.updated_at IS '最后修改UTC毫秒';
COMMENT ON COLUMN ht_delivery.completed_at IS '目标已确认UTC毫秒';

CREATE TABLE ht_exchange_log (
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
  source_ref VARCHAR(4000),
  destination_ref VARCHAR(4000),
  business_key VARCHAR(800),
  batch_key VARCHAR(800),
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
COMMENT ON TABLE ht_exchange_log IS '追加写的HTTP Kafka FTP交换历史日志';
COMMENT ON COLUMN ht_exchange_log.id IS '不可变日志UUID';
COMMENT ON COLUMN ht_exchange_log.protocol IS 'HTTP KAFKA FTP LOCAL';
COMMENT ON COLUMN ht_exchange_log.direction IS 'IN OUT ADMIN';
COMMENT ON COLUMN ht_exchange_log.event_type IS 'MESSAGE_RECEIVE DELIVERY HTTP_ACCESS FILE_DOWNLOAD等';
COMMENT ON COLUMN ht_exchange_log.phase IS 'START FINISH RESULT';
COMMENT ON COLUMN ht_exchange_log.outcome IS '本次事件结果不是整体业务结果';
COMMENT ON COLUMN ht_exchange_log.attempt_id IS '一次尝试ID非稳定投递ID';
COMMENT ON COLUMN ht_exchange_log.request_id IS 'HTTP服务端请求ID';
COMMENT ON COLUMN ht_exchange_log.owner IS '经过认证的来源或操作者';
COMMENT ON COLUMN ht_exchange_log.route_code IS '逻辑路由';
COMMENT ON COLUMN ht_exchange_log.message_id IS '关联消息可空';
COMMENT ON COLUMN ht_exchange_log.delivery_id IS '关联稳定目标任务可空';
COMMENT ON COLUMN ht_exchange_log.file_id IS '关联文件可空';
COMMENT ON COLUMN ht_exchange_log.target_code IS '逻辑目标';
COMMENT ON COLUMN ht_exchange_log.source_ref IS '来源路径或协议坐标不得含凭证';
COMMENT ON COLUMN ht_exchange_log.destination_ref IS '目标路径Topic或动作编码';
COMMENT ON COLUMN ht_exchange_log.business_key IS '源业务编号可空';
COMMENT ON COLUMN ht_exchange_log.batch_key IS '源批次编号可空';
COMMENT ON COLUMN ht_exchange_log.payload_sha256 IS '本阶段输入或发送字节摘要';
COMMENT ON COLUMN ht_exchange_log.payload_bytes IS '对应阶段实际字节数或清单声明值见事件类型';
COMMENT ON COLUMN ht_exchange_log.record_count IS '逻辑记录数BINARY文件为空';
COMMENT ON COLUMN ht_exchange_log.http_status IS '实际HTTP响应状态可空';
COMMENT ON COLUMN ht_exchange_log.started_at IS '本次尝试开始UTC毫秒';
COMMENT ON COLUMN ht_exchange_log.duration_ms IS '本次尝试耗时毫秒';
COMMENT ON COLUMN ht_exchange_log.error_code IS '脱敏错误码';
COMMENT ON COLUMN ht_exchange_log.metadata IS '协议回执与诊断信息不含请求正文及认证头';
COMMENT ON COLUMN ht_exchange_log.created_at IS '日志追加UTC毫秒';

CREATE INDEX ix_delivery_ready ON ht_delivery(target_type,status,next_attempt_at);
CREATE INDEX ix_delivery_lease ON ht_delivery(status,lease_until);
CREATE INDEX ix_delivery_message ON ht_delivery(message_id,status);
CREATE INDEX ix_message_owner ON ht_message(owner,created_at);
CREATE INDEX ix_message_route ON ht_message(route_code,created_at);
CREATE INDEX ix_message_business ON ht_message(business_key,created_at);
CREATE INDEX ix_message_batch ON ht_message(batch_key,created_at);
CREATE INDEX ix_file_ready ON ht_file_checkpoint(status,next_attempt_at);
CREATE INDEX ix_file_created ON ht_file_checkpoint(created_at);
CREATE INDEX ix_exchange_time ON ht_exchange_log(created_at);
CREATE INDEX ix_exchange_message ON ht_exchange_log(message_id,created_at);
CREATE INDEX ix_exchange_delivery ON ht_exchange_log(delivery_id,created_at);
CREATE INDEX ix_exchange_file ON ht_exchange_log(file_id,created_at);
CREATE INDEX ix_exchange_attempt ON ht_exchange_log(attempt_id,created_at);
CREATE INDEX ix_exchange_protocol ON ht_exchange_log(protocol,direction,created_at);
CREATE INDEX ix_exchange_route ON ht_exchange_log(route_code,created_at);
CREATE INDEX ix_exchange_batch ON ht_exchange_log(batch_key,created_at);

-- 独立运行模式登记镜像。既有库执行 V1.1.2__standalone_registry.dm.sql；新库可直接执行本文件。
CREATE TABLE ht_standalone_datasource (
  datasource_id VARCHAR(64) PRIMARY KEY, datasource_name VARCHAR(1020) NOT NULL,
  source_system_id VARCHAR(512) DEFAULT '' NOT NULL, source_system_name VARCHAR(1020) DEFAULT '' NOT NULL,
  contract_version VARCHAR(400) DEFAULT '' NOT NULL, is_enable SMALLINT DEFAULT 1 NOT NULL,
  is_del SMALLINT DEFAULT 0 NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL
);
CREATE TABLE ht_standalone_datasource_client (
  datasource_id VARCHAR(64) NOT NULL, client_id VARCHAR(512) NOT NULL, is_enable SMALLINT DEFAULT 1 NOT NULL,
  is_del SMALLINT DEFAULT 0 NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  PRIMARY KEY(datasource_id,client_id)
);
CREATE TABLE ht_standalone_table (
  datasource_id VARCHAR(64) NOT NULL, table_name VARCHAR(800) NOT NULL, table_id VARCHAR(512) DEFAULT '' NOT NULL,
  table_comment VARCHAR(1020) DEFAULT '' NOT NULL, schema_version VARCHAR(400) DEFAULT '' NOT NULL,
  is_enable SMALLINT DEFAULT 1 NOT NULL, is_del SMALLINT DEFAULT 0 NOT NULL,
  created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY(datasource_id,table_name)
);
CREATE TABLE ht_standalone_table_column (
  datasource_id VARCHAR(64) NOT NULL, table_name VARCHAR(800) NOT NULL, column_name VARCHAR(800) NOT NULL,
  column_comment VARCHAR(1020) DEFAULT '' NOT NULL, data_type VARCHAR(400) DEFAULT '' NOT NULL,
  ordinal_no INTEGER NOT NULL, is_required SMALLINT DEFAULT 0 NOT NULL, is_enable SMALLINT DEFAULT 1 NOT NULL,
  is_del SMALLINT DEFAULT 0 NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  PRIMARY KEY(datasource_id,table_name,column_name)
);
CREATE TABLE ht_standalone_registry_sync (
  idempotency_key VARCHAR(800) PRIMARY KEY, payload_sha256 VARCHAR(64) NOT NULL,
  datasource_id VARCHAR(64) NOT NULL, applied_at BIGINT NOT NULL
);
CREATE INDEX ix_standalone_table_active ON ht_standalone_table(datasource_id,is_enable,is_del);
CREATE INDEX ix_standalone_column_active ON ht_standalone_table_column(datasource_id,table_name,is_enable,is_del,ordinal_no);
CREATE INDEX ix_standalone_client_active ON ht_standalone_datasource_client(datasource_id,is_enable,is_del);

-- 核心对账：按ht_message.created_at选入账窗口；按ht_delivery.id逐目标统计。
-- 重试次数/START/FINISH日志行数不是业务接收条数。
-- 未完成/FAILED/UNKNOWN及关联原文不能通过普通成功清理流程删除。
