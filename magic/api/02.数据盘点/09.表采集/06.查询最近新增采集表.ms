{
  "id": "collect_additions_v1",
  "groupId": "collection_api_group_v1",
  "name": "06.查询最近新增采集表",
  "path": "/additions",
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
  "description": "分页查询最近一次成功采集新增的表和视图。同址 GET 和 HEAD 共用 /collect/additions。"
}
================================
import '@/collect/additions' as read

// GET /dst/collect/additions。同址读取共用 /collect/additions，不连接物理数据源。
return read()
