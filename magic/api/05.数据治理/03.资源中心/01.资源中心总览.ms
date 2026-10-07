{
  "properties": {},
  "id": "77dd13a825764993848cb05d8cb6a771",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "01.资源中心总览",
  "createTime": 1782955345141,
  "updateTime": 1787153757526,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/layer-list",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "资源中心 01：汇总 DST/ODS/DWD/DWM/DWS 五层数据；涉及 sym_dict_t、db_table_t、db_datasource_t、da_prop_t、dwm_center_layer_source_t、sym_application_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.资源中心总览
// 路由：GET /dwm/center/layer-list
// 业务说明：资源中心 01：汇总 DST/ODS/DWD/DWM/DWS 五层数据；涉及 sym_dict_t、db_table_t、db_datasource_t、da_prop_t、dwm_center_layer_source_t、sym_application_t 等业务表。
import '@get:/dwm/center/dst-info' as dstInfo
import java.util.Map

/*
 * 资源中心 01：汇总 DST/ODS/DWD/DWM/DWS 五层数据。
 *
 * 分层字典读取 sym_dict_t；表资产读取 db_table_t，并通过 db_datasource_t 关联
 * 应用系统。全程只读，不依赖旧 catalog 资产或 Java DataAssetQueryService。
 */

var textOf = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var lower = (value) => textOf(value).trim().toLowerCase()
var rowValue = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) { return value }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) { return value }
    }
    // 返回当前业务分支的标准结果。
    return null
}

// 从 MySQL 业务表读取当前有效数据。
var layerRows = db.select("""
    SELECT tid, dict_name, dict_code, parent_id, tree_path, level_no, sort_no
      FROM sym_dict_t
     WHERE is_del = 0
       AND tree_path LIKE '0.8961bb1c926143a89dd9d0b0e0adb4ef.%'
     ORDER BY level_no, sort_no, dict_name
""")
var roots = {}
for (row in layerRows) {
    var code = lower(firstValue(row, ['dictCode', 'dict_code']))
    if (['dst', 'ods', 'dwd', 'dwm', 'dws'].contains(code)) {
        roots[code] = row
    }
}
// 从 MySQL 业务表读取当前有效数据。
var tableRows = db.select("""
    SELECT *
      FROM db_table_t
     WHERE is_del = 0
       AND asset_status = 2
""")
// 从 MySQL 业务表读取当前有效数据。
var datasourceRows = db.select("""
    SELECT d.tid, d.db_name, d.app_id, d.org_id, d.db_type, d.node_id,
           MAX(CASE WHEN p.prop_name IN ('dataSourceType', 'connection.dataSourceType') AND p.is_del = 0 THEN p.prop_value END) AS data_source_type,
           MAX(CASE WHEN p.prop_name = 'nodeId' AND p.is_del = 0 THEN p.prop_value END) AS node_id_prop
      FROM db_datasource_t d
 LEFT JOIN da_prop_t p ON p.parent_id = d.tid
     WHERE d.is_del = 0
     GROUP BY d.tid, d.db_name, d.app_id, d.org_id, d.db_type, d.node_id
""")
var datasourceMap = {}
for (source in datasourceRows) {
    datasourceMap[firstValue(source, ['tid'])] = source
}

