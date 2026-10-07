{
  "properties": {},
  "id": "dd4214943e5341c482a59821a64245eb",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "05.质量规则保存修改",
  "createTime": 1782955345061,
  "updateTime": 1784437771851,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"a3b8fb4786b64c3b98cb4a3d6978fab5\",\n    \"ruleName\": \"非空校验2233\",\n    \"ruleCode\": \"not_null_check\",\n    \"checkDimension\": \"0\",\n    \"ruleLevel\": \"0\",\n    \"ruleDescription\": \"核心字段不含 NULL值\",\n    \"scopeOfApplication\": \"所有必填字段\",\n    \"configParameters\": \"column: 校验字段\",\n    \"checkLogic\": \"SELECT count(1) FROM ${table} WHERE ${column} IS NULL\",\n    \"createdBy\": \"admin\",\n    \"updatedBy\": \"admin\",\n    \"tenantId\": \"T-9527\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验请求资料后执行质量规则保存修改，返回保存或配置结果；使用请求参数 tid、ruleName、ruleCode、checkDimension、ruleLevel、ruleDescription、scopeOfApplication、configParameters；涉及 data_gov_quality_rule 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.质量规则保存修改
// 路由：POST /dwm/quality/saveOrUpdate
// 业务说明：校验请求资料后执行质量规则保存修改，返回保存或配置结果；使用请求参数 tid、ruleName、ruleCode、checkDimension、ruleLevel、ruleDescription、scopeOfApplication、configParameters；涉及 data_gov_quality_rule 等业务表。
import cn.hutool.core.util.IdUtil
import log
import tenantRuntime

/**
 * 保存或更新数据质量校验规则
 * POST /api/gov/quality/rule/saveOrUpdate
 * 
 * 如果 body.tid 有值则更新，否则新增
 * 数据保存到 data_gov_quality_rule 表
 */

// 获取请求参数
var tid = body?.tid
var ruleName = body?.ruleName
var ruleCode = body?.ruleCode
var checkDimension = body?.checkDimension
var ruleLevel = body?.ruleLevel
var ruleDescription = body?.ruleDescription
var scopeOfApplication = body?.scopeOfApplication
var configParameters = body?.configParameters
var checkLogic = body?.checkLogic
var createdBy = body?.createdBy
var updatedBy = body?.updatedBy
var tenantId = tenantRuntime.id()

var isUpdate = tid != null && tid != ''

// 当前时间
var now = new Date()

// ========== 保存数据 data_gov_quality_rule ==========
if (isUpdate) {
    // 更新数据
    db.table('data_gov_quality_rule')
        .primary('tid')
        .update({
            tid: tid,
            rule_name: ruleName,
            rule_code: ruleCode,
            check_dimension: checkDimension,
            rule_level: ruleLevel,
            rule_description: ruleDescription,
            scope_of_application: scopeOfApplication,
            config_parameters: configParameters,
            check_logic: checkLogic,
            updated_by: updatedBy,
            updated_time: now,
            tenant_id: tenantId
        })
    log.info('更新数据质量规则成功, tid: {}', tid)
} else {
    // 新增数据，生成新的 tid
    tid = IdUtil.getSnowflakeNextIdStr()
    // 从 MySQL 业务表读取当前有效数据。
    db.table('data_gov_quality_rule')
        .primary('tid')
        .insert({
            tid: tid,
            rule_name: ruleName,
            rule_code: ruleCode,
            check_dimension: checkDimension,
            rule_level: ruleLevel,
            rule_description: ruleDescription,
            scope_of_application: scopeOfApplication,
            config_parameters: configParameters,
            check_logic: checkLogic,
            created_by: createdBy,
            created_time: now,
            updated_by: updatedBy,
            updated_time: now,
            is_del: 0,
            tenant_id: tenantId
        })
    log.info('新增数据质量规则成功, tid: {}', tid)
}

// ========== 查询保存后的完整数据 ==========
//     .where()
//     .eq('tid', tid)
//     .eq('is_del', 0)
//     .selectOne()

// 构建返回数据


// ========== 返回结果 ==========
//     code: 200,
//     success: true,
//     message: isUpdate ? '更新成功' : '新增成功',
//     data: data
// }

return tid
