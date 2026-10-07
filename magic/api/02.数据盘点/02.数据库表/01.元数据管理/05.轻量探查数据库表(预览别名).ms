{
  "properties": {},
  "id": "metadata_tables_preview_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "05.轻量探查数据库表(预览别名)",
  "createTime": 1783639496542,
  "updateTime": 1789378460165,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/tables/preview",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"dbId\": \"codex_kingbase_firmament_data_v6\",\n    \"pageNo\": 1,\n    \"pageSize\": 50,\n    \"keyword\": \"\",\n    \"schemaName\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"pageNo\": 1,\n  \"pageSize\": 50,\n  \"total\": 350,\n  \"rows\": []\n}",
  "description": "轻量探查数据库表（POST /dst/database/metadata/tables/preview）。从 db_datasource_t 读取连接信息，调用 MetadataExplorerService 分页读取物理库表/视图摘要；Oracle 默认限定为登录用户，仅返回当前连接可探查且包含对象的用户列表，schemaName 可切换指定用户，旧的失效选择会自动回退；无关键字全量口径会把探查总表数同步回 db_datasource_t.table_num，供数据源管理列表统计使用；前端第二步使用 pageNo/pageSize 加载更多，旧 limit 调用继续兼容；使用请求参数 dbId、silent、keyword、limit、pageNo、pageSize、schemaName、annotationStatus；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.轻量探查数据库表(预览别名)
// 路由：POST /dst/database/metadata/tables/preview
// 业务说明：轻量探查数据库表（POST /dst/database/metadata/tables/preview）。从 db_datasource_t 读取连接信息，调用 MetadataExplorerService 分页读取物理库表/视图摘要；Oracle 默认限定为登录用户，仅返回当前连接可探查且包含对象的用户列表，schemaName 可切换指定用户，旧的失效选择会自动回退；无关键字全量口径会把探查总表数同步回 db_datasource_t.table_num，供数据源管理列表统计使用；前端第二步使用 pageNo/pageSize 加载更多，旧 limit 调用继续兼容；使用请求参数 dbId、silent、keyword、limit、pageNo、pageSize、schemaName、annotationStatus；执行数据库写入并返回处理结果。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import jsons
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService as structuredSourceProbeService
import tenantRuntime
import java.util.Map
import java.util.Date
import java.util.ArrayList
import java.util.HashSet

/*
 * 元数据管理 05：轻量预览物理数据库表/视图。
 * 轻写：不写 db_table_t / db_table_column_t；无关键字探查时只同步 db_datasource_t.table_num。
 * 新流程优先使用数据库侧分页，避免 Kingbase、Oracle 等大库首次进入时全量探查。
 */
var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var lower = (value) => blank(value) ? '' : textOf(value).toLowerCase()
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
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) {
            return value
        }
    }
    return null
}
var loadAsset = (assetId) => {
    if (blank(assetId)) {
        return null
    }
    var main = db.table('db_datasource_t').where().eq('tid', assetId).eq('is_del', 0).selectOne()
    if (main == null) {
        return null
    }
    var result = {
        tid: rowValue(main, ['tid']),
        id: rowValue(main, ['tid']),
        assetType: 'db',
        dbName: rowValue(main, ['dbName', 'db_name']),
        dbType: rowValue(main, ['dbType', 'db_type']),
        jdbcURL: rowValue(main, ['jdbcUrl', 'jdbc_url']),
        username: rowValue(main, ['username']),
        password: rowValue(main, ['password']),
        appId: rowValue(main, ['appId', 'app_id']),
        orgId: rowValue(main, ['orgId', 'org_id'])
    }
    var connectionConfig = datasourceConnectionConfig('read', rowValue(main, ['tid']), null, false)
    if (connectionConfig != null && !connectionConfig.isEmpty()) { result.putAll(connectionConfig) }
    if (!blank(rowValue(main, ['dbName', 'db_name']))) { result.dbName = rowValue(main, ['dbName', 'db_name']) }
    if (!blank(rowValue(main, ['dbType', 'db_type']))) { result.dbType = rowValue(main, ['dbType', 'db_type']) }
    if (rowValue(main, ['jdbcUrl', 'jdbc_url']) != null) { result.jdbcURL = rowValue(main, ['jdbcUrl', 'jdbc_url']) }
    if (rowValue(main, ['username']) != null) { result.username = rowValue(main, ['username']) }
    if (rowValue(main, ['password']) != null) { result.password = rowValue(main, ['password']) }
    return result
}
var databaseTypeOf = (code) => {
    if (blank(code)) {
        return DatabaseType.MYSQL
    }
    try {
        return DatabaseType.fromCode(textOf(code).toLowerCase())
    } catch (e) {
        return DatabaseType.MYSQL
    }
}
var dataSourceOf = (asset) => {
    var dbType = databaseTypeOf(firstValue(asset, ['dbType', 'databaseType', 'dataSourceType']))
    var typeCode = textOf(dbType.getCode()).toLowerCase()
    var virtualResource = !dbType.isRelational()
    var zookeeperQuorum = firstValue(asset, ['zookeeperQuorum', 'zkQuorum'])
    var hiveZooKeeperDiscovery = dbType == DatabaseType.HIVE && !blank(zookeeperQuorum)
    var host = firstValue(asset, ['host', 'dbIp', 'minioEndpoint', 'apiUrl', 'url', 'nodes', 'ftpHost', 'hostname', 'bootstrapServers', 'kafkaBootstrapServers', 'maxcomputeEndpoint'])
    var port = firstValue(asset, ['port', 'dbPort', 'ftpPort'])
    var databaseName = firstValue(asset, ['database', 'dbName', 'dbMetaDbName', 'schema', 'defaultSchema', 'currentSchema', 'maxcomputeProject', 'minioBucket', 'bucket', 'topic', 'kafkaTopic', 'indexName', 'apiName', 'assetName', 'name'])
    var username = firstValue(asset, ['username', 'dbUser', 'dbMetaUser', 'user', 'maxcomputeAccessKeyId', 'minioAccessKey', 'ftpUsername', 'apiUser'])
    var password = firstValue(asset, ['password', 'dbPassword', 'dbMetaPassword', 'maxcomputeAccessKeySecret', 'minioSecretKey', 'ftpPassword', 'apiToken'])
    if (!virtualResource && (blank(databaseName) || (!hiveZooKeeperDiscovery && (blank(host) || blank(username))))) {
        exit 400, '数据源连接参数不完整'
    }
    var dsConfig = new DataSourceConfig()
    dsConfig.setDatabaseType(dbType)
    dsConfig.setHost(blank(host) ? (hiveZooKeeperDiscovery ? '' : 'virtual') : textOf(host))
    dsConfig.setPort(blank(port) ? dbType.getDefaultPort() : port::int)
    dsConfig.setDatabase(blank(databaseName) ? typeCode + '_resource' : textOf(databaseName))
    dsConfig.setUsername(blank(username) ? '' : textOf(username))
    dsConfig.setPassword(blank(password) ? '' : textOf(password))
    dsConfig.setJdbcUrl(blank(firstValue(asset, ['jdbcURL', 'jdbcUrl', 'jdbc_url'])) ? null : textOf(firstValue(asset, ['jdbcURL', 'jdbcUrl', 'jdbc_url'])))
    dsConfig.setSchema(blank(firstValue(asset, ['schema', 'defaultSchema', 'currentSchema'])) ? null : textOf(firstValue(asset, ['schema', 'defaultSchema', 'currentSchema'])))
    dsConfig.setAuthMode(firstValue(asset, ['authMode', 'auth']))
    dsConfig.setPrincipal(firstValue(asset, ['principal', 'hivePrincipal', 'servicePrincipal']))
    dsConfig.setUserPrincipal(firstValue(asset, ['userPrincipal', 'clientPrincipal', 'kerberosUserPrincipal']))
    dsConfig.setKeytabPath(firstValue(asset, ['keytabPath', 'keytab', 'userKeytab']))
    dsConfig.setKrb5ConfPath(firstValue(asset, ['krb5ConfPath', 'krb5Conf', 'krb5']))
    dsConfig.setJaasConfPath(firstValue(asset, ['jaasConfPath', 'jaasConfig']))
    dsConfig.setZookeeperQuorum(blank(zookeeperQuorum) ? null : textOf(zookeeperQuorum))
    dsConfig.setZookeeperNamespace(firstValue(asset, ['zookeeperNamespace', 'zooKeeperNamespace']))
    dsConfig.setServiceDiscoveryMode(firstValue(asset, ['serviceDiscoveryMode', 'serviceDiscovery']))
    dsConfig.setSaslQop(firstValue(asset, ['saslQop', 'sasl.qop']))
    dsConfig.setClientConfigDir(firstValue(asset, ['clientConfigDir', 'mrsClientConfigDir']))
    dsConfig.setExtraParams(blank(firstValue(asset, ['extraParams', 'connectParams', 'jdbcParams', 'maxcomputeEndpoint'])) ? null : textOf(firstValue(asset, ['extraParams', 'connectParams', 'jdbcParams', 'maxcomputeEndpoint'])))
    dsConfig.setConnectionTimeout(30)
    return dsConfig
}

