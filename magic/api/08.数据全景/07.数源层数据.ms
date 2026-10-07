{
  "properties": {},
  "id": "df78370537ce49bb8b0638b25f1d568c",
  "script": null,
  "groupId": "5ac60a54d85b4682ab46fbb8def36ea2",
  "name": "07.数源层数据",
  "createTime": 1782955343430,
  "updateTime": 1784437771633,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/collect/app",
  "method": "GET",
  "parameters": [
    {
      "name": "pageNo",
      "value": "1",
      "description": "真实数据示例参数：pageNo",
      "required": false,
      "dataType": "Integer",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "pageSize",
      "value": "20",
      "description": "真实数据示例参数：pageSize",
      "required": false,
      "dataType": "Integer",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "queryType",
      "value": "2",
      "description": "真实数据示例参数：queryType",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "keyword",
      "value": "测试",
      "description": "真实数据示例参数：keyword",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n    \"queryType\": \"app\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"total\": 12,\n        \"list\": [\n            {\n                \"orgId\": \"2058490591645663234\",\n                \"orgName\": \"办公室\",\n                \"tagCount\": 0,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 1,\n                \"categoryCount\": 2,\n                \"updateTime\": \"2026-06-24 14:59:57\"\n            },\n            {\n                \"orgId\": \"2058552064266862593\",\n                \"orgName\": \"海洋经济处\",\n                \"tagCount\": 0,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 2,\n                \"categoryCount\": 2,\n                \"updateTime\": \"2026-06-29 17:03:06\"\n            },\n            {\n                \"orgId\": \"2058552152884117505\",\n                \"orgName\": \"防灾减灾处\",\n                \"tagCount\": 0,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 0,\n                \"categoryCount\": 0,\n                \"updateTime\": null\n            },\n            {\n                \"orgId\": \"2058552216662704130\",\n                \"orgName\": \"渔业与质量监督处\",\n                \"tagCount\": 0,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 1,\n                \"categoryCount\": 1,\n                \"updateTime\": \"2026-06-29 17:46:19\"\n            },\n            {\n                \"orgId\": \"2058552271218016258\",\n                \"orgName\": \"渔船渔港管理处\",\n                \"tagCount\": 0,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 0,\n                \"categoryCount\": 0,\n                \"updateTime\": null\n            },\n            {\n                \"orgId\": \"2058552438872735746\",\n                \"orgName\": \"科技与对外合作处\",\n                \"tagCount\": 0,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 0,\n                \"categoryCount\": 0,\n                \"updateTime\": null\n            },\n            {\n                \"orgId\": \"2058552749586776065\",\n                \"orgName\": \"福建省渔业资源监测中心\",\n                \"tagCount\": 0,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 0,\n                \"categoryCount\": 0,\n                \"updateTime\": null\n            },\n            {\n                \"orgId\": \"2058552847548940290\",\n                \"orgName\": \"福建省海洋预报台\",\n                \"tagCount\": 272,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 0,\n                \"categoryCount\": 0,\n                \"updateTime\": \"2026-06-04 20:06:45\"\n            },\n            {\n                \"orgId\": \"2058552907896586242\",\n                \"orgName\": \"福建省海洋经济运行监测与评估中心\",\n                \"tagCount\": 0,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 0,\n                \"categoryCount\": 0,\n                \"updateTime\": null\n            },\n            {\n                \"orgId\": \"2067450426957762560\",\n                \"orgName\": \"海洋与渔业数据中心\",\n                \"tagCount\": 0,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 0,\n                \"categoryCount\": 0,\n                \"updateTime\": null\n            },\n            {\n                \"orgId\": \"2058493826078998529\",\n                \"orgName\": \"福建省渔业减灾中心\",\n                \"tagCount\": 66370,\n                \"dataHjCount\": 25148984,\n                \"categoryHjCount\": 84,\n                \"categoryCount\": 149,\n                \"updateTime\": \"2026-06-29 17:08:14\"\n            },\n            {\n                \"orgId\": \"2069338822711939072\",\n                \"orgName\": \"福建省水产研究所\",\n                \"tagCount\": 0,\n                \"dataHjCount\": 0,\n                \"categoryHjCount\": 0,\n                \"categoryCount\": 0,\n                \"updateTime\": null\n            }\n        ]\n    }\n}",
  "description": "按 queryType 区分局内与局外机构，支持分页和关键词筛选，返回数源层的机构及数据资源统计。",
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
            "name": "total",
            "value": "34",
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
            "name": "list",
            "value": "",
            "description": "",
            "required": false,
            "dataType": "Array",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": [
              {
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
                    "name": "orgId",
                    "value": "2067074670135975936",
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
                    "name": "orgName",
                    "value": "东海航海保障中心福州航标处",
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
                    "name": "tagCount",
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
                    "name": "dataHjCount",
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
                    "name": "categoryHjCount",
                    "value": "1",
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
                    "name": "categoryCount",
                    "value": "1",
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
                    "name": "updateTime",
                    "value": "2026-06-25 14:07:10",
                    "description": "",
                    "required": false,
                    "dataType": "String",
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
        ]
      }
    ]
  }
}
================================
// 接口：07.数源层数据
// 路由：GET /scene/collect/app
// 业务说明：按 queryType 区分局内与局外机构，支持分页和关键词筛选，返回数源层的机构及数据资源统计。
// 数源层数据接口
// 路径: /scene/collect/app
// 方法: GET
// 参数:
//   pageNo    - 页码（默认1）
//   pageSize  - 每页条数（默认10）
//   keyword   - 机构名称关键词搜索（可选）
//   queryType - 0=局内机构（行政处室/事业单位下），1=局外机构（横向厅局/国家部委下）
// 返回:
//   total/list - 根据queryType返回对应分组的总数和列表
//   数据量(dataHjCount): 直接查table，assetType=table + orgId匹配 + dbId属于nodeId="业务库"的库，sum recordCount
//   标签数(tagCount): 同上路径，sum fieldCount
//   数据目录(categoryHjCount): 机构下有挂载表的 catalog 数量(catalog类型且存在targetTableId字段)
//   数据资源(categoryCount): 机构下所有 catalog(asset_type='catalog')的总数
//   数据更新时间(updateTime): 机构下所有资产(表+目录)中最新的 updated_time

