{
  "properties" : { },
  "id" : "084a92c02bd05f8bb8507cc4c4660e40",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "02.catalog-read",
  "createTime" : null,
  "updateTime" : 1791030855462,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/catalog-read",
  "description" : "读取当前租户的磐石资源中心目录。分页按来源数据源范围与活跃分层或分库绑定过滤，支持关键词、来源、审核状态、编目筛选和排序。另供详情、索引投影和兼容交付台账读取，不修改业务数据。",
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "input",
    "value" : null,
    "description" : "mode 选择读取入口，单条查询传 id，分页传 query、page 和 size。业务租户与角色范围由当前会话确定。",
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ]
}
================================
import tenantRuntime
import tableSort
import tableRead
import dataScope
import jsons
import cn.dev33.satoken.stp.StpUtil
import java.time.Instant
import '@post:/sym/user/me' as currentUser

/**
 * 磐石目录读取函数。/resource-center/catalog-read
 * mode 选择读取入口。编目分页接收 query、page 和 size，编目单条和详情按 id 读取。
 * 返回当前租户可见的目录分页、单条详情、内部索引投影或兼容交付台账。
 * 查询只读。公开入口应用各自的角色范围，审核状态以目录主表为准。
 */

var tenantId = tenantRuntime.id()
var text = value => value == null ? '' : value + ''
var blank = value => text(value).trim() == ''
var mode = input == null ? '' : text(input.mode)
var id = input == null ? '' : text(input.id)

// 把角色范围加到业务查询上，目录和来源数据源各自使用相应的权限决策。
var restrictDirectoryScope = (query, scope) => {
    if (scope.scope == 'OWNER') {
        if (blank(scope.userId)) {
            exit 403, '数据范围无效'
        }
        query.eq('created_by', scope.userId)
    } else if (scope.scope == 'ORG') {
        if (scope.organizationIds == null || scope.organizationIds.size() == 0) {
            exit 404, '数据目录不存在或无权查看'
        }
        query.in('org_id', scope.organizationIds)
    } else if (scope.scope != 'ALL') {
        exit 403, '数据范围无效'
    }
}

