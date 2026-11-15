{
  "properties": {},
  "id": "nifi_map_retention_get_v1",
  "script": null,
  "groupId": "64f711bbc78e59fd86e03d6f0ba9a1c9",
  "name": "05.查询映射版本保留策略",
  "path": "/keep",
  "description": "读取映射版本保留策略。",
  "parameters": [],
  "options": [],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingRetention' as read

// GET /nifi/plan/keep 查询映射版本保留策略。
// GET /nifi/plan/keep 读取映射版本保留策略。
// GET 和 HEAD 共用 @/common/nifiMappingRetention 的业务读取。

return read()
