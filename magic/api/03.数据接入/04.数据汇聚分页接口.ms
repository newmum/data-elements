{
  "properties" : { },
  "id" : "76fd211790f54164947f276824d19c1d",
  "script" : null,
  "groupId" : "1f4028f20aaa4f379b5e37a343b99fa9",
  "name" : "04.数据汇聚分页接口",
  "createTime" : 1782955344724,
  "updateTime" : 1791105908862,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/dataAggPage",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n  \"viewLevel\": \"tableDetail\",\n  \"tableId\": \"015c09572d174e89b9bea9c765ef72df\"\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "{\n  \"viewLevel\": \"tableDetail\",\n  \"detail\": {\n    \"tid\": \"015c09572d174e89b9bea9c765ef72df\",\n    \"tableName\": \"police_reexplore_new_case_clue\",\n    \"tableNameCn\": \"公安重新探查新增线索表\",\n    \"datasourceName\": \"出入境人员动管系统数据库\",\n    \"businessType\": \"业务表\",\n    \"fieldCount\": 8,\n    \"annotated\": true\n  }\n}",
  "description" : "数据汇聚分页接口（POST /ods/dataAggPage）。页面专用只读接口；当前租户内，数据源与数据表登记状态均大于 0，排除资源中心 ODS 层及分域配置的数据源作为主列表来源；表清单默认按本表与已授权关联画布的最新修改时间倒序、表ID升序后服务端分页，空时间排末尾，任务及来源/全部已授权目标按当前页批量补充，ES 仅辅助层级和详情展示；不写入数据库；使用请求参数 pageNum、pageSize、viewLevel、datasourceId、datasourceIds、tableId、tid、keyword、accessStatus（表视图：0待接入、1已接入、2待审批、3待启动）；涉及 db_table_t、db_datasource_t、data_access_agg_task_t 等业务表。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================

import cn.hutool.json.JSONUtil
import log
import tenantEs
import '@/common/configData' as configData
import '@/common/ods-source-classifier' as odsSourceClassifier
import '@/common/access-table-family' as accessTableFamily
import tenantRuntime
import taskRuntime
import tableSort
import tableRead

/*
 * 数据汇聚分页。POST /ods/dataAggPage
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
// 默认按本表与关联画布最新修改时间倒序；白名单排序先于分页，补齐链路后保持数据库顺序。
var sortFields={tableName:'table_name',createdTime:'created_time',updatedTime:'updated_time',
    fieldCount:'field_count',recordCount:'record_count',taskCount:'taskCount'}
var sortField=body?.sortField==null?'updatedTime':text(body.sortField)
var sortColumn=tableSort.field(body,sortFields,'updatedTime')
var sortDir=tableSort.direction(body)

/*
 * 数据源节点被选中后进入表级视图。
 * 数据表按关系库登记状态筛选、分页，再批量补齐当前页关联的接入任务。
 */
var viewLevel = text(body?.viewLevel)
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

var distributionView = false
// 接入列表一次取得当前租户的 ODS 绑定分类，随后从主列表排除这些来源。
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
    // 接入状态是查询契约，不复用登记 asset_status 或登记审批 flow_status。
    // 0 待接入，1 已接入（本表任务实际运行），2 待审批（部门提交接入申请，尚无任务），3 待启动。
    // NiFi 无法确认已部署任务时返回空值及“状态待确认”，不伪造停止或运行状态。
    var requestedAccessStatus = text(body?.accessStatus)

    // 数据源和数据表的 asset_status 均大于 0 才可接入。
    // flow_status 不作为登记完成的判断条件。
    // 一次读取已授权主档；候选来源仍要求已登记，链路目标不因其登记进度被误排除。
    var accessibleDatasourceRows = db.table('db_datasource_t').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).select()
    var registeredDatasourceRows = accessibleDatasourceRows.filter(row => row.assetStatus > 0 || row.asset_status > 0).filter(matchesTaskCategory)
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

    // 运行状态筛选使用同一次快照，避免计数、分页和任务展示观察到不同版本。
    var needsRuntimeFilter = requestedAccessStatus == '1' || requestedAccessStatus == '3'
    var tableRuntimeSnapshot = needsRuntimeFilter ? taskRuntime.snapshot() : null
    var freshRuntime = tableRuntimeSnapshot?.fresh == true
    var runningGroupIds = []
    if (freshRuntime && tableRuntimeSnapshot.index != null) {
        for (groupId, runtimeGroup in tableRuntimeSnapshot.index.groups()) {
            if (runtimeGroup.running()) { runningGroupIds.add(groupId) }
        }
    }

    if (tableKeyword.length() > 100) { exit 400, '搜索词过长' }
    var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
    var numberColumn = (alias, name) => tableRead.column(alias, name, 'NUMBER')
    var timeColumn = (alias, name) => tableRead.column(alias, name, 'TIME')
    var active = alias => tableRead.equal(numberColumn(alias, 'is_del'), 0)
    var present = expression => tableRead.not(tableRead.isNull(expression))
    var tableDatasource = textColumn('t', 'datasource_id')
    var tableName = textColumn('t', 'table_name')
    var tableNameCn = textColumn('t', 'table_name_cn')
    var tableComment = tableRead.column('t', 'table_comment', 'LOB')
    var taskSource = textColumn('task', 'source_table_id')

    // 历史目标可以未登记，但来源、目标表及其所属数据源必须是当前租户有效记录。
    var authorizedTasks = () => {
        var query = tableRead.from('data_access_agg_task_t', 'task', tenantId)
        query.leftJoin('db_table_t', 'hop', 'tid', 'source_table_id', [active('hop')])
        query.leftJoin('db_table_t', 'target', 'tid', 'target_table_id', [active('target')])
        query.leftJoin('db_datasource_t', 'hop_db', 'tenant_id', 'tenant_id', [
            active('hop_db'), tableRead.equal(textColumn('hop_db', 'tid'), textColumn('hop', 'datasource_id'))
        ])
        query.leftJoin('db_datasource_t', 'target_db', 'tenant_id', 'tenant_id', [
            active('target_db'), tableRead.equal(textColumn('target_db', 'tid'), textColumn('target', 'datasource_id'))
        ])
        query.where(tableRead.all([active('task'), present(textColumn('hop_db', 'tid')),
            present(textColumn('target_db', 'tid'))]))
        return query
    }

    var candidatesFor = sourceIds => {
        var query = tableRead.from('db_table_t', 't', tenantId)
        query.where(tableRead.all([
            active('t'), tableRead.greaterThan(numberColumn('t', 'asset_status'), 0),
            tableRead.in(tableDatasource, sourceIds),
            tableRead.in(textColumn('t', 'business_type'), ['业务表', '日志表', '字典表'])
        ]))
        if (!blank(tableKeyword)) {
            query.where(tableRead.any([tableRead.contains(tableName, tableKeyword),
                tableRead.contains(tableNameCn, tableKeyword), tableRead.contains(tableComment, tableKeyword)]))
        }
        if (!['0', '1', '2', '3'].contains(requestedAccessStatus)) { return query }

        // 页签按直接任务判定。已有画布即以画布 PG 为准，即使该 PG 为空。
        var tasks = authorizedTasks()
        tasks.where(tableRead.in(textColumn('hop', 'datasource_id'), sourceIds))
        var statusProjection = { source_id: taskSource }
        if (needsRuntimeFilter) {
            tasks.leftJoin('nifi_pipeline_t', 'status_pipeline', 'id', 'pipeline_id', [active('status_pipeline')])
            var effectiveGroup = tableRead.when(present(textColumn('status_pipeline', 'id')),
                textColumn('status_pipeline', 'nifi_process_group_id'), textColumn('task', 'process_group_id'))
            // 快照不可用时，只能判定无 PG 的任务待启动。带 PG 的旧任务继续待确认。
            var excluded = freshRuntime ? tableRead.in(effectiveGroup, runningGroupIds)
                : present(tableRead.blankAsNull(tableRead.trim(effectiveGroup)))
            statusProjection.excluded_count = tableRead.countIf(excluded)
        }
        query.leftJoinGrouped(tasks, 'direct_access', 'source_id', 'tid', statusProjection, [taskSource])
        var directSource = textColumn('direct_access', 'source_id')
        if (requestedAccessStatus == '0' || requestedAccessStatus == '2') {
            var approval = tableRead.equal(tableRead.coalesce([numberColumn('t', 'access_request_status'), 0]), 1)
            query.where(tableRead.all([tableRead.isNull(directSource),
                requestedAccessStatus == '2' ? approval : tableRead.not(approval)]))
        } else {
            var hasExcluded = tableRead.greaterThan(numberColumn('direct_access', 'excluded_count'), 0)
            if (requestedAccessStatus == '1') {
                // 过期快照中的 PG 不能判断任务正在运行。
                query.where(freshRuntime ? hasExcluded : tableRead.in(textColumn('t', 'tid'), []))
            } else {
                query.where(tableRead.all([present(directSource), tableRead.not(hasExcluded)]))
            }
        }
        return query
    }

    // 统计先按候选数据源汇总，再应用用户选择的来源类型。
    if (body?.includeDatasourceTypeStatistics == true) {
        var facetRows = candidatesFor(scopedDatasourceIds).grouped({ source_id: tableDatasource,
            table_count: tableRead.count() }, [tableDatasource])
        for (facet in facetRows) {
            var source = registeredDatasourceMap[text(facet.source_id)]
            if (source == null) { continue }
            var tableCount = facet.table_count || 0
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

    // 全局排序先按 15 跳祖先聚合。有效画布的修改时间优先，历史任务才回退到任务时间。
    var sortTasks = authorizedTasks()
    sortTasks.leftJoin('nifi_pipeline_t', 'sort_pipeline', 'id', 'pipeline_id', [active('sort_pipeline')])
    var epoch = numberColumn('sort_pipeline', 'updated_at')
    var taskTime = tableRead.when(present(epoch), tableRead.epochMillis(epoch), timeColumn('task', 'updated_time'))
    var authorizedDatasourceIds = accessibleDatasourceRows.map(row => text(row.tid))
    var ancestors = sortTasks.ancestors('access_ancestor', 'db_table_t', 'parent', 'tid', 'source_table_id',
        textColumn('hop', 'tid'), textColumn('hop', 'source_table_id'),
        { task_id: textColumn('task', 'tid'), modified_time: taskTime },
        [active('parent'), tableRead.in(textColumn('parent', 'datasource_id'), authorizedDatasourceIds)], 15)
    var ancestorId = textColumn('access_ancestor', 'node_id')
    var query = candidatesFor(scopedDatasourceIds)
    var sortProjection = { node_id: ancestorId, modified_time: tableRead.max(timeColumn('access_ancestor', 'modified_time')) }
    if (sortColumn == 'taskCount') {
        sortProjection.task_count = tableRead.countDistinct(textColumn('access_ancestor', 'task_id'))
    }
    // 此关联只用于行字段和排序。总数与角标无需展开祖先。
    query.leftJoinGroupedProjection(ancestors, 'task_sort', 'node_id', 'tid', sortProjection, [ancestorId])
    var updatedTime = timeColumn('t', 'updated_time')
    var taskUpdatedTime = timeColumn('task_sort', 'modified_time')
    var modifiedTime = tableRead.when(tableRead.any([tableRead.isNull(updatedTime),
        tableRead.greaterThan(taskUpdatedTime, updatedTime)]), taskUpdatedTime, updatedTime)
    var orders = {
        table_name: tableName,
        created_time: timeColumn('t', 'created_time'),
        updated_time: modifiedTime,
        field_count: numberColumn('t', 'field_count'),
        record_count: numberColumn('t', 'record_count')
    }
    var order = sortColumn == 'taskCount' ? tableRead.coalesce([numberColumn('task_sort', 'task_count'), 0]) : orders[sortColumn]
    // 保留旧部署可能仍存在的表登记字段，并在同一查询返回全局计算的修改时间。
    var registeredPage = query.pageRootRows({ access_modified_time: modifiedTime }, order, sortDir, 'tid', pageNum, pageSize)
    var registeredTableTotal = registeredPage.total
    var tableCandidates = registeredPage.rows
    var candidateIds = []
    var registeredTableMap = {}
    for (row in tableCandidates) {
        candidateIds.add(row.tid)
        registeredTableMap[row.tid] = row
    }

    // 当前页数据源已经按租户批量校验，复用主表展示名称。
    var selectedDatasourceNameMap = {}
    var selectedDatasourceAppNameMap = {}
    for (sourceId in scopedDatasourceIds) {
        var source = registeredDatasourceMap[sourceId]
        selectedDatasourceNameMap[sourceId] = first(source, ['dbName', 'db_name'])
        selectedDatasourceAppNameMap[sourceId] = first(source, ['appName', 'app_name'])
    }


    var tableWorkflowMap = registeredTableMap

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
                // 兼容单目标字段，取已校验并按链路距离和 ID 排序的第一个目标。
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
            updatedTime: first(tableCandidate, ['accessModifiedTime', 'access_modified_time', 'updatedTime', 'updated_time']),
            tableUpdatedTime: first(tableCandidate, ['updatedTime', 'updated_time']),
            sourceCatalogId: first(tableCandidate, ['sourceCatalogId', 'source_catalog_id', 'catalogId', 'catalog_id']),
            catalogId: first(tableCandidate, ['sourceCatalogId', 'source_catalog_id', 'catalogId', 'catalog_id']),
            targetTableId: hasTask ? tasks[0].targetTableId : materializedTargetId,
            materializedTableId: materializedTargetId,
            targetTableName: hasTask ? tasks[0].targetTableName : first(materializedTarget, ['tableName', 'table_name', 'tableNameCn', 'table_name_cn']),
            targetTables: targetTables,
            taskCount: tasks.size(),
            accessTaskId: hasTask ? tasks[0].tid : '',
            accessRequestStatus: first(tableCandidate,['accessRequestStatus','access_request_status']) || 0,
            requestedBy: first(tableCandidate,['accessRequestedBy','access_requested_by']),
            requestedAt: first(tableCandidate,['accessRequestedAt','access_requested_at']),
            accessStatus: hasTask ? '1' : '0',
            accessStatusText: hasTask ? '已接入' : '待接入',
            tasks: tasks
        })
    }

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
    var live = tableRuntimeSnapshot == null ? taskRuntime.snapshot() : tableRuntimeSnapshot
    var runtimeRows = []
    for (pageTable in tablePage) { for (task in pageTable.tasks) { runtimeRows.add(task) } }
    taskRuntime.decorate(runtimeRows, live)
    for (task in runtimeRows) { task.currentStatus = task.runtimeState; task.taskStatusText = task.runtimeLabel }
    for (pageTable in tablePage) {
        var ownTasks = pageTable.tasks.filter(task => text(task.sourceTableId) == text(pageTable.tid))
        var ownRunning = ownTasks.filter(task => task.running == true).size() > 0
        var ownUnconfirmed = ownTasks.filter(task => task.runtimeFresh != true && !blank(task.runtimeProcessGroupId)).size() > 0
        var pendingApproval = text(pageTable.accessRequestStatus) == '1'
        pageTable.accessStatus = ownRunning ? '1' : ownTasks.size() == 0 ? pendingApproval ? '2' : '0' : ownUnconfirmed ? '' : '3'
        pageTable.accessStatusText = ownRunning ? '已接入' : ownTasks.size() == 0 ? pendingApproval ? '待审批' : '待接入' : ownUnconfirmed ? '状态待确认' : '待启动'
    }
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

