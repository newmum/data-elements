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
import tenantRuntime
import tableRead
import jsons
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
import '@/resource-center/planning-read' as readPlanning

// 共享函数 @/resource-center/planning-command。由资源中心命令网关传入 kind、method、id、action、version 和 body。
// 维护当前租户的数仓分层、分域、目录分类及存储绑定，返回保存、解绑、核对、移动或归档结果。
// 已有记录按版本更新。分层交换及绑定与数据源节点标记在各自的事务内提交。
// 绑定核对只检查配置，归档只更新规划记录。函数不连接目标数据库或创建物理库。
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
    if (result == '' || result.length() > max) {
        exit 400, name + '不能为空或超过长度限制'
    }
    return result
}
var identifier = value => {
    var result = text(value)
    if (!result.matches('[A-Za-z][A-Za-z0-9_]{0,62}')) {
        exit 400, '技术代码须以字母开头，仅包含字母、数字和下划线'
    }
    return result
}
var current = readPlanning({})
var find = (list, key) => {
    for (item in list) {
        if (item.id == key) {
            return item
        }
    }
    exit 404, '记录不存在或不属于当前租户'
}
var checkVersion = row => {
    if (input.version == null || text(input.version) != text(row.version)) {
        exit 409, '记录已更新，请刷新后重试'
    }
}
var firstPageRow = query => {
    var page = query.page(1, 0)
    return page.list == null || page.list.size() == 0 ? null : page.list[0]
}
var activeTable = table => db.table(table).where().eq('tenant_id', tenantId).eq('is_del', 0)
var versioned = (table, recordId, expected) => activeTable(table).eq('tid', recordId).eq('revision_no', expected)