var dbId = body == null ? null : body.dbId
var silent = body != null && body.silent == true
if (blank(dbId)) {
    if (silent) {
        return { found: false, rows: [], list: [], total: 0, physicalTotal: 0 }
    }
    exit 400, 'dbId 不能为空'
}
var asset = loadAsset(dbId)
if (asset == null) {
    if (silent) {
        return { found: false, rows: [], list: [], total: 0, physicalTotal: 0 }
    }
    exit 404, '数据源资产不存在'
}

var tenantId = tenantRuntime.id()
if (blank(tenantId)) {
    exit 401, '登录状态已失效，请重新登录'
}

var keyword = lower(body == null ? null : body.keyword)
var limit = body == null || body.limit == null ? 200 : body.limit::int
var pageNo = body == null || body.pageNo == null ? null : body.pageNo::int
var pageSize = body == null || body.pageSize == null ? null : body.pageSize::int
var paged = pageNo != null || pageSize != null
var unlimited = limit <= 0
var tables = []
var total = 0
var physicalTotal = 0
var sourceType = lower(firstValue(asset, ['dbType', 'databaseType', 'dataSourceType']))
var structuredSource = sourceType == 'api' || sourceType == 'ftp' || sourceType == 'kafka'
var oracleSource = sourceType == 'oracle'
var loginUsername = textOf(firstValue(asset, ['username', 'dbUser', 'dbMetaUser', 'user']))
var loginSchema = blank(loginUsername) ? null : textOf(loginUsername).trim().toUpperCase()
var requestedSchema = body == null ? null : body.schemaName
var selectedSchema = null
var metadataConfig = dataSourceOf(asset)
var schemaOptions = new ArrayList()
if (oracleSource) {
    selectedSchema = blank(requestedSchema) ? loginSchema : textOf(requestedSchema).trim().toUpperCase()
    // 仅返回当前连接可通过 ALL_TABLES / ALL_VIEWS 探查、且至少有一个对象的
    // Owner。该服务在 Oracle 侧用一次聚合查询完成，不能在这里逐用户探查。
    var availableSchemas = metadataExplorerService.getSchemasWithTables(metadataConfig)
    var availableSchemaSet = new HashSet()
    schemaOptions = new ArrayList()
    for (schemaOption in availableSchemas == null ? [] : availableSchemas) {
        var normalizedSchema = blank(schemaOption) ? null : textOf(schemaOption).trim().toUpperCase()
        if (!blank(normalizedSchema) && !availableSchemaSet.contains(normalizedSchema)) {
            availableSchemaSet.add(normalizedSchema)
            schemaOptions.add(normalizedSchema)
        }
    }
    // 已保存的选择可能来自旧列表、权限变更或数据源切换。自动回退到当前账号
    // （若其有可探查对象），否则回退到首个可用 Owner，页面不会再收到 400。
    if (!blank(selectedSchema) && !availableSchemaSet.contains(selectedSchema)) {
        if (!blank(loginSchema) && availableSchemaSet.contains(loginSchema)) {
            selectedSchema = loginSchema
        } else if (!schemaOptions.isEmpty()) {
            selectedSchema = schemaOptions.get(0)
        } else {
            selectedSchema = loginSchema
        }
    }
    // 当前连接账号有可探查对象时固定排在首位，其他 Owner 保持数据库返回顺序。
    var orderedSchemas = new ArrayList()
    if (!blank(loginSchema) && availableSchemaSet.contains(loginSchema)) {
        orderedSchemas.add(loginSchema)
    }
    for (schemaOption in schemaOptions) {
        if (lower(schemaOption) != lower(loginSchema)) {
            orderedSchemas.add(schemaOption)
        }
    }
    schemaOptions = orderedSchemas
    metadataConfig.setSchema(selectedSchema)
}
var physicalTableName = (table) => {
    var name = table == null ? null : table.tableName
    if (!oracleSource || blank(name)) {
        return name
    }
    var owner = table.schemaName
    if (blank(owner) || lower(owner) == lower(loginUsername) || textOf(name).indexOf('.') >= 0) {
        return name
    }
    return textOf(owner) + '.' + textOf(name)
}
var annotationStatus = lower(body == null ? null : body.annotationStatus)
var unmarkedOnly = annotationStatus == 'unmarked'
var pendingOnly = annotationStatus == 'pending'
// “暂不处理”只读取已保存的 db_table_t 草稿快照并在控制库分页，
// 不再为了筛选该分类枚举全部物理表。物理表删除仍由用户显式“重新探查”确认。
if (pendingOnly) {
    var safePendingPageNo = pageNo == null || pageNo < 1 ? 1 : pageNo
    var safePendingPageSize = pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 100)
    var pendingQuery = db.table('db_table_t').where()
        .eq('tenant_id', tenantId)
        .eq('datasource_id', dbId)
        .eq('is_del', 0)
        .in('business_type', ['暂不处理', '不确定'])
    if (!blank(keyword)) {
        pendingQuery.like('table_name', '%' + keyword + '%')
    }
    var pendingPage = pendingQuery.orderBy('updated_time', 'desc')
        .page(safePendingPageSize, (safePendingPageNo - 1) * safePendingPageSize)
    var pendingRows = pendingPage == null || pendingPage.list == null ? [] : pendingPage.list
    var pendingTotal = pendingPage == null || pendingPage.total == null ? pendingRows.size() : pendingPage.total
    var pendingPreviewRows = new ArrayList()
    for (pendingRow in pendingRows) {
        var pendingTableName = firstValue(pendingRow, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
        if (blank(pendingTableName)) {
            continue
        }
        var pendingResourceName = firstValue(pendingRow, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn'])
        pendingPreviewRows.add({
            dbId: dbId,
            tid: firstValue(pendingRow, ['tid']),
            tableId: firstValue(pendingRow, ['tid']),
            tableName: pendingTableName,
            tableNameEn: pendingTableName,
            physicalTableComment: pendingResourceName,
            savedResourceName: pendingResourceName,
            tableNameCn: pendingResourceName,
            tableComment: pendingResourceName,
            tableType: firstValue(pendingRow, ['tableType', 'table_type']),
            businessType: firstValue(pendingRow, ['businessType', 'business_type']),
            businessTypeReason: firstValue(pendingRow, ['businessTypeReason', 'business_type_reason']),
            schemaName: firstValue(pendingRow, ['schemaName', 'schema_name']),
            recordCount: firstValue(pendingRow, ['recordCount', 'record_count']) == null ? 0 : firstValue(pendingRow, ['recordCount', 'record_count']),
            fieldCount: firstValue(pendingRow, ['fieldCount', 'field_count', 'columnCount', 'column_count']) == null ? 0 : firstValue(pendingRow, ['fieldCount', 'field_count', 'columnCount', 'column_count']),
            storageSize: firstValue(pendingRow, ['storageSize', 'storage_size', 'totalSizeFormatted', 'total_size_formatted']) || '0 B',
            totalSizeFormatted: firstValue(pendingRow, ['totalSizeFormatted', 'total_size_formatted', 'storageSize', 'storage_size']) || '0 B',
            totalSizeBytes_num: firstValue(pendingRow, ['totalSizeBytes', 'total_size_bytes']) == null ? 0 : firstValue(pendingRow, ['totalSizeBytes', 'total_size_bytes']),
            annotated: false,
            registered: false
        })
    }
    return {
        found: true,
        dbId: dbId,
        pageNo: safePendingPageNo,
        pageSize: safePendingPageSize,
        total: pendingTotal,
        physicalTotal: pendingTotal,
        annotationStatus: annotationStatus,
        oracle: oracleSource,
        selectedSchema: selectedSchema,
        schemaOptions: schemaOptions,
        rows: pendingPreviewRows,
        list: pendingPreviewRows
    }
}
// validationOnly 不返回表明细，只返回覆盖全部物理表的下一步校验汇总。
// 它不能使用浏览器当前页数据，否则大库分页时会错误拦截“全部暂不处理”的场景。
var validationOnlyValue = body == null ? null : body.validationOnly
var validationOnly = validationOnlyValue == true
if (!validationOnly && validationOnlyValue != null) {
    validationOnly = lower(validationOnlyValue) == 'true'
}
var draftMetadataByName = {}

if (unmarkedOnly) {
    // 标注状态属于平台资产语义，必须在后端完成物理表与 db_table_t 的合并后再分页。
    // 否则先分页物理表、再由前端剔除已标注表，会出现“总数 173 但首屏 0/21 条”的错页。
    var managedRows = db.table('db_table_t').where()
        .eq('tenant_id', tenantId)
        .eq('datasource_id', dbId)
        .eq('is_del', 0)
        .orderBy('updated_time', 'desc')
        .select()
    var registeredNames = new HashSet()
    var registeredNameList = new ArrayList()
    // managedRows are newest first. Evaluate each physical table once so a
    // historical annotated snapshot cannot inflate the current unmarked count.
    var resolvedManagedNames = new HashSet()
    for (managed in managedRows == null ? [] : managedRows) {
        var managedName = lower(firstValue(managed, ['tableName', 'table_name', 'tableNameEn', 'table_name_en']))
        var managedInSelectedSchema = true
        if (oracleSource && !blank(managedName)) {
            var qualifiedManagedName = managedName.indexOf('.') > 0
            var selectedPrefix = lower(selectedSchema) + '.'
            if (lower(selectedSchema) == lower(loginSchema)) {
                managedInSelectedSchema = !qualifiedManagedName || managedName.indexOf(selectedPrefix) == 0
            } else {
                managedInSelectedSchema = managedName.indexOf(selectedPrefix) == 0
            }
        }
        if (!blank(managedName) && managedInSelectedSchema && !resolvedManagedNames.contains(managedName)) {
            resolvedManagedNames.add(managedName)
            var annotatedValue = lower(rowValue(managed, ['annotated']))
            var isAnnotated = annotatedValue == '1' || annotatedValue == 'true'
            if (isAnnotated) {
                if (!registeredNames.contains(managedName)) {
                    registeredNames.add(managedName)
                    registeredNameList.add(managedName)
                }
            } else if (draftMetadataByName[managedName] == null) {
                // 重新标注后的资产仍保留用户已填写的中文名和业务分类，重新进入第二步时回填。
                draftMetadataByName[managedName] = managed
            }
        }
    }

    if (validationOnly) {
        var validationPhysicalTables = new ArrayList()
        if (structuredSource) {
            var summaryPageNo = 1
            var summaryPageSize = 500
            var summaryExpectedTotal = 0
            while (true) {
                var summaryPage = structuredSourceProbeService.getTablesPage(asset, summaryPageNo, summaryPageSize, null)
                var summaryRows = summaryPage == null || summaryPage.rows == null ? [] : summaryPage.rows
                for (summaryRow in summaryRows) {
                    validationPhysicalTables.add(summaryRow)
                }
                summaryExpectedTotal = summaryPage == null || summaryPage.total == null ? validationPhysicalTables.size() : summaryPage.total
                if (summaryRows.size() < summaryPageSize || validationPhysicalTables.size() >= summaryExpectedTotal) {
                    break
                }
                summaryPageNo = summaryPageNo + 1
            }
        } else {
            validationPhysicalTables = metadataExplorerService.getTables(metadataConfig)
        }

        var totalUnmarked = 0
        var pendingUnmarked = 0
        var blockingUnmarked = 0
        for (physical in validationPhysicalTables == null ? [] : validationPhysicalTables) {
            var physicalName = lower(physicalTableName(physical))
            if (blank(physicalName) || registeredNames.contains(physicalName)) {
                continue
            }
            totalUnmarked = totalUnmarked + 1
            var draftMetadata = draftMetadataByName[physicalName]
            var draftType = lower(firstValue(draftMetadata, ['businessType', 'business_type']))
            if (draftType == '暂不处理' || draftType == '不确定') {
                pendingUnmarked = pendingUnmarked + 1
            } else {
                blockingUnmarked = blockingUnmarked + 1
            }
        }
        return {
            found: true,
            dbId: dbId,
            totalUnmarked: totalUnmarked,
            pendingUnmarked: pendingUnmarked,
            blockingUnmarked: blockingUnmarked,
            canProceed: blockingUnmarked == 0,
            oracle: oracleSource,
            selectedSchema: selectedSchema,
            schemaOptions: schemaOptions
        }
    }

    if (structuredSource) {
        // 文件、对象存储和 API 等结构化来源的单页上限是 500；这里必须继续读取
        // 后续页，再合并已登记快照并计算“未标注”总数。只取第一页会把第 501 条
        // 之后的表误判为不存在，页面随即错误显示“数据表标注已完成”。
        var allPhysicalTables = new ArrayList()
        var structuredPageNo = 1
        var structuredPageSize = 500
        var structuredExpectedTotal = 0
        while (true) {
            var structuredPage = structuredSourceProbeService.getTablesPage(
                asset,
                structuredPageNo,
                structuredPageSize,
                textOf(body == null ? null : body.keyword)
            )
            var structuredRows = structuredPage == null || structuredPage.rows == null ? [] : structuredPage.rows
            for (structuredRow in structuredRows) {
                allPhysicalTables.add(structuredRow)
            }
            structuredExpectedTotal = structuredPage == null || structuredPage.total == null
                ? allPhysicalTables.size()
                : structuredPage.total
            if (structuredRows.size() < structuredPageSize || allPhysicalTables.size() >= structuredExpectedTotal) {
                break
            }
            structuredPageNo = structuredPageNo + 1
        }
        physicalTotal = structuredExpectedTotal
        var unmarkedTables = new ArrayList()
        for (physical in allPhysicalTables == null ? [] : allPhysicalTables) {
            var physicalName = lower(physical.tableName)
            var physicalComment = lower(physical.tableComment)
            var keywordMatched = blank(keyword) || physicalName.indexOf(keyword) >= 0 || physicalComment.indexOf(keyword) >= 0
            if (keywordMatched && !registeredNames.contains(physicalName)) {
                unmarkedTables.add(physical)
            }
        }
        total = unmarkedTables.size()
        var safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo
        var safePageSize = pageSize == null || pageSize < 1 ? 100 : pageSize
        var fromIndex = (safePageNo - 1) * safePageSize
        var toIndex = Math.min(fromIndex + safePageSize, total)
        tables = fromIndex >= total ? [] : unmarkedTables.subList(fromIndex, toIndex)
    } else {
        var unmarkedPage = metadataExplorerService.getTablesPageExcluding(
            metadataConfig,
            pageNo == null || pageNo < 1 ? 1 : pageNo,
            pageSize == null || pageSize < 1 ? 100 : pageSize,
            textOf(body == null ? null : body.keyword),
            registeredNameList
        )
        tables = unmarkedPage == null || unmarkedPage.rows == null ? [] : unmarkedPage.rows
        total = unmarkedPage == null || unmarkedPage.total == null ? 0 : unmarkedPage.total
        physicalTotal = total + registeredNames.size()
    }
} else if (structuredSource) {
    var structuredPage = structuredSourceProbeService.getTablesPage(
        asset,
        paged ? pageNo : 1,
        paged ? pageSize : (unlimited ? 500 : limit),
        textOf(body == null ? null : body.keyword)
    )
    tables = structuredPage == null || structuredPage.rows == null ? [] : structuredPage.rows
    total = structuredPage == null || structuredPage.total == null ? 0 : structuredPage.total
    physicalTotal = total
} else if (paged) {
    var page = metadataExplorerService.getTablesPage(metadataConfig, pageNo, pageSize, textOf(body == null ? null : body.keyword))
    tables = page == null || page.rows == null ? [] : page.rows
    total = page == null || page.total == null ? 0 : page.total
    physicalTotal = total
} else {
    tables = metadataExplorerService.getTables(metadataConfig)
    total = tables == null ? 0 : tables.size()
    physicalTotal = total
}

// 只有无关键字时，total 才代表该数据源的物理库表总数；关键字过滤后的 total 不能回写。
if (blank(body == null ? null : body.keyword) && physicalTotal > 0) {
    var oldDatasource = db.table('db_datasource_t').where().eq('tid', dbId).eq('is_del', 0).selectOne()
    var oldTableNum = oldDatasource == null ? 0 : (rowValue(oldDatasource, ['tableNum', 'table_num']) == null ? 0 : rowValue(oldDatasource, ['tableNum', 'table_num'])::int)
    if (physicalTotal > oldTableNum) {
        db.table('db_datasource_t').primary('tid').update({ tid: dbId, table_num: physicalTotal, updated_time: new Date() }, true)
    }
}

var rows = []
for (table in tables == null ? [] : tables) {
    var tableName = physicalTableName(table)
    var tableComment = blank(table.tableComment) ? tableName : table.tableComment
    var draftMetadata = draftMetadataByName[lower(tableName)]
    // Keep the physical comment separately for traceability.  The data-resource
    // name is editable business metadata, so an already saved name must be
    // returned as the display value instead of being overwritten by an older
    // physical-table comment on blur or after a reload.
    var savedResourceName = firstValue(draftMetadata, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn'])
    var displayResourceName = blank(savedResourceName) ? tableComment : savedResourceName
    if (!paged && !blank(keyword) && lower(tableName).indexOf(keyword) < 0 && lower(tableComment).indexOf(keyword) < 0) {
        continue
    }
    rows.add({
        dbId: dbId,
        tableName: tableName,
        tableNameEn: tableName,
        physicalTableComment: tableComment,
        savedResourceName: savedResourceName,
        tableNameCn: displayResourceName,
        tableComment: displayResourceName,
        tableType: table.tableType,
        businessType: firstValue(draftMetadata, ['businessType', 'business_type']),
        businessTypeReason: firstValue(draftMetadata, ['businessTypeReason', 'business_type_reason']),
        schemaName: table.schemaName,
        recordCount: table.rowCount == null ? 0 : table.rowCount,
        fieldCount: table.columnCount == null ? 0 : table.columnCount,
        columns: structuredSource ? table.columns : null,
        storageSize: blank(table.totalSizeFormatted) ? '0 B' : table.totalSizeFormatted,
        totalSizeFormatted: blank(table.totalSizeFormatted) ? '0 B' : table.totalSizeFormatted,
        totalSizeBytes_num: table.totalSizeBytes == null ? 0 : table.totalSizeBytes
    })
    if (!paged && !unlimited && rows.size() >= limit) {
        break
    }
}

if (paged) {
    return {
        pageNo: pageNo == null || pageNo < 1 ? 1 : pageNo,
        pageSize: pageSize == null || pageSize < 1 ? 50 : pageSize,
        total: total,
        physicalTotal: physicalTotal,
        annotationStatus: annotationStatus,
        oracle: oracleSource,
        selectedSchema: selectedSchema,
        schemaOptions: schemaOptions,
        rows: rows,
        list: rows
    }
}
return rows
