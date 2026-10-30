{
  "properties": {},
  "id": "datapd_database_list_01",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "02.数据源列表查询",
  "createTime": null,
  "updateTime": 1784510683572,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"appId\": \"可选\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "[]",
  "description": "下拉列表同样读取 ES，避免每次打开表单扫描主表和全部动态属性；使用请求参数 appId。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.数据源列表查询
// 路由：POST /dst/database/list
// 业务说明：下拉列表同样读取 ES，避免每次打开表单扫描主表和全部动态属性；使用请求参数 appId。
import tenantEs
import tenantRuntime
import dataScope
import '@/common/configData' as configData

// 下拉列表同样读取 ES，避免每次打开表单扫描主表和全部动态属性。
var filters = [ { term: { assetType: 'db' } } ]
var excluded = [
    { wildcard: { dbName: 'validation_*' } },
    { wildcard: { database: 'validation_*' } }
]
// 解析请求体并为可选参数设置默认值。
if (body?.appId != null && (body.appId + '').trim() != '') {
    filters.add({ term: { appId: body.appId } })
}
// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var result = tenantEs.index(configData().es_dataassets).search({ size: 10000, query: { bool: { filter: filters, must_not: excluded } }, sort: [ { updatedTime: { order: 'desc', unmapped_type: 'keyword' } } ] })
var tenantId = tenantRuntime.id()
var candidateIds = []
for (hit in (result?.hits?.hits == null ? [] : result.hits.hits)) {
    var id = hit?._source?.tid || hit?._id
    if (id != null && !candidateIds.contains(id + '')) { candidateIds.add(id + '') }
}
var allowed = {}
if (candidateIds.size() > 0) {
    var sources = db.table('db_datasource_t').where().in('tid', candidateIds)
        .eq('tenant_id', tenantId).eq('is_del', 0).select()
    sources = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', sources)
    for (source in sources) {
        var sourceAppId = source.appId || source.app_id
        if (body?.appId == null || (body.appId + '').trim() == '' || (sourceAppId + '') == (body.appId + '')) {
            allowed[source.tid + ''] = source
        }
    }
}
var list = []
for (hit in (result?.hits?.hits == null ? [] : result.hits.hits)) {
    var id = hit?._source?.tid || hit?._id
    if (hit?._source != null && allowed[id + ''] != null) {
        list.add(hit._source)
    }
}
// 返回当前业务分支的标准结果。
return list
