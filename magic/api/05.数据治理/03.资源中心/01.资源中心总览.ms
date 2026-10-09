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
  "description": "资源中心总览：按当前租户和角色数据范围聚合五层表数及记录数，不加载表资产正文。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 路由：GET /dwm/center/layer-list
// 总览只读取轻量元数据和数据库聚合结果，禁止读取完整表资产或逐表补查。
import '@get:/dwm/center/dst-info' as dstInfo
import tenantRuntime
import dataScope
import java.util.Map
import cn.hutool.json.JSONUtil

var textOf = value => value == null ? '' : ('' + value).trim()
var blank = value => textOf(value) == ''
var lower = value => textOf(value).toLowerCase()
var rowValue = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value == null) { value = row[key.toUpperCase()] }
        if (value != null) { return value }
    }
    return null
}
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) { return value }
    }
    return null
}
var tenantId = textOf(tenantRuntime.id())
if (tenantId == '') { exit 401, '当前租户无效' }
var scope = dataScope.dataSource()
var scopeMode = textOf(scope?.scope)
var scopeUserId = textOf(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId == '') {
    exit 403, '无法确定当前数据范围'
}
// 权限条件固定在 SQL 中，身份/部门/租户均绑定；空部门范围不返回数据。
var scopeOrgCount = scopeOrgIds.size()
if (scopeOrgCount == 0) { scopeOrgIds = [''] }

var layerRows = db.select("""
    SELECT tid, dict_name, dict_code, parent_id, tree_path
      FROM sym_dict_t
     WHERE tenant_id=#{tenantId} AND is_del=0
       AND tree_path LIKE '0.8961bb1c926143a89dd9d0b0e0adb4ef.%'
     ORDER BY level_no, sort_no, dict_name
""")
var roots = {}
var childrenByParent = {}
var rootByCode = { dst: 'dst', ods: 'ods', dwd: 'dwd', dwm: 'dwm', dws: 'dws' }
for (row in layerRows) {
    var code = lower(firstValue(row, ['dictCode', 'dict_code']))
    if (rootByCode[code] != null) { roots[code] = row }
    var parentId = textOf(firstValue(row, ['parentId', 'parent_id']))
    if (childrenByParent[parentId] == null) { childrenByParent[parentId] = [] }
    childrenByParent[parentId].add(row)
}
// 一次建立分类索引；后续只遍历按来源和分类汇总的行，不遍历物理表。
for (row in layerRows) {
    var code = lower(firstValue(row, ['dictCode', 'dict_code']))
    if (rootByCode[code] != null) { continue }
    var treePath = '.' + textOf(firstValue(row, ['treePath', 'tree_path'])) + '.'
    for (rootCode in ['ods', 'dwd', 'dwm', 'dws']) {
        var rootId = textOf(firstValue(roots[rootCode], ['tid']))
        if (rootId != '' && treePath.indexOf('.' + rootId + '.') >= 0) { rootByCode[code] = rootCode; break }
    }
}
var rootCodeOf = code => rootByCode[lower(code)] == null ? 'dst' : rootByCode[lower(code)]
var datasourceRows = db.select("""
    SELECT d.tid, d.db_name, d.db_type, d.pool_cfg
      FROM db_datasource_t d
     WHERE d.tenant_id=#{tenantId} AND d.is_del=0 AND (
           #{scopeMode}='ALL'
           OR (#{scopeMode}='OWNER' AND d.created_by=#{scopeUserId})
           OR (#{scopeMode}='ORG' AND #{scopeOrgCount}>0 AND d.org_id IN
               <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>)
       )
""")
// 旧登记属性只批量读取分类值，保留历史数据源的回退语义。
var legacyTypes = db.select("""
    SELECT p.parent_id, MAX(p.prop_value) AS source_type
      FROM da_prop_t p
      JOIN db_datasource_t d ON d.tid=p.parent_id AND d.tenant_id=#{tenantId} AND d.is_del=0
     WHERE p.tenant_id=#{tenantId} AND p.is_del=0
       AND p.prop_name IN ('dataSourceType', 'connection.dataSourceType') AND (
           #{scopeMode}='ALL'
           OR (#{scopeMode}='OWNER' AND d.created_by=#{scopeUserId})
           OR (#{scopeMode}='ORG' AND #{scopeOrgCount}>0 AND d.org_id IN
               <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>)
       )
     GROUP BY p.parent_id
""")
var sourceTypeMap = {}
for (row in legacyTypes) {
    sourceTypeMap[textOf(firstValue(row, ['parentId', 'parent_id']))] = lower(firstValue(row, ['sourceType', 'source_type']))
}
var datasourceMap = {}
for (source in datasourceRows) {
    var sourceId = textOf(firstValue(source, ['tid']))
    var poolConfig = firstValue(source, ['poolCfg', 'pool_cfg'])
    var sourceType = ''
    if (!blank(poolConfig) && JSONUtil.isTypeJSONObject(poolConfig)) {
        // 配置只在服务端解析分类，不返回或记录连接凭据。
        var config = JSONUtil.parseObj(poolConfig)
        sourceType = lower(config.get('dataSourceType'))
        if (sourceType == '' && config.get('connection') instanceof Map) {
            sourceType = lower(config.get('connection').get('dataSourceType'))
        }
    }
    if (sourceType == '') { sourceType = textOf(sourceTypeMap[sourceId]) }
    sourceTypeMap[sourceId] = sourceType
    datasourceMap[sourceId] = {
        tid: sourceId,
        dbName: firstValue(source, ['dbName', 'db_name']),
        dbType: firstValue(source, ['dbType', 'db_type'])
    }
}

