{
  "id": "nifi_runtime_events_v1",
  "groupId": "139cd4c65b0f5440a2d10b3650aeacab",
  "name": "05.订阅实时状态事件",
  "path": "/events",
  "method": "GET",
  "description": "订阅当前行业租户的运行状态版本和采样健康信息。HEAD 返回事件流响应头。",
  "parameters": [],
  "options": [
    {"name": "access", "value": "TENANT_SESSION"},
    {"name": "head", "value": "true"}
  ]
}
================================
import request
import response
import taskRuntime
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

// 事件仅通知当前行业租户页面重新读取授权数据。HEAD 不创建订阅。
if (request.get().getMethod() == 'HEAD') {
    return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
        .header('Cache-Control', 'no-cache, no-transform')
        .header('X-Accel-Buffering', 'no').build()
}
// 页面传入已授权任务绑定的节点范围；模块只读取当前租户节点，失效绑定仍保留待确认。
var nodeValues = request.getValues('nodeIds')
if (nodeValues != null && nodeValues.size()>1) { exit 400, '节点范围只能提供一个JSON数组' }
var nodeIds = nodeValues == null || nodeValues.isEmpty() ? null : nodeValues[0]
var stream = nodeIds == null ? taskRuntime.eventStream() : taskRuntime.eventStreamForNodes(nodeIds)
response.setHeader('Cache-Control', 'no-cache, no-transform')
response.setHeader('X-Accel-Buffering', 'no')
return stream
