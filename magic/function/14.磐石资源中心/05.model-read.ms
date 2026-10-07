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
// 03.读取逻辑模型、不可变版本、物化方案和共享库表资源。
import tenantRuntime
import jsons
import cn.hutool.json.JSONUtil
import '@/resource-center/technical-read' as technicalRead
var tenantId = tenantRuntime.id()
var text = v => v == null ? '' : v + ''
var parse = v => text(v) == '' ? {} : JSONUtil.parse(v)
var models = []
var modelRows = db.select('SELECT * FROM res_logical_model WHERE tenant_id=#{tenantId} AND is_del=0 ORDER BY updated_time DESC')
var revisionRows = db.select('SELECT * FROM res_logical_model_revision WHERE tenant_id=#{tenantId} ORDER BY revision_number')
for (r in modelRows) {
    var design = parse(r.designJson)
    var frozen = []
    for (v in revisionRows) { if (v.modelId == r.tid) {
        var snapshot = parse(v.contentJson)
        snapshot.id = text(v.tid); snapshot.number = v.revisionNumber; snapshot.note = v.note
        snapshot.signature = v.contentHash; snapshot.createdAt = v.createdTime
        frozen.add(snapshot)
    } }
    models.add({id:text(r.tid),version:r.revisionNo,createdAt:r.createdTime,updatedAt:r.updatedTime,
        name:r.modelName,code:r.modelCode,domain:text(r.domainName),description:text(r.modelDescription),
        databaseId:r.databaseId,owner:text(r.ownerName),method:text(r.modelType),state:r.modelStatus == 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE',
        entities:design.entities == null ? [] : design.entities,relationships:design.relationships == null ? [] : design.relationships,
        nodes:design.nodes == null ? [] : design.nodes,outputEntityId:design.outputEntityId,fusion:design.fusion,frozen:frozen})
}
var plans = []
for (r in db.select('SELECT * FROM res_materialization_plan WHERE tenant_id=#{tenantId} AND is_del=0 AND plan_json IS NOT NULL ORDER BY updated_time DESC')) {
    var plan = parse(r.planJson)
    plan.id = text(r.tid); plan.version = r.revisionNo; plan.state = r.planStatus
    plan.createdAt = r.createdTime; plan.updatedAt = r.updatedTime
    plans.add(plan)
}
var tables = db.select('SELECT * FROM db_table_t WHERE tenant_id=#{tenantId} AND is_del=0 AND resource_domain_id IS NOT NULL ORDER BY updated_time DESC')
var ids = []; for (r in tables) { ids.add(text(r.tid)) }
var entities = input != null && input.entities != null ? input.entities : (ids.size() == 0 ? [] : technicalRead({entityIds:ids}).entities)
var entityMap = {}; for (e in entities) { entityMap[e.id] = e }
var resources = []
for (r in tables) {
    var e = entityMap[text(r.tid)]
    if (e == null) { continue }
    resources.add({id:text(r.tid),entityId:text(r.tid),version:r.resourceRevisionNo,createdAt:r.createdTime,updatedAt:r.updatedTime,
        structure:e,structureRevisionId:text(r.revision),name:text(r.tableNameCn) == '' ? r.tableName : r.tableNameCn,
        databaseId:r.resourceDomainId,domain:r.businessType,owner:text(r.resourceOwner),description:text(r.assetDesc),
        origin:r.resourcePlanId == null ? 'SNAPSHOT' : 'MATERIALIZATION',state:r.resourceState,
        modelId:r.resourceModelId,deploymentId:r.resourcePlanId,designFieldMap:parse(r.resourceFieldMap)})
}
var itemsets = []
for (r in db.select('SELECT * FROM res_model_itemset WHERE tenant_id=#{tenantId} AND is_del=0 ORDER BY updated_time DESC')) {
    itemsets.add({id:text(r.tid),version:r.revisionNo,createdAt:r.createdTime,updatedAt:r.updatedTime,
        name:r.itemsetName,modelId:r.modelId,items:JSONUtil.parse(r.itemsJson),state:r.itemsetStatus})
}
// Magic's SQL mapper folds unquoted aliases; normalize both forms before returning logs.
var logs = db.select('SELECT tid AS `id`,actor_id AS `actorId`,action_name AS `action`,object_id AS `objectId`,object_name AS `name`,detail_text AS `detail`,created_time AS `time` FROM res_resource_operation_log WHERE tenant_id=#{tenantId} ORDER BY created_time DESC LIMIT 2000')
var actorIds = []
for (log in logs) {
    log.actorId = text(log.actorId) == '' ? text(log.actorid) : text(log.actorId)
    log.objectId = text(log.objectId) == '' ? text(log.objectid) : text(log.objectId)
    if (log.actorId != '' && !actorIds.contains(log.actorId)) { actorIds.add(log.actorId) }
}
if (actorIds.size() > 0) {
    try {
        // Resolve only users named in this tenant's audit records, without a collation-sensitive join.
        var actorNames = {}
        var users = db.table('rm_user_t').where().eq('tenant_id',tenantId).in('id',actorIds).select()
        for (u in users) {
            var realName = text(u.realName) == '' ? text(u.real_name) : text(u.realName)
            var userName = text(u.userName) == '' ? text(u.user_name) : text(u.userName)
            actorNames[text(u.id)] = realName == '' ? userName : realName
        }
        for (log in logs) { log.actorName = actorNames[log.actorId] }
    } catch(e) { /* retain actor IDs when a tenant has no local user directory */ }
}
return {models:models,materializations:plans,resources:resources,itemSets:itemsets,logs:logs}
