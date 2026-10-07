{
  "properties": {},
  "id": "ad917e60dad742ec94d462697944d7c7",
  "script": null,
  "groupId": "8f2a6564b6fd43c28ff7a5e35d4d7cd2",
  "name": "05.页签缩略图",
  "createTime": 1782955345299,
  "updateTime": 1784437771883,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveTabThumbnail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"页签id\",\n    \"thumbnail\": \"URL\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"1ea1bd0491654e6e8dbbf9c5e1ee544f\"\n}",
  "description": "校验页签标识和缩略图内容，更新应用仓库页签的缩略图字段并返回保存结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.页签缩略图
// 路由：POST /das/repository/tab/saveTabThumbnail
// 业务说明：校验页签标识和缩略图内容，更新应用仓库页签的缩略图字段并返回保存结果。
import log
import cn.dev33.satoken.stp.StpUtil;


// 参数校验
var tid = body?.tid
if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,'页签ID不能为空'
}

// 检查记录是否存在
var exists = db.table('app_workshop_repository_tab')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .count()

if (exists == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,'页签不存在'
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

if (body?.thumbnail != null && body?.thumbnail != '') {
    updateData.thumbnail = body?.thumbnail
}

// 更新数据
db.table('app_workshop_repository_tab')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .update(updateData)

log.info('修改应用仓库成功, tid: {}', tid)

// ========== 返回结果 ==========
return tid
