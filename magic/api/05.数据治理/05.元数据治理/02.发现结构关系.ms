{
  "properties": {},
  "id": "dwm_metadata_governance_discover_01",
  "script": null,
  "groupId": "dwm_metadata_governance_group_01",
  "name": "02.发现结构关系",
  "createTime": null,
  "updateTime": 1790614324337,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/relations/discover",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "只读取当前租户已采集的表、字段和主键/唯一键快照，按名称与类型生成候选逻辑关系，不读取业务行或写入物理外键。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：发现结构关系
// 业务说明：只读取当前租户已采集的表、字段和主键/唯一键快照，按名称与类型生成候选逻辑关系，不读取业务行或写入物理外键。
import tenantRuntime
import metadataGovernance

var tenantId = tenantRuntime.id()
// 未指定 tableIds 时检查当前租户的已采集表；结构匹配和候选去重由复用的发现器完成。
return metadataGovernance.discover(body == null ? {} : body)
