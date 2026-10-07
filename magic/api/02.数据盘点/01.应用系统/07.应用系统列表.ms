{
  "properties": {},
  "id": "datapd_application_list_01",
  "script": null,
  "groupId": "7868bf218e904ebc999a94f041d91c36",
  "name": "07.应用系统列表",
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"keyword\": \"公安\",\n  \"limit\": 50\n}",
  "headers": [],
  "paths": [],
  "responseBody": "[]",
  "description": "应用系统下拉列表，默认返回最近 100 条，支持关键词检索；使用请求参数 keyword、limit。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.应用系统列表
// 路由：POST /dst/application/list
// 业务说明：应用系统下拉列表，默认返回最近 100 条，支持关键词检索；使用请求参数 keyword、limit。
// 应用系统列表：处理 POST /list 的核心查询和组装返回数据逻辑。
import tenantEs
import '@/common/configData' as configData

var filters = [{ term: { assetType: 'app' } }]
var excluded = [
  { wildcard: { appName: '未匹配业务系统' } },
  { wildcard: { applicationSystemName: '未匹配业务系统' } }
]
var keyword = body?.keyword == null ? '' : (body.keyword + '').trim()
if (keyword != '') {
  filters.add({
    bool: {
      should: [
        { wildcard: { appName: `*${keyword}*` } },
        { wildcard: { applicationSystemName: `*${keyword}*` } }
      ],
      minimum_should_match: 1
    }
  })
}
var size = body?.limit == null ? 100 : body.limit::int
if (size < 1) { size = 100 }
if (size > 100) { size = 100 }
var result = tenantEs.index(configData().es_dataassets).search({
  size: size,
  query: { bool: { filter: filters, must_not: excluded } },
  sort: [{ updatedTime: { order: 'desc', unmapped_type: 'keyword' } }]
})
var list = []
for (hit in (result?.hits?.hits == null ? [] : result.hits.hits)) {
  if (hit?._source != null) {
    list.add(hit._source)
  }
}
return list
