{
  "properties" : { },
  "id" : "49733c5bdee44ec7bbaa10c1dee2c46c",
  "script" : null,
  "groupId" : "0f27d7cf99494ceca369c14f35fbb987",
  "name" : "19.登记表接入状态汇总",
  "createTime" : 1792474376069,
  "updateTime" : null,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/registrationAccessSummary",
  "description" : "当前租户登记表的申请、直接任务、可执行动作及实时运行摘要，最多100项，固定批量查询。",
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "payload",
    "value" : null,
    "description" : "当前数据源和本页表标识，最多100项",
    "required" : true,
    "dataType" : "Object",
    "type" : "java.util.Map",
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ]
}
================================
/*
 * 功能：第五步当前页登记表的申请、直接接入任务及运行摘要；管理员创建任务即代表受理。
 * 输入：payload.datasourceId 必填；tableIds 为1～100个去重表ID，全部须属于该数据源。
 * 输出：list含统一accessStatus、任务及可执行动作；totals按整个当前数据源统计，不按本页推算。
 * 权限：当前租户、登记数据源角色范围；只展示本表由管理员配置的直接任务基本信息。
 * 数据：只读db_table_t、db_datasource_t、data_access_agg_task_t、nifi_pipeline_t。
 * 约束：源表授权不授予目标库通用查询权限；目标表及流程仍校验同租户、有效记录。
 * 并发：每批固定SQL和一次NiFi快照，无逐表数据库或外部请求；无写入副作用。
 * 异常：无权、混入其他数据源ID及超限直接拒绝；NiFi不可用返回待确认，不伪造停止。
 */
import tenantRuntime
import dataScope
import taskRuntime
var text = value => value == null ? '' : value + ''
var first = (row, names) => {
    if (row == null) { return null }
    for (name in names) { if (row[name] != null) { return row[name] } }
    return null
}
var tenantId = tenantRuntime.id()
var datasourceId = text(payload?.datasourceId).trim()
var ids = []
for (id in (payload?.tableIds || [])) {
    var value = text(id).trim()
    if (value != '' && !ids.contains(value)) { ids.add(value) }
}
if (datasourceId == '' || ids.size() == 0 || ids.size() > 100) { exit 400, '请选择当前数据源的1至100张数据表' }
var sources = db.table('db_datasource_t').columns('tid','db_name','asset_status','org_id','created_by').where()
    .eq('tenant_id',tenantId).eq('tid',datasourceId).eq('is_del',0).select()
sources = dataScope.visibleDataSourcesFor('MENU:2081000000000000002',sources)
if (sources.size() != 1) { exit 403, '数据源不存在或不在当前角色范围内' }
// 列表不读取字段治理JSON、连接配置和源表数据；这里只返回操作确认所需摘要。
var tables = db.table('db_table_t').columns('tid','datasource_id','table_name','table_name_cn','business_type',
    'field_count','asset_status','timestamp_field','access_request_status','access_requested_by','access_requested_at').where()
    .eq('tenant_id',tenantId).eq('datasource_id',datasourceId).eq('is_del',0).in('tid',ids).select()