var sourceRelationRows = db.select("""
    SELECT target_id, target_type, datasource_id
      FROM dwm_center_layer_source_t
     WHERE tenant_id=#{tenantId} AND is_del=0
""")
var sourceRelationMap = {}
for (relation in sourceRelationRows) {
    var source = datasourceMap[textOf(firstValue(relation, ['datasourceId', 'datasource_id']))]
    // 关联目标逐 ID 从已受租户/角色约束的索引校验，隐藏不可见数据源。
    if (source != null) {
        var key = textOf(firstValue(relation, ['targetType', 'target_type'])) + '_' + textOf(firstValue(relation, ['targetId', 'target_id']))
        sourceRelationMap[key] = source
    }
}
var sourceInfoOf = (targetType, targetId) => {
    var source = sourceRelationMap[textOf(targetType) + '_' + textOf(targetId)]
    return source == null ? { datasourceId: null, datasourceName: null, dbType: null } :
        { datasourceId: source.tid, datasourceName: source.dbName, dbType: source.dbType }
}

// COUNT/SUM 在数据库完成；不会读取字段治理 JSON、列定义等大字段。
var tableStats = db.select("""
    SELECT t.datasource_id, LOWER(TRIM(COALESCE(t.data_source_type, ''))) AS table_type,
           COUNT(*) AS table_count, COALESCE(SUM(t.record_count), 0) AS data_count
      FROM db_table_t t
      JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=#{tenantId} AND d.is_del=0
     WHERE t.tenant_id=#{tenantId} AND t.is_del=0 AND t.asset_status=2 AND (
           #{scopeMode}='ALL'
           OR (#{scopeMode}='OWNER' AND d.created_by=#{scopeUserId})
           OR (#{scopeMode}='ORG' AND #{scopeOrgCount}>0 AND d.org_id IN
               <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>)
       )
     GROUP BY t.datasource_id, LOWER(TRIM(COALESCE(t.data_source_type, '')))
""")
var layerCounts = { dst: 0, ods: 0, dwd: 0, dwm: 0, dws: 0 }
var domainCounts = {}
var addDomain = (rootCode, code, tableCount, dataCount) => {
    if (blank(code)) { return }
    var key = rootCode + '_' + code
    if (domainCounts[key] == null) { domainCounts[key] = { tableCount: 0, dataCount: 0 } }
    domainCounts[key].tableCount = domainCounts[key].tableCount + tableCount
    domainCounts[key].dataCount = domainCounts[key].dataCount + dataCount
}
for (stats in tableStats) {
    var tableType = lower(firstValue(stats, ['tableType', 'table_type']))
    var sourceType = lower(sourceTypeMap[textOf(firstValue(stats, ['datasourceId', 'datasource_id']))])
    var rootCode = rootCodeOf(tableType == '' ? sourceType : tableType)
    var tableCount = rowValue(stats, ['tableCount', 'table_count'])::long
    var dataCount = rowValue(stats, ['dataCount', 'data_count'])::long
    layerCounts[rootCode] = layerCounts[rootCode] + tableCount
    // 保留表分类/来源分类匹配任一分域的原口径，同一代码不重复计数。
    addDomain(rootCode, tableType, tableCount, dataCount)
    if (sourceType != tableType) { addDomain(rootCode, sourceType, tableCount, dataCount) }
}
var buildDictionaryItems = rootCode => {
    var rootId = textOf(firstValue(roots[rootCode], ['tid']))
    var children = childrenByParent[rootId] == null ? [] : childrenByParent[rootId]
    var items = []
    for (child in children) {
        var childCode = lower(firstValue(child, ['dictCode', 'dict_code']))
        var counts = domainCounts[rootCode + '_' + childCode]
        var source = sourceInfoOf('domain', firstValue(child, ['tid']))
        items.add({
            id: firstValue(child, ['tid']), name: firstValue(child, ['dictName', 'dict_name']), layerCode: childCode,
            sourceId: source.datasourceId, sourceName: source.datasourceName, sourceDbType: source.dbType,
            catalogCount: counts == null ? 0 : counts.tableCount,
            tableCount: counts == null ? 0 : counts.tableCount,
            dataCount: counts == null ? 0 : counts.dataCount
        })
    }
    return items
}
// ODS 与其他数仓层一致，按 dataSourceType 字典下的二级分类展示。
// 例如 ODS 下的“原始库”节点即为可维护、可保存目标数据源的分域节点，
// 不再按应用系统聚合，确保分域关联可以正确回显。
var odsItems = buildDictionaryItems('ods')
var odsTableCount = layerCounts.ods
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
        categoryCount: layerCounts.dws,
        domains: [{ name: '专题库', items: dwsItems }]
    },
    {
        id: dwmId,
        name: 'DWM',
        title: '整合层 DWM',
        sourceId: dwmSource.datasourceId,
        sourceName: dwmSource.datasourceName,
        sourceDbType: dwmSource.dbType,
        categoryCount: layerCounts.dwm,
        domains: [{ name: '主题库', items: dwmItems }]
    },
    {
        id: dwdId,
        name: 'DWD',
        title: '基础层 DWD',
        sourceId: dwdSource.datasourceId,
        sourceName: dwdSource.datasourceName,
        sourceDbType: dwdSource.dbType,
        categoryCount: layerCounts.dwd,
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
