{
  "properties" : {
    "huaweiHiveTargetDeletion" : "magic-gateway-v2"
  },
  "id" : "ods_data_agg_reset_01",
  "script" : null,
  "groupId" : "1f4028f20aaa4f379b5e37a343b99fa9",
  "name" : "15.取消接入流程与删除目标表",
  "createTime" : 1786615200000,
  "updateTime" : 1790955212007,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/dataAggReset",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n  \"sourceTableId\": \"2087688601765228544\",\n  \"deleteTargetTable\": true\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "取消或重建数据接入；仅清理当前租户所选来源表关联的目标和流程。华为 MRS Hive 从登记配置读取配置集与数据库名，调用 23.华为MRSHive元数据网关执行 SHOW TABLES、确认删表与删除后校验，不要求登记 JDBC 地址或元数据库连接。dryRun=true 只读预检；使用请求参数 sourceTableId、tableId、tid、deleteTargetTable、dryRun、targetTableId、target_table_id、targetDbId；涉及 db_table_t、data_access_agg_task_t、db_datasource_t、data_access_field_mapping、data_access_task_monitor_snap_t、nifi_pipeline_t 等业务表。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：15.取消接入流程与删除目标表
// 路由：POST /ods/dataAggReset
// 业务说明：取消或重建数据接入；仅清理当前租户所选来源表关联的目标和流程。华为 MRS Hive 从登记配置读取配置集与数据库名，使用服务端连接执行 SHOW TABLES、DROP TABLE 与删除后校验，不要求登记 JDBC 地址或元数据库连接。dryRun=true 只读预检；使用请求参数 sourceTableId、tableId、tid、deleteTargetTable、dryRun、targetTableId、target_table_id、targetDbId；涉及 db_table_t、data_access_agg_task_t、db_datasource_t、data_access_field_mapping、data_access_task_monitor_snap_t、nifi_pipeline_t 等业务表。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import '@post:/dst/database/metadata/huaweiMrsHiveJdbcDebug' as huaweiHiveGateway
/*
 * Rebuild recovery for a source table.
 * The operation is deliberately source-scoped so one table cannot remove another
 * table's target metadata or deployed pipeline.
 * Huawei MRS Hive uses the registered server-owned profile, without browser JDBC
 * credentials or a Metastore connection. dryRun=true only probes physical state.
 */
import jsons
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifiClient
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.TargetTableDeletionService as targetTableDeletionService
import java.util.Map
import log
import tenantRuntime

var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (!blank(value)) { return value }
    }
    return null
}
var safeTableName = (value) => text(value).trim().matches('^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?$')
// The metadata gateway owns the server-side MRS connection; this API only verifies
// that the selected target belongs to the source task before asking it to act.
var managedHiveOf = (config) => config != null && config.getDatabaseType() == DatabaseType.HIVE
    && text(config.getConnectorProperties()?.get('metadataAccessMode')) == 'server-managed-mrs'
