{
  "properties" : { },
  "id" : "2ebdc2a6af4b5e2989cd8add98c849fa",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "03.materialization-command",
  "createTime" : null,
  "updateTime" : 1791068445365,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/materialization-command",
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
import tableRead
import jsons
import java.lang.IllegalStateException
import java.util.Map
import cn.hutool.json.JSONUtil
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver as connectionProperties
import com.linewell.dataelement.metautil.service.MetadataRelationJdbcService as fusionJdbc
import '@post:/dst/database/metadata/getCreateTableDDL' as generateDDL
import '@post:/dst/database/metadata/createTable' as createPhysicalTable
import '@/resource-center/planning-read' as readPlanning
import '@/resource-center/model-read' as readModels
import '@/resource-center/model-validate' as validateModel

/**
 * 磐石逻辑模型物化命令。/resource-center/materialization-command
 * 接收物化方案、冻结版本和目标绑定。支持创建、预检、执行、重试、取消、融合装载和状态读取。
 * 预检只生成 DDL。真实建表和装载要求显式确认，按当前租户、方案版本与绑定快照核验。
 */

var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId == '') {
    exit 401, '无法识别当前租户'
}
var actorId = StpUtil.getLoginIdAsString()
var text = value => value == null ? '' : (value + '').trim()
var clone = value => JSONUtil.parse(jsons.toJSONStr(value))
var payload = input.body == null ? {} : input.body
var id = text(input.id)
var action = text(input.action)
var method = text(input.method)
var key = value => {
    var name = text(value)
    if (!name.matches('[A-Za-z_][A-Za-z0-9_]{0,62}')) {
        exit 400, '表和字段技术名称须为安全标识符，最多63字符'
    }
    return name
}
var locate = (items, objectId) => {
    for (item in items) {
        if (item.id == objectId) {
            return item
        }
    }
    return null
}
var find = (items, objectId) => {
    var item = locate(items, objectId)
    if (item == null) {
        exit 404, '对象不存在或不属于当前租户'
    }
    return item
}
var unwrap = value => {
    if (value instanceof Map && value.success == false) {
        var message = text(value.message)
        throw new IllegalStateException(message == '' ? text(value.msg) : message)
    }
    return value instanceof Map && value.data != null ? value.data : value
}
var audit = (verb, plan, detail) => db.table('res_resource_operation_log').primary('tid').insert({
    tid: IdUtil.fastSimpleUUID(), tenant_id: tenantId, actor_id: actorId, action_name: verb,
    object_id: plan.id, object_name: plan.name, detail_text: detail, created_time: new Date()
})
var readPlan = planId => {
    var row = db.camel().table('res_materialization_plan').columns(
        'tid', 'plan_json', 'revision_no', 'plan_status', 'updated_time', 'created_time'
    ).where().eq('tenant_id', tenantId).eq('tid', planId).eq('is_del', 0).selectOne()
    if (row == null || text(row.planJson) == '') {
        exit 404, '物化方案不存在或不属于当前租户'
    }
    var plan = JSONUtil.parse(row.planJson)
    plan.id = text(row.tid)
    plan.version = row.revisionNo
    plan.state = row.planStatus
    plan.updatedAt = row.updatedTime
    plan.createdAt = row.createdTime
    return plan
}
var writePlan = (plan, expected, expectedState) => {
    var now = new Date()
    var query = db.table('res_materialization_plan').where().eq('tenant_id', tenantId)
        .eq('tid', plan.id).eq('is_del', 0).eq('revision_no', expected)
    if (expectedState != null) {
        query.eq('plan_status', expectedState)
    }
    var changed = query.update({
        plan_json: jsons.toJSONStr(plan), plan_status: plan.state, revision_no: expected + 1,
        updated_by: actorId, updated_time: now
    })
    if (changed != 1) {
        return false
    }
    plan.version = expected + 1
    plan.updatedAt = now
    return true
}
var save = (plan, expected) => {
    if (!writePlan(plan, expected, null)) {
        exit 409, '物化方案已更新，请刷新后重试'
    }
    return plan
}
// 按当前租户的规划和登记连接解析物化目标，并核对方案保存的绑定快照。
var bindingFor = (plan, missingAsFailure) => {
    var planning = readPlanning({})
    var binding = locate(planning.bindings, plan.bindingId)
    var domain = binding == null ? null : locate(planning.databases, binding.databaseId)
    if (binding == null || domain == null) {
        if (missingAsFailure) {
            throw new IllegalStateException('目标绑定或分库已失效，请重新核对')
        }
        exit 404, '对象不存在或不属于当前租户'
    }
    if (domain.state != 'ACTIVE' || domain.mode != 'MANAGED' || binding.category == 'REFERENCE') {
        throw new IllegalStateException('只有有效管理型分库可以物化')
    }
    if (binding.state != 'VALID' || binding.checkedHash != binding.currentHash) {
        throw new IllegalStateException('绑定配置已变化或尚未校验，请重新检查')
    }
    if (plan.bindingHash != null && plan.bindingHash != binding.currentHash) {
        throw new IllegalStateException('方案绑定快照已失效，请重新创建物化方案')
    }
    var registered = connectionProperties.resolve(tenantId, binding.sourceId)
    if (registered == null || registered.isEmpty()) {
        throw new IllegalStateException('目标数据源不存在或不属于当前租户')
    }
    var pick = keys => {
        for (name in keys) {
            if (text(registered[name]) != '') {
                return text(registered[name])
            }
        }
        return ''
    }
    var engine = pick(['dbType', 'databaseType']).toLowerCase()
    var catalog = pick(['database', 'dbMetaDbName', 'dbName'])
    var schema = pick(['schema', 'defaultSchema', 'currentSchema', 'schemaName'])
    if (schema == '' && ['oracle', 'dameng'].contains(engine)) {
        schema = pick(['username', 'user'])
    }
    if (schema == '' && ['postgresql', 'kingbase', 'kingbase8', 'gaussdb'].contains(engine)) {
        schema = 'public'
    }
    if (catalog == '' && schema == '') {
        throw new IllegalStateException('目标数据源未登记实际数据库或Schema')
    }
    // 目标只能落在已登记连接的数据库和 Schema。
    if (text(binding.catalog) != '' && text(binding.catalog).toLowerCase() != catalog.toLowerCase()) {
        throw new IllegalStateException('绑定数据库与数据源登记配置不一致')
    }
    var expectedSchema = ['mysql', 'mariadb'].contains(engine) ? catalog : schema
    if (text(binding.schema) != '' && text(binding.schema).toLowerCase() != expectedSchema.toLowerCase()) {
        throw new IllegalStateException('绑定Schema与数据源登记配置不一致')
    }
    return {binding: binding, domain: domain, engine: engine}
}

