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
  "description" : "数据汇聚分页接口（POST /ods/dataAggPage）。页面专用只读接口；当前租户和角色范围内，数据源与数据表登记状态均大于 0，排除资源中心 ODS 层及分域配置的数据源作为主列表来源；表清单默认按本表与已授权关联画布的最新修改时间倒序、表ID升序后服务端分页，空时间排末尾，任务及来源/全部已授权目标按当前页批量补充，ES 仅辅助层级和详情展示；不写入数据库；使用请求参数 pageNum、pageSize、viewLevel、datasourceId、datasourceIds、tableId、tid、keyword、accessStatus（表视图：0待接入、1已接入、2待审批、3待启动）；涉及 db_table_t、db_datasource_t、data_access_agg_task_t 等业务表。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================

import cn.hutool.json.JSONUtil
// 接口：04.数据汇聚分页接口
// 路由：POST /ods/dataAggPage
// 业务说明：数据汇聚分页接口（POST /ods/dataAggPage）。页面专用只读接口；当前租户和角色范围内，数据源与数据表登记状态均大于 0；表清单默认按本表与已授权关联画布的最新修改时间倒序、表ID升序后服务端分页，空时间排末尾，任务及来源/全部已授权目标按当前页批量补充，ES 仅辅助层级和详情展示；不写入数据库；使用请求参数 pageNum、pageSize、viewLevel、datasourceId、datasourceIds、tableId、tid、keyword、accessStatus（表视图：0待接入、1已接入、2待审批、3待启动）；涉及 db_table_t、db_datasource_t、data_access_agg_task_t 等业务表。
import tenantEs
import '@/common/configData' as configData
import tenantRuntime
import identityRuntime
import dataScope
import taskRuntime

