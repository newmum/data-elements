{
  "properties": {},
  "id": "3cd606bbc1a247c19577f17f898ef3ad",
  "script": null,
  "groupId": "df01177ff7604bbfb813175969adba1b",
  "name": "06.数源层",
  "createTime": 1782955343606,
  "updateTime": 1784437771706,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/data-dst",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"collectLayerLeft\": [\n            4,\n            153,\n            517\n        ],\n        \"collectLayerMiddle\": [\n            12,\n            5466,\n            12402088\n        ],\n        \"collectLayerRight\": [\n            30,\n            13,\n            31\n        ]\n    }\n}",
  "description": "按局内、局外和国家部委来源汇总机构、数据表及记录数，返回数源层大屏指标。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.数源层
// 路由：GET /scene/data-count/data-dst
// 业务说明：按局内、局外和国家部委来源汇总机构、数据表及记录数，返回数源层大屏指标。
// dst层统计接口
// 路径: /api/dataassets/dstLayer
// 方法: POST
// 参数: 无
// 返回: { collectLayerLeft: [国家部委数量, 国家部委数据表数量, 国家部委数据总量], collectLayerMiddle: [局内单位数量, 局内数据表数量, 局内数据总数量], collectLayerRight: [横向厅局单位数量, 横向厅局数据表数量, 横向厅局数据总数量] }

import tenantEs;
import '@/common/configData' as configData;

try {
    // ========== 配置 ==========
    // 国家部委根ID（左侧）
    var leftRootOrgId = '2067073520506609664';
    // 局内共享部门根ID（中间）
    var middleRootOrgIds = ['2057658733680787457', '2058490433772060673'];
    // 横向厅局根ID（右侧）
    var rightRootOrgId = '2067073568296509440';

    // keyword字段求和脚本
    var sumRecordScript = "if (doc['recordCount'].size() == 0) { return 0; } def v = doc['recordCount'].value; if (v != null && v != '') { return Double.parseDouble(v); } return 0;";

    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var esIndex = tenantEs.index(configData().es_dataassets);

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
    var dbResult = esIndex.search(dbDsl);
    var businessDbIds = [];
    dbResult.hits.hits.forEach((hit) => {
        businessDbIds.add(hit._id);
    });

    // ========== 递归获取所有子机构ID ==========
    var getAllChildOrgIds = (rootId) => {
        var result = [];
        result.add(rootId);
        
        // 从 MySQL 业务表读取当前有效数据。
        var children = db.table("rm_org_t")
            .where().eq("parent_id", rootId).eq("nature", 2).eq("deleted", 0)
            .select();
        
        for (child in children) {
            var subChildIds = getAllChildOrgIds(child.id);
            for (subId in subChildIds) {
                result.add(subId);
            }
        }
        
        // 返回当前业务分支的标准结果。
        return result;
    };

    // ========== 1. collectLayerLeft: 国家部委(左) ==========
    // 1.1 获取所有子机构ID
    var leftOrgIds = getAllChildOrgIds(leftRootOrgId);
    // 国家部委数量 = 子机构数量（排除根ID本身）
    var leftOrgCount = leftOrgIds.size().asInt() - 1;

    // 1.2 国家部委数据表数量: ES中orgId在leftOrgIds中, assetType=table, dbId在业务库中的数量
    var leftTableDsl = {
        "size": 0,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "assetType": "table" } },
                    { "terms": { "dbId": businessDbIds } },
                    { "terms": { "orgId": leftOrgIds } }
                ]
            }
        }
    };
    var leftTableResult = esIndex.search(leftTableDsl);
    var leftTableCount = leftTableResult.hits.total.value;

    // 1.3 国家部委数据总量: ES中orgId在leftOrgIds中, assetType=table, dbId在业务库中的recordCount总和
    var leftRecordDsl = {
        "size": 0,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "assetType": "table" } },
                    { "terms": { "dbId": businessDbIds } },
                    { "terms": { "orgId": leftOrgIds } }
                ]
            }
        },
        "aggs": {
            "total_records": { "sum": { "script": { "source": sumRecordScript, "lang": "painless" } } }
        }
    };
    var leftRecordResult = esIndex.search(leftRecordDsl);
    var leftRecordTotal = leftRecordResult.aggregations.total_records.value;

    // ========== 2. collectLayerMiddle: 局内单位(中) ==========
    // 2.1 获取所有子机构ID
    var middleOrgIds = [];
    for (rootId in middleRootOrgIds) {
        var childIds = getAllChildOrgIds(rootId);
        for (id in childIds) {
            middleOrgIds.add(id);
        }
    }
    // 局内单位数量 = 子机构数量（排除根ID本身）
    var middleOrgCount = middleOrgIds.size().asInt() - middleRootOrgIds.size().asInt();

    // 2.2 局内数据表数量: ES中orgId在middleOrgIds中, assetType=table, dbId在业务库中的数量
    var middleTableDsl = {
        "size": 0,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "assetType": "table" } },
                    { "terms": { "dbId": businessDbIds } },
                    { "terms": { "orgId": middleOrgIds } }
                ]
            }
        }
    };
    var middleTableResult = esIndex.search(middleTableDsl);
    var middleTableCount = middleTableResult.hits.total.value;

    // 2.3 局内数据总数量: ES中orgId在middleOrgIds中, assetType=table, dbId在业务库中的recordCount总和
    var middleRecordDsl = {
        "size": 0,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "assetType": "table" } },
                    { "terms": { "dbId": businessDbIds } },
                    { "terms": { "orgId": middleOrgIds } }
                ]
            }
        },
        "aggs": {
            "total_records": { "sum": { "script": { "source": sumRecordScript, "lang": "painless" } } }
        }
    };
    var middleRecordResult = esIndex.search(middleRecordDsl);
    var middleRecordTotal = middleRecordResult.aggregations.total_records.value;

    // ========== 3. collectLayerRight: 横向厅局(右) ==========
    // 3.1 获取所有子机构ID
    var rightOrgIds = getAllChildOrgIds(rightRootOrgId);
    // 横向厅局单位数量 = 子机构数量（排除根ID本身）
    var rightOrgCount = rightOrgIds.size().asInt() - 1;

    // 3.2 横向厅局数据表数量: ES中orgId在rightOrgIds中, assetType=table, dbId在业务库中的数量
    var rightTableDsl = {
        "size": 0,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "assetType": "table" } },
                    { "terms": { "dbId": businessDbIds } },
                    { "terms": { "orgId": rightOrgIds } }
                ]
            }
        }
    };
    var rightTableResult = esIndex.search(rightTableDsl);
    var rightTableCount = rightTableResult.hits.total.value;

    // 3.3 横向厅局数据总数量: ES中orgId在rightOrgIds中, assetType=table, dbId在业务库中的recordCount总和
    var rightRecordDsl = {
        "size": 0,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "assetType": "table" } },
                    { "terms": { "dbId": businessDbIds } },
                    { "terms": { "orgId": rightOrgIds } }
                ]
            }
        },
        "aggs": {
            "total_records": { "sum": { "script": { "source": sumRecordScript, "lang": "painless" } } }
        }
    };
    var rightRecordResult = esIndex.search(rightRecordDsl);
    var rightRecordTotal = rightRecordResult.aggregations.total_records.value;

    // ========== 返回结果 ==========
    return {
        // [国家部委数量, 国家部委数据表数量, 国家部委数据总量]
        collectLayerLeft: [leftOrgCount, leftTableCount, leftRecordTotal],
        // [局内单位数量, 局内数据表数量, 局内数据总数量]
        collectLayerMiddle: [middleOrgCount, middleTableCount, middleRecordTotal],
        // [横向厅局单位数量, 横向厅局数据表数量, 横向厅局数据总数量]
        collectLayerRight: [rightOrgCount, rightTableCount, rightRecordTotal]
    };

} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '查询数据汇聚层统计失败: ' + e.message;
}
