{
  "properties": {},
  "id": "b910193ab56143129466e2c69613286d",
  "script": null,
  "groupId": "7c271b88983b467282fd9d498d0f8d66",
  "name": "04.更新大屏",
  "createTime": 1782955345232,
  "updateTime": 1784437771876,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/update",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"1\",\n    \"name\": \"123\",\n    \"nameCn\": \"2323423\",\n    \"sourceCode\": \"json\",\n    \"thumbnail\": \"缩略图\",\n    \"parentId\": \"1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"1\"\n}",
  "description": "校验请求资料后执行更新大屏，返回保存或配置结果；使用请求参数 tid、name、nameCn、sourceCode、thumbnail、parentId；涉及 app_workshop_big_screen 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.更新大屏
// 路由：POST /das/repository/bigScreen/update
// 业务说明：校验请求资料后执行更新大屏，返回保存或配置结果；使用请求参数 tid、name、nameCn、sourceCode、thumbnail、parentId；涉及 app_workshop_big_screen 等业务表。
import cn.hutool.json.JSONUtil
import log
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
import IdGenerator
import jsons

// 解析请求体并为可选参数设置默认值。
var tid = body?.tid
if(tid == null || tid == ""){
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "tid不能为空";
}
var name = body?.name
var nameCn = body?.nameCn
var sourceCode = body?.sourceCode
var thumbnail = body?.thumbnail
var parentId = body?.parentId

var currentUserId = StpUtil.getLoginId() + ""

var now = new Date()

// 构建更新数据
var updateData = {
    tid: oldTid,
    updated_by: currentUserId,
    updated_time: now
}

if (body?.name != null && body?.name != '') {
    updateData.name = body?.name
}

if (body?.nameCn != null && body?.nameCn != '') {
    updateData.nameCn = body?.nameCn
}

if (body?.sourceCode != null && body?.sourceCode != '') {
    updateData.sourceCode = body?.sourceCode
}

if (body?.thumbnail != null && body?.thumbnail != '') {
    updateData.thumbnail = body?.thumbnail
}

if (body?.parentId != null && body?.parentId != '') {
    updateData.parentId = body?.parentId
}


//var tid =  IdUtil.getSnowflakeNextIdStr()
db.table("app_workshop_big_screen").where().eq("tid", tid).update(updateData)

// db.table("app_workshop_big_screen").where().eq("tid", oldTid).update({
//     is_del : "1",
//     updated_time: now
// })

return tid
