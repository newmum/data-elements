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
  "description": "分页读取接入任务、表同步明细和 NiFi 运行状态快照。按当前登录租户及有效来源数据源的 ALL、ORG 或 OWNER 范围查询。支持 pageNum、pageSize、taskName、taskStatus、nifiNetworkCode、nifiNodeId、sortField 和 sortDir，返回分页数据和状态快照信息。仅当当前租户无有效业务任务时，ALL 范围可读取历史流水线。租户或数据范围无效时拒绝，不支持的任务状态返回 400。只读。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================

import dataScope
import jsons
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider
import tableRead
import tableSort
import taskRuntime
import tenantRuntime

/**
 * POST /ods/task/page 分页读取接入任务和表同步明细，附带本次 NiFi 运行状态快照。
 * 支持任务名称、状态、节点、网络筛选和白名单排序，返回 pageNum、pageSize、total 和 list。
 * 按可信租户及来源数据源的 ALL、ORG、OWNER 范围读取。仅租户没有有效任务且范围为 ALL 时，
 * 兼容显示旧流水线。接口只读取数据和运行状态快照，无业务写入。
 */

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

var sortField = tableSort.field(body, {
    taskName: 'taskName',
    lastRunning: 'lastRunning',
    endRunning: 'endRunning',
    createdTime: 'createdTime',
    updatedTime: 'updatedTime',
    flowFilesIn: 'flowFilesIn',
    flowFilesOut: 'flowFilesOut',
    queuedCount: 'queuedCount',
    bytesIn: 'bytesIn',
    bytesOut: 'bytesOut',
    activeThreadCount: 'activeThreadCount'
}, 'updatedTime')
var sortDirection = tableSort.direction(body)
var pageNum = body?.pageNum == null ? 1 : body.pageNum::long
var pageSize = body?.pageSize == null ? 20 : body.pageSize::long
pageNum = Math.max(1, pageNum)
pageSize = Math.min(100, pageSize < 1 ? 20 : pageSize)::int
var status = text(body?.taskStatus)
var keyword = text(body?.taskName)
var tenantId = text(tenantRuntime.id())
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
var live = taskRuntime.snapshot()
var scope = dataScope.dataSource()
var scopeMode = text(scope?.scope)
var scopeUserId = text(scope?.userId)
var scopeOrgIds = scope?.organizationIds || []
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId == '') {
    exit 403, '无法确定当前数据范围'
}
if (status != '' && status != 'all' && !['0', '1', '2', '3'].contains(status)) {
    exit 400, '不支持的任务状态'
}

var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
var numberColumn = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var timeColumn = (alias, name) => tableRead.column(alias, name, 'TIME')
var active = alias => tableRead.equal(numberColumn(alias, 'is_del'), 0)

var groupMatches = (processGroup, groupStatus) => {
    var batches = []
    for (batch in live.get('groups' + groupStatus) || []) {
        batches.add(tableRead.in(processGroup, batch))
    }
    return tableRead.any(batches)
}

var statusFilter = (query, processGroup, lifecycle) => {
    if (status == '' || status == 'all') {
        return
    }
    // 运行组从同一份快照分批匹配。空分组和缺失生命周期按未部署状态处理。
    var hasGroup = tableRead.not(tableRead.isNull(tableRead.blankAsNull(processGroup)))
    var noGroup = tableRead.not(hasGroup)
    var running = tableRead.equal(lifecycle, 'RUNNING')
    var failed = tableRead.equal(lifecycle, 'DEPLOY_FAILED')
    if (status == '0') {
        var undeployed = tableRead.any([tableRead.isNull(lifecycle), tableRead.not(tableRead.any([running, failed]))])
        query.where(tableRead.any([groupMatches(processGroup, 0), tableRead.all([noGroup, undeployed])]))
    } else if (status == '1') {
        query.where(groupMatches(processGroup, 1))
    } else if (status == '2') {
        query.where(tableRead.any([groupMatches(processGroup, 2), tableRead.all([noGroup, failed])]))
    } else {
        var knownGroup = tableRead.any([
            groupMatches(processGroup, 0), groupMatches(processGroup, 1), groupMatches(processGroup, 2)
        ])
        query.where(tableRead.any([
            tableRead.all([hasGroup, tableRead.not(knownGroup)]), tableRead.all([noGroup, running])
        ]))
    }
}

