{
  "properties": {},
  "id": "dwm_metadata_governance_fk_preview_01",
  "script": null,
  "groupId": "dwm_metadata_governance_group_01",
  "name": "06.预检查物理外键",
  "createTime": 1790635185084,
  "updateTime": 1790644824492,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/relations/foreign-key/preview",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "只读检查目标键、字段类型和数据库能力，生成外键 DDL、撤销 SQL 与确认摘要，不修改源库结构。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：预检查物理外键
// 业务说明：只读检查目标键、字段类型和数据库能力，生成外键 DDL、撤销 SQL 与确认摘要，不修改源库结构。
import tenantRuntime
import metadataGovernance

var tenantId = tenantRuntime.id()
if (body?.tid == null || ('' + body.tid).trim() == '') { exit 400, '关系标识不能为空' }

// 物理数据库、驱动和索引检查由跨源适配器完成，预览结果供人工确认。
return metadataGovernance.previewForeignKey(body)