var accessStatus = ''
var datasourceViewStartedAt = System.currentTimeMillis()
var must = [{ term: { assetType: 'db' } }]
var mustNot = [
    { wildcard: { dbName: 'validation_*' } },
    { wildcard: { database: 'validation_*' } }
]

// 将前端筛选条件转换成 ES 查询条件；accessStatus 需要回表后判断，所以只记录不进入 ES。
var addCondition = (condition) => {
    if (condition == null || blank(condition.field) || condition.value == null || text(condition.value) == '') {
        return
    }
    var field = condition.field
    var type = blank(condition.type) ? 'match' : condition.type
    var value = condition.value
    if (field == 'assetType') {
        return
    }
    if (field == 'accessStatus') {
        accessStatus = text(value)
        return
    }
    if (field == 'keyword') {
        must.add({ bool: { should: [
            { wildcard: { dbName: '*' + value + '*' } },
            { wildcard: { appName: '*' + value + '*' } },
            { wildcard: { database: '*' + value + '*' } },
            { wildcard: { schema: '*' + value + '*' } }
        ], minimum_should_match: 1 } })
        return
    }
    if (type == 'in') {
        var terms = {}
        terms[field] = value
        must.add({ terms: terms })
        return
    }
    var term = {}
    term[field] = value
    must.add({ term: term })
}

