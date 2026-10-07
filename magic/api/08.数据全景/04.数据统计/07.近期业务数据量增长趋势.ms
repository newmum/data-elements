{
  "properties": {},
  "id": "4770f23e7671404ebe0b31f429336c84",
  "script": null,
  "groupId": "df01177ff7604bbfb813175969adba1b",
  "name": "07.近期业务数据量增长趋势",
  "createTime": 1782955343616,
  "updateTime": 1784437771708,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/recent",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"recent_week_hj\": [\n            {\n                \"timeValue\": \"04月13日\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"04月20日\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"04月27日\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"05月04日\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"05月11日\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"05月18日\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"05月25日\",\n                \"countNum\": 97068\n            },\n            {\n                \"timeValue\": \"06月01日\",\n                \"countNum\": 6257422\n            },\n            {\n                \"timeValue\": \"06月08日\",\n                \"countNum\": 295619\n            },\n            {\n                \"timeValue\": \"06月15日\",\n                \"countNum\": 6254\n            },\n            {\n                \"timeValue\": \"06月22日\",\n                \"countNum\": 1414\n            },\n            {\n                \"timeValue\": \"06月29日\",\n                \"countNum\": 6273128\n            }\n        ],\n        \"recent_month_hj\": [\n            {\n                \"timeValue\": \"2025-09\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"2025-10\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"2025-11\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"2025-12\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"2026-01\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"2026-02\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"2026-03\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"2026-04\",\n                \"countNum\": 0\n            },\n            {\n                \"timeValue\": \"2026-05\",\n                \"countNum\": 97068\n            },\n            {\n                \"timeValue\": \"2026-06\",\n                \"countNum\": 12833837\n            }\n        ]\n    }\n}",
  "description": "说明: 查询业务库下的表，按周/月统计recordCount总和（业务数据量增长趋势）。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.近期业务数据量增长趋势
// 路由：GET /scene/data-count/recent
// 业务说明：说明: 查询业务库下的表，按周/月统计recordCount总和（业务数据量增长趋势）。
// 近期业务数据量增长趋势接口
// 路径: /api/dataassets/recentBusiness
// 方法: POST
// 参数: 无
// 说明: 查询业务库下的表，按周/月统计recordCount总和（业务数据量增长趋势）

import tenantEs;
import '@/common/configData' as configData

try {
    // keyword字段求和脚本（recordCount是keyword类型，需转数值）
    var sumScript = "if (doc['recordCount'].size() > 0) { try { return Double.parseDouble(doc['recordCount'].value); } catch(Exception e) {} } return 0;";

    // ========== 查询业务库的dbId列表（table通过dbId关联） ==========
    var dbDsl = {
        "size": 1000,
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

    if (businessDbIds.size().asInt() == 0) {
        // 返回当前业务分支的标准结果。
        return {
            recent_week_hj: [],
            recent_month_hj: []
        };
    }

    // ========== 对table按regTime_date做周+月聚合（无时间范围过滤） ==========
    var dsl = {
        "size": 0,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "assetType": "table" } },
                    { "terms": { "dbId": businessDbIds } },
                    { "exists": { "field": "recordCount" } }
                ]
            }
        },
        "aggs": {
            "recent_week_hj": {
                "date_histogram": {
                    "field": "regTime_date",
                    "calendar_interval": "week",
                    "format": "MM月dd日",
                    "min_doc_count": 0,
                    "extended_bounds": {
                        "min": "now-11w/w",
                        "max": "now"
                    }
                },
                "aggs": {
                    "total_row_count": {
                        "sum": { "script": { "source": sumScript } }
                    }
                }
            },
            "recent_month_hj": {
                "date_histogram": {
                    "field": "regTime_date",
                    "calendar_interval": "month",
                    "format": "yyyy-MM",
                    "min_doc_count": 0,
                    "extended_bounds": {
                        "min": "now-9M/M",
                        "max": "now"
                    }
                },
                "aggs": {
                    "total_row_count": {
                        "sum": { "script": { "source": sumScript } }
                    }
                }
            }
        }
    };

    // 执行ES查询
    var result = tenantEs.index(configData().es_dataassets).search(dsl);

    // 格式化周统计结果
    var recentWeekHj = [];
    var weekBuckets = result.aggregations.recent_week_hj.buckets;
    for (bucket in weekBuckets) {
        recentWeekHj.push({
            timeValue: bucket.key_as_string,
            countNum: bucket.total_row_count.value
        });
    }

    // 格式化月统计结果
    var recentMonthHj = [];
    var monthBuckets = result.aggregations.recent_month_hj.buckets;
    for (bucket in monthBuckets) {
        recentMonthHj.push({
            timeValue: bucket.key_as_string,
            countNum: bucket.total_row_count.value
        });
    }

    // 返回当前业务分支的标准结果。
    return {
        recent_week_hj: recentWeekHj,
        recent_month_hj: recentMonthHj
    };

} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '查询近期业务数据量增长趋势失败: ' + e.message;
}
