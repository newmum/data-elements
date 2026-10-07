{
  "properties": {},
  "id": "7634052f946c41c3b35a4cae37623eb7",
  "script": null,
  "groupId": "542f981a013646b6b28446891c2b259e",
  "name": "03.资源汇聚分页",
  "createTime": 1782955343515,
  "updateTime": 1784437771661,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/data-collect-page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"queryType\": 0,\n    \"pageNum\": 1,\n    \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"total\": 83,\n        \"pageNum\": 1,\n        \"pageSize\": 10,\n        \"list\": [\n            {\n                \"tableName\": \"标准库_重点渔船\",\n                \"datasourceName\": \"海上福建数据专区标准库\",\n                \"catalogName\": \"标准库_重点渔船(DWD_SHIP_PORT_KEY_VESSEL_INFO)\",\n                \"organName\": \"福建省渔业减灾中心\",\n                \"rowTotal\": 295\n            },\n            {\n                \"tableName\": \"资产数据表(父表)\",\n                \"datasourceName\": \"福建省渔船动态监控管理系统mysql\",\n                \"catalogName\": \"资产数据目录(父表)(da_asset_t)\",\n                \"organName\": \"福建省渔业减灾中心\",\n                \"rowTotal\": 2167\n            },\n            {\n                \"tableName\": \"\",\n                \"datasourceName\": \"\",\n                \"catalogName\": \"船舶所有权信息\",\n                \"organName\": \"福建省渔业减灾中心\",\n                \"rowTotal\": 127401\n            },\n            {\n                \"tableName\": \"标准库_浮标数据_目标表\",\n                \"datasourceName\": \"达梦_TEST\",\n                \"catalogName\": \"标准库_浮标数据(DWD_DISAS_PRE_BUOY_DATA)\",\n                \"organName\": \"福建省渔业减灾中心\",\n                \"rowTotal\": 11\n            },\n            {\n                \"tableName\": \"基本表_海上船舶\",\n                \"datasourceName\": \"海上福建数据专区标准库\",\n                \"catalogName\": \"基本目录_海上船舶(DWD_SHIP_PORT_SEA_SHIP_DTL)\",\n                \"organName\": \"福建省渔业减灾中心\",\n                \"rowTotal\": 108516\n            },\n            {\n                \"tableName\": \"标准库_浮标海洋环境浮标次表层流数据_观测网\",\n                \"datasourceName\": \"HSFJ达梦\",\n                \"catalogName\": \"标准库_浮标海洋环境浮标次目录层流数据_观测网(DWD_DISAS_PRE_GC_BUOY_SURFACE_CURR_DTL)\",\n                \"organName\": \"福建省渔业减灾中心\",\n                \"rowTotal\": 0\n            },\n            {\n                \"tableName\": \"\",\n                \"datasourceName\": \"\",\n                \"catalogName\": \"标准表_执法案件信息表\",\n                \"organName\": \"福建省渔业减灾中心\",\n                \"rowTotal\": 0\n            },\n            {\n                \"tableName\": \"标准库_地波雷达观测信息\",\n                \"datasourceName\": \"\",\n                \"catalogName\": \"标准库_地波雷达观测信息(DWD_DISAS_PRE_GROUND_OBSERVE_INFO)\",\n                \"organName\": \"福建省渔业减灾中心\",\n                \"rowTotal\": 0\n            },\n            {\n                \"tableName\": \"\",\n                \"datasourceName\": \"\",\n                \"catalogName\": \"FBYZT_SJ_BHJQ(FBYZT_SJ_BHJQ)\",\n                \"organName\": \"\",\n                \"rowTotal\": 0\n            },\n            {\n                \"tableName\": \"标准库_浮标海洋环境浮标次表层流数据_观测网\",\n                \"datasourceName\": \"海上福建数据专区标准库\",\n                \"catalogName\": \"标准库_浮标海洋环境浮标次目录层流数据_观测网(DWD_DISAS_PRE_GC_BUOY_SURFACE_CURR_DTL)\",\n                \"organName\": \"福建省渔业减灾中心\",\n                \"rowTotal\": 0\n            }\n        ]\n    }\n}",
  "description": "按局内或局外来源及页面条件读取机构汇聚资料，返回资源汇聚列表与分页总数。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.资源汇聚分页
// 路由：POST /scene/share/data-collect-page
// 业务说明：按局内或局外来源及页面条件读取机构汇聚资料，返回资源汇聚列表与分页总数。
// 资源汇聚情况 - 分页接口
// 三个表格的分页查询接口

import tenantEs;
import '@/common/configData' as configData;

// ========== 通用工具函数 ==========

// 批量查询组织名称
var queryOrgNames = (orgIdList) => {
    var orgNameMap = {};
    if (orgIdList.size().asInt() > 0) {
        // 从 MySQL 业务表读取当前有效数据。
        var orgList = db.table("rm_org_t")
            .where().in("id", orgIdList)
            .select();
        for (org in orgList) {
            orgNameMap[org.id] = org.name;
        }
    }
    // 返回当前业务分支的标准结果。
    return orgNameMap;
};

// 批量查询db信息
var queryDbNames = (dbIdList) => {
    var dbMap = {};
    for (dbId in dbIdList) {
        var dbDsl = {
            "size": 1,
            "query": { "term": { "id": dbId } },
            "_source": ["dbName"]
        };
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        var dbResult = tenantEs.index(configData().es_dataassets).search(dbDsl);
        if (dbResult.hits.hits.size().asInt() > 0) {
            dbMap[dbId] = dbResult.hits.hits[0]._source.dbName || '';
        }
    }
    // 返回当前业务分支的标准结果。
    return dbMap;
};

// 批量查询catalog信息
var queryCatalogNames = (catalogIdList) => {
    var catalogMap = {};
    for (catalogId in catalogIdList) {
        var catDsl = {
            "size": 1,
            "query": { "term": { "id": catalogId } },
            "_source": ["catalogName"]
        };
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        var catResult = tenantEs.index(configData().es_dataassets).search(catDsl);
        if (catResult.hits.hits.size().asInt() > 0) {
            catalogMap[catalogId] = catResult.hits.hits[0]._source.catalogName || '';
        }
    }
    // 返回当前业务分支的标准结果。
    return catalogMap;
};


// ========================================================================
// 接口1：汇聚表列表分页
// 路径: /api/dataassets/convergeTablePage
// 方法: POST
// 参数: { pageNum: 1, pageSize: 10 }
// 逻辑: 汇聚表 = catalog有targetTableId → 关联table获取表名和rowCount
// ========================================================================
var convergeTablePage = () => {
    // 解析请求体并为可选参数设置默认值。
    var pageNum = body?.pageNum ? body.pageNum.asInt() : 1;
    var pageSize = body?.pageSize ? body.pageSize.asInt() : 10;

    try {
        // ========== 1. 查询 targetTableId 不为空的 catalog ==========
        // 这些 catalog 已汇聚了表，targetTableId 就是汇聚的表ID
        var catalogDsl = {
            "size": 1000,
            "query": {
                "bool": {
                    "filter": [
                        { "term": { "assetType": "catalog" } },
                        { "exists": { "field": "targetTableId" } }
                    ]
                }
            },
            "_source": ["targetTableId", "catalogName", "orgId"]
        };
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        var catalogResult = tenantEs.index(configData().es_dataassets).search(catalogDsl);

        // 构建映射：targetTableId → { catalogName, orgId }
        var catalogMap = {};
        var targetTableIdList = [];
        var orgIdSet = {};
        for (hit in catalogResult.hits.hits) {
            var src = hit._source;
            if (src.targetTableId) {
                targetTableIdList.push(src.targetTableId);
                catalogMap[src.targetTableId] = {
                    catalogName: src.catalogName || '',
                    orgId: src.orgId || ''
                };
            }
            if (src.orgId) { orgIdSet[src.orgId] = true; }
        }

        if (targetTableIdList.size().asInt() == 0) {
            // 返回当前业务分支的标准结果。
            return { total: 0, pageNum: pageNum, pageSize: pageSize, list: [] };
        }

        // ========== 2. 关联查询 table 资产，获取表名和统计值 ==========
        // 总数
        var countDsl = {
            "size": 0,
            "query": {
                "bool": {
                    "filter": [
                        { "term": { "assetType": "table" } },
                        { "terms": { "id": targetTableIdList } }
                    ]
                }
            }
        };
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        var countResult = tenantEs.index(configData().es_dataassets).search(countDsl);
        var total = catalogResult.hits.total.value;

        // 分页查询（按rowCount降序）
        var from = (pageNum - 1) * pageSize;
        var tableDsl = {
            "size": pageSize,
            "from": from,
            "query": {
                "bool": {
                    "filter": [
                        { "term": { "assetType": "table" } },
                        { "terms": { "id": targetTableIdList } }
                    ]
                }
            },
            "sort": [{ "rowCount": { "order": "desc", "unmapped_type": "keyword" } }],
            "_source": ["id", "tableNameCn", "dbId", "rowCount", "orgId"]
        };
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        var tableResult = tenantEs.index(configData().es_dataassets).search(tableDsl);

        // ========== 3. 查询关联信息（dbName、orgName） ==========
        var dbIdSet = {};
        for (hit in tableResult.hits.hits) {
            var src = hit._source;
            if (src.dbId) { dbIdSet[src.dbId] = true; }
            if (src.orgId) { orgIdSet[src.orgId] = true; }
        }

        var dbIdList = [];
        for (dbId in dbIdSet.keySet()) { dbIdList.push(dbId); }
        var dbMap = queryDbNames(dbIdList);

        var orgIdList = [];
        for (orgId in orgIdSet.keySet()) { orgIdList.push(orgId); }
        var orgNameMap = queryOrgNames(orgIdList);

        // ========== 4. 组装数据 ==========
        var list = [];
        for (hit in tableResult.hits.hits) {
            var src = hit._source;
            var tableId = src.id;
            var catInfo = catalogMap[tableId] || {};

            var rowTotal = 0;
            if (src.rowCount) {
                try { rowTotal = Double.parseDouble(src.rowCount); } catch(e) { rowTotal = 0; }
            }

            list.push({
                tableName: src.tableNameCn || '',           // 表名（来自table资产）
                datasourceName: dbMap[src.dbId] || '',      // 数据源（来自table关联的db）
                catalogName: catInfo.catalogName || '',     // 目录名（来自catalog）
                organName: orgNameMap[src.orgId] || catInfo.orgId ? orgNameMap[catInfo.orgId] : '',  // 部门
                rowTotal: rowTotal                          // 数据量（来自table的rowCount）
            });
        }

        // 返回当前业务分支的标准结果。
        return { total: total, pageNum: pageNum, pageSize: pageSize, list: list };

    } catch(e) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, '查询汇聚表列表失败: ' + e.message;
    }
};


