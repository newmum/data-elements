{
  "properties": {},
  "id": "3b16670b63db46ae90ddd95e3b0d2406",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "38.NiFi接入任务链路读取",
  "path": "/nifiTaskChainRows",
  "createTime": 1794631082358,
  "updateTime": null,
  "parameters": [
    { "name": "kind", "description": "仅允许链路定义中已登记的读取类型", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "identifier", "description": "已由业务入口验证的任务、流程或来源根表标识", "required": false, "dataType": "String", "type": "java.lang.String" },
    { "name": "byPipeline", "description": "锚点或保存态按流程标识读取", "required": false, "dataType": "Boolean", "type": "java.lang.Boolean" },
    { "name": "ids", "description": "业务入口收集的流程、表或数据源标识集合", "required": false, "dataType": "Array", "type": "java.util.List" }
  ],
  "options": [],
  "description": "读取当前认证行业租户中的接入任务链路锚点、任务、保存态画布或登记数据。固定层级，批量按标识查询，单项读取至多一条 SQL。调用方继续逐标识校验关联和权限，函数不写入数据。"
}
================================
import tableRead
import tenantRuntime
import java.lang.IllegalArgumentException
import java.util.LinkedHashMap

// @/common/nifiTaskChainRows 为接入任务链路提供有界读取窗口。kind 选择读取类型，
// identifier 和 byPipeline 选择单个任务、关联画布或来源根表，ids 用于批量查找。
// 仅访问可信租户数据库。由上层核对每项关系及权限；此函数不读 NiFi，也不写业务数据。
var tenantId = tenantRuntime.id()
var maxDepth = 16
var maxTasks = 100
var maxResourceIds = 200
var reader = (tableName, alias) => tableRead.from(tableName, alias, tenantId).withTimeoutSeconds(10)
var textColumn = (alias, field) => tableRead.column(alias, field, 'TEXT')
var numberColumn = (alias, field) => tableRead.column(alias, field, 'NUMBER')
var active = alias => tableRead.equal(numberColumn(alias, 'is_del'), 0)

if (kind == 'anchor' || kind == 'tasks' || kind == 'saved') {
    if (identifier == null || identifier.isBlank()) {
        throw new IllegalArgumentException('缺少接入任务链路读取标识')
    }
    var task = reader('data_access_agg_task_t', 'a')
    task.where(active('a'))
    task.leftJoin('db_table_t', 's0', 'tid', 'source_table_id', [active('s0')])
    var taskId = textColumn('a', 'tid')
    var pipelineId = textColumn('a', 'pipeline_id')

    if (kind != 'saved') {
        // 在内存构造固定 16 级关联，整条链路由单条查询读取。自身 ID 是旧登记的根标记。
        var level = 1
        while (level <= maxDepth) {
            var previous = 's' + (level - 1)
            var current = 's' + level
            var notSelf = tableRead.not(tableRead.equal(textColumn(previous, 'source_table_id'), textColumn(previous, 'tid')))
            task.leftJoinFrom(previous, 'db_table_t', current, 'tid', 'source_table_id', [active(current), notSelf])
            level = level + 1
        }
    }

    if (kind != 'tasks') {
        task.where(tableRead.equal(byPipeline ? pipelineId : taskId, identifier))
    }
    if (kind == 'anchor') {
        var anchor = {task_id: taskId, pipeline_id: pipelineId}
        var depth = 0
        while (depth <= maxDepth) {
            var ancestor = 's' + depth
            anchor.put('ancestor_' + depth, textColumn(ancestor, 'tid'))
            anchor.put('datasource_' + depth, textColumn(ancestor, 'datasource_id'))
            anchor.put('parent_' + depth, textColumn(ancestor, 'source_table_id'))
            depth = depth + 1
        }
        // 读取两行以区分唯一关联与重复画布标识。
        return task.rows(anchor, 2)
    }

    task.leftJoin('db_table_t', 'target', 'tid', 'target_table_id', [active('target')])
    var projection = {
        task_id: taskId,
        task_name: textColumn('a', 'task_name'),
        pipeline_id: pipelineId,
        source_db_id: textColumn('a', 'source_db_id'),
        target_db_id: textColumn('a', 'target_db_id'),
        source_table_id: textColumn('a', 'source_table_id'),
        target_table_id: textColumn('a', 'target_table_id'),
        source_table_name: textColumn('s0', 'table_name'),
        actual_source_db_id: textColumn('s0', 'datasource_id'),
        target_table_name: textColumn('target', 'table_name'),
        actual_target_db_id: textColumn('target', 'datasource_id')
    }
    if (kind == 'saved') {
        return task.rows(projection, 2)
    }

    projection.put('task_status', numberColumn('a', 'task_status'))
    projection.put('source_table_name_cn', textColumn('s0', 'table_name_cn'))
    projection.put('target_table_name_cn', textColumn('target', 'table_name_cn'))
    var related = [tableRead.equal(textColumn('s0', 'tid'), identifier)]
    var index = 1
    while (index <= maxDepth) {
        var relation = 's' + index
        related.add(tableRead.equal(textColumn(relation, 'tid'), identifier))
        projection.put('ancestor_datasource_' + index, textColumn(relation, 'datasource_id'))
        index = index + 1
    }
    task.where(tableRead.any(related))
    // 原链路沿用数据库对创建时间空值的顺序，以任务 ID 处理同一时间。
    return task.rows(projection, [tableRead.column('a', 'created_time', 'TIME'), taskId], ['asc', 'asc'], maxTasks + 1, true)
}

