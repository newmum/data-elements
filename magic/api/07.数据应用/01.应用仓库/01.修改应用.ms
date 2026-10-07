{
  "properties": {},
  "id": "1a6a20bdcc4d485e978491431a4956d6",
  "script": null,
  "groupId": "37c35deab6bc4e678c8955cc951f2ffd",
  "name": "01.修改应用",
  "createTime": 1782955345153,
  "updateTime": 1784437771869,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/update",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"1f079665d86b4e06b7542247498eb9f0\",\n    \"appName\": \"数据资产管理平台999\",\n    \"appType\": 1,\n    \"description\": \"用于管理数据资产的综合性平台\",\n    \"appIcon\": \"https://example.com/icon.png\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"1f079665d86b4e06b7542247498eb9f0\"\n}",
  "description": "校验请求资料后执行修改应用，返回保存或配置结果；使用请求参数 tid、appName、appType、description、appIcon、publishStatus；涉及 app_workshop_repository 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.修改应用
// 路由：POST /das/repository/update
// 业务说明：校验请求资料后执行修改应用，返回保存或配置结果；使用请求参数 tid、appName、appType、description、appIcon、publishStatus；涉及 app_workshop_repository 等业务表。
import log
import cn.dev33.satoken.stp.StpUtil;

/**
 * 应用仓库修改
 * POST /api/das/repository/update
 * 
 * 修改应用仓库记录
 */

// 参数校验
var tid = body?.tid
if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,'应用ID不能为空'
}

// 检查记录是否存在
var exists = db.table('app_workshop_repository')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .count()

if (exists == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,'应用不存在'
}

// 获取当前时间
var now = new Date()
var currentTime = now.format('yyyy-MM-dd HH:mm:ss')

var currentUserId = StpUtil.getLoginId() + ""

// 构建更新数据
var updateData = {
    updated_by: currentUserId,
    updated_time: currentTime
}

if (body?.appName != null && body?.appName != '') {
    updateData.app_name = body?.appName
}

if (body?.appType != null) {
    updateData.app_type = body?.appType
}

if (body?.description != null) {
    updateData.description = body?.description
}

if (body?.appIcon != null) {
    updateData.app_icon = body?.appIcon
}

if (body?.publishStatus != null) {
    updateData.publish_status = body?.publishStatus::int
}


// 更新数据
db.table('app_workshop_repository')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .update(updateData)

log.info('修改应用仓库成功, tid: {}', tid)

// ========== 返回结果 ==========
return db.table('app_workshop_repository')
    .where()
    .eq('tid', tid)
    .selectOne()