for (condition in (body?.conditions == null ? [] : body.conditions)) {
    addCondition(condition)
}

var sort = {}
sort[sortField] = { order: sortDir, unmapped_type: 'keyword' }

// ES 是检索投影；这里一次取足候选集，再基于接入状态做内存分页，当前库约数百条足够快。
var result = safeAssetSearch({
    from: 0,
    size: 2000,
    track_total_hits: true,
    query: { bool: { must: must, must_not: mustNot } },
    sort: [sort]
})

var candidates = []
for (hit in (result?.hits?.hits == null ? [] : result.hits.hits)) {
    var doc = hit._source
    if (doc == null) {
        continue
    }
    doc.tid = doc.tid || hit._id
    candidates.add(doc)
}
if (candidates.size() == 0) {
    var fallbackDatasourceRows = db.table('db_datasource_t').where()
        .eq('tenant_id', tenantId).eq('is_del', 0)
        .gt('asset_status', 0).select()
    for (fallbackDatasource in (fallbackDatasourceRows == null ? [] : fallbackDatasourceRows)) {
        fallbackDatasource.tid = text(first(fallbackDatasource, ['tid']))
        candidates.add(fallbackDatasource)
    }
}
var candidateSearchFinishedAt = System.currentTimeMillis()

// ES 只是数据源的展示投影。无论 ES 是否陈旧，最终均以 db_datasource_t
// 的登记完成状态作为任务管理的准入条件，并补回 ES 尚未投影的新登记数据源。
var completedDatasourceRows = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId).eq('is_del', 0)
    .gt('asset_status', 0).select()
