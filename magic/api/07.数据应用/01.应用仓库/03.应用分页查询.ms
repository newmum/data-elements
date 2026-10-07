{
  "properties": {},
  "id": "6d310836db5f463a8942fa81958c35ec",
  "script": null,
  "groupId": "37c35deab6bc4e678c8955cc951f2ffd",
  "name": "03.应用分页查询",
  "createTime": 1782955345170,
  "updateTime": 1784437771871,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": \"1\",\n    \"pageSize\": \"20\",\n    \"appName\": \"测试业务系统\",\n    \"publishStatus\": 2\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"total\": 1,\n        \"list\": [\n            {\n                \"tid\": \"1\",\n                \"tenantId\": \"1\",\n                \"revision\": \"1\",\n                \"createdBy\": \"2\",\n                \"createdTime\": \"2026-04-22 11:16:17\",\n                \"isDel\": 0,\n                \"updatedBy\": null,\n                \"updatedTime\": null,\n                \"appName\": null,\n                \"appType\": null,\n                \"appIcon\": null,\n                \"description\": null,\n                \"publishStatus\": null,\n                \"publishUrl\": null,\n                \"publishId\": null\n            }\n        ]\n    }\n}",
  "description": "按应用名称、类型、发布状态及分页参数查询应用仓库，返回应用列表和总数。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.应用分页查询
// 路由：POST /das/repository/page
// 业务说明：按应用名称、类型、发布状态及分页参数查询应用仓库，返回应用列表和总数。
// 核心业务逻辑：按接口约定编排当前请求并返回业务处理结果。
import log

/**
 * 应用仓库分页查询
 * POST /api/das/repository/page
 * 
 * 支持分页查询应用仓库列表
 */

// 获取分页参数
var pageNum = body?.pageNum::long(0)
var pageSize = body?.pageSize::long(20)
var offsetLimit = pageNum == 0? 0 : (pageNum - 1)* pageSize

// 获取查询条件
var appName = body?.appName
var appType = body?.appType
var publishStatus = body?.publishStatus

// 构建查询条件
var query = db.table('app_workshop_repository')
    .where()
    .eq('is_del', 0)

if (appName != null && appName != '') {
    query = query.like('app_name', '%' + appName + '%')
}

// if (appType != null) {
//     query = query.eq('app_type', appType)
// }

if (publishStatus != null) {
    query = query.eq('publish_status', publishStatus)
}

// ========== 查询总数 ==========
var total = query.count()

// ========== 查询列表 ==========
var list = query
    .orderBy('created_time', 'desc')
    .page(pageSize, offsetLimit)

log.info('分页查询应用仓库成功, pageNum: {}, pageSize: {}, total: {}', pageNum, pageSize, total)

// ========== 返回结果 ==========
return list
