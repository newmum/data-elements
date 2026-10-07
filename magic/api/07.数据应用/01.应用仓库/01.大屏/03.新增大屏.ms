{
  "properties": {},
  "id": "085a628db82248c9aec36a1e57e1f9f2",
  "script": null,
  "groupId": "7c271b88983b467282fd9d498d0f8d66",
  "name": "03.新增大屏",
  "createTime": 1782955345224,
  "updateTime": 1784437771875,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"name\": \"123\",\n    \"nameCn\": \"2323423\",\n    \"sourceCode\": \"json\",\n    \"thumbnail\": \"缩略图\",\n    \"parentId\": \"1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"f55db88041a14eaba35fbd06dc91e171\"\n}",
  "description": "校验请求资料后执行新增大屏，返回保存或配置结果；使用请求参数 name、nameCn、sourceCode、thumbnail、parentId；涉及 app_workshop_big_screen 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.新增大屏
// 路由：POST /das/repository/bigScreen/save
// 业务说明：校验请求资料后执行新增大屏，返回保存或配置结果；使用请求参数 name、nameCn、sourceCode、thumbnail、parentId；涉及 app_workshop_big_screen 等业务表。
import cn.hutool.json.JSONUtil
import log
import cn.hutool.core.util.IdUtil
import IdGenerator
import jsons


// 解析请求体并为可选参数设置默认值。
var name = body?.name
var nameCn = body?.nameCn
var sourceCode = body?.sourceCode
var thumbnail = body?.thumbnail
var parentId = body?.parentId

var tid =  IdUtil.getSnowflakeNextIdStr()
var now = new Date()
// 将校验后的业务数据写入 MySQL 主表。
db.table("app_workshop_big_screen").insert({
    tid : tid,
    name : name,
    name_cn : nameCn,
    source_code : sourceCode,
    thumbnail : thumbnail,
    parent_id : parentId,
    created_time: now,
    updated_time: now
})
// 返回当前业务分支的标准结果。
return tid
