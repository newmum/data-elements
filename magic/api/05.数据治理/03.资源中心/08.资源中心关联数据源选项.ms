{
  "properties": {},
  "id": "dwm_center_datasource_options_01",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "08.资源中心关联数据源选项",
  "createTime": 1785067200000,
  "updateTime": 1785067200000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/datasource-options",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"list\": [\n    {\n      \"label\": \"出入境人员动管系统数据库\",\n      \"value\": \"4b5d9992d89744b9b514f8905dc27998\",\n      \"dbType\": \"mysql\",\n      \"nodeName\": \"业务库\"\n    }\n  ],\n  \"total\": 1\n}",
  "description": "资源中心关联数据源选项（POST /dwm/center/datasource-options）。只读接口；从 db_datasource_t 读取全部有效数据源并支持名称/库类型/节点类型模糊搜索，用于分层/分域配置选择关联数据源；不读取或返回 connection extension property 密码；使用请求参数 keyword。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.资源中心关联数据源选项
// 路由：POST /dwm/center/datasource-options
// 业务说明：资源中心关联数据源选项（POST /dwm/center/datasource-options）。只读接口；从 db_datasource_t 读取全部有效数据源并支持名称/库类型/节点类型模糊搜索，用于分层/分域配置选择关联数据源；不读取或返回 connection extension property 密码；使用请求参数 keyword。
/*
 * 资源中心分层/分域配置的数据源下拉选项。
 * - 下拉展示全部有效数据源，方便从现有业务库/前置机/数据中心中选择；
 * - 真正保存时由 /dwm/center/node/save 将被关联数据源 node_id 归一为“数据中心”字典项。
 */
var keyword = body?.keyword == null ? '' : (body.keyword + '').trim()
var likeKeyword = '%' + keyword.replace("'", "''") + '%'

// node_id 与字典 tid 在历史库里可能存在不同 collation，避免 SQL join 产生排序规则冲突。
var nodeRows = db.select("""
    SELECT c.tid, c.dict_name
      FROM sym_dict_t c
      JOIN sym_dict_t p ON p.tid = c.parent_id AND p.is_del = 0
     WHERE c.is_del = 0
       AND p.dict_code = 'nodeType'
""")
var nodeNameById = {}
for (node in (nodeRows == null ? [] : nodeRows)) {
    nodeNameById[node.tid + ''] = node.dictName
}

var rows = db.select("""
    SELECT tid, db_name, db_type, show_connect, connection_status, node_id
      FROM db_datasource_t
     WHERE is_del = 0
       AND (tid IS NULL OR tid NOT LIKE 'codex\_%')
       AND (db_name IS NULL OR db_name NOT LIKE 'codex\_%')
     ORDER BY updated_time DESC, created_time DESC, db_name ASC
     LIMIT 500
""")
var list = []
for (row in (rows == null ? [] : rows)) {
    var nodeName = nodeNameById[row.nodeId + '']
    if (keyword != '') {
        var hitText = ((row.dbName == null ? '' : row.dbName) + ' ' +
            (row.dbType == null ? '' : row.dbType) + ' ' +
            (nodeName == null ? '' : nodeName)).toLowerCase()
        if (!hitText.contains(keyword.toLowerCase())) {
            continue
        }
    }
    list.add({
        label: row.dbName,
        value: row.tid,
        tid: row.tid,
        dbName: row.dbName,
        dbType: row.dbType,
        nodeId: row.nodeId,
        nodeName: nodeName,
        showConnect: row.showConnect,
        connectionStatus: row.connectionStatus
    })
    if (list.size() >= 100) {
        break
    }
}

return { list: list, total: list.size() }
