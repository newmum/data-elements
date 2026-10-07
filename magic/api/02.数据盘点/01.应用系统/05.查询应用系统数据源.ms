{
  "properties": {},
  "id": "datapd_application_databases_01",
  "script": null,
  "groupId": "7868bf218e904ebc999a94f041d91c36",
  "name": "05.查询应用系统数据源",
  "createTime": null,
  "updateTime": 1787156089361,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/databases",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"appId\":\"01910f1ff2ba4933b2cd759057d3fbf4\"}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": []\n}",
  "description": "从 db_datasource_t 查询应用系统下的数据源，并合并 connection extension property 连接参数；主数据字段以 db_datasource_t 为准，不读取 ES 或 da_prop_t；使用请求参数 appId。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.查询应用系统数据源
// 路由：POST /dst/application/databases
// 业务说明：从 db_datasource_t 查询应用系统下的数据源，并合并 connection extension property 连接参数；主数据字段以 db_datasource_t 为准，不读取 ES 或 da_prop_t；使用请求参数 appId。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
// 查询应用系统数据源：处理 POST /databases 的核心查询和组装返回数据逻辑。
import java.util.Map
import cn.hutool.json.JSONUtil as jsons
var appId = body?.appId
if (appId == null || (appId + '').trim() == '') {
    exit 400, 'appId不能为空'
}
var first = (row, keys) => {
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            return value
        }
    }
    return null
}
var result = []
for (main in db.table('db_datasource_t').where().eq('app_id', appId).eq('is_del', 0).orderBy('updated_time', 'desc').select()) {
    var item = {}
    var tid = first(main, ['tid'])
    var connectionConfig = datasourceConnectionConfig('read', tid, null, false)
    var hasConnection = connectionConfig != null && !connectionConfig.isEmpty()
    if (hasConnection) {
        item.putAll(connectionConfig)
    }
    item.tid = tid
    item.id = tid
    item.assetType = 'db'
    item.dbName = first(main, ['dbName', 'db_name'])
    item.dbType = first(main, ['dbType', 'db_type'])
    item.orgId = first(main, ['orgId', 'org_id'])
    item.storageDomain = first(main, ['storageDomain', 'storage_domain'])
    item.nodeId = first(main, ['nodeId', 'node_id'])
    item.assetDesc = first(main, ['remark'])
    item.showConnect = hasConnection ? 1 : 0
    item.tableNum = first(main, ['tableNum', 'table_num'])
    item.appId = appId
    item.assetStatus = 2
    result.add(item)
}
return result