var pageResult = (query, readRows) => {
    if (pageNum > 2147483647) {
        // 兼容长整型页码。超出分页组件整数范围时仍返回筛选总数和原请求页码。
        return { total: query.grouped({ total: tableRead.count() }, [])[0].total, rows: [] }
    }
    return readRows(pageNum::int)
}

var response = (total, list) => {
    return {
        pageNum: pageNum,
        pageSize: pageSize,
        total: total,
        list: list,
        runtimeRevision: live.revision,
        runtimeFresh: live.fresh,
        runtimeObservedAt: live.observedAt,
        runtimeMessage: live.message
    }
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

var datasourceById = {}
var tableById = {}
var datasourceName = id => datasourceById[text(id)]?.dbName
var tableRow = id => tableById[text(id)]
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

// 来源数据源关联参与 ORG、OWNER 权限过滤。ALL 仍可查看来源已失效的本租户任务。
var tasks = tableRead.from('data_access_agg_task_t', 'a', tenantId)
tasks.leftJoin('db_datasource_t', 'd', 'tid', 'source_db_id', [active('d')])
tasks.leftJoin('nifi_pipeline_t', 'p', 'id', 'pipeline_id', [active('p')])
tasks.where(active('a'))
if (scopeMode == 'OWNER') {
    tasks.where(tableRead.equal(textColumn('d', 'created_by'), scopeUserId))
} else if (scopeMode == 'ORG') {
    tasks.where(tableRead.in(textColumn('d', 'org_id'), scopeOrgIds))
}
var keywordPattern = '%' + keyword + '%'
if (keyword != '') {
    // 保留旧查询的 LIKE 通配符及数据库自身的任务名排序规则。
    tasks.where(tableRead.like(textColumn('a', 'task_name'), keywordPattern))
}

// 流水线记录存在时使用它的运行组。流水线的空运行组表示尚未部署。
var lifecycle = textColumn('p', 'status')
var processGroup = tableRead.when(tableRead.isNull(textColumn('p', 'id')),
    textColumn('a', 'process_group_id'), textColumn('p', 'nifi_process_group_id'))
statusFilter(tasks, processGroup, lifecycle)

var taskSorts = {
    taskName: textColumn('a', 'task_name'),
    lastRunning: textColumn('a', 'last_running'),
    endRunning: textColumn('a', 'end_running'),
    createdTime: timeColumn('a', 'created_time'),
    updatedTime: timeColumn('a', 'updated_time'),
    flowFilesIn: numberColumn('a', 'flow_files_in'),
    flowFilesOut: numberColumn('a', 'flow_files_out'),
    queuedCount: numberColumn('a', 'queued_count'),
    bytesIn: numberColumn('a', 'bytes_in'),
    bytesOut: numberColumn('a', 'bytes_out'),
    activeThreadCount: numberColumn('a', 'active_thread_count')
}
var taskPage = pageResult(tasks, number => tasks.pageRootRows({
    lifecycle_status: lifecycle,
    runtime_process_group_id: processGroup
}, taskSorts[sortField], sortDirection, 'tid', number, pageSize))

// 沿用 Magic API 的列名映射，保留不同部署中的全部任务列及其驼峰键名。
var camelColumns = new CamelColumnMapperProvider()
var taskRows = []
var pageDatasourceIds = []
var pageTableIds = []
var collectIds = (ids, values) => {
    for (value in values) {
        if (!blank(value) && !ids.contains(value)) {
            ids.add(value)
        }
    }
}
for (row in taskPage.rows) {
    var task = {}
    for (key in row.keySet()) {
        task[camelColumns.mapping(key)] = row[key]
    }
    taskRows.add(task)
    collectIds(pageDatasourceIds, [task.sourceDbId, task.targetDbId])
    collectIds(pageTableIds, [task.sourceTableId, task.targetTableId])
}

// 只对本页任务批量补齐来源和目标名称，查询数不随页内任务数量增长。
if (pageDatasourceIds.size() > 0) {
    var sources = db.table('db_datasource_t').columns('tid', 'db_name').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', pageDatasourceIds).select()
    for (source in sources) {
        datasourceById[text(source.tid)] = source
    }
}
if (pageTableIds.size() > 0) {
    var tables = db.table('db_table_t').columns('tid', 'table_name', 'table_name_cn', 'record_count').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', pageTableIds).select()
    for (table in tables) {
        tableById[text(table.tid)] = table
    }
}
var decorated = []
for (row in taskRows) {
    decorated.add(decorateTaskRow(row))
}
taskRuntime.decorate(decorated, live)

// 有效任务存在时，即使请求页或筛选结果为空也不会回退到旧流水线。
if (scopeMode != 'ALL' || taskPage.total > 0) {
    return response(taskPage.total, decorated)
}
var existingTask = db.table('data_access_agg_task_t').columns('tid').where()
    .eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (existingTask != null) {
    return response(taskPage.total, decorated)
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

// 无来源归属的历史流水线仅在 ALL 范围可见。历史时间排序沿用各数据库的空值顺序。
var pipelineFields = {
    id: textColumn('p', 'id'),
    name: textColumn('p', 'name'),
    description: textColumn('p', 'description'),
    status: textColumn('p', 'status'),
    dslJson: tableRead.column('p', 'dsl_json', 'LOB'),
    nifiProcessGroupId: textColumn('p', 'nifi_process_group_id'),
    lastDeployedAt: numberColumn('p', 'last_deployed_at'),
    lastStoppedAt: numberColumn('p', 'last_stopped_at'),
    createdAt: numberColumn('p', 'created_at'),
    updatedAt: numberColumn('p', 'updated_at')
}
var pipelines = tableRead.from('nifi_pipeline_t', 'p', tenantId)
pipelines.where(active('p'))
if (keyword != '') {
    pipelines.where(tableRead.like(tableRead.lower(pipelineFields.name), keywordPattern))
}
statusFilter(pipelines, pipelineFields.nifiProcessGroupId, pipelineFields.status)

var pipelineSorts = {
    taskName: pipelineFields.name,
    lastRunning: pipelineFields.lastDeployedAt,
    endRunning: pipelineFields.lastStoppedAt,
    createdTime: pipelineFields.createdAt,
    updatedTime: pipelineFields.updatedAt
}
var pipelineSort = pipelineSorts[sortField]
// 旧流水线没有任务监控指标，指标排序只按流水线 ID 升序。
var pipelineDirection = pipelineSort == null ? 'asc' : sortDirection
var pipelineOrder = pipelineSort == null ? pipelineFields.id : pipelineSort
var pipelinePage = pageResult(pipelines, number => pipelines.page(pipelineFields,
    pipelineOrder, pipelineDirection, 'id', 'asc', number, pageSize, true))

var filtered = []
for (pipeline in pipelinePage.rows) {
    var taskStatus = statusOf(pipeline.status)
    var dslInfo = extractDslInfo(pipeline)
    var item = {
        tid: pipeline.id,
        id: pipeline.id,
        pipelineId: pipeline.id,
        processGroupId: pipeline.nifiProcessGroupId,
        runtimeProcessGroupId: pipeline.nifiProcessGroupId,
        lifecycleStatus: pipeline.status,
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
    filtered.add(item)
}

taskRuntime.decorate(filtered, live)
return response(pipelinePage.total, filtered)
