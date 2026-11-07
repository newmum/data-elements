{
  "properties": {},
  "id": "1f475a245fda4ba4a885b3ac4f8e2d59",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "10.Hive原始库数据表分页接口",
  "path": "/hiveTargetPage",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "分发任务数据表分页（保留 POST /ods/hiveTargetPage 路由）。只读；以当前租户资源中心 ODS 层及分域绑定库中登记状态大于 0 的表为真实来源，服务端分页并批量补齐授权链路与目标；与接入分页使用相同 tasks/targetTables 契约，不按 Hive 类型或历史物化来源筛选。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================

import cn.hutool.json.JSONUtil
import tenantEs
import '@/common/configData' as configData
import '@/common/ods-source-classifier' as odsSourceClassifier
import '@/common/access-table-family' as accessTableFamily
import tenantRuntime
import taskRuntime
import tableSort
import tableRead

/*
 * 分发任务数据表分页。POST /ods/hiveTargetPage
 *
 * 核心口径：
 * 1. 关系库主表限定数据源与数据表登记状态均大于 0，并保留租户归属和业务类型；
 * 2. 数据表清单直接在主表筛选、分页；详情以主表为准，ES 仅辅助补充展示字段；
 * 3. 接入任务及来源/目标信息按当前页表 ID 一次批量读取，展开卡片不再补查；
 * 4. 本接口只读，不创建任务、不物化建表、不修改登记状态。
 */
var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var first = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        if (row[key] != null && text(row[key]) != '') {
            return row[key]
        }
    }
    return null
}
var lower = (value) => text(value).toLowerCase().replace('_', '').replace('-', '').replace(' ', '')
var safeAssetSearch = (query) => {
    try {
        return tenantEs.index(configData().es_dataassets).search(query)
    } catch (e) {
        return { hits: { hits: [] } }
    }
}
var isAccessTableType = (value) => {
    var v = lower(value)
    return v == '业务表' || v == '日志表' || v == '字典表' ||
        v == 'business' || v == 'businesstable' || v == 'log' || v == 'logtable' ||
        v == 'dict' || v == 'dictionary' || v == 'dictionarytable'
}
var formatCycle = (value) => {
    var v = text(value)
    if (blank(v)) {
        return '-'
    }
    if (v == '0') {
        return '手动'
    }
    if (v == '1') {
        return '每日'
    }
    if (v == '2') {
        return '每周'
    }
    if (v == '3') {
        return '每月'
    }
    if (v == '4') {
        return '实时'
    }
    return v
}

// 接入候选属于租户业务数据；只能使用服务端会话中的租户上下文，不能信任请求参数。
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '登录状态已失效，请重新登录'
}

var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
var pageSize = body?.pageSize == null ? 20 : body.pageSize::int
if (pageNum < 1) { pageNum = 1 }
if (pageSize < 1) { pageSize = 20 }
if (pageSize > 100) { pageSize = 100 }
// 只接受允许的排序字段，并在查询登记表时按排序条件分页。
var sortFields={tableName:'table_name',createdTime:'created_time',updatedTime:'updated_time',
    fieldCount:'field_count',recordCount:'record_count',taskCount:'taskCount'}
var sortColumn=tableSort.field(body,sortFields,'updatedTime')
var sortDir=tableSort.direction(body)

/*
 * 数据源节点被选中后进入表级视图。
 * 数据表按关系库登记状态筛选、分页，再批量补齐当前页关联的接入任务。
 */
var viewLevel = text(body?.viewLevel) == 'tableDetail' ? 'tableDetail' : 'table'
var selectedDatasourceId = text(body?.datasourceId)
var selectedDatasourceIds = body?.datasourceIds == null ? [] : body.datasourceIds
if (!blank(selectedDatasourceId) && !selectedDatasourceIds.contains(selectedDatasourceId)) {
    selectedDatasourceIds.add(selectedDatasourceId)
}

/*
 * 表名点击后的详情以当前租户关系库记录为准，ES 仅补充展示信息。
 */
