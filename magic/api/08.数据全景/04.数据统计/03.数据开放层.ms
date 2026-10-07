{
  "properties": {},
  "id": "40479a2f64a74864996b994895453196",
  "script": null,
  "groupId": "df01177ff7604bbfb813175969adba1b",
  "name": "03.数据开放层",
  "createTime": 1782955343570,
  "updateTime": 1784437771688,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/data-share",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"shareLayerLeft\": [\n            10,\n            101,\n            109647\n        ],\n        \"shareLayerMiddle\": [\n            8,\n            2,\n            35\n        ],\n        \"shareLayerRight\": [\n            0,\n            2,\n            836\n        ]\n    }\n}",
  "description": "统计共享应用、目录及数据量，按大屏的左中右三个区域组装开放层指标。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.数据开放层
// 路由：GET /scene/data-count/data-share
// 业务说明：统计共享应用、目录及数据量，按大屏的左中右三个区域组装开放层指标。
// 数据开放层-大屏统计接口
// 路径: /api/shareLayer/dashboard
// 方法: POST
// 参数: 无
// 返回: { shareLayerLeft: [appCount, catalogCount, recordTotal], shareLayerMiddle: [fixed8, appCount, fieldTotal], shareLayerRight: [apiCount, catalogCount, recordTotal] }

import tenantEs;
import '@/common/configData' as configData;
import cn.hutool.core.util.StrUtil

var sumRecordScript = "if (doc['recordCount'].size() == 0) { return 0; } def v = doc['recordCount'].value; if (v != null && v != '') { return Double.parseDouble(v); } return 0;";
var sumFieldScript  = "if (doc['fieldCount'].size() == 0)  { return 0; } def v = doc['fieldCount'].value;  if (v != null && v != '') { return Double.parseDouble(v); } return 0;";

try {
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var esIndex = tenantEs.index(configData().es_dataassets);

    // ================================================================
    // Q1: 汇聚目录统计 — catalog 有 targetTableId（数据汇聚）
    // ================================================================
    var convergeDsl = {
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
        "_source": ["appId", "targetTableId"]
    };

    var convergeResult = esIndex.search(convergeDsl);
    var convergeAppCnt     = 0;
    var convergeCatalogCnt = convergeResult.hits.total.value;
    var convergeRecordSum  = 0;
    var convergeTableIds = [];
    var convergeAppIdMap = {};
    for (hit in convergeResult.hits.hits) {
        var src = hit._source;
        if (src.targetTableId) { convergeTableIds.push(src.targetTableId); }
        if (src.appId) { convergeAppIdMap[src.appId] = true; }
    }
    for (k in convergeAppIdMap.keySet()) { convergeAppCnt = convergeAppCnt + 1; }

    // Q1.1: 查询挂接表的recordCount总和
    if (convergeTableIds.size().asInt() > 0) {
        var convergeTableDsl = {
            "size": 0,
            "query": {
                "bool": {
                    "filter": [
                        { "term": { "assetType": "table" } },
                        { "terms": { "tid": convergeTableIds } }
                    ]
                }
            },
            "aggs": {
                "total_records": { "sum": { "script": { "source": sumRecordScript, "lang": "painless" } } }
            }
        };
        var convergeTableResult = esIndex.search(convergeTableDsl);
        convergeRecordSum = convergeTableResult.aggregations.total_records.value;
    }

    // ================================================================
    // Q2: 已发布目录 — catalog.publishStatus = "1"
    //     取出文档级数据供后续 Q3/Q4 使用
    // ================================================================
    var publishedDsl = {
        "size": 9000,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "assetType": "catalog" } },
                    { "term": { "publishStatus": 1 } }
                ]
            }
        },
        "_source": ["id", "appId", "targetTableId", "recordCount"]
    };

    var publishedResult = esIndex.search(publishedDsl);
    var publishedHits   = publishedResult.hits.hits;
    var publishedTotal  = publishedResult.hits.total.value;

    // 从已发布目录文档中提取:
    //   catalogIds       → 用于 Q4 查 API
    //   targetTableIds   → 用于 Q3 查 table 的 fieldCount
    //   appIdMap         → 计算 distinct appId 数
    //   publishedRecordTotal → 累计 recordCount
    var catalogIds     = [];
    var targetTableIds = [];
    var appIdMap       = {};
    var publishedRecordTotal = 0;

    for (doc in publishedHits) {
        var src = doc._source;
        catalogIds.add(doc._id);
        if (src.targetTableId) { targetTableIds.add(src.targetTableId); }
        if (src.appId)         { appIdMap[src.appId] = true; }
    }

    // distinct appId 计数
    var publishedAppCnt = 0;
    for (k in appIdMap.keySet()) { publishedAppCnt = publishedAppCnt + 1; }

    // ================================================================
    // Q3: 已发布目录挂接表的字段总数
    //     table._id in targetTableIds → sum(fieldCount)
    // ================================================================
    var publishedFieldTotal = 0;
    if (targetTableIds.size() > 0) {
        var tableDsl = {
            "size": 0,
            "query": {
                "bool": {
                    "filter": [
                        { "term": { "assetType": "table" } },
                        { "terms": { "_id": targetTableIds } }
                    ]
                }
            },
            "aggs": {
                "total_fields": { "sum": { "script": { "source": sumFieldScript, "lang": "painless" } } },
                "total_rows": { "sum": { "script": { "source": sumRecordScript, "lang": "painless" } } },
            }
        };
        var tableResult = esIndex.search(tableDsl);
        publishedFieldTotal = tableResult.aggregations.total_fields.value;
        publishedRecordTotal = tableResult.aggregations.total_rows.value;
    }

    // ================================================================
    // Q4: 已发布目录关联的 API 总数
    //     api.catalogId in catalogIds
    // ================================================================
    var publishedApiCnt = 0;
    if (catalogIds.size() > 0) {
        var apiDsl = {
            "size": 0,
            "query": {
                "bool": {
                    "filter": [
                        { "term": { "assetType": "api" } },
                        { "terms": { "catalogId": catalogIds } }
                    ]
                }
            }
        };
        var apiResult = esIndex.search(apiDsl);
        publishedApiCnt = apiResult.hits.total.value;
    }

    // ================================================================
    // 组装返回
    // ================================================================
    return {
        shareLayerLeft:   [ convergeAppCnt,       convergeCatalogCnt,  convergeRecordSum      ],
        shareLayerMiddle: [ 8,                    publishedAppCnt,     publishedFieldTotal     ],
        shareLayerRight:  [ publishedApiCnt,      publishedTotal,      publishedRecordTotal    ]
    };

} catch (e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '大屏接口查询失败: ' + e.message;
}
