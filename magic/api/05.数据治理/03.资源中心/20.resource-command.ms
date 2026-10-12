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
// 02.资源中心业务命令入口。路由白名单、租户会话与乐观版本统一校验。
import tenantRuntime
import dataScope
import tableSort
import cn.hutool.core.util.StrUtil
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
var tenantId = tenantRuntime.id()
if (body == null || body.kind == null) { exit 400,'缺少资源操作类型' }
var command = body
if (command.tenantId != null) { exit 400,'业务租户由当前会话确定' }
if (!['GET','POST','PUT','DELETE'].contains(command.method)) { exit 400,'不支持的操作方法' }
if (command.body == null) { command.body = {} }
if (command.body.tenantId != null) { exit 400,'不能通过请求切换业务租户' }
var kind = command.kind + ''

// Lists use the same authenticated command gateway, with a read-only operation.
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
            '创建逻辑模型':{stage:'设计',event:'创建模型'}, '复制逻辑模型':{stage:'设计',event:'复制模型'},
            '维护逻辑模型：保存':{stage:'设计',event:'保存模型设计'}, '维护逻辑模型：reverse':{stage:'设计',event:'导入来源表'},
            '维护逻辑模型：reference':{stage:'设计',event:'引用模型实体'}, '维护逻辑模型：bind':{stage:'标准化',event:'绑定数据标准'},
            '生成模型数据项集':{stage:'标准化',event:'生成数据项集'}, '提交标准候选':{stage:'标准化',event:'提交标准候选'},
            '冻结设计版本':{stage:'版本',event:'冻结设计版本'}, '维护逻辑模型：archive':{stage:'版本',event:'归档模型'},
            '创建物化方案':{stage:'物化',event:'创建物化方案'}, '重新预检物化':{stage:'物化',event:'重新预检物化'},
            '开始物化建表':{stage:'物化',event:'开始物化建表'}, '物化执行完成':{stage:'物化',event:'物化执行完成'},
            '请求取消物化':{stage:'物化',event:'请求取消物化'}, '装载融合模型数据':{stage:'物化',event:'装载融合数据'},
            '融合模型装载失败':{stage:'物化',event:'融合装载失败'}
        }
        var actions = []; var stageActions = []
        var stage = q.stage == null ? '' : q.stage + ''
        if (!['','设计','标准化','版本','物化'].contains(stage)) { exit 400,'模型阶段无效' }
        for (action in events.keySet()) { actions.add(action); if (stage == '' || events[action].stage == stage) { stageActions.add(action) } }
        var modelId = q.modelId == null ? '' : q.modelId + ''
        if (modelId != '' && db.selectOne('SELECT tid FROM res_logical_model WHERE tenant_id=#{tenantId} AND tid=#{modelId} AND is_del=0') == null) { exit 404,'模型不属于当前租户' }
        var keyword = q.keyword == null ? '' : (q.keyword + '').trim()
        if (keyword.length() > 200) { exit 400,'检索关键词过长' }
        var search = '%' + keyword + '%'
        var nameSql = "COALESCE(m.model_name,l.object_name,'关联模型已不存在')"
        var order = tableSort.field(q,{time:'l.created_time',modelName:nameSql},'time')
        var dir = tableSort.direction(q)
        var logsSql = """
          SELECT l.tid AS id,l.actor_id,l.action_name AS action,l.object_id,l.object_name AS name,l.detail_text AS detail,
                 l.created_time AS time,m.tid AS model_id,m.model_code,m.model_status AS model_status,
        """ + nameSql + """ AS model_name
            FROM res_resource_operation_log l
            LEFT JOIN res_materialization_plan p ON p.tid=l.object_id AND p.tenant_id=l.tenant_id AND p.is_del=0
            LEFT JOIN res_model_itemset i ON i.tid=l.object_id AND i.tenant_id=l.tenant_id AND i.is_del=0
            LEFT JOIN res_logical_model m ON m.tid=COALESCE(p.model_id,i.model_id,l.object_id) AND m.tenant_id=l.tenant_id AND m.is_del=0
           WHERE l.tenant_id=#{tenantId}
             AND l.action_name IN <foreach collection="stageActions" item="action" open="(" close=")" separator=",">#{action}</foreach>
             AND (#{modelId}='' OR m.tid=#{modelId})
             AND (#{keyword}='' OR m.model_name LIKE #{search} OR m.model_code LIKE #{search} OR l.object_name LIKE #{search} OR l.detail_text LIKE #{search} OR l.action_name LIKE #{search})
        """ + " ORDER BY (" + order + ") IS NULL," + order + " " + dir + ",l.tid ASC"
        var result = db.page(logsSql,size,(page-1)*size)
        var value = (row,key) => row[key] == null ? row[StrUtil.toCamelCase(key)] == null ? row[key.toLowerCase()] == null ? row[key.toUpperCase()] : row[key.toLowerCase()] : row[StrUtil.toCamelCase(key)] : row[key]
        var actorIds = []; for (row in result.list) { var id = value(row,'actor_id'); if (id != null && !actorIds.contains(id)) { actorIds.add(id) } }
        var users = actorIds.size() == 0 ? [] : db.table('rm_user_t').column('id,real_name,user_name').where().eq('tenant_id',tenantId).in('id',actorIds).select()
        var names = {}; for (user in users) { names[value(user,'id') + ''] = value(user,'real_name') == null || value(user,'real_name') == '' ? value(user,'user_name') : value(user,'real_name') }
        var list = []
        for (row in result.list) {
            var action = value(row,'action') + ''; var actor = value(row,'actor_id') + ''
            list.add({id:value(row,'id'),actorId:actor,actorName:names[actor],action:action,objectId:value(row,'object_id'),name:value(row,'name'),
                detail:value(row,'detail'),time:value(row,'time'),modelId:value(row,'model_id'),modelName:value(row,'model_name'),modelCode:value(row,'model_code'),
                modelArchived:value(row,'model_status') == 'ARCHIVED',stage:events[action].stage,event:events[action].event})
        }
        return {list:list,total:result.total,page:page,size:size}
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
        var domainIds = []; for (domain in planning.databases) { domainIds.add(domain.id) }
        if (domainIds.size() == 0) { domainIds = [''] }
        var scope = dataScope.dataSource()
        var scopeMode = scope.scope + ''; var scopeUserId = scope.userId + ''
        var scopeOrgIds = scope.organizationIds == null ? [] : scope.organizationIds
        var scopeOrgCount = scopeOrgIds.size(); if (scopeOrgCount == 0) { scopeOrgIds = [''] }
        if (!['ALL','OWNER','ORG'].contains(scopeMode) || scopeUserId == '') { exit 403,'无法确定当前数据范围' }
        var counts = db.select("""SELECT t.resource_domain_id AS id,COUNT(*) AS amount
            FROM db_table_t t JOIN db_datasource_t s ON s.tid=t.datasource_id AND s.tenant_id=t.tenant_id AND s.is_del=0
           WHERE t.tenant_id=#{tenantId} AND t.is_del=0
             AND (#{scopeMode}='ALL' OR (#{scopeMode}='OWNER' AND s.created_by=#{scopeUserId})
               OR (#{scopeMode}='ORG' AND #{scopeOrgCount}>0 AND s.org_id IN
                 <foreach collection="scopeOrgIds" item="oid" open="(" close=")" separator=",">#{oid}</foreach>))
             AND t.resource_domain_id IN <foreach collection="domainIds" item="did" open="(" close=")" separator=",">#{did}</foreach>
           GROUP BY t.resource_domain_id""")
        var resourceCounts = {}; for (row in counts) { resourceCounts[row.id + ''] = row.amount }
        for (layer in planning.layers) {
            var count = 0; for (domain in planning.databases) { if (domain.layerId == layer.id) { count = count + 1 } }
            layer.count = count; layer.tables = layer.sourceId == '' ? null : layer.tableCount; layer.source = layer.sourceName
            if (table == 'layers') { all.add(layer) }
            if (table == 'bindings') { all.add({id:'layer:' + layer.id,name:layer.name,source:layer.sourceName,tableCount:layer.sourceId == '' ? null : layer.tableCount}) }
        }
        for (domain in planning.databases) {
            domain.count = resourceCounts[domain.id] == null ? 0 : resourceCounts[domain.id]
            domain.tables = domain.sourceId == '' ? null : domain.tableCount; domain.source = domain.sourceName
            if (table == 'databases') { all.add(domain) }
            if (table == 'bindings') { all.add({id:'domain:' + domain.id,name:domain.name,source:domain.sourceName,tableCount:domain.sourceId == '' ? null : domain.tableCount}) }
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
    // Every requested ID is checked against the authorized server collection before ordering.
    var selected = []
    for (id in unique) { if (byId[id] == null) { exit 404,'部分记录不存在或不在当前可见范围' }; selected.add(byId[id]) }
    var keyword = q.keyword == null ? '' : (q.keyword + '').trim().toLowerCase()
    if (keyword.length() > 200) { exit 400,'检索关键词过长' }
    var filtered = []
    for (row in selected) {
        if (keyword == '' || ((row.name == null ? '' : row.name) + ' ' + (row.code == null ? '' : row.code) + ' ' + (row.source == null ? '' : row.source)).toLowerCase().contains(keyword)) { filtered.add(row) }
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
