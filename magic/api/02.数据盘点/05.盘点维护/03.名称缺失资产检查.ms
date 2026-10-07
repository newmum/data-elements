{
  "properties": {},
  "id": "b24c52a1ce7b4905b21aa6b3a2545b62",
  "script": null,
  "groupId": "datapd_maintenance_group_01",
  "name": "03.名称缺失资产检查",
  "createTime": 1782955343687,
  "updateTime": 1784437771717,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/maintenance/missing-names",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"pageNum\": 1,\n        \"pageSize\": 2000,\n        \"total\": 0,\n        \"list\": []\n    }\n}",
  "description": "只读检查：找出 app、db、table、catalog 中名称字段不存在或为空的 ES 文档，不做自动修复；使用请求参数 assetType。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.名称缺失资产检查
// 路由：POST /dst/maintenance/maintenance/missing-names
// 业务说明：只读检查：找出 app、db、table、catalog 中名称字段不存在或为空的 ES 文档，不做自动修复；使用请求参数 assetType。
import tenantEs
import '@/common/configData' as configData
import java.util.List

// 只读检查：找出 app、db、table、catalog 中名称字段不存在或为空的 ES 文档，不做自动修复。
var requested = body?.assetType
var types = requested == null ? ['app', 'db', 'table', 'catalog'] : (requested instanceof List ? requested : [requested])
var names = { app: 'appName', db: 'dbName', table: 'tableName', catalog: 'catalogName' }
var list = []
for (type in types) {
    var field = names[type]
    if (field == null) {
        continue
    }
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var result = tenantEs.index(configData().es_dataassets).search({
        size: 2000,
        query: { bool: { filter: [ { term: { assetType: type } } ], should: [ { bool: { must_not: [ { exists: { field: field } } ] } }, { term: { [field]: '' } } ], minimum_should_match: 1 } }
    })
    for (hit in (result?.hits?.hits == null ? [] : result.hits.hits)) {
        if (hit?._source != null) {
            list.add(hit._source)
        }
    }
}
// 返回当前业务分支的标准结果。
return { pageNum: 1, pageSize: 2000, total: list.size(), list: list }
