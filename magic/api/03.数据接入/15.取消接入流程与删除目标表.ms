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
  "description" : "取消或重建数据接入；仅清理当前租户所选来源表关联的目标和流程。未关联但没有任务、目录及衍生表引用的同名目标，预检返回 targetUnlinked；实际删除须指定预检确认的目标表 ID 并提交 confirmUnlinkedTarget=true，且不清理当前来源表任务。Hive（含 MRS 与普通 Hive）仅清理接入流程和目标登记，不连接或删除物理表；非 Hive 执行删表，连接或删除异常时取消可清理任务并保留目标登记供重试。NiFi 清理失败保留流程记录。dryRun=true 只读预检；batchDryRun=true 时需同时提交 dryRun=true、deleteTargetTable=true 与最多 20 个 targets，服务端批量预取并逐 ID 校验租户及角色范围，只读预检不写业务数据；checkPhysicalExistence=true 用于物化重名检查，否则 Hive 跳过物理探查。单目标删除仍须再次授权。涉及 db_table_t、data_access_agg_task_t、db_datasource_t、data_access_field_mapping、data_access_task_monitor_snap_t、nifi_pipeline_t 等业务表。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：15.取消接入流程与删除目标表
// 路由：POST /ods/dataAggReset
// 业务说明：取消或重建数据接入。batchDryRun=true 只读预检最多 20 个 targets，批量读取并逐 ID 核验当前租户及角色权限；未关联且无人引用的登记目标须用明确 ID 和 confirmUnlinkedTarget 二次确认，且不清理来源任务。单目标删除重新授权。Hive 清理保留物理表；物化重名预检显式提交 checkPhysicalExistence=true。非 Hive 连接/删除异常和 NiFi 清理异常返回待重试状态并保留相关记录。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
/*
 * Rebuild recovery for a source table.
 * The operation is deliberately source-scoped so one table cannot remove another
 * table's target metadata or deployed pipeline.
 * Hive cleanup retains the physical table without opening a Hive connection.
 * Materialization explicitly requests read-only physical-name checks.
 */
