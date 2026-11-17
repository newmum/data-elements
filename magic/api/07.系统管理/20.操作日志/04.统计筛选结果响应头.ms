{
  "id": "tenant_logops_stats_head_v1",
  "groupId": "sys_logops_v1",
  "name": "04.统计筛选结果响应头",
  "path": "/stats",
  "method": "HEAD",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "操作日志统计筛选结果。按可信行业会话和当前角色限定范围，接收日期及筛选参数并返回聚合结果，复用 /common/logeventsearch。与 GET 共用鉴权及查询，返回空正文。"
}
================================
import '@/common/logeventsearch' as readEvent
import '@/common/logresponse' as respond
import request

// HEAD /sym/logops/stats。操作日志统计筛选结果。按可信行业会话和当前角色限定范围，接收日期及筛选参数并返回聚合结果，复用 /common/logeventsearch。与 GET 共用鉴权及查询，返回空正文。
return respond(() => readEvent('operations', true, request))
