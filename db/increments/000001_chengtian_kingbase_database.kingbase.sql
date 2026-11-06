-- 澄天租户-新建人大金仓独立业务数据库（库级增量，不修改其他行业库）。
-- 适用起始结构：KingbaseES V8R6 Oracle 兼容实例中尚不存在 baseline_ct；
-- 源业务结构为当前 MySQL chengtian 的 161 表统一基线。
-- 目标范围：仅 baseline_ct.public；控制库 baseline 及其他行业库保持原位。
-- 执行顺序：
-- 1. 备份原 Nacos、运行包及 MySQL 一致性快照，确认目标数据库不存在。
-- 2. 使用已有建库权限连接该实例的维护库，单独执行以下库级 SQL，不能放在事务中。
-- 3. 切换连接到新 baseline_ct，确认 current_database() 与 UTF8；仅在空 public
--    执行 ../baseline_ct/init.sql，创建完整原生对象。不要在维护库执行该完整文件。
-- 4. 使用 tools/kingbase-db.py 的受控数据迁移/摘要校验流程；本文件不含业务 INSERT。
-- 5. 停机补齐最终源快照并验证，再切换专用 Nacos data-element_ct.yml。
-- 后置检查：161 表、575 索引、2 外键、20 更新时间触发器、49 函数；
--            tools/check-db.py 与 tools/export-db.py 核对实际绑定及完整结构。
-- 回退：未开放写入前可恢复原配置和原运行包；开放写入后必须先审查数据差异。
--       本文件不删除目标库，不创建账号，不授权，不包含凭据。

SELECT datname, pg_encoding_to_char(encoding) AS encoding
FROM pg_database WHERE datname = 'baseline_ct';
-- 前置查询必须为零行；非零时停止，禁止重建或覆盖已有数据库。
CREATE DATABASE baseline_ct ENCODING 'UTF8' TEMPLATE template0;
SELECT datname, pg_encoding_to_char(encoding) AS encoding
FROM pg_database WHERE datname = 'baseline_ct';
