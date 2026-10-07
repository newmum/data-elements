{
  "properties": {},
  "id": "5aad1c7bb48843a48a985ef41c0c2218",
  "script": null,
  "groupId": "55c792d1ba0f4cb79b01a6d5c42dacea",
  "name": "05.数据元删除",
  "createTime": 1782955344960,
  "updateTime": 1786716526544,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/meatDeleteById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"de73de5f9daf42d9916f35fa2aee32e2\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据id删除数数据标准数据元；使用请求参数 tid；涉及 da_metadata_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.数据元删除
// 路由：POST /dwm/standard/element/meatDeleteById
// 业务说明：根据id删除数数据标准数据元；使用请求参数 tid；涉及 da_metadata_t 等业务表。
import log

/**
 * 根据id删除数数据标准数据元
 * 
 * 根据tid逻辑删除数据标准数据元
 */

// 获取请求参数
var tid = body?.tid

if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tid不能为空'
}

// 当前时间
var now = new Date()

// ========== 逻辑删除数据 ==========
var result = db.table('da_metadata_t')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .update({
        is_del: 1,
        updated_time: now
    })

if (result == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '规则不存在'
}

log.info('删除数据标准数据元成功, tid: {}', tid)

// ========== 返回结果 ==========
return true
