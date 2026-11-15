{
  "properties": {},
  "id": "nifi_map_retention_head_v1",
  "script": null,
  "groupId": "64f711bbc78e59fd86e03d6f0ba9a1c9",
  "name": "06.核验映射版本保留策略",
  "path": "/keep",
  "description": "读取映射版本保留策略。",
  "parameters": [],
  "options": [],
  "method": "HEAD",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingRetention' as read

// HEAD /nifi/plan/keep 核验映射版本保留策略。
// HEAD /nifi/plan/keep 读取映射版本保留策略。
// GET 和 HEAD 共用 @/common/nifiMappingRetention 的业务读取。

return read()
