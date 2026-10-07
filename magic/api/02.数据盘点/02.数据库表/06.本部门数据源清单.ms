{
  "properties" : { },
  "id" : "datapd_dept_datasources_01",
  "script" : null,
  "groupId" : "datapd_database_group_01",
  "name" : "06.本部门数据源清单",
  "createTime" : null,
  "updateTime" : 1791109965040,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/departmentDataSources",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\"mode\":\"summary\"}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "",
  "description" : "本部门数据源管理：mode=summary 返回轻量概要与批量统计；mode=tables 按 datasourceId 分页读取已登记表。当前租户及菜单数据权限校验；默认模式兼容历史调用者；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；涉及 db_datasource_t、db_table_t、sym_application_t、rm_org_t 等业务表。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：06.本部门数据源清单
// 路由：POST /dst/database/departmentDataSources
// 业务说明：本部门数据源管理：mode=summary 返回轻量概要与批量统计；mode=tables 按 datasourceId 分页读取已登记表；mode=reportTables 批量分页读取可见上报数据源的业务表和日志表。当前租户及菜单数据权限校验；默认模式兼容历史调用者；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；涉及 db_datasource_t、db_table_t、sym_application_t、rm_org_t 等业务表。
import "@post:/sym/user/me" as me
import tenantRuntime
import tenantEs
import dataScope
import tenantAccounts
import '@/common/configData' as configData

/*
 * 本部门数据源管理接口。
 * 数据读取：优先 Elasticsearch dataassets 索引；ES 数据源或表投影缺失时批量回退 db_datasource_t/db_table_t，禁止列表循环回查 MySQL。
 * 安全边界：不信任前端 orgId，始终使用当前会话 /sym/user/me 的部门过滤数据源。
 * mode=summary：数据源概要、MySQL 批量统计、ES size=0 变更聚合；mode=tables：权限校验后分页读取已登记表。
 * 默认模式兼容历史调用者；只读，不连接外部来源库、不采集字段。
 */

var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var numberValue = (value) => value == null ? 0 : value::int

// CODEX_ACTIVE_DATASOURCE_GUARD: 以当前租户业务库的有效记录过滤 ES 残留投影。
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '无法识别当前租户'
}

var userInfo = me()
var orgId = userInfo?.orgId
var scopeResource = 'MENU:2081000000000000002'