if (tables.size() != ids.size()) { exit 403, '部分数据表不属于当前数据源或已不可访问' }
var tasks = db.select("""
    SELECT task.tid,task.source_table_id,task.pipeline_id,task.task_name,task.schedule_cycle,
           task.last_running,task.end_running,task.is_timeout,
           target.table_name AS target_table_name,target.table_name_cn AS target_table_name_cn,
           target_db.db_name AS target_datasource_name,
           pipeline.id AS active_pipeline_id,pipeline.status AS lifecycle_status,pipeline.last_deployed_hash AS deployed_hash,
           pipeline.last_deployed_at AS deployed_at,
           CASE WHEN pipeline.id IS NOT NULL THEN pipeline.nifi_process_group_id ELSE task.process_group_id END AS runtime_process_group_id
    FROM data_access_agg_task_t task
    JOIN db_table_t target ON target.tid=task.target_table_id AND target.tenant_id=#{tenantId} AND target.is_del=0
    JOIN db_datasource_t target_db ON target_db.tid=target.datasource_id AND target_db.tenant_id=#{tenantId} AND target_db.is_del=0
    LEFT JOIN nifi_pipeline_t pipeline ON pipeline.id=task.pipeline_id AND pipeline.tenant_id=#{tenantId} AND pipeline.is_del=0
    WHERE task.tenant_id=#{tenantId} AND task.is_del=0
      AND task.source_table_id IN <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
    ORDER BY task.created_time ASC,task.tid ASC
""")
var live = taskRuntime.snapshot()
taskRuntime.decorate(tasks,live)
var formatFrequency = value => {
    var cycle = text(value)
    return cycle == '0' ? '手动触发（无固定更新周期）' : cycle == '1' ? '每日' : cycle == '2' ? '每周'
        : cycle == '3' ? '每月' : cycle == '4' ? '实时' : cycle == '' ? '未配置' : cycle
}
var latestTime = values => {
    var times = values.filter(value => value != null)
    // 同一租户库的时间字段使用同一时区和类型；保留原始时间值，仅按标准时间文本排序。
    times = times.sort((left,right) => text(right).replace('T',' ').compareTo(text(left).replace('T',' ')))
    return times.size() == 0 ? null : times[0]
}
var groups = {}
for (task in tasks) {
    var sourceId = text(first(task,['sourceTableId','source_table_id']))
    if (groups[sourceId] == null) { groups[sourceId] = [] }
    var state = text(task.runtimeState)
    var pipelineId = text(first(task,['pipelineId','pipeline_id']))
    var deployed = text(task.runtimeProcessGroupId).trim() != '' && text(task.deployedHash).trim() != ''
    var lifecycle = text(task.lifecycleStatus).toUpperCase()
    var configured = text(first(task,['activePipelineId','active_pipeline_id'])) != ''
    var busy = ['DEPLOYING','STARTING','STOPPING'].contains(lifecycle) || state == 'STOPPING'
    task.requiresDeployment = !deployed || lifecycle == 'SAVED'
    task.canStart = configured && pipelineId != '' && live.fresh == true && !busy && task.running != true
        && (['STOPPED','UNDEPLOYED'].contains(state) || (state == 'INVALID' && !deployed))
    task.canStop = pipelineId != '' && live.fresh == true && task.running == true && state != 'STOPPING'
    task.actionHint = !configured ? '任务配置缺失，请完善接入配置后重试'
        : live.fresh != true ? '运行状态暂时无法确认，请稍后重试'
        : busy ? '任务正在处理，请等待状态更新'
        : task.canStart ? task.requiresDeployment ? '启动时自动部署已保存配置，部署完成后开始同步' : '按已保存配置启动；需要更新部署时将自动处理'
        : task.canStop ? '停止后保留已同步数据，可再次启动'
        : state == 'EMPTY' ? '任务尚未配置来源和目标，请完善配置' : task.runtimeLabel
    task.taskName = first(task,['taskName','task_name'])
    task.pipelineId = pipelineId
    task.currentStatus = state
    task.taskStatusText = task.runtimeLabel
    task.scheduleCycle = first(task,['scheduleCycle','schedule_cycle'])
    task.lastRunning = first(task,['lastRunning','last_running'])
    task.endRunning = first(task,['endRunning','end_running'])
    groups[sourceId].add(task)
}
var tableMap = {}
for (table in tables) { tableMap[text(table.tid)] = table }
var rows = []
for (id in ids) {
    var table = tableMap[id]
    var ownTasks = groups[id] || []
    var running = ownTasks.filter(task => task.running == true).size() > 0
    var unknown = ownTasks.filter(task => task.runtimeFresh != true && text(task.runtimeProcessGroupId).trim() != '').size() > 0
    var requested = text(first(table,['accessRequestStatus','access_request_status'])) == '1'
    var eligible = ['业务表','字典表','日志表'].contains(text(first(table,['businessType','business_type'])))
        && (first(table,['assetStatus','asset_status']) || 0)::int > 0
    var status = running ? '1' : ownTasks.size() > 0 ? unknown ? '' : '3' : requested ? '2' : '0'
    var label = status == '1' ? '已接入' : status == '3' ? '待启动' : status == '2' ? '待审批' : status == '0' ? '待接入' : '状态待确认'
    var cycles = []
    for (task in ownTasks) {
        var cycle = formatFrequency(task.scheduleCycle)
        task.frequency = cycle
        if (!cycles.contains(cycle)) { cycles.add(cycle) }
    }
    var frequency = cycles.size() == 0 ? '未配置' : cycles.size() == 1 ? cycles[0] : '多个周期（' + ownTasks.size() + '个任务）'
    // 一张表有多个直接任务时，展示它们最近的同步事实，不能由最早创建的任务遮盖其他任务。
    var lastExecutedAt = latestTime(ownTasks.map(task => task.lastRunning))
    var lastDataUpdatedAt = latestTime(ownTasks.map(task => task.endRunning))
    rows.add({tableId:id,sourceTableId:id,tableName:first(table,['tableName','table_name']),
        tableNameCn:first(table,['tableNameCn','table_name_cn']),businessType:first(table,['businessType','business_type']),
        fieldCount:first(table,['fieldCount','field_count']),timestampField:first(table,['timestampField','timestamp_field']),
        datasourceName:first(sources[0],['dbName','db_name']),accessStatus:status,accessStatusText:label,
        accessRequestStatus:first(table,['accessRequestStatus','access_request_status']) || 0,
        requestedBy:first(table,['accessRequestedBy','access_requested_by']),requestedAt:first(table,['accessRequestedAt','access_requested_at']),
        eligible:eligible,canApply:eligible && ownTasks.size() == 0 && !requested,taskCount:ownTasks.size(),tasks:ownTasks,
        frequency:frequency,lastExecutedAt:lastExecutedAt,lastDataUpdatedAt:lastDataUpdatedAt,lastBusinessTime:null})
}
// 顶部卡片按当前数据源全部有效核心表聚合，绝不使用当前页长度充当全量统计。
var runningChunks = []
var invalidChunks = []
var runningIds = []
var invalidIds = []
if (live.fresh == true && live.index != null) {
    for (groupId, group in live.index.groups()) {
        if (group.running()) {
            runningIds.add(groupId)
            if (runningIds.size() == 500) { runningChunks.add(runningIds); runningIds = [] }
        }
        if (['INVALID','RUN_ERROR'].contains(group.state())) {
            invalidIds.add(groupId)
            if (invalidIds.size() == 500) { invalidChunks.add(invalidIds); invalidIds = [] }
        }
    }
    if (runningIds.size() > 0) { runningChunks.add(runningIds) }
    if (invalidIds.size() > 0) { invalidChunks.add(invalidIds) }
}
var effectiveGroup = '(CASE WHEN p.id IS NOT NULL THEN p.nifi_process_group_id ELSE task.process_group_id END)'
var runningCondition = runningChunks.size() == 0 ? '1=0' : '<foreach collection="runningChunks" item="batch" open="(" close=")" separator=" OR ">'
    + effectiveGroup + ' IN <foreach collection="batch" item="g" open="(" close=")" separator=",">#{g}</foreach></foreach>'
