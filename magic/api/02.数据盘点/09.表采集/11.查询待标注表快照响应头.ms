{
  "id": "collect_snapshot_head_v1",
  "groupId": "collection_api_group_v1",
  "name": "11.查询待标注表快照响应头",
  "path": "/snapshot",
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
  "description": "分页查询来源的未标注快照。GET 和 HEAD 共用 /collect/snapshot。"
}
================================
import '@/collect/snapshot' as read

// HEAD /dst/collect/snapshot。共享脚本处理可信数据源范围和持久化快照。
return read()
