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
  "description": "分页读取当前登录租户的有效登记表及所属数据源。支持标识、筛选条件、关键词及白名单排序。先选取每个来源的同名表代表记录，再筛选和分页，返回 pageNum、pageSize、total、list。分层目录搜索可匹配完整注释，目录排序仅用回退注释的前 512 字符。只读。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import java.util.List
import tableRead
import tableSort
import tenantRuntime

/**
 * POST /dst/database/table/page 查询登记数据表及所属来源，供数源层、数仓分层和登记页面使用。
 * 支持 tid、dbId、datasourceId、conditions、keyword 和排序参数，返回 pageNum、pageSize、total、list。
 * 只读取当前登录租户的有效登记表及来源，沿用分层目录的共享范围。每页最多 100 条，无业务写入。
 */

var tenantId = tenantRuntime.id()
var textOf = value => value == null ? '' : value + ''
var blank = value => textOf(value).trim() == ''
var nonBlank = values => {
    for (value in values) {
        if (!blank(value)) {
            return value
        }
    }
    return null
}

var columnKinds = {
    TEXT: [
        'tid', 'datasource_id', 'table_name', 'table_name_en', 'table_name_cn', 'table_type',
        'business_type', 'business_type_reason', 'storage_size', 'total_size_formatted', 'ddl_create_time',
        'ddl_update_time', 'org_id', 'related_directory', 'data_source_type', 'timestamp_field',
        'dictionary_structure_type', 'source_catalog_id', 'source_table_id', 'source_table_name',
        'manage_unit', 'catalog_name', 'catalog_name_en'
    ],
    LOB: [
        'table_comment', 'org_path', 'field_governance_config', 'dictionary_profiles',
        'dictionary_profile', 'dictionary_categories', 'asset_desc'
    ],
    NUMBER: [
        'annotated', 'record_count', 'field_count', 'total_size_bytes', 'dictionary_table_flag',
        'asset_status', 'flow_status'
    ],
    TIME: ['reg_time', 'created_time', 'updated_time']
}
var tableColumns = alias => {
    var columns = {}
    for (kind, names in columnKinds) {
        for (name in names) {
            columns[name] = tableRead.column(alias, name, kind)
        }
    }
    return columns
}

// 同来源同名表先选出代表行，再按标识或关键词筛选，避免旧重复记录重新出现在列表。
var registered = tableRead.from('db_table_t', 'original', tenantId)
var original = tableColumns('original')
registered.where(tableRead.equal(tableRead.column('original', 'is_del', 'NUMBER'), 0))
var blankPhysicalName = tableRead.isNull(tableRead.blankAsNull(tableRead.trim(original.table_name)))
var nameKey = tableRead.when(blankPhysicalName, original.tid, tableRead.lower(original.table_name))
var tables = registered.ranked('t', original, [original.datasource_id, nameKey], [
    tableRead.coalesce([original.field_count, 0]),
    tableRead.coalesce([original.annotated, 0]),
    original.updated_time,
    original.tid
], ['desc', 'desc', 'desc', 'asc'], true)

// 分层目录共享当前行业的登记表。缺少当前租户有效来源的记录不会进入结果。
tables.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id', [
    tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0)
])
tables.where(tableRead.not(tableRead.isNull(tableRead.column('d', 'tid', 'TEXT'))))
var columns = tableColumns('t')
columns.tenant_id = tableRead.column('t', 'tenant_id', 'TEXT')
columns.db_name = tableRead.column('d', 'db_name', 'TEXT')
columns.app_id = tableRead.column('d', 'app_id', 'TEXT')
var catalogName = tableRead.coalesce([
    tableRead.blankAsNull(tableRead.trim(columns.catalog_name)),
    tableRead.blankAsNull(tableRead.trim(columns.table_name_cn))
])

