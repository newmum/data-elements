{
  "properties": {},
  "id": "b78cf957a41645ccbea4fda1f6c6b9e9",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "03.收藏接口",
  "createTime": 1782955344081,
  "updateTime": 1787153757069,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/collect",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"catalogId\":\"validation-catalog\",\"collected\":true}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"code\":0,\"success\":true,\"data\":true}",
  "description": "按当前租户和部门设置已上架目录的收藏状态。collected 为布尔值，省略时兼容原切换调用。返回实际收藏状态。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import org.redisson.api.RedissonClient as redissonClient
import "@post:/sym/user/me" as me
import '@/common/configData' as configData
import elasticsearch
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import tableRead
import tenantRuntime
import log

// POST /dws/market/collect：设置当前部门对数据目录的收藏状态，返回实际状态。
// 显式传 collected 时允许重试。旧调用省略时按当前状态切换。
var submittedId = body?.catalogId
if (!(submittedId instanceof String) && !(submittedId instanceof Number)) {
    exit 400, '数据目录标识无效'
}
var catalogId = (submittedId + '').trim()
if (!catalogId.matches('[A-Za-z0-9_-]{1,32}')) {
    exit 400, '数据目录标识无效'
}
var desired = body?.collected
if (body.containsKey('collected') && !(desired instanceof Boolean)) {
    exit 400, '收藏状态必须为布尔值'
}

var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') { exit 401, '请先登录' }
// 用户接口使用当前会话应用，调用方的筛选或业务字段不能参与身份解析。
var submitted = body
var user = null
try {
    body = {}
    user = me()
} finally {
    body = submitted
}
if (user?.id == null || (user.id + '').trim() == '') {
    exit 401, '请先登录'
}
var department = user.orgId
if (department == null || (department + '').trim() == '') {
    exit 409, '当前账号未关联部门，无法操作收藏'
}

var lock = null
var acquired = false
try {
    lock = redissonClient.getLock('data-elements:market:favorite:' + tenantId + ':' + department + ':' + catalogId)
    acquired = lock.tryLock()
} catch (error) {
    exit 503, '收藏暂时无法处理，请稍后重试'
}
if (!acquired) {
    exit 409, '收藏状态正在更新，请稍后重试'
}
try {
    var textColumn = (name) => tableRead.column('f', name, 'TEXT')
    var favorite = tableRead.from('data_market_collect_t', 'f', tenantId)
    favorite.where(tableRead.equal(textColumn('apply_org_id'), department))
    favorite.where(tableRead.equal(textColumn('catalog_id'), catalogId))
    var existing = favorite.rows({tid: textColumn('tid')}, textColumn('tid'), 'asc', 1).size() > 0
    var nextState = desired == null ? !existing : desired
    if (nextState == existing) { return existing }

    // 取消收藏只删除当前部门的记录，保留对历史失效目录的清理能力。
    if (!nextState) {
        db.table('data_market_collect_t').where()
            .eq('tenant_id', tenantId).eq('apply_org_id', department).eq('catalog_id', catalogId).delete()
        return false
    }

    // 新增收藏同时校验登记主表和租户发布索引，避免引用其它租户或未上架目录。
    var catalog = db.table('da_catalog_t').columns('tid').where()
        .eq('tenant_id', tenantId).eq('tid', catalogId).eq('is_del', 0)
        .eq('asset_status', 2).eq('flow_status', 2).selectOne()
    if (catalog == null) { exit 404, '该数据目录不存在或暂未上架' }
    var indexName = esCommonService.resolveTenantIndexName(configData().es_dataassets)
    var result = null
    try {
        result = elasticsearch.index(indexName).search({
            size: 1,
            _source: ['tid', 'assetType'],
            query: {bool: {filter: [
                {term: {tid: catalogId}},
                {term: {assetType: 'catalog'}},
                {term: {publishStatus: 1}},
                {bool: {should: [{term: {tenantId: tenantId}}, {bool: {must_not: [{exists: {field: 'tenantId'}}]}}],
                    minimum_should_match: 1}}
            ]}}
        })
    } catch (error) {
        if ((error?.message + '').contains('index_not_found_exception')) {
            exit 404, '该数据目录不存在或暂未上架'
        }
        throw error
    }
    if (result?.timed_out == true || (result?._shards?.failed || 0) > 0) {
        exit 502, '目录状态暂时无法确认，请稍后重试'
    }
    var hits = result?.hits?.hits || []
    if (hits.size() == 0 || hits[0]?._source?.tid != catalogId || hits[0]._source.assetType != 'catalog') {
        exit 404, '该数据目录不存在或暂未上架'
    }
    db.table('data_market_collect_t').insert({
        tid: IdUtil.getSnowflakeNextIdStr(),
        tenant_id: tenantId,
        catalog_id: catalogId,
        apply_user_id: user.id,
        apply_user_name: user.userName,
        apply_org_id: department,
        org_tree_path: user.serialNumber
    })
    return true
} finally {
    try {
        lock.unlock()
    } catch (error) {
        log.warn('目录收藏操作锁释放失败')
    }
}
