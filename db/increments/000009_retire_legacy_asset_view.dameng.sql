-- 数据盘点旧资产兼容视图退役。适用于广电实际运行的达梦模式 "baseline_beijing_gd"。
-- 旧结构须为 da_asset_t 普通视图，唯一基础对象是同模式 da_catalog_t，asset_type 固定为 catalog。
-- 执行人先单独执行前置查询，核对部署绑定及对象标识的实际大小写、类型和定义。
-- 核对全实例可见的依赖目录、同义词、程序、触发器、已登记配置和外部使用者，存在引用或权限不足即停止。
-- 将实际 GET_DDL、模式属主、视图与列注释、视图及列授权、目录表结构摘要和业务指纹保存在受限位置。
-- 达梦 DDL 可能隐式提交，RESTRICT 默认行为受 DROP_CASCADE_VIEW 参数影响，仍须独立核查依赖。
-- 回退按受限原始定义、注释及授权恢复该视图，然后逐项回读。恢复材料不写入普通日志或版本库。

-- 阶段 1，预期当前模式与实际绑定一致，并有一个 VIEW 和一个 TABLE，入向依赖为零行。
SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS target_schema FROM DUAL;
SELECT OBJECT_NAME, OBJECT_TYPE
FROM DBA_OBJECTS
WHERE OWNER = 'baseline_beijing_gd' AND OBJECT_NAME IN ('da_asset_t', 'da_catalog_t')
  AND OBJECT_TYPE IN ('VIEW', 'TABLE')
ORDER BY OBJECT_NAME;
SELECT DBMS_METADATA.GET_DDL('VIEW', 'da_asset_t', 'baseline_beijing_gd') FROM DUAL;
SELECT OWNER, NAME, TYPE
FROM DBA_DEPENDENCIES
WHERE REFERENCED_OWNER = 'baseline_beijing_gd' AND REFERENCED_NAME = 'da_asset_t';

-- 阶段 2，经前置审核后执行。失败停止，先回读对象状态。
DROP VIEW "baseline_beijing_gd"."da_asset_t" RESTRICT;

-- 阶段 3，预期只剩下 da_catalog_t 的 TABLE，并对照目录表业务指纹。
SELECT OBJECT_NAME, OBJECT_TYPE
FROM DBA_OBJECTS
WHERE OWNER = 'baseline_beijing_gd' AND OBJECT_NAME IN ('da_asset_t', 'da_catalog_t')
  AND OBJECT_TYPE IN ('VIEW', 'TABLE')
ORDER BY OBJECT_NAME;
