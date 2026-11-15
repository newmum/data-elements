{
  "properties": {},
  "id": "nifi_map_plan_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "58.核验映射计划版本",
  "path": "/field-mapping/plans/{id}",
  "description": "读取并核对已授权流程的固定计划版本。",
  "parameters": [],
  "options": [],
  "method": "HEAD",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingPlanDetail' as read

// HEAD /nifi/api/field-mapping/plans/{id} 读取并核对已授权流程的固定计划版本。
// GET 和 HEAD 共用 @/common/nifiMappingPlanDetail 的业务读取。

return read(path.id)