var requestMode = text(body?.mode).trim()
if (!blank(requestMode) && !['summary', 'tables', 'reportTables'].contains(requestMode)) { exit 400, '不支持的清单查询模式' }
if (requestMode == 'reportTables') {
    // 一页报告的表跨数据源批量读取；前端不得对每个数据源再发一次表查询。
    var submittedIds = body?.datasourceIds
    if (!(submittedIds instanceof List) || submittedIds.size() == 0 || submittedIds.size() > 200) {
        exit 400, '数据源标识必须是 1 至 200 项的数组'
    }
    var requestedIds = []
    for (rawId in submittedIds) {
        var sourceId = text(rawId).trim()
        if (blank(sourceId) || sourceId.length() > 32) { exit 400, '数据源标识不合法' }
        if (!requestedIds.contains(sourceId)) { requestedIds.add(sourceId) }
    }
    // 先按租户过滤主表，再应用当前菜单的角色数据范围，任何越权项使整批失败。
    var allowedSources = db.select("""
        select tid, org_id, created_by,
               coalesce(
                   nullif(json_unquote(json_extract(if(json_valid(pool_cfg), pool_cfg, '{}'), '$.accessMode')), ''),
                   nullif(json_unquote(json_extract(if(json_valid(pool_cfg), pool_cfg, '{}'), '$.dataAccessMode')), ''),
                   ''
               ) as accessMode
          from db_datasource_t
         where tenant_id=#{tenantId} and is_del=0 and tid in
               <foreach collection="requestedIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
    """)
    allowedSources = dataScope.visibleDataSourcesFor(scopeResource, allowedSources)
    if (allowedSources == null || allowedSources.size() != requestedIds.size()) {
        exit 403, '数据源不存在或无权访问'
    }
    var allowedIds = []
    for (source in allowedSources) {
        var accessMode = text(source.accessmode || source.accessMode).trim().toLowerCase()
        if (accessMode != 'upload') { exit 403, '数据源不是数据上报方式' }
        allowedIds.add(text(source.tid))
    }
    var reportPageNum = body?.pageNum == null ? 1 : body.pageNum::int
    var reportPageSize = body?.pageSize == null ? 100 : body.pageSize::int
    if (reportPageNum < 1) { reportPageNum = 1 }
    if (reportPageSize < 1) { reportPageSize = 100 }
    if (reportPageSize > 100) { reportPageSize = 100 }
    // 与单源元数据表接口保持相同去重优先级：同名先取已标注，再取最近更新。
    // 过滤业务类型置于去重之后，不能让较旧的同名业务表重新进入结果。
    var reportSql = """
        select ranked.tid, ranked.datasource_id, ranked.table_name, ranked.table_name_cn,
               ranked.table_comment, ranked.table_type, ranked.business_type,
               ranked.field_count, ranked.record_count, ranked.timestamp_field,
               ranked.updated_time
          from (
            select t.tid, t.datasource_id, t.table_name, t.table_name_cn,
                   t.table_comment, t.table_type, t.business_type, t.annotated,
                   t.field_count, t.record_count, t.timestamp_field, t.updated_time,
                   row_number() over (
                       partition by t.datasource_id, lower(t.table_name)
                       order by coalesce(t.annotated, 0) desc, t.updated_time desc, t.tid desc
                   ) as name_rank
              from db_table_t t
             where t.tenant_id=#{tenantId} and t.is_del=0
               and t.datasource_id in
                   <foreach collection="allowedIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
          ) ranked
         where ranked.name_rank=1 and ranked.annotated=1
           and ranked.business_type in ('业务表', '日志表')
         order by ranked.updated_time desc, ranked.tid desc
    """
    var reportPage = db.page(reportSql, reportPageSize, (reportPageNum - 1) * reportPageSize)
    var reportRows = []
    for (row in (reportPage.list == null ? [] : reportPage.list)) {
        reportRows.add({
            tid: row.tid, datasourceId: row.datasourceId,
            tableName: row.tableName, tableNameCn: row.tableNameCn,
            tableComment: row.tableComment, tableType: row.tableType,
            businessType: row.businessType, fieldCount: numberValue(row.fieldCount),
            recordCount: numberValue(row.recordCount), timestampField: row.timestampField,
            businessTimeField: '', updatedTime: row.updatedTime
        })
    }
    return { pageNum: reportPageNum, pageSize: reportPageSize,
        total: numberValue(reportPage.total), list: reportRows }
}
if (requestMode == 'tables') {
    var datasourceId = text(body?.datasourceId).trim()
    if (blank(datasourceId)) { exit 400, '缺少数据源标识' }
    var sourceRows = db.table('db_datasource_t').where()
        .eq('tid', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).select()
    sourceRows = dataScope.visibleDataSourcesFor(scopeResource, sourceRows)
    if (sourceRows == null || sourceRows.size() == 0) { exit 404, '数据源不存在或无权访问' }
    var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
    var pageSize = body?.pageSize == null ? 10 : body.pageSize::int
    if (pageNum < 1) { pageNum = 1 }
    if (pageSize < 1) { pageSize = 10 }
    if (pageSize > 100) { pageSize = 100 }
    var escapedSourceId = datasourceId.replace("'", "''")
    var escapedPageTenant = tenantId.replace("'", "''")
    var pageWhere = " datasource_id = '" + escapedSourceId + "' and tenant_id = '" + escapedPageTenant
        + "' and ifnull(is_del, 0) = 0 and asset_status = 2"
        + " and lower(ifnull(business_type, '')) not in ('暂不处理', '暂不登记', '不确定', 'deferred')"
    var totalRows = db.select("select count(1) as total from db_table_t where " + pageWhere)
    var total = numberValue(totalRows[0].total)
    var maxPage = total == 0 ? 1 : ((total + pageSize - 1) / pageSize)::int
    if (pageNum > maxPage) { pageNum = maxPage }
    var offset = (pageNum - 1) * pageSize
    // 仅返回当前页的展示字段，不读取连接信息、字段快照或治理 JSON。
    var pageRows = db.select("""
        select tid, datasource_id as datasourceId, table_name as tableName,
               table_name_en as tableNameEn, table_name_cn as tableNameCn,
               table_comment as tableComment, table_type as tableType,
               business_type as businessType, field_count as fieldCount,
               asset_status as assetStatus, created_time as createdTime, updated_time as updatedTime
          from db_table_t where ${pageWhere}
         order by created_time desc, updated_time desc, table_name asc, tid asc
         limit ${offset}, ${pageSize}
    """)
    var pageList = []
    for (row in pageRows) {
        pageList.add({ tid: row.tid, id: row.tid, datasourceId: row.datasourceid,
            dbId: row.datasourceid, tableName: row.tablename || row.tablenameen,
            tableNameCn: row.tablenamecn || row.tablecomment, tableComment: row.tablecomment,
            tableType: row.tabletype, businessType: row.businesstype, fieldCount: row.fieldcount,
            assetStatus: row.assetstatus, createdTime: row.createdtime, updatedTime: row.updatedtime })
    }
    // 只为当前页补齐显式变更标签，禁止下载数据源下所有表文档。
    var pageIds = []
    for (item in pageList) { pageIds.add(text(item.tid)) }
    if (pageIds.size() > 0) {
        var pageChanges = tenantEs.index(configData().es_dataassets).search({
            size: pageSize, _source: ['tid', 'changeStatus', 'change_status'],
            query: { bool: { must: [
                { term: { assetType: 'table' } }, { ids: { values: pageIds } }
            ] } }
        })
        var changeById = {}
        for (hit in (pageChanges?.hits?.hits == null ? [] : pageChanges.hits.hits)) {
            changeById[text(hit?._source?.tid || hit._id)] = text(hit?._source?.changeStatus || hit?._source?.change_status).toLowerCase()
        }
        for (item in pageList) { item.changeStatus = changeById[text(item.tid)] || 'unchanged' }
    }
    return { datasourceId: datasourceId, pageNum: pageNum, pageSize: pageSize, total: total, list: pageList }
}
var summaryOnly = requestMode == 'summary'


