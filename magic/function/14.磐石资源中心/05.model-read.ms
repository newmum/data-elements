{
  "properties" : { },
  "id" : "7a5e47d9db00585f8d0feeefedcd6465",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "05.model-read",
  "createTime" : null,
  "updateTime" : 1791068403811,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/model-read",
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
import tenantRuntime
import cn.hutool.json.JSONUtil
import '@/resource-center/technical-read' as technicalRead

/**
 * 磐石逻辑模型读取。/resource-center/model-read
 * projectionOnly 为 true 时只返回配置；完整读取可传 entities 复用当前已读取的技术表结构。
 * 返回当前租户的模型、冻结版本、物化方案、共享表资源、数据项集和审计日志，只读。
 */

var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId == '') {
    exit 401, '无法识别当前租户'
}
var text = v => v == null ? '' : v + ''
var parse = v => text(v) == '' ? {} : JSONUtil.parse(v)
var rowsOf = page => page.list == null ? [] : page.list
var readConfiguration = (query, maximum, message) => {
    // 视图必须能判断是否超限，空配置统一返回数组。
    var page = query.page(maximum + 1, 0)
    var rows = rowsOf(page)
    if (page.total > maximum || rows.size() > maximum) {
        exit 400, message
    }
    return rows
}

var models = []
var modelLimitMessage = '模型配置超过视图上限，请缩小配置范围'
var modelRows = readConfiguration(db.camel().table('res_logical_model').columns(
    'tid', 'revision_no', 'created_time', 'updated_time', 'model_name', 'model_code', 'domain_name',
    'model_description', 'database_id', 'owner_name', 'model_type', 'model_status', 'design_json'
).where().eq('tenant_id', tenantId).eq('is_del', 0).orderBy('updated_time', 'desc').orderBy('tid'),
    1000, modelLimitMessage)
var revisionRows = readConfiguration(db.camel().table('res_logical_model_revision').columns(
    'tid', 'model_id', 'revision_number', 'content_json', 'note', 'content_hash', 'created_time'
).where().eq('tenant_id', tenantId).orderBy('revision_number').orderBy('tid'), 10000, modelLimitMessage)

// 一次读取当前租户的版本，再按模型归组，避免每个模型扫描整个版本集合。
var revisionsByModel = {}
for (revision in revisionRows) {
    var modelId = text(revision.modelId)
    if (revisionsByModel[modelId] == null) {
        revisionsByModel[modelId] = []
    }
    revisionsByModel[modelId].add(revision)
}
for (r in modelRows) {
    var design = parse(r.designJson)
    var frozen = []
    var revisions = revisionsByModel[text(r.tid)]
    for (v in revisions == null ? [] : revisions) {
        var snapshot = parse(v.contentJson)
        snapshot.id = text(v.tid)
        snapshot.number = v.revisionNumber
        snapshot.note = v.note
        snapshot.signature = v.contentHash
        snapshot.createdAt = v.createdTime
        frozen.add(snapshot)
    }
    models.add({
        id: text(r.tid), version: r.revisionNo, createdAt: r.createdTime, updatedAt: r.updatedTime,
        name: r.modelName, code: r.modelCode, domain: text(r.domainName), description: text(r.modelDescription),
        databaseId: r.databaseId, owner: text(r.ownerName), method: text(r.modelType),
        state: r.modelStatus == 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE',
        entities: design.entities == null ? [] : design.entities,
        relationships: design.relationships == null ? [] : design.relationships,
        nodes: design.nodes == null ? [] : design.nodes,
        outputEntityId: design.outputEntityId, fusion: design.fusion, frozen: frozen
    })
}
var plans = []
var planRows = readConfiguration(db.camel().table('res_materialization_plan').columns(
    'tid', 'revision_no', 'plan_status', 'plan_json', 'created_time', 'updated_time'
).where().eq('tenant_id', tenantId).eq('is_del', 0).isNotNull('plan_json')
    .orderBy('updated_time', 'desc').orderBy('tid'), 1000, '物化配置超过视图上限')
