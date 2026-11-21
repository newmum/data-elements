{
  "properties": {},
  "id": "nifi_pipe_runtime_list_get_v1",
  "script": null,
  "groupId": "139cd4c65b0f5440a2d10b3650aeacab",
  "name": "02.查询流程运行列表",
  "path": "/list",
  "description": "返回当前行业租户已保存流程与可用的运行指标。本租户的原生状态使用共享根快照，保留原数组与诊断契约。",
  "method": "GET",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiSavedCanvasRows' as loadSavedCanvases
import '@/common/nifiPipelineListResponse' as respond
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import java.lang.Character
import java.lang.Exception
import java.lang.IllegalStateException
import java.lang.Long
import java.lang.Math
import java.lang.NumberFormatException
import java.lang.String
import java.lang.StringBuilder
import java.util.HashSet
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException

// 返回当前行业租户已保存流程与可用的运行指标。
// 本租户的原生状态使用共享根快照，保留原数组与诊断契约。

var firstText = (config, keys) => {
    if (config != null) {
        for (key in keys) {
            var item = config[key]
            if (item != null) {
                var value = String.valueOf(item)
                if (!value.isBlank()) {
                    return value
                }
            }
        }
    }
    return null
}

var databaseName = config => {
    var jdbc = firstText(config, ['jdbcUrl'])
    if (jdbc != null) {
        return jdbc
    }
    var host = firstText(config, ['host'])
    var port = firstText(config, ['port'])
    var database = firstText(config, ['database', 'dbName', 'schema'])
    var address = host == null ? (port == null ? '' : port) : host + (port == null ? '' : ':' + port)
    return address + (database == null ? '' : '/' + database)
}

var savedRuntimeRow = canvas => {
    var source = null
    var sink = null
    var nodes = canvas.dsl?.nodes()
    if (nodes != null) {
        for (node in nodes) {
            if (source == null && 'source'.equalsIgnoreCase(node.category())) {
                source = node
            }
            if (sink == null && 'sink'.equalsIgnoreCase(node.category())) {
                sink = node
            }
            if (source != null && sink != null) {
                break
            }
        }
    }
    var sourceConfig = source?.config()
    var sinkConfig = sink?.config()
    var latest = canvas.lastDeployedAt?.longValue()
    var end = canvas.lastStoppedAt?.longValue()
    return {
        seq: 0,
        id: canvas.id,
        taskName: canvas.name,
        currentStatus: canvas.status.name(),
        processGroupId: canvas.processGroupId,
        latestRunTime: latest,
        endTime: end,
        executionTimeMs: latest == null || end == null ? null : Math.max(0L, end - latest),
        scheduleFrequency: firstText(sourceConfig, ['schedulingPeriod', 'schedule', 'cron']),
        syncMode: firstText(sourceConfig, ['syncMode']),
        sourceDatabase: databaseName(sourceConfig),
        sourceTables: firstText(sourceConfig, ['tables', 'tableList', 'table', 'tableName', 'sourceTable']),
        targetDatabase: databaseName(sinkConfig),
        targetTable: firstText(sinkConfig, ['table', 'targetTable', 'tableName']),
        sourceNodeName: source?.label(),
        targetNodeName: sink?.label()
    }
}

var metric = value => {
    if (value.isNumber()) {
        return value.asLong(0L)
    }
    // 沿用第一段数字的旧格式，包括全角数字，逗号分隔不拼接。
    var text = value.asText('')
    var digits = new StringBuilder()
    var i = 0
    while (i < text.length()) {
        var ch = text.charAt(i)
        if (Character.isDigit(ch)) {
            digits.append(ch)
        } else if (digits.length() > 0) {
            break
        }
        i = i + 1
    }
    if (digits.length() == 0) {
        return 0L
    }
    try {
        return Long.parseLong(digits.toString())
    } catch (failure) {
        var cause = failure
        while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
            && cause.getCause() != null) {
            cause = cause.getCause()
        }
        if (cause instanceof NumberFormatException) {
            return 0L
        }
        throw failure
    }
}

return respond(() => {
    var rows = loadSavedCanvases(savedRuntimeRow)
    var groups = new HashSet()
    for (row in rows) {
        if (row.processGroupId != null && !row.processGroupId.isBlank()) {
            groups.add(row.processGroupId)
        }
    }
    var snapshots = {}
    var statusError = null
    if (!groups.isEmpty()) {
        try {
            // 对整页流程共用根组快照。栈逆序入队保留原有深度优先遍历和重复组覆盖顺序。
            var root = nifi.getProcessGroupStatus(nifi.getRootProcessGroupId())
                .path('processGroupStatus').path('aggregateSnapshot')
            if (!root.isObject()) {
                throw new IllegalStateException('NiFi 根流程组状态响应缺少 aggregateSnapshot')
            }
            var pending = {node: root, next: null}
            while (pending != null) {
                var item = pending.node
                pending = pending.next
                var id = item.path('id').asText(null)
                if (id != null && groups.contains(id)) {
                    snapshots[id] = item
                }
                var children = item.path('processGroupStatusSnapshots')
                var elements = []
                // Magic 的 for-in 通过 JsonNode 的公共 Iterable 协议访问子组。
                for (entry in children) {
                    var child = entry.path('processGroupStatusSnapshot')
                    if (child.isObject()) {
                        elements.add(child)
                    }
                }
                var index = elements.size() - 1
                while (index >= 0) {
                    pending = {node: elements[index], next: pending}
                    index = index - 1
                }
            }
        } catch (failure) {
            var cause = failure
            while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
                && cause.getCause() != null) {
                cause = cause.getCause()
            }
            if (!(cause instanceof Exception)) {
                throw failure
            }
            var message = cause.getMessage()
            statusError = message == null ? 'NiFi 根流程组状态获取失败' : message
        }
    }
    var sequence = 0
    for (row in rows) {
        sequence = sequence + 1
        row.seq = sequence
        if (row.processGroupId == null || row.processGroupId.isBlank()) {
            continue
        }
        var snapshot = snapshots[row.processGroupId]
        if (statusError != null) {
            row.nifiStatusError = statusError
        } else if (snapshot == null || !snapshot.has('queuedCount') || !snapshot.has('activeThreadCount')) {
            row.nifiStatusError = 'NiFi 流程组不存在或状态指标不完整'
        } else {
            var active = metric(snapshot.path('activeThreadCount'))
            row.activeThreads = active
            row.queuedCount = metric(snapshot.path('queuedCount'))
            row.queued = snapshot.path('queued').asText(null)
            row.inputCount = Math.max(0L, Math.max(metric(snapshot.path('flowFilesIn')), metric(snapshot.path('flowFilesReceived'))))
            row.outputCount = Math.max(Math.max(0L, metric(snapshot.path('flowFilesOut'))),
                Math.max(metric(snapshot.path('flowFilesSent')), metric(snapshot.path('flowFilesTransferred'))))
            row.bytesRead = snapshot.path('bytesRead').asText(null)
            row.bytesWritten = snapshot.path('bytesWritten').asText(null)
            row.bytesSent = snapshot.path('bytesSent').asText(null)
            row.running = active > 0 || row.currentStatus == 'RUNNING'
        }
    }
    return rows
})
