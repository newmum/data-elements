{
  "properties": {},
  "id": "f15e72e40a2c41f4af8620ceb68a393b",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "06.质量规则删除",
  "createTime": 1782955345090,
  "updateTime": 1784437771853,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/deleteById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"2e83a7abf33140c7a3df27d0c79e98c1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据id删除数据质量校验规则；使用请求参数 tid；涉及 data_gov_quality_rule 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.质量规则删除
// 路由：POST /dwm/quality/deleteById
// 业务说明：根据id删除数据质量校验规则；使用请求参数 tid；涉及 data_gov_quality_rule 等业务表。
import log

/**
 * 根据id删除数据质量校验规则
 * POST /api/gov/quality/rule/delete
 * 
 * 根据tid逻辑删除数据质量校验规则
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
var result = db.table('data_gov_quality_rule')
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

log.info('删除数据质量规则成功, tid: {}', tid)

// ========== 返回结果 ==========
return true
