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
      "value": "codex_kingbase_firmament_data_v6",
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
    }
  ],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": "[]",
  "description": "获取当前数据源已保存的数据表快照（GET /dst/database/metadata/tables）。默认返回数组兼容步骤三至五；paged=true 时按租户、数据源、业务类型和名称关键词在数据库侧去重分页，供登记第二步右侧列表使用；不连接物理数据源；涉及 db_datasource_t、metadata_table_collection_job_t、db_table_t、da_catalog_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.获取表列表
// 路由：GET /dst/database/metadata/tables
// 业务说明：获取当前数据源已保存的数据表快照（GET /dst/database/metadata/tables）。默认返回数组兼容步骤三至五；paged=true 时按租户、数据源、业务类型和名称关键词在数据库侧去重分页，供登记第二步右侧列表使用；不连接物理数据源；涉及 db_datasource_t、metadata_table_collection_job_t、db_table_t、da_catalog_t 等业务表。
import java.util.Map
import jsons
import tenantRuntime
var tenantId = tenantRuntime.id()

/*
 * 元数据管理 06：读取当前数据源的表资产快照。
 * 标注状态的事实来源是 db_table_t；物理表实时清单由 /tables/preview 提供。
 */
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
        // This endpoint is the persisted registration snapshot used by the
        // right pane, so its resource name must not be replaced by a physical
        // database comment returned from a later exploration.
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

if (blank(dbId)) {
    exit 400, 'dbId 不能为空'
}

