{
  "properties": {},
  "id": "3c15325662a5467ea49e20753c3fa82d",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "03.质量规则分页查询",
  "createTime": 1782955345070,
  "updateTime": 1784437771852,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": \"1\",\n    \"pageSize\": \"20\",\n    \"ruleName\": \"22\",\n    \"ruleCode\": \"not_null\",\n    \"checkDimension\": \"0\",\n    \"ruleLevel\": \"0\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "支持分页查询数据质量校验规则列表；使用请求参数 pageNum、pageSize、ruleName、ruleCode、checkDimension、ruleLevel；涉及 data_gov_quality_rule 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.质量规则分页查询
// 路由：POST /dwm/quality/page
// 业务说明：支持分页查询数据质量校验规则列表；使用请求参数 pageNum、pageSize、ruleName、ruleCode、checkDimension、ruleLevel；涉及 data_gov_quality_rule 等业务表。
// 核心业务逻辑：按接口约定编排当前请求并返回业务处理结果。
import log

/**
 * 分页查询数据质量校验规则
 * POST /api/gov/quality/rule/page
 * 
 * 支持分页查询数据质量校验规则列表
 */

// 获取分页参数
var pageNum = body?.pageNum::long(0)
var pageSize = body?.pageSize::long(20)
var offsetLimit = pageNum == 0? 0 : (pageNum - 1)* pageSize

// 获取查询条件
var ruleName = body?.ruleName
var ruleCode = body?.ruleCode
var checkDimension = body?.checkDimension
var ruleLevel = body?.ruleLevel

// 构建查询条件
var query = db.table('data_gov_quality_rule')
    .where()
    .eq('is_del', 0)

if (ruleName != null && ruleName != '') {
    query = query.like('rule_name', '%' + ruleName + '%')
}

if (ruleCode != null && ruleCode != '') {
    query = query.like('rule_code', '%' + ruleCode + '%')
}

if (checkDimension != null && checkDimension != '') {
    query = query.eq('check_dimension', checkDimension)
}

if (ruleLevel != null && ruleLevel != '') {
    query = query.eq('rule_level', ruleLevel)
}

// ========== 查询总数 ==========
var total = query.count()

// ========== 查询列表 ==========
var list = query
    .orderBy('created_time', 'desc')
    .page(pageSize, offsetLimit)

log.info('分页查询数据质量规则成功, pageNum: {}, pageSize: {}, total: {}', pageNum, pageSize, total)

// ========== 返回结果 ==========
return {
    total: total,
    list: list
}
