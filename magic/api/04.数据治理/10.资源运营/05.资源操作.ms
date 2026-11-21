{
  "properties": {},
  "id": "b6aaff9e31925977b94369d347e7c32a",
  "script": null,
  "groupId": "89db9624d0e543cd9cb97631c19be661",
  "name": "05.资源操作",
  "createTime": null,
  "updateTime": 1790982798990,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/command",
  "method": "POST",
  "parameters": [],
  "options": [{"name": "access", "value": "TENANT_SESSION"}],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "磐石资源中心受控业务命令",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
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
import '@get:/dwm/center/inventory' as dstInfo
import metadataAsset
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
import '@/resource-center/technical-read' as technicalRead
import '@post:/dst/db/sample' as sampleData
import jsons
import cn.hutool.json.JSONUtil
import cn.hutool.crypto.digest.DigestUtil
import '@/resource-center/model-validate' as validateModel
import java.lang.IllegalStateException
import java.util.Map
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver as connectionProperties
import com.linewell.dataelement.metautil.service.MetadataRelationJdbcService as fusionJdbc
import '@post:/dst/db/ddl' as generateDDL
import '@post:/dst/db/create' as createPhysicalTable
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import log
import java.time.Instant
import java.lang.Math
import java.util.List
import '@post:/dst/catalog/save' as saveCatalog
import '@post:/sym/user/me' as currentUser

// POST /dwm/ops/command 资源操作
// 磐石资源中心受控业务命令

/**
 * 磐石资源中心业务入口。POST /dwm/ops/command
 * 接收 kind、method 和 body。表格读取传 kind=table、method=GET，body 指定 table、分页及筛选。
 * 模型日志和目录在数据库分页，配置视图先逐项核验请求 ID 再排序。业务租户取自当前会话。
 * 登记、详情、预览和归档在本入口校验租户和版本，各类写入调用下方对应的局部函数。
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

// 维护数仓分层、分域、目录分类及存储绑定。
if (['layers','databases','bindings','classifications'].contains(kind)) {
    var planningCommand = input => {
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
        var current = planningRead({})
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
    }
    return planningCommand(command)
}
// 维护逻辑模型、设计版本及模型数据项集。
if (['models','itemsets'].contains(kind)) {
    var modelCommand = input => {
        var tenantId = tenantRuntime.id()
        if (tenantId == null || (tenantId + '').trim() == '') {
            exit 401, '当前登录租户无效，请重新登录'
        }
        var actorId = StpUtil.getLoginIdAsString()
        var now = new Date()
        var text = v => v == null ? '' : (v + '').trim()
        var clone = v => JSONUtil.parse(jsons.toJSONStr(v))
        var required = (v, label, max) => { var s = text(v); if (s == '' || s.length() > max) { exit 400,label + '不能为空或超过长度限制' }; return s }
        var key = v => { var s = text(v); if (!s.matches('[A-Za-z_][A-Za-z0-9_]{0,62}')) { exit 400,'技术代码格式无效' }; return s }
        var find = (rows,id) => { for (r in rows) { if (r.id == id) { return r } }; exit 404,'对象不存在或不属于当前租户' }
        var payload = input.body == null ? {} : input.body
        var kind = text(input.kind); var id = text(input.id); var action = text(input.action); var method = text(input.method)
        var state = modelRead({entities:[]})
        var content = m => ({entities:m.entities,relationships:m.relationships,nodes:m.nodes,outputEntityId:m.outputEntityId,fusion:m.fusion})
        var check = m => { if (text(input.version) != text(m.version)) { exit 409,'模型已更新，请刷新后重试' } }
        var audit = (verb,m,detail) => db.table('res_resource_operation_log').primary('tid').insert({tid:IdUtil.fastSimpleUUID(),tenant_id:tenantId,
            actor_id:actorId,action_name:verb,object_id:m.id,object_name:m.name,detail_text:text(detail),created_time:now})
        var designSummary = m => {
            var fields = 0; var sources = 0
            for (e in m.entities) { fields = fields + e.fields.size(); if (e.role == 'SOURCE') { sources = sources + 1 } }
            var mappings = m.fusion == null || m.fusion.mappings == null ? 0 : m.fusion.mappings.size()
            return '来源表 ' + sources + ' 张，设计实体 ' + m.entities.size() + ' 个，字段 ' + fields + ' 个，关系 ' + m.relationships.size() + ' 条，字段映射 ' + mappings + ' 条'
        }
        var logical = f => {
            var t = text(f.typeFamily).toLowerCase()
            if (t == 'integer') { return 'Int64' }; if (t == 'decimal' || t == 'float') { return 'Decimal' }
            if (t == 'date') { return 'Date' }; if (t == 'timestamp') { return 'Timestamp' }; if (t == 'boolean') { return 'Boolean' }
            if (t == 'binary') { return 'Binary' }; return 'String'
        }
        var reverse = (ids,sourceId,occupiedNames) => {
            if (ids == null || ids.size() == 0 || ids.size() > 300) { exit 400,'请选择1至300张来源表' }
            var result = {entities:[],relationships:[],nodes:[]}
            var technical = technicalRead({entityIds:ids})
            if (technical.entities.size() != ids.size()) { exit 404,'部分来源表不存在或不属于当前租户' }
            var usedNames = occupiedNames == null ? [] : occupiedNames
            for (source in technical.entities) {
                if (text(sourceId) != '' && text(source.sourceId) != text(sourceId)) { exit 409,'所选技术表不属于当前来源数据库' }
                var e = clone(source); var fmap = {}; var originMap = {}
                e.id = IdUtil.fastSimpleUUID(); e.sourceId = 'panshi-design'; e.grain = ''; e.role = 'SOURCE'
                var originalName = text(source.name)
                var safeName = originalName.replaceAll('[^A-Za-z0-9_]','_')
                if (!safeName.matches('[A-Za-z_].*')) { safeName = 'SRC_' + safeName }
                if (safeName.length() > 63) { safeName = safeName.substring(0,63) }
                var alias = safeName; var suffix = 2
                while (usedNames.contains(alias.toLowerCase())) { var ending = '_S' + suffix; alias = safeName.substring(0,Math.min(safeName.length(),63-ending.length())) + ending; suffix = suffix + 1 }
                e.name = alias; usedNames.add(alias.toLowerCase())
                for (f in e.fields) { var oldId = f.id; f.id = IdUtil.fastSimpleUUID(); fmap[oldId] = f.id; originMap[f.id] = oldId
                    f.logicalType = logical(f); if (f.logicalType == 'String' && (f.length == null || f.length < 1)) { f.length = 128 }
                    if (f.logicalType == 'String' && f.length > 4000) { f.logicalType = 'Text' }
                    f.businessName = text(f.comment); f.remove('standard') }
                for (k in e.keys == null ? [] : e.keys) { k.id = IdUtil.fastSimpleUUID(); var ids2 = []; for (fid in k.fieldIds) { ids2.add(fmap[fid]) }; k.fieldIds = ids2 }
                e.origin = {entityId:source.id,sourceId:source.sourceId,tableName:originalName,snapshotId:technical.manifest[source.sourceId],fieldMap:originMap}
                result.entities.add(e); result.nodes.add({entityId:e.id,position:{x:120 + (result.entities.size() % 4) * 340,y:120 + (result.entities.size() / 4)::int * 500}})
            }
            return result
        }
        var outputFrom = (m, first) => {
            var outputName = m.code; var suffix = 1; var occupied = true
            while (occupied) {
                occupied = false
                for (source in m.entities) { if (text(source.name).toLowerCase() == outputName.toLowerCase()) { occupied = true } }
                if (occupied) { var ending = suffix == 1 ? '_D' : '_D' + suffix; outputName = m.code.substring(0,Math.min(m.code.length(),63-ending.length())) + ending; suffix = suffix + 1 }
            }
            var e = {id:IdUtil.fastSimpleUUID(),sourceId:'panshi-design',role:'OUTPUT',name:outputName,displayName:m.name,
                kind:'table',fields:[],keys:[],grain:'',comment:text(m.description)}
            var mappings = []
            if (first != null) {
                var fieldIds = {}
                for (f in first.fields) {
                    var field = clone(f); var previous = field.id; field.id = IdUtil.fastSimpleUUID(); field.path = field.name
                    field.remove('origin'); e.fields.add(field); fieldIds[previous] = field.id
                    mappings.add({id:IdUtil.fastSimpleUUID(),targetFieldId:field.id,sourceEntityId:first.id,
                        sourceFieldId:previous,transform:'DIRECT'})
                }
                for (k in first.keys == null ? [] : first.keys) {
                    var members = []; for (fid in k.fieldIds) { if (fieldIds[fid] != null) { members.add(fieldIds[fid]) } }
                    if (members.size() > 0) { e.keys.add({id:IdUtil.fastSimpleUUID(),kind:k.kind,fieldIds:members,enforced:'unknown',scope:'full'}) }
                }
            }
            m.entities.add(e); m.nodes.add({entityId:e.id,position:{x:Math.max(700,m.entities.size()*350),y:120}})
            m.outputEntityId = e.id; m.fusion = {baseEntityId:first == null ? null : first.id,joins:[],mappings:mappings}
            return e
        }
        var save = (m, fresh) => {
            var designJson = jsons.toJSONStr(content(m))
            if (fresh) {
                db.table('res_logical_model').primary('tid').insert({tid:m.id,tenant_id:tenantId,model_code:m.code,model_name:m.name,model_type:m.method,
                    domain_name:m.domain,database_id:m.databaseId,owner_name:m.owner,model_status:m.state,model_description:m.description,
                    design_json:designJson,revision_no:1,created_by:actorId,created_time:now,updated_by:actorId,updated_time:now,is_del:0})
            } else {
                // 旧版本作为更新条件。允许显式空说明覆盖旧值。
                var expected = m.version
                var changed = db.table('res_logical_model').withBlank().where()
                    .eq('tenant_id', tenantId)
                    .eq('tid', m.id)
                    .eq('is_del', 0)
                    .eq('revision_no', expected)
                    .update({
                        model_name: m.name,
                        model_description: m.description,
                        design_json: designJson,
                        model_status: m.state,
                        revision_no: expected + 1,
                        updated_by: actorId,
                        updated_time: now
                    })
                if (changed != 1) {
                    exit 409, '模型已更新，请刷新后重试'
                }
                m.version = expected + 1
            }
            // 同一事务重建当前租户模型的全部字段投影，包括此前删除的投影行。
            db.table('res_logical_model_field').where()
                .eq('tenant_id', tenantId)
                .eq('model_id', m.id)
                .delete()
            for (e in m.entities) { for (f in e.fields) {
                var pk = false; for (k in e.keys == null ? [] : e.keys) { if (k.kind == 'primary' && k.fieldIds.contains(f.id)) { pk = true } }
                db.table('res_logical_model_field').primary('tid').insert({tid:IdUtil.fastSimpleUUID(),tenant_id:tenantId,model_id:m.id,entity_id:e.id,
                    design_field_id:f.id,field_code:f.name,field_name:text(f.businessName),data_type:f.logicalType,data_length:f.length,
                    data_precision:f.precision,numeric_scale:f.scale,nullable_flag:f.nullable == 'false' ? 0 : 1,primary_key_flag:pk ? 1 : 0,
                    standard_id:f.standard == null ? null : f.standard.id,standard_revision:f.standard == null ? null : f.standard.revisionId,
                    field_description:text(f.comment),sort_no:f.ordinal,created_by:actorId,created_time:now,updated_by:actorId,updated_time:now,is_del:0})
            } }
            m.updatedAt = now; return m
        }
        if (kind == 'itemsets') {
            var set = find(state.itemSets,id); check(set)
            if (action != 'submit' || set.state != 'DRAFT') { exit 409,'数据项集已提交或操作不支持' }
            var m = find(state.models,set.modelId)
            return db.transaction(() => {
                var items = clone(set.items); var created = 0
                for (item in items) {
                    if (item.standardId != null || item.candidateId != null) { continue }
                    var e = find(m.entities,item.entityId); var f = find(e.fields,item.fieldId); var candidateId = IdUtil.fastSimpleUUID()
                    var candidateCode = 'PS_' + candidateId.substring(0,20).toUpperCase()
                    var candidateName = text(f.businessName) == '' ? text(f.name) : text(f.businessName)
                    if (candidateName.length() > 50) { candidateName = candidateName.substring(0,50) }
                    var definition = text(f.comment); if (definition.length() > 255) { definition = definition.substring(0,255) }
                    db.table('da_metadata_t').primary('tid').insert({tid:candidateId,tenant_id:tenantId,meta_name:candidateName,meta_code:candidateCode,
                        standard_encode:candidateCode,biz_def:definition,field_type:f.logicalType,field_length:f.length,numeric_precision:f.precision,
                        numeric_scale:f.scale,is_nullable:f.nullable == 'false' ? 0 : 1,publish_status:0,version_no:'1',created_by:actorId,
                        created_time:now,updated_by:actorId,updated_time:now,is_del:0})
                    item.candidateId = candidateId
                    created = created + 1
                }
                // 最终版本校验失败时退出事务，撤销此前写入的候选项。
                var expected = set.version
                var changed = db.table('res_model_itemset').where()
                    .eq('tenant_id', tenantId)
                    .eq('tid', id)
                    .eq('revision_no', expected)
                    .eq('is_del', 0)
                    .update({
                        items_json: jsons.toJSONStr(items),
                        itemset_status: 'SUBMITTED',
                        revision_no: expected + 1,
                        updated_time: now,
                        updated_by: actorId
                    })
                if (changed != 1) {
                    exit 409, '数据项集已经更新'
                }
                audit('提交标准候选', set, '提交 ' + items.size() + ' 个数据项，新建 ' + created + ' 个未发布标准候选草稿')
                set.items = items
                set.state = 'SUBMITTED'
                set.version = expected + 1
                return set
            })
        }
        if (kind != 'models') { exit 400,'不支持的模型对象类型' }
        if (method == 'POST' && id == '') {
            var code = key(payload.code); for (old in state.models) { if (text(old.code).toLowerCase() == code.toLowerCase()) { exit 409,'模型代码已存在' } }
            var planning = planningRead({})
            var database = find(planning.databases,required(payload.databaseId,'目标分库',64))
            if (database.state != 'ACTIVE') { exit 409,'目标分库已归档' }
            var layer = find(planning.layers,text(database.layerId))
            if (layer.state != 'ACTIVE') { exit 409,'目标分层已归档' }
            if (text(payload.layerId) != '' && text(payload.layerId) != text(layer.id)) { exit 400,'目标分库不属于所选分层' }
            var domain = text(database.domain) == '' ? text(database.name) : text(database.domain)
            if (domain.length() > 128) { domain = domain.substring(0,128) }
            var m = {id:IdUtil.fastSimpleUUID(),version:1,name:required(payload.name,'模型名称',255),code:code,domain:domain,
                description:text(payload.description),databaseId:database.id,owner:text(payload.owner) == '' ? actorId : text(payload.owner),
                method:text(payload.method) == '' ? 'MANUAL' : payload.method,state:'ACTIVE',entities:[],relationships:[],nodes:[],frozen:[],createdAt:now,updatedAt:now}
            if (!['MANUAL','STANDARD','REVERSE'].contains(m.method)) { exit 400,'不支持的建模方式' }
            if (m.method == 'REVERSE') { var r = reverse(payload.entityIds,payload.sourceId,[]); if(r.entities.size()==0){exit 400,'请选择至少一张来源技术表'}; m.entities = r.entities; m.nodes = r.nodes; outputFrom(m,m.entities[0]) }
            if (m.method == 'STANDARD') {
                if (payload.standardIds == null || payload.standardIds.size() == 0) { exit 400,'请选择已发布标准' }
                var standards = technicalRead({entityIds:[]}).standards
                var e = outputFrom(m,null)
                for (sid in payload.standardIds) {
                    var st = find(standards,sid); if (st.status != 'PUBLISHED' || st.publishedRevisionId == null) { exit 409,'仅可引用已发布标准' }
                    var name = text(st.code).matches('[A-Za-z_][A-Za-z0-9_]{0,62}') ? st.code : 'field_' + (e.fields.size() + 1)
                    var f = {id:IdUtil.fastSimpleUUID(),name:name,path:name,ordinal:e.fields.size(),nativeType:st.type,typeFamily:st.typeFamily == null ? 'string' : st.typeFamily,
                        nullable:'true',businessName:st.name,comment:st.definition,length:st.length,precision:st.precision,scale:st.scale,standard:{id:st.id,revisionId:st.publishedRevisionId}}
                    f.logicalType = logical(f)
                    if (f.logicalType == 'String' && (f.length == null || f.length < 1)) { f.length = 128 }
                    if (f.logicalType == 'String' && f.length > 4000) { f.logicalType = 'Text' }
                    e.fields.add(f)
                }
            }
            if (m.method == 'MANUAL') { outputFrom(m,null) }
            var methodName = m.method == 'REVERSE' ? '逆向解析' : m.method == 'STANDARD' ? '依标建模' : '手工建模'
            return db.transaction(() => { save(m,true); audit('创建逻辑模型',m,'创建方式：' + methodName + '；目标分层：' + layer.name + '；目标分库：' + database.name + '；' + designSummary(m)); return m })
        }
        var m = find(state.models,id)
        if (method == 'GET' && action == '') { return m }
        if (action == 'validate') { return validateModel(clone(m)) }
        check(m)
        if (m.state != 'ACTIVE' && action != 'copy') { exit 409,'模型已归档' }
        var logDetail = ''
        if (method == 'PUT' && action == '') {
            var previousName = m.name; var previousDescription = m.description; var previousDesign = jsons.toJSONStr(content(m))
            if (payload.content != null) {
                var c = payload.content
                if (c.entities == null || c.entities.size() > 300 || c.relationships == null || c.nodes == null || jsons.toJSONStr(c).length() > 5000000) { exit 400,'无效或过大的模型内容' }
                var ids = []
                for (e in c.entities) { if (text(e.id) == '' || ids.contains(e.id) || e.fields == null || e.fields.size() > 1000) { exit 400,'实体标识重复或字段不合法' }; ids.add(e.id)
                    var original = null; for (old in m.entities) { if (old.id == e.id) { original = old } }
                    if (e.reference != null && (original == null || original.reference == null)) { exit 400,'请通过引用入口添加参照实体' }
                    if (original == null && m.outputEntityId != null) { exit 409,'融合模型请通过逆向解析添加来源表，目标表 D 只能有一张' }
                    if (original != null && original.reference != null && jsons.toJSONStr(original) != jsons.toJSONStr(e)) { exit 409,'参照实体只读' }
                    if (original != null && original.role == 'SOURCE' && jsons.toJSONStr(original) != jsons.toJSONStr(e)) { exit 409,'来源技术快照只读' }
                }
                m.entities = c.entities; m.relationships = c.relationships; m.nodes = c.nodes
                if (m.outputEntityId != null || c.outputEntityId != null) {
                    if (m.outputEntityId != null && text(c.outputEntityId) != text(m.outputEntityId)) { exit 409,'目标输出表不能在保存时改写' }
                    m.outputEntityId = c.outputEntityId; m.fusion = c.fusion
                }
            }
            if (payload.name != null) { m.name = required(payload.name,'模型名称',255) }; if (payload.description != null) { m.description = text(payload.description) }
            var changes = []
            if (previousName != m.name) { changes.add('模型名称由“' + previousName + '”改为“' + m.name + '”') }
            if (previousDescription != m.description) { changes.add('业务说明已修改') }
            if (previousDesign != jsons.toJSONStr(content(m))) { changes.add('画布实体、字段、关系或映射已修改') }
            logDetail = (changes.size() == 0 ? '未检测到设计内容变化' : changes.join('；')) + '；' + designSummary(m)
        } else if (action == 'freeze') {
            var validation = validateModel(clone(m)); if (!validation.valid) { exit 422,validation.errors.join('；') }
            var standards = technicalRead({entityIds:[]}).standards
            for (e in m.entities) { for (f in e.fields) { if (f.standard != null) {
                var st = find(standards,f.standard.id)
                if (st.status != 'PUBLISHED' || st.publishedRevisionId != f.standard.revisionId) { exit 409,'引用标准已变化，请重新核对：' + f.name }
            } } }
            return db.transaction(() => {
                save(m,false)
                var rev = content(clone(m)); rev.id = IdUtil.fastSimpleUUID(); rev.number = m.frozen.size() + 1
                rev.note = required(payload.note,'版本说明',1000); rev.createdAt = now; rev.signature = DigestUtil.sha256Hex(jsons.toJSONStr(content(m)))
                db.table('res_logical_model_revision').primary('tid').insert({tid:rev.id,tenant_id:tenantId,model_id:id,revision_number:rev.number,
                    content_json:jsons.toJSONStr(content(m)),content_hash:rev.signature,note:rev.note,created_by:actorId,created_time:now})
                m.frozen.add(rev); audit('冻结设计版本',m,'冻结为 v' + rev.number + '；版本说明：' + rev.note + '；' + designSummary(m)); return rev
            })
        } else if (action == 'reverse') {
            var occupied = [];for(existing in m.entities){occupied.add(text(existing.name).toLowerCase())}
            var r = reverse(payload.entityIds,payload.sourceId,occupied); for (e in r.entities) { for (existing in m.entities) { if (existing.origin != null && text(existing.origin.entityId) == text(e.origin.entityId)) { exit 409,'该技术表已加入模型：' + text(e.origin.tableName) } }; m.entities.add(e) }
            m.nodes.addAll(r.nodes)
            if (m.outputEntityId == null && r.entities.size() > 0) { outputFrom(m,r.entities[0]) }
            logDetail = '导入来源技术表 ' + r.entities.size() + ' 张；' + designSummary(m)
        } else if (action == 'bind') {
            var e = find(m.entities,text(payload.entityId)); if (e.reference != null || e.role == 'SOURCE') { exit 409,'来源或参照实体只读' }
            var f = find(e.fields,text(payload.fieldId)); var st = find(technicalRead({entityIds:[]}).standards,text(payload.standardId))
            if (st.status != 'PUBLISHED') { exit 409,'仅允许关联已发布标准' }
            f.standard = {id:st.id,revisionId:st.publishedRevisionId}
            if (payload.apply == true) { f.businessName = st.name; f.nativeType = st.type; f.typeFamily = st.typeFamily == null ? 'string' : st.typeFamily
                f.length = st.length; f.precision = st.precision; f.scale = st.scale; f.logicalType = logical(f) }
            logDetail = '设计字段 ' + e.name + '.' + f.name + ' 绑定标准 ' + st.name + '（' + st.code + '）' + (payload.apply == true ? '，并应用标准字段定义' : '，仅建立引用')
        } else if (action == 'copy' || action == 'reference') {
            var source = action == 'copy' ? m : find(state.models,text(payload.modelId))
            var sourceName = source.name
            if (action == 'reference' && source.id == m.id) { exit 409,'模型不能引用自身' }
            var copied = action == 'copy' ? content(clone(source)) : clone(find(source.frozen,text(payload.revisionId)))
            var map = {}
            for (e in copied.entities) { map[e.id] = IdUtil.fastSimpleUUID(); for (f in e.fields) { map[f.id] = IdUtil.fastSimpleUUID() } }
            for (e in copied.entities) {
                if (action == 'reference' && e.reference != null && e.reference.modelId == m.id) { exit 409,'不允许循环参照' }
                e.id = map[e.id]; for (f in e.fields) { f.id = map[f.id] }
                for (k in e.keys == null ? [] : e.keys) { k.id = IdUtil.fastSimpleUUID(); var fields = []; for (fid in k.fieldIds) { fields.add(map[fid]) }; k.fieldIds = fields }
                if (action == 'reference') { e.reference = {modelId:source.id,revisionId:payload.revisionId} } else { e.remove('reference') }
            }
            for (r in copied.relationships) { r.id = IdUtil.fastSimpleUUID(); r.sourceEntityId = map[r.sourceEntityId]; r.targetEntityId = map[r.targetEntityId]
                for (p in r.mappings) { p.sourceFieldId = map[p.sourceFieldId]; p.targetFieldId = map[p.targetFieldId] } }
            for (n in copied.nodes) { n.entityId = map[n.entityId] }
            if (copied.outputEntityId != null) {
                copied.outputEntityId = map[copied.outputEntityId]
                if (copied.fusion != null) {
                    copied.fusion.baseEntityId = map[copied.fusion.baseEntityId]
                    for (j in copied.fusion.joins == null ? [] : copied.fusion.joins) {
                        j.id = IdUtil.fastSimpleUUID(); j.leftEntityId = map[j.leftEntityId]; j.rightEntityId = map[j.rightEntityId]
                        j.orderFieldId = j.orderFieldId == null ? null : map[j.orderFieldId]
                        for (p in j.pairs) { p.leftFieldId = map[p.leftFieldId]; p.rightFieldId = map[p.rightFieldId] }
                    }
                    for (mapping in copied.fusion.mappings == null ? [] : copied.fusion.mappings) {
                        mapping.id = IdUtil.fastSimpleUUID(); mapping.targetFieldId = map[mapping.targetFieldId]
                        mapping.sourceEntityId = map[mapping.sourceEntityId]; mapping.sourceFieldId = map[mapping.sourceFieldId]
                    }
                }
            }
            if (action == 'copy') {
                m = clone(m); m.id = IdUtil.fastSimpleUUID(); m.name = required(payload.name,'副本名称',255); m.code = key(payload.code)
                for (existing in state.models) { if (text(existing.code).toLowerCase() == text(m.code).toLowerCase()) { exit 409,'副本代码已存在' } }
                m.version = 1; m.method = 'COPY'; m.state = 'ACTIVE'; m.frozen = []; m.createdAt = now
                m.entities = copied.entities; m.relationships = copied.relationships; m.nodes = copied.nodes
                m.outputEntityId = copied.outputEntityId; m.fusion = copied.fusion
                return db.transaction(() => { save(m,true); audit('复制逻辑模型',m,'复制自模型 ' + sourceName + '；' + designSummary(m)); return m })
            }
            for (e in copied.entities) { for (old in m.entities) { if (text(old.name).toLowerCase() == text(e.name).toLowerCase()) { exit 409,'引用实体名称已存在：' + e.name } } }
            m.entities.addAll(copied.entities); m.relationships.addAll(copied.relationships); m.nodes.addAll(copied.nodes)
            logDetail = '引用模型 ' + sourceName + ' 的冻结版本，共加入 ' + copied.entities.size() + ' 个只读实体；' + designSummary(m)
        } else if (action == 'archive') {
            for (p in state.materializations) { if (p.modelId == id && ['READY','QUEUED','RUNNING','CANCELLING'].contains(p.state)) { exit 409,'尚有未完成物化方案' } }; m.state = 'ARCHIVED'
            logDetail = '模型已归档；历史版本和操作日志保留，后续不再参与设计与标准化'
        } else if (action == 'items') {
            var items = []; for (e in m.entities) { if (e.reference != null || (m.outputEntityId != null && e.id != m.outputEntityId)) { continue }; for (f in e.fields) { items.add({entityId:e.id,fieldId:f.id,name:text(f.businessName) == '' ? f.name : f.businessName,standardId:f.standard == null ? null : f.standard.id}) } }
            if (items.size() == 0) { exit 400,'模型没有可生成数据项集的字段' }
            var bound = 0; for (item in items) { if (item.standardId != null) { bound = bound + 1 } }
            var setId = IdUtil.fastSimpleUUID(); var name = m.name + '数据项集'
            db.table('res_model_itemset').primary('tid').insert({tid:setId,tenant_id:tenantId,model_id:id,itemset_name:name,items_json:jsons.toJSONStr(items),itemset_status:'DRAFT',revision_no:1,created_by:actorId,created_time:now,updated_by:actorId,updated_time:now,is_del:0})
            audit('生成模型数据项集',{id:setId,name:name},'来源模型：' + m.name + '；生成 ' + items.size() + ' 个数据项，其中已绑定标准 ' + bound + ' 个'); return {id:setId,name:name,modelId:id,items:items,state:'DRAFT',version:1,createdAt:now,updatedAt:now}
        } else { exit 400,'不支持的模型操作' }
        return db.transaction(() => { save(m,false); audit('维护逻辑模型：' + (action == '' ? '保存' : action),m,logDetail + '；设计修订 v' + m.version); return m })
    }
    return modelCommand(command)
}
// 编排物化方案的预检、提交、执行、重试和装载。
if (kind == 'materializations') {
    var materializationCommand = input => {
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
            var planning = planningRead({})
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
            var models = modelRead({entities: []})
            var model = find(models.models, text(payload.modelId))
            var frozen = find(model.frozen, text(payload.revisionId))
            var planning = planningRead({})
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
    }
    return materializationCommand(command)
}
// 处理目录编目、发布、订阅、交付及可见范围。
if (['catalogs','publications','subscriptions','deliveries','policy'].contains(kind)) {
    var catalogCommand = input => {
        var text = value => value == null ? '' : (value + '').trim()
        var parse = value => text(value) == '' ? {} : jsons.toJSONObject(value)
        var payload = input.body == null ? {} : input.body
        var kind = text(input.kind)
        var id = text(input.id)
        var action = text(input.action)
        var method = text(input.method).toUpperCase()

        // 资源中心详情按其来源数据权限读取，避免套用盘点入口的目录归属范围。
        if (kind == 'catalogs' && method == 'GET' && action == 'directory-detail') {
            return catalogRead({
                mode: 'center-directory-detail',
                id: id
            })
        }

        var tenantId = tenantRuntime.id()
        var actorId = StpUtil.getLoginIdAsString()
        var user = currentUser()
        var orgId = user.orgId == null ? '' : user.orgId + ''
        var now = new Date()
        var required = (value, title, max) => {
            var result = text(value)
            if (result == '' || result.length() > max) {
                exit 400, title + '不能为空或超过长度限制'
            }
            return result
        }
        var epoch = value => {
            try {
                return Instant.parse(text(value)).toEpochMilli()
            } catch (error) {
                exit 400, '请提供带时区的有效日期时间'
            }
        }
        var checkVersion = row => {
            if (input.version == null || text(input.version) != text(row.version)) {
                exit 409, '记录已更新，请刷新后重试'
            }
        }
        var requireOrganizations = values => {
            if (!(values instanceof List) || values.size() == 0 || values.size() > 1000) {
                exit 400, '请选择 1 至 1000 个可见部门'
            }
            var ids = []
            var unique = {}
            for (value in values) {
                var key = required(value, '部门', 32)
                if (unique[key] == null) {
                    unique[key] = true
                    ids.add(key)
                }
            }
            // 一次读取本租户的有效部门，再逐个核验输入，外租户和已删除部门均不能加入可见范围。
            var found = {}
            var rows = db.lower().table('rm_org_t').columns('id').where()
                .eq('tenant_id', tenantId).eq('deleted', 0).in('id', ids).select()
            for (row in rows) {
                found[text(row.id)] = true
            }
            for (key in ids) {
                if (found[key] == null) {
                    exit 400, '请选择当前租户的有效部门'
                }
            }
        }
        var ownProvider = provider => {
            if (orgId == '' || orgId != text(provider)) {
                exit 403, '只允许资源提供部门维护或审核此目录'
            }
        }

        var selectBoundSourceTable = tableId => {
            var table = db.lower().table('db_table_t')
                .columns('tid', 'datasource_id', 'table_name')
                .where()
                .eq('tenant_id', tenantId)
                .eq('tid', tableId)
                .eq('is_del', 0)
                .selectOne()
            if (table == null || text(table.datasource_id) == '') {
                exit 400, '请选择数仓规划已关联数据源中的数据表'
            }

            // 保存前使用与资源中心列表相同的来源权限，防止越权标识先被写入再由详情拒绝。
            var scope = dataScope.dataSource()
            if (scope == null || text(scope.userId) == '' || !['ALL', 'ORG', 'OWNER'].contains(scope.scope)) {
                exit 403, '无法确定当前数据范围'
            }
            var source = db.table('db_datasource_t')
                .columns('tid')
                .where()
                .eq('tenant_id', tenantId)
                .eq('tid', table.datasource_id)
                .eq('is_del', 0)
            if (scope.scope == 'OWNER') {
                source.eq('created_by', scope.userId)
            } else if (scope.scope == 'ORG') {
                if (scope.organizationIds == null || scope.organizationIds.size() == 0) {
                    exit 400, '请选择数仓规划已关联数据源中的数据表'
                }
                source.in('org_id', scope.organizationIds)
            }
            if (source.selectOne() == null) {
                exit 400, '请选择数仓规划已关联数据源中的数据表'
            }

            var bindings = db.table('dwm_center_layer_source_t')
                .where()
                .eq('tenant_id', tenantId)
                .eq('datasource_id', table.datasource_id)
                .eq('is_del', 0)
                .in('target_type', ['layer', 'domain'])
                .count()
            if (bindings == 0) {
                exit 400, '请选择数仓规划已关联数据源中的数据表'
            }
            return table
        }

        var catalogVersionQuery = (catalogId, expected) => {
            var query = db.table('da_catalog_t')
                .where()
                .eq('tenant_id', tenantId)
                .eq('tid', catalogId)
                .eq('is_del', 0)
            // 历史空版本按 1 读取。将版本条件放入更新语句，避免读取后并发覆盖。
            if (expected == 1) {
                query.and(version => version.eq('resource_revision_no', expected).or().isNull('resource_revision_no'))
            } else {
                query.eq('resource_revision_no', expected)
            }
            return query
        }

        var writeDraft = (catalogId, draft) => {
            var old = db.table('da_prop_t').where().eq('tenant_id', tenantId).eq('parent_id', catalogId)
                .eq('data_type', 'catalog').eq('prop_name', 'resourceCenterDraft').eq('is_del', 0).selectOne()
            if (old == null) {
                db.table('da_prop_t').primary('tid').insert({
                    tid: IdUtil.getSnowflakeNextIdStr(),
                    tenant_id: tenantId,
                    parent_id: catalogId,
                    data_type: 'catalog',
                    prop_name: 'resourceCenterDraft',
                    prop_type: '0',
                    prop_value: jsons.toJSONStr(draft),
                    is_del: 0,
                    created_time: now,
                    updated_time: now,
                    created_by: actorId,
                    updated_by: actorId
                })
            } else {
                db.table('da_prop_t').where().eq('tenant_id', tenantId).eq('tid', old.tid)
                    .update({prop_value: jsons.toJSONStr(draft), updated_time: now, updated_by: actorId})
            }
        }
        var bumpCatalog = (catalogId, version) => {
            var changed = db.table('da_catalog_t').where().eq('tid', catalogId).eq('tenant_id', tenantId)
                .eq('resource_revision_no', version).eq('is_del', 0)
                .update({resource_revision_no: version + 1, updated_time: now, updated_by: actorId})
            if (changed != 1) {
                exit 409, '目录已被更新，请刷新'
            }
        }
        var validate = (draft, freeze) => {
            for (key in ['name', 'code', 'summary', 'provider', 'contact', 'resourceId', 'classificationId']) {
                if (text(draft[key]) == '') {
                    exit 400, '目录必填信息不完整：' + key
                }
            }
            requireOrganizations([draft.provider])
            var classes = planningRead({}).classifications
            var hasClass = false
            for (item in classes) {
                if (item.id == draft.classificationId) {
                    hasClass = true
                }
            }
            if (!hasClass) {
                exit 400, '请选择有效的资源目录分类'
            }
            if (!['CSV', 'JSON'].contains(draft.format)) {
                exit 400, '请选择 CSV 或 JSON 交付格式'
            }
            if (!['ONCE', 'DAILY', 'WEEKLY', 'MONTHLY'].contains(draft.frequency)) {
                exit 400, '更新频率无效'
            }
            if (!['CONDITIONAL', 'UNCONDITIONAL', 'NONE'].contains(draft.shareMode)) {
                exit 400, '共享方式无效'
            }
            if (draft.shareMode != 'UNCONDITIONAL' && text(draft.conditions) == '') {
                exit 400, '请填写共享条件或不共享原因'
            }
            if (!['WORKSPACE', 'DEPARTMENTS'].contains(draft.visibility)) {
                exit 400, '可见范围无效'
            }
            if (draft.visibility == 'DEPARTMENTS') {
                requireOrganizations(draft.visibleDepartments)
            }
            var structure = catalogRead({mode: 'structure', id: draft.resourceId})
            var fieldById = {}
            for (field in structure.fields) {
                fieldById[text(field.id)] = field
            }
            if (draft.fieldIds == null || draft.fieldIds.isEmpty() || draft.fieldIds.size() > 500) {
                exit 400, '请选择 1 至 500 个数据项'
            }
            var seen = []
            var labels = []
            for (fieldId in draft.fieldIds) {
                var field = fieldById[text(fieldId)]
                if (field == null || seen.contains(fieldId)) {
                    exit 400, '数据项不属于来源表或重复选择'
                }
                if (text(field.name).toLowerCase().matches('.*(password|passwd|secret|token|private_key|internal_note).*')) {
                    exit 400, '交付范围包含禁止公开的凭据或内部字段'
                }
                var label = draft.fieldLabels == null ? '' : text(draft.fieldLabels[text(fieldId)])
                if (label == '') {
                    label = text(field.comment) == '' ? field.name : field.comment
                }
                if (labels.contains(label)) {
                    exit 400, '对外数据项名称不能重复'
                }
                labels.add(label);
                seen.add(fieldId)
            }
            if (draft.structure != null && !freeze) {
                for (prior in draft.structure.fields) {
                    if (!draft.fieldIds.contains(prior.id)) {
                        continue
                    }
                    var latest = fieldById[text(prior.id)]
                    if (latest == null || text(latest.name) != text(prior.name) || text(latest.nativeType) != text(prior.nativeType) ||
                        text(latest.length) != text(prior.length) || text(latest.scale) != text(prior.scale)) {
                        exit 409, '批准的数据项结构已变化，请重新编制并审核目录版本'
                    }
                }
            }
            return structure
        }
        var saveSub = (sub, version, flowStatus) => {
            var changed = db.table('data_apply_form_t').where().eq('tid', sub.id).eq('tenant_id', tenantId)
                .eq('resource_revision_no', version).eq('is_del', 0).update({
                    resource_scope_json: jsons.toJSONStr(sub),
                    resource_revision_no: version + 1,
                    flow_status: flowStatus,
                    updated_time: now,
                    updated_by: actorId
                })
            if (changed != 1) {
                exit 409, '订阅已被修改，请刷新后重试'
            }
        }
        var syncCatalogIndex = catalogId => {
            try {
                // 状态更新通过业务校验后，使用同一租户的内部详情投影刷新索引。
                var projection = catalogRead({
                    mode: 'directory-index-projection',
                    id: catalogId
                })
                esCommonService.mergeCatalogProjection('dataassets', catalogId, jsons.toJSONStr(projection), projection.viewCount)
            } catch (error) {
                log.warn('catalog search projection refresh failed, tid: {}, error: {}', catalogId, error.message)
            }
        }
        var writeAuditNote = (catalogId, opinion) => {
            var old = db.table('da_prop_t').where().eq('tenant_id', tenantId).eq('parent_id', catalogId)
                .eq('data_type', 'catalog').eq('prop_name', 'resourceCenterAuditOpinion').eq('is_del', 0).selectOne()
            if (old == null) {
                db.table('da_prop_t').primary('tid').insert({
                    tid: IdUtil.getSnowflakeNextIdStr(),
                    tenant_id: tenantId,
                    parent_id: catalogId,
                    data_type: 'catalog',
                    prop_name: 'resourceCenterAuditOpinion',
                    prop_type: '0',
                    prop_value: opinion,
                    is_del: 0,
                    created_time: now,
                    updated_time: now,
                    created_by: actorId,
                    updated_by: actorId
                })
            } else {
                db.table('da_prop_t').where().eq('tenant_id', tenantId).eq('tid', old.tid)
                    .update({prop_value: opinion, updated_time: now, updated_by: actorId})
            }
        }
        if (kind == 'policy') {
            exit 409, '目录审核必须由提供部门办理；跨网同步尚未配置真实连接，不允许启用模拟结果'
        }
        if (kind == 'catalogs') {
            var entry = id == '' ? null : catalogRead({mode: 'one', id: id})
            if (method == 'GET') {
                return entry
            }
            if (entry != null) {
                checkVersion(entry)
            }
            if (action == '' && ['POST', 'PUT'].contains(method)) {
                if (entry != null && !['DRAFT', 'RETURNED'].contains(entry.draft.state)) {
                    exit 409, '请先撤回审核或重新编目后再修改目录'
                }
                var props = payload.propList
                if (props == null) {
                    exit 400, '请填写目录编目信息'
                }
                required(props.catalogName, '目录名称', 255)
                required(props.orgId, '来源部门', 32)
                var tableId = required(props.sourceTableId, '数据中心表', 32)
                var selectedTable = selectBoundSourceTable(tableId)
                var existingCatalog = db.table('da_catalog_t')
                    .where()
                    .eq('tenant_id', tenantId)
                    .eq('source_table_id', tableId)
                    .eq('is_del', 0)
                // 编辑时排除当前目录。新建时不把空字符串作为数据库筛选条件。
                if (id != '') {
                    existingCatalog.ne('tid', id)
                }
                if (existingCatalog.count() > 0) {
                    exit 409, '这张数据表已有目录，请打开现有目录编目'
                }
                props.dbId = selectedTable.datasource_id
                props.sourceTableName = selectedTable.table_name
                if (payload.catalogItems == null || payload.catalogItems.isEmpty()) {
                    exit 400, '请至少选择一项数据表字段'
                }
                for (item in payload.catalogItems) {
                    required(item.colName, '数据项名称', 255)
                    required(item.colEn, '数据项英文名', 255)
                    required(item.colType, '数据项类型', 255)
                }
                if (payload.dryRun == true) {
                    return {
                        dryRun: true,
                        sourceTableId: tableId,
                        sourceId: selectedTable.datasource_id,
                        itemCount: payload.catalogItems.size()
                    }
                }
                var previousBody = body
                var saved = null
                try {
                    body = {
                        tid: id == '' ? null : id,
                        assetType: 'catalog',
                        reviewRequired: true,
                        propList: props,
                        catalogItems: payload.catalogItems
                    }
                    saved = saveCatalog()
                } finally {
                    body = previousBody
                }
                var catalogId = text(saved.tid) == '' ? text(saved.id) : text(saved.tid)
                if (catalogId == '') {
                    exit 500, '目录保存未返回有效标识'
                }
                return catalogRead({mode: 'one', id: catalogId})
            }
            if (entry == null) {
                exit 404, '资源目录不存在'
            }
            if (action == 'check') {
                var count = db.table('da_catalog_item_t').where().eq('tenant_id', tenantId)
                    .eq('catalog_id', id).eq('is_del', 0).count()
                return {id: id, checks: count > 0 ? [] : [{code: 'items', level: 'ERROR', message: '请先编制目录数据项'}]}
            }
            if (action == 'submit') {
                if (!['DRAFT', 'RETURNED'].contains(entry.draft.state)) {
                    exit 409, '当前目录不可提交审核'
                }
                var count = db.table('da_catalog_item_t').where().eq('tenant_id', tenantId)
                    .eq('catalog_id', id).eq('is_del', 0).count()
                if (count == 0) {
                    exit 400, '请先编制目录数据项'
                }
                var expected = entry.version
                var changed = catalogVersionQuery(id, expected)
                    .and(status => status.in('flow_status', [0, 3, 4]).or().isNull('flow_status'))
                    .update({
                        asset_status: 1,
                        flow_status: 1,
                        resource_revision_no: expected + 1,
                        updated_time: now,
                        updated_by: actorId
                    })
                if (changed != 1) {
                    exit 409, '目录状态已变化，请刷新'
                }
                syncCatalogIndex(id)
                return {id: id, state: 'PENDING_REVIEW'}
            }
            if (action == 'review' || action == 'withdraw') {
                if (entry.draft.state != 'PENDING_REVIEW') {
                    exit 409, '目录不在审核中'
                }
                if (action == 'review' && text(entry.draft.submittedBy) == actorId) {
                    exit 403, '不可审核本人提交的目录'
                }
                if (action == 'withdraw' && text(entry.draft.submittedBy) != actorId) {
                    exit 403, '只允许提交人撤回'
                }
                var approve = action == 'review' && payload.approve == true
                var opinion = action == 'withdraw' ? '提交人撤回' : text(payload.opinion)
                if (action == 'review' && !approve) {
                    opinion = required(payload.opinion, '退回原因', 1000)
                }
                var changed = db.transaction(() => {
                    var assetStatus = action == 'withdraw' || !approve ? 0 : 2
                    var flowStatus = action == 'withdraw' ? 4 : approve ? 2 : 3
                    var expected = entry.version
                    var updated = catalogVersionQuery(id, expected)
                        .eq('flow_status', 1)
                        .update({
                            asset_status: assetStatus,
                            flow_status: flowStatus,
                            resource_revision_no: expected + 1,
                            updated_time: now,
                            updated_by: actorId
                        })
                    if (updated != 1) {
                        exit 409, '审核状态已变化，请刷新'
                    }
                    writeAuditNote(id, opinion)
                    return updated
                })
                syncCatalogIndex(id)
                return {id: id, approved: approve, changed: changed}
            }
            if (action == 'new-version') {
                if (entry.draft.state != 'APPROVED') {
                    exit 409, '只有已审核目录可以重新编目'
                }
                var expected = entry.version
                var changed = catalogVersionQuery(id, expected)
                    .eq('flow_status', 2)
                    .eq('asset_status', 2)
                    .update({
                        asset_status: 0,
                        flow_status: 0,
                        resource_revision_no: expected + 1,
                        updated_time: now,
                        updated_by: actorId
                    })
                if (changed != 1) {
                    exit 409, '目录已更新，请刷新'
                }
                syncCatalogIndex(id)
                return {id: id, state: 'DRAFT'}
            }
            exit 400, '不支持的目录动作'
        }
        if (kind == 'publications') {
            if (id == '' && method == 'POST') {
                var entry = catalogRead({mode: 'one', id: required(payload.entryId, '目录', 32)})
                ownProvider(entry.draft.provider)
                var revisionId = required(payload.revisionId, '版本', 32)
                if (entry.draft.id != revisionId || entry.draft.state != 'APPROVED') {
                    exit 409, '只能发布当前已批准的冻结版本'
                }
                validate(entry.draft, false)
                var changed = db.table('res_catalog_publish_request').where().eq('tenant_id', tenantId).eq('tid', revisionId)
                    .eq('request_status', 'APPROVED').eq('is_del', 0).update({
                        request_status: 'PUBLISHED',
                        published_time: now,
                        updated_time: now,
                        updated_by: actorId,
                        revision_no: 2
                    })
                if (changed != 1) {
                    exit 409, '该版本已发布或状态已变化'
                }
                return {id: revisionId, state: 'PUBLISHED'}
            }
            var publication = catalogRead({mode: 'publication', id: id})
            ownProvider(publication.revision.provider);
            checkVersion(publication)
            if (action == 'sync') {
                exit 409, '未配置真实跨网目录同步连接，不能将模拟结果记为成功'
            }
            if (action != 'offline') {
                exit 400, '不支持的发布动作'
            }
            var reason = required(payload.reason, '下线原因', 1000)
            var changed = db.table('res_catalog_publish_request').where().eq('tenant_id', tenantId).eq('tid', id)
                .eq('request_status', 'PUBLISHED').eq('revision_no', publication.version).eq('is_del', 0)
                .update({
                    request_status: 'OFFLINE',
                    offline_reason: reason,
                    revision_no: publication.version+1,
                    updated_time: now,
                    updated_by: actorId
                })
            if (changed != 1) {
                exit 409, '发布状态已变化，请刷新'
            }
            return {id: id, state: 'OFFLINE'}
        }
        if (kind == 'subscriptions' && id == '') {
            var publication = catalogRead({mode: 'publication', id: required(payload.publicationId, '发布版本', 32)})
            if (publication.state != 'PUBLISHED' || publication.revision.shareMode == 'NONE') {
                exit 409, '此版本不允许申请'
            }
            requireOrganizations([orgId])
            var fields = payload.fieldIds
            if (fields == null || fields.isEmpty()) {
                exit 400, '请选择申请数据项'
            }
            var seen = []
            for (field in fields) {
                if (!publication.revision.fieldIds.contains(field) || seen.contains(field)) {
                    exit 400, '申请字段超出发布范围或重复'
                }
                seen.add(field)
            }
            var from = epoch(payload.validFrom)
            var until = epoch(payload.validTo)
            if (until <= now.getTime() || until <= from || until-from > 366*86400000L) {
                exit 400, '请设置一年以内的有效申请期限'
            }
            if (payload.frequency != 'ONCE') {
                exit 409, '当前交付引擎支持单次真实导出，请选择单次交付；周期调度尚未启用'
            }
            id = IdUtil.getSnowflakeNextIdStr()
            var sub = {
                id: id,
                publicationId: publication.id,
                applicantId: actorId,
                department: orgId,
                purpose: required(payload.purpose, '业务用途', 1000),
                fieldIds: fields,
                approvedFields: [],
                frequency: 'ONCE',
                validFrom: text(payload.validFrom),
                validTo: text(payload.validTo),
                state: 'PENDING'
            }
            db.table('data_apply_form_t').primary('tid').insert({
                tid: id,
                tenant_id: tenantId,
                apply_user_id: actorId,
                apply_user_name: text(user.name),
                apply_org_id: orgId,
                apply_name: publication.revision.name+'订阅申请',
                catalog_ids: publication.entryId,
                type: 'catalogSubscribe',
                flow_status: 1,
                resource_scope_json: jsons.toJSONStr(sub),
                resource_revision_no: 1,
                created_by: actorId,
                updated_by: actorId,
                created_time: now,
                updated_time: now,
                is_del: 0
            })
            return {id: id, state: 'PENDING'}
        }
        if (kind == 'subscriptions' || kind == 'deliveries') {
            var delivery = kind == 'deliveries' ? catalogRead({mode: 'delivery', id: id}) : null
            var sub = catalogRead({mode: 'subscription', id: delivery == null ? id : delivery.subscriptionId})
            var publication = catalogRead({mode: 'publication', id: sub.publicationId})
            if (kind == 'subscriptions' && action == 'review') {
                ownProvider(publication.revision.provider);
                checkVersion(sub)
                if (sub.applicantId == actorId) {
                    exit 403, '不得审批自己的订阅申请'
                }
                if (sub.state != 'PENDING') {
                    exit 409, '申请已处理'
                }
                sub.opinion = required(payload.opinion, '审批意见', 1000);
                sub.reviewer = actorId
                if (payload.approve == true) {
                    if (publication.state != 'PUBLISHED') {
                        exit 409, '目录已下线'
                    }
                    validate(publication.revision, false)
                    var fields = payload.fieldIds
                    if (fields == null || fields.isEmpty()) {
                        exit 400, '请选择批准数据项'
                    }
                    var seen = []
                    for (field in fields) {
                        if (!sub.fieldIds.contains(field) || !publication.revision.fieldIds.contains(field) || seen.contains(field)) {
                            exit 400, '批准范围只能缩小'
                        }
                        seen.add(field)
                    }
                    var until = payload.validTo == null ? sub.validTo : text(payload.validTo)
                    if (epoch(until) > epoch(sub.validTo) || epoch(until) <= Math.max(now.getTime(), epoch(sub.validFrom))) {
                        exit 400, '批准期限只能缩短且必须仍有效'
                    }
                    sub.validTo = until;
                    sub.approvedFields = fields;
                    sub.grantId = IdUtil.getSnowflakeNextIdStr()
                    sub.state = epoch(sub.validFrom) > now.getTime() ? 'SCHEDULED' : 'ACTIVE'
                } else {
                    sub.state = 'RETURNED'
                }
                saveSub(sub, sub.version, payload.approve == true ? 2 : 4)
                return {id: sub.id, state: sub.state}
            }
            if (kind == 'subscriptions' && ['revoke', 'pause'].contains(action)) {
                checkVersion(sub)
                if (sub.applicantId != actorId) {
                    ownProvider(publication.revision.provider)
                }
                sub.opinion = required(payload.reason, '原因', 1000);
                sub.state = action == 'revoke' ? 'REVOKED' : 'PAUSED'
                saveSub(sub, sub.version, 6)
                return {id: sub.id, state: sub.state}
            }
            if (sub.applicantId != actorId) {
                exit 403, '只允许申请人读取获批数据'
            }
            if (publication.state != 'PUBLISHED' || sub.state != 'ACTIVE' || epoch(sub.validFrom) > now.getTime() || epoch(sub.validTo) <= now.getTime()) {
                exit 403, '授权尚未生效、已撤销、已暂停或已到期'
            }
            var latestStructure = validate(publication.revision, false)
            if (kind == 'deliveries') {
                if (delivery.state != 'SUCCEEDED') {
                    exit 409, '批次尚未生成成功'
                }
                return delivery
            }
            if (action != 'deliver') {
                exit 400, '不支持的订阅动作'
            }
            var columns = []
            var labels = []
            var masks = []
            for (fieldId in sub.approvedFields) {
                if (!sub.fieldIds.contains(fieldId) || !publication.revision.fieldIds.contains(fieldId)) {
                    exit 403, '批准范围无效'
                }
                for (field in publication.revision.structure.fields) {
                    if (field.id != fieldId) {
                        continue
                    }
                    columns.add(field.name)
                    labels.add(publication.revision.fieldLabels[fieldId] == null ? field.name : publication.revision.fieldLabels[fieldId])
                    var latestMask = false
                    for (latestField in latestStructure.fields) {
                        if (latestField.id == field.id && (latestField.masking == true || text(latestField.masking) == '1')) {
                            latestMask = true
                        }
                    }
                    masks.add(text(field.name).toLowerCase().matches('.*(phone|mobile|email|identity|certificate).*') || latestMask || field.masking == true || text(field.masking) == '1')
                }
            }
            if (columns.isEmpty()) {
                exit 403, '没有有效的批准字段'
            }
            var deliveryId = IdUtil.getSnowflakeNextIdStr()
            var tableId = publication.revision.resourceId
            db.table('data_distribution_task_t').primary('tid').insert({
                tid: deliveryId,
                tenant_id: tenantId,
                apply_form_id: sub.id,
                catalog_id: publication.entryId,
                catalog_name: publication.revision.name,
                source_table_id: tableId,
                source_table_name: publication.revision.structure.name,
                task_code: 'EXPORT-'+deliveryId,
                task_name: publication.revision.name+'批准范围导出',
                task_status: 'RUNNING',
                progress: 0,
                execution_engine: 'RESOURCE_EXPORT',
                execution_mode: 'APPROVED_SNAPSHOT',
                started_time: now,
                created_time: now,
                updated_time: now,
                created_by: actorId,
                updated_by: actorId,
                is_del: 0
            })
            try {
                var previousBody = body
                var sampled = null
                try {
                    body = {tableId: tableId, sampleSize: 1001, columns: columns};
                    sampled = sampleData()
                } finally {
                    body = previousBody
                }
                var raw = sampled.rows == null ? sampled.data : sampled.rows
                if (raw == null) {
                    exit 502, '来源未返回可识别的数据行，未生成空白成功文件'
                }
                var rows = []
                var csvCell = value => {
                    var cell = value == null ? '' : value + ''
                    if (cell.matches('^[=+@-].*')) {
                        cell = "'"+cell
                    }
                    return '"'+cell.replace('"', '""')+'"'
                }
                var lines = []
                var header = []
                for (label in labels) {
                    header.add(csvCell(label))
                }
                lines.add(header.join(','))
                var index = 0
                for (row in raw) {
                    if (index >= 1000) {
                        break
                    }
                    var output = {};
                    var line = [];
                    var fieldIndex = 0
                    for (column in columns) {
                        var value = row[column]
                        if (masks[fieldIndex] && value != null) {
                            value = '***'
                        }
                        output[labels[fieldIndex]] = value;
                        line.add(csvCell(value));
                        fieldIndex = fieldIndex+1
                    }
                    rows.add(output);
                    lines.add(line.join(','));
                    index = index+1
                }
                var result = {
                    subscriptionId: sub.id,
                    publicationId: publication.id,
                    grantId: sub.grantId,
                    fieldIds: sub.approvedFields,
                    rows: rows.size(),
                    availableRows: raw.size(),
                    truncated: raw.size() > 1000,
                    format: publication.revision.format,
                    filename: 'resource_'+publication.id+'_'+deliveryId+'.'+publication.revision.format.toLowerCase(),
                    content: publication.revision.format == 'JSON' ? jsons.toJSONStr(rows) : '\ufeff'+lines.join('\r\n'),
                    scheduledFor: now
                }
                db.table('data_distribution_task_t').where().eq('tenant_id', tenantId).eq('tid', deliveryId)
                    .update({
                        task_status: 'SUCCEEDED',
                        progress: 100,
                        resource_result_json: jsons.toJSONStr(result),
                        status_message: raw.size() > 1000 ? '已导出前1000行，来源记录更多' : '批准字段真实数据导出完成',
                        finished_time: new Date(),
                        updated_time: new Date()
                    })
                return {id: deliveryId, state: 'SUCCEEDED', rows: rows.size(), truncated: raw.size() > 1000}
            } catch (error) {
                db.table('data_distribution_task_t').where().eq('tenant_id', tenantId).eq('tid', deliveryId)
                    .update({
                        task_status: 'FAILED',
                        status_message: '来源读取失败，请检查数据源连接和字段结构',
                        finished_time: new Date(),
                        updated_time: new Date()
                    })
                exit 502, '真实来源读取失败，交付任务已记录失败，可检查连接后重试'
            }
        }
        exit 400, '不支持的目录运营操作'
    }
    return catalogCommand(command)
}
if (kind != 'resources') { exit 400, '未知资源业务操作' }

// 登记、读取与归档共享同一个可信租户范围。
if (tenantId == null || tenantId == '') {
    exit 401, '无法识别当前租户'
}
var actorId = StpUtil.getLoginIdAsString()
var now = new Date()
var text = v => v == null ? '' : (v + '').trim()
var payload = command.body == null ? {} : command.body
var id = text(command.id)
var action = text(command.action)
var method = text(command.method)
var find = (rows, key) => {
    for (row in rows) {
        if (row.id == key) {
            return row
        }
    }
    exit 404, '对象不存在或不属于当前租户'
}

if (id == '' && method == 'POST') {
    id = text(payload.entityId)
    var table = db.camel().table('db_table_t').columns(
        'tid', 'datasource_id', 'table_name', 'table_name_cn', 'resource_domain_id', 'resource_state', 'resource_revision_no'
    ).where().eq('tenant_id', tenantId).eq('tid', id).eq('is_del', 0).selectOne()
    if (table == null) {
        exit 404, '技术实体不存在'
    }
    if (text(table.resourceDomainId) != '' && table.resourceState == 'ACTIVE') {
        exit 409, '该技术实体已有有效资源登记'
    }
    var domain = find(planningRead({}).databases, text(payload.databaseId))
    if (domain.state != 'ACTIVE') {
        exit 409, '规划分库已归档'
    }
    var owner = text(payload.owner)
    if (owner == '' || owner.length() > 128) {
        exit 400, '请填写责任方，最多128字'
    }
    var name = text(payload.name) == '' ? table.tableNameCn : text(payload.name)
    if (name == null || text(name) == '') {
        name = table.tableName
    }
    if (text(name).length() > 512 || text(payload.domain).length() > 64) {
        exit 400, '名称或业务域超过长度限制'
    }
    var expected = table.resourceRevisionNo
    // 同时核对登记条件和旧版本，审计写入失败时回滚登记。
    db.transaction(() => {
        var changed = db.table('db_table_t').withBlank().where().eq('tenant_id', tenantId)
            .eq('tid', id).eq('is_del', 0).eq('resource_revision_no', expected)
            .and(available => available.isNull('resource_domain_id').or().eq('resource_state', 'ARCHIVED'))
            .update({
                resource_domain_id: domain.id, resource_owner: owner, resource_state: 'ACTIVE',
                resource_revision_no: expected + 1, table_name_cn: name, business_type: text(payload.domain),
                asset_desc: text(payload.description), asset_status: 2, flow_status: 2, reg_time: now,
                updated_time: now, updated_by: actorId
            })
        if (changed != 1) {
            exit 409, '该技术实体已由另一请求登记'
        }
        db.table('res_resource_operation_log').primary('tid').insert({
            tid: IdUtil.fastSimpleUUID(), tenant_id: tenantId, actor_id: actorId,
            action_name: '登记仓库资源', object_id: id, object_name: name,
            detail_text: '沿用共享技术实体，未复制数据表', created_time: now
        })
    })
    if (text(table.datasourceId) != '') {
        metadataAsset.refreshRegistrationStatus(table.datasourceId, false)
    }
    return find(modelRead({}).resources, id)
}

if (id == '') {
    exit 404, '对象不存在或不属于当前租户'
}
var resource = find(modelRead({}).resources, id)
if (method == 'GET') {
    var technical = technicalRead({entityIds: [id]})
    var record = resource
    record.source = find(technical.sources, resource.structure.sourceId)
    var allowed = []
    var masked = []
    var fields = db.lower().table('db_table_column_t').columns('column_name', 'is_masking').where()
        .eq('tenant_id', tenantId).eq('table_id', id).eq('is_del', 0).orderBy('ordinal_position').select()
    // 凭据字段不进入预览请求，个人信息和显式脱敏字段在响应中统一掩码。
    for (field in fields) {
        var name = text(field.column_name)
        var normalized = name.toLowerCase()
        if (normalized.matches('.*(password|secret|token|credential|private_key).*')) {
            continue
        }
        allowed.add(name)
        if (field.is_masking == 1 || normalized.matches('.*(phone|mobile|email|identity|certificate_no).*')) {
            masked.add(name)
        }
    }
    record.records = []
    if (allowed.size() > 0) {
        var previousBody = body
        try {
            body = {tableId: id, sampleSize: 10, columns: allowed}
            var sample = sampleData()
            var rows = sample.data == null ? (sample.rows == null ? [] : sample.rows) : sample.data
            for (row in rows) {
                for (fieldName in masked) {
                    if (row[fieldName] != null) {
                        row[fieldName] = '***'
                    }
                }
                record.records.add(row)
            }
        } catch (e) {
            record.previewError = '读取数据预览失败，请稍后重试'
        } finally {
            body = previousBody
        }
    }
    return record
}
if (action != 'archive') {
    exit 400, '不支持的资源操作'
}
if (text(command.version) != text(resource.version)) {
    exit 409, '资源已更新，请刷新重试'
}
// 只检查当前租户仍有效的发布引用，找到一项就拒绝归档。
var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var numeric = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var published = tableRead.from('res_catalog_publish_request', 'p', tenantId)
published.leftJoin('da_catalog_t', 'c', 'tid', 'catalog_id', [tableRead.equal(numeric('c', 'is_del'), 0)])
published.where(tableRead.equal(numeric('p', 'is_del'), 0))
published.where(tableRead.equal(column('p', 'request_status'), 'PUBLISHED'))
published.where(tableRead.equal(column('c', 'source_table_id'), id))
var publishedId = column('p', 'tid')
if (published.rows({id: publishedId}, publishedId, 'asc', 1).size() > 0) {
    exit 409, '仍有已发布目录引用该资源，请先下线目录'
}
var expected = resource.version
var changed = db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', id).eq('is_del', 0)
    .eq('resource_revision_no', expected).update({
        resource_state: 'ARCHIVED', resource_revision_no: expected + 1, updated_time: now, updated_by: actorId
    })
if (changed != 1) {
    exit 409, '资源已更新，请刷新重试'
}
return {id: id, state: 'ARCHIVED', version: expected + 1}
