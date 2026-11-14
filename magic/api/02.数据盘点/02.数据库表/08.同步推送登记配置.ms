{
  "properties": {},
  "id": "c78db2b6d3b04a2ba5d0854d8171a106",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "08.同步推送登记配置",
  "createTime": 1792650432292,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/push/sync",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"all\":true,\"nodeId\":\"当前租户推送节点ID\",\"preview\":true}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "同步当前可信租户内已完成登记的推送数据源配置。all=true、nodeId、preview=true 返回数据源/表清单、节点镜像状态、planHash 和调用地址；只读预览以服务间密钥调用节点一次 STATUS。确认传同一 planHash，清单变化返回409，成功返回逐表结果。原 dryRun 保持本地只读、不调用节点。上限100源/1000表/10000字段/1MiB；逐ID校验当前租户，不接受外部地址或凭据，不签发业务密钥。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import tableRead
import tableWrite
import cn.hutool.extra.spring.SpringUtil
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.util.LinkedHashMap
import java.util.Map
import cn.hutool.crypto.digest.DigestUtil
import java.lang.Math
import cn.hutool.http.HttpRequest
import cn.hutool.core.util.IdUtil

/**
 * POST /dst/database/push/sync 将当前登录租户内已登记的推送契约同步到海通节点。
 * datasourceId 指定单个数据源；all=true 需指定当前租户的 nodeId，支持 preview 及 planHash 校验。
 * dryRun 只生成本地结果，preview 最多查询一次节点状态。正式同步先保存租户契约快照，
 * 再向服务端已配置的目标发起请求，核对回执后返回同步结果。节点地址和凭据均由服务端管理。
 */
var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') {
    exit 401, '当前登录租户无效，请重新登录'
}
var audit = LoggerFactory.getLogger('data.push.registry.sync')
if (body == null || !(body instanceof Map)) {
    exit 400, '请求体必须为对象'
}
for (key in body.keySet()) {
    if (!['datasourceId', 'dryRun', 'nodeId', 'all', 'preview', 'planHash'].contains(key)) {
        exit 400, '请求包含未支持的同步参数'
    }
}
if (body.all != null && body.all != true && body.all != false) {
    exit 400, 'all必须为布尔值'
}
if (body.dryRun != null && body.dryRun != true && body.dryRun != false) {
    exit 400, 'dryRun必须为布尔值'
}
if (body.preview != null && body.preview != true && body.preview != false) {
    exit 400, 'preview必须为布尔值'
}
if ((body.preview == true || body.planHash != null) && body.all != true) {
    exit 400, '清单预览和校验仅用于全部同步'
}
if (body.planHash != null && (!(body.planHash instanceof String) || !body.planHash.matches('[a-f0-9]{64}'))) {
    exit 400, '同步清单标识不合法'
}

var str = value => value == null ? '' : (value + '').trim()
var nodeId = str(body.nodeId)
var pushNode = null
if (nodeId != '') {
    if (!nodeId.matches('[A-Za-z0-9][A-Za-z0-9_-]{0,63}')) {
        exit 400, '推送节点标识不合法'
    }
    // 目标地址只能从当前租户的有效节点读取，调用方无法覆盖。
    pushNode = db.table('push_node_t').where()
        .eq('tenant_id', tenantId).eq('tid', nodeId).eq('is_del', 0).selectOne()
    if (pushNode == null) {
        exit 403, '推送节点不存在或无权访问'
    }
    if (pushNode.enabled != 1 && pushNode.enabled != true) {
        exit 409, '推送节点已停用，请先配置并启用节点'
    }
}

