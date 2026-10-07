{
  "properties": {},
  "id": "9ca047fe684845d59009d947fa3d0fe6",
  "script": null,
  "groupId": "df01177ff7604bbfb813175969adba1b",
  "name": "08.顶部数据统计",
  "createTime": 1782955343625,
  "updateTime": 1784437771711,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/stats",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"dataTotal\": 6653042,\n        \"dataTodayTotal\": 0,\n        \"dataConverge\": 109647,\n        \"dataShare\": 836\n    }\n}",
  "description": "汇总业务库、登记表、今日新增、数据汇聚及共享申请等指标，返回资产全景顶部统计。",
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
            "name": "dataTotal",
            "value": "6886306",
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
            "name": "dataTodayTotal",
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
// 接口：08.顶部数据统计
// 路由：GET /scene/data-count/stats
// 业务说明：汇总业务库、登记表、今日新增、数据汇聚及共享申请等指标，返回资产全景顶部统计。
// 数据统计概览接口
// 路径: /api/dataassets/stats
// 业务库: 已注册assetStatus=2 nodeId=业务库 的db
// 数据总量 = 业务库下 table 的 recordCount 总和
// 今日新增 = 业务库下 注册时间regTime 为今日的 table 的 recordCount 总和
// 数据汇聚 = 有 targetTableId 的 catalog 挂接表的 recordCount 总和
// 数据共享 = 已审核的目录订阅申请单中订阅的 catalog 挂接表的 recordCount 总和
import tenantEs;
import '@/common/configData' as configData
import cn.hutool.core.util.StrUtil

// ========== 查询业务库的dbId列表（table通过dbId关联） ==========
var dbDsl = {
    "size": 5000,
    "query": {
        "bool": {
            "must": [
                { "term": { "assetType": "db" } },
                { "term": { "assetStatus": 2 } },
                { "term": { "nodeId": "业务库" } }
            ]
        }
    },
    "_source": false
};
// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var dbResult = tenantEs.index(configData().es_dataassets).search(dbDsl);
var businessDbIds = [];
dbResult.hits.hits.forEach((hit) => {
    businessDbIds.push(hit._id);
});

var dsl = {
  "size": 0,
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "assetType": "table"
          }
        },
        {
          "exists": {
            "field": "recordCount"
          }
        }
      ],
      "filter": [
        { "terms": { "dbId": businessDbIds } }
      ]
    }
  },
  "aggs": {
    "total_row_count": {
      "sum": {
        "script": {
          "source": "def v = doc['recordCount'].value; if (v != null && v != '') { return Double.parseDouble(v); } return 0;"
        }
      }
    }
  }
}

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var dataTotal = tenantEs.index(configData().es_dataassets).search(dsl)?.aggregations?.total_row_count?.value;
if (!dataTotal) { dataTotal = 0; }

import cn.hutool.core.date.DateUtil
var today = DateUtil.format(new Date(), "yyyy-MM-dd")

dsl = {
  "size": 0,
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "assetType": "table"
          }
        },
        {
          "range": {
            "regTime_date": {
              "gte": "now/d",
              "lte": "now"
            }
          }
        }
      ],
      "filter": [
        { "terms": { "dbId": businessDbIds } }
      ]
    }
  },
  "aggs": {
    "total_row_count": {
      "sum": {
        "script": {
          "source": "if (doc['recordCount'].size() == 0) { return 0; } def v = doc['recordCount'].value; if (v != null && v != '') { return Double.parseDouble(v); } return 0;"
        }
      }
    }
  }
}

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var dataTodayTotal = tenantEs.index(configData().es_dataassets).search(dsl)?.aggregations?.total_row_count?.value;
if (!dataTodayTotal) { dataTodayTotal = 0; }

