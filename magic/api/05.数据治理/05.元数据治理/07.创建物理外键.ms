{
  "properties": {},
  "id": "dwm_metadata_governance_fk_create_01",
  "script": null,
  "groupId": "dwm_metadata_governance_group_01",
  "name": "07.创建物理外键",
  "createTime": 1790635185171,
  "updateTime": 1790644826249,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/relations/foreign-key/create",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "仅在明确确认且预览摘要匹配时，对源数据库执行已预览的外键 DDL，并按版本记录结果；不自动创建目标索引。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：创建物理外键
// 业务说明：仅在明确确认且预览摘要匹配时，对源数据库执行已预览的外键 DDL，并按版本记录结果；不自动创建目标索引。
import tenantRuntime
import metadataGovernance

var tenantId = tenantRuntime.id()
if (body?.tid == null || ('' + body.tid).trim() == '') { exit 400, '关系标识不能为空' }
if (body?.confirmPhysicalChange != true) { exit 400, '必须明确确认源数据库结构变更' }
if (body?.previewHash == null || ('' + body.previewHash).trim() == '') {
    exit 400, '请先预览物理外键'
}

// DDL 不能像普通台账事务一样安全回滚；执行器强制再核对预览哈希并记录结果。
return metadataGovernance.createForeignKey(body)
