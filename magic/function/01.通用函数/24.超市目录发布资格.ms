{
  "properties": {},
  "id": "09ecb205502244aba26408368d56aad3",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "24.超市目录发布资格",
  "path": "/market-catalog-eligibility",
  "description": "核验当前租户最多 100 个已通过目录主记录校验的候选。一次读取发布索引，返回已上架、允许共享且关联资源的目录编号。严格校验接收数组，索引不可确认时报错；购物车列表可以显式请求返回 eligibleIds=null，保留原购物车记录。",
  "parameters": [
    {
      "name": "rows",
      "description": "数组为调用方按当前租户及审批状态核验的目录，每项提供 tid 和可空的 targetTableId。购物车列表可传 {candidates: 数组, allowUnconfirmed: true}，索引不可确认时返回 eligibleIds=null。",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import '@/common/configData' as configData
import elasticsearch
import tenantRuntime

// '@/common/market-catalog-eligibility'：批量核验已通过关系库租户与审批检查的目录发布资格。
// rows 数组最多 100 项，按当前租户一次读取索引，返回按输入顺序去重的 eligibleIds。
// 只有购物车列表显式传入 {candidates, allowUnconfirmed: true} 时，索引不可确认才返回 null 资格结果。
var allowUnconfirmed = rows instanceof Map && rows.allowUnconfirmed == true
if (allowUnconfirmed) { rows = rows.candidates }
if (!(rows instanceof List) || rows.size() > 100) { exit 400, '目录候选最多 100 项' }
var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') { exit 401, '当前租户会话无效' }
if (rows.size() == 0) { return {eligibleIds: []} }

var hasLink = value => value instanceof String ? value.trim() != '' : value instanceof Number && value != 0
var ids = []
var candidateTableLinks = {}
for (row in rows) {
    if (!(row instanceof Map) || (!(row.tid instanceof String) && !(row.tid instanceof Number))) {
        exit 400, '目录候选标识无效'
    }
    var id = (row.tid + '').trim()
    if (!id.matches('[^\\s,]{1,32}')) { exit 400, '目录候选标识无效' }
    if (!candidateTableLinks.containsKey(id)) { ids.add(id) }
    candidateTableLinks[id] = candidateTableLinks[id] == true || hasLink(row.targetTableId)
}

// 索引结果不完整不能用来判定目录失效。写入调用默认继续报告错误并中止。
var incompleteSearch = () => {
    if (allowUnconfirmed) { return {eligibleIds: null} }
    exit 502, '目录状态暂时无法确认，请稍后重试'
}

// 只读取按目录编号保存的权威索引文档，历史重复投影不能占用候选结果或覆盖资格。
var search = null
try {
    var indexName = esCommonService.resolveTenantIndexName(configData().es_dataassets)
    search = elasticsearch.index(indexName).search({
        size: ids.size(),
        track_total_hits: true,
        _source: ['tid', 'assetType', 'publishStatus', 'tenantId', 'shareType', 'targetTableId', 'apiId', 'fileId', 'dirId'],
        query: {bool: {filter: [
            {ids: {values: ids}},
            {term: {assetType: 'catalog'}},
            {term: {publishStatus: 1}},
            {bool: {should: [{term: {tenantId: tenantId}}, {bool: {must_not: [{exists: {field: 'tenantId'}}]}}],
                minimum_should_match: 1}}
        ]}}
    })
} catch (error) {
    var message = error?.message == null ? '' : error.message + ''
    if (message.contains('index_not_found_exception')) { return {eligibleIds: []} }
    if (allowUnconfirmed) { return {eligibleIds: null} }
    throw error
}

var hits = search?.hits?.hits
var total = search?.hits?.total
var totalValue = total instanceof Map && total.relation == 'eq' ? total.value : total
if (search?.timed_out != false || !(search?._shards?.failed instanceof Number) || search._shards.failed != 0 ||
    !(hits instanceof List) || !(totalValue instanceof Number) || totalValue != hits.size() || hits.size() > ids.size()) {
    return incompleteSearch()
}

var eligibilityById = {}
for (hit in hits) {
    if (!(hit instanceof Map) || !(hit._id instanceof String) || !(hit._source instanceof Map) ||
        !candidateTableLinks.containsKey(hit._id) || eligibilityById.containsKey(hit._id)) {
        return incompleteSearch()
    }
    var item = hit._source
    if (!(item.tid instanceof String)) { return incompleteSearch() }
    var sameTenant = item.tenantId == null || (item.tenantId + '') == tenantId
    var published = item.tid == hit._id && item.assetType == 'catalog' && (item.publishStatus + '') == '1'
    var shareAllowed = item.shareType == null || (item.shareType + '').trim() != '3'
    var linked = candidateTableLinks[hit._id] || hasLink(item.targetTableId) || hasLink(item.apiId) ||
        hasLink(item.fileId) || hasLink(item.dirId)
    eligibilityById[hit._id] = sameTenant && published && shareAllowed && linked
}
return {eligibleIds: ids.filter(id => eligibilityById[id] == true)}