if (viewLevel == 'tableDetail') {
    var selectedTableId = text(body?.tableId || body?.tid)
    if (blank(selectedTableId)) {
        return { viewLevel: 'tableDetail', detail: null }
    }

    // 详情页以 db_table_t 为权威数据源：刷新元数据会写回这里，ES 只作为搜索投影兜底。
    var tableDetailRow = db.table('db_table_t')
        .where().eq('tenant_id', tenantId).eq('tid', selectedTableId).eq('is_del', 0)
        .selectOne()
    if (tableDetailRow == null) {
        return { viewLevel: 'tableDetail', detail: null }
    }
    var detailSourceId = first(tableDetailRow, ['datasourceId', 'datasource_id'])
    var detailSources = db.table('db_datasource_t').where().eq('tid', detailSourceId)
        .eq('tenant_id', tenantId).eq('is_del', 0).select()
    if (detailSources.size() != 1) { return { viewLevel: 'tableDetail', detail: null } }
    // 用户单击既有链路的目标详情：未登记的物化目标仍可只读查看，不能扩大主列表或创建资格。
    var detailTableStatus = first(tableDetailRow, ['assetStatus', 'asset_status'])
    var detailSourceStatus = first(detailSources[0], ['assetStatus', 'asset_status'])
    if (detailTableStatus == null || detailTableStatus::int <= 0 || detailSourceStatus == null || detailSourceStatus::int <= 0) {
        var activeTargetTasks = db.table('data_access_agg_task_t').where().eq('tenant_id', tenantId)
            .eq('is_del', 0).eq('target_table_id', selectedTableId).count()
        if (activeTargetTasks == 0) { return { viewLevel: 'tableDetail', detail: null } }
    }
    var tableDetailResult = safeAssetSearch({
        from: 0,
        size: 1,
        query: {
            bool: {
                filter: [{ term: { assetType: 'table' } }],
                must: [{
                    bool: {
                        should: [
                            { ids: { values: [selectedTableId] } },
                            { term: { tid: selectedTableId } },
                            { term: { id: selectedTableId } }
                        ],
                        minimum_should_match: 1
                    }
                }]
            }
        }
    })
    var tableDetailHits = tableDetailResult?.hits?.hits == null ? [] : tableDetailResult.hits.hits
    if (tableDetailRow == null && tableDetailHits.size() == 0) {
        return { viewLevel: 'tableDetail', detail: null }
    }
    var tableDetailHit = tableDetailHits.size() == 0 ? null : tableDetailHits[0]
    var tableDetailDoc = tableDetailHit?._source
    var tableDetailDatasourceId = text(first(tableDetailRow, ['datasourceId', 'datasource_id', 'dbId', 'db_id']) || first(tableDetailDoc, ['datasourceId', 'dbId', 'datasource_id', 'db_id']))
    var tableDetailDatasourceName = first(tableDetailDoc, ['dbName', 'db_name', 'datasourceName', 'datasource_name'])

    // 老版本 table 投影可能未携带数据源名称，因此仍在同一个 ES 索引内读取 db 文档补齐。
    if (blank(tableDetailDatasourceName) && !blank(tableDetailDatasourceId)) {
        var tableDetailDatasourceResult = safeAssetSearch({
            from: 0,
            size: 1,
            query: {
                bool: {
                    filter: [
                        { term: { assetType: 'db' } },
                        { term: { tid: tableDetailDatasourceId } }
                    ]
                }
            },
            _source: ['tid', 'dbName']
        })
        var tableDetailDatasourceHits = tableDetailDatasourceResult?.hits?.hits == null ? [] : tableDetailDatasourceResult.hits.hits
        if (tableDetailDatasourceHits.size() > 0) {
            tableDetailDatasourceName = tableDetailDatasourceHits[0]?._source?.dbName
        }
    }
    if (blank(tableDetailDatasourceName) && !blank(tableDetailDatasourceId)) {
        var tableDetailDatasourceRow = db.table('db_datasource_t')
            .columns('db_name')
            .where().eq('tid', tableDetailDatasourceId).eq('is_del', 0)
            .selectOne()
        tableDetailDatasourceName = tableDetailDatasourceRow?.dbName || tableDetailDatasourceRow?.db_name
    }

    var tableDetailTid = text(first(tableDetailRow, ['tid', 'id']) || first(tableDetailDoc, ['tid', 'id']) || tableDetailHit?._id)
    var tableDetailRecordCount = tableDetailRow != null ? first(tableDetailRow, ['recordCount', 'record_count']) : first(tableDetailDoc, ['recordCount', 'record_count', 'dataCount', 'data_count'])
    var tableDetailStorageSize = tableDetailRow != null ? first(tableDetailRow, ['storageSize', 'storage_size', 'totalSizeFormatted', 'total_size_formatted']) : first(tableDetailDoc, ['storageSize', 'storage_size', 'totalSizeFormatted', 'total_size_formatted'])
    var tableDetailTotalSizeBytes = tableDetailRow != null ? first(tableDetailRow, ['totalSizeBytes', 'total_size_bytes']) : first(tableDetailDoc, ['totalSizeBytes', 'total_size_bytes'])
    var tableDetailDdlCreateTime = tableDetailRow != null ? first(tableDetailRow, ['ddlCreatTime', 'ddl_create_time', 'ddlCreateTime']) : first(tableDetailDoc, ['ddlCreatTime', 'ddl_create_time', 'ddlCreateTime'])
    var tableDetailDdlUpdateTime = tableDetailRow != null ? first(tableDetailRow, ['ddlUpdateTime', 'ddl_update_time']) : first(tableDetailDoc, ['ddlUpdateTime', 'ddl_update_time'])
    var tableDetailUpdatedTime = tableDetailRow != null ? first(tableDetailRow, ['updatedTime', 'updated_time']) : first(tableDetailDoc, ['updatedTime', 'updated_time'])
    var annotatedValue = first(tableDetailRow, ['annotated', 'isAnnotated', 'is_annotated']) || first(tableDetailDoc, ['annotated', 'isAnnotated', 'is_annotated'])
    var annotatedText = text(annotatedValue).toLowerCase()
    return {
        viewLevel: 'tableDetail',
        detail: {
            tid: tableDetailTid,
            datasourceId: tableDetailDatasourceId,
            datasourceName: tableDetailDatasourceName,
            dbId: tableDetailDatasourceId,
            dbName: tableDetailDatasourceName,
            sourceDbName: tableDetailDatasourceName,
            tableName: first(tableDetailRow, ['tableName', 'table_name', 'tableNameEn', 'table_name_en']) || first(tableDetailDoc, ['tableName', 'table_name', 'tableNameEn', 'table_name_en']),
            tableNameCn: first(tableDetailRow, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment', 'assetDesc', 'asset_desc']) || first(tableDetailDoc, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']),
            tableComment: first(tableDetailRow, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn', 'assetDesc', 'asset_desc']) || first(tableDetailDoc, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn']),
            assetDesc: first(tableDetailRow, ['assetDesc', 'asset_desc']) || first(tableDetailDoc, ['assetDesc', 'asset_desc']),
            businessType: first(tableDetailRow, ['businessType', 'business_type']) || first(tableDetailDoc, ['businessType', 'business_type']),
            tableType: first(tableDetailRow, ['tableType', 'table_type']) || first(tableDetailDoc, ['tableType', 'table_type']),
            fieldCount: tableDetailRow != null ? first(tableDetailRow, ['fieldCount', 'field_count']) : first(tableDetailDoc, ['fieldCount', 'field_count']),
            recordCount: tableDetailRecordCount,
            dataCount: tableDetailRecordCount,
            storageSize: tableDetailStorageSize,
            totalSizeFormatted: tableDetailStorageSize,
            totalSizeBytes: tableDetailTotalSizeBytes,
            totalSizeBytes_num: tableDetailTotalSizeBytes,
            dataUpdateTime: first(tableDetailRow, ['dataUpdateTime', 'data_update_time']) || tableDetailUpdatedTime || tableDetailDdlUpdateTime,
            ddlCreatTime: tableDetailDdlCreateTime,
            ddlCreateTime: tableDetailDdlCreateTime,
            ddlUpdateTime: tableDetailDdlUpdateTime,
            annotated: annotatedValue == true || annotatedText == '1' || annotatedText == 'true',
            assetStatus: first(tableDetailRow, ['assetStatus', 'asset_status']) || first(tableDetailDoc, ['assetStatus', 'asset_status']),
            flowStatus: first(tableDetailRow, ['flowStatus', 'flow_status']) || first(tableDetailDoc, ['flowStatus', 'flow_status']),
            orgId: first(tableDetailRow, ['orgId', 'org_id']) || first(tableDetailDoc, ['orgId', 'org_id']),
            createdTime: first(tableDetailRow, ['createdTime', 'created_time']) || first(tableDetailDoc, ['createdTime', 'created_time']),
            updatedTime: tableDetailUpdatedTime,
            regTime: first(tableDetailRow, ['regTime', 'reg_time']) || first(tableDetailDoc, ['regTime', 'reg_time'])
        }
    }
}