var value = (row, camel, snake) => row[camel] == null ? row[snake] : row[camel]
var textColumn = (alias, field) => tableRead.column(alias, field, 'TEXT')
var numberColumn = (alias, field) => tableRead.column(alias, field, 'NUMBER')
var active = alias => tableRead.equal(numberColumn(alias, 'is_del'), 0)
var activeIncludingLegacy = alias => tableRead.any([active(alias), tableRead.isNull(numberColumn(alias, 'is_del'))])
var settingsBean = 'data-element.push-registry-com.linewell.dataelement.dataservice.push.DataPushRegistryProperties'
var recordNodeStatus = (status, sourceId) => {
    // 回执状态单独写回已选节点；批量同步的空来源标识会覆盖此前的单源状态。
    db.table('push_node_t').withBlank().where()
        .eq('tenant_id', tenantId).eq('tid', nodeId).eq('is_del', 0)
        .update({last_sync_time: new Date(), last_sync_status: status, last_sync_source_id: sourceId})
}

// 节点配置同步按当前租户读取登记范围，与数据源管理菜单的可见范围相互独立。
if (body.all == true) {
    if (pushNode == null) {
        exit 400, '同步全部配置必须选择推送节点'
    }
    if (body.datasourceId != null) {
        exit 400, '全部同步不能同时指定单个数据源'
    }
    var bm = new ObjectMapper()
    var sourcePage = db.table('db_datasource_t').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).orderBy('tid').page(1001, 0)
    if (sourcePage.total > 1000) {
        exit 413, '租户数据源超过1000项，请先缩小同步范围'
    }
    var pending = []
    var skipped = []
    // Magic 分页的空结果可能没有 list；按空集合处理，保留空计划和空文件型来源。
    for (row in sourcePage.list || []) {
        var cfg = {}
        try {
            cfg = bm.readValue(str(value(row, 'poolCfg', 'pool_cfg')) || '{}', Map)
        } catch (e) {
            continue
        }
        var mode = str(cfg.accessMode || cfg.access_mode || cfg.dataAccessMode || cfg.data_access_mode).toLowerCase()
        if (!['receive', 'push'].contains(mode)) {
            continue
        }
        var sourceName = str(value(row, 'dbName', 'db_name')) || str(row.tid)
        var reason = ''
        if (![1, 2].contains(value(row, 'assetStatus', 'asset_status')::int)) {
            reason = '数据源登记尚未完成'
        } else if (cfg.pushDelivery?.enabled == false || cfg.push_delivery?.enabled == false) {
            reason = '推送功能已停用'
        }
        if (reason != '') {
            skipped.add({datasourceId: row.tid, datasourceName: sourceName, reason: reason})
            continue
        }
        pending.add({row: row, config: cfg, name: sourceName})
    }
    if (pending.size() + skipped.size() > 100) {
        exit 413, '单次全部同步最多100个推送数据源，请调整配置规模'
    }

    // 先按可信数据源集合一次读取表、字段、已发布契约与所属系统，逐项组装期间不查库。
    var ids = pending.map(item => item.row.tid)
    var tables = []
    var columns = []
    var schemas = []
    var apps = []
    if (ids.size() > 0) {
        var tp = db.table('db_table_t').where()
            .eq('tenant_id', tenantId).in('datasource_id', ids).eq('asset_status', 2)
            .and(w => w.ne('table_type', 'FILE').or().isNull('table_type'))
            .and(w => w.eq('is_del', 0).or().isNull('is_del'))
            .orderBy('table_name').orderBy('tid').page(1001, 0)
        if (tp.total > 1000) {
            exit 413, '单次全部同步最多1000张表'
        }
        tables = tp.list || []
        var tableIds = tables.map(item => item.tid)
        if (tableIds.size() > 0) {
            var cp = db.table('db_table_column_t').where()
                .eq('tenant_id', tenantId).in('table_id', tableIds)
                .and(w => w.eq('is_del', 0).or().isNull('is_del'))
                .orderBy('ordinal_position').orderBy('tid').page(10001, 0)
            if (cp.total > 10000) {
                exit 413, '单次全部同步最多10000个字段'
            }
            columns = cp.list || []
            schemas = db.table('data_push_schema_t').where()
                .eq('tenant_id', tenantId).in('datasource_id', ids).in('table_id', tableIds).eq('is_del', 0).select()
        }
        var appIds = pending.map(item => value(item.row, 'appId', 'app_id'))
            .filter(id => id != null && str(id) != '').distinct()
        if (appIds.size() > 0) {
            apps = db.table('sym_application_t').where()
                .eq('tenant_id', tenantId).in('tid', appIds).eq('is_del', 0).select()
        }
    }
    var columnsByTable = {}
    var tablesBySource = {}
    var oldByTable = {}
    var appsById = {}
    for (column in columns) {
        var id = str(value(column, 'tableId', 'table_id'))
        if (columnsByTable[id] == null) {
            columnsByTable[id] = []
        }
        columnsByTable[id].add(column)
    }
    for (table in tables) {
        var id = str(value(table, 'datasourceId', 'datasource_id'))
        if (tablesBySource[id] == null) {
            tablesBySource[id] = []
        }
        tablesBySource[id].add(table)
    }
    for (schema in schemas) {
        var schemaKey = str(value(schema, 'datasourceId', 'datasource_id')) + ':' + str(value(schema, 'tableId', 'table_id'))
        oldByTable[schemaKey] = schema
    }
    for (app in apps) {
        appsById[str(app.tid)] = str(value(app, 'appName', 'app_name'))
    }
    var snapshots = []
    var publications = []
    var sourceIds = []
    var totalTables = 0
    var totalColumns = 0
    for (item in pending) {
        var id = str(item.row.tid)
        var sourceTables = tablesBySource[id] || []
        var normalized = []
        var sourcePublications = []
        var count = 0
        var reason = ''
        if (sourceTables.size() == 0 && str(item.config.pushDataType).toUpperCase() != 'UNSTRUCTURED') {
            reason = '尚无已完成登记的数据表'
        }
        if (sourceTables.size() > 100) {
            reason = '数据表超过单数据源100张上限'
        }
        var seenTables = []
        for (table in sourceTables) {
            var tableId = str(table.tid)
            var tableName = str(value(table, 'tableName', 'table_name'))
            if (!tableName.matches('[A-Za-z0-9_.-]{1,200}') || seenTables.contains(tableName.toLowerCase())) {
                reason = '数据表名称不合法或重复'
            }
            seenTables.add(tableName.toLowerCase())
            var fields = columnsByTable[tableId] || []
            if (fields.size() == 0) {
                reason = '存在已登记但未配置有效字段的数据表'
            }
            var normalizedFields = []
            var canonical = []
            var attachmentPolicies = []
            var attachmentByName = {}
            var governanceRaw = value(table, 'fieldGovernanceConfig', 'field_governance_config')
            var governance = governanceRaw instanceof Map
                ? governanceRaw : (str(governanceRaw) == '' ? {} : bm.readValue(str(governanceRaw), Map))
            for (attachment in governance.attachmentFields || []) {
                attachmentByName[str(attachment.columnName)] = attachment
            }
            var seenFields = []
            for (field in fields) {
                var fieldName = str(value(field, 'columnName', 'column_name'))
                var type = str(value(field, 'columnType', 'column_type')) || str(value(field, 'dataType', 'data_type'))
                var ordinal = (value(field, 'ordinalPosition', 'ordinal_position') || 0)::int
                var required = (field.nullable == null ? 1 : field.nullable)::int == 0
                    || (value(field, 'primaryKey', 'primary_key') || 0)::int == 1
                if (!fieldName.matches('[A-Za-z0-9_.-]{1,200}') || seenFields.contains(fieldName.toLowerCase())
                    || ordinal < 1 || ordinal > 10000 || type.length() > 100) {
                    reason = '字段名称、类型或顺序不符合推送契约要求'
                }
                seenFields.add(fieldName.toLowerCase())
                var columnComment = str(value(field, 'columnComment', 'column_comment'))
                normalizedFields.add({
                    columnName: fieldName, columnComment: columnComment.substring(0, Math.min(columnComment.length(), 255)),
                    dataType: type, ordinal: ordinal, required: required, enabled: true
                })
                // 规范字段顺序与单源发布一致，保证同一字段定义得到相同指纹。
                var canonicalField = {name: fieldName, type: type, required: required, ordinal: ordinal}
                var attachment = attachmentByName[fieldName]
                if (attachment != null) {
                    var policy = {
                        columnName: fieldName, maxFileBytes: attachment.maxFileBytes::long,
                        allowedExtensions: attachment.allowedExtensions
                    }
                    canonicalField.attachment = policy
                    attachmentPolicies.add(policy)
                }
                canonical.add(canonicalField)
            }
            var hash = DigestUtil.sha256Hex(bm.writeValueAsString(canonical))
            var old = oldByTable[id + ':' + tableId]
            var unchanged = old != null && str(value(old, 'schemaHash', 'schema_hash')) == hash
            var version = old == null ? 1L : value(old, 'schemaVersion', 'schema_version')::long + (unchanged ? 0L : 1L)
            var schemaId = old == null ? IdUtil.getSnowflakeNextIdStr() : str(old.tid)
            var delivery = new LinkedHashMap(item.config.pushDelivery || {})
            delivery.attachmentFields = attachmentPolicies
            sourcePublications.add({
                old: old, schemaId: schemaId, tableId: tableId, tableName: tableName, version: version,
                hash: hash, sourceId: id, delivery: bm.writeValueAsString(delivery), fields: canonical
            })
            var comment = str(value(table, 'tableNameCn', 'table_name_cn'))
                || str(value(table, 'tableComment', 'table_comment')) || tableName
            normalized.add({
                tableId: tableId, tableName: tableName, tableComment: comment.substring(0, Math.min(comment.length(), 255)),
                schemaVersion: version + '', enabled: true, columns: normalizedFields
            })
            count += fields.size()
        }
        if (reason != '') {
            skipped.add({datasourceId: id, datasourceName: item.name, reason: reason})
            continue
        }
        var appId = str(value(item.row, 'appId', 'app_id'))
        snapshots.add({
            datasourceId: id, datasourceName: item.name, sourceSystemId: appId, sourceSystemName: appsById[appId] || '',
            contractVersion: new Date().getTime() + '', enabled: true, clientIds: ['push-' + id], tables: normalized
        })
        publications.addAll(sourcePublications)
        sourceIds.add(id)
        totalTables += normalized.size()
        totalColumns += count
    }
    var targetUrl = str(pushNode.baseUrl || pushNode.base_url).replaceAll('/+$', '') + '/api/v1/data-push'
    // 指纹包含目标及登记定义，省略每次生成的时间戳，让确认时能够识别实际配置变化。
    var planSources = snapshots.map(snapshot => {
        datasourceId: snapshot.datasourceId, datasourceName: snapshot.datasourceName,
        sourceSystemId: snapshot.sourceSystemId, sourceSystemName: snapshot.sourceSystemName,
        enabled: snapshot.enabled, clientIds: snapshot.clientIds, tables: snapshot.tables
    })
    var planHash = DigestUtil.sha256Hex(bm.writeValueAsString({
        tenantId: tenantId, nodeId: nodeId, targetUrl: targetUrl, datasources: planSources
    }))
    if (body.planHash != null && str(body.planHash) != planHash) {
        exit 409, '同步清单已变化，请刷新清单后重新确认'
    }
    var rows = []
    for (snapshot in snapshots) {
        for (table in snapshot.tables) {
            var definition = [table.tableId, table.tableName, table.tableComment, table.schemaVersion, table.enabled,
                table.columns.map(field => [
                    field.columnName, field.columnComment, field.dataType, field.ordinal, field.required, field.enabled
                ])]
            rows.add({
                datasourceId: snapshot.datasourceId, datasourceName: snapshot.datasourceName,
                tableId: table.tableId, tableName: table.tableName, tableComment: table.tableComment,
                columnCount: table.columns.size(), schemaVersion: table.schemaVersion,
                definitionHash: DigestUtil.sha256Hex(bm.writeValueAsString(definition)),
                syncStatus: 'UNKNOWN', syncedAt: null
            })
        }
    }
    var result = {
        synced: true, all: true, nodeId: nodeId, datasourceCount: snapshots.size(), tableCount: totalTables,
        columnCount: totalColumns, skipped: skipped,
        sources: snapshots.map(snapshot => {
            datasourceId: snapshot.datasourceId, datasourceName: snapshot.datasourceName, tableCount: snapshot.tables.size()
        }),
        rows: rows, planHash: planHash, requestPath: '/dst/database/push/sync', targetUrl: targetUrl
    }
    var envelope = {deliveryType: 'REGISTRY_SYNC', datasources: snapshots}
    var encoded = bm.writeValueAsString(envelope)
    if (bm.writeValueAsBytes(envelope).length > 1048576) {
        exit 413, '全部推送配置超过1MiB，请调整配置规模'
    }
    if (body.preview == true) {
        result.synced = false
        result.valid = true
        result.dryRun = true
        var statusResponse = null
        try {
            // 状态预览只请求一次，并拒绝节点返回清单之外或重复的资产。
            if (rows.size() > 0) {
                var statusSettings = SpringUtil.getBean(settingsBean)
                if (statusSettings.getStandaloneSyncKey() == '') {
                    throw new IllegalStateException('missing server configuration')
                }
                var statusBody = bm.writeValueAsString({
                    deliveryType: 'REGISTRY_SYNC', registryAction: 'STATUS',
                    tableRefs: rows.map(row => {datasourceId: row.datasourceId, tableName: row.tableName})
                })
                statusResponse = HttpRequest.post(targetUrl).timeout(10000).setFollowRedirects(false)
                    .header('Content-Type', 'application/json')
                    .header('X-Standalone-Registry-Key', statusSettings.getStandaloneSyncKey())
                    .body(statusBody).execute()
                if (statusResponse.getStatus() != 200) {
                    throw new IllegalStateException('status unavailable')
                }
                var status = bm.readValue(statusResponse.body(), Map)
                if (status.code != 'REGISTRY_STATUS' || status.readOnly != true
                    || status.tables == null || status.tables.size() > rows.size()) {
                    throw new IllegalStateException('status receipt mismatch')
                }
                var wanted = rows.map(row => str(row.datasourceId) + ':' + str(row.tableName))
                var mirrors = {}
                for (mirror in status.tables) {
                    var key = str(mirror.datasourceId) + ':' + str(mirror.tableName)
                    if (!wanted.contains(key) || mirrors[key] != null) {
                        throw new IllegalStateException('foreign mirror')
                    }
                    mirrors[key] = mirror
                }
                for (row in rows) {
                    var mirror = mirrors[str(row.datasourceId) + ':' + str(row.tableName)]
                    var matches = mirror != null && mirror.active == true && mirror.definitionHash == row.definitionHash
                    row.syncStatus = mirror == null ? 'NEW' : (matches ? 'SYNCED' : 'CHANGED')
                    row.syncedAt = mirror == null ? null : mirror.syncedAt
                }
            }
            result.statusConfirmed = true
        } catch (e) {
            result.statusConfirmed = false
            result.statusMessage = '节点暂未返回同步记录，本次仍将同步清单中的全部有效表'
        } finally {
            if (statusResponse != null) {
                statusResponse.close()
            }
        }
        return result
    }
    if (body.dryRun == true) {
        result.synced = false
        result.valid = true
        result.dryRun = true
        return result
    }
    if (snapshots.size() == 0) {
        return result
    }
    var settings = SpringUtil.getBean(settingsBean)
    if (settings.getStandaloneSyncKey() == '') {
        exit 503, '服务端尚未配置同步密钥'
    }
    var schemaIds = publications.map(pub => pub.schemaId)
    if (schemaIds.size() > 0) {
        // 已停用字段一并预取，重启登记时沿用历史字段 ID；全部写入共用一个租户事务。
        db.transaction(() => {
            var existingFields = db.table('data_push_schema_field_t').columns(['tid', 'schema_id', 'field_name']).where()
                .eq('tenant_id', tenantId).in('schema_id', schemaIds).select()
            var fieldsByKey = {}
            for (field in existingFields) {
                var fieldKey = str(value(field, 'schemaId', 'schema_id')) + ':' + str(value(field, 'fieldName', 'field_name'))
                fieldsByKey[fieldKey] = field
            }

            var now = new Date()
            var newSchemas = []
            var changedSchemas = []
            var expectedSchemas = []
            var newFields = []
            var changedFields = []
            for (pub in publications) {
                var record = {
                    tid: pub.schemaId, datasource_id: pub.sourceId, table_id: pub.tableId,
                    table_name: pub.tableName, schema_version: pub.version, schema_hash: pub.hash,
                    status: 'PUBLISHED', delivery_config: pub.delivery, updated_time: now
                }
                if (pub.old == null) {
                    record.tenant_id = tenantId
                    record.is_del = 0
                    record.published_time = now
                    newSchemas.add(record)
                } else {
                    changedSchemas.add(record)
                    expectedSchemas.add({
                        tid: pub.schemaId, datasource_id: pub.sourceId, table_id: pub.tableId,
                        schema_version: value(pub.old, 'schemaVersion', 'schema_version'),
                        schema_hash: value(pub.old, 'schemaHash', 'schema_hash'), published_time: now
                    })
                }
                for (field in pub.fields) {
                    var old = fieldsByKey[pub.schemaId + ':' + field.name]
                    var row = {
                        tid: old == null ? IdUtil.getSnowflakeNextIdStr() : old.tid,
                        schema_id: pub.schemaId, field_name: field.name, data_type: field.type,
                        required_flag: field.required ? 1 : 0, ordinal_position: field.ordinal, is_del: 0
                    }
                    if (old == null) {
                        row.tenant_id = tenantId
                        newFields.add(row)
                    } else {
                        changedFields.add(row)
                    }
                }
            }

            // 按关联键写入每批契约，任一记录失效即回滚；批次内不补查单条记录。
            if (newSchemas.size() > 0) {
                db.table('data_push_schema_t').batchInsert(newSchemas, 500)
            }
            if (changedSchemas.size() > 0) {
                // 以探查时的版本与指纹获取写锁，避免等待并发发布后覆盖其新版本。
                var guarded = tableWrite.batchUpdate('data_push_schema_t', tenantId, ['published_time'],
                    ['tid', 'datasource_id', 'table_id', 'schema_version', 'schema_hash'], {is_del: 0}, expectedSchemas)
                if (guarded != expectedSchemas.size()) {
                    exit 409, '已发布表契约发生变化，请刷新同步清单后重试'
                }
                var changed = tableWrite.batchUpdate('data_push_schema_t', tenantId,
                    ['table_name', 'schema_version', 'schema_hash', 'status', 'delivery_config', 'updated_time'],
                    ['tid', 'datasource_id', 'table_id'], {is_del: 0}, changedSchemas)
                if (changed != changedSchemas.size()) {
                    exit 409, '已发布表契约发生变化，请刷新同步清单后重试'
                }
            }
            // 先停用旧字段，再批量恢复当前版本中仍存在的字段；保留被移除字段的历史 ID。
            db.table('data_push_schema_field_t').where()
                .eq('tenant_id', tenantId).in('schema_id', schemaIds).update({is_del: 1})
            if (newFields.size() > 0) {
                db.table('data_push_schema_field_t').batchInsert(newFields, 500)
            }
            if (changedFields.size() > 0) {
                // 字段上限为 10000，按有界 JDBC 批次提交，所有分块仍在同一事务内。
                var offset = 0
                while (offset < changedFields.size()) {
                    var chunk = changedFields.subList(offset, Math.min(offset + 500, changedFields.size()))
                    var changed = tableWrite.batchUpdate('data_push_schema_field_t', tenantId,
                        ['data_type', 'required_flag', 'ordinal_position', 'is_del'],
                        ['tid', 'schema_id', 'field_name'], {}, chunk)
                    if (changed != chunk.size()) {
                        exit 409, '已发布字段契约发生变化，请刷新同步清单后重试'
                    }
                    offset += 500
                }
            }
        })
    }
    // 契约提交后再联系节点。远程失败保留本地已发布版本，供用同一清单重试。
    var response = null
    try {
        response = HttpRequest.post(targetUrl).timeout(30000).setFollowRedirects(false)
            .header('Content-Type', 'application/json')
            .header('X-Standalone-Registry-Key', settings.getStandaloneSyncKey())
            .header('Idempotency-Key', 'node-batch-' + nodeId + '-' + IdUtil.fastSimpleUUID()).body(encoded).execute()
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new IllegalStateException('节点未确认保存')
        }
        var received = bm.readValue(response.body(), Map)
        if (received.datasourceIds == null || received.datasourceIds.size() != sourceIds.size()
            || !sourceIds.containsAll(received.datasourceIds) || !received.datasourceIds.containsAll(sourceIds)
            || received.tableCount != totalTables || received.columnCount != totalColumns) {
            throw new IllegalStateException('节点返回的配置范围不匹配')
        }
        recordNodeStatus('SUCCESS', null)
    } catch (e) {
        recordNodeStatus('FAILED', null)
        audit.warn('批量推送登记配置同步未完成，nodeId={}，errorClass={}', nodeId, e.getClass().getName())
        exit 503, '全部配置未能确认保存到海通，请检查节点服务后重试'
    } finally {
        if (response != null) {
            response.close()
        }
    }
    for (row in rows) {
        row.syncStatus = 'SUCCESS'
        row.syncedAt = new Date().getTime()
    }
    return result
}