var directoryDetail = directoryId => {
    if (blank(tenantId)) {
        exit 401, '当前登录租户无效，请重新登录'
    }
    if (blank(directoryId)) {
        exit 400, '目录标识不能为空'
    }

    var centerDirectory = mode == 'center-directory-detail'
    var catalog = db.lower().table('da_catalog_t')
        .columns('tid', 'tenant_id', 'asset_status', 'flow_status', 'reg_time', 'created_time', 'updated_time',
            'catalog_name', 'catalog_name_en', 'asset_desc', 'app_id', 'app_name', 'db_id', 'db_name',
            'source_table_id', 'source_table_name', 'source_db_id', 'source_db_name',
            'target_table_id', 'target_table_name', 'target_db_id', 'target_db_type',
            'org_id', 'manage_unit', 'business_type', 'data_source_type',
            'timestamp_field', 'dictionary_table_flag', 'field_governance_config', 'market_view_count')
        .where()
        .eq('tenant_id', tenantId)
        .eq('tid', directoryId)
    if (centerDirectory) {
        catalog.eq('is_del', 0)
    } else {
        // 兼容盘点历史数据中未设置删除标记的有效目录。
        catalog.and(active => active.eq('is_del', 0).or().isNull('is_del'))
    }
    if (mode == 'directory-detail') {
        // 盘点详情沿用盘点列表范围。
        restrictDirectoryScope(catalog, dataScope.resource('MENU:2081000000000000002'))
    }
    var row = catalog.selectOne()
    if (row == null) {
        exit 404, '数据目录不存在或无权查看'
    }
    if (centerDirectory) {
        // 资源中心列表按活跃来源、分层或分库绑定和来源角色范围授权。
        var scope = dataScope.dataSource()
        if (blank(scope.userId)) {
            exit 403, '数据范围无效'
        }
        if (blank(row.source_table_id)) {
            exit 404, '数据目录不存在或无权查看'
        }

        var sourceTable = db.lower().table('db_table_t')
            .columns('datasource_id')
            .where()
            .eq('tenant_id', tenantId)
            .eq('tid', row.source_table_id)
            .eq('is_del', 0)
            .selectOne()
        if (sourceTable == null || blank(sourceTable.datasource_id)) {
            exit 404, '数据目录不存在或无权查看'
        }

        var source = db.table('db_datasource_t')
            .columns('tid')
            .where()
            .eq('tenant_id', tenantId)
            .eq('tid', sourceTable.datasource_id)
            .eq('is_del', 0)
        restrictDirectoryScope(source, scope)
        if (source.selectOne() == null) {
            exit 404, '数据目录不存在或无权查看'
        }

        var bindingCount = db.table('dwm_center_layer_source_t').where()
            .eq('tenant_id', tenantId)
            .eq('datasource_id', sourceTable.datasource_id)
            .eq('is_del', 0)
            .in('target_type', ['layer', 'domain'])
            .count()
        if (bindingCount == 0) {
            exit 404, '数据目录不存在或无权查看'
        }
    }

    // 保留旧详情返回的字段名与资产状态别名。
    var item = {
        tid: row.tid,
        id: row.tid,
        tenantId: row.tenant_id,
        assetType: 'catalog',
        assetStatus: row.asset_status,
        flowStatus: row.flow_status,
        regTime: row.reg_time,
        createdTime: row.created_time,
        updatedTime: row.updated_time,
        catalogName: row.catalog_name,
        catalogNameEn: row.catalog_name_en,
        assetDesc: row.asset_desc,
        appId: row.app_id,
        appName: row.app_name,
        dbId: row.db_id,
        dbName: row.db_name,
        sourceTableId: row.source_table_id,
        sourceTableName: row.source_table_name,
        sourceDbId: row.source_db_id,
        sourceDbName: row.source_db_name,
        targetTableId: row.target_table_id,
        targetTableName: row.target_table_name,
        targetDbId: row.target_db_id,
        targetDbType: row.target_db_type,
        orgId: row.org_id,
        manageUnit: row.manage_unit,
        businessType: row.business_type,
        dataSourceType: row.data_source_type,
        timestampField: row.timestamp_field,
        dictionaryTableFlag: row.dictionary_table_flag,
        fieldGovernanceConfig: row.field_governance_config
    }
    item.asset_type = item.assetType
    item.asset_status = item.assetStatus
    item.flow_status = item.flowStatus

    // 目录通过权限检查后，一次读取所有扩展属性，只填补缺失或空的详情字段。
    var props = {}
    var propRows = db.lower().table('da_prop_t')
        .columns('prop_name', 'prop_value')
        .where()
        .eq('tenant_id', tenantId)
        .eq('parent_id', row.tid)
        .eq('data_type', 'catalog')
        .and(active => active.eq('is_del', 0).or().isNull('is_del'))
        .select()
    for (prop in propRows) {
        var name = prop.prop_name
        var value = prop.prop_value
        if (!blank(name)) {
            props[name] = value
            if (item[name] == null || item[name] == '') {
                item[name] = value
            }
        }
    }
    // 关系库累计值为权威，扩展属性仅用于其他业务字段。
    item.viewCount = row.market_view_count::long(0)
    props.remove('viewCount')
    item.props = props
    return item
}

if (['directory-detail', 'center-directory-detail', 'directory-index-projection'].contains(mode)) {
    // 索引刷新入口只由已完成目录业务校验和状态更新的内部命令调用。
    return directoryDetail(id)
}

var val = (row, name) => row == null ? null : row[name] == null ?
    row[name.toLowerCase()] == null ? row[name.toUpperCase()] : row[name.toLowerCase()] : row[name]

