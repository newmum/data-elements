{
  "properties": {},
  "id": "7ec2c6ba85f944b6b3b169dea47c82b0",
  "script": null,
  "groupId": "542f981a013646b6b28446891c2b259e",
  "name": "01.数据共享情况",
  "createTime": 1782955343494,
  "updateTime": 1784437771650,
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
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"bar1\": [],\n        \"bar2\": [],\n        \"barTable1\": [],\n        \"barTable2\": []\n    }\n}",
  "description": "资源汇聚情况接口 - 共享部门排行；涉及 rm_org_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.数据共享情况
// 路由：GET /scene/share/data-share
// 业务说明：资源汇聚情况接口 - 共享部门排行；涉及 rm_org_t 等业务表。
// 资源汇聚情况接口 - 共享部门排行
// 路径: /api/dataassets/resourceShareRank
// 方法: POST
// 参数: 无
// 说明: 统计国家级和市级共享部门排行，包括共享库表和共享API总量

import tenantEs;
import '@/common/configData' as configData;

try {
    // ========== 1. 查询已通过的申请单 ==========
    var applyDsl = {
        "size": 1000,
        "query": {
            "bool": {
                "filter": [
                    { "term": { "flowStatus": 2 } }
                ]
            }
        },
        "_source": ["catalogIds", "applyOrgId", "applyName", "updatedTime"]
    };
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var applyResult = tenantEs.index(configData().es_dataassets_apply).search(applyDsl);

    // ========== 2. 收集所有catalogIds和申请机构信息 ==========
    var allCatalogIdSet = {};  // catalogId -> true（去重）
    var applyOrgMap = {};      // catalogId -> { orgId, applyName, updatedTime }

    for (hit in applyResult.hits.hits) {
        var src = hit._source;
        var catalogIdsStr = src.catalogIds;
        var applyOrgId = src.applyOrgId;
        var applyName = src.applyName || '';
        var updatedTime = src.updatedTime || '';

        if (!catalogIdsStr || !applyOrgId) { continue; }

        // catalogIds 是逗号分隔的字符串，需要拆分
        var catalogIds = catalogIdsStr.split(",");
        for (cid in catalogIds) {
            var catalogId = cid.trim();
            if (!catalogId) { continue; }

            allCatalogIdSet[catalogId] = true;
            applyOrgMap[catalogId] = {
                orgId: applyOrgId,
                applyName: applyName,
                updatedTime: updatedTime
            };
        }
    }

    // ========== 3. 查询catalog资产获取目录详情 ==========
    // 将catalogIds转为数组
    var catalogIdList = [];
    for (catalogId in allCatalogIdSet.keySet()) {
        catalogIdList.push(catalogId);
    }

    // catalogId -> { catalogName, targetTableId, orgId }
    var catalogDetailMap = {};
    var batchSize = 100;
    var listSize = catalogIdList.size().asInt();

    if (listSize > 0) {
        // 分批查询catalog资产
        var batch = 0;
        var totalBatches = (listSize + batchSize - 1) / batchSize;

        while (batch < totalBatches) {
            var start = batch * batchSize;
            var end = start + batchSize;
            if (end > listSize) { end = listSize; }
            var batchIds = catalogIdList.subList(start, end);

            var catalogDsl = {
                "size": 1000,
                "query": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "catalog" } },
                            { "terms": { "tid": batchIds } }
                        ]
                    }
                },
                "_source": ["tid", "catalogName", "targetTableId", "orgId", "updatedTime"]
            };
            // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
            var catalogResult = tenantEs.index(configData().es_dataassets).search(catalogDsl);

            for (hit in catalogResult.hits.hits) {
                var src = hit._source;
                catalogDetailMap[src.tid] = {
                    catalogName: src.catalogName || '',
                    targetTableId: src.targetTableId || '',
                    orgId: src.orgId || '',
                    updatedTime: src.updatedTime || ''
                };
            }

            batch = batch + 1;
        }
    }

    // ========== 4. 统计每个机构的共享库表数量 ==========
    // 共享库表：catalog有targetTableId
    var orgTableStats = {};  // orgId -> { tableCount, catalogNames, latestUpdateTime }

    for (catalogId in applyOrgMap.keySet()) {
        var applyInfo = applyOrgMap[catalogId];
        var catalogDetail = catalogDetailMap[catalogId];

        if (!catalogDetail) { continue; }

        var orgId = applyInfo.orgId;
        var catalogName = catalogDetail.catalogName;
        var targetTableId = catalogDetail.targetTableId;
        var updatedTime = applyInfo.updatedTime || catalogDetail.updatedTime || '';

        if (!orgTableStats[orgId]) {
            orgTableStats[orgId] = {
                tableCount: 0,
                catalogNames: [],
                latestUpdateTime: ''
            };
        }

        // catalog有targetTableId表示汇聚了库表，即为共享库表
        if (targetTableId && targetTableId != '') {
            orgTableStats[orgId].tableCount = orgTableStats[orgId].tableCount + 1;
        }

        // 收集目录名称（最多3个）
        if (catalogName && orgTableStats[orgId].catalogNames.size() < 3) {
            var alreadyExists = false;
            for (name in orgTableStats[orgId].catalogNames) {
                if (name == catalogName) { alreadyExists = true; break; }
            }
            if (!alreadyExists) {
                orgTableStats[orgId].catalogNames.push(catalogName);
            }
        }

        // 更新最新时间
        if (updatedTime > orgTableStats[orgId].latestUpdateTime) {
            orgTableStats[orgId].latestUpdateTime = updatedTime;
        }
    }

    // ========== 5. 查询共享API（通过catalogId关联） ==========
    // 共享API：申请单catalogIds匹配api.catalogId
    var orgApiStats = {};  // orgId -> { apiCount }

    if (listSize > 0) {
        var batch = 0;
        var totalBatches = (listSize + batchSize - 1) / batchSize;

        while (batch < totalBatches) {
            var start = batch * batchSize;
            var end = start + batchSize;
            if (end > listSize) { end = listSize; }
            var batchIds = catalogIdList.subList(start, end);

            var apiDsl = {
                "size": 1000,
                "query": {
                    "bool": {
                        "filter": [
                            { "term": { "assetType": "api" } },
                            { "terms": { "catalogId": batchIds } }
                        ]
                    }
                },
                "_source": ["catalogId", "orgId"]
            };
            // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
            var apiResult = tenantEs.index(configData().es_dataassets).search(apiDsl);

            for (hit in apiResult.hits.hits) {
                var src = hit._source;
                var orgId = src.orgId;

                if (!orgId) { continue; }

                // 通过catalogId找到对应的申请机构
                var catalogId = src.catalogId;
                var applyInfo = applyOrgMap[catalogId];
                if (applyInfo) {
                    orgId = applyInfo.orgId;  // 使用申请机构的orgId
                }

                if (!orgApiStats[orgId]) {
                    orgApiStats[orgId] = { apiCount: 0 };
                }
                orgApiStats[orgId].apiCount = orgApiStats[orgId].apiCount + 1;
            }

            batch = batch + 1;
        }
    }

    // ========== 6. 查询组织机构信息 ==========
    // 横向厅局的顶级组织ID
    var horizontalOrgIds = ['2067073568296509440', '2067073520506609664'];
    // 局内数据对外共享的顶级组织ID
    var internalOrgIds = ['2057658733680787457', '2058490433772060673'];

    // 查询所有nature=2（部门）的组织
    var orgList = db.table("rm_org_t")
        .where().eq("nature", 2).eq("deleted", 0)
        .select();

    // 构建组织映射：orgId -> { name, parentId }
    var orgMap = {};
    for (org in orgList) {
        orgMap[org.id] = {
            name: org.name,
            parentId: org.parentId
        };
    }

    // 判断部门属于哪个顶级组织（递归向上查找）
    var findTopOrg = (orgId, depth) => {
        if (depth > 10 || !orgId || !orgMap[orgId]) { return null; }

        var parentId = orgMap[orgId].parentId;

        // 检查父级是否是顶级组织
        for (topId in horizontalOrgIds) {
            if (parentId == topId) { return topId; }
        }
        for (topId in internalOrgIds) {
            if (parentId == topId) { return topId; }
        }

        // 递归向上查找
        return findTopOrg(parentId, depth + 1);
    };

    var getTopOrgId = (orgId) => {
        // 返回当前业务分支的标准结果。
        return findTopOrg(orgId, 0);
    };

    // ========== 7. 合并统计并分类 ==========
    var allOrgIds = {};
    for (orgId in orgTableStats.keySet()) { allOrgIds[orgId] = true; }
    for (orgId in orgApiStats.keySet()) { allOrgIds[orgId] = true; }

    var horizontalStats = [];
    var internalStats = [];

    for (orgId in allOrgIds.keySet()) {
        var tableStats = orgTableStats[orgId];
        var apiStats = orgApiStats[orgId];
        var orgInfo = orgMap[orgId];

        if (!orgInfo) { continue; }

        var tableCount = tableStats ? tableStats.tableCount : 0;
        var apiCount = apiStats ? apiStats.apiCount : 0;
        var catalogNames = tableStats ? tableStats.catalogNames : [];
        var latestUpdateTime = tableStats ? tableStats.latestUpdateTime : '';

        var topOrgId = getTopOrgId(orgId);
        var organName = orgInfo.name;
        var sourceSystem = catalogNames.size() > 0 ? catalogNames[0] : '';

        var item = {
            organName: organName,
            count1: tableCount,
            count2: apiCount,
            sourceSystem: sourceSystem,
            updateTime: latestUpdateTime
        };

        // 判断属于哪个顶级组织
        var isHorizontal = false;
        for (topId in horizontalOrgIds) {
            if (topOrgId == topId) { isHorizontal = true; break; }
        }

        if (isHorizontal) {
            horizontalStats.push(item);
        } else {
            var isInternal = false;
            for (topId in internalOrgIds) {
                if (topOrgId == topId) { isInternal = true; break; }
            }
            if (isInternal) {
                internalStats.push(item);
            }
        }
    }

    // ========== 8. 排序并截取 ==========
    // 确保count1和count2是数字类型，使用asInt()转换
    horizontalStats = horizontalStats.sort((a, b) => {
        // 返回当前业务分支的标准结果。
        return (b.count1.asInt() + b.count2.asInt()) - (a.count1.asInt() + a.count2.asInt());
    });
    internalStats = internalStats.sort((a, b) => {
        // 返回当前业务分支的标准结果。
        return (b.count1.asInt() + b.count2.asInt()) - (a.count1.asInt() + a.count2.asInt());
    });

    var bar1Limit = horizontalStats.size() >= 8 ? 8 : horizontalStats.size();
    var bar2Limit = internalStats.size() >= 5 ? 5 : internalStats.size();

    var bar1 = horizontalStats.subList(0, bar1Limit);
    var bar2 = internalStats.subList(0, bar2Limit);

    var barTable1Limit = horizontalStats.size() >= 13 ? 13 : horizontalStats.size();
    var barTable2Limit = internalStats.size() >= 8 ? 8 : internalStats.size();

    var barTable1 = horizontalStats.subList(0, barTable1Limit);
    var barTable2 = internalStats.subList(0, barTable2Limit);

    // ========== 9. 格式化返回数据 ==========
    var formatBar = (list) => {
        var result = [];
        for (item in list) {
            result.push({
                organName: item.organName,
                count1: item.count1.asInt(),
                count2: item.count2.asInt()
            });
        }
        // 返回当前业务分支的标准结果。
        return result;
    };

    var formatTable = (list) => {
        var result = [];
        for (item in list) {
            result.push({
                organName: item.organName,
                sourceSystem: item.sourceSystem,
                count1: item.count1.asInt(),
                count2: item.count2.asInt(),
                updateTime: item.updateTime
            });
        }
        // 返回当前业务分支的标准结果。
        return result;
    };

    // 返回当前业务分支的标准结果。
    return {
        bar1: formatBar(bar1),
        bar2: formatBar(bar2),
        barTable1: formatTable(barTable1),
        barTable2: formatTable(barTable2)
    };

} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '查询资源汇聚情况失败: ' + e.message;
}
