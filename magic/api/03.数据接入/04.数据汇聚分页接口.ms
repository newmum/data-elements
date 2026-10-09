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
  "description" : "数据汇聚分页接口（POST /ods/dataAggPage）。页面专用只读接口；关系库限定已完成登记的数据源，数据表清单和详情读取 Elasticsearch dataassets 投影，接入任务按表 ID 从 data_access_agg_task_t 批量补充；不写入数据库；使用请求参数 pageNum、pageSize、viewLevel、datasourceId、datasourceIds、tableId、tid、keyword；涉及 db_table_t、db_datasource_t、data_access_agg_task_t 等业务表。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import cn.hutool.json.JSONUtil
// 接口：04.数据汇聚分页接口
// 路由：POST /ods/dataAggPage
// 业务说明：数据汇聚分页接口（POST /ods/dataAggPage）。页面专用只读接口；关系库限定已完成登记的数据源，数据表清单和详情读取 Elasticsearch dataassets 投影，接入任务按表 ID 从 data_access_agg_task_t 批量补充；不写入数据库；使用请求参数 pageNum、pageSize、viewLevel、datasourceId、datasourceIds、tableId、tid、keyword；涉及 db_table_t、db_datasource_t、data_access_agg_task_t 等业务表。
import tenantEs
import '@/common/configData' as configData
import tenantRuntime
import dataScope

/*
 * 数据汇聚分页
 *
 * 核心口径：
 * 1. 关系库主表限定已完成登记的数据源，ES 的 dataassets 索引承担范围内检索；
 * 2. 数据表清单和数据表详情均只读取 ES 的 table 投影，避免进入详情时回表超时或数据口径不一致；
 * 3. 接入任务不属于资产搜索投影，仅按 ES 返回的表 ID 批量读取 data_access_agg_task_t；
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
// 接入表清单的口径固定为 db_table_t 最后修改时间倒序。不要使用 ES 的
// createdTime / updatedTime 投影，也不要让前端请求字段改变登记表排序。
var sortField = 'updatedTime'
var sortDir = 'desc'

/*
 * 数据源节点被选中后进入表级视图。
 * 数据表清单只从 Elasticsearch 的 table 投影读取；MySQL 仅补充该表关联的接入任务，
 * 避免组织树和表清单因数据库联表查询产生超时或统计口径不一致。
 */
var viewLevel = text(body?.viewLevel)
var selectedDatasourceId = text(body?.datasourceId)
var selectedDatasourceIds = body?.datasourceIds == null ? [] : body.datasourceIds
if (!blank(selectedDatasourceId) && !selectedDatasourceIds.contains(selectedDatasourceId)) {
    selectedDatasourceIds.add(selectedDatasourceId)
}

/*
 * 表名点击后的详情直接读取 ES。详情弹窗与列表使用同一份 table 投影，
 * 不回查 db_table_t，确保重新探查和索引更新后页面立即反映最新元数据。
 */