var assembleCatalogs = (catalogRows, itemRows) => {
    var itemsByCatalog = {}
    for (item in itemRows) {
        var key = text(val(item, 'catalogId'))
        if (itemsByCatalog[key] == null) {
            itemsByCatalog[key] = []
        }
        itemsByCatalog[key].add(item)
    }
    var catalogs = []
    for (row in catalogRows) {
        var key = text(val(row, 'id'))
        var items = itemsByCatalog[key] == null ? [] : itemsByCatalog[key]
        var fieldIds = []
        var labels = {}
        for (item in items) {
            var fieldId = text(val(item, 'fieldId'))
            if (fieldId == '') {
                fieldId = text(val(item, 'itemId'))
            }
            if (fieldId != '') {
                fieldIds.add(fieldId)
                labels[fieldId] = val(item, 'label')
            }
        }
        var assetStatus = val(row, 'assetStatus')
        var flowStatus = val(row, 'flowStatus')
        var state = text(flowStatus) == '1' ? 'PENDING_REVIEW' :
            text(flowStatus) == '3' ? 'RETURNED' :
            text(assetStatus) == '2' && text(flowStatus) == '2' ? 'APPROVED' : 'DRAFT'
        var draft = {
            id: text(val(row, 'id')),
            number: val(row, 'version'),
            state: state,
            name: text(val(row, 'name')),
            code: text(val(row, 'code')),
            summary: text(val(row, 'summary')),
            provider: text(val(row, 'provider')),
            contact: '',
            resourceId: text(val(row, 'resourceId')),
            resourceName: text(val(row, 'resourceName')),
            sourceId: text(val(row, 'sourceId')),
            assetStatus: assetStatus,
            flowStatus: flowStatus,
            submittedBy: val(row, 'updatedBy'),
            submittedAt: state == 'PENDING_REVIEW' ? val(row, 'updatedAt') : null,
            classificationId: '',
            keywords: [],
            frequency: 'ONCE',
            shareMode: 'NONE',
            conditions: '',
            visibility: 'WORKSPACE',
            visibleDepartments: [],
            format: 'CSV',
            fieldIds: fieldIds,
            fieldLabels: labels,
            note: '',
            checks: []
        }
        var entry = {
            id: text(val(row, 'id')),
            version: val(row, 'version'),
            draft: draft,
            revisions: [],
            createdAt: val(row, 'createdAt'),
            updatedAt: val(row, 'updatedAt'),
            createdBy: val(row, 'createdBy')
        }
        catalogs.add(entry)
    }
    return catalogs
}

var sourceScope = () => {
    var scope = dataScope.dataSource()
    if (!['ALL', 'ORG', 'OWNER'].contains(text(scope.scope)) || blank(scope.userId)) {
        exit 403, '无法确定当前数据范围'
    }
    return scope
}

var snapshotWindow = 100
var readSnapshotPages = readPage => {
    var rows = []
    var pageNo = 1
    var total = null
    // 兼容状态仍返回完整集合，每次读取有上限，空页及时结束。
    while (total == null || (pageNo - 1) * snapshotWindow < total) {
        var page = readPage(pageNo)
        var batch = page?.list
        if (batch == null || batch.size() == 0) {
            break
        }
        if (total == null) {
            total = page.total
        }
        rows.addAll(batch)
        if (batch.size() < snapshotWindow) {
            break
        }
        pageNo = pageNo + 1
    }
    return rows
}

var assembleCatalogBatch = rows => {
    if (rows.size() == 0) {
        return []
    }
    // 只为当前窗口已授权的目录 ID 批量读取数据项。
    var ids = rows.map(row => row.id)
    var itemRows = db.lower().table('da_catalog_item_t')
        .columns('catalog_id', 'tid', 'source_table_column_id', 'col_name')
        .where()
        .eq('tenant_id', tenantId)
        .in('catalog_id', ids)
        .eq('is_del', 0)
        .orderBy('catalog_id').orderBy('sort_no').orderBy('tid').select()
        .map(item => ({
            catalogId: item.catalog_id,
            itemId: item.tid,
            fieldId: item.source_table_column_id,
            label: item.col_name
        }))
    return assembleCatalogs(rows, itemRows)
}

