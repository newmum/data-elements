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
  "description": "服务分组列表（POST /dws/flowserve/gateway/group/list）。读取 sym_dict_t 中 biz_type=apiServiceGroup 的服务分组字典，并聚合 api_info_t 服务数量；当前不新建独立分组表；使用请求参数 keyword、name。"
}
================================
// 接口：02.服务分组列表
// 路由：POST /dws/flowserve/gateway/group/list
// 业务说明：服务分组列表（POST /dws/flowserve/gateway/group/list）。读取 sym_dict_t 中 biz_type=apiServiceGroup 的服务分组字典，并聚合 api_info_t 服务数量；当前不新建独立分组表；使用请求参数 keyword、name。
/*
 * 服务分组树。
 * 分组暂不独立建表，统一使用 sym_dict_t 的业务字典：
 * biz_type=apiServiceGroup，默认分组 tid=api_service_group_default。
 */
var blank = value => value == null || (value + '').trim() == ''
var sqlValue = value => "'" + ((value + '').replace("'", "''")) + "'"
var likeValue = value => "'%" + ((value + '').replace("'", "''")) + "%'"

var whereSql = " where is_del = 0 and biz_type = 'apiServiceGroup' "
if (!blank(body?.keyword || body?.name)) {
    var keyword = body?.keyword || body?.name
    whereSql += " and (dict_name like " + likeValue(keyword) + " or dict_code like " + likeValue(keyword) + ") "
}

var rows = db.select("""
select
  tid as id,
  tid as value,
  dict_name as name,
  dict_name as label,
  dict_code as code,
  icon as icon,
  sort_no as sortNo
from sym_dict_t
""" + whereSql + """
order by sort_no, dict_name
""")

var countRows = db.select("""
select coalesce(group_id, 'api_service_group_default') as groupId, count(1) as total
  from api_info_t
 where is_del = 0
 group by coalesce(group_id, 'api_service_group_default')
""")
var countMap = {}
var total = 0
for (item in countRows) {
    var key = (item.groupId || item.groupid || item.group_id || 'api_service_group_default') + ''
    var num = item.total == null ? 0 : item.total::int(0)
    countMap[key] = num
    total = total + num
}

var list = []
for (row in rows) {
    var id = row.id + ''
    row.count = countMap[id] == null ? 0 : countMap[id]
    row.children = []
    list.add(row)
}

return {
    list: list,
    children: list,
    total: total,
    totalGroups: list.size()
}
