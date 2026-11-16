{
  "properties": {},
  "id": "ea87bf02b8a843b5a0491ebdc6aa8071",
  "groupId": "6f0ef420df525a7caa6cccc7acdc8819",
  "name": "08.关联原生流程",
  "path": "/native",
  "method": "POST",
  "description": "登记表与默认 NiFi 节点现有流程组的只读关联。操作 list、inspect、bind、detail、unlink。"
}
================================
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowFailureDetails
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService as runtime
import java.lang.System
import java.lang.Math
import dataScope
import tableRead
import tenantRuntime

// POST /ods/registry/native 将已登记来源表与原生流程组关联用于观察。
// 关联不建立平台代理画布和接入任务，不授权启停、部署或删除 NiFi 资源。
var tenantId = tenantRuntime.id()
var scope = dataScope.dataSource()
var text = value => value == null ? '' : ('' + value).trim()
var action = text(body?.action)
if (!['list', 'inspect', 'bind', 'detail', 'unlink'].contains(action)) {
    exit 400, '不支持的原生流程关联操作'
}
var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var active = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
var withSource = (query, tableAlias) => {
    query.leftJoin('db_datasource_t', 'd', 'tenant_id', 'tenant_id', [
        active('d'), tableRead.equal(column('d', 'tid'), column(tableAlias, 'datasource_id'))
    ])
    query.where(tableRead.all([active(tableAlias),
        tableRead.greaterThan(tableRead.column(tableAlias, 'asset_status', 'NUMBER'), 0),
        tableRead.greaterThan(tableRead.column('d', 'asset_status', 'NUMBER'), 0),
        tableRead.in(column(tableAlias, 'business_type'), ['业务表', '日志表', '字典表'])]))
    if (scope.scope == 'OWNER') {
        query.where(tableRead.equal(column('d', 'created_by'), scope.userId))
    } else if (scope.scope == 'ORG') {
        query.where(tableRead.in(column('d', 'org_id'), scope.organizationIds))
    }
    return query
}
var sourceFields = {
    sourceTableId: column('t', 'tid'),
    tableName: column('t', 'table_name'),
    tableNameCn: column('t', 'table_name_cn'),
    datasourceName: column('d', 'db_name')
}
var links = () => {
    var q = tableRead.from('data_access_native_flow_t', 'l', tenantId)
    q.leftJoin('db_table_t', 't', 'tid', 'source_table_id', [active('t')])
    return withSource(q, 't')
}
var projection = {
    id: column('l', 'tid'), nodeId: column('l', 'node_id'),
    nodeName: column('l', 'node_name'), processGroupId: column('l', 'process_group_id'),
    groupName: column('l', 'group_name'), createdBy: column('l', 'created_by'),
    createdAt: tableRead.column('l', 'created_at', 'NUMBER')
}
projection.putAll(sourceFields)
var mayUnlink = row => scope.scope == 'ALL' || row.createdBy == scope.userId

if (action == 'list') {
    var page = text(body?.page)
    var size = text(body?.size)
    var keyword = text(body?.keyword)
    if ((!page.isEmpty() && !page.matches('[1-9][0-9]{0,5}')) ||
        (!size.isEmpty() && !size.matches('[1-9][0-9]{0,2}')) || keyword.length() > 128) {
        exit 400, '查询参数无效'
    }
    var q = links()
    if (!keyword.isEmpty()) {
        q.where(tableRead.any([tableRead.contains(projection.groupName, keyword),
            tableRead.contains(projection.tableName, keyword), tableRead.contains(projection.tableNameCn, keyword),
            tableRead.contains(projection.processGroupId, keyword)]))
    }
    var result = q.page(projection, projection.createdAt, 'desc', 'tid',
        page.isEmpty() ? 1 : page::int, size.isEmpty() ? 20 : Math.min(100, size::int))
    var records = result.rows.map(row => {
        row.canUnlink = mayUnlink(row)
        row.remove('createdBy')
        return row
    })
    return {list: records, total: result.total}
}

var stored = null
var source = null
var groupId = null
if (['detail', 'unlink'].contains(action)) {
    var id = text(body?.id)
    if (!id.matches('[0-9]{1,32}')) { exit 400, '关联编号无效' }
    var q = links()
    q.where(tableRead.equal(projection.id, id))
    var rows = q.rows(projection, 1)
    if (rows.isEmpty()) { exit 404, '找不到有权查看的关联' }
    stored = rows[0]
    if (action == 'unlink') {
        if (!mayUnlink(stored)) {
            exit 403, '只有关联创建人或拥有全部数据范围的用户可以解除关联'
        }
        db.normal().table('data_access_native_flow_t').where().eq('tenant_id', tenantId).eq('tid', id).delete()
        return {id: id}
    }
    groupId = stored.processGroupId
} else {
    var sourceId = text(body?.sourceTableId)
    groupId = text(body?.processGroupId).toLowerCase()
    if (!sourceId.matches('[A-Za-z0-9_-]{1,32}') ||
        !groupId.matches('[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}')) {
        exit 400, '请选择来源表并填写 NiFi 流程组编号（UUID）'
    }
    var q = withSource(tableRead.from('db_table_t', 't', tenantId), 't')
    q.where(tableRead.equal(sourceFields.sourceTableId, sourceId))
    var rows = q.rows(sourceFields, 1)
    if (rows.isEmpty()) { exit 404, '来源表未登记或不在当前数据范围内' }
    source = rows[0]
}

