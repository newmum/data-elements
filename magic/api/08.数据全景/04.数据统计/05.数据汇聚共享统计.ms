{
  "properties": {},
  "id": "3e5a46b0a5de4ad9be6be4bc1eadbb91",
  "script": null,
  "groupId": "df01177ff7604bbfb813175969adba1b",
  "name": "05.数据汇聚共享统计",
  "createTime": 1782955343594,
  "updateTime": 1784437771703,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/converge",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"cityAppHjCount\": 5,\n        \"cityDbHjCount\": 86,\n        \"cityCategoryHjCount\": 198,\n        \"countyDbHjCount\": 1,\n        \"countyAppHjCount\": 0,\n        \"countyCategoryHjCount\": 4\n    }\n}",
  "description": "说明: 统计数据汇聚和共享情况，包括业务系统、数据目录、数据资源三个维度。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": {
    "name": "",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "code",
        "value": "0",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "msg",
        "value": "success",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "success",
        "value": "true",
        "description": "",
        "required": false,
        "dataType": "Boolean",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "data",
        "value": "",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": [
          {
            "name": "cityAppHjCount",
            "value": "2",
            "description": "",
            "required": false,
            "dataType": "Integer",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "cityDbHjCount",
            "value": "83",
            "description": "",
            "required": false,
            "dataType": "Integer",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "cityCategoryHjCount",
            "value": "5645",
            "description": "",
            "required": false,
            "dataType": "Integer",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "countyDbHjCount",
            "value": "0",
            "description": "",
            "required": false,
            "dataType": "Integer",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "countyAppHjCount",
            "value": "0",
            "description": "",
            "required": false,
            "dataType": "Integer",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "countyCategoryHjCount",
            "value": "0",
            "description": "",
            "required": false,
            "dataType": "Integer",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          }
        ]
      }
    ]
  }
}
================================
// 接口：05.数据汇聚共享统计
// 路由：GET /scene/data-count/converge
// 业务说明：说明: 统计数据汇聚和共享情况，包括业务系统、数据目录、数据资源三个维度。
// 数据汇聚共享情况接口
// 路径: /dataassets/scence/aggregationShare/stat
// 方法: get
// 参数: 无
// 说明: 统计数据汇聚和共享情况，包括业务系统、数据目录、数据资源三个维度
//       数据汇聚(city前缀)：从dataassets索引统计资产挂载情况
//       数据共享(county前缀)：从apply索引统计已通过申请单，
//          countyDbHjCount=申请单中通过的目录数(去重catalogId)、
//          countyAppHjCount=通过的目录中有挂载appId的数量、
//          countyCategoryHjCount=目录下挂载的table+api+file资源总数

import tenantEs;
import '@/common/configData' as configData;

