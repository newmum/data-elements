{
  "properties": {},
  "id": "2ef3373371f9445280a1b5ca8d18efcb",
  "script": null,
  "groupId": "061bd630dc3758bdaf894b4508985387",
  "name": "05.读取接入模板详情",
  "path": "/templatedetail",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "读取当前行业可见的接入模板完整流程、配置和原始定义。"
}
================================
import tenantRuntime
import tableRead
import dataScope
import cn.hutool.json.JSONUtil

// POST /nifi/canvas/templatedetail。管理者查看任意状态，业务使用者查看已发布版本。
var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId.isBlank()) { exit 401, '请重新登录' }
var requested = (body?.tid || '').toString().trim()
if (!requested.matches('[A-Za-z0-9_-]{1,32}')) { exit 400, '模板版本标识无效' }
var query = tableRead.from('nifi_access_template_t', 't', tenantId)
var column = (name, kind) => tableRead.column('t', name, kind)
query.where(tableRead.equal(column('tid', 'TEXT'), requested))
if (dataScope.dataSource().scope != 'ALL') { query.where(tableRead.equal(column('status', 'TEXT'), 'PUBLISHED')) }
var rows = query.rows({tid: column('tid','TEXT'),familyId: column('family_id','TEXT'),
    name: column('display_name','TEXT'),version: column('revision_no','NUMBER'),status: column('status','TEXT'),
    definition: column('definition','LOB'),updatedAt: column('updated_at','NUMBER'),
    defaultTenantId: column('default_tenant_id','TEXT')}, 1)
if (rows.isEmpty()) { exit 404, '模板不存在或无访问权限' }

var row = rows[0]
var spec = JSONUtil.parseObj(row.definition)
var targetIds = spec.targetDatasourceIds == null ? [] : spec.targetDatasourceIds
var boundTargets = []
if (!targetIds.isEmpty()) {
    var targets = tableRead.from('db_datasource_t','d',tenantId)
    var targetId = tableRead.column('d','tid','TEXT')
    targets.where(tableRead.in(targetId,targetIds))
    targets.where(tableRead.equal(tableRead.column('d','is_del','NUMBER'),0))
    var candidates = targets.rows({tid:targetId,dbName:tableRead.column('d','db_name','TEXT'),
        dbType:tableRead.column('d','db_type','TEXT'),createdBy:tableRead.column('d','created_by','TEXT'),
        orgId:tableRead.column('d','org_id','TEXT')},targetId,'asc',targetIds.size())
    var visible = dataScope.visibleDataSourcesFor('MENU:2081000000000000002',candidates)
    var byId = {}
    for (target in visible) { byId[target.tid] = target }
    for (id in targetIds) {
        if (byId[id] == null) { exit 403, '模板绑定的目标库不存在或超出当前数据范围，请联系模板维护人员' }
        boundTargets.add({tid:id,value:id,label:byId[id].dbName,dbName:byId[id].dbName,
            dbType:byId[id].dbType,layerCode:'ODS',tablePrefix:'ODS',parentValue:'TEMPLATE'})
    }
}

return {tid: row.tid,familyId: row.familyId,name: row.name,version: row.version,status: row.status,
    spec: spec,targets:boundTargets,isDefault:row.defaultTenantId == tenantId,updatedAt: row.updatedAt}