var readCatalogEntries = scope => {
    var one = mode == 'one'
    if (one && blank(id)) {
        exit 404, '资源目录不存在'
    }
    var paged = mode == 'page'
    var query = paged && input.query != null ? input.query : {}
    var keyword = text(query.keyword).trim()
    if (keyword.length() > 200) {
        exit 400, '检索关键词过长'
    }
    var sourceId = text(query.sourceId)
    var status = text(query.status)
    if (!['', 'DRAFT', 'PENDING_REVIEW', 'RETURNED', 'APPROVED'].contains(status)) {
        exit 400, '审核状态无效'
    }
    var scopeMode = text(scope.scope)

    var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
    var number = (alias, name) => tableRead.column(alias, name, 'NUMBER')
    var time = (alias, name) => tableRead.column(alias, name, 'TIME')
    var active = alias => tableRead.equal(number(alias, 'is_del'), 0)

    // 同一来源可有多个分层或分库绑定。按数据源归并后再关联，确保目录不重复。
    var bindingSource = column('b', 'datasource_id')
    var bindings = tableRead.from('dwm_center_layer_source_t', 'b', tenantId)
    bindings.where(active('b'))
    bindings.where(tableRead.in(column('b', 'target_type'), ['layer', 'domain']))

    var sources = tableRead.from('db_table_t', 't', tenantId)
    sources.where(active('t'))
    sources.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id', [active('d')])
    sources.where(tableRead.not(tableRead.isNull(column('d', 'tid'))))
    sources.leftJoinGrouped(bindings, 'bound', 'source_id', 'datasource_id',
        {source_id: bindingSource}, [bindingSource])
    sources.where(tableRead.not(tableRead.isNull(column('bound', 'source_id'))))
    // 资源中心目录按活跃来源数据源授权，不按目录自身的创建人或机构筛选。
    if (scopeMode == 'OWNER') {
        sources.where(tableRead.equal(column('d', 'created_by'), scope.userId))
    } else if (scopeMode == 'ORG') {
        var organizations = scope.organizationIds == null ? [] : scope.organizationIds
        sources.where(tableRead.in(column('d', 'org_id'), organizations))
    }

    var tableId = column('t', 'tid')
    var catalogs = tableRead.from('da_catalog_t', 'c', tenantId)
    catalogs.where(active('c'))
    catalogs.leftJoinGrouped(sources, 'allowed_source', 'table_id', 'source_table_id',
        {table_id: tableId}, [tableId])
    catalogs.where(tableRead.not(tableRead.isNull(column('allowed_source', 'table_id'))))
    if (one) {
        catalogs.where(tableRead.equal(column('c', 'tid'), id))
    }

    var assetStatus = number('c', 'asset_status')
    var flowStatus = number('c', 'flow_status')
    var approved = tableRead.all([tableRead.equal(assetStatus, 2), tableRead.equal(flowStatus, 2)])
    var state = tableRead.when(tableRead.equal(flowStatus, 1), 'PENDING_REVIEW',
        tableRead.when(tableRead.equal(flowStatus, 3), 'RETURNED',
            tableRead.when(approved, 'APPROVED', 'DRAFT')))

    if (keyword != '') {
        // 沿用目录检索的 LIKE 通配符语义，搜索串按参数绑定。
        var pattern = '%' + keyword + '%'
        catalogs.where(tableRead.any([
            tableRead.like(column('c', 'catalog_name'), pattern),
            tableRead.like(column('c', 'catalog_name_en'), pattern),
            tableRead.like(column('c', 'source_table_name'), pattern)
        ]))
    }
    if (sourceId != '') {
        catalogs.where(tableRead.equal(column('c', 'db_id'), sourceId))
    }
    if (status != '') {
        catalogs.where(tableRead.equal(state, status))
    }

    var sortField = tableSort.field(query, {
        name: 'name', updatedAt: 'updatedAt', createdAt: 'createdAt', itemCount: 'itemCount', fields: 'itemCount'
    }, 'updatedAt')
    var sortDirection = tableSort.direction(query)
    var itemCount = null
    // 编目筛选与字段数排序都在数据库分页前执行。
    if (query.cataloged == true || sortField == 'itemCount') {
        var itemCatalogId = column('ci', 'catalog_id')
        var items = tableRead.from('da_catalog_item_t', 'ci', tenantId)
        items.where(active('ci'))
        catalogs.leftJoinGrouped(items, 'item_stats', 'catalog_id', 'tid', {
            catalog_id: itemCatalogId,
            item_count: tableRead.count()
        }, [itemCatalogId])
        itemCount = tableRead.coalesce([number('item_stats', 'item_count'), 0])
        if (query.cataloged == true) {
            catalogs.where(tableRead.greaterThan(itemCount, 0))
        }
    }

    var columns = {
        id: column('c', 'tid'),
        name: column('c', 'catalog_name'),
        code: column('c', 'catalog_name_en'),
        summary: tableRead.column('c', 'asset_desc', 'LOB'),
        provider: column('c', 'org_id'),
        resourceId: column('c', 'source_table_id'),
        resourceName: column('c', 'source_table_name'),
        sourceId: column('c', 'db_id'),
        assetStatus: assetStatus,
        flowStatus: flowStatus,
        version: tableRead.coalesce([number('c', 'resource_revision_no'), 1]),
        createdBy: column('c', 'created_by'),
        createdAt: time('c', 'created_time'),
        updatedBy: column('c', 'updated_by'),
        updatedAt: time('c', 'updated_time')
    }
    var sort = sortField == 'itemCount' ? itemCount : columns[sortField]
    if (one) {
        // 单条读复用同一权限查询，无需计算列表总数。
        var rows = catalogs.rows(columns, sort, sortDirection, 1)
        if (rows.size() == 0) {
            exit 404, '资源目录不存在'
        }
        return assembleCatalogBatch(rows)[0]
    }
    if (paged) {
        var page = catalogs.page(columns, sort, sortDirection, 'tid', input.page, input.size)
        return {list: assembleCatalogBatch(page.rows), total: page.total, page: input.page, size: input.size}
    }
    return readSnapshotPages(pageNo => {
        var page = catalogs.page(columns, sort, sortDirection, 'tid', pageNo, snapshotWindow)
        return {list: assembleCatalogBatch(page.rows), total: page.total}
    })
}

