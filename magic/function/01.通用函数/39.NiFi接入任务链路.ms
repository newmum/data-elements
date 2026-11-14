{
  "properties": {},
  "id": "eb7168fdec7a422cbdd49784a9c7c981",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "39.NiFi接入任务链路",
  "path": "/nifiAccessTaskChain",
  "createTime": 1794631004967,
  "updateTime": null,
  "parameters": [
    {
      "name": "identifier",
      "description": "访问任务或已登记画布的唯一标识",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "byPipeline",
      "description": "为真时按画布标识定位接入任务，否则按任务标识定位",
      "required": true,
      "dataType": "Boolean",
      "type": "java.lang.Boolean"
    },
    {
      "name": "nativeView",
      "description": "是否为用户主动打开 NiFi 原生画布的请求",
      "required": true,
      "dataType": "Boolean",
      "type": "java.lang.Boolean"
    }
  ],
  "options": [],
  "description": "按当前租户逐项校验接入任务、来源、目标和保存画布，返回有界任务链路及展示连线。关联异常时可回退到已授权的当前画布。仅主动打开原生画布时按需读取一次 NiFi 根组快照，无写入。"
}
================================
import '@/common/nifiTaskChainRows' as query
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineResourceReferences
import java.lang.IllegalStateException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Objects
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import nifiTaskChainRuntime

// @/common/nifiAccessTaskChain 按 identifier 和 byPipeline 读取接入任务及画布，返回展示节点、连线和原生入口。
// 所有查库由共享函数从当前行业会话确定可信租户。普通画布只查库；nativeView 为真时才可能查 NiFi。
// 只有链路冲突可回退到已授权的已保存画布。越权、未找到和当前画布不可读继续返回对应错误。函数无写入。
var maxDepth = 16
var maxTasks = 100
var maxReferences = 200
var normalNativeMessage = '各任务保留独立的NiFi流程组，可选择任务打开原生画布'
var nativeUrlPrefix = '/nifi-ui/#/process-groups/'

var text = (row, key) => row == null ? '' : Objects.toString(row.get(key), '').trim()
var required = value => {
    if (value == null || value.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, '缺少任务或画布标识')
    }
    return value.trim()
}
var conflict = reason => {
    throw new ResponseStatusException(HttpStatus.CONFLICT, reason)
}
var forbidden = reason => {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, reason)
}
var selectedTask = rows => {
    if (rows.size() != 1) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, '接入任务不存在或关联不唯一')
    }
    return rows[0]
}
var id = required(identifier)

// 逐 ID 验证批量查回的资源。软删除的表仅能授权同租户的历史画布查看，再核验其实际数据源。
var includeTableSources = (ids, datasourceIds, historical) => {
    if (ids.isEmpty()) {
        return
    }
    var kind = historical ? 'historicalTables' : 'tables'
    var rows = query(kind, null, false, new ArrayList(ids))
    var resolved = new LinkedHashSet()
    for (row in rows) {
        resolved.add(text(row, 'tid'))
        datasourceIds.add(text(row, 'datasource_id'))
    }
    if (!resolved.equals(ids)) {
        forbidden(historical ? '任务引用的数据表不存在或无访问权限' : '链路引用的数据表不存在或无访问权限')
    }
}
var authorizedSources = ids => {
    if (ids.contains('') || ids.size() > maxReferences) {
        conflict('数据源关联不完整或超过200项')
    }
    var result = new LinkedHashMap()
    for (row in query('sources', null, false, new ArrayList(ids))) {
        result.put(text(row, 'tid'), row)
    }
    if (!result.keySet().equals(ids)) {
        forbidden('链路数据源不存在或无访问权限')
    }
    return result
}

