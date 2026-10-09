{
  "properties" : { },
  "id" : "ods_batch_create_job_01",
  "script" : null,
  "groupId" : "1f4028f20aaa4f379b5e37a343b99fa9",
  "name" : "19.批量创建任务作业",
  "createTime" : 1791112318054,
  "updateTime" : 1791117206123,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/batchCreateJob",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\"action\":\"list\"}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "{\"jobs\":[]}",
  "description" : "当前租户批量创建接入任务的持久作业。创建时核验来源库、唯一物化目标表及租户；可列出、查看、领取、回报、停止与重试。领取后调用已有 /ods/dataAggTaskEnsure，页面关闭后暂停，重新打开可续跑。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：19.批量创建任务作业
// 路由：POST /ods/batchCreateJob
// 业务说明：按当前租户保存批量作业及逐项结果；领取一项后由调用方执行既有
// /ods/dataAggTaskEnsure，再携租约回报结果。页面离开时不在后台继续执行。
import tenantRuntime
import dataScope
import cn.hutool.core.util.IdUtil
import cn.hutool.json.JSONUtil

var text = value => value == null ? '' : value + ''
var blank = value => text(value).trim() == ''
var tenantId = tenantRuntime.id()
if (blank(tenantId)) { exit 401, '当前登录租户无效，请重新登录' }
var action = text(body?.action).trim()
var jobId = text(body?.jobId).trim()
var now = new Date()
var leaseCutoff = new Date(now.getTime() - 10 * 60 * 1000)

var visibleDbIdsFor = ids => {
    if (ids.size() == 0) { return [] }
    var rows = db.table('db_datasource_t').where().eq('tenant_id', tenantId)
        .eq('is_del', 0).in('tid', ids).select()
    var visible = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', rows)
    var result = []
    for (row in visible) { result.add(text(row.tid)) }
    return result
}
var assertJobVisible = job => {
    if (job == null) { exit 404, '批量作业不存在或不属于当前租户' }
    var visibleIds = visibleDbIdsFor([text(job.sourceDbId), text(job.targetDbId)])
    if (!visibleIds.contains(text(job.sourceDbId)) || !visibleIds.contains(text(job.targetDbId))) {
        exit 403, '批量作业的来源库或目标库不在当前角色的数据范围内'
    }
    return job
}
var ownedJob = id => {
    var job = db.table('ods_batch_create_job_t').where()
        .eq('tenant_id', tenantId).eq('job_id', id).selectOne()
    return assertJobVisible(job)
}
var publicItem = row => ({
    itemId: row.itemId, jobId: row.jobId, itemIndex: row.itemIndex,
    sourceTableId: row.sourceTableId, sourceTableName: row.sourceTableName,
    sourceCatalogId: row.sourceCatalogId, targetTableId: row.targetTableId,
    targetTableName: row.targetTableName, status: row.status,
    attemptCount: row.attemptCount, taskId: row.taskId,
    wasExisting: row.wasExisting, errorMessage: row.errorMessage,
    claimedTime: row.claimedTime, completedTime: row.completedTime
})
var itemsOf = id => {
    var rows = db.table('ods_batch_create_item_t').where()
        .eq('tenant_id', tenantId).eq('job_id', id)
        .orderBy('item_index', 'asc').select()
    var result = []
    for (row in rows) { result.add(publicItem(row)) }
    return result
}
var detail = id => {
    var job = ownedJob(id)
    if (job == null) { exit 404, '批量作业不存在或不属于当前租户' }
    return {job: job, items: itemsOf(id)}
}
var countsOf = id => {
    return {
        pending: db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId).eq('job_id', id).eq('status', 'PENDING').count(),
        running: db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId).eq('job_id', id).eq('status', 'RUNNING').count(),
        succeeded: db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId).eq('job_id', id).eq('status', 'SUCCEEDED').count(),
        failed: db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId).eq('job_id', id).eq('status', 'FAILED').count()
    }
}
var refreshStatus = id => {
    var job = ownedJob(id)
    var counts = countsOf(id)
    var status = 'RUNNING'
    var finished = null
    if (job.stopRequested == true || text(job.stopRequested) == '1') {
        status = 'STOPPED'
        finished = new Date()
    } else if (counts.pending == 0 && counts.running == 0) {
        status = counts.failed == 0 ? 'SUCCEEDED' : (counts.succeeded > 0 ? 'PARTIAL' : 'FAILED')
        finished = new Date()
    }
    db.table('ods_batch_create_job_t').where().eq('tenant_id', tenantId).eq('job_id', id)
        .update({status: status, finished_time: finished, updated_time: new Date()})
    return status
}