var distributionView = true
// 分发列表一次取得当前租户的 ODS 绑定分类，只保留符合登记状态的来源。
var odsClassification = odsSourceClassifier()
var warehouseDatasourceIds = odsClassification.query.grouped(odsClassification.projection, odsClassification.groups)
    .map(row => text(row.source_id)).filter(id => !blank(id))
// 与数据源管理相同的身份：接入方式优先，兼容历史驱动别名和无连接 API。
var taskDatasourceType = (row) => {
    var raw = text(first(row,['dbType','db_type'])).trim().toLowerCase().replaceAll('\\s|_|-','')
    var aliases = {pg:'postgresql',postgres:'postgresql',mssql:'sqlserver',dm:'dameng',kingbase:'kingbase8',kingbasees:'kingbase8',es:'elasticsearch',elastic:'elasticsearch'}
    var key = aliases[raw] || (blank(raw) ? 'other' : raw)
    var pool = {}
    try { var value=first(row,['poolCfg','pool_cfg']); if (!blank(value)) { pool=JSONUtil.parseObj(value) } } catch (ignored) { }
    var mode=text(pool.accessMode || pool.dataAccessMode).trim().toLowerCase()
    var noConnection=['0','false','no','off'].contains(text(first(row,['showConnect','show_connect'])).trim().toLowerCase())
    if(mode=='proxy') {return 'mode:proxy'}
    if(['upload','report'].contains(mode) || (key=='api' && noConnection)) {return 'mode:upload'}
    if(['receive','push'].contains(mode)) {return 'mode:receive'}
    return key
}
var requestedDatasourceType=text(body?.datasourceType).trim().toLowerCase()
if(requestedDatasourceType.length()>80) {exit 400,'数据源类型过长'}
var datasourceTypeStatistics={unit:'table',complete:body?.includeDatasourceTypeStatistics==true,total:0,typeCounts:{},dbTypes:{}}

var matchesTaskCategory = (datasource) => distributionView == warehouseDatasourceIds.contains(text(first(datasource, ['tid', 'id'])))

