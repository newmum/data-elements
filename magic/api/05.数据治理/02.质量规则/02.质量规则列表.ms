{
  "properties": {},
  "id": "7220cd17e6c24858a617d3e6f2d4ea42",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "02.质量规则列表",
  "createTime": 1782955345078,
  "updateTime": 1784437771853,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"ruleName\": \"测试\",\n    \"ruleCode\": \"测试\",\n    \"checkDimension\": \"0\",\n    \"ruleLevel\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询数据质量校验规则列表（不分页）；使用请求参数 ruleName、ruleCode、checkDimension、ruleLevel；涉及 data_gov_quality_rule 等业务表。",
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
// 接口：02.质量规则列表
// 路由：POST /dwm/quality/list
// 业务说明：查询数据质量校验规则列表（不分页）；使用请求参数 ruleName、ruleCode、checkDimension、ruleLevel；涉及 data_gov_quality_rule 等业务表。
// 核心业务逻辑：按接口约定编排当前请求并返回业务处理结果。
import log

/**
 * 查询数据质量校验规则列表
 * POST /api/gov/quality/rule/list
 * 
 * 查询数据质量校验规则列表（不分页）
 */

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

// ========== 查询列表 ==========
var list = query
    .orderBy('created_time', 'desc')
    .select()

log.info('查询数据质量规则列表成功, 共查询到 {} 条数据', list.length)

// ========== 返回结果 ==========
return list