// ========================================================================
// 接口2：API资产列表分页
// 路径: /api/dataassets/apiAssetPage
// 方法: POST
// 参数: { pageNum: 1, pageSize: 10 }
// ========================================================================
var apiAssetPage = () => {
    var pageNum = body?.pageNum ? body.pageNum.asInt() : 1;
    var pageSize = body?.pageSize ? body.pageSize.asInt() : 10;
    var from = (pageNum - 1) * pageSize;

    try {
        // 1. 查询总数
        var countDsl = {
            "size": 0,
            "query": {
                "bool": {
                    "filter": [{ "term": { "assetType": "api" } }],
                    "must_not": [{ "term": { "catalogId": "" } }]
                }
            }
        };
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        var countResult = tenantEs.index(configData().es_dataassets).search(countDsl);
        var total = countResult.hits.total.value;

        // 2. 分页查询API资产
        var apiDsl = {
            "size": pageSize,
            "from": from,
            "query": {
                "bool": {
                    "filter": [{ "term": { "assetType": "api" } }],
                    "must_not": [{ "term": { "catalogId": "" } }]
                }
            },
            "sort": [{ "requestCount": { "order": "desc", "unmapped_type": "keyword" } }],
            "_source": ["name", "catalogId", "orgId", "requestCount"]
        };
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        var apiResult = tenantEs.index(configData().es_dataassets).search(apiDsl);

        // 3. 收集catalogId和orgId
        var catalogIdSet = {};
        var orgIdSet = {};
        for (hit in apiResult.hits.hits) {
            var src = hit._source;
            if (src.catalogId) { catalogIdSet[src.catalogId] = true; }
            if (src.orgId) { orgIdSet[src.orgId] = true; }
        }

        // 4. 批量查询catalog和组织信息
        var catalogIdList = [];
        for (catalogId in catalogIdSet.keySet()) { catalogIdList.push(catalogId); }
        var catalogMap = queryCatalogNames(catalogIdList);

        var orgIdList = [];
        for (orgId in orgIdSet.keySet()) { orgIdList.push(orgId); }
        var orgNameMap = queryOrgNames(orgIdList);

        // 5. 组装数据
        var list = [];
        for (hit in apiResult.hits.hits) {
            var src = hit._source;
            var totalApiRequest = 0;
            if (src.requestCount) {
                try { totalApiRequest = Double.parseDouble(src.requestCount); } catch(e) { totalApiRequest = 0; }
            }
            list.push({
                apiName: src.name || '',
                catalogName: catalogMap[src.catalogId] || '',
                organName: orgNameMap[src.orgId] || '',
                totalApiRequest: totalApiRequest
            });
        }

        // 返回当前业务分支的标准结果。
        return { total: total, pageNum: pageNum, pageSize: pageSize, list: list };

    } catch(e) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, '查询API资产列表失败: ' + e.message;
    }
};


