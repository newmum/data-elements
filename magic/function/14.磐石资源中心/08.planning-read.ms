{
  "properties" : { },
  "id" : "4e0ee97e906054229d56e22da830272e",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "08.planning-read",
  "createTime" : null,
  "updateTime" : 1790989454788,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/planning-read",
  "description" : null,
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "input",
    "value" : null,
    "description" : null,
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ]
}
================================
// 共享数仓规划投影：字典层/分域、节点数据源关联和已采集表均来自当前租户。
import tenantRuntime
import jsons
import cn.hutool.crypto.digest.DigestUtil

var tenantId = tenantRuntime.id()
var text = value => value == null ? '' : value + ''
var json = value => text(value) == '' ? {} : jsons.toJSONObject(value)
var root = db.selectOne("""
    SELECT tid, tree_path, level_no
      FROM sym_dict_t
     WHERE tenant_id = #{tenantId} AND dict_code = 'dataSourceType' AND is_del = 0
     LIMIT 1
""")
var classRoot = db.selectOne("""
    SELECT tid FROM sym_dict_t
     WHERE tenant_id = #{tenantId} AND dict_code = 'resourceCatalogClass' AND is_del = 0
     LIMIT 1
""")
var layerRows = root == null ? [] : db.select("""
    SELECT tid, dict_name AS name, dict_code AS layer_code, parent_id,
           tree_path, level_no, sort_no,
           dict_desc AS purpose, planning_config_json AS config,
           revision_no AS version, created_time, updated_time
      FROM sym_dict_t
     WHERE tenant_id = #{tenantId} AND parent_id = #{root.tid} AND is_del = 0
     ORDER BY sort_no, dict_name, tid
""")
var domainRows = root == null ? [] : db.select("""
    SELECT d.tid, d.dict_name AS name, d.dict_code AS domain_code, d.parent_id,
           d.tree_path, d.level_no, d.sort_no,
           d.dict_desc AS purpose, d.planning_config_json AS config,
           d.revision_no AS version, d.created_time, d.updated_time
      FROM sym_dict_t d
      JOIN sym_dict_t p ON p.tid = d.parent_id AND p.tenant_id = d.tenant_id AND p.is_del = 0
     WHERE d.tenant_id = #{tenantId} AND d.is_del = 0 AND p.parent_id = #{root.tid}
     ORDER BY p.sort_no, d.sort_no, d.dict_name, d.tid
""")
var classRows = classRoot == null ? [] : db.select("""
    SELECT tid, dict_name AS name, dict_code AS class_code,
           revision_no AS version, created_time, updated_time
      FROM sym_dict_t
     WHERE tenant_id = #{tenantId} AND parent_id = #{classRoot.tid} AND is_del = 0
     ORDER BY sort_no, dict_name, tid
""")
var tableCounts = {}
for (row in db.select("""
    SELECT datasource_id AS source_id, COUNT(*) AS table_count
      FROM db_table_t WHERE tenant_id = #{tenantId} AND is_del = 0
     GROUP BY datasource_id
""")) { tableCounts[text(row.sourceId)] = row.tableCount }
var relationRows = db.select("""
    SELECT b.tid, b.target_id, b.target_type,
           b.datasource_id AS source_id, b.configuration_json AS config,
           b.revision_no AS version, b.created_time, b.updated_time,
           s.db_name AS source_name, s.db_type AS profile, s.updated_time AS source_updated_at
      FROM dwm_center_layer_source_t b
      LEFT JOIN db_datasource_t s ON s.tid = b.datasource_id AND s.tenant_id = b.tenant_id AND s.is_del = 0
     WHERE b.tenant_id = #{tenantId} AND b.is_del = 0 AND b.target_type IN ('layer', 'domain')
     ORDER BY b.updated_time DESC, b.tid DESC
""")
var relationByTarget = {}
for (row in relationRows) {
    var key = text(row.targetType) + ':' + text(row.targetId)
    if (relationByTarget[key] == null) { relationByTarget[key] = row }
}
var layers = []
var layerById = {}
var builtIns = ['DST', 'ODS', 'DWD', 'DWM', 'DWS', 'ODAS']
for (row in layerRows) {
    var config = json(row.config)
    var source = relationByTarget['layer:' + text(row.tid)]
    var sourceId = source == null ? '' : text(source.sourceId)
    var layer = {id:text(row.tid), name:row.name, code:text(row.layerCode).toUpperCase(),
        purpose:text(row.purpose), owner:text(config.owner), order:layers.size(), sortNo:row.sortNo,
        builtIn:builtIns.contains(text(row.layerCode).toUpperCase()),
        state:config.state == 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE',
        sourceId:sourceId, sourceName:source == null ? '' : text(source.sourceName),
        sourceDbType:source == null ? '' : text(source.profile),
        bindingId:source == null ? '' : text(source.tid),
        bindingVersion:source == null ? null : source.version,
        tableCount:tableCounts[sourceId] == null ? 0 : tableCounts[sourceId],
        version:row.version, createdAt:row.createdAt, updatedAt:row.updatedAt}
    layers.add(layer)
    layerById[layer.id] = layer
}
var databases = []
var domainById = {}
for (row in domainRows) {
    var parent = layerById[text(row.parentId)]
    if (parent == null) { continue }
    var config = json(row.config)
    var source = relationByTarget['domain:' + text(row.tid)]
    var sourceId = source == null ? '' : text(source.sourceId)
    var domain = {id:text(row.tid), name:row.name, code:text(row.domainCode), layerId:parent.id,
        domain:config.domain == null ? text(row.name) : text(config.domain),
        owner:text(config.owner), purpose:text(row.purpose),
        mode:parent.code == 'DST' || config.mode == 'REFERENCE' ? 'REFERENCE' : 'MANAGED',
        state:config.state == 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE',
        sourceId:sourceId, sourceName:source == null ? '' : text(source.sourceName),
        sourceDbType:source == null ? '' : text(source.profile),
        bindingId:source == null ? '' : text(source.tid),
        bindingVersion:source == null ? null : source.version,
        tableCount:tableCounts[sourceId] == null ? 0 : tableCounts[sourceId],
        version:row.version, createdAt:row.createdAt, updatedAt:row.updatedAt}
    databases.add(domain)
    domainById[domain.id] = domain
}
var bindings = []
for (row in relationRows) {
    if (row.targetType != 'domain' || domainById[text(row.targetId)] == null) { continue }
    var config = json(row.config)
    var environment = config.environment == null ? 'DEV' : config.environment
    var category = config.category == null ? 'REFERENCE' : config.category
    var hash = DigestUtil.sha256Hex(text(row.sourceId) + '|' + text(row.sourceUpdatedAt) + '|' + environment + '|' +
        text(config.catalog) + '|' + text(config.schema) + '|' + text(config.prefix) + '|' + category)
    bindings.add({id:text(row.tid), databaseId:text(row.targetId), sourceId:text(row.sourceId),
        environment:environment, catalog:text(config.catalog), schema:text(config.schema), prefix:text(config.prefix),
        category:category, profile:text(row.profile), sourceName:text(row.sourceName),
        tableCount:tableCounts[text(row.sourceId)] == null ? 0 : tableCounts[text(row.sourceId)],
        state:row.sourceName == null ? 'INVALID' : config.checkedHash == hash ? 'VALID' : 'NOT_CHECKED',
        checkedHash:config.checkedHash, checkedAt:config.checkedAt, currentHash:hash,
        version:row.version, createdAt:row.createdAt, updatedAt:row.updatedAt})
}
var classes = []
for (row in classRows) {
    classes.add({id:text(row.tid), name:row.name, code:text(row.classCode), version:row.version,
        createdAt:row.createdAt, updatedAt:row.updatedAt})
}
return {layers:layers, databases:databases, bindings:bindings, classifications:classes}