// 一次读取全部候选画布，先限制 DSL 总字节及节点数量，再根据精确 ID 集合验证读取范围。
var loadPipelines = ids => {
    var result = new LinkedHashMap()
    var nodeCount = 0
    var byteCount = 0
    for (row in query('pipelines', null, false, new ArrayList(ids))) {
        var source = text(row, 'dsl_json')
        byteCount = byteCount + source.getBytes(StandardCharsets.UTF_8).length
        if (byteCount > 8 * 1024 * 1024) {
            conflict('链路画布超过8MB，请分段管理')
        }
        var dsl = null
        try {
            dsl = nifiTaskChainRuntime.dsl(source)
        } catch (invalid) {
            conflict('任务画布内容无法读取，请检查保存的配置')
        }
        if (dsl == null) {
            conflict('任务画布内容无法读取，请检查保存的配置')
        }
        var nodes = dsl.nodes()
        nodeCount = nodeCount + (nodes == null ? 0 : nodes.size())
        if (nodeCount > 5000) {
            conflict('链路组件超过5000个，请分段管理')
        }
        try {
            var pipeline = nifiTaskChainRuntime.pipeline(row, dsl)
            result.put(pipeline.id(), pipeline)
        } catch (invalid) {
            conflict('任务画布内容无法读取，请检查保存的配置')
        }
    }
    if (!result.keySet().equals(ids)) {
        forbidden('链路画布不存在或不属于当前租户')
    }
    return result
}

var validateTaskSource = (task, pipeline) => {
    var nodes = pipeline.dsl().nodes()
    if (nodes == null) {
        return
    }
    var actualSource = text(task, 'actual_source_db_id')
    var taskSource = text(task, 'source_db_id')
    var taskTable = text(task, 'source_table_id')
    var matchedSource = actualSource == taskSource
    for (node in nodes) {
        if (node.category() != 'source' || node.config() == null) {
            continue
        }
        var config = node.config()
        var table = text(config, 'sourceTableId')
        if (!table.isBlank() && table != taskTable) {
            conflict('任务来源表与保存的画布不一致，请检查任务关联')
        }
        var physical = text(config, 'registeredDatasourceId')
        // 历史推送只在来源表、逻辑来源、ODS 域及物理 FTP/SFTP 共同匹配时允许双重来源标识。
        var pushStorage = ['source.ftp', 'source.sftp'].contains(node.manifestKey())
            && table == taskTable && !table.isBlank()
            && actualSource == text(config, 'logicalSourceDatasourceId')
            && text(config, 'pushStorageDomain') == 'fjghcc'
            && !physical.isBlank() && physical != actualSource
            && (taskSource == actualSource || taskSource == physical)
        if (pushStorage) {
            matchedSource = true
        }
        for (key in ['registeredDatasourceId', 'sourceDbId', 'selectedDatabaseId', 'logicalSourceDatasourceId']) {
            var database = text(config, key)
            var matches = pushStorage ? (database == actualSource || database == physical) : database == taskSource
            if (!database.isBlank() && !matches) {
                conflict('任务来源库与保存的画布不一致，请检查任务关联')
            }
        }
    }
    if (!matchedSource) {
        conflict('任务来源库与登记表不一致，请检查任务关联')
    }
}
var readRoots = (selected, datasourceIds) => {
    var ancestry = new LinkedHashSet()
    var root = ''
    var depth = 0
    while (depth <= maxDepth) {
        var table = text(selected, 'ancestor_' + depth)
        if (table.isBlank()) {
            if (depth == 0 || !text(selected, 'parent_' + (depth - 1)).isBlank()) {
                conflict('来源表关联不完整，请检查登记记录')
            }
            break
        }
        if (!ancestry.add(table)) {
            conflict('来源表存在循环关联')
        }
        datasourceIds.add(text(selected, 'datasource_' + depth))
        root = table
        var parent = text(selected, 'parent_' + depth)
        // 根表自引用是登记历史标记。多表循环、断链和超深关联按各自冲突处理。
        if (table == parent) {
            break
        }
        if (depth == maxDepth && !parent.isBlank()) {
            conflict('链路超过16级，请分段管理')
        }
        depth = depth + 1
    }
    return root
}