if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录'
}
var scope = sourceScope()
if (mode == 'page' || mode == 'one') {
    return readCatalogEntries(scope)
}

if (mode == 'structure') {
    // 校验同租户活动来源表。发布及订阅动作的目录权限由调用方校验。
    if (blank(id)) {
        exit 404, '来源表已删除或不属于当前租户'
    }
    var table = db.lower().table('db_table_t')
        .columns('tid', 'datasource_id', 'table_name', 'table_name_cn', 'table_comment', 'updated_time')
        .where()
        .eq('tenant_id', tenantId)
        .eq('tid', id)
        .eq('is_del', 0)
        .selectOne()
    if (table == null) {
        exit 404, '来源表已删除或不属于当前租户'
    }
    var fields = db.lower().table('db_table_column_t')
        .columns('tid', 'column_name', 'column_comment', 'data_type', 'length', 'scale',
            'nullable', 'primary_key', 'ordinal_position', 'is_masking')
        .where()
        .eq('tenant_id', tenantId)
        .eq('table_id', id)
        .eq('is_del', 0)
        .orderBy('ordinal_position').orderBy('tid')
        .select()
        .map(row => ({
            id: row.tid,
            name: row.column_name,
            path: row.column_name,
            comment: row.column_comment,
            nativeType: row.data_type,
            typeFamily: row.data_type,
            length: row.length,
            scale: row.scale,
            nullable: row.nullable,
            primaryKey: row.primary_key,
            ordinal: row.ordinal_position,
            masking: row.is_masking
        }))
    return {
        id: table.tid,
        sourceId: table.datasource_id,
        name: table.table_name,
        displayName: table.table_name_cn,
        comment: table.table_comment,
        updatedAt: table.updated_time,
        fields: fields,
        kind: 'table',
        keys: []
    }
}