var callHiveGateway = (request) => {
    var originalBody = body
    try {
        body = request
        var response = huaweiHiveGateway()
        if (response == null || response.success != true) {
            throw new IllegalStateException('华为 MRS Hive 元数据网关调用失败')
        }
        return response
    } finally {
        body = originalBody
    }
}
var hivePreflight = (config, requestedName) => {
    var scope = text(config.getDatabase()).trim()
    if (!scope.matches('^[A-Za-z_][A-Za-z0-9_]*$')) {
        throw new IllegalArgumentException('Hive 目标数据库名不合法')
    }
    var profile = text(config.getConnectorProperties()?.get('hiveProfile')).trim()
    if (profile != '' && profile.toLowerCase() != 'default') {
        throw new IllegalArgumentException('未配置的华为 MRS Hive 配置集: ' + profile)
    }
    var name = text(requestedName).trim()
    var separator = name.indexOf('.')
    if (separator >= 0) {
        var requestedScope = name.substring(0, separator)
        if (requestedScope.toLowerCase() != scope.toLowerCase()) {
            throw new IllegalArgumentException('目标表所属数据库或 Schema 与所选目标数据源不一致：' + scope)
        }
        name = name.substring(separator + 1)
    }
    if (!name.matches('^[A-Za-z_][A-Za-z0-9_]*$')) {
        throw new IllegalArgumentException('Hive 目标表名不合法')
    }
    var response = callHiveGateway({ action: 'tables', contract: 'gateway', database: scope })
    var found = false
    for (candidate in (response.tables == null ? [] : response.tables)) {
        if (text(candidate).toLowerCase() == name.toLowerCase()) {
            found = true
            break
        }
    }
    return { physicalExists: found, targetTableName: name, targetScope: scope, databaseType: 'hive' }
}
var hiveDrop = (config, requestedName) => {
    var before = hivePreflight(config, requestedName)
    if (!before.physicalExists) {
        return { physicalExists: false, physicalDeleted: false, targetTableName: before.targetTableName,
            targetScope: before.targetScope, databaseType: 'hive' }
    }
    callHiveGateway({ action: 'dropTable', contract: 'gateway', database: before.targetScope,
        table: before.targetTableName,
        confirm: 'DROP_TABLE:' + before.targetScope + '.' + before.targetTableName })
    var after = hivePreflight(config, requestedName)
    if (after.physicalExists) {
        throw new IllegalStateException('华为 Hive 目标物理表删除后仍存在，已保留流程和元数据：'
            + before.targetScope + '.' + before.targetTableName)
    }
    return { physicalExists: true, physicalDeleted: true, targetTableName: before.targetTableName,
        targetScope: before.targetScope, databaseType: 'hive' }
}
var dataSourceOf = (database) => {
    var config = datasourceConnectionConfig('read', first(database, ['tid']), null, false)
    var dbTypeText = text(first(database, ['dbType', 'db_type'])).trim().toLowerCase()
    var jdbcUrl = text(first(config, ['jdbcURL', 'jdbcUrl']) || first(database, ['jdbcUrl', 'jdbc_url']))
    var compatibleMode = text(first(config, ['compatibleMode', 'compatibilityMode', 'mode', 'databaseMode', 'dbMode'])
        || first(database, ['compatibleMode', 'compatibilityMode', 'mode', 'databaseMode', 'dbMode'])).trim().toUpperCase()
    var normalizedType = dbTypeText.replace('_', '').replace('-', '')
    var type
    if (normalizedType == 'oceanbase') {
        var lowerJdbcUrl = jdbcUrl.toLowerCase()
        type = compatibleMode == 'ORACLE'
            || lowerJdbcUrl.startsWith('jdbc:oceanbase:oracle:')
            || lowerJdbcUrl.startsWith('jdbc:oceanbaseoracle:')
            || lowerJdbcUrl.startsWith('jdbc:oracle:')
            ? DatabaseType.OCEANBASE_ORACLE : DatabaseType.OCEANBASE_MYSQL
    } else {
        try { type = DatabaseType.fromCode(dbTypeText) }
        catch (ignored) { exit 400, '不支持的目标数据库类型，无法安全删除目标表：' + dbTypeText }
    }
    var host = first(config, ['host', 'dbMetaIp']) || first(database, ['host', 'db_ip'])
    var port = first(config, ['port', 'dbMetaPort']) || first(database, ['port', 'db_port'])
    var databaseName = first(config, ['database', 'dbName', 'dbMetaDbName']) || first(database, ['database', 'db_name'])
    var username = first(config, ['username', 'dbMetaUser']) || first(database, ['username', 'db_user'])
    var accessMode = text(first(config, ['metadataAccessMode', 'metadata_access_mode'])).trim().toLowerCase()
    var hiveMode = text(first(config, ['hiveConnectionMode', 'hive_connection_mode'])).trim().toLowerCase()
    var hiveProfile = text(first(config, ['hiveProfile', 'hive_profile'])).trim()
    var managedHive = type == DatabaseType.HIVE && accessMode != 'jdbc' && hiveMode != 'open-source'
        && (accessMode == 'server-managed-mrs' || hiveMode == 'huawei-mrs' || !blank(hiveProfile))
    if (blank(databaseName) || (!managedHive && (blank(host) || blank(username)))) {
        exit 400, '目标数据源连接信息不完整，无法删除目标物理表'
    }
    var result = new DataSourceConfig()
    result.setDatabaseType(type)
    result.setHost(text(host))
    result.setPort(blank(port) ? type.getDefaultPort() : port::int)
    result.setDatabase(text(databaseName))
    result.setUsername(text(username))
    result.setPassword(text(first(config, ['password', 'dbMetaPassword']) || first(database, ['password', 'db_password'])))
    result.setJdbcUrl(jdbcUrl)
    if (managedHive) {
        result.setConnectorProperties({
            metadataAccessMode: 'server-managed-mrs',
            hiveConnectionMode: 'huawei-mrs',
            hiveProfile: blank(hiveProfile) ? 'default' : hiveProfile
        })
    }
    // 显式 Schema 优先；旧达梦登记的 database 保存业务 Schema。
    // 与 AbstractMetadataExplorer.effectiveConfig / 建表链路使用相同回退，
    // 不能让删除服务把管理账号 SYSDBA 误当成目标业务库。
    var targetSchema = first(config, ['schema', 'defaultSchema', 'currentSchema', 'dbMetaSchema', 'db_schema'])
        || first(database, ['schema', 'defaultSchema', 'currentSchema', 'dbMetaSchema', 'db_schema'])
    if (blank(targetSchema) && type == DatabaseType.DAMENG) { targetSchema = databaseName }
    result.setSchema(targetSchema)
    return result
}
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) { exit 401, '登录状态已失效，请重新登录' }

