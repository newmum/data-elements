{
  "properties": {},
  "id": "fa237a4aca795e95b6a1086b2e3ed012",
  "script": null,
  "groupId": "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name": "07.planning-command",
  "createTime": null,
  "updateTime": 1790989934805,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/planning-command",
  "description": null,
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "input",
      "value": null,
      "description": null,
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}
================================
// 02.维护共享数仓规划：沿用字典层/域与单节点主数据源关联，不执行物理迁库。
import tenantRuntime
import jsons
import cn.hutool.core.util.IdUtil
import cn.hutool.crypto.digest.DigestUtil
import cn.dev33.satoken.stp.StpUtil
import '@/resource-center/planning-read' as readPlanning

var tenantId = tenantRuntime.id()
var actorId = StpUtil.getLoginIdAsString()
var text = value => value == null ? '' : (value + '').trim()
var payload = input.body == null ? {} : input.body
var kind = text(input.kind)
var id = text(input.id)
var action = text(input.action)
var method = text(input.method).toUpperCase()
var now = new Date()
var required = (value, name, max) => {
    var result = text(value)
    if (result == '' || result.length() > max) { exit 400, name + '不能为空或超过长度限制' }
    return result
}
var identifier = value => {
    var result = text(value)
    if (!result.matches('[A-Za-z][A-Za-z0-9_]{0,62}')) { exit 400, '技术代码须以字母开头，仅包含字母、数字和下划线' }
    return result
}
var current = readPlanning({})
var find = (list, key) => {
    for (item in list) { if (item.id == key) { return item } }
    exit 404, '记录不存在或不属于当前租户'
}
var checkVersion = row => {
    if (input.version == null || text(input.version) != text(row.version)) { exit 409, '记录已更新，请刷新后重试' }
}
if (kind == 'bindings') {
    var targetType = text(payload.targetType)
    if (targetType == '') { targetType = 'domain' }
    if (!['layer','domain'].contains(targetType)) { exit 400, '不支持的绑定节点类型' }
    var old = id == '' ? null : find(current.bindings, id)
    var targetId = text(payload.targetId)
    if (targetId == '') { targetId = old == null ? text(payload.databaseId) : old.databaseId }
    var target = targetType == 'layer' ? find(current.layers, targetId) : find(current.databases, targetId)
    if (target.state != 'ACTIVE') { exit 409, '该规划节点已归档' }
    if (old != null && old.databaseId != targetId) { exit 409, '绑定节点与原记录不一致' }
    var relation = db.selectOne("""
        SELECT tid, datasource_id AS source_id, configuration_json AS config,
               revision_no AS version
          FROM dwm_center_layer_source_t
         WHERE tenant_id = #{tenantId} AND target_type = #{targetType}
           AND target_id = #{targetId} AND is_del = 0
         ORDER BY updated_time DESC, tid DESC LIMIT 1
    """)
    if (input.version != null && (relation == null || text(input.version) != text(relation.version))) {
        exit 409, '绑定已更新，请刷新后重试'
    }
    if (action == 'check') {
        if (targetType != 'domain' || old == null) { exit 400, '仅支持校验分库绑定' }
        if (old.category != 'RELATIONAL') { exit 400, '仅可核对已启用物化的分库' }
        var config = {environment: old.environment, catalog: old.catalog, schema: old.schema,
            prefix: old.prefix, category: old.category, checkedHash: old.currentHash, checkedAt: now}
        if (old.catalog == '' && old.schema == '') { exit 400, '请先设置数据库或 Schema' }
        for (other in current.bindings) {
            if (other.id != id && other.category != 'REFERENCE' && old.category != 'REFERENCE' &&
                other.sourceId == old.sourceId && other.environment == old.environment &&
                other.catalog == old.catalog && other.schema == old.schema) { exit 409, '该命名空间已由另一分库管理' }
        }
        var expected = old.version
        var changed = db.update("""
            UPDATE dwm_center_layer_source_t SET configuration_json = #{configJson},
                   revision_no = revision_no + 1, updated_time = #{now}
             WHERE tid = #{id} AND tenant_id = #{tenantId} AND is_del = 0 AND revision_no = #{expected}
        """, {configJson: jsons.toJSONStr(config), now: now, id: id, tenantId: tenantId, expected: expected})
        if (changed != 1) { exit 409, '绑定配置已更新，请重新检查' }
        return {id: id, checked: true, connectionTested: false}
    }
    if (action != '' || !['POST','PUT'].contains(method)) { exit 400, '不支持的绑定操作' }
    var sourceId = text(payload.sourceId)
    if (sourceId.length() > 32) { exit 400, '数据源标识过长' }
    if (sourceId == '') {
        if (relation == null) { return {saved: true, cleared: true} }
        var removed = db.update("""
            UPDATE dwm_center_layer_source_t SET is_del = 1, revision_no = revision_no + 1,
                   updated_time = #{now}
             WHERE tid = #{relation.tid} AND tenant_id = #{tenantId}
               AND revision_no = #{relation.version} AND is_del = 0
        """)
        if (removed != 1) { exit 409, '绑定已更新，请刷新后重试' }
        return {id: text(relation.tid), saved: true, cleared: true}
    }
    var source = db.selectOne("""
        SELECT tid, db_type AS profile FROM db_datasource_t
         WHERE tid = #{sourceId} AND tenant_id = #{tenantId} AND is_del = 0
    """)
    if (source == null) { exit 404, '共享数据源不存在' }
    var configurationSupplied = payload.environment != null || payload.catalog != null || payload.schema != null ||
        payload.prefix != null || payload.category != null
    if (relation != null && text(relation.sourceId) == sourceId && !configurationSupplied) {
        return {id: text(relation.tid), saved: true, unchanged: true}
    }
    var previous = relation == null || text(relation.sourceId) != sourceId ? {} : jsons.toJSONObject(relation.config == null ? '{}' : relation.config)
    var environment = text(payload.environment == null ? previous.environment : payload.environment)
    if (environment == '') { environment = 'DEV' }
    if (!['DEV','TEST','PROD'].contains(environment)) { exit 400, '请选择有效环境' }
    var catalog = text(payload.catalog == null ? previous.catalog : payload.catalog)
    var schema = text(payload.schema == null ? previous.schema : payload.schema)
    var prefix = text(payload.prefix == null ? previous.prefix : payload.prefix)
    for (part in [catalog, schema]) { if (part != '' && !part.matches('[A-Za-z0-9_-]{1,128}')) { exit 400, '命名空间格式无效' } }
    if (prefix != '' && !prefix.matches('[A-Za-z_][A-Za-z0-9_]{0,31}')) { exit 400, '表名前缀格式无效' }
    var category = targetType == 'layer' || target.mode == 'REFERENCE' ? 'REFERENCE' :
        text(payload.category == null ? previous.category : payload.category)
    if (category == '') { category = 'REFERENCE' }
    if (!['RELATIONAL','REFERENCE'].contains(category)) { exit 400, '请选择关系型数仓或外部引用' }
    var config = {environment: environment, catalog: catalog, schema: schema, prefix: prefix, category: category}
    var configJson = jsons.toJSONStr(config)
    var node = db.selectOne("""
        SELECT d.tid FROM sym_dict_t d JOIN sym_dict_t p ON p.tid = d.parent_id AND p.tenant_id = d.tenant_id
         WHERE d.tenant_id = #{tenantId} AND d.is_del = 0 AND p.is_del = 0
           AND p.dict_code = 'nodeType' AND d.dict_name = '数据中心' LIMIT 1
    """)
    if (node == null) { exit 409, '缺少数据中心节点字典配置' }
    return db.transaction(() => {
        if (relation == null) {
            id = IdUtil.getSnowflakeNextIdStr()
            db.table('dwm_center_layer_source_t').primary('tid').insert({tid: id, tenant_id: tenantId,
                target_id: targetId, target_type: targetType, layer_code: target.code, datasource_id: sourceId,
                configuration_json: configJson, revision_no: 1, created_time: now, updated_time: now, is_del: 0})
        } else {
            id = text(relation.tid)
            var changed = db.table('dwm_center_layer_source_t').where().eq('tid', id).eq('tenant_id', tenantId)
                .eq('revision_no', relation.version).eq('is_del', 0).update({datasource_id: sourceId,
                    layer_code: target.code, configuration_json: configJson,
                    revision_no: relation.version + 1, updated_time: now})
            if (changed != 1) { exit 409, '绑定已被其他操作修改' }
        }
        db.table('db_datasource_t').where().eq('tenant_id', tenantId).eq('tid', sourceId).eq('is_del', 0)
            .update({node_id: node.tid, updated_time: now})
        return {id: id, saved: true}
    })
}
if (!['layers','databases','classifications'].contains(kind)) { exit 400, '不支持的规划对象' }
var list = kind == 'layers' ? current.layers : kind == 'databases' ? current.databases : current.classifications
var old = id == '' ? null : find(list, id)
if (old != null) { checkVersion(old) }
if (action == 'move' && kind == 'layers') {
    var nextOrder = old.order + (payload.direction == 'up' ? -1 : 1)
    if (nextOrder < 0 || nextOrder >= list.size()) { exit 409, '已经位于边界' }
    return db.transaction(() => {
        var neighbor = list[nextOrder]
        var moved = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('tid', id).eq('revision_no', old.version)
            .eq('is_del', 0).update({sort_no: neighbor.sortNo, revision_no: old.version + 1, updated_time: now})
        var swapped = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('tid', neighbor.id).eq('revision_no', neighbor.version)
            .eq('is_del', 0).update({sort_no: old.sortNo, revision_no: neighbor.version + 1, updated_time: now})
        if (moved != 1 || swapped != 1) { exit 409, '分层顺序已更新，请刷新后重试' }
        return {id: id, moved: true}
    })
}
if (method == 'DELETE' || action == 'archive') {
    if (old == null) { exit 404, '记录不存在' }
    if (kind == 'layers' && old.builtIn) { exit 409, '默认分层不可删除' }
    var child = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('parent_id', id).eq('is_del', 0).count()
    var binding = db.table('dwm_center_layer_source_t').where().eq('tenant_id', tenantId).eq('target_id', id).eq('is_del', 0).count()
    if (child > 0 || binding > 0) { exit 409, '该规划仍有子节点或存储绑定，请先处理引用' }
    if (kind == 'databases') {
        var used = db.selectOne("""SELECT COUNT(*) AS total FROM res_logical_model
            WHERE tenant_id = #{tenantId} AND is_del = 0 AND database_id = #{id}""")
        if (used.total > 0) { exit 409, '该分库仍被逻辑模型引用' }
    }
    if (kind == 'classifications') {
        var used = db.selectOne("""SELECT COUNT(*) AS total FROM da_prop_t
            WHERE tenant_id = #{tenantId} AND is_del = 0 AND prop_name = 'resourceCenterDraft'
              AND JSON_VALID(prop_value) AND JSON_UNQUOTE(JSON_EXTRACT(prop_value, '$.classificationId')) = #{id}""")
        if (used.total > 0) { exit 409, '该分类已被资源目录引用' }
    }
    db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('tid', id).eq('revision_no', old.version)
        .update({is_del: 1, revision_no: old.version + 1, updated_time: now, updated_by: actorId})
    return {id: id, removed: true}
}
if (action != '' || !['POST','PUT'].contains(method)) { exit 400, '不支持的规划动作' }
var rootCode = kind == 'classifications' ? 'resourceCatalogClass' : 'dataSourceType'
if (kind == 'databases') { find(current.layers, text(payload.layerId)) }
var parentId = kind == 'databases' ? text(payload.layerId) : ''
var parent = kind == 'databases' ? db.selectOne("""
    SELECT tid AS id, dict_code AS code, tree_path, level_no
      FROM sym_dict_t WHERE tenant_id = #{tenantId} AND tid = #{parentId} AND is_del = 0 LIMIT 1
""") : db.selectOne("""
    SELECT tid AS id, dict_code AS code, tree_path, level_no
      FROM sym_dict_t WHERE tenant_id = #{tenantId} AND dict_code = #{rootCode} AND is_del = 0 LIMIT 1
""")
if (parent == null) { exit 409, '缺少规划根字典，请先初始化共享字典配置' }
var name = required(payload.name, '名称', 100)
var code = identifier(payload.code)
if (kind == 'layers') { code = code.toUpperCase() }
if (old != null && kind == 'layers' && old.builtIn && code != old.code) { exit 409, '默认层代码不可修改' }
parentId = text(parent.id)
var duplicate = db.selectOne("""SELECT tid FROM sym_dict_t WHERE tenant_id = #{tenantId}
    AND parent_id = #{parentId} AND LOWER(dict_code) = LOWER(#{code}) AND is_del = 0 AND tid != #{id} LIMIT 1""")