// 筛选沿用物理英文名和来源组织回退。返回值仍保留表记录自身的英文名和组织。
var filterColumns = {
    tid: columns.tid,
    dbId: columns.datasource_id,
    datasourceId: columns.datasource_id,
    tableName: columns.table_name,
    tableNameEn: columns.table_name,
    tableNameCn: columns.table_name_cn,
    // 保留注释在各数据库原生字符比较中的排序规则和尾部空格语义。
    tableComment: tableRead.column('t', 'table_comment', 'TEXT'),
    appId: columns.app_id,
    orgId: tableRead.coalesce([columns.org_id, tableRead.column('d', 'org_id', 'TEXT')]),
    businessType: columns.business_type,
    dataSourceType: columns.data_source_type,
    assetStatus: columns.asset_status,
    fieldCount: columns.field_count,
    recordCount: columns.record_count,
    createdTime: columns.created_time,
    updatedTime: columns.updated_time,
    regTime: columns.reg_time
}
var conditions = []
if (body?.conditions != null) {
    conditions.addAll(body.conditions)
}
for (field in ['tid', 'dbId', 'datasourceId', 'businessType', 'dataSourceType', 'orgId', 'assetStatus']) {
    if (!blank(body?.get(field))) {
        conditions.add({field: field, value: body.get(field), type: 'match'})
    }
}
var noMatch = tableRead.any([])
for (condition in conditions) {
    var field = condition?.field
    if (field == 'assetType') {
        if (condition.value != 'table') {
            tables.where(noMatch)
        }
        continue
    }
    var type = condition?.type || 'match'
    var value = condition?.value
    if (field == 'catalogName' || field == 'catalogOrTableName') {
        if (type != 'like') {
            exit 400, '不支持的数据表筛选方式：' + type
        }
        var keyword = textOf(value)
        // 目录名和中文名都为空时才匹配完整注释。基础层还匹配三个表名字段。
        var matches = [
            tableRead.containsLiteral(catalogName, keyword),
            tableRead.all([
                tableRead.isNull(catalogName),
                tableRead.containsLiteral(columns.table_comment, keyword)
            ])
        ]
        if (field == 'catalogOrTableName') {
            matches.add(tableRead.containsLiteral(columns.table_name, keyword))
            matches.add(tableRead.containsLiteral(columns.table_name_cn, keyword))
            matches.add(tableRead.containsLiteral(columns.table_name_en, keyword))
        }
        tables.where(tableRead.any(matches))
        continue
    }
    var column = filterColumns[field]
    if (column == null) {
        exit 400, '不支持的数据表筛选字段：' + field
    }
    if (type == 'null') {
        tables.where(tableRead.isNull(column))
    } else if (type == 'in') {
        if (!(value instanceof List) || value.size() > 1000) {
            exit 400, '多值筛选最多 1000 项'
        }
        tables.where(tableRead.in(column, value))
    } else if (type == 'like') {
        tables.where(tableRead.containsLiteral(column, textOf(value)))
    } else if (type == 'match' || type == 'eq') {
        // 空值匹配须显式用 null 筛选，普通等值空参数保持无结果。
        tables.where(value == null ? noMatch : tableRead.equal(column, value))
    } else {
        exit 400, '不支持的数据表筛选方式：' + type
    }
}
var keyword = body?.keyword
if (!blank(keyword)) {
    var literal = textOf(keyword)
    tables.where(tableRead.any([
        tableRead.containsLiteral(columns.table_name, literal),
        tableRead.containsLiteral(columns.table_name_cn, literal),
        tableRead.containsLiteral(columns.table_comment, literal)
    ]))
}

