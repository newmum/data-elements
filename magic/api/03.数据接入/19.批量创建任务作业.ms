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
import tenantRuntime
import tableSort
import tableRead
import tableWrite
import cn.hutool.core.util.IdUtil
import cn.hutool.json.JSONUtil

/**
 * POST /ods/batchCreateJob。按当前租户预览、保存、查看及处理批量创建作业。
 * 领取一项后由调用方执行 /ods/dataAggTaskEnsure，再凭租约回报。页面离开后可继续。
 * 创建按请求键幂等，领取、回报、停止和重试在各自事务内校验归属并更新状态。
 */

var text = value => value == null ? '' : value + ''
var blank = value => text(value).trim() == ''
var tenantId = tenantRuntime.id()
if (blank(tenantId)) { exit 401, '当前登录租户无效，请重新登录' }
var action = text(body?.action).trim()
var jobId = text(body?.jobId).trim()
var now = new Date()
var leaseCutoff = new Date(now.getTime() - 10 * 60 * 1000)
var itemColumn = (field, kind) => tableRead.column('i', field, kind)
var itemRead = () => tableRead.from('ods_batch_create_item_t', 'i', tenantId)
var itemIdColumn = itemColumn('item_id', 'TEXT')
var itemIndexColumn = itemColumn('item_index', 'NUMBER')
var itemStatusColumn = itemColumn('status', 'TEXT')
var itemJobColumn = itemColumn('job_id', 'TEXT')
var itemSourceColumn = itemColumn('source_table_id', 'TEXT')
var itemClaimedColumn = itemColumn('claimed_time', 'TIME')

var visibleDbIdsFor = ids => {
    if (ids.size() == 0) { return [] }
    var rows = db.table('db_datasource_t').where().eq('tenant_id', tenantId)
        .eq('is_del', 0).in('tid', ids).select()
    var visible = (rows)
    var result = []
    for (row in visible) { result.add(text(row.tid)) }
    return result
}
var assertJobVisible = job => {
    if (job == null) { exit 404, '批量作业不存在或不属于当前租户' }
    var visibleIds = visibleDbIdsFor([text(job.sourceDbId), text(job.targetDbId)])
    if (!visibleIds.contains(text(job.sourceDbId)) || !visibleIds.contains(text(job.targetDbId))) {
        exit 403, '批量作业的来源库或目标库不存在或已删除'
    }
    return job
}
var ownedJob = id => {
    var job = db.table('ods_batch_create_job_t').where()
        .eq('tenant_id', tenantId).eq('job_id', id).selectOne()
    return assertJobVisible(job)
}
var lockJob = id => {
    var row = tableWrite.lockRow('ods_batch_create_job_t', tenantId, 'job_id', id,
        ['source_db_id', 'target_db_id', 'stop_requested'], {})
    if (row == null) { exit 404, '批量作业不存在或不属于当前租户' }
    return {sourceDbId: row.source_db_id, targetDbId: row.target_db_id, stopRequested: row.stop_requested}
}
var itemProjection = {
    itemId: itemIdColumn, jobId: itemJobColumn, itemIndex: itemIndexColumn,
    sourceTableId: itemSourceColumn, sourceTableName: itemColumn('source_table_name', 'TEXT'),
    sourceCatalogId: itemColumn('source_catalog_id', 'TEXT'), targetTableId: itemColumn('target_table_id', 'TEXT'),
    targetTableName: itemColumn('target_table_name', 'TEXT'), status: itemStatusColumn,
    attemptCount: itemColumn('attempt_count', 'NUMBER'), taskId: itemColumn('task_id', 'TEXT'),
    wasExisting: itemColumn('was_existing', 'NUMBER'), errorMessage: itemColumn('error_message', 'LOB'),
    claimedTime: itemClaimedColumn, completedTime: itemColumn('completed_time', 'TIME')
}
var itemsOf = (id, submissionOrder) => {
    var sort = submissionOrder ? 'item_index' : tableSort.field(body,
        {sourceTableName: 'source_table_name', attemptCount: 'attempt_count', item_index: 'item_index'}, 'item_index')
    var direction = submissionOrder || body?.sortDir == null ? 'asc' : tableSort.direction(body)
    var primary = sort == 'source_table_name' ? itemProjection.sourceTableName
        : (sort == 'attempt_count' ? itemProjection.attemptCount : itemIndexColumn)
    var query = itemRead()
    query.where(tableRead.equal(itemJobColumn, id))
    var rows = query.rows(itemProjection, [primary, itemIndexColumn, itemIdColumn], [direction, 'asc', 'asc'], 501)
    if (rows.size() > 500) { exit 413, '作业项超过500，请缩小作业范围' }
    return rows
}
var detail = id => {
    return {job: ownedJob(id), items: itemsOf(id, false)}
}
var countsOf = id => {
    var query = itemRead()
    query.where(tableRead.equal(itemJobColumn, id))
    return query.grouped({
        pending: tableRead.countIf(tableRead.equal(itemStatusColumn, 'PENDING')),
        running: tableRead.countIf(tableRead.equal(itemStatusColumn, 'RUNNING')),
        succeeded: tableRead.countIf(tableRead.equal(itemStatusColumn, 'SUCCEEDED')),
        failed: tableRead.countIf(tableRead.equal(itemStatusColumn, 'FAILED'))
    }, [])[0]
}
var refreshStatus = (id, job) => {
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
    db.table('ods_batch_create_job_t').withBlank().where().eq('tenant_id', tenantId).eq('job_id', id)
        .update({status: status, finished_time: finished, updated_time: new Date()})
    return status
}

