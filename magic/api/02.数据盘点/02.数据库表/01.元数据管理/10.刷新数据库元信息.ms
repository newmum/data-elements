{
  "properties": {},
  "id": "metadata_refresh_db_info_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "10.刷新数据库元信息",
  "createTime": 1783639496542,
  "updateTime": 1787156377648,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/refreshDbInfo",
  "method": "GET",
  "parameters": [
    {
      "name": "tid",
      "value": "8b0a98de552b4bc68a203293fadfff33",
      "description": "数据库资产ID",
      "required": true,
      "dataType": "String",
      "type": null,
      "defaultValue": "",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "tableId",
      "description": "可选：只刷新指定表；tid 必须是该表所属数据源 ID",
      "required": false,
      "dataType": "String"
    },
    {
      "name": "dryRun",
      "description": "true 时仅探查，不写入元数据和搜索索引",
      "required": false,
      "dataType": "String"
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前登录租户和数据源角色范围刷新元数据概览。tid 指定数据源，tableId 可选且须归属该数据源；dryRun 仅返回探查结果。正式刷新只更新概览及数据库更新时间，提交后尝试更新资产索引；目标无权限或探查期间记录变化时拒绝写入。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableWrite
import log
import java.util.Objects
import '@get:/dst/database/metadata/dbInfo' as queryDatabaseInfo
import '@post:/dst/maintenance/refresh' as refreshAssetIndex

/**
 * GET /dst/database/metadata/refreshDbInfo 刷新当前租户内、角色可访问的资产概览。
 * tid 指定数据源；传 tableId 时仅探查并更新归属该数据源的单张登记表。
 * dryRun=true 只返回探查结果。正式刷新仅更新主表的概览和更新时间，
 * 提交后尝试刷新对应资产索引，返回表概览或带兼容字段名的数据源概览。
 */
var text = value => value == null ? '' : value + ''
var blank = value => text(value).trim() == ''
if (blank(tid)) {
    exit 400, 'tid 不能为空'
}
var tenantId = tenantRuntime.id()
if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录'
}

var sourceId = tid
var requestedTableId = tableId
var tableRefresh = !blank(requestedTableId)
var sourceFields = ['tid', 'org_id', 'created_by', 'updated_time']
var tableFields = ['tid', 'datasource_id', 'table_name', 'updated_time']
var active = {is_del: 0}
var sourcePermission = 'MENU:2081000000000000002'
var readActive = (table, id, columns) => {
    // 主键查询只取身份、归属和版本，读取与加锁使用同一套 JDBC 值转换。
    var lookup = tableRead.from(table, 'asset', tenantId)
    var key = tableRead.column('asset', 'tid', 'TEXT')
    lookup.where(tableRead.equal(key, id))
    lookup.where(tableRead.equal(tableRead.column('asset', 'is_del', 'NUMBER'), 0))
    var projection = {}
    for (field in columns) {
        projection[field] = tableRead.column('asset', field, field == 'updated_time' ? 'TIME' : 'TEXT')
    }
    var rows = lookup.grouped(projection, [])
    return rows.size() == 0 ? null : rows[0]
}
var authorizeSource = source => {
    if (source == null) {
        exit 404, '数据源资产不存在或无访问权限'
    }
    if (!dataScope.canReadDataSourceFor(sourcePermission, source)) {
        exit 403, '数据源不存在或无权修改'
    }
}

// 先核对指定表的有效状态和所属数据源，避免以任意表 ID 探查其他数据源。
var previousTable = tableRefresh ? readActive('db_table_t', requestedTableId, tableFields) : null
if (tableRefresh) {
    if (previousTable == null) {
        exit 404, '数据表不存在或无访问权限'
    }
    if (text(previousTable.datasource_id) != text(sourceId)) {
        exit 403, '数据表不属于指定数据源'
    }
}
// 实时探查可能连接外部库，刷新与预检均先使用数据源保存的角色范围。
var previousSource = readActive('db_datasource_t', sourceId, sourceFields)
authorizeSource(previousSource)
var info = queryDatabaseInfo()
if (dryRun == true || dryRun == 'true') {
    return info
}

var persistSummary = () => {
    // 探查结束后才开启短事务，先锁定父源，再复核权限和已探查版本。
    var currentSource = tableWrite.lockRow('db_datasource_t', tenantId, 'tid', sourceId, sourceFields, active)
    if (currentSource == null) {
        exit 404, '数据源不存在或已删除'
    }
    authorizeSource(currentSource)
    if (!Objects.equals(previousSource.updated_time, currentSource.updated_time)) {
        exit 409, '数据源在探查期间已变化，请重新刷新'
    }

    if (tableRefresh) {
        var currentTable = tableWrite.lockRow('db_table_t', tenantId, 'tid', requestedTableId, tableFields, active)
        if (currentTable == null) {
            exit 404, '数据表不存在或已删除'
        }
        if (text(currentTable.datasource_id) != text(sourceId)) {
            exit 403, '数据表不属于指定数据源'
        }
        if (!Objects.equals(previousTable.updated_time, currentTable.updated_time)
            || !Objects.equals(previousTable.table_name, currentTable.table_name)) {
            exit 409, '数据表在探查期间已变化，请重新刷新'
        }

        // 探查返回的 0、空字符串及 null 都是有效结果；适配层逐项绑定并沿用数据库时钟。
        var changed = tableWrite.updateWithDatabaseTimestamp('db_table_t', tenantId, 'tid', requestedTableId, {
            record_count: info.recordCount,
            field_count: info.fieldCount,
            storage_size: info.storageSize,
            total_size_formatted: info.totalSizeFormatted,
            total_size_bytes: info.totalSizeBytes,
            ddl_create_time: info.ddlCreateTime,
            ddl_update_time: info.ddlUpdateTime
        }, {is_del: 0, datasource_id: sourceId}, 'updated_time')
        if (changed == 0) {
            exit 404, '数据表不存在或已删除'
        }
        return
    }

    // 概览属于主表物理列，连接配置及更新人均由原业务流程维护。
    var changed = tableWrite.updateWithDatabaseTimestamp('db_datasource_t', tenantId, 'tid', sourceId, {
        table_num: info.tableCount,
        db_version: info.dbVersion,
        view_count: info.viewCount,
        procedure_count: info.procedureCount,
        user_count: info.userCount,
        data_size: info.dataSize,
        total_size: info.totalSize
    }, active, 'updated_time')
    if (changed == 0) {
        exit 404, '数据源不存在或已删除'
    }
}
db.transaction(persistSummary)

// 主表提交后再刷新搜索索引，投影失败保留已保存的概览供重试。
var submitted = body
var assetType = tableRefresh ? 'table' : 'db'
try {
    body = {tid: tableRefresh ? requestedTableId : sourceId, assetType: assetType}
    refreshAssetIndex()
} catch (e) {
    log.warn('元数据概览已保存，资产搜索索引刷新失败，资产类型：{}', assetType)
} finally {
    body = submitted
}
if (tableRefresh) {
    return info
}
return {
    tid: sourceId,
    dbVersion: info.dbVersion, db_version: info.dbVersion,
    dataSize: info.dataSize, data_size: info.dataSize,
    tableCount: info.tableCount, tableNum: info.tableCount, table_num: info.tableCount,
    viewCount: info.viewCount, view_count: info.viewCount,
    procedureCount: info.procedureCount, procedure_count: info.procedureCount,
    totalSize: info.totalSize, total_size: info.totalSize,
    userCount: info.userCount, user_count: info.userCount
}
