{
  "id": "collect_status_head_v1",
  "groupId": "collection_api_group_v1",
  "name": "02.查询数据源采集状态响应头",
  "path": "/status",
  "method": "HEAD",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "查询当前行业角色可见数据源的最近或指定采集任务。同址 GET 和 HEAD 共用 /collect/status。"
}
================================
import '@/collect/status' as read

// HEAD /dst/collect/status。同址读取共用 /collect/status，不连接物理数据源。
return read()
