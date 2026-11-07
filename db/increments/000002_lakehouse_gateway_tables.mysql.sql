-- 数据服务-补齐湖仓分层和服务网关15张公共业务表。
-- 适用起始结构：161表行业统一基线，且本增量列出的全部15表均不存在；
-- 任一表已存在即停止，逐表核对现行结构，不允许重复执行或DROP后重建。
-- 目标范围：当前环境实际绑定的行业业务库；不用于控制库、恢复来源库或独立海通库。
-- 本机公安/海渔/广电已经具有15表，仅澄天金仓缺表；部署时必须重新确认，
-- 不以本机库名推断生产库，也不复制其他租户的业务数据或tenant_id默认值。
-- 顺序：核对连接/缺表集合/161旧结构 -> 受限备份 -> 本增量 -> 结构检查和完整导出。
-- 后置：15表240列、49索引含15主键；无外键/生成列/自动更新时间触发器。
-- 回退：先停止新功能写入，核对新增表仍为空且无登记/外键/程序引用，
-- 再使用单独审阅的恢复材料处理；已有数据不得通过删表回退。
-- MySQL/达梦DDL可能隐式提交，不承诺事务回滚；执行前确认无长期事务并设置有界等锁。

SELECT DATABASE();
SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('lake_zone_object_t','lake_zone_rule_t','lake_zone_t','svc_feedback_t','svc_gateway_app_t','svc_gateway_instance_t','svc_gateway_log_t','svc_gateway_policy_t','svc_gateway_region_t','svc_gateway_route_t','svc_gray_release_t','svc_market_package_item_t','svc_market_package_t','svc_metric_hour_t','svc_subscription_t');
-- 上述存在表查询必须返回0行，否则停止。禁止客户端忽略后续错误继续执行。
SET SESSION lock_wait_timeout=5;
SET SESSION innodb_lock_wait_timeout=5;

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

SELECT COUNT(*) AS expected_15_tables FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME IN ('lake_zone_object_t','lake_zone_rule_t','lake_zone_t','svc_feedback_t','svc_gateway_app_t','svc_gateway_instance_t','svc_gateway_log_t','svc_gateway_policy_t','svc_gateway_region_t','svc_gateway_route_t','svc_gray_release_t','svc_market_package_item_t','svc_market_package_t','svc_metric_hour_t','svc_subscription_t');
