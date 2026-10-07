{
  "properties": {},
  "id": "1155bcd0ccad47b49a6c4a94251cf52c",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "01.任务监控分页查询",
  "createTime": 1782955344777,
  "updateTime": 1790833930101,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20,\n  \"taskStatus\": \"all\",\n  \"taskName\": \"出入境\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"code\": 0,\n  \"msg\": \"success\",\n  \"success\": true,\n  \"data\": {\n    \"total\": 1,\n    \"list\": [\n      {\n        \"tid\": \"示例任务ID\",\n        \"taskName\": \"数据接入同步任务\",\n        \"taskStatus\": 1,\n        \"sourceDbName\": \"出入境人员动管系统数据库\",\n        \"sourceTableSummary\": \"3 张表\",\n        \"targetDbName\": \"目标库\",\n        \"targetTableSummary\": \"3 张表\",\n        \"syncTables\": [\n          {\n            \"sourceTableName\": \"police_person_info\",\n            \"targetTableName\": \"ods_police_person_info\",\n            \"syncStatus\": 1,\n            \"syncStatusText\": \"运行中\"\n          }\n        ]\n      }\n    ]\n  }\n}",
  "description": "任务监控分页查询（POST /ods/task/page）。只读接口；优先读取 data_access_agg_task_t，并补充 db_datasource_t、db_table_t 形成任务-表同步明细；旧任务表为空时兼容读取 nifi_pipeline_t 流水线快照；用于任务管理页面主表展开查看表同步情况；使用请求参数 pageNum、pageSize、taskStatus、taskName、nifiNetworkCode、nifiNodeId。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.任务监控分页查询
// 路由：POST /ods/task/page
// 业务说明：任务监控分页查询（POST /ods/task/page）。只读接口；优先读取 data_access_agg_task_t，并补充 db_datasource_t、db_table_t 形成任务-表同步明细；旧任务表为空时兼容读取 nifi_pipeline_t 流水线快照；用于任务管理页面主表展开查看表同步情况；使用请求参数 pageNum、pageSize、taskStatus、taskName、nifiNetworkCode、nifiNodeId。
/*
 * 任务监控分页查询（POST /ods/task/page）
 *
 * 业务语义：任务管理页面以“任务”为主行，展开后查看该任务下每张表的同步情况。
 * 读取范围：data_access_agg_task_t / db_datasource_t / db_table_t / nifi_pipeline_t。
 * 兼容策略：新登记的接入任务读 data_access_agg_task_t；如果旧任务表为空，则回退 nifi_pipeline_t。
 * 写入行为：只读，不创建任务、不启动任务、不更新运行状态。
 */
import java.util.ArrayList
import jsons
import tenantRuntime

var text = value => {
    if (value == null) {
        return ''
    }
    return (value + '').trim()
}

var blank = value => text(value) == ''

var firstText = (config, keys) => {
    if (config == null) {
        return null
    }
    for (key in keys) {
        var value = config[key]
        if (!blank(value)) {
            return text(value)
        }
    }
    return null
}

var pageNum = body?.pageNum == null ? 1 : body.pageNum::long
var pageSize = body?.pageSize == null ? 20 : body.pageSize::long
pageNum = pageNum < 1 ? 1 : pageNum
pageSize = pageSize < 1 ? 20 : pageSize
var offset = (pageNum - 1) * pageSize
var status = text(body?.taskStatus)
var keyword = text(body?.taskName).toLowerCase()
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录后查看接入任务'
}

// 当前运行态由租户默认的数据接入节点负责。历史任务尚未记录独立节点时，
// 按该实际运行节点过滤，避免树形筛选出现“选了节点却仍混入其他节点任务”。
var selectedNetworkCode = text(body?.nifiNetworkCode)
var selectedNodeId = text(body?.nifiNodeId)
var runtimeNode = db.table('nifi_node_t')
    .columns('tid', 'network_code', 'node_code', 'node_name')
    .where().eq('tenant_id', tenantId).eq('is_del', 0).eq('enabled', 1)
    .orderBy('is_default', 'desc').orderBy('updated_time', 'desc').selectOne()
var runtimeNodeId = runtimeNode == null ? '' : text(runtimeNode.tid)
var runtimeNetworkCode = runtimeNode == null ? '' : text(runtimeNode.networkCode)
var matchesRuntimeNode = (blank(selectedNetworkCode) || selectedNetworkCode == runtimeNetworkCode)
    && (blank(selectedNodeId) || selectedNodeId == runtimeNodeId)
if (!matchesRuntimeNode) {
    return { pageNum: pageNum, pageSize: pageSize, total: 0, list: [] }
}

