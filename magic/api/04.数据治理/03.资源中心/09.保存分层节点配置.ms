{
  "properties": {},
  "id": "dwm_center_node_save_01",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "09.保存分层节点配置",
  "createTime": 1785067200000,
  "updateTime": 1785067200000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/node/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"layerId\": \"现有分层ID\",\n  \"name\": \"基础层 DWD\",\n  \"datasourceId\": \"现有数据源ID\",\n  \"domains\": [\n    {\"targetId\": \"现有分域ID\", \"dictName\": \"标准库\", \"dictCode\": \"standard\", \"sortNo\": 0, \"datasourceId\": \"\"}\n  ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"success\": true\n}",
  "description": "在当前租户内一次保存分层和提交的直接分域，校验节点、数据源和同级编码后，在同一事务写入名称、分域编码、排序及数据源关联。layerId、name、datasourceId、domains 用于整层保存；兼容 targetType、targetId、name、datasourceId、layerCode、applyToAllDomains 单节点请求。空数据源标识解绑，未提交的分域保持原配置。成功返回 success。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import tableRead
import cn.hutool.core.util.IdUtil
import java.util.List

// POST /dwm/center/node/save 保存当前租户的数仓分层和分域配置，成功返回 {success: true}。
// 整层请求提交 layerId、可选 name 和 datasourceId，以及最多 500 个直接分域。每个分域提交现有 ID、名称、编码、排序和数据源 ID。
// 单节点请求沿用 targetType、targetId、name、datasourceId、layerCode 和 applyToAllDomains。空数据源标识表示解绑。
// 校验全部节点和数据源后，字典、绑定及数据源节点标记在同一事务内更新。未提交的分域不参与保存。
var tenantId = tenantRuntime.id()
var textOf = value => value == null ? '' : (value + '').trim()
if (textOf(tenantId) == '') {
    exit 401, '当前租户无效'
}
var batch = body?.layerId != null || body?.domains != null
var targetType = batch ? 'layer' : textOf(body?.targetType)
var targetId = textOf(batch ? body?.layerId : body?.targetId)
var submitted = batch ? body?.domains : []
var name = textOf(body?.name)
var rootSourceId = textOf(body?.datasourceId)
var applyToAllDomains = !batch && targetType == 'layer' && rootSourceId != '' && body?.applyToAllDomains == true
if (!['layer', 'domain'].contains(targetType) || targetId == '' || targetId.length() > 32) {
    exit 400, '请选择有效的分层或分域节点'
}
if (!(submitted instanceof List) || submitted.size() > 500) {
    exit 400, '整层保存最多支持 500 个分域'
}
if (name.length() > 255 || rootSourceId.length() > 32) {
    exit 400, '节点名称或数据源标识过长'
}

var dictionary = () => db.camel().table('sym_dict_t').columns('tid', 'parent_id', 'dict_name', 'dict_code', 'sort_no', 'revision_no')
    .where().eq('tenant_id', tenantId).eq('is_del', 0)
var firstDictionaryId = (code, name) => {
    var dict = tableRead.from('sym_dict_t', 'd', tenantId)
    var id = tableRead.column('d', 'tid', 'TEXT')
    dict.where(tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0))
    if (name == null) {
        dict.where(tableRead.equal(tableRead.column('d', 'dict_code', 'TEXT'), code))
    } else {
        dict.leftJoin('sym_dict_t', 'p', 'tid', 'parent_id', [])
        dict.where(tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0))
        dict.where(tableRead.equal(tableRead.column('p', 'dict_code', 'TEXT'), code))
        dict.where(tableRead.equal(tableRead.column('d', 'dict_name', 'TEXT'), name))
    }
    var rows = dict.rows({tid: id}, id, 'asc', 1)
    return rows.size() == 0 ? null : rows[0].tid
}

// 按真实字典父子关系核验目标，不能使用客户端层级或编码定位其他字典。
var rootId = firstDictionaryId('dataSourceType', null)
var node = dictionary().eq('tid', targetId).selectOne()
if (node == null || textOf(node.tid) != targetId) {
    exit 404, '分层节点不存在'
}
var parentId = textOf(node.parentId)
var layer = targetType == 'layer' ? node : parentId == '' ? null : dictionary().eq('tid', parentId).selectOne()
if (rootId == null || layer == null || textOf(layer.tid) == textOf(rootId) || textOf(layer.parentId) != textOf(rootId)
    || (targetType == 'domain' && textOf(layer.tid) != parentId)) {
    exit 404, '节点不属于当前租户的数仓规划'
}