import tenantEs;
import '@/common/configData' as configData

// ==================== 入参 ====================
var pNo = pageNo != null ? pageNo : 1
var pSize = pageSize != null ? pageSize : 10
var kw = keyword != null && keyword != '' ? keyword : null
// queryType: 优先从URL参数读取，其次从body读取（import调用时走body）
var qt = queryType != null ? queryType : (body != null && body.queryType != null ? body.queryType : 0)

// ==================== 分类父节点ID（固定值） ====================
var outerParentIds = ['2067073568296509440', '2067073520506609664']
var innerParentIds = ['2057658733680787457', '2058490433772060673']

var targetParentIds = (qt + '') == '1' ? outerParentIds : innerParentIds

// ==================== Step 1: 递归获取所有子孙机构（不含根ID，nature=2） ====================
var getAllDescendantOrgs = (rootId) => {
    var result = []
    // 从 MySQL 业务表读取当前有效数据。
    var children = db.table("rm_org_t")
        .where().eq("parent_id", rootId).eq("nature", 2).eq("deleted", 0)
        .orderBy("sort_num", "asc")
        .select()
    for (child in children) {
        result.add(child)
        var subChildren = getAllDescendantOrgs(child.id)
        for (subChild in subChildren) {
            result.add(subChild)
        }
    }
    // 返回当前业务分支的标准结果。
    return result
}

var allOrgs = []
for (rootId in targetParentIds) {
    var descendants = getAllDescendantOrgs(rootId)
    for (org in descendants) {
        allOrgs.add(org)
    }
}

// 关键词过滤
if (kw != null) {
    var filteredOrgs = []
    for (org in allOrgs) {
        var orgName = org.name != null ? (org.name + '') : ''
        if (orgName.indexOf(kw) >= 0) {
            filteredOrgs.add(org)
        }
    }
    allOrgs = filteredOrgs
}

var total = allOrgs.size()

if (total == 0) {
    // 返回当前业务分支的标准结果。
    return { total: 0, list: [] }
}

// ==================== Step 2: 内存分页 ====================
var offsetLimit = (pNo - 1) * pSize
if (offsetLimit >= total) {
    // 返回当前业务分支的标准结果。
    return { total: total, list: [] }
}
var endIdx = offsetLimit + pSize
if (endIdx > total) {
    endIdx = total
}
var pageOrgs = allOrgs.subList(offsetLimit, endIdx)

// ==================== Step 3: 构建当前页机构ID列表 ====================
var pageOrgIdStrs = []
for (org in pageOrgs) {
    pageOrgIdStrs.add(org.id + '')
}

