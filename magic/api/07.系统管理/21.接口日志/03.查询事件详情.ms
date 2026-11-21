{
  "id": "tenant_logreq_detail_get_v1",
  "groupId": "sys_logreq_v1",
  "name": "03.查询事件详情",
  "path": "/detail",
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
  "description": "接口日志查询事件详情。按可信行业会话和当前角色限定范围，接收事件标识，复用 /common/logeventobjects。"
}
================================
import '@/common/logeventobjects' as readEvent
import '@/common/logresponse' as respond
import request

// GET /sym/logreq/detail。接口日志查询事件详情。按可信行业会话和当前角色限定范围，接收事件标识，复用 /common/logeventobjects。
return respond(() => readEvent('requests', 'detail', request, null))
