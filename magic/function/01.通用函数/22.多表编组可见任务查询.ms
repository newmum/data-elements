{
  "properties": {},
  "id": "b407fbd38e4c4d048c10eb9e92845d91",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "22.多表编组可见任务查询",
  "path": "/multi-task-visible-query",
  "description": "为多表编组候选分页和保存校验构建同一套任务、来源表及数据源范围条件。",
  "parameters": [
    { "name": "tenantId", "description": "当前认证租户 ID", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "scopeMode", "description": "当前账号的数据范围", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "scopeUserId", "description": "当前数据范围对应的账号 ID", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "scopeOrgIds", "description": "当前账号的授权部门 ID", "required": true, "dataType": "Array", "type": "java.util.List" }
  ],
  "options": []
}
================================
import tableRead

/**
 * 构建多表编组候选和保存校验共用的可见任务查询。
 * 调用方传入从认证上下文取得的租户和数据范围，按场景追加分页条件或任务 ID。
 * 返回已限定租户、有效记录和账号数据范围的查询，构建阶段不读取或写入数据库。
 */
var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
var active = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
var sourceTableId = textColumn('source_table', 'tid')
var sourceDbId = textColumn('source_db', 'tid')

// 数据范围按来源登记表的真实数据源判定。历史任务的来源库字段可能记录实际采集节点。
var query = tableRead.from('data_access_agg_task_t', 'task', tenantId)
query.leftJoin('db_table_t', 'source_table', 'tid', 'source_table_id', [active('source_table')])
query.leftJoin('db_datasource_t', 'source_db', 'tenant_id', 'tenant_id', [
    tableRead.equal(sourceDbId, textColumn('source_table', 'datasource_id')),
    active('source_db')
])
query.where(tableRead.all([
    active('task'),
    tableRead.not(tableRead.isNull(sourceTableId)),
    tableRead.not(tableRead.isNull(sourceDbId))
]))

// 部门列表为空时，结构化 IN 条件不匹配任何记录。
if (scopeMode == 'OWNER') {
    query.where(tableRead.equal(textColumn('source_db', 'created_by'), scopeUserId))
} else if (scopeMode == 'ORG') {
    query.where(tableRead.in(textColumn('source_db', 'org_id'), scopeOrgIds))
}
return query
