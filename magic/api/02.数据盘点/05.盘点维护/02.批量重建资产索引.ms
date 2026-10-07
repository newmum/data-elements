{
  "properties" : { },
  "id" : "datapd_assets_batch_refresh_01",
  "script" : null,
  "groupId" : "datapd_maintenance_group_01",
  "name" : "02.批量重建资产索引",
  "createTime" : null,
  "updateTime" : 1791111892971,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/batchRefresh",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n    \"assetType\": [\n        \"app\",\n        \"db\",\n        \"table\"\n    ]\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"message\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"refreshed\": 19358,\n        \"removed\": 0,\n        \"assetTypes\": [\n            \"app\",\n            \"db\",\n            \"table\"\n        ]\n    },\n    \"timestamp\": 1790154626251\n}",
  "description" : "按当前租户重建应用、数据源、数据表及目录检索投影；tids 可指定最多 200 个 ID，预先批量读取主表、关联和统计数据，避免逐资产查询；不传 tids 时执行全量重建并可清理陈旧文档。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : {
    "name" : "",
    "value" : "",
    "description" : "",
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : "",
    "error" : "",
    "expression" : "",
    "children" : [ {
      "name" : "code",
      "value" : "0",
      "description" : "",
      "required" : false,
      "dataType" : "Integer",
      "type" : null,
      "defaultValue" : null,
      "validateType" : "",
      "error" : "",
      "expression" : "",
      "children" : [ ]
    }, {
      "name" : "msg",
      "value" : "success",
      "description" : "",
      "required" : false,
      "dataType" : "String",
      "type" : null,
      "defaultValue" : null,
      "validateType" : "",
      "error" : "",
      "expression" : "",
      "children" : [ ]
    }, {
      "name" : "message",
      "value" : "success",
      "description" : "",
      "required" : false,
      "dataType" : "String",
      "type" : null,
      "defaultValue" : null,
      "validateType" : "",
      "error" : "",
      "expression" : "",
      "children" : [ ]
    }, {
      "name" : "success",
      "value" : "true",
      "description" : "",
      "required" : false,
      "dataType" : "Boolean",
      "type" : null,
      "defaultValue" : null,
      "validateType" : "",
      "error" : "",
      "expression" : "",
      "children" : [ ]
    }, {
      "name" : "data",
      "value" : "",
      "description" : "",
      "required" : false,
      "dataType" : "Object",
      "type" : null,
      "defaultValue" : null,
      "validateType" : "",
      "error" : "",
      "expression" : "",
      "children" : [ {
        "name" : "refreshed",
        "value" : "19358",
        "description" : "",
        "required" : false,
        "dataType" : "Integer",
        "type" : null,
        "defaultValue" : null,
        "validateType" : "",
        "error" : "",
        "expression" : "",
        "children" : [ ]
      }, {
        "name" : "removed",
        "value" : "0",
        "description" : "",
        "required" : false,
        "dataType" : "Integer",
        "type" : null,
        "defaultValue" : null,
        "validateType" : "",
        "error" : "",
        "expression" : "",
        "children" : [ ]
      }, {
        "name" : "assetTypes",
        "value" : "",
        "description" : "",
        "required" : false,
        "dataType" : "Array",
        "type" : null,
        "defaultValue" : null,
        "validateType" : "",
        "error" : "",
        "expression" : "",
        "children" : [ {
          "name" : "",
          "value" : "app",
          "description" : "",
          "required" : false,
          "dataType" : "String",
          "type" : null,
          "defaultValue" : null,
          "validateType" : "",
          "error" : "",
          "expression" : "",
          "children" : [ ]
        } ]
      } ]
    }, {
      "name" : "timestamp",
      "value" : "1790154228798",
      "description" : "",
      "required" : false,
      "dataType" : "Long",
      "type" : null,
      "defaultValue" : null,
      "validateType" : "",
      "error" : "",
      "expression" : "",
      "children" : [ ]
    } ]
  }
}
================================
// 接口：02.批量重建资产索引
// 路由：POST /dst/maintenance/batchRefresh
// 业务说明：按当前租户批量重建应用、数据源、数据表和目录索引；指定 tids 时统一预取主表与统计依赖。
import cn.hutool.json.JSONUtil as JSONUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.elasticsearch.EsPageInfo
import org.springframework.data.elasticsearch.core.ElasticsearchOperations as esOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import java.util.List
import java.util.Map
import jsons
import tenantRuntime
var tenantId = tenantRuntime.id()