if (kind == 'bindings') {
    var targetType = text(payload.targetType)
    if (targetType == '') {
        targetType = 'domain'
    }
    if (!['layer', 'domain'].contains(targetType)) {
        exit 400, '不支持的绑定节点类型'
    }
    var old = id == '' ? null : find(current.bindings, id)
    var targetId = text(payload.targetId)
    if (targetId == '') {
        targetId = old == null ? text(payload.databaseId) : old.databaseId
    }
    var target = targetType == 'layer' ? find(current.layers, targetId) : find(current.databases, targetId)
    if (target.state != 'ACTIVE') {
        exit 409, '该规划节点已归档'
    }
    if (old != null && old.databaseId != targetId) {
        exit 409, '绑定节点与原记录不一致'
    }
    // 历史记录出现同一目标的多条有效绑定时，沿用更新时间和唯一标识选择最近一条。
    var bindingTable = 'dwm_center_layer_source_t'
    var relation = firstPageRow(db.camel().table(bindingTable)
        .columns('tid', 'datasource_id', 'configuration_json', 'revision_no').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).eq('target_type', targetType).eq('target_id', targetId)
        .orderBy('updated_time', 'desc').orderBy('tid', 'desc'))
    if (input.version != null && (relation == null || text(input.version) != text(relation.revisionNo))) {
        exit 409, '绑定已更新，请刷新后重试'
    }
    if (action == 'check') {
        if (targetType != 'domain' || old == null) {
            exit 400, '仅支持校验分库绑定'
        }
        if (old.category != 'RELATIONAL') {
            exit 400, '仅可核对已启用物化的分库'
        }
        var config = {environment: old.environment, catalog: old.catalog, schema: old.schema,
            prefix: old.prefix, category: old.category, checkedHash: old.currentHash, checkedAt: now}
        if (old.catalog == '' && old.schema == '') {
            exit 400, '请先设置数据库或 Schema'
        }
        for (other in current.bindings) {
            if (other.id != id && other.category != 'REFERENCE' && old.category != 'REFERENCE' &&
                other.sourceId == old.sourceId && other.environment == old.environment &&
                other.catalog == old.catalog && other.schema == old.schema) { exit 409, '该命名空间已由另一分库管理' }
        }
        var expected = old.version
        var changed = versioned(bindingTable, id, expected).update({
            configuration_json: jsons.toJSONStr(config), revision_no: expected + 1, updated_time: now
        })
        if (changed != 1) {
            exit 409, '绑定配置已更新，请重新检查'
        }
        return {id: id, checked: true, connectionTested: false}
    }
    if (action != '' || !['POST', 'PUT'].contains(method)) {
        exit 400, '不支持的绑定操作'
    }
    var sourceId = text(payload.sourceId)
    if (sourceId.length() > 32) {
        exit 400, '数据源标识过长'
    }
    if (sourceId == '') {
        if (relation == null) {
            return {saved: true, cleared: true}
        }
        var removed = versioned(bindingTable, relation.tid, relation.revisionNo)
            .update({is_del: 1, revision_no: relation.revisionNo + 1, updated_time: now})
        if (removed != 1) {
            exit 409, '绑定已更新，请刷新后重试'
        }
        return {id: text(relation.tid), saved: true, cleared: true}
    }
    var source = db.table('db_datasource_t').column('tid').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).eq('tid', sourceId).selectOne()
    if (source == null) {
        exit 404, '共享数据源不存在'
    }
    var configurationSupplied = payload.environment != null || payload.catalog != null || payload.schema != null ||
        payload.prefix != null || payload.category != null
    if (relation != null && text(relation.datasourceId) == sourceId && !configurationSupplied) {
        return {id: text(relation.tid), saved: true, unchanged: true}
    }
    var previous = relation == null || text(relation.datasourceId) != sourceId || relation.configurationJson == null
        ? {} : jsons.toJSONObject(relation.configurationJson)
    var environment = text(payload.environment == null ? previous.environment : payload.environment)
    if (environment == '') {
        environment = 'DEV'
    }
    if (!['DEV', 'TEST', 'PROD'].contains(environment)) {
        exit 400, '请选择有效环境'
    }
    var catalog = text(payload.catalog == null ? previous.catalog : payload.catalog)
    var schema = text(payload.schema == null ? previous.schema : payload.schema)
    var prefix = text(payload.prefix == null ? previous.prefix : payload.prefix)
    for (part in [catalog, schema]) {
        if (part != '' && !part.matches('[A-Za-z0-9_-]{1,128}')) {
            exit 400, '命名空间格式无效'
        }
    }
    if (prefix != '' && !prefix.matches('[A-Za-z_][A-Za-z0-9_]{0,31}')) {
        exit 400, '表名前缀格式无效'
    }
    var category = targetType == 'layer' || target.mode == 'REFERENCE' ? 'REFERENCE' :
        text(payload.category == null ? previous.category : payload.category)
    if (category == '') {
        category = 'REFERENCE'
    }
    if (!['RELATIONAL', 'REFERENCE'].contains(category)) {
        exit 400, '请选择关系型数仓或外部引用'
    }
    var config = {environment: environment, catalog: catalog, schema: schema, prefix: prefix, category: category}
    var configJson = jsons.toJSONStr(config)
    var nodes = tableRead.from('sym_dict_t', 'd', tenantId)
    var nodeId = tableRead.column('d', 'tid', 'TEXT')
    nodes.leftJoin('sym_dict_t', 'p', 'tid', 'parent_id', [])
    nodes.where(tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0))
    nodes.where(tableRead.equal(tableRead.column('p', 'dict_code', 'TEXT'), 'nodeType'))
    nodes.where(tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0))
    nodes.where(tableRead.equal(tableRead.column('d', 'dict_name', 'TEXT'), '数据中心'))
    var dataCenter = nodes.rows({tid: nodeId}, nodeId, 'asc', 1)
    if (dataCenter.size() == 0) {
        exit 409, '缺少数据中心节点字典配置'
    }

    // 关联写入和节点标记共用事务。版本冲突中止整个操作。
    return db.transaction(() => {
        if (relation == null) {
            id = IdUtil.getSnowflakeNextIdStr()
            db.table(bindingTable).primary('tid').insert({tid: id, tenant_id: tenantId,
                target_id: targetId, target_type: targetType, layer_code: target.code, datasource_id: sourceId,
                configuration_json: configJson, revision_no: 1, created_time: now, updated_time: now, is_del: 0})
        } else {
            id = text(relation.tid)
            var changed = versioned(bindingTable, id, relation.revisionNo).update({datasource_id: sourceId,
                    layer_code: target.code, configuration_json: configJson,
                    revision_no: relation.revisionNo + 1, updated_time: now})
            if (changed != 1) {
                exit 409, '绑定已被其他操作修改'
            }
        }
        activeTable('db_datasource_t').eq('tid', sourceId)
            .update({node_id: dataCenter[0].tid, updated_time: now})
        return {id: id, saved: true}
    })
}
if (!['layers', 'databases', 'classifications'].contains(kind)) {
    exit 400, '不支持的规划对象'
}
var list = kind == 'layers' ? current.layers : kind == 'databases' ? current.databases : current.classifications
var old = id == '' ? null : find(list, id)
if (old != null) {
    checkVersion(old)
}
if (action == 'move' && kind == 'layers') {
    var nextOrder = old.order + (payload.direction == 'up' ? -1 : 1)
    if (nextOrder < 0 || nextOrder >= list.size()) {
        exit 409, '已经位于边界'
    }
    // 两个相邻节点分别校验版本，任一更新失败时回滚整个顺序交换。
    return db.transaction(() => {
        var neighbor = list[nextOrder]
        var moved = versioned('sym_dict_t', id, old.version)
            .update({sort_no: neighbor.sortNo, revision_no: old.version + 1, updated_time: now})
        var swapped = versioned('sym_dict_t', neighbor.id, neighbor.version)
            .update({sort_no: old.sortNo, revision_no: neighbor.version + 1, updated_time: now})
        if (moved != 1 || swapped != 1) {
            exit 409, '分层顺序已更新，请刷新后重试'
        }
        return {id: id, moved: true}
    })
}
if (method == 'DELETE' || action == 'archive') {
    if (old == null) {
        exit 404, '记录不存在'
    }
    if (kind == 'layers' && old.builtIn) {
        exit 409, '默认分层不可删除'
    }
    // 活动子节点、绑定以及该类型的业务引用都会阻止归档。
    var child = activeTable('sym_dict_t').eq('parent_id', id).count()
    var binding = activeTable('dwm_center_layer_source_t').eq('target_id', id).count()
    if (child > 0 || binding > 0) {
        exit 409, '该规划仍有子节点或存储绑定，请先处理引用'
    }
    if (kind == 'databases') {
        if (activeTable('res_logical_model').eq('database_id', id).count() > 0) {
            exit 409, '该分库仍被逻辑模型引用'
        }
    }
    if (kind == 'classifications') {
        var props = tableRead.from('da_prop_t', 'p', tenantId)
        var propertyId = tableRead.column('p', 'tid', 'TEXT')
        var classificationId = tableRead.jsonText(tableRead.column('p', 'prop_value', 'LOB'), 'classificationId')
        props.where(tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0))
        props.where(tableRead.equal(tableRead.column('p', 'prop_name', 'TEXT'), 'resourceCenterDraft'))
        props.where(tableRead.equal(classificationId, id))
        if (props.rows({tid: propertyId}, propertyId, 'asc', 1).size() > 0) {
            exit 409, '该分类已被资源目录引用'
        }
    }
    var changed = versioned('sym_dict_t', id, old.version)
        .update({is_del: 1, revision_no: old.version + 1, updated_time: now, updated_by: actorId})
    if (changed != 1) {
        exit 409, '规划已被修改，请刷新后重试'
    }
    return {id: id, removed: true}
}
if (action != '' || !['POST', 'PUT'].contains(method)) {
    exit 400, '不支持的规划动作'
}
var rootCode = kind == 'classifications' ? 'resourceCatalogClass' : 'dataSourceType'
if (kind == 'databases') {
    find(current.layers, text(payload.layerId))
}
var parentId = kind == 'databases' ? text(payload.layerId) : ''
var parents = db.camel().table('sym_dict_t').columns('tid', 'dict_code', 'tree_path', 'level_no').where()
    .eq('tenant_id', tenantId).eq('is_del', 0)
