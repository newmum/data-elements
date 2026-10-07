{
  "properties": {},
  "id": "0b346b21034e4b0892f9ef825105f9c6",
  "script": null,
  "groupId": "55c792d1ba0f4cb79b01a6d5c42dacea",
  "name": "02.数据元详情",
  "createTime": 1782955344986,
  "updateTime": 1786716526792,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "queryById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"6069308f214645588a368fef51ce2007\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按数据元主键读取未删除的定义和治理资料，返回编辑与查看页面使用的详情。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.数据元详情
// 路由：POST /dwm/standard/element/queryById
// 业务说明：按数据元主键读取未删除的定义和治理资料，返回编辑与查看页面使用的详情。
import log

/**
 * 根据tid查询
 * 
 */

// 获取请求参数
var tid = body?.tid

if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tid不能为空'
}

// ========== 查询数据 ==========
var result = db.table('da_metadata_t')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .selectOne()

if (result == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '规则不存在'
}

log.info('查询数据质量规则成功, tid: {}', tid)

// ========== 返回结果 ==========
return result
