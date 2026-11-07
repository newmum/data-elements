{
  "properties": {},
  "id": "7ddfe8a23a524cc0ad8f801cb57b7381",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "05.聚合统计",
  "createTime": 1782955344101,
  "updateTime": 1784437771760,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/count",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"success\": true,\n    \"data\": {\n        \"orgPath\": {}\n    }\n}",
  "description": "在当前租户索引统计已上架的数据目录，按组织路径返回数量，不返回目录明细。需要有效登录账号，兼容旧超市页面发送的固定聚合参数。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import "@post:/sym/user/me" as me
import '@/common/configData' as configData
import elasticsearch
import tenantRuntime

// POST /dws/market/count：统计当前租户已上架目录的组织路径分布，返回 {orgPath: {路径: 数量}}。
// 请求传空对象，旧页面的固定过滤和聚合形状也可用。聚合仅执行只读搜索。
var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') {
    exit 401, '当前租户会话无效'
}
// 用户接口从会话选择当前应用，聚合请求的附加字段不能影响身份校验。
var submitted = body
var user = null
try {
    body = {}
    user = me()
} finally {
    body = submitted
}
if (user?.id == null || (user.id + '').trim() == '') {
    exit 401, '当前用户会话无效'
}

var bucketLimit = 10000
if (body != null && !(body instanceof Map)) {
    exit 400, '目录统计参数必须为对象'
}
if (body != null && !body.isEmpty()) {
    // 兼容已加载的旧页面。只认可原固定参数，绝不将调用方给出的 DSL 传给检索服务。
    var query = body.query
    var aggs = body.aggs
    if (body.size() != 2 || !(query instanceof List) || query.size() != 2
        || !(aggs instanceof Map) || aggs.size() != 1) {
        exit 400, '目录统计仅支持组织路径聚合'
    }
    var facet = aggs.orgPath
    var terms = facet?.terms
    if (!(facet instanceof Map) || facet.size() != 1 || !(terms instanceof Map) || terms.size() != 2
        || terms.field != 'orgPath' || !(terms.size instanceof Number) || terms.size != bucketLimit) {
        exit 400, '目录统计仅支持组织路径聚合'
    }

    var catalog = false
    var published = false
    for (clause in query) {
        var term = clause?.term
        if (!(clause instanceof Map) || clause.size() != 1 || !(term instanceof Map) || term.size() != 1) {
            exit 400, '目录统计仅支持已上架目录'
        }
        if (term.assetType == 'catalog') {
            catalog = true
        } else if (term.publishStatus instanceof Number && term.publishStatus == 1) {
            published = true
        } else {
            exit 400, '目录统计仅支持已上架目录'
        }
    }
    if (!catalog || !published) {
        exit 400, '目录统计仅支持已上架目录'
    }
}

// 与超市列表使用同一租户范围，保留未写租户字段的历史投影。
var dsl = {
    size: 0,
    track_total_hits: false,
    query: {bool: {filter: [
        {term: {assetType: 'catalog'}},
        {term: {publishStatus: 1}},
        {bool: {should: [{term: {tenantId: tenantId}}, {bool: {must_not: [{exists: {field: 'tenantId'}}]}}],
            minimum_should_match: 1}}
    ]}},
    aggs: {orgPath: {terms: {field: 'orgPath', size: bucketLimit}}}
}

// 使用可信配置解析物理租户索引，查询路径不初始化索引，也不预先请求索引元信息。
var indexName = esCommonService.resolveTenantIndexName(configData().es_dataassets)
var search = null
try {
    search = elasticsearch.index(indexName).search(dsl)
} catch (error) {
    var message = error?.message == null ? '' : error.message + ''
    if (message.contains('index_not_found_exception')) {
        return {orgPath: {}}
    }
    throw error
}

var buckets = search?.aggregations?.orgPath?.buckets
if (search?.timed_out == true || (search?._shards?.failed || 0) > 0 || !(buckets instanceof List)) {
    exit 502, '目录统计未完成，请稍后重试'
}
var counts = {}
for (bucket in buckets) {
    counts[bucket.key + ''] = bucket.doc_count
}
return {orgPath: counts}