completedDatasourceRows = completedDatasourceRows.filter(matchesTaskCategory)
var completedDatasourceMap = {}
for (completedDatasource in (completedDatasourceRows == null ? [] : completedDatasourceRows)) {
    var completedDatasourceId = text(first(completedDatasource, ['tid', 'id']))
    if (!blank(completedDatasourceId)) {
        completedDatasourceMap[completedDatasourceId] = completedDatasource
    }
}
var completedCandidates = []
var completedCandidateIds = []
for (candidate in candidates) {
    var candidateDatasourceId = text(candidate?.tid || candidate?.id)
    if (blank(candidateDatasourceId) || completedDatasourceMap[candidateDatasourceId] == null || completedCandidateIds.contains(candidateDatasourceId)) {
        continue
    }
    completedCandidates.add(candidate)
    completedCandidateIds.add(candidateDatasourceId)
}
for (completedDatasource in (completedDatasourceRows == null ? [] : completedDatasourceRows)) {
    var completedDatasourceId = text(first(completedDatasource, ['tid', 'id']))
    if (!blank(completedDatasourceId) && !completedCandidateIds.contains(completedDatasourceId)) {
        completedDatasource.tid = completedDatasourceId
        completedCandidates.add(completedDatasource)
        completedCandidateIds.add(completedDatasourceId)
    }
}
candidates = completedCandidates
var datasourceScopeFinishedAt = System.currentTimeMillis()

