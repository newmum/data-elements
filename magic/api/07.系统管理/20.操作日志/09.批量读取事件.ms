{
  "id": "tenant_logops_batch_post_v1",
  "groupId": "sys_logops_v1",
  "name": "09.批量读取事件",
  "path": "/batch",
  "method": "POST",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "操作日志批量读取事件。按可信行业会话和当前角色限定范围，接收最多 100 个不同事件标识的 JSON 数组，复用 /common/logeventobjects。"
}
================================
import '@/common/logeventobjects' as readEvent
import '@/common/logresponse' as respond
import request

// POST /sym/logops/batch。操作日志批量读取事件。按可信行业会话和当前角色限定范围，接收最多 100 个不同事件标识的 JSON 数组，复用 /common/logeventobjects。
return respond(() => readEvent('operations', 'batch', request, body))