var invalidCondition = invalidChunks.size() == 0 ? '1=0' : '<foreach collection="invalidChunks" item="batch" open="(" close=")" separator=" OR ">'
    + effectiveGroup + ' IN <foreach collection="batch" item="g" open="(" close=")" separator=",">#{g}</foreach></foreach>'
var freshFlag = live.fresh == true ? 1 : 0
var totals = db.selectOne("""
    SELECT COUNT(*) AS total,
           COALESCE(SUM(CASE WHEN x.taskCount IS NULL AND COALESCE(t.access_request_status,0)<>1 THEN 1 ELSE 0 END),0) AS pending,
           COALESCE(SUM(CASE WHEN x.taskCount IS NULL AND t.access_request_status=1 THEN 1 ELSE 0 END),0) AS pending_approval,
           COALESCE(SUM(CASE WHEN x.taskCount>0 THEN 1 ELSE 0 END),0) AS configured,
           COALESCE(SUM(CASE WHEN x.isRunning=1 THEN 1 ELSE 0 END),0) AS running,
           COALESCE(SUM(CASE WHEN x.taskCount>0 AND x.isRunning=0 AND (#{freshFlag}=1 OR x.hasDeployed=0) THEN 1 ELSE 0 END),0) AS ready,
           COALESCE(SUM(CASE WHEN x.isInvalid=1 THEN 1 ELSE 0 END),0) AS problem,
           COALESCE(SUM(CASE WHEN x.isDelayed=1 THEN 1 ELSE 0 END),0) AS delayed_count
    FROM db_table_t t
    LEFT JOIN (
        SELECT task.source_table_id,COUNT(*) AS taskCount,
               MAX(CASE WHEN (""" + runningCondition + """) THEN 1 ELSE 0 END) AS isRunning,
               MAX(CASE WHEN (""" + invalidCondition + """) THEN 1 ELSE 0 END) AS isInvalid,
               MAX(CASE WHEN task.is_timeout=1 THEN 1 ELSE 0 END) AS isDelayed,
               MAX(CASE WHEN NULLIF(${effectiveGroup},'') IS NOT NULL THEN 1 ELSE 0 END) AS hasDeployed
        FROM data_access_agg_task_t task
        JOIN db_table_t source ON source.tid=task.source_table_id AND source.tenant_id=#{tenantId} AND source.is_del=0 AND source.datasource_id=#{datasourceId}
        JOIN db_table_t target ON target.tid=task.target_table_id AND target.tenant_id=#{tenantId} AND target.is_del=0
        JOIN db_datasource_t target_db ON target_db.tid=target.datasource_id AND target_db.tenant_id=#{tenantId} AND target_db.is_del=0
        LEFT JOIN nifi_pipeline_t p ON p.id=task.pipeline_id AND p.tenant_id=#{tenantId} AND p.is_del=0
        WHERE task.tenant_id=#{tenantId} AND task.is_del=0
        GROUP BY task.source_table_id
    ) x ON x.source_table_id=t.tid
    WHERE t.tenant_id=#{tenantId} AND t.datasource_id=#{datasourceId} AND t.is_del=0 AND t.asset_status>0
      AND t.business_type IN ('业务表','字典表','日志表')
""")
totals.delayed = first(totals,['delayedCount','delayed_count']) || 0
return {list:rows,totals:totals,runtimeRevision:live.revision,runtimeFresh:live.fresh,
    runtimeObservedAt:live.observedAt,runtimeMessage:live.message}