var text = (value) => value == null ? '' : value + ''
var blank = (value) => text(value).trim() == ''
var scalar = (value) => value == null || text(value).trim().toLowerCase() == 'null' ? null : text(value)
var numberValue = (value) => value == null ? 0 : value::int
var first = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value == null) {
            var upperKey = text(key).toUpperCase()
            value = row instanceof Map ? row.get(upperKey) : row[upperKey]
        }
        if (value != null) {
            return value
        }
    }
    return null
}
// 与共享 datasourceConnectionConfig('read') 保持相同的主表/pool_cfg 合并规则，
// 但复用已经按租户批量读出的主表行，避免每个数据源再次读库。
var connectionProps = main => {
    if (main == null) { return {} }
    var raw = first(main, ['poolCfg', 'pool_cfg'])
    var config = {}
    if (!blank(raw)) {
        try {
            var parsed = raw instanceof Map ? raw : JSONUtil.parse(text(raw))
            if (parsed != null) { config.putAll(parsed) }
        } catch (e) {
            exit 500, '数据源连接配置 pool_cfg 不是有效 JSON 对象'
        }
    }
    var pairs = {
        dbType: ['dbType', 'db_type'],
        jdbcURL: ['jdbcURL', 'jdbcUrl', 'jdbc_url'],
        username: ['username'],
        password: ['password'],
        dbVersion: ['dbVersion', 'db_version'],
        nodeId: ['nodeId', 'node_id'],
        storageDomain: ['storageDomain', 'storage_domain'],
        showConnect: ['showConnect', 'show_connect'],
        connectionStatus: ['connectionStatus', 'connection_status']
    }
    for (key in pairs.keySet()) {
        var value = first(main, pairs[key])
        if (value != null) { config[key] = value }
    }
    return config
}
var requested = body?.assetType
var types = requested == null ? ['app', 'db', 'table', 'catalog'] : (requested instanceof List ? requested : [requested])
var tids = body?.tids
var typeEnabled = (value) => {
    for (item in types) {
        if (text(item) == value) {
            return true
        }
    }
    return false
}
var tidEnabled = (value) => {
    if (tids == null || tids.size() == 0) {
        return true
    }
    for (item in tids) {
        if (text(item) == text(value)) {
            return true
        }
    }
    return false
}
var excludedIndexProps = {
    fieldGovernanceConfig: true,
    dictionaryProfiles: true,
    dictionaryProfile: true,
    dictionaryCategories: true,
    columns: true
}
var putIndexProps = (target, source) => {
    if (source == null) {
        return
    }
    for (key in source.keySet()) {
        if (excludedIndexProps[key] == null) {
            target[key] = source[key]
        }
    }
}

var ids = []
var docs = []
var active = {}

