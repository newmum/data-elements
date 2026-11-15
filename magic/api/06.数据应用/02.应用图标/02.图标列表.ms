{
  "properties": {},
  "id": "3f0547c49dde4effa23196872929ac9c",
  "script": null,
  "groupId": "ad2ad753a7445685b0df139590804d88",
  "name": "02.图标列表",
  "createTime": 1782955345251,
  "updateTime": 1784437771877,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": [\n        {\n            \"tid\": \"1e489cb251fd4c768fe2777cb9ce3458\",\n            \"tenantId\": null,\n            \"revision\": null,\n            \"createdBy\": \"1995681734511165441\",\n            \"createdTime\": \"2026-04-20 11:01:25\",\n            \"isDel\": 0,\n            \"updatedBy\": \"1995681734511165441\",\n            \"updatedTime\": \"2026-04-20 11:01:25\",\n            \"orgId\": null,\n            \"appIcon\": \"https://example.com/icon.png\"\n        }\n    ]\n}",
  "description": "读取应用工作坊自定义图标清单，返回页面可选择的图标资料。按当前认证行业租户处理。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.IdUtil
import log
import tenantRuntime

// GET /das/icon/list 图标列表。读取应用工作坊自定义图标清单，返回页面可选择的图标资料。
var tenantId = tenantRuntime.id()

var now = new Date()
var currentTime = now.format('yyyy-MM-dd HH:mm:ss')
var tid = IdUtil.getSnowflakeNextIdStr()
var currentUserId = StpUtil.getLoginId() + ""


// 检查记录是否存在
var data = db.table('app_workshop_repository_self_icon')
    .where().eq('tenant_id', tenantId)
    .eq('created_by', currentUserId)
    .eq('is_del', 0)
    .select()

// 返回当前业务分支的标准结果。
return data;