// 这里映射目标实库的 DDL 字段类型。方案、登记和审计仍走当前租户的结构化读写。
var physical = (field, engine) => {
    var logical = text(field.logicalType)
    if (logical == 'String') {
        return (['oracle', 'dameng'].contains(engine) ? 'VARCHAR2' : 'VARCHAR') + '(' + field.length + ')'
    }
    if (logical == 'Text') {
        return ['oracle', 'dameng'].contains(engine) ? 'CLOB' : engine == 'sqlserver' ? 'NVARCHAR(MAX)' : 'TEXT'
    }
    if (logical == 'Int32') {
        return 'INTEGER'
    }
    if (logical == 'Int64') {
        return ['oracle', 'dameng'].contains(engine) ? 'NUMBER(19)' : 'BIGINT'
    }
    if (logical == 'Decimal') {
        return 'DECIMAL(' + field.precision + ',' + field.scale + ')'
    }
    if (logical == 'Boolean') {
        return ['oracle', 'dameng'].contains(engine) ? 'NUMBER(1)' : engine == 'sqlserver' ? 'BIT' : 'BOOLEAN'
    }
    if (logical == 'Date') {
        return 'DATE'
    }
    if (logical == 'Timestamp') {
        return engine == 'sqlserver' ? 'DATETIME2' : ['mysql', 'mariadb'].contains(engine) ? 'DATETIME' : 'TIMESTAMP'
    }
    if (logical == 'UUID') {
        return ['postgresql', 'kingbase', 'kingbase8'].contains(engine) ? 'UUID' : 'CHAR(36)'
    }
    if (logical == 'Binary') {
        return ['postgresql', 'kingbase', 'kingbase8'].contains(engine) ? 'BYTEA' : engine == 'sqlserver' ? 'VARBINARY(MAX)' : 'BLOB'
    }
    if (logical == 'JSON' && ['mysql', 'mariadb', 'postgresql'].contains(engine)) {
        return engine == 'postgresql' ? 'JSONB' : 'JSON'
    }
    throw new IllegalStateException('字段' + text(field.name) + '的' + logical + '类型没有目标方言映射')
}