if (viewLevel == 'tableDetail') {
    var selectedTableId = text(body?.tableId || body?.tid)
    if (blank(selectedTableId)) {
        return { viewLevel: 'tableDetail', detail: null }
    }

    // 详情页以 db_table_t 为权威数据源：刷新元数据会写回这里，ES 只作为搜索投影兜底。
    var tableDetailRow = db.table('db_table_t')
        .where().eq('tid', selectedTableId).eq('is_del', 0)
        .selectOne()
    if (tableDetailRow == null || text(first(tableDetailRow, ['assetStatus', 'asset_status'])) != '2') {
        return { viewLevel: 'tableDetail', detail: null }
    }
    var detailSourceId = first(tableDetailRow, ['datasourceId', 'datasource_id'])
    var detailSources = db.table('db_datasource_t').where().eq('tid', detailSourceId)
        .eq('tenant_id', tenantId).eq('is_del', 0).in('asset_status', [1, 2]).select()
    detailSources = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', detailSources)
    if (detailSources.size() != 1) { return { viewLevel: 'tableDetail', detail: null } }
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
    var tableDetailRecordCount = first(tableDetailRow, ['recordCount', 'record_count']) || first(tableDetailDoc, ['recordCount', 'record_count', 'dataCount', 'data_count'])
    var tableDetailStorageSize = first(tableDetailRow, ['storageSize', 'storage_size', 'totalSizeFormatted', 'total_size_formatted']) || first(tableDetailDoc, ['storageSize', 'storage_size', 'totalSizeFormatted', 'total_size_formatted'])
    var tableDetailTotalSizeBytes = first(tableDetailRow, ['totalSizeBytes', 'total_size_bytes']) || first(tableDetailDoc, ['totalSizeBytes', 'total_size_bytes'])
    var tableDetailDdlCreateTime = first(tableDetailRow, ['ddlCreatTime', 'ddl_create_time', 'ddlCreateTime']) || first(tableDetailDoc, ['ddlCreatTime', 'ddl_create_time', 'ddlCreateTime'])
    var tableDetailDdlUpdateTime = first(tableDetailRow, ['ddlUpdateTime', 'ddl_update_time']) || first(tableDetailDoc, ['ddlUpdateTime', 'ddl_update_time'])
    var tableDetailUpdatedTime = first(tableDetailRow, ['updatedTime', 'updated_time']) || first(tableDetailDoc, ['updatedTime', 'updated_time'])
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
            fieldCount: first(tableDetailRow, ['fieldCount', 'field_count']) || first(tableDetailDoc, ['fieldCount', 'field_count']),
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

if (viewLevel == 'table') {
    var tableKeyword = text(body?.keyword).trim()
    var requestedAccessStatus = text(body?.accessStatus)

    // 第五步已结束的数据源（1/2）下，只有表状态 2 才可接入。
    // flow_status 不作为登记完成的判断条件。
    var registeredDatasourceRows = db.table('db_datasource_t').where()
        .eq('tenant_id', tenantId).eq('is_del', 0)
        .in('asset_status', [1, 2]).select()
    registeredDatasourceRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', registeredDatasourceRows)
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
    } else {
        scopedDatasourceIds = registeredDatasourceIds
    }
    if (scopedDatasourceIds.size() == 0) {
        return {
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

    /*
     * 任务表候选必须是已完成登记的表：asset_status=2。
     * annotated=1 仅代表第二步已经标注，不能越过表资产的完成边界。ES
     * 仅是搜索投影；候选直接从 db_table_t 主表产生，避免投影滞后或大库扫描。
     */
    var registeredTableQuery = db.table('db_table_t').where()
        .in('datasource_id', scopedDatasourceIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .eq('asset_status', 2)
        .in('business_type', ['业务表', '日志表', '字典表'])
    // 默认“全部”页无需先装载所有登记表再做内存分页。直接让 MySQL 按
    // updated_time 排序分页，随后只查询当前页的接入任务，避免大库页面超时。
    // 搜索和按接入状态筛选需要先完成额外判断，仍走后面的全量准确分支。
    var pageFromRegisteredTable = blank(tableKeyword) && requestedAccessStatus != '0' && requestedAccessStatus != '1'
    var registeredTableTotal = 0
    var registeredTableRows = []
    if (pageFromRegisteredTable) {
        var registeredTablePage = registeredTableQuery.orderBy('updated_time', 'desc')
            .page(pageSize, (pageNum - 1) * pageSize)
        registeredTableRows = registeredTablePage == null || registeredTablePage.list == null ? [] : registeredTablePage.list
        registeredTableTotal = registeredTablePage == null || registeredTablePage.total == null ? registeredTableRows.size() : registeredTablePage.total
    } else {
        registeredTableRows = registeredTableQuery.orderBy('updated_time', 'desc').select()
    }
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
        var fallbackTableText = text(first(fallbackTableRow, ['tableName', 'table_name'])) + ' ' +
            text(first(fallbackTableRow, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']))
        if (blank(fallbackTableId) || (!blank(tableKeyword) && !fallbackTableText.toLowerCase().contains(tableKeyword.toLowerCase()))) {
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
        var workflowRows = db.table('db_table_t').where().in('tid', candidateIds).eq('is_del', 0).select()
        for (workflowRow in (workflowRows == null ? [] : workflowRows)) {
            tableWorkflowMap[text(first(workflowRow, ['tid']))] = workflowRow
        }
    }

    // 新版接入入口以来源表为主：物化目标表会写 db_table_t.source_table_id。
    // 列表提前补齐“已物化但未建任务”的目标表，点击创建任务时可直接生成 NiFi 任务。
    var materializedTargetMap = {}
    var materializedTargetDatasourceIds = []
    var materializedTargets = []
    if (candidateIds.size() > 0) {
        materializedTargets = db.table('db_table_t').where()
            .eq('tenant_id', tenantId).in('source_table_id', candidateIds).eq('is_del', 0).select()
        for (materializedTarget in (materializedTargets == null ? [] : materializedTargets)) {
            var materializedSourceId = text(first(materializedTarget, ['sourceTableId', 'source_table_id']))
            var materializedTid = text(first(materializedTarget, ['tid']))
            if (!blank(materializedSourceId) && materializedTid != materializedSourceId) {
                if (materializedTargetMap[materializedSourceId] == null) {
                    materializedTargetMap[materializedSourceId] = []
                }
                materializedTargetMap[materializedSourceId].add(materializedTarget)
                materializedTargetDatasourceIds.add(text(first(materializedTarget, ['datasourceId', 'datasource_id'])))
            }
        }
    }

    var chainSourceIds = []
    for (candidateId in candidateIds) { chainSourceIds.add(candidateId) }
    var chainRootBySource = {}
    for (target in (materializedTargets == null ? [] : materializedTargets)) {
        var id = text(target.tid)
        if (!chainSourceIds.contains(id)) { chainSourceIds.add(id) }
        chainRootBySource[id] = text(first(target, ['sourceTableId', 'source_table_id']))
    }
    var taskPipelineIds = []
    // 接入任务是业务主数据，不在 dataassets 搜索投影中；按当前候选表一次性批量读取。
    var taskGroups = {}
    var targetTableIds = []
    if (candidateIds.size() > 0) {
        var candidateTasks = db.table('data_access_agg_task_t').where()
            .eq('tenant_id', tenantId).in('source_table_id', chainSourceIds).eq('is_del', 0)
            .orderBy('updated_time', 'desc').select()
        for (candidateTask in (candidateTasks == null ? [] : candidateTasks)) {
            var candidateSourceTableId = text(first(candidateTask, ['sourceTableId', 'source_table_id']))
            if (blank(candidateSourceTableId)) {
                continue
            }
            var groupSourceId = candidateIds.contains(candidateSourceTableId) ? candidateSourceTableId : chainRootBySource[candidateSourceTableId]
            if (blank(groupSourceId)) { continue }
            if (taskGroups[groupSourceId] == null) { taskGroups[groupSourceId] = [] }
            taskGroups[groupSourceId].add(candidateTask)
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
    var taskPipelines = taskPipelineIds.size() == 0 ? [] : db.table('nifi_pipeline_t').columns('id', 'dsl_json').where()
        .eq('tenant_id', tenantId).in('id', taskPipelineIds).eq('is_del', 0).select()
    for (pipeline in taskPipelines) {
        var ids = []
        var dsl = {}
        try { dsl = JSONUtil.parseObj(text(first(pipeline, ['dslJson', 'dsl_json']))) } catch (ignored) {}
        for (node in (dsl.nodes == null ? [] : dsl.nodes)) {
            if (text(node.category).toLowerCase() == 'sink') {
                var targetId = text(node.config?.targetTableId)
                if (!blank(targetId) && !ids.contains(targetId)) { ids.add(targetId) }
            }
        }
        pipelineTargetIds[text(pipeline.id)] = ids
        var orderedTargets = dsl.accessChain?.targetTableIds
        if (orderedTargets != null && ids.size() == 1) { pipelineChainOrder[text(pipeline.id)] = orderedTargets.indexOf(ids[0]) }
    }
    var selectedTargetTableMap = {}
    if (targetTableIds.size() > 0) {
        var selectedTargetRows = db.table('db_table_t').where()
            .in('tid', targetTableIds).eq('is_del', 0).select()
        for (selectedTarget in (selectedTargetRows == null ? [] : selectedTargetRows)) {
            selectedTargetTableMap[text(first(selectedTarget, ['tid']))] = selectedTarget
        }
    }

    var materializedDatasourceMap = {}
    if (materializedTargetDatasourceIds.size() > 0) {
        var materializedDatasourceRows = db.table('db_datasource_t').where()
            .in('tid', materializedTargetDatasourceIds).eq('is_del', 0).select()
        var visibleMaterializedDatasources = dataScope.visibleDataSourcesFor('MENU:2081000000000000002',
            materializedDatasourceRows == null ? [] : materializedDatasourceRows)
        for (materializedDatasource in visibleMaterializedDatasources) {
            materializedDatasourceMap[text(first(materializedDatasource, ['tid']))] = materializedDatasource
        }
    }
    var targetSummary = (target) => {
        var targetDbId = text(first(target, ['datasourceId', 'datasource_id']))
        var targetDatasource = materializedDatasourceMap[targetDbId]
        return {
            targetTableId: text(first(target, ['tid'])),
            targetTableName: first(target, ['tableName', 'table_name', 'tableNameCn', 'table_name_cn']) || '',
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
        for (materializedTarget in materializedTargetsForSource) {
            if (materializedDatasourceMap[text(first(materializedTarget, ['datasourceId', 'datasource_id']))] != null) {
                targetTables.add(targetSummary(materializedTarget))
            }
        }
        var tasks = []
        for (rawTask in rawTasks) {
            var taskTargetId = text(first(rawTask, ['targetTableId', 'target_table_id']))
            var taskTarget = selectedTargetTableMap[taskTargetId]
            if (taskTarget == null || materializedDatasourceMap[text(first(taskTarget, ['datasourceId', 'datasource_id']))] == null) { continue }
            var currentStatus = text(first(rawTask, ['currentStatus', 'current_status', 'monitorStatus', 'monitor_status'])).toUpperCase()
            var taskStatus = text(first(rawTask, ['taskStatus', 'task_status']))
            var taskStatusText = taskStatus == '1' ? '运行中' : taskStatus == '2' ? '运行异常' : '未启用'
            if (currentStatus.contains('FAIL') || currentStatus.contains('ERROR')) {
                taskStatusText = '运行异常'
            } else if (currentStatus == 'RUNNING') {
                taskStatusText = '运行中'
            } else if (currentStatus == 'STOPPED') {
                taskStatusText = '已停止'
            }
            var taskTargets = []
            var taskPipelineId = text(first(rawTask, ['pipelineId', 'pipeline_id']))
            var ownTargetIds = pipelineTargetIds[taskPipelineId]
            if (ownTargetIds == null || ownTargetIds.size() == 0) { ownTargetIds = [taskTargetId] }
            for (target in targetTables) { if (ownTargetIds.contains(target.targetTableId)) { taskTargets.add(target) } }
            if (taskTargets.size() == 0 && taskTarget != null) { taskTargets.add(targetSummary(taskTarget)) }
            var actualSourceId = text(first(rawTask, ['sourceTableId', 'source_table_id']))
            var actualSourceTable = actualSourceId == selectedTableId ? tableCandidate : null
            for (target in materializedTargetsForSource) { if (text(target.tid) == actualSourceId) { actualSourceTable = target; break } }
            var actualSourceDbId = text(first(actualSourceTable, ['datasourceId', 'datasource_id']))
            if (actualSourceId != selectedTableId && materializedDatasourceMap[actualSourceDbId] == null) { continue }
            tasks.add({
                tid: text(first(rawTask, ['tid'])),
                sourceTableId: actualSourceId,
                rootSourceTableId: selectedTableId,
                sourceTableName: first(actualSourceTable, ['tableName', 'table_name']) || '',
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
                currentStatus: currentStatus,
                taskStatus: first(rawTask, ['taskStatus', 'task_status']),
                scheduleStrategy: first(rawTask, ['scheduleStrategy', 'schedule_strategy']),
                delayLevel: first(rawTask, ['delayLevel', 'delay_level']),
                isTimeout: first(rawTask, ['isTimeout', 'is_timeout']),
                monitorStatus: first(rawTask, ['monitorStatus', 'monitor_status']),
                monitorMessage: first(rawTask, ['monitorMsg', 'monitor_msg']),
                taskStatusText: taskStatusText,
                targetDbId: taskTargets.size() == 0 ? '' : taskTargets[0].targetDbId,
                targetDbName: taskTargets.size() == 0 ? '' : taskTargets[0].targetDbName,
                targetDbType: taskTargets.size() == 0 ? '' : taskTargets[0].targetDbType,
                targetTables: taskTargets
            })
        }

        tasks = tasks.sort((left, right) => left.chainOrder >= 0 && right.chainOrder >= 0 ? left.chainOrder - right.chainOrder : 0)
        var hasTask = tasks.size() > 0
        var workflowRow = tableWorkflowMap[selectedTableId]
        // 只有已完成登记的表才会作为接入候选。
        var materializedTarget = materializedTargetsForSource.size() > 0 ? materializedTargetsForSource[0] : null
        var materializedTargetId = text(first(materializedTarget, ['tid']))
        if (requestedAccessStatus == '1' && !hasTask) {
            continue
        }
        if (requestedAccessStatus == '0' && hasTask) {
            continue
        }
        tableItems.add({
            tid: selectedTableId,
            sourceTableId: selectedTableId,
            datasourceId: tableDatasourceId,
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

    // ES 检索、接入任务补齐和 MySQL 兜底合并后，必须按登记表最终的
    // updated_time 再排序，避免 ES 投影延迟导致第一页顺序不稳定。
    var tableUpdatedSortKey = (item) => {
        var value = item?.updatedTime
        return blank(value) ? '' : text(value).replace('T', ' ').replace('Z', '')
    }
    tableItems = tableItems.sort((left, right) => {
        var leftKey = tableUpdatedSortKey(left)
        var rightKey = tableUpdatedSortKey(right)
        if (leftKey == rightKey) {
            var leftId = text(left?.tid)
            var rightId = text(right?.tid)
            if (leftId == rightId) { return 0 }
            return leftId > rightId ? 1 : -1
        }
        return rightKey > leftKey ? 1 : -1
    })

    var tableTotal = pageFromRegisteredTable ? registeredTableTotal : tableItems.size()
    var tablePage = []
    if (pageFromRegisteredTable) {
        tablePage = tableItems
    } else {
        var tableOffset = (pageNum - 1) * pageSize
        var tableIndex = 0
        for (tableItem in tableItems) {
            if (tableIndex >= tableOffset && tablePage.size() < pageSize) {
                tablePage.add(tableItem)
            }
            tableIndex = tableIndex + 1
        }
    }

    // 所属部门取当前数据源主表的 org_id，不使用表创建人、前端部门或 ES 旧投影。
    // 数据源已经通过当前租户及角色数据范围校验；仅为当前页最多 100 行补齐部门。
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
    return {
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
    { wildcard: { dbName: 'codex_*' } },
    { wildcard: { database: 'codex_*' } }
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
        .in('asset_status', [1, 2]).select()
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
    .in('asset_status', [1, 2]).select()
completedDatasourceRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', completedDatasourceRows)
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
if (accessStatus == '0' || accessStatus == '1') {
    var summaryRows = db.select("""
        SELECT t.datasource_id AS datasourceId,
               COUNT(*) AS tableTotal,
               COUNT(task.source_table_id) AS accessed
          FROM db_table_t t
          JOIN db_datasource_t source
            ON source.tid = t.datasource_id
           AND source.tenant_id = #{tenantId}
           AND source.is_del = 0
           AND source.asset_status IN (1, 2)
          LEFT JOIN (
               SELECT DISTINCT tenant_id, source_table_id
                 FROM data_access_agg_task_t
                WHERE tenant_id = #{tenantId}
                  AND is_del = 0
          ) task
            ON task.tenant_id = t.tenant_id
           AND task.source_table_id = t.tid
         WHERE t.tenant_id = #{tenantId}
           AND t.is_del = 0
           AND t.asset_status = 2
           AND LOWER(REPLACE(REPLACE(REPLACE(COALESCE(t.business_type, ''), '_', ''), '-', ''), ' ', ''))
               IN ('业务表', '日志表', '字典表', 'business', 'businesstable',
                   'log', 'logtable', 'dict', 'dictionary', 'dictionarytable')
         GROUP BY t.datasource_id
    """)
    for (summaryRow in (summaryRows == null ? [] : summaryRows)) {
        var summaryDatasourceId = text(first(summaryRow, ['datasourceId', 'datasourceid', 'datasource_id']))
        var summaryTotal = first(summaryRow, ['tableTotal', 'tabletotal']) || 0
        var summaryAccessed = first(summaryRow, ['accessed']) || 0
        summaryMap[summaryDatasourceId] = { done: summaryTotal > 0 && summaryAccessed >= summaryTotal }
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
        .in('asset_status', [1, 2]).select()
    for (row in datasourceRows) {
        datasourceMap[text(first(row, ['tid']))] = row
    }
}

var tableRows = []
if (datasourceIds.size() > 0) {
    tableRows = db.table('db_table_t').where().in('datasource_id', datasourceIds)
        .eq('tenant_id', tenantId).eq('is_del', 0)
        .eq('asset_status', 2).orderBy('updated_time', 'desc').select()
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
        .in('source_table_id', tableIds).eq('is_del', 0)
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
    var targetRows = db.table('db_table_t').where().in('tid', targetIds).eq('is_del', 0).select()
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
        targetTableId: targetId,
        targetTableName: first(target, ['tableName', 'table_name', 'tableNameCn', 'table_name_cn']) || '',
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