// 当切换“待接入/已接入”页签时，按数据源下可接入表是否全部已有接入任务判断。
var summaryMap = {}
// 不筛选接入状态时无需统计全部候选数据源，直接分页；筛选时用一次分组查询
// 替代逐数据源查表、逐表 COUNT。任务先按来源表去重，避免重复任务放大已接入数。
if ((accessStatus == '0' || accessStatus == '1') && completedCandidateIds.size() > 0) {
    var tasks = tableRead.from('data_access_agg_task_t', 'task', tenantId)
    var sourceTable = tableRead.column('task', 'source_table_id', 'TEXT')
    tasks.where(tableRead.equal(tableRead.column('task', 'is_del', 'NUMBER'), 0))
    var summary = tableRead.from('db_table_t', 't', tenantId)
    summary.leftJoin('db_datasource_t', 'source', 'tid', 'datasource_id', [
        tableRead.equal(tableRead.column('source', 'is_del', 'NUMBER'), 0),
        tableRead.greaterThan(tableRead.column('source', 'asset_status', 'NUMBER'), 0)
    ])
    summary.leftJoinGrouped(tasks, 'linked_task', 'source_id', 'tid', { source_id: sourceTable }, [sourceTable])
    var sourceId = tableRead.column('t', 'datasource_id', 'TEXT')
    var normalizedType = tableRead.column('t', 'business_type', 'TEXT')
    for (separator in ['_', '-', ' ']) { normalizedType = tableRead.replace(normalizedType, separator, '') }
    summary.where(tableRead.all([
        tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0),
        tableRead.greaterThan(tableRead.column('t', 'asset_status', 'NUMBER'), 0),
        tableRead.in(sourceId, completedCandidateIds),
        tableRead.not(tableRead.isNull(tableRead.column('source', 'tid', 'TEXT'))),
        tableRead.in(tableRead.lower(normalizedType), ['业务表', '日志表', '字典表', 'business', 'businesstable',
            'log', 'logtable', 'dict', 'dictionary', 'dictionarytable'])
    ]))
    var summaryRows = summary.grouped({ source_id: sourceId, table_total: tableRead.count(),
        accessed: tableRead.countIf(tableRead.not(tableRead.isNull(tableRead.column('linked_task', 'source_id', 'TEXT')))) }, [sourceId])
    for (row in summaryRows) {
        summaryMap[text(row.source_id)] = { done: row.table_total > 0 && row.accessed >= row.table_total }
    }
}

