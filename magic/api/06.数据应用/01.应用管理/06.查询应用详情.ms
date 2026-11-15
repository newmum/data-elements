{
  "properties": {},
  "id": "237d7a94845b43baadfdca2351155bae",
  "script": null,
  "groupId": "37c35deab6bc4e678c8955cc951f2ffd",
  "name": "06.查询应用详情",
  "createTime": 1782955345197,
  "updateTime": 1784437771873,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"tid\": \"1\",\n        \"tenantId\": \"1\",\n        \"revision\": \"1\",\n        \"createdBy\": \"2\",\n        \"createdTime\": \"2026-04-22 11:16:17\",\n        \"isDel\": 0,\n        \"updatedBy\": null,\n        \"updatedTime\": null,\n        \"appName\": null,\n        \"appType\": null,\n        \"appIcon\": null,\n        \"description\": null,\n        \"publishStatus\": null,\n        \"publishUrl\": null,\n        \"publishId\": null\n    }\n}",
  "description": "根据ID查询应用仓库详细信息；使用请求参数 tid；涉及 app_workshop_repository 等业务表。按当前认证行业租户处理。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import log
import tenantRuntime

// POST /das/app/detail 查询应用详情。根据ID查询应用仓库详细信息；使用请求参数 tid；涉及 app_workshop_repository 等业务表。
var tenantId = tenantRuntime.id()

// 获取应用ID
var tid = body?.tid

if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,'应用ID不能为空'
}

// 查询应用详情
var appInfo = db.table('app_workshop_repository')
    .where().eq('tenant_id', tenantId)
    .eq('tid', tid)
    .eq('is_del', 0)
    .selectOne()

if (appInfo == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,'应用不存在'
}

log.info('查询应用仓库详情成功, tid: {}', tid)

// ========== 返回结果 ==========
return appInfo