// When a caller passes specific tids, rebuild only those documents. This keeps save/delete flows fast:
// /dst/database/saveOrUpdate -> /dst/maintenance/refresh -> /dst/maintenance/batchRefresh.
var hasTargetTids = tids != null && tids.size() > 0
if (hasTargetTids) {
    var targetIds = []
    var targetDocs = []
    var touched = 0
    // 同一请求只接受有限的 ID，查询均按当前租户收敛后再关联，不能扩大可见范围。
    if (!(tids instanceof List) || tids.size() > 200) {
        exit 400, 'tids 必须是数组且单次不超过 200 个'
    }
    var requestedIds = []
    var requestedSet = {}
    for (targetId in tids) {
        var requestedId = text(targetId).trim()
        if (!blank(requestedId) && requestedSet[requestedId] != true) {
            requestedSet[requestedId] = true
            requestedIds.add(requestedId)
        }
    }
    var dbById = {}
    var appById = {}
    var tableById = {}
    var appIds = []
    var sourceIds = []
    var referencedAppIds = []
    var sourceAppById = {}
    var appNamesById = {}
    if (requestedIds.size() > 0) {
        if (typeEnabled('db')) {
            for (sourceRow in db.table('db_datasource_t').where()
                .in('tid', requestedIds).eq('tenant_id', tenantId).eq('is_del', 0).select()) {
                var sourceId = text(first(sourceRow, ['tid']))
                dbById[sourceId] = sourceRow
                sourceIds.add(sourceId)
                var sourceProps = connectionProps(sourceRow)
                var sourceAppId = first(sourceRow, ['appId', 'app_id'])
                if (blank(sourceAppId)) { sourceAppId = sourceProps.appId }
                if (!blank(sourceAppId) && !referencedAppIds.contains(text(sourceAppId))) {
                    referencedAppIds.add(text(sourceAppId))
                }
            }
        }
        if (typeEnabled('app')) {
            for (appRow in db.table('sym_application_t').where()
                .in('tid', requestedIds).eq('tenant_id', tenantId).eq('is_del', 0).select()) {
                var appId = text(first(appRow, ['tid']))
                appById[appId] = appRow
                appIds.add(appId)
            }
        }
        if (typeEnabled('table')) {
            for (tableRow in db.table('db_table_t').where()
                .in('tid', requestedIds).eq('tenant_id', tenantId).eq('is_del', 0).select()) {
                tableById[text(first(tableRow, ['tid']))] = tableRow
            }
        }
    }
    if (appIds.size() > 0) {
        for (appSource in db.table('db_datasource_t').where()
            .in('app_id', appIds).eq('tenant_id', tenantId).eq('is_del', 0).select()) {
            var appSourceId = text(first(appSource, ['tid']))
            if (!sourceIds.contains(appSourceId)) { sourceIds.add(appSourceId) }
            sourceAppById[appSourceId] = text(first(appSource, ['appId', 'app_id']))
        }
    }
    if (referencedAppIds.size() > 0) {
        for (namedApp in db.table('sym_application_t').where()
            .in('tid', referencedAppIds).eq('tenant_id', tenantId).eq('is_del', 0).select()) {
            appNamesById[text(first(namedApp, ['tid']))] = first(namedApp, ['appName', 'app_name'])
        }
    }
    var dbCountForApp = {}
    for (sourceId in sourceAppById.keySet()) {
        var ownerAppId = sourceAppById[sourceId]
        dbCountForApp[ownerAppId] = (dbCountForApp[ownerAppId] == null ? 0 : dbCountForApp[ownerAppId]) + 1
    }
    var tableCountForSource = {}
    var annotatedForSource = {}
    var registeredForSource = {}
    var problemForSource = {}
    var tableCountForApp = {}
    if (sourceIds.size() > 0) {
        for (sourceTable in db.table('db_table_t').where()
            .in('datasource_id', sourceIds).eq('tenant_id', tenantId).eq('is_del', 0).select()) {
            var statsSourceId = text(first(sourceTable, ['datasourceId', 'datasource_id']))
            tableCountForSource[statsSourceId] = (tableCountForSource[statsSourceId] == null ? 0 : tableCountForSource[statsSourceId]) + 1
            if (numberValue(first(sourceTable, ['annotated'])) == 1) {
                annotatedForSource[statsSourceId] = (annotatedForSource[statsSourceId] == null ? 0 : annotatedForSource[statsSourceId]) + 1
            }
            if (numberValue(first(sourceTable, ['assetStatus', 'asset_status'])) == 2) {
                registeredForSource[statsSourceId] = (registeredForSource[statsSourceId] == null ? 0 : registeredForSource[statsSourceId]) + 1
            } else {
                problemForSource[statsSourceId] = (problemForSource[statsSourceId] == null ? 0 : problemForSource[statsSourceId]) + 1
            }
            var statsAppId = sourceAppById[statsSourceId]
            if (!blank(statsAppId)) {
                tableCountForApp[statsAppId] = (tableCountForApp[statsAppId] == null ? 0 : tableCountForApp[statsAppId]) + 1
            }
        }
    }
    var appPropsById = {}
    if (appIds.size() > 0) {
        for (appProp in db.table('da_prop_t').where().in('parent_id', appIds)
            .eq('tenant_id', tenantId).eq('data_type', 'app').eq('is_del', 0).select()) {
            var propOwnerId = text(first(appProp, ['parentId', 'parent_id']))
            if (appPropsById[propOwnerId] == null) { appPropsById[propOwnerId] = {} }
            var appPropName = first(appProp, ['propName', 'prop_name'])
            if (!blank(appPropName)) { appPropsById[propOwnerId][appPropName] = first(appProp, ['propValue', 'prop_value']) }
        }
    }
    var catalogCountForSource = {}
    if (typeEnabled('db') && sourceIds.size() > 0) {
        // Dameng 的 prop_value 为 CLOB，不做 VARCHAR 等值比较；单次取出后在内存汇总。
        for (catalogProp in db.table('da_prop_t').where().eq('tenant_id', tenantId)
            .eq('prop_name', 'dbId').eq('data_type', 'catalog').eq('is_del', 0).select()) {
            var catalogSourceId = text(first(catalogProp, ['propValue', 'prop_value']))
            if (dbById[catalogSourceId] != null) {
                catalogCountForSource[catalogSourceId] = (catalogCountForSource[catalogSourceId] == null ? 0 : catalogCountForSource[catalogSourceId]) + 1
            }
        }
    }
    var catalogCountForApp = {}
    if (appIds.size() > 0) {
        for (catalogProp in db.table('da_prop_t').where().eq('tenant_id', tenantId)
            .eq('prop_name', 'appId').eq('data_type', 'catalog').eq('is_del', 0).select()) {
            var catalogAppId = text(first(catalogProp, ['propValue', 'prop_value']))
            if (appById[catalogAppId] != null) {
                catalogCountForApp[catalogAppId] = (catalogCountForApp[catalogAppId] == null ? 0 : catalogCountForApp[catalogAppId]) + 1
            }
        }
    }
    for (targetId in requestedIds) {
        var idText = text(targetId)
        if (blank(idText)) {
            continue
        }
        if (typeEnabled('db')) {
            var main = dbById[idText]
            if (main != null) {
                var doc = {}
                var props = connectionProps(main)
                var hasConnection = props != null && !props.isEmpty()
                doc.assetType = 'db'
                doc.dbName = first(main, ['dbName', 'db_name'])
                if (blank(doc.dbName)) {
                    doc.dbName = props.dbName
                }
                doc.database = blank(props.database) ? doc.dbName : scalar(props.database)
                doc.dbType = first(main, ['dbType', 'db_type'])
                if (blank(doc.dbType)) {
                    doc.dbType = scalar(props.dbType)
                }
                doc.appId = first(main, ['appId', 'app_id'])
                if (blank(doc.appId)) {
                    doc.appId = scalar(props.appId)
                }
                if (!blank(doc.appId)) {
                    doc.appName = appNamesById[text(doc.appId)]
                }
                doc.orgId = first(main, ['orgId', 'org_id'])
                if (blank(doc.orgId)) {
                    doc.orgId = scalar(props.orgId)
                }
                doc.showConnect = hasConnection ? 1 : 0
                var storedConnectionStatus = first(main, ['connectionStatus', 'connection_status'])
                doc.connectionStatus = blank(storedConnectionStatus) ? (hasConnection ? 'unknown' : 'unprovided') : text(storedConnectionStatus)
                doc.assetDesc = first(main, ['remark'])
                doc.dbVersion = first(main, ['dbVersion', 'db_version'])
                doc.storageDomain = first(main, ['storageDomain', 'storage_domain'])
                doc.nodeId = first(main, ['nodeId', 'node_id'])
                doc.contactName = scalar(props.contactName)
                doc.jdbcType = scalar(props.jdbcType)
                doc.dataSize = first(main, ['dataSize', 'data_size'])
                if (blank(doc.dataSize)) {
                    doc.dataSize = scalar(props.dataSize)
                }
                doc.indexSize = scalar(props.indexSize)
                doc.totalSize = first(main, ['totalSize', 'total_size'])
                doc.viewCount = first(main, ['viewCount', 'view_count'])
                if (doc.viewCount == null) {
                    doc.viewCount = scalar(props.viewCount)
                }
                doc.procedureCount = first(main, ['procedureCount', 'procedure_count'])
                if (doc.procedureCount == null) {
                    doc.procedureCount = scalar(props.procedureCount)
                }
                doc.userCount = first(main, ['userCount', 'user_count'])
                doc.assetStatus = first(main, ['assetStatus', 'asset_status']) == null ? 0 : first(main, ['assetStatus', 'asset_status'])
                doc.flowStatus = first(main, ['flowStatus', 'flow_status']) == null ? 0 : first(main, ['flowStatus', 'flow_status'])
                doc.tenantId = tenantId
                doc.regTime = first(main, ['regTime', 'reg_time'])
                doc.createdTime = first(main, ['createdTime', 'created_time'])
                doc.updatedTime = first(main, ['updatedTime', 'updated_time'])
                doc.regTime_date = doc.regTime
                doc.createdTime_date = doc.createdTime
                doc.updatedTime_date = doc.updatedTime
                var currentTableNum = tableCountForSource[idText] == null ? 0 : tableCountForSource[idText]
                var currentAnnotatedNum = annotatedForSource[idText] == null ? 0 : annotatedForSource[idText]
                var currentRegisteredNum = registeredForSource[idText] == null ? 0 : registeredForSource[idText]
                doc.tableNum = currentTableNum
                doc.annotatedTableNum = currentAnnotatedNum
                doc.unannotatedTableNum = currentTableNum - currentAnnotatedNum
                doc.registeredTableNum = currentRegisteredNum
                doc.unregisteredTableNum = currentTableNum - currentRegisteredNum
                doc.problemTableNum = problemForSource[idText] == null ? 0 : problemForSource[idText]
                doc.catalogNum = catalogCountForSource[idText] == null ? 0 : catalogCountForSource[idText]
                doc.tid = idText
                doc.id = idText
                targetIds.add(idText)
                targetDocs.add(doc)
                touched = touched + 1
                continue
            }
        }
        if (typeEnabled('app')) {
            var appMain = appById[idText]
            if (appMain != null) {
                var appDoc = {}
                var appProps = appPropsById[idText] == null ? {} : appPropsById[idText]
                putIndexProps(appDoc, appProps)
                appDoc.assetType = 'app'
                appDoc.appName = first(appMain, ['appName', 'app_name'])
                appDoc.assetDesc = first(appMain, ['appDesc', 'app_desc'])
                appDoc.appUrl = first(appMain, ['appUrl', 'app_url'])
                appDoc.provinceUrl = appDoc.appUrl
                appDoc.applicationClass = first(appMain, ['assetClass', 'asset_class'])
                // orgId is the data-source department. manageUnit, if supplied, remains an independent dynamic property.
                appDoc.orgId = appMain.orgId
                if (blank(appDoc.orgId)) { appDoc.orgId = appMain.org_id }
                if (blank(appDoc.orgId)) { appDoc.orgId = appMain.departId }
                if (blank(appDoc.orgId)) { appDoc.orgId = first(appMain, ['orgId', 'departId', 'org_id']) }
                appDoc.assetStatus = first(appMain, ['assetStatus', 'asset_status']) == null ? 2 : first(appMain, ['assetStatus', 'asset_status'])
                appDoc.flowStatus = first(appMain, ['flowStatus', 'flow_status']) == null ? 0 : first(appMain, ['flowStatus', 'flow_status'])
                appDoc.tenantId = tenantId
                appDoc.regTime = first(appMain, ['regTime', 'reg_time'])
                appDoc.createdTime = first(appMain, ['createdTime', 'created_time'])
                appDoc.updatedTime = first(appMain, ['updatedTime', 'updated_time'])
                appDoc.regTime_date = appDoc.regTime
                appDoc.createdTime_date = appDoc.createdTime
                appDoc.updatedTime_date = appDoc.updatedTime
                appDoc.dbNum = dbCountForApp[idText] == null ? 0 : dbCountForApp[idText]
                // 单系统保存也保留列表所需的表数、目录数，不因定向重建丢失聚合字段。
                appDoc.tableNum = tableCountForApp[idText] == null ? 0 : tableCountForApp[idText]
                appDoc.catalogNum = catalogCountForApp[idText] == null ? 0 : catalogCountForApp[idText]
                appDoc.tid = idText
                appDoc.id = idText
                targetIds.add(idText)
                targetDocs.add(appDoc)
                touched = touched + 1
                continue
            }
        }
        if (typeEnabled('table')) {
            var tableMain = tableById[idText]
            if (tableMain != null) {
                var tableDoc = {}
                tableDoc.assetType = 'table'
                tableDoc.dbId = first(tableMain, ['datasourceId', 'datasource_id'])
                tableDoc.datasourceId = tableDoc.dbId
                tableDoc.tableName = first(tableMain, ['tableName', 'table_name'])
                tableDoc.tableNameEn = tableDoc.tableName
                tableDoc.tableNameCn = first(tableMain, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment'])
                tableDoc.tableComment = first(tableMain, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn'])
                tableDoc.tableType = first(tableMain, ['tableType', 'table_type'])
                tableDoc.businessType = first(tableMain, ['businessType', 'business_type'])
                tableDoc.recordCount = first(tableMain, ['recordCount', 'record_count'])
                tableDoc.fieldCount = first(tableMain, ['fieldCount', 'field_count'])
                tableDoc.assetStatus = first(tableMain, ['assetStatus', 'asset_status'])
                tableDoc.flowStatus = first(tableMain, ['flowStatus', 'flow_status'])
                tableDoc.tenantId = tenantId
                tableDoc.regTime = first(tableMain, ['regTime', 'reg_time'])
                tableDoc.createdTime = first(tableMain, ['createdTime', 'created_time'])
                tableDoc.updatedTime = first(tableMain, ['updatedTime', 'updated_time'])
                tableDoc.regTime_date = tableDoc.regTime
                tableDoc.createdTime_date = tableDoc.createdTime
                tableDoc.updatedTime_date = tableDoc.updatedTime
                tableDoc.tid = idText
                tableDoc.id = idText
                targetIds.add(idText)
                targetDocs.add(tableDoc)
                touched = touched + 1
            }
        }
    }
    if (targetIds.size() > 0) {
        esCommonService.bulkSaveWithIds('dataassets', targetIds, targetDocs)
        if (typeEnabled('app')) {
            // 保留已有映射和字段类型；显式刷新当前租户索引，使保存后的首次查询读取新值。
            esOperations.indexOps(IndexCoordinates.of(esCommonService.resolveTenantIndexName('dataassets'))).refresh()
        }
    }
    return { refreshed: targetIds.size(), removed: 0, assetTypes: types, targeted: true, requested: tids.size(), touched: touched }
}

var propsByParent = {}
var tableCountByApp = {}
var catalogCountByApp = {}
var tableCountByDb = {}
var annotatedCountByDb = {}
var registeredCountByDb = {}
var problemCountByDb = {}
var catalogCountByDb = {}
for (prop in db.table('da_prop_t').where().eq('is_del', 0).select()) {
    var parentId = first(prop, ['parentId', 'parent_id'])
    var propName = first(prop, ['propName', 'prop_name'])
    var propValue = first(prop, ['propValue', 'prop_value'])
    var dataType = first(prop, ['dataType', 'data_type'])
    if (!blank(parentId)) {
        if (propsByParent[parentId] == null) {
            propsByParent[parentId] = {}
        }
        if (!blank(propName)) {
            propsByParent[parentId][propName] = propValue
        }
    }
    if (propName == 'appId' && dataType == 'catalog' && !blank(propValue)) {
        catalogCountByApp[propValue] = (catalogCountByApp[propValue] == null ? 0 : catalogCountByApp[propValue]) + 1
    }
    if (propName == 'dbId' && dataType == 'catalog' && !blank(propValue)) {
        catalogCountByDb[propValue] = (catalogCountByDb[propValue] == null ? 0 : catalogCountByDb[propValue]) + 1
    }
}
var appNames = {}
for (app in db.table('sym_application_t').where().eq('is_del', 0).select()) {
    appNames[first(app, ['tid'])] = first(app, ['appName', 'app_name'])
}
var dbCountByApp = {}
var sourceAppById = {}
for (source in db.table('db_datasource_t').where().eq('is_del', 0).select()) {
    var sourceId = first(source, ['tid'])
    var appId = first(source, ['appId', 'app_id'])
    sourceAppById[sourceId] = appId
    if (!blank(appId)) {
        dbCountByApp[appId] = (dbCountByApp[appId] == null ? 0 : dbCountByApp[appId]) + 1
    }
}
for (table in db.table('db_table_t').where().eq('is_del', 0).select()) {
    var sourceId = first(table, ['datasourceId', 'datasource_id'])
    var appId = sourceAppById[sourceId]
    tableCountByDb[sourceId] = (tableCountByDb[sourceId] == null ? 0 : tableCountByDb[sourceId]) + 1
    if (numberValue(first(table, ['annotated'])) == 1) {
        annotatedCountByDb[sourceId] = (annotatedCountByDb[sourceId] == null ? 0 : annotatedCountByDb[sourceId]) + 1
    }
    if (numberValue(first(table, ['assetStatus', 'asset_status'])) == 2) {
        registeredCountByDb[sourceId] = (registeredCountByDb[sourceId] == null ? 0 : registeredCountByDb[sourceId]) + 1
    }
    if (numberValue(first(table, ['assetStatus', 'asset_status'])) != 2) {
        problemCountByDb[sourceId] = (problemCountByDb[sourceId] == null ? 0 : problemCountByDb[sourceId]) + 1
    }
    if (!blank(appId)) {
        tableCountByApp[appId] = (tableCountByApp[appId] == null ? 0 : tableCountByApp[appId]) + 1
    }
}

var addDocument = (id, doc) => {
    if (!tidEnabled(id)) {
        return
    }
    doc.tid = id
    doc.id = id
    active[id] = true
    ids.add(id)
    docs.add(doc)
}
if (typeEnabled('app')) {
    for (main in db.table('sym_application_t').where().eq('is_del', 0).select()) {
        var id = first(main, ['tid'])
        var props = propsByParent[id] == null ? {} : propsByParent[id]
        var doc = {}
        putIndexProps(doc, props)
        doc.assetType = 'app'
        doc.appName = first(main, ['appName', 'app_name'])
        doc.assetDesc = first(main, ['appDesc', 'app_desc'])
        doc.appUrl = first(main, ['appUrl', 'app_url'])
        doc.provinceUrl = doc.appUrl
        doc.applicationClass = first(main, ['assetClass', 'asset_class'])
        // orgId is the data-source department. Do not overwrite dynamic manageUnit during a rebuild.
        doc.orgId = main.orgId
        if (blank(doc.orgId)) { doc.orgId = main.org_id }
        if (blank(doc.orgId)) { doc.orgId = main.departId }
        if (blank(doc.orgId)) { doc.orgId = first(main, ['orgId', 'departId', 'org_id']) }
        doc.assetStatus = first(main, ['assetStatus', 'asset_status']) == null ? 2 : first(main, ['assetStatus', 'asset_status'])
        doc.flowStatus = first(main, ['flowStatus', 'flow_status']) == null ? 0 : first(main, ['flowStatus', 'flow_status'])
        doc.tenantId = tenantId
        doc.regTime = first(main, ['regTime', 'reg_time'])
        doc.createdTime = first(main, ['createdTime', 'created_time'])
        doc.updatedTime = first(main, ['updatedTime', 'updated_time'])
        doc.regTime_date = doc.regTime; doc.createdTime_date = doc.createdTime; doc.updatedTime_date = doc.updatedTime
        doc.dbNum = dbCountByApp[id] == null ? 0 : dbCountByApp[id]
        doc.tableNum = tableCountByApp[id] == null ? 0 : tableCountByApp[id]
        doc.catalogNum = catalogCountByApp[id] == null ? 0 : catalogCountByApp[id]
        addDocument(id, doc)
    }
}
if (typeEnabled('db')) {
    for (main in db.table('db_datasource_t').where().eq('is_del', 0).select()) {
        var id = first(main, ['tid'])
        var props = connectionProps(main)
        var hasConnection = props != null && !props.isEmpty()
        var doc = {}
        doc.assetType = 'db'
        doc.dbName = first(main, ['dbName', 'db_name'])
        if (blank(doc.dbName)) {
            doc.dbName = props.dbName
        }
        doc.database = blank(props.database) ? doc.dbName : scalar(props.database)
        doc.dbType = first(main, ['dbType', 'db_type'])
        if (blank(doc.dbType)) {
            doc.dbType = scalar(props.dbType)
        }
        doc.appId = first(main, ['appId', 'app_id'])
        if (blank(doc.appId)) {
            doc.appId = scalar(props.appId)
        }
        doc.appName = blank(doc.appId) ? null : appNames[doc.appId]
        doc.orgId = first(main, ['orgId', 'org_id'])
        if (blank(doc.orgId)) {
            doc.orgId = scalar(props.orgId)
        }
        doc.showConnect = hasConnection ? 1 : 0
        var fullStoredConnectionStatus = first(main, ['connectionStatus', 'connection_status'])
        doc.connectionStatus = blank(fullStoredConnectionStatus) ? (hasConnection ? 'unknown' : 'unprovided') : text(fullStoredConnectionStatus)
        doc.assetDesc = first(main, ['remark'])
        doc.dbVersion = first(main, ['dbVersion', 'db_version'])
        doc.storageDomain = first(main, ['storageDomain', 'storage_domain'])
        doc.nodeId = first(main, ['nodeId', 'node_id'])
        doc.contactName = scalar(props.contactName)
        doc.jdbcType = scalar(props.jdbcType)
        doc.dataSize = scalar(props.dataSize)
        doc.indexSize = scalar(props.indexSize)
        doc.viewCount = scalar(props.viewCount)
        doc.procedureCount = scalar(props.procedureCount)
        doc.assetStatus = first(main, ['assetStatus', 'asset_status']) == null ? 0 : first(main, ['assetStatus', 'asset_status'])
        doc.flowStatus = first(main, ['flowStatus', 'flow_status']) == null ? 0 : first(main, ['flowStatus', 'flow_status'])
        doc.tenantId = tenantId
        doc.regTime = first(main, ['regTime', 'reg_time'])
        doc.createdTime = first(main, ['createdTime', 'created_time'])
        doc.updatedTime = first(main, ['updatedTime', 'updated_time'])
        doc.regTime_date = doc.regTime; doc.createdTime_date = doc.createdTime; doc.updatedTime_date = doc.updatedTime
        var fullTableNum = tableCountByDb[id] == null ? 0 : tableCountByDb[id]
        var fullAnnotatedNum = annotatedCountByDb[id] == null ? 0 : annotatedCountByDb[id]
        var fullRegisteredNum = registeredCountByDb[id] == null ? 0 : registeredCountByDb[id]
        doc.tableNum = fullTableNum
        doc.annotatedTableNum = fullAnnotatedNum
        doc.unannotatedTableNum = fullTableNum - fullAnnotatedNum
        doc.registeredTableNum = fullRegisteredNum
        doc.unregisteredTableNum = fullTableNum - fullRegisteredNum
        doc.problemTableNum = problemCountByDb[id] == null ? 0 : problemCountByDb[id]
        doc.catalogNum = catalogCountByDb[id] == null ? 0 : catalogCountByDb[id]
        addDocument(id, doc)
    }
}
if (typeEnabled('table')) {
    for (main in db.table('db_table_t').where().eq('is_del', 0).select()) {
        var id = first(main, ['tid'])
        var doc = {}
        doc.assetType = 'table'
        doc.dbId = first(main, ['datasourceId', 'datasource_id'])
        doc.datasourceId = doc.dbId
        doc.tableName = first(main, ['tableName', 'table_name'])
        doc.tableNameEn = doc.tableName
        doc.tableNameCn = first(main, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment'])
        doc.tableComment = first(main, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn'])
        doc.tableType = first(main, ['tableType', 'table_type'])
        doc.businessType = first(main, ['businessType', 'business_type'])
        doc.businessTypeReason = first(main, ['businessTypeReason', 'business_type_reason'])
        doc.annotated = first(main, ['annotated'])
        doc.recordCount = first(main, ['recordCount', 'record_count'])
        doc.fieldCount = first(main, ['fieldCount', 'field_count'])
        doc.storageSize = first(main, ['storageSize', 'storage_size'])
        doc.dataSourceType = first(main, ['dataSourceType', 'data_source_type'])
        doc.timestampField = first(main, ['timestampField', 'timestamp_field'])
        doc.orgId = first(main, ['orgId', 'org_id'])
        doc.orgPath = first(main, ['orgPath', 'org_path'])
        doc.assetStatus = first(main, ['assetStatus', 'asset_status'])
        doc.flowStatus = first(main, ['flowStatus', 'flow_status'])
        doc.tenantId = tenantId
        doc.regTime = first(main, ['regTime', 'reg_time'])
        doc.createdTime = first(main, ['createdTime', 'created_time'])
        doc.updatedTime = first(main, ['updatedTime', 'updated_time'])
        doc.regTime_date = doc.regTime; doc.createdTime_date = doc.createdTime; doc.updatedTime_date = doc.updatedTime
        addDocument(id, doc)
    }
}
if (typeEnabled('catalog')) {
    for (main in db.table('da_asset_t').where().eq('asset_type', 'catalog').eq('is_del', 0).select()) {
        var id = first(main, ['tid'])
        var doc = {}
        if (propsByParent[id] != null) {
            putIndexProps(doc, propsByParent[id])
        }
        doc.assetType = 'catalog'
        doc.assetStatus = first(main, ['assetStatus', 'asset_status'])
        doc.flowStatus = first(main, ['flowStatus', 'flow_status'])
        doc.tenantId = tenantId
        doc.regTime = first(main, ['regTime', 'reg_time'])
        doc.createdTime = first(main, ['createdTime', 'created_time'])
        doc.updatedTime = first(main, ['updatedTime', 'updated_time'])
        doc.regTime_date = doc.regTime; doc.createdTime_date = doc.createdTime; doc.updatedTime_date = doc.updatedTime
        addDocument(id, doc)
    }
}
if (ids.size() > 0) {
    esCommonService.bulkSaveWithIds('dataassets', ids, docs)
}

var removed = 0
if (body?.purgeStale != false && (tids == null || tids.size() == 0)) {
    var existing = esCommonService.searchByJsonQuery('dataassets', jsons.toJSONStr({ bool: { filter: [ { terms: { assetType: types } } ] } }), Map.class, 1, 10000)
    var stale = []
    for (doc in existing.list) {
        var id = doc.tid
        if (!blank(id) && active[id] != true) {
            stale.add(id)
        }
    }
    if (stale.size() > 0) {
        removed = esCommonService.bulkDeleteByIds('dataassets', stale)
    }
}
return { refreshed: ids.size(), removed: removed, assetTypes: types }