// 目录排序只取回退注释的前 512 字符，普通表名排序仍使用完整的回退值。
var displayName = tableRead.coalesce([
    tableRead.blankAsNull(columns.table_name_cn),
    tableRead.blankAsNull(columns.table_comment),
    columns.table_name
])
var sortColumns = {
    tableName: displayName,
    name: displayName,
    tableNameEn: columns.table_name,
    catalogName: tableRead.coalesce([
        catalogName,
        tableRead.blankAsNull(tableRead.trimmedTextPrefix(columns.table_comment, 512))
    ]),
    tableNameCn: columns.table_name_cn,
    fieldCount: columns.field_count,
    recordCount: columns.record_count,
    totalSizeBytes: columns.total_size_bytes,
    ddlCreatTime: columns.ddl_create_time,
    ddlCreateTime: columns.ddl_create_time,
    ddlUpdateTime: columns.ddl_update_time,
    regTime: columns.reg_time,
    createdTime: columns.created_time,
    updatedTime: columns.updated_time
}
var sortNames = {}
for (name in sortColumns.keySet()) {
    sortNames[name] = name
}
var sortField = tableSort.field(body, sortNames, 'updatedTime')
var pageNum = Math.max(1, body?.pageNum::int(1))
var pageSize = Math.min(100, Math.max(1, body?.pageSize::int(20)))
var page = tables.page(columns, sortColumns[sortField], tableSort.direction(body), 'tid', pageNum, pageSize)

// 兼容现用页面的中文名、目录和时间字段，完整注释与治理配置只随当前页返回。
var list = page.rows.map(row => ({
    tid: row.tid,
    id: row.tid,
    assetType: 'table',
    datasourceId: nonBlank([row.datasource_id]),
    dbId: nonBlank([row.datasource_id]),
    tableName: nonBlank([row.table_name]),
    tableNameEn: nonBlank([row.table_name_en, row.table_name]),
    tableNameCn: nonBlank([row.table_name_cn, row.table_comment]),
    tableComment: nonBlank([row.table_comment, row.table_name_cn]),
    tableType: nonBlank([row.table_type]),
    businessType: nonBlank([row.business_type]),
    businessTypeReason: nonBlank([row.business_type_reason]),
    annotated: row.annotated,
    recordCount: row.record_count,
    fieldCount: row.field_count,
    storageSize: nonBlank([row.storage_size]),
    totalSizeBytes: row.total_size_bytes,
    totalSizeFormatted: nonBlank([row.total_size_formatted, row.storage_size]),
    ddlCreatTime: nonBlank([row.ddl_create_time]),
    ddlUpdateTime: nonBlank([row.ddl_update_time]),
    orgId: nonBlank([row.org_id]),
    orgPath: nonBlank([row.org_path]),
    relatedDirectory: nonBlank([row.related_directory]),
    dataSourceType: nonBlank([row.data_source_type]),
    timestampField: nonBlank([row.timestamp_field]),
    dictionaryStructureType: nonBlank([row.dictionary_structure_type]),
    dictionaryTableFlag: row.dictionary_table_flag,
    fieldGovernanceConfig: row.field_governance_config,
    dictionaryProfiles: row.dictionary_profiles,
    dictionaryProfile: row.dictionary_profile,
    dictionaryCategories: row.dictionary_categories,
    sourceCatalogId: nonBlank([row.source_catalog_id]),
    sourceTableId: nonBlank([row.source_table_id, row.tid]),
    sourceTableName: nonBlank([row.source_table_name, row.table_name]),
    manageUnit: nonBlank([row.manage_unit, row.org_id]),
    assetDesc: nonBlank([row.asset_desc, row.table_comment]),
    catalogName: nonBlank([row.catalog_name, row.table_name_cn]),
    catalogNameEn: nonBlank([row.catalog_name_en, row.table_name]),
    tenantId: nonBlank([row.tenant_id]),
    assetStatus: row.asset_status,
    flowStatus: row.flow_status,
    regTime: row.reg_time,
    createdTime: row.created_time,
    updatedTime: row.updated_time,
    regTime_date: row.reg_time,
    createdTime_date: row.created_time,
    updatedTime_date: row.updated_time,
    dbName: row.db_name,
    appId: row.app_id
}))
return {pageNum: pageNum, pageSize: pageSize, total: page.total, list: list}
