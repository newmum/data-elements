{
  "properties": {},
  "id": "e8c07b2ddfa84eb6a21655498a15e596",
  "script": null,
  "groupId": "5e2962ddb12b4ed98ccdd9a389640db6",
  "name": "01.删除图标",
  "createTime": 1782955345242,
  "updateTime": 1784437771876,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"1e489cb251fd4c768fe2777cb9ce3458\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": true\n}",
  "description": "逻辑删除（更新is_del字段为1）；使用请求参数 tid；涉及 app_workshop_repository_self_icon 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.删除图标
// 路由：POST /das/repository/icon/delete
// 业务说明：逻辑删除（更新is_del字段为1）；使用请求参数 tid；涉及 app_workshop_repository_self_icon 等业务表。
import log

/**
 * 应用仓库删除
 * DELETE /api/das/repository/delete/{tid}
 * 
 * 逻辑删除应用仓库记录
 */

// 获取应用ID
var tid = body?.tid

if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,'应用图标ID不能为空'
}

// 检查记录是否存在
var exists = db.table('app_workshop_repository_self_icon')
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

// 逻辑删除（更新is_del字段为1）
db.table('app_workshop_repository_self_icon')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .update({
        is_del: 1,
        updated_time: currentTime
    })

log.info('删除应用图标成功, tid: {}', tid)

// ========== 返回结果 ==========
return true