// ==================== Step 4: 查询 nodeId="业务库" 的数据库ID列表 ====================
// nodeId 是 assetType=db 文档上的字段，table 文档通过 dbId 关联到 db 文档
// 因此需要先查出 nodeId="业务库" 的所有 db tid，再用 dbId 过滤 table
var businessDbDsl = {
    "size": 10000,
    "query": {
        "bool": {
            "filter": [
                { "term": { "assetType": "db" } },
                { "term": { "assetStatus": 2 } },
                { "term": { "nodeId": "业务库" } }
            ]
        }
    },
    "_source": ["tid"]
}

var indexName = configData().es_dataassets
var businessDbIds = []
try {
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var dbResult = tenantEs.index(indexName).search(businessDbDsl)
    if (dbResult && dbResult.hits && dbResult.hits.hits) {
        for (hit in dbResult.hits.hits) {
            if (hit._source && hit._source.tid) {
                businessDbIds.add(hit._source.tid + '')
            }
        }
    }
} catch (e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'ES查询业务库db失败: ' + e.message
}

// ==================== Step 5: ES 聚合 A — table 直查（dataHjCount + tagCount + tableUpdateTime） ====================
// 直接查 table，仅匹配 orgId，通过 dbId 过滤只统计业务库下的表
var sumRecordScript = "if (doc['recordCount'].size() == 0) { return 0; } def v = doc['recordCount'].value; if (v != null && v != '') { return Double.parseDouble(v); } return 0;"
var sumFieldScript = "if (doc['fieldCount'].size() == 0) { return 0; } def v = doc['fieldCount'].value; if (v != null && v != '') { return Double.parseDouble(v); } return 0;"

var tableDsl = {
    "size": 0,
    "query": {
        "bool": {
            "filter": [
                { "term": { "assetType": "table" } },
                { "terms": { "orgId": pageOrgIdStrs } }
            ]
        }
    },
    "aggs": {
        "by_org": {
            "terms": { "field": "orgId", "size": 1000 },
            "aggs": {
                "total_records": { "sum": { "script": { "source": sumRecordScript, "lang": "painless" } } },
                "total_fields": { "sum": { "script": { "source": sumFieldScript, "lang": "painless" } } },
                "latest_update": {
                    "top_hits": {
                        "sort": [{ "updatedTime": { "order": "desc" } }],
                        "size": 1,
                        "_source": ["updatedTime"]
                    }
                }
            }
        }
    }
}

// 如果查到了业务库dbId，则加上 dbId 过滤；否则用不可能匹配的条件确保结果为空
if (businessDbIds.size() > 0) {
    tableDsl.query.bool.filter.add({ "terms": { "dbId": businessDbIds } })
} else {
    // 没有业务库时，table 统计结果应为 0
    tableDsl.query.bool.filter.add({ "term": { "dbId": "__NONEXISTENT__" } })
}

// ==================== Step 6: ES 聚合 B — catalog 有 targetTableId（categoryHjCount） ====================
// 仅匹配 orgId
var categoryHjDsl = {
    "size": 0,
    "query": {
        "bool": {
            "filter": [
                { "term": { "assetType": "catalog" } },
                { "exists": { "field": "targetTableId" } },
                { "terms": { "orgId": pageOrgIdStrs } }
            ]
        }
    },
    "aggs": {
        "by_org": {
            "terms": { "field": "orgId", "size": 1000 },
            "aggs": {
                "catalog_hj_count": { "value_count": { "field": "tid" } }
            }
        }
    }
}

// ==================== Step 7: ES 聚合 C — catalog 总数（categoryCount + updateTime） ====================
// 仅匹配 orgId
var catalogDsl = {
    "size": 0,
    "query": {
        "bool": {
            "filter": [
                { "term": { "assetType": "catalog" } },
                { "terms": { "orgId": pageOrgIdStrs } }
            ]
        }
    },
    "aggs": {
        "by_org": {
            "terms": { "field": "orgId", "size": 1000 },
            "aggs": {
                "catalog_count": { "value_count": { "field": "tid" } },
                "latest_update": {
                    "top_hits": {
                        "sort": [{ "updatedTime": { "order": "desc" } }],
                        "size": 1,
                        "_source": ["updatedTime"]
                    }
                }
            }
        }
    }
}

// ==================== Step 8: 执行 ES 聚合查询 ====================
var tableAgg = null
var categoryHjAgg = null
var catalogAgg = null

try {
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    tableAgg = tenantEs.index(indexName).search(tableDsl)
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    categoryHjAgg = tenantEs.index(indexName).search(categoryHjDsl)
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    catalogAgg = tenantEs.index(indexName).search(catalogDsl)
} catch (e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'ES聚合查询失败: ' + e.message
}