var datasource = db.table('db_datasource_t')
    .columns('tid', 'db_name', 'app_id', 'org_id', 'tenant_id')
    .where().eq('tid', dbId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()

/* 登记目录抽屉：复用同一 db_table_t 采集快照，按目录关联状态真分页。
 * 只读、不连接源库、不写资产；不传 catalogPaged 时保持第二步原有接口行为。
 */
if (textOf(catalogPaged) == '1' || textOf(catalogPaged).toLowerCase() == 'true') {
    if (datasource == null) { exit 404, '数据源不存在或无访问权限' }
    var safePageNo = pageNo == null || pageNo::int < 1 ? 1 : pageNo::int
    var safePageSize = pageSize == null || pageSize::int < 1 ? 20 : Math.min(pageSize::int, 100)
    var registeredMode = textOf(catalogStatus) == 'registered'
    var selectedBusinessType = blank(businessType) || textOf(businessType) == '全部' ? null : textOf(businessType)
    var tableKeywords = []
    for (word in textOf(tableNames).split('\n')) {
        if (!blank(word)) { tableKeywords.add('%' + textOf(word).trim().toLowerCase() + '%') }
    }
    var catalogKeywords = []
    for (word in textOf(catalogNames).split('\n')) {
        if (!blank(word)) { catalogKeywords.add('%' + textOf(word).trim().toLowerCase() + '%') }
    }
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
    var catalogBaseSql = """
        select * from (
            select t.tid, t.datasource_id, t.table_name, t.table_name_en, t.table_name_cn,
                   t.table_comment, t.table_type,
                   case
                     when (t.annotated=1 or t.business_type_reason like '%人工调整%')
                          and t.business_type is not null and t.business_type<>'' then t.business_type
                     when t.business_type in ('不确定', '暂不处理') then t.business_type
                     when regexp_like(regexp_replace(lower(t.table_name), '[^a-z0-9]+', '_'), '(^|_)(bak|backup|bk|old|copy|his|history|archive|arch|dump)(_|$)')
                       or regexp_like(regexp_replace(lower(t.table_name), '[^a-z0-9]+', '_'), '(^|_)(19|20)[0-9]{6,12}($|_)')
                       or regexp_like(concat(concat(lower(t.table_name), ' '), lower(coalesce(t.table_comment, t.table_name_cn, ''))), '备份|历史|归档|副本') then '备份表'
                     when regexp_like(regexp_replace(lower(t.table_name), '[^a-z0-9]+', '_'), '(^|_)(log|logs|audit|trace|access|operate|operation|login)(_|$)')
                       or regexp_like(concat(concat(lower(t.table_name), ' '), lower(coalesce(t.table_comment, t.table_name_cn, ''))), '日志|审计|轨迹|访问记录|操作记录|登录记录') then '日志表'
                     when regexp_like(regexp_replace(lower(t.table_name), '[^a-z0-9]+', '_'), '(^|_)(dict|dictionary|code|enum|lookup|type|param|params|config|constant)(_|$)')
                       or regexp_like(concat(concat(lower(t.table_name), ' '), lower(coalesce(t.table_comment, t.table_name_cn, ''))), '字典|码表|代码表|枚举|参数|配置|常量|类型') then '字典表'
                     when regexp_like(regexp_replace(lower(t.table_name), '[^a-z0-9]+', '_'), '(^|_)(flow|workflow|process|proc|task|approval|approve|node|todo|bpm)(_|$)')
                       or regexp_like(concat(concat(lower(t.table_name), ' '), lower(coalesce(t.table_comment, t.table_name_cn, ''))), '流程|过程|审批|工单|任务|节点|待办') then '过程表'
                     else coalesce(nullif(t.business_type, ''), '业务表')
                   end as business_type, t.business_type_reason,
                   t.annotated, t.field_count, t.org_id, t.org_path, t.tenant_id,
                   t.asset_status, t.flow_status, t.created_time, t.updated_time,
                   c.tid as source_catalog_id, c.catalog_name, c.catalog_name_en,
                   row_number() over (
                       partition by lower(trim(t.table_name))
                       order by case when c.tid is not null then 1 else 0 end desc, coalesce(t.annotated, 0) desc,
                                t.updated_time desc, coalesce(t.field_count, 0) desc, t.tid desc
                   ) as row_rank
              from db_table_t t
              left join da_catalog_t c on c.tid=t.source_catalog_id
                   and c.tenant_id=#{tenantId} and c.is_del=0
             where t.tenant_id=#{tenantId} and t.datasource_id=#{dbId} and t.is_del=0
               <if test="missingNames.size() > 0">
                 and (t.annotated=1 or c.tid is not null or lower(trim(t.table_name)) not in
                   <foreach collection="missingNames" item="missingName" open="(" close=")" separator=",">#{missingName}</foreach>)
               </if>
        ) ranked where ranked.row_rank=1
    """
    var counts = db.select('select case when ranked.source_catalog_id is not null then 1 else 0 end as registered, count(1) as total from (' + catalogBaseSql + ') ranked group by case when ranked.source_catalog_id is not null then 1 else 0 end')
    var pendingTotal = 0
    var registeredTotal = 0
    for (count in counts) {
        if (textOf(rowValue(count, ['registered'])) == '1' || textOf(rowValue(count, ['registered'])) == 'true') {
            registeredTotal = rowValue(count, ['total'])::long
        } else { pendingTotal = rowValue(count, ['total'])::long }
    }
    var catalogFilterSql = """
        <if test="registeredMode">and ranked.source_catalog_id is not null</if>
        <if test="!registeredMode">and ranked.source_catalog_id is null</if>
        <if test="selectedBusinessType != null and selectedBusinessType != ''">
          and ranked.business_type=#{selectedBusinessType}
        </if>
        <if test="tableKeywords.size() > 0">
          and <foreach collection="tableKeywords" item="word" open="(" close=")" separator=" or ">lower(ranked.table_name) like #{word}</foreach>
        </if>
        <if test="catalogKeywords.size() > 0">
          and <foreach collection="catalogKeywords" item="word" open="(" close=")" separator=" or ">lower(coalesce(ranked.catalog_name, '')) like #{word}</foreach>
        </if>
    """
    var page = db.page(catalogBaseSql + catalogFilterSql + ' order by ranked.table_name, ranked.tid', safePageSize, (safePageNo - 1) * safePageSize)
    var rows = []
    for (row in page.list == null ? [] : page.list) {
        var item = tableItem(row, datasource)
        item.catalogId = item.sourceCatalogId
        item.catalogName = rowValue(row, ['catalogName', 'catalog_name'])
        item.catalogNameEn = rowValue(row, ['catalogNameEn', 'catalog_name_en'])
        rows.add(item)
    }
    return {pageNo: safePageNo, pageSize: safePageSize, total: page.total,
        pendingTotal: pendingTotal, registeredTotal: registeredTotal, rows: rows, list: rows}
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
        .eq('datasource_id', dbId)
        .eq('is_del', 0)
        .eq('annotated', 0)
        .orderBy('updated_time', 'desc')
        .select()
}var pagedMode = textOf(paged).trim().toLowerCase() == 'true' || textOf(paged) == '1'
if (pagedMode) {
    var safePageNo = pageNo == null || pageNo::int < 1 ? 1 : pageNo::int
    var safePageSize = pageSize == null || pageSize::int < 1 ? 50 : Math.min(pageSize::int, 100)
    var selectedBusinessType = blank(businessType) || textOf(businessType) == '全部' ? null : textOf(businessType)
    var searchKeyword = blank(keyword) ? null : '%' + textOf(keyword).trim().toLowerCase() + '%'
    var dedupSql = """
        select * from (
            select t.tid, t.datasource_id, t.table_name, t.table_name_en, t.table_name_cn,
                   t.table_comment, t.table_type, t.business_type, t.business_type_reason,
                   t.annotated, t.record_count, t.field_count, t.storage_size,
                   t.total_size_bytes, t.total_size_formatted, t.org_id, t.org_path,
                   t.related_directory, t.data_source_type, t.timestamp_field,
                   t.dictionary_structure_type, t.dictionary_table_flag, t.source_catalog_id,
                   t.field_governance_config, t.dictionary_profiles,
                   t.dictionary_profile, t.dictionary_categories,
                   t.source_table_id, t.source_table_name, t.manage_unit, t.asset_status,
                   t.flow_status, t.reg_time, t.created_time, t.updated_time, t.tenant_id,
                   row_number() over (
                       partition by lower(t.table_name)
                       order by coalesce(t.field_count, 0) desc, t.updated_time desc, t.tid desc
                   ) as row_rank
              from db_table_t t
             where t.tenant_id = #{tenantId}
               and t.datasource_id = #{dbId}
               and t.is_del = 0
               and t.annotated = 1
        ) ranked
        where ranked.row_rank = 1
          <if test="selectedBusinessType != null and selectedBusinessType != ''">
            and ranked.business_type = #{selectedBusinessType}
          </if>
          <if test="searchKeyword != null and searchKeyword != ''">
            and (lower(coalesce(ranked.table_name, '')) like #{searchKeyword}
                 or lower(coalesce(ranked.table_name_cn, '')) like #{searchKeyword}
                 or lower(coalesce(ranked.table_comment, '')) like #{searchKeyword})
          </if>
    """
    var page = db.page(dedupSql + ' order by ranked.updated_time desc, ranked.tid desc', safePageSize, (safePageNo - 1) * safePageSize)
    var rows = []
    for (row in page.list == null ? [] : page.list) {
        rows.add(tableItem(row, datasource))
    }

    var statsRows = db.select("""
        select coalesce(grouped.business_type, '业务表') as business_type, count(1) as total
          from (
            select ranked.business_type
              from (
                select t.table_name, t.business_type,
                       row_number() over (
                           partition by lower(t.table_name)
                           order by coalesce(t.field_count, 0) desc, t.updated_time desc, t.tid desc
                       ) as row_rank
                  from db_table_t t
                 where t.tenant_id = #{tenantId}
                   and t.datasource_id = #{dbId}
                   and t.is_del = 0
                   and t.annotated = 1
              ) ranked
             where ranked.row_rank = 1
          ) grouped
         group by coalesce(grouped.business_type, '业务表')
    """)
    var businessStats = {}
    var annotatedTotal = 0
    for (stat in statsRows == null ? [] : statsRows) {
        var type = firstValue(stat, ['businessType', 'business_type'])
        var count = rowValue(stat, ['total']) == null ? 0 : rowValue(stat, ['total'])::long
        businessStats[blank(type) ? '业务表' : type] = count
        annotatedTotal = annotatedTotal + count
    }
    return {
        pageNo: safePageNo,
        pageSize: safePageSize,
        total: page.total == null ? 0 : page.total,
        annotatedTotal: annotatedTotal,
        businessStats: businessStats,
        rows: rows,
        list: rows
    }
}

// datasource_id 必须下推到数据库查询，禁止读取整个租户的表后在 Magic 内存过滤。
var tableRows = []
// 审查页只传输已标注表及其持久化治理配置，不传输上万张未标注表，
// 也不探查源端字段。排序/去重与第二步右侧已标注列表完全一致。
if (textOf(annotatedOnly).trim().toLowerCase() == 'true' || textOf(annotatedOnly) == '1') {
    tableRows = db.select("""
        select * from (
            select t.*, row_number() over (
                partition by lower(t.table_name)
                order by coalesce(t.field_count, 0) desc, t.updated_time desc, t.tid desc
            ) as row_rank
            from db_table_t t
            where t.tenant_id = #{tenantId} and t.datasource_id = #{dbId}
              and t.is_del = 0 and t.annotated = 1
        ) ranked where ranked.row_rank = 1
        order by ranked.updated_time desc, ranked.tid desc
    """)
} else {
    tableRows = db.table('db_table_t').where().eq('tenant_id', tenantId)
        .eq('datasource_id', dbId).eq('is_del', 0)
        .orderBy('updated_time', 'desc').select()
}
// One physical table can have historical registration snapshots.  Keep the latest
// persisted snapshot so an older dictionary classification cannot mask a newer process-table edit.
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
