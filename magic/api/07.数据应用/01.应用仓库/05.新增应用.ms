{
  "properties": {},
  "id": "9e49822ca4814e2bbad915ac4bd74c0f",
  "script": null,
  "groupId": "37c35deab6bc4e678c8955cc951f2ffd",
  "name": "05.新增应用",
  "createTime": 1782955345188,
  "updateTime": 1784437771872,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/add",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"appName\": \"数据资产管理平台\",\n    \"appType\": 1,\n    \"description\": \"用于管理数据资产的综合性平台\",\n    \"appIcon\": \"https://example.com/icon.png\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"tid\": \"1f079665d86b4e06b7542247498eb9f0\"\n    }\n}",
  "description": "校验请求资料后执行新增应用，返回保存或配置结果；使用请求参数 appName、appType、description、appIcon、publishStatus；涉及 app_workshop_repository 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.新增应用
// 路由：POST /das/repository/add
// 业务说明：校验请求资料后执行新增应用，返回保存或配置结果；使用请求参数 appName、appType、description、appIcon、publishStatus；涉及 app_workshop_repository 等业务表。
import log
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil

/**
 * 应用仓库新增
 * POST /api/das/repository/add
 * 
 * 新增应用仓库记录
 */

// 参数校验
var appName = body?.appName
if (appName == null || appName == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,'应用名称不能为空'
}

var appType = body?.appType
if (appType == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,'应用类型不能为空'
}

// 生成主键ID
var tid = IdUtil.getSnowflakeNextIdStr()
var currentUserId = StpUtil.getLoginId() + ""
var createdBy = currentUserId
var updatedBy = currentUserId

// 获取当前时间
var now = new Date()
var currentTime = now.format('yyyy-MM-dd HH:mm:ss')

// 插入数据
db.table('app_workshop_repository')
    .insert({
        tid: tid,
        app_name: appName,
        app_type: appType,
        description: body?.description,
        app_icon: body?.appIcon,
        publish_status: body?.publishStatus::int(0),
        is_del: 0,
        created_by: createdBy,
        created_time: currentTime,
        updated_by: updatedBy,
        updated_time: currentTime
    })

log.info('新增应用仓库成功, tid: {}, appName: {}', tid, appName)

// ========== 返回结果 ==========
return {
        tid: tid 
}