// 页面只提供关键字搜索；最终范围由“数据源管理”菜单的角色数据权限统一过滤。
var keywordText = text(body?.keyword).trim()
var datasourceMust = [
    { term: { assetType: 'db' } }
]
if (!blank(keywordText)) {
    // 关键字也在 ES 过滤，前端不能借搜索绕过部门权限。
    datasourceMust.add({
        bool: {
            should: [
                { wildcard: { dbName: '*' + keywordText + '*' } },
                { wildcard: { appName: '*' + keywordText + '*' } },
                { wildcard: { database: '*' + keywordText + '*' } },
                { wildcard: { dbType: '*' + keywordText + '*' } }
            ],
            minimum_should_match: 1
        }
    })
}

// ES 仅作为候选投影；全部 / 本部门 / 本人范围在主表过滤阶段统一裁剪。
var datasourceSearch = tenantEs.index(configData().es_dataassets).search({
    _source: summaryOnly ? ['tid', 'id', 'dbName', 'appId', 'appName', 'dbType', 'databaseType',
        'database', 'schema', 'host', 'port', 'accessMode', 'dataAccessMode', 'orgId', 'orgName',
        'tableNum', 'assetStatus', 'showConnect', 'connectionStatus', 'description',
        'createdTime', 'updatedTime', 'createdBy', 'storageDomain', 'nodeId'] : true,
    size: 1000,
    track_total_hits: true,
    query: {
        bool: {
            must: datasourceMust,
            must_not: [
                { wildcard: { dbName: 'codex_*' } },
                { wildcard: { database: 'codex_*' } }
            ]
        }
    },
    sort: [ { updatedTime: { order: 'desc', unmapped_type: 'keyword' } } ]
})

var result = []
var resultById = {}
var datasourceIds = []
for (hit in (datasourceSearch?.hits?.hits == null ? [] : datasourceSearch.hits.hits)) {
    if (hit?._source == null) {
        continue
    }
    var item = hit._source
    item.tid = item.tid || hit._id
    item.id = item.id || item.tid
    item.orgName = userInfo?.orgName
    // 数据源 ES 投影里可能已经有最近一次探查得到的物理总表数。
    // 这个总数和 db_table_t 已保存行数不是同一个口径，不能在后续统计时被清零丢掉。
    item.exploredTableNum = numberValue(item.tableNum || item.table_num || item.tableCount || item.table_count)
    item.tableNum = 0
    item.annotatedTableNum = 0
    item.unannotatedTableNum = 0
    item.registeredTableNum = 0
    item.unregisteredTableNum = 0
    item.businessTableNum = 0
    item.dictionaryTableNum = 0
    item.logTableNum = 0
    item.coreTableNum = 0
    item.registeredCoreTableNum = 0
    item.deferredTableNum = 0
    item.issueTableNum = 0
    item.problemTableNum = 0
    item.changedTableNum = 0
    item.addedTableNum = 0
    item.modifiedTableNum = 0
    item.deletedTableNum = 0
    item.tables = []
    datasourceIds.add(item.tid)
    resultById[text(item.tid)] = true
    result.add(item)
}

