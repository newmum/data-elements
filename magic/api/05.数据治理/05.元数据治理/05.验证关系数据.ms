{
  "properties": {},
  "id": "dwm_metadata_governance_validate_01",
  "script": null,
  "groupId": "dwm_metadata_governance_group_01",
  "name": "05.验证关系数据",
  "createTime": 1790635184974,
  "updateTime": 1790644822906,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/relations/validate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据当前租户关系定义执行受限的跨源键验证，将汇总证据按版本保存到关系台账；不返回源端业务行。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：验证逻辑关系的数据
// 业务说明：根据当前租户关系定义执行受限的跨源键验证，将汇总证据按版本保存到关系台账；不返回源端业务行。
import tenantRuntime
import metadataGovernance

var tenantId = tenantRuntime.id()
if (body?.tid == null || ('' + body.tid).trim() == '') { exit 400, '关系标识不能为空' }

// JDBC 验证器处理不同数据库驱动和采样上限；保存证据时强制版本检查。
return metadataGovernance.validateRelation(body)