// ========== 数据汇聚 = 所有 catalog 挂接表的 recordCount 总和 ==========
// 1. 查有 targetTableId 的 catalog，收集 tableId
var catalogDsl = {
    "size": 9000,
    "query": {
        "bool": {
            "filter": [
                { "term": { "assetType": "catalog" } },
                // { "term": { "assetStatus": 2 } },
                { "exists": { "field": "targetTableId" } }
            ]
        }
    },
    "_source": ["targetTableId"]
}
// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var catalogResult = tenantEs.index(configData().es_dataassets).search(catalogDsl);
var convergeTableIds = [];
for (hit in catalogResult.hits.hits) {
    var src = hit._source;
    if (src.targetTableId) {
        convergeTableIds.push(src.targetTableId);
    }
}

// 2. 查这些表的 recordCount 总和
var dataConverge = 0;
if (convergeTableIds.size().asInt() > 0) {
    var convergeDsl = {
        "size": 0,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "assetType": "table" } },
                    { "terms": { "_id": convergeTableIds } }
                ]
            }
        },
        "aggs": {
            "total_row_count": {
                "sum": {
                    "script": {
                        "source": "if (doc['recordCount'].size() == 0) { return 0; } def v = doc['recordCount'].value; if (v != null && v != '') { return Double.parseDouble(v); } return 0;"
                    }
                }
            }
        }
    }
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    dataConverge = tenantEs.index(configData().es_dataassets).search(convergeDsl)?.aggregations?.total_row_count?.value;
    if (!dataConverge) { dataConverge = 0; }
}

// ========== 数据共享 = 目录订阅申请单中订阅的目录挂接表的 recordCount 总和 ==========
// 1. 查已审核的目录订阅申请单，收集 catalogIds
var applyDsl = {
    "size": 9000,
    "query": {
        "bool": {
            "filter": [
                // { "term": { "assetType": "apply" } },
                { "term": { "flowStatus": 2 } },
                { "term": { "type": "catalogSubscribe" } }
            ]
        }
    },
    "_source": ["catalogIds"]
}
// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var applyResult = tenantEs.index(configData().es_dataassets_apply).search(applyDsl);
// return applyResult
var sharedCatalogIds = [];
for (hit in applyResult.hits.hits) {
    var src = hit._source;
    if (src.catalogIds) {
        for (cid in StrUtil.split(src.catalogIds, ',')) {
            sharedCatalogIds.push(cid);
        }
    }
}

// 2. 查这些 catalog 的 targetTableId
var sharedTableIds = [];
if (sharedCatalogIds.size().asInt() > 0) {
    var sharedCatalogDsl = {
        "size": 1000,
        "query": {
            "bool": {
                "filter": [
                    { "terms": { "_id": sharedCatalogIds } }
                ]
            }
        },
        "_source": ["targetTableId"]
    }
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var sharedCatalogResult = tenantEs.index(configData().es_dataassets).search(sharedCatalogDsl);
    for (hit in sharedCatalogResult.hits.hits) {
        var src = hit._source;
        if (src.targetTableId) {
            sharedTableIds.push(src.targetTableId);
        }
    }
}

// 3. 查这些表的 recordCount 总和
var dataShare = 0;
if (sharedTableIds.size().asInt() > 0) {
    var shareDsl = {
        "size": 0,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "assetType": "table" } },
                    { "terms": { "_id": sharedTableIds } }
                ]
            }
        },
        "aggs": {
            "total_row_count": {
                "sum": {
                    "script": {
                        "source": "if (doc['recordCount'].size() == 0) { return 0; } def v = doc['recordCount'].value; if (v != null && v != '') { return Double.parseDouble(v); } return 0;"
                    }
                }
            }
        }
    }
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    dataShare = tenantEs.index(configData().es_dataassets).search(shareDsl)?.aggregations?.total_row_count?.value;
    if (!dataShare) { dataShare = 0; }
}

// 返回当前业务分支的标准结果。
return {
    dataTotal,
    dataTodayTotal,
    dataConverge,
    dataShare
}
