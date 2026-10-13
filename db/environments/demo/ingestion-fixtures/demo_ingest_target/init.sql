-- 当前完整结构基线：仅适用于新空库，不含业务数据、账号或授权。
-- 不得直接在现有数据库执行；现有库变更须单独审阅差异并备份。
SET NAMES utf8mb4;
CREATE DATABASE `demo_ingest_target` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `demo_ingest_target`;
SET FOREIGN_KEY_CHECKS=0;

-- TABLE ods_codex_snapshot_sftp_5bc63309f3d1
CREATE TABLE `ods_codex_snapshot_sftp_5bc63309f3d1` (
  `person_id` varchar(4) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试人员编号',
  `person_name` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试人员名称',
  `region` varchar(5) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试地区代码',
  `ODS_UUID` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集成验证-登记快照SFTP物化';

-- TABLE ods_demo_customer
CREATE TABLE `ods_demo_customer` (
  `customer_id` bigint NOT NULL COMMENT '演示客户编号',
  `customer_name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生成的演示客户名称',
  `city` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '演示所在城市',
  `amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '演示累计金额，单位元',
  `updated_at` datetime NOT NULL COMMENT '记录最后修改时间',
  `ODS_UUID` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ODS主键',
  `ODS_RKSJ` datetime NOT NULL COMMENT 'ODS入库时间',
  `ODS_GXSJ` datetime NOT NULL COMMENT 'ODS更新时间',
  PRIMARY KEY (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演示接入验证-物化客户记录';

SET FOREIGN_KEY_CHECKS=1;