var readyForNative = pipeline => {
    var groupId = pipeline.nifiProcessGroupId()
    return groupId != null && !groupId.isBlank() && pipeline.lastDeployedHash() != null
}
var markNativeReady = (item, pipeline) => {
    var groupId = pipeline.nifiProcessGroupId()
    item.put('processGroupId', groupId)
    item.put('nativeUrl', nativeUrlPrefix + groupId)
}
var nativeSelection = (items, reason) => {
    for (item in items) {
        if (item.containsKey('nativeUrl')) {
            return {mode: 'SELECT_TASK', reason: reason}
        }
    }
    return {mode: 'NONE', reason: reason}
}
var itemFields = (task, sources, pipeline) => {
    var sourceDbId = text(task, 'source_db_id')
    var targetDbId = text(task, 'target_db_id')
    return {
        taskId: text(task, 'task_id'), taskName: text(task, 'task_name'),
        sourceTableId: text(task, 'source_table_id'), targetTableId: text(task, 'target_table_id'),
        sourceDbId: sourceDbId, targetDbId: targetDbId,
        sourceDbName: text(sources.get(sourceDbId), 'db_name'), targetDbName: text(sources.get(targetDbId), 'db_name'),
        sourceTableName: text(task, 'source_table_name'), targetTableName: text(task, 'target_table_name'),
        pipelineId: pipeline.id(), pipeline: pipeline
    }
}
var targetTables = (pipeline, fallback) => {
    var ids = new LinkedHashSet()
    ids.add(fallback)
    var nodes = pipeline.dsl().nodes()
    if (nodes != null) {
        for (node in nodes) {
            if (node.category() == 'sink' && node.config() != null) {
                var targetId = text(node.config(), 'targetTableId')
                if (!targetId.isBlank()) {
                    ids.add(targetId)
                }
            }
        }
    }
    return ids
}

