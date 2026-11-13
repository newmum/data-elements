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
import '@post:/dst/maintenance/refresh' as refreshAssetIndex
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import apiPullConfig
import tenantRuntime
import dataScope
import metadataAsset
import tableRead
import tableWrite
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import cn.hutool.core.util.IdUtil
import cn.hutool.json.JSONUtil
import java.util.List
import java.util.Map
import jsons
import log

/**
 * POST /dst/database/saveOrUpdate 保存当前租户的数据源主记录、应用归属和连接配置。
 * tid 为空时新增，propList 提交登记字段，连接扩展配置保存在 pool_cfg。
 * completeOnly=true 审查并更新登记状态。dryRun=true 只返回预检结果，无持久化或索引操作。
 * 主数据持久化后同步搜索索引。deferIndexRefresh 允许调用方随后触发普通保存的索引同步。
 */
var text = (value) => value == null ? null : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var nowTime = new Date()
var tenantId = tenantRuntime.id()

// 解析请求体并为可选参数设置默认值。
var tid = blank(body == null ? null : body.tid) ? IdUtil.getSnowflakeNextIdStr() : body.tid
var props = body == null || body.propList == null ? {} : body.propList
var dryRun = props.dryRun == true || body?.dryRun == true
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
// 结束登记允许部分表未完成；状态由当前租户有效表的 asset_status 聚合决定。
var completionReview = null
var isProxyCompletion = false
var proxyServiceConditions = null
var proxySourceConditions = null
var requestedStatus = first(props, ['assetStatus', 'asset_status'])
if (props.completeOnly == true || ['1', '2'].contains(text(requestedStatus))) {
    var existed = db.table('db_datasource_t').where().eq('tid', tid).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (existed == null) { exit 404, '数据源不存在或不属于当前租户' }
    var visible = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', [existed])
    if (visible == null || visible.size() != 1) { exit 403, '数据源不存在或已删除' }
    var completionPool = first(existed, ['poolCfg', 'pool_cfg'])
    if (completionPool == null) { completionPool = {} }
    if (completionPool instanceof String) {
        try { completionPool = JSONUtil.parseObj(completionPool) } catch (e) { completionPool = {} }
    }
    var completionMode = first(existed, ['accessMode', 'access_mode'])
    if (blank(completionMode)) { completionMode = completionPool.accessMode }
    isProxyCompletion = ['proxy', 'agent'].contains(blank(completionMode) ? '' : text(completionMode).toLowerCase())
    if (isProxyCompletion) {
        // 预检与实际更新共用同一组服务范围条件。空组织和缺失的 OWNER 标识均不可见。
        var completionScope = dataScope.dataSource()
        proxyServiceConditions = {is_del: 0}
        if (completionScope.scope == 'OWNER') {
            proxyServiceConditions.created_by = blank(completionScope.userId) ? [] : completionScope.userId
        } else if (completionScope.scope == 'ORG') {
            proxyServiceConditions.org_id = completionScope.organizationIds == null ? [] : completionScope.organizationIds
        } else if (completionScope.scope != 'ALL') {
            exit 403, '数据范围无效'
        }

        var serviceAlias = 'completion_service'
        var services = tableRead.from('api_info_t', serviceAlias, tenantId)
        services.where(tableRead.equal(tableRead.column(serviceAlias, 'datasource_id', 'TEXT'), tid))
        for (field in proxyServiceConditions.keySet()) {
            var conditionValue = proxyServiceConditions[field]
            var column = tableRead.column(serviceAlias, field, field == 'is_del' ? 'NUMBER' : 'TEXT')
            services.where(conditionValue instanceof List
                ? tableRead.in(column, conditionValue)
                : tableRead.equal(column, conditionValue))
        }
        var counts = services.grouped({service_count: tableRead.count()}, [])
        var completionServiceCount = counts[0].service_count::long
        var eligible = completionServiceCount > 0
        completionReview = {
            tid: tid, assetStatus: eligible ? 2 : 1, completionEligible: eligible,
            tableReviewReady: eligible, serviceCount: completionServiceCount,
            tableCount: 0, registeredTableCount: 0, pendingTableCount: 0
        }
        proxySourceConditions = {is_del: 0, updated_time: first(existed, ['updatedTime', 'updated_time'])}
    } else {
        completionReview = metadataAsset.reviewRegistrationStatus(tid)
    }
    if (dryRun) { return completionReview }
}
var completionAssetStatus = completionReview == null ? 0 : completionReview.assetStatus
var persistProxyCompletion = () => {
    // 单条更新同时校验数据源版本和可见服务。预检后任一条件失效都拒绝提交。
    var changed = tableWrite.updateIfExists('db_datasource_t', tenantId, 'tid', tid, {
        asset_status: completionAssetStatus, flow_status: 2, updated_time: nowTime
    }, proxySourceConditions, 'api_info_t', 'datasource_id', proxyServiceConditions)
    if (changed != 1) { exit 409, '服务清单已变化，请重新确认后结束登记' }
}
if (props.completeOnly == true) {
    if (isProxyCompletion) {
        if (completionReview.completionEligible != true) { exit 400, '请先登记至少一项当前数据范围内的数据服务，再结束登记' }
        db.transaction(persistProxyCompletion)
    } else {
        completionReview = metadataAsset.refreshRegistrationStatus(tid, true)
        completionAssetStatus = completionReview.assetStatus
    }
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
        registeredTableCount: completionReview.registeredTableCount,
        pendingTableCount: completionReview.pendingTableCount,
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
    'gaussdbVariant', 'transportMode', 'httpPath', 'ftpsImplicit', 'trustStorePath', 'trustStorePassword', 'nifiTrustStorePath',
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
    'nodes', 'authMethod', 'apiKey', 'indexName', 'defaultIndex'
]
// 第一步未提交的字段继承当前租户的有效主记录，权限也按该记录判断。
var old = db.table('db_datasource_t').where().eq('tid', tid).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (old == null && completionReview != null) {
    exit 409, '数据源记录已变化，请重新读取后保存'
}
if (old != null && !dataScope.canReadDataSourceFor('MENU:2081000000000000002', old)) {
    exit 403, '数据源不存在或无权修改'
}
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
if (['es', 'elastic'].contains(requestedConnectionType)) {
    requestedConnectionType = 'elasticsearch'
}
var blankOverwriteNamesByType = {
    elasticsearch: {
        database: true, indexName: true, defaultIndex: true, jdbcURL: true,
        nodes: true, username: true, password: true, apiKey: true, token: true
    },
    minio: {
        minioEndpoint: true, minioAccessKey: true, minioSecretKey: true,
        minioBucket: true, minioRegion: true, minioUseSSL: true,
        minioPrefix: true, minioFilePattern: true, minioSingleFile: true
    },
    ftp: {
        ftpsImplicit: true, trustStorePath: true, trustStorePassword: true, nifiTrustStorePath: true,
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
    var validModes = { explore: true, receive: true, capture: true, upload: true, proxy: true }
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
// 上传清单由专用登记接口维护，普通基本信息保存只能沿用服务端已有清单。
// 不提供物理连接时前面会重置 connectionConfig，但不能因此丢失已登记文件及版本。
if (accessMode == 'upload' && oldConnectionConfig.reportRegistration != null) {
    connectionConfig.reportRegistration = oldConnectionConfig.reportRegistration
}
// 数据推送由平台提供统一接入通道，不依赖调用方填写物理库连接；将其明确记录为已提供，
// 以便列表状态和登记确认页不会误显示为“未提供连接信息”。
if (accessMode == 'receive') {
    // 来源是上游程序或服务的地址，与平台接收节点地址分别配置；仅要求非空，接受 IP 或 URL。
    // 未提交 host 的局部更新沿用已有值，明确清空则拒绝，避免重新显示旧地址。
    var sourceAddressSubmitted = props instanceof Map && props.containsKey('host')
    var sourceAddress = sourceAddressSubmitted ? props.host : oldConnectionConfig.host
    if (blank(sourceAddress)) {
        exit 400, '请填写数据来源地址'
    }
    connectionConfig.host = text(sourceAddress).trim()
    showConnect = 1
    connectionConfig.pushChannelProvided = true
}
// 数据内容类型独立于接入方式、数据库协议及历史频率字段，仅影响推送协议展示。
var pushDataType = first(props, ['pushDataType', 'push_data_type'])
if (accessMode == 'receive') {
    if (blank(pushDataType)) { pushDataType = first(oldConnectionConfig, ['pushDataType', 'push_data_type']) }
    pushDataType = blank(pushDataType) ? 'STRUCTURED' : text(pushDataType).trim().toUpperCase()
    if (pushDataType == '结构化数据') { pushDataType = 'STRUCTURED' }
    if (pushDataType == '非结构化数据') { pushDataType = 'UNSTRUCTURED' }
    if (!['STRUCTURED', 'UNSTRUCTURED'].contains(pushDataType)) { exit 400, '推送数据类型仅支持结构化数据或非结构化数据' }
    connectionConfig.pushDataType = pushDataType
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
// 数据源状态：0 待登记，1 未登记完成，2 所有表已完成。客户端不能跳过表状态聚合。
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
if (accessMode == 'proxy') { dbType=null }
var isApiCapture = accessMode == 'capture' && !blank(dbType) && text(dbType).toLowerCase() == 'api'
if (isApiCapture && props instanceof Map && props.containsKey('apiPullItems')) {
    try { apiPullConfig.validateDefinitions(props.apiPullItems) }
    catch (e) { exit 400, 'API 抓取登记定义无效，请核对地址、表名及凭据引用' }
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
    connectionConfig.hiveProfile = ''
    connectionConfig.serviceDiscoveryMode = ''
    connectionConfig.zookeeperQuorum = ''
    connectionConfig.zookeeperNamespace = ''
}var assetStatus = oldAssetStatus == null ? 0 : oldAssetStatus
var flowStatus = oldFlowStatus == null ? 0 : oldFlowStatus
var changedWhenProvided = (oldValue, inputValue) => !blank(inputValue) && text(oldValue) != text(inputValue)
// 连接属性由键值记录承载；隐藏凭据和未展示字段不参与重置判断。
// connector fields otherwise turn an unchanged edit into a false re-registration.
var connectionChanged = old != null && (
    changedWhenProvided(inferDbType(first(old, ['dbType', 'db_type']), first(old, ['jdbcUrl', 'jdbc_url'])), dbType) ||
    changedWhenProvided(first(oldConnectionConfig, ['gaussdbVariant']), first(props, ['gaussdbVariant'])) ||
    changedWhenProvided(first(oldConnectionConfig, ['hiveConnectionMode']), first(props, ['hiveConnectionMode'])) ||
    changedWhenProvided(first(old, ['jdbcUrl', 'jdbc_url']), first(props, ['jdbcURL', 'jdbcUrl', 'jdbc_url'])) ||
    (text(props.showConnect) == '0' && text(oldShowConnect) == '1')
)
if (connectionChanged && ['1', '2'].contains(text(oldAssetStatus)) && explicitAssetStatusText != '2') {
    assetStatus = 0
    flowStatus = 0
}
// 资产状态是“是否已注册”的唯一来源；登记页首次保存不自动完成第五步。
if (explicitAssetStatusText == '0') {
    assetStatus = 0
    flowStatus = 0
} else if (['1', '2'].contains(explicitAssetStatusText)) {
    assetStatus = completionAssetStatus
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
// ES允许匿名连接；主表镜像也应接收明确清空的值，否则重新读取时会覆盖pool_cfg中的空值。
if (requestedConnectionType == 'elasticsearch' && props instanceof Map) {
    if (props.containsKey('jdbcURL')) { mainJdbcUrl = props.jdbcURL }
    if (props.containsKey('username')) { mainUsername = props.username }
    if (props.containsKey('password')) { mainPassword = props.password }
}

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
// 数据组织、密码、令牌及协议专属参数都由数据源主表 pool_cfg JSON 统一承载。
// 局部编辑没有回传的字段不会被覆盖；明确关闭连接时才会清空连接配置。
// 接入方式是无连接登记也需要保留的业务配置，清空连接后单独写回它。
var persistConnection = () => {
    if (showConnect == 0) {
        datasourceConnectionConfig('save', tid, {}, true)
        var registrationConfig = {}
        if (!blank(accessMode)) {
            registrationConfig.accessMode = accessMode
        }
        // API 抓取离线登记仍保存请求定义，以便网络恢复后生成对应任务。
        if (isApiCapture) {
            var apiItems = props instanceof Map && props.containsKey('apiPullItems')
                ? props.apiPullItems : oldConnectionConfig.apiPullItems
            if (apiItems != null) { registrationConfig.apiPullItems = apiItems }
        }
        if (accessMode == 'upload' && oldConnectionConfig.reportRegistration != null) {
            registrationConfig.reportRegistration = oldConnectionConfig.reportRegistration
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
}

var persistSource = () => {
    if (old == null) {
        main.created_time = nowTime
        db.table('db_datasource_t').primary('tid').insert(main)
    } else {
        // 历史整表提交也可能要求完成代理登记。先校验服务，再在同一事务保存主表和连接配置。
        var guardedCompletion = isProxyCompletion && completionReview.completionEligible == true
        if (guardedCompletion) { persistProxyCompletion() }
        var update = db.table('db_datasource_t').withBlank().where()
            .eq('tid', tid).eq('tenant_id', tenantId).eq('is_del', 0)
        if (!guardedCompletion) {
            // 表单按已读版本合并隐藏字段，冲突时重新读取，避免覆盖并发编辑或复活已删除记录。
            var expectedUpdatedTime = first(old, ['updatedTime', 'updated_time'])
            if (expectedUpdatedTime == null) { update.isNull('updated_time') }
            else { update.eq('updated_time', expectedUpdatedTime) }
        }
        var changed = update.update(main)
        // 通过代理校验时已锁定并更新主记录。部分驱动对相同值的第二次更新返回 0。
        if (changed != 1 && !(guardedCompletion && changed == 0)) {
            exit 409, '数据源记录已变化，请重新读取后保存'
        }
    }
    persistConnection()
}
// 普通保存预检完成校验和字段推导后直接返回，避免将预检写入主表、配置或索引。
if (dryRun) {
    return {tid: tid, assetStatus: assetStatus, indexRefreshPending: false}
}
db.transaction(persistSource)

var savedTid = tid
// ES 是查询投影而非主数据。编辑页可要求把索引重建放到成功返回后执行，避免
// 网络超时将已持久化的数据源保存操作卡在等待状态。
var deferIndexRefresh = body != null && (body.deferIndexRefresh == true || body.deferIndexRefresh == 1 || body.deferIndexRefresh == '1')
if (deferIndexRefresh) {
    return { tid: savedTid, assetStatus: assetStatus, indexRefreshPending: true }
}
body = { tid: savedTid }
try {
    refreshAssetIndex()
} catch (e) {
    // 主数据已经提交；ES 为投影索引，连接故障不能回滚或阻断数据源保存。
    log.warn('刷新数据源搜索索引失败，稍后可通过盘点维护重试: {}', e.message)
}
return { tid: savedTid, assetStatus: assetStatus, indexRefreshPending: false }