if (duplicate != null) { exit 409, '同层级技术代码已存在' }
var config = {owner: text(payload.owner), domain: text(payload.domain),
    mode: kind == 'databases' && (parent.code == 'DST' || payload.mode == 'REFERENCE') ? 'REFERENCE' : 'MANAGED', state: 'ACTIVE'}
var values = {dict_name: name, dict_code: code, parent_id: parentId, dict_desc: text(payload.purpose),
    planning_config_json: jsons.toJSONStr(config), updated_time: now, updated_by: actorId}
if (old == null) {
    id = IdUtil.getSnowflakeNextIdStr()
    var siblings = db.selectOne("""SELECT COALESCE(MAX(sort_no), 0) AS max_sort FROM sym_dict_t
        WHERE tenant_id = #{tenantId} AND parent_id = #{parentId} AND is_del = 0""")
    values.putAll({tid: id, tenant_id: tenantId, sort_no: siblings.maxSort + 1,
        level_no: parent.levelNo + 1, tree_path: text(parent.treePath) + '.' + id,
        revision_no: 1, created_by: actorId, created_time: now, is_del: 0})
    db.table('sym_dict_t').primary('tid').insert(values)
} else {
    values.revision_no = old.version + 1
    values.tree_path = text(parent.treePath) + '.' + id
    values.level_no = parent.levelNo + 1
    if (kind == 'databases' && old.layerId != parentId) {
        var nextSibling = db.selectOne("""SELECT COALESCE(MAX(sort_no), 0) AS max_sort FROM sym_dict_t
            WHERE tenant_id = #{tenantId} AND parent_id = #{parentId} AND is_del = 0""")
        values.sort_no = nextSibling.maxSort + 1
    }
    var changed = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('tid', id)
        .eq('revision_no', old.version).eq('is_del', 0).update(values)
    if (changed != 1) { exit 409, '规划已被修改，请刷新后重试' }
}
return {id: id, saved: true}