var loadChain = () => {
    var selected = selectedTask(query('anchor', id, byPipeline, []))
    var datasourceIds = new LinkedHashSet()
    var root = readRoots(selected, datasourceIds)
    var tasks = query('tasks', root, false, [])
    if (tasks.size() > maxTasks) {
        conflict('链路超过100个任务，请分段管理')
    }
    var pipelineIds = new LinkedHashSet()
    var containsSelection = false
    for (task in tasks) {
        var sourceDb = text(task, 'actual_source_db_id')
        var targetDb = text(task, 'actual_target_db_id')
        var registeredSourceDb = text(task, 'source_db_id')
        if (sourceDb.isBlank() || targetDb.isBlank() || registeredSourceDb.isBlank() || targetDb != text(task, 'target_db_id')) {
            conflict('任务来源或目标关联不完整，请检查登记记录')
        }
        datasourceIds.add(sourceDb)
        datasourceIds.add(targetDb)
        datasourceIds.add(registeredSourceDb)
        var depth = 1
        while (depth <= maxDepth) {
            var ancestorDb = text(task, 'ancestor_datasource_' + depth)
            if (!ancestorDb.isBlank()) {
                datasourceIds.add(ancestorDb)
            }
            depth = depth + 1
        }
        pipelineIds.add(required(text(task, 'pipeline_id')))
        if (text(task, 'task_id') == text(selected, 'task_id')) {
            containsSelection = true
        }
    }
    if (pipelineIds.size() != tasks.size()) {
        conflict('多个任务关联同一画布，请检查任务关联')
    }
    if (!containsSelection) {
        conflict('选定任务不在来源链路中')
    }
    var pipelines = loadPipelines(pipelineIds)
    for (task in tasks) {
        validateTaskSource(task, pipelines.get(text(task, 'pipeline_id')))
    }
    // 多目标历史配置可能引用任务首目标之外的表。整批授权所有登记表及它们实际所属数据源。
    var dslTableIds = new LinkedHashSet()
    for (pipeline in pipelines.values()) {
        var nodes = pipeline.dsl().nodes()
        if (nodes == null) {
            continue
        }
        for (node in nodes) {
            if (node == null || node.config() == null) {
                continue
            }
            var config = node.config()
            for (key in ['registeredDatasourceId', 'logicalSourceDatasourceId', 'selectedDatabaseId', 'sourceDbId',
                'targetDbId', 'dbId', 'datasourceId', 'dataSourceId']) {
                var datasource = text(config, key)
                if (!datasource.isBlank()) {
                    datasourceIds.add(datasource)
                }
            }
            for (key in ['sourceTableId', 'targetTableId', 'registeredTableId']) {
                var table = text(config, key)
                if (!table.isBlank()) {
                    dslTableIds.add(table)
                }
            }
        }
    }
    if (dslTableIds.size() > maxReferences) {
        conflict('链路引用表超过200张，请分段管理')
    }
    includeTableSources(dslTableIds, datasourceIds, false)
    var sources = authorizedSources(datasourceIds)
    var items = []
    for (task in tasks) {
        var pipeline = pipelines.get(text(task, 'pipeline_id'))
        if (pipeline == null) {
            conflict('接入任务的画布不存在或已删除')
        }
        var item = itemFields(task, sources, pipeline)
        item.put('sourceTableNameCn', text(task, 'source_table_name_cn'))
        item.put('targetTableNameCn', text(task, 'target_table_name_cn'))
        if (readyForNative(pipeline)) {
            markNativeReady(item, pipeline)
        } else {
            item.put('unavailableReason', '当前任务尚未成功部署，请先保存并部署')
        }
        items.add(item)
    }

    // 展示连线来自实际任务表和旧多目标画布，每个来源的目标集只提取一次，按原任务次序串接。
    var links = []
    var downstreamTasks = new LinkedHashSet()
    if (items.size() > 1) {
        for (fromIndex, fromItem in items) {
            var targets = targetTables(fromItem.pipeline, fromItem.targetTableId)
            for (toIndex, toItem in items) {
                if (fromIndex != toIndex && targets.contains(toItem.sourceTableId)) {
                    links.add({fromTaskId: fromItem.taskId, toTaskId: toItem.taskId, tableId: toItem.sourceTableId})
                    downstreamTasks.add(toItem.taskId)
                }
            }
        }
    }
    for (item in items) {
        var stored = item.pipeline
        var design = stored
        if (downstreamTasks.contains(item.taskId)) {
            design = HiveSinkPublicationPolicy.downstreamTask(stored, item.sourceTableId, item.targetTableId)
        }
        item.put('pipeline', HiveSinkPublicationPolicy.forCanvas(design).withStoredDesignVersion(stored))
    }
    return {
        rootSourceTableId: root, selectedPipelineId: text(selected, 'pipeline_id'), selectedTaskId: text(selected, 'task_id'),
        items: items, links: links, nativeCanvas: nativeSelection(items, normalNativeMessage)
    }
}

var savedTask = reason => {
    var task = selectedTask(query('saved', id, byPipeline, []))
    var pipelineId = required(text(task, 'pipeline_id'))
    var ids = new LinkedHashSet()
    ids.add(pipelineId)
    var pipeline = loadPipelines(ids).get(pipelineId)
    var references = PipelineResourceReferences.from(pipeline)
    PipelineResourceReferences.addTask(pipeline, task, references)
    var datasourceIds = references.datasourceIds()
    var tableIds = references.tableIds()
    for (key in ['actual_source_db_id', 'actual_target_db_id']) {
        var datasource = text(task, key)
        if (!datasource.isBlank()) {
            datasourceIds.add(datasource)
        }
    }
    var standalone = PipelineResourceReferences.standalone(pipeline, task)
    if (tableIds.size() > maxReferences) {
        conflict('任务引用表超过200张')
    }
    includeTableSources(tableIds, datasourceIds, true)
    var sources = datasourceIds.isEmpty() ? new LinkedHashMap() : authorizedSources(datasourceIds)
    var item = itemFields(task, sources, HiveSinkPublicationPolicy.forCanvas(pipeline).withStoredDesignVersion(pipeline))
    if (standalone && readyForNative(pipeline)) {
        markNativeReady(item, pipeline)
    } else if (!standalone) {
        item.put('unavailableReason', '关联链路暂不可用，可继续编辑当前任务保存的画布')
    }
    var items = [item]
    var nativeCanvas = standalone ? nativeSelection(items, '可打开当前独立流程的原生画布')
        : {mode: 'NONE', reason: '请先核对任务关联后重新打开'}
    return {
        rootSourceTableId: text(task, 'source_table_id'), selectedTaskId: text(task, 'task_id'),
        selectedPipelineId: pipelineId, items: items, links: [], standalone: standalone, readOnly: false,
        relationshipUnavailable: !standalone, warnings: standalone ? [] : [reason == null ? '关联链路暂不可用' : reason],
        nativeCanvas: nativeCanvas
    }
}