import jsons
import cn.hutool.json.JSONUtil as poolJsons
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifiClient
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.TargetTableDeletionService as targetTableDeletionService
import java.util.Map
import java.util.List
import log
import tenantRuntime
import dataScope

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
// 接入清理永不执行 Hive DROP，也不依赖 Hive 可连通；物理表由运维手动处理。
var hiveTypeOf = (database) => {
    var type = text(first(database, ['dbType', 'db_type'])).trim().toLowerCase().replace('_', '').replace('-', '')
    return type == 'hive' || type == 'mrshive' || type == 'huaweihive' || type == 'huaweimrshive'
}
var dataSourceOf = (database, prefetchedConfig) => {
    var config = prefetchedConfig == null
        ? datasourceConnectionConfig('read', first(database, ['tid']), null, false)
        : prefetchedConfig
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
        catch (ignored) { throw new IllegalArgumentException('不支持的目标数据库类型，无法安全删除目标表') }
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
        throw new IllegalStateException('目标数据源连接信息不完整，无法删除目标物理表')
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

// The materialization dialog checks several proposed targets before it may
// delete any existing table. Resolve tenant rows once, authorize every ID as
// a group, and only then probe the bounded set of distinct physical databases.
// This branch is read-only. The established single-target delete path below
// still rechecks a target immediately before its irreversible operation.
if (body?.batchDryRun == true) {
    if (body?.dryRun != true || body?.deleteTargetTable != true) {
        exit 400, '批量预检只支持 dryRun=true 且 deleteTargetTable=true'
    }
    var submitted = body?.targets
    if (!(submitted instanceof List) || submitted.size() < 1 || submitted.size() > 20) {
        exit 400, '一次最多预检20个目标表'
    }
    var batchSourceId = text(body?.sourceTableId).trim()
    if (blank(batchSourceId) || batchSourceId.length() > 64) { exit 400, '来源表 ID 不合法' }
    var plans = []
    var requestedDbIds = []
    var requestedNames = []
    var explicitTableIds = []
    var seenTargets = []
    for (raw in submitted) {
        var selectedDbId = text(raw?.targetDbId).trim()
        var selectedName = text(raw?.targetTableName).trim()
        var selectedTableId = text(raw?.targetTableId).trim()
        if (blank(selectedDbId) || selectedDbId.length() > 64 || !safeTableName(selectedName)
            || (!blank(selectedTableId) && selectedTableId.length() > 64)) {
            exit 400, '批量预检包含不合法的目标数据源、表名或表 ID'
        }
        var targetKey = selectedDbId + '|' + selectedName.toUpperCase()
        if (seenTargets.contains(targetKey)) { exit 400, '批量预检包含重复目标表' }
        seenTargets.add(targetKey)
        plans.add({targetDbId: selectedDbId, targetTableName: selectedName,
            targetTableId: selectedTableId, confirmUnmanagedTarget: raw?.confirmUnmanagedTarget == true})
        if (!requestedDbIds.contains(selectedDbId)) { requestedDbIds.add(selectedDbId) }
        var foldedName = selectedName.toUpperCase()
        if (!requestedNames.contains(foldedName)) { requestedNames.add(foldedName) }
        if (!blank(selectedTableId) && !explicitTableIds.contains(selectedTableId)) {
            explicitTableIds.add(selectedTableId)
        }
    }

    var batchSource = db.table('db_table_t').where().eq('tid', batchSourceId)
        .eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (batchSource == null) { exit 404, '来源表不存在或不属于当前租户' }
    var batchSourceDbId = text(first(batchSource, ['datasourceId', 'datasource_id'])).trim()
    if (blank(batchSourceDbId)) { exit 409, '来源表缺少所属数据源' }
    var allDbIds = [batchSourceDbId]
    for (selectedDbId in requestedDbIds) {
        if (!allDbIds.contains(selectedDbId)) { allDbIds.add(selectedDbId) }
    }
    var databaseRows = db.table('db_datasource_t').where().eq('tenant_id', tenantId)
        .eq('is_del', 0).in('tid', allDbIds).select()
    var visibleDatabases = dataScope.visibleDataSourcesFor(
        'MENU:2081000000000000002', databaseRows == null ? [] : databaseRows)
    var databaseById = {}
    for (databaseRow in visibleDatabases) { databaseById[text(first(databaseRow, ['tid']))] = databaseRow }
    for (selectedDbId in allDbIds) {
        if (databaseById[selectedDbId] == null) {
            exit 403, '来源或目标数据源不存在，或不在当前角色的数据范围内'
        }
    }
    var sourceName = text(first(batchSource, ['tableName', 'table_name']))
    for (plan in plans) {
        if (plan.targetDbId == batchSourceDbId
            && plan.targetTableName.toUpperCase() == sourceName.trim().toUpperCase()) {
            exit 409, '目标数据源和表名指向来源表本身，已拒绝预检'
        }
        if (plan.targetTableId == batchSourceId) { exit 409, '目标表 ID 指向来源表本身' }
    }

    // The name query is bounded by 20 datasource IDs and 20 names. A capped
    // page detects ambiguous/stale metadata rather than loading a full table.
    var byNamePage = db.page("""
        select tid, datasource_id, table_name, source_table_id
          from db_table_t
         where tenant_id=#{tenantId} and is_del=0
           and datasource_id in
               <foreach collection="requestedDbIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
           and upper(table_name) in
               <foreach collection="requestedNames" item="name" open="(" close=")" separator=",">#{name}</foreach>
    """, 401, 0)
    if (byNamePage == null || byNamePage.total > 400) {
        exit 409, '目标表候选元数据过多，无法安全预检'
    }
    var candidateRows = byNamePage.list == null ? [] : byNamePage.list
    var explicitRows = explicitTableIds.size() == 0 ? [] : db.table('db_table_t').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', explicitTableIds).select()
    var explicitById = {}
    for (row in explicitRows) { explicitById[text(first(row, ['tid']))] = row }
    var resolvedPlans = []
    var resolvedTableIds = []
    for (plan in plans) {
        var matched = null
        if (!blank(plan.targetTableId)) {
            matched = explicitById[plan.targetTableId]
            if (matched == null) { exit 404, '目标表 ID 不存在或不属于当前租户' }
            if (text(first(matched, ['datasourceId', 'datasource_id'])) != plan.targetDbId
                || text(first(matched, ['tableName', 'table_name'])).trim().toUpperCase()
                    != plan.targetTableName.toUpperCase()) {
                exit 409, '目标表 ID 与目标数据源或表名不匹配'
            }
        } else {
            for (candidate in candidateRows) {
                if (text(first(candidate, ['datasourceId', 'datasource_id'])) == plan.targetDbId
                    && text(first(candidate, ['tableName', 'table_name'])).trim().toUpperCase()
                        == plan.targetTableName.toUpperCase()) {
                    if (matched != null) { exit 409, '目标数据源和表名匹配到多条元数据，请提供目标表 ID' }
                    matched = candidate
                }
            }
        }
        var resolvedId = matched == null ? '' : text(first(matched, ['tid']))
        if (resolvedId == batchSourceId) { exit 409, '目标表元数据指向来源表本身' }
        if (!blank(resolvedId) && !resolvedTableIds.contains(resolvedId)) {
            resolvedTableIds.add(resolvedId)
        }
        resolvedPlans.add({plan: plan, table: matched, targetTableId: resolvedId})
    }
    var linkedTasks = resolvedTableIds.size() == 0 ? [] : db.table('data_access_agg_task_t').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('target_table_id', resolvedTableIds).select()
    var taskByTableId = {}
    for (linkedTask in linkedTasks) {
        var linkedId = text(first(linkedTask, ['targetTableId', 'target_table_id']))
        if (text(first(linkedTask, ['sourceTableId', 'source_table_id'])) != batchSourceId) {
            exit 409, '目标表已有其他来源表的接入任务，不能删除或复用'
        }
        if (!blank(linkedId)) { taskByTableId[linkedId] = linkedTask }
    }
    var catalogReferences = resolvedTableIds.size() == 0 ? [] : db.table('da_prop_t').where()
        .in('prop_name', ['targetTableId', 'sourceTableId'])
        .eq('is_del', 0).in('prop_value', resolvedTableIds).select()
    var catalogReferencedIds = []
    for (catalogReference in catalogReferences) {
        catalogReferencedIds.add(text(first(catalogReference, ['propValue', 'prop_value'])))
    }
    var unlinkedByTargetId = {}
    var unlinkedTargetIds = []
    for (resolved in resolvedPlans) {
        var plan = resolved.plan
        var selectedTable = resolved.table
        if (selectedTable == null) {
            if (!plan.confirmUnmanagedTarget) {
                exit 409, '目标表未登记，请明确确认未登记目标的物理预检'
            }
        } else {
            var linkedSourceId = text(first(selectedTable, ['sourceTableId', 'source_table_id']))
            var linkedTask = taskByTableId[resolved.targetTableId]
            if (!blank(linkedSourceId) && linkedSourceId != batchSourceId) {
                exit 409, '目标表元数据已关联其他来源表，不能删除或复用'
            }
            var unlinkedTarget = blank(linkedSourceId) && linkedTask == null
            if (unlinkedTarget && catalogReferencedIds.contains(resolved.targetTableId)) {
                exit 409, '目标表仍被数据目录引用，不能删除或复用'
            }
            if (unlinkedTarget) { unlinkedTargetIds.add(resolved.targetTableId) }
            unlinkedByTargetId[resolved.targetTableId] = unlinkedTarget
            var taskDbId = text(first(linkedTask, ['targetDbId', 'target_db_id']))
            if (!blank(taskDbId) && taskDbId != plan.targetDbId) {
                exit 409, '目标表元数据与接入任务的数据源不一致'
            }
        }
    }
    if (unlinkedTargetIds.size() > 0) {
        var sourceTasksForTargets = db.table('data_access_agg_task_t').where()
            .eq('tenant_id', tenantId).eq('is_del', 0)
            .in('source_table_id', unlinkedTargetIds).select()
        var derivedTargets = db.table('db_table_t').where()
            .eq('tenant_id', tenantId).eq('is_del', 0)
            .in('source_table_id', unlinkedTargetIds).select()
        if ((sourceTasksForTargets != null && sourceTasksForTargets.size() > 0)
            || (derivedTargets != null && derivedTargets.size() > 0)) {
            exit 409, '已有任务或目标表使用该表作为来源，不能删除或复用'
        }
    }

    // pool_cfg is already present on the one prefetched datasource row. Never
    // call datasourceConnectionConfig('read') inside this target loop.
    var poolConfigOf = (databaseRow) => {
        var rawPool = first(databaseRow, ['poolCfg', 'pool_cfg'])
        var parsed = blank(rawPool) ? {} : (rawPool instanceof Map ? rawPool : poolJsons.parse(text(rawPool)))
        if (!(parsed instanceof Map)) { exit 500, '目标数据源 pool_cfg 不是有效 JSON 对象' }
        var values = {}
        values.putAll(parsed)
        var masterPairs = {
            dbType: ['dbType', 'db_type'], jdbcURL: ['jdbcURL', 'jdbcUrl', 'jdbc_url'],
            username: ['username'], password: ['password']
        }
        for (key in masterPairs.keySet()) {
            var masterValue = first(databaseRow, masterPairs[key])
            if (masterValue != null) { values[key] = masterValue }
        }
        return values
    }
    var namesByDatasource = {}
    for (resolved in resolvedPlans) {
        var selectedDbId = resolved.plan.targetDbId
        if (namesByDatasource[selectedDbId] == null) { namesByDatasource[selectedDbId] = [] }
        namesByDatasource[selectedDbId].add(resolved.plan.targetTableName)
    }
    var physicalByTarget = {}
    // This deadline is checked between physical datasource calls. JDBC query
    // timeout is configured separately; a hung driver may ignore both limits.
    var preflightStartedAt = new Date()
    var preflightDeadline = preflightStartedAt.getTime() + 110000
    for (selectedDbId in requestedDbIds) {
        var preflightNow = new Date()
        if (preflightNow.getTime() > preflightDeadline) { exit 504, '批量物理表预检超时，未修改任何目标' }
        var names = namesByDatasource[selectedDbId]
        var targetDatabase = databaseById[selectedDbId]
        var groupResults
        try {
            if (hiveTypeOf(targetDatabase) && body?.checkPhysicalExistence != true) {
                groupResults = []
                for (name in names) {
                    groupResults.add({databaseType: 'hive', physicalExists: null,
                        targetHivePhysicalRetained: true, physicalTargetTableName: name})
                }
            } else {
                var targetConfig = dataSourceOf(targetDatabase, poolConfigOf(targetDatabase))
                targetConfig.setConnectionTimeout(10)
                // 物化明确要求检查物理重名；接入删除不连接 Hive。
                groupResults = targetTableDeletionService.preflightBatch(targetConfig, names)
            }
        } catch (error) {
            var reason = text(error.message || error)
            if (body?.checkPhysicalExistence == true || reason.indexOf('不合法') >= 0
                || reason.indexOf('不一致') >= 0 || reason.indexOf('同名表') >= 0) {
                exit 400, '目标物理表检查失败，未修改任何目标；请检查目标库连接及表名后重试'
            }
            groupResults = []
            for (name in names) {
                groupResults.add({physicalExists: null, targetCleanupPending: true,
                    physicalTargetTableName: name})
            }
        }
        if (groupResults == null || groupResults.size() != names.size()) {
            exit 502, '目标数据源批量物理预检结果不完整，未修改任何目标'
        }
        var groupIndex = 0
        for (name in names) {
            var resultKey = selectedDbId + '|' + text(name).toUpperCase()
            physicalByTarget[resultKey] = groupResults[groupIndex]
            groupIndex = groupIndex + 1
        }
        var preflightCheckedAt = new Date()
        if (preflightCheckedAt.getTime() > preflightDeadline) { exit 504, '批量物理表预检超时，未修改任何目标' }
    }
    var preflightResults = []
    for (resolved in resolvedPlans) {
        var plan = resolved.plan
        var resultKey = plan.targetDbId + '|' + plan.targetTableName.toUpperCase()
        var preflight = physicalByTarget[resultKey]
        if (preflight == null) { exit 502, '目标表批量物理预检结果缺失，未修改任何目标' }
        preflightResults.add({targetDbId: plan.targetDbId, targetTableName: plan.targetTableName,
            targetTableId: resolved.targetTableId, targetMetadataMissing: resolved.table == null,
            targetUnlinked: unlinkedByTargetId[resolved.targetTableId] == true,
            targetPhysicalExists: preflight.physicalExists == true,
            targetPhysicalState: preflight.targetHivePhysicalRetained == true ? 'retained'
                : (preflight.targetCleanupPending == true ? 'unknown' : (preflight.physicalExists == true ? 'exists' : 'absent')),
            targetHivePhysicalRetained: preflight.targetHivePhysicalRetained == true,
            targetCleanupPending: preflight.targetCleanupPending == true,
            targetScope: text(preflight.targetScope), targetDatabaseType: text(preflight.databaseType),
            physicalTargetTableName: text(preflight.physicalTargetTableName),
            historicalScopeNormalized: preflight.historicalScopeNormalized == true,
            sideEffectsApplied: false})
    }
    return {batchDryRun: true, dryRun: true, sourceTableId: batchSourceId,
        results: preflightResults, sideEffectsApplied: false}
}

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
var requestedTargetTableId = targetTableId
var requestedTaskId = text(body?.taskId).trim()
// 独立任务只取消用户指定的任务/目标；历史单任务多目标画布按其真实sink关联匹配。
if (!blank(requestedTaskId)) {
    var selectedTasks = []
    for (task in tasks) { if (text(task.tid) == requestedTaskId) { selectedTasks.add(task) } }
    if (selectedTasks.size() != 1) { exit 403, '所选任务不存在、不属于当前租户或来源表' }
    tasks = selectedTasks
} else if (!blank(requestedTargetTableId)) {
    var pipelineIds = []
    for (task in tasks) {
        var pipelineId = text(first(task, ['pipelineId', 'pipeline_id']))
        if (!blank(pipelineId) && !pipelineIds.contains(pipelineId)) { pipelineIds.add(pipelineId) }
    }
    var pipelines = pipelineIds.size() == 0 ? [] : db.table('nifi_pipeline_t').columns('id', 'dsl_json').where()
        .eq('tenant_id', tenantId).in('id', pipelineIds).eq('is_del', 0).select()
    var matchingPipelineIds = []
    for (pipeline in pipelines) {
        var dsl = {}
        try { dsl = poolJsons.parseObj(text(first(pipeline, ['dslJson', 'dsl_json']))) } catch (ignored) {}
        for (node in (dsl.nodes == null ? [] : dsl.nodes)) {
            if (text(node.category).toLowerCase() == 'sink' && text(node.config?.targetTableId) == requestedTargetTableId) {
                matchingPipelineIds.add(text(pipeline.id)); break
            }
        }
    }
    var selectedTasks = []
    for (task in tasks) {
        if (text(first(task, ['targetTableId', 'target_table_id'])) == requestedTargetTableId
            || matchingPipelineIds.contains(text(first(task, ['pipelineId', 'pipeline_id'])))) { selectedTasks.add(task) }
    }
    tasks = selectedTasks
}
var targetDbId = text(body?.targetDbId || body?.targetDatasourceId || body?.target_db_id).trim()
var targetTableName = text(body?.targetTableName || body?.target_table_name).trim()
var targetSelectionMode = ''
var targetMetadataMissing = false
var targetUnlinked = false
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
        if (!blank(targetSourceTableId) && targetSourceTableId != sourceTableId) {
            exit 409, '目标表元数据已关联其他来源表，已拒绝删除'
        }
        targetUnlinked = blank(targetSourceTableId) && !targetChosenByTask
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
            if (!blank(matchedTargetSourceId) && matchedTargetSourceId != sourceTableId) {
                exit 409, '匹配到的目标表元数据已关联其他来源表，已拒绝删除'
            }
            targetUnlinked = blank(matchedTargetSourceId) && !targetChosenByTask
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
    if (targetTable != null) {
        // 汇聚表正在作为下一跳来源时保留物理表，须先取消下游任务。
        var downstreamTasks = db.table('data_access_agg_task_t').where()
            .eq('tenant_id', tenantId).eq('source_table_id', targetTableId).eq('is_del', 0).select()
        if (downstreamTasks.size() > 0) { exit 409, '目标表仍作为下游任务的来源，请先删除下游任务再删除目标表' }
        // A table without source_table_id can still belong to another active
        // task or catalog. Resolve those references before a physical probe.
        var activeTargetTasks = db.table('data_access_agg_task_t').where()
            .eq('tenant_id', tenantId).eq('target_table_id', targetTableId).eq('is_del', 0).select()
        for (activeTargetTask in activeTargetTasks) {
            if (text(first(activeTargetTask, ['sourceTableId', 'source_table_id'])) != sourceTableId) {
                exit 409, '目标表已有其他来源表的接入任务，已拒绝删除'
            }
        }
        if (targetUnlinked) {
            var catalogReferences = db.table('da_prop_t').where()
                .in('prop_name', ['targetTableId', 'sourceTableId'])
                .eq('prop_value', targetTableId).eq('is_del', 0).select()
            if (catalogReferences != null && catalogReferences.size() > 0) {
                exit 409, '目标表仍被数据目录引用，已拒绝删除'
            }
            var sourceTasksForTarget = db.table('data_access_agg_task_t').where()
                .eq('tenant_id', tenantId).eq('source_table_id', targetTableId).eq('is_del', 0).select()
            var derivedTargets = db.table('db_table_t').where()
                .eq('tenant_id', tenantId).eq('source_table_id', targetTableId).eq('is_del', 0).select()
            if ((sourceTasksForTarget != null && sourceTasksForTarget.size() > 0)
                || (derivedTargets != null && derivedTargets.size() > 0)) {
                exit 409, '已有任务或目标表使用该表作为来源，已拒绝删除'
            }
            if (!dryRun && (blank(requestedTargetTableId) || body?.confirmUnlinkedTarget != true)) {
                exit 409, '未关联目标表须先预检并指定目标表 ID，再明确确认删除'
            }
        }
    }
} else {
    // Cancelling a task never requires resolving or deleting a target asset.
    // Retain a target ID for response compatibility only when it is not the
    // source row itself and belongs to the active tenant. Prefetch all task
    // target rows once; a loop over tasks must not issue one SELECT per ID.
    var cancelCandidateIds = []
    if (!blank(targetTableId) && targetTableId != sourceTableId) { cancelCandidateIds.add(targetTableId) }
    for (task in (tasks == null ? [] : tasks)) {
        var candidateId = text(first(task, ['targetTableId', 'target_table_id']))
        if (!blank(candidateId) && candidateId != sourceTableId && !cancelCandidateIds.contains(candidateId)) {
            cancelCandidateIds.add(candidateId)
        }
    }
    var cancelTargetRows = cancelCandidateIds.size() == 0 ? [] : db.table('db_table_t').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', cancelCandidateIds).select()
    var cancelTargetById = {}
    for (candidate in cancelTargetRows) { cancelTargetById[text(first(candidate, ['tid']))] = candidate }
    for (candidateId in cancelCandidateIds) {
        if (cancelTargetById[candidateId] != null) {
            targetTable = cancelTargetById[candidateId]
            targetTableId = candidateId
            break
        }
    }
    if (targetTable != null) {
        targetDbId = text(first(targetTable, ['datasourceId', 'datasource_id']))
        targetTableName = text(first(targetTable, ['tableName', 'table_name']))
    }
}