var itemsFor = (entity, engine) => {
    var columns = []
    var primary = []
    var unique = []
    for (constraint in entity.keys == null ? [] : entity.keys) {
        if (constraint.kind == 'primary') {
            primary.addAll(constraint.fieldIds)
        }
        if (constraint.kind == 'unique' && constraint.fieldIds.size() == 1) {
            unique.add(constraint.fieldIds[0])
        }
    }
    var ordinal = 0
    for (field in entity.fields) {
        ordinal = ordinal + 1
        var nativeType = physical(field, engine)
        var baseType = nativeType.contains('(') ? nativeType.substring(0, nativeType.indexOf('(')) : nativeType
        var isPrimary = primary.contains(field.id) ? 1 : 0
        columns.add({
            columnName: key(field.name), columnComment: text(field.businessName) == '' ? text(field.comment) : text(field.businessName),
            dataType: baseType, columnType: nativeType, length: field.length, precisionLength: field.precision,
            scale: field.scale, nullable: field.nullable == 'false' ? 0 : 1, primaryKey: isPrimary,
            isUnique: unique.contains(field.id) ? 1 : 0, autoIncrement: 0, indexed: isPrimary,
            ordinalPosition: ordinal, designFieldId: field.id, standardId: field.standard == null ? null : field.standard.id
        })
    }
    return columns
}
// 在当前租户的目标数据源中批量检查重名，由数据库比较两侧 LOWER 后的值，保留实际排序规则。
var registeredTargetNames = (items, sourceId) => {
    var aliasByName = {}
    var matches = []
    var projection = {}
    var registeredName = tableRead.column('t', 'table_name', 'TEXT')
    var tableName = tableRead.lower(registeredName)
    for (item in items) {
        var name = text(item.targetName)
        if (item.state == 'SUCCEEDED' || !name.matches('[A-Za-z_][A-Za-z0-9_]{0,62}') || aliasByName[name] != null) {
            continue
        }
        var alias = 'target_' + matches.size()
        // 候选名已经校验为非空。列只用于给绑定参数提供文本类型与排序规则。
        var requestedName = tableRead.lower(tableRead.coalesce([name, registeredName]))
        var match = tableRead.equal(tableName, requestedName)
        aliasByName[name] = alias
        matches.add(match)
        projection[alias] = tableRead.countIf(match)
    }
    var registered = {}
    if (matches.size() > 0) {
        var tables = tableRead.from('db_table_t', 't', tenantId)
        tables.where(tableRead.equal(tableRead.column('t', 'datasource_id', 'TEXT'), sourceId))
        tables.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
        tables.where(tableRead.any(matches))
        var row = tables.grouped(projection, [])[0]
        for (name in aliasByName.keySet()) {
            registered[name] = row[aliasByName[name]] > 0
        }
    }
    return registered
}

var precheckMessage = error => {
    var cause = error
    // 脚本运行时会包装异常，预检结果只保留实际错误信息。
    while (['MagicScriptRuntimeException', 'MagicScriptException'].contains(cause.getClass().getSimpleName()) && cause.getCause() != null) {
        cause = cause.getCause()
    }
    return text(cause.getMessage())
}