var datasourceId = str(body.datasourceId)
if (!datasourceId.matches('[A-Za-z0-9][A-Za-z0-9_-]{0,63}')) {
    exit 400, 'datasourceId格式不正确'
}
// 数据源与应用关联统一约束在当前租户；应用缺失时沿用空名称。
var sourceQuery = tableRead.from('db_datasource_t', 'd', tenantId)
sourceQuery.leftJoin('sym_application_t', 'a', 'tid', 'app_id', [active('a')])
sourceQuery.where(tableRead.equal(textColumn('d', 'tid'), datasourceId))
sourceQuery.where(active('d'))
var sources = sourceQuery.grouped({
    tid: textColumn('d', 'tid'), assetStatus: numberColumn('d', 'asset_status'),
    poolCfg: tableRead.column('d', 'pool_cfg', 'LOB'), appId: textColumn('d', 'app_id'),
    sourceSystemName: textColumn('a', 'app_name')
}, [])
if (sources.size() != 1) {
    exit 403, '数据源不存在或已删除'
}
var source = sources[0]
var mapper = new ObjectMapper()
var config = str(source.poolCfg) == '' ? {} : mapper.readValue(source.poolCfg + '', Map)
var mode = str(config.accessMode || config.access_mode || config.dataAccessMode || config.data_access_mode).toLowerCase()
if (!['receive', 'push'].contains(mode)) {
    exit 422, '仅支持接入方式为数据推送的数据源'
}
var sourceStatus = source.assetStatus
if (sourceStatus == null || ![1, 2].contains(sourceStatus::int)) {
    exit 409, '请先结束数据源登记并完成需要推送的表登记'
}
if (config.pushDelivery?.enabled == false || config.push_delivery?.enabled == false) {
    exit 409, '该数据源的推送功能已停用'
}

