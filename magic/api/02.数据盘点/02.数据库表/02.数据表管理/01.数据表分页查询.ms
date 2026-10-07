{
  "properties": {},
  "id": "datapd_db_table_page_01",
  "script": null,
  "groupId": "datapd_db_table_group_01",
  "name": "01.数据表分页查询",
  "createTime": 1784454007655,
  "updateTime": 1790394629431,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"dbId\": \"可选数据源ID\",\n    \"conditions\": [\n        {\n            \"field\": \"assetType\",\n            \"type\": \"match\",\n            \"value\": \"app\"\n        }\n    ],\n    \"keyword\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据表管理 01：分页查询已登记数据表；使用请求参数 tid、dbId、datasourceId、conditions、keyword、pageNum、pageSize；涉及 db_table_t、db_datasource_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.数据表分页查询
// 路由：POST /dst/database/table/page
// 业务说明：数据表管理 01：分页查询已登记数据表；使用请求参数 tid、dbId、datasourceId、conditions、keyword、pageNum、pageSize；涉及 db_table_t、db_datasource_t 等业务表。
import java.util.Map
import tenantRuntime
var tenantId = tenantRuntime.id()

/*
 * 数据表管理 01：分页查询已登记数据表。
 *
 * 主数据及全部表属性直接读取 db_table_t，数据源名称读取 db_datasource_t。
 * 本接口只读，不访问物理业务库、不写 MySQL 或 Elasticsearch。
 */

var textOf = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var lower = (value) => textOf(value).trim().toLowerCase()
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
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var putIfPresent = (target, name, value) => {
    if (value != null) {
        target[name] = value
    }
}
var tableData = (row) => {
    var tid = rowValue(row, ['tid'])
    var item = {
        tid: tid,
        id: tid,
        assetType: 'table',
        datasourceId: firstValue(row, ['datasourceId', 'datasource_id']),
        dbId: firstValue(row, ['datasourceId', 'datasource_id']),
        tableName: firstValue(row, ['tableName', 'table_name']),
        tableNameEn: firstValue(row, ['tableNameEn', 'table_name_en', 'tableName', 'table_name']),
        tableNameCn: firstValue(row, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']),
        tableComment: firstValue(row, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn']),
        tableType: firstValue(row, ['tableType', 'table_type']),
        businessType: firstValue(row, ['businessType', 'business_type']),
        businessTypeReason: firstValue(row, ['businessTypeReason', 'business_type_reason']),
        annotated: rowValue(row, ['annotated']),
        recordCount: rowValue(row, ['recordCount', 'record_count']),
        fieldCount: rowValue(row, ['fieldCount', 'field_count']),
        storageSize: firstValue(row, ['storageSize', 'storage_size']),
        totalSizeBytes: rowValue(row, ['totalSizeBytes', 'total_size_bytes']),
        totalSizeFormatted: firstValue(row, ['totalSizeFormatted', 'total_size_formatted', 'storageSize', 'storage_size']),
        ddlCreatTime: firstValue(row, ['ddlCreateTime', 'ddl_create_time']),
        ddlUpdateTime: firstValue(row, ['ddlUpdateTime', 'ddl_update_time']),
        orgId: firstValue(row, ['orgId', 'org_id']),
        orgPath: firstValue(row, ['orgPath', 'org_path']),
        relatedDirectory: firstValue(row, ['relatedDirectory', 'related_directory']),
        dataSourceType: firstValue(row, ['dataSourceType', 'data_source_type']),
        timestampField: firstValue(row, ['timestampField', 'timestamp_field']),
        dictionaryStructureType: firstValue(row, ['dictionaryStructureType', 'dictionary_structure_type']),
        dictionaryTableFlag: rowValue(row, ['dictionaryTableFlag', 'dictionary_table_flag']),
        fieldGovernanceConfig: rowValue(row, ['fieldGovernanceConfig', 'field_governance_config']),
        dictionaryProfiles: rowValue(row, ['dictionaryProfiles', 'dictionary_profiles']),
        dictionaryProfile: rowValue(row, ['dictionaryProfile', 'dictionary_profile']),
        dictionaryCategories: rowValue(row, ['dictionaryCategories', 'dictionary_categories']),
        sourceCatalogId: firstValue(row, ['sourceCatalogId', 'source_catalog_id']),
        sourceTableId: firstValue(row, ['sourceTableId', 'source_table_id', 'tid']),
        sourceTableName: firstValue(row, ['sourceTableName', 'source_table_name', 'tableName', 'table_name']),
        manageUnit: firstValue(row, ['manageUnit', 'manage_unit', 'orgId', 'org_id']),
        assetDesc: firstValue(row, ['assetDesc', 'asset_desc', 'tableComment', 'table_comment']),
        catalogName: firstValue(row, ['catalogName', 'catalog_name', 'tableNameCn', 'table_name_cn']),
        catalogNameEn: firstValue(row, ['catalogNameEn', 'catalog_name_en', 'tableName', 'table_name']),
        tenantId: firstValue(row, ['tenantId', 'tenant_id']),
        assetStatus: rowValue(row, ['assetStatus', 'asset_status']),
        flowStatus: rowValue(row, ['flowStatus', 'flow_status']),
        regTime: rowValue(row, ['regTime', 'reg_time']),
        createdTime: rowValue(row, ['createdTime', 'created_time']),
        updatedTime: rowValue(row, ['updatedTime', 'updated_time'])
    }
    item.regTime_date = item.regTime
    item.createdTime_date = item.createdTime
    item.updatedTime_date = item.updatedTime
    // 返回当前业务分支的标准结果。
    return item
}
var matches = (item, condition) => {
    if (condition == null || blank(condition.field)) {
        // 返回当前业务分支的标准结果。
        return true
    }
    var actual = item[condition.field]
    var expected = condition.value
    var type = blank(condition.type) ? 'match' : condition.type
    if (type == 'null') {
        // 返回当前业务分支的标准结果。
        return blank(actual)
    }
    if (type == 'like') {
        // 返回当前业务分支的标准结果。
        return lower(actual).indexOf(lower(expected)) >= 0
    }
    if (type == 'in') {
        if (expected == null) {
            // 返回当前业务分支的标准结果。
            return true
        }
        for (value in expected) {
            if (textOf(actual) == textOf(value)) {
                // 返回当前业务分支的标准结果。
                return true
            }
        }
        // 返回当前业务分支的标准结果。
        return false
    }
    // 返回当前业务分支的标准结果。
    return textOf(actual) == textOf(expected)
}

// 先将精确的表/数据源范围下推到 MySQL，避免登记一张表时读取整个租户的治理配置。
var requestedTableId = body?.tid
var requestedSourceId = blank(body?.dbId) ? body?.datasourceId : body.dbId
var tableRows = []
if (!blank(requestedTableId)) {
    tableRows = db.table('db_table_t').where().eq('tenant_id', tenantId)
        .eq('is_del', 0).eq('tid', requestedTableId).select()
} else if (!blank(requestedSourceId)) {
    tableRows = db.select("""
        SELECT * FROM db_table_t
         WHERE tenant_id = #{tenantId} AND is_del = 0 AND datasource_id = #{requestedSourceId}
         ORDER BY updated_time DESC, created_time DESC
    """)
} else {
    tableRows = db.select("""
        SELECT * FROM db_table_t
         WHERE tenant_id = #{tenantId} AND is_del = 0
         ORDER BY updated_time DESC, created_time DESC
    """)
}
// 从 MySQL 业务表读取当前有效数据。
var datasourceRows = db.select("""
    SELECT tid, db_name, app_id, org_id
      FROM db_datasource_t
     WHERE is_del = 0
""")
var datasourceMap = {}
for (source in datasourceRows) {
    datasourceMap[firstValue(source, ['tid'])] = source
}

// 解析请求体并为可选参数设置默认值。
var conditions = body == null || body.conditions == null ? [] : body.conditions
for (field in ['tid', 'dbId', 'datasourceId', 'businessType', 'dataSourceType', 'orgId', 'assetStatus']) {
    if (body != null && !blank(body[field])) {
        conditions.add({ field: field, value: body[field], type: 'match' })
    }
}
var keyword = body == null ? null : body.keyword
var rows = []
for (tableRow in tableRows) {
    var item = tableData(tableRow)
    var source = datasourceMap[item.datasourceId]
    if (source != null) {
        item.dbName = firstValue(source, ['dbName', 'db_name'])
        item.appId = firstValue(source, ['appId', 'app_id'])
        if (blank(item.orgId)) {
            item.orgId = firstValue(source, ['orgId', 'org_id'])
        }
    }
    var accepted = true
    for (condition in conditions) {
        if (!matches(item, condition)) {
            accepted = false
        }
    }
    if (accepted && !blank(keyword)) {
        var searchText = lower(item.tableName) + ' ' + lower(item.tableNameCn) + ' ' + lower(item.tableComment)
        accepted = searchText.indexOf(lower(keyword)) >= 0
    }
    if (accepted) {
        rows.add(item)
    }
}

var betterRow = (next, current) => {
    if (current == null) { return true }
    var nextFields = next.fieldCount == null ? 0 : next.fieldCount::int
    var currentFields = current.fieldCount == null ? 0 : current.fieldCount::int
    if (nextFields != currentFields) { return nextFields > currentFields }
    var nextAnnotated = next.annotated == null ? 0 : next.annotated::int
    var currentAnnotated = current.annotated == null ? 0 : current.annotated::int
    return nextAnnotated > currentAnnotated
}
var rowByKey = {}
var dedupedRows = []
for (item in rows) {
    var key = firstValue(item, ['datasourceId', 'dbId']) + '@@' + lower(item.tableName)
    if (blank(item.tableName) || blank(firstValue(item, ['datasourceId', 'dbId']))) {
        dedupedRows.add(item)
    } else if (rowByKey[key] == null) {
        rowByKey[key] = item
        dedupedRows.add(item)
    } else if (betterRow(item, rowByKey[key])) {
        var oldItem = rowByKey[key]
        rowByKey[key] = item
        for (idx, existing in dedupedRows) { if (existing.tid == oldItem.tid) { dedupedRows[idx] = item } }
    }
}
rows = dedupedRows

var pageNum = body == null || body.pageNum == null ? 1 : body.pageNum::int
var pageSize = body == null || body.pageSize == null ? 20 : body.pageSize::int
if (pageNum < 1) {
    pageNum = 1
}
if (pageSize < 1) {
    pageSize = 20
}
var offset = (pageNum - 1) * pageSize
var pageRows = []
for (index, item in rows) {
    if (index >= offset && index < offset + pageSize) {
        pageRows.add(item)
    }
}
// 返回当前业务分支的标准结果。
return {
    pageNum: pageNum,
    pageSize: pageSize,
    total: rows.size(),
    list: pageRows
}
