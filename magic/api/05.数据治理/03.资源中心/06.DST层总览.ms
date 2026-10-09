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
import tenantRuntime
import dataScope

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

var tenantId = textOf(tenantRuntime.id())
if (tenantId == '') { exit 401, '当前租户无效' }
var scope = dataScope.dataSource()
var scopeMode = textOf(scope?.scope)
var scopeUserId = textOf(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId == '') {
    exit 403, '无法确定当前数据范围'
}
// 权限条件固定在 SQL 中，身份/部门/租户均绑定；空部门范围不返回数据。
var scopeOrgCount = scopeOrgIds.size()
if (scopeOrgCount == 0) { scopeOrgIds = [''] }

// 按数据源所属租户和角色范围一次聚合。
var statsRows = db.select("""
    SELECT d.org_id,
           COUNT(DISTINCT d.app_id) AS app_count,
           COUNT(DISTINCT d.tid) AS db_count,
           COUNT(DISTINCT t.tid) AS table_count
      FROM db_datasource_t d
      LEFT JOIN db_table_t t
        ON t.datasource_id = d.tid
       AND t.tenant_id = #{tenantId}
       AND t.is_del = 0
       AND t.asset_status = 2
     WHERE d.tenant_id = #{tenantId}
       AND d.is_del = 0
       AND (
           #{scopeMode}='ALL'
           OR (#{scopeMode}='OWNER' AND d.created_by=#{scopeUserId})
           OR (#{scopeMode}='ORG' AND #{scopeOrgCount}>0 AND d.org_id IN
               <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>)
       )
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
var rootSerial = textOf(rowValue(currentUser, ['orgRootSerialNumber', 'org_root_serial_number']))
var rootPattern = rootSerial + '%'
var orgList = db.select("""
    SELECT id, name FROM rm_org_t
     WHERE tenant_id=#{tenantId} AND deleted=0
       AND (#{rootSerial}='' OR serial_number LIKE #{rootPattern})
     ORDER BY sort_num
""")

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