// 一次聚合获取规模；不按表读取字段，也不接受客户端省略需要同步的表。
var countQuery = tableRead.from('db_table_t', 't', tenantId)
countQuery.leftJoin('db_table_column_t', 'c', 'table_id', 'tid', [activeIncludingLegacy('c')])
countQuery.where(tableRead.equal(textColumn('t', 'datasource_id'), datasourceId))
countQuery.where(tableRead.equal(numberColumn('t', 'asset_status'), 2))
countQuery.where(activeIncludingLegacy('t'))
var tableKey = textColumn('t', 'tid')
var hasColumn = tableRead.not(tableRead.isNull(textColumn('c', 'tid')))
var counts = countQuery.grouped({
    tableCount: tableRead.countDistinct(tableKey),
    columnCount: tableRead.countIf(hasColumn),
    tablesWithColumns: tableRead.countDistinct(tableRead.when(hasColumn, tableKey, null))
}, [])[0]
var tableCount = counts.tableCount::int
var columnCount = counts.columnCount::int
if (tableCount == 0 || columnCount == 0) {
    exit 409, '数据源尚无已完成登记且含字段的数据表'
}
if (counts.tablesWithColumns::int != tableCount) {
    exit 409, '存在已登记但没有有效字段的数据表，请补齐后同步'
}
if (tableCount > 100 || columnCount > 10000) {
    exit 413, '单次配置同步最多100张已登记表和10000个字段'
}
var clientId = 'push-' + datasourceId
if (body.dryRun == true) {
    return {
        valid: true, dryRun: true, nodeId: nodeId, datasourceId: datasourceId,
        clientId: clientId, tableCount: tableCount, columnCount: columnCount
    }
}

