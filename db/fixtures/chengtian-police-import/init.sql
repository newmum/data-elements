-- 当前完整结构基线：仅适用于新空库，不含业务数据、账号或授权。
-- 不得直接在现有数据库执行；现有库变更须单独审阅差异并备份。
SET NAMES utf8mb4;
CREATE DATABASE `ct_police_copy_targets` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `ct_police_copy_targets`;
SET FOREIGN_KEY_CHECKS=0;

-- TABLE ga_021b064186515420
CREATE TABLE `ga_021b064186515420` (
  `DICT_ID` varchar(50) DEFAULT NULL COMMENT '行业类型',
  `SYSTEM_NAME` varchar(500) DEFAULT NULL COMMENT '系统名称',
  `BZ_ID` varchar(50) DEFAULT NULL COMMENT '主键',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`ODS_UUID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-行业场所类型表';

-- TABLE ga_0651abc65a585e82
CREATE TABLE `ga_0651abc65a585e82` (
  `jbh` varchar(32) NOT NULL COMMENT '接警事件唯一标识（接警编号）',
  `fxasjsj` datetime(6) NOT NULL COMMENT '报案人提供的案件发现时间',
  `fxasjdd` varchar(200) NOT NULL COMMENT '报案人提供的案件发现地点',
  `fxasjdd_xzqhdm` varchar(12) NOT NULL COMMENT '发现地点的行政区划编码',
  `fxasjdd_dzmc` varchar(200) NOT NULL COMMENT '发现地点的具体地址描述',
  `slsj` datetime(6) NOT NULL COMMENT '公安机关正式受理案件的时间',
  `sldw` varchar(50) NOT NULL COMMENT '受理案件的公安机关单位编码',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`jbh`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-接处警主信息表（存储接警核心业务信息）';

-- TABLE ga_2e173b8ddb9658d9
CREATE TABLE `ga_2e173b8ddb9658d9` (
  `education_level_code` varchar(16) NOT NULL COMMENT '学历编码',
  `education_level_name` varchar(64) NOT NULL COMMENT '学历名称',
  `enabled` bigint NOT NULL COMMENT '是否启用：1启用，0停用',
  `sort_no` bigint NOT NULL COMMENT '排序号',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '更新时间',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`education_level_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-演示字典表：学历层次';

-- TABLE ga_41b1f25aba0358df
CREATE TABLE `ga_41b1f25aba0358df` (
  `ID` varchar(255) NOT NULL COMMENT '主键',
  `YWLSHM` varchar(255) DEFAULT NULL COMMENT '业务流水号码',
  `CJCLDM` varchar(255) DEFAULT NULL COMMENT '预留字段1',
  `SCR_XM` varchar(255) DEFAULT NULL COMMENT '送车人姓名',
  `SCR_CYZJDM` varchar(255) DEFAULT NULL COMMENT '送车人常用证件代码',
  `SCR_CYZJDM_cn` varchar(255) DEFAULT NULL COMMENT '送车人常用证件代码翻译',
  `SCR_ZJHM` varchar(255) DEFAULT NULL COMMENT '送车人证件号码',
  `SCR_LXDH` varchar(255) DEFAULT NULL COMMENT '送车人联系电话',
  `SHC_SJ` datetime(6) DEFAULT NULL COMMENT '收车时间',
  `SHCR_XM` varchar(255) DEFAULT NULL COMMENT '收车人姓名',
  `QCR_XM` varchar(255) DEFAULT NULL COMMENT '取车人姓名',
  `QCR_LXDH` varchar(255) DEFAULT NULL COMMENT '取车人联系电话',
  `QCR_CYZJDM` varchar(255) DEFAULT NULL COMMENT '取车人常用证件代码',
  `QCR_CYZJDM_cn` varchar(255) DEFAULT NULL COMMENT '取车人常用证件代码翻译',
  `QCR_ZJHM` varchar(255) DEFAULT NULL COMMENT '取车人证件号码',
  `QC_SJ` datetime(6) DEFAULT NULL COMMENT '取车时间',
  `CYRY_NUM` varchar(255) DEFAULT NULL COMMENT '记录从业人员编码',
  `DATA_FROM` varchar(128) DEFAULT NULL COMMENT '预留字段2',
  `CREATE_TIME` datetime(6) DEFAULT NULL COMMENT '登记时间',
  `CSDM` varchar(255) DEFAULT NULL COMMENT '场所代码',
  `DWMC` varchar(255) DEFAULT NULL COMMENT '单位名称',
  `ZAGLDWBM` varchar(255) DEFAULT NULL COMMENT '治安管理单位编码',
  `ZAGLDWMC` varchar(255) DEFAULT NULL COMMENT '治安管理单位名称',
  `UPDATE_TIME` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `IS_DEL` varchar(128) DEFAULT NULL COMMENT '是否删除',
  `IS_DEL_cn` varchar(255) DEFAULT NULL COMMENT '是否删除翻译',
  `ODS_UUID` varchar(255) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-承接车辆交接信息';

-- TABLE ga_4b49f3a1b2665889
CREATE TABLE `ga_4b49f3a1b2665889` (
  `id` varchar(255) NOT NULL COMMENT '主键id',
  `entity_name` varchar(256) DEFAULT NULL COMMENT '市场主体名称',
  `entity_type` varchar(32) DEFAULT NULL COMMENT '类型',
  `credit_code` varchar(32) DEFAULT NULL COMMENT '统一社会信用代码',
  `address` varchar(512) DEFAULT NULL COMMENT '市场主体地址',
  `legal_person_name` varchar(16) DEFAULT NULL COMMENT '法定代表人姓名',
  `legal_person_phone` varchar(16) DEFAULT NULL COMMENT '法定代表人联系电话',
  `enterprise_status` varchar(32) DEFAULT NULL COMMENT '企业状态',
  `license_status` varchar(16) DEFAULT NULL COMMENT '许可证状态',
  `business_items` varchar(1024) DEFAULT NULL COMMENT '经营项目',
  `create_time` varchar(255) DEFAULT NULL COMMENT '创建时间',
  `update_time` varchar(255) DEFAULT NULL COMMENT '更新时间',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-福建省市监局餐饮店信息';

-- TABLE ga_50cc07189a8c5479
CREATE TABLE `ga_50cc07189a8c5479` (
  `jbh` varchar(32) NOT NULL COMMENT '接警事件唯一标识（接警编号）',
  `fxasjsj` datetime(6) NOT NULL COMMENT '报案人提供的案件发现时间',
  `fxasjdd` varchar(200) NOT NULL COMMENT '报案人提供的案件发现地点',
  `fxasjdd_xzqhdm` varchar(12) NOT NULL COMMENT '发现地点的行政区划编码',
  `fxasjdd_dzmc` varchar(200) NOT NULL COMMENT '发现地点的具体地址描述',
  `slsj` datetime(6) NOT NULL COMMENT '公安机关正式受理案件的时间',
  `sldw` varchar(50) NOT NULL COMMENT '受理案件的公安机关单位编码',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`jbh`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-接处警主信息表（存储接警核心业务信息）';

-- TABLE ga_843bee7297b1581f
CREATE TABLE `ga_843bee7297b1581f` (
  `category_code` varchar(32) NOT NULL COMMENT '业务类别字段',
  `category_name` varchar(80) NOT NULL COMMENT '业务类别名称',
  `item_code` varchar(32) NOT NULL COMMENT '关联字段编码',
  `item_name` varchar(100) NOT NULL COMMENT '字典中文描述',
  `enabled` bigint NOT NULL COMMENT '状态字段：1有效，0无效',
  `sort_no` bigint NOT NULL COMMENT '排序号',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`category_code`,`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-公安业务基础代码表';

-- TABLE ga_85835f6f0cd55c9d
CREATE TABLE `ga_85835f6f0cd55c9d` (
  `ID` varchar(40) NOT NULL COMMENT '主键id',
  `FDDBR_ZJHM` varchar(200) DEFAULT NULL COMMENT '法定代表人证件号码',
  `CSMC` varchar(200) DEFAULT NULL COMMENT '招牌名',
  `FDDBR_XJZD` longtext COMMENT '法定代表人现居住地',
  `CS_LXDH` varchar(200) DEFAULT NULL COMMENT '场所联系电话',
  `CS_XZQHDM` varchar(64) DEFAULT NULL COMMENT '场所行政区划代码',
  `CSFZR_XM` varchar(200) DEFAULT NULL COMMENT '场所负责人姓名',
  `CSFZR_SFZHM` varchar(200) DEFAULT NULL COMMENT '场所负责人证件号码',
  `CSFZR_LXDH` varchar(200) DEFAULT NULL COMMENT '场所负责人联系电话',
  `CSFZR_XJZD` longtext COMMENT '场所负责人现居住地',
  `BWFZR_XM` varchar(200) DEFAULT NULL COMMENT '保卫负责人姓名',
  `BWRY_SL` varchar(128) DEFAULT NULL COMMENT '保卫负责人数量',
  `BWBM_LXDH` varchar(200) DEFAULT NULL COMMENT '保卫负责人联系电话',
  `CS_MJPFM` varchar(64) DEFAULT NULL COMMENT '场所面积',
  `THBABH` varchar(64) DEFAULT NULL COMMENT '特行备案编号',
  `THBAJGMC` varchar(64) DEFAULT NULL COMMENT '特行备案机构名称',
  `HYJYXKZHM` varchar(64) DEFAULT NULL COMMENT '特种行业经营许可证号码',
  `HYJYXKZFZJGMC` longtext COMMENT '特种行业经营许可证发证机构名称',
  `CS_JYZTDM` varchar(64) DEFAULT NULL COMMENT '场所经营状态代码',
  `ZRMJ_XM` varchar(200) DEFAULT NULL COMMENT '责任民警姓名',
  `ZAGLDWBM` varchar(64) DEFAULT NULL COMMENT '治安管理单位编码',
  `CREATE_TIME` datetime(6) DEFAULT NULL COMMENT '记录生成时间',
  `DZBM` longtext COMMENT '标准地址代码',
  `SPJKSBPDZSF` varchar(500) DEFAULT NULL COMMENT '视频监控设备判断指示符',
  `XXCJSBPDZSF` varchar(500) DEFAULT NULL COMMENT '信息采集设备判断指示符',
  `XFSBPDZSF` varchar(500) DEFAULT NULL COMMENT '消防设备判断指示符',
  `CS_DZMC` longtext COMMENT '场所二维码地址名称',
  `ZXDHZB` varchar(64) DEFAULT NULL COMMENT '横坐标',
  `ZXDZZB` varchar(32) DEFAULT NULL COMMENT '纵坐标',
  `ZAGLDWMC` varchar(64) DEFAULT NULL COMMENT '治安管理单位编码',
  `CSDM` varchar(50) DEFAULT NULL COMMENT '场所代码',
  `XXZJBH` varchar(50) DEFAULT NULL COMMENT '信息主键编号',
  `DWMC` varchar(100) DEFAULT NULL COMMENT '单位名称',
  `ZCDZ` varchar(300) DEFAULT NULL COMMENT '注册地址',
  `TYSHXYDM` varchar(50) DEFAULT NULL COMMENT '统一社会信用代码',
  `FDDBR_XM` varchar(200) DEFAULT NULL COMMENT '法定代表人姓名',
  `FDDBR_LXDH` varchar(200) DEFAULT NULL COMMENT '法定代表人联系电话',
  `UPDATE_TIME` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `LGPCSFZR_LXDH` varchar(200) DEFAULT NULL COMMENT '列管派出所负责人联系电话',
  `LGPCSFZR_XM` varchar(200) DEFAULT NULL COMMENT '列管派出所负责人姓名',
  `ZRMJ_LXDH` varchar(200) DEFAULT NULL COMMENT '责任民警联系电话',
  `CS_KYRQ` datetime(6) DEFAULT NULL COMMENT '开业日期',
  `DJRQ` datetime(6) DEFAULT NULL COMMENT '日期时间',
  `CS_XDZ` varchar(300) DEFAULT NULL COMMENT '场所现地址',
  `IS_COMPLETE` varchar(128) DEFAULT NULL COMMENT '备案信息是否完整',
  `IS_CARD` varchar(128) DEFAULT NULL COMMENT '是否有证',
  `YLTHCSLXDM` varchar(64) DEFAULT NULL COMMENT '娱乐特行场所类型代码',
  `ZRMJ_NUM` varchar(64) DEFAULT NULL COMMENT '责任民警警号',
  `AUDIT_ID` varchar(64) DEFAULT NULL COMMENT '审核ID',
  `CS_ZB` varchar(64) DEFAULT NULL COMMENT '场所坐标',
  `DATA_FROM` varchar(128) DEFAULT NULL COMMENT '预留字段',
  `SH_STATUS` varchar(128) DEFAULT NULL COMMENT '企业审核状态',
  `USERNAME` varchar(64) DEFAULT NULL COMMENT '添加人员',
  `CS_XP` longtext COMMENT '场所照片',
  `FDDBR_XP` longtext COMMENT '法人照片',
  `CSFZRXP` varchar(1000) DEFAULT NULL COMMENT '负责人照片',
  `CS_JF` varchar(128) DEFAULT NULL COMMENT '场所积分',
  `SCANNING` varchar(10) DEFAULT NULL COMMENT '是否扫描企业坐标信息',
  `FDDBR_ZJLX` varchar(64) DEFAULT NULL COMMENT '法定代表人证件号类型',
  `IS_DEL` varchar(128) DEFAULT NULL COMMENT '是否删除',
  `CSFZR_ZJLX` varchar(255) DEFAULT NULL COMMENT '场所负责人证件号类型',
  `ISLGUPDATE` varchar(20) DEFAULT NULL COMMENT '是否更新旅馆',
  `FDDBR_DZBM` varchar(500) DEFAULT NULL COMMENT '法人居住地址编码',
  `CSFZR_DZBM` varchar(500) DEFAULT NULL COMMENT '负责人居住地址编码',
  `ZC_DZBM` varchar(500) DEFAULT NULL COMMENT '注册地址编码',
  `INDUSTRY_TYPE` varchar(32) DEFAULT NULL COMMENT '行业细类',
  `LGXJ` varchar(40) DEFAULT NULL COMMENT '旅馆星级',
  `LGCWS` varchar(128) DEFAULT NULL COMMENT '旅馆床位数',
  `LGFJS` varchar(128) DEFAULT NULL COMMENT '旅馆房间数',
  `IS_TOP` varchar(1) DEFAULT NULL COMMENT '是否置顶',
  `SUNIT_HY_SORT` varchar(300) DEFAULT NULL COMMENT '旅馆类型',
  `YLCS_TYPE` varchar(16) DEFAULT NULL COMMENT '娱乐场所类别',
  `SAFETY_LEVEL` varchar(16) DEFAULT NULL COMMENT '安全等级',
  `SAFETY_LEVEL_DICT` varchar(16) DEFAULT NULL COMMENT '定级理由字典值',
  `SAFETY_LEVEL_CONTENT` longtext COMMENT '定级理由',
  `OPERATOR` varchar(64) DEFAULT NULL COMMENT '操作人',
  `OPERATOR_TIME` datetime(6) DEFAULT NULL COMMENT '操作时间',
  `SITUATION` varchar(500) DEFAULT NULL COMMENT '其他情形',
  `OLD_SAFETY_LEVEL` varchar(16) DEFAULT NULL COMMENT '上次安全等级',
  `OLD_OPERATOR_TIME` datetime(6) DEFAULT NULL COMMENT '上次操作时间',
  `RECORD_ID` varchar(200) DEFAULT NULL COMMENT '备案id',
  `QPS_FJ_ZS` varchar(10) DEFAULT NULL COMMENT '棋牌室房间总数',
  `SRYY_ZW_ZS` varchar(10) DEFAULT NULL COMMENT '私人影院座位总数',
  `QPS_TYPE` varchar(1) DEFAULT NULL COMMENT '棋牌室场所类型',
  `JBYLJYCS_TYPE` varchar(1) DEFAULT NULL COMMENT '剧本娱乐经营场所类型',
  `PBJYLTYG_TYPE` varchar(1) DEFAULT NULL COMMENT '汉服恋爱等各类体验馆场所类型',
  `DRJSL` varchar(128) DEFAULT NULL COMMENT '电竞酒店单人间数量',
  `SRJSL` varchar(128) DEFAULT NULL COMMENT '电竞酒店双人间数量',
  `JSJTS` varchar(128) DEFAULT NULL COMMENT '电竞酒店计算机台数',
  `HDKRNRYSL` varchar(128) DEFAULT NULL COMMENT '核定可容纳人员数量',
  `SMYANQK` varchar(1) DEFAULT NULL COMMENT '电竞酒店扫描仪安装情况',
  `AFJKXTAZKJ` varchar(1) DEFAULT NULL COMMENT '电竞酒店安防监控系统安装情况',
  `AMCYSL` varchar(128) DEFAULT NULL COMMENT '按摩床椅数量',
  `AMSSL` varchar(128) DEFAULT NULL COMMENT '按摩师数量',
  `AQCKSL` varchar(128) DEFAULT NULL COMMENT '按摩场所安全出口数量',
  `GABABH` varchar(100) DEFAULT NULL COMMENT '按摩场所公安备案编号',
  `SXESSXSJYMS` varchar(1) DEFAULT NULL COMMENT '实行24小时经营模式',
  `SZQT` varchar(1) DEFAULT NULL COMMENT '设置前台',
  `ZRZS` varchar(1) DEFAULT NULL COMMENT '专人值守',
  `BAPBQK` varchar(1) DEFAULT NULL COMMENT '保安配备情况',
  `ZYBA` varchar(20) DEFAULT NULL COMMENT '专业保安',
  `ZPBA` varchar(20) DEFAULT NULL COMMENT '自聘保安',
  `JKTTSL` varchar(128) DEFAULT NULL COMMENT '监控探头数量',
  `JKSPKBCSC` varchar(20) DEFAULT NULL COMMENT '监控视频可保存时长',
  `CRKJKTT` varchar(20) DEFAULT NULL COMMENT '出入口监控探头',
  `DTQTJKTT` varchar(20) DEFAULT NULL COMMENT '大堂前台监控探头',
  `AQTDJKTT` varchar(20) DEFAULT NULL COMMENT '安全通道监控探头',
  `BX_FWJS` varchar(128) DEFAULT NULL COMMENT '包间数量',
  `BWFZRZJLX` varchar(4) DEFAULT NULL COMMENT '保卫负责人证件类型',
  `BWFZRZJHM` varchar(100) DEFAULT NULL COMMENT '保卫负责人证件号码',
  `BWFZRXJZD` varchar(100) DEFAULT NULL COMMENT '保卫负责人现居住地',
  `BWFZRXJZDBM` varchar(100) DEFAULT NULL COMMENT '保卫负责人现居住地编码',
  `AM_TYPE` varchar(1) DEFAULT NULL COMMENT '按摩服务场所类型',
  `IS_CULTURE_LICENSE` varchar(1) DEFAULT NULL COMMENT '是否有文化证',
  `RISK_LEVEL` varchar(1) DEFAULT NULL COMMENT '风险等级',
  `RISK_SCORE` varchar(128) DEFAULT NULL COMMENT '风险分数',
  `DDZXCXSJWX_TYPE` varchar(1) DEFAULT NULL COMMENT '电动自行车销售及维修场所类型',
  `OFFER_LODGING` varchar(10) DEFAULT NULL COMMENT '提供住宿',
  `CORP_GRADE` varchar(1) DEFAULT NULL COMMENT '企业等级',
  `FDDBR_ZJHLX` varchar(10) DEFAULT NULL COMMENT '法定代表人证件号类型',
  `WL_XSYD` varchar(20) DEFAULT NULL COMMENT '是否开通线上预定 ',
  `WL_XSYD_ZW` varchar(100) DEFAULT NULL COMMENT '是否开通线上预定中文',
  `SFHYJDF` varchar(20) DEFAULT NULL COMMENT '电竞酒店类型',
  `SFHYJDF_ZW` varchar(100) DEFAULT NULL COMMENT '电竞酒店类型中文',
  `DJ_FJS` varchar(20) DEFAULT NULL COMMENT '电竞房间数',
  `DJ_DNS` varchar(20) DEFAULT NULL COMMENT '电竞电脑数',
  `JB_TYPE` varchar(20) DEFAULT NULL COMMENT '酒吧类型',
  `YYJB_XL` varchar(20) DEFAULT NULL COMMENT '演艺酒吧细类',
  `TSJBZTFG` varchar(200) DEFAULT NULL COMMENT '特殊酒吧主题/风格',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-场所信息表';

-- TABLE ga_93c90b85b1475e41
CREATE TABLE `ga_93c90b85b1475e41` (
  `vehicle_id` varchar(64) NOT NULL COMMENT '车辆标识',
  `plate_no` varchar(64) NOT NULL COMMENT '号牌号码',
  `plate_color_code` varchar(64) NOT NULL COMMENT '号牌颜色编码',
  `vehicle_type_code` varchar(64) NOT NULL COMMENT '车辆类型编码',
  `brand` varchar(200) NOT NULL COMMENT '品牌',
  `model` varchar(200) NOT NULL COMMENT '型号',
  `vin` varchar(64) NOT NULL COMMENT '车辆识别代号',
  `engine_no` varchar(64) NOT NULL COMMENT '发动机号码',
  `owner_name` varchar(200) NOT NULL COMMENT '所有人姓名',
  `owner_cert_type_code` varchar(64) NOT NULL COMMENT '所有人证件类型编码',
  `owner_cert_no` varchar(64) NOT NULL COMMENT '所有人证件号',
  `owner_phone` varchar(64) DEFAULT NULL COMMENT '所有人电话',
  `registration_date` varchar(255) NOT NULL COMMENT '注册日期',
  `issue_date` varchar(255) NOT NULL COMMENT '发证日期',
  `inspection_valid_until` varchar(255) NOT NULL COMMENT '检验有效期至',
  `insurance_valid_until` varchar(255) NOT NULL COMMENT '保险有效期至',
  `use_nature_code` varchar(64) NOT NULL COMMENT '使用性质编码',
  `status_code` varchar(64) NOT NULL COMMENT '车辆状态编码',
  `manage_unit_code` varchar(64) NOT NULL COMMENT '管理单位编码',
  `parking_address` varchar(500) DEFAULT NULL COMMENT '停放地址',
  `source_system` varchar(200) NOT NULL COMMENT '来源系统',
  `created_at` varchar(255) NOT NULL COMMENT '创建时间',
  `updated_at` varchar(255) NOT NULL COMMENT '更新时间',
  `data_status` varchar(128) NOT NULL COMMENT '数据状态',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`vehicle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-车辆登记信息';

-- TABLE ga_978df9a5cc165e49
CREATE TABLE `ga_978df9a5cc165e49` (
  `case_status_code` varchar(16) NOT NULL COMMENT '案件状态编码',
  `case_status_name` varchar(64) NOT NULL COMMENT '案件状态名称',
  `enabled` decimal(5,0) NOT NULL COMMENT '是否启用：1启用，0停用',
  `sort_no` decimal(10,0) NOT NULL COMMENT '排序号',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '更新时间',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`case_status_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-演示字典表：案件办理状态';

-- TABLE ga_98405767512b5071
CREATE TABLE `ga_98405767512b5071` (
  `case_type_code` varchar(16) NOT NULL COMMENT '案件类别编码',
  `case_type_name` varchar(64) NOT NULL COMMENT '案件类别名称',
  `enabled` bigint NOT NULL COMMENT '是否启用：1启用，0停用',
  `remark` varchar(128) DEFAULT NULL COMMENT '备注',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`case_type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-案件业务类型代码表';

-- TABLE ga_9d2ead050c3f5c52
CREATE TABLE `ga_9d2ead050c3f5c52` (
  `id` varchar(255) NOT NULL COMMENT '主键id',
  `entity_name` varchar(256) DEFAULT NULL COMMENT '市场主体名称',
  `entity_type` varchar(32) DEFAULT NULL COMMENT '类型',
  `credit_code` varchar(32) DEFAULT NULL COMMENT '统一社会信用代码',
  `address` varchar(512) DEFAULT NULL COMMENT '市场主体地址',
  `legal_person_name` varchar(16) DEFAULT NULL COMMENT '法定代表人姓名',
  `legal_person_phone` varchar(16) DEFAULT NULL COMMENT '法定代表人联系电话',
  `license_status` varchar(16) DEFAULT NULL COMMENT '许可证状态',
  `enterprise_status` varchar(32) DEFAULT NULL COMMENT '企业状态',
  `product_info` varchar(1024) DEFAULT NULL COMMENT '产品信息',
  `create_time` varchar(255) DEFAULT NULL COMMENT '创建时间',
  `update_time` varchar(255) DEFAULT NULL COMMENT '更新时间',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-福建省市监局小作坊信息';

-- TABLE ga_a25ebbd420275ad2
CREATE TABLE `ga_a25ebbd420275ad2` (
  `ID` varchar(64) NOT NULL COMMENT '主键',
  `YWLSHM` varchar(64) DEFAULT NULL COMMENT '业务流水号码',
  `CJCLDM` varchar(64) DEFAULT NULL COMMENT '预留字段1',
  `SCR_XM` varchar(200) DEFAULT NULL COMMENT '送车人姓名',
  `SCR_CYZJDM` varchar(64) DEFAULT NULL COMMENT '送车人常用证件代码',
  `SCR_CYZJDM_cn` varchar(255) DEFAULT NULL COMMENT '送车人常用证件代码翻译',
  `SCR_ZJHM` varchar(128) DEFAULT NULL COMMENT '送车人证件号码',
  `SCR_LXDH` varchar(64) DEFAULT NULL COMMENT '送车人联系电话',
  `SHC_SJ` datetime(6) DEFAULT NULL COMMENT '收车时间',
  `SHCR_XM` varchar(64) DEFAULT NULL COMMENT '收车人姓名',
  `QCR_XM` varchar(200) DEFAULT NULL COMMENT '取车人姓名',
  `QCR_LXDH` varchar(64) DEFAULT NULL COMMENT '取车人联系电话',
  `QCR_CYZJDM` varchar(64) DEFAULT NULL COMMENT '取车人常用证件代码',
  `QCR_CYZJDM_cn` varchar(255) DEFAULT NULL COMMENT '取车人常用证件代码翻译',
  `QCR_ZJHM` varchar(64) DEFAULT NULL COMMENT '取车人证件号码',
  `QC_SJ` datetime(6) DEFAULT NULL COMMENT '取车时间',
  `CYRY_NUM` varchar(64) DEFAULT NULL COMMENT '记录从业人员编码',
  `DATA_FROM` varchar(128) DEFAULT NULL COMMENT '预留字段2',
  `CREATE_TIME` datetime(6) DEFAULT NULL COMMENT '登记时间',
  `CSDM` varchar(64) DEFAULT NULL COMMENT '场所代码',
  `DWMC` varchar(1000) DEFAULT NULL COMMENT '单位名称',
  `ZAGLDWBM` varchar(64) DEFAULT NULL COMMENT '治安管理单位编码',
  `ZAGLDWMC` varchar(100) DEFAULT NULL COMMENT '治安管理单位名称',
  `UPDATE_TIME` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `IS_DEL` varchar(128) DEFAULT NULL COMMENT '是否删除',
  `IS_DEL_cn` varchar(255) DEFAULT NULL COMMENT '是否删除翻译',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-承接车辆交接信息';

-- TABLE ga_ab2f30ea40855f12
CREATE TABLE `ga_ab2f30ea40855f12` (
  `category_code` varchar(32) NOT NULL COMMENT '业务类别字段',
  `category_name` varchar(80) NOT NULL COMMENT '业务类别名称',
  `item_code` varchar(32) NOT NULL COMMENT '关联字段编码',
  `item_name` varchar(100) NOT NULL COMMENT '字典中文描述',
  `enabled` bigint NOT NULL COMMENT '状态字段：1有效，0无效',
  `sort_no` bigint NOT NULL COMMENT '排序号',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`category_code`,`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-公安业务基础代码表';

-- TABLE ga_b679f7f4a4675fac
CREATE TABLE `ga_b679f7f4a4675fac` (
  `id` bigint NOT NULL COMMENT '主键',
  `record_no` varchar(32) NOT NULL COMMENT '登记编号',
  `person_name` varchar(64) NOT NULL COMMENT '人员姓名',
  `certificate_no` varchar(32) DEFAULT NULL COMMENT '证件号码（演示脱敏编号）',
  `gender_code` varchar(8) NOT NULL COMMENT '【字典关联】dict_gender.gender_code',
  `case_type_code` varchar(16) NOT NULL COMMENT '【字典关联】dict_case_type.case_type_code',
  `education_level_code` varchar(16) NOT NULL COMMENT '【字典关联】demo_dict_education_level.education_level_code',
  `case_status_code` varchar(16) NOT NULL COMMENT '【字典关联】demo_dict_case_status.case_status_code',
  `source_certificate_type` varchar(32) DEFAULT NULL COMMENT '【统一格式演示】建议强制对标：证件类型',
  `source_marital_status` varchar(32) DEFAULT NULL COMMENT '【统一格式演示】建议强制对标：婚姻状况',
  `source_occupation_text` varchar(64) DEFAULT NULL COMMENT '【统一格式演示】建议强制对标：职业类别',
  `case_no` varchar(32) DEFAULT NULL COMMENT '案件编号',
  `case_title` varchar(200) DEFAULT NULL COMMENT '案件标题',
  `occurred_at` datetime(6) DEFAULT NULL COMMENT '案事件发生时间',
  `reported_at` datetime(6) DEFAULT NULL COMMENT '登记上报时间',
  `risk_level` bigint DEFAULT NULL COMMENT '风险等级：1低，2中，3高',
  `phone_no` varchar(32) DEFAULT NULL COMMENT '联系电话',
  `domicile_address` varchar(255) DEFAULT NULL COMMENT '户籍地址',
  `residence_address` varchar(255) DEFAULT NULL COMMENT '现住地址',
  `handler_unit` varchar(128) DEFAULT NULL COMMENT '承办单位',
  `handler_name` varchar(64) DEFAULT NULL COMMENT '承办人',
  `org_code` varchar(32) DEFAULT NULL COMMENT '机构编码',
  `source_system` varchar(128) DEFAULT NULL COMMENT '来源系统',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '更新时间',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-演示业务表：人员涉案登记（4个字典关联、3个统一格式字段，未登记）';

-- TABLE ga_b73e75c135a0545a
CREATE TABLE `ga_b73e75c135a0545a` (
  `case_status_code` varchar(16) NOT NULL COMMENT '案件状态编码',
  `case_status_name` varchar(64) NOT NULL COMMENT '案件状态名称',
  `enabled` bigint NOT NULL COMMENT '是否启用：1启用，0停用',
  `sort_no` bigint NOT NULL COMMENT '排序号',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '更新时间',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`case_status_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-演示字典表：案件办理状态';

-- TABLE ga_bec35a6e27b35b3c
CREATE TABLE `ga_bec35a6e27b35b3c` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `ar` bigint NOT NULL COMMENT '国籍（地区）',
  `cety` bigint NOT NULL COMMENT '身份证件类型',
  `ceco` varchar(18) NOT NULL COMMENT '身份证件代码',
  `xm` varchar(3) NOT NULL COMMENT '姓名',
  `staticsDay` datetime(6) NOT NULL COMMENT '统计日期',
  `imtm` varchar(19) NOT NULL COMMENT '本地入库时间',
  `tags` varchar(11) NOT NULL COMMENT '标签',
  `lstArriDay` datetime(6) NOT NULL COMMENT '首次感知到厦',
  `arzwm` varchar(2) NOT NULL COMMENT '国家地区',
  `cetyzwm` varchar(5) NOT NULL COMMENT '证件类型',
  `score` varchar(128) NOT NULL COMMENT '分数',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-福建省当前在厦当日流入信息';

-- TABLE ga_c5d51d1e4e88594f
CREATE TABLE `ga_c5d51d1e4e88594f` (
  `CSDM` varchar(64) DEFAULT NULL COMMENT '场所代码',
  `YLCSLXDM` varchar(64) DEFAULT NULL COMMENT '娱乐场所类型代码',
  `YLCSLXDM_cn` varchar(255) DEFAULT NULL COMMENT '娱乐场所类型代码翻译',
  `HDXFZ_SL` varchar(128) DEFAULT NULL COMMENT '核定消费者数量',
  `BX_FWJS` varchar(128) DEFAULT NULL COMMENT '包厢房屋间数',
  `YLCSZAJBDM` varchar(64) DEFAULT NULL COMMENT '娱乐场所治安级别代码',
  `YLXMMC` varchar(1000) DEFAULT NULL COMMENT '娱乐项目名称',
  `UPDATE_TIME` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `CREATE_TIME` datetime(6) DEFAULT NULL COMMENT '登记时间',
  `DWMC` varchar(64) DEFAULT NULL COMMENT '单位名称',
  `ZAGLDWBM` varchar(64) DEFAULT NULL COMMENT '治安管理单位编码',
  `ZAGLDWMC` varchar(100) DEFAULT NULL COMMENT '治安管理单位名称',
  `XXZJBH` varchar(20) DEFAULT NULL COMMENT '信息主键编号(旧)',
  `JYFW` varchar(500) DEFAULT NULL COMMENT '预留字段1',
  `YLJYXKZDJHM` varchar(200) DEFAULT NULL COMMENT '预留字段2',
  `YLYYXKZDJRQ` datetime(6) DEFAULT NULL COMMENT '预留字段3',
  `YYZZDJRQ` datetime(6) DEFAULT NULL COMMENT '预留字段4',
  `CSLX` varchar(10) DEFAULT NULL COMMENT '是否游戏游艺场所',
  `CSLX_cn` varchar(255) DEFAULT NULL COMMENT '是否游戏游艺场所翻译',
  `YXJLX_SL` varchar(4000) DEFAULT NULL COMMENT '游戏机类型数量',
  `ID` varchar(200) NOT NULL COMMENT '主键id',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-娱乐场所附加信息表';

-- TABLE ga_c738fcb703b55741
CREATE TABLE `ga_c738fcb703b55741` (
  `gender_code` varchar(8) NOT NULL COMMENT '性别编码',
  `gender_name` varchar(32) NOT NULL COMMENT '性别名称',
  `enabled` bigint NOT NULL COMMENT '是否启用：1启用，0停用',
  `sort_no` bigint NOT NULL COMMENT 'sort_no',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`gender_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-人员性别代码表';

-- TABLE ga_d533fc3647b05faf
CREATE TABLE `ga_d533fc3647b05faf` (
  `ID` varchar(64) NOT NULL COMMENT '主键',
  `DAN_YAO_ID` varchar(255) DEFAULT NULL COMMENT '弹药信息主键',
  `RU_KU_TIME` datetime(6) DEFAULT NULL COMMENT '入库时间',
  `RU_LU_RENYUAN` varchar(255) DEFAULT NULL COMMENT '登入人员',
  `RU_KU_SHULIANG` varchar(255) DEFAULT NULL COMMENT '入库数量',
  `CREATE_TIME` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-弹药入库信息表';

-- TABLE ga_d96d7223953c5744
CREATE TABLE `ga_d96d7223953c5744` (
  `id` bigint NOT NULL COMMENT '主键',
  `record_no` varchar(32) NOT NULL COMMENT '登记编号',
  `person_name` varchar(64) NOT NULL COMMENT '人员姓名',
  `certificate_no` varchar(32) DEFAULT NULL COMMENT '证件号码（演示脱敏编号）',
  `gender_code` varchar(8) NOT NULL COMMENT '【字典关联】dict_gender.gender_code',
  `case_type_code` varchar(16) NOT NULL COMMENT '【字典关联】dict_case_type.case_type_code',
  `education_level_code` varchar(16) NOT NULL COMMENT '【字典关联】demo_dict_education_level.education_level_code',
  `case_status_code` varchar(16) NOT NULL COMMENT '【字典关联】demo_dict_case_status.case_status_code',
  `source_certificate_type` varchar(32) DEFAULT NULL COMMENT '【统一格式演示】建议强制对标：证件类型',
  `source_marital_status` varchar(32) DEFAULT NULL COMMENT '【统一格式演示】建议强制对标：婚姻状况',
  `source_occupation_text` varchar(64) DEFAULT NULL COMMENT '【统一格式演示】建议强制对标：职业类别',
  `case_no` varchar(32) DEFAULT NULL COMMENT '案件编号',
  `case_title` varchar(200) DEFAULT NULL COMMENT '案件标题',
  `occurred_at` datetime(6) DEFAULT NULL COMMENT '案事件发生时间',
  `reported_at` datetime(6) DEFAULT NULL COMMENT '登记上报时间',
  `risk_level` bigint DEFAULT NULL COMMENT '风险等级：1低，2中，3高',
  `phone_no` varchar(32) DEFAULT NULL COMMENT '联系电话',
  `domicile_address` varchar(255) DEFAULT NULL COMMENT '户籍地址',
  `residence_address` varchar(255) DEFAULT NULL COMMENT '现住地址',
  `handler_unit` varchar(128) DEFAULT NULL COMMENT '承办单位',
  `handler_name` varchar(64) DEFAULT NULL COMMENT '承办人',
  `org_code` varchar(32) DEFAULT NULL COMMENT '机构编码',
  `source_system` varchar(128) DEFAULT NULL COMMENT '来源系统',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '更新时间',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-演示业务表：人员涉案登记（4个字典关联、3个统一格式字段，未登记）';

-- TABLE ga_da48ff0d7c075948
CREATE TABLE `ga_da48ff0d7c075948` (
  `ROWKEY` varchar(800) NOT NULL COMMENT '系统唯一标识',
  `TIME` datetime(6) DEFAULT NULL COMMENT '系统数据更新时间戳',
  `SIRC_ACTION` longtext COMMENT '系统操作标识，仅表示公共数据汇聚共享平台上最近一次的状态，不作为在目的库中的操作类型标识。add：新增数据，update：更新数据，delete：逻辑删除',
  `SIRC_UPDATETIMESTAMP` datetime(6) DEFAULT NULL COMMENT '每次更新数据时，该字段都会更新为当前时间',
  `SIRC_CREATETIMESTAMP` datetime(6) DEFAULT NULL COMMENT '每次插入数据时，该字段都会设置为当前时间',
  `SIRC_PUSHTIME` datetime(6) DEFAULT NULL COMMENT '公共数据汇聚共享平台推送数据的时间',
  `SIRC_OPERATION_COUNT` varchar(128) DEFAULT NULL COMMENT '公共数据汇聚共享平台当日对本条数据的操作次数',
  `SIRC_ROWKEY_TIME` datetime(6) DEFAULT NULL COMMENT '汇聚平台首次接入数据日期',
  `RESCD` varchar(200) DEFAULT NULL COMMENT '水库编码',
  `GOV_DUTY` longtext COMMENT '政府责任人',
  `GOV_DUTY_UNIT` longtext COMMENT '政府责任人工作单位',
  `GOV_DUTY_POST` longtext COMMENT '政府责任人职务',
  `GOV_DUTY_LINK` longtext COMMENT '政府责任人联系方式',
  `AUT_DUTY` longtext COMMENT '主管部门责任人',
  `AUT_DUTY_UNIT` longtext COMMENT '主管部门责任人工作单位',
  `AUT_DUTY_POST` longtext COMMENT '主管部门责任人职务',
  `AUT_DUTY_LINK` longtext COMMENT '主管部门责任人联系方式',
  `MAN_DUTY` longtext COMMENT '管理单位责任人',
  `MAN_DUTY_UNIT` varchar(800) DEFAULT NULL COMMENT '管理单位责任人工作单位',
  `MAN_DUTY_POST` varchar(800) DEFAULT NULL COMMENT '管理单位责任人职务',
  `MAN_DUTY_LINK` varchar(800) DEFAULT NULL COMMENT '管理单位责任人联系方式',
  `ADM_DUTY` varchar(800) DEFAULT NULL COMMENT '行政责任人',
  `ADM_DUTY_UNIT` varchar(800) DEFAULT NULL COMMENT '行政责任人工作单位',
  `ADM_DUTY_POST` varchar(800) DEFAULT NULL COMMENT '行政责任人职务',
  `ADM_DUTY_LINK` varchar(800) DEFAULT NULL COMMENT '行政责任人联系方式',
  `TEC_DUTY` varchar(800) DEFAULT NULL COMMENT '技术责任人',
  `TEC_DUTY_UNIT` varchar(800) DEFAULT NULL COMMENT '技术责任人工作单位',
  `TEC_DUTY_POST` varchar(800) DEFAULT NULL COMMENT '技术责任人职务',
  `TEC_DUTY_LINK` varchar(800) DEFAULT NULL COMMENT '技术责任人联系方式',
  `PAT_DUTY` varchar(800) DEFAULT NULL COMMENT '巡查负责人',
  `PAT_DUTY_UNIT` varchar(800) DEFAULT NULL COMMENT '巡查负责人工作单位',
  `PAT_DUTY_POST` varchar(800) DEFAULT NULL COMMENT '巡查负责人职务',
  `PAT_DUTY_LINK` varchar(800) DEFAULT NULL COMMENT '巡查负责人联系方式',
  `YEAR` varchar(60) DEFAULT NULL COMMENT '年份',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  `__copy_key_hash` binary(32) GENERATED ALWAYS AS (unhex(sha2(`ROWKEY`,256))) STORED COMMENT '原业务主键完整值的SHA256索引键，仅用于独立实验库唯一性',
  UNIQUE KEY `ux_copy_business_key_hash` (`__copy_key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-水库大坝安全责任人信息';

-- TABLE ga_ea41ad237a1c5172
CREATE TABLE `ga_ea41ad237a1c5172` (
  `ROWKEY` varchar(800) NOT NULL COMMENT '系统唯一标识',
  `TIME` datetime(6) DEFAULT NULL COMMENT '系统数据更新时间戳',
  `SIRC_ACTION` varchar(800) DEFAULT NULL COMMENT '系统操作标识，仅表示公共数据汇聚共享平台上最近一次的状态，不作为在目的库中的操作类型标识。add：新增数据，update：更新数据，delete：逻辑删除',
  `SIRC_UPDATETIMESTAMP` datetime(6) DEFAULT NULL COMMENT '每次更新数据时，该字段都会更新为当前时间',
  `SIRC_CREATETIMESTAMP` datetime(6) DEFAULT NULL COMMENT '每次插入数据时，该字段都会设置为当前时间',
  `SIRC_PUSHTIME` datetime(6) DEFAULT NULL COMMENT '公共数据汇聚共享平台推送数据的时间',
  `SIRC_OPERATION_COUNT` varchar(128) DEFAULT NULL COMMENT '公共数据汇聚共享平台当日对本条数据的操作次数',
  `SIRC_ROWKEY_TIME` datetime(6) DEFAULT NULL COMMENT '汇聚平台首次接入数据日期',
  `SIRC_PROBLEM` varchar(4000) DEFAULT NULL COMMENT '问题描述',
  `ENTERPRISE_ID` varchar(200) DEFAULT NULL COMMENT '项目（法人）单位信息主键',
  `VALIDITY_FLAG` varchar(8) DEFAULT NULL COMMENT '有效标识',
  `VALIDITY_FLAG_cn` varchar(255) DEFAULT NULL COMMENT '有效标识翻译',
  `CHINA_FOREIGN_SHARE_RATIO` varchar(24) DEFAULT NULL COMMENT '项目单位中外方各股东及持股比例是否与项目资本金相同',
  `CHINA_FOREIGN_SHARE_RATIO_cn` varchar(255) DEFAULT NULL COMMENT '项目单位中外方各股东及持股比例是否与项目资本金相同翻译',
  `ENTERPRISE_NATURE` varchar(24) DEFAULT NULL COMMENT '项目单位性质',
  `CONTACT_PHONE` varchar(64) DEFAULT NULL COMMENT '联系手机',
  `CONTACT_TEL` varchar(128) DEFAULT NULL COMMENT '联系电话',
  `CONTACT_EMAIL` varchar(256) DEFAULT NULL COMMENT '联系人邮箱',
  `ENTERPRISE_PLACE` varchar(1024) DEFAULT NULL COMMENT '项目单位注册地址',
  `CORRESPONDENCE_ADDRESS` varchar(1024) DEFAULT NULL COMMENT '通讯地址',
  `BUSINESS_SCOPE` longtext COMMENT '主要经营范围',
  `ENTERPRISE_NAME` varchar(1024) DEFAULT NULL COMMENT '项目（法人）单位',
  `PROJECT_CODE` varchar(128) DEFAULT NULL COMMENT '项目代码',
  `PROJECT_CODE_cn` varchar(255) DEFAULT NULL COMMENT '项目代码翻译',
  `LEREP_CERTTYPE` varchar(24) DEFAULT NULL COMMENT '项目法人证照类型',
  `LEREP_CERTTYPE_cn` varchar(255) DEFAULT NULL COMMENT '项目法人证照类型翻译',
  `LEREP_CERTNO` varchar(200) DEFAULT NULL COMMENT '项目法人证照号码',
  `LEREP_CERTNO_cn` varchar(255) DEFAULT NULL COMMENT '项目法人证照号码翻译',
  `CONTACT_NAME` varchar(800) DEFAULT NULL COMMENT '联系人名称',
  `CONTACT_FAX` varchar(64) DEFAULT NULL COMMENT '传真',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  `__copy_key_hash` binary(32) GENERATED ALWAYS AS (unhex(sha2(`ROWKEY`,256))) STORED COMMENT '原业务主键完整值的SHA256索引键，仅用于独立实验库唯一性',
  UNIQUE KEY `ux_copy_business_key_hash` (`__copy_key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-投资项目监管地方项目法人单位信息表';

-- TABLE ga_efa051d23f7150e7
CREATE TABLE `ga_efa051d23f7150e7` (
  `case_category_code` varchar(2) NOT NULL COMMENT '案件类别编码',
  `case_category_name` varchar(64) NOT NULL COMMENT '案件类别名称',
  `enabled` bigint NOT NULL COMMENT '是否启用：1启用，0停用',
  `sort_no` bigint NOT NULL COMMENT '排序号',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`case_category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-案件类别代码表';

-- TABLE ga_f1d859d29f2c5de2
CREATE TABLE `ga_f1d859d29f2c5de2` (
  `case_category_code` varchar(2) NOT NULL COMMENT '案件类别编码',
  `case_category_name` varchar(64) NOT NULL COMMENT '案件类别名称',
  `enabled` bigint NOT NULL COMMENT '是否启用：1启用，0停用',
  `sort_no` bigint NOT NULL COMMENT '排序号',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `ODS_UUID` varchar(32) NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime(6) NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime(6) NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`case_category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公安迁入实验-案件类别代码表';

SET FOREIGN_KEY_CHECKS=1;
