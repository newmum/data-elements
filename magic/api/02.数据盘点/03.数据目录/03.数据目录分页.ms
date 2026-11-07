{
  "properties": {},
  "id": "datapd_catalog_page_compat_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "03.数据目录分页",
  "createTime": null,
  "updateTime": 1790484171436,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20,\n  \"conditions\": []\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"pageNum\":1,\"pageSize\":20,\"total\":0,\"list\":[]}",
  "description": "数据目录分页接口（POST /dst/catalog/page）。目录、树统计和详情统一读取 da_catalog_t；使用请求参数 conditions、keyword、statisticsOnly、pageNum、pageSize、sortField、sortDir。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableSort
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.List
/**
 * 数据目录分页查询。POST /dst/catalog/page
 * 接收分页、排序、关键词和筛选条件，返回目录列表与总数。
 * statisticsOnly 为 true 时返回组织与应用计数。统计和列表使用同一租户及角色范围，只读。
 */

var text = value => value == null ? '' : (value + '').trim()
var tenantId = text(tenantRuntime.id())
if (tenantId == '') {
    exit 401, '当前登录租户无效，请重新登录'
}
var scope = dataScope.resource('MENU:2081000000000000002')
if (!['ALL', 'ORG', 'OWNER'].contains(scope.scope)) {
    exit 403, '数据范围无效'
}

var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var master = name => column('c', name)
var isActive = alias => tableRead.equal(tableRead.coalesce([column(alias, 'is_del'), 0]), 0)
var query = tableRead.from('da_catalog_t', 'c', tenantId)
query.where(isActive('c'))
// 可信角色范围先行。请求里的组织、创建人或目录条件不能扩大结果。
if (scope.scope == 'OWNER') {
    query.where(text(scope.userId) == '' ? tableRead.any([]) : tableRead.equal(master('created_by'), scope.userId))
} else if (scope.scope == 'ORG') {
    query.where(tableRead.in(master('org_id'), scope.organizationIds))
}

// 仅关联当前租户的有效记录。目录没有留存名称时，使用关联记录的名称。
query.leftJoin('sym_application_t', 'a', 'tid', 'app_id', [isActive('a')])
query.leftJoin('db_datasource_t', 'd', 'tid', 'db_id', [isActive('d')])
query.leftJoin('db_table_t', 't', 'tid', 'source_table_id', [tableRead.equal(column('t', 'is_del'), 0)])
var projectedName = (alias, name) => tableRead.coalesce([tableRead.blankAsNull(master(name)), column(alias, name)])
var fields = {
    tid: master('tid'),
    tenantId: master('tenant_id'),
    assetStatus: tableRead.column('c', 'asset_status', 'NUMBER'),
    flowStatus: tableRead.column('c', 'flow_status', 'NUMBER'),
    regTime: tableRead.column('c', 'reg_time', 'TIME'),
    createdTime: tableRead.column('c', 'created_time', 'TIME'),
    updatedTime: tableRead.column('c', 'updated_time', 'TIME'),
    catalogName: master('catalog_name'),
    catalogNameEn: master('catalog_name_en'),
    assetDesc: tableRead.column('c', 'asset_desc', 'LOB'),
    appId: master('app_id'),
    dbId: master('db_id'),
    sourceTableId: master('source_table_id'),
    sourceTableName: master('source_table_name'),
    orgId: master('org_id'),
    manageUnit: master('manage_unit'),
    businessType: master('business_type'),
    timestampField: master('timestamp_field'),
    dictionaryTableFlag: master('dictionary_table_flag'),
    appName: projectedName('a', 'app_name'),
    dbName: projectedName('d', 'db_name'),
    dataSourceType: tableRead.coalesce([
        tableRead.blankAsNull(master('data_source_type')),
        tableRead.boundedText(tableRead.column('p_source', 'prop_value', 'LOB')),
        'ods'
    ]),
    dataLevel: tableRead.column('p_level', 'prop_value', 'LOB'),
    shareType: tableRead.column('p_share', 'prop_value', 'LOB'),
    recordCount: tableRead.column('t', 'record_count', 'NUMBER'),
    fieldCount: tableRead.column('t', 'field_count', 'NUMBER'),
    totalSizeBytes: tableRead.column('t', 'total_size_bytes', 'NUMBER')
}
// 关键字匹配目录名或来源表名。数值和时间条件使用与列对应的参数类型。
var names = value => tableRead.any([
    tableRead.contains(fields.catalogName, value),
    tableRead.contains(fields.sourceTableName, value)
])
var typed = (field, value) => {
    var normalized = text(value)
    try {
        if (['assetStatus', 'flowStatus', 'recordCount', 'fieldCount', 'totalSizeBytes'].contains(field)) {
            return new BigDecimal(normalized)
        }
        if (['regTime', 'createdTime', 'updatedTime'].contains(field)) {
            if (normalized.length() == 10) {
                normalized = normalized + ' 00:00:00'
            }
            return Timestamp.valueOf(normalized.replace('T', ' '))
        }
        return normalized
    } catch (e) {
        return null
    }
}

var conditions = body?.conditions == null ? [] : body.conditions
if (!(conditions instanceof List) || conditions.size() > 32) {
    exit 400, '数据目录筛选条件最多 32 个'
}
var used = {}
for (condition in conditions) {
    if (condition == null || text(condition.field) == '' || text(condition.value) == '') {
        continue
    }
    var field = text(condition.field)
    if (field == 'assetType') {
        if (text(condition.value) != 'catalog') {
            query.where(tableRead.any([]))
        }
        continue
    }
    if (field == 'orgIds') {
        field = 'orgId'
    }
    if (fields[field] == null) {
        exit 400, '不支持的数据目录筛选字段：' + field
    }
    used[field] = true
    var type = text(condition.type).toLowerCase()
    if (type == 'in') {
        if (!(condition.value instanceof List)) {
            exit 400, '多值筛选必须传入数组'
        }
        if (condition.value.size() > 1000) {
            exit 400, '数据目录多值筛选最多 1000 项'
        }
        var values = condition.value.map(value => typed(field, value)).filter(value => value != null).distinct()
        query.where(tableRead.in(fields[field], values))
    } else if (type == 'like') {
        var value = text(condition.value)
        query.where(field == 'catalogName' ? names(value) : tableRead.contains(fields[field], value))
    } else if (type == '' || type == 'match' || type == 'eq') {
        var value = typed(field, condition.value)
        query.where(value == null ? tableRead.any([]) : tableRead.equal(fields[field], value))
    } else {
        exit 400, '不支持的数据目录筛选方式：' + type
    }
}
if (text(body?.keyword) != '') {
    query.where(names(text(body.keyword)))
}

// 过滤需要的属性参与计数，普通属性只为页面投影关联，避免扩大统计开销。
var statisticsOnly = body?.statisticsOnly == true
var propertyAliases = {dataSourceType: 'p_source', dataLevel: 'p_level', shareType: 'p_share'}
for (property in propertyAliases.keySet()) {
    if (!statisticsOnly || used[property] == true) {
        query.leftJoinLatest(
            'da_prop_t', propertyAliases[property], 'parent_id', 'tid',
            {data_type: 'catalog', prop_name: property}, 'is_del', 'updated_time', 'tid', used[property] != true
        )
    }
}

if (statisticsOnly) {
    // 数据库按组织、应用和数据源聚合。脚本只组装统计树。
    var rows = query.grouped({
        orgId: fields.orgId,
        appId: fields.appId,
        appName: fields.appName,
        dbId: fields.dbId,
        dbName: fields.dbName,
        dbType: column('d', 'db_type'),
        total: tableRead.count()
    }, [
        fields.orgId, fields.appId, master('app_name'), column('a', 'app_name'),
        fields.dbId, master('db_name'), column('d', 'db_name'), column('d', 'db_type')
    ])
    var total = 0
    var orgCounts = new LinkedHashMap()
    var appMap = new LinkedHashMap()
    for (row in rows) {
        var count = row.total::int
        total = total + count
        var orgId = text(row.orgId)
        var appId = text(row.appId)
        if (orgId == '') {
            continue
        }
        orgCounts[orgId] = (orgCounts[orgId] == null ? 0 : orgCounts[orgId]) + count
        if (appId == '') {
            continue
        }
        if (appMap[orgId] == null) {
            appMap[orgId] = new LinkedHashMap()
        }
        if (appMap[orgId][appId] == null) {
            var appName = text(row.appName)
            appMap[orgId][appId] = {
                label: appName == '' ? '未命名业务系统' : appName,
                value: appId,
                type: 'app',
                count: 0,
                datasources: []
            }
        }
        var app = appMap[orgId][appId]
        app.count = app.count + count
        var dbId = text(row.dbId)
        if (dbId != '') {
            app.datasources.add({label: row.dbName, value: dbId, count: count, dbType: row.dbType})
        }
    }
    var orgApps = new LinkedHashMap()
    for (orgId in appMap.keySet()) {
        orgApps[orgId] = new ArrayList(appMap[orgId].values())
    }
    return {total: total, orgCounts: orgCounts, orgApps: orgApps}
}

var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
var pageSize = body?.pageSize == null ? 20 : body.pageSize::int
if (pageNum < 1) {
    pageNum = 1
}
if (pageSize < 1) {
    pageSize = 20
}
if (pageSize > 100) {
    pageSize = 100
}
var sortField = tableSort.field(body, {
    catalogName: 'catalogName',
    regTime: 'regTime',
    createdTime: 'createdTime',
    updatedTime: 'updatedTime',
    recordCount: 'recordCount',
    fieldCount: 'fieldCount',
    totalSizeBytes: 'totalSizeBytes'
}, 'updatedTime')
var page = query.page(fields, fields[sortField], tableSort.direction(body), 'tid', pageNum, pageSize)
var list = []
var itemMap = {}
var ids = []
for (row in page.rows) {
    // 分页方言可能返回额外技术列。仅组装声明的目录字段。
    var item = {}
    for (field in fields.keySet()) {
        item[field] = row[field]
    }
    item.assetType = 'catalog'
    item.props = {}
    item.id = item.tid
    item.displayStatus = item.flowStatus == 1 ? 1 : item.assetStatus
    list.add(item)
    ids.add(item.tid)
    itemMap[text(item.tid)] = item
}

// 只为本页可见目录批取属性。保留主表的身份、状态与归属，旧的无时间属性先处理。
if (ids.size() > 0) {
    var props = db.lower().table('da_prop_t')
        .columns('parent_id', 'prop_name', 'prop_value', 'updated_time')
        .where()
        .eq('tenant_id', tenantId)
        .eq('data_type', 'catalog')
        .in('parent_id', ids)
        .and(active => active.eq('is_del', 0).or().isNull('is_del'))
        .orderBy('updated_time')
        .orderBy('tid')
        .select()
    var masterFields = [
        'tid', 'id', 'tenantId', 'assetType', 'assetStatus', 'flowStatus', 'orgId',
        'appId', 'dbId', 'sourceTableId', 'displayStatus', 'props'
    ]
    var applyProp = prop => {
        var item = itemMap[text(prop.parent_id)]
        var name = text(prop.prop_name)
        if (item == null || name == '') {
            return
        }
        item.props[name] = prop.prop_value
        if (!masterFields.contains(name) && text(item[name]) == '') {
            item[name] = prop.prop_value
        }
    }
    for (prop in props) {
        if (prop.updated_time == null) {
            applyProp(prop)
        }
    }
    for (prop in props) {
        if (prop.updated_time != null) {
            applyProp(prop)
        }
    }
}
return {pageNum: pageNum, pageSize: pageSize, total: page.total, list: list}
