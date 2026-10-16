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


import tableSort
var sortColumn = tableSort.field(body, {'taskName': 'task_name', 'lastRunning': 'last_running', 'endRunning': 'end_running', 'createdTime': 'created_time', 'updatedTime': 'updated_time','flowFilesIn':'flow_files_in','flowFilesOut':'flow_files_out','queuedCount':'queued_count','bytesIn':'bytes_in','bytesOut':'bytes_out','activeThreadCount':'active_thread_count'}, 'updatedTime')
var sortDirection = tableSort.direction(body)

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
import taskRuntime

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
pageSize = Math.min(100,pageSize < 1 ? 20 : pageSize)
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
var live = taskRuntime.snapshot()
var runtimePg = 'CASE WHEN p.id IS NOT NULL THEN p.nifi_process_group_id ELSE a.process_group_id END'
var hasPg = '('+runtimePg+" IS NOT NULL AND "+runtimePg+"<>'')"
var groupSql = groupStatus => {
    var parts=[]
    var batches=live.get('groups'+groupStatus)||[]
    var index=0
    for(batch in batches) {
        parts.add(runtimePg+' IN <foreach collection="live.groups'+groupStatus+'['+index+']" item="pg" open="(" close=")" separator=",">#{pg}</foreach>')
        index=index+1
    }
    return parts.size()==0?'1=0':'('+parts.join(' OR ')+')'
}
var stoppedSql=groupSql(0)
var runningSql=groupSql(1)
var invalidSql=groupSql(2)
var unknownSql=groupSql(3)
var knownSql='('+stoppedSql+' OR '+runningSql+' OR '+invalidSql+')'
var undeployedSql='(NOT '+hasPg+' AND COALESCE(p.status,\'\')<>\'RUNNING\' AND COALESCE(p.status,\'\')<>\'DEPLOY_FAILED\')'
var failedSql='(NOT '+hasPg+' AND p.status=\'DEPLOY_FAILED\')'

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

// Page enrichment is two bulk reads, independent of row count.
var datasourceById={}
var tableById={}
var datasourceName=id=>datasourceById[text(id)]?.dbName
var tableRow=id=>tableById[text(id)]
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
import dataScope
var scope=dataScope.dataSource();
var scopeMode=text(scope?.scope);
var scopeUserId=text(scope?.userId);
var scopeOrgIds=scope?.organizationIds||[];
if(!['ALL','ORG','OWNER'].contains(scopeMode)||scopeUserId=='') {exit 403,'无法确定当前数据范围';}
var sourceScope=scopeMode=='ALL'?'1=1':scopeMode=='OWNER'?'d.created_by=#{scopeUserId}':scopeOrgIds.size()==0?'1=0':'d.org_id in <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>';
var keywordPattern='%'+text(body?.taskName)+'%';
var statusSql='';
if(!blank(status)&&status!='all') {
 if(!['0','1','2','3'].contains(status)) {exit 400,'不支持的任务状态';}
 var condition=status=='0'?'('+stoppedSql+' OR '+undeployedSql+')':status=='1'?runningSql:status=='2'?'('+invalidSql+' OR '+failedSql+')':'(('+hasPg+' AND NOT '+knownSql+') OR (NOT '+hasPg+' AND p.status=\'RUNNING\'))';
 statusSql=' AND ('+condition+')';
}
var result=db.page("""
 SELECT a.*,p.status lifecycle_status,CASE WHEN p.id IS NOT NULL THEN p.nifi_process_group_id ELSE a.process_group_id END runtime_process_group_id
 FROM data_access_agg_task_t a
 LEFT JOIN db_datasource_t d ON d.tid=a.source_db_id AND d.tenant_id=a.tenant_id AND d.is_del=0
 LEFT JOIN nifi_pipeline_t p ON p.id=a.pipeline_id AND p.tenant_id=a.tenant_id AND p.is_del=0
 WHERE a.tenant_id=#{tenantId} AND a.is_del=0 AND (${sourceScope})
 AND (#{keyword}='' OR a.task_name LIKE #{keywordPattern})
 """+statusSql+" ORDER BY CASE WHEN a."+sortColumn+" IS NULL THEN 1 ELSE 0 END ASC,a."+sortColumn+" "+sortDirection+",a.tid ASC",pageSize,offset);
