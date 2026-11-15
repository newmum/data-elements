{
  "properties": {},
  "id": "e38ba90d18b141488564a791ccdcefcd",
  "script": null,
  "groupId": "37c35deab6bc4e678c8955cc951f2ffd",
  "name": "02.删除应用",
  "createTime": 1782955345162,
  "updateTime": 1784437771870,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "逻辑删除（更新is_del字段为1）；使用请求参数 tid；涉及 app_workshop_repository 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.删除应用
// 路由：POST /das/repository/delete
// 业务说明：逻辑删除（更新is_del字段为1）；使用请求参数 tid；涉及 app_workshop_repository 等业务表。
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

// 逻辑删除（更新is_del字段为1）
db.table('app_workshop_repository')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .update({
        is_del: 1,
        updated_time: currentTime
    })

log.info('删除应用仓库成功, tid: {}', tid)

// ========== 返回结果 ==========
return true
