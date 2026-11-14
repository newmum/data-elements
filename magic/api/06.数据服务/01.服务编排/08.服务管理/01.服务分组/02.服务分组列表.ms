{
  "properties": {},
  "id": "5d26a3dfa99b49edbe32d53bdfca061e",
  "script": null,
  "groupId": "fb9192d12472469f82f204f1a4310a28",
  "name": "02.服务分组列表",
  "createTime": 1782955363375,
  "updateTime": 1785000000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"keyword\": \"默认\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"children\": [{\"id\": \"api_service_group_default\", \"name\": \"默认分组\", \"count\": 10}],\n  \"total\": 10\n}",
  "description": "读取当前租户的服务分组字典，返回分组树与当前角色可见的服务总数。keyword 或 name 筛选分组名称与编码，departmentOnly 按会话部门收窄服务计数。未分组服务计入默认分组。角色范围无效或本部门入口缺少会话部门时返回 403。只读。"
}
================================
import "@post:/sym/user/me" as me
import dataScope
import tableRead
import tenantRuntime

/**
 * POST /dws/flowserve/gateway/group/list 读取服务管理左侧的分组树和服务数。
 * keyword 或 name 筛选显示的分组，total 仍统计当前范围的全部有效服务。
 * departmentOnly 按会话部门收窄服务范围。分组字典按当前租户读取，接口只读。
 */
var blank = value => value == null || (value + '').trim() == ''
var tenantId = tenantRuntime.id()
var scope = dataScope.dataSource()

var services = tableRead.from('api_info_t', 'service', tenantId)
services.where(tableRead.equal(tableRead.column('service', 'is_del', 'NUMBER'), 0))
var serviceOrg = tableRead.column('service', 'org_id', 'TEXT')
// 服务数同时受可信租户、角色及本部门视图限制，与服务列表的范围一致。
if (scope.scope == 'OWNER') {
    var owner = tableRead.column('service', 'created_by', 'TEXT')
    services.where(tableRead.in(owner, blank(scope.userId) ? [] : [scope.userId]))
} else if (scope.scope == 'ORG') {
    services.where(tableRead.in(serviceOrg, scope.organizationIds || []))
} else if (scope.scope != 'ALL') {
    exit 403, '数据范围无效'
}
if (body?.departmentOnly == true) {
    var orgId = me()?.orgId
    if (blank(orgId)) {
        exit 403, '当前账号未关联部门，无法查看本部门服务'
    }
    services.where(tableRead.equal(serviceOrg, orgId))
}

// 分组属于租户级字典。搜索只影响分组树，保留数据库原有的名称和空值顺序。
var groups = db.camel().table('sym_dict_t').columns('tid', 'dict_name', 'dict_code', 'icon', 'sort_no').where()
    .eq('tenant_id', tenantId).eq('is_del', 0).eq('biz_type', 'apiServiceGroup')
var keyword = body?.keyword || body?.name
if (!blank(keyword)) {
    var pattern = '%' + keyword + '%'
    groups.and(match => match.like('dict_name', pattern).or().like('dict_code', pattern))
}
var definitions = groups.orderBy('sort_no', 'asc').orderBy('dict_name', 'asc').orderBy('tid', 'asc').select()

// 按物理分组字段一次聚合。历史 NULL、空字符串和默认分组标识在响应中合并计数。
var defaultGroup = 'api_service_group_default'
var groupId = tableRead.column('service', 'group_id', 'TEXT')
var counts = services.grouped({groupId: groupId, total: tableRead.count()}, [groupId])
var countByGroup = {}
var total = 0
for (entry in counts) {
    var id = entry.groupId || defaultGroup
    var count = entry.total::int(0)
    countByGroup[id] = (countByGroup[id] || 0) + count
    total = total + count
}

var list = []
for (definition in definitions) {
    list.add({
        id: definition.tid,
        value: definition.tid,
        name: definition.dictName,
        label: definition.dictName,
        code: definition.dictCode,
        icon: definition.icon,
        // 原接口由 Magic 的列名映射返回 sortno，保留已有调用字段。
        sortno: definition.sortNo,
        count: countByGroup[definition.tid] || 0,
        children: []
    })
}
return {list: list, children: list, total: total, totalGroups: list.size()}
