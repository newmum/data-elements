{
  "properties": {},
  "id": "nifi_map_plans_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "55.查询映射计划历史",
  "path": "/field-mapping/plans",
  "description": "分页读取当前租户授权流程的版本历史。",
  "parameters": [],
  "options": [],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingPlans' as read

// GET /nifi/api/field-mapping/plans 分页读取当前租户授权流程的版本历史。
// GET 和 HEAD 共用 @/common/nifiMappingPlans 的业务读取。

return read()
