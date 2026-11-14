{
  "properties": {},
  "id": "93fcc3a46552459d836f50a112addafd",
  "script": null,
  "groupId": "2cb941922e934cb0984438557189e854",
  "name": "02.工作台事项分页",
  "path": "/items",
  "createTime": 1792844788331,
  "updateTime": null,
  "parameters": [],
  "options": [],
  "description": "按当前租户和工作台角色范围分页查询表、数据源、任务或变更。表事项先选定同名代表行，最多核验 100 个选中表 ID。来源事项可按登记阶段筛选，表数取同名代表行，采集状态取最近有效任务。返回当前视图、数据范围、列表和总数。",
  "method": "POST",
  "requestBody": "{\"scope\":\"auto\"}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
import '@/common/workbenchData' as workbenchData
import '@/common/workbenchChanges' as workbenchChanges
import tableRead
import tableSort
import java.util.List
import java.util.Objects

// POST /dst/statistics/workbench/items 为工作台下钻及勾选清单提供分页。
// 按当前租户和角色解析视图，支持 kind、搜索、状态、业务类型及排序，返回列表、总数和实际数据范围。
// 表事项先按所属数据源选定代表行，再整批验证选中 ID，最后筛选和分页。本接口只读。
// 来源事项支持探查、字典、业务及审查阶段，显示同名代表表统计和最近有效采集状态。
var context = workbenchData(body)
var pageNo = Math.max(1, body?.pageNo == null ? 1 : body.pageNo::int)
var pageSize = Math.min(100, Math.max(1, body?.pageSize == null ? 20 : body.pageSize::int))
var kind = body?.kind == null ? 'tables' : body.kind + ''
if (kind == 'changes') {
    return workbenchChanges(body, context)
}
if (!['tables', 'sources', 'tasks'].contains(kind)) {
    exit 400, '工作台事项类型无效'
}
var keyword = body?.keyword == null ? '' : (body.keyword + '').trim()
if (keyword.length() > 100) {
    exit 400, '搜索关键字最多100字'
}
var keywordLike = '%' + keyword + '%'
var envelope = (rows, total) => {
    return {
        list: rows, total: total, pageNo: pageNo, pageSize: pageSize, mode: context.mode, scope: context.scope
    }
}
var defaultSort = keyword == '' ? 'updatedTime' : 'name'
var text = (alias, field) => tableRead.column(alias, field, 'TEXT')
var number = (alias, field) => tableRead.column(alias, field, 'NUMBER')
var time = (alias, field) => tableRead.column(alias, field, 'TIME')

var sourceFilters = () => {
    // 数据源须在当前角色的可见范围，ALL 的“我负责的”仍限定为本人创建的来源。
    var sources = [tableRead.equal(number('s', 'is_del'), 0)]
    if (context.scope == 'ORG') {
        sources.add(tableRead.in(text('s', 'org_id'), context.scopeOrgIds))
    } else if (context.scope == 'OWNER' || (context.scope == 'ALL' && context.mode == 'mine')) {
        sources.add(tableRead.equal(text('s', 'created_by'), context.userId))
    }
    return sources
}
var representativeTables = projection => {
    var tables = tableRead.from('db_table_t', 't', context.tenantId)
    tables.leftJoin('db_datasource_t', 's', 'tid', 'datasource_id', sourceFilters())
    tables.where(tableRead.not(tableRead.isNull(text('s', 'tid'))))
    tables.where(tableRead.equal(number('t', 'is_del'), 0))

    // 在筛选之前选择每个来源的同名代表行，保留旧排名的优先级和数据库原生空时间顺序。
    return tables.ranked('r', projection, [text('t', 'datasource_id'), tableRead.lower(text('t', 'table_name'))], [
        tableRead.coalesce([number('t', 'annotated'), 0]), tableRead.coalesce([number('t', 'field_count'), 0]),
        time('t', 'updated_time'), text('t', 'tid')
    ], ['desc', 'desc', 'desc', 'desc'], true)
}
var pagedEnvelope = page => {
    // 保留分页方言附带的绝对行号，以及没有匹配项与请求页越界的区别。
    var rows = page.total == 0 ? null : page.rows.map(row => {
        if (row.containsKey('ROW_ID')) {
            row.rowId = row.remove('ROW_ID')
        } else if (row.containsKey('row_id')) {
            row.rowId = row.remove('row_id')
        }
        return row
    })
    return envelope(rows, page.total)
}

if (kind == 'tables') {
    var businessType = body?.businessType == null ? '' : body.businessType + ''
    var status = body?.status == null ? '' : body.status + ''
    if (!['', 'pending', 'unregistered', 'registered'].contains(status)) {
        exit 400, '表状态无效'
    }
    var comment = alias => tableRead.column(alias, 'table_comment', 'LOB')
    var ranked = representativeTables({
        tid: text('t', 'tid'), datasource_id: text('t', 'datasource_id'), table_name: text('t', 'table_name'),
        table_name_cn: text('t', 'table_name_cn'), table_comment: comment('t'), source_name: text('s', 'db_name'),
        business_type: text('t', 'business_type'), business_type_reason: text('t', 'business_type_reason'),
        annotated: number('t', 'annotated'), asset_status: number('t', 'asset_status'), field_count: number('t', 'field_count'),
        created_time: time('t', 'created_time'), updated_time: time('t', 'updated_time')
    })

    var selected = body?.ids
    var rankedId = text('r', 'tid')
    if (selected != null) {
        if (!(selected instanceof List) || selected.isEmpty() || selected.size() > 100
            || selected.distinct().size() != selected.size()) {
            exit 400, '处理清单须包含1至100个不重复表ID'
        }
        var ids = selected.map(id => Objects.toString(id, null))
        ranked.where(tableRead.in(rankedId, ids))
        // 从授权代表行一次取回全部键，逐个比较原始字符，避免宽松排序规则接受近似 ID。
        var permitted = ranked.grouped({id: rankedId}, []).map(row => Objects.toString(row.id, null))
        if (permitted.size() != ids.size() || !permitted.containsAll(ids) || ids.distinct().size() != ids.size()) {
            exit 403, '处理清单包含已失效、跨租户或无权访问的数据表'
        }
    }

    var rankedAnnotated = number('r', 'annotated')
    var assetStatus = number('r', 'asset_status')
    var rankedBusinessType = text('r', 'business_type')
    var rankedName = text('r', 'table_name')
    var nameCn = text('r', 'table_name_cn')
    var tableComment = comment('r')
    var sourceName = text('r', 'source_name')
    if (status == 'pending') {
        ranked.where(tableRead.not(tableRead.equal(tableRead.coalesce([rankedAnnotated, 0]), 1)))
    } else if (status == 'unregistered') {
        ranked.where(tableRead.not(tableRead.equal(tableRead.coalesce([assetStatus, 0]), 2)))
    } else if (status == 'registered') {
        ranked.where(tableRead.equal(assetStatus, 2))
    }
    if (businessType != '') {
        ranked.where(tableRead.equal(rankedBusinessType, businessType))
    }
    if (keyword != '') {
        // 沿用现有搜索的 LIKE 通配语义，搜索条件只匹配已选定的代表行。
        ranked.where(tableRead.any([
            tableRead.like(rankedName, keywordLike), tableRead.like(nameCn, keywordLike),
            tableRead.like(tableComment, keywordLike), tableRead.like(sourceName, keywordLike)
        ]))
    }

    var sortFields = {
        name: 'table_name', tableName: 'table_name', source: 'source_name',
        createdTime: 'created_time', updatedTime: 'updated_time', columnCount: 'field_count'
    }
    var sortKey = tableSort.field(body, sortFields, defaultSort)
    var direction = tableSort.direction(body)
    var sortType = sortKey == 'field_count' ? 'NUMBER' : (sortKey.endsWith('_time') ? 'TIME' : 'TEXT')
    var page = ranked.page({
        id: rankedId, datasourceId: text('r', 'datasource_id'), tableName: rankedName, tableNameCn: nameCn,
        tableComment: tableComment, tableLabel: tableRead.coalesce([tableRead.blankAsNull(nameCn), tableComment, rankedName]),
        source: sourceName, businessType: rankedBusinessType, annotated: rankedAnnotated, assetStatus: assetStatus,
        columnCount: number('r', 'field_count'), businessTypeReason: text('r', 'business_type_reason'),
        createdTime: time('r', 'created_time'), updatedTime: time('r', 'updated_time')
    }, tableRead.column('r', sortKey, sortType), direction, 'tid', pageNo, pageSize)

    return pagedEnvelope(page)
}

if (kind == 'sources') {
    var stage = body?.stage == null ? '' : body.stage + ''
    if (!['', 'explore', 'dictionary', 'business', 'review'].contains(stage)) {
        exit 400, '登记阶段无效'
    }
    var sources = tableRead.from('db_datasource_t', 's', context.tenantId)
    sourceFilters().forEach(condition => sources.where(condition))
    var sourceId = text('s', 'tid')
    var sourceName = text('s', 'db_name')
    var assetStatus = number('s', 'asset_status')
    var createdAt = time('s', 'created_time')
    var updatedAt = time('s', 'updated_time')
    if (keyword != '') {
        sources.where(tableRead.like(sourceName, keywordLike))
    }

    // 标注和登记都以代表表为准。只有相应登记阶段需要读取历史治理配置。
    var checkSaved = stage == 'dictionary' || stage == 'business'
    var projection = {datasource_id: text('t', 'datasource_id'), annotated: number('t', 'annotated')}
    if (checkSaved) {
        projection.business_type = text('t', 'business_type')
        projection.asset_status = number('t', 'asset_status')
        projection.field_governance_config = tableRead.column('t', 'field_governance_config', 'LOB')
    }
    var ranked = representativeTables(projection)
    var rankedSourceId = text('r', 'datasource_id')
    var stats = {
        datasource_id: rankedSourceId,
        table_count: tableRead.count(),
        marked_count: tableRead.countIf(tableRead.equal(number('r', 'annotated'), 1))
    }
    if (checkSaved) {
        var types = stage == 'dictionary' ? ['字典表'] : ['业务表', '日志表']
        var required = tableRead.in(text('r', 'business_type'), types)
        var document = tableRead.column('r', 'field_governance_config', 'LOB')
        // 沿用历史配置的字面标记，兼容含空格的旧文本以及已登记状态。
        var marker = '"registrationSavedPhase":"' + stage + '"'
        var saved = tableRead.any([
            tableRead.equal(number('r', 'asset_status'), 2),
            tableRead.hasSubstring(tableRead.replace(document, ' ', ''), marker)
        ])
        stats.required_count = tableRead.countIf(required)
        stats.saved_count = tableRead.countIf(tableRead.all([required, saved]))
    }
    if (checkSaved || stage == 'explore') {
        sources.leftJoinGrouped(ranked, 'ss', 'datasource_id', 'tid', stats, [rankedSourceId])
    } else {
        sources.leftJoinGroupedProjection(ranked, 'ss', 'datasource_id', 'tid', stats, [rankedSourceId])
    }

    // 采集记录按原始来源键分组，先按创建时间和任务 ID 选最近的有效记录，再按历史键宽度关联。
    var jobs = tableRead.from('metadata_table_collection_job_t', 'j', context.tenantId)
    jobs.where(tableRead.equal(number('j', 'is_del'), 0))
    var jobSourceId = text('j', 'datasource_id')
    var latest = jobs.ranked('l', {
        datasource_id: jobSourceId, collection_status: text('j', 'status')
    }, [jobSourceId], [time('j', 'created_time'), text('j', 'tid')], ['desc', 'desc'], true)
    var latestSourceId = text('l', 'datasource_id')
    sources.leftJoinGrouped(latest, 'lj', 'datasource_id', 'tid', {
        datasource_id: latestSourceId, collection_status: tableRead.max(text('l', 'collection_status'))
    }, [latestSourceId], 64)

    var tableCount = number('ss', 'table_count')
    var markedCount = number('ss', 'marked_count')
    var totalTables = tableRead.coalesce([tableCount, 0])
    var totalMarked = tableRead.coalesce([markedCount, 0])
    var collectionStatus = text('lj', 'collection_status')
    if (stage == 'explore') {
        sources.where(tableRead.equal(totalTables, 0))
        // 沿用原比较，由数据库处理空字符串。达梦缺少采集状态时仍不会列入待探查。
        sources.where(tableRead.not(tableRead.equal(tableRead.coalesce([collectionStatus, '']), 'SUCCEEDED')))
    } else if (checkSaved) {
        var requiredCount = tableRead.coalesce([number('ss', 'required_count'), 0])
        var savedCount = tableRead.coalesce([number('ss', 'saved_count'), 0])
        sources.where(tableRead.not(tableRead.all([
            tableRead.greaterThan(totalTables, 0), tableRead.equal(totalMarked, totalTables),
            tableRead.equal(requiredCount, savedCount)
        ])))
    } else if (stage == 'review') {
        sources.where(tableRead.not(tableRead.equal(tableRead.coalesce([assetStatus, 0]), 2)))
    }

    // 展示将缺失的统计计为零，排序仍用原始列，未采集的空值放到页尾。
    var sortFields = {
        name: 'name', createdTime: 'createdTime', updatedTime: 'updatedTime',
        tableCount: 'tableCount', markedCount: 'markedCount'
    }
    var sorts = {
        name: sourceName, createdTime: createdAt, updatedTime: updatedAt,
        tableCount: tableCount, markedCount: markedCount
    }
    var sort = sorts[tableSort.field(body, sortFields, defaultSort)]
    var page = sources.page({
        id: sourceId, name: sourceName, source: sourceName, assetStatus: assetStatus,
        connectionStatus: text('s', 'connection_status'), createdTime: createdAt, updatedTime: updatedAt,
        tableCount: totalTables, markedCount: totalMarked, collectionStatus: collectionStatus
    }, sort, tableSort.direction(body), 'tid', pageNo, pageSize)
    return pagedEnvelope(page)
}

// 任务分支继续使用共享范围的既有查询，保留参数绑定、名称检索和分页契约。
var tenantId = context.tenantId
var userId = context.userId
var scopeOrgIds = context.scopeOrgIds
var sql = context.cte + """
    SELECT k.tid id,k.task_name name,k.db_name source,k.datasource_id,k.source_table_id,k.table_name,
           k.runtime_state,k.monitor_time,k.monitor_status,k.monitor_msg,k.delay_ms_estimate,k.queued_count,
           k.flow_files_in,k.flow_files_out,k.active_thread_count,k.last_running,k.end_running,k.updated_time,k.created_time
    FROM task_scope k
    WHERE 1=1
"""
if (keyword != '') {
    sql += ' AND (k.task_name LIKE #{keywordLike} OR k.db_name LIKE #{keywordLike}) '
}
var column = tableSort.field(body, {name: 'k.task_name', createdTime: 'k.created_time', updatedTime: 'k.updated_time'}, defaultSort)
var direction = tableSort.direction(body)
var order = ' ORDER BY CASE WHEN ' + column + ' IS NULL THEN 1 ELSE 0 END,' + column + ' ' + direction + ',k.tid ASC'
var page = db.page(sql + order, pageSize, (pageNo - 1) * pageSize)
return envelope(page.list, page.total)
