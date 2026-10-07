-- 当前完整结构基线：仅适用于新空库，不含业务数据、账号或授权。
-- 不得直接在现有数据库执行；现有库变更须单独审阅差异并备份。
SET NAMES utf8mb4;
CREATE DATABASE `baseline` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `baseline`;
SET FOREIGN_KEY_CHECKS=0;

-- TABLE api_backup_t
CREATE TABLE `api_backup_t` (
  `id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户标识',
  `tag` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标签',
  `type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型',
  `name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '名称',
  `content` longtext COLLATE utf8mb4_general_ci COMMENT '内容',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人员',
  `create_date` bigint DEFAULT NULL COMMENT '创建日期',
  `updated_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人标识',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint DEFAULT NULL COMMENT '删除标志：0正常，1删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='低代码接口-接口资源历史版本（Magic API）';

-- TABLE api_file_t
CREATE TABLE `api_file_t` (
  `tid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主键标识',
  `api_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '接口标识',
  `file_kind` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件类别',
  `file_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'Magic资源完整内容',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人标识',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人标识',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint DEFAULT NULL COMMENT '删除标志：0正常，1删除',
  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户标识',
  `file_path` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Magic资源路径'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='低代码接口-接口资源文件（Magic API）';

-- TABLE api_job_t
CREATE TABLE `api_job_t` (
  `tid` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `job_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务编码',
  `run_count` bigint NOT NULL DEFAULT '0' COMMENT '执行次数',
  `last_started_at` datetime(6) DEFAULT NULL COMMENT '最近一次开始时间',
  `last_finished_at` datetime(6) DEFAULT NULL COMMENT '最近一次结束时间',
  `last_duration_ms` bigint DEFAULT NULL COMMENT '最近一次执行耗时（毫秒）',
  `last_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'NEVER' COMMENT '最近一次执行状态；可选值：RUNNING/COMPLETED/DISABLED/FAILED',
  `last_error` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近一次错误信息',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_magic_system_job_status_code` (`job_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统调度-API定时任务最近一次执行状态';

-- TABLE data_push_relay_client_t
CREATE TABLE `data_push_relay_client_t` (
  `tid` varchar(32) NOT NULL COMMENT '主键标识',
  `relay_client_id` varchar(128) NOT NULL COMMENT '中继客户端标识',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户标识',
  `datasource_id` varchar(32) DEFAULT NULL COMMENT '数据源标识；为空时可访问该租户已发布的推送数据源',
  `is_enable` tinyint NOT NULL DEFAULT '1' COMMENT '启用标志',
  `created_time` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_time` datetime(6) NOT NULL COMMENT '更新时间',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志，0未删除、1已删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_data_push_relay_client_scope` (`relay_client_id`,`tenant_id`,`datasource_id`),
  KEY `idx_data_push_relay_client_lookup` (`relay_client_id`,`is_enable`,`is_del`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据推送-海通推送调用方租户与数据源范围绑定';

-- TABLE iam_access_grant_t
CREATE TABLE `iam_access_grant_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '被授予的业务应用ID',
  `identity_domain` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '主体身份域',
  `principal_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主体类型；可选值：SUBJECT/ORG/GROUP/CLIENT',
  `subject_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '人员直接授权主体',
  `org_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '机构授权主体',
  `group_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '权限组授权主体',
  `client_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '机器客户端授权主体',
  `include_children` tinyint NOT NULL DEFAULT '0' COMMENT '机构授权是否含下级',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/REVOKED',
  `valid_from` datetime(6) NOT NULL COMMENT '起效',
  `valid_until` datetime(6) DEFAULT NULL COMMENT '到期',
  `reason` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '授权理由',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  KEY `ix_21_1` (`app_id`,`status`),
  KEY `ix_21_2` (`subject_id`,`app_id`),
  KEY `ix_21_3` (`org_id`,`app_id`),
  KEY `ix_21_4` (`group_id`,`app_id`),
  KEY `ix_21_5` (`client_id`,`app_id`),
  CONSTRAINT `ck_21_1` CHECK (((`valid_until` is null) or (`valid_until` > `valid_from`))),
  CONSTRAINT `ck_21_2` CHECK ((((`principal_type` = _utf8mb4'SUBJECT') and (`subject_id` is not null) and (`org_id` is null) and (`group_id` is null) and (`client_id` is null)) or ((`principal_type` = _utf8mb4'ORG') and (`subject_id` is null) and (`org_id` is not null) and (`group_id` is null) and (`client_id` is null)) or ((`principal_type` = _utf8mb4'GROUP') and (`subject_id` is null) and (`org_id` is null) and (`group_id` is not null) and (`client_id` is null)) or ((`principal_type` = _utf8mb4'CLIENT') and (`subject_id` is null) and (`org_id` is null) and (`group_id` is null) and (`client_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-应用资格和角色授权来源';

-- TABLE iam_account_security_t
CREATE TABLE `iam_account_security_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `principal_kind` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主体类别；可选值：OPERATOR/AUTH_ACCOUNT',
  `principal_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '对应认证账号ID',
  `failed_count` int NOT NULL DEFAULT '0' COMMENT '连续失败次数',
  `locked_until` datetime(6) DEFAULT NULL COMMENT '锁定截止',
  `password_changed_time` datetime(6) DEFAULT NULL COMMENT '最近改密时间',
  `security_version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '安全版本，改密等操作触发会话版本失效',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_10_1` (`principal_kind`,`principal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-认证主体锁定与安全状态';

-- TABLE iam_app_org_t
CREATE TABLE `iam_app_org_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '业务应用ID',
  `org_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '中央机构ID',
  `include_children` tinyint NOT NULL DEFAULT '0' COMMENT '是否包含下级',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/REVOKED',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_26_1` (`app_id`,`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-应用机构目录范围';

-- TABLE iam_application_config_t
CREATE TABLE `iam_application_config_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；迁移数据沿用应用创建人',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，UTC',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '配置项乐观版本',
  `app_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用标识；关联身份应用主表的标识',
  `config_group` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '配置分组，界面公开配置为PORTAL',
  `config_code` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '配置项编码',
  `config_value` longtext COLLATE utf8mb4_bin NOT NULL COMMENT '配置项原值；大尺寸应用标识也可存放',
  `value_type` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'STRING' COMMENT 'STRING或BOOLEAN，读取时还原类型',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_iam_application_config` (`app_id`,`config_group`,`config_code`),
  KEY `ix_iam_application_config_group` (`config_group`,`is_del`),
  CONSTRAINT `ck_iam_application_config_type` CHECK ((`value_type` in (_utf8mb4'STRING',_utf8mb4'BOOLEAN')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-应用配置项；业务应用的界面配置独立于应用扩展资料';

-- TABLE iam_application_group_t
CREATE TABLE `iam_application_group_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `code` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '分组编码，不复用已归档编码',
  `name` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '分组名称',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_1_1` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-应用分组';

-- TABLE iam_application_runtime_t
CREATE TABLE `iam_application_runtime_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'IAM应用ID',
  `runtime_tenant_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '共享框架 sym_tenant_t.tid',
  `backend_code` varchar(64) COLLATE utf8mb4_bin NOT NULL DEFAULT 'data-elements' COMMENT '后端部署标识',
  `environment` varchar(32) COLLATE utf8mb4_bin NOT NULL DEFAULT 'development' COMMENT '运行环境标识',
  `local_permission_app_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '本地角色菜单原app_id，非IAM应用ID',
  `binding_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '绑定状态；可选值：PENDING/VERIFIED/DISABLED',
  `verified_time` datetime(6) DEFAULT NULL COMMENT '最近完成实际数据源核验的时间',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_3_1` (`app_id`),
  UNIQUE KEY `uk_3_2` (`runtime_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-应用与原运行租户绑定';

-- TABLE iam_application_t
CREATE TABLE `iam_application_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `code` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '全局应用编码；现有应用沿用原行业编码',
  `name` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '业务应用名称',
  `sort_no` int NOT NULL DEFAULT '1000' COMMENT '应用管理展示顺序，数值越小越靠前',
  `kind` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'BUSINESS' COMMENT 'BUSINESS行业应用；INTERNAL为卓鉴自己的管理资源容器',
  `group_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '应用分组ID',
  `description` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '说明',
  `owner_operator_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '平台侧负责操作账号，可空',
  `homepage` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '应用首页，不放令牌',
  `identity_domains` json NOT NULL COMMENT '支持的身份域数组，workforce/public',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'DRAFT' COMMENT '状态；可选值：DRAFT/ACTIVE/DISABLED/ARCHIVED',
  `auth_mode` varchar(24) COLLATE utf8mb4_bin NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL或后续显式启用的FEDERATED',
  `provisioning_mode` varchar(24) COLLATE utf8mb4_bin NOT NULL DEFAULT 'OFF' COMMENT 'OFF/HTTP；下发与认证独立开关',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_2_1` (`code`),
  KEY `ix_2_1` (`group_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-身份应用主表';

-- TABLE iam_appointment_t
CREATE TABLE `iam_appointment_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `subject_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '中央人员ID',
  `org_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '机构ID',
  `post_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '岗位字典编码',
  `position_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '职务字典编码',
  `rank_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '职级字典编码',
  `title_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '职称字典编码',
  `is_primary` tinyint NOT NULL DEFAULT '0' COMMENT '是否主职',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/ENDED',
  `valid_from` datetime(6) NOT NULL COMMENT '生效时间',
  `valid_until` datetime(6) DEFAULT NULL COMMENT '截止时间',
  `primary_subject_id` varchar(32) COLLATE utf8mb4_bin GENERATED ALWAYS AS ((case when ((`is_del` = 0) and (`status` = _utf8mb4'ACTIVE') and (`is_primary` = 1)) then `subject_id` else NULL end)) STORED COMMENT '每人最多一条标记活动主职',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_18_1` (`primary_subject_id`),
  KEY `ix_18_1` (`subject_id`,`status`),
  KEY `ix_18_2` (`org_id`,`status`),
  CONSTRAINT `ck_18_1` CHECK (((`valid_until` is null) or (`valid_until` > `valid_from`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-人员机构与主兼任';

-- TABLE iam_audit_event_t
CREATE TABLE `iam_audit_event_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `event_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '事件类型；可选值：OPERATION/AUTH/API/MIGRATION',
  `identity_domain` varchar(16) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '事件身份域',
  `actor_kind` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '操作主体类别；可选值：OPERATOR/AUTH_ACCOUNT/CLIENT/SYSTEM/LEGACY',
  `actor_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作者ID或历史主体引用',
  `app_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '目标应用；平台事件可为INTERNAL应用',
  `action` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定业务动作码',
  `object_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '目标对象类型',
  `object_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '目标对象ID',
  `result` varchar(24) COLLATE utf8mb4_bin NOT NULL COMMENT 'SUCCESS/DENIED/FAILED等',
  `reason_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可检索原因码',
  `account_fingerprint` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '失败登录账号指纹，不存口令',
  `trace_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '链路号',
  `ip` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '访问地址',
  `request_path` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '脱敏后的路径',
  `http_method` varchar(16) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '请求方法',
  `http_status` int DEFAULT NULL COMMENT 'HTTP状态',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时',
  `event_time` datetime(6) NOT NULL COMMENT '事件实际发生时间，历史迁移不改写',
  `snapshot_json` json DEFAULT NULL COMMENT '脱敏前后值和必要上下文，不含凭据或完整报文',
  PRIMARY KEY (`tid`),
  KEY `ix_13_1` (`event_type`,`event_time`),
  KEY `ix_13_2` (`app_id`,`event_time`),
  KEY `ix_13_3` (`actor_kind`,`actor_id`,`event_time`),
  KEY `ix_13_4` (`trace_id`),
  KEY `ix_13_5` (`account_fingerprint`,`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-操作认证和API审计';

-- TABLE iam_auth_account_t
CREATE TABLE `iam_auth_account_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `subject_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '中央人员ID；不是平台操作账号',
  `identity_domain` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '身份领域；可选值为单位人员域、公众域',
  `username` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '统一认证登录名',
  `legal_entity_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可选法人子账号上下文，需有效经办关系',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态；可选值：PENDING/ACTIVE/DISABLED',
  `must_change_password` tinyint NOT NULL DEFAULT '0' COMMENT '下次强制改密',
  `active_username` varchar(100) COLLATE utf8mb4_bin GENERATED ALWAYS AS ((case when (`is_del` = 0) then lower(trim(`username`)) else NULL end)) STORED COMMENT '同域有效登录名',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_33_1` (`identity_domain`,`active_username`),
  KEY `ix_33_1` (`subject_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-可选的统一认证账号';

-- TABLE iam_auth_client_t
CREATE TABLE `iam_auth_client_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '所属业务应用；不是另一个行业应用',
  `client_key` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '协议client_id，全局唯一',
  `name` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '终端/机器名称',
  `client_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '客户端类型；可选值：PUBLIC/CONFIDENTIAL/SERVICE',
  `protocol` varchar(24) COLLATE utf8mb4_bin NOT NULL DEFAULT 'NONE' COMMENT 'NONE/OIDC；其他协议后续显式扩展',
  `grant_types` json DEFAULT NULL COMMENT '经过白名单校验的授权模式',
  `scopes` json DEFAULT NULL COMMENT '允许请求的scope白名单',
  `token_auth_method` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'none/client_secret_basic/private_key_jwt等',
  `require_pkce` tinyint NOT NULL DEFAULT '1' COMMENT '交互授权码使用PKCE',
  `access_ttl_seconds` int NOT NULL DEFAULT '600' COMMENT '访问令牌有效期',
  `refresh_ttl_seconds` int NOT NULL DEFAULT '86400' COMMENT '刷新令牌最大有效期',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/ACTIVE/DISABLED；登记不代表协议已开通',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_34_1` (`client_key`),
  KEY `ix_34_1` (`app_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-应用的终端与机器身份登记';

-- TABLE iam_auth_redirect_t
CREATE TABLE `iam_auth_redirect_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `client_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '客户端ID',
  `uri_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '地址类型；可选值：LOGIN/LOGOUT',
  `uri` varchar(2000) COLLATE utf8mb4_bin NOT NULL COMMENT '完整登记地址',
  `uri_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '完整地址摘要便于唯一索引',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_35_1` (`client_id`,`uri_type`,`uri_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-认证精确回调地址';

-- TABLE iam_auth_session_t
CREATE TABLE `iam_auth_session_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `principal_kind` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主体类别；可选值：OPERATOR/AUTH_ACCOUNT/CLIENT',
  `principal_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '认证主体ID',
  `client_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属认证客户端',
  `app_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '令牌限定应用',
  `session_fingerprint` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '会话或令牌标识摘要，不是原始令牌',
  `family_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '刷新轮换族标识',
  `security_version` bigint unsigned NOT NULL COMMENT '签发时安全版本',
  `issued_at` datetime(6) NOT NULL COMMENT '签发时间',
  `expires_at` datetime(6) NOT NULL COMMENT '到期时间',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '撤销时间',
  `revoke_reason` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '撤销原因',
  `authorization_key` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '框架授权ID；不作为令牌使用',
  `authorization_cipher` longtext COLLATE utf8mb4_bin COMMENT '框架授权状态密文，包含不可直接读取的协议临时数据',
  `code_fingerprint` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '授权码摘要查找',
  `access_fingerprint` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '访问令牌摘要查找',
  `refresh_fingerprint` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前刷新令牌摘要查找',
  `previous_refresh_fingerprint` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '已消费刷新令牌摘要，用于重放撤销整个族',
  `state_fingerprint` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '协议state摘要查找',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_36_1` (`session_fingerprint`),
  UNIQUE KEY `uk_iam_auth_authorization` (`authorization_key`),
  KEY `ix_36_1` (`principal_kind`,`principal_id`),
  KEY `ix_36_2` (`family_id`),
  KEY `ix_36_3` (`expires_at`),
  KEY `ix_iam_auth_code` (`code_fingerprint`),
  KEY `ix_iam_auth_access` (`access_fingerprint`),
  KEY `ix_iam_auth_refresh` (`refresh_fingerprint`),
  KEY `ix_iam_auth_previous_refresh` (`previous_refresh_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-认证会话元数据与撤销索引';

-- TABLE iam_change_event_t
CREATE TABLE `iam_change_event_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `event_type` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '资料或授权变更事件类型',
  `object_type` varchar(24) COLLATE utf8mb4_bin NOT NULL COMMENT '主对象类型',
  `object_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '主对象ID',
  `object_version` bigint unsigned NOT NULL COMMENT '主对象版本',
  `scope_json` json DEFAULT NULL COMMENT '受影响身份域和应用范围，不含秘密',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态；可选值：PENDING/PROCESSING/DONE/FAILED',
  `lease_until` datetime(6) DEFAULT NULL COMMENT '处理租约',
  `lease_token` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '工作者标识',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '重算尝试次数',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_32_1` (`event_type`,`object_type`,`object_id`,`object_version`),
  KEY `ix_32_1` (`status`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-控制库事务变更与待处理事件';

-- TABLE iam_credential_t
CREATE TABLE `iam_credential_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `owner_kind` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属人类别；可选值：OPERATOR/AUTH_ACCOUNT/CLIENT/SYNC_CONFIG',
  `owner_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '归属账号或配置ID',
  `credential_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '凭据类型；可选值：PASSWORD/TOTP/WEBAUTHN/CLIENT_SECRET/HMAC_KEY/RECOVERY_CODE',
  `slot` varchar(100) COLLATE utf8mb4_bin NOT NULL DEFAULT 'primary' COMMENT '密码固定primary；设备或轮换密钥使用独立槽',
  `revision` int unsigned NOT NULL DEFAULT '1' COMMENT '凭据版本',
  `algorithm` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '明确算法和编码版本',
  `secret_hash` varchar(2048) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '密码、恢复码、客户端验证密钥的单向摘要',
  `secret_cipher` text COLLATE utf8mb4_bin COMMENT '需要恢复使用的TOTP种子或签名密钥的密文',
  `key_ref` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '外部加密主密钥引用；不存主密钥值',
  `public_data` json DEFAULT NULL COMMENT '公开公钥及非敏感参数，不放密码',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态；可选值：PENDING/ACTIVE/RETIRED/REVOKED',
  `not_before` datetime(6) DEFAULT NULL COMMENT '可用起点',
  `expires_at` datetime(6) DEFAULT NULL COMMENT '失效时间',
  `active_slot` varchar(160) COLLATE utf8mb4_bin GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'ACTIVE') then concat(`credential_type`,_utf8mb4':',`slot`) else NULL end)) STORED COMMENT '防止同凭据槽多个活动版本',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_11_1` (`owner_kind`,`owner_id`,`credential_type`,`slot`,`revision`),
  UNIQUE KEY `uk_11_2` (`owner_kind`,`owner_id`,`active_slot`),
  CONSTRAINT `ck_11_1` CHECK (((`credential_type` <> _utf8mb4'PASSWORD') or (`slot` = _utf8mb4'primary'))),
  CONSTRAINT `ck_11_2` CHECK (((`secret_hash` is null) or (`secret_cipher` is null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-分用途凭据与密钥版本';

-- TABLE iam_dict_item_t
CREATE TABLE `iam_dict_item_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `identity_domain` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '身份域',
  `category` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'LINE/POST/POSITION/RANK/TITLE等',
  `code` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '同类编码',
  `name` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '显示名称',
  `parent_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '同类父字典',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/DISABLED',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_19_1` (`identity_domain`,`category`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-条线岗位职务等IAM字典';

-- TABLE iam_external_link_t
CREATE TABLE `iam_external_link_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '目标应用',
  `receiver_instance_id` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '接收方实例',
  `object_type` varchar(24) COLLATE utf8mb4_bin NOT NULL COMMENT '对象类型',
  `source_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '中央对象ID',
  `local_id` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '接收方返回本地ID',
  `ack_version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '接收确认版本',
  `ack_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '确认资料摘要',
  `management_state` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'MANAGED' COMMENT '管理状态；可选值：MANAGED/DETACHED/CONFLICT',
  `last_ack_time` datetime(6) DEFAULT NULL COMMENT '最近确认时间',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_31_1` (`app_id`,`object_type`,`source_id`),
  UNIQUE KEY `uk_31_2` (`app_id`,`receiver_instance_id`,`object_type`,`local_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-中央对象与应用本地对象映射';

-- TABLE iam_field_definition_t
CREATE TABLE `iam_field_definition_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `identity_domain` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '身份域',
  `entity_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '实体类型；可选值：SUBJECT/ORG/APPLICATION/RESOURCE',
  `field_key` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '字段键',
  `label` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '显示名',
  `value_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '取值类型；可选值：STRING/NUMBER/DATE/BOOLEAN/ENUM',
  `required_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否必填',
  `validation_json` json DEFAULT NULL COMMENT '长度、枚举、格式及迁移默认值等',
  `sensitive_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否敏感',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/DISABLED',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_20_1` (`identity_domain`,`entity_type`,`field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-扩展属性定义';

-- TABLE iam_grant_role_rela_t
CREATE TABLE `iam_grant_role_rela_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `grant_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '授权来源ID',
  `role_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '应用角色ID',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '同应用约束值',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_22_1` (`grant_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-授权包含的应用角色';

-- TABLE iam_group_member_t
CREATE TABLE `iam_group_member_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `group_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '权限组ID',
  `subject_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '人员ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/REVOKED',
  `valid_from` datetime(6) NOT NULL COMMENT '生效时间',
  `valid_until` datetime(6) DEFAULT NULL COMMENT '截止时间',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_24_1` (`group_id`,`subject_id`),
  KEY `ix_24_1` (`subject_id`,`status`),
  CONSTRAINT `ck_24_1` CHECK (((`valid_until` is null) or (`valid_until` > `valid_from`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-权限组人员成员';

-- TABLE iam_legal_entity_t
CREATE TABLE `iam_legal_entity_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `code` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '法人稳定中央编号',
  `name` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '法人名称',
  `registration_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '统一社会信用代码，经过校验',
  `representative_subject_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '法定代表人自然人档案',
  `verification_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'UNVERIFIED' COMMENT '核验状态；可选值：UNVERIFIED/PENDING/VERIFIED/REJECTED/EXPIRED',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/DISABLED/ARCHIVED',
  `ext_json` json DEFAULT NULL COMMENT '法人扩展资料',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_38_1` (`code`),
  UNIQUE KEY `uk_38_2` (`registration_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-法人主体';

-- TABLE iam_legal_member_t
CREATE TABLE `iam_legal_member_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `entity_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '法人ID',
  `subject_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '自然人档案ID',
  `auth_account_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '具体子账号，可空',
  `relation_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关系类型；可选值：REPRESENTATIVE/AGENT/SUBACCOUNT',
  `app_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可选限定业务应用',
  `valid_from` datetime(6) NOT NULL COMMENT '生效时间',
  `valid_until` datetime(6) DEFAULT NULL COMMENT '失效时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态；可选值：PENDING/ACTIVE/REVOKED',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  KEY `ix_39_1` (`entity_id`,`status`),
  KEY `ix_39_2` (`subject_id`,`status`),
  KEY `ix_39_3` (`auth_account_id`),
  CONSTRAINT `ck_39_1` CHECK (((`valid_until` is null) or (`valid_until` > `valid_from`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-法人自然人及子账号关系';

-- TABLE iam_migration_map_t
CREATE TABLE `iam_migration_map_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `batch_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '迁移批次',
  `source_schema` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '来源库名，仅记录来源不用于动态执行',
  `source_table` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '来源表',
  `source_id` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '原ID或组合键',
  `target_type` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '目标表或归档类型',
  `target_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '新ID或保留ID',
  `source_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '迁移快照摘要',
  `state` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '状态；可选值：COPIED/VERIFIED/ARCHIVED/ROLLED_BACK',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_15_1` (`batch_id`,`source_schema`,`source_table`,`source_id`,`target_type`),
  KEY `ix_15_1` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-重构迁移批次与ID对照';

-- TABLE iam_operator_role_rela_t
CREATE TABLE `iam_operator_role_rela_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `operator_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '操作账号ID',
  `role_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '卓鉴INTERNAL应用下的平台角色ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/REVOKED',
  `valid_from` datetime(6) NOT NULL COMMENT '生效时间',
  `valid_until` datetime(6) DEFAULT NULL COMMENT '失效时间，空表示不设固定到期',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_5_1` (`operator_id`,`role_id`),
  CONSTRAINT `ck_5_1` CHECK (((`valid_until` is null) or (`valid_until` > `valid_from`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-平台操作账号角色分配';

-- TABLE iam_operator_scope_t
CREATE TABLE `iam_operator_scope_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `assignment_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '平台角色分配ID',
  `identity_domain` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT 'workforce/public；分别授权',
  `scope_kind` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '范围类别；可选值：ALL/APPLICATION/ORG',
  `app_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '范围限定应用，可与ORG组合限定机构内指定应用',
  `org_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '范围限定机构',
  `include_children` tinyint NOT NULL DEFAULT '0' COMMENT '是否含下级机构',
  PRIMARY KEY (`tid`),
  KEY `ix_6_1` (`assignment_id`),
  KEY `ix_6_2` (`app_id`),
  KEY `ix_6_3` (`org_id`),
  CONSTRAINT `ck_6_1` CHECK ((((`scope_kind` = _utf8mb4'ALL') and (`app_id` is null) and (`org_id` is null)) or ((`scope_kind` = _utf8mb4'APPLICATION') and (`app_id` is not null) and (`org_id` is null)) or ((`scope_kind` = _utf8mb4'ORG') and (`org_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-平台角色分配的管理范围';

-- TABLE iam_operator_t
CREATE TABLE `iam_operator_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `username` varchar(50) COLLATE utf8mb4_bin NOT NULL COMMENT '仅用于卓鉴管理控制台登录的账号',
  `display_name` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '仅未关联人员的平台账号保存显示名',
  `phone` varchar(256) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '联系方式，可按策略加密',
  `email` varchar(512) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '邮箱，可按策略加密',
  `subject_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可选关联人员档案，不继承档案的应用权限',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态；可选值：PENDING/ACTIVE/DISABLED',
  `must_change_password` tinyint NOT NULL DEFAULT '0' COMMENT '下次登录是否必须改密',
  `last_login_time` datetime(6) DEFAULT NULL COMMENT '最近平台登录时间',
  `active_username` varchar(50) COLLATE utf8mb4_bin GENERATED ALWAYS AS ((case when (`is_del` = 0) then lower(trim(`username`)) else NULL end)) STORED COMMENT '有效账号规范名',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_4_1` (`active_username`),
  UNIQUE KEY `uk_iam_operator_subject_id` (`subject_id`),
  CONSTRAINT `fk_iam_operator_subject_id` FOREIGN KEY (`subject_id`) REFERENCES `iam_subject_t` (`tid`),
  CONSTRAINT `ck_iam_operator_profile_source` CHECK ((((`subject_id` is null) and (`display_name` is not null)) or ((`subject_id` is not null) and (`display_name` is null) and (`phone` is null) and (`email` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-卓鉴平台操作账号';

-- TABLE iam_org_t
CREATE TABLE `iam_org_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `identity_domain` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '所属身份域',
  `code` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '域内稳定机构编码',
  `name` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '机构名称',
  `parent_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '同域上级机构；根为空',
  `path` varchar(2000) COLLATE utf8mb4_bin NOT NULL COMMENT '以ID构造的规范路径，含自身ID边界分隔',
  `depth` int NOT NULL DEFAULT '0' COMMENT '层级',
  `leader_subject_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '负责人档案ID',
  `line_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '条线字典编码',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/DISABLED/ARCHIVED',
  `ext_json` json DEFAULT NULL COMMENT '机构扩展属性',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_17_1` (`identity_domain`,`code`),
  KEY `ix_17_1` (`identity_domain`,`parent_id`),
  KEY `ix_17_2` (`identity_domain`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-中央机构树';

-- TABLE iam_permission_group_t
CREATE TABLE `iam_permission_group_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `identity_domain` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '身份域',
  `code` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '组编码',
  `name` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '组名称',
  `description` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '说明',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/DISABLED',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_23_1` (`identity_domain`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-跨应用权限组合';

-- TABLE iam_public_profile_t
CREATE TABLE `iam_public_profile_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `subject_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'public域人员ID',
  `document_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '证件类型',
  `document_cipher` text COLLATE utf8mb4_bin COMMENT '证件号密文',
  `document_fingerprint` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '受密钥保护的查重指纹',
  `verification_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'UNVERIFIED' COMMENT '核验状态；可选值：UNVERIFIED/PENDING/VERIFIED/REJECTED/EXPIRED',
  `verified_time` datetime(6) DEFAULT NULL COMMENT '实名通过时间',
  `verification_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前有效核验记录ID',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_37_1` (`subject_id`),
  KEY `ix_37_1` (`document_type`,`document_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-公众自然人实名资料';

-- TABLE iam_request_receipt_t
CREATE TABLE `iam_request_receipt_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `operator_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '当前平台操作账号ID',
  `operation_code` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '接口业务动作码',
  `request_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '调用者请求号',
  `request_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '规范化有效请求内容摘要，不记录密码本身',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '状态；可选值：IN_PROGRESS/SUCCEEDED/FAILED/LEGACY_ARCHIVED',
  `result_json` json DEFAULT NULL COMMENT '可安全回放结果，禁止令牌和秘密',
  `error_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '失败码',
  `expires_at` datetime(6) DEFAULT NULL COMMENT '非历史回执保留截止',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_14_1` (`operator_id`,`operation_code`,`request_id`),
  KEY `ix_14_1` (`status`,`updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-业务请求幂等回执';

-- TABLE iam_resource_t
CREATE TABLE `iam_resource_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '所属应用',
  `parent_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '同应用父资源ID',
  `code` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '应用内唯一权限标识',
  `name` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '资源名称',
  `resource_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '资源类型；可选值：MENU/BUTTON/API',
  `path` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '页面或API路径',
  `http_method` varchar(16) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'API请求方法',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/DISABLED',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `ext_json` json DEFAULT NULL COMMENT '资源扩展资料',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  `identity_domain` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'workforce' COMMENT '业务资源身份域；平台内部资源固定workforce',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_8_1` (`app_id`,`code`),
  KEY `ix_8_1` (`app_id`,`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-应用菜单按钮和API资源';

-- TABLE iam_role_resource_rela_t
CREATE TABLE `iam_role_resource_rela_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `role_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '角色ID',
  `resource_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '资源ID',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '归属应用用于隔离校验',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_9_1` (`role_id`,`resource_id`),
  KEY `ix_9_1` (`app_id`,`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-角色资源关联';

-- TABLE iam_role_t
CREATE TABLE `iam_role_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '所属应用；平台角色归属INTERNAL应用',
  `code` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '应用内角色编码',
  `name` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '角色名称',
  `identity_domain` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '身份领域；可选值为单位人员域、公众域',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/DISABLED',
  `description` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '职责说明',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_7_1` (`app_id`,`code`),
  KEY `ix_7_1` (`app_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-应用角色与平台内部角色';

-- TABLE iam_setting_t
CREATE TABLE `iam_setting_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `scope_kind` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '范围类别；可选值：GLOBAL/DOMAIN/APPLICATION',
  `scope_key` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '全局固定global；身份域或应用ID',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '分类；可选值：SECURITY/SESSION/BRANDING/FIELD_PROTECTION/API_ACCESS',
  `config_key` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '配置项键',
  `value_json` json NOT NULL COMMENT '经过类型和范围校验的配置，不存秘密',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/DISABLED',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_12_1` (`scope_kind`,`scope_key`,`category`,`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-统一身份配置与策略';

-- TABLE iam_subject_app_t
CREATE TABLE `iam_subject_app_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `subject_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '中央人员ID',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '目标业务应用ID',
  `account_alias` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '该应用拟使用的本地账号名',
  `directory_assigned` tinyint NOT NULL DEFAULT '1' COMMENT '是否仍有人工目录分配来源；纯授权派生记录显式写0',
  `assignment_mode` varchar(24) COLLATE utf8mb4_bin NOT NULL DEFAULT 'DIRECTORY_ONLY' COMMENT '计算结果：DIRECTORY_ONLY仅目录；GRANT_DERIVED有有效授权',
  `assignment_status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE有目录或授权来源；REVOKED无来源仍保留撤销回执',
  `desired_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '期望的中央来源可用状态，不覆盖本地停用',
  `desired_version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '此应用分配的递增期望版本',
  `ack_version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '接收方已确认应用的版本',
  `sync_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'NOT_CONNECTED' COMMENT '同步状态；可选值：NOT_CONNECTED/PENDING/IN_SYNC/FAILED/CONFLICT',
  `projection_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '访问资格、角色和资源排序集合摘要，用于期望版本变更判定',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_25_1` (`subject_id`,`app_id`),
  KEY `ix_25_1` (`app_id`,`sync_status`),
  CONSTRAINT `ck_25_1` CHECK ((`directory_assigned` in (0,1))),
  CONSTRAINT `ck_25_2` CHECK ((`desired_enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-人员应用分配与下发投影';

-- TABLE iam_subject_t
CREATE TABLE `iam_subject_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `identity_domain` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '身份领域；可选值为单位人员域、公众域',
  `subject_code` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定中央人员编号',
  `account_hint` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '建议下发登录名；不是平台登录凭据',
  `name` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '姓名',
  `phone` varchar(256) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '受保护联系方式',
  `email` varchar(512) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '受保护邮箱',
  `employee_no` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '员工编号',
  `employment_type` varchar(24) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'EMPLOYEE/PARTNER/TEMPORARY等',
  `join_date` date DEFAULT NULL COMMENT '入职日期',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；可选值：ACTIVE/DISABLED/ARCHIVED',
  `ext_json` json DEFAULT NULL COMMENT '扩展属性；原record_json剩余资料须明确归属后迁移',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_16_1` (`identity_domain`,`subject_code`),
  KEY `ix_16_1` (`identity_domain`,`status`),
  KEY `ix_16_2` (`identity_domain`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-中央人员主档';

-- TABLE iam_sync_attempt_t
CREATE TABLE `iam_sync_attempt_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `item_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '下发项ID',
  `attempt_no` int NOT NULL COMMENT '本项尝试序号',
  `credential_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '本次实际使用的服务凭据版本ID；未发出时可空',
  `started_time` datetime(6) NOT NULL COMMENT '开始时间',
  `finished_time` datetime(6) DEFAULT NULL COMMENT '完成时间',
  `transport_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '传输状态；可选值：SENT/TIMEOUT/NETWORK_ERROR/REJECTED/ACKNOWLEDGED',
  `http_status` int DEFAULT NULL COMMENT 'HTTP状态',
  `result_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '业务回执码',
  `safe_message` varchar(2000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '脱敏原因',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_30_1` (`item_id`,`attempt_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-同步每次尝试记录';

-- TABLE iam_sync_config_t
CREATE TABLE `iam_sync_config_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '目标应用',
  `endpoint` varchar(1000) COLLATE utf8mb4_bin NOT NULL COMMENT '经校验的完整接收URL',
  `protocol_version` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'IDAAS/2；旧IDAAS/1需显式适配',
  `receiver_instance_id` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '接收方固定实例标识，防止地址错配',
  `timeout_seconds` int NOT NULL DEFAULT '10' COMMENT '有限超时',
  `batch_size` int NOT NULL DEFAULT '100' COMMENT '单任务分批大小',
  `max_attempts` int NOT NULL DEFAULT '5' COMMENT '重试上限',
  `object_types` json NOT NULL COMMENT '启用的对象类型清单',
  `mapping_json` json DEFAULT NULL COMMENT '可声明的字段映射和字段归属规则，不执行任意代码',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '默认关闭，连通验证成功后显式开通',
  `last_test_time` datetime(6) DEFAULT NULL COMMENT '最近连接测试',
  `last_test_result` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'SUCCESS/FAILED；不代表业务已同步',
  `is_del` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标志；不用于删除审计',
  `display_name` varchar(100) COLLATE utf8mb4_bin NOT NULL DEFAULT '接收配置' COMMENT '接收配置展示名称',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_27_1` (`app_id`),
  CONSTRAINT `ck_27_1` CHECK ((`timeout_seconds` between 1 and 60)),
  CONSTRAINT `ck_27_2` CHECK ((`batch_size` between 1 and 1000)),
  CONSTRAINT `ck_27_3` CHECK ((`max_attempts` between 1 and 20))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-应用HTTP接收配置';

-- TABLE iam_sync_item_t
CREATE TABLE `iam_sync_item_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `task_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '任务ID',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '目标应用ID',
  `object_type` varchar(24) COLLATE utf8mb4_bin NOT NULL COMMENT 'ORG/SUBJECT/APPOINTMENT/ROLE/RESOURCE/USER_ROLE等',
  `object_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '中央对象稳定ID',
  `source_version` bigint unsigned NOT NULL COMMENT '该对象面向该应用的期望版本',
  `event_action` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '事件操作；可选值：UPSERT/DISABLE/REMOVE',
  `sequence_no` int NOT NULL COMMENT '任务依赖顺序',
  `depends_on_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '单一前置项；复杂依赖由拓扑分批处理',
  `payload_json` json NOT NULL COMMENT '冻结的发送资料，禁止任何密码或密钥',
  `payload_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '规范资料摘要，重试不可改变',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态；可选值：PENDING/RUNNING/APPLIED/DUPLICATE/STALE/FAILED/CONFLICT/CANCELED',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_retry_time` datetime(6) DEFAULT NULL COMMENT '下次重试',
  `result_json` json DEFAULT NULL COMMENT '校验并脱敏的接收回执',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_29_1` (`task_id`,`sequence_no`),
  KEY `ix_29_1` (`app_id`,`object_type`,`object_id`,`source_version`),
  KEY `ix_29_2` (`status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-不可变下发事件与结果';

-- TABLE iam_sync_task_t
CREATE TABLE `iam_sync_task_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '目标应用',
  `config_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '接收配置',
  `config_version` bigint unsigned NOT NULL COMMENT '已锁定配置版本',
  `config_snapshot_json` json NOT NULL COMMENT '创建时冻结的非秘密接收地址、实例、协议和映射配置',
  `mode` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '模式；可选值：FULL/DELTA/RECONCILE',
  `request_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '创建任务幂等号',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态；可选值：PENDING/RUNNING/PARTIAL/SUCCEEDED/FAILED/CANCELED',
  `lease_token` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '工作者租约标识，不是用户登录令牌',
  `lease_until` datetime(6) DEFAULT NULL COMMENT '租约超时',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总项数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功项数',
  `failure_count` int NOT NULL DEFAULT '0' COMMENT '失败项数',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_28_1` (`app_id`,`request_id`),
  KEY `ix_28_1` (`status`,`lease_until`),
  KEY `ix_28_2` (`app_id`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-同步与对账任务';

-- TABLE iam_tenant_directory_link_t
CREATE TABLE `iam_tenant_directory_link_t` (
  `tid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键标识',
  `app_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用标识',
  `object_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '对象类型',
  `local_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '本地标识',
  `central_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '中心标识',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '更新时间',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_iam_tenant_directory_local` (`app_id`,`object_type`,`local_id`),
  UNIQUE KEY `uk_iam_tenant_directory_central` (`app_id`,`object_type`,`central_id`),
  KEY `ix_iam_tenant_directory_central` (`central_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-租户目录导入时的身份映射；不包含凭据或授权';

-- TABLE iam_verification_t
CREATE TABLE `iam_verification_t` (
  `tid` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT (utc_timestamp(6)) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT '1' COMMENT '乐观版本，每次有效修改加1',
  `target_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '目标类型；可选值：SUBJECT/LEGAL_ENTITY',
  `target_id` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '待核验主体',
  `provider_code` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '核验渠道',
  `provider_request_id` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '外部请求号',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态；可选值：PENDING/VERIFIED/REJECTED/EXPIRED',
  `result_code` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '脱敏结果码',
  `evidence_ref` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '受控证据引用，不直接保存证件图片',
  `consent_ref` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '必要的申请/授权记录引用',
  `requested_time` datetime(6) NOT NULL COMMENT '申请时间',
  `completed_time` datetime(6) DEFAULT NULL COMMENT '完成时间',
  `expires_at` datetime(6) DEFAULT NULL COMMENT '结果到期',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `uk_40_1` (`provider_code`,`provider_request_id`),
  KEY `ix_40_1` (`target_type`,`target_id`,`requested_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一身份-实名与法人核验过程';

-- TABLE sym_config_t
CREATE TABLE `sym_config_t` (
  `TID` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `CONFIG_CODE` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置编码',
  `CONFIG_NAME` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配置名称',
  `CONFIG_VALUE` longtext COLLATE utf8mb4_general_ci COMMENT '配置值',
  `IS_USE` decimal(1,0) DEFAULT NULL COMMENT '是否使用',
  `SORT_NO` decimal(4,0) DEFAULT NULL COMMENT '排序编号',
  `CONFIG_TYPE` decimal(8,0) DEFAULT NULL COMMENT '配置类型',
  `CREATE_USER_ID` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建用户标识',
  `CREATE_TIME` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `UPDATE_TIME` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `CONFIG_DESC` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配置描述',
  `IS_ENCRYPT` decimal(1,0) DEFAULT NULL COMMENT '是否加密',
  `MODULE_ID` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '模块标识',
  `IS_DEL` tinyint DEFAULT NULL COMMENT '删除标志：0正常，1删除',
  `CREATE_BY` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人员',
  `UPDATE_BY` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人员',
  `JSON_DATA` text COLLATE utf8mb4_general_ci COMMENT 'JSON数据数据',
  `TENANT_ID` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `CONFIG_GROUP` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置分组',
  `VALUE_TYPE` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '值类型',
  `CONFIG_SCOPE` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置范围',
  `CACHE_TTL_SECONDS` int NOT NULL COMMENT '缓存缓存时长秒',
  `VERSION_NO` bigint NOT NULL COMMENT '版本编号',
  PRIMARY KEY (`TID`),
  UNIQUE KEY `INDEX33565610` (`TID`),
  KEY `idx_sym_config_lookup` (`TENANT_ID`,`CONFIG_GROUP`,`IS_USE`,`IS_DEL`,`SORT_NO`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统管理-系统配置';

-- TABLE sym_tenant_t
CREATE TABLE `sym_tenant_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `key` int DEFAULT NULL COMMENT '键',
  `code` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '编码',
  `name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '名称',
  `init_flag` tinyint DEFAULT NULL COMMENT '初始化标志',
  `json_config` longtext COLLATE utf8mb4_general_ci COMMENT 'JSON数据配置',
  `sort_no` int DEFAULT NULL COMMENT '排序编号',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人标识',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人标识',
  `is_del` tinyint DEFAULT NULL COMMENT '删除标志：0正常，1删除',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户标识',
  `status` tinyint NOT NULL COMMENT '状态',
  `isolation_mode` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '隔离模式',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `INDEX33565611` (`tid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统管理-租户定义';

-- TABLE ui_component_history_t
CREATE TABLE `ui_component_history_t` (
  `tid` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `tenant_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户标识',
  `component_id` varchar(36) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件标识',
  `source_code` longtext COLLATE utf8mb4_general_ci COMMENT 'Vue单文件组件源码',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人标识',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人标识',
  `is_del` tinyint DEFAULT NULL COMMENT '删除标志：0正常，1删除',
  PRIMARY KEY (`tid`),
  UNIQUE KEY `INDEX33565612` (`tid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='低代码组件-低代码组件历史版本';

-- TABLE ui_component_t
CREATE TABLE `ui_component_t` (
  `tid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键标识',
  `pid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '父级标识',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '名称',
  `source_code` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'Vue单文件组件源码',
  `compile_js` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '组件编译后JavaScript',
  `compile_css` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '组件编译后样式',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人标识',
  `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人标识',
  `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `is_del` tinyint DEFAULT NULL COMMENT '删除标志：0正常，1删除',
  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户标识',
  `sort_order` int DEFAULT NULL COMMENT '排序顺序',
  PRIMARY KEY (`tid`) USING BTREE,
  UNIQUE KEY `INDEX33565613` (`tid`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='低代码组件-低代码组件定义';

-- VIEW iam_operator_profile_v
CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `iam_operator_profile_v` AS select `o`.`tid` AS `tid`,`o`.`created_by` AS `created_by`,`o`.`created_time` AS `created_time`,`o`.`updated_by` AS `updated_by`,`o`.`updated_time` AS `updated_time`,`o`.`version` AS `version`,`o`.`username` AS `username`,(case when (`o`.`subject_id` is null) then `o`.`display_name` else `person`.`name` end) AS `display_name`,(case when (`o`.`subject_id` is null) then `o`.`phone` else `person`.`phone` end) AS `phone`,(case when (`o`.`subject_id` is null) then `o`.`email` else `person`.`email` end) AS `email`,`o`.`subject_id` AS `subject_id`,`person`.`version` AS `subject_version`,`o`.`status` AS `status`,`o`.`must_change_password` AS `must_change_password`,`o`.`last_login_time` AS `last_login_time`,`o`.`active_username` AS `active_username`,`o`.`is_del` AS `is_del` from (`iam_operator_t` `o` left join `iam_subject_t` `person` on((`person`.`tid` = `o`.`subject_id`)));

-- VIEW magic_system_job_status_t
CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `magic_system_job_status_t` AS select `api_job_t`.`tid` AS `tid`,`api_job_t`.`job_code` AS `job_code`,`api_job_t`.`run_count` AS `run_count`,`api_job_t`.`last_started_at` AS `last_started_at`,`api_job_t`.`last_finished_at` AS `last_finished_at`,`api_job_t`.`last_duration_ms` AS `last_duration_ms`,`api_job_t`.`last_status` AS `last_status`,`api_job_t`.`last_error` AS `last_error` from `api_job_t`;

SET FOREIGN_KEY_CHECKS=1;
