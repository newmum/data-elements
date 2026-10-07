{
  "properties": {},
  "id": "af0138a85b1d4b6b9b7b7252c541aaa8",
  "script": null,
  "groupId": "27defcc7cb47425eb18cd62f8bf153b0",
  "name": "05.标准代码删除",
  "createTime": 1782955345018,
  "updateTime": 1786716527064,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "deleteById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据 tid 逻辑删除标准代码表，将 is_del 置为 1；涉及 da_metadata_code_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.标准代码删除
// 路由：POST /dwm/standard/code/deleteById
// 业务说明：根据 tid 逻辑删除标准代码表，将 is_del 置为 1；涉及 da_metadata_code_t 等业务表。
// 核心业务逻辑：按接口约定编排当前请求并返回业务处理结果。
import log

/**
 * 删除标准代码表（逻辑删除）
 * POST /api/gov/standard/code/delete
 * 
 * 根据 tid 逻辑删除标准代码表，将 is_del 置为 1
 */

// 获取请求参数
var tid = body?.tid
var updatedBy = body?.updatedBy

// 参数校验
if (tid == null || tid == '') {
    throw 'tid不能为空'
}

// 检查数据是否存在
var existingData = db.table('da_metadata_code_t')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .selectOne()

if (existingData == null) {
    log.warn('尝试删除不存在的标准代码表记录, tid: {}', tid)
    throw '数据不存在或已被删除'
}

// 当前时间
var now = new Date()

// ========== 逻辑删除 ==========
var deleteCount = db.table('da_metadata_code_t')
    .where()
    .eq('tid', tid)
    .update({
        is_del: 1,
        updated_by: updatedBy,
        updated_time: now
    })

if (deleteCount == 0) {
    log.error('删除标准代码表失败, tid: {}', tid)
    throw '删除失败'
}

log.info('删除标准代码表成功, tid: {}, 删除记录数: {}', tid, deleteCount)

// ========== 返回结果 ==========
return {
    tid: tid
}
