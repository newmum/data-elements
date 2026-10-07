{
  "properties": {},
  "id": "datapd_datasource_connection_config_01",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "08.读取或保存数据源连接配置",
  "createTime": 1787155921712,
  "updateTime": 1787587507117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/datasourceConnectionConfig",
  "description": "从数据源主表及 pool_cfg 读取连接扩展属性，或按 action 保存允许配置；不使用旧动态属性表。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "action",
      "value": null,
      "description": "要执行的业务操作",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "datasourceId",
      "value": null,
      "description": "数据源标识",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "values",
      "value": null,
      "description": "save 时的连接参数对象",
      "required": false,
      "dataType": "Object",
      "type": "java.util.Map",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "clearAll",
      "value": null,
      "description": "save 时是否清空全部连接参数",
      "required": false,
      "dataType": "Boolean",
      "type": "java.lang.Boolean",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}
================================
// 共享函数：08.读取或保存数据源连接配置
// 函数路径：/common/datasourceConnectionConfig
// 业务说明：从数据源主表及 pool_cfg 读取连接扩展属性，或按 action 保存允许配置；不使用旧动态属性表。
// 函数输入：action：要执行的业务操作；datasourceId：数据源标识；values：save 时的连接参数对象；clearAll：save 时是否清空全部连接参数。
import cn.hutool.json.JSONUtil as jsons
import tenantRuntime
import java.util.Map

/*
 * 数据源连接配置统一入口。
 *
 * 连接主字段保存在 db_datasource_t，完整的扩展配置保存在同一记录的 pool_cfg JSON 对象。
 * 本函数不读取、不写入 da_prop_t；保留 read/json/save 调用约定以兼容现有 Magic 接口。
 */
var textOf = value => value == null ? '' : value + ''
var blank = value => value == null || textOf(value).trim() == ''
var tenantId = textOf(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '无法识别当前租户，请重新登录'
}
var rowValue = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            return value
        }
    }
    return null
}
var mainOf = datasourceId => {
    if (blank(datasourceId)) {
        return null
    }
    return db.table('db_datasource_t').where()
        .eq('tid', textOf(datasourceId))
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .selectOne()
}
var poolOf = main => {
    var raw = rowValue(main, ['poolCfg', 'pool_cfg'])
    if (blank(raw)) {
        return {}
    }
    try {
        var parsed = raw instanceof Map ? raw : jsons.parse(textOf(raw))
        if (parsed == null) {
            return {}
        }
        return parsed
    } catch (e) {
        exit 500, '数据源连接配置 pool_cfg 不是有效 JSON 对象'
    }
}
var copyMaster = (result, main) => {
    var pairs = {
        dbType: ['dbType', 'db_type'],
        jdbcURL: ['jdbcURL', 'jdbcUrl', 'jdbc_url'],
        username: ['username'],
        password: ['password'],
        dbVersion: ['dbVersion', 'db_version'],
        nodeId: ['nodeId', 'node_id'],
        storageDomain: ['storageDomain', 'storage_domain'],
        showConnect: ['showConnect', 'show_connect'],
        connectionStatus: ['connectionStatus', 'connection_status']
    }
    for (key in pairs.keySet()) {
        var value = rowValue(main, pairs[key])
        if (value != null) {
            result[key] = value
        }
    }
}
var read = datasourceId => {
    var main = mainOf(datasourceId)
    if (main == null) {
        return {}
    }
    var result = {}
    result.putAll(poolOf(main))
    copyMaster(result, main)
    return result
}
var save = (datasourceId, values, clearAll) => {
    var main = mainOf(datasourceId)
    if (main == null) {
        exit 404, '数据源不存在或不属于当前租户'
    }
    var config = clearAll == true ? {} : poolOf(main)
    if (clearAll != true && values != null) {
        // MagicScript cannot invoke Map.Entry#getKey/getValue on values passed
        // across a function boundary. Iterate the map keys and read each value
        // through the map instead, which also works for native script objects.
        var valueKeys = values instanceof Map ? values.keySet() : values
        for (keyItem in valueKeys) {
            var key = textOf(keyItem)
            var value = values instanceof Map ? values.get(keyItem) : values[keyItem]
            if (!blank(key) && value != null) {
                config[key] = value
            }
        }
    }
    var update = {
        tid: rowValue(main, ['tid']),
        pool_cfg: clearAll == true ? null : jsons.toJsonStr(config),
        updated_time: new Date()
    }
    if (clearAll == true) {
        update.jdbc_url = null
        update.username = null
        update.password = null
        update.driver_class_name = null
    } else {
        var dbType = config.dbType
        var jdbcUrl = config.jdbcURL == null ? config.jdbcUrl : config.jdbcURL
        if (!blank(dbType)) {
            update.db_type = textOf(dbType)
        }
        if (!blank(jdbcUrl)) {
            update.jdbc_url = textOf(jdbcUrl)
        }
        if (config.username != null) {
            update.username = textOf(config.username)
        }
        if (config.password != null) {
            update.password = textOf(config.password)
        }
    }
    db.table('db_datasource_t').primary('tid').update(update, true)
    return read(datasourceId)
}

if (action == 'read') {
    return read(datasourceId)
}
if (action == 'json') {
    return jsons.toJSONStr(read(datasourceId))
}
if (action == 'save') {
    return save(datasourceId, values, clearAll == true)
}
exit 400, 'action 仅支持 read、json 或 save'
