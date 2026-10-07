{
  "properties": {},
  "id": "cat_org_tree_compat_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "05.组织业务系统数据库树",
  "createTime": 1784769000000,
  "updateTime": 1787153759350,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/organization-tree",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"parentId\": \"2026503859392086017\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "[{\"id\":\"机构ID\",\"label\":\"机构名称\",\"value\":\"机构ID\",\"children\":[],\"catalogNum\":0}]",
  "description": "组织业务系统数据库树兼容接口（POST /dst/catalog/organization-tree）。只读接口；读取 SSO 组织树、sym_application_t、db_datasource_t、da_asset_t 与 da_prop_t，返回数据接入/数据汇聚页面左侧树；请求示例 parentId 来自当前用户根机构。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.组织业务系统数据库树
// 路由：POST /dst/catalog/organization-tree
// 业务说明：组织业务系统数据库树兼容接口（POST /dst/catalog/organization-tree）。只读接口；读取 SSO 组织树、sym_application_t、db_datasource_t、da_asset_t 与 da_prop_t，返回数据接入/数据汇聚页面左侧树；请求示例 parentId 来自当前用户根机构。
import tenantRuntime
import java.util.Map

/* 数据目录树：部门 → 应用系统 → 已完成登记的数据源，仅统计可自动生成目录的已登记表。 */
var text = value => value == null ? '' : (value + '').trim()
var blank = value => text(value) == ''
var first = (row, keys) => {
  for (key in keys) {
    if (row != null && row[key] != null) {
      return row[key]
    }
  }
  return null
}
var tenantId=text(tenantRuntime.id())
if (tenantId == '') {
  exit 401, '当前登录租户无效，请重新登录'
}
var parentId=body==null?null:body.parentId
if (blank(parentId)) {
  exit 400, 'parentId不能为空'
}
var counts={}
for(row in db.select("select t.datasource_id db_id,d.app_id,count(1) catalog_num from db_table_t t join db_datasource_t d on d.tid=t.datasource_id and d.tenant_id=t.tenant_id where t.tenant_id='"+tenantId+"' and ifnull(t.is_del,0)=0 and ifnull(d.is_del,0)=0 and t.asset_status=2 and t.flow_status=2 and d.asset_status=2 and ifnull(d.is_enable,1)=1 group by t.datasource_id,d.app_id")){counts[text(first(row,['dbId','db_id']))]={appId:text(first(row,['appId','app_id'])),count:(first(row,['catalogNum','catalog_num'])+'')::int}}
var orgRows=db.table('rm_org_t').where().eq('deleted',0).select()
var rows=[]
var orgIds={}
for (org in orgRows) {
  var oid=text(first(org,['id','tid','orgId','org_id']))
  if (oid == '') {
    continue
  }
  var node={}
  node.putAll(org)
  node.id=oid
  node.value=oid
  node.label=first(org,['name','orgName','org_name'])
  node.type='org'
  node.children=[]
  node.catalogNum=0
  rows.add(node)
  orgIds[oid]=true
}
var apps={}
var appCounts={}
for (sourceId in counts.keySet()) {
  var appId=counts[sourceId].appId
  if (appId != '') {
    appCounts[appId]=(appCounts[appId]||0)+counts[sourceId].count
  }
}
for (app in db.table('sym_application_t').where().eq('tenant_id',tenantId).eq('is_del',0).select()) {
  var appId=text(first(app,['tid','id']))
  var orgId=text(first(app,['orgId','org_id','departId']))
  if (appCounts[appId] == null || orgIds[orgId] == null) {
    continue
  }
  var node={id:appId,tid:appId,parentId:orgId,value:appId,label:first(app,['appName','app_name']),name:first(app,['appName','app_name']),type:'app',children:[],catalogNum:appCounts[appId]}
  rows.add(node)
  apps[appId]=node
}
for (source in db.table('db_datasource_t').where().eq('tenant_id',tenantId).eq('is_del',0).eq('asset_status',2).eq('is_enable',1).select()) {
  var sourceId=text(first(source,['tid','id']))
  var info=counts[sourceId]
  var appId=text(first(source,['appId','app_id']))
  if (info == null || apps[appId] == null) {
    continue
  }
  rows.add({id:sourceId,tid:sourceId,parentId:appId,value:sourceId,label:first(source,['dbName','db_name']),name:first(source,['dbName','db_name']),type:'db',dbType:first(source,['dbType','db_type']),children:[],catalogNum:info.count})
}
var toTree
toTree=(pid)=>{
  var result=[]
  for (row in rows) {
    if (text(first(row,['parentId','parent_id','pid'])) == text(pid)) {
      row.children=toTree(first(row,['id']))
      result.add(row)
    }
  }
  return result
}
var rollup
rollup=(nodes)=>{
  var total=0
  for (node in nodes) {
    var child=rollup(node.children||[])
    var own=(node.catalogNum||0)::int
    node.catalogNum=own>child?own:child
    total=total+node.catalogNum
  }
  return total
}
var tree=toTree(parentId)
rollup(tree)
return tree
