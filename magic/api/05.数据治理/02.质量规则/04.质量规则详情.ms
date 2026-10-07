{
  "properties": {},
  "id": "ed622b19f7834df19cec9b1af9989ed7",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "04.质量规则详情",
  "createTime": 1782955345043,
  "updateTime": 1784437771838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "queryById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"222\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据tid查询数据质量校验规则；涉及 data_gov_quality_rule 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.质量规则详情
// 路由：POST /dwm/quality/queryById
// 业务说明：根据tid查询数据质量校验规则；涉及 data_gov_quality_rule 等业务表。
import log
import request

/**
 * 根据tid查询数据质量校验规则
 * GET /api/gov/quality/rule/query
 * 
 * 根据tid查询数据质量校验规则详情
 */

// 获取请求参数
var tid = request.getParameter('tid')

if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tid不能为空'
}

// ========== 查询数据 ==========
var result = db.table('data_gov_quality_rule')
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