/*
 * 数据汇聚分页
 *
 * 核心口径：
 * 1. 关系库主表限定数据源与数据表登记状态均大于 0，并保留租户、角色范围和业务类型；
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
import tableSort
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
    detailSources = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', detailSources)
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
// 接入/分发依据当前租户资源中心 ODS 层及其分域绑定分类，不按库名、节点类型或数据库产品判断。
// 同时校验真实字典根、层/域和绑定的 tenant_id / is_del，旧解绑或其他租户配置不能改变当前候选范围。
var warehouseBindings = db.select("""
    SELECT DISTINCT binding.datasource_id AS datasourceId
      FROM dwm_center_layer_source_t binding
      JOIN sym_dict_t node ON node.tid=binding.target_id AND node.tenant_id=#{tenantId} AND node.is_del=0
      JOIN sym_dict_t ods ON ods.tenant_id=#{tenantId} AND ods.is_del=0 AND LOWER(ods.dict_code)='ods'
           AND ((binding.target_type='layer' AND node.tid=ods.tid)
                OR (binding.target_type='domain' AND node.parent_id=ods.tid))
      JOIN sym_dict_t root ON root.tid=ods.parent_id AND root.tenant_id=#{tenantId}
           AND root.is_del=0 AND root.parent_id='0' AND root.dict_code='dataSourceType'
     WHERE binding.tenant_id=#{tenantId} AND binding.is_del=0 AND binding.datasource_id IS NOT NULL
""")
var warehouseDatasourceIds = []
for (binding in (warehouseBindings == null ? [] : warehouseBindings)) {
    var bindingDatasourceId = text(first(binding, ['datasourceId', 'datasourceid', 'datasource_id']))
    if (!blank(bindingDatasourceId) && !warehouseDatasourceIds.contains(bindingDatasourceId)) {
        warehouseDatasourceIds.add(bindingDatasourceId)
    }
}
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
    accessibleDatasourceRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', accessibleDatasourceRows)
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

    // 运行状态筛选先取一次快照；其他页签在查询后读取，避免长查询期间版本变化触发连续补刷。
    var tableRuntimeSnapshot = requestedAccessStatus == '1' || requestedAccessStatus == '3' ? taskRuntime.snapshot() : null
    var runningGroupChunks = []
    var groupChunk = []
    if (tableRuntimeSnapshot != null && tableRuntimeSnapshot.fresh == true && tableRuntimeSnapshot.index != null) {
        var runtimeGroups = tableRuntimeSnapshot.index.groups()
        for (groupId, runtimeGroup in runtimeGroups) {
            if (runtimeGroup.running()) {
                groupChunk.add(groupId)
                if (groupChunk.size() == 500) { runningGroupChunks.add(groupChunk); groupChunk = [] }
            }
        }
        if (groupChunk.size() > 0) { runningGroupChunks.add(groupChunk) }
    }

    /*
     * 任务表候选必须有登记状态：asset_status>0。
     * annotated=1 仅代表第二步已经标注，不能越过表资产的完成边界。ES
     * 仅是搜索投影；候选直接从 db_table_t 主表产生，避免投影滞后或大库扫描。
     */
    // 所有页签和关键词均在数据库内分页，后续只批量装载当前页的链路。
    if (tableKeyword.length() > 100) { exit 400, '搜索词过长' }
    var taskEpochBase = new Date(0::long)
    var sortTargetSourceIds=accessibleDatasourceRows.map(row=>text(row.tid))
    if(sortTargetSourceIds.size()==0) {sortTargetSourceIds=['__none__']}
    // 按实际租户业务库转换毫秒时间戳；复用现有技术模块取得方言，连接由模块负责释放。
    var sortDatabaseProduct=text(identityRuntime.inspectTenantBinding(tenantId).databaseProduct).toLowerCase()
    var pipelineTimeSql=''
    if(sortDatabaseProduct.contains('mysql') || sortDatabaseProduct.contains('mariadb') || sortDatabaseProduct=='h2') {
        pipelineTimeSql='TIMESTAMPADD(MICROSECOND,MOD(sortPipeline.updated_at,1000)*1000,TIMESTAMPADD(SECOND,FLOOR(sortPipeline.updated_at/1000),CAST(#{taskEpochBase} AS DATETIME(3))))'
    } else if(sortDatabaseProduct.contains('dm') || sortDatabaseProduct.contains('oracle')) {
        pipelineTimeSql="(CAST(#{taskEpochBase} AS TIMESTAMP)+NUMTODSINTERVAL(sortPipeline.updated_at/1000.0,'SECOND'))"
    } else { exit 500, '当前业务库尚未适配任务修改时间排序' }
    // 有画布时不采用任务监控更新时间；旧任务才回退任务时间，避免监控刷新让列表反复置顶。
    // 数据库一次展开最多15跳授权祖先并聚合；先聚合再关联候选，避免逐候选重复追溯。
    // MySQL/H2要求RECURSIVE关键字，达梦/Oracle使用递归WITH，不能直接复用同一前缀。
    var recursivePrefix=sortDatabaseProduct.contains('dm') || sortDatabaseProduct.contains('oracle') ? 'WITH ' : 'WITH RECURSIVE '
    var taskSortCteSql=recursivePrefix+"""
        task_ancestry(task_id,root_table_id,parent_table_id,modified_time,hop_depth) AS (
          SELECT sortTask.tid,hop.tid,hop.source_table_id,
                 CASE WHEN sortPipeline.updated_at IS NOT NULL THEN """+pipelineTimeSql+""" ELSE sortTask.updated_time END,0
            FROM data_access_agg_task_t sortTask
            JOIN db_table_t hop ON hop.tid=sortTask.source_table_id AND hop.tenant_id=#{tenantId} AND hop.is_del=0
            JOIN db_table_t sortTarget ON sortTarget.tid=sortTask.target_table_id AND sortTarget.tenant_id=#{tenantId} AND sortTarget.is_del=0
            LEFT JOIN nifi_pipeline_t sortPipeline ON sortPipeline.id=sortTask.pipeline_id AND sortPipeline.tenant_id=#{tenantId} AND sortPipeline.is_del=0
           WHERE sortTask.tenant_id=#{tenantId} AND sortTask.is_del=0
             AND hop.datasource_id IN <foreach collection="sortTargetSourceIds" item="sortHopSource" open="(" close=")" separator=",">#{sortHopSource}</foreach>
             AND sortTarget.datasource_id IN <foreach collection="sortTargetSourceIds" item="sortTargetSource" open="(" close=")" separator=",">#{sortTargetSource}</foreach>
          UNION ALL
          SELECT ancestry.task_id,parent.tid,parent.source_table_id,ancestry.modified_time,ancestry.hop_depth+1
            FROM task_ancestry ancestry
            JOIN db_table_t parent ON parent.tid=ancestry.parent_table_id AND parent.tenant_id=#{tenantId} AND parent.is_del=0
           WHERE ancestry.hop_depth<15 AND parent.tid<>ancestry.root_table_id
             AND parent.datasource_id IN <foreach collection="sortTargetSourceIds" item="sortAncestorSource" open="(" close=")" separator=",">#{sortAncestorSource}</foreach>
        ),task_sort AS (
          SELECT root_table_id,MAX(modified_time) AS task_modified_time,COUNT(DISTINCT task_id) AS sort_task_count
            FROM task_ancestry GROUP BY root_table_id
        )
    """
    var keywordPattern = '%' + tableKeyword.toLowerCase().replace('!', '!!').replace('%', '!%').replace('_', '!_') + '%'
    var visibleTaskDatasourceIds = []
    var taskStatusSql = ''
    if (['0','1','2','3'].contains(requestedAccessStatus)) {
        // 目标库不必是原始库的登记状态；但来源/目标都必须通过角色范围。
        var taskDatasourceRows = db.table('db_datasource_t').where()
            .eq('tenant_id', tenantId).eq('is_del', 0).select()
        taskDatasourceRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', taskDatasourceRows)
        for (taskDatasource in taskDatasourceRows) { visibleTaskDatasourceIds.add(text(taskDatasource.tid)) }
        if (visibleTaskDatasourceIds.size() == 0) { visibleTaskDatasourceIds = ['__none__'] }
        var accessPipelineJoin = ['0','2'].contains(requestedAccessStatus) ? '' : """
                LEFT JOIN nifi_pipeline_t access_pipeline ON access_pipeline.id=task.pipeline_id
                     AND access_pipeline.tenant_id=#{tenantId} AND access_pipeline.is_del=0
        """
        // 已绑定有效画布以其 PG 为准；旧任务才使用任务 PG，与下方任务装饰使用同一规则。
        var effectiveGroupSql = '(CASE WHEN access_pipeline.id IS NOT NULL THEN access_pipeline.nifi_process_group_id ELSE task.process_group_id END)'
        var taskRuntimeCondition = '1=0'
        if (runningGroupChunks.size() > 0) {
            taskRuntimeCondition = '<foreach collection="runningGroupChunks" item="groupBatch" open="(" close=")" separator=" OR ">'
                + effectiveGroupSql + ' IN <foreach collection="groupBatch" item="runningGroup" open="(" close=")" separator=",">#{runningGroup}</foreach></foreach>'
        }
        var existsSql = """
            EXISTS (
                SELECT 1 FROM data_access_agg_task_t task
                JOIN db_table_t hop ON hop.tid=task.source_table_id
                     AND hop.tenant_id=#{tenantId} AND hop.is_del=0
                JOIN db_datasource_t hop_db ON hop_db.tid=hop.datasource_id
                     AND hop_db.tenant_id=#{tenantId} AND hop_db.is_del=0
                JOIN db_table_t target ON target.tid=task.target_table_id
                     AND target.tenant_id=#{tenantId} AND target.is_del=0
                ${accessPipelineJoin}
                WHERE task.tenant_id=#{tenantId} AND task.is_del=0
                  AND hop.tid=t.tid
                  AND hop.datasource_id IN <foreach collection="visibleTaskDatasourceIds" item="hopDb" open="(" close=")" separator=",">#{hopDb}</foreach>
                  AND target.datasource_id IN <foreach collection="visibleTaskDatasourceIds" item="targetDb" open="(" close=")" separator=",">#{targetDb}</foreach>
            )
        """
        if (requestedAccessStatus == '0' || requestedAccessStatus == '2') {
            taskStatusSql = ' AND NOT ' + existsSql + (requestedAccessStatus == '2' ? ' AND COALESCE(t.access_request_status,0)=1' : ' AND COALESCE(t.access_request_status,0)<>1')
        } else {
            var runningExistsSql = existsSql.replace('AND hop.tid=t.tid', 'AND hop.tid=t.tid AND (' + taskRuntimeCondition + ')')
            if (requestedAccessStatus == '1') {
                taskStatusSql = ' AND ' + runningExistsSql
            } else {
                // 快照不可用时，仅无 PG 的未部署任务可确定为待启动，已有 PG 的任务保持待确认。
                var excludedExistsSql = tableRuntimeSnapshot.fresh == true ? runningExistsSql
                    : existsSql.replace('AND hop.tid=t.tid', 'AND hop.tid=t.tid AND NULLIF(TRIM(' + effectiveGroupSql + "), '') IS NOT NULL")
                taskStatusSql = ' AND ' + existsSql + ' AND NOT ' + excludedExistsSql
            }
        }
    }
    // 同值以表ID稳定排序，空值始终排末尾；全部、搜索和状态页签复用同一分页顺序。
    var tableOrderExpression = sortField=='taskCount' ? 'ordered_table.sort_task_count' : sortField=='updatedTime' ? 'ordered_table.access_modified_time' : 'ordered_table.'+sortColumn
    var tableNullOrderSql = sortField=='taskCount' ? '' : 'CASE WHEN '+tableOrderExpression+' IS NULL THEN 1 ELSE 0 END ASC,'
    var tablePageSql = """
        SELECT t.*
    """+',task_sort.task_modified_time'+(sortField=='taskCount' ? ',COALESCE(task_sort.sort_task_count,0) AS sort_task_count' : '')+"""
          FROM db_table_t t
        LEFT JOIN task_sort ON task_sort.root_table_id=t.tid
         WHERE t.tenant_id=#{tenantId} AND t.is_del=0 AND t.asset_status>0
           AND t.datasource_id IN <foreach collection="scopedDatasourceIds" item="sourceDb" open="(" close=")" separator=",">#{sourceDb}</foreach>
           AND t.business_type IN ('业务表','日志表','字典表')
           AND (#{tableKeyword}='' OR LOWER(t.table_name) LIKE #{keywordPattern} ESCAPE '!'
                OR LOWER(t.table_name_cn) LIKE #{keywordPattern} ESCAPE '!'
                OR LOWER(t.table_comment) LIKE #{keywordPattern} ESCAPE '!')
    """ + taskStatusSql
    tablePageSql='SELECT modified_table.*,CASE WHEN modified_table.updated_time IS NULL OR modified_table.task_modified_time>modified_table.updated_time THEN modified_table.task_modified_time ELSE modified_table.updated_time END AS access_modified_time FROM ('+tablePageSql+') modified_table'
    tablePageSql=taskSortCteSql+'SELECT ordered_table.* FROM ('+tablePageSql+') ordered_table ORDER BY '+tableNullOrderSql+tableOrderExpression+' '+sortDir+',ordered_table.tid ASC'
    var registeredTablePage = db.page(tablePageSql, pageSize, (pageNum - 1) * pageSize)
    var registeredTableTotal = registeredTablePage == null || registeredTablePage.total == null ? 0 : registeredTablePage.total
    var registeredTableRows = registeredTablePage == null || registeredTablePage.list == null ? [] : registeredTablePage.list
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

    // 当前页登记表已经来自关系库，直接复用同一批权威记录。
    // 重读完整行会重复传输字段治理和字典配置等大字段，导致大页在低带宽环境超时。
    var tableWorkflowMap = registeredTableMap

    // 已有链路支持 source_table_id 保留原始根、逐跳父级两种历史表示，根自指也有效。
    // 固定 16 层按父级主键关联；用祖先 ID 集合过滤，避免每个 hop 与整页 root 的 OR 连接乘积。
    // 一次批量查询后按祖先集合归组，不按表行或链路深度补查；别名固定，请求值仍绑定。
    var materializedTargetMap = {}
    var materializedTargetDatasourceIds = []
    var materializedTargets = []
    var candidateRootMap = {}
    var chainSourceIds = []
    var chainRootBySource = {}
    var chainRootsBySource = {}
    for (candidateId in candidateIds) {
        candidateRootMap[candidateId] = true
        chainSourceIds.add(candidateId)
    }
    var rootIdSetSql = '<foreach collection="candidateIds" item="rootId" open="(" close=")" separator=",">#{rootId}</foreach>'
    var ancestorJoinSql = ''
    var ancestorColumnsSql = ',hop.source_table_id AS ancestorSource0'
    var ancestorMatchSql = '(hop.tid<>hop.source_table_id AND hop.source_table_id IN ' + rootIdSetSql + ')'
    var ancestorDepth = 1
    while (ancestorDepth <= 15) {
        var ancestorAlias = 'ancestor_' + ancestorDepth
        var previousAlias = ancestorDepth == 1 ? 'hop' : 'ancestor_' + (ancestorDepth - 1)
        ancestorJoinSql = ancestorJoinSql + ' LEFT JOIN db_table_t ' + ancestorAlias
            + ' ON ' + ancestorAlias + '.tid=' + previousAlias + '.source_table_id'
            + ' AND ' + ancestorAlias + '.tenant_id=#{tenantId} AND ' + ancestorAlias + '.is_del=0'
            + ' AND ' + ancestorAlias + '.tid<>' + previousAlias + '.tid'
        ancestorColumnsSql = ancestorColumnsSql + ',' + ancestorAlias + '.source_table_id AS ancestorSource' + ancestorDepth
        ancestorMatchSql = ancestorMatchSql + ' OR (hop.tid<>' + ancestorAlias + '.source_table_id AND '
            + ancestorAlias + '.source_table_id IN ' + rootIdSetSql + ')'
        ancestorDepth = ancestorDepth + 1
    }
    if (candidateIds.size() > 0) {
        var familySql = 'SELECT hop.*' + ancestorColumnsSql + ' FROM db_table_t hop' + ancestorJoinSql
            + ' WHERE hop.tenant_id=#{tenantId} AND hop.is_del=0 AND (' + ancestorMatchSql + ')'
        var familyPage = db.page(familySql, 2001, 0)
        if (familyPage != null && familyPage.total != null && familyPage.total::int > 2000) {
            exit 413, '当前页关联表超过 2000 项，请缩小页大小或选择具体数据源后重试'
        }
        materializedTargets = familyPage == null || familyPage.list == null ? [] : familyPage.list
        var materializedAssociationCount = 0
        for (materializedTarget in (materializedTargets == null ? [] : materializedTargets)) {
            var materializedTid = text(first(materializedTarget, ['tid']))
            if (blank(materializedTid)) { continue }
            var matchedRoots = []
            var matchedDepth = 0
            while (matchedDepth < 16) {
                var materializedRootId = text(first(materializedTarget, ['ancestorSource' + matchedDepth,
                    'ancestorsource' + matchedDepth, 'ancestor_source_' + matchedDepth]))
                if (materializedRootId != materializedTid && candidateRootMap[materializedRootId] == true
                    && !matchedRoots.contains(materializedRootId)) {
                    matchedRoots.add(materializedRootId)
                    materializedAssociationCount = materializedAssociationCount + 1
                    if (materializedAssociationCount > 2000) {
                        exit 413, '当前页关联表超过 2000 项，请缩小页大小或选择具体数据源后重试'
                    }
                    if (materializedTargetMap[materializedRootId] == null) { materializedTargetMap[materializedRootId] = [] }
                    materializedTargetMap[materializedRootId].add(materializedTarget)
                    if (chainRootsBySource[materializedTid] == null) { chainRootsBySource[materializedTid] = [] }
                    chainRootsBySource[materializedTid].add(materializedRootId)
                    if (chainRootBySource[materializedTid] == null) { chainRootBySource[materializedTid] = materializedRootId }
                }
                matchedDepth = matchedDepth + 1
            }
            if (matchedRoots.size() > 0) {
                if (!chainSourceIds.contains(materializedTid)) { chainSourceIds.add(materializedTid) }
                var materializedDbId = text(first(materializedTarget, ['datasourceId', 'datasource_id']))
                if (!materializedTargetDatasourceIds.contains(materializedDbId)) { materializedTargetDatasourceIds.add(materializedDbId) }
            }
        }
    }
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
            // 新任务候选必须登记；已有下游仅按租户、有效记录、角色授权，不能因数仓库登记状态为 0 隐藏。
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
        var materializedTarget = materializedTargetsForSource.size() > 0 ? materializedTargetsForSource[0] : null
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
completedDatasourceRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', completedDatasourceRows)
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
           AND source.asset_status > 0
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
           AND t.asset_status > 0
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