var precheck = plan => {
    var errors = []
    var ddlParts = []
    var validation = validateModel(clone(plan.revision))
    errors.addAll(validation.errors)
    try {
        var target = bindingFor(plan, true)
        var names = {}
        var start = 0
        while (start < plan.items.size()) {
            var end = Math.min(start + 100, plan.items.size())
            var items = plan.items.subList(start, end)
            var registered = registeredTargetNames(items, target.binding.sourceId)
            var entries = []
            var requests = []
            var localError = null
            // 先准备本批有效前缀。后续对象的本地错误不能盖过前序对象的 DDL 错误。
            for (item in items) {
                if (item.state == 'SUCCEEDED') {
                    entries.add({item: item, request: null})
                    continue
                }
                try {
                    var name = key(item.targetName)
                    var normalized = name.toLowerCase()
                    if (names[normalized] == true) {
                        throw new IllegalStateException('方案中存在重复目标表名')
                    }
                    names[normalized] = true
                    var entity = find(plan.revision.entities, item.designEntityId)
                    if (entity.reference != null) {
                        throw new IllegalStateException('引用模型的只读实体不能物化')
                    }
                    if (registered[name] == true) {
                        throw new IllegalStateException('目标表已登记，不覆盖：' + name)
                    }
                    var request = {propList: {tableName: name, tableNameCn: text(entity.displayName)},
                        tableItems: itemsFor(entity, target.engine)}
                    requests.add(request)
                    entries.add({item: item, request: request})
                } catch (e) {
                    localError = e
                    break
                }
            }

            var generated = null
            var generationError = null
            if (requests.size() > 0) {
                var savedBody = body
                try {
                    body = {canvas: true, canvasBatchGenerate: true, dbId: target.binding.sourceId, targets: requests}
                    generated = unwrap(generateDDL())
                } catch (e) {
                    generationError = e
                } finally {
                    body = savedBody
                }
            }
            // 按方案原顺序回填 DDL，失败位置之后的条目维持原样。
            var index = 0
            for (entry in entries) {
                if (entry.request == null) {
                    ddlParts.add(entry.item.ddl)
                    continue
                }
                if (generationError != null) {
                    throw generationError
                }
                var results = generated?.results
                if (results == null || index >= results.size()) {
                    var message = generated?.error?.index == index ? text(generated.error.message) : ''
                    throw new IllegalStateException(message == '' ? '共享DDL接口未返回建表脚本' : message)
                }
                var result = results[index]
                if (result.tableName != entry.request.propList.tableName) {
                    throw new IllegalStateException('共享DDL接口返回的目标表顺序无效')
                }
                entry.item.ddl = text(result.ddl)
                if (entry.item.ddl == '') {
                    throw new IllegalStateException('共享DDL接口未返回建表脚本')
                }
                entry.item.columns = entry.request.tableItems
                ddlParts.add(entry.item.ddl)
                index = index + 1
            }
            if (localError != null) {
                throw localError
            }
            start = end
        }
        if (errors.size() == 0 && plan.bindingHash == null) {
            plan.bindingHash = target.binding.currentHash
        }
    } catch (e) {
        errors.add(precheckMessage(e))
    }
    plan.errors = errors
    plan.warnings = ['预检只检查模型、命名、登记冲突和SQL方言；实际连接与实库同名检查在执行时完成。',
        '仅创建表结构；不装载数据，不生成跨来源物理外键，不覆盖已有表。']
    plan.ddl = ddlParts.join('\n\n')
    plan.state = errors.size() > 0 ? 'BLOCKED' : 'READY'
    return plan
}
if (method == 'POST' && id == '') {
    var models = readModels({entities: []})
    var model = find(models.models, text(payload.modelId))
    var frozen = find(model.frozen, text(payload.revisionId))
    var planning = readPlanning({})
    var binding = find(planning.bindings, text(payload.bindingId))
    var chosen = payload.entityIds == null ? [] : payload.entityIds
    if (chosen.size() == 0 || chosen.size() > 300) {
        exit 400, '请选择1至300个物化实体'
    }
    // 融合模型只能在模型绑定的分库物化唯一输出表。
    if (text(frozen.outputEntityId) != '') {
        if (chosen.size() != 1 || text(chosen[0]) != text(frozen.outputEntityId)) {
            exit 400, '融合模型只能物化唯一输出表，来源表不可物化'
        }
        if (text(binding.databaseId) != text(model.databaseId)) {
            exit 409, '物化目标必须是模型指定的目标分库'
        }
    }
    var selected = []
    var targetNames = []
    var seen = []
    for (entityId in chosen) {
        if (seen.contains(entityId)) {
            exit 400, '实体选择不能重复'
        }
        seen.add(entityId)
        var entity = find(frozen.entities, entityId)
        if (entity.reference != null) {
            exit 400, '参照实体不能物化'
        }
        var customName = payload.targetNames == null ? null : payload.targetNames[entityId]
        var targetName = key(customName == null ? text(binding.prefix) + entity.name : customName)
        selected.add({id: IdUtil.fastSimpleUUID(), designEntityId: entityId, targetName: targetName, state: 'PENDING'})
        targetNames.add(targetName)
    }
    var now = new Date()
    var plan = {
        id: IdUtil.fastSimpleUUID(), version: 1, name: text(payload.name) == '' ? model.name + '物化' : text(payload.name),
        modelId: model.id, revisionId: frozen.id, bindingId: binding.id, bindingHash: binding.currentHash,
        revision: clone(frozen), items: selected, attempt: 0, createdAt: now, updatedAt: now, requestedBy: actorId
    }
    precheck(plan)
    db.table('res_materialization_plan').primary('tid').insert({
        tid: plan.id, tenant_id: tenantId, plan_code: 'PS_' + plan.id, model_id: model.id,
        target_datasource_id: binding.sourceId, target_table_name: selected[0].targetName,
        plan_status: plan.state, risk_level: 'STRUCTURE_CREATE', binding_id: binding.id, frozen_revision_id: frozen.id,
        plan_json: jsons.toJSONStr(plan), revision_no: 1, created_by: actorId, created_time: now,
        updated_by: actorId, updated_time: now, is_del: 0
    })
    var precheckResult = plan.state == 'READY' ? '可执行' : '存在阻断'
    audit('创建物化方案', plan, '模型：' + model.name + '；冻结版本：v' + frozen.number +
        '；目标表：' + targetNames.join('、') + '；预检结果：' + precheckResult)
    return plan
}
var plan = readPlan(id)
if (method == 'GET') {
    return plan
}
if (text(input.version) != text(plan.version)) {
    exit 409, '物化方案已更新，请刷新后重试'
}
if (action == 'cancel') {
    var expectedState = plan.state
    if (!['QUEUED', 'RUNNING'].contains(expectedState)) {
        exit 409, '任务已结束，不可取消'
    }
    return db.transaction(() => {
        plan.state = 'CANCELLING'
        if (!writePlan(plan, plan.version, expectedState)) {
            exit 409, '物化方案已更新，请刷新后重试'
        }
        audit('请求取消物化', plan, '当前单表操作不可中断，后续对象将停止')
        return plan
    })
}
if (action == 'precheck') {
    if (['RUNNING', 'QUEUED', 'CANCELLING', 'SUCCEEDED'].contains(plan.state)) {
        exit 409, '执行中或完成的方案不能重新预检'
    }
    precheck(plan)
    save(plan, plan.version)
    var precheckResult = plan.state == 'READY' ? '可执行' : '存在阻断'
    audit('重新预检物化', plan, '预检结果：' + precheckResult + '；问题 ' + plan.errors.size() + ' 项')
    return plan
}
if (action == 'load') {
    if (payload.confirmExecution != true) {
        exit 400, '请确认将冻结的字段映射装载到目标表'
    }
    if (plan.state != 'SUCCEEDED' || text(plan.revision.outputEntityId) == '') {
        exit 409, '请先完成融合模型唯一输出表 D 的物化建表'
    }
    if (plan.items.size() != 1 || plan.items[0].state != 'SUCCEEDED' || text(plan.items[0].resourceId) == '') {
        exit 409, '目标表尚未完整创建与登记'
    }
    if (plan.loadState == 'SUCCEEDED') {
        exit 409, '该冻结版本已装载，禁止重复写入'
    }
    if (plan.loadState == 'RUNNING') {
        exit 409, '融合装载正在执行，请稍后刷新'
    }

    var target = bindingFor(plan, false)
    var output = find(plan.revision.entities, plan.revision.outputEntityId)
    var fusion = plan.revision.fusion
    if (fusion == null || fusion.mappings == null || fusion.mappings.size() == 0) {
        exit 422, '冻结版本没有可执行的字段映射'
    }
    var validation = validateModel(clone(plan.revision))
    if (!validation.valid) {
        exit 422, validation.errors.join('；')
    }

    // 批量读取当前租户的来源表和已登记的输出表，逐项核对冻结表名、数据源及目标方案归属。
    var item = plan.items[0]
    var resourceId = text(item.resourceId)
    var sourceEntities = []
    var sourceTableIds = []
    var seenTableIds = {}
    for (entity in plan.revision.entities) {
        if (entity.role != 'SOURCE') {
            continue
        }
        var tableId = text(entity.origin?.entityId)
        if (tableId == '') {
            exit 422, '来源表缺少共享技术快照标识'
        }
        sourceEntities.add(entity)
        if (seenTableIds[tableId] == null) {
            sourceTableIds.add(tableId)
            seenTableIds[tableId] = true
        }
    }
    var tableIds = []
    tableIds.addAll(sourceTableIds)
    if (seenTableIds[resourceId] == null) {
        tableIds.add(resourceId)
    }
    var registeredTables = {}
    var tableRows = db.camel().table('db_table_t').columns('tid', 'datasource_id', 'table_name', 'resource_plan_id').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', tableIds).select()
    for (row in tableRows) {
        registeredTables[text(row.tid)] = row
    }
    var registeredOutput = registeredTables[resourceId]
    var outputPlanId = text(registeredOutput?.resourcePlanId)
    if (registeredOutput == null || text(registeredOutput.datasourceId) != text(target.binding.sourceId)
        || text(registeredOutput.tableName) != text(item.targetName) || (outputPlanId != '' && outputPlanId != text(plan.id))) {
        exit 409, '已登记的物化目标已变化，请重新核对目标表'
    }

    var sourceById = {}
    var sources = []
    for (entity in sourceEntities) {
        var original = registeredTables[text(entity.origin.entityId)]
        var expectedName = text(entity.origin.tableName == null ? entity.name : entity.origin.tableName)
        var changedSource = entity.origin.sourceId != null && text(original?.datasourceId) != text(entity.origin.sourceId)
        if (original == null || text(original.tableName) != expectedName || changedSource) {
            exit 409, '来源技术表已变化，请重新逆向解析：' + text(entity.name)
        }
        sourceById[entity.id] = entity
        sources.add({entityId: entity.id, sourceId: text(original.datasourceId), tableName: text(original.tableName)})
    }

    // 收集关联键、取最新排序字段和输出映射引用。构造物理请求时先用冻结名称，后续批量确认现有列。
    var fieldReferences = []
    var columnIds = []
    var seenColumnIds = {}
    var frozenFieldName = (entity, fieldId) => {
        var field = find(entity.fields, fieldId)
        var columnId = entity.origin.fieldMap == null ? '' : text(entity.origin.fieldMap[fieldId])
        var fieldName = text(field.name)
        if (columnId == '') {
            exit 409, '来源字段没有原始快照映射：' + fieldName
        }
        fieldReferences.add({tableId: text(entity.origin.entityId), columnId: columnId, fieldName: fieldName})
        if (seenColumnIds[columnId] == null) {
            columnIds.add(columnId)
            seenColumnIds[columnId] = true
        }
        return fieldName
    }
    var joins = []
    for (join in fusion.joins) {
        var left = sourceById[join.leftEntityId]
        var right = sourceById[join.rightEntityId]
        if (left == null || right == null) {
            exit 422, '融合关联引用了非来源表'
        }
        var pairs = []
        for (pair in join.pairs) {
            pairs.add({leftName: frozenFieldName(left, pair.leftFieldId), rightName: frozenFieldName(right, pair.rightFieldId)})
        }
        joins.add({
            leftEntityId: join.leftEntityId, rightEntityId: join.rightEntityId, joinType: join.joinType, pairs: pairs,
            rightCardinality: join.rightCardinality, dedupe: join.dedupe,
            orderName: join.dedupe == 'LATEST' ? frozenFieldName(right, join.orderFieldId) : null
        })
    }
    var mappings = []
    for (mapping in fusion.mappings) {
        var from = sourceById[mapping.sourceEntityId]
        if (from == null) {
            exit 422, '字段映射引用了非来源表'
        }
        var targetField = find(output.fields, mapping.targetFieldId)
        mappings.add({
            sourceEntityId: from.id, sourceName: frozenFieldName(from, mapping.sourceFieldId),
            targetName: key(targetField.name), transform: mapping.transform
        })
    }
    var registeredColumns = {}
    var start = 0
    while (start < columnIds.size()) {
        var end = Math.min(start + 500, columnIds.size())
        var rows = db.camel().table('db_table_column_t').columns('tid', 'table_id', 'column_name').where()
            .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', columnIds.subList(start, end))
            .in('table_id', sourceTableIds).select()
        for (row in rows) {
            registeredColumns[text(row.tid)] = row
        }
        start = end
    }
    for (reference in fieldReferences) {
        var column = registeredColumns[reference.columnId]
        if (column == null || text(column.tableId) != reference.tableId || text(column.columnName) != reference.fieldName) {
            exit 409, '来源字段快照已变化，请重新解析：' + reference.fieldName
        }
    }

    // 先按版本领取装载，外部 JDBC 提交后再记录结果。两个请求同时发起时，只有领取成功的一方调用物理装载。
    plan.loadState = 'RUNNING'
    plan.loadStartedAt = new Date()
    plan.loadError = null
    save(plan, plan.version)
    var failLoad = message => {
        plan.loadState = 'FAILED'
        plan.loadError = message
        save(plan, plan.version)
        audit('融合模型装载失败', plan, '目标表 ' + item.targetName + '；原因：' + message)
        return plan
    }
    try {
        var result = fusionJdbc.loadModelFusion({
            targetSourceId: target.binding.sourceId, targetTable: item.targetName, baseEntityId: fusion.baseEntityId,
            sources: sources, joins: joins, mappings: mappings
        })
        plan.loadedRows = result.insertedRows
        plan.loadState = 'SUCCEEDED'
        plan.loadedAt = new Date()
        plan.loadError = null
        var outputUpdate = db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', resourceId).eq('is_del', 0)
            .eq('datasource_id', registeredOutput.datasourceId).eq('table_name', registeredOutput.tableName)
        if (registeredOutput.resourcePlanId == null) {
            outputUpdate.isNull('resource_plan_id')
        } else {
            outputUpdate.eq('resource_plan_id', registeredOutput.resourcePlanId)
        }
        var updated = outputUpdate.update({record_count: result.insertedRows, updated_time: new Date(), updated_by: actorId})
        if (updated != 1) {
            return failLoad('数据已装载，目标表登记已变化，行数未回写')
        }
        save(plan, plan.version)
        audit('装载融合模型数据', plan, '目标表 ' + item.targetName + '，写入 ' + result.insertedRows + ' 行')
        return plan
    } catch (e) {
        return failLoad(text(e.getMessage()))
    }
}
if (!['execute', 'retry'].contains(action)) {
    exit 400, '不支持的物化操作'
}
if (payload.confirmExecution != true) {
    exit 400, '请核对SQL后明确确认执行物化建表'
}
if (!['READY', 'FAILED', 'PARTIAL', 'CANCELLED'].contains(plan.state)) {
    exit 409, '方案不在可执行状态'
}
var previousState = plan.state
if (plan.items == null || plan.items.size() == 0 || plan.items.size() > 300) {
    exit 409, '物化方案对象清单无效，请重新创建'
}
var itemIds = {}
var pendingIds = []
for (item in plan.items) {
    var itemId = text(item.id)
    if (itemId == '' || itemIds[itemId] != null) {
        exit 409, '物化对象标识无效或重复，请重新创建方案'
    }
    itemIds[itemId] = true
    if (item.state != 'SUCCEEDED') {
        pendingIds.add(itemId)
    }
}
precheck(plan)
if (plan.errors.size() > 0) {
    save(plan, plan.version)
    exit 409, '预检未通过，请查看方案错误'
}
plan.state = 'RUNNING'
plan.attempt = plan.attempt == null ? 1 : plan.attempt + 1
plan.startedAt = new Date()
plan.finishedAt = null
for (item in plan.items) {
    if (item.state != 'SUCCEEDED') {
        item.state = 'PENDING'
        item.error = null
    }
}