if (action == 'list') {
    // 最近 100 个租户作业的来源/目标库一次批量预取并按当前角色过滤。
    var page = db.table('ods_batch_create_job_t').where().eq('tenant_id', tenantId)
        .orderBy('created_time', 'desc').page(100, 0)
    var rows = page == null || page.list == null ? [] : page.list
    var ids = []
    for (job in rows) {
        var sourceId = text(job.sourceDbId)
        var targetId = text(job.targetDbId)
        if (!ids.contains(sourceId)) { ids.add(sourceId) }
        if (!ids.contains(targetId)) { ids.add(targetId) }
    }
    var visibleIds = visibleDbIdsFor(ids)
    var jobs = []
    for (job in rows) {
        if (visibleIds.contains(text(job.sourceDbId)) && visibleIds.contains(text(job.targetDbId))) {
            jobs.add(job)
        }
    }
    return {jobs: jobs}
}
if (action == 'sourceOptions') {
    // 在数据库分页之前施加当前角色的 ALL/ORG/OWNER 条件；不先取全量再过滤。
    var scope = dataScope.resource('MENU:2081000000000000002')
    var query = db.table('db_datasource_t').where().eq('tenant_id', tenantId)
        .eq('is_del', 0).in('asset_status', [1, 2])
    if (scope.scope == 'OWNER') { query = query.eq('created_by', scope.userId) }
    if (scope.scope == 'ORG') {
        if (scope.organizationIds == null || scope.organizationIds.size() == 0) {
            return {list: [], total: 0}
        }
        query = query.in('org_id', scope.organizationIds)
    }
    if (!['ALL', 'ORG', 'OWNER'].contains(text(scope.scope))) { exit 403, '当前角色数据范围无效' }
    var optionsPage = query.orderBy('updated_time', 'desc').page(201, 0)
    if (optionsPage != null && optionsPage.total > 200) {
        exit 400, '当前角色可见来源库超过200个，请先缩小数据范围'
    }
    var options = []
    var optionRows = optionsPage == null || optionsPage.list == null ? [] : optionsPage.list
    for (row in optionRows) {
        // 禁止将连接串、账号、口令等 db_datasource_t 原始配置返回浏览器。
        options.add({tid: text(row.tid), dbName: text(row.dbName), dbType: text(row.dbType)})
    }
    return {list: options,
        total: optionsPage == null ? 0 : optionsPage.total}
}
if (action == 'targetOptions') {
    // 目标库仍须属于 ODS 分域配置，同时按当前角色数据源范围过滤。
    var dictRoot = db.table('sym_dict_t').where().eq('tenant_id', tenantId)
        .eq('dict_code', 'dataSourceType').eq('parent_id', '0').eq('is_del', 0).selectOne()
    if (dictRoot == null) { return {list: [], total: 0} }
    var odsRoot = db.table('sym_dict_t').where().eq('tenant_id', tenantId)
        .eq('parent_id', dictRoot.tid).eq('dict_code', 'ods').eq('is_del', 0).selectOne()
    if (odsRoot == null) { return {list: [], total: 0} }
    var domainPage = db.table('sym_dict_t').where().eq('tenant_id', tenantId)
        .eq('parent_id', odsRoot.tid).eq('is_del', 0).page(201, 0)
    if (domainPage == null || domainPage.total > 200) { exit 400, 'ODS分域超过200个，请缩小范围' }
    var domainRows = domainPage.list == null ? [] : domainPage.list
    var domainIds = []
    for (domain in domainRows) { domainIds.add(text(domain.tid)) }
    if (domainIds.size() == 0) { return {list: [], total: 0} }
    var relationPage = db.table('dwm_center_layer_source_t').where()
        .eq('tenant_id', tenantId).eq('target_type', 'domain')
        .eq('is_del', 0).in('target_id', domainIds).page(201, 0)
    if (relationPage == null || relationPage.total > 200) { exit 400, 'ODS目标库关联超过200个，请缩小范围' }
    var relations = relationPage.list == null ? [] : relationPage.list
    var dbIds = []
    for (relation in relations) {
        var candidateId = text(relation.datasourceId)
        if (!blank(candidateId) && !dbIds.contains(candidateId)) { dbIds.add(candidateId) }
    }
    if (dbIds.size() == 0) { return {list: [], total: 0} }
    var dbRows = db.table('db_datasource_t').where().eq('tenant_id', tenantId)
        .eq('is_del', 0).eq('show_connect', 1).in('tid', dbIds).select()
    var visibleRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', dbRows)
    var options = []
    for (row in visibleRows) {
        options.add({tid: text(row.tid), dbName: text(row.dbName), dbType: text(row.dbType)})
    }
    return {list: options, total: options.size()}
}
if (action == 'preview') {
    var previewDbId = text(body?.datasourceId).trim()
    var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
    var pageSize = body?.pageSize == null ? 20 : body.pageSize::int
    if (blank(previewDbId) || pageNum < 1 || pageNum > 100000 || pageSize < 1 || pageSize > 50) {
        exit 400, '来源表分页参数无效'
    }
    var previewDb = db.table('db_datasource_t').where().eq('tenant_id', tenantId)
        .eq('tid', previewDbId).eq('is_del', 0).in('asset_status', [1, 2]).selectOne()
    if (previewDb == null || dataScope.visibleDataSourcesFor('MENU:2081000000000000002', [previewDb]).size() != 1) {
        exit 403, '来源库不存在或不在当前角色的数据范围内'
    }
    var tablePage = db.table('db_table_t').where().eq('tenant_id', tenantId)
        .eq('datasource_id', previewDbId).eq('is_del', 0).eq('asset_status', 2)
        .in('business_type', ['业务表', '日志表', '字典表'])
        .orderBy('updated_time', 'desc').page(pageSize, (pageNum - 1) * pageSize)
    var tableRows = tablePage == null || tablePage.list == null ? [] : tablePage.list
    if (tableRows.size() == 0) {
        return {list: [], total: tablePage == null ? 0 : tablePage.total}
    }
    var ids = []
    for (row in tableRows) { ids.add(text(row.tid)) }
    var linkedPage = db.table('db_table_t').where().eq('tenant_id', tenantId)
        .eq('is_del', 0).in('source_table_id', ids).page(pageSize * 2 + 1, 0)
    if (linkedPage == null || linkedPage.total > pageSize * 2) {
        exit 400, '当前页物化目标关系过多，请核对后重试'
    }
    var targetGroups = {}
    var linkedRows = linkedPage.list == null ? [] : linkedPage.list
    var targetDbIds = []
    for (targetRow in linkedRows) {
        var candidateDbId = text(targetRow.datasourceId)
        if (!blank(candidateDbId) && !targetDbIds.contains(candidateDbId)) { targetDbIds.add(candidateDbId) }
    }
    var visibleTargetDbIds = visibleDbIdsFor(targetDbIds)
    var visibleTargetTableIds = []
    var relationCounts = {}
    for (targetRow in linkedRows) {
        var sourceId = text(targetRow.sourceTableId)
        if (text(targetRow.tid) == sourceId) { continue }
        var key = 'id:' + sourceId
        relationCounts[key] = relationCounts[key] == null ? 1 : relationCounts[key] + 1
        if (!visibleTargetDbIds.contains(text(targetRow.datasourceId))) { continue }
        visibleTargetTableIds.add(text(targetRow.tid))
        if (targetGroups[key] == null) { targetGroups[key] = [] }
        targetGroups[key].add({targetTableId: text(targetRow.tid),
            targetTableName: text(targetRow.tableName),
            targetDbId: text(targetRow.datasourceId), targetDbName: ''})
    }
    var taskPage = db.table('data_access_agg_task_t').where()
        .eq('tenant_id', tenantId).eq('is_del', 0)
        .in('source_table_id', ids).page(pageSize * 5 + 1, 0)
    if (taskPage == null || taskPage.total > pageSize * 5) {
        exit 400, '当前页已有接入任务过多，请核对后重试'
    }
    var tasks = {}
    var taskRows = taskPage.list == null ? [] : taskPage.list
    for (task in taskRows) {
        if (!visibleTargetTableIds.contains(text(task.targetTableId))) { continue }
        var taskKey = 'id:' + text(task.sourceTableId)
        if (tasks[taskKey] == null) { tasks[taskKey] = task }
    }
    var result = []
    for (row in tableRows) {
        var key = 'id:' + text(row.tid)
        var targetTables = targetGroups[key] == null ? [] : targetGroups[key]
        var existingTask = tasks[key]
        result.add({tid: text(row.tid), datasourceId: previewDbId,
            tableName: text(row.tableName), tableNameCn: text(row.tableNameCn),
            sourceCatalogId: text(row.sourceCatalogId), targetTables: targetTables,
            relationAmbiguous: relationCounts[key] != null && relationCounts[key] > 1,
            taskCount: existingTask == null ? 0 : 1,
            accessTaskId: existingTask == null ? '' : text(existingTask.tid)})
    }
    return {list: result, total: tablePage.total}
}
if (action == 'detail') {
    if (blank(jobId)) { exit 400, 'jobId不能为空' }
    return detail(jobId)
}
if (action == 'create') {
    var requestKey = text(body?.requestKey).trim()
    var sourceDbId = text(body?.sourceDbId).trim()
    var targetDbId = text(body?.targetDbId).trim()
    var requested = body?.items
    if (blank(requestKey) || requestKey.length() > 64 || !requestKey.matches('^[A-Za-z0-9_-]+$')) {
        exit 400, '请求幂等键无效'
    }
    if (blank(sourceDbId) || blank(targetDbId)) { exit 400, '请选择来源库和目标库' }
    if (requested == null || requested.size() == 0 || requested.size() > 500) {
        exit 400, '每个批量作业须选择 1 至 500 张来源表'
    }
    var rules = {
        removePrefix: text(body?.matchRule?.removePrefix),
        addPrefix: text(body?.matchRule?.addPrefix),
        addSuffix: text(body?.matchRule?.addSuffix)
    }
    var ruleJson = JSONUtil.toJsonStr(rules)
    var seen = {}
    var sourceIds = []
    var requestedPairs = []
    for (requestedItem in requested) {
        var sourceId = text(requestedItem?.sourceTableId).trim()
        var targetId = text(requestedItem?.targetTableId).trim()
        if (blank(sourceId) || blank(targetId) || seen['id:' + sourceId] != null) {
            exit 400, '来源表或目标表标识缺失，或同一来源表重复提交'
        }
        seen['id:' + sourceId] = true
        sourceIds.add(sourceId)
        requestedPairs.add({sourceId: sourceId, targetId: targetId})
    }
    var verifySameRequest = priorJob => {
        if (text(priorJob.sourceDbId) != sourceDbId || text(priorJob.targetDbId) != targetDbId
            || text(priorJob.matchRuleJson) != ruleJson) {
            exit 409, '幂等键已用于不同的来源库、目标库或匹配规则'
        }
        var priorItems = itemsOf(priorJob.jobId)
        if (priorItems.size() != requestedPairs.size()) {
            exit 409, '幂等键已用于不同的来源表清单'
        }
        var index = 0
        for (pair in requestedPairs) {
            if (text(priorItems[index].sourceTableId) != pair.sourceId
                || text(priorItems[index].targetTableId) != pair.targetId) {
                exit 409, '幂等键已用于不同的来源表或目标表清单'
            }
            index = index + 1
        }
        return detail(priorJob.jobId)
    }
    var sourceDb = db.table('db_datasource_t').where().eq('tenant_id', tenantId)
        .eq('tid', sourceDbId).eq('is_del', 0).selectOne()
    var targetDb = db.table('db_datasource_t').where().eq('tenant_id', tenantId)
        .eq('tid', targetDbId).eq('is_del', 0).selectOne()
    if (sourceDb == null || targetDb == null || !['1', '2'].contains(text(first(sourceDb, ['assetStatus', 'asset_status'])))) {
        exit 400, '所选数据库不存在、不属于当前租户或来源库尚未完成登记'
    }
    // 当前会话角色的 ALL/ORG/OWNER 数据源范围只计算一次，随后每个
    // 来源/目标 ID 都须通过已授权的数据源关系；混入无权项整单拒绝。
    var visibleDbs = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', [sourceDb, targetDb])
    var visibleDbIds = []
    for (visibleDb in visibleDbs) { visibleDbIds.add(text(visibleDb.tid)) }
    if (!visibleDbIds.contains(sourceDbId) || !visibleDbIds.contains(targetDbId)) {
        exit 403, '来源库或目标库不在当前角色的数据范围内'
    }
    var prior = db.table('ods_batch_create_job_t').where().eq('tenant_id', tenantId)
        .eq('request_key', requestKey).selectOne()
    if (prior != null) { return verifySameRequest(prior) }
    try {
    return db.transaction(() => {
        var existing = db.table('ods_batch_create_job_t').where().eq('tenant_id', tenantId)
            .eq('request_key', requestKey).selectOne()
        if (existing != null) { return verifySameRequest(existing) }
        var newJobId = IdUtil.simpleUUID()
        var jobName = text(body?.jobName).trim()
        if (blank(jobName)) { jobName = '批量创建任务' }
        if (jobName.length() > 255) { exit 400, '作业名称不能超过255个字符' }
        // 每组最多 100 个 ID；查询带当前会话租户和已登记来源库约束。
        // 关联目标每组只取最多 2N+1 行；超过 N 个唯一目标的请求必定无效，
        // 因此无需全量装载异常的一对多关系。
        var sourceRows = {}
        var targetGroups = {}
        var offset = 0
        while (offset < sourceIds.size()) {
            var end = offset + 100
            if (end > sourceIds.size()) { end = sourceIds.size() }
            var chunk = sourceIds.subList(offset::int, end::int)
            var sourcesInChunk = db.table('db_table_t').where()
                .eq('tenant_id', tenantId).eq('datasource_id', sourceDbId)
                .eq('is_del', 0).in('tid', chunk).select()
            for (sourceRow in sourcesInChunk) {
                sourceRows['id:' + text(sourceRow.tid)] = sourceRow
            }
            var maxLinkedRows = chunk.size() * 2 + 1
            var linkedPage = db.table('db_table_t').where()
                .eq('tenant_id', tenantId).eq('is_del', 0)
                .in('source_table_id', chunk).page(maxLinkedRows, 0)
            if (linkedPage == null || linkedPage.total > chunk.size() * 2) {
                exit 400, '物化目标表关联过多，请核对来源表后分批重试'
            }
            var linkedRows = linkedPage.list == null ? [] : linkedPage.list
            for (targetRow in linkedRows) {
                var linkedSourceId = text(targetRow.sourceTableId)
                var linkedTargetId = text(targetRow.tid)
                if (linkedTargetId == linkedSourceId) { continue }
                var key = 'id:' + linkedSourceId
                if (targetGroups[key] == null) { targetGroups[key] = [] }
                targetGroups[key].add(targetRow)
            }
            offset = offset + 100
        }
        // 已批量预取所有来源与关联目标。这里逐 ID 校验只访问内存，
        // 缺失、跨租户、非本库或一对多任一项都会拒绝整单，不静默跳过。
        var checkedItems = []
        for (requestedItem in requested) {
            var sourceId = text(requestedItem?.sourceTableId).trim()
            var targetId = text(requestedItem?.targetTableId).trim()
            var sourceTable = sourceRows['id:' + sourceId]
            var linkedTargets = targetGroups['id:' + sourceId]
            var targetTable = linkedTargets == null || linkedTargets.size() != 1 ? null : linkedTargets[0]
            if (sourceTable == null || text(sourceTable.assetStatus) != '2'
                || !['业务表', '日志表', '字典表'].contains(text(sourceTable.businessType))
                || targetTable == null || text(targetTable.tid) != targetId
                || text(targetTable.datasourceId) != targetDbId) {
                exit 400, '来源表或唯一物化目标表已变化，请刷新匹配预览'
            }
            checkedItems.add({sourceId: sourceId, sourceTable: sourceTable,
                targetId: targetId, targetTable: targetTable})
        }
        db.table('ods_batch_create_job_t').primary('job_id').insert({
            job_id: newJobId, tenant_id: tenantId, request_key: requestKey,
            job_name: jobName, source_db_id: sourceDbId, target_db_id: targetDbId,
            match_rule_json: ruleJson, status: 'RUNNING',
            stop_requested: 0, total_count: requested.size(),
            created_time: now, updated_time: now
        })
        var index = 0
        for (checked in checkedItems) {
            db.table('ods_batch_create_item_t').primary('item_id').insert({
                item_id: IdUtil.simpleUUID(), job_id: newJobId, tenant_id: tenantId,
                item_index: index, source_table_id: checked.sourceId,
                source_table_name: text(checked.sourceTable.tableName),
                source_catalog_id: checked.sourceTable.sourceCatalogId,
                target_table_id: checked.targetId, target_table_name: text(checked.targetTable.tableName),
                status: 'PENDING', attempt_count: 0,
                created_time: now, updated_time: now
            })
            index = index + 1
        }
        return detail(newJobId)
    })
    } catch (e) {
        // 同一幂等键的并发首提可能在唯一约束处竞争；事务回滚后回读胜者。
        var winner = db.table('ods_batch_create_job_t').where().eq('tenant_id', tenantId)
            .eq('request_key', requestKey).selectOne()
        if (winner != null) { return verifySameRequest(winner) }
        throw e
    }
}