// 读取资源中心分层/分域节点已经配置的数据中心数据源关联。
var sourceRelationRows = db.select("""
    SELECT r.target_id, r.target_type, r.layer_code, r.datasource_id,
           d.db_name, d.db_type, d.node_id
      FROM dwm_center_layer_source_t r
 LEFT JOIN db_datasource_t d ON d.tid = r.datasource_id AND d.is_del = 0
     WHERE r.is_del = 0
""")
var sourceRelationMap = {}
for (relation in sourceRelationRows) {
    sourceRelationMap[textOf(firstValue(relation, ['targetType', 'target_type'])) + '_' + textOf(firstValue(relation, ['targetId', 'target_id']))] = relation
}
var sourceInfoOf = (targetType, targetId) => {
    var relation = sourceRelationMap[textOf(targetType) + '_' + textOf(targetId)]
    if (relation == null) {
        return { datasourceId: null, datasourceName: null, dbType: null }
    }
    return {
        datasourceId: firstValue(relation, ['datasourceId', 'datasource_id']),
        datasourceName: firstValue(relation, ['dbName', 'db_name']),
        dbType: firstValue(relation, ['dbType', 'db_type'])
    }
}
// 从 MySQL 业务表读取当前有效数据。
var appRows = db.select("""
    SELECT tid, app_name
      FROM sym_application_t
     WHERE is_del = 0
       AND asset_status = 2
""")
var appMap = {}
for (app in appRows) {
    appMap[firstValue(app, ['tid'])] = app
}
var layerTables = { dst: [], ods: [], dwd: [], dwm: [], dws: [] }
var sourceLayerOf = (source) => {
    if (source == null) { return '' }
    return lower(firstValue(source, ['dataSourceType', 'data_source_type']))
}
var rootCodeOf = (sourceType) => {
    var code = lower(sourceType)
    if (blank(code)) { return 'dst' }
    if (layerTables[code] != null) { return code }
    for (rootCode in ['ods', 'dwd', 'dwm', 'dws']) {
        var root = roots[rootCode]
        var rootId = firstValue(root, ['tid'])
        var child = layerRows.find(item => lower(firstValue(item, ['dictCode', 'dict_code'])) == code)
        var treePath = firstValue(child, ['treePath', 'tree_path'])
        if (!blank(rootId) && !blank(treePath) && treePath.indexOf(rootId) >= 0) {
            // 返回当前业务分支的标准结果。
            return rootCode
        }
    }
    // 返回当前业务分支的标准结果。
    return 'dst'
}
for (table in tableRows) {
    var source = datasourceMap[firstValue(table, ['datasourceId', 'datasource_id'])]
    // 表级数据组织优先；历史登记只在数据源 da_prop_t 保存了 ODS/DST 时，
    // 回退到该数据源的登记属性，避免整库已归属 ODS 却在资源中心漏展示。
    var sourceType = firstValue(table, ['dataSourceType', 'data_source_type'])
    if (blank(sourceType)) {
        sourceType = sourceLayerOf(source)
    }
    var code = rootCodeOf(sourceType)
    layerTables[code].add(table)
}
var buildDictionaryItems = (rootCode) => {
    var root = roots[rootCode]
    var rootId = firstValue(root, ['tid'])
    var children = layerRows.filter(item => textOf(firstValue(item, ['parentId', 'parent_id'])) == textOf(rootId))
    var items = []
    for (child in children) {
    var childCode = lower(firstValue(child, ['dictCode', 'dict_code']))
    var rows = layerTables[rootCode].filter(table =>
            lower(firstValue(table, ['dataSourceType', 'data_source_type'])) == childCode ||
            lower(sourceLayerOf(datasourceMap[firstValue(table, ['datasourceId', 'datasource_id'])])) == childCode
        )
        var dataCount = 0
        for (table in rows) {
            var recordCount = rowValue(table, ['recordCount', 'record_count'])
            dataCount = dataCount + (recordCount == null ? 0 : recordCount::long)
        }
        items.add({
            id: firstValue(child, ['tid']),
            name: firstValue(child, ['dictName', 'dict_name']),
            layerCode: childCode,
            sourceId: sourceInfoOf('domain', firstValue(child, ['tid'])).datasourceId,
            sourceName: sourceInfoOf('domain', firstValue(child, ['tid'])).datasourceName,
            sourceDbType: sourceInfoOf('domain', firstValue(child, ['tid'])).dbType,
            catalogCount: rows.size(),
            tableCount: rows.size(),
            dataCount: dataCount
        })
    }
    // 返回当前业务分支的标准结果。
    return items
}
// ODS 与其他数仓层一致，按 dataSourceType 字典下的二级分类展示。
// 例如 ODS 下的“原始库”节点即为可维护、可保存目标数据源的分域节点，
// 不再按应用系统聚合，确保分域关联可以正确回显。
var odsItems = buildDictionaryItems('ods')
var odsTableCount = layerTables.ods.size()
var dstResult = dstInfo()
var dwdItems = buildDictionaryItems('dwd')
var dwmItems = buildDictionaryItems('dwm')
var dwsItems = buildDictionaryItems('dws')
var dwsId = firstValue(roots.dws, ['tid'])
var dwmId = firstValue(roots.dwm, ['tid'])
var dwdId = firstValue(roots.dwd, ['tid'])
var odsId = firstValue(roots.ods, ['tid'])
var dstId = firstValue(roots.dst, ['tid'])
var dwsSource = sourceInfoOf('layer', dwsId)
var dwmSource = sourceInfoOf('layer', dwmId)
var dwdSource = sourceInfoOf('layer', dwdId)
var odsSource = sourceInfoOf('layer', odsId)
var dstSource = sourceInfoOf('layer', dstId)
// 返回当前业务分支的标准结果。
return [
    {
        id: dwsId,
        name: 'DWS',
        title: '服务层 DWS',
        sourceId: dwsSource.datasourceId,
        sourceName: dwsSource.datasourceName,
        sourceDbType: dwsSource.dbType,
        categoryCount: layerTables.dws.size(),
        domains: [{ name: '专题库', items: dwsItems }]
    },
    {
        id: dwmId,
        name: 'DWM',
        title: '整合层 DWM',
        sourceId: dwmSource.datasourceId,
        sourceName: dwmSource.datasourceName,
        sourceDbType: dwmSource.dbType,
        categoryCount: layerTables.dwm.size(),
        domains: [{ name: '主题库', items: dwmItems }]
    },
    {
        id: dwdId,
        name: 'DWD',
        title: '基础层 DWD',
        sourceId: dwdSource.datasourceId,
        sourceName: dwdSource.datasourceName,
        sourceDbType: dwdSource.dbType,
        categoryCount: layerTables.dwd.size(),
        domains: [{ name: '标准库', items: dwdItems }]
    },
    {
        id: odsId,
        name: 'ODS',
        title: '贴源层 ODS',
        sourceId: odsSource.datasourceId,
        sourceName: odsSource.datasourceName,
        sourceDbType: odsSource.dbType,
        categoryCount: odsTableCount,
        domains: [{ name: '原始库', items: odsItems }]
    },
    {
        id: dstId,
        name: 'DST',
        title: '数源层 DST',
        sourceId: dstSource.datasourceId,
        sourceName: dstSource.datasourceName,
        sourceDbType: dstSource.dbType,
        tableCount: dstResult == null ? 0 : dstResult.dstTableCount,
        domains: [{ name: '原始库', items: dstResult == null ? [] : dstResult.dst }]
    }
]
