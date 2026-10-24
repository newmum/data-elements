-- 当前完整结构基线：仅适用于新空库，不含业务数据、账号或授权。
-- 不得直接在现有数据库执行；现有库变更须单独审阅差异并备份。
SET NAMES utf8mb4;
CREATE DATABASE `personaldb` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `personaldb`;
SET FOREIGN_KEY_CHECKS=0;

-- TABLE demo_dict_case_status
CREATE TABLE `demo_dict_case_status` (
  `case_status_code` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '案件状态编码',
  `case_status_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '案件状态名称',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1启用，0停用',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`case_status_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演示字典表：案件办理状态';

-- TABLE demo_dict_education_level
CREATE TABLE `demo_dict_education_level` (
  `education_level_code` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学历编码',
  `education_level_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学历名称',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1启用，0停用',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`education_level_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演示字典表：学历层次';

-- TABLE demo_multivalue_gender_20261003
CREATE TABLE `demo_multivalue_gender_20261003` (
  `id` bigint NOT NULL,
  `gender_code` varchar(64) NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- TABLE demo_person_case_intake_biz
CREATE TABLE `demo_person_case_intake_biz` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登记编号',
  `person_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '人员姓名',
  `certificate_no` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '证件号码（演示脱敏编号）',
  `gender_code` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '【字典关联】dict_gender.gender_code',
  `case_type_code` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '【字典关联】dict_case_type.case_type_code',
  `education_level_code` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '【字典关联】demo_dict_education_level.education_level_code',
  `case_status_code` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '【字典关联】demo_dict_case_status.case_status_code',
  `source_certificate_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '【统一格式演示】建议强制对标：证件类型',
  `source_marital_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '【统一格式演示】建议强制对标：婚姻状况',
  `source_occupation_text` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '【统一格式演示】建议强制对标：职业类别',
  `case_no` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '案件编号',
  `case_title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '案件标题',
  `occurred_at` datetime DEFAULT NULL COMMENT '案事件发生时间',
  `reported_at` datetime DEFAULT NULL COMMENT '登记上报时间',
  `risk_level` tinyint DEFAULT '0' COMMENT '风险等级：1低，2中，3高',
  `phone_no` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `domicile_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '户籍地址',
  `residence_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '现住地址',
  `handler_unit` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '承办单位',
  `handler_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '承办人',
  `org_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '机构编码',
  `source_system` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源系统',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_demo_person_case_record_no` (`record_no`),
  KEY `idx_demo_person_case_gender` (`gender_code`),
  KEY `idx_demo_person_case_type` (`case_type_code`),
  KEY `idx_demo_person_case_education` (`education_level_code`),
  KEY `idx_demo_person_case_status` (`case_status_code`),
  KEY `idx_demo_person_case_occurred_at` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演示业务表：人员涉案登记（4个字典关联、3个统一格式字段，未登记）';

-- TABLE dict_case_category_force_demo
CREATE TABLE `dict_case_category_force_demo` (
  `case_category_code` varchar(2) COLLATE utf8mb4_general_ci NOT NULL COMMENT '案件类别编码',
  `case_category_name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '案件类别名称',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1启用，0停用',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`case_category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件类别代码表';

-- TABLE dict_case_type
CREATE TABLE `dict_case_type` (
  `case_type_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '案件类别编码',
  `case_type_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '案件类别名称',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1启用，0停用',
  `remark` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`case_type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='案件业务类型代码表';

-- TABLE dict_common_code
CREATE TABLE `dict_common_code` (
  `category_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类别字段',
  `category_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类别名称',
  `item_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联字段编码',
  `item_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典中文描述',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '状态字段：1有效，0无效',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  PRIMARY KEY (`category_code`,`item_code`),
  KEY `idx_category_name` (`category_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公安业务基础代码表';

-- TABLE dict_gender
CREATE TABLE `dict_gender` (
  `gender_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '性别编码',
  `gender_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '性别名称',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1启用，0停用',
  `sort_no` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`gender_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员性别代码表';

-- TABLE jc_jcj_main
CREATE TABLE `jc_jcj_main` (
  `jbh` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '接警事件唯一标识（接警编号）',
  `fxasjsj` datetime NOT NULL COMMENT '报案人提供的案件发现时间',
  `fxasjdd` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '报案人提供的案件发现地点',
  `fxasjdd_xzqhdm` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '发现地点的行政区划编码',
  `fxasjdd_dzmc` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '发现地点的具体地址描述',
  `slsj` datetime NOT NULL COMMENT '公安机关正式受理案件的时间',
  `sldw` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '受理案件的公安机关单位编码',
  PRIMARY KEY (`jbh`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='接处警主信息表（存储接警核心业务信息）';

-- TABLE police_case_record
CREATE TABLE `police_case_record` (
  `case_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '案件编号',
  `case_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '案件编号展示',
  `case_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '案件名称',
  `case_type_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '案件类别编码',
  `case_category_code` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '案件类别代码',
  `case_status_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '案件状态编码',
  `person_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联人员编号',
  `org_code` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办案机构编码',
  `case_source_channel` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '案件来源渠道编码',
  `occur_time` datetime NOT NULL COMMENT '案发时间',
  `register_time` datetime NOT NULL COMMENT '受理时间',
  PRIMARY KEY (`case_id`),
  UNIQUE KEY `uk_case_no` (`case_no`),
  KEY `idx_case_type_code` (`case_type_code`),
  KEY `idx_case_status_code` (`case_status_code`),
  KEY `idx_register_time` (`register_time`),
  KEY `idx_case_category_code` (`case_category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='案件受立案信息表';

-- TABLE police_new_alarm_event
CREATE TABLE `police_new_alarm_event` (
  `alarm_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '告警 ID',
  `alarm_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '告警编号',
  `alarm_type_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '告警类型编码',
  `alarm_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '告警级别',
  `report_person` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上报人',
  `report_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上报联系电话',
  `alarm_address` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '告警地址',
  `receive_time` datetime DEFAULT NULL COMMENT '受理时间',
  `handle_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理状态	',
  PRIMARY KEY (`alarm_id`),
  UNIQUE KEY `uk_alarm_no` (`alarm_no`),
  KEY `idx_alarm_time` (`receive_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公安新增警情事件表-重新探查新增示例';

-- TABLE police_person_info
CREATE TABLE `police_person_info` (
  `person_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '人员编号',
  `person_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名',
  `sfzh` varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份证号码',
  `gender_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '性别编码',
  `ethnic_code` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '民族编码',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `address` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '户籍地址',
  `org_code` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '管辖机构编码',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`person_id`),
  UNIQUE KEY `uk_sfzh` (`sfzh`),
  KEY `idx_gender_code` (`gender_code`),
  KEY `idx_org_code` (`org_code`),
  KEY `idx_ethnic_code` (`ethnic_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实有人口基本信息表';

-- TABLE police_reexplore_new_case_clue
CREATE TABLE `police_reexplore_new_case_clue` (
  `clue_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '线索编号',
  `clue_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '线索单号',
  `clue_source_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '线索来源编码',
  `clue_content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '线索内容',
  `report_person` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '举报人',
  `report_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '举报电话',
  `receive_time` datetime NOT NULL COMMENT '接收时间',
  `handle_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '处置状态',
  PRIMARY KEY (`clue_id`),
  UNIQUE KEY `uk_police_reexplore_new_case_clue_no` (`clue_no`),
  KEY `idx_police_reexplore_new_case_clue_status` (`handle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公安重新探查新增线索表';

-- TABLE police_vehicle_info
CREATE TABLE `police_vehicle_info` (
  `vehicle_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车辆编号',
  `plate_no` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车牌号码',
  `owner_person_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车主人员编号',
  `vehicle_type_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车辆类型编码',
  `color_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '车身颜色编码',
  `register_time` datetime NOT NULL COMMENT '登记时间',
  PRIMARY KEY (`vehicle_id`),
  KEY `idx_owner_person_id` (`owner_person_id`),
  KEY `idx_vehicle_type_code` (`vehicle_type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公安车辆登记信息表';

-- TABLE proc_case_handle_flow
CREATE TABLE `proc_case_handle_flow` (
  `flow_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程编号',
  `case_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '案件编号',
  `node_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程节点编码',
  `node_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程节点名称',
  `handler_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理人姓名',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`flow_id`),
  KEY `idx_case_id` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公安案件办理过程表';

SET FOREIGN_KEY_CHECKS=1;