// 运行状态归一：0 未启用，1 运行中，2 运行异常。
var statusOf = value => {
    var normalized = text(value).toUpperCase()
    if (normalized == 'RUNNING' || normalized == 'STARTING' || normalized == 'DEPLOYING') {
        return 1
    }
    if (normalized.contains('ERROR') || normalized == 'INVALID' || normalized == 'FAILED') {
        return 2
    }
    return 0
}

var datasourceName = id => {
    if (blank(id)) {
        return null
    }
    var row = db.table('db_datasource_t').columns('db_name').where()
        .eq('tid', id).eq('is_del', 0).selectOne()
    return row == null ? null : row.dbName
}

var tableRow = id => {
    if (blank(id)) {
        return null
    }
    return db.table('db_table_t').columns('tid', 'table_name', 'table_name_cn', 'catalog_name', 'record_count').where()
        .eq('tid', id).eq('is_del', 0).selectOne()
}

var tableDisplayName = row => {
    if (row == null) {
        return null
    }
    return !blank(row.tableNameCn) ? row.tableNameCn : row.tableName
}

var scheduleText = row => {
    if (!blank(row.cronExpress)) {
        return row.cronExpress
    }
    if (row.scheduleStrategy == 0) {
        return '实时'
    }
    if (row.scheduleCycle == 1) {
        return '每日'
    }
    if (row.scheduleCycle == 2) {
        return '每周'
    }
    if (row.scheduleCycle == 3) {
        return '每月'
    }
    return blank(row.scheduleCycle) ? null : row.scheduleCycle
}

var statusTextOf = value => {
    var v = value::int
    if (v == 1) {
        return '运行中'
    }
    if (v == 2) {
        return '运行异常'
    }
    return '未启用'
}

var matchesKeyword = row => {
    if (blank(keyword)) {
        return true
    }
    var content = (
        text(row.taskName) + ' ' +
        text(row.sourceDbName) + ' ' +
        text(row.sourceTableName) + ' ' +
        text(row.targetDbName) + ' ' +
        text(row.targetTableName)
    ).toLowerCase()
    return content.contains(keyword)
}

var decorateTaskRow = row => {
    var sourceTable = tableRow(row.sourceTableId)
    var targetTable = tableRow(row.targetTableId)
    row.sourceDbName = datasourceName(row.sourceDbId)
    row.targetDbName = datasourceName(row.targetDbId)
    row.sourceTableName = tableDisplayName(sourceTable)
    row.targetTableName = tableDisplayName(targetTable)
    row.targetPhysicalTableName = targetTable == null ? null : targetTable.tableName
    row.sourceTableSummary = row.sourceTableName
    row.targetTableSummary = row.targetTableName
    row.scheduleFrequency = scheduleText(row)
    row.nifiNodeId = runtimeNodeId
    row.nifiNetworkCode = runtimeNetworkCode
    row.nifiNodeName = runtimeNode == null ? null : runtimeNode.nodeName
    row.syncTables = [{
        tid: row.tid,
        taskId: row.tid,
        sourceDbName: row.sourceDbName,
        sourceTableId: row.sourceTableId,
        sourceTableName: row.sourceTableName,
        targetDbName: row.targetDbName,
        targetTableId: row.targetTableId,
        targetTableName: row.targetTableName,
        targetPhysicalTableName: row.targetPhysicalTableName,
        primaryKey: row.sourceTablePrimaryKey,
        incrementKey: row.sourceTableIncrementKey,
        dataCount: sourceTable == null ? null : sourceTable.recordCount,
        lastRunning: row.lastRunning,
        endRunning: row.endRunning,
        syncStatus: row.taskStatus,
        syncStatusText: statusTextOf(row.taskStatus),
        monitorStatus: row.monitorStatus,
        monitorMsg: row.monitorMsg
    }]
    return row
}

// 主任务表查询：这里保持按 data_access_agg_task_t 分页，避免一次性读取大量运行监控数据。
var query = db.table('data_access_agg_task_t').where().eq('tenant_id', tenantId).eq('is_del', 0)
if (!blank(status) && status != 'all') {
    var statusValue = status::int
    if (statusValue == 2) {
        query.eq('task_status', 1).in('monitor_status', ['ERROR', 'Error', 'error'])
    } else if (statusValue == 1) {
        query.eq('task_status', 1).notIn('monitor_status', ['ERROR', 'Error', 'error'])
    } else {
        query.eq('task_status', statusValue)
    }
}
if (!blank(body?.taskName)) {
    query.like('task_name', '%' + body.taskName + '%')
}

var result = query.orderBy('updated_time', 'desc').page(pageSize, offset)
result.list = result.list == null ? [] : result.list
var decorated = new ArrayList()
for (row in result.list) {
    var item = decorateTaskRow(row)
    if (matchesKeyword(item)) {
        decorated.add(item)
    }
}
result.list = decorated

// 如果业务任务表为空，兼容展示 data-elements-yunti 迁移过来的 NiFi 流水线。
var existingTask = db.table('data_access_agg_task_t').columns('tid').where()
    .eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
