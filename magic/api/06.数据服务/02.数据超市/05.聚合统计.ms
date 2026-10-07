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
  "requestBody": "{\n    \"query\": [\n        {\n            \"term\": {\n                \"assetType\": \"table\"\n            }\n        }\n    ],\n    \"aggs\": {\n        \"orgPath\": {\n            \"terms\": {\n                \"field\": \"orgPath\",\n                \"size\": 10000\n            }\n        }\n    },\n    \"indexType\": \"app\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 100120,\n    \"msg\": \"user_no_login\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "根据请求中的检索条件、索引类型和聚合定义，查询当前租户的检索索引并返回聚合桶。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.聚合统计
// 路由：POST /dws/market/count
// 业务说明：根据请求中的检索条件、索引类型和聚合定义，查询当前租户的检索索引并返回聚合桶。
import tenantEs;
import log;
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import '@/common/configData' as configData

// 解析请求体并为可选参数设置默认值。
var {query, indexType, aggs } = body;
indexType = indexType || configData().es_dataassets


// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var existInxex = esCommonService.indexExists("dataassets");
if(!existInxex){
    // 返回当前业务分支的标准结果。
    return {}
}

var dsl = {
    query: {
        bool: {
            must: query
        }
    },
};


if (aggs !== null){
    dsl["aggs"] = aggs
} 
// 根据`DSL`语句搜索
var searchlist = tenantEs.index(indexType).search(dsl);

var data =  searchlist.aggregations
var result = {}
data.each((key, val) => {
    result[key] = {}
        val.buckets?.each(item => {
            result[key][item.key] = item.doc_count
        })
})

// 返回当前业务分支的标准结果。
return result
