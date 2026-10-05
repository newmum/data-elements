-- 卓鉴IAM整体设计评审草案：未执行，不是当前baseline的迁移脚本。
-- 目标MySQL 8.0；仅允许在后续批准的新建空白评审schema验证。
-- 不含USE、ALTER、DROP、RENAME、INSERT；不要直接在现有baseline执行。
-- 阶段1至5描述完整长期结构；阶段6为旧共享账号退役，不增加本方案业务表。
-- 关系采用项目现有的逻辑外键方式，由Magic同库事务、引用锁和离线完整性检查落实。

-- 01. 阶段1：应用分组
CREATE TABLE `iam_application_group_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `code` varchar(64) NOT NULL COMMENT '分组编码，不复用已归档编码',
  `name` varchar(100) NOT NULL COMMENT '分组名称',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_1_1` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='应用分组';

-- 02. 阶段1：身份应用主表
CREATE TABLE `iam_application_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `code` varchar(64) NOT NULL COMMENT '全局应用编码；现有应用沿用原行业编码',
  `name` varchar(200) NOT NULL COMMENT '业务应用名称',
  `kind` varchar(16) NOT NULL DEFAULT 'BUSINESS' COMMENT 'BUSINESS行业应用；INTERNAL为卓鉴自己的管理资源容器',
  `group_id` varchar(32) NULL COMMENT '应用分组ID',
  `description` varchar(1000) NULL COMMENT '说明',
  `owner_operator_id` varchar(32) NULL COMMENT '平台侧负责操作账号，可空',
  `homepage` varchar(1000) NULL COMMENT '应用首页，不放令牌',
  `identity_domains` json NOT NULL COMMENT '支持的身份域数组，workforce/public',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/DISABLED/ARCHIVED',
  `auth_mode` varchar(24) NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL或后续显式启用的FEDERATED',
  `provisioning_mode` varchar(24) NOT NULL DEFAULT 'OFF' COMMENT 'OFF/HTTP；下发与认证独立开关',
  `ext_json` json NULL COMMENT '经字段定义校验的扩展资料',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_2_1` (`code`),
  KEY `ix_2_1` (`group_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='身份应用主表';

-- 03. 阶段1：应用与原运行租户绑定
CREATE TABLE `iam_application_runtime_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) NOT NULL COMMENT 'IAM应用ID',
  `runtime_tenant_id` varchar(32) NOT NULL COMMENT '共享框架 sym_tenant_t.tid',
  `backend_code` varchar(64) NOT NULL DEFAULT 'data-elements' COMMENT '后端部署标识',
  `environment` varchar(32) NOT NULL DEFAULT 'development' COMMENT '运行环境标识',
  `local_permission_app_id` varchar(64) NULL COMMENT '本地角色菜单原app_id，非IAM应用ID',
  `binding_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/VERIFIED/DISABLED',
  `verified_time` datetime(6) NULL COMMENT '最近完成实际数据源核验的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_3_1` (`app_id`),
  UNIQUE KEY `uk_3_2` (`runtime_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='应用与原运行租户绑定';

-- 04. 阶段1：卓鉴平台操作账号
CREATE TABLE `iam_operator_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `username` varchar(50) NOT NULL COMMENT '仅用于卓鉴管理控制台登录的账号',
  `display_name` varchar(100) NOT NULL COMMENT '姓名或显示名',
  `phone` varchar(256) NULL COMMENT '联系方式，可按策略加密',
  `email` varchar(512) NULL COMMENT '邮箱，可按策略加密',
  `subject_id` varchar(32) NULL COMMENT '可选关联人员档案，不继承档案的应用权限',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/DISABLED',
  `must_change_password` tinyint NOT NULL DEFAULT 0 COMMENT '下次登录是否必须改密',
  `last_login_time` datetime(6) NULL COMMENT '最近平台登录时间',
  `active_username` varchar(50) GENERATED ALWAYS AS (CASE WHEN is_del=0 THEN lower(trim(username)) ELSE NULL END) STORED COMMENT '有效账号规范名',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_4_1` (`active_username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='卓鉴平台操作账号';

-- 05. 阶段1：平台操作账号角色分配
CREATE TABLE `iam_operator_role_rela_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `operator_id` varchar(32) NOT NULL COMMENT '操作账号ID',
  `role_id` varchar(32) NOT NULL COMMENT '卓鉴INTERNAL应用下的平台角色ID',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
  `valid_from` datetime(6) NOT NULL COMMENT '生效时间',
  `valid_until` datetime(6) NULL COMMENT '失效时间，空表示不设固定到期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_5_1` (`operator_id`,`role_id`),
  CONSTRAINT `ck_5_1` CHECK (valid_until IS NULL OR valid_until>valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='平台操作账号角色分配';

-- 06. 阶段1：平台角色分配的管理范围
CREATE TABLE `iam_operator_scope_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `assignment_id` varchar(32) NOT NULL COMMENT '平台角色分配ID',
  `identity_domain` varchar(16) NOT NULL COMMENT 'workforce/public；分别授权',
  `scope_kind` varchar(16) NOT NULL COMMENT 'ALL/APPLICATION/ORG',
  `app_id` varchar(32) NULL COMMENT '范围限定应用，可与ORG组合限定机构内指定应用',
  `org_id` varchar(32) NULL COMMENT '范围限定机构',
  `include_children` tinyint NOT NULL DEFAULT 0 COMMENT '是否含下级机构',
  PRIMARY KEY (`id`),
  KEY `ix_6_1` (`assignment_id`),
  KEY `ix_6_2` (`app_id`),
  KEY `ix_6_3` (`org_id`),
  CONSTRAINT `ck_6_1` CHECK ((scope_kind='ALL' AND app_id IS NULL AND org_id IS NULL) OR (scope_kind='APPLICATION' AND app_id IS NOT NULL AND org_id IS NULL) OR (scope_kind='ORG' AND org_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='平台角色分配的管理范围';

-- 07. 阶段1：应用角色与平台内部角色
CREATE TABLE `iam_role_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) NOT NULL COMMENT '所属应用；平台角色归属INTERNAL应用',
  `code` varchar(100) NOT NULL COMMENT '应用内角色编码',
  `name` varchar(100) NOT NULL COMMENT '角色名称',
  `identity_domain` varchar(16) NOT NULL COMMENT 'workforce/public',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `description` varchar(1000) NULL COMMENT '职责说明',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_7_1` (`app_id`,`code`),
  KEY `ix_7_1` (`app_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='应用角色与平台内部角色';

-- 08. 阶段1：应用菜单按钮和API资源
CREATE TABLE `iam_resource_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) NOT NULL COMMENT '所属应用',
  `parent_id` varchar(32) NULL COMMENT '同应用父资源ID',
  `code` varchar(160) NOT NULL COMMENT '应用内唯一权限标识',
  `name` varchar(200) NOT NULL COMMENT '资源名称',
  `resource_type` varchar(16) NOT NULL COMMENT 'MENU/BUTTON/API',
  `path` varchar(1000) NULL COMMENT '页面或API路径',
  `http_method` varchar(16) NULL COMMENT 'API请求方法',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '顺序',
  `ext_json` json NULL COMMENT '资源扩展资料',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_8_1` (`app_id`,`code`),
  KEY `ix_8_1` (`app_id`,`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='应用菜单按钮和API资源';

-- 09. 阶段1：角色资源关联
CREATE TABLE `iam_role_resource_rela_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `role_id` varchar(32) NOT NULL COMMENT '角色ID',
  `resource_id` varchar(32) NOT NULL COMMENT '资源ID',
  `app_id` varchar(32) NOT NULL COMMENT '归属应用用于隔离校验',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_9_1` (`role_id`,`resource_id`),
  KEY `ix_9_1` (`app_id`,`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='角色资源关联';

-- 10. 阶段1：认证主体锁定与安全状态
CREATE TABLE `iam_account_security_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `principal_kind` varchar(24) NOT NULL COMMENT 'OPERATOR/AUTH_ACCOUNT',
  `principal_id` varchar(32) NOT NULL COMMENT '对应认证账号ID',
  `failed_count` int NOT NULL DEFAULT 0 COMMENT '连续失败次数',
  `locked_until` datetime(6) NULL COMMENT '锁定截止',
  `password_changed_time` datetime(6) NULL COMMENT '最近改密时间',
  `security_version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '安全版本，改密等操作触发会话版本失效',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_10_1` (`principal_kind`,`principal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='认证主体锁定与安全状态';

-- 11. 阶段1：分用途凭据与密钥版本
CREATE TABLE `iam_credential_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `owner_kind` varchar(24) NOT NULL COMMENT 'OPERATOR/AUTH_ACCOUNT/CLIENT/SYNC_CONFIG',
  `owner_id` varchar(32) NOT NULL COMMENT '归属账号或配置ID',
  `credential_type` varchar(24) NOT NULL COMMENT 'PASSWORD/TOTP/WEBAUTHN/CLIENT_SECRET/HMAC_KEY/RECOVERY_CODE',
  `slot` varchar(100) NOT NULL DEFAULT 'primary' COMMENT '密码固定primary；设备或轮换密钥使用独立槽',
  `revision` int unsigned NOT NULL DEFAULT 1 COMMENT '凭据版本',
  `algorithm` varchar(64) NOT NULL COMMENT '明确算法和编码版本',
  `secret_hash` varchar(2048) NULL COMMENT '密码、恢复码、客户端验证密钥的单向摘要',
  `secret_cipher` text NULL COMMENT '需要恢复使用的TOTP种子或签名密钥的密文',
  `key_ref` varchar(200) NULL COMMENT '外部加密主密钥引用；不存主密钥值',
  `public_data` json NULL COMMENT '公开公钥及非敏感参数，不放密码',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/RETIRED/REVOKED',
  `not_before` datetime(6) NULL COMMENT '可用起点',
  `expires_at` datetime(6) NULL COMMENT '失效时间',
  `active_slot` varchar(160) GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN concat(credential_type,':',slot) ELSE NULL END) STORED COMMENT '防止同凭据槽多个活动版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_11_1` (`owner_kind`,`owner_id`,`credential_type`,`slot`,`revision`),
  UNIQUE KEY `uk_11_2` (`owner_kind`,`owner_id`,`active_slot`),
  CONSTRAINT `ck_11_1` CHECK (credential_type<>'PASSWORD' OR slot='primary'),
  CONSTRAINT `ck_11_2` CHECK (secret_hash IS NULL OR secret_cipher IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='分用途凭据与密钥版本';

-- 12. 阶段1：IAM自身配置与策略
CREATE TABLE `iam_setting_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `scope_kind` varchar(16) NOT NULL COMMENT 'GLOBAL/DOMAIN/APPLICATION',
  `scope_key` varchar(64) NOT NULL COMMENT '全局固定global；身份域或应用ID',
  `category` varchar(32) NOT NULL COMMENT 'SECURITY/SESSION/BRANDING/FIELD_PROTECTION/API_ACCESS',
  `config_key` varchar(100) NOT NULL COMMENT '配置项键',
  `value_json` json NOT NULL COMMENT '经过类型和范围校验的配置，不存秘密',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_12_1` (`scope_kind`,`scope_key`,`category`,`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='IAM自身配置与策略';

-- 13. 阶段1：操作认证和API审计
CREATE TABLE `iam_audit_event_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `event_type` varchar(24) NOT NULL COMMENT 'OPERATION/AUTH/API/MIGRATION',
  `identity_domain` varchar(16) NULL COMMENT '事件身份域',
  `actor_kind` varchar(24) NOT NULL COMMENT 'OPERATOR/AUTH_ACCOUNT/CLIENT/SYSTEM/LEGACY',
  `actor_id` varchar(64) NULL COMMENT '操作者ID或历史主体引用',
  `app_id` varchar(32) NULL COMMENT '目标应用；平台事件可为INTERNAL应用',
  `action` varchar(100) NOT NULL COMMENT '稳定业务动作码',
  `object_type` varchar(32) NULL COMMENT '目标对象类型',
  `object_id` varchar(64) NULL COMMENT '目标对象ID',
  `result` varchar(24) NOT NULL COMMENT 'SUCCESS/DENIED/FAILED等',
  `reason_code` varchar(100) NULL COMMENT '可检索原因码',
  `account_fingerprint` char(64) NULL COMMENT '失败登录账号指纹，不存口令',
  `trace_id` varchar(64) NOT NULL COMMENT '链路号',
  `ip` varchar(64) NULL COMMENT '访问地址',
  `request_path` varchar(1000) NULL COMMENT '脱敏后的路径',
  `http_method` varchar(16) NULL COMMENT '请求方法',
  `http_status` int NULL COMMENT 'HTTP状态',
  `duration_ms` bigint NULL COMMENT '耗时',
  `event_time` datetime(6) NOT NULL COMMENT '事件实际发生时间，历史迁移不改写',
  `snapshot_json` json NULL COMMENT '脱敏前后值和必要上下文，不含凭据或完整报文',
  PRIMARY KEY (`id`),
  KEY `ix_13_1` (`event_type`,`event_time`),
  KEY `ix_13_2` (`app_id`,`event_time`),
  KEY `ix_13_3` (`actor_kind`,`actor_id`,`event_time`),
  KEY `ix_13_4` (`trace_id`),
  KEY `ix_13_5` (`account_fingerprint`,`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='操作认证和API审计';

-- 14. 阶段1：业务请求幂等回执
CREATE TABLE `iam_request_receipt_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `operator_id` varchar(32) NOT NULL COMMENT '当前平台操作账号ID',
  `operation_code` varchar(100) NOT NULL COMMENT '接口业务动作码',
  `request_id` varchar(64) NOT NULL COMMENT '调用者请求号',
  `request_hash` char(64) NOT NULL COMMENT '规范化有效请求内容摘要，不记录密码本身',
  `status` varchar(24) NOT NULL COMMENT 'IN_PROGRESS/SUCCEEDED/FAILED/LEGACY_ARCHIVED',
  `result_json` json NULL COMMENT '可安全回放结果，禁止令牌和秘密',
  `error_code` varchar(100) NULL COMMENT '失败码',
  `expires_at` datetime(6) NULL COMMENT '非历史回执保留截止',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_14_1` (`operator_id`,`operation_code`,`request_id`),
  KEY `ix_14_1` (`status`,`updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='业务请求幂等回执';

-- 15. 阶段1：重构迁移批次与ID对照
CREATE TABLE `iam_migration_map_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `batch_id` varchar(64) NOT NULL COMMENT '迁移批次',
  `source_schema` varchar(100) NOT NULL COMMENT '来源库名，仅记录来源不用于动态执行',
  `source_table` varchar(100) NOT NULL COMMENT '来源表',
  `source_id` varchar(100) NOT NULL COMMENT '原ID或组合键',
  `target_type` varchar(100) NOT NULL COMMENT '目标表或归档类型',
  `target_id` varchar(64) NOT NULL COMMENT '新ID或保留ID',
  `source_hash` char(64) NOT NULL COMMENT '迁移快照摘要',
  `state` varchar(24) NOT NULL COMMENT 'COPIED/VERIFIED/ARCHIVED/ROLLED_BACK',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_15_1` (`batch_id`,`source_schema`,`source_table`,`source_id`,`target_type`),
  KEY `ix_15_1` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='重构迁移批次与ID对照';

-- 16. 阶段2：中央人员主档
CREATE TABLE `iam_subject_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `identity_domain` varchar(16) NOT NULL COMMENT 'workforce/public',
  `subject_code` varchar(64) NOT NULL COMMENT '稳定中央人员编号',
  `account_hint` varchar(100) NULL COMMENT '建议下发登录名；不是平台登录凭据',
  `name` varchar(100) NOT NULL COMMENT '姓名',
  `phone` varchar(256) NULL COMMENT '受保护联系方式',
  `email` varchar(512) NULL COMMENT '受保护邮箱',
  `employee_no` varchar(100) NULL COMMENT '员工编号',
  `employment_type` varchar(24) NULL COMMENT 'EMPLOYEE/PARTNER/TEMPORARY等',
  `join_date` date NULL COMMENT '入职日期',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/ARCHIVED',
  `ext_json` json NULL COMMENT '扩展属性；原record_json剩余资料须明确归属后迁移',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_16_1` (`identity_domain`,`subject_code`),
  KEY `ix_16_1` (`identity_domain`,`status`),
  KEY `ix_16_2` (`identity_domain`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='中央人员主档';

-- 17. 阶段2：中央机构树
CREATE TABLE `iam_org_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `identity_domain` varchar(16) NOT NULL COMMENT '所属身份域',
  `code` varchar(100) NOT NULL COMMENT '域内稳定机构编码',
  `name` varchar(200) NOT NULL COMMENT '机构名称',
  `parent_id` varchar(32) NULL COMMENT '同域上级机构；根为空',
  `path` varchar(2000) NOT NULL COMMENT '以ID构造的规范路径，含自身ID边界分隔',
  `depth` int NOT NULL DEFAULT 0 COMMENT '层级',
  `leader_subject_id` varchar(32) NULL COMMENT '负责人档案ID',
  `line_code` varchar(100) NULL COMMENT '条线字典编码',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/ARCHIVED',
  `ext_json` json NULL COMMENT '机构扩展属性',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_17_1` (`identity_domain`,`code`),
  KEY `ix_17_1` (`identity_domain`,`parent_id`),
  KEY `ix_17_2` (`identity_domain`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='中央机构树';

-- 18. 阶段2：人员机构与主兼任
CREATE TABLE `iam_appointment_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `subject_id` varchar(32) NOT NULL COMMENT '中央人员ID',
  `org_id` varchar(32) NOT NULL COMMENT '机构ID',
  `post_code` varchar(100) NULL COMMENT '岗位字典编码',
  `position_code` varchar(100) NULL COMMENT '职务字典编码',
  `rank_code` varchar(100) NULL COMMENT '职级字典编码',
  `title_code` varchar(100) NULL COMMENT '职称字典编码',
  `is_primary` tinyint NOT NULL DEFAULT 0 COMMENT '是否主职',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ENDED',
  `valid_from` datetime(6) NOT NULL COMMENT '生效时间',
  `valid_until` datetime(6) NULL COMMENT '截止时间',
  `primary_subject_id` varchar(32) GENERATED ALWAYS AS (CASE WHEN is_del=0 AND status='ACTIVE' AND is_primary=1 THEN subject_id ELSE NULL END) STORED COMMENT '每人最多一条标记活动主职',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_18_1` (`primary_subject_id`),
  KEY `ix_18_1` (`subject_id`,`status`),
  KEY `ix_18_2` (`org_id`,`status`),
  CONSTRAINT `ck_18_1` CHECK (valid_until IS NULL OR valid_until>valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='人员机构与主兼任';

-- 19. 阶段2：条线岗位职务等IAM字典
CREATE TABLE `iam_dict_item_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `identity_domain` varchar(16) NOT NULL COMMENT '身份域',
  `category` varchar(32) NOT NULL COMMENT 'LINE/POST/POSITION/RANK/TITLE等',
  `code` varchar(100) NOT NULL COMMENT '同类编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `parent_id` varchar(32) NULL COMMENT '同类父字典',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_19_1` (`identity_domain`,`category`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='条线岗位职务等IAM字典';

-- 20. 阶段2：扩展属性定义
CREATE TABLE `iam_field_definition_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `identity_domain` varchar(16) NOT NULL COMMENT '身份域',
  `entity_type` varchar(24) NOT NULL COMMENT 'SUBJECT/ORG/APPLICATION/RESOURCE',
  `field_key` varchar(64) NOT NULL COMMENT '字段键',
  `label` varchar(100) NOT NULL COMMENT '显示名',
  `value_type` varchar(24) NOT NULL COMMENT 'STRING/NUMBER/DATE/BOOLEAN/ENUM',
  `required_flag` tinyint NOT NULL DEFAULT 0 COMMENT '是否必填',
  `validation_json` json NULL COMMENT '长度、枚举、格式及迁移默认值等',
  `sensitive_flag` tinyint NOT NULL DEFAULT 0 COMMENT '是否敏感',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_20_1` (`identity_domain`,`entity_type`,`field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='扩展属性定义';

-- 21. 阶段3：应用资格和角色授权来源
CREATE TABLE `iam_access_grant_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) NOT NULL COMMENT '被授予的业务应用ID',
  `identity_domain` varchar(16) NOT NULL COMMENT '主体身份域',
  `principal_type` varchar(16) NOT NULL COMMENT 'SUBJECT/ORG/GROUP/CLIENT',
  `subject_id` varchar(32) NULL COMMENT '人员直接授权主体',
  `org_id` varchar(32) NULL COMMENT '机构授权主体',
  `group_id` varchar(32) NULL COMMENT '权限组授权主体',
  `client_id` varchar(32) NULL COMMENT '机器客户端授权主体',
  `include_children` tinyint NOT NULL DEFAULT 0 COMMENT '机构授权是否含下级',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
  `valid_from` datetime(6) NOT NULL COMMENT '起效',
  `valid_until` datetime(6) NULL COMMENT '到期',
  `reason` varchar(1000) NULL COMMENT '授权理由',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  KEY `ix_21_1` (`app_id`,`status`),
  KEY `ix_21_2` (`subject_id`,`app_id`),
  KEY `ix_21_3` (`org_id`,`app_id`),
  KEY `ix_21_4` (`group_id`,`app_id`),
  KEY `ix_21_5` (`client_id`,`app_id`),
  CONSTRAINT `ck_21_1` CHECK (valid_until IS NULL OR valid_until>valid_from),
  CONSTRAINT `ck_21_2` CHECK ((principal_type='SUBJECT' AND subject_id IS NOT NULL AND org_id IS NULL AND group_id IS NULL AND client_id IS NULL) OR (principal_type='ORG' AND subject_id IS NULL AND org_id IS NOT NULL AND group_id IS NULL AND client_id IS NULL) OR (principal_type='GROUP' AND subject_id IS NULL AND org_id IS NULL AND group_id IS NOT NULL AND client_id IS NULL) OR (principal_type='CLIENT' AND subject_id IS NULL AND org_id IS NULL AND group_id IS NULL AND client_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='应用资格和角色授权来源';

-- 22. 阶段3：授权包含的应用角色
CREATE TABLE `iam_grant_role_rela_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `grant_id` varchar(32) NOT NULL COMMENT '授权来源ID',
  `role_id` varchar(32) NOT NULL COMMENT '应用角色ID',
  `app_id` varchar(32) NOT NULL COMMENT '同应用约束值',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_22_1` (`grant_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='授权包含的应用角色';

-- 23. 阶段3：跨应用权限组合
CREATE TABLE `iam_permission_group_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `identity_domain` varchar(16) NOT NULL COMMENT '身份域',
  `code` varchar(100) NOT NULL COMMENT '组编码',
  `name` varchar(200) NOT NULL COMMENT '组名称',
  `description` varchar(1000) NULL COMMENT '说明',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_23_1` (`identity_domain`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='跨应用权限组合';

-- 24. 阶段3：权限组人员成员
CREATE TABLE `iam_group_member_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `group_id` varchar(32) NOT NULL COMMENT '权限组ID',
  `subject_id` varchar(32) NOT NULL COMMENT '人员ID',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
  `valid_from` datetime(6) NOT NULL COMMENT '生效时间',
  `valid_until` datetime(6) NULL COMMENT '截止时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_24_1` (`group_id`,`subject_id`),
  KEY `ix_24_1` (`subject_id`,`status`),
  CONSTRAINT `ck_24_1` CHECK (valid_until IS NULL OR valid_until>valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='权限组人员成员';

-- 25. 阶段2：人员应用分配与下发投影
CREATE TABLE `iam_subject_app_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `subject_id` varchar(32) NOT NULL COMMENT '中央人员ID',
  `app_id` varchar(32) NOT NULL COMMENT '目标业务应用ID',
  `account_alias` varchar(100) NULL COMMENT '该应用拟使用的本地账号名',
  `directory_assigned` tinyint NOT NULL DEFAULT 1 COMMENT '是否仍有人工目录分配来源；纯授权派生记录显式写0',
  `assignment_mode` varchar(24) NOT NULL DEFAULT 'DIRECTORY_ONLY' COMMENT '计算结果：DIRECTORY_ONLY仅目录；GRANT_DERIVED有有效授权',
  `assignment_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE有目录或授权来源；REVOKED无来源仍保留撤销回执',
  `desired_enabled` tinyint NOT NULL DEFAULT 0 COMMENT '期望的中央来源可用状态，不覆盖本地停用',
  `desired_version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '此应用分配的递增期望版本',
  `ack_version` bigint unsigned NOT NULL DEFAULT 0 COMMENT '接收方已确认应用的版本',
  `sync_status` varchar(24) NOT NULL DEFAULT 'NOT_CONNECTED' COMMENT 'NOT_CONNECTED/PENDING/IN_SYNC/FAILED/CONFLICT',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_25_1` (`subject_id`,`app_id`),
  KEY `ix_25_1` (`app_id`,`sync_status`),
  CONSTRAINT `ck_25_1` CHECK (directory_assigned IN (0,1)),
  CONSTRAINT `ck_25_2` CHECK (desired_enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='人员应用分配与下发投影';

-- 26. 阶段2：应用机构目录范围
CREATE TABLE `iam_app_org_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) NOT NULL COMMENT '业务应用ID',
  `org_id` varchar(32) NOT NULL COMMENT '中央机构ID',
  `include_children` tinyint NOT NULL DEFAULT 0 COMMENT '是否包含下级',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_26_1` (`app_id`,`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='应用机构目录范围';

-- 27. 阶段4：应用HTTP接收配置
CREATE TABLE `iam_sync_config_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) NOT NULL COMMENT '目标应用',
  `endpoint` varchar(1000) NOT NULL COMMENT '经校验的完整接收URL',
  `protocol_version` varchar(32) NOT NULL COMMENT 'IDAAS/2；旧IDAAS/1需显式适配',
  `receiver_instance_id` varchar(100) NOT NULL COMMENT '接收方固定实例标识，防止地址错配',
  `timeout_seconds` int NOT NULL DEFAULT 10 COMMENT '有限超时',
  `batch_size` int NOT NULL DEFAULT 100 COMMENT '单任务分批大小',
  `max_attempts` int NOT NULL DEFAULT 5 COMMENT '重试上限',
  `object_types` json NOT NULL COMMENT '启用的对象类型清单',
  `mapping_json` json NULL COMMENT '可声明的字段映射和字段归属规则，不执行任意代码',
  `enabled` tinyint NOT NULL DEFAULT 0 COMMENT '默认关闭，连通验证成功后显式开通',
  `last_test_time` datetime(6) NULL COMMENT '最近连接测试',
  `last_test_result` varchar(32) NULL COMMENT 'SUCCESS/FAILED；不代表业务已同步',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_27_1` (`app_id`),
  CONSTRAINT `ck_27_1` CHECK (timeout_seconds BETWEEN 1 AND 60),
  CONSTRAINT `ck_27_2` CHECK (batch_size BETWEEN 1 AND 1000),
  CONSTRAINT `ck_27_3` CHECK (max_attempts BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='应用HTTP接收配置';

-- 28. 阶段4：同步与对账任务
CREATE TABLE `iam_sync_task_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) NOT NULL COMMENT '目标应用',
  `config_id` varchar(32) NOT NULL COMMENT '接收配置',
  `config_version` bigint unsigned NOT NULL COMMENT '已锁定配置版本',
  `config_snapshot_json` json NOT NULL COMMENT '创建时冻结的非秘密接收地址、实例、协议和映射配置',
  `mode` varchar(24) NOT NULL COMMENT 'FULL/DELTA/RECONCILE',
  `request_id` varchar(64) NOT NULL COMMENT '创建任务幂等号',
  `status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/PARTIAL/SUCCEEDED/FAILED/CANCELED',
  `lease_token` varchar(64) NULL COMMENT '工作者租约标识，不是用户登录令牌',
  `lease_until` datetime(6) NULL COMMENT '租约超时',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '总项数',
  `success_count` int NOT NULL DEFAULT 0 COMMENT '成功项数',
  `failure_count` int NOT NULL DEFAULT 0 COMMENT '失败项数',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_28_1` (`app_id`,`request_id`),
  KEY `ix_28_1` (`status`,`lease_until`),
  KEY `ix_28_2` (`app_id`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='同步与对账任务';

-- 29. 阶段4：不可变下发事件与结果
CREATE TABLE `iam_sync_item_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `task_id` varchar(32) NOT NULL COMMENT '任务ID',
  `app_id` varchar(32) NOT NULL COMMENT '目标应用ID',
  `object_type` varchar(24) NOT NULL COMMENT 'ORG/SUBJECT/APPOINTMENT/ROLE/RESOURCE/USER_ROLE等',
  `object_id` varchar(64) NOT NULL COMMENT '中央对象稳定ID',
  `source_version` bigint unsigned NOT NULL COMMENT '该对象面向该应用的期望版本',
  `event_action` varchar(24) NOT NULL COMMENT 'UPSERT/DISABLE/REMOVE',
  `sequence_no` int NOT NULL COMMENT '任务依赖顺序',
  `depends_on_id` varchar(32) NULL COMMENT '单一前置项；复杂依赖由拓扑分批处理',
  `payload_json` json NOT NULL COMMENT '冻结的发送资料，禁止任何密码或密钥',
  `payload_hash` char(64) NOT NULL COMMENT '规范资料摘要，重试不可改变',
  `status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/APPLIED/DUPLICATE/STALE/FAILED/CONFLICT/CANCELED',
  `attempt_count` int NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `next_retry_time` datetime(6) NULL COMMENT '下次重试',
  `result_json` json NULL COMMENT '校验并脱敏的接收回执',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_29_1` (`task_id`,`sequence_no`),
  KEY `ix_29_1` (`app_id`,`object_type`,`object_id`,`source_version`),
  KEY `ix_29_2` (`status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='不可变下发事件与结果';

-- 30. 阶段4：同步每次尝试记录
CREATE TABLE `iam_sync_attempt_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `item_id` varchar(32) NOT NULL COMMENT '下发项ID',
  `attempt_no` int NOT NULL COMMENT '本项尝试序号',
  `credential_id` varchar(32) NULL COMMENT '本次实际使用的服务凭据版本ID；未发出时可空',
  `started_time` datetime(6) NOT NULL COMMENT '开始时间',
  `finished_time` datetime(6) NULL COMMENT '完成时间',
  `transport_status` varchar(24) NOT NULL COMMENT 'SENT/TIMEOUT/NETWORK_ERROR/REJECTED/ACKNOWLEDGED',
  `http_status` int NULL COMMENT 'HTTP状态',
  `result_code` varchar(100) NULL COMMENT '业务回执码',
  `safe_message` varchar(2000) NULL COMMENT '脱敏原因',
  `duration_ms` bigint NULL COMMENT '耗时',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_30_1` (`item_id`,`attempt_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='同步每次尝试记录';

-- 31. 阶段4：中央对象与应用本地对象映射
CREATE TABLE `iam_external_link_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) NOT NULL COMMENT '目标应用',
  `receiver_instance_id` varchar(100) NOT NULL COMMENT '接收方实例',
  `object_type` varchar(24) NOT NULL COMMENT '对象类型',
  `source_id` varchar(64) NOT NULL COMMENT '中央对象ID',
  `local_id` varchar(100) NOT NULL COMMENT '接收方返回本地ID',
  `ack_version` bigint unsigned NOT NULL DEFAULT 0 COMMENT '接收确认版本',
  `ack_hash` char(64) NULL COMMENT '确认资料摘要',
  `management_state` varchar(24) NOT NULL DEFAULT 'MANAGED' COMMENT 'MANAGED/DETACHED/CONFLICT',
  `last_ack_time` datetime(6) NULL COMMENT '最近确认时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_31_1` (`app_id`,`object_type`,`source_id`),
  UNIQUE KEY `uk_31_2` (`app_id`,`receiver_instance_id`,`object_type`,`local_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='中央对象与应用本地对象映射';

-- 32. 阶段4：控制库事务变更与待处理事件
CREATE TABLE `iam_change_event_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `event_type` varchar(64) NOT NULL COMMENT '资料或授权变更事件类型',
  `object_type` varchar(24) NOT NULL COMMENT '主对象类型',
  `object_id` varchar(64) NOT NULL COMMENT '主对象ID',
  `object_version` bigint unsigned NOT NULL COMMENT '主对象版本',
  `scope_json` json NULL COMMENT '受影响身份域和应用范围，不含秘密',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE/FAILED',
  `lease_until` datetime(6) NULL COMMENT '处理租约',
  `lease_token` varchar(64) NULL COMMENT '工作者标识',
  `attempt_count` int NOT NULL DEFAULT 0 COMMENT '重算尝试次数',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_32_1` (`event_type`,`object_type`,`object_id`,`object_version`),
  KEY `ix_32_1` (`status`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='控制库事务变更与待处理事件';

-- 33. 阶段5：可选的统一认证账号
CREATE TABLE `iam_auth_account_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `subject_id` varchar(32) NOT NULL COMMENT '中央人员ID；不是平台操作账号',
  `identity_domain` varchar(16) NOT NULL COMMENT 'workforce/public',
  `username` varchar(100) NOT NULL COMMENT '统一认证登录名',
  `legal_entity_id` varchar(32) NULL COMMENT '可选法人子账号上下文，需有效经办关系',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/DISABLED',
  `must_change_password` tinyint NOT NULL DEFAULT 0 COMMENT '下次强制改密',
  `active_username` varchar(100) GENERATED ALWAYS AS (CASE WHEN is_del=0 THEN lower(trim(username)) ELSE NULL END) STORED COMMENT '同域有效登录名',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_33_1` (`identity_domain`,`active_username`),
  KEY `ix_33_1` (`subject_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='可选的统一认证账号';

-- 34. 阶段3：应用的终端与机器身份登记
CREATE TABLE `iam_auth_client_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `app_id` varchar(32) NOT NULL COMMENT '所属业务应用；不是另一个行业应用',
  `client_key` varchar(100) NOT NULL COMMENT '协议client_id，全局唯一',
  `name` varchar(100) NOT NULL COMMENT '终端/机器名称',
  `client_type` varchar(24) NOT NULL COMMENT 'PUBLIC/CONFIDENTIAL/SERVICE',
  `protocol` varchar(24) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/OIDC；其他协议后续显式扩展',
  `grant_types` json NULL COMMENT '经过白名单校验的授权模式',
  `scopes` json NULL COMMENT '允许请求的scope白名单',
  `token_auth_method` varchar(32) NULL COMMENT 'none/client_secret_basic/private_key_jwt等',
  `require_pkce` tinyint NOT NULL DEFAULT 1 COMMENT '交互授权码使用PKCE',
  `access_ttl_seconds` int NOT NULL DEFAULT 600 COMMENT '访问令牌有效期',
  `refresh_ttl_seconds` int NOT NULL DEFAULT 86400 COMMENT '刷新令牌最大有效期',
  `status` varchar(16) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/ACTIVE/DISABLED；登记不代表协议已开通',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_34_1` (`client_key`),
  KEY `ix_34_1` (`app_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='应用的终端与机器身份登记';

-- 35. 阶段5：认证精确回调地址
CREATE TABLE `iam_auth_redirect_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `client_id` varchar(32) NOT NULL COMMENT '客户端ID',
  `uri_type` varchar(16) NOT NULL COMMENT 'LOGIN/LOGOUT',
  `uri` varchar(2000) NOT NULL COMMENT '完整登记地址',
  `uri_hash` char(64) NOT NULL COMMENT '完整地址摘要便于唯一索引',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_35_1` (`client_id`,`uri_type`,`uri_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='认证精确回调地址';

-- 36. 阶段5：认证会话元数据与撤销索引
CREATE TABLE `iam_auth_session_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `principal_kind` varchar(24) NOT NULL COMMENT 'OPERATOR/AUTH_ACCOUNT/CLIENT',
  `principal_id` varchar(32) NOT NULL COMMENT '认证主体ID',
  `client_id` varchar(32) NULL COMMENT '所属认证客户端',
  `app_id` varchar(32) NULL COMMENT '令牌限定应用',
  `session_fingerprint` char(64) NOT NULL COMMENT '会话或令牌标识摘要，不是原始令牌',
  `family_id` varchar(64) NULL COMMENT '刷新轮换族标识',
  `security_version` bigint unsigned NOT NULL COMMENT '签发时安全版本',
  `issued_at` datetime(6) NOT NULL COMMENT '签发时间',
  `expires_at` datetime(6) NOT NULL COMMENT '到期时间',
  `revoked_at` datetime(6) NULL COMMENT '撤销时间',
  `revoke_reason` varchar(100) NULL COMMENT '撤销原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_36_1` (`session_fingerprint`),
  KEY `ix_36_1` (`principal_kind`,`principal_id`),
  KEY `ix_36_2` (`family_id`),
  KEY `ix_36_3` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='认证会话元数据与撤销索引';

-- 37. 阶段5：公众自然人实名资料
CREATE TABLE `iam_public_profile_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `subject_id` varchar(32) NOT NULL COMMENT 'public域人员ID',
  `document_type` varchar(32) NULL COMMENT '证件类型',
  `document_cipher` text NULL COMMENT '证件号密文',
  `document_fingerprint` char(64) NULL COMMENT '受密钥保护的查重指纹',
  `verification_status` varchar(24) NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED/PENDING/VERIFIED/REJECTED/EXPIRED',
  `verified_time` datetime(6) NULL COMMENT '实名通过时间',
  `verification_id` varchar(32) NULL COMMENT '当前有效核验记录ID',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_37_1` (`subject_id`),
  KEY `ix_37_1` (`document_type`,`document_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='公众自然人实名资料';

-- 38. 阶段5：法人主体
CREATE TABLE `iam_legal_entity_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `code` varchar(100) NOT NULL COMMENT '法人稳定中央编号',
  `name` varchar(200) NOT NULL COMMENT '法人名称',
  `registration_code` varchar(100) NULL COMMENT '统一社会信用代码，经过校验',
  `representative_subject_id` varchar(32) NULL COMMENT '法定代表人自然人档案',
  `verification_status` varchar(24) NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED/PENDING/VERIFIED/REJECTED/EXPIRED',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/ARCHIVED',
  `ext_json` json NULL COMMENT '法人扩展资料',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_38_1` (`code`),
  UNIQUE KEY `uk_38_2` (`registration_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='法人主体';

-- 39. 阶段5：法人自然人及子账号关系
CREATE TABLE `iam_legal_member_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `entity_id` varchar(32) NOT NULL COMMENT '法人ID',
  `subject_id` varchar(32) NOT NULL COMMENT '自然人档案ID',
  `auth_account_id` varchar(32) NULL COMMENT '具体子账号，可空',
  `relation_type` varchar(24) NOT NULL COMMENT 'REPRESENTATIVE/AGENT/SUBACCOUNT',
  `app_id` varchar(32) NULL COMMENT '可选限定业务应用',
  `valid_from` datetime(6) NOT NULL COMMENT '生效时间',
  `valid_until` datetime(6) NULL COMMENT '失效时间',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/REVOKED',
  `is_del` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标志；不用于删除审计',
  PRIMARY KEY (`id`),
  KEY `ix_39_1` (`entity_id`,`status`),
  KEY `ix_39_2` (`subject_id`,`status`),
  KEY `ix_39_3` (`auth_account_id`),
  CONSTRAINT `ck_39_1` CHECK (valid_until IS NULL OR valid_until>valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='法人自然人及子账号关系';

-- 40. 阶段5：实名与法人核验过程
CREATE TABLE `iam_verification_t` (
  `id` varchar(32) NOT NULL COMMENT '稳定主键，应用生成',
  `created_by` varchar(32) NULL COMMENT '创建操作账号；系统任务可空',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
  `updated_by` varchar(32) NULL COMMENT '最近更新操作账号',
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，写入显式更新',
  `version` bigint unsigned NOT NULL DEFAULT 1 COMMENT '乐观版本，每次有效修改加1',
  `target_type` varchar(16) NOT NULL COMMENT 'SUBJECT/LEGAL_ENTITY',
  `target_id` varchar(32) NOT NULL COMMENT '待核验主体',
  `provider_code` varchar(64) NOT NULL COMMENT '核验渠道',
  `provider_request_id` varchar(100) NOT NULL COMMENT '外部请求号',
  `status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/VERIFIED/REJECTED/EXPIRED',
  `result_code` varchar(100) NULL COMMENT '脱敏结果码',
  `evidence_ref` varchar(1000) NULL COMMENT '受控证据引用，不直接保存证件图片',
  `consent_ref` varchar(200) NULL COMMENT '必要的申请/授权记录引用',
  `requested_time` datetime(6) NOT NULL COMMENT '申请时间',
  `completed_time` datetime(6) NULL COMMENT '完成时间',
  `expires_at` datetime(6) NULL COMMENT '结果到期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_40_1` (`provider_code`,`provider_request_id`),
  KEY `ix_40_1` (`target_type`,`target_id`,`requested_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='实名与法人核验过程';