try {

    // ============================================================
    // Part 1: 数据汇聚统计 (city前缀)
    // 从 sea_fishery_elements_dataassets 索引查询
    // ============================================================

    var hjDsl = {
        "size": 0,
        "aggs": {
            // 有挂载表的业务系统：通过table的orgId聚合得到去重后的app数量
            // table.orgId 对应所属app的id，每个唯一的orgId代表一个"有挂载表的业务系统"
            "table_apps": {
                "filter": { "term": { "assetType": "table" } },
                "aggs": {
                    "unique_apps": {
                        "terms": { "field": "orgId", "size": 100000 }
                    }
                }
            },
            // 有挂载表的目录数：catalog有targetTableId的数量
            "catalog_with_target": {
                "filter": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "catalog" } },
                            { "exists": { "field": "targetTableId" } }
                        ]
                    }
                }
            },
            // 数据资源总数：table + api + file 三种类型文档数
            "api_with_catalog": {
                "filter": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "api" } },
                            { "exists": { "field": "catalogId" } }
                        ]
                    }
                }
            },
            "file_with_catalog": {
                "filter": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "file" } },
                            { "exists": { "field": "catalogId" } }
                        ]
                    }
                }
            },
            "table_with_catalog": {
                "filter": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "table" } },
                            { "exists": { "field": "catalogId" } }
                        ]
                    }
                }
            }
        }
    };

    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var hjResult = tenantEs.index(configData().es_dataassets).search(hjDsl);

    var cityAppHjCount     = hjResult.aggregations.table_apps.unique_apps.buckets.size();
    var cityDbHjCount      = hjResult.aggregations.catalog_with_target.doc_count;
    var apiHjCount     = hjResult.aggregations.api_with_catalog.doc_count;
    var fileHjCount    = hjResult.aggregations.file_with_catalog.doc_count;
    var tableHjCount = hjResult.aggregations.table_with_catalog.doc_count;
    var cityCategoryHjCount = apiHjCount + fileHjCount + tableHjCount;

    // ============================================================
    // Part 2: 数据共享统计 (county前缀)
    // 从 sea_fishery_elements_dataassets_apply 索引查询已通过的申请单
    // ============================================================

    // 2.1 查询已通过的申请单（flowStatus=2），收集去重后的catalogId
    var applyDsl = {
        "size": 10000,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "flowStatus": 2 } }
                ]
            }
        },
        "_source": ["catalogIds"]
    };

    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var applyResult = tenantEs.index(configData().es_dataassets_apply).search(applyDsl);

    // 收集所有唯一的catalogId
    var allCatalogIds = {};

    for (hit in applyResult.hits.hits) {
        var src = hit._source;
        var catalogIds = src.catalogIds;
        if (catalogIds) {
            var idList = catalogIds.split(',');
            for (cid in idList) {
                var trimmedId = cid.trim();
                if (trimmedId) {
                    allCatalogIds[trimmedId] = true;
                }
            }
        }
    }

    // countyDbHjCount: 申请单中通过的目录数 = 去重后的catalogId数量
    var countyDbHjCount = allCatalogIds.keySet().size();

    // 2.2 分批查询共享目录，统计：
    //   countyAppHjCount = 通过的目录中有挂载appId的数量
    //   countyCategoryHjCount = 目录下挂载的table+api+file资源总数
    var countyAppHjCount = 0;
    var countyCategoryHjCount = 0;

    if (countyDbHjCount > 0) {
        var catalogIdList = [];
        for (cid in allCatalogIds.keySet()) {
            catalogIdList.push(cid);
        }

        var batchSize = 500;
        var batchIndex = 0;
        var totalBatches = (catalogIdList.size() + batchSize - 1) / batchSize;

        while (batchIndex < totalBatches) {
            var start = batchIndex * batchSize;
            var end = start + batchSize;
            if (end > catalogIdList.size()) { end = catalogIdList.size(); }
            var batchIds = catalogIdList.subList(start, end);

            // 查询有挂载appId的目录数
            var catalogDsl = {
                "size": 0,
                "query": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "catalog" } },
                            { "terms": { "id": batchIds } },
                            { "exists": { "field": "appId" } }
                        ]
                    }
                }
            };

            // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
            var catResult = tenantEs.index(configData().es_dataassets).search(catalogDsl);
            if (catResult.hits && catResult.hits.total) {
                var catTotal = catResult.hits.total.value != null ? catResult.hits.total.value : catResult.hits.total;
                countyAppHjCount = countyAppHjCount + catTotal;
            }

            // 查询目录下挂载的table+api+file资源总数
            var resourceDsl = {
                "size": 0,
                "query": {
                    "bool": {
                        "filter": [
                            { "terms": { "assetType": ["table", "api", "file"] } },
                            { "terms": { "catalogId": batchIds } }
                        ]
                    }
                }
            };

            // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
            var resResult = tenantEs.index(configData().es_dataassets).search(resourceDsl);
            if (resResult.hits && resResult.hits.total) {
                var resTotal = resResult.hits.total.value != null ? resResult.hits.total.value : resResult.hits.total;
                countyCategoryHjCount = countyCategoryHjCount + resTotal;
            }

            batchIndex = batchIndex + 1;
        }
    }

    // ============================================================
    // 返回结果
    // ============================================================
    return {
        cityAppHjCount: cityAppHjCount,
        cityDbHjCount: cityDbHjCount,
        cityCategoryHjCount: cityCategoryHjCount,
        countyDbHjCount: countyDbHjCount,
        countyAppHjCount: countyAppHjCount,
        countyCategoryHjCount: countyCategoryHjCount
    };

} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '查询数据汇聚共享情况失败: ' + e.message;
}