// 节点必须有已配置的租户根组。使用当前节点快照验证归属，拒绝整个 NiFi 全局根。
var snapshot = runtime.snapshot()
if (snapshot.fresh != true) { exit 503, '暂时无法读取默认 NiFi 节点，请稍后重试' }
var node = tableRead.from('nifi_node_t', 'n', tenantId)
node.where(tableRead.all([active('n'), tableRead.equal(column('n', 'tid'), snapshot.nodeId),
    tableRead.equal(tableRead.column('n', 'enabled', 'NUMBER'), 1)]))
var nodes = node.rows({name: column('n', 'node_name'), root: column('n', 'root_process_group_id')}, 1)
if (nodes.isEmpty()) { exit 409, '默认 NiFi 节点已改变，请刷新后重试' }
var root = text(nodes[0].root)
if (root.isEmpty() || root == 'root') { exit 409, '默认 NiFi 节点需要配置本租户的专用根流程组' }
if (stored != null && stored.nodeId != snapshot.nodeId) { exit 409, '此关联的原节点已不是当前默认节点，无法读取状态' }
if (groupId == root || snapshot.index.rawStatus(root) == null || snapshot.index.rawStatus(groupId) == null) {
    exit 404, '在当前默认节点的租户根流程组内找不到此编号'
}
var binding = nifi.bindRuntimeNode()
try {
    if (nifi.getRootProcessGroupId() != root) { exit 409, '默认 NiFi 节点已改变，请刷新后重试' }
    var component = nifi.getProcessGroup(groupId).path('component')
    if (component.path('id').asText('') != groupId) { exit 404, '无法确认流程组归属' }
    var path = [groupId]
    var child = component
    // 核实原生父链；不能通过子组编号绕过上级平台流程或跨租户标记。
    while (true) {
        if (child.path('comments').asText('').startsWith('data-elements:v1:')) {
            exit 409, '该流程组位于平台管理的流程内，请从原任务入口查看'
        }
        var parent = child.path('parentGroupId').asText('')
        if (parent == root) { break }
        if (path.size() >= 16 || path.contains(parent) || snapshot.index.rawStatus(parent) == null) {
            exit 404, '无法确认流程组归属'
        }
        path.add(parent)
        child = nifi.getProcessGroup(parent).path('component')
        if (child.path('id').asText('') != parent) { exit 404, '无法确认父流程组' }
    }
    var managed = (table, field, key) => {
        var q = tableRead.from(table, 'm', tenantId)
        q.where(tableRead.all([active('m'), tableRead.in(column('m', field), path)]))
        return !q.rows({id: column('m', key)}, 1).isEmpty()
    }
    if (managed('nifi_pipeline_t', 'nifi_process_group_id', 'id') ||
        managed('data_access_agg_task_t', 'process_group_id', 'tid')) {
        exit 409, '此流程组已由平台管理，请从原任务入口查看'
    }
    var details = runtime.describe(snapshot, groupId, 'STOPPED')
    details.groupName = component.path('name').asText(groupId)
    details.nodeName = nodes[0].name
    details.processGroupId = groupId
    if (action == 'inspect') { return details }
    if (action == 'detail') {
        var logs = []
        for (item in nifi.getBulletinBoard(groupId, 0L).path('bulletinBoard').path('bulletins').elements()) {
            if (logs.size() >= 100) { break }
            var entry = item.path('bulletin')
            var level = entry.path('level').asText('')
            if (level == 'ERROR' || level == 'WARNING') {
                logs.add({id: item.path('id').asText(''), level: level,
                    source: entry.path('sourceName').asText(''), message: FlowFailureDetails.safe(entry.path('message').asText('')),
                    time: entry.path('timestampIso').asText(entry.path('timestamp').asText(''))})
            }
        }
        details.logs = logs
        return details
    }
    var id = IdUtil.getSnowflakeNextIdStr()
    try {
        db.normal().table('data_access_native_flow_t').primary('tid').insert({
            tid: id, tenant_id: tenantId, source_table_id: source.sourceTableId,
            node_id: snapshot.nodeId, node_name: details.nodeName,
            process_group_id: groupId, group_name: details.groupName,
            created_by: scope.userId, created_at: System.currentTimeMillis()
        })
    } catch (failure) {
        var duplicate = tableRead.from('data_access_native_flow_t', 'l', tenantId)
        duplicate.where(tableRead.all([tableRead.equal(projection.nodeId, snapshot.nodeId),
            tableRead.equal(projection.processGroupId, groupId)]))
        var previous = duplicate.rows({id: projection.id, sourceTableId: column('l', 'source_table_id')}, 1)
        if (!previous.isEmpty()) {
            if (previous[0].sourceTableId == source.sourceTableId) { return {id: previous[0].id} }
            exit 409, '此流程组已有关联，请在原生关联列表查看'
        }
        throw failure
    }
    return {id: id}
} finally {
    binding.close()
}