// ========================================================================
// 接口3：文件资产列表分页
// 路径: /api/dataassets/fileAssetPage
// 方法: POST
// 参数: { pageNum: 1, pageSize: 10 }
// ========================================================================
var fileAssetPage = () => {
    var pageNum = body?.pageNum ? body.pageNum.asInt() : 1;
    var pageSize = body?.pageSize ? body.pageSize.asInt() : 10;
    var from = (pageNum - 1) * pageSize;

    try {
        // 1. 查询总数
        var countDsl = {
            "size": 0,
            "query": {
                "bool": {
                    "filter": [{ "term": { "assetType": "file" } }],
                    "must_not": [{ "term": { "catalogId": "" } }]
                }
            }
        };
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        var countResult = tenantEs.index(configData().es_dataassets).search(countDsl);
        var total = countResult.hits.total.value;

        // 2. 分页查询文件资产
        var fileDsl = {
            "size": pageSize,
            "from": from,
            "query": {
                "bool": {
                    "filter": [{ "term": { "assetType": "file" } }],
                    "must_not": [{ "term": { "catalogId": "" } }]
                }
            },
            "sort": [{ "downloadCount": { "order": "desc", "unmapped_type": "keyword" } }],
            "_source": ["fileName", "catalogId", "orgId", "downloadCount"]
        };
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        var fileResult = tenantEs.index(configData().es_dataassets).search(fileDsl);

        // 3. 收集catalogId和orgId
        var catalogIdSet = {};
        var orgIdSet = {};
        for (hit in fileResult.hits.hits) {
            var src = hit._source;
            if (src.catalogId) { catalogIdSet[src.catalogId] = true; }
            if (src.orgId) { orgIdSet[src.orgId] = true; }
        }

        // 4. 批量查询catalog和组织信息
        var catalogIdList = [];
        for (catalogId in catalogIdSet.keySet()) { catalogIdList.push(catalogId); }
        var catalogMap = queryCatalogNames(catalogIdList);

        var orgIdList = [];
        for (orgId in orgIdSet.keySet()) { orgIdList.push(orgId); }
        var orgNameMap = queryOrgNames(orgIdList);

        // 5. 组装数据
        var list = [];
        for (hit in fileResult.hits.hits) {
            var src = hit._source;
            var downloadTotal = 0;
            if (src.downloadCount) {
                try { downloadTotal = Double.parseDouble(src.downloadCount); } catch(e) { downloadTotal = 0; }
            }
            list.push({
                fileName: src.fileName || '',
                catalogName: catalogMap[src.catalogId] || '',
                organName: orgNameMap[src.orgId] || '',
                downloadTotal: downloadTotal
            });
        }

        // 返回当前业务分支的标准结果。
        return { total: total, pageNum: pageNum, pageSize: pageSize, list: list };

    } catch(e) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, '查询文件资产列表失败: ' + e.message;
    }
};


if (body?.queryType == 0){
    // 返回当前业务分支的标准结果。
    return convergeTablePage()
}
if (body?.queryType == 1){
    // 返回当前业务分支的标准结果。
    return apiAssetPage()
}
if (body?.queryType == 2){
    // 返回当前业务分支的标准结果。
    return fileAssetPage()
}