var taskFields={task_name:'taskName',task_desc:'taskDesc',source_db_id:'sourceDbId',target_db_id:'targetDbId',source_table_id:'sourceTableId',target_table_id:'targetTableId',source_table_primary_key:'sourceTablePrimaryKey',source_table_increment_key:'sourceTableIncrementKey',schedule_strategy:'scheduleStrategy',schedule_cycle:'scheduleCycle',schedule_running:'scheduleRunning',last_running:'lastRunning',end_running:'endRunning',created_time:'createdTime',updated_time:'updatedTime',pipeline_id:'pipelineId',process_group_id:'processGroupId',task_status:'taskStatus',monitor_status:'monitorStatus',monitor_msg:'monitorMsg',flow_files_in:'flowFilesIn',flow_files_out:'flowFilesOut',queued_count:'queuedCount',bytes_in:'bytesIn',bytes_out:'bytesOut',active_thread_count:'activeThreadCount',monitor_time:'monitorTime'};
for(row in result.list||[]) {for(field in taskFields.keySet()) {var camel=taskFields.get(field);row[camel]=row[camel]==null?row[field]:row[camel];}}
result.list = result.list == null ? [] : result.list
var pageDatasourceIds=[]
var pageTableIds=[]
for(row in result.list) {
    for(value in [row.sourceDbId,row.targetDbId]) {if(!blank(value) && !pageDatasourceIds.contains(value)) {pageDatasourceIds.add(value)}}
    for(value in [row.sourceTableId,row.targetTableId]) {if(!blank(value) && !pageTableIds.contains(value)) {pageTableIds.add(value)}}
}
if(pageDatasourceIds.size()>0) {
    var sources=db.table('db_datasource_t').columns('tid','db_name').where()
        .eq('tenant_id',tenantId).eq('is_del',0).in('tid',pageDatasourceIds).select()
    for(source in sources) {datasourceById[text(source.tid)]=source}
}
if(pageTableIds.size()>0) {
    var tables=db.table('db_table_t').columns('tid','table_name','table_name_cn','record_count').where()
        .eq('tenant_id',tenantId).eq('is_del',0).in('tid',pageTableIds).select()
    for(table in tables) {tableById[text(table.tid)]=table}
}
var decorated = new ArrayList()
for (row in result.list) {
    var item = decorateTaskRow(row)
    decorated.add(item)
}
result.list = decorated
taskRuntime.decorate(result.list,live)
var liveResult={pageNum:pageNum,pageSize:pageSize,total:result.total,list:result.list,runtimeRevision:live.revision,runtimeFresh:live.fresh,runtimeObservedAt:live.observedAt,runtimeMessage:live.message}

// 如果业务任务表为空，兼容展示 data-elements-yunti 迁移过来的 NiFi 流水线。
var existingTask = db.table('data_access_agg_task_t').columns('tid').where()
    .eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
var taskTotal = existingTask == null ? 0 : 1
if (taskTotal > 0) {
    return liveResult
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
var pipelineSortColumn=tableSort.field(body,{taskName:'name',lastRunning:'last_deployed_at',endRunning:'last_stopped_at',createdTime:'created_at',updatedTime:'updated_at',flowFilesIn:'NULLIF(id,id)',flowFilesOut:'NULLIF(id,id)',queuedCount:'NULLIF(id,id)',bytesIn:'NULLIF(id,id)',bytesOut:'NULLIF(id,id)',activeThreadCount:'NULLIF(id,id)'},'updatedTime');
var pipelineStatusSql='';
if(!blank(status)&&status!='all') {
 pipelineStatusSql=statusSql.replace(runtimePg,'nifi_process_group_id').replace('p.status','status');
}
// Unbound legacy pipelines are visible only to the all-data role. They have no source ownership relation.
var pipelineScopeSql=scopeMode=='ALL'?'1=1':'1=0';
var pipelinePage=db.page("SELECT * FROM nifi_pipeline_t WHERE tenant_id=#{tenantId} AND is_del=0 AND (${pipelineScopeSql}) AND (#{keyword}='' OR LOWER(name) LIKE #{keywordPattern})"+pipelineStatusSql+" ORDER BY "+pipelineSortColumn+" "+sortDirection+",id ASC",pageSize,offset);
var pipelines=pipelinePage.list||[];

var pipelineFields={dsl_json:'dslJson',nifi_process_group_id:'nifiProcessGroupId',last_deployed_at:'lastDeployedAt',last_stopped_at:'lastStoppedAt',created_at:'createdAt',updated_at:'updatedAt'};
for (pipeline in pipelines) {
    for(field in pipelineFields.keySet()) {var camel=pipelineFields.get(field);pipeline[camel]=pipeline[camel]==null?pipeline[field]:pipeline[camel];}
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

taskRuntime.decorate(filtered,live)
return {pageNum:pageNum,pageSize:pageSize,total:pipelinePage.total,list:filtered,runtimeRevision:live.revision,runtimeFresh:live.fresh,runtimeObservedAt:live.observedAt,runtimeMessage:live.message};
