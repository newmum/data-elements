{
  "properties": {},
  "id": "datapd_save_update_datasource_01",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "04.保存数据源",
  "createTime": null,
  "updateTime": 1790641057850,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"tid\":\"4b5d9992d89744b9b514f8905dc27998\",\"propList\":{\"dbName\":\"出入境人员动管系统数据库\",\"dbType\":\"mysql\",\"appId\":\"2e83a7abf33140c7a3df27d0c79e98c\"}}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "保存数据源主记录、应用关系和 db_datasource_t.pool_cfg 连接配置，并同步 Elasticsearch；不使用 da_prop_t 动态属性；使用请求参数 tid、propList、dryRun、deferIndexRefresh。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.保存数据源
// 路由：POST /dst/database/saveOrUpdate
// 业务说明：保存数据源主记录、应用关系和 db_datasource_t.pool_cfg 连接配置，并同步 Elasticsearch；不使用 da_prop_t 动态属性；使用请求参数 tid、propList、dryRun、deferIndexRefresh。
import '@post:/dst/maintenance/refresh' as refreshAssetIndex
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import tenantRuntime
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import cn.hutool.core.util.IdUtil
import cn.hutool.json.JSONUtil
import java.util.Map
import jsons
import log

/*
 * 数据源 04：新增或修改 db_datasource_t。
 *
 * 保存接口只写数据源主表和应用关系，并同步 ES 搜索投影；不连接物理库、不执行元数据探查。
 * 连接参数统一保存到 db_datasource_t.pool_cfg JSON 对象。第五步完成登记使用 completeOnly=true，只更新 asset_status，不触碰连接信息。
 */
var text = (value) => value == null ? null : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var nowTime = new Date()
var tenantId = tenantRuntime.id()

// 解析请求体并为可选参数设置默认值。
var tid = blank(body == null ? null : body.tid) ? IdUtil.getSnowflakeNextIdStr() : body.tid
var props = body == null || body.propList == null ? {} : body.propList
var first = (row, keys) => {
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

if (blank(tenantId)) {
    exit 400, '无法识别当前租户，请重新登录后再保存数据源'
}
// 只在尝试完成登记时读取表配置。普通第一步保存与第二至四步“下一步”不做完成校验。
// asset_status=2 是可用元数据，不能替代第三、四步显式保存的 registrationSavedPhase。
var completionReview = null
var requestedStatus = first(props, ['assetStatus', 'asset_status'])
if (props.completeOnly == true || text(requestedStatus) == '2') {
    var existed = db.table('db_datasource_t').where().eq('tid', tid).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (existed == null) {
        exit 404, '数据源不存在或不属于当前租户'
    }
    var completionTables = db.table('db_table_t').where().eq('datasource_id', tid).eq('tenant_id', tenantId).eq('is_del', 0).select()
    var unannotatedTableCount = 0
    var deferredTableCount = 0
    var pendingDictionaryCount = 0
    var pendingBusinessCount = 0
    var pendingLogCount = 0
    var completionIssues = []
    for (completionTable in completionTables) {
        var typeValue = first(completionTable, ['businessType', 'business_type'])
        var businessTypeText = blank(typeValue) ? '' : text(typeValue).trim()
        var annotatedValue = first(completionTable, ['annotated'])
        var annotatedText = blank(annotatedValue) ? '' : text(annotatedValue).trim().toLowerCase()
        var annotated = annotatedText == '1' || annotatedText == 'true' || annotatedText == 'yes'
        var tableName = first(completionTable, ['tableName', 'table_name_en', 'table_name'])
        var tableId = first(completionTable, ['tid'])
        if (!annotated || !['字典表', '业务表', '日志表', '过程表', '备份表', '不确定', '暂不处理'].contains(businessTypeText)) {
            unannotatedTableCount = unannotatedTableCount + 1
            completionIssues.add({ tableId: tableId, tableName: tableName, step: 2, message: '尚未完成标注' })
            continue
        }
        if (['不确定', '暂不处理'].contains(businessTypeText)) {
            deferredTableCount = deferredTableCount + 1
            continue
        }
        if (!['字典表', '业务表', '日志表'].contains(businessTypeText)) {
            continue
        }
        var configValue = first(completionTable, ['fieldGovernanceConfig', 'field_governance_config'])
        var config = null
        try {
            config = configValue instanceof Map ? configValue : blank(configValue) ? null : JSONUtil.parseObj(text(configValue))
        } catch (parseError) {
            // 无效 JSON 同未保存配置一样阻断完成登记，不使用历史资产状态兜底。
            config = null
        }
        var expectedPhase = businessTypeText == '字典表' ? 'dictionary' : 'business'
        var savedPhase = first(config, ['registrationSavedPhase'])
        var savedType = first(config, ['businessType'])
        if (text(savedPhase) != expectedPhase || (!blank(savedType) && text(savedType) != businessTypeText)) {
            if (businessTypeText == '字典表') {
                pendingDictionaryCount = pendingDictionaryCount + 1
            } else if (businessTypeText == '业务表') {
                pendingBusinessCount = pendingBusinessCount + 1
            } else {
                pendingLogCount = pendingLogCount + 1
            }
            completionIssues.add({ tableId: tableId, tableName: tableName, step: businessTypeText == '字典表' ? 3 : 4, message: '尚未完成' + businessTypeText + '登记' })
        }
    }
    var tableReviewReady = completionTables.size() > 0 && completionIssues.size() == 0
    completionReview = {
        tid: tid, completionEligible: tableReviewReady, tableReviewReady: tableReviewReady,
        tableCount: completionTables.size(), unannotatedTableCount: unannotatedTableCount,
        deferredTableCount: deferredTableCount, pendingDictionaryCount: pendingDictionaryCount,
        pendingBusinessCount: pendingBusinessCount, pendingLogCount: pendingLogCount, issues: completionIssues
    }
    // dryRun 仅报告真实校验结果，不修改 MySQL / ES，也不伪装成已完成。
    if (props.dryRun == true || body?.dryRun == true) {
        completionReview.dryRun = true
        completionReview.assetStatus = first(existed, ['assetStatus', 'asset_status'])
        return completionReview
    }
    if (!tableReviewReady) {
        var reasons = []
        if (completionTables.size() == 0) { reasons.add('尚未采集或登记数据表') }
        if (unannotatedTableCount > 0) { reasons.add(unannotatedTableCount + ' 张表未完成标注') }
        if (pendingDictionaryCount > 0) { reasons.add(pendingDictionaryCount + ' 张字典表未完成登记') }
        if (pendingBusinessCount > 0) { reasons.add(pendingBusinessCount + ' 张业务表未完成登记') }
        if (pendingLogCount > 0) { reasons.add(pendingLogCount + ' 张日志表未完成登记') }
        var samples = []
        for (issue in completionIssues) {
            if (samples.size() >= 3) { break }
            samples.add(issue.tableName + '：' + issue.message)
        }
        exit 400, '不能结束登记：' + reasons.join('、') + '。' + samples.join('；') + '。请返回对应步骤完善并保存。'
    }
}
// 第五步完成登记只落流程状态，不重写连接池配置，避免隐藏密码导致连接信息被清空。
var completionAssetStatus = 2
if (props.completeOnly == true) {
    db.table('db_datasource_t').where().eq('tid', tid).eq('tenant_id', tenantId).eq('is_del', 0).update({
        asset_status: completionAssetStatus,
        flow_status: 2,
        updated_time: nowTime
    })
    var completionIndexFields = {
        assetStatus: completionAssetStatus,
        flowStatus: 2,
        updatedTime: nowTime,
        updatedTime_date: nowTime
    }
    var indexSyncMode = 'status-patch'
    try {
        var dataassetsExists = esCommonService.existsById('dataassets', tid)
        if (dataassetsExists) {
            esCommonService.partialUpdateFieldsFast('dataassets', tid, completionIndexFields)
        } else {
            body = { tid: tid, assetType: 'db' }
            refreshAssetIndex()
            indexSyncMode = 'single-rebuild'
        }
    } catch (e) {
        log.warn('Datasource completion index sync failed; rebuilding one datasource: {}', e.message)
        body = { tid: tid, assetType: 'db' }
        try {
            refreshAssetIndex()
            indexSyncMode = 'single-rebuild'
        } catch (refreshError) {
            log.warn('Datasource completion index rebuild failed: {}', refreshError.message)
            indexSyncMode = 'failed'
        }
    }
    return {
        tid: tid,
        assetStatus: completionAssetStatus,
        displayStatus: completionAssetStatus,
        completionEligible: true,
        tableReviewReady: completionReview.tableReviewReady,
        tableCount: completionReview.tableCount,
        unannotatedTableCount: completionReview.unannotatedTableCount,
        deferredTableCount: completionReview.deferredTableCount,
        indexSync: indexSyncMode
    }
}
var sourceOrgId = first(props, ['sourceOrgId', 'source_org_id', 'orgId', 'org_id'])
var preserveSourceOrgFlag = first(props, ['departmentOnly', 'department_only'])
var preserveSourceOrg = props.preserveSourceOrg == true ||
    (!blank(preserveSourceOrgFlag) && text(preserveSourceOrgFlag).toLowerCase() == 'true')
if (!blank(props.appId) && preserveSourceOrg != true) {
    var application = db.table('sym_application_t').where()
        .eq('tid', props.appId).eq('is_del', 0).selectOne()
    var applicationOrgId = first(application, ['departId', 'org_id'])
    if (!blank(applicationOrgId)) {
        sourceOrgId = applicationOrgId
    }
}

var connectionNames = [
    'host', 'port', 'database', 'username', 'password', 'jdbcURL', 'schema',
    'jdbcType', 'serviceName', 'sid', 'currentSchema', 'defaultSchema',
    'authMode', 'auth', 'principal', 'hivePrincipal', 'servicePrincipal', 'userPrincipal', 'clientPrincipal', 'hiveConnectionMode',
    'keytabPath', 'keytab', 'userKeytab', 'krb5ConfPath', 'krb5Conf', 'jaasConfPath', 'jaasConfig',
    'clientConfigDir', 'mrsClientConfigDir', 'zookeeperQuorum', 'zkQuorum', 'zookeeperNamespace',
    'zooKeeperNamespace', 'serviceDiscoveryMode', 'serviceDiscovery', 'saslQop', 'sasl.qop', 'ssl',
    'driverPath', 'driverClassName', 'sslMode', 'encrypt', 'trustServerCertificate',
    'dbMetaType', 'dbMetaDbName', 'dbMetaIp', 'dbMetaPort', 'dbMetaUser', 'dbMetaPassword',
    'dbIp', 'dbPort', 'dbUser', 'dbPassword', 'dbServerName',
    'maxcomputeEndpoint', 'maxcomputeProject', 'maxcomputeAccessKeyId',
    'maxcomputeAccessKeySecret', 'maxcomputeTunnelEndpoint',
    'minioEndpoint', 'minioAccessKey', 'minioSecretKey', 'minioBucket',
    'minioRegion', 'minioUseSSL', 'minioPrefix', 'minioFilePattern', 'minioSingleFile',
    'ftpProtocol', 'protocol', 'ftpHost', 'hostname',
    'ftpPort', 'ftpPath', 'ftpUsername', 'ftpPassword', 'ftpPassiveMode', 'sftpKnownHosts',
    'ftpFilePattern', 'ftpRecursive', 'ftpSingleFile', 'ftpCharset', 'ftpDelimiter',
    'apiMethod', 'method', 'apiUrl', 'url', 'apiAuthType', 'apiUsername', 'apiPassword',
    'apiToken', 'token', 'authHeader', 'authorization', 'apiKeyName', 'apiKeyValue',
    'apiKeyPosition', 'apiHeaders', 'headers', 'apiBody', 'requestBody', 'responseSample',
    'apiTimeoutSeconds', 'apiCollectionPaths', 'apiPullItems',
    'kafkaBootstrapServers', 'bootstrapServers', 'kafkaTopic', 'topic', 'kafkaGroupId',
    'kafkaSecurityProtocol', 'securityProtocol', 'kafkaSaslMechanism', 'saslMechanism',
    'kafkaUsername', 'saslUsername', 'kafkaPassword', 'saslPassword', 'kafkaSchemaRegistryUrl',
    'kafkaSampleSize', 'kafkaPollTimeoutMs',
    'nodes'
]
// Existing values are the source of truth for fields not rendered in step one.
var old = db.table('db_datasource_t').where().eq('tid', tid).eq('is_del', 0).selectOne()
var oldConnectionConfig = old == null ? {} : datasourceConnectionConfig('read', tid, null, false)
var oldShowConnect = old == null ? null : first(old, ['showConnect', 'show_connect'])
var connectionConfig = {}
var connectionCount = 0
// The registration page sends blank values for controls the user deliberately clears.
// Preserve an omitted or hidden field, but let a blank value overwrite the selected
// connector's own fields so stale pool_cfg values cannot reappear after a refresh.
var requestedConnectionType = blank(first(props, ['dbType', 'db_type', 'databaseType', 'database_type']))
    ? '' : text(first(props, ['dbType', 'db_type', 'databaseType', 'database_type'])).trim().toLowerCase()
if (requestedConnectionType == 'sftp') {
    requestedConnectionType = 'ftp'
}
var blankOverwriteNamesByType = {
    minio: {
        minioEndpoint: true, minioAccessKey: true, minioSecretKey: true,
        minioBucket: true, minioRegion: true, minioUseSSL: true,
        minioPrefix: true, minioFilePattern: true, minioSingleFile: true
    },
    ftp: {
        ftpProtocol: true, ftpHost: true, ftpPort: true, ftpPath: true,
        ftpUsername: true, ftpPassword: true, ftpPassiveMode: true,
        ftpFilePattern: true, ftpRecursive: true, ftpSingleFile: true,
        ftpCharset: true, ftpDelimiter: true
    }
}
var blankOverwriteNames = blankOverwriteNamesByType[requestedConnectionType] || {}
for (name in connectionNames) {
    var value = props instanceof Map ? props.get(name) : props[name]
    if (value != null && (!blank(value) || blankOverwriteNames[name] == true)) {
        connectionConfig[name] = value
        if (!blank(value)) {
            connectionCount = connectionCount + 1
        }
    }
}
var showConnect = 0
if (text(props.showConnect) == '0') {
    showConnect = 0
} else if (connectionCount > 0) {
    showConnect = 1
} else if (old != null && text(oldShowConnect) == '1') {
    showConnect = 1
}
if (showConnect == 0) {
    connectionConfig = {}
    connectionCount = 0
}
// 数据组织与协议专属字段保存到数据源主表的 pool_cfg；没有回传的隐藏凭据沿用已有配置。
// 联系人是数据源的低频扩展信息，不再占用主表列；即使不提供连接也保留。
if (props instanceof Map && props.containsKey('contactName')) {
    connectionConfig.contactName = props.contactName
}
if (props instanceof Map && props.containsKey('contactPhone')) {
    connectionConfig.contactPhone = props.contactPhone
}
var dataSourceType = first(props, ['dataSourceType', 'data_source_type'])
if (!blank(dataSourceType)) {
    connectionConfig.dataSourceType = text(dataSourceType)
}
// 接入方式是登记流程配置，不属于物理连接凭据。即使“不提供连接信息”，
// 也必须写入 pool_cfg，供重新编辑和第五步审查摘要恢复正确方式。
var normalizeAccessMode = (value) => {
    if (blank(value)) {
        return null
    }
    var normalized = text(value).trim().toLowerCase()
    var aliases = {
        extract: 'explore',
        extraction: 'explore',
        push: 'receive',
        pull: 'capture',
        report: 'upload'
    }
    if (aliases[normalized] != null) {
        normalized = aliases[normalized]
    }
    var validModes = { explore: true, receive: true, capture: true, upload: true }
    return validModes[normalized] == true ? normalized : null
}
var accessMode = normalizeAccessMode(first(props, [
    'accessMode', 'access_mode', 'dataAccessMode', 'data_access_mode', 'registrationAccessMode'
]))
if (blank(accessMode)) {
    accessMode = normalizeAccessMode(first(oldConnectionConfig, [
        'accessMode', 'access_mode', 'dataAccessMode', 'data_access_mode', 'registrationAccessMode'
    ]))
}
if (!blank(accessMode)) {
    connectionConfig.accessMode = accessMode
}
// 数据推送由平台提供统一接入通道，不依赖调用方填写物理库连接；将其明确记录为已提供，
// 以便列表状态和登记确认页不会误显示为“未提供连接信息”。
if (accessMode == 'receive') {
    showConnect = 1
    connectionConfig.pushChannelProvided = true
}
// 数据推送能力声明是可选配置。提供了值时服务端复核枚举；未选择不阻断保存，
// 且不会对 JDBC/FTP/Kafka 等其他接入方式写入该配置。
var receivePayloadSize = first(props, ['receivePayloadSize', 'receive_payload_size'])
// `receiveTimeliness` 是上一版字段。保留读取兼容，使已登记的数据源进入编辑页后
// 可以自动转成“批量推送”；新保存只写 receivePushFrequency。
var receivePushFrequency = first(props, ['receivePushFrequency', 'receive_push_frequency', 'receiveTimeliness', 'receive_timeliness'])
var receiveProfile = {}
if (accessMode == 'receive') {
    var validReceivePayloadSizes = {
        '10B-100KB': true,
        '100KB-10MB': true,
        '10MB-1GB': true
    }
    if (text(receivePushFrequency) == 'non-realtime') {
        receivePushFrequency = 'batch'
    }
    var validReceivePushFrequencies = {
        realtime: true,
        batch: true
    }
    if (!blank(receivePayloadSize) && validReceivePayloadSizes[text(receivePayloadSize)] != true) {
        exit 400, '推送数据大小不正确，请选择 10B-100KB、100KB-10MB 或 10MB-1GB'
    }
    if (!blank(receivePushFrequency) && validReceivePushFrequencies[text(receivePushFrequency)] != true) {
        exit 400, '推送数据频率不正确，请选择实时推送或批量推送'
    }
    if (!blank(receivePayloadSize)) {
        receiveProfile.receivePayloadSize = text(receivePayloadSize)
        connectionConfig.receivePayloadSize = receiveProfile.receivePayloadSize
    }
    if (!blank(receivePushFrequency)) {
        receiveProfile.receivePushFrequency = text(receivePushFrequency)
        connectionConfig.receivePushFrequency = receiveProfile.receivePushFrequency
    }
}
var dbName = blank(props.dbName) ? props.database : props.dbName

var dbTypeCode = (value) => {
    var code = blank(value) ? '' : text(value).toLowerCase()
    var values = {
        mysql: 1, mariadb: 1, 'tdsql-mysql': 1, oracle: 2, postgresql: 3,
        'tdsql-pg': 3, highgo: 3, sqlserver: 4, hive: 5, gaussdb: 6,
        gbase8a: 7, dameng: 8, dm: 8, kingbase8: 9, kingbase: 9, vertica: 10,
        oceanbasemysql: 11, oceanbaseoracle: 12, oceanbase: 13, db2: 14,
        clickhouse: 15, gbase8s: 16, oscar: 17, maxcompute: 20, minio: 21,
        ftp: 22, sftp: 22, api: 23, kafka: 24, elasticsearch: 25
    }
    return values[code] == null ? 0 : values[code]
}
var inferDbType = (value, jdbcUrl) => {
    var raw = blank(value) ? '' : text(value).toLowerCase()
    if (!blank(raw)) {
        return raw
    }
    var url = blank(jdbcUrl) ? '' : text(jdbcUrl).toLowerCase()
    if (url.indexOf(':oracle:') >= 0) {
        return 'oracle'
    }
    if (url.indexOf(':mysql:') >= 0) {
        return 'mysql'
    }
    if (url.indexOf(':postgresql:') >= 0) {
        return 'postgresql'
    }
    if (url.indexOf(':dm:') >= 0) {
        return 'dameng'
    }
    if (url.indexOf(':kingbase') >= 0) {
        return 'kingbase8'
    }
    if (url.indexOf(':sqlserver:') >= 0) {
        return 'sqlserver'
    }
    return null
}

// 租户仅来自当前已认证请求的服务端上下文：TenantWebInterceptor 会先将
// Sa-Token 会话中的 tenantId 绑定到 TenantContext。绝不采纳请求体或旧记录，
// 以免跨租户写入或在编辑历史数据时掩盖会话异常。
if (blank(tenantId)) {
    exit 400, '无法识别当前租户，请重新登录后再保存数据源'
}
// 详情页“是否已注册”直接绑定 db_datasource_t.asset_status：0 待注册、1 审核中、2 已注册。
// 审核中只作为既有状态展示，人工编辑只允许在 0 和 2 之间切换。
var explicitAssetStatus = first(props, ['assetStatus', 'asset_status'])
var explicitAssetStatusText = blank(explicitAssetStatus) ? '' : text(explicitAssetStatus)
var oldAssetStatus = old == null ? null : first(old, ['assetStatus', 'asset_status'])
var oldConnectionStatus = old == null ? null : first(old, ['connectionStatus', 'connection_status'])
// 仅登记页测试连接生成的有效状态可以覆盖历史状态；未测试的普通保存仍保留未知。
// 页面仅在服务端连通性检测返回后提交 success 或 failed，避免将任意文本写成状态。
var incomingConnectionStatus = text(first(props, ['connectionStatus', 'connection_status']))
var normalizedIncomingConnectionStatus = blank(incomingConnectionStatus) ? '' : incomingConnectionStatus.trim().toLowerCase()
var connectionStatus = oldConnectionStatus
if (accessMode == 'receive') {
    // 推送端点由平台统一提供，登记保存即代表该接入通道已就绪。
    connectionStatus = 'success'
} else if (showConnect == 0) {
    connectionStatus = 'unprovided'
} else if (['success', 'connected', 'ok', 'true', '1'].contains(normalizedIncomingConnectionStatus)) {
    connectionStatus = 'success'
} else if (['failed', 'fail', 'error', 'false', '0'].contains(normalizedIncomingConnectionStatus)) {
    connectionStatus = 'failed'
} else if (blank(connectionStatus)) {
    connectionStatus = 'unknown'
}
var oldFlowStatus = old == null ? null : first(old, ['flowStatus', 'flow_status'])
var dbType = inferDbType(first(props, ['dbType', 'db_type', 'databaseType', 'database_type']), first(props, ['jdbcURL', 'jdbcUrl', 'jdbc_url']))
if (blank(dbType) && old != null) {
    dbType = inferDbType(first(old, ['dbType', 'db_type']), first(old, ['jdbcUrl', 'jdbc_url']))
}
// 华为 MRS Hive 的元数据仅由服务端 hwhive 模块访问。登记时只持久化模式标记，
// 供登记后续步骤和 NiFi 重新加载当前租户的数据源，不信任浏览器提交的 JDBC/Kerberos 参数。
var hiveConnectionMode = first(props, ['hiveConnectionMode', 'hive_connection_mode'])
if (blank(hiveConnectionMode)) {
    hiveConnectionMode = first(oldConnectionConfig, ['hiveConnectionMode', 'hive_connection_mode'])
}
var isServerManagedHuaweiMrs = (blank(dbType) ? '' : text(dbType)).trim().toLowerCase() == 'hive' &&
    (blank(hiveConnectionMode) ? '' : text(hiveConnectionMode)).trim().toLowerCase() == 'huawei-mrs'
if (isServerManagedHuaweiMrs) {
    connectionConfig.hiveConnectionMode = 'huawei-mrs'
    connectionConfig.metadataAccessMode = 'server-managed-mrs'
} else if (!blank(hiveConnectionMode)) {
    connectionConfig.hiveConnectionMode = text(hiveConnectionMode).trim()
    connectionConfig.metadataAccessMode = 'jdbc'
}var assetStatus = oldAssetStatus == null ? 0 : oldAssetStatus
var flowStatus = oldFlowStatus == null ? 0 : oldFlowStatus
var changedWhenProvided = (oldValue, inputValue) => !blank(inputValue) && text(oldValue) != text(inputValue)
// 连接属性由键值记录承载；隐藏凭据和未展示字段不参与重置判断。
// connector fields otherwise turn an unchanged edit into a false re-registration.
var connectionChanged = old != null && (
    changedWhenProvided(inferDbType(first(old, ['dbType', 'db_type']), first(old, ['jdbcUrl', 'jdbc_url'])), dbType) ||
    changedWhenProvided(first(old, ['jdbcUrl', 'jdbc_url']), first(props, ['jdbcURL', 'jdbcUrl', 'jdbc_url'])) ||
    (text(props.showConnect) == '0' && text(oldShowConnect) == '1')
)
if (connectionChanged && text(oldAssetStatus) == '2' && explicitAssetStatusText != '2') {
    assetStatus = 0
    flowStatus = 0
}
// 资产状态是“是否已注册”的唯一来源；登记页首次保存不自动完成第五步。
if (explicitAssetStatusText == '0') {
    assetStatus = 0
    flowStatus = 0
} else if (explicitAssetStatusText == '2') {
    assetStatus = 2
    flowStatus = 2
}

// node_id 只保存 sym_dict_t 中 nodeType 子项的 tid；兼容旧入参误传中文/编码时自动归一为字典 ID。
var resolveNodeTypeId = (value) => {
    if (blank(value)) {
        return null
    }
    var raw = text(value).trim()
    var root = db.table('sym_dict_t').where().eq('dict_code', 'nodeType').eq('is_del', 0).selectOne()
    var parentId = root == null ? null : root.tid
    var existed = db.table('sym_dict_t').where().eq('tid', raw).eq('is_del', 0).selectOne()
    if (existed != null && (blank(parentId) || first(existed, ['parentId', 'parent_id']) == parentId)) {
        return raw
    }
    if (!blank(parentId)) {
        var byName = db.table('sym_dict_t').where().eq('parent_id', parentId).eq('dict_name', raw).eq('is_del', 0).selectOne()
        if (byName != null) {
            return byName.tid
        }
        var byCode = db.table('sym_dict_t').where().eq('parent_id', parentId).eq('dict_code', raw).eq('is_del', 0).selectOne()
        if (byCode != null) {
            return byCode.tid
        }
    }
    return raw
}

var storageDomain = first(props, ['storageDomain', 'storage_domain', 'SSWL', 'networkDomain', 'network_domain'])
var nodeId = resolveNodeTypeId(first(props, ['nodeId', 'node_id']))
var valueOrOld = (value, keys) => blank(value) && old != null ? first(old, keys) : value
var mainJdbcUrl = valueOrOld(first(props, ['jdbcURL', 'jdbcUrl', 'jdbc_url']), ['jdbcUrl', 'jdbc_url'])
var mainUsername = valueOrOld(props.username, ['username'])
var mainPassword = valueOrOld(props.password, ['password'])

var main = {
    tid: tid,
    db_name: dbName,
    database_type: dbTypeCode(dbType),
    db_type: dbType,
    driver_class_name: showConnect == 1 ? valueOrOld(props.driverClassName, ['driverClassName', 'driver_class_name']) : null,
    jdbc_url: showConnect == 1 ? mainJdbcUrl : null,
    username: showConnect == 1 ? mainUsername : null,
    password: showConnect == 1 ? mainPassword : null,
    remark: valueOrOld(props.assetDesc, ['remark']),
    is_enable: old == null ? 1 : valueOrOld(props.isEnable, ['isEnable', 'is_enable']),
    app_id: valueOrOld(props.appId, ['appId', 'app_id']),
    // 数据源部门归属在创建时确定。编辑人、表单回填值和所属应用的事权单位
    // 都不能覆盖已有记录的 org_id，否则部门范围与创建归属会被错误改写。
    org_id: old == null
        ? valueOrOld(sourceOrgId, ['orgId', 'org_id'])
        : first(old, ['orgId', 'org_id']),
    storage_domain: valueOrOld(storageDomain, ['storageDomain', 'storage_domain']),
    node_id: valueOrOld(nodeId, ['nodeId', 'node_id']),
    db_version: valueOrOld(props.dbVersion, ['dbVersion', 'db_version']),
    user_count: valueOrOld(props.userCount, ['userCount', 'user_count']),
    view_count: valueOrOld(props.viewCount, ['viewCount', 'view_count']),
    procedure_count: valueOrOld(props.procedureCount, ['procedureCount', 'procedure_count']),
    data_size: valueOrOld(props.dataSize, ['dataSize', 'data_size']),
    total_size: valueOrOld(props.totalSize, ['totalSize', 'total_size']),
    show_connect: showConnect,
    connection_status: connectionStatus,
    tenant_id: tenantId,
    table_num: valueOrOld(props.tableCount, ['tableCount', 'tableNum', 'table_num']),
    asset_status: assetStatus,
    flow_status: flowStatus,
    flow_order_id: old == null ? null : first(old, ['flowOrderId', 'flow_order_id']),
    is_del: 0,
    updated_time: nowTime,
    reg_time: old == null ? nowTime : first(old, ['regTime', 'reg_time'])
}
if (old == null) {
    main.created_time = nowTime
    db.table('db_datasource_t').primary('tid').insert(main)
} else {
    db.table('db_datasource_t').primary('tid').update(main, true)
}

// 数据组织、密码、令牌及协议专属参数都由数据源主表 pool_cfg JSON 统一承载。
// 局部编辑没有回传的字段不会被覆盖；明确关闭连接时才会清空连接配置。
// 接入方式是无连接登记也需要保留的业务配置，清空连接后单独写回它。
if (showConnect == 0) {
    datasourceConnectionConfig('save', tid, {}, true)
    var registrationConfig = {}
    if (!blank(accessMode)) {
        registrationConfig.accessMode = accessMode
    }
    if (accessMode == 'receive') {
        if (!blank(receiveProfile.receivePayloadSize)) {
            registrationConfig.receivePayloadSize = receiveProfile.receivePayloadSize
        }
        if (!blank(receiveProfile.receivePushFrequency)) {
            registrationConfig.receivePushFrequency = receiveProfile.receivePushFrequency
        }
    }
    if (!blank(accessMode)) {
        datasourceConnectionConfig('save', tid, registrationConfig, false)
    }
} else {
    datasourceConnectionConfig('save', tid, connectionConfig, false)
}

var savedTid = tid
// ES 是查询投影而非主数据。编辑页可要求把索引重建放到成功返回后执行，避免
// 网络超时将已持久化的数据源保存操作卡在等待状态。
var deferIndexRefresh = body != null && (body.deferIndexRefresh == true || body.deferIndexRefresh == 1 || body.deferIndexRefresh == '1')
if (deferIndexRefresh) {
    return { tid: savedTid, indexRefreshPending: true }
}
body = { tid: savedTid }
try {
    refreshAssetIndex()
} catch (e) {
    // 主数据已经提交；ES 为投影索引，连接故障不能回滚或阻断数据源保存。
    log.warn('刷新数据源搜索索引失败，稍后可通过盘点维护重试: {}', e.message)
}
return { tid: savedTid, indexRefreshPending: false }