if (kind != 'pipelines' && kind != 'tables' && kind != 'historicalTables' && kind != 'sources') {
    throw new IllegalArgumentException('接入任务链路读取类型无效')
}
if (ids == null || ids.isEmpty()) {
    return []
}
var idLimit = kind == 'pipelines' ? maxTasks : maxResourceIds
if (ids.size() > idLimit) {
    throw new IllegalArgumentException('接入任务链路读取标识超过限制')
}

if (kind == 'pipelines') {
    var pipeline = reader('nifi_pipeline_t', 'p')
    var pipelineId = textColumn('p', 'id')
    pipeline.where(active('p'))
    pipeline.where(tableRead.in(pipelineId, ids))
    // 只取原画布解码和设计版本使用的字段，DSL 和节点映射保留完整长文本。
    return pipeline.rows({
        id: pipelineId,
        name: textColumn('p', 'name'),
        description: textColumn('p', 'description'),
        created_at: numberColumn('p', 'created_at'),
        updated_at: numberColumn('p', 'updated_at'),
        dsl_json: tableRead.column('p', 'dsl_json', 'LOB'),
        nifi_process_group_id: textColumn('p', 'nifi_process_group_id'),
        status: textColumn('p', 'status'),
        last_deployed_hash: textColumn('p', 'last_deployed_hash'),
        last_deployed_at: numberColumn('p', 'last_deployed_at'),
        last_stopped_at: numberColumn('p', 'last_stopped_at'),
        node_mapping_json: tableRead.column('p', 'node_mapping_json', 'LOB'),
        last_bulletin_id: numberColumn('p', 'last_bulletin_id')
    }, maxTasks + 1)
}

if (kind == 'sources') {
    var source = reader('db_datasource_t', 'd')
    var sourceId = textColumn('d', 'tid')
    source.where(active('d'))
    source.where(tableRead.in(sourceId, ids))
    return source.rows({tid: sourceId, db_name: textColumn('d', 'db_name')}, maxResourceIds + 1)
}

var table = reader('db_table_t', 't')
var tableId = textColumn('t', 'tid')
// 保存态可核查同租户已逻辑删除的历史登记；正常链路仅接受有效表。
if (kind == 'tables') {
    table.where(active('t'))
}
table.where(tableRead.in(tableId, ids))
return table.rows({tid: tableId, datasource_id: textColumn('t', 'datasource_id')}, maxResourceIds + 1)
