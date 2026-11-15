{
  "properties": {},
  "id": "ec36858da4464ae3a44f14db2cc44f23",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "47.NiFi删除原生组响应",
  "path": "/nifiDeleteNativeGroups",
  "description": "共用原生组删除的参数绑定、当前租户归属检查、原生节点绑定和锁。",
  "parameters": [
    {
      "name": "parameterName",
      "description": "原始 HTTP 参数名",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "deleteAuthorizedGroups",
      "description": "入口在全树核验后执行清理并返回 HTTP 响应",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiDeletionGroupScope' as groupScope
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import java.lang.StringBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import request
import tableRead
import tenantRuntime

// @/common/nifiDeleteNativeGroups 为单组删除和父组删除共用参数绑定、租户校验、锁和 NiFi 节点绑定。
// 先核验完整子树，只有全部通过后才在原生节点和锁内执行入口传入的删除操作。
var values = request.getValues(parameterName)
if (values == null) {
    values = request.getValues(parameterName + '[]')
}
if (values == null) {
    return ResponseEntity.ok(CommonResponse.fail(StatusCodeEnum.PARAMS_NOT_FOUND.getCode(),
        '缺少必要参数: ' + parameterName, '参数缺失'))
}
var raw = new StringBuilder()
var separator = ''
for (value in values) {
    raw.append(separator)
    raw.append(value)
    separator = ','
}
var id = raw.toString().trim()
if (id.isBlank()) {
    return ResponseEntity.badRequest().body({deleted: false, error: parameterName + ' is required'})
}
var outcome = {deleted: false}
outcome[parameterName] = id

try {
    var tenantId = tenantRuntime.id()
    return mutation.withMutationLock(() => {
        if (!id.matches('[A-Za-z0-9_-]{1,128}') || 'root'.equalsIgnoreCase(id)) {
            outcome.error = '流程组不存在或不属于当前租户'
            return ResponseEntity.status(404).body(outcome)
        }
        // 无本租户标记的旧组只可使用当前租户的活动流程或接入任务绑定。
        var linked = (table, groupColumn, key) => {
            var query = tableRead.from(table, 'x', tenantId)
            query.where(tableRead.equal(tableRead.column('x', 'is_del', 'NUMBER'), 0))
            query.where(tableRead.equal(tableRead.column('x', groupColumn, 'TEXT'), id))
            return !query.rows({id: tableRead.column('x', key, 'TEXT')}, 1).isEmpty()
        }
        var stored = linked('nifi_pipeline_t', 'nifi_process_group_id', 'id')
            || linked('data_access_agg_task_t', 'process_group_id', 'tid')
        var runtime = nifi.bindRuntimeNode()
        try {
            var groups = groupScope([{id: id, linked: stored}], nifi.getRootProcessGroupId(), null)
            return deleteAuthorizedGroups(groups, id)
        } finally {
            if (runtime != null) {
                runtime.close()
            }
        }
    })
} catch (failure) {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
        cause = cause.getCause()
    }
    var status = 500
    outcome.error = cause.getMessage()
    if (cause instanceof ResponseStatusException) {
        status = cause.getStatusCode().value()
        outcome.error = cause.getReason()
    } else if (cause instanceof NifiException) {
        status = cause.status() > 0 ? cause.status() : 502
        outcome.status = status
        outcome.body = cause.body()
    }
    return ResponseEntity.status(status).body(outcome)
}
