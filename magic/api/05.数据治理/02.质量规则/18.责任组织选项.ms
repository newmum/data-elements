{
  "properties": {},
  "id": "a1000000000000000000000000000017",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "18.责任组织选项",
  "createTime": 1790495138845,
  "updateTime": 1790498525706,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/options/organizations",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取当前租户未删除的组织机构，按顺序返回质量整改责任组织选择项。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：18.责任组织选项
// 路由：POST /dwm/quality/options/organizations
// 业务说明：读取当前租户未删除的组织机构，按顺序返回质量整改责任组织选择项。
import tenantRuntime
// Responsibility selector; never accept a browser-provided tenant.
var tenantId=tenantRuntime.id()
return db.select('SELECT id AS tid,name,parent_id FROM rm_org_t WHERE tenant_id=#{tenantId} AND (deleted IS NULL OR deleted=0) AND id<>\'ROOT\' ORDER BY serial_number, name')