var requestedById = {}
for (domain in submitted) {
    var id = textOf(domain?.targetId)
    var dictName = textOf(domain?.dictName)
    var dictCode = textOf(domain?.dictCode)
    var sort = textOf(domain?.sortNo)
    var sourceId = textOf(domain?.datasourceId)
    if (id == '' || id.length() > 32 || requestedById[id] != null) {
        exit 400, '分域标识无效或重复'
    }
    if (dictName == '' || dictName.length() > 255 || dictCode == '' || dictCode.length() > 255
        || domain.datasourceId == null || sourceId.length() > 32 || !sort.matches('^-?[0-9]{1,10}$')) {
        exit 400, '分域名称、编码、排序或数据源无效'
    }
    var sortNumber = sort::long
    if (sortNumber < -2147483647 - 1 || sortNumber > 2147483647) {
        exit 400, '分域排序超出允许范围'
    }
    requestedById[id] = {name: dictName, code: dictCode, sortNo: sortNumber::int, sourceId: sourceId}
}

var mainPatch = name == '' ? {} : {dict_name: name}
var targets = [{node: node, type: targetType, patch: mainPatch, sourceId: rootSourceId,
    bind: !batch || body?.datasourceId != null, code: batch ? textOf(node.dictCode) : textOf(body?.layerCode)}]
if (submitted.size() > 0 || applyToAllDomains) {
    // 同批读取同级节点，校验提交项并检查它们与未提交节点的最终编码是否冲突。
    var page = dictionary().eq('parent_id', node.tid).orderBy('tid', 'asc').page(501, 0)
    var children = page.list == null ? [] : page.list
    if (children.size() > 500) {
        exit 400, '该层分域超过 500 个，无法整层保存'
    }
    var found = 0
    var seenCodes = {}
    for (child in children) {
        var requested = requestedById[child.tid]
        if (requested != null) {
            found = found + 1
            targets.add({node: child, type: 'domain', sourceId: requested.sourceId, bind: true, code: requested.code,
                patch: {dict_name: requested.name, dict_code: requested.code, sort_no: requested.sortNo}})
        } else if (applyToAllDomains) {
            targets.add({node: child, type: 'domain', sourceId: rootSourceId, bind: true, code: textOf(child.dictCode), patch: {}})
        }
        if (batch) {
            var finalCode = requested == null ? textOf(child.dictCode) : requested.code
            var normalizedCode = finalCode.toLowerCase()
            if (normalizedCode != '') {
                if (seenCodes[normalizedCode] != null && (seenCodes[normalizedCode] || requested != null)) {
                    exit 409, '同一分层下的分域编码不能重复'
                }
                seenCodes[normalizedCode] = requested != null
            }
        }
    }
    if (found != submitted.size()) {
        exit 404, '部分分域不存在或不属于当前分层'
    }
}

// 绑定和数据源各做一次有界读取，入参 ID 必须与实际同租户记录逐项对应。
var relationTargetIds = []
var sourceIds = []
var seenSources = {}
for (target in targets) {
    if (!target.bind) {
        continue
    }
    relationTargetIds.add(target.node.tid)
    if (target.sourceId != '') {
        if (target.code.length() > 32) {
            exit 400, '关联数据源的分层或分域编码最多 32 个字符'
        }
        if (seenSources[target.sourceId] == null) {
            seenSources[target.sourceId] = true
            sourceIds.add(target.sourceId)
        }
    }
}
var dataCenterId = null
if (sourceIds.size() > 0) {
    if (sourceIds.size() > 500) {
        exit 400, '整层保存最多支持 500 个数据源'
    }
    var sourceRows = db.camel().table('db_datasource_t').column('tid').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', sourceIds).select()
    var sourceById = {}
    for (source in sourceRows) {
        sourceById[source.tid] = true
    }
    for (sourceId in sourceIds) {
        if (sourceById[sourceId] == null) {
            exit 404, '部分关联数据源不存在或不属于当前租户'
        }
    }
    dataCenterId = firstDictionaryId('nodeType', '数据中心')
    if (dataCenterId == null) {
        exit 500, '缺少数据中心节点字典项，无法保存关联数据源'
    }
}

