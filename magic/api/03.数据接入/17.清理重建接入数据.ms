{
  "properties": {},
  "id": "93adfae2a5074269981dc2a45d7358d5",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "17.清理重建接入数据",
  "createTime": 1784437770674,
  "updateTime": 1787156377586,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/cleanup-rebuild",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"dryRun\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "Magic 负责定位并逻辑删除接入任务、字段映射、监控快照、流水线、目标表；使用请求参数 dryRun、tid；涉及 db_datasource_t、db_table_t、data_access_agg_task_t、data_access_field_mapping、data_access_task_monitor_snap_t、nifi_pipeline_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：17.清理重建接入数据
// 路由：POST /ods/cleanup-rebuild
// 业务说明：Magic 负责定位并逻辑删除接入任务、字段映射、监控快照、流水线、目标表；使用请求参数 dryRun、tid；涉及 db_datasource_t、db_table_t、data_access_agg_task_t、data_access_field_mapping、data_access_task_monitor_snap_t、nifi_pipeline_t 等业务表。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
/*
 * 清理并重建数据接入目标
 *
 * Magic 负责定位并逻辑删除接入任务、字段映射、监控快照、流水线、目标表
 * 及字段，并清空目录目标关系。NiFiClient 和 MetadataExplorerService 仅负责
 * 运行时进程组清理与远端物理删表。body.dryRun=true 时只返回影响范围。
 */
import cn.hutool.json.JSONUtil as jsons
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifiClient
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService
import java.util.Map
import log

var blank = value => value == null || (value + '').trim() == ''
var valueOf = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (!blank(value)) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var loadDatabase = dbId => {
    // 从 MySQL 业务表读取当前有效数据。
    var main = db.table('db_datasource_t').where().eq('tid', dbId).eq('is_del', 0).selectOne()
    if (main == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    var result = {
        tid: main.tid,
        dbType: main.dbType,
        dbName: main.dbName,
        jdbcURL: main.jdbcUrl,
        username: main.username,
        password: main.password
    }
    result.putAll(datasourceConnectionConfig('read', main.tid, null, false))
    // 返回当前业务分支的标准结果。
    return result
}
var dataSourceOf = asset => {
    var host = valueOf(asset, ['host', 'dbIp'])
    var port = valueOf(asset, ['port', 'dbPort'])
    var databaseName = valueOf(asset, ['database', 'dbName', 'dbMetaDbName'])
    var username = valueOf(asset, ['username', 'dbUser', 'dbMetaUser', 'user'])
    if (blank(host) || blank(databaseName) || blank(username)) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 400, '旧目标数据库连接参数不完整'
    }
    var type
    try {
        type = DatabaseType.fromCode(((valueOf(asset, ['dbType', 'databaseType']) || 'mysql') + '').toLowerCase())
    } catch (e) {
        type = DatabaseType.MYSQL
    }
    var config = new DataSourceConfig()
    config.setDatabaseType(type)
    config.setHost(host + '')
    config.setPort(blank(port) ? type.getDefaultPort() : port::int)
    config.setDatabase(databaseName + '')
    config.setUsername(username + '')
    config.setPassword((valueOf(asset, ['password', 'dbPassword', 'dbMetaPassword']) || '') + '')
    config.setSchema(valueOf(asset, ['schema', 'defaultSchema']))
    // 返回当前业务分支的标准结果。
    return config
}

// 解析请求体并为可选参数设置默认值。
var catalogId = body?.tid
if (blank(catalogId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'tid参数不正确'
}
// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var catalog = esCommonService.getDocument('dataassets', catalogId + '')
if (catalog == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '资源目录不存在'
}

var oldTableId = catalog.targetTableId
// 从 MySQL 业务表读取当前有效数据。
var oldTable = blank(oldTableId) ? null : db.table('db_table_t').where()
    .eq('tid', oldTableId).eq('is_del', 0).selectOne()
var oldDbId = oldTable == null ? catalog.targetDbId : oldTable.datasourceId
var oldTableName = oldTable == null ? catalog.targetTableName : oldTable.tableName
// 从 MySQL 业务表读取当前有效数据。
var tasks = db.table('data_access_agg_task_t').where()
    .eq('data_catalog_id', catalogId).eq('is_del', 0).select()
var taskIds = []
for (task in tasks) {
    taskIds.add(task.tid)
}
var result = {
    catalogId: catalogId,
    oldTableId: oldTableId,
    oldDbId: oldDbId,
    oldTableName: oldTableName,
    taskIds: taskIds,
    dryRun: body?.dryRun == true
}
if (body?.dryRun == true) {
    // 返回当前业务分支的标准结果。
    return result
}

var now = new Date()
for (task in tasks) {
    // 从 MySQL 业务表读取当前有效数据。
    db.table('data_access_agg_task_t').where().eq('tid', task.tid).eq('is_del', 0)
        .update({is_del: 1, updated_time: now})
    // 从 MySQL 业务表读取当前有效数据。
    db.table('data_access_field_mapping').where().eq('task_id', task.tid).eq('is_del', 0)
        .update({is_del: 1, updated_time: now})
    // 从 MySQL 业务表读取当前有效数据。
    db.table('data_access_task_monitor_snap_t').where().eq('task_id', task.tid).eq('is_del', 0)
        .update({is_del: 1})
    if (!blank(task.pipelineId)) {
        // 从 MySQL 业务表读取当前有效数据。
        db.table('nifi_pipeline_t').where().eq('id', task.pipelineId).eq('is_del', 0)
            .update({is_del: 1, updated_at: System.currentTimeMillis()})
    }
    if (!blank(task.processGroupId)) {
        try {
            nifiClient.cleanupProcessGroup(task.processGroupId + '')
        } catch (e) {
            log.warn('NiFi进程组清理失败, processGroupId={}, message={}', task.processGroupId, e.message)
        }
    }
}

if (!blank(oldTableId)) {
    // 从 MySQL 业务表读取当前有效数据。
    db.table('db_table_column_t').where().eq('table_id', oldTableId).eq('is_del', 0)
        .update({is_del: 1, updated_time: now})
    // 从 MySQL 业务表读取当前有效数据。
    db.table('db_table_t').where().eq('tid', oldTableId).eq('is_del', 0)
        .update({is_del: 1, updated_time: now})
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    esCommonService.deleteById('dataassets', oldTableId + '')
}

var catalogPropsToClear = ['targetTableId', 'targetTableName', 'targetDbType', 'targetDbId', 'accessTaskId']
for (propName in catalogPropsToClear) {
    var prop = db.table('da_prop_t').where()
        .eq('parent_id', catalogId)
        .eq('prop_name', propName)
        .eq('is_del', 0)
        .selectOne()
    if (prop != null) {
        db.table('da_prop_t').primary('tid').update({
            tid: prop.tid,
            prop_value: null,
            updated_time: now
        }, true)
    }
}

if (!blank(oldDbId) && !blank(oldTableName)) {
    if (!(oldTableName + '').matches('^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?$')) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 400, '旧目标表名称不合法，无法执行物理删表'
    }
    var database = loadDatabase(oldDbId + '')
    if (database == null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 404, '旧目标数据库不存在'
    }
    metadataExplorerService.createTable(dataSourceOf(database), 'DROP TABLE IF EXISTS ' + oldTableName)
}

// 返回当前业务分支的标准结果。
return result
