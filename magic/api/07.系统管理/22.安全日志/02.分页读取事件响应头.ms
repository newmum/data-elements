{
  "id": "tenant_logsec_page_head_v1",
  "groupId": "sys_logsec_v1",
  "name": "02.分页读取事件响应头",
  "path": "/page",
  "method": "HEAD",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "安全日志分页读取事件。按可信行业会话和当前角色限定范围，接收分页、筛选和排序参数，复用 /common/logeventsearch。与 GET 共用鉴权及查询，返回空正文。"
}
================================
import '@/common/logeventsearch' as readEvent
import '@/common/logresponse' as respond
import request

// HEAD /sym/logsec/page。安全日志分页读取事件。按可信行业会话和当前角色限定范围，接收分页、筛选和排序参数，复用 /common/logeventsearch。与 GET 共用鉴权及查询，返回空正文。
return respond(() => readEvent('security', false, request))
