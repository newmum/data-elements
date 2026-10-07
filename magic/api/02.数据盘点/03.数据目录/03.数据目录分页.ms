{
  "properties": {},
  "id": "datapd_catalog_page_compat_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "03.数据目录分页",
  "createTime": null,
  "updateTime": 1790484171436,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20,\n  \"conditions\": []\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"pageNum\":1,\"pageSize\":20,\"total\":0,\"list\":[]}",
  "description": "数据目录分页接口（POST /dst/catalog/page）。目录、树统计和详情统一读取 da_catalog_t；使用请求参数 conditions、keyword、statisticsOnly、pageNum、pageSize、sortField、sortDir。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.数据目录分页
// 路由：POST /dst/catalog/page
// 业务说明：数据目录分页接口（POST /dst/catalog/page）。目录、树统计和详情统一读取 da_catalog_t；使用请求参数 conditions、keyword、statisticsOnly、pageNum、pageSize、sortField、sortDir。
import tenantRuntime
import java.util.LinkedHashMap
import java.util.ArrayList
import java.util.List

/* 数据目录分页（只读）：da_catalog_t 为权威来源，不以源表登记状态作为准入条件。
 * 组织树统计复用同一查询条件。所有主表、属性和关联对象均限制在登录租户内。
 * 不补建目录，不修改源表；分页仅加载当前页及该页动态属性。
 */
var text = value => value == null ? '' : (value + '').trim()
var blank = value => text(value) == ''
var sqlValue = value => "'" + text(value).replace("'", "''") + "'"
var rowValue = (row, keys) => {
  for (key in keys) {
    if (row != null && row[key] != null) { return row[key] }
  }
  return null
}
var tenantId = text(tenantRuntime.id())
if (tenantId == '') { exit 401, '当前登录租户无效，请重新登录' }
var propExpr = name => "(select p.prop_value from da_prop_t p where p.tenant_id=c.tenant_id and p.parent_id=c.tid and p.data_type='catalog' and ifnull(p.is_del,0)=0 and p.prop_name=" + sqlValue(name) + " order by p.updated_time desc,p.tid desc limit 1)"
var fields = {
  tid:'c.tid', tenantId:'c.tenant_id', assetStatus:'c.asset_status', flowStatus:'c.flow_status',
  regTime:'c.reg_time', createdTime:'c.created_time', updatedTime:'c.updated_time',
  catalogName:'c.catalog_name', catalogNameEn:'c.catalog_name_en', assetDesc:'c.asset_desc',
  appId:'c.app_id', dbId:'c.db_id', sourceTableId:'c.source_table_id', sourceTableName:'c.source_table_name',
  orgId:'c.org_id', manageUnit:'c.manage_unit', businessType:'c.business_type', timestampField:'c.timestamp_field',
  dictionaryTableFlag:'c.dictionary_table_flag',
  appName:"coalesce(nullif(c.app_name,''),a.app_name)",
  dbName:"coalesce(nullif(c.db_name,''),d.db_name)",
  dataSourceType:"coalesce(nullif(c.data_source_type,'')," + propExpr('dataSourceType') + ",'ods')",
  dataLevel:propExpr('dataLevel'), shareType:propExpr('shareType')
}
var fromSql = " from da_catalog_t c left join sym_application_t a on a.tid=c.app_id and a.tenant_id=c.tenant_id and ifnull(a.is_del,0)=0 left join db_datasource_t d on d.tid=c.db_id and d.tenant_id=c.tenant_id and ifnull(d.is_del,0)=0 "
var whereSql = " where c.tenant_id=" + sqlValue(tenantId) + " and ifnull(c.is_del,0)=0 "
var conditions = body == null || body.conditions == null ? [] : body.conditions
for (condition in conditions) {
  if (condition == null || blank(condition.field) || condition.value == null || text(condition.value) == '') { continue }
  var field = text(condition.field)
  if (field == 'assetType') {
    if (text(condition.value) != 'catalog') { whereSql = whereSql + ' and 1=0 ' }
    continue
  }
  if (field == 'orgIds') { field = 'orgId' }
  if (fields[field] == null) { exit 400, '不支持的数据目录筛选字段：' + field }
  var expr = fields[field]
  var type = text(condition.type).toLowerCase()
  if (type == 'in') {
    var values = condition.value
    if (!(values instanceof List)) { exit 400, '多值筛选必须传入数组' }
    if (values.size() == 0) { whereSql = whereSql + ' and 1=0 '; continue }
    whereSql = whereSql + ' and ' + expr + ' in (' + values.map(v => sqlValue(v)).join(',') + ')'
  } else if (type == 'like') {
    // 名称查询同时匹配关联数据资源；instr 将用户的 %/_ 当作普通字符。
    if (field == 'catalogName') {
      whereSql = whereSql + ' and (instr(lower(ifnull(' + fields.catalogName + ",'')),lower(" + sqlValue(condition.value) + '))>0 or instr(lower(ifnull(' + fields.sourceTableName + ",'')),lower(" + sqlValue(condition.value) + '))>0)'
    } else {
      whereSql = whereSql + ' and instr(lower(ifnull(' + expr + ",'')),lower(" + sqlValue(condition.value) + '))>0'
    }
  } else if (type == '' || type == 'match' || type == 'eq') {
    whereSql = whereSql + ' and ' + expr + '=' + sqlValue(condition.value)
  } else { exit 400, '不支持的数据目录筛选方式：' + type }
}
var keyword = body == null ? '' : text(body.keyword)
if (keyword != '') {
  whereSql = whereSql + ' and (instr(lower(ifnull(' + fields.catalogName + ",'')),lower(" + sqlValue(keyword) + '))>0 or instr(lower(ifnull(' + fields.sourceTableName + ",'')),lower(" + sqlValue(keyword) + '))>0)'
}

// 同一目录集合生成树统计，避免“树显示数量”和“点击后的列表”口径分裂。
if (body != null && body.statisticsOnly == true) {
  var rows = db.select('select c.org_id orgId,c.app_id appId,' + fields.appName + ' appName,c.db_id dbId,' + fields.dbName + ' dbName,d.db_type dbType,count(1) total' + fromSql + whereSql + ' group by c.org_id,c.app_id,c.app_name,a.app_name,c.db_id,c.db_name,d.db_name,d.db_type')
  var total = 0
  var orgCounts = new LinkedHashMap()
  var appMap = new LinkedHashMap()
  for (row in rows) {
    var count = rowValue(row,['total','TOTAL'])::int
    total = total + count
    var orgId = text(rowValue(row,['orgId','orgid','ORGID']))
    var appId = text(rowValue(row,['appId','appid','APPID']))
    if (orgId == '') { continue }
    orgCounts[orgId] = (orgCounts[orgId] == null ? 0 : orgCounts[orgId]) + count
    if (appId == '') { continue }
    if (appMap[orgId] == null) { appMap[orgId] = new LinkedHashMap() }
    if (appMap[orgId][appId] == null) {
      var appName = text(rowValue(row,['appName','appname','APPNAME']))
      appMap[orgId][appId] = { label: appName == '' ? '未命名业务系统' : appName, value:appId, type:'app', count:0, datasources:[] }
    }
    var app = appMap[orgId][appId]
    app.count = app.count + count
    var dbId = text(rowValue(row,['dbId','dbid','DBID']))
    if (dbId != '') {
      app.datasources.add({label:rowValue(row,['dbName','dbname','DBNAME']),value:dbId,count:count,dbType:rowValue(row,['dbType','dbtype','DBTYPE'])})
    }
  }
  var orgApps = new LinkedHashMap()
  for (orgId in appMap.keySet()) { orgApps[orgId] = new ArrayList(appMap[orgId].values()) }
  return { total:total, orgCounts:orgCounts, orgApps:orgApps }
}

var pageNum = body == null || body.pageNum == null ? 1 : body.pageNum::int
var pageSize = body == null || body.pageSize == null ? 20 : body.pageSize::int
if (pageNum < 1) { pageNum = 1 }
if (pageSize < 1) { pageSize = 20 }
if (pageSize > 100) { pageSize = 100 }
var countRows = db.select('select count(1) total' + fromSql + whereSql)
var total = rowValue(countRows[0],['total','TOTAL'])::int
var sortField = body == null ? '' : text(body.sortField)
if (!['updatedTime','createdTime','regTime','catalogName','tid'].contains(sortField)) { sortField = 'updatedTime' }
var sortDir = body != null && text(body.sortDir).toLowerCase() == 'asc' ? 'asc' : 'desc'
var columns = []
for (field in fields.keySet()) { columns.add(fields[field] + ' ' + field) }
var rows = db.select('select ' + columns.join(',') + fromSql + whereSql + ' order by ' + fields[sortField] + ' ' + sortDir + ',c.tid ' + sortDir + ' limit ' + ((pageNum-1)*pageSize) + ',' + pageSize)
var list = []
var ids = []
var itemMap = {}
for (row in rows) {
  var item = {assetType:'catalog',props:{}}
  for (field in fields.keySet()) { item[field] = rowValue(row,[field,field.toLowerCase(),field.toUpperCase()]) }
  item.id = item.tid
  item.displayStatus = item.flowStatus == 1 ? 1 : item.assetStatus
  list.add(item)
  ids.add(sqlValue(item.tid))
  itemMap[text(item.tid)] = item
}
if (ids.size() > 0) {
  var props = db.select("select parent_id parentId,prop_name propName,prop_value propValue from da_prop_t where tenant_id=" + sqlValue(tenantId) + " and ifnull(is_del,0)=0 and data_type='catalog' and parent_id in (" + ids.join(',') + ') order by updated_time,tid')
  for (prop in props) {
    var item = itemMap[text(rowValue(prop,['parentId','parentid','PARENTID']))]
    var name = text(rowValue(prop,['propName','propname','PROPNAME']))
    if (item == null || name == '') { continue }
    var value = rowValue(prop,['propValue','propvalue','PROPVALUE'])
    item.props[name] = value
    // 身份、状态以及组织归属仅以主表为准；动态属性补全其余表单内容。
    if (!['tid','id','tenantId','assetType','assetStatus','flowStatus','orgId','appId','dbId','sourceTableId','displayStatus','props'].contains(name) && blank(item[name])) { item[name] = value }
  }
}
return {pageNum:pageNum,pageSize:pageSize,total:total,list:list}