if (viewLevel == 'table') {
    var tableKeyword = text(body?.keyword).trim()
    var requestedAccessStatus = text(body?.accessStatus)

    // 数据源和数据表的 asset_status 均大于 0 才可接入。
    // flow_status 不作为登记完成的判断条件。
    var registeredDatasourceRows = db.table('db_datasource_t').where()
        .eq('tenant_id', tenantId).eq('is_del', 0)
        .gt('asset_status', 0).select()
    registeredDatasourceRows = registeredDatasourceRows.filter(matchesTaskCategory)
    var registeredDatasourceIds = []
    var registeredDatasourceMap = {}
    for (registeredDatasourceRow in (registeredDatasourceRows == null ? [] : registeredDatasourceRows)) {
        var registeredDatasourceId = text(first(registeredDatasourceRow, ['tid']))
        if (!blank(registeredDatasourceId) && !registeredDatasourceIds.contains(registeredDatasourceId)) {
            registeredDatasourceIds.add(registeredDatasourceId)
            registeredDatasourceMap[registeredDatasourceId] = registeredDatasourceRow
        }
    }
    var scopedDatasourceIds = []
    if (selectedDatasourceIds.size() > 0) {
        for (requestedDatasourceId in selectedDatasourceIds) {
            var requestedDatasourceIdText = text(requestedDatasourceId)
            if (registeredDatasourceIds.contains(requestedDatasourceIdText) &&
                !scopedDatasourceIds.contains(requestedDatasourceIdText)) {
                scopedDatasourceIds.add(requestedDatasourceIdText)
            }
        }
    } else if (!['org','app','datasource'].contains(text(body?.scopeType))) {
        scopedDatasourceIds = registeredDatasourceIds
    }
    if (scopedDatasourceIds.size() == 0) {
        return {
            datasourceTypeStatistics: datasourceTypeStatistics,
            viewLevel: 'table',
            scopeType: text(body?.scopeType),
            scopeId: text(body?.scopeId),
            datasourceId: selectedDatasourceId,
            datasourceIds: [],
            pageNum: pageNum,
            pageSize: pageSize,
            total: 0,
            list: []
        }
    }

    if (tableKeyword.length() > 100) {
        exit 400, '搜索词过长'
    }
    var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
    var numberColumn = (alias, name) => tableRead.column(alias, name, 'NUMBER')
    var active = alias => tableRead.equal(numberColumn(alias, 'is_del'), 0)
    var present = expression => tableRead.not(tableRead.isNull(expression))
    var tableDatasourceColumn = textColumn('t', 'datasource_id')
    var tableTypes = ['业务表', '日志表', '字典表']
    var tableNameColumn = textColumn('t', 'table_name')
    var tableNameCnColumn = textColumn('t', 'table_name_cn')
    var tableCommentColumn = tableRead.column('t', 'table_comment', 'LOB')

    var directTaskSourceId = textColumn('dt', 'source_table_id')
    var validatedTaskQuery = (sourceIds, requireSourceDb) => {
        var tasks = tableRead.from('data_access_agg_task_t', 'dt', tenantId)
        tasks.leftJoin('db_table_t', 'st', 'tid', 'source_table_id', [active('st')])
        tasks.leftJoin('db_table_t', 'tt', 'tid', 'target_table_id', [active('tt')])
        tasks.leftJoin('db_datasource_t', 'tdb', 'tenant_id', 'tenant_id', [
            active('tdb'), tableRead.equal(textColumn('tdb', 'tid'), textColumn('tt', 'datasource_id'))
        ])
        tasks.where(tableRead.all([
            active('dt'), present(textColumn('st', 'tid')), present(textColumn('tdb', 'tid'))
        ]))
        if (requireSourceDb) {
            tasks.leftJoin('db_datasource_t', 'sdb', 'tenant_id', 'tenant_id', [
                active('sdb'), tableRead.equal(textColumn('sdb', 'tid'), textColumn('st', 'datasource_id'))
            ])
            tasks.where(tableRead.in(textColumn('st', 'datasource_id'), sourceIds))
            tasks.where(present(textColumn('sdb', 'tid')))
        }
        return tasks
    }

    var candidateQuery = sourceIds => {
        var query = tableRead.from('db_table_t', 't', tenantId)
        query.where(tableRead.all([
            active('t'),
            tableRead.greaterThan(numberColumn('t', 'asset_status'), 0),
            tableRead.in(tableDatasourceColumn, sourceIds),
            tableRead.in(textColumn('t', 'business_type'), tableTypes)
        ]))
        if (!blank(tableKeyword)) {
            query.where(tableRead.any([
                tableRead.contains(tableNameColumn, tableKeyword),
                tableRead.contains(tableNameCnColumn, tableKeyword),
                tableRead.contains(tableCommentColumn, tableKeyword)
            ]))
        }
        if (requestedAccessStatus == '0' || requestedAccessStatus == '1') {
            // 状态页签按直接任务判定，来源与目标均须关联有效库。
            var tasks = validatedTaskQuery(sourceIds, true)
            query.leftJoinGrouped(tasks, 'direct_task', 'source_table_id', 'tid',
                { source_table_id: directTaskSourceId }, [directTaskSourceId])
            var matched = textColumn('direct_task', 'source_table_id')
            query.where(requestedAccessStatus == '1' ? present(matched) : tableRead.isNull(matched))
        }
        return query
    }

    // 按来源库一次聚合。统计忽略自身类型条件，其余条件与各自分页共用。
    if (body?.includeDatasourceTypeStatistics == true) {
        var facetSources = candidateQuery(scopedDatasourceIds).grouped({
            source_id: tableDatasourceColumn,
            table_count: tableRead.count()
        }, [tableDatasourceColumn])
        for (facet in facetSources) {
            var source = registeredDatasourceMap[text(first(facet, ['source_id', 'sourceId', 'SOURCE_ID']))]
            if (source == null) {
                continue
            }
            var tableCount = first(facet, ['table_count', 'tableCount', 'TABLE_COUNT']) || 0
            var key = taskDatasourceType(source)
            var raw = text(first(source, ['dbType', 'db_type']))
            datasourceTypeStatistics.total += tableCount
            datasourceTypeStatistics.typeCounts[key] = (datasourceTypeStatistics.typeCounts[key] || 0) + tableCount
            if (!blank(raw)) {
                datasourceTypeStatistics.dbTypes[raw] = (datasourceTypeStatistics.dbTypes[raw] || 0) + tableCount
            }
        }
    }
    if (!blank(requestedDatasourceType)) {
        scopedDatasourceIds = scopedDatasourceIds.filter(id => taskDatasourceType(registeredDatasourceMap[text(id)]) == requestedDatasourceType)
        if (scopedDatasourceIds.size() == 0) {
            return { viewLevel: 'table', pageNum: pageNum, pageSize: pageSize, total: 0, list: [],
                datasourceTypeStatistics: datasourceTypeStatistics }
        }
    }

    // 候选表只投影本页展示所需列，排序在数据库完成。同值按表 ID 升序。
    var columns = {
        tid: textColumn('t', 'tid'),
        datasource_id: tableDatasourceColumn,
        table_name: tableNameColumn,
        table_name_en: textColumn('t', 'table_name_en'),
        table_name_cn: tableNameCnColumn,
        table_comment: tableCommentColumn,
        business_type: textColumn('t', 'business_type'),
        table_type: textColumn('t', 'table_type'),
        field_count: numberColumn('t', 'field_count'),
        record_count: numberColumn('t', 'record_count'),
        created_time: tableRead.column('t', 'created_time', 'TIME'),
        updated_time: tableRead.column('t', 'updated_time', 'TIME'),
        reg_time: tableRead.column('t', 'reg_time', 'TIME'),
        source_catalog_id: textColumn('t', 'source_catalog_id')
    }
    var pageQuery = candidateQuery(scopedDatasourceIds)
    var order = columns[sortColumn]
    if (sortColumn == 'taskCount') {
        // 排序计入有效目标库上的直接任务及最多 16 跳的后代任务，按任务 ID 去重。
        var tasks = validatedTaskQuery([], false)
        var family = tasks.ancestors('task_family', 'db_table_t', 'parent_table', 'tid', 'source_table_id',
            textColumn('st', 'tid'), textColumn('st', 'source_table_id'),
            {task_id: textColumn('dt', 'tid')}, [active('parent_table')], 16)
        var nodeId = textColumn('task_family', 'node_id')
        pageQuery.leftJoinGroupedProjection(family, 'task_sort', 'node_id', 'tid', {
            node_id: nodeId,
            task_count: tableRead.countDistinct(textColumn('task_family', 'task_id'))
        }, [nodeId])
        order = tableRead.coalesce([numberColumn('task_sort', 'task_count'), 0])
    }
    var page = pageQuery.page(columns, order, sortDir, 'tid', pageNum, pageSize)
    var registeredTableTotal = page.total
    var registeredTableRows = page.rows
    var registeredTableMap = {}
    for (registeredTableRow in (registeredTableRows == null ? [] : registeredTableRows)) {
        var registeredTableId = text(first(registeredTableRow, ['tid', 'id']))
        if (!blank(registeredTableId)) {
            registeredTableMap[registeredTableId] = registeredTableRow
        }
    }
    var tableHits = []
    var tableHitIndexes = {}
    var tableHitIndex = 0
    for (tableHit in tableHits) {
        var tableHitId = text(first(tableHit?._source, ['tid', 'id']) || tableHit?._id)
        if (!blank(tableHitId)) {
            tableHitIndexes[tableHitId] = tableHitIndex
        }
        tableHitIndex = tableHitIndex + 1
    }
    for (fallbackTableRow in (registeredTableRows == null ? [] : registeredTableRows)) {
        var fallbackTableId = text(first(fallbackTableRow, ['tid', 'id']))
        if (blank(fallbackTableId)) {
            continue
        }
        if (tableHitIndexes[fallbackTableId] == null) {
            tableHitIndexes[fallbackTableId] = tableHits.size()
            tableHits.add({ _id: fallbackTableId, _source: fallbackTableRow })
        } else {
            // The relational record also carries the latest business type,
            // workflow state and source-table binding.
            tableHits[tableHitIndexes[fallbackTableId]]._source = fallbackTableRow
        }
    }
    var datasourceIdsForNames = []
    for (selectedDatasourceIdForName in scopedDatasourceIds) {
        if (!datasourceIdsForNames.contains(selectedDatasourceIdForName)) {
            datasourceIdsForNames.add(selectedDatasourceIdForName)
        }
    }
    for (tableHitForDatasource in tableHits) {
        var tableDocForDatasource = tableHitForDatasource?._source
        var tableDatasourceIdForName = text(first(tableDocForDatasource, ['datasourceId', 'dbId', 'datasource_id', 'db_id']))
        if (!blank(tableDatasourceIdForName) && !datasourceIdsForNames.contains(tableDatasourceIdForName)) {
            datasourceIdsForNames.add(tableDatasourceIdForName)
        }
    }

    // 数据源名称以 db_datasource_t 主表为准。不要为补充展示名称再访问 ES，
    // 否则根节点查询会随数据源数量放大，并破坏本接口的主表查询口径。
    var selectedDatasourceNameMap = {}
    var selectedDatasourceAppNameMap = {}
    for (fallbackDatasourceId in datasourceIdsForNames) {
        var fallbackDatasource = registeredDatasourceMap[text(fallbackDatasourceId)]
        if (fallbackDatasource != null) {
            selectedDatasourceNameMap[text(fallbackDatasourceId)] = first(fallbackDatasource, ['dbName', 'db_name'])
            selectedDatasourceAppNameMap[text(fallbackDatasourceId)] = first(fallbackDatasource, ['appName', 'app_name'])
        }
    }

    // 只保留五步登记支持接入的业务表、日志表和字典表。
    var tableCandidates = []
    var candidateIds = []
    for (hit in tableHits) {
        var tableDoc = hit?._source
        var tableId = text(first(tableDoc, ['tid', 'id']) || hit?._id)
        var registeredTable = registeredTableMap[tableId]
        if (tableDoc == null || registeredTable == null || !isAccessTableType(first(registeredTable, ['businessType', 'business_type']))) {
            continue
        }
        tableDoc = registeredTable
        tableDoc.tid = tableId
        tableCandidates.add(tableDoc)
        candidateIds.add(tableId)
    }

    // 表级工作流状态以关系库为唯一事实来源，避免 ES 异步投影期间状态展示滞后。
    var tableWorkflowMap = {}
    if (candidateIds.size() > 0) {
        var workflowRows = db.table('db_table_t').where().eq('tenant_id', tenantId).in('tid', candidateIds).eq('is_del', 0).select()
        for (workflowRow in (workflowRows == null ? [] : workflowRows)) {
            tableWorkflowMap[text(first(workflowRow, ['tid']))] = workflowRow
        }
    }

    // 仅对当前页已授权来源表按层批量读取物化链路。
    var tableFamily = accessTableFamily(candidateIds)
    if (tableFamily.limitMessage != null) { exit 413, tableFamily.limitMessage }
    var materializedTargetMap = tableFamily.targetsByRoot
    var materializedTargetDatasourceIds = tableFamily.datasourceIds
    var chainSourceIds = tableFamily.sourceIds
    var chainRootsBySource = tableFamily.rootsByTarget
    var taskPipelineIds = []
    // 接入任务是业务主数据，不在 dataassets 搜索投影中；按当前候选表一次性批量读取。
    var taskGroups = {}
    var targetTableIds = []
    if (candidateIds.size() > 0) {
        var taskFamilyPage = db.table('data_access_agg_task_t').where()
            .eq('tenant_id', tenantId).in('source_table_id', chainSourceIds).eq('is_del', 0)
            .orderBy('updated_time', 'desc').page(2001, 0)
        if (taskFamilyPage != null && taskFamilyPage.total != null && taskFamilyPage.total::int > 2000) {
            exit 413, '当前页关联任务超过 2000 项，请缩小页大小或选择具体数据源后重试'
        }
        var candidateTasks = taskFamilyPage == null || taskFamilyPage.list == null ? [] : taskFamilyPage.list
        for (candidateTask in (candidateTasks == null ? [] : candidateTasks)) {
            var candidateSourceTableId = text(first(candidateTask, ['sourceTableId', 'source_table_id']))
            if (blank(candidateSourceTableId)) {
                continue
            }
            // 原表和上一跳表同页时都应拥有对应任务，不能随分页范围改变归组。
            var groupSourceIds = []
            if (candidateIds.contains(candidateSourceTableId)) { groupSourceIds.add(candidateSourceTableId) }
            for (chainRoot in (chainRootsBySource[candidateSourceTableId] == null ? [] : chainRootsBySource[candidateSourceTableId])) {
                if (!blank(chainRoot) && candidateIds.contains(chainRoot) && !groupSourceIds.contains(chainRoot)) { groupSourceIds.add(chainRoot) }
            }
            if (groupSourceIds.size() == 0) { continue }
            for (groupSourceId in groupSourceIds) {
                if (taskGroups[groupSourceId] == null) { taskGroups[groupSourceId] = [] }
                taskGroups[groupSourceId].add(candidateTask)
            }
            var pipelineId = text(first(candidateTask, ['pipelineId', 'pipeline_id']))
            if (!blank(pipelineId) && !taskPipelineIds.contains(pipelineId)) { taskPipelineIds.add(pipelineId) }
            var candidateTargetTableId = text(first(candidateTask, ['targetTableId', 'target_table_id']))
            if (!blank(candidateTargetTableId)) {
                targetTableIds.add(candidateTargetTableId)
            }
        }
    }

    // 画布目标只用一次批量读取：历史多目标任务仍准确展示其目标，新任务仅一个。
    var pipelineTargetIds = {}
    var pipelineChainOrder = {}
    var pipelineStatuses = {}
    var pipelineGroups = {}
    var taskPipelines = taskPipelineIds.size() == 0 ? [] : db.table('nifi_pipeline_t').columns('id', 'dsl_json', 'status', 'nifi_process_group_id').where()
        .eq('tenant_id', tenantId).in('id', taskPipelineIds).eq('is_del', 0).select()
    for (pipeline in taskPipelines) {
        pipelineStatuses[text(pipeline.id)] = text(pipeline.status).trim().toUpperCase()
        pipelineGroups[text(pipeline.id)] = text(first(pipeline, ['nifiProcessGroupId', 'nifi_process_group_id']))
        var ids = []
        var dsl = {}
        try { dsl = JSONUtil.parseObj(text(first(pipeline, ['dslJson', 'dsl_json']))) } catch (ignored) {}
        for (node in (dsl.nodes == null ? [] : dsl.nodes)) {
            if (text(node.category).toLowerCase() == 'sink') {
                var targetId = text(node.config?.targetTableId)
                if (!blank(targetId) && !ids.contains(targetId)) { ids.add(targetId) }
                if (!blank(targetId) && !targetTableIds.contains(targetId)) { targetTableIds.add(targetId) }
            }
        }
        pipelineTargetIds[text(pipeline.id)] = ids
        var orderedTargets = dsl.accessChain?.targetTableIds
        if (orderedTargets != null && ids.size() == 1) { pipelineChainOrder[text(pipeline.id)] = orderedTargets.indexOf(ids[0]) }
    }
    if (targetTableIds.size() > 2000) { exit 413, '当前页链路目标超过 2000 项，请缩小页大小或选择具体数据源后重试' }
    var selectedTargetTableMap = {}
    if (targetTableIds.size() > 0) {
        var selectedTargetRows = db.table('db_table_t').where()
            .eq('tenant_id', tenantId).in('tid', targetTableIds).eq('is_del', 0).select()
        for (selectedTarget in (selectedTargetRows == null ? [] : selectedTargetRows)) {
            selectedTargetTableMap[text(first(selectedTarget, ['tid']))] = selectedTarget
            var selectedTargetDbId = text(first(selectedTarget, ['datasourceId', 'datasource_id']))
            if (!blank(selectedTargetDbId) && !materializedTargetDatasourceIds.contains(selectedTargetDbId)) { materializedTargetDatasourceIds.add(selectedTargetDbId) }
        }
    }

    var materializedDatasourceMap = {}
    if (materializedTargetDatasourceIds.size() > 0) {
        var materializedDatasourceRows = db.table('db_datasource_t').where()
            .eq('tenant_id', tenantId).in('tid', materializedTargetDatasourceIds).eq('is_del', 0).select()
        var visibleMaterializedDatasources = (materializedDatasourceRows == null ? [] : materializedDatasourceRows)
        for (materializedDatasource in visibleMaterializedDatasources) {
            materializedDatasourceMap[text(first(materializedDatasource, ['tid']))] = materializedDatasource
        }
    }
    var targetSummary = (target) => {
        var targetDbId = text(first(target, ['datasourceId', 'datasource_id']))
        var targetDatasource = materializedDatasourceMap[targetDbId]
        return {
            targetTableId: text(first(target, ['tid'])),
            targetTableName: first(target, ['tableName', 'table_name']) || '',
            targetTableNameCn: first(target, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']) || '',
            targetSchemaName: first(target, ['schemaName', 'schema_name']) || '',
            targetDbId: targetDbId,
            targetDbName: first(targetDatasource, ['dbName', 'db_name']) || '',
            targetDbType: first(targetDatasource, ['dbType', 'db_type']) || first(target, ['dbType', 'db_type']) || ''
        }
    }

    var tableItems = []
    for (tableCandidate in tableCandidates) {
        var selectedTableId = text(tableCandidate.tid)
        var tableDatasourceId = text(first(tableCandidate, ['datasourceId', 'dbId', 'datasource_id', 'db_id']))
        var rawTasks = taskGroups[selectedTableId] == null ? [] : taskGroups[selectedTableId]
        var materializedTargetsForSource = materializedTargetMap[selectedTableId] == null ? [] : materializedTargetMap[selectedTableId]
        var targetTables = []
        var materializedTarget = null
        for (linkedTable in materializedTargetsForSource) {
            if (materializedDatasourceMap[text(first(linkedTable, ['datasourceId', 'datasource_id']))] != null) {
                // 单目标兼容字段与可见目标列表使用同一条有效记录。
                if (materializedTarget == null) { materializedTarget = linkedTable }
                targetTables.add(targetSummary(linkedTable))
            }
        }
        var tasks = []
        for (rawTask in rawTasks) {
            var taskTargetId = text(first(rawTask, ['targetTableId', 'target_table_id']))
            var taskTarget = selectedTargetTableMap[taskTargetId]
            if (taskTarget == null || materializedDatasourceMap[text(first(taskTarget, ['datasourceId', 'datasource_id']))] == null) { continue }
            var taskPipelineId = text(first(rawTask, ['pipelineId', 'pipeline_id']))
            var taskStatus = text(first(rawTask, ['taskStatus', 'task_status']))
            // 与任务监控一致：已绑定流程以流程主表 PG 为准，旧记录才使用任务 PG。
            var runtimeGroup = pipelineGroups.containsKey(taskPipelineId) ? pipelineGroups[taskPipelineId]
                : text(first(rawTask, ['processGroupId', 'process_group_id']))
            var taskTargets = []
            var ownTargetIds = pipelineTargetIds[taskPipelineId]
            if (ownTargetIds == null || ownTargetIds.size() == 0) { ownTargetIds = [taskTargetId] }
            for (ownTargetId in ownTargetIds) {
                var ownTarget = selectedTargetTableMap[text(ownTargetId)]
                if (ownTarget != null && materializedDatasourceMap[text(first(ownTarget, ['datasourceId', 'datasource_id']))] != null) { taskTargets.add(targetSummary(ownTarget)) }
            }
            if (taskTargets.size() == 0 && taskTarget != null) { taskTargets.add(targetSummary(taskTarget)) }
            var actualSourceId = text(first(rawTask, ['sourceTableId', 'source_table_id']))
            var actualSourceTable = actualSourceId == selectedTableId ? tableCandidate : null
            for (target in materializedTargetsForSource) { if (text(target.tid) == actualSourceId) { actualSourceTable = target; break } }
            var actualSourceDbId = text(first(actualSourceTable, ['datasourceId', 'datasource_id']))
            // 新任务候选必须登记；已有下游仅按租户、有效记录和关联关系，不能因数仓库登记状态为 0 隐藏。
            if (actualSourceTable == null || (actualSourceId != selectedTableId && materializedDatasourceMap[actualSourceDbId] == null)) { continue }
            tasks.add({
                tid: text(first(rawTask, ['tid'])),
                sourceTableId: actualSourceId,
                rootSourceTableId: selectedTableId,
                sourceTableName: first(actualSourceTable, ['tableName', 'table_name']) || '',
                sourceTableNameCn: first(actualSourceTable, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']) || '',
                sourceSchemaName: first(actualSourceTable, ['schemaName', 'schema_name']) || '',
                sourceDbId: actualSourceDbId,
                sourceDbType: first(actualSourceId == selectedTableId ? registeredDatasourceMap[actualSourceDbId]
                    : materializedDatasourceMap[actualSourceDbId], ['dbType', 'db_type']) || '',
                sourceDbName: actualSourceId == selectedTableId ? selectedDatasourceNameMap[actualSourceDbId]
                    : first(materializedDatasourceMap[actualSourceDbId], ['dbName', 'db_name']),
                chainOrder: pipelineChainOrder[taskPipelineId] == null ? -1 : pipelineChainOrder[taskPipelineId],
                taskName: first(rawTask, ['taskName', 'task_name']) || '未命名接入任务',
                targetTableId: taskTargetId,
                pipelineId: text(first(rawTask, ['pipelineId', 'pipeline_id'])),
                targetTableName: first(taskTarget, ['tableName', 'table_name', 'tableNameCn', 'table_name_cn']) || '',
                scheduleCycle: first(rawTask, ['scheduleCycle', 'schedule_cycle']),
                scheduleCycleText: formatCycle(first(rawTask, ['scheduleCycle', 'schedule_cycle'])),
                lastRunning: first(rawTask, ['lastRunning', 'last_running', 'updatedTime', 'updated_time']),
                endRunning: first(rawTask, ['endRunning', 'end_running']),
                runtimeProcessGroupId: runtimeGroup,
                lifecycleStatus: pipelineStatuses[taskPipelineId] || '',
                taskStatus: first(rawTask, ['taskStatus', 'task_status']),
                scheduleStrategy: first(rawTask, ['scheduleStrategy', 'schedule_strategy']),
                delayLevel: first(rawTask, ['delayLevel', 'delay_level']),
                isTimeout: first(rawTask, ['isTimeout', 'is_timeout']),
                monitorStatus: first(rawTask, ['monitorStatus', 'monitor_status']),
                monitorMessage: first(rawTask, ['monitorMsg', 'monitor_msg']),
                taskStatusText: '',
                targetDbId: taskTargets.size() == 0 ? '' : taskTargets[0].targetDbId,
                targetDbName: taskTargets.size() == 0 ? '' : taskTargets[0].targetDbName,
                targetDbType: taskTargets.size() == 0 ? '' : taskTargets[0].targetDbType,
                targetTables: taskTargets
            })
        }

        // 只展示从当前来源沿已授权真实任务边可达的链路，防止失权中间节点后的孤立任务泄漏。
        // 16 次内存前沿遍历没有任何 SQL/API 调用；visited 防止真正的任务环，根 source_table_id 自指不参与这里的任务边。
        var familyTasks = []
        var acceptedTaskIds = []
        var visitedTableIds = [selectedTableId]
        var taskFrontier = [selectedTableId]
        var taskDepth = 0
        while (taskDepth < 16 && taskFrontier.size() > 0) {
            var nextFrontier = []
            for (familyTask in tasks) {
                if (!taskFrontier.contains(text(familyTask.sourceTableId)) || acceptedTaskIds.contains(text(familyTask.tid))) { continue }
                acceptedTaskIds.add(text(familyTask.tid))
                familyTasks.add(familyTask)
                for (familyTarget in familyTask.targetTables) {
                    var nextTableId = text(familyTarget.targetTableId)
                    if (!blank(nextTableId) && !visitedTableIds.contains(nextTableId)) {
                        visitedTableIds.add(nextTableId); nextFrontier.add(nextTableId)
                    }
                }
            }
            taskFrontier = nextFrontier
            taskDepth = taskDepth + 1
        }
        tasks = familyTasks.sort((left, right) => left.chainOrder >= 0 && right.chainOrder >= 0 ? left.chainOrder - right.chainOrder : 0)
        var hasTask = tasks.size() > 0
        var workflowRow = tableWorkflowMap[selectedTableId]
        // 只有数据源和数据表均有登记状态的表才会作为接入候选。
        var materializedTargetId = text(first(materializedTarget, ['tid']))
        tableItems.add({
            tid: selectedTableId,
            sourceTableId: selectedTableId,
            datasourceId: tableDatasourceId,
            dbType: first(registeredDatasourceMap[tableDatasourceId], ['dbType', 'db_type']) || '',
            datasourceName: first(tableCandidate, ['dbName', 'db_name', 'datasourceName', 'datasource_name']) || selectedDatasourceNameMap[tableDatasourceId],
            appName: first(tableCandidate, ['appName', 'app_name', 'applicationName', 'application_name']) || selectedDatasourceAppNameMap[tableDatasourceId],
            tableName: first(tableCandidate, ['tableName', 'table_name', 'tableNameEn', 'table_name_en']),
            tableNameCn: first(tableCandidate, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']),
            tableComment: first(tableCandidate, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn']),
            businessType: first(tableCandidate, ['businessType', 'business_type']),
            tableType: first(tableCandidate, ['tableType', 'table_type']),
            fieldCount: first(tableCandidate, ['fieldCount', 'field_count']),
            dataCount: first(tableCandidate, ['recordCount', 'record_count']),
            // db_table_t 是表登记创建时间的事实来源；ES 仅在旧记录缺失该字段时兜底。
            createdTime: first(workflowRow, ['createdTime', 'created_time', 'createTime', 'create_time', 'regTime', 'reg_time']) || first(tableCandidate, ['createdTime', 'created_time', 'createTime', 'create_time', 'regTime', 'reg_time']),
            updatedTime: first(tableCandidate, ['updatedTime', 'updated_time']),
            sourceCatalogId: first(tableCandidate, ['sourceCatalogId', 'source_catalog_id', 'catalogId', 'catalog_id']),
            catalogId: first(tableCandidate, ['sourceCatalogId', 'source_catalog_id', 'catalogId', 'catalog_id']),
            targetTableId: hasTask ? tasks[0].targetTableId : materializedTargetId,
            materializedTableId: materializedTargetId,
            targetTableName: hasTask ? tasks[0].targetTableName : first(materializedTarget, ['tableName', 'table_name', 'tableNameCn', 'table_name_cn']),
            targetTables: targetTables,
            taskCount: tasks.size(),
            accessTaskId: hasTask ? tasks[0].tid : '',
            accessStatus: hasTask ? '1' : '0',
            accessStatusText: hasTask ? '已接入' : '待接入',
            tasks: tasks
        })
    }

    // 补齐任务和组织信息时始终沿用数据库分页顺序，默认排序也在数据库完成。
    var tableTotal = registeredTableTotal
    var tablePage = tableItems

    // 所属部门取当前数据源主表的 org_id，不使用表创建人、前端部门或 ES 旧投影。
    // 数据源已经按当前租户读取；仅为当前页最多 100 行补齐部门。
    var pageOrgIds = []
    for (pageTable in tablePage) {
        var pageDatasource = registeredDatasourceMap[text(pageTable.datasourceId)]
        var pageOrgId = text(first(pageDatasource, ['orgId', 'org_id']))
        pageTable.orgId = pageOrgId
        if (!blank(pageOrgId) && !pageOrgIds.contains(pageOrgId)) {
            pageOrgIds.add(pageOrgId)
        }
    }
    var pageOrgNameMap = {}
    if (pageOrgIds.size() > 0) {
        var pageOrgRows = db.table('rm_org_t').columns('id', 'name').where()
            .in('id', pageOrgIds).eq('tenant_id', tenantId).eq('deleted', 0).select()
        for (pageOrg in (pageOrgRows == null ? [] : pageOrgRows)) {
            pageOrgNameMap[text(first(pageOrg, ['id']))] = first(pageOrg, ['name'])
        }
    }
    for (pageTable in tablePage) {
        pageTable.orgName = pageOrgNameMap[text(pageTable.orgId)] || ''
    }
    // 只装饰当前已授权、可达任务；一次节点快照供全部链路复用，不逐任务查 NiFi。
    var live = taskRuntime.snapshot()
    var runtimeRows = []
    for (pageTable in tablePage) { for (task in pageTable.tasks) { runtimeRows.add(task) } }
    taskRuntime.decorate(runtimeRows, live)
    for (task in runtimeRows) { task.currentStatus = task.runtimeState; task.taskStatusText = task.runtimeLabel }
    return {
        datasourceTypeStatistics: datasourceTypeStatistics,
        runtimeRevision: live.revision,
        runtimeFresh: live.fresh,
        runtimeObservedAt: live.observedAt,
        runtimeMessage: live.message,
        viewLevel: 'table',
        scopeType: text(body?.scopeType),
        scopeId: text(body?.scopeId),
        datasourceId: selectedDatasourceId,
        datasourceIds: scopedDatasourceIds,
        pageNum: pageNum,
        pageSize: pageSize,
        total: tableTotal,
        list: tablePage
    }
}
