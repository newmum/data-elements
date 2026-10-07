{
  "properties": {},
  "id": "980b22c606a24830951e47836fc2e9ad",
  "script": null,
  "groupId": "55c792d1ba0f4cb79b01a6d5c42dacea",
  "name": "04.数据元发布状态修改",
  "createTime": 1782955344968,
  "updateTime": 1786716526626,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/updatePublishStatus",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据 tid 更新数据元的发布状态；涉及 da_metadata_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": {
    "name": "",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "code",
        "value": "100120",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "msg",
        "value": "user_no_login",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "success",
        "value": "false",
        "description": "",
        "required": false,
        "dataType": "Boolean",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "data",
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  }
}
================================
// 接口：04.数据元发布状态修改
// 路由：POST /dwm/standard/element/updatePublishStatus
// 业务说明：根据 tid 更新数据元的发布状态；涉及 da_metadata_t 等业务表。
import log

/**
 * 修改数据元发布状态
 * POST /api/gov/data/meta/updatePublishStatus
 * 
 * 根据 tid 更新数据元的发布状态
 * 请求参数：tid, publishStatus, updatedBy
 */

// 获取请求参数
var tid = body?.tid
var publishStatus = body?.publishStatus
var updatedBy = body?.updatedBy

// 参数校验
if (tid == null || tid == '') {
    throw 'tid不能为空'
}

if (publishStatus == null) {
    throw 'publishStatus不能为空'
}

// 当前时间
var now = new Date()

// ========== 更新发布状态 ==========
var updateCount = db.table('da_metadata_t')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)  // 只更新未删除的数据
    .update({
        publish_status: publishStatus,
        updated_time: now
    })

if (updateCount == 0) {
    // 如果没有更新到任何记录，可能是tid不存在或数据已被删除
    log.error('更新发布状态失败，未找到对应数据或数据已被删除, tid: {}', tid)
    throw '更新失败，未找到对应数据'
}

log.info('更新数据元发布状态成功, tid: {}, publishStatus: {}, 更新记录数: {}', 
    tid, publishStatus, updateCount)

// ========== 查询更新后的完整数据 ==========
var resultData = db.table('da_metadata_t')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .selectOne()

if (resultData == null) {
    throw '数据不存在或已被删除'
}

// 返回当前业务分支的标准结果。
return resultData
