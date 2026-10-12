-- 当前完整结构基线：仅适用于新空库，不含业务数据、账号或授权。
-- 不得直接在现有数据库执行；现有库变更须单独审阅差异并备份。
SET NAMES utf8mb4;
CREATE DATABASE `demo_ingest_source` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `demo_ingest_source`;
SET FOREIGN_KEY_CHECKS=0;

-- TABLE demo_customer
CREATE TABLE `demo_customer` (
  `customer_id` bigint NOT NULL COMMENT '演示客户编号',
  `customer_name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生成的演示客户名称',
  `city` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '演示所在城市',
  `amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '演示累计金额，单位元',
  `updated_at` datetime(3) NOT NULL COMMENT '记录最后修改时间，精确到毫秒',
  PRIMARY KEY (`customer_id`),
  KEY `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演示接入验证-生成客户记录';

SET FOREIGN_KEY_CHECKS=1;
