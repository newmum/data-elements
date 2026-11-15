{
  "properties": {},
  "id": "nifi_map_retention_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "63.查询映射版本保留策略",
  "path": "/field-mapping/retention-policy",
  "description": "读取映射版本保留策略。",
  "parameters": [],
  "options": [],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingRetention' as read

// GET /nifi/api/field-mapping/retention-policy 读取映射版本保留策略。
// GET 和 HEAD 共用 @/common/nifiMappingRetention 的业务读取。

return read()
