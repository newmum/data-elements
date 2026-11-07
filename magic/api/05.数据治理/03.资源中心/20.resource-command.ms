{
  "properties" : { },
  "id" : "b6aaff9e31925977b94369d347e7c32a",
  "script" : null,
  "groupId" : "46fe687ba7304a608b5e2865725ad21c",
  "name" : "20.resource-command",
  "createTime" : null,
  "updateTime" : 1790982798990,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/resource-command",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "",
  "description" : "磐石资源中心受控业务命令",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableSort
import cn.hutool.core.util.NumberUtil
import '@/resource-center/planning-read' as planningRead
import '@/resource-center/model-read' as modelRead
import '@/resource-center/catalog-read' as catalogRead
import '@get:/dwm/center/dst-info' as dstInfo
import '@/resource-center/planning-command' as planningCommand
import '@/resource-center/model-command' as modelCommand
import '@/resource-center/materialization-command' as materializationCommand
import '@/resource-center/resource-command' as resourceCommand
import '@/resource-center/catalog-command' as catalogCommand

/**
 * 磐石资源中心业务入口。POST /dwm/center/resource-command
 * 接收 kind、method 和 body。表格读取传 kind=table、method=GET，body 指定 table、分页及筛选。
 * 模型日志和目录在数据库分页，配置视图先逐项核验请求 ID 再排序。业务租户取自当前会话。
 * 写入请求交给对应业务函数校验版本和权限。
 */

var tenantId = tenantRuntime.id()
if (body == null || body.kind == null) { exit 400,'缺少资源操作类型' }
var command = body
if (command.tenantId != null) { exit 400,'业务租户由当前会话确定' }
if (!['GET','POST','PUT','DELETE'].contains(command.method)) { exit 400,'不支持的操作方法' }
if (command.body == null) { command.body = {} }
if (command.body.tenantId != null) { exit 400,'不能通过请求切换业务租户' }
var kind = command.kind + ''

// 表格请求通过同一认证入口，只允许读取。
if (kind == 'table') {
    if (command.method != 'GET') { exit 400,'表格排序只支持读取' }
    var q = command.body
    var table = q.table == null ? '' : q.table + ''
    var page = q.page == null ? 1 : NumberUtil.parseInt(q.page + '')
    var size = q.size == null ? 20 : NumberUtil.parseInt(q.size + '')
    if (page < 1 || page > 100000 || size < 1 || size > 100) { exit 400,'分页参数无效' }
    if (table == 'catalogs') { return catalogRead({mode:'page',query:q,page:page,size:size}) }

    if (table == 'modelLogs') {
        var events = {
            '创建逻辑模型': {stage: '设计', event: '创建模型'},
            '复制逻辑模型': {stage: '设计', event: '复制模型'},
            '维护逻辑模型：保存': {stage: '设计', event: '保存模型设计'},
            '维护逻辑模型：reverse': {stage: '设计', event: '导入来源表'},
            '维护逻辑模型：reference': {stage: '设计', event: '引用模型实体'},
            '维护逻辑模型：bind': {stage: '标准化', event: '绑定数据标准'},
            '生成模型数据项集': {stage: '标准化', event: '生成数据项集'},
            '提交标准候选': {stage: '标准化', event: '提交标准候选'},
            '冻结设计版本': {stage: '版本', event: '冻结设计版本'},
            '维护逻辑模型：archive': {stage: '版本', event: '归档模型'},
            '创建物化方案': {stage: '物化', event: '创建物化方案'},
            '重新预检物化': {stage: '物化', event: '重新预检物化'},
            '开始物化建表': {stage: '物化', event: '开始物化建表'},
            '物化执行完成': {stage: '物化', event: '物化执行完成'},
            '请求取消物化': {stage: '物化', event: '请求取消物化'},
            '装载融合模型数据': {stage: '物化', event: '装载融合数据'},
            '融合模型装载失败': {stage: '物化', event: '融合装载失败'}
        }
        var stage = q.stage == null ? '' : q.stage + ''
        if (!['', '设计', '标准化', '版本', '物化'].contains(stage)) {
            exit 400, '模型阶段无效'
        }
        var stageActions = []
        for (action in events.keySet()) {
            if (stage == '' || events[action].stage == stage) {
                stageActions.add(action)
            }
        }
        var modelId = q.modelId == null ? '' : q.modelId + ''
        if (modelId != '') {
            var model = db.lower().table('res_logical_model').columns('tid').where()
                .eq('tenant_id', tenantId).eq('tid', modelId).eq('is_del', 0).selectOne()
            if (model == null) {
                exit 404, '模型不属于当前租户'
            }
        }
        var keyword = q.keyword == null ? '' : (q.keyword + '').trim()
        if (keyword.length() > 200) {
            exit 400, '检索关键词过长'
        }

        var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
        var active = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
        var logs = tableRead.from('res_resource_operation_log', 'l', tenantId)
        var logAction = column('l', 'action_name')
        var logName = column('l', 'object_name')
        var logDetail = tableRead.column('l', 'detail_text', 'LOB')
        var linkedModelId = column('m', 'tid')
        var modelName = column('m', 'model_name')
        var modelCode = column('m', 'model_code')
        var displayName = tableRead.coalesce([modelName, logName, '关联模型已不存在'])

        logs.leftJoin('res_materialization_plan', 'p', 'tid', 'object_id', [active('p')])
        logs.leftJoin('res_model_itemset', 'i', 'tid', 'object_id', [active('i')])
        // 优先按有效物化方案、数据项集的模型标识关联，最后才用日志对象标识。
        var modelReference = tableRead.coalesce([column('p', 'model_id'), column('i', 'model_id'), column('l', 'object_id')])
        logs.leftJoin('res_logical_model', 'm', 'tenant_id', 'tenant_id', [
            active('m'), tableRead.equal(linkedModelId, modelReference)
        ])
        logs.where(tableRead.in(logAction, stageActions))
        if (modelId != '') {
            logs.where(tableRead.equal(linkedModelId, modelId))
        }
        if (keyword != '') {
            // 保留原有通配检索，关键词作为参数绑定。
            var search = '%' + keyword + '%'
            logs.where(tableRead.any([
                tableRead.like(modelName, search), tableRead.like(modelCode, search),
                tableRead.like(logName, search), tableRead.like(logDetail, search), tableRead.like(logAction, search)
            ]))
        }
        var logTime = tableRead.column('l', 'created_time', 'TIME')
        var order = tableSort.field(q, {time: 'time', modelName: 'modelName'}, 'time')
        var result = logs.page({
            id: column('l', 'tid'), actorId: column('l', 'actor_id'), action: logAction,
            objectId: column('l', 'object_id'), name: logName, detail: logDetail, time: logTime,
            modelId: linkedModelId, modelName: displayName, modelCode: modelCode, modelStatus: column('m', 'model_status')
        }, order == 'time' ? logTime : displayName, tableSort.direction(q), 'tid', page, size)

        // 操作人标识只取当前页，并按当前租户批量查询姓名。
        var actorIds = []
        for (row in result.rows) {
            if (row.actorId != null && !actorIds.contains(row.actorId)) {
                actorIds.add(row.actorId)
            }
        }
        var names = {}
        if (actorIds.size() > 0) {
            var users = db.lower().table('rm_user_t').columns('id', 'real_name', 'user_name').where()
                .eq('tenant_id', tenantId).in('id', actorIds).select()
            for (user in users) {
                names[user.id + ''] = user.real_name == null || user.real_name == '' ? user.user_name : user.real_name
            }
        }
        var list = []
        for (row in result.rows) {
            var actor = row.actorId + ''
            var event = events[row.action]
            list.add({
                id: row.id, actorId: actor, actorName: names[actor], action: row.action, objectId: row.objectId,
                name: row.name, detail: row.detail, time: row.time, modelId: row.modelId, modelName: row.modelName,
                modelCode: row.modelCode, modelArchived: row.modelStatus == 'ARCHIVED', stage: event.stage, event: event.event
            })
        }
        return {list: list, total: result.total, page: page, size: size}
    }
    var ids = q.ids == null ? [] : q.ids
    if (ids.size() > 1000) { exit 400,'配置视图最多支持1000项，请缩小范围' }
    if (ids.size() == 0 && ['layers','databases','bindings','dst','models','materializations','itemSets','fields','materializationItems'].contains(table)) { return {keys:[],total:0} }
    var unique = []; for (id in ids) {
        if (id == null || (id + '').length() > 256 || unique.contains(id + '')) { exit 400,'行标识无效或重复' }
        unique.add(id + '')
    }
    var all = []; var allowed = {name:'name',updatedAt:'updatedAt',createdAt:'createdAt'}
    if (['layers','databases','bindings'].contains(table)) {
        var planning = planningRead({})
        if (table == 'layers') {
            var domainsByLayer = {}
            for (domain in planning.databases) {
                var count = domainsByLayer[domain.layerId]
                domainsByLayer[domain.layerId] = count == null ? 1 : count + 1
            }
            for (layer in planning.layers) {
                layer.count = domainsByLayer[layer.id] == null ? 0 : domainsByLayer[layer.id]
                layer.tables = layer.sourceId == '' ? null : layer.tableCount
                layer.source = layer.sourceName
                all.add(layer)
            }
        } else if (table == 'databases') {
            for (domain in planning.databases) {
                domain.tables = domain.sourceId == '' ? null : domain.tableCount
                domain.source = domain.sourceName
                all.add(domain)
            }
        } else {
            var bindingRow = (prefix, row) => ({
                id: prefix + row.id, name: row.name, source: row.sourceName,
                tableCount: row.sourceId == '' ? null : row.tableCount
            })
            all.addAll(planning.layers.map(row => bindingRow('layer:', row)))
            all.addAll(planning.databases.map(row => bindingRow('domain:', row)))
        }
        allowed.putAll({count:'count',tables:'tables',tableCount:'tableCount',source:'source'})
    } else if (['models','materializations','itemSets','fields','materializationItems'].contains(table)) {
        var state = modelRead({projectionOnly:true})
        if (table == 'models') { for (row in state.models) { row.entityCount = row.entities.size(); all.add(row) }; allowed.entityCount = 'entityCount' }
        if (table == 'materializations') {
            for (row in state.materializations) {
                row.itemCount = row.items.size(); var success = 0
                for (item in row.items) { if (item.state == 'SUCCEEDED') { success = success + 1 } }
                row.successCount = success; all.add(row)
            }
            allowed.putAll({itemCount:'itemCount',successCount:'successCount'})
        }
        if (table == 'itemSets') {
            for (row in state.itemSets) {
                row.itemCount = row.items.size(); var candidates = 0
                for (item in row.items) { if (item.candidateId != null) { candidates = candidates + 1 } }
                row.candidateCount = candidates; all.add(row)
            }
            allowed.putAll({itemCount:'itemCount',candidateCount:'candidateCount'})
        }
        if (table == 'fields') {
            var model = null; for (row in state.models) { if (row.id == q.parentId) { model = row } }
            if (model == null) { exit 404,'模型不存在或不属于当前租户' }
            for (entity in model.entities) { for (field in entity.fields) {
                all.add({id:entity.id + ':' + field.id,name:field.businessName == null || field.businessName == '' ? field.name : field.businessName})
            } }
        }
        if (table == 'materializationItems') {
            var plan = null; for (row in state.materializations) { if (row.id == q.parentId) { plan = row } }
            if (plan == null) { exit 404,'方案不存在或不属于当前租户' }
            for (item in plan.items) { all.add({id:item.id,name:item.targetName}) }
        }
    } else if (table == 'dst') {
        var info = dstInfo(); for (row in info.dst) { row.id = row.orgId; all.add(row) }
        allowed.putAll({appCount:'appCount',dbCount:'dbCount',tableCount:'tableCount'})
    } else { exit 400,'不支持的资源表格' }
    var byId = {}; for (row in all) { byId[row.id + ''] = row }
    // 请求的每个 ID 都必须来自当前会话可见的服务端集合。
    var selected = []
    for (id in unique) { if (byId[id] == null) { exit 404,'部分记录不存在或不在当前可见范围' }; selected.add(byId[id]) }
    var keyword = q.keyword == null ? '' : (q.keyword + '').trim().toLowerCase()
    if (keyword.length() > 200) { exit 400,'检索关键词过长' }
    var filtered = []
    for (row in selected) {
        if (keyword == '' || ((row.name == null ? '' : row.name) + ' ' + (row.code == null ? '' : row.code) + ' ' + (row.source == null ? '' : row.source)).toLowerCase().contains(keyword)) { filtered.add(row) }
    }
    if (table == 'databases' && filtered.size() > 0) {
        // 仅分库视图使用分域资源数。对已校验且匹配检索的分域先批量统计，再排序和分页。
        var scope = dataScope.dataSource()
        var scopeMode = scope?.scope == null ? '' : scope.scope + ''
        var scopeUserId = scope?.userId == null ? '' : scope.userId + ''
        var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
        if (!['ALL', 'OWNER', 'ORG'].contains(scopeMode) || scopeUserId == '') {
            exit 403, '无法确定当前数据范围'
        }
        var tables = tableRead.from('db_table_t', 't', tenantId)
        var domainId = tableRead.column('t', 'resource_domain_id', 'TEXT')
        tables.leftJoin('db_datasource_t', 's', 'tid', 'datasource_id', [
            tableRead.equal(tableRead.column('s', 'is_del', 'NUMBER'), 0)
        ])
        tables.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
        tables.where(tableRead.not(tableRead.isNull(tableRead.column('s', 'tid', 'TEXT'))))
        tables.where(tableRead.in(domainId, filtered.map(row => row.id)))
        if (scopeMode == 'OWNER') {
            tables.where(tableRead.equal(tableRead.column('s', 'created_by', 'TEXT'), scopeUserId))
        } else if (scopeMode == 'ORG') {
            tables.where(tableRead.in(tableRead.column('s', 'org_id', 'TEXT'), scopeOrgIds))
        }
        var resourceCounts = {}
        for (row in tables.grouped({id: domainId, amount: tableRead.count()}, [domainId])) {
            resourceCounts[row.id] = row.amount
        }
        for (row in filtered) {
            row.count = resourceCounts[row.id] == null ? 0 : resourceCounts[row.id]
        }
    }
    var order = {sortField:q.sortField == null ? 'name' : q.sortField,sortDir:q.sortDir == null ? 'asc' : q.sortDir}
    var sorted = tableSort.rows(filtered,order,allowed,'name','id')
    var start = Math.min((page - 1) * size,sorted.size()); var end = Math.min(start + size,sorted.size())
    var keys = []; var statistics = {}
    for (row in sorted.subList(start,end)) {
        keys.add(row.id); var values = {}
        for (field in ['count','tables','tableCount','entityCount','itemCount','successCount','candidateCount','appCount','dbCount']) {
            if (row.containsKey(field)) { values[field] = row[field] }
        }
        statistics[row.id] = values
    }
    return {keys:keys,statistics:statistics,total:sorted.size()}
}

if (['layers','databases','bindings','classifications'].contains(kind)) { return planningCommand(command) }
if (['models','itemsets'].contains(kind)) { return modelCommand(command) }
if (kind == 'materializations') { return materializationCommand(command) }
if (kind == 'resources') { return resourceCommand(command) }
if (['catalogs','publications','subscriptions','deliveries','policy'].contains(kind)) { return catalogCommand(command) }
exit 400,'未知资源业务操作'
