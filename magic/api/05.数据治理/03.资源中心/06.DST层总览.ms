{
  "properties": {},
  "id": "48f4dd83b6a943fbba6b252921c730f8",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "06.DST层总览",
  "createTime": 1782955345100,
  "updateTime": 1787153757484,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dst-info",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "资源中心 06：按当前组织树汇总 DST 层应用系统、数据源和数据表；涉及 rm_org_t、db_datasource_t、db_table_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.DST层总览
// 路由：GET /dwm/center/dst-info
// 业务说明：资源中心 06：按当前组织树汇总 DST 层应用系统、数据源和数据表；涉及 rm_org_t、db_datasource_t、db_table_t 等业务表。
import "@post:/sym/user/me" as me
import java.util.Map

/*
 * 资源中心 06：按当前组织树汇总 DST 层应用系统、数据源和数据表。
 *
 * 统计直接读取 sym_application_t、db_datasource_t、db_table_t，避免 ES 短暂
 * 不可用时拖垮资源中心首页。该接口只读，不修改资产或索引。
 */

var textOf = (value) => value == null ? '' : value + ''
var rowValue = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}

// 从 MySQL 业务表读取当前有效数据。
var statsRows = db.select("""
    SELECT d.org_id,
           COUNT(DISTINCT d.app_id) AS app_count,
           COUNT(DISTINCT d.tid) AS db_count,
           COUNT(DISTINCT t.tid) AS table_count
      FROM db_datasource_t d
      LEFT JOIN db_table_t t
        ON t.datasource_id = d.tid
       AND t.is_del = 0
       AND t.asset_status = 2
     WHERE d.is_del = 0
       AND d.asset_status IN (1, 2)
     GROUP BY d.org_id
""")
var statsByOrg = {}
for (row in statsRows) {
    var orgId = textOf(rowValue(row, ['orgId', 'org_id']))
    if (!orgId.isEmpty()) {
        statsByOrg[orgId] = {
            appCount: rowValue(row, ['appCount', 'app_count']),
            dbCount: rowValue(row, ['dbCount', 'db_count']),
            tableCount: rowValue(row, ['tableCount', 'table_count'])
        }
    }
}

var currentUser = me()
var rootSerial = rowValue(currentUser, ['orgRootSerialNumber', 'org_root_serial_number'])
// 从 MySQL 业务表读取当前有效数据。
var orgQuery = db.table('rm_org_t')
    .excludes('shortName', 'creditCode', 'standard', 'createId', 'updateId', 'createTime', 'updateTime', 'deleted')
    .where()
    .eq('deleted', '0')
if (rootSerial != null && !textOf(rootSerial).isEmpty()) {
    orgQuery.like('serial_number', textOf(rootSerial) + '%')
}
var orgList = orgQuery.orderBy('sort_num').select()

var result = []
var dstTableCount = 0
var dbCount = 0
for (org in orgList) {
    var orgId = textOf(rowValue(org, ['id']))
    var stats = statsByOrg[orgId]
    if (stats != null && (stats.appCount > 0 || stats.dbCount > 0 || stats.tableCount > 0)) {
        result.add({
            orgId: orgId,
            name: rowValue(org, ['name']),
            appCount: stats.appCount,
            dbCount: stats.dbCount,
            tableCount: stats.tableCount
        })
        dstTableCount = dstTableCount + stats.tableCount
        dbCount = dbCount + stats.dbCount
    }
}

// 返回当前业务分支的标准结果。
return {
    dstTableCount: dstTableCount,
    dbCount: dbCount,
    dst: result
}
