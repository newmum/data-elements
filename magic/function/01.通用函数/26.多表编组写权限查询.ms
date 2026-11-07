{
  "properties": {},
  "id": "19e712f3e2ab4b529d0910896dfc1c39",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "26.多表编组写权限查询",
  "path": "/multi-group-edit-query",
  "description": "构建删除编组和补齐成员关联共用的编组写权限查询。",
  "parameters": [
    { "name": "tenantId", "description": "当前认证租户 ID", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "groupId", "description": "已校验格式的编组请求标识", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "actor", "description": "当前会话账号 ID", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "scopeMode", "description": "当前账号的数据范围", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "scopeOrgIds", "description": "当前账号的授权部门 ID", "required": true, "dataType": "Array", "type": "java.util.List" }
  ],
  "options": []
}
================================
import tableRead

/**
 * 构建多表编组的写权限查询。租户、账号及角色范围由调用接口从认证上下文解析。
 * 只返回当前账号创建且全部成员来源可见的编组。调用方按动作决定是否允许读取已删除编组。
 * 返回可继续追加条件的查询，函数本身不读写数据库。
 */
var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
var active = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
var memberGroupId = textColumn('member', 'group_id')
var sourceTableId = textColumn('source_table', 'tid')
var sourceDbId = textColumn('source_db', 'tid')

// 只聚合当前编组中来源表或数据源失效、或超出角色范围的成员。
var members = tableRead.from('data_access_multi_group_item_t', 'member', tenantId)
members.leftJoin('db_table_t', 'source_table', 'tid', 'source_table_id', [active('source_table')])
members.leftJoin('db_datasource_t', 'source_db', 'tenant_id', 'tenant_id', [
    tableRead.equal(sourceDbId, textColumn('source_table', 'datasource_id')),
    active('source_db')
])
var denied = [tableRead.isNull(sourceTableId), tableRead.isNull(sourceDbId)]
// 空创建人和空部门不属于账号的数据范围，须显式处理 SQL 的空值比较。
if (scopeMode == 'OWNER') {
    var owner = textColumn('source_db', 'created_by')
    denied.add(tableRead.isNull(owner))
    denied.add(tableRead.not(tableRead.equal(owner, actor)))
} else if (scopeMode == 'ORG') {
    var org = textColumn('source_db', 'org_id')
    denied.add(tableRead.isNull(org))
    denied.add(tableRead.not(tableRead.in(org, scopeOrgIds)))
}
members.where(tableRead.equal(memberGroupId, groupId))
members.where(tableRead.any(denied))

// 存在任一不可见成员就拒绝整组。历史空编组没有来源权限冲突。
var query = tableRead.from('data_access_multi_group_t', 'task_group', tenantId)
query.leftJoinGrouped(members, 'denied_members', 'group_id', 'tid', {group_id: memberGroupId}, [memberGroupId])
query.where(tableRead.all([
    tableRead.equal(textColumn('task_group', 'tid'), groupId),
    tableRead.equal(textColumn('task_group', 'created_by'), actor),
    tableRead.isNull(textColumn('denied_members', 'group_id'))
]))
return query
