{
  "id": "collect_additions_head_v1",
  "groupId": "collection_api_group_v1",
  "name": "07.查询最近新增采集表响应头",
  "path": "/additions",
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
  "description": "分页查询最近一次成功采集新增的表和视图。同址 GET 和 HEAD 共用 /collect/additions。"
}
================================
import '@/collect/additions' as read

// HEAD /dst/collect/additions。同址读取共用 /collect/additions，不连接物理数据源。
return read()
