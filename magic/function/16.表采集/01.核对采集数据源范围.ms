{
  "id": "collect_fn_scope_v1",
  "groupId": "collection_fn_group_v1",
  "name": "01.核对采集数据源范围",
  "path": "/scope",
  "parameters": [
    {
      "name": "tenantId",
      "dataType": "Object",
      "required": false,
      "description": "可信会话租户"
    },
    {
      "name": "ids",
      "dataType": "Object",
      "required": false,
      "description": "已校验且去重的数据源 ID 列表"
    }
  ],
  "description": "供采集状态、快照清单读入口共同使用。一次预取当前角色可见的数据源。"
}
================================
import dataScope
import tableRead

// /collect/scope 根据可信租户和当前角色批量核对来源。
var sources = tableRead.from('db_datasource_t', 'd', tenantId)
var id = tableRead.column('d', 'tid', 'TEXT')
sources.where(tableRead.in(id, ids))
sources.where(tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0))
var scope = dataScope.dataSource()
if (scope.scope == 'ORG') {
    sources.where(tableRead.in(tableRead.column('d', 'org_id', 'TEXT'), scope.organizationIds))
} else if (scope.scope == 'OWNER') {
    sources.where(tableRead.equal(tableRead.column('d', 'created_by', 'TEXT'), scope.userId))
} else if (scope.scope != 'ALL') {
    throw new IllegalStateException('无法确定数据源访问范围')
}
return sources.rows({tid: id}, ids.size()).map(row => row.tid)