// ==================== Step 9: 解析聚合结果 ====================
// tableMap: orgId → { dataHjCount, tagCount, tableUpdateTime }
var tableMap = {}
if (tableAgg && tableAgg.aggregations && tableAgg.aggregations.by_org) {
    var buckets = tableAgg.aggregations.by_org.buckets
    if (buckets) {
        for (bucket in buckets) {
            var dataHjCount = 0
            if (bucket.total_records && bucket.total_records.value) {
                dataHjCount = bucket.total_records.value
            }
            var tagCount = 0
            if (bucket.total_fields && bucket.total_fields.value) {
                tagCount = bucket.total_fields.value
            }
            var tableUpdateTime = null
            if (bucket.latest_update && bucket.latest_update.hits && bucket.latest_update.hits.hits && bucket.latest_update.hits.hits.size() > 0) {
                tableUpdateTime = bucket.latest_update.hits.hits[0]._source.updatedTime
            }
            tableMap[bucket.key + ''] = { 'dataHjCount': dataHjCount, 'tagCount': tagCount, 'tableUpdateTime': tableUpdateTime }
        }
    }
}

// categoryHjMap: orgId → categoryHjCount
var categoryHjMap = {}
if (categoryHjAgg && categoryHjAgg.aggregations && categoryHjAgg.aggregations.by_org) {
    var buckets = categoryHjAgg.aggregations.by_org.buckets
    if (buckets) {
        for (bucket in buckets) {
            var hjCount = 0
            if (bucket.catalog_hj_count && bucket.catalog_hj_count.value) {
                hjCount = bucket.catalog_hj_count.value
            }
            categoryHjMap[bucket.key + ''] = hjCount
        }
    }
}

// catalogMap: orgId → { categoryCount, catalogUpdateTime }
var catalogMap = {}
if (catalogAgg && catalogAgg.aggregations && catalogAgg.aggregations.by_org) {
    var buckets = catalogAgg.aggregations.by_org.buckets
    if (buckets) {
        for (bucket in buckets) {
            var count = 0
            if (bucket.catalog_count && bucket.catalog_count.value) {
                count = bucket.catalog_count.value
            }
            var maxTime = null
            if (bucket.latest_update && bucket.latest_update.hits && bucket.latest_update.hits.hits && bucket.latest_update.hits.hits.size() > 0) {
                maxTime = bucket.latest_update.hits.hits[0]._source.updatedTime
            }
            catalogMap[bucket.key + ''] = { 'categoryCount': count, 'catalogUpdateTime': maxTime }
        }
    }
}

// ==================== Step 10: 组装返回结果 ====================
var resultList = []
for (org in pageOrgs) {
    var orgIdStr = org.id + ''

    // dataHjCount + tagCount: 直接查 table（dbId属于业务库），按 orgId 匹配
    var dataHjCount = 0
    var tagCount = 0
    var tableUpdateTime = null
    var tData = tableMap[orgIdStr]
    if (tData) {
        dataHjCount = tData['dataHjCount']
        tagCount = tData['tagCount']
        tableUpdateTime = tData['tableUpdateTime']
    }

    // categoryHjCount: 有挂载表的 catalog 数量
    var categoryHjCount = 0
    var hjCount = categoryHjMap[orgIdStr]
    if (hjCount != null) {
        categoryHjCount = hjCount
    }

    // categoryCount: 所有 catalog 总数
    var categoryCount = 0
    var catalogUpdateTime = null
    var cData = catalogMap[orgIdStr]
    if (cData) {
        categoryCount = cData['categoryCount']
        catalogUpdateTime = cData['catalogUpdateTime']
    }

    // updateTime = max(table更新时间, catalog 更新时间)
    var updateTime = null
    if (tableUpdateTime && catalogUpdateTime) {
        updateTime = tableUpdateTime > catalogUpdateTime ? tableUpdateTime : catalogUpdateTime
    } else if (tableUpdateTime) {
        updateTime = tableUpdateTime
    } else if (catalogUpdateTime) {
        updateTime = catalogUpdateTime
    }

    resultList.add({
        'orgId':           org.id,
        'orgName':         org.name,
        'tagCount':        tagCount,
        'dataHjCount':     dataHjCount,
        'categoryHjCount': categoryHjCount,
        'categoryCount':   categoryCount,
        'updateTime':      updateTime
    })
}

// 返回当前业务分支的标准结果。
return {
    total: total,
    list:  resultList
}
