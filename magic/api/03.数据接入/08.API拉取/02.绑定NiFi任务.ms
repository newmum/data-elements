{
  "properties": {},
  "id": "ods_api_pull_bind_task_01",
  "script": null,
  "groupId": "ods_api_pull_group_01",
  "name": "02.绑定NiFi任务",
  "path": "/bind-task",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "接收 datasourceId、sourceTableId 和 pipelineId，按当前租户及接入数据源角色范围校验登记表、接入任务和有效画布，事务内将现有 API 拉取规则绑定到实际读取 API 的首跳。返回适用状态、绑定画布及更新条数；非拉取来源安全跳过。"
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableWrite
import cn.hutool.json.JSONUtil
import java.util.Map
import java.util.List

// POST /ods/api-pull/bind-task 在显式创建或复用接入任务后维护 API 拉取规则。
// 接收数据源、根来源表和画布标识。校验认证租户、来源权限和持久化任务关系后绑定实际采集的首跳画布。
// 返回适用状态、绑定画布和规则更新条数。接口只更新已有规则，不创建或启动流程。
var tenantId = tenantRuntime.id()
var text = value => value == null ? '' : '' + value
var datasourceId = text(body?.datasourceId).trim()
var sourceTableId = text(body?.sourceTableId).trim()
var pipelineId = text(body?.pipelineId).trim()
if (text(tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录'
}
if (datasourceId == '') {
    exit 400, '数据源标识不能为空'
}
if (sourceTableId == '') {
    exit 400, '来源表标识不能为空'
}
if (pipelineId == '') {
    exit 400, 'NiFi 流程标识不能为空'
}

var active = { is_del: 0 }
var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var activeColumn = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
var sourceFields = ['tid', 'db_type', 'pool_cfg', 'org_id', 'created_by']
var tableFields = ['tid', 'datasource_id', 'source_table_id']
var taskFields = ['tid', 'pipeline_id', 'source_db_id', 'source_table_id', 'target_db_id', 'target_table_id']
var jsonObject = value => {
    if (value instanceof Map) {
        return value
    }
    try {
        return text(value).trim() == '' ? {} : JSONUtil.parseObj(text(value))
    } catch (e) {
        return {}
    }
}
var isApiPull = datasource => {
    if (text(datasource.db_type).toLowerCase() != 'api') {
        return false
    }
    var config = jsonObject(datasource.pool_cfg)
    var accessMode = null
    for (key in config.keySet()) {
        if (text(key).toLowerCase() == 'accessmode') {
            if (accessMode != null) {
                return false
            }
            accessMode = text(config[key]).toLowerCase()
        }
    }
    return ['capture', 'pull'].contains(accessMode)
}

// 其他类型也会从接入工作台调用本接口。只读判断适用性，沿用原有跳过结果。
var sourceQuery = tableRead.from('db_datasource_t', 'd', tenantId)
sourceQuery.where(tableRead.equal(column('d', 'tid'), datasourceId))
sourceQuery.where(activeColumn('d'))
var sourceRows = sourceQuery.grouped({
    tid: column('d', 'tid'),
    db_type: column('d', 'db_type'),
    pool_cfg: tableRead.column('d', 'pool_cfg', 'LOB')
}, [])
if (sourceRows.size() != 1 || text(sourceRows[0].tid) != datasourceId) {
    exit 400, '数据源不存在或不属于当前租户'
}
if (!isApiPull(sourceRows[0])) {
    return {applicable: false, message: '当前来源不是 API 数据拉取，未绑定表规则'}
}

var readTasks = (condition, limit) => {
    var tasks = tableRead.from('data_access_agg_task_t', 't', tenantId)
    tasks.leftJoin('nifi_pipeline_t', 'p', 'id', 'pipeline_id', [activeColumn('p')])
    tasks.where(activeColumn('t'))
    tasks.where(tableRead.not(tableRead.isNull(column('p', 'id'))))
    tasks.where(condition)
    return tasks.rows({
        tid: column('t', 'tid'),
        pipeline_id: column('t', 'pipeline_id'),
        active_pipeline_id: column('p', 'id'),
        source_db_id: column('t', 'source_db_id'),
        source_table_id: column('t', 'source_table_id'),
        target_db_id: column('t', 'target_db_id'),
        target_table_id: column('t', 'target_table_id')
    }, column('t', 'tid'), 'asc', limit)
}
var isActivePipeline = task => text(task.pipeline_id) != ''
    && text(task.active_pipeline_id) == text(task.pipeline_id)
var lockTask = selected => {
    var task = tableWrite.lockRow('data_access_agg_task_t', tenantId, 'tid', selected.tid, taskFields, active)
    if (task == null) {
        exit 403, 'NiFi 流程不存在或不属于当前接入任务'
    }
    for (field in taskFields) {
        if (text(task[field]) != text(selected[field])) {
            exit 409, '接入任务已变化，请重新读取后重试'
        }
    }
}

var readTarget = targetId => {
    var target = tableRead.from('db_table_t', 'r', tenantId)
    target.where(tableRead.equal(column('r', 'tid'), targetId))
    target.where(activeColumn('r'))
    var rows = target.grouped({
        tid: column('r', 'tid'), datasource_id: column('r', 'datasource_id'),
        source_table_id: column('r', 'source_table_id')
    }, [])
    return rows.size() == 1 ? rows[0] : null
}

var readDownstreamDesign = () => {
    var query = tableRead.from('nifi_pipeline_t', 'p', tenantId)
    query.where(tableRead.equal(column('p', 'id'), pipelineId))
    query.where(activeColumn('p'))
    var rows = query.grouped({
        id: column('p', 'id'), dsl_json: tableRead.column('p', 'dsl_json', 'LOB')
    }, [])
    if (rows.size() != 1 || text(rows[0].id) != pipelineId) {
        exit 403, 'NiFi 流程不存在或不属于当前接入任务'
    }
    return text(rows[0].dsl_json)
}

var firstPipelineForDownstream = (selected, savedDesign) => {
    // 复用全部旧任务时，任务接口按最近修改返回，首项可能是下游；根据已保存的画布和表关系回溯实际首跳。
    var design = jsonObject(savedDesign)
    var selectedSource = text(selected.source_table_id)
    var selectedTarget = text(selected.target_table_id)
    var sinks = design.nodes instanceof List
        ? design.nodes.filter(node => text(node?.category) == 'sink') : []
    var chain = sinks.size() == 1 ? sinks[0]?.config?.accessChain : null
    if (text(chain?.rootSourceTableId) != sourceTableId || text(chain?.sourceTableId) != selectedSource
        || text(chain?.targetTableId) != selectedTarget || selectedSource == '' || selectedTarget == '') {
        exit 403, 'NiFi 流程不属于当前 API 来源链路'
    }
    var related = tableRead.from('db_table_t', 'r', tenantId)
    related.where(activeColumn('r'))
    related.where(tableRead.equal(column('r', 'source_table_id'), sourceTableId))
    var targets = related.rows({
        tid: column('r', 'tid'), datasource_id: column('r', 'datasource_id'),
        source_table_id: column('r', 'source_table_id')
    }, column('r', 'tid'), 'asc', 101)
    if (targets.size() > 100) {
        exit 409, '当前来源的物化目标过多，请先核对接入链路'
    }
    var sourceByTable = {}
    sourceByTable[sourceTableId] = datasourceId
    var ids = [sourceTableId]
    for (target in targets) {
        var targetId = text(target.tid)
        if (targetId != sourceTableId && text(target.source_table_id) == sourceTableId && text(target.datasource_id) != '') {
            sourceByTable[targetId] = text(target.datasource_id)
            ids.add(targetId)
        }
    }
    var matchesRegistration = task => sourceByTable[text(task.source_table_id)] != null
        && sourceByTable[text(task.source_table_id)] == text(task.source_db_id)
        && sourceByTable[text(task.target_table_id)] != null
        && sourceByTable[text(task.target_table_id)] == text(task.target_db_id)
    if (!matchesRegistration(selected) || selectedTarget == sourceTableId) {
        exit 403, 'NiFi 流程不属于当前 API 来源链路'
    }
    // 一次预取当前根来源的活动任务与画布，循环仅在已校验的有界集合中回溯。
    var tasks = readTasks(tableRead.all([
        tableRead.in(column('t', 'source_table_id'), ids),
        tableRead.in(column('t', 'target_table_id'), ids)
    ]), 201)
    if (tasks.size() > 200) {
        exit 409, '当前来源的接入任务过多，请先核对接入链路'
    }
    var predecessors = {}
    for (task in tasks) {
        if (isActivePipeline(task) && matchesRegistration(task)) {
            var targetId = text(task.target_table_id)
            if (predecessors[targetId] == null) {
                predecessors[targetId] = []
            }
            predecessors[targetId].add(task)
        }
    }
    var targetOrder = design?.accessChain?.targetTableIds
    var selectedIndex = targetOrder instanceof List ? targetOrder.indexOf(selectedTarget) : -1
    if (targetOrder instanceof List && (selectedIndex < 1 || text(targetOrder[selectedIndex - 1]) != selectedSource)) {
        exit 403, 'NiFi 流程不属于当前 API 来源链路'
    }
    var current = selectedSource
    var visited = [selectedTarget]
    var firstTask = null
    while (current != sourceTableId) {
        if (visited.contains(current)) {
            exit 409, '接入链路存在循环，请先核对任务'
        }
        visited.add(current)
        var expectedSource = selectedIndex < 0 ? null
            : selectedIndex == 1 ? sourceTableId : text(targetOrder[selectedIndex - 2])
        var candidates = predecessors[current] == null ? [] : predecessors[current]
        var preceding = candidates.filter(task => expectedSource == null || expectedSource == text(task.source_table_id))
        if (preceding.size() != 1) {
            exit 409, '无法唯一确认接入链路的上一跳，请先核对任务'
        }
        firstTask = preceding[0]
        current = text(firstTask.source_table_id)
        if (selectedIndex > 0) {
            selectedIndex--
        }
    }
    return firstTask
}

var bind = db.transaction(() => {
    // 与显式创建任务遵循同一根来源锁顺序，再复核数据源归属、角色范围及实时接入方式。
    var sourceTable = tableWrite.lockRow('db_table_t', tenantId, 'tid', sourceTableId, tableFields,
        { is_del: 0, datasource_id: datasourceId })
    if (sourceTable == null || text(sourceTable.tid) != sourceTableId || text(sourceTable.datasource_id) != datasourceId) {
        exit 403, '来源表不存在或不属于当前数据源'
    }
    var source = tableWrite.lockRow('db_datasource_t', tenantId, 'tid', datasourceId, sourceFields, active)
    if (source == null || text(source.tid) != datasourceId || !dataScope.canReadDataSource(source)) {
        exit 403, '当前角色无权绑定此数据源的拉取规则'
    }
    if (!isApiPull(source)) {
        exit 409, '数据源接入方式已变化，请重新读取后重试'
    }
    var selectedTasks = readTasks(tableRead.equal(column('t', 'pipeline_id'), pipelineId), 2)
    if (selectedTasks.size() != 1 || text(selectedTasks[0].pipeline_id) != pipelineId || !isActivePipeline(selectedTasks[0])) {
        exit 403, 'NiFi 流程不存在或不属于当前接入任务'
    }
    var selected = selectedTasks[0]
    var resolvedTask = selected
    var downstream = text(selected.source_table_id) != sourceTableId
    var savedDesign = ''
    if (downstream) {
        savedDesign = readDownstreamDesign()
        resolvedTask = firstPipelineForDownstream(selected, savedDesign)
    }
    // 与取消操作使用相同的加锁顺序。先核对任务，随后加锁画布，再写入规则。
    lockTask(selected)
    if (text(selected.tid) != text(resolvedTask.tid)) {
        lockTask(resolvedTask)
    }
    if (downstream) {
        var requestedPipeline = tableWrite.lockRow('nifi_pipeline_t', tenantId, 'id', pipelineId, ['id', 'dsl_json'], active)
        if (requestedPipeline == null || text(requestedPipeline.id) != pipelineId
            || !savedDesign.equals(text(requestedPipeline.dsl_json))) {
            exit 409, 'NiFi 流程已变化，请重新读取后重试'
        }
    }
    var firstPipeline = text(resolvedTask.pipeline_id)
    var first = tableWrite.lockRow('nifi_pipeline_t', tenantId, 'id', firstPipeline, ['id'], active)
    if (first == null || text(first.id) != firstPipeline) {
        exit 403, 'NiFi 流程不存在或不属于当前接入任务'
    }
    if (text(selected.source_table_id) == sourceTableId) {
        var targetId = text(selected.target_table_id)
        if (text(selected.source_db_id) != datasourceId || targetId == '' || targetId == sourceTableId) {
            exit 403, 'NiFi 流程不属于当前 API 来源链路'
        }
        var target = readTarget(targetId)
        if (target == null || text(target.tid) != targetId || text(target.datasource_id) == ''
            || text(target.datasource_id) != text(selected.target_db_id)
            || (text(target.source_table_id) != '' && text(target.source_table_id) != sourceTableId)) {
            exit 403, 'NiFi 流程不属于当前 API 来源链路'
        }
    }

    // 每租户每个有效来源表只允许一条规则。保留原有系统更新人与数据库微秒时钟。
    var rule = tableWrite.lockRow('data_pull_rule_t', tenantId, 'source_table_id', sourceTableId,
        ['tid', 'datasource_id', 'source_table_id'], { is_del: 0, datasource_id: datasourceId })
    if (rule == null) {
        return { pipelineId: firstPipeline, boundRules: 0 }
    }
    if (text(rule.datasource_id) != datasourceId || text(rule.source_table_id) != sourceTableId) {
        exit 403, '拉取规则不属于当前数据源和来源表'
    }
    var changed = tableWrite.updateWithDatabaseTimestamp('data_pull_rule_t', tenantId, 'tid', rule.tid,
        { nifi_task_id: firstPipeline, updated_by: 'system' },
        { is_del: 0, datasource_id: datasourceId, source_table_id: sourceTableId }, 'updated_time', 6)
    return { pipelineId: firstPipeline, boundRules: changed }
})
return {applicable: true, pipelineId: bind.pipelineId, boundRules: bind.boundRules,
        message: bind.boundRules > 0
            ? '表级拉取规则已绑定 NiFi 任务；可在画布中部署、手动启动或按 Cron 调度'
            : '当前表尚未同步出 API 拉取规则，请返回第二步保存表标注后重试'}