// A dry run does not grant permission for the later destructive single-target
// call. Recheck the source and selected target datasource for that request.
var singleDatasourceIds = []
if (!blank(sourceDatasourceId)) { singleDatasourceIds.add(sourceDatasourceId) }
if (!blank(targetDbId) && !singleDatasourceIds.contains(targetDbId)) { singleDatasourceIds.add(targetDbId) }
if (singleDatasourceIds.size() == 0) { exit 409, '来源表缺少所属数据源' }
var singleDatasourceRows = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', singleDatasourceIds).select()
var visibleSingleDatasources = dataScope.visibleDataSourcesFor(
    'MENU:2081000000000000002', singleDatasourceRows == null ? [] : singleDatasourceRows)
var visibleSingleIds = []
for (visibleRow in visibleSingleDatasources) { visibleSingleIds.add(text(first(visibleRow, ['tid']))) }
for (selectedId in singleDatasourceIds) {
    if (!visibleSingleIds.contains(selectedId)) {
        exit 403, '来源或目标数据源不存在，或不在当前角色的数据范围内'
    }
}

// 预检只决定物理清理策略；连接异常允许取消任务，但保留目标登记用于恢复。
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
var targetHivePhysicalRetained = false
var targetCleanupPending = false
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
    var preflightDatasource = null
    for (databaseRow in singleDatasourceRows) {
        if (text(first(databaseRow, ['tid'])) == targetDbId) { preflightDatasource = databaseRow; break }
    }
    if (preflightDatasource == null) {
        exit 404, '目标数据源不存在或不属于当前租户，已保留流程和元数据'
    } else if (hiveTypeOf(preflightDatasource)) {
        // 包括 MRS 与普通 JDBC Hive。仅依据已授权主记录的真实类型，不接受客户端类型。
        preflightDatabaseType = 'hive'
        targetHivePhysicalRetained = true
        targetPhysicalCleanupSkipped = true
        targetCleanupSkipReason = 'hive_manual_cleanup_required'
    } else {
        try {
        preflightConfig = dataSourceOf(preflightDatasource)
        // Resolve against the entire selected datasource scope, not a 200-row
        // page.  The Java service also locks Oracle/OceanBase Oracle to its
        // configured schema and rejects a qualified name from another schema.
        var preflight = targetTableDeletionService.preflight(preflightConfig, physicalTargetTableName)
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
        if (missingDatasourceMessage(preflightMessage)) {
            targetPhysicalDatasourceMissing = true
            targetPhysicalCleanupSkipped = true
            targetCleanupSkipReason = 'target_physical_datasource_missing'
        } else if (preflightMessage.indexOf('不一致') < 0 || separator < 1) {
            if (preflightMessage.indexOf('不合法') >= 0 || preflightMessage.indexOf('同名表') >= 0
                || preflightMessage.indexOf('不支持') >= 0 || preflightMessage.indexOf('不一致') >= 0) { exit 409, '无法安全定位目标表，未执行删除' }
            // 连接/驱动/探查失败时允许取消接入流程；保留目标登记用于后续重试。
            targetCleanupPending = true
            targetPhysicalCleanupSkipped = true
            targetCleanupSkipReason = 'target_preflight_unavailable'
            log.warn('Target preflight unavailable; retaining target metadata, targetId={}', targetTableId)
        } else {
            physicalTargetTableName = targetTableName.substring(separator + 1)
            if (!safeTableName(physicalTargetTableName)) {
                exit 400, '目标表名不合法，已保留流程和元数据：' + targetTableName
            }
            try {
                var normalizedPreflight = targetTableDeletionService.preflight(preflightConfig, physicalTargetTableName)
                preflightPhysicalExists = normalizedPreflight.physicalExists == true
                preflightScope = text(normalizedPreflight.targetScope)
                preflightDatabaseType = text(normalizedPreflight.databaseType)
                historicalScopeNormalized = true
            } catch (normalizedError) {
                var normalizedMessage = text(normalizedError.message || normalizedError)
                if (missingDatasourceMessage(normalizedMessage)) {
                    targetPhysicalDatasourceMissing = true
                    targetPhysicalCleanupSkipped = true
                    targetCleanupSkipReason = 'target_physical_datasource_missing'
                } else {
                    if (normalizedMessage.indexOf('不合法') >= 0 || normalizedMessage.indexOf('不一致') >= 0
                        || normalizedMessage.indexOf('同名表') >= 0 || normalizedMessage.indexOf('不支持') >= 0) {
                        exit 409, '无法安全定位目标表，未执行删除'
                    }
                    targetCleanupPending = true
                    targetPhysicalCleanupSkipped = true
                    targetCleanupSkipReason = 'target_preflight_unavailable'
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
        taskCount: targetUnlinked || tasks == null ? 0 : tasks.size(),
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
        targetUnlinked: targetUnlinked,
        targetDatasourceMissing: targetDatasourceMissing,
        targetPhysicalDatasourceMissing: targetPhysicalDatasourceMissing,
        targetPhysicalCleanupSkipped: targetPhysicalCleanupSkipped,
        targetCleanupSkipReason: targetCleanupSkipReason,
        targetHivePhysicalRetained: targetHivePhysicalRetained,
        targetCleanupPending: targetCleanupPending,
        targetMetadataRetained: targetCleanupPending,
        message: targetHivePhysicalRetained ? 'Hive 物理表将保留，仅清理接入流程和目标登记；重新物化需修改表名或手动删除原表'
            : (targetCleanupPending ? '目标库暂时无法检查，将取消接入流程并保留目标表登记供重试' : ''),
        deleteTargetTableRequested: deleteTargetTable,
        targetTableDeleted: false,
        sideEffectsApplied: false
    }
}

// 先清理外部流程。清理失败的流程及目标登记保留为可重试状态，不能伪报完成。
var selectedPipelineIds = []
for (task in (targetUnlinked ? [] : (tasks == null ? [] : tasks))) {
    var selectedPipelineId = text(first(task, ['pipelineId', 'pipeline_id']))
    if (!blank(selectedPipelineId) && !selectedPipelineIds.contains(selectedPipelineId)) {
        selectedPipelineIds.add(selectedPipelineId)
    }
}
// 兼容旧任务未保存 process_group_id 的情况，一次读取本租户的流程登记。
var selectedPipelineRows = selectedPipelineIds.size() == 0 ? [] : db.table('nifi_pipeline_t').where()
    .eq('tenant_id', tenantId).in('id', selectedPipelineIds).eq('is_del', 0).select()
var processGroupByPipelineId = {}
for (pipeline in selectedPipelineRows) {
    processGroupByPipelineId[text(pipeline.id)] = text(first(pipeline, ['nifiProcessGroupId', 'nifi_process_group_id']))
}
var processGroupOf = (task) => {
    var direct = first(task, ['processGroupId', 'process_group_id'])
    return blank(direct) ? text(processGroupByPipelineId[text(first(task, ['pipelineId', 'pipeline_id']))]) : text(direct)
}
var processGroupCleanupPendingCount = 0
var cleanedProcessGroups = []
var pendingProcessGroups = []
for (task in (targetUnlinked ? [] : (tasks == null ? [] : tasks))) {
    var processGroupId = processGroupOf(task)
    if (!blank(processGroupId) && !cleanedProcessGroups.contains(processGroupId)) {
        cleanedProcessGroups.add(processGroupId)
        try { nifiClient.cleanupProcessGroup(processGroupId) } catch (error) {
            processGroupCleanupPendingCount = processGroupCleanupPendingCount + 1
            pendingProcessGroups.add(processGroupId)
            log.warn('NiFi cleanup pending; processGroupId={}', processGroupId)
        }
    }
}
if (deleteTargetTable && processGroupCleanupPendingCount > 0) {
    targetCleanupPending = true
    targetPhysicalCleanupSkipped = true
    targetCleanupSkipReason = 'nifi_cleanup_pending'
}
var physicalDeleteResult = null
if (deleteTargetTable && !targetPhysicalCleanupSkipped) {
    try {
        physicalDeleteResult = targetTableDeletionService.drop(preflightConfig, physicalTargetTableName)
    } catch (error) {
        var deleteMessage = text(error.message || error)
        if (missingObjectMessage(deleteMessage)) {
            targetPhysicalCleanupSkipped = true
            targetCleanupSkipReason = 'target_physical_missing'
        } else {
            targetCleanupPending = true
            targetPhysicalCleanupSkipped = true
            targetCleanupSkipReason = 'target_delete_pending'
            log.warn('Target drop unavailable; retaining target metadata, targetId={}', targetTableId)
        }
    }
}

var now = new Date()
var pipelineCount = 0
for (task in (targetUnlinked ? [] : (tasks == null ? [] : tasks))) {
    var taskId = text(first(task, ['tid']))
    var pipelineId = text(first(task, ['pipelineId', 'pipeline_id']))
    var processGroupId = processGroupOf(task)
    if (pendingProcessGroups.contains(processGroupId)) {
        db.table('data_access_agg_task_t').where().eq('tid', taskId).eq('tenant_id', tenantId).eq('is_del', 0)
            .update({ task_status: 0, updated_time: now })
        continue
    }
    db.table('data_access_field_mapping').where().eq('task_id', taskId).eq('is_del', 0)
        .update({ is_del: 1, updated_time: now })
    db.table('data_access_task_monitor_snap_t').where().eq('task_id', taskId).eq('is_del', 0)
        .update({ is_del: 1 })
    db.table('data_access_agg_task_t').where().eq('tid', taskId).eq('is_del', 0)
        .update({ is_del: 1, task_status: 0, updated_time: now })
    if (!blank(pipelineId)) {
        db.table('nifi_pipeline_t').where().eq('tenant_id', tenantId).eq('id', pipelineId).eq('is_del', 0)
            .update({ is_del: 1, updated_at: now.getTime() })
        pipelineCount = pipelineCount + 1
    }
}

if (deleteTargetTable && !targetCleanupPending) {
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
    if (!targetUnlinked) {
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
}

return {
    sourceTableId: sourceTableId,
    sourceDatasourceId: sourceDatasourceId,
    sourceTableName: sourceTableName,
    sourceTableSoftDeleted: false,
    taskCount: targetUnlinked || tasks == null ? 0 : tasks.size(),
    pipelineCount: pipelineCount,
    targetTableId: targetTable == null ? '' : text(first(targetTable, ['tid'])),
    resolvedTargetTableId: targetTable == null ? '' : text(first(targetTable, ['tid'])),
    targetDbId: targetDbId,
    resolvedTargetDatabaseId: targetDbId,
    targetTableDeleted: deleteTargetTable && !targetCleanupPending,
    targetTableName: targetTableName,
    resolvedTargetTableName: targetTableName,
    targetSelectionMode: targetSelectionMode,
    targetMetadataMissing: targetMetadataMissing,
    targetUnlinked: targetUnlinked,
    physicalTargetTableName: physicalTargetTableName,
    historicalScopeNormalized: historicalScopeNormalized,
    targetMetadataMissing: targetMetadataMissing,
    targetDatasourceMissing: targetDatasourceMissing,
    targetPhysicalDatasourceMissing: targetPhysicalDatasourceMissing,
    targetPhysicalCleanupSkipped: targetPhysicalCleanupSkipped,
    targetCleanupSkipReason: targetCleanupSkipReason,
    targetHivePhysicalRetained: targetHivePhysicalRetained,
    targetCleanupPending: targetCleanupPending,
    targetMetadataRetained: targetCleanupPending,
    processGroupCleanupPendingCount: processGroupCleanupPendingCount,
    message: (targetCleanupPending ? '已处理可清理的接入任务，目标登记已保留供重试，未确认删除目标物理表'
        : (targetHivePhysicalRetained ? '接入流程和目标登记已清理，Hive 物理表已保留；重新物化请修改表名或手动删除原表'
            : (deleteTargetTable ? '接入流程与所选目标表已清理' : '接入任务和关联流程已清理，目标表及数据已保留')))
        + (processGroupCleanupPendingCount > 0 ? '；部分 NiFi 流程清理待重试，已保留流程记录' : ''),
    targetPhysicalState: !deleteTargetTable || targetHivePhysicalRetained ? 'retained' : (targetCleanupPending ? 'unknown' : 'absent'),
    targetPhysicalExists: physicalDeleteResult == null ? null : physicalDeleteResult.physicalExists == true,
    targetPhysicalDeleted: physicalDeleteResult == null ? false : physicalDeleteResult.physicalDeleted == true
}
