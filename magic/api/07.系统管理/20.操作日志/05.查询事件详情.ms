{
  "id": "tenant_logops_detail_get_v1",
  "groupId": "sys_logops_v1",
  "name": "05.查询事件详情",
  "path": "/detail",
  "method": "GET",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "操作日志查询事件详情。按可信行业会话和当前角色限定范围，接收事件标识，复用 /common/logeventobjects。"
}
================================
import '@/common/logeventobjects' as readEvent
import '@/common/logresponse' as respond
import request

// GET /sym/logops/detail。操作日志查询事件详情。按可信行业会话和当前角色限定范围，接收事件标识，复用 /common/logeventobjects。
return respond(() => readEvent('operations', 'detail', request, null))
