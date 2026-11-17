{
  "id": "tenant_logops_items_get_v1",
  "groupId": "sys_logops_v1",
  "name": "07.分页读取批次明细",
  "path": "/items",
  "method": "GET",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "操作日志分页读取批次明细。按可信行业会话和当前角色限定范围，接收父事件标识及分页参数，复用 /common/logeventobjects。"
}
================================
import '@/common/logeventobjects' as readEvent
import '@/common/logresponse' as respond
import request

// GET /sym/logops/items。操作日志分页读取批次明细。按可信行业会话和当前角色限定范围，接收父事件标识及分页参数，复用 /common/logeventobjects。
return respond(() => readEvent('operations', 'items', request, null))
