{
  "properties": {},
  "id": "2231eb40aa364f73952adc343ce38566",
  "script": null,
  "groupId": "542f981a013646b6b28446891c2b259e",
  "name": "02.数据应用成效",
  "createTime": 1782955343504,
  "updateTime": 1784437771656,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/data-application",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"organCatalogCount\": {\n            \"gjCount\": 2,\n            \"sjCount\": 3,\n            \"bmCount\": 50\n        },\n        \"organDemandCount\": {\n            \"gjCount\": 0,\n            \"sjCount\": 0,\n            \"bmCount\": 0\n        },\n        \"organShareCatalogRank\": [\n            {\n                \"categoryName\": \"标准库_浮标数据(DWD_DISAS_PRE_BUOY_DATA)\",\n                \"catalogCount\": 1\n            },\n            {\n                \"categoryName\": \"FBYZT_SJ_BHJQ(FBYZT_SJ_BHJQ)\",\n                \"catalogCount\": 1\n            },\n            {\n                \"categoryName\": \"海洋经济处_资源服务目录\",\n                \"catalogCount\": 1\n            }\n        ],\n        \"organShareApplyRank\": [\n            {\n                \"applyOrgName\": \"福建省渔业减灾中心\",\n                \"applyCount\": 1\n            },\n            {\n                \"applyOrgName\": \"海洋经济处\",\n                \"applyCount\": 1\n            }\n        ]\n    }\n}",
  "description": "汇总当前租户的数据目录订阅和应用使用情况，返回数据应用成效大屏指标。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.数据应用成效
// 路由：GET /scene/share/data-application
// 业务说明：汇总当前租户的数据目录订阅和应用使用情况，返回数据应用成效大屏指标。
// 数据应用成效总览接口
// 路径: /api/dataassets/dataApplyOverview
// 方法: POST
// 参数: 无
// 说明:
//   organCatalogCount  - 订阅申请统计（type=catalogSubscribe）
//     gjCount = 写死为 8
//     sjCount = 被订阅申请的目录数
//     bmCount = 被订阅申请的目录的挂接表的字段总数（sum fieldCount）
//   organDemandCount   - 需求申请统计（type=need）
//     gjCount = 被需求申请的目录的应用系统数（去重 appId）
//     sjCount = 被需求申请的目录数
//     bmCount = 被需求申请的目录的挂接表的字段总数（sum fieldCount）
//   organShareCatalogRank - 目录被申请次数排行Top10
//   organShareApplyRank   - 部门目录被申请次数排行Top10

import tenantEs;
import '@/common/configData' as configData;

try {

    // ========== 1. 查询订阅申请单和需求申请单，收集 catalogIds ==========
    // 订阅申请
    var subscribeDsl = {
        "size": 1000,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "type": "catalogSubscribe" } },
                    { "term": { "flowStatus": 2 } }
                ]
            }
        },
        "_source": ["catalogIds"]
    };
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var subscribeResult = tenantEs.index(configData().es_dataassets_apply).search(subscribeDsl);

    var subscribeCatalogIds = [];
    for (hit in subscribeResult.hits.hits) {
        var src = hit._source;
        if (!src.catalogIds) { continue; }
        var ids = src.catalogIds.split(",");
        for (cid in ids) {
            var trimmed = cid.trim();
            if (trimmed) { subscribeCatalogIds.push(trimmed); }
        }
    }

    // 需求申请
    var needDsl = {
        "size": 1000,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "type": "need" } },
                    { "term": { "flowStatus": 2 } }
                ]
            }
        },
        "_source": ["catalogIds"]
    };
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var needResult = tenantEs.index(configData().es_dataassets_apply).search(needDsl);

    var needCatalogIds = [];
    for (hit in needResult.hits.hits) {
        var src = hit._source;
        if (!src.catalogIds) { continue; }
        var ids = src.catalogIds.split(",");
        for (cid in ids) {
            var trimmed = cid.trim();
            if (trimmed) { needCatalogIds.push(trimmed); }
        }
    }

    // ========== 2. 合并所有 catalogIds，批量查 catalog 资产 ==========
    // 收集去重的 catalogIds
    var allCatalogIdSet = {};
    for (cid in subscribeCatalogIds) { allCatalogIdSet[cid] = true; }
    for (cid in needCatalogIds) { allCatalogIdSet[cid] = true; }
    var allCatalogIds = [];
    for (cid in allCatalogIdSet.keySet()) { allCatalogIds.push(cid); }

    // catalogId -> { appId, targetTableId }
    var catalogInfoMap = {};
    if (allCatalogIds.size().asInt() > 0) {
        var catBatchSize = 100;
        var catTotal = allCatalogIds.size().asInt();
        var catBatch = 0;
        var catBatches = (catTotal + catBatchSize - 1) / catBatchSize;

        while (catBatch < catBatches) {
            var start = catBatch * catBatchSize;
            var end = start + catBatchSize;
            if (end > catTotal) { end = catTotal; }
            var batchIds = allCatalogIds.subList(start, end);

            var catDsl = {
                "size": 1000,
                "query": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "catalog" } },
                            { "terms": { "tid": batchIds } }
                        ]
                    }
                },
                "_source": ["tid", "appId", "targetTableId"]
            };
            // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
            var catResult = tenantEs.index(configData().es_dataassets).search(catDsl);

            for (hit in catResult.hits.hits) {
                var src = hit._source;
                catalogInfoMap[src.tid] = {
                    appId: src.appId || '',
                    targetTableId: src.targetTableId || ''
                };
            }
            catBatch = catBatch + 1;
        }
    }

    // ========== 3. 计算订阅申请的 gjCount/sjCount/bmCount ==========
    // sjCount = 被订阅申请的目录数
    var subSjCount = subscribeCatalogIds.size().asInt();

    // gjCount = 被订阅申请的目录的应用系统数（去重 appId）
    var subAppIdSet = {};
    for (cid in subscribeCatalogIds) {
        var info = catalogInfoMap[cid];
        if (info && info.appId) { subAppIdSet[info.appId] = true; }
    }
    var subGjCount = subAppIdSet.keySet().size().asInt();

    // bmCount = 被订阅申请的目录的挂接表的字段总数
    // 收集 targetTableIds
    var subTableIds = [];
    for (cid in subscribeCatalogIds) {
        var info = catalogInfoMap[cid];
        if (info && info.targetTableId) { subTableIds.push(info.targetTableId); }
    }

    var subBmCount = 0;
    if (subTableIds.size().asInt() > 0) {
        // 去重
        var subTableIdSet = {};
        for (tid in subTableIds) { subTableIdSet[tid] = true; }
        var uniqueSubTableIds = [];
        for (tid in subTableIdSet.keySet()) { uniqueSubTableIds.push(tid); }

        var fieldBatchSize = 100;
        var fieldTotal = uniqueSubTableIds.size().asInt();
        var fieldBatch = 0;
        var fieldBatches = (fieldTotal + fieldBatchSize - 1) / fieldBatchSize;

        while (fieldBatch < fieldBatches) {
            var fStart = fieldBatch * fieldBatchSize;
            var fEnd = fStart + fieldBatchSize;
            if (fEnd > fieldTotal) { fEnd = fieldTotal; }
            var fBatchIds = uniqueSubTableIds.subList(fStart, fEnd);

            var fieldDsl = {
                "size": 1000,
                "query": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "table" } },
                            { "terms": { "_id": fBatchIds } }
                        ]
                    }
                },
                "_source": ["fieldCount"]
            };
            // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
            var fieldResult = tenantEs.index(configData().es_dataassets).search(fieldDsl);

            for (hit in fieldResult.hits.hits) {
                var src = hit._source;
                if (src.fieldCount) {
                    subBmCount = subBmCount + src.fieldCount.asInt();
                }
            }
            fieldBatch = fieldBatch + 1;
        }
    }

    // ========== 4. 计算需求申请的 gjCount/sjCount/bmCount ==========
    var needSjCount = needCatalogIds.size().asInt();

    var needAppIdSet = {};
    for (cid in needCatalogIds) {
        var info = catalogInfoMap[cid];
        if (info && info.appId) { needAppIdSet[info.appId] = true; }
    }
    var needGjCount = needAppIdSet.keySet().size().asInt();

    var needTableIds = [];
    for (cid in needCatalogIds) {
        var info = catalogInfoMap[cid];
        if (info && info.targetTableId) { needTableIds.push(info.targetTableId); }
    }

    var needBmCount = 0;
    if (needTableIds.size().asInt() > 0) {
        var needTableIdSet = {};
        for (tid in needTableIds) { needTableIdSet[tid] = true; }
        var uniqueNeedTableIds = [];
        for (tid in needTableIdSet.keySet()) { uniqueNeedTableIds.push(tid); }

        var nFieldBatchSize = 100;
        var nFieldTotal = uniqueNeedTableIds.size().asInt();
        var nFieldBatch = 0;
        var nFieldBatches = (nFieldTotal + nFieldBatchSize - 1) / nFieldBatchSize;

        while (nFieldBatch < nFieldBatches) {
            var nfStart = nFieldBatch * nFieldBatchSize;
            var nfEnd = nfStart + nFieldBatchSize;
            if (nfEnd > nFieldTotal) { nfEnd = nFieldTotal; }
            var nfBatchIds = uniqueNeedTableIds.subList(nfStart, nfEnd);

            var nFieldDsl = {
                "size": 1000,
                "query": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "table" } },
                            { "terms": { "_id": nfBatchIds } }
                        ]
                    }
                },
                "_source": ["fieldCount"]
            };
            // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
            var nFieldResult = tenantEs.index(configData().es_dataassets).search(nFieldDsl);

            for (hit in nFieldResult.hits.hits) {
                var src = hit._source;
                if (src.fieldCount) {
                    needBmCount = needBmCount + src.fieldCount.asInt();
                }
            }
            nFieldBatch = nFieldBatch + 1;
        }
    }

    // ========== 4. 查询所有已通过的目录订阅申请单，统计catalogId被申请次数 + 归属部门被申请次数 ==========
    var applyDsl = {
        "size": 1000,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "type": "catalogSubscribe" } },
                    { "term": { "flowStatus": 2 } }
                ]
            }
        },
        "_source": ["catalogIds"]
    };
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var applyResult = tenantEs.index(configData().es_dataassets_apply).search(applyDsl);

    // 统计每个catalogId被申请的次数
    var catalogApplyCountMap = {};  // catalogId -> applyCount

    for (hit in applyResult.hits.hits) {
        var src = hit._source;
        var catalogIdsStr = src.catalogIds;
        if (!catalogIdsStr) { continue; }

        var catalogIds = catalogIdsStr.split(",");
        for (cid in catalogIds) {
            var catalogId = cid.trim();
            if (!catalogId) { continue; }

            if (!catalogApplyCountMap[catalogId]) {
                catalogApplyCountMap[catalogId] = 0;
            }
            catalogApplyCountMap[catalogId] = catalogApplyCountMap[catalogId] + 1;
        }
    }

    // 收集所有涉及的catalogId，批量查catalog资产获取 catalogName 和 orgId
    var involvedCatalogIds = [];
    for (cid in catalogApplyCountMap.keySet()) {
        involvedCatalogIds.push(cid);
    }

    // catalogId -> { catalogName, orgId }
    var catalogInfoMap = {};
    var invListSize = involvedCatalogIds.size().asInt();

    if (invListSize > 0) {
        var batchSize = 100;
        var batch = 0;
        var totalBatches = (invListSize + batchSize - 1) / batchSize;

        while (batch < totalBatches) {
            var start = batch * batchSize;
            var end = start + batchSize;
            if (end > invListSize) { end = invListSize; }
            var batchIds = involvedCatalogIds.subList(start, end);

            var catInfoDsl = {
                "size": 1000,
                "query": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "catalog" } },
                            { "terms": { "tid": batchIds } }
                        ]
                    }
                },
                "_source": ["tid", "catalogName", "orgId"]
            };
            // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
            var catInfoResult = tenantEs.index(configData().es_dataassets).search(catInfoDsl);

            for (hit in catInfoResult.hits.hits) {
                var src = hit._source;
                catalogInfoMap[src.tid] = {
                    catalogName: src.catalogName || src.tid,
                    orgId: src.orgId || ''
                };
            }

            batch = batch + 1;
        }
    }

    // 组装 organShareCatalogRank：每个catalog被申请次数，取Top10
    var catalogRankList = [];
    for (catalogId in catalogApplyCountMap.keySet()) {
        var info = catalogInfoMap[catalogId];
        var catName = info ? info.catalogName : catalogId;
        catalogRankList.push({
            categoryName: catName,
            catalogCount: catalogApplyCountMap[catalogId],
            _sort: catalogApplyCountMap[catalogId]
        });
    }
    catalogRankList = catalogRankList.sort((a, b) => b._sort.asInt() - a._sort.asInt());
    var catRankLimit = catalogRankList.size().asInt() >= 10 ? 10 : catalogRankList.size().asInt();
    var organShareCatalogRank = [];
    var catIdx = 0;
    for (item in catalogRankList) {
        if (catIdx >= catRankLimit) { break; }
        organShareCatalogRank.push({
            categoryName: item.categoryName,
            catalogCount: item.catalogCount.asInt()
        });
        catIdx = catIdx + 1;
    }

    // 组装 organShareApplyRank：按catalog归属部门统计被申请总次数，取Top10
    var orgApplyCountMap = {};  // orgId -> count
    for (catalogId in catalogApplyCountMap.keySet()) {
        var info = catalogInfoMap[catalogId];
        if (!info || !info.orgId) { continue; }

        var orgId = info.orgId;
        if (!orgApplyCountMap[orgId]) {
            orgApplyCountMap[orgId] = 0;
        }
        orgApplyCountMap[orgId] = orgApplyCountMap[orgId] + catalogApplyCountMap[catalogId];
    }

    // 排序取Top10
    var orgRankList = [];
    for (orgId in orgApplyCountMap.keySet()) {
        orgRankList.push({ orgId: orgId, applyCount: orgApplyCountMap[orgId] });
    }
    orgRankList = orgRankList.sort((a, b) => b.applyCount.asInt() - a.applyCount.asInt());
    var orgRankLimit = orgRankList.size().asInt() >= 10 ? 10 : orgRankList.size().asInt();
    var top10OrgIds = [];
    var orgIdx = 0;
    for (item in orgRankList) {
        if (orgIdx >= orgRankLimit) { break; }
        top10OrgIds.push(item.orgId);
        orgIdx = orgIdx + 1;
    }

    // 查询机构名称
    var orgNameMap = {};
    if (top10OrgIds.size() > 0) {
        // 从 MySQL 业务表读取当前有效数据。
        var orgList = db.table("rm_org_t")
            .where().in("id", top10OrgIds)
            .select();
        for (org in orgList) {
            orgNameMap[org.id] = org.name;
        }
    }

    // 组装结果（保持排序）
    var organShareApplyRank = [];
    for (item in orgRankList) {
        if (organShareApplyRank.size().asInt() >= orgRankLimit) { break; }
        var orgId = item.orgId;
        var orgName = orgNameMap[orgId] ? orgNameMap[orgId] : orgId;
        organShareApplyRank.push({
            applyOrgName: orgName,
            applyCount: item.applyCount.asInt()
        });
    }

    // ========== 返回结果 ==========
    return {
        organCatalogCount: {
            gjCount: 8,
            sjCount: subSjCount,
            bmCount: subBmCount
        },
        organDemandCount: {
            gjCount: needGjCount,
            sjCount: needSjCount,
            bmCount: needBmCount
        },
        organShareCatalogRank: organShareCatalogRank,
        organShareApplyRank: organShareApplyRank
    };

} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '查询数据应用成效失败: ' + e.message;
}