if (blank(jobId)) { exit 400, 'jobId不能为空' }
if (action == 'stop') {
    return db.transaction(() => {
        var job = db.selectOne('SELECT * FROM ods_batch_create_job_t WHERE tenant_id = #{tenantId} AND job_id = #{jobId} FOR UPDATE')
        assertJobVisible(job)
        db.table('ods_batch_create_job_t').where().eq('tenant_id', tenantId).eq('job_id', jobId)
            .update({stop_requested: 1, status: 'STOPPED', finished_time: new Date(), updated_time: new Date()})
        return detail(jobId)
    })
}
if (action == 'retry') {
    return db.transaction(() => {
        var job = db.selectOne('SELECT * FROM ods_batch_create_job_t WHERE tenant_id = #{tenantId} AND job_id = #{jobId} FOR UPDATE')
        assertJobVisible(job)
        var freshRunning = db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
            .eq('job_id', jobId).eq('status', 'RUNNING').gt('claimed_time', leaseCutoff).selectOne()
        if (freshRunning != null) { exit 409, '仍有创建项正在执行，请稍后重试' }
        var counts = countsOf(jobId)
        if (counts.pending + counts.failed + counts.running == 0) { return detail(jobId) }
        for (status in ['FAILED', 'RUNNING']) {
            db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
                .eq('job_id', jobId).eq('status', status).update({
                    status: 'PENDING', lease_token: null, claimed_time: null,
                    error_message: null, updated_time: new Date()
                })
        }
        db.table('ods_batch_create_job_t').where().eq('tenant_id', tenantId).eq('job_id', jobId)
            .update({stop_requested: 0, status: 'RUNNING', finished_time: null, updated_time: new Date()})
        return detail(jobId)
    })
}
if (action == 'claim') {
    return db.transaction(() => {
        var job = db.selectOne('SELECT * FROM ods_batch_create_job_t WHERE tenant_id = #{tenantId} AND job_id = #{jobId} FOR UPDATE')
        assertJobVisible(job)
        if (job.stopRequested == true || text(job.stopRequested) == '1') {
            return {done: true, stopped: true, status: 'STOPPED'}
        }
        // 只允许同一作业同时执行一项；10 分钟失联租约可重新领取，确保断线可续跑。
        var running = db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
            .eq('job_id', jobId).eq('status', 'RUNNING').orderBy('item_index', 'asc').selectOne()
        if (running != null) {
            var stale = running.claimedTime == null ? running : db.table('ods_batch_create_item_t').where()
                .eq('tenant_id', tenantId).eq('item_id', running.itemId)
                .lte('claimed_time', leaseCutoff).selectOne()
            if (stale == null) { return {done: false, busy: true, status: 'RUNNING'} }
            db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
                .eq('item_id', running.itemId).eq('status', 'RUNNING')
                .update({status: 'PENDING', lease_token: null, claimed_time: null, updated_time: new Date()})
        }
        var item = db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
            .eq('job_id', jobId).eq('status', 'PENDING').orderBy('item_index', 'asc').selectOne()
        if (item == null) {
            var finalStatus = refreshStatus(jobId)
            return {done: true, status: finalStatus}
        }
        // 同一租户同一来源表的不同作业也不能并发调用单项确保接口。
        // 先锁来源表行，使两个作业的领取事务串行，再检查跨作业租约。
        var sourceTableId = text(item.sourceTableId)
        var sourceLock = db.selectOne('SELECT tid FROM db_table_t WHERE tenant_id = #{tenantId} AND tid = #{sourceTableId} AND is_del = 0 FOR UPDATE')
        if (sourceLock == null) { exit 409, '来源表已失效，请刷新批量作业' }
        var otherRunning = db.table('ods_batch_create_item_t').where()
            .eq('tenant_id', tenantId).eq('source_table_id', sourceTableId)
            .eq('status', 'RUNNING').selectOne()
        if (otherRunning != null) {
            if (otherRunning.claimedTime != null && otherRunning.claimedTime.after(leaseCutoff)) {
                return {done: false, busy: true, status: 'RUNNING'}
            }
            db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
                .eq('item_id', otherRunning.itemId).eq('status', 'RUNNING')
                .update({status: 'PENDING', lease_token: null, claimed_time: null,
                    updated_time: new Date()})
        }
        var lease = IdUtil.simpleUUID()
        db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
            .eq('item_id', item.itemId).eq('status', 'PENDING').update({
                status: 'RUNNING', lease_token: lease,
                attempt_count: item.attemptCount + 1, claimed_time: new Date(),
                updated_time: new Date()
            })
        db.table('ods_batch_create_job_t').where().eq('tenant_id', tenantId).eq('job_id', jobId)
            .update({status: 'RUNNING', updated_time: new Date()})
        var claimedItem = db.table('ods_batch_create_item_t').where()
            .eq('tenant_id', tenantId).eq('item_id', item.itemId).selectOne()
        return {done: false, busy: false, item: publicItem(claimedItem), leaseToken: lease}
    })
}
if (action == 'complete') {
    var itemId = text(body?.itemId).trim()
    var leaseToken = text(body?.leaseToken).trim()
    var success = body?.success == true
    var taskId = text(body?.taskId).trim()
    var errorMessage = text(body?.error).trim()
    if (blank(itemId) || blank(leaseToken) || (success && blank(taskId))) {
        exit 400, '完成回报缺少创建项、租约或任务标识'
    }
    if (!success && blank(errorMessage)) { errorMessage = '创建接口未返回成功结果' }
    if (errorMessage.length() > 2000) { errorMessage = errorMessage.substring(0, 2000) }
    return db.transaction(() => {
        var job = db.selectOne('SELECT * FROM ods_batch_create_job_t WHERE tenant_id = #{tenantId} AND job_id = #{jobId} FOR UPDATE')
        assertJobVisible(job)
        var item = db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
            .eq('job_id', jobId).eq('item_id', itemId).selectOne()
        if (item == null) { exit 404, '创建项不存在或不属于当前租户' }
        if (success) {
            // 客户端只报告结果；服务端按当前租户及本项来源/目标表确认真实任务。
            var actualTask = db.table('data_access_agg_task_t').where()
                .eq('tenant_id', tenantId).eq('tid', taskId).eq('is_del', 0)
                .eq('source_table_id', item.sourceTableId)
                .eq('target_table_id', item.targetTableId).selectOne()
            if (actualTask == null) {
                exit 409, '回报的接入任务不存在或与当前来源表、目标表不一致'
            }
        }
        if (item.status == 'SUCCEEDED' && success && item.taskId == taskId) { return detail(jobId) }
        if (item.status != 'RUNNING' || item.leaseToken != leaseToken) {
            exit 409, '创建项租约已失效，请刷新作业并按幂等流程重试'
        }
        db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
            .eq('job_id', jobId).eq('item_id', itemId).eq('lease_token', leaseToken)
            .update({status: success ? 'SUCCEEDED' : 'FAILED', task_id: success ? taskId : null,
                was_existing: success ? (body?.existing == true ? 1 : 0) : null,
                error_message: success ? null : errorMessage,
                completed_time: new Date(), updated_time: new Date()})
        refreshStatus(jobId)
        return detail(jobId)
    })
}
exit 400, '不支持的批量作业操作'
