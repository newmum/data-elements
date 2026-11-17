{
  "id": "collect_saved_v1",
  "groupId": "collection_api_group_v1",
  "name": "04.查询已保存采集表",
  "path": "/saved",
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
  "description": "按数据源与角色范围分页查询已登记的表和视图。同址 GET 和 HEAD 共用 /collect/saved。"
}
================================
import '@/collect/saved' as read

// GET /dst/collect/saved。同址读取共用 /collect/saved，不连接物理数据源。
return read()