var parent = kind == 'databases' ? parents.eq('tid', parentId).selectOne()
    : firstPageRow(parents.eq('dict_code', rootCode).orderBy('tid', 'asc'))
if (parent == null) {
    exit 409, '缺少规划根字典，请先初始化共享字典配置'
}
var name = required(payload.name, '名称', 100)
var code = identifier(payload.code)
if (kind == 'layers') {
    code = code.toUpperCase()
}
if (old != null && kind == 'layers' && old.builtIn && code != old.code) {
    exit 409, '默认层代码不可修改'
}
parentId = text(parent.tid)
// 技术代码在同一父节点下忽略大小写。新建时没有自身 ID 可排除。
var siblings = tableRead.from('sym_dict_t', 'd', tenantId)
var siblingId = tableRead.column('d', 'tid', 'TEXT')
siblings.where(tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0))
siblings.where(tableRead.equal(tableRead.column('d', 'parent_id', 'TEXT'), parentId))
siblings.where(tableRead.equal(tableRead.lower(tableRead.column('d', 'dict_code', 'TEXT')), code.toLowerCase()))
if (id != '') {
    siblings.where(tableRead.not(tableRead.equal(siblingId, id)))
}
if (siblings.rows({tid: siblingId}, siblingId, 'asc', 1).size() > 0) {
    exit 409, '同层级技术代码已存在'
}
var nextSortNo = () => {
    var children = tableRead.from('sym_dict_t', 'd', tenantId)
    var sortNo = tableRead.column('d', 'sort_no', 'NUMBER')
    children.where(tableRead.equal(tableRead.column('d', 'parent_id', 'TEXT'), parentId))
    children.where(tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0))
    var maximum = children.grouped({sortNo: tableRead.max(sortNo)}, [])[0].sortNo
    return maximum == null ? 1 : maximum + 1
}
var config = {owner: text(payload.owner), domain: text(payload.domain),
    mode: kind == 'databases' && (parent.dictCode == 'DST' || payload.mode == 'REFERENCE') ? 'REFERENCE' : 'MANAGED', state: 'ACTIVE'}
var values = {dict_name: name, dict_code: code, parent_id: parentId, dict_desc: text(payload.purpose),
    planning_config_json: jsons.toJSONStr(config), updated_time: now, updated_by: actorId}
if (old == null) {
    id = IdUtil.getSnowflakeNextIdStr()
    values.putAll({tid: id, tenant_id: tenantId, sort_no: nextSortNo(),
        level_no: parent.levelNo + 1, tree_path: text(parent.treePath) + '.' + id,
        revision_no: 1, created_by: actorId, created_time: now, is_del: 0})
    db.table('sym_dict_t').primary('tid').insert(values)
} else {
    values.revision_no = old.version + 1
    values.tree_path = text(parent.treePath) + '.' + id
    values.level_no = parent.levelNo + 1
    if (kind == 'databases' && old.layerId != parentId) {
        values.sort_no = nextSortNo()
    }
    var changed = versioned('sym_dict_t', id, old.version).update(values)
    if (changed != 1) {
        exit 409, '规划已被修改，请刷新后重试'
    }
}
return {id: id, saved: true}
