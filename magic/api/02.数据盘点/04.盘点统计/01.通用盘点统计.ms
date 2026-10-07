{
  "properties": {},
  "id": "5e9d44728cfc491090f29b80def8858a",
  "script": null,
  "groupId": "9ef163a809ec40b59d35eb27d2f584a5",
  "name": "01.通用盘点统计",
  "createTime": 1782955344201,
  "updateTime": 1787153758291,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/common",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"conditions\": [\n    {\"field\": \"assetType\", \"type\": \"match\", \"value\": \"db\"}\n  ],\n  \"countFields\": [\"orgId\"]\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"_total\": 284,\n  \"orgId\": {\n    \"1351480083570880513\": 9,\n    \"1351480076432175106\": 23\n  }\n}",
  "description": "通用盘点统计（POST /dst/statistics/common）。应用系统按与分页列表完全相同的 Elasticsearch 快照返回总数和 orgId 聚合，保证左侧“全部/部门”统计与右侧分页一致；请求示例使用当前 ES 中的应用系统数据。当 assetType=db 且仅统计 orgId 时保留数据源兼容逻辑；使用请求参数 countFields、conditions；涉及 db_datasource_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.通用盘点统计
// 路由：POST /dst/statistics/common
// 业务说明：通用盘点统计（POST /dst/statistics/common）。应用系统按与分页列表完全相同的 Elasticsearch 快照返回总数和 orgId 聚合，保证左侧“全部/部门”统计与右侧分页一致；请求示例使用当前 ES 中的应用系统数据。当 assetType=db 且仅统计 orgId 时保留数据源兼容逻辑；使用请求参数 countFields、conditions；涉及 db_datasource_t 等业务表。
import tenantEs
import '@/common/configData' as configData;

// 接口说明：资产字段聚合统计。
// 读取范围：ES dataassets 索引。
// 写入行为：无写入。
// 校验方式：用 assetType+orgId 聚合对比 ES terms 结果。

// 资产通用统计接口。
// 读取范围：ES dataassets 索引，统计 da_asset_t 同步到 ES 后的资产快照。
// 写入行为：无写入，不修改数据库或 ES；仅返回各字段的 terms 聚合计数。
// 调用场景：my-app/my-db 等低代码页面用它给组织树、系统、数据库等筛选条件标注数量。
// 条件规则：match 使用 term 精确匹配，in 使用 terms，like 使用 wildcard；空值条件会被忽略。

var countFields = body?.countFields || [];
var conditions = body?.conditions || [];
var text = (value) => value == null ? '' : value + '';
var blank = (value) => value == null || text(value).trim() == '';
var lower = (value) => blank(value) ? '' : text(value).trim().toLowerCase();

if (countFields.size() == 0) {
    // 返回当前业务分支的标准结果。
    return {};
}

// 数据源组织树以 db_datasource_t 为主数据，不能依赖可能延迟刷新的 ES orgId 投影。
var requestedAssetType = '';
for (condition in conditions) {
    if (condition?.field == 'assetType' && condition?.value != null) {
        requestedAssetType = lower(condition.value);
    }
}
if (requestedAssetType == 'db' && countFields.size() == 1 && countFields[0] == 'orgId') {
    var orgCounts = {};
    var datasourceRows = db.table('db_datasource_t').columns('org_id', 'db_name').where()
        .eq('is_del', 0).select();
    for (datasourceRow in (datasourceRows == null ? [] : datasourceRows)) {
        var orgId = datasourceRow.orgId == null ? datasourceRow.org_id : datasourceRow.orgId;
        var dbName = datasourceRow.dbName == null ? datasourceRow.db_name : datasourceRow.dbName;
        if (blank(orgId) || lower(dbName).startsWith('codex_')) {
            continue;
        }
        var key = text(orgId);
        orgCounts[key] = (orgCounts[key] == null ? 0 : orgCounts[key]) + 1;
    }
    return { orgId: orgCounts };
}

var aggs = {};
for (field in countFields) {
    if (!field) {
        continue;
    }
    aggs[field] = {
        terms: {
            field: field,
            size: 10000
        }
    };
}

var filters = [];
for (item in conditions) {
    var field = item?.field;
    var type = item?.type || 'match';
    var value = item?.value;

    if (!field || value == null || value === '') {
        continue;
    }

    if (type === 'in') {
        filters.add({
            terms: {
                [field]: value
            }
        });
        continue;
    }

    if (type === 'like') {
        filters.add({
            wildcard: {
                [field]: `*${value}*`
            }
        });
        continue;
    }

    filters.add({
        term: {
            [field]: value
        }
    });
}

// 应用系统列表会排除占位数据，统计必须采用完全相同的口径，避免树节点数量大于列表。
var excluded = [
    { wildcard: { appName: '未匹配业务系统*' } },
    { wildcard: { applicationSystemName: '未匹配业务系统*' } }
];

// 即使没有筛选条件也统一使用 bool 查询，以便稳定附加排除条件。
var query = {
    bool: {
        filter: filters,
        must_not: excluded
    }
};

var dsl = {
    size: 0,
    track_total_hits: true,
    query: query,
    aggs: aggs
};

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var searchResult = tenantEs.index(configData().es_dataassets).search(dsl);
var result = searchResult?.aggregations || {};
var stat = {};

for (name, aggregation in result) {
    stat[name] = {};
    if (!aggregation?.buckets) {
        continue;
    }
    for (bucket in aggregation.buckets) {
        if (!bucket.key) {
            continue;
        }
        stat[name][bucket.key] = bucket.doc_count;
    }
}

// 总数和聚合必须来自同一次 ES 查询，避免历史部门编号不在组织树时“全部”数量少算。
var totalObject = searchResult?.hits?.total;
stat._total = totalObject?.value == null
    ? (totalObject == null ? 0 : totalObject)
    : totalObject.value;

// 返回当前业务分支的标准结果。
return stat;