var collectParents = null
collectParents = (snapshot, parent, parents, depth) => {
    if (depth > 32 || parents.size() >= 5000) {
        throw new IllegalStateException('NiFi流程组层级超过本次核验范围')
    }
    var groupId = snapshot.path('id').asText('')
    if (groupId.isBlank()) {
        return
    }
    if (parents.containsKey(groupId)) {
        throw new IllegalStateException('NiFi流程组层级存在重复标识')
    }
    parents.put(groupId, parent)
    for (child in snapshot.path('processGroupStatusSnapshots')) {
        collectParents(child.path('processGroupStatusSnapshot'), groupId, parents, depth + 1)
    }
}
var includeNativeHierarchy = graph => {
    var items = graph.items
    var groups = new LinkedHashSet()
    for (item in items) {
        if (item.containsKey('nativeUrl')) {
            groups.add(item.processGroupId)
        }
    }
    if (groups.size() < 2) {
        return
    }
    try {
        var root = nifiTaskChainRuntime.nativeHierarchy()
        if (!root.isObject() || root.path('id').asText('').isBlank()) {
            throw new IllegalStateException('NiFi流程组层级不完整')
        }
        var parents = new LinkedHashMap()
        collectParents(root, '', parents, 0)
        for (item in items) {
            if (item.containsKey('nativeUrl') && !parents.containsKey(item.processGroupId)) {
                item.remove('nativeUrl')
                item.remove('processGroupId')
                item.put('unavailableReason', '当前NiFi节点未找到该任务流程组，请核对节点配置或重新部署')
            }
        }
        graph.put('nativeCanvas', nativeSelection(items, normalNativeMessage))

        // 只有直接子组恰好等于已授权任务组，才提供共同父组链接，防止带出其它任务。
        var common = parents.get(groups.getFirst())
        if (common != null && !common.isBlank()) {
            var siblings = new LinkedHashSet()
            for (groupId in parents.keySet()) {
                if (common == parents.get(groupId)) {
                    siblings.add(groupId)
                }
            }
            if (siblings.equals(groups)) {
                graph.put('nativeCanvas', {
                    mode: 'COMMON_PARENT', url: nativeUrlPrefix + common, processGroupId: common,
                    reason: '共同父流程组展示各任务组总览；进入任务组后查看内部处理器'
                })
            }
        }
    } catch (unavailable) {
        graph.put('nativeCanvas', nativeSelection(items, '暂时无法核验NiFi共同父流程组，请选择已部署的任务入口'))
    }
}

var graph = null
try {
    graph = loadChain()
} catch (error) {
    var cause = error
    while (['MagicScriptRuntimeException', 'MagicScriptException'].contains(cause.getClass().getSimpleName()) && cause.getCause() != null) {
        cause = cause.getCause()
    }
    // 仅链路冲突允许再次读取当前画布，授权拒绝及其它错误交给原 MVC 异常处理。
    if (!(cause instanceof ResponseStatusException) || cause.getStatusCode().value() != 409) {
        throw error
    }
    graph = savedTask(cause.getReason())
}
if (nativeView) {
    includeNativeHierarchy(graph)
}
return graph
