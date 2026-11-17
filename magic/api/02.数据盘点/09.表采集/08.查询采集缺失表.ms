{
  "id": "collect_changes_v1",
  "groupId": "collection_api_group_v1",
  "name": "08.查询采集缺失表",
  "path": "/changes",
  "method": "GET",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "分页查询最近任务报告的缺失表。GET 和 HEAD 共用 /collect/changes。"
}
================================
import '@/collect/changes' as read

// GET /dst/collect/changes。共享脚本处理可信数据源范围和持久化快照。
return read()