// ES 数据源主投影可能存在刷新延迟。按当前租户批量回表补齐主卡片，
// 并由菜单级角色范围过滤，避免前端参数扩大可见范围。
var escapedTenantId = tenantId.replace("'", "''")
var keywordSql = ""
if (!blank(keywordText)) {
    var escapedKeyword = keywordText.replace("'", "''")
    keywordSql = " and (d.db_name like '%" + escapedKeyword + "%' or ifnull(a.app_name, '') like '%" + escapedKeyword + "%' or ifnull(d.db_type, '') like '%" + escapedKeyword + "%')"
}
var activeDatasourceById = {}
var datasourceRows = db.select("""
    select d.tid,
           d.db_name as dbName,
           d.remark as description,
           d.app_id as appId,
           a.app_name as appName,
           d.db_type as dbType,
           d.database_type as databaseType,
           coalesce(
             nullif(json_unquote(json_extract(if(json_valid(d.pool_cfg), d.pool_cfg, '{}'), '$.accessMode')), ''),
             nullif(json_unquote(json_extract(if(json_valid(d.pool_cfg), d.pool_cfg, '{}'), '$.dataAccessMode')), ''),
             ''
           ) as accessMode,
           d.org_id as orgId,
           d.created_by as createdBy,
           o.name as orgName,
           o.org_path as orgPath,
           ifnull(d.table_num, 0) as exploredTableNum,
           d.asset_status as assetStatus,
           d.show_connect as showConnect,
           d.connection_status as connectionStatus,
           d.updated_time as updatedTime,
           d.created_time as createdTime,
           d.storage_domain as storageDomain,
           d.node_id as nodeId
      from db_datasource_t d
      left join sym_application_t a
        on a.tid = d.app_id
       and ifnull(a.is_del, 0) = 0
      left join rm_org_t o
        on o.id = d.org_id
       and ifnull(o.deleted, 0) = 0
     where ifnull(d.is_del, 0) = 0
       and d.tenant_id = '${escapedTenantId}'
       and ifnull(d.db_name, '') not like 'codex_%'
       ${keywordSql}
     order by d.updated_time desc
     limit 1000
""")
// 按“数据源管理”菜单绑定的角色范围过滤：全部 / 本部门 / 本人登记。
datasourceRows = dataScope.visibleDataSourcesFor(scopeResource, datasourceRows)
// 创建人主数据位于控制库；批量解析可见数据源的显示名称，避免逐卡片查询。
var creatorIds = []
for (row in datasourceRows) {
    var creatorId = text(row.createdby)
    if (!blank(creatorId) && !creatorIds.contains(creatorId)) {
        creatorIds.add(creatorId)
    }
}
var creatorNames = tenantAccounts.names(creatorIds)
for (row in datasourceRows) {
    var rowTid = text(row.tid)
    if (blank(rowTid)) {
        continue
    }
    activeDatasourceById[rowTid] = true
    if (resultById[rowTid] == true) {
        // ES 投影可能是旧版本；部门树以业务库中的组织信息为准，补全/覆盖现有投影。
        for (existingItem in result) {
            if (text(existingItem.tid) == rowTid) {
                existingItem.orgId = row.orgid
                existingItem.orgName = row.orgname || userInfo?.orgName
                existingItem.orgPath = row.orgpath
                existingItem.appId = row.appid || existingItem.appId
                existingItem.appName = row.appname || existingItem.appName
                existingItem.dbType = row.dbtype || existingItem.dbType
                existingItem.databaseType = row.databasetype || existingItem.databaseType
                existingItem.accessMode = row.accessmode || existingItem.accessMode
                existingItem.assetStatus = row.assetstatus
                // ES 可能仍是探查前的连接信息；卡片的接入方式展示以业务主表为准。
                existingItem.showConnect = row.showconnect
                existingItem.connectionStatus = row.connectionstatus || existingItem.connectionStatus
                existingItem.createdBy = row.createdby
                existingItem.createdByName = creatorNames[text(row.createdby)] || ''
                existingItem.createdTime = row.createdtime || existingItem.createdTime
                existingItem.updatedTime = row.updatedtime || existingItem.updatedTime
                break
            }
        }
        continue
    }
    var item = {
        tid: rowTid,
        id: rowTid,
        dbName: row.dbname,
        description: row.description,
        appId: row.appid,
        appName: row.appname,
        dbType: row.dbtype,
        databaseType: row.databasetype,
        accessMode: row.accessmode,
        orgId: row.orgid,
        createdBy: row.createdby,
        createdByName: creatorNames[text(row.createdby)] || '',
        orgName: row.orgname || userInfo?.orgName,
        orgPath: row.orgpath,
        exploredTableNum: numberValue(row.exploredtablenum),
        tableNum: 0,
        annotatedTableNum: 0,
        unannotatedTableNum: 0,
        registeredTableNum: 0,
        unregisteredTableNum: 0,
        businessTableNum: 0,
        dictionaryTableNum: 0,
        logTableNum: 0,
        coreTableNum: 0,
        registeredCoreTableNum: 0,
        deferredTableNum: 0,
        issueTableNum: 0,
        problemTableNum: 0,
        changedTableNum: 0,
        addedTableNum: 0,
        modifiedTableNum: 0,
        deletedTableNum: 0,
        assetStatus: row.assetstatus,
        showConnect: row.showconnect,
        connectionStatus: row.connectionstatus,
        updatedTime: row.updatedtime,
        createdTime: row.createdtime,
        storageDomain: row.storagedomain,
        nodeId: row.nodeid,
        tables: []
    }
    datasourceIds.add(rowTid)
    resultById[rowTid] = true
    result.add(item)
}