var settings = SpringUtil.getBean(settingsBean)
if ((pushNode == null && settings.getRelayBaseUrl() == '') || settings.getStandaloneSyncKey() == '') {
    exit 503, '海通推送配置同步地址或服务间密钥尚未配置'
}
var snapshot = null
try {
    // 单源发布与现有接收接口共用契约构造，保留文件表排除及原有字段规则。
    var schemas = SpringUtil.getBean('dataPushSchemaService')
    snapshot = schemas.standaloneSnapshot(tenantId, datasourceId, clientId)
} catch (e) {
    audit.warn('推送契约准备未完成，datasourceId={}，errorClass={}', datasourceId, e.getClass().getName())
    exit 409, '推送契约未能生成，请核对已登记表字段后重试'
}
snapshot.sourceSystemId = (source.appId || '') + ''
snapshot.sourceSystemName = (source.sourceSystemName || '') + ''
var envelope = {deliveryType: 'REGISTRY_SYNC', datasource: snapshot}
if (mapper.writeValueAsBytes(envelope).length > 1048576) {
    exit 413, '推送配置超过1MiB，请精简表字段说明后重试'
}
try {
    if (pushNode == null) {
        var relay = SpringUtil.getBean('haitongStandaloneRegistryClient')
        relay.synchronize(datasourceId, snapshot)
    } else {
        // 节点由当前租户配置指定，回执必须确认同一个数据源。
        var response = null
        try {
            var target = (pushNode.baseUrl || pushNode.base_url) + '/api/v1/data-push'
            response = HttpRequest.post(target).timeout(10000).setFollowRedirects(false)
                .header('Content-Type', 'application/json')
                .header('X-Standalone-Registry-Key', settings.getStandaloneSyncKey())
                .header('Idempotency-Key', 'node-registry-' + nodeId + '-' + datasourceId + '-' + IdUtil.fastSimpleUUID())
                .body(mapper.writeValueAsString(envelope)).execute()
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException('推送节点未确认保存')
            }
            var received = mapper.readValue(response.body(), Map)
            if ((received.datasourceId + '') != datasourceId) {
                throw new IllegalStateException('推送节点返回的数据源不匹配')
            }
        } finally {
            if (response != null) {
                response.close()
            }
        }
        recordNodeStatus('SUCCESS', datasourceId)
    }
} catch (e) {
    if (pushNode != null) {
        recordNodeStatus('FAILED', datasourceId)
    }
    // 不把服务间地址、连接配置、响应正文或凭据写入响应及日志。
    audit.warn('推送登记配置同步未完成，datasourceId={}，errorClass={}', datasourceId, e.getClass().getName())
    exit 503, '推送配置未能确认保存到海通，请稍后使用同一数据源重试'
}
return {
    synced: true, nodeId: nodeId, datasourceId: datasourceId, clientId: clientId,
    tableCount: tableCount, columnCount: columnCount, target: 'haitong'
}