var actorId = StpUtil.getLoginIdAsString()
var user = currentUser()
var orgId = text(user.orgId)
var parse = value => text(value) == '' ? {} : jsons.toJSONObject(value)
var now = new Date().getTime()

var requestQuery = () => db.lower().table('res_catalog_publish_request')
    .columns('tid', 'catalog_id', 'request_status', 'snapshot_json', 'revision_number', 'revision_no',
        'created_by', 'created_time', 'reviewer_id', 'reviewed_time', 'review_comment',
        'published_time', 'offline_reason', 'sync_status', 'updated_time')
    .where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .isNotNull('snapshot_json')
var formQuery = () => db.lower().table('data_apply_form_t')
    .columns('tid', 'apply_user_id', 'apply_org_id', 'resource_scope_json',
        'resource_revision_no', 'created_time', 'updated_time')
    .where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .isNotNull('resource_scope_json')
var taskQuery = () => db.lower().table('data_distribution_task_t')
    .columns('tid', 'apply_form_id', 'resource_result_json', 'task_status', 'status_message',
        'created_time', 'updated_time')
    .where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .eq('execution_engine', 'RESOURCE_EXPORT')

var publicationOf = row => {
    if (row == null || !['PUBLISHED', 'OFFLINE'].contains(row.request_status)) {
        return null
    }
    var revision = parse(row.snapshot_json)
    // 部门可见的发布版本需要当前部门属于发布者或配置范围。
    if (revision.visibility == 'DEPARTMENTS') {
        var visible = !blank(orgId) && (revision.provider == orgId ||
            (revision.visibleDepartments != null && revision.visibleDepartments.contains(orgId)))
        if (!visible) {
            return null
        }
    }
    revision.id = text(row.tid)
    revision.number = row.revision_number
    revision.state = 'APPROVED'
    revision.submittedBy = row.created_by
    revision.submittedAt = row.created_time
    revision.reviewer = row.reviewer_id
    revision.reviewedAt = row.reviewed_time
    revision.opinion = row.review_comment
    if (revision.checks == null) {
        revision.checks = []
    }
    return {
        id: text(row.tid),
        entryId: text(row.catalog_id),
        revisionId: text(row.tid),
        revision: revision,
        state: row.request_status == 'PUBLISHED' ? 'PUBLISHED' : 'OFFLINE',
        version: row.revision_no,
        busState: row.sync_status == null ? 'NOT_CONFIGURED' : row.sync_status,
        publishedBy: row.reviewer_id,
        reason: row.offline_reason,
        createdAt: row.published_time,
        updatedAt: row.updated_time
    }
}

var subscriptionOf = (row, sub, request) => {
    // 除申请者外，只允许冻结发布版本中记录的提供部门读取。
    var provider = request == null ? '' : text(parse(request.snapshot_json).provider)
    if (row.apply_user_id != actorId && (blank(orgId) || blank(provider) || provider != orgId)) {
        return null
    }
    sub.putAll({
        id: text(row.tid),
        applicantId: row.apply_user_id,
        department: row.apply_org_id,
        version: row.resource_revision_no,
        createdAt: row.created_time,
        updatedAt: row.updated_time
    })
    // 过期优先于下线暂停。未过期且发布有效时按开始时间激活。
    if (['ACTIVE', 'SCHEDULED'].contains(sub.state)) {
        if (Instant.parse(sub.validTo).toEpochMilli() <= now) {
            sub.state = 'EXPIRED'
        } else if (request == null || request.request_status != 'PUBLISHED') {
            sub.state = 'PAUSED'
        } else if (Instant.parse(sub.validFrom).toEpochMilli() <= now) {
            sub.state = 'ACTIVE'
        }
    }
    return sub
}
var readSubscription = subscriptionId => {
    if (blank(subscriptionId)) {
        return null
    }
    var row = formQuery().eq('tid', subscriptionId).selectOne()
    if (row == null) {
        return null
    }
    var sub = parse(row.resource_scope_json)
    var request = blank(sub.publicationId) ? null : requestQuery().eq('tid', sub.publicationId).selectOne()
    return subscriptionOf(row, sub, request)
}

