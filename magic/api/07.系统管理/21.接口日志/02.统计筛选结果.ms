{
  "id": "tenant_logreq_stats_get_v1",
  "groupId": "sys_logreq_v1",
  "name": "02.统计筛选结果",
  "path": "/stats",
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
  "description": "接口日志统计筛选结果。按可信行业会话和当前角色限定范围，接收日期及筛选参数并返回聚合结果，复用 /common/logeventsearch。"
}
================================
import '@/common/logeventsearch' as readEvent
import '@/common/logresponse' as respond
import request

// GET /sym/logreq/stats。接口日志统计筛选结果。按可信行业会话和当前角色限定范围，接收日期及筛选参数并返回聚合结果，复用 /common/logeventsearch。
return respond(() => readEvent('requests', true, request))
