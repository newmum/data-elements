{
  "properties": {},
  "id": "datapd_catalog_list_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "01.数据目录列表",
  "createTime": null,
  "updateTime": 1787153759182,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"85368b91972c4a35b83b8fa73e34e740\",\n  \"pageNum\": 1,\n  \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"tid\": \"85368b91972c4a35b83b8fa73e34e740\",\n  \"assetType\": \"catalog\",\n  \"catalogName\": \"生成目录信息(sys_gen_info)\",\n  \"dbId\": \"38ef1b566eb4477ebfc07b4c0c8aa973\",\n  \"sourceTableId\": \"fb64c63470c14ee286a29fcea82a259c\"\n}",
  "description": "按当前登录租户分页读取目录登记和当前页扩展属性，返回兼容字段的目录数组。可按 tid 查询，默认每页 1000 条，上限 1000 条。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tableRead
import tenantRuntime

// POST /dst/catalog/list 分页读取当前租户的目录登记和扩展属性。
// 兼容目录字典和旧低代码调用的数组、字段别名及缺省页大小。传入 tid 时只选该目录。
var tenantId = tenantRuntime.id()
var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
var pageSize = body?.pageSize == null ? 1000 : body.pageSize::int
if (pageSize < 0 || pageSize > 1000) {
    exit 400, '目录列表每页最多 1000 条'
}
if (pageSize == 0) {
    return []
}
var offset = pageNum <= 1 ? 0L : (pageNum::long - 1L) * pageSize

// 旧目录字典按当前认证租户读取。请求内的租户、部门和资产类型不能改变授权范围。
var catalogQuery = db.lower().table('da_catalog_t').columns([
    'tid', 'tenant_id', 'asset_status', 'flow_status', 'reg_time', 'created_time', 'updated_time'
]).where().eq('tenant_id', tenantId)
    .and(active => active.eq('is_del', 0).or().isNull('is_del'))
var tid = body?.tid
if (tid != null && (tid + '').trim() != '') {
    catalogQuery.eq('tid', tid + '')
}
// 先在数据库按原先的双时间排序分页。同一时间使用目录主键稳定分页。
var page = catalogQuery.orderBy('updated_time', 'desc').orderBy('created_time', 'desc')
    .orderBy('tid', 'asc').page(pageSize, offset)
var rows = page.list
if (rows == null || rows.isEmpty()) {
    return []
}

var propertyNames = [
    'catalogName', 'catalogNameEn', 'assetDesc', 'dbId', 'sourceTableId', 'sourceTableName',
    'orgId', 'manageUnit', 'businessType', 'dataSourceType', 'timestampField', 'dictionaryTableFlag',
    'dictionaryProfile', 'dictionaryProfiles', 'fieldGovernanceConfig'
]
var parent = tableRead.column('p', 'parent_id', 'TEXT')
var property = tableRead.column('p', 'prop_name', 'TEXT')
var propertyQuery = tableRead.from('da_prop_t', 'p', tenantId)
propertyQuery.where(tableRead.in(parent, rows.map(row => row.tid)))
propertyQuery.where(tableRead.in(property, propertyNames))
propertyQuery.where(tableRead.equal(tableRead.coalesce([tableRead.column('p', 'is_del', 'NUMBER'), 0]), 0))
// 一次取回当前页的属性。历史重复属性使用主键最小的一条，避免重复记录扩大响应。
var ranked = propertyQuery.ranked('r', {
    parent_id: parent,
    prop_name: property,
    prop_value: tableRead.column('p', 'prop_value', 'LOB')
}, [parent, property], [tableRead.column('p', 'tid', 'TEXT')], ['asc'])
var rankedParent = tableRead.column('r', 'parent_id', 'TEXT')
var rankedName = tableRead.column('r', 'prop_name', 'TEXT')
var properties = ranked.rankedRows({
    parentId: rankedParent,
    name: rankedName,
    value: tableRead.column('r', 'prop_value', 'LOB')
}, [rankedParent, rankedName], ['asc', 'asc'], true)
var byCatalog = {}
for (entry in properties) {
    if (byCatalog[entry.parentId] == null) {
        byCatalog[entry.parentId] = {}
    }
    byCatalog[entry.parentId][entry.name.toLowerCase()] = entry.value
}

return rows.map(row => {
    // 保留运行时已对外返回的小写键和旧下划线键的空值。实际状态、时间由小写键承载。
    var item = {
        tid: row.tid, id: row.tid, tenantid: row.tenant_id, assettype: 'catalog',
        assetstatus: row.asset_status, flowstatus: row.flow_status,
        regtime: row.reg_time, createdtime: row.created_time, updatedtime: row.updated_time,
        asset_type: null, asset_status: null, flow_status: null,
        reg_time: null, created_time: null, updated_time: null,
        viewCount: 0
    }
    var extensions = byCatalog[row.tid]
    for (name in propertyNames) {
        var alias = name.toLowerCase()
        item[alias] = extensions == null ? null : extensions[alias]
    }
    return item
})
