{
  "properties": {},
  "id": "52e398a9f2fc429da79048b9ea719b37",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "06.获取表列表",
  "createTime": 1782955344303,
  "updateTime": 1790483136251,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/tables",
  "method": "GET",
  "parameters": [
    {
      "name": "dbId",
      "value": "validation_kingbase_metadata",
      "description": "数据源ID",
      "required": true,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "paged",
      "value": "false",
      "description": "true时返回第二步已标注表分页结果，默认保持数组响应兼容旧调用",
      "required": false,
      "dataType": "Boolean",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "pageNo",
      "value": "1",
      "description": "分页页码",
      "required": false,
      "dataType": "Integer",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "pageSize",
      "value": "50",
      "description": "每页条数，最大100",
      "required": false,
      "dataType": "Integer",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "businessType",
      "value": "",
      "description": "可选业务类型筛选",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "keyword",
      "value": "",
      "description": "按数据表英文名或数据资源中文名筛选",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "annotatedOnly",
      "value": null,
      "description": "审查页仅返回已标注表及保存配置；与第二步已标注列表去重口径一致",
      "required": false,
      "dataType": "Boolean",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "includeGovernance",
      "value": "true",
      "description": "是否返回字段治理配置；第五步首屏传 false，展开单表时再读取完整配置",
      "required": false,
      "dataType": "Boolean",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "allSnapshots",
      "value": "false",
      "description": "paged=true 时包含未标注与已标注快照；默认保持第二步已标注列表口径",
      "required": false,
      "dataType": "Boolean",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "sortField",
      "value": "",
      "description": "快照分页白名单排序字段",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "sortDir",
      "value": "",
      "description": "快照分页白名单排序方向",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": "[]",
  "description": "读取当前租户数据源的已保存表快照（GET /dst/database/metadata/tables）。默认返回数组。paged=true 用于登记第二步和来源详情，catalogPaged=true 按有效目录关联状态分页并返回两类登记数量。仅查询平台登记快照，不连接物理来源库。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import java.util.Map
import jsons
import tableRead
import tenantRuntime

/**
 * GET /dst/database/metadata/tables 读取当前租户数据源的已保存表快照。
 * dbId 必填。默认返回数组，paged=true 返回去重后的分页、筛选总数和筛选前统计。
 * catalogPaged=true 按当前租户的有效目录关联分页，返回筛选前的待登记和已登记数量。
 * annotatedOnly=true 保留历史完整数组，按数据源和标注状态选取同名代表快照。
 * includeGovernance=false 省略治理大字段。此接口只读取登记快照，不连接物理来源库。
 */
var tenantId = tenantRuntime.id()
var textOf = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
// 第五步大数据源首屏只需要表级摘要。默认保留完整治理配置，传 false 时显式省略大字段。
var includeGovernanceFlag = !(textOf(includeGovernance).trim().toLowerCase() == 'false' || textOf(includeGovernance) == '0')
var rowValue = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) { return value }
    }
    return null
}
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) { return value }
    }
    return null
}

