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
  "description" : "标准代码详情：兼容主键与代码集标识查询；只读 da_metadata_code_t；使用请求参数 tid、id、dictCode、codeSet、codeValue。",
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
// 接口：03.标准代码详情
// 路由：POST /dwm/standard/code/queryById
// 业务说明：标准代码详情：兼容主键与代码集标识查询；lookups 可一次批量查询最多 50 个标识；只读当前租户 da_metadata_code_t。
// 标准代码详情：兼容主键与代码集标识查询；只读 da_metadata_code_t。
import log
import tenantRuntime

var tenantId = tenantRuntime.id()
if (body?.lookups != null) {
    var submitted = body.lookups
    if (!(submitted instanceof List) || submitted.size() == 0 || submitted.size() > 50) {
        exit 400, '标准代码标识必须是 1 至 50 项的数组'
    }
    var lookups = []
    for (raw in submitted) {
        var lookup = ('' + raw).trim()
        if (lookup == '' || lookup.length() > 255) { exit 400, '标准代码标识不合法' }
        if (!lookups.contains(lookup)) { lookups.add(lookup) }
    }
    var rows = db.select("""
        select * from da_metadata_code_t
         where tenant_id=#{tenantId} and is_del=0 and (
             tid in <foreach collection="lookups" item="id" open="(" close=")" separator=",">#{id}</foreach>
             or (code_value='__HEADER__' and (
                 dict_code in <foreach collection="lookups" item="id" open="(" close=")" separator=",">#{id}</foreach>
                 or code_set in <foreach collection="lookups" item="id" open="(" close=")" separator=",">#{id}</foreach>
             ))
         )
    """)
    var byTid = {}
    var byDictCode = {}
    var byCodeSet = {}
    for (row in rows) {
        var tid = '' + row.tid
        if (lookups.contains(tid) && byTid[tid] == null) { byTid[tid] = row }
        if (row.codeValue == '__HEADER__') {
            var dictCode = '' + row.dictCode
            var codeSet = '' + row.codeSet
            if (lookups.contains(dictCode) && byDictCode[dictCode] == null) { byDictCode[dictCode] = row }
            if (lookups.contains(codeSet) && byCodeSet[codeSet] == null) { byCodeSet[codeSet] = row }
        }
    }
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

var data = db.table('da_metadata_code_t')
    .where()
    .eq('tid', lookup)
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .selectOne()

if (data == null) {
    data = db.table('da_metadata_code_t')
        .where()
        .eq('dict_code', lookup)
        .eq('tenant_id', tenantId)
        .eq('code_value', '__HEADER__')
        .eq('is_del', 0)
        .selectOne()
}

if (data == null) {
    data = db.table('da_metadata_code_t')
        .where()
        .eq('code_set', lookup)
        .eq('tenant_id', tenantId)
        .eq('code_value', '__HEADER__')
        .eq('is_del', 0)
        .selectOne()
}

if (data == null) {
    log.warn('未找到标准代码记录, lookup: {}', lookup)
    return { found: false, tid: lookup, message: '标准代码不存在或已删除', dict_item_value: '[]' }
}

return data