// ES 只作为加速投影；已在业务库软删除或不属于当前租户的主记录不得返回。
var activeResult = []
var activeDatasourceIds = []
for (item in result) {
    var itemTid = text(item.tid)
    if (activeDatasourceById[itemTid] == true) {
        activeResult.add(item)
        activeDatasourceIds.add(itemTid)
    }
}
result = activeResult
datasourceIds = activeDatasourceIds

// 旧调用者保留兼容；管理首屏跳过所有表文档读取及全量表循环。
if (!summaryOnly) {
var tableGroups = {}
if (datasourceIds.size() > 0) {
    // 第二次 ES 查询：批量读取当前结果集下的表，避免每个数据源分别查询数据库。
    var tableSearch = null
    try {
        tableSearch = tenantEs.index(configData().es_dataassets).search({
        size: 10000,
        query: {
            bool: {
                must: [
                    { term: { assetType: 'table' } },
                    {
                        bool: {
                            should: [
                                { terms: { datasourceId: datasourceIds } },
                                { terms: { dbId: datasourceIds } }
                            ],
                            minimum_should_match: 1
                        }
                    }
                ]
            }
        },
        sort: [
            { updatedTime: { order: 'desc', unmapped_type: 'keyword' } },
            { tableName: { order: 'asc', unmapped_type: 'keyword' } }
        ]
        })
    } catch (tableSearchError) {
        // 表索引超时不能阻断数据源管理；下方按 datasource_id 批量回表补齐。
        tableSearch = null
    }

    for (hit in (tableSearch?.hits?.hits == null ? [] : tableSearch.hits.hits)) {
        if (hit?._source == null) {
            continue
        }
        var table = hit._source
        table.tid = table.tid || hit._id
        table.id = table.id || table.tid
        var datasourceId = text(table.datasourceId || table.dbId)
        if (blank(datasourceId)) {
            continue
        }
        var rawChangeStatus = text(table.changeStatus || table.change_status).toLowerCase()

        // 变更类型只使用显式探查结果，不再从登记工作流字段猜测。
        if (blank(rawChangeStatus)) {
            rawChangeStatus = 'unchanged'
        }
        table.changeStatus = rawChangeStatus
        table.hasIssue = text(table.assetStatus) != '2'
        if (tableGroups[datasourceId] == null) {
            tableGroups[datasourceId] = []
        }
        tableGroups[datasourceId].add(table)
    }

    // ES 表投影历史上未包含 createdTime。这里按当前数据源一次性回读表主表的
    // 创建/修改时间，并回填到 ES 表对象；避免页面误把修改时间当创建时间，也避免逐表查询。
    var tableTimeById = {}
    var tableTimeByName = {}
    if (datasourceIds.size() > 0) {
        var timeQuotedDatasourceIds = []
        for (timeDatasourceId in datasourceIds) {
            timeQuotedDatasourceIds.add("'" + text(timeDatasourceId).replace("'", "''") + "'")
        }
        var tableTimeRows = db.select("""
            select tid,
                   datasource_id as datasourceId,
                   table_name as tableName,
                   created_time as createdTime,
                   updated_time as updatedTime,
                   asset_status as assetStatus
             from db_table_t
             where ifnull(is_del, 0) = 0
               and tenant_id = '${escapedTenantId}'
               and datasource_id in (${timeQuotedDatasourceIds.join(',')})
        """)
        for (timeRow in tableTimeRows) {
            tableTimeById[text(timeRow.tid)] = timeRow
            tableTimeByName[text(timeRow.datasourceid) + '::' + text(timeRow.tablename).toLowerCase()] = timeRow
        }
        for (timeDatasourceId in datasourceIds) {
            var timeGroup = tableGroups[text(timeDatasourceId)]
            if (timeGroup == null) {
                continue
            }
            for (table in timeGroup) {
                var timeRow = tableTimeById[text(table.tid || table.id)]
                if (timeRow == null) {
                    var timeTableName = text(table.tableName || table.table_name || table.sourceTableName || table.tableNameEn).toLowerCase()
                    timeRow = tableTimeByName[text(timeDatasourceId) + '::' + timeTableName]
                }
                if (timeRow != null) {
                    table.createdTime = timeRow.createdtime
                    table.updatedTime = timeRow.updatedtime
                    table.assetStatus = timeRow.assetstatus
                    table.hasIssue = text(timeRow.assetstatus) != '2'
                }
            }
        }
    }

    var missingDatasourceIds = []
    for (datasourceId in datasourceIds) {
        var group = tableGroups[text(datasourceId)]
        if (group == null || group.size() == 0) {
            missingDatasourceIds.add(datasourceId)
        }
    }

    // ES 表投影未刷新或缺失时，按 datasource_id 批量从 db_table_t 补齐统计和展开清单；
    // 仍然避免逐数据源回表查询，保证本部门页面不会因为单行循环拖慢。
    if (missingDatasourceIds.size() > 0) {
        var quotedIds = []
        for (missingId in missingDatasourceIds) {
            quotedIds.add("'" + text(missingId).replace("'", "''") + "'")
        }
        var dbTableRows = db.select("""
            select tid,
                   datasource_id as datasourceId,
                   table_name as tableName,
                   table_name_en as tableNameEn,
                   table_name_cn as tableNameCn,
                   table_comment as tableComment,
                   table_type as tableType,
                   business_type as businessType,
                   field_count as fieldCount,
                   annotated,
                   asset_status as assetStatus,
                   updated_time as updatedTime,
                   created_time as createdTime
              from db_table_t
             where ifnull(is_del, 0) = 0
               and tenant_id = '${escapedTenantId}'
               and datasource_id in (${quotedIds.join(',')})
             order by table_name asc
        """)
        for (row in dbTableRows) {
            var datasourceId = text(row.datasourceid)
            if (blank(datasourceId)) {
                continue
            }
            var table = {
                tid: row.tid,
                id: row.tid,
                datasourceId: datasourceId,
                dbId: datasourceId,
                tableName: row.tablename || row.tablenameen,
                tableNameCn: row.tablenamecn || row.tablecomment,
                tableComment: row.tablecomment,
                tableType: row.tabletype,
                businessType: row.businesstype,
                fieldCount: row.fieldcount,
                annotated: row.annotated,
                assetStatus: row.assetstatus,
                changeStatus: 'unchanged',
                updatedTime: row.updatedtime,
                createdTime: row.createdtime
            }
            table.hasIssue = text(row.assetstatus) != '2'
            if (tableGroups[datasourceId] == null) {
                tableGroups[datasourceId] = []
            }
            tableGroups[datasourceId].add(table)
        }
    }
}

// 在内存中组装表级统计，确保卡片指标与展开清单来自同一批 ES 文档。
for (item in result) {
    var tables = tableGroups[text(item.tid)]
    item.tables = tables == null ? [] : tables
    item.tableNum = item.tables.size()
    for (table in item.tables) {
        if (numberValue(table.annotated) == 1) {
            item.annotatedTableNum = item.annotatedTableNum + 1
        } else {
            item.unannotatedTableNum = item.unannotatedTableNum + 1
        }

        if (text(table.assetStatus) == '2') {
            item.registeredTableNum = item.registeredTableNum + 1
        } else {
            item.unregisteredTableNum = item.unregisteredTableNum + 1
        }
        var businessType = blank(table.businessType) ? '' : text(table.businessType).toLowerCase()
        var coreTable = ['business', '业务表', 'dict', 'dictionary', '字典表', 'log', '日志表'].contains(businessType)
        var deferredTable = ['暂不处理', '暂不登记', '不确定'].contains(businessType)
        if (coreTable) {
            item.coreTableNum = item.coreTableNum + 1
            if (text(table.assetStatus) == '2') {
                item.registeredCoreTableNum = item.registeredCoreTableNum + 1
            }
        }
        if (deferredTable) {
            item.deferredTableNum = item.deferredTableNum + 1
        }
        if (businessType == 'business' || businessType == '业务表') {
            item.businessTableNum = item.businessTableNum + 1
        } else if (businessType == 'dict' || businessType == 'dictionary' || businessType == '字典表') {
            item.dictionaryTableNum = item.dictionaryTableNum + 1
        } else if (businessType == 'log' || businessType == '日志表') {
            item.logTableNum = item.logTableNum + 1
        }
        if (table.hasIssue) {
            item.issueTableNum = item.issueTableNum + 1
        }
        // 未登记且有问题的同一张表只计一次。
        if (text(table.assetStatus) != '2' || table.hasIssue) {
            item.problemTableNum = item.problemTableNum + 1
        }

        if (table.changeStatus == 'added') {
            item.addedTableNum = item.addedTableNum + 1
            item.changedTableNum = item.changedTableNum + 1
        } else if (table.changeStatus == 'deleted') {
            item.deletedTableNum = item.deletedTableNum + 1
            item.changedTableNum = item.changedTableNum + 1
        } else if (table.changeStatus == 'modified') {
            item.modifiedTableNum = item.modifiedTableNum + 1
            item.changedTableNum = item.changedTableNum + 1
        }
    }
}


}
var mysqlStatsByDb = {}
if (datasourceIds.size() > 0) {
    var quotedStatIds = []
    for (statId in datasourceIds) {
        quotedStatIds.add("'" + text(statId).replace("'", "''") + "'")
    }
    var datasourceStoredTotals = {}
    var datasourceTotalRows = db.select("""
        select tid,
               ifnull(table_num, 0) as tableNum,
               remark as description
          from db_datasource_t
         where ifnull(is_del, 0) = 0
           and tenant_id = '${escapedTenantId}'
           and tid in (${quotedStatIds.join(',')})
    """)
    for (totalRow in datasourceTotalRows) {
        var totalDatasourceId = text(totalRow.tid)
        if (!blank(totalDatasourceId)) {
            datasourceStoredTotals[totalDatasourceId] = {
                tableNum: numberValue(totalRow.tablenum),
                description: totalRow.description
            }
        }
    }

    var statRows = db.select("""
        select datasource_id as datasourceId,
               count(1) as tableNum,
               sum(case when ifnull(annotated, 0) = 1 then 1 else 0 end) as annotatedTableNum,
               sum(case when ifnull(annotated, 0) <> 1 then 1 else 0 end) as unannotatedTableNum,
               sum(case when ifnull(asset_status, 0) = 2 then 1 else 0 end) as registeredTableNum,
               sum(case when lower(ifnull(business_type, '')) in ('business', '业务表') then 1 else 0 end) as businessTableNum,
               sum(case when lower(ifnull(business_type, '')) in ('dict', 'dictionary', '字典表') then 1 else 0 end) as dictionaryTableNum,
               sum(case when lower(ifnull(business_type, '')) in ('log', '日志表') then 1 else 0 end) as logTableNum,
               sum(case when lower(ifnull(business_type, '')) in ('business', '业务表', 'dict', 'dictionary', '字典表', 'log', '日志表') then 1 else 0 end) as coreTableNum,
               sum(case when lower(ifnull(business_type, '')) in ('business', '业务表', 'dict', 'dictionary', '字典表', 'log', '日志表')
                         and ifnull(asset_status, 0) = 2 then 1 else 0 end) as registeredCoreTableNum,
               sum(case when lower(ifnull(business_type, '')) in ('暂不处理', '暂不登记', '不确定') then 1 else 0 end) as deferredTableNum,
               sum(case when ifnull(asset_status, 0) <> 2 then 1 else 0 end) as problemTableNum
          from db_table_t
         where ifnull(is_del, 0) = 0
           and tenant_id = '${escapedTenantId}'
           and datasource_id in (${quotedStatIds.join(',')})
         group by datasource_id
    """)
    for (stat in statRows) {
        var statDatasourceId = text(stat.datasourceid)
        if (!blank(statDatasourceId)) {
            var statTableNum = numberValue(stat.tablenum)
            var statAnnotatedTableNum = numberValue(stat.annotatedtablenum)
            var statRegisteredTableNum = numberValue(stat.registeredtablenum)
            mysqlStatsByDb[statDatasourceId] = {
                tableNum: statTableNum,
                annotatedTableNum: statAnnotatedTableNum,
                unannotatedTableNum: numberValue(stat.unannotatedtablenum),
                registeredTableNum: statRegisteredTableNum,
                unregisteredTableNum: statTableNum - statRegisteredTableNum,
                businessTableNum: numberValue(stat.businesstablenum),
                dictionaryTableNum: numberValue(stat.dictionarytablenum),
                logTableNum: numberValue(stat.logtablenum),
                coreTableNum: numberValue(stat.coretablenum),
                registeredCoreTableNum: numberValue(stat.registeredcoretablenum),
                deferredTableNum: numberValue(stat.deferredtablenum),
                problemTableNum: numberValue(stat.problemtablenum)
            }
        }
    }
    for (item in result) {
        var mysqlStats = mysqlStatsByDb[text(item.tid)]
        var exploredTotal = numberValue(item.exploredTableNum)
        var storedDatasource = datasourceStoredTotals[text(item.tid)]
        var storedTotal = storedDatasource == null ? 0 : numberValue(storedDatasource.tableNum)
        if (storedTotal > exploredTotal) {
            exploredTotal = storedTotal
        }
        if (storedDatasource != null && !blank(storedDatasource.description)) {
            // MySQL 主表是数据源描述的权威来源，覆盖 ES 可能滞后的空投影。
            item.description = storedDatasource.description
        }
        if (mysqlStats != null) {
            var displayTableNum = mysqlStats.tableNum
            if (exploredTotal > displayTableNum) {
                displayTableNum = exploredTotal
            }
            item.tableNum = displayTableNum
            item.annotatedTableNum = mysqlStats.annotatedTableNum
            item.unannotatedTableNum = displayTableNum - mysqlStats.annotatedTableNum
            item.registeredTableNum = mysqlStats.registeredTableNum
            item.unregisteredTableNum = displayTableNum - mysqlStats.registeredTableNum
            item.businessTableNum = mysqlStats.businessTableNum
            item.dictionaryTableNum = mysqlStats.dictionaryTableNum
            item.logTableNum = mysqlStats.logTableNum
            item.coreTableNum = mysqlStats.coreTableNum
            item.registeredCoreTableNum = mysqlStats.registeredCoreTableNum
            item.deferredTableNum = mysqlStats.deferredTableNum
            item.problemTableNum = mysqlStats.problemTableNum
        } else if (exploredTotal > item.tableNum) {
            item.tableNum = exploredTotal
            item.unannotatedTableNum = exploredTotal - item.annotatedTableNum
            item.unregisteredTableNum = exploredTotal - item.registeredTableNum
        }
        if (item.unannotatedTableNum < 0) { item.unannotatedTableNum = 0 }
        if (item.unregisteredTableNum < 0) { item.unregisteredTableNum = 0 }
    }
}