// 领取与开始审计一并提交。物理 DDL 在事务外运行，取消请求可以独立更新方案。
db.transaction(() => {
    if (!writePlan(plan, plan.version, previousState)) {
        exit 409, '物化方案已更新，请刷新后重试'
    }
    audit('开始物化建表', plan, '已明确确认执行，尝试 ' + plan.attempt)
})

var createdPreviously = {}
var historyError = ''
if (pendingIds.size() > 0) {
    try {
        // 领取之后按对象一次查出历史物理建表标记，重试不会逐项查询历史执行表。
        var history = tableRead.from('res_materialization_execution', 'history', tenantId)
        var itemKey = tableRead.column('history', 'item_id', 'TEXT')
        history.where(tableRead.equal(tableRead.column('history', 'plan_id', 'TEXT'), plan.id))
        history.where(tableRead.equal(tableRead.column('history', 'is_del', 'NUMBER'), 0))
        history.where(tableRead.equal(tableRead.column('history', 'physical_created', 'NUMBER'), 1))
        history.where(tableRead.in(itemKey, pendingIds))
        for (row in history.grouped({itemId: itemKey}, [itemKey])) {
            createdPreviously[text(row.itemId)] = true
        }
    } catch (error) {
        historyError = '无法核验物化历史，请稍后重试'
    }
}

