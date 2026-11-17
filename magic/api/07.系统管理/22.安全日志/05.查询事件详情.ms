{
  "id": "tenant_logsec_detail_get_v1",
  "groupId": "sys_logsec_v1",
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
  "description": "安全日志查询事件详情。按可信行业会话和当前角色限定范围，接收事件标识，复用 /common/logeventobjects。"
}
================================
import '@/common/logeventobjects' as readEvent
import '@/common/logresponse' as respond
import request

// GET /sym/logsec/detail。安全日志查询事件详情。按可信行业会话和当前角色限定范围，接收事件标识，复用 /common/logeventobjects。
return respond(() => readEvent('security', 'detail', request, null))
