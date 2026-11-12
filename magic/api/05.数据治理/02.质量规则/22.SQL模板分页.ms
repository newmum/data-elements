{
  "properties" : { },
  "id" : "quality_sql_template_page_v1",
  "script" : null,
  "groupId" : "d5ce0d4911e94d7bb3c22a9f67547406",
  "name" : "22.SQL模板分页",
  "createTime" : 1792504666367,
  "updateTime" : null,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/templates/page",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : null,
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "按当前租户分页查询可复用SQL异常条件模板；固定一次计数和一次分页，不按行补查。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import tenantRuntime
import tableRead
import tableSort

/*
 * POST /dwm/quality/templates/page。供质量规则菜单和任务选择器读取当前租户的 SQL 模板。
 * 参数包括 page、size（1 至 100）、keyword、dimension 和白名单排序，返回 total 与 list。
 * 只读当前认证租户的有效模板，模板由固定范围标识与历史质量规则区分。
 */
var tenantId = tenantRuntime.id()
var page = Math.max(1, body?.page::int(1))
var size = Math.min(100, Math.max(1, body?.size::int(20)))
var keyword = body?.keyword
var dimension = body?.dimension

var field = tableSort.field(body, {
    rule_name: 'rule_name',
    rule_code: 'rule_code',
    updated_time: 'updated_time'
}, 'updated_time')
var direction = tableSort.direction(body)
var column = (name, type) => tableRead.column('r', name, type)
var ruleName = column('rule_name', 'TEXT')
var ruleCode = column('rule_code', 'TEXT')
var checkDimension = column('check_dimension', 'TEXT')
var updatedTime = column('updated_time', 'TIME')
var sortColumns = {rule_name: ruleName, rule_code: ruleCode, updated_time: updatedTime}

// 租户条件由结构化查询绑定，数据和总数共用同一模板范围及筛选条件。
var query = tableRead.from('data_gov_quality_rule', 'r', tenantId)
query.where(tableRead.equal(column('is_del', 'NUMBER'), 0))
query.where(tableRead.equal(column('scope_of_application', 'TEXT'), 'QUALITY_SQL_TEMPLATE_V1'))
if (keyword != null) {
    var pattern = '%' + keyword + '%'
    query.where(tableRead.any([tableRead.like(ruleName, pattern), tableRead.like(ruleCode, pattern)]))
}
if (dimension != null && dimension != '') {
    query.where(tableRead.equal(checkDimension, dimension))
}

// 数据库先排序再分页，同值以 tid 升序决胜，空值放在末尾。
var result = query.page({
    tid: column('tid', 'TEXT'),
    rule_name: ruleName,
    rule_code: ruleCode,
    check_dimension: checkDimension,
    rule_description: column('rule_description', 'LOB'),
    check_logic: column('check_logic', 'LOB'),
    updated_time: updatedTime
}, sortColumns[field], direction, 'tid', page, size)

// 保持原分页契约：无匹配时 list 为 null，有匹配但页码越界时返回空数组。
if (result.total == 0) {
    return {total: result.total, list: null}
}

// 使用各驱动一致的列标签读取结果，再显式组装原有驼峰字段。
var list = []
for (row in result.rows) {
    list.add({
        tid: row.tid,
        ruleName: row.rule_name,
        ruleCode: row.rule_code,
        checkDimension: row.check_dimension,
        ruleDescription: row.rule_description,
        checkLogic: row.check_logic,
        updatedTime: row.updated_time
    })
}
return {total: result.total, list: list}