var deliveryOf = row => {
    var result = parse(row.resource_result_json)
    result.putAll({
        id: text(row.tid),
        subscriptionId: text(row.apply_form_id),
        state: row.task_status,
        error: row.status_message,
        version: 1,
        createdAt: row.created_time,
        updatedAt: row.updated_time
    })
    return result
}

if (mode == 'publication') {
    var publication = blank(id) ? null : publicationOf(requestQuery().eq('tid', id)
        .in('request_status', ['PUBLISHED', 'OFFLINE']).selectOne())
    if (publication == null) {
        exit 404, '发布版本不存在或当前部门不可见'
    }
    return publication
}
if (mode == 'subscription') {
    var subscription = readSubscription(id)
    if (subscription == null) {
        exit 404, '订阅不存在或无权查看'
    }
    return subscription
}
if (mode == 'delivery') {
    var task = blank(id) ? null : taskQuery().eq('tid', id).selectOne()
    // 交付可见性由关联订阅决定。单条交付保留下载内容。
    if (task == null || readSubscription(task.apply_form_id) == null) {
        exit 404, '交付批次不存在或无权查看'
    }
    return deliveryOf(task)
}

var catalogs = readCatalogEntries(scope)
var requestRows = readSnapshotPages(pageNo => requestQuery()
    .orderBy('revision_number').orderBy('tid')
    .page(snapshotWindow, (pageNo - 1) * snapshotWindow))
var publications = []
var requestById = {}
for (row in requestRows) {
    requestById[text(row.tid)] = row
    var publication = publicationOf(row)
    if (publication != null) {
        publications.add(publication)
    }
}

var formRows = readSnapshotPages(pageNo => formQuery().orderBy('tid')
    .page(snapshotWindow, (pageNo - 1) * snapshotWindow))
var subscriptions = []
var allowedSubs = []
for (row in formRows) {
    var sub = parse(row.resource_scope_json)
    var subscription = subscriptionOf(row, sub, requestById[text(sub.publicationId)])
    if (subscription != null) {
        subscriptions.add(subscription)
        allowedSubs.add(subscription.id)
    }
}

// 交付按已授权订阅分批查询。跨批合并时继续按创建时间倒序。
var deliveries = []
var start = 0
while (start < allowedSubs.size()) {
    var subscriptionIds = allowedSubs.subList(start, Math.min(start + snapshotWindow, allowedSubs.size()))
    var tasks = readSnapshotPages(pageNo => taskQuery().in('apply_form_id', subscriptionIds)
        .orderBy('created_time', 'desc').orderBy('tid')
        .page(snapshotWindow, (pageNo - 1) * snapshotWindow))
    for (task in tasks) {
        var delivery = deliveryOf(task)
        delivery.remove('content')
        deliveries.add(delivery)
    }
    start = start + snapshotWindow
}
if (allowedSubs.size() > snapshotWindow) {
    deliveries = deliveries.sort((left, right) => {
        var order = right.createdAt.compareTo(left.createdAt)
        return order == 0 ? left.id.compareTo(right.id) : order
    })
}
return {
    catalogs: catalogs,
    publications: publications,
    subscriptions: subscriptions,
    deliveries: deliveries,
    autoApprove: false,
    busConfigured: false,
    deliveryScheduling: false,
    actor: {id: actorId, orgId: orgId, name: user.name},
    capabilities: {scheduledDelivery: false, manualDelivery: true, crossNetworkSync: false, autoApprove: false}
}