var tableItem = (row, datasource) => {
    var tid = rowValue(row, ['tid'])
    var datasourceId = firstValue(row, ['datasourceId', 'datasource_id'])
    var tableName = firstValue(row, ['tableName', 'table_name'])
    var item = {
        tid: tid,
        id: tid,
        assetType: 'table',
        datasourceId: datasourceId,
        dbId: datasourceId,
        tableName: tableName,
        tableNameEn: firstValue(row, ['tableNameEn', 'table_name_en', 'tableName', 'table_name']),
        // 右侧列表展示已持久化的登记名称，后续物理探查返回的注释不能覆盖它。
        registeredResourceName: firstValue(row, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn']),
        tableNameCn: firstValue(row, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']),
        tableComment: firstValue(row, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn']),
        tableType: firstValue(row, ['tableType', 'table_type']),
        businessType: firstValue(row, ['businessType', 'business_type']),
        businessTypeReason: firstValue(row, ['businessTypeReason', 'business_type_reason']),
        annotated: rowValue(row, ['annotated']),
        recordCount: rowValue(row, ['recordCount', 'record_count']),
        fieldCount: rowValue(row, ['fieldCount', 'field_count']),
        storageSize: firstValue(row, ['storageSize', 'storage_size']),
        totalSizeBytes: rowValue(row, ['totalSizeBytes', 'total_size_bytes']),
        totalSizeFormatted: firstValue(row, ['totalSizeFormatted', 'total_size_formatted', 'storageSize', 'storage_size']),
        orgId: firstValue(row, ['orgId', 'org_id', 'manageUnit', 'manage_unit']),
        orgPath: firstValue(row, ['orgPath', 'org_path']),
        appId: firstValue(row, ['appId', 'app_id']),
        relatedDirectory: firstValue(row, ['relatedDirectory', 'related_directory']),
        dataSourceType: firstValue(row, ['dataSourceType', 'data_source_type']),
        timestampField: firstValue(row, ['timestampField', 'timestamp_field']),
        dictionaryStructureType: firstValue(row, ['dictionaryStructureType', 'dictionary_structure_type']),
        dictionaryTableFlag: rowValue(row, ['dictionaryTableFlag', 'dictionary_table_flag']),
        sourceCatalogId: firstValue(row, ['sourceCatalogId', 'source_catalog_id']),
        sourceTableId: firstValue(row, ['sourceTableId', 'source_table_id', 'tid']),
        sourceTableName: firstValue(row, ['sourceTableName', 'source_table_name', 'tableName', 'table_name']),
        manageUnit: firstValue(row, ['manageUnit', 'manage_unit', 'orgId', 'org_id']),
        assetDesc: firstValue(row, ['assetDesc', 'asset_desc', 'tableComment', 'table_comment']),
        catalogName: firstValue(row, ['catalogName', 'catalog_name', 'tableNameCn', 'table_name_cn']),
        catalogNameEn: firstValue(row, ['catalogNameEn', 'catalog_name_en', 'tableName', 'table_name']),
        tenantId: firstValue(row, ['tenantId', 'tenant_id']),
        assetStatus: rowValue(row, ['assetStatus', 'asset_status']),
        flowStatus: rowValue(row, ['flowStatus', 'flow_status']),
        regTime: rowValue(row, ['regTime', 'reg_time']),
        createdTime: rowValue(row, ['createdTime', 'created_time']),
        updatedTime: rowValue(row, ['updatedTime', 'updated_time']),
        dbName: firstValue(datasource, ['dbName', 'db_name'])
    }
    if (blank(item.appId)) { item.appId = firstValue(datasource, ['appId', 'app_id']) }
    if (blank(item.orgId)) { item.orgId = firstValue(datasource, ['orgId', 'org_id']) }
    item.regTime_date = item.regTime
    item.createdTime_date = item.createdTime
    item.updatedTime_date = item.updatedTime
    if (includeGovernanceFlag) {
        item.fieldGovernanceConfig = rowValue(row, ['fieldGovernanceConfig', 'field_governance_config'])
        item.dictionaryProfiles = rowValue(row, ['dictionaryProfiles', 'dictionary_profiles'])
        item.dictionaryProfile = rowValue(row, ['dictionaryProfile', 'dictionary_profile'])
        item.dictionaryCategories = rowValue(row, ['dictionaryCategories', 'dictionary_categories'])
    }
    return item
}

var snapshotProjection = (alias, legacyArray) => {
    var columnKinds = {
        tid: 'TEXT', datasource_id: 'TEXT', table_name: 'TEXT', table_name_en: 'TEXT', table_name_cn: 'TEXT',
        table_comment: 'LOB', table_type: 'TEXT', business_type: 'TEXT', business_type_reason: 'TEXT',
        annotated: 'NUMBER', record_count: 'NUMBER', field_count: 'NUMBER', storage_size: 'TEXT',
        total_size_bytes: 'NUMBER', total_size_formatted: 'TEXT', org_id: 'TEXT', org_path: 'LOB',
        related_directory: 'TEXT', data_source_type: 'TEXT', timestamp_field: 'TEXT',
        dictionary_structure_type: 'TEXT', dictionary_table_flag: 'NUMBER', source_catalog_id: 'TEXT',
        source_table_id: 'TEXT', source_table_name: 'TEXT', manage_unit: 'TEXT', asset_status: 'NUMBER',
        flow_status: 'NUMBER', reg_time: 'TIME', created_time: 'TIME', updated_time: 'TIME', tenant_id: 'TEXT'
    }
    // 历史数组优先返回快照上的描述和目录名，再由 tableItem 应用兼容回退。
    if (legacyArray) {
        columnKinds.asset_desc = 'LOB'
        columnKinds.catalog_name = 'TEXT'
        columnKinds.catalog_name_en = 'TEXT'
    }
    if (includeGovernanceFlag) {
        columnKinds.field_governance_config = 'LOB'
        columnKinds.dictionary_profiles = 'LOB'
        columnKinds.dictionary_profile = 'LOB'
        columnKinds.dictionary_categories = 'LOB'
    }
    var projection = {}
    for (name in columnKinds.keySet()) {
        projection[name] = tableRead.column(alias, name, columnKinds[name])
    }
    return projection
}

if (blank(dbId)) {
    exit 400, 'dbId 不能为空'
}

var datasource = db.table('db_datasource_t')
    .columns('tid', 'db_name', 'app_id', 'org_id', 'tenant_id')
    .where().eq('tid', dbId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
// 登记步骤读取数据源快照，不再叠加元数据管理菜单的角色范围校验。
// tenantRuntime 与 SQL 租户条件仍限定本次读取的数据归属。
if (datasource == null) {
    exit 404, '数据源不存在或已删除'
}

// 登记目录抽屉复用第二步的已保存快照，按有效目录关联状态在库内分页。
if (textOf(catalogPaged) == '1' || textOf(catalogPaged).toLowerCase() == 'true') {
    var safePageNo = pageNo == null || pageNo::int < 1 ? 1 : pageNo::int
    var safePageSize = pageSize == null || pageSize::int < 1 ? 20 : Math.min(pageSize::int, 100)
    var registeredMode = textOf(catalogStatus) == 'registered'
    var selectedBusinessType = blank(businessType) || textOf(businessType) == '全部' ? null : textOf(businessType)
    var snapshotAlias = 'catalog_snapshot'
    var catalogAlias = 'linked_catalog'
    var rankedAlias = 'ranked_catalog'
    var snapshotColumn = (name, kind) => tableRead.column(snapshotAlias, name, kind)
    var rankedColumn = (name, kind) => tableRead.column(rankedAlias, name, kind)
    var catalogId = tableRead.column(catalogAlias, 'tid', 'TEXT')
    var linked = tableRead.not(tableRead.isNull(catalogId))
    var tableName = snapshotColumn('table_name', 'TEXT')
    var annotated = snapshotColumn('annotated', 'NUMBER')
    var isAnnotated = tableRead.equal(annotated, 1)
    var nameKey = tableRead.lower(tableRead.trim(tableName))

    // 目录关联必须在当前租户且未删除，失效引用按待登记处理。
    var snapshots = tableRead.from('db_table_t', snapshotAlias, tenantId)
    snapshots.leftJoin('da_catalog_t', catalogAlias, 'tid', 'source_catalog_id', [
        tableRead.equal(tableRead.column(catalogAlias, 'is_del', 'NUMBER'), 0)
    ])
    snapshots.where(tableRead.all([
        tableRead.equal(snapshotColumn('datasource_id', 'TEXT'), dbId),
        tableRead.equal(snapshotColumn('is_del', 'NUMBER'), 0)
    ]))

    // 与第二步一致：采集未匹配的未标注行不回到待登记；已标注和目录关联行保留。
    var missingNames = []
    if (textOf(checkCollectionMissing) == '1') {
        var job = db.table('metadata_table_collection_job_t').columns('deleted_detail')
            .where().eq('tenant_id', tenantId).eq('datasource_id', dbId)
            .eq('status', 'SUCCEEDED').eq('is_del', 0)
            .orderBy('created_time', 'desc').orderBy('tid', 'desc').selectOne()
        var detail = rowValue(job, ['deletedDetail', 'deleted_detail'])
        if (!blank(detail)) {
            for (missing in jsons.toJSONArray(textOf(detail))) {
                var name = textOf(rowValue(missing, ['tableName', 'table_name'])).trim().toLowerCase()
                if (!blank(name)) { missingNames.add(name) }
            }
        }
    }
    if (missingNames.size() > 0) {
        snapshots.where(tableRead.any([isAnnotated, linked, tableRead.not(tableRead.in(nameKey, missingNames))]))
    }

    // 先确定同名快照的代表行，再施加界面筛选。沿用原库的空值排序，避免改变历史代表行。
    var rankOrder = [
        tableRead.when(linked, 1, 0),
        tableRead.coalesce([annotated, 0]),
        snapshotColumn('updated_time', 'TIME'),
        tableRead.coalesce([snapshotColumn('field_count', 'NUMBER'), 0]),
        snapshotColumn('tid', 'TEXT')
    ]
    var rankSnapshots = projection => snapshots.ranked(rankedAlias, projection, [nameKey], rankOrder,
        ['desc', 'desc', 'desc', 'desc', 'desc'], true)
    var rankedCatalogId = rankedColumn('source_catalog_id', 'TEXT')
    var unlinked = tableRead.isNull(rankedCatalogId)
    var counts = rankSnapshots({source_catalog_id: catalogId}).grouped({
        pending_total: tableRead.countIf(unlinked),
        registered_total: tableRead.countIf(tableRead.not(unlinked))
    }, [])

    // 自动规则从低到高构造。人工标注、人工调整及显式保留的类型优先于推断。
    var storedType = snapshotColumn('business_type', 'TEXT')
    var nonBlankType = tableRead.blankAsNull(storedType)
    var reason = snapshotColumn('business_type_reason', 'TEXT')
    var nameTokens = tableRead.regexReplace(tableRead.lower(tableName), '[^a-z0-9]+', '_')
    var description = tableRead.coalesce([
        snapshotColumn('table_comment', 'LOB'), snapshotColumn('table_name_cn', 'TEXT'), ''
    ])
    // 中文关键词无需大小写转换，保留长注释全文供数据库匹配。
    var classificationText = tableRead.concat(tableRead.concat(tableName, ' '), description)
    var rules = [
        ['过程表', '(^|_)(flow|workflow|process|proc|task|approval|approve|node|todo|bpm)(_|$)',
            '流程|过程|审批|工单|任务|节点|待办'],
        ['字典表', '(^|_)(dict|dictionary|code|enum|lookup|type|param|params|config|constant)(_|$)',
            '字典|码表|代码表|枚举|参数|配置|常量|类型'],
        ['日志表', '(^|_)(log|logs|audit|trace|access|operate|operation|login)(_|$)',
            '日志|审计|轨迹|访问记录|操作记录|登录记录'],
        ['备份表', '(^|_)(bak|backup|bk|old|copy|his|history|archive|arch|dump)(_|$)|(^|_)(19|20)[0-9]{6,12}($|_)',
            '备份|历史|归档|副本']
    ]
    var displayType = tableRead.coalesce([nonBlankType, '业务表'])
    for (rule in rules) {
        var matches = tableRead.any([
            tableRead.regexLike(nameTokens, rule[1]), tableRead.regexLike(classificationText, rule[2])
        ])
        displayType = tableRead.when(matches, rule[0], displayType)
    }
    var explicitType = tableRead.any([
        tableRead.in(storedType, ['不确定', '暂不处理']),
        tableRead.all([
            tableRead.any([isAnnotated, tableRead.like(reason, '%人工调整%')]),
            tableRead.not(tableRead.isNull(nonBlankType))
        ])
    ])

    // 仅返回抽屉所需的快照与目录字段，治理大字段仍不在该分页分支投影。
    var columnKinds = {
        tid: 'TEXT', datasource_id: 'TEXT', table_name: 'TEXT', table_name_en: 'TEXT', table_name_cn: 'TEXT',
        table_comment: 'LOB', table_type: 'TEXT', business_type: 'TEXT', business_type_reason: 'TEXT',
        annotated: 'NUMBER', field_count: 'NUMBER', org_id: 'TEXT', org_path: 'LOB', tenant_id: 'TEXT',
        asset_status: 'NUMBER', flow_status: 'NUMBER', created_time: 'TIME', updated_time: 'TIME',
        source_catalog_id: 'TEXT', catalog_name: 'TEXT', catalog_name_en: 'TEXT'
    }
    var snapshotFields = {}
    var pageFields = {}
    for (name in columnKinds.keySet()) {
        snapshotFields[name] = snapshotColumn(name, columnKinds[name])
        pageFields[name] = rankedColumn(name, columnKinds[name])
    }
    snapshotFields.business_type = tableRead.when(explicitType, storedType, displayType)
    snapshotFields.source_catalog_id = catalogId
    snapshotFields.catalog_name = tableRead.column(catalogAlias, 'catalog_name', 'TEXT')
    snapshotFields.catalog_name_en = tableRead.column(catalogAlias, 'catalog_name_en', 'TEXT')
    var ranked = rankSnapshots(snapshotFields)
    ranked.where(registeredMode ? tableRead.not(unlinked) : unlinked)
    if (selectedBusinessType != null) {
        ranked.where(tableRead.equal(pageFields.business_type, selectedBusinessType))
    }
    var filterKeywords = (keywords, column) => {
        var matches = []
        var searchColumn = tableRead.lower(column)
        for (word in textOf(keywords).split('\n')) {
            if (!blank(word)) {
                matches.add(tableRead.like(searchColumn, '%' + textOf(word).trim().toLowerCase() + '%'))
            }
        }
        if (matches.size() > 0) {
            ranked.where(tableRead.any(matches))
        }
    }
    filterKeywords(tableNames, pageFields.table_name)
    filterKeywords(catalogNames, tableRead.coalesce([pageFields.catalog_name, '']))
    var page = ranked.page(pageFields, pageFields.table_name, 'asc', 'tid', 'asc', safePageNo, safePageSize, true)
    var rows = []
    for (row in page.rows) {
        var item = tableItem(row, datasource)
        item.catalogId = item.sourceCatalogId
        item.catalogName = rowValue(row, ['catalogName', 'catalog_name'])
        item.catalogNameEn = rowValue(row, ['catalogNameEn', 'catalog_name_en'])
        rows.add(item)
    }
    return {
        pageNo: safePageNo,
        pageSize: safePageSize,
        total: page.total,
        pendingTotal: rowValue(counts[0], ['pending_total']),
        registeredTotal: rowValue(counts[0], ['registered_total']),
        rows: rows,
        list: rows
    }
}

/*
 * 第二步右侧列表使用数据库真分页。先按当前租户和表名去重，避免历史重复资产
 * 同时占用分页位置并造成总数与展示不一致；默认数组模式保持原接口兼容性。
 * pending=true 仅提供“暂不处理”的已保存快照给左侧物理表合并，绝不作为已标注表显示。
 */
var pendingMode = textOf(pending).trim().toLowerCase() == 'true' || textOf(pending) == '1'
if (pendingMode) {
    // 只返回未标注持久化快照，供第二步左侧合并使用；前端仅对业务类型为
    // “暂不处理 / 不确定”的记录采用其分类，其他未标注记录仍按物理元数据推断。
    // 直接返回 NamedTable 行，避免额外对象转换导致字段名在不同驱动下丢失。
    return db.table('db_table_t').where()
        .eq('tenant_id', tenantId)
        .eq('datasource_id', dbId)
        .eq('is_del', 0)
        .eq('annotated', 0)
        .orderBy('updated_time', 'desc')
        .select()
}
var pagedMode = textOf(paged).trim().toLowerCase() == 'true' || textOf(paged) == '1'
if (pagedMode) {
    // 详情页展示与登记两侧共用的全部快照；默认仍为第二步已标注列表。
    var allSnapshotsFlag = textOf(allSnapshots).toLowerCase() == 'true' || textOf(allSnapshots) == '1'
    var safePageNo = pageNo == null || pageNo::int < 1 ? 1 : pageNo::int
    var safePageSize = pageSize == null || pageSize::int < 1 ? 50 : Math.min(pageSize::int, 100)
    var selectedBusinessType = blank(businessType) || textOf(businessType) == '全部' ? null : textOf(businessType)
    var searchKeyword = blank(keyword) ? null : '%' + textOf(keyword).trim().toLowerCase() + '%'
    var snapshotAlias = 'snapshot'
    var rankedAlias = 'ranked_snapshot'
    var snapshotColumn = (name, kind) => tableRead.column(snapshotAlias, name, kind)
    var annotatedColumn = snapshotColumn('annotated', 'NUMBER')
    var businessColumn = snapshotColumn('business_type', 'TEXT')
    var snapshots = tableRead.from('db_table_t', snapshotAlias, tenantId)
    snapshots.where(tableRead.all([
        tableRead.equal(snapshotColumn('datasource_id', 'TEXT'), dbId),
        tableRead.equal(snapshotColumn('is_del', 'NUMBER'), 0)
    ]))
    if (!allSnapshotsFlag) {
        snapshots.where(tableRead.equal(annotatedColumn, 1))
    }

    // 按原表名的小写值分组，依次比较标注状态、字段数、更新时间和表 ID。
    var nameKey = tableRead.lower(snapshotColumn('table_name', 'TEXT'))
    var rankOrder = [
        tableRead.coalesce([annotatedColumn, 0]),
        tableRead.coalesce([snapshotColumn('field_count', 'NUMBER'), 0]),
        snapshotColumn('updated_time', 'TIME'),
        snapshotColumn('tid', 'TEXT')
    ]
    var rankSnapshots = projection => snapshots.ranked(rankedAlias, projection, [nameKey], rankOrder,
        ['desc', 'desc', 'desc', 'desc'])

    // 统计使用相同的代表行规则，只投影统计字段，且不受分页关键词和类型筛选影响。
    var stats = rankSnapshots({
        business_type: tableRead.coalesce([businessColumn, '业务表']),
        annotated: annotatedColumn
    })
    var statsBusinessType = tableRead.column(rankedAlias, 'business_type', 'TEXT')
    var statsAnnotated = tableRead.column(rankedAlias, 'annotated', 'NUMBER')
    var statsRows = stats.grouped({
        business_type: statsBusinessType,
        annotated: statsAnnotated,
        total: tableRead.count()
    }, [statsBusinessType, statsAnnotated])

    // 摘要请求只投影分页所需字段，按 includeGovernance 决定是否读取治理配置。
    var ranked = rankSnapshots(snapshotProjection(snapshotAlias, false))
    var projection = snapshotProjection(rankedAlias, false)

    // 先选代表行，再过滤类型和关键词，避免匹配到已被去重的旧快照。
    if (selectedBusinessType != null) {
        ranked.where(tableRead.equal(projection.business_type, selectedBusinessType))
    }
    if (searchKeyword != null) {
        var matchesKeyword = column => tableRead.like(tableRead.lower(tableRead.coalesce([column, ''])), searchKeyword)
        ranked.where(tableRead.any([
            matchesKeyword(projection.table_name),
            matchesKeyword(projection.table_name_cn),
            matchesKeyword(projection.table_comment)
        ]))
    }
    var sortColumns = {
        tableName: projection.table_name,
        tableComment: projection.table_comment,
        fieldCount: projection.field_count,
        annotated: projection.annotated,
        businessType: projection.business_type
    }
    var selectedSort = sortColumns[textOf(sortField)]
    var sortOrder = selectedSort == null ? projection.updated_time : selectedSort
    var direction = selectedSort == null || textOf(sortDir).toLowerCase() == 'desc' ? 'desc' : 'asc'
    var page = ranked.page(projection, sortOrder, direction, 'tid', 'desc', safePageNo, safePageSize)
    var rows = page.rows.map(row => tableItem(row, datasource))

    var businessStats = {}
    var annotatedTotal = 0
    var snapshotTotal = 0
    for (stat in statsRows) {
        var type = firstValue(stat, ['businessType', 'business_type'])
        var count = rowValue(stat, ['total']) == null ? 0 : rowValue(stat, ['total'])::long
        var key = blank(type) ? '业务表' : type
        businessStats[key] = (businessStats[key] == null ? 0 : businessStats[key]) + count
        snapshotTotal = snapshotTotal + count
        if (textOf(rowValue(stat, ['annotated'])) == '1' || textOf(rowValue(stat, ['annotated'])) == 'true') {
            annotatedTotal = annotatedTotal + count
        }
    }
    return {
        pageNo: safePageNo,
        pageSize: safePageSize,
        total: page.total,
        annotatedTotal: annotatedTotal,
        snapshotTotal: snapshotTotal,
        businessStats: businessStats,
        rows: rows,
        list: rows
    }
}

// 数据源范围和标注条件在数据库内处理，避免读取其他来源或未标注的快照。
var tableRows = []
var annotatedOnlyMode = textOf(annotatedOnly).trim().toLowerCase() == 'true' || textOf(annotatedOnly) == '1'
if (annotatedOnlyMode) {
    var snapshotAlias = 'annotated_snapshot'
    var rankedAlias = 'ranked_annotated'
    var snapshot = snapshotProjection(snapshotAlias, true)
    var snapshots = tableRead.from('db_table_t', snapshotAlias, tenantId)
    snapshots.where(tableRead.all([
        tableRead.equal(snapshot.datasource_id, dbId),
        tableRead.equal(tableRead.column(snapshotAlias, 'is_del', 'NUMBER'), 0),
        tableRead.equal(snapshot.annotated, 1)
    ]))

    // 首次去重按数据库的表名小写和排序规则，空时间沿用当前方言的原生顺序。
    var ranked = snapshots.ranked(rankedAlias, snapshot, [tableRead.lower(snapshot.table_name)], [
        tableRead.coalesce([snapshot.field_count, 0]), snapshot.updated_time, snapshot.tid
    ], ['desc', 'desc', 'desc'], true)
    var projection = snapshotProjection(rankedAlias, true)
    // 旧客户端取得同一语句快照的完整数组，按更新时间和 ID 保持原返回顺序。
    tableRows = ranked.rankedRows(projection, [projection.updated_time, projection.tid], ['desc', 'desc'], true)
} else {
    tableRows = db.table('db_table_t').where().eq('tenant_id', tenantId)
        .eq('datasource_id', dbId).eq('is_del', 0)
        .orderBy('updated_time', 'desc').select()
}
// 再按去首尾空白、小写后的表名去重，更新时间缺失时沿用创建时间。
var latestByTableName = {}
var updatedValue = (row) => {
    var value = firstValue(row, ['updatedTime', 'updated_time', 'createdTime', 'created_time'])
    return blank(value) ? '' : textOf(value)
}
for (row in tableRows == null ? [] : tableRows) {
    var tableNameKey = textOf(firstValue(row, ['tableName', 'table_name'])).trim().toLowerCase()
    if (tableNameKey == '') { continue }
    var current = latestByTableName[tableNameKey]
    if (current == null || updatedValue(row) > updatedValue(current)) {
        latestByTableName[tableNameKey] = row
    }
}
var rows = []
for (row in tableRows == null ? [] : tableRows) {
    var tableNameKey = textOf(firstValue(row, ['tableName', 'table_name'])).trim().toLowerCase()
    if (tableNameKey != '' && latestByTableName[tableNameKey] == row) {
        rows.add(tableItem(row, datasource))
    }
}
return rows