var filtered = []
for (source in candidates) {
    var datasourceId = text(source.tid || source.id)
    if (blank(datasourceId)) {
        continue
    }
    var summary = summaryMap[datasourceId]
    var done = summary != null && summary.done
    if (accessStatus == '1' && !done) {
        continue
    }
    if (accessStatus == '0' && done) {
        continue
    }
    filtered.add(source)
}
var datasourceFilterFinishedAt = System.currentTimeMillis()

var total = filtered.size()
var offset = (pageNum - 1) * pageSize
var pageSources = []
var index = 0
for (source in filtered) {
    if (index >= offset && pageSources.size() < pageSize) {
        pageSources.add(source)
    }
    index = index + 1
}

var datasourceIds = []
for (source in pageSources) {
    datasourceIds.add(text(source.tid || source.id))
}

var datasourceMap = {}
if (datasourceIds.size() > 0) {
    // 当前页再回主表修正数据源真实字段，避免 ES 旧投影误导页面。
    var datasourceRows = db.table('db_datasource_t').where().in('tid', datasourceIds)
        .eq('tenant_id', tenantId).eq('is_del', 0)
        .gt('asset_status', 0).select()
    for (row in datasourceRows) {
        datasourceMap[text(first(row, ['tid']))] = row
    }
}

var tableRows = []
if (datasourceIds.size() > 0) {
    tableRows = db.table('db_table_t').where().in('datasource_id', datasourceIds)
        .eq('tenant_id', tenantId).eq('is_del', 0)
        .gt('asset_status', 0).orderBy('updated_time', 'desc').select()
}

var tableIds = []
for (table in (tableRows == null ? [] : tableRows)) {
    if (isAccessTableType(first(table, ['businessType', 'business_type']))) {
        tableIds.add(text(first(table, ['tid'])))
    }
}

var taskMap = {}
var targetIds = []
if (tableIds.size() > 0) {
    // 接入任务以 source_table_id 关联登记原表；一张表有多条任务时取最新查询结果中的第一条。
    var taskRows = db.table('data_access_agg_task_t').where()
        .eq('tenant_id', tenantId).in('source_table_id', tableIds).eq('is_del', 0)
        .orderBy('updated_time', 'desc').select()
    for (task in (taskRows == null ? [] : taskRows)) {
        var sourceTableId = text(first(task, ['sourceTableId', 'source_table_id']))
        if (blank(sourceTableId) || taskMap[sourceTableId] != null) {
            continue
        }
        taskMap[sourceTableId] = task
        var targetId = text(first(task, ['targetTableId', 'target_table_id']))
        if (!blank(targetId)) {
            targetIds.add(targetId)
        }
    }
}

var targetTableMap = {}
if (targetIds.size() > 0) {
    var targetRows = db.table('db_table_t').where()
        .eq('tenant_id', tenantId).in('tid', targetIds).eq('is_del', 0).select()
    for (target in (targetRows == null ? [] : targetRows)) {
        targetTableMap[text(first(target, ['tid']))] = target
    }
}