if (action == 'list') {
    // 最近 100 个租户作业的来源/目标库一次批量预取并校验当前租户有效记录。
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
    // 直接在当前行业库分页查询有效数据源，不再叠加角色 OWNER/ORG 范围。
    var query = db.table('db_datasource_t').where().eq('tenant_id', tenantId)
        .eq('is_del', 0).gt('asset_status', 0)
    var optionsPage = query.orderBy('updated_time', 'desc').page(201, 0)
    if (optionsPage != null && optionsPage.total > 200) {
        exit 400, '当前行业来源库超过200个，请先缩小筛选范围'
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
    // 目标库仍须属于 ODS 分域配置，按当前租户读取有效数据源。
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
    var visibleRows = (dbRows)
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
    if (blank(previewDbId) || pageNum < 1 || pageNum > 100000 || pageSize < 1 || pageSize > 100) {
        exit 400, '来源表分页参数无效'
    }
    var previewDb = db.table('db_datasource_t').where().eq('tenant_id', tenantId)
        .eq('tid', previewDbId).eq('is_del', 0).gt('asset_status', 0).selectOne()
    if (previewDb == null) {
        exit 403, '来源库不存在或不在当前租户有效记录范围内'
    }
    // 保留预览只接受表名排序的接口校验。
    tableSort.field(body, {tableName: 'tableName'}, 'tableName')
    var sourceDirection = tableSort.direction(body)
    var source = tableRead.from('db_table_t', 's', tenantId)
    var sourceColumn = field => tableRead.column('s', field, 'TEXT')
    var nameColumn = sourceColumn('table_name')
    var chineseNameColumn = sourceColumn('table_name_cn')
    source.where(tableRead.equal(sourceColumn('datasource_id'), previewDbId))
    source.where(tableRead.equal(tableRead.column('s', 'is_del', 'NUMBER'), 0))
    source.where(tableRead.greaterThan(tableRead.column('s', 'asset_status', 'NUMBER'), 0))
    source.where(tableRead.in(sourceColumn('business_type'), ['业务表', '日志表', '字典表']))
    var sourceName = tableRead.coalesce([tableRead.blankAsNull(chineseNameColumn), nameColumn])
    var tablePage = source.page({tid: sourceColumn('tid'), tableName: nameColumn, tableNameCn: chineseNameColumn,
        sourceCatalogId: sourceColumn('source_catalog_id')}, sourceName, sourceDirection, 'tid', 'asc',
        pageNum, pageSize, true)
    var tableRows = tablePage.rows
    if (tableRows.size() == 0) {
        return {list: [], total: tablePage.total}
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
        // 幂等比较以提交时顺序为准，详情排序只影响展示。
        var priorItems = itemsOf(priorJob.jobId, true)
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
    var sourceStatus = sourceDb?.assetStatus == null ? sourceDb?.asset_status : sourceDb.assetStatus
    if (sourceDb == null || targetDb == null || sourceStatus == null || sourceStatus::int <= 0) {
        exit 400, '所选数据库不存在、不属于当前租户或来源库尚未完成登记'
    }
    // 当前租户的数据源只批量读取一次，随后每个
    // 来源/目标 ID 都须通过已授权的数据源关系；混入无权项整单拒绝。
    var visibleDbs = ([sourceDb, targetDb])
    var visibleDbIds = []
    for (visibleDb in visibleDbs) { visibleDbIds.add(text(visibleDb.tid)) }
    if (!visibleDbIds.contains(sourceDbId) || !visibleDbIds.contains(targetDbId)) {
        exit 403, '来源库或目标库不存在或已删除'
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
                if (sourceTable == null || (sourceTable.assetStatus == null || sourceTable.assetStatus::int <= 0)
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
            var itemRows = []
            var index = 0
            for (checked in checkedItems) {
                itemRows.add({
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
            db.table('ods_batch_create_item_t').batchInsert(itemRows)
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
        assertJobVisible(lockJob(jobId))
        db.table('ods_batch_create_job_t').where().eq('tenant_id', tenantId).eq('job_id', jobId)
            .update({stop_requested: 1, status: 'STOPPED', finished_time: new Date(), updated_time: new Date()})
        return detail(jobId)
    })
}
if (action == 'retry') {
    return db.transaction(() => {
        assertJobVisible(lockJob(jobId))
        var freshRunning = db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
            .eq('job_id', jobId).eq('status', 'RUNNING').gt('claimed_time', leaseCutoff).selectOne()
        if (freshRunning != null) { exit 409, '仍有创建项正在执行，请稍后重试' }
        var counts = countsOf(jobId)
        if (counts.pending + counts.failed + counts.running == 0) { return detail(jobId) }
        // 重试释放旧租约，同时清空上一次错误和作业结束时间。
        db.table('ods_batch_create_item_t').withBlank().where().eq('tenant_id', tenantId)
            .eq('job_id', jobId).in('status', ['FAILED', 'RUNNING']).update({
                status: 'PENDING', lease_token: null, claimed_time: null,
                error_message: null, updated_time: new Date()
            })
        db.table('ods_batch_create_job_t').withBlank().where().eq('tenant_id', tenantId).eq('job_id', jobId)
            .update({stop_requested: 0, status: 'RUNNING', finished_time: null, updated_time: new Date()})
        return detail(jobId)
    })
}
if (action == 'claim') {
    var leaseColumn = itemColumn('lease_token', 'TEXT')
    var claimProjection = {itemId: itemIdColumn, itemIndex: itemIndexColumn, sourceTableId: itemSourceColumn,
        attemptCount: itemProjection.attemptCount, leaseToken: leaseColumn}
    var runningOf = (column, value) => {
        var query = itemRead()
        query.where(tableRead.equal(column, value))
        query.where(tableRead.equal(itemStatusColumn, 'RUNNING'))
        return query
    }
    var hasFreshLease = (column, value) => {
        var query = runningOf(column, value)
        query.where(tableRead.greaterThan(itemClaimedColumn, leaseCutoff))
        return query.rows({itemId: itemIdColumn}, itemIdColumn, 'asc', 1).size() > 0
    }
    var claimPlan = () => {
        if (hasFreshLease(itemJobColumn, jobId)) { return {busy: true, item: null, stale: null} }
        var expired = runningOf(itemJobColumn, jobId).rows(claimProjection, itemIndexColumn, 'asc', 1)
        var pendingQuery = itemRead()
        pendingQuery.where(tableRead.equal(itemJobColumn, jobId))
        pendingQuery.where(tableRead.equal(itemStatusColumn, 'PENDING'))
        var pending = pendingQuery.rows(claimProjection, itemIndexColumn, 'asc', 1)
        var stale = expired.size() == 0 ? null : expired[0]
        var item = pending.size() == 0 ? null : pending[0]
        if (stale != null && (item == null || stale.itemIndex < item.itemIndex)) { item = stale }
        return {busy: false, item: item, stale: stale}
    }
    var lockSource = (id, activeOnly) => tableWrite.lockRow('db_table_t', tenantId, 'tid', id,
        ['tid'], activeOnly ? {is_del: 0} : {})
    var expiredItems = () => db.table('ods_batch_create_item_t').withBlank().where()
        .eq('tenant_id', tenantId).eq('status', 'RUNNING')
        .and(expiry => expiry.isNull('claimed_time').or().lte('claimed_time', leaseCutoff))
    var resetExpired = query => query.update({
        status: 'PENDING', lease_token: null, claimed_time: null, updated_time: new Date()
    })
    var resetOwn = stale => {
        var query = expiredItems().eq('job_id', jobId).eq('item_id', stale.itemId)
        if (stale.leaseToken == null) { query.isNull('lease_token') }
        else { query.eq('lease_token', stale.leaseToken) }
        if (resetExpired(query) != 1) { exit 409, '创建项租约已变化，请刷新作业后重试' }
    }
    var busy = {done: false, busy: true, status: 'RUNNING'}
    var before = ownedJob(jobId)
    var attempts = 0
    while (attempts < 3) {
        attempts = attempts + 1
        // 事务外只预选候选。事务内须先取得主键锁，再用新的读视图确认。
        var tentative = before.stopRequested == true || text(before.stopRequested) == '1'
            ? {item: null, stale: null} : claimPlan()
        var result = db.transaction(() => {
            var job = lockJob(jobId)
            var sourceTableId = text(tentative.item?.sourceTableId)
            var staleSourceId = text(tentative.stale?.sourceTableId)
            var secondSource = !blank(staleSourceId) && staleSourceId != sourceTableId
            var staleFirst = secondSource && staleSourceId.compareTo(sourceTableId) < 0
            // 同时涉及两个来源时统一按主键顺序加锁，避免两作业互等。
            if (staleFirst) { lockSource(staleSourceId, false) }
            var sourceLock = blank(sourceTableId) ? null : lockSource(sourceTableId, true)
            if (secondSource && !staleFirst) { lockSource(staleSourceId, false) }

            // MySQL 的普通读从这里开始，能看到等待来源锁期间提交的其他作业租约。
            assertJobVisible(job)
            if (job.stopRequested == true || text(job.stopRequested) == '1') {
                return {done: true, stopped: true, status: 'STOPPED'}
            }
            var plan = claimPlan()
            if (plan.busy) { return busy }
            if (plan.item?.itemId != tentative.item?.itemId || plan.stale?.itemId != tentative.stale?.itemId) {
                return {retryClaim: true}
            }
            if (plan.item == null) { return {done: true, status: refreshStatus(jobId, job)} }
            if (sourceLock == null) { exit 409, '来源表已失效，请刷新批量作业' }
            if (hasFreshLease(itemSourceColumn, sourceTableId)) {
                if (plan.stale != null) { resetOwn(plan.stale) }
                return busy
            }

            // 无有效租约才回收同源历史失联项。只更新预取的有限主键，避免范围锁。
            var expired = runningOf(itemSourceColumn, sourceTableId)
                .rows({itemId: itemIdColumn}, itemIdColumn, 'asc', 501)
            if (expired.size() > 500) { exit 409, '来源表失联创建项过多，请先核查作业' }
            var expiredIds = []
            for (row in expired) { expiredIds.add(row.itemId) }
            if (plan.stale != null && !expiredIds.contains(plan.stale.itemId)) { resetOwn(plan.stale) }
            if (expiredIds.size() > 0) {
                var reset = resetExpired(expiredItems().eq('source_table_id', sourceTableId).in('item_id', expiredIds))
                if (reset != expiredIds.size()) { exit 409, '创建项租约已变化，请刷新作业后重试' }
            }

            var item = plan.item
            var lease = IdUtil.simpleUUID()
            var claimed = db.table('ods_batch_create_item_t').where().eq('tenant_id', tenantId)
                .eq('job_id', jobId).eq('item_id', item.itemId).eq('status', 'PENDING').update({
                    status: 'RUNNING', lease_token: lease, attempt_count: item.attemptCount + 1,
                    claimed_time: new Date(), updated_time: new Date()
                })
            if (claimed != 1) { exit 409, '创建项已变化，请刷新作业后重试' }
            db.table('ods_batch_create_job_t').where().eq('tenant_id', tenantId).eq('job_id', jobId)
                .update({status: 'RUNNING', updated_time: new Date()})
            var claimedQuery = itemRead()
            claimedQuery.where(tableRead.equal(itemJobColumn, jobId))
            claimedQuery.where(tableRead.equal(itemIdColumn, item.itemId))
            var claimedItem = claimedQuery.rows(itemProjection, itemIdColumn, 'asc', 1)[0]
            return {done: false, busy: false, item: claimedItem, leaseToken: lease}
        })
        if (result.retryClaim != true) { return result }
        if (attempts < 3) { before = ownedJob(jobId) }
    }
    exit 409, '作业队列已变化，请刷新作业后重试'
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
        var job = assertJobVisible(lockJob(jobId))
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
        // 成功回放须同时匹配原回报令牌和任务。被替换的旧租约不能借同一任务冒充成功。
        if (item.status == 'SUCCEEDED' && success && item.taskId == taskId && item.leaseToken == leaseToken) {
            return detail(jobId)
        }
        if (item.status != 'RUNNING' || item.leaseToken != leaseToken) {
            exit 409, '创建项租约已失效，请刷新作业并按幂等流程重试'
        }
        var changed = db.table('ods_batch_create_item_t').withBlank().where().eq('tenant_id', tenantId)
            .eq('job_id', jobId).eq('item_id', itemId).eq('status', 'RUNNING').eq('lease_token', leaseToken)
            .update({status: success ? 'SUCCEEDED' : 'FAILED', task_id: success ? taskId : null,
                was_existing: success ? (body?.existing == true ? 1 : 0) : null,
                error_message: success ? null : errorMessage,
                completed_time: new Date(), updated_time: new Date()})
        if (changed != 1) { exit 409, '创建项租约已失效，请刷新作业并按幂等流程重试' }
        refreshStatus(jobId, job)
        return detail(jobId)
    })
}
exit 400, '不支持的批量作业操作'