if (summaryOnly && datasourceIds.size() > 0) {
    var sourceFilters = {}
    for (sourceId in datasourceIds) {
        sourceFilters[text(sourceId)] = { bool: { should: [
            { term: { datasourceId: sourceId } }, { term: { dbId: sourceId } }
        ], minimum_should_match: 1 } }
    }
    var changeResult = tenantEs.index(configData().es_dataassets).search({
        size: 0,
        query: { term: { assetType: 'table' } },
        aggs: { bySource: {
            filters: { filters: sourceFilters },
            aggs: { changes: { filters: { filters: {
                added: { bool: { should: [{term:{changeStatus:'added'}},{term:{change_status:'added'}}], minimum_should_match:1 } },
                modified: { bool: { should: [{term:{changeStatus:'modified'}},{term:{change_status:'modified'}}], minimum_should_match:1 } },
                deleted: { bool: { should: [{term:{changeStatus:'deleted'}},{term:{change_status:'deleted'}}], minimum_should_match:1 } }
            } } } }
        } }
    })
    for (item in result) {
        var counts = changeResult?.aggregations?.bySource?.buckets[text(item.tid)]?.changes?.buckets
        item.addedTableNum = numberValue(counts?.added?.doc_count)
        item.modifiedTableNum = numberValue(counts?.modified?.doc_count)
        item.deletedTableNum = numberValue(counts?.deleted?.doc_count)
        item.changedTableNum = item.addedTableNum + item.modifiedTableNum + item.deletedTableNum
        item.remove('tables')
        // 概要必须为轻量投影：不携带凭据、配置、列数组或历史治理 JSON。
        for (key in ['password','username','jdbcUrl','jdbc_url','poolCfg','pool_cfg','columns',
                     'fieldGovernanceConfig','field_governance_config','dictionaryProfiles','dictionaryProfile']) {
            item.remove(key)
        }
    }
}

var sourceSortKey = (item) => {
    var value = item?.updatedTime || item?.updated_time || item?.createdTime || item?.created_time
    return blank(value) ? '' : text(value).replace('T', ' ').replace('Z', '')
}

// Sort after ES and MySQL fallback rows are merged, so newly saved fallback rows do not stay at the end.
result = result.sort((left, right) => {
    var leftKey = sourceSortKey(left)
    var rightKey = sourceSortKey(right)
    if (rightKey > leftKey) {
        return 1
    }
    if (rightKey < leftKey) {
        return -1
    }
    return 0
})

var total = result.size()
return { orgId: orgId, orgName: userInfo?.orgName, total: total, list: result }