var taskTotal = existingTask == null ? 0 : 1
if (taskTotal > 0) {
    return result
}

var parseDatabaseFromJdbcUrl = jdbcUrl => {
    var url = text(jdbcUrl)
    if (blank(url)) {
        return null
    }
    var queryIndex = url.indexOf('?')
    if (queryIndex >= 0) {
        url = url.substring(0, queryIndex)
    }
    var slashIndex = url.lastIndexOf('/')
    if (slashIndex >= 0 && slashIndex < url.length() - 1) {
        return url.substring(slashIndex + 1)
    }
    return null
}

var extractDslInfo = pipeline => {
    var info = {
        sourceDbName: null,
        sourceTableName: null,
        targetDbName: null,
        targetTableName: null,
        scheduleFrequency: null
    }
    var dslText = text(pipeline.dslJson == null ? pipeline.dsl_json : pipeline.dslJson)
    if (blank(dslText)) {
        return info
    }
    var dsl = jsons.toJSONObject(dslText)
    var nodes = dsl == null ? null : dsl.nodes
    if (nodes == null) {
        return info
    }
    for (node in nodes) {
        var category = text(node.category).toLowerCase()
        var manifestKey = text(node.manifestKey == null ? node.manifest_key : node.manifestKey).toLowerCase()
        var config = node.config
        if (config != null && text(config).startsWith('{')) {
            config = jsons.toJSONObject(text(config))
        }
        var isSource = category == 'source' || manifestKey.startsWith('source.')
        var isTarget = category == 'sink' || category == 'target'
            || manifestKey.startsWith('sink.') || manifestKey.startsWith('target.')
        if (isSource) {
            info.sourceDbName = info.sourceDbName || firstText(config, ['database', 'dbName', 'host', 'bucket'])
            info.sourceTableName = info.sourceTableName || firstText(config, ['table', 'tableName', 'topic', 'path'])
            info.scheduleFrequency = info.scheduleFrequency || firstText(config, ['schedulingPeriod', 'cronExpression'])
        }
        if (isTarget) {
            info.targetDbName = info.targetDbName || firstText(config, ['database', 'dbName'])
                || parseDatabaseFromJdbcUrl(config == null ? null : config.jdbcUrl)
            info.targetTableName = info.targetTableName || firstText(config, ['table', 'tableName', 'targetTable', 'topic'])
        }
    }
    return info
}

var filtered = new ArrayList()
var pipelines = db.table('nifi_pipeline_t').where().eq('tenant_id', tenantId).eq('is_del', 0).orderBy('updated_at', 'desc').select()
for (pipeline in pipelines) {
    var taskStatus = statusOf(pipeline.status)
    if (!blank(status) && status != 'all' && taskStatus != status::int) {
        continue
    }
    var dslInfo = extractDslInfo(pipeline)
    var item = {
        tid: pipeline.id,
        id: pipeline.id,
        pipelineId: pipeline.id,
        processGroupId: pipeline.nifiProcessGroupId,
        recordSource: 'pipeline',
        taskName: pipeline.name,
        taskDesc: pipeline.description,
        taskStatus: taskStatus,
        currentStatus: pipeline.status,
        lastRunning: pipeline.lastDeployedAt,
        endRunning: pipeline.lastStoppedAt,
        scheduleRunning: dslInfo.scheduleFrequency,
        scheduleFrequency: dslInfo.scheduleFrequency,
        sourceDbName: dslInfo.sourceDbName,
        sourceTableName: dslInfo.sourceTableName,
        sourceTableSummary: dslInfo.sourceTableName,
        targetDbName: dslInfo.targetDbName,
        targetTableName: dslInfo.targetTableName,
        targetTableSummary: dslInfo.targetTableName,
        createdTime: pipeline.createdAt,
        updatedTime: pipeline.updatedAt,
        syncTables: [{
            taskId: pipeline.id,
            sourceDbName: dslInfo.sourceDbName,
            sourceTableName: dslInfo.sourceTableName,
            targetDbName: dslInfo.targetDbName,
            targetTableName: dslInfo.targetTableName,
            lastRunning: pipeline.lastDeployedAt,
            endRunning: pipeline.lastStoppedAt,
            syncStatus: taskStatus,
            syncStatusText: statusTextOf(taskStatus),
            monitorStatus: pipeline.status,
            monitorMsg: pipeline.description
        }]
    }
    if (matchesKeyword(item)) {
        filtered.add(item)
    }
}

var start = offset
var end = offset + pageSize
if (start < 0) {
    start = 0
}
if (end > filtered.size()) {
    end = filtered.size()
}
return {
    pageNum: pageNum,
    pageSize: pageSize,
    total: filtered.size(),
    list: start >= filtered.size() ? [] : new ArrayList(filtered.subList(start::int, end::int))
}
