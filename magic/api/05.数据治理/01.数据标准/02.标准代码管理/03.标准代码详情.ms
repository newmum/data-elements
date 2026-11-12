{
  "properties" : { },
  "id" : "684305fb587f4e60904f927b36331e34",
  "script" : null,
  "groupId" : "27defcc7cb47425eb18cd62f8bf153b0",
  "name" : "03.标准代码详情",
  "createTime" : 1782955345033,
  "updateTime" : 1791111886673,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "queryById",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n    \"tid\": \"6af9d796b7f64f178824b6b170b2a468\",\n    \"dictItemValue\": \"122\"\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "读取当前登录租户的有效标准代码。支持 tid、id、dictCode、codeSet、codeValue 单项查询及最多 50 项 lookups 批量查询，按主键、字典编码、代码集的顺序匹配。",
  "requestBodyDefinition" : {
    "name" : "",
    "value" : "",
    "description" : "",
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : "",
    "error" : "",
    "expression" : "",
    "children" : [ ]
  },
  "responseBodyDefinition" : null
}
================================
import log
import tenantRuntime

/*
 * POST /dwm/standard/code/queryById。读取当前登录租户的有效标准代码详情。
 * 单项接受 tid、id、dictCode、codeSet 或 codeValue，返回记录或未找到说明。
 * lookups 接受 1 至 50 个标识，返回按原标识索引的对象，每个未命中项均有占位。
 */
var tenantId = tenantRuntime.id()
var headerValue = '__HEADER__'
var activeCodes = () => db.table('da_metadata_code_t').where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)

if (body?.lookups != null) {
    var submitted = body.lookups
    if (!(submitted instanceof List) || submitted.size() == 0 || submitted.size() > 50) {
        exit 400, '标准代码标识必须是 1 至 50 项的数组'
    }

    var lookups = []
    for (raw in submitted) {
        var lookup = ('' + raw).trim()
        if (lookup == '' || lookup.length() > 255) {
            exit 400, '标准代码标识不合法'
        }
        if (!lookups.contains(lookup)) {
            lookups.add(lookup)
        }
    }

    // 每批一次读取，只有代码集头记录可通过字典编码或代码集匹配。
    var rows = activeCodes()
        .and(match => match.in('tid', lookups).or().and(header => header
            .eq('code_value', headerValue)
            .and(alias => alias.in('dict_code', lookups).or().in('code_set', lookups))))
        .select()

    var byTid = {}
    var byDictCode = {}
    var byCodeSet = {}
    for (row in rows) {
        var tid = '' + row.tid
        if (lookups.contains(tid) && byTid[tid] == null) {
            byTid[tid] = row
        }
        if (row.codeValue == headerValue) {
            var dictCode = '' + row.dictCode
            var codeSet = '' + row.codeSet
            if (lookups.contains(dictCode) && byDictCode[dictCode] == null) {
                byDictCode[dictCode] = row
            }
            if (lookups.contains(codeSet) && byCodeSet[codeSet] == null) {
                byCodeSet[codeSet] = row
            }
        }
    }

    // 保留主键优先，其次字典编码，再匹配代码集。
    var result = {}
    for (lookup in lookups) {
        result[lookup] = byTid[lookup] || byDictCode[lookup] || byCodeSet[lookup] ||
            { found: false, tid: lookup, message: '标准代码不存在或已删除', dict_item_value: '[]' }
    }
    return result
}

var lookup = body?.tid || body?.id || body?.dictCode || body?.codeSet || body?.codeValue
if (lookup == null || lookup == '') {
    return { found: false, message: '标准代码标识不能为空', dict_item_value: '[]' }
}

// 保留单项入口的数据库等值比较与分级查找。
var data = activeCodes().eq('tid', lookup).selectOne()
if (data == null) {
    data = activeCodes()
        .eq('dict_code', lookup)
        .eq('code_value', headerValue)
        .selectOne()
}

if (data == null) {
    data = activeCodes()
        .eq('code_set', lookup)
        .eq('code_value', headerValue)
        .selectOne()
}

if (data == null) {
    log.warn('未找到标准代码记录, lookup: {}', lookup)
    return { found: false, tid: lookup, message: '标准代码不存在或已删除', dict_item_value: '[]' }
}

return data
