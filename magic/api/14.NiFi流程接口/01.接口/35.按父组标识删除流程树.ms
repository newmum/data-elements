{
  "properties": {},
  "id": "5d340458be2a074424507a72e8f13c6e",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "35.按父组标识删除流程树",
  "path": "/pipelines/deleteTreeByParentPgId",
  "description": "校验当前租户对指定子树的完整归属，按子组在前的顺序清理。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiDeleteNativeGroups' as deleteGroup

// POST /nifi/api/pipelines/deleteTreeByParentPgId 按 parentPgId 删除本租户原生流程子树。
// @/common/nifiDeleteNativeGroups 先核验整棵树，然后按子组在前的顺序清理并返回结果。
return deleteGroup('parentPgId', (groups, parentPgId) => null)
