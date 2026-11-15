{
  "properties": {},
  "id": "dwm_metadata_governance_fk_preview_01",
  "script": null,
  "groupId": "e99b14c3772b40fdb8abf241878e67cf",
  "name": "01.预检查物理外键",
  "createTime": 1790635185084,
  "updateTime": 1790644824492,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/check",
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
import tenantRuntime
import metadataGovernance
// POST /dwm/foreign/check 预检查物理外键
// 只读检查目标键、字段类型和数据库能力，生成外键 DDL、撤销 SQL 与确认摘要，不修改源库结构。

var tenantId = tenantRuntime.id()
if (body?.tid == null || ('' + body.tid).trim() == '') { exit 400, '关系标识不能为空' }

// 物理数据库、驱动和索引检查由跨源适配器完成，预览结果供人工确认。
return metadataGovernance.previewForeignKey(body)
