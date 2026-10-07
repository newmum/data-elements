{
  "properties": {},
  "id": "6f3c094d24d8441eb97de48fd0adcb18",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "07.DST表统计",
  "createTime": 1782955345131,
  "updateTime": 1784454007655,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dstTable",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "统计nodeId 为业务库的表，app org db 三个维度统计。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.DST表统计
// 路由：POST /dwm/center/dstTable
// 业务说明：统计nodeId 为业务库的表，app org db 三个维度统计。
// 统计nodeId 为业务库的表，app org db 三个维度统计
// table没有orgId，需要通过dbId关联db获取orgId

import tenantEs;
import '@/common/configData' as configData;

// 第一步：查询所有在用的业务库，获取dbId、appId、orgId的映射关系
var dsl_db = {
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
  "_source": ["id", "appId", "orgId"]  // 获取appId和orgId
};

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var dbResult = tenantEs.index(configData().es_dataassets).search(dsl_db);

// 构建映射关系
var dbIdToAppId = {};
var dbIdToOrgId = {};
var dbIds = [];
dbResult.hits.hits.forEach((hit) => {
  var dbId = hit._id;
  var appId = hit._source.appId;
  var orgId = hit._source.orgId;
  dbIdToAppId[dbId] = appId;
  dbIdToOrgId[dbId] = orgId;
  dbIds.push(dbId);
});

// 第二步：查询属于业务库的table，按dbId聚合（table没有orgId）
var dsl_table = {
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "assetType": "table" } },
        { "terms": { "dbId": dbIds } }
      ]
    }
  },
  "aggs": {
    "by_dbId": {
      "terms": {
        "field": "dbId",
        "size": 10000
      }
    }
  }
};

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var tableResult = tenantEs.index(configData().es_dataassets).search(dsl_table);

// 第三步：通过dbId关联，计算三个维度的统计
var orgIdStats = {};
var dbIdStats = {};
var appIdStats = {};

tableResult.aggregations.by_dbId.buckets.forEach((bucket) => {
  var dbId = bucket.key;
  var count = bucket.doc_count;

  // dbId维度：直接统计
  dbIdStats[dbId] = count;

  // orgId维度：通过dbIdToOrgId映射
  var orgId = dbIdToOrgId[dbId];
  if (orgId) {
    if (!orgIdStats[orgId]) {
      orgIdStats[orgId] = 0;
    }
    orgIdStats[orgId] = orgIdStats[orgId] + count;
  }

  // appId维度：通过dbIdToAppId映射
  var appId = dbIdToAppId[dbId];
  if (appId) {
    if (!appIdStats[appId]) {
      appIdStats[appId] = 0;
    }
    appIdStats[appId] = appIdStats[appId] + count;
  }
});

// 返回结果
var result = {
  orgId: orgIdStats,
  dbId: dbIdStats,
  appId: appIdStats
};

// 返回当前业务分支的标准结果。
return result;
