{
  "properties": {},
  "id": "7620748a6f17464fab1e392b278c6365",
  "script": null,
  "groupId": "37c35deab6bc4e678c8955cc951f2ffd",
  "name": "04.应用发布",
  "createTime": 1782955345178,
  "updateTime": 1784437771872,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/publish",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"xxxx\",\n    \"publishStatus\": \"0\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": null\n}",
  "description": "按请求目标与业务状态执行应用发布，返回本次操作结果；使用请求参数 tid、publishStatus；涉及 app_workshop_repository 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.应用发布
// 路由：POST /das/repository/publish
// 业务说明：按请求目标与业务状态执行应用发布，返回本次操作结果；使用请求参数 tid、publishStatus；涉及 app_workshop_repository 等业务表；执行数据库写入并返回处理结果。
import log
import cn.dev33.satoken.stp.StpUtil;

// 解析请求体并为可选参数设置默认值。
var tid = body?.tid
var publishStatus  = body?.publishStatus

// 生成主键ID
var currentUserId = StpUtil.getLoginId() + ""
var createdBy = currentUserId
var updatedBy = currentUserId

// 获取当前时间
var now = new Date()
var currentTime = now.format('yyyy-MM-dd HH:mm:ss')

// 构建更新数据
var updateData = {
    updated_by: currentUserId,
    updated_time: currentTime
}

if (body?.publishStatus != null && body?.publishStatus != '') {
    updateData.publish_status = body?.publishStatus
}


// 从 MySQL 业务表读取当前有效数据。
db.table("app_workshop_repository").where()
.eq("tid", tid).update(updateData)

// 返回当前业务分支的标准结果。
return "success"
