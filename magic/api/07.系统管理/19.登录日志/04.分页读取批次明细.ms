{
  "id": "tenant_logauth_items_get_v1",
  "groupId": "sys_logauth_v1",
  "name": "04.分页读取批次明细",
  "path": "/items",
  "method": "GET",
  "parameters": [],
  "paths": [],
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
  "description": "登录日志分页读取批次明细。按可信行业会话和当前角色限定范围，接收父事件标识及分页参数，复用 /common/logeventobjects。"
}
================================
import '@/common/logeventobjects' as readEvent
import '@/common/logresponse' as respond
import request

// GET /sym/logauth/items。登录日志分页读取批次明细。按可信行业会话和当前角色限定范围，接收父事件标识及分页参数，复用 /common/logeventobjects。
return respond(() => readEvent('auth', 'items', request, null))