var sourceTableId = text(body?.sourceTableId || body?.tableId || body?.tid).trim()
var deleteTargetTable = body?.deleteTargetTable == true
var dryRun = body?.dryRun == true
if (blank(sourceTableId)) { exit 400, 'sourceTableId不能为空' }

var sourceTable = db.table('db_table_t').where()
    .eq('tid', sourceTableId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (sourceTable == null) { exit 404, '来源表不存在' }

var tasks = db.table('data_access_agg_task_t').where()
    .eq('tenant_id', tenantId).eq('source_table_id', sourceTableId).eq('is_del', 0).select()
var targetTable = null
var targetChosenByTask = false
var targetTableId = text(body?.targetTableId || body?.target_table_id).trim()
var targetDbId = text(body?.targetDbId || body?.targetDatasourceId || body?.target_db_id).trim()
var targetTableName = text(body?.targetTableName || body?.target_table_name).trim()
var targetSelectionMode = ''
var targetMetadataMissing = false
var sourceDatasourceId = text(first(sourceTable, ['datasourceId', 'datasource_id']))
var sourceTableName = text(first(sourceTable, ['tableName', 'table_name']))
var normalizeIdentifier = (value) => text(value).trim().toUpperCase()
var sameIdentifier = (left, right) => normalizeIdentifier(left) == normalizeIdentifier(right)
var taskForTargetId = (candidateId) => {
    for (task in (tasks == null ? [] : tasks)) {
        if (text(first(task, ['targetTableId', 'target_table_id'])) == text(candidateId)) {
            return task
        }
    }
    return null
}

if (deleteTargetTable) {
    if (blank(targetDbId) || blank(targetTableName) || !safeTableName(targetTableName)) {
        exit 400, '请提供合法的目标数据源 ID 和目标表名'
    }
    if (targetDbId == sourceDatasourceId && sameIdentifier(targetTableName, sourceTableName)) {
        exit 409, '目标数据源和表名指向来源表本身，已拒绝删除'
    }
    if (!blank(targetTableId) && targetTableId == sourceTableId) {
        exit 409, '目标表 ID 与来源表 ID 相同，已拒绝删除'
    }

    if (!blank(targetTableId)) {
        // An explicitly supplied metadata ID is authoritative, but it must
        // match the exact target datasource/name and belong to this source.
        targetSelectionMode = 'target_table_id'
        targetTable = db.table('db_table_t').where()
            .eq('tid', targetTableId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
        if (targetTable == null) { exit 404, '目标表 ID 不存在或不属于当前租户' }
        var resolvedTargetId = text(first(targetTable, ['tid']))
        var resolvedTargetDbId = text(first(targetTable, ['datasourceId', 'datasource_id']))
        var resolvedTargetName = text(first(targetTable, ['tableName', 'table_name']))
        if (resolvedTargetId == sourceTableId) { exit 409, '目标表 ID 指向来源表本身，已拒绝删除' }
        if (resolvedTargetDbId != targetDbId || !sameIdentifier(resolvedTargetName, targetTableName)) {
            exit 409, '目标表 ID 与指定的目标数据源或目标表名不匹配'
        }
        var linkedTask = taskForTargetId(targetTableId)
        targetChosenByTask = linkedTask != null
        var targetSourceTableId = text(first(targetTable, ['sourceTableId', 'source_table_id']))
        if ((!blank(targetSourceTableId) && targetSourceTableId != sourceTableId)
            || (blank(targetSourceTableId) && !targetChosenByTask)) {
            exit 409, '目标表元数据未关联到当前来源表，已拒绝删除'
        }
        var taskTargetDbId = text(first(linkedTask, ['targetDbId', 'target_db_id']))
        if (!blank(taskTargetDbId) && taskTargetDbId != targetDbId) {
            exit 409, '接入任务的目标数据源与目标表元数据不一致'
        }
    } else {
        // Without an ID, only the exact datasource + exact table name may
        // resolve metadata. Never fall back to source_table_id alone: source
        // rows can carry that value too.
        targetSelectionMode = 'datasource_and_table_name'
        var exactTargetRows = db.table('db_table_t').where()
            .eq('tenant_id', tenantId).eq('datasource_id', targetDbId)
            .eq('table_name', targetTableName).eq('is_del', 0).select()
        var exactTargetCandidates = []
        for (candidate in (exactTargetRows == null ? [] : exactTargetRows)) {
            var candidateId = text(first(candidate, ['tid']))
            var candidateDbId = text(first(candidate, ['datasourceId', 'datasource_id']))
            var candidateName = text(first(candidate, ['tableName', 'table_name']))
            if (blank(candidateId) || candidateId == sourceTableId) { continue }
            if (candidateDbId == targetDbId && sameIdentifier(candidateName, targetTableName)) {
                exactTargetCandidates.add(candidate)
            }
        }
        if (exactTargetCandidates.size() > 1) {
            exit 409, '目标数据源和表名匹配到多条元数据，请传入目标表 ID'
        }
        if (exactTargetCandidates.size() == 1) {
            targetTable = exactTargetCandidates[0]
            var matchedTargetId = text(first(targetTable, ['tid']))
            var matchedTargetSourceId = text(first(targetTable, ['sourceTableId', 'source_table_id']))
            var matchedTask = taskForTargetId(matchedTargetId)
            targetChosenByTask = matchedTask != null
            if ((!blank(matchedTargetSourceId) && matchedTargetSourceId != sourceTableId)
                || (blank(matchedTargetSourceId) && !targetChosenByTask)) {
                exit 409, '匹配到的目标表元数据未关联到当前来源表，已拒绝删除'
            }
            var matchedTaskDbId = text(first(matchedTask, ['targetDbId', 'target_db_id']))
            if (!blank(matchedTaskDbId) && matchedTaskDbId != targetDbId) {
                exit 409, '接入任务的目标数据源与目标表元数据不一致'
            }
            targetTableId = matchedTargetId
        } else {
            targetMetadataMissing = true
            if (body?.confirmUnmanagedTarget != true) {
                exit 409, '目标表未登记，请确认要按指定目标数据源和表名预检未登记表'
            }
        }
    }
} else {
    // Cancelling a task never requires resolving or deleting a target asset.
    // Retain a target ID for response compatibility only when it is not the
    // source row itself and belongs to the active tenant.
    if (!blank(targetTableId) && targetTableId != sourceTableId) {
        targetTable = db.table('db_table_t').where()
            .eq('tid', targetTableId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    }
    if (targetTable == null) {
        for (task in (tasks == null ? [] : tasks)) {
            var candidateId = text(first(task, ['targetTableId', 'target_table_id']))
            if (!blank(candidateId) && candidateId != sourceTableId) {
                targetTable = db.table('db_table_t').where()
                    .eq('tid', candidateId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
                if (targetTable != null) { targetTableId = candidateId; break }
            }
        }
    }
    if (targetTable != null) {
        targetDbId = text(first(targetTable, ['datasourceId', 'datasource_id']))
        targetTableName = text(first(targetTable, ['tableName', 'table_name']))
    }
}

// Preflight before task cleanup: a target connection failure must not leave a
// half-cancelled task that cannot be rebuilt from the page.
var preflightConfig = null
var preflightPhysicalExists = false
var preflightScope = ''
var preflightDatabaseType = ''
var physicalTargetTableName = targetTableName
var historicalScopeNormalized = false
var targetDatasourceMissing = false
var targetPhysicalDatasourceMissing = false
var targetPhysicalCleanupSkipped = false
var targetCleanupSkipReason = ''
var missingObjectMessage = (value) => {
    var message = text(value).toLowerCase()
    return message.indexOf('表不存在') >= 0 || message.indexOf('对象不存在') >= 0
        || message.indexOf('无效的表或视图名') >= 0 || message.indexOf('ora-00942') >= 0
        || message.indexOf('table or view does not exist') >= 0
        || message.indexOf('object does not exist') >= 0
}
var missingDatasourceMessage = (value) => {
    var message = text(value).toLowerCase()
    return message.indexOf('unknown database') >= 0 || message.indexOf('database does not exist') >= 0
        || message.indexOf('数据库不存在') >= 0 || message.indexOf('ora-12514') >= 0
        || message.indexOf('ora-12505') >= 0 || message.indexOf('服务名不存在') >= 0
        || message.indexOf('服务名无效') >= 0
}
if (deleteTargetTable) {
    // A missing table-asset row is not permission to guess from source_table_id.
    // In that case the explicit datasource/name pair is still preflighted, but
    // no table metadata row can be soft-deleted.
    var preflightDatasource = db.table('db_datasource_t').where()
        .eq('tid', targetDbId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (preflightDatasource == null) {
        exit 404, '目标数据源不存在或不属于当前租户，已保留流程和元数据'
    } else {
        preflightConfig = dataSourceOf(preflightDatasource)
        try {
        // Resolve against the entire selected datasource scope, not a 200-row
        // page.  The Java service also locks Oracle/OceanBase Oracle to its
        // configured schema and rejects a qualified name from another schema.
        var preflight = managedHiveOf(preflightConfig)
            ? hivePreflight(preflightConfig, physicalTargetTableName)
            : targetTableDeletionService.preflight(preflightConfig, physicalTargetTableName)
        preflightPhysicalExists = preflight.physicalExists == true
        preflightScope = text(preflight.targetScope)
        preflightDatabaseType = text(preflight.databaseType)
        } catch (error) {
        // Historical materialization rows can retain a qualified source-table
        // name after the target datasource has been reconfigured to another
        // schema.  Do not let that stale qualifier prevent cleanup forever.
        // Probe the unqualified name only inside the datasource's configured
        // scope; this never broadens the destructive operation to the old
        // schema.  A missing table is then the normal idempotent cleanup case.
        var preflightMessage = text(error.message || error)
        var separator = targetTableName.lastIndexOf('.')
        if (managedHiveOf(preflightConfig) && missingDatasourceMessage(preflightMessage)) {
            exit 400, '华为 Hive 目标数据库无法确认，已保留流程和元数据：' + preflightMessage
        } else if (missingDatasourceMessage(preflightMessage)) {
            targetPhysicalDatasourceMissing = true
            targetPhysicalCleanupSkipped = true
            targetCleanupSkipReason = 'target_physical_datasource_missing'
        } else if (preflightMessage.indexOf('不一致') < 0 || separator < 1) {
            exit 400, '无法校验目标物理表，已保留流程和元数据：' + preflightMessage
        } else {
            physicalTargetTableName = targetTableName.substring(separator + 1)
            if (!safeTableName(physicalTargetTableName)) {
                exit 400, '目标表名不合法，已保留流程和元数据：' + targetTableName
            }
            try {
                var normalizedPreflight = managedHiveOf(preflightConfig)
                    ? hivePreflight(preflightConfig, physicalTargetTableName)
                    : targetTableDeletionService.preflight(preflightConfig, physicalTargetTableName)
                preflightPhysicalExists = normalizedPreflight.physicalExists == true
                preflightScope = text(normalizedPreflight.targetScope)
                preflightDatabaseType = text(normalizedPreflight.databaseType)
                historicalScopeNormalized = true
            } catch (normalizedError) {
                var normalizedMessage = text(normalizedError.message || normalizedError)
                if (managedHiveOf(preflightConfig) && missingDatasourceMessage(normalizedMessage)) {
                    exit 400, '华为 Hive 目标数据库无法确认，已保留流程和元数据：' + normalizedMessage
                } else if (missingDatasourceMessage(normalizedMessage)) {
                    targetPhysicalDatasourceMissing = true
                    targetPhysicalCleanupSkipped = true
                    targetCleanupSkipReason = 'target_physical_datasource_missing'
                } else {
                    exit 400, '无法校验目标物理表，已保留流程和元数据：' + normalizedMessage
                }
            }
        }
    }
}
}
if (dryRun) {
    return {
        dryRun: true,
        sourceTableId: sourceTableId,
        sourceDatasourceId: sourceDatasourceId,
        sourceTableName: sourceTableName,
        sourceTableExcluded: true,
        sourceTableSoftDeletePlanned: false,
        taskCount: tasks == null ? 0 : tasks.size(),
        targetTableId: targetTable == null ? '' : text(first(targetTable, ['tid'])),
        resolvedTargetTableId: targetTable == null ? '' : text(first(targetTable, ['tid'])),
        targetDbId: targetDbId,
        resolvedTargetDatabaseId: targetDbId,
        targetTableName: targetTableName,
        resolvedTargetTableName: targetTableName,
        targetSelectionMode: targetSelectionMode,
        targetPhysicalExists: preflightPhysicalExists,
        targetScope: preflightScope,
        targetDatabaseType: preflightDatabaseType,
        resolvedTargetDatabaseType: preflightDatabaseType,
        physicalTargetTableName: physicalTargetTableName,
        historicalScopeNormalized: historicalScopeNormalized,
        targetMetadataMissing: targetMetadataMissing,
        targetDatasourceMissing: targetDatasourceMissing,
        targetPhysicalDatasourceMissing: targetPhysicalDatasourceMissing,
        targetPhysicalCleanupSkipped: targetPhysicalCleanupSkipped,
        targetCleanupSkipReason: targetCleanupSkipReason,
        deleteTargetTableRequested: deleteTargetTable,
        targetTableDeleted: false,
        sideEffectsApplied: false
    }
}

// The physical-table action is deliberately first.  A driver, permission or
// dialect failure (notably OceanBase Oracle) must leave the task, pipeline and
// catalog rows available for recovery instead of creating a half-deleted flow.
var physicalDeleteResult = null
if (deleteTargetTable && !targetPhysicalCleanupSkipped) {
    try {
        physicalDeleteResult = managedHiveOf(preflightConfig)
            ? hiveDrop(preflightConfig, physicalTargetTableName)
            : targetTableDeletionService.drop(preflightConfig, physicalTargetTableName)
    } catch (error) {
        var deleteMessage = text(error.message || error)
        if (missingObjectMessage(deleteMessage)) {
            targetPhysicalCleanupSkipped = true
            targetCleanupSkipReason = 'target_physical_missing'
        } else {
            exit 400, '删除目标物理表失败，已保留流程和元数据：' + deleteMessage
        }
    }
}

var now = new Date()
var pipelineCount = 0
for (task in (tasks == null ? [] : tasks)) {
    var taskId = text(first(task, ['tid']))
    var pipelineId = text(first(task, ['pipelineId', 'pipeline_id']))
    db.table('data_access_field_mapping').where().eq('task_id', taskId).eq('is_del', 0)
        .update({ is_del: 1, updated_time: now })
    db.table('data_access_task_monitor_snap_t').where().eq('task_id', taskId).eq('is_del', 0)
        .update({ is_del: 1 })
    db.table('data_access_agg_task_t').where().eq('tid', taskId).eq('is_del', 0)
        .update({ is_del: 1, task_status: 0, updated_time: now })
    if (!blank(pipelineId)) {
        db.table('nifi_pipeline_t').where().eq('id', pipelineId).eq('is_del', 0)
            .update({ is_del: 1, updated_at: now.getTime() })
        pipelineCount = pipelineCount + 1
    }
    var processGroupId = text(first(task, ['processGroupId', 'process_group_id']))
    if (!blank(processGroupId)) {
        try { nifiClient.cleanupProcessGroup(processGroupId) } catch (error) {
            log.warn('Unable to clean NiFi process group {}, reason={}', processGroupId, error.message)
        }
    }
}

if (deleteTargetTable) {
    if (targetTable != null) {
        var targetId = text(first(targetTable, ['tid']))
        if (blank(targetId) || targetId == sourceTableId) {
            exit 409, '目标表元数据指向来源表本身，已拒绝软删除'
        }
        db.table('db_table_column_t').where().eq('table_id', targetId).eq('is_del', 0)
            .update({ is_del: 1, updated_time: now })
        db.table('db_table_t').where().eq('tid', targetId).ne('tid', sourceTableId)
            .eq('tenant_id', tenantId).eq('is_del', 0)
            .update({ is_del: 1, updated_time: now })
        try { esCommonService.deleteById('dataassets', targetId) } catch (ignored) {}
    }
    var remainingTargets = db.table('db_table_t').where()
        .eq('tenant_id', tenantId).eq('source_table_id', sourceTableId)
        .ne('tid', sourceTableId).eq('is_del', 0).select()
    var catalogs = db.table('da_prop_t').where().eq('prop_name', 'sourceTableId')
        .eq('prop_value', sourceTableId).eq('is_del', 0).select()
    if (remainingTargets == null || remainingTargets.size() == 0) {
        for (catalogProp in (catalogs == null ? [] : catalogs)) {
            var catalogId = text(first(catalogProp, ['parentId', 'parent_id']))
            db.table('da_prop_t').where().eq('parent_id', catalogId).eq('is_del', 0)
                .update({ is_del: 1, updated_time: now })
            db.table('da_catalog_t').where().eq('tid', catalogId).eq('is_del', 0)
                .update({ is_del: 1, updated_time: now })
            try { esCommonService.deleteById('dataassets', catalogId) } catch (ignored) {}
        }
        db.table('db_table_t').where().eq('tid', sourceTableId).eq('is_del', 0)
            .update({ source_catalog_id: null, catalog_name: null, updated_time: now })
    } else {
        // 多目标场景删除其中一个目标后，其余目标仍然有效。目录保留并把兼容的
        // 单目标属性指向首个剩余目标，避免后续重新创建任务时误判为无目标表。
        var remainingTarget = remainingTargets[0]
        var remainingTargetId = text(first(remainingTarget, ['tid']))
        var remainingTargetDbId = text(first(remainingTarget, ['datasourceId', 'datasource_id']))
        var remainingTargetName = text(first(remainingTarget, ['tableName', 'table_name']))
        for (catalogProp in (catalogs == null ? [] : catalogs)) {
            var remainingCatalogId = text(first(catalogProp, ['parentId', 'parent_id']))
            db.table('da_prop_t').where().eq('parent_id', remainingCatalogId)
                .eq('prop_name', 'targetTableId').eq('is_del', 0)
                .update({ prop_value: remainingTargetId, updated_time: now })
            db.table('da_prop_t').where().eq('parent_id', remainingCatalogId)
                .eq('prop_name', 'targetDbId').eq('is_del', 0)
                .update({ prop_value: remainingTargetDbId, updated_time: now })
            db.table('da_prop_t').where().eq('parent_id', remainingCatalogId)
                .eq('prop_name', 'targetTableName').eq('is_del', 0)
                .update({ prop_value: remainingTargetName, updated_time: now })
        }
    }
}

return {
    sourceTableId: sourceTableId,
    sourceDatasourceId: sourceDatasourceId,
    sourceTableName: sourceTableName,
    sourceTableSoftDeleted: false,
    taskCount: tasks == null ? 0 : tasks.size(),
    pipelineCount: pipelineCount,
    targetTableId: targetTable == null ? '' : text(first(targetTable, ['tid'])),
    resolvedTargetTableId: targetTable == null ? '' : text(first(targetTable, ['tid'])),
    targetDbId: targetDbId,
    resolvedTargetDatabaseId: targetDbId,
    targetTableDeleted: deleteTargetTable,
    targetTableName: targetTableName,
    resolvedTargetTableName: targetTableName,
    targetSelectionMode: targetSelectionMode,
    targetMetadataMissing: targetMetadataMissing,
    physicalTargetTableName: physicalTargetTableName,
    historicalScopeNormalized: historicalScopeNormalized,
    targetMetadataMissing: targetMetadataMissing,
    targetDatasourceMissing: targetDatasourceMissing,
    targetPhysicalDatasourceMissing: targetPhysicalDatasourceMissing,
    targetPhysicalCleanupSkipped: targetPhysicalCleanupSkipped,
    targetCleanupSkipReason: targetCleanupSkipReason,
    targetPhysicalExists: physicalDeleteResult == null ? false : physicalDeleteResult.physicalExists == true,
    targetPhysicalDeleted: physicalDeleteResult == null ? false : physicalDeleteResult.physicalDeleted == true
}
