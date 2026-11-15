{
  "properties": {},
  "id": "nifi_map_plans_head_v1",
  "script": null,
  "groupId": "64f711bbc78e59fd86e03d6f0ba9a1c9",
  "name": "02.核验映射计划历史",
  "path": "/list",
  "description": "分页读取当前租户授权流程的版本历史。",
  "parameters": [],
  "options": [],
  "method": "HEAD",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingPlans' as read

// HEAD /nifi/plan/list 核验映射计划历史。
// HEAD /nifi/plan/list 分页读取当前租户授权流程的版本历史。
// GET 和 HEAD 共用 @/common/nifiMappingPlans 的业务读取。

return read()