for (r in planRows) {
    var plan = parse(r.planJson)
    plan.id = text(r.tid)
    plan.version = r.revisionNo
    plan.state = r.planStatus
    plan.createdAt = r.createdTime
    plan.updatedAt = r.updatedTime
    plans.add(plan)
}
var projectionOnly = input != null && input.projectionOnly == true
var tables = []
if (!projectionOnly) {
    // 资源状态仍返回完整集合，单次 SQL 最多读 1000 行，以首批总数限定读取窗口。
    var resourceQuery = db.camel().table('db_table_t').columns(
        'tid', 'resource_revision_no', 'created_time', 'updated_time', 'revision', 'table_name_cn', 'table_name',
        'resource_domain_id', 'business_type', 'resource_owner', 'asset_desc', 'resource_plan_id', 'resource_state',
        'resource_model_id', 'resource_field_map'
    ).where().eq('tenant_id', tenantId).eq('is_del', 0).isNotNull('resource_domain_id')
        .orderBy('updated_time', 'desc').orderBy('tid')
    var windowSize = 1000
    var first = resourceQuery.page(windowSize, 0)
    tables.addAll(rowsOf(first))
    var offset = windowSize
    while (offset < first.total) {
        var window = rowsOf(resourceQuery.page(windowSize, offset))
        if (window.size() == 0) {
            break
        }
        tables.addAll(window)
        offset = offset + windowSize
    }
}
var ids = []
for (r in tables) {
    ids.add(text(r.tid))
}
var entities = input != null && input.entities != null ? input.entities : (ids.size() == 0 ? [] : technicalRead({entityIds:ids}).entities)
var entityMap = {}
for (e in entities) {
    entityMap[e.id] = e
}
var resources = []
for (r in tables) {
    var e = entityMap[text(r.tid)]
    if (e == null) {
        continue
    }
    resources.add({
        id: text(r.tid), entityId: text(r.tid), version: r.resourceRevisionNo,
        createdAt: r.createdTime, updatedAt: r.updatedTime, structure: e, structureRevisionId: text(r.revision),
        name: text(r.tableNameCn) == '' ? r.tableName : r.tableNameCn, databaseId: r.resourceDomainId,
        domain: r.businessType, owner: text(r.resourceOwner), description: text(r.assetDesc),
        origin: r.resourcePlanId == null ? 'SNAPSHOT' : 'MATERIALIZATION', state: r.resourceState,
        modelId: r.resourceModelId, deploymentId: r.resourcePlanId, designFieldMap: parse(r.resourceFieldMap)
    })
}
var itemsets = []
var itemRows = readConfiguration(db.camel().table('res_model_itemset').columns(
    'tid', 'revision_no', 'created_time', 'updated_time', 'itemset_name', 'model_id', 'items_json', 'itemset_status'
).where().eq('tenant_id', tenantId).eq('is_del', 0).orderBy('updated_time', 'desc').orderBy('tid'),
    1000, '数据项集配置超过视图上限')
for (r in itemRows) {
    itemsets.add({
        id: text(r.tid), version: r.revisionNo, createdAt: r.createdTime, updatedAt: r.updatedTime,
        name: r.itemsetName, modelId: r.modelId, items: JSONUtil.parse(r.itemsJson), state: r.itemsetStatus
    })
}
var logs = []
var actorIds = []
if (!projectionOnly) {
    var audit = db.lower().table('res_resource_operation_log').columns(
        'tid', 'actor_id', 'action_name', 'object_id', 'object_name', 'detail_text', 'created_time'
    ).where().eq('tenant_id', tenantId).orderBy('created_time', 'desc').orderBy('tid')
    for (row in rowsOf(audit.page(2000, 0))) {
        var actorId = text(row.actor_id)
        logs.add({
            id: row.tid, actorId: actorId, action: row.action_name, objectId: text(row.object_id),
            name: row.object_name, detail: row.detail_text, time: row.created_time,
            actorid: row.actor_id, objectid: row.object_id
        })
        if (actorId != '' && !actorIds.contains(actorId)) {
            actorIds.add(actorId)
        }
    }
}
if (actorIds.size() > 0) {
    try {
        // 只解析本次日志里的操作人。每个 IN 最多 1000 项，兼容各租户方言。
        var actorNames = {}
        var users = db.lower().table('rm_user_t').columns('id', 'real_name', 'user_name')
            .where().eq('tenant_id', tenantId)
        if (actorIds.size() <= 1000) {
            users.in('id', actorIds)
        } else {
            users.and(ids => ids.in('id', actorIds.subList(0, 1000)).or()
                .in('id', actorIds.subList(1000, actorIds.size())))
        }
        for (u in users.select()) {
            actorNames[text(u.id)] = text(u.real_name) == '' ? text(u.user_name) : text(u.real_name)
        }
        for (log in logs) {
            log.actorName = actorNames[log.actorId]
        }
    } catch (e) {
        // 某些租户缺少本地用户目录，保留已授权日志中的操作人标识。
    }
}
return {models: models, materializations: plans, resources: resources, itemSets: itemsets, logs: logs}