// 正常执行仅按当前版本和状态推进。只有并发写入导致条件更新失败时才回读方案。
var observeCancellation = () => {
    var latest = readPlan(plan.id)
    if (latest.state != 'CANCELLING' || text(latest.attempt) != text(plan.attempt)) {
        exit 409, '物化方案已更新，请刷新后重试'
    }
    plan.version = latest.version
    plan.state = 'CANCELLING'
}
for (item in plan.items) {
    if (item.state == 'SUCCEEDED') {
        continue
    }
    if (historyError != '') {
        item.state = 'FAILED'
        item.error = historyError
        break
    }
    // 预占成功后只允许这一张表开始。取消先赢得版本更新时，后续对象没有物理副作用。
    if (!writePlan(plan, plan.version, 'RUNNING')) {
        observeCancellation()
        break
    }
    var executionId = IdUtil.fastSimpleUUID()
    var executionRecorded = false
    var checkingBinding = false
    var target = null
    var failure = '无法写入物化执行记录，请稍后重试'
    try {
        var started = new Date()
        db.table('res_materialization_execution').primary('tid').insert({
            tid: executionId, tenant_id: tenantId, plan_id: plan.id, item_id: item.id,
            execution_status: 'RUNNING', execution_mode: 'PHYSICAL_CREATE', ddl_text: item.ddl,
            physical_created: 0, started_time: started, created_by: actorId,
            created_time: started, updated_by: actorId, updated_time: started, is_del: 0
        })
        executionRecorded = true
        if (createdPreviously[text(item.id)] == true) {
            failure = '该对象此前已物理创建，请先核对并登记已有表，未重复执行DDL'
            throw new IllegalStateException(failure)
        }
        // 绑定和实际数据源可能在两次物理操作之间变化。建表前复核并使用这次校验的目标。
        checkingBinding = true
        failure = '目标绑定或连接配置已变化，请重新核对物化方案'
        target = bindingFor(plan, true)
        checkingBinding = false
        failure = '目标建表未成功，请核对现有表和目标连接'
        var savedBody = body
        var created = null
        try {
            body = {canvas: true, dbId: target.binding.sourceId, tableName: item.targetName, ddl: item.ddl, skipIfExists: false}
            created = unwrap(createPhysicalTable())
        } finally {
            body = savedBody
        }
        if (!(created instanceof Map) || created.created != true || created.status != 'created') {
            failure = '目标实库表已存在或建表未成功，未覆盖已有对象'
            throw new IllegalStateException(failure)
        }
        failure = '物理表已创建，但资源登记未完成，请核对并登记已有表'
        db.table('res_materialization_execution').where().eq('tenant_id', tenantId).eq('tid', executionId)
            .update({physical_created: 1, updated_time: new Date()})
        var entity = find(plan.revision.entities, item.designEntityId)
        var tableId = IdUtil.fastSimpleUUID()
        var fieldMap = {}
        db.transaction(() => {
            var now = new Date()
            db.table('db_table_t').primary('tid').insert({
                tid: tableId, tenant_id: tenantId, datasource_id: target.binding.sourceId,
                table_name: item.targetName, table_name_en: item.targetName, table_name_cn: text(entity.displayName),
                table_comment: text(entity.comment), table_type: 'TABLE', business_type: target.domain.domain,
                field_count: entity.fields.size(), record_count: 0, asset_desc: text(entity.comment),
                asset_status: 2, flow_status: 2, resource_domain_id: target.domain.id,
                resource_owner: target.domain.owner, resource_state: 'ACTIVE', resource_model_id: plan.modelId,
                resource_plan_id: plan.id, resource_revision_no: 1, revision: 1, created_by: actorId,
                created_time: now, updated_by: actorId, updated_time: now, is_del: 0
            })
            for (column in item.columns) {
                var columnId = IdUtil.fastSimpleUUID()
                fieldMap[column.designFieldId] = columnId
                db.table('db_table_column_t').primary('tid').insert({
                    tid: columnId, tenant_id: tenantId, table_id: tableId, column_name: column.columnName,
                    column_comment: column.columnComment, data_type: column.dataType, column_type: column.columnType,
                    length: column.length, precision_length: column.precisionLength, scale: column.scale,
                    nullable: column.nullable, primary_key: column.primaryKey, is_unique: column.isUnique,
                    indexed: column.indexed, ordinal_position: column.ordinalPosition, data_standard_id: column.standardId,
                    revision: 1, created_by: actorId, created_time: now, updated_by: actorId, updated_time: now, is_del: 0
                })
            }
            db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', tableId)
                .update({resource_field_map: jsons.toJSONStr(fieldMap)})
            db.table('res_materialization_execution').where().eq('tenant_id', tenantId).eq('tid', executionId)
                .update({target_table_id: tableId, execution_status: 'SUCCEEDED',
                    execution_summary: '物理表已创建，共享技术元数据和仓库归属已登记', finished_time: now, updated_time: now})
        })
        item.entityId = tableId
        item.resourceId = tableId
        item.state = 'SUCCEEDED'
        item.error = null
    } catch (error) {
        item.state = 'FAILED'
        // 底层驱动异常可能含连接或长 SQL。执行记录保存可操作的业务原因，长度与方言无关。
        item.error = failure
        if (executionRecorded) {
            db.table('res_materialization_execution').where().eq('tenant_id', tenantId).eq('tid', executionId)
                .update({execution_status: 'FAILED', failure_summary: item.error, finished_time: new Date(), updated_time: new Date()})
        }
    }
    // 当前对象可完成登记，进度写入不能覆盖已经成功提交的取消。
    if (!writePlan(plan, plan.version, 'RUNNING')) {
        observeCancellation()
        if (!writePlan(plan, plan.version, 'CANCELLING')) {
            exit 409, '物化方案已更新，请刷新后重试'
        }
        break
    }
    if (checkingBinding || !executionRecorded) {
        break
    }
}
var succeeded = 0
var failed = 0
var cancelled = 0
for (item in plan.items) {
    if (item.state == 'SUCCEEDED') {
        succeeded = succeeded + 1
    } else if (item.state == 'FAILED') {
        failed = failed + 1
    } else if (item.state == 'PENDING') {
        item.state = 'CANCELLED'
        cancelled = cancelled + 1
    }
}
var finish = expectedState => {
    plan.state = succeeded == plan.items.size() ? 'SUCCEEDED' : succeeded > 0 ? 'PARTIAL' :
        expectedState == 'CANCELLING' ? 'CANCELLED' : 'FAILED'
    plan.finishedAt = new Date()
    return writePlan(plan, plan.version, expectedState)
}
if (!finish(plan.state)) {
    // 最后一个对象写完后也可能收到取消，重新核对后提交终态，避免滞留在 CANCELLING。
    observeCancellation()
    if (!finish('CANCELLING')) {
        exit 409, '物化方案已更新，请刷新后重试'
    }
}
audit('物化执行完成', plan, '成功 ' + succeeded + ' 项，失败 ' + failed + ' 项，取消 ' + cancelled + ' 项')
return plan
