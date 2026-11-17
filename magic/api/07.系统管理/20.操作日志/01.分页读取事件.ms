{
  "id": "tenant_logops_page_get_v1",
  "groupId": "sys_logops_v1",
  "name": "01.分页读取事件",
  "path": "/page",
  "method": "GET",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "操作日志分页读取事件。按可信行业会话和当前角色限定范围，接收分页、筛选和排序参数，复用 /common/logeventsearch。"
}
================================
import '@/common/logeventsearch' as readEvent
import '@/common/logresponse' as respond
import request

// GET /sym/logops/page。操作日志分页读取事件。按可信行业会话和当前角色限定范围，接收分页、筛选和排序参数，复用 /common/logeventsearch。
return respond(() => readEvent('operations', false, request))
