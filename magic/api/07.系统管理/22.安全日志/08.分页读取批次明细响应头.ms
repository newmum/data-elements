{
  "id": "tenant_logsec_items_head_v1",
  "groupId": "sys_logsec_v1",
  "name": "08.分页读取批次明细响应头",
  "path": "/items",
  "method": "HEAD",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "安全日志分页读取批次明细。按可信行业会话和当前角色限定范围，接收父事件标识及分页参数，复用 /common/logeventobjects。与 GET 共用鉴权及查询，返回空正文。"
}
================================
import '@/common/logeventobjects' as readEvent
import '@/common/logresponse' as respond
import request

// HEAD /sym/logsec/items。安全日志分页读取批次明细。按可信行业会话和当前角色限定范围，接收父事件标识及分页参数，复用 /common/logeventobjects。与 GET 共用鉴权及查询，返回空正文。
return respond(() => readEvent('security', 'items', request, null))
