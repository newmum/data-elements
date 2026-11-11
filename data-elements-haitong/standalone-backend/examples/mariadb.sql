CREATE DATABASE IF NOT EXISTS haitong_validation CHARACTER SET utf8mb4;
USE haitong_validation;

CREATE TABLE IF NOT EXISTS flow_source (
    record_id VARCHAR(40) NOT NULL PRIMARY KEY COMMENT '样例记录编号',
    amount DECIMAL(12,2) NOT NULL COMMENT '样例金额',
    status VARCHAR(20) NOT NULL COMMENT '来源状态文本'
) COMMENT '流程验证-输入记录';

CREATE TABLE IF NOT EXISTS flow_target (
    record_id VARCHAR(40) NOT NULL PRIMARY KEY COMMENT '通过质检的记录编号',
    amount DECIMAL(12,2) NOT NULL COMMENT '通过质检的非负金额',
    status VARCHAR(20) NOT NULL COMMENT '规范化后的业务状态',
    quality_status VARCHAR(10) NOT NULL COMMENT '质检结论，PASS 表示通过'
) COMMENT '流程验证-质检通过记录';

INSERT IGNORE INTO flow_source (record_id,amount,status) VALUES
    ('validation-01',12.50,'active'),
    ('validation-02',7.00,'pending'),
    ('validation-03',-2.00,'active'),
    ('validation-04',3.00,'unknown');