var activeBindings = {}
var deletedBindings = {}
if (relationTargetIds.size() > 0) {
    var relationQuery = db.camel().table('dwm_center_layer_source_t')
        .columns('tid', 'target_id', 'target_type', 'is_del', 'revision_no').where()
        .eq('tenant_id', tenantId).in('target_type', ['layer', 'domain']).in('is_del', [0, 1])
    var relations = relationQuery.in('target_id', relationTargetIds).select()
    for (relation in relations) {
        var key = relation.targetType + ':' + relation.targetId
        var byKey = relation.isDel == 0 ? activeBindings : deletedBindings
        if (byKey[key] != null) {
            exit 409, '节点存在重复绑定记录，请先核对历史配置'
        }
        byKey[key] = relation
    }
}

var now = new Date()
var bindingVersion = (relation, deleted) => {
    var table = db.table('dwm_center_layer_source_t')
    if (deleted == 1) {
        table.withBlank()
    }
    return table.where().eq('tenant_id', tenantId).eq('tid', relation.tid).eq('is_del', deleted)
        .eq('revision_no', relation.revisionNo)
}
return db.transaction(() => {
    // 先更新分层版本以协调同层整批保存，再按各节点快照更新分域。任一版本冲突都回滚整批。
    for (target in targets) {
        var key = target.type + ':' + target.node.tid
        var active = activeBindings[key]
        var changesBinding = target.bind && (target.sourceId != '' || active != null)
        var guardsChildren = batch && target.type == 'layer' && submitted.size() > 0
        if (target.patch.size() > 0 || changesBinding || guardsChildren) {
            target.patch.revision_no = target.node.revisionNo + 1
            target.patch.updated_time = now
            var changed = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('is_del', 0)
                .eq('tid', target.node.tid).eq('parent_id', target.node.parentId).eq('revision_no', target.node.revisionNo)
                .update(target.patch)
            if (changed != 1) {
                exit 409, '分层或分域已修改，请刷新后重试'
            }
        }
    }
    for (target in targets) {
        if (!target.bind) {
            continue
        }
        var key = target.type + ':' + target.node.tid
        var active = activeBindings[key]
        var deleted = deletedBindings[key]
        var changed = 1
        if (target.sourceId == '') {
            if (active != null) {
                if (deleted != null) {
                    exit 409, '该节点存在历史解绑记录，请先核对后再解绑'
                }
                changed = bindingVersion(active, 0).update({is_del: 1, revision_no: active.revisionNo + 1, updated_time: now})
            }
        } else if (active != null) {
            changed = bindingVersion(active, 0).update({layer_code: target.code, datasource_id: target.sourceId,
                revision_no: active.revisionNo + 1, updated_time: now})
        } else if (deleted != null) {
            // 复用已解绑记录，保留标识与版本链。新绑定按原约定重新配置物化校验。
            changed = bindingVersion(deleted, 1).update({is_del: 0, layer_code: target.code == '' ? null : target.code, datasource_id: target.sourceId,
                configuration_json: null, revision_no: deleted.revisionNo + 1, updated_time: now})
        } else {
            db.table('dwm_center_layer_source_t').primary('tid').insert({tid: IdUtil.getSnowflakeNextIdStr(), tenant_id: tenantId,
                target_id: target.node.tid, target_type: target.type, layer_code: target.code, datasource_id: target.sourceId,
                is_del: 0, revision_no: 1, created_time: now, updated_time: now})
        }
        if (changed != 1) {
            exit 409, '节点绑定已修改，请刷新后重试'
        }
    }

    // 被选中的数据源批量标记为数据中心，解绑不会修改之前的数据源标记。
    if (sourceIds.size() > 0) {
        var marked = db.table('db_datasource_t').where().eq('tenant_id', tenantId).eq('is_del', 0)
            .in('tid', sourceIds).update({node_id: dataCenterId, updated_time: now})
        if (marked != sourceIds.size()) {
            exit 409, '部分数据源已修改，请刷新后重试'
        }
    }
    return {success: true}
})
