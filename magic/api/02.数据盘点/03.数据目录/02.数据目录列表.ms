{
  "properties": {},
  "id": "datapd_catalog_list_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "02.数据目录列表",
  "createTime": null,
  "updateTime": 1787153759182,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"85368b91972c4a35b83b8fa73e34e740\",\n  \"pageNum\": 1,\n  \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"tid\": \"85368b91972c4a35b83b8fa73e34e740\",\n  \"assetType\": \"catalog\",\n  \"catalogName\": \"生成目录信息(sys_gen_info)\",\n  \"dbId\": \"38ef1b566eb4477ebfc07b4c0c8aa973\",\n  \"sourceTableId\": \"fb64c63470c14ee286a29fcea82a259c\"\n}",
  "description": "数据目录资产列表兼容接口（POST /dst/catalog/list）。读取 da_asset_t 与 da_prop_t 中的目录资产，兼容个人中心、数据超市和文件目录同步中对旧 /dst/catalog/list 的 import；请求示例使用当前库中真实 catalog tid；使用请求参数 pageNum、pageSize、tid。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.数据目录列表
// 路由：POST /dst/catalog/list
// 业务说明：数据目录资产列表兼容接口（POST /dst/catalog/list）。读取 da_asset_t 与 da_prop_t 中的目录资产，兼容个人中心、数据超市和文件目录同步中对旧 /dst/catalog/list 的 import；请求示例使用当前库中真实 catalog tid；使用请求参数 pageNum、pageSize、tid。
/*
 * 数据目录资产列表兼容接口。
 *
 * 旧个人中心、数据超市浏览和目录同步脚本会 import @post:/dst/catalog/list。
 * 当前登记主链路已经迁移到 db_datasource_t / db_table_t，但目录资产仍保留在 da_asset_t + da_prop_t，
 * 因此这里补回只读兼容层，避免登录后门户统计因为 import 缺失而失败。
 */
var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var sqlValue = (value) => "'" + text(value).replace("'", "''") + "'"

var pageNum = body == null || body.pageNum == null ? 1 : body.pageNum::int
var pageSize = body == null || body.pageSize == null ? 1000 : body.pageSize::int
var offset = pageNum <= 1 ? 0 : (pageNum - 1) * pageSize
var tid = body == null ? null : body.tid

var whereSql = " where ifnull(a.is_del, 0) = 0 "
if (!blank(tid)) {
    // 指定 tid 时用于数据超市浏览接口读取单条目录详情。
    whereSql = whereSql + " and a.tid = " + sqlValue(tid)
}

var propExpr = (name, alias) => {
    return "(select p.prop_value from da_prop_t p where p.parent_id = a.tid and ifnull(p.is_del, 0) = 0 and p.prop_name = '" + name + "' limit 1) as " + alias
}

var sql = "select " +
    "a.tid as tid, a.tid as id, a.tenant_id as tenantId, 'catalog' as assetType, " +
    "a.asset_status as assetStatus, a.flow_status as flowStatus, " +
    "a.reg_time as regTime, a.created_time as createdTime, a.updated_time as updatedTime, " +
    propExpr("catalogName", "catalogName") + ", " +
    propExpr("catalogNameEn", "catalogNameEn") + ", " +
    propExpr("assetDesc", "assetDesc") + ", " +
    propExpr("dbId", "dbId") + ", " +
    propExpr("sourceTableId", "sourceTableId") + ", " +
    propExpr("sourceTableName", "sourceTableName") + ", " +
    propExpr("orgId", "orgId") + ", " +
    propExpr("manageUnit", "manageUnit") + ", " +
    propExpr("businessType", "businessType") + ", " +
    propExpr("dataSourceType", "dataSourceType") + ", " +
    propExpr("timestampField", "timestampField") + ", " +
    propExpr("dictionaryTableFlag", "dictionaryTableFlag") + ", " +
    propExpr("dictionaryProfile", "dictionaryProfile") + ", " +
    propExpr("dictionaryProfiles", "dictionaryProfiles") + ", " +
    propExpr("fieldGovernanceConfig", "fieldGovernanceConfig") + " " +
    "from da_catalog_t a " + whereSql +
    " order by a.updated_time desc, a.created_time desc limit " + offset + ", " + pageSize

var rows = db.select(sql)
for (item in rows) {
    // 旧调用方常按数据资产统一字段读取，这里补齐常用别名。
    item.asset_type = item.assetType
    item.asset_status = item.assetStatus
    item.flow_status = item.flowStatus
    item.created_time = item.createdTime
    item.updated_time = item.updatedTime
    item.reg_time = item.regTime
    item.viewCount = item.viewCount == null ? 0 : item.viewCount
}

return rows