var tableGroups = {}
for (table in (tableRows == null ? [] : tableRows)) {
    var businessType = first(table, ['businessType', 'business_type'])
    if (!isAccessTableType(businessType)) {
        continue
    }
    var sourceTableId = text(first(table, ['tid']))
    var datasourceId = text(first(table, ['datasourceId', 'datasource_id']))
    var task = taskMap[sourceTableId]
    var targetId = text(first(task, ['targetTableId', 'target_table_id']))
    var target = targetTableMap[targetId]
    var item = {
        tid: sourceTableId,
        sourceTableId: sourceTableId,
        datasourceId: datasourceId,
        catalogId: first(table, ['sourceCatalogId', 'source_catalog_id']),
        sourceCatalogId: first(table, ['sourceCatalogId', 'source_catalog_id']),
        catalogName: first(table, ['catalogName', 'catalog_name', 'tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']),
        tableName: first(table, ['tableName', 'table_name', 'tableNameEn', 'table_name_en']),
        tableNameCn: first(table, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']),
        businessType: businessType,
        dataCount: first(table, ['recordCount', 'record_count']),
        dataUpdateTime: first(task, ['endRunning', 'end_running', 'updatedTime', 'updated_time']) || first(table, ['updatedTime', 'updated_time']),
        lastBizTime: first(table, ['ddlUpdateTime', 'ddl_update_time', 'updatedTime', 'updated_time']),
        targetTableId: target == null ? '' : targetId,
        targetTableName: first(target, ['tableName', 'table_name']) || '',
        targetTableNameCn: first(target, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']) || '',
        targetSchemaName: first(target, ['schemaName', 'schema_name']) || '',
        targetDbType: first(target, ['dbType', 'db_type']) || '',
        accessTaskId: first(task, ['tid']) || '',
        accessTaskName: first(task, ['taskName', 'task_name']) || '',
        scheduleCycle: first(task, ['scheduleCycle', 'schedule_cycle']),
        scheduleCycleText: formatCycle(first(task, ['scheduleCycle', 'schedule_cycle'])),
        accessStatusText: task == null ? '未接入' : '已接入'
    }
    if (tableGroups[datasourceId] == null) {
        tableGroups[datasourceId] = []
    }
    tableGroups[datasourceId].add(item)
}

var list = []
for (source in pageSources) {
    var datasourceId = text(source.tid || source.id)
    var main = datasourceMap[datasourceId]
    if (main == null) {
        continue
    }
    var tables = tableGroups[datasourceId] == null ? [] : tableGroups[datasourceId]
    var accessed = 0
    for (table in tables) {
        if (!blank(table.accessTaskId)) {
            accessed = accessed + 1
        }
        table.appName = source.appName
        table.orgName = source.orgName
    }
    var done = tables.size() > 0 && accessed >= tables.size()
    list.add({
        tid: datasourceId,
        id: datasourceId,
        dbName: first(main, ['dbName', 'db_name']) || source.dbName,
        dbType: first(main, ['dbType', 'db_type']) || source.dbType,
        database: source.database || source.schema,
        schema: source.schema,
        appId: first(main, ['appId', 'app_id']) || source.appId,
        appName: source.appName,
        orgId: first(main, ['orgId', 'org_id']) || source.orgId,
        orgName: source.orgName,
        assetStatus: first(main, ['assetStatus', 'asset_status']) || source.assetStatus,
        connectionStatus: first(main, ['connectionStatus', 'connection_status']) || source.connectionStatus,
        accessTableTotal: tables.size(),
        accessedTableNum: accessed,
        accessStatus: done ? '1' : '0',
        accessStatusText: done ? '已接入' : '待接入',
        tables: tables
    })
}

var datasourceViewFinishedAt = System.currentTimeMillis()
var datasourceViewElapsedMs = datasourceViewFinishedAt - datasourceViewStartedAt
if (datasourceViewElapsedMs >= 1000) {
    log.info('dataAggPage datasource view timing: tenant={}, status={}, candidates={}, matched={}, searchMs={}, scopeMs={}, filterMs={}, pageMs={}, totalMs={}',
        tenantId, accessStatus, candidates.size(), total,
        candidateSearchFinishedAt - datasourceViewStartedAt,
        datasourceScopeFinishedAt - candidateSearchFinishedAt,
        datasourceFilterFinishedAt - datasourceScopeFinishedAt,
        datasourceViewFinishedAt - datasourceFilterFinishedAt,
        datasourceViewElapsedMs)
}
return { pageNum: pageNum, pageSize: pageSize, total: total, list: list }
