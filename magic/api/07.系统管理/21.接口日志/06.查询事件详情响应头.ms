{
  "id": "tenant_logreq_detail_head_v1",
  "groupId": "sys_logreq_v1",
  "name": "06.查询事件详情响应头",
  "path": "/detail",
  "method": "HEAD",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "接口日志查询事件详情。按可信行业会话和当前角色限定范围，接收事件标识，复用 /common/logeventobjects。与 GET 共用鉴权及查询，返回空正文。"
}
================================
import '@/common/logeventobjects' as readEvent
import '@/common/logresponse' as respond
import request

// HEAD /sym/logreq/detail。接口日志查询事件详情。按可信行业会话和当前角色限定范围，接收事件标识，复用 /common/logeventobjects。与 GET 共用鉴权及查询，返回空正文。
return respond(() => readEvent('requests', 'detail', request, null))
