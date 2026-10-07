{
  "properties": {},
  "id": "430c40a44f8b49a58feb7194882b181e",
  "script": null,
  "groupId": "5e2962ddb12b4ed98ccdd9a389640db6",
  "name": "03.新增图标",
  "createTime": 1782955345260,
  "updateTime": 1784437771878,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/add",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"appIcon\": \"https://example.com/icon.png\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"tid\": \"1e489cb251fd4c768fe2777cb9ce3458\"\n    }\n}",
  "description": "校验请求资料后执行新增图标，返回保存或配置结果；使用请求参数 appIcon；涉及 app_workshop_repository_self_icon 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.新增图标
// 路由：POST /das/repository/icon/add
// 业务说明：校验请求资料后执行新增图标，返回保存或配置结果；使用请求参数 appIcon；涉及 app_workshop_repository_self_icon 等业务表。
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
var appIcon = body?.appIcon
if (appIcon == null || appIcon == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,'appIcon不能为空'
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
db.table('app_workshop_repository_self_icon')
    .insert({
        tid: tid,
        app_icon: body?.appIcon,
        is_del: 0,
        created_by: createdBy,
        created_time: currentTime,
        updated_by: updatedBy,
        updated_time: currentTime
    })

log.info('新增应用图标成功, tid: {}, appIcon: {}', tid, appIcon)

// ========== 返回结果 ==========
return {
        tid: tid 
}
