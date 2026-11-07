{
  "properties": {},
  "id": "f12fde8d078541abb7083a4cfc6fb3b4",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "04.浏览接口",
  "createTime": 1782955344092,
  "updateTime": 1784437771759,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/view",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"validation-market-catalog\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": [\n        {\n            \"tid\": \"validation-market-catalog\",\n            \"viewCount\": 2\n        }\n    ]\n}",
  "description": "按 tid 为当前租户已上架目录登记一次浏览。会话、主记录及发布索引校验后，在关系库事务内原子递增 market_view_count；索引仅补投影，失败不重复计数。返回目录编号和已确认浏览量。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import "@post:/sym/user/me" as me
import '@/common/configData' as configData
import elasticsearch
import tableRead
import tableCounter
import tenantRuntime
import log

// POST /dws/market/view：为当前租户已上架目录登记一次浏览，关系库保存权威计数。
// tid 为目录编号。一次调用计一次访问；未提供访问幂等键，不自动重试不确定的提交。
// 返回 tid 与已确认的 viewCount。索引补投影失败仍返回该值，不创建缺失索引或文档。
var tid = body?.tid == null ? '' : (body.tid + '').trim()
if (!tid.matches('[A-Za-z0-9_-]{1,32}')) { exit 400, '数据目录标识无效' }
var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') { exit 401, '请先登录' }

// 当前应用从登录会话确定，请求中的租户、应用与计数均不参与身份和增量解析。
var submitted = body
var actor = null
try {
    body = {}
    actor = me()
} finally {
    body = submitted
}
if (actor?.id == null || (actor.id + '').trim() == '') { exit 401, '请先登录' }

var catalog = tableRead.from('da_catalog_t', 'c', tenantId)
var catalogId = tableRead.column('c', 'tid', 'TEXT')
catalog.where(tableRead.equal(catalogId, tid))
catalog.where(tableRead.equal(tableRead.column('c', 'is_del', 'NUMBER'), 0))
catalog.where(tableRead.equal(tableRead.column('c', 'asset_status', 'NUMBER'), 2))
catalog.where(tableRead.equal(tableRead.column('c', 'flow_status', 'NUMBER'), 2))
var unavailable = '该数据目录不存在或暂未上架'
if (catalog.rows({tid: catalogId}, catalogId, 'asc', 1).size() == 0) { exit 404, unavailable }

var logicalIndex = configData().es_dataassets
var physicalIndex = esCommonService.resolveTenantIndexName(logicalIndex)
var search = null
try {
    // 上架事实仍以当前租户已有索引为准。只读验证不会自动创建缺失索引。
    search = elasticsearch.index(physicalIndex).search({
        size: 1,
        track_total_hits: true,
        _source: ['tid', 'tenantId', 'assetType', 'publishStatus'],
        query: {bool: {filter: [
            {ids: {values: [tid]}},
            {term: {assetType: 'catalog'}},
            {term: {publishStatus: 1}},
            {bool: {should: [{term: {tenantId: tenantId}}, {bool: {must_not: [{exists: {field: 'tenantId'}}]}}],
                minimum_should_match: 1}}
        ]}}
    })
} catch (error) {
    var message = error?.message == null ? '' : error.message + ''
    if (message.contains('index_not_found_exception')) { exit 404, unavailable }
    throw error
}
var hits = search?.hits?.hits
var total = search?.hits?.total
var totalValue = total instanceof Map && total.relation == 'eq' ? total.value : total
if (search?.timed_out != false || !(search?._shards?.failed instanceof Number) || search._shards.failed != 0 ||
    !(hits instanceof List) || !(totalValue instanceof Number) || totalValue != hits.size() || hits.size() > 1) {
    exit 502, '目录状态暂时无法确认，请稍后重试'
}
if (hits.size() == 0) { exit 404, unavailable }
var hit = hits[0]
var projected = hit?._source
if (hit?._id != tid || !(projected instanceof Map) || projected.tid != tid ||
    projected.assetType != 'catalog' || (projected.publishStatus + '') != '1' ||
    (projected.tenantId != null && (projected.tenantId + '') != tenantId)) {
    exit 404, unavailable
}

// ES 校验时须已上架；提交时再锁定并校验目录主记录，避免预读后被删除或撤销审批。
// 跨存储不能提供上架状态与增量的同一原子点；原子计数和明确回滚重试由技术适配器承担。
var counted = tableCounter.incrementAndGet('da_catalog_t', 'market_view_count', tid,
    {is_del: 0, asset_status: 2, flow_status: 2})
if (counted?.matched == false) { exit 404, unavailable }
if (counted?.matched != true || !(counted?.value instanceof Number) || counted.value::long < 1 ||
    !(counted.incremented == true || counted.saturated == true)) {
    exit 502, '浏览量提交结果无法确认，请刷新查看，勿重复提交'
}
var viewCount = counted.value

// 关系库已经提交，补投影仅按最大值推进既有文档；失败不能把已计数访问变成可重试的整次失败。
try {
    if (esCommonService.advanceCatalogCounter(logicalIndex, tid, viewCount) != true) {
        log.warn('目录浏览量已保存，索引文档缺失，等待后续投影同步')
    }
} catch (error) {
    log.warn('目录浏览量已保存，索引补投影暂未确认，等待后续投影同步')
}
return [{tid: tid, viewCount: viewCount}]
