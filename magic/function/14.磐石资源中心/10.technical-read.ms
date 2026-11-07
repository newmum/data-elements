{
  "properties" : { },
  "id" : "490931832d8a5b87a46bdb77544bb02c",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "10.technical-read",
  "createTime" : null,
  "updateTime" : 1790982798928,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/technical-read",
  "description" : null,
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "input",
    "value" : null,
    "description" : null,
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
import tableRead
import cn.hutool.json.JSONUtil
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.NumberUtil
import cn.hutool.crypto.digest.DigestUtil
import '@post:/sym/user/me' as currentUser

/**
 * 磐石技术元数据读取。/resource-center/technical-read
 * 接收 pageNo、pageSize、entityKeyword，或最多 1000 个 entityIds。分页默认 200 张并补齐已登记的仓库资源。
 * 只读当前租户，返回数据源、技术表和字段、标准映射、质量观测及当前会话身份。
 * 响应不包含连接凭据、连接串或质量问题的原始数据快照。
 */

var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId == '') {
    exit 401, '无法识别当前租户'
}
var text = value => value == null ? '' : value + ''
var number = value => text(value).matches('^[0-9]+$') ? NumberUtil.parseInt(text(value)) : 0
var bool = value => ['1', 'true', 'TRUE', 'YES', 'Y', 'yes', 'y'].contains(text(value))
var nullable = value => value == null || text(value) == '' ? 'unknown' : bool(value) ? 'true' : ['0','false','FALSE','NO','N','no','n'].contains(text(value)) ? 'false' : 'unknown'
var family = value => {
    var type = text(value).toLowerCase()
    if (type.contains('timestamp') || type.contains('datetime')) { return 'timestamp' }
    if (type.startsWith('date')) { return 'date' }
    if (type.contains('int') || type == 'serial' || type == 'bigserial') { return 'integer' }
    if (type.contains('decimal') || type.contains('numeric') || type.contains('number') || type.contains('money')) { return 'decimal' }
    if (type.contains('float') || type.contains('double') || type == 'real') { return 'float' }
    if (type.contains('bool') || type == 'bit') { return 'boolean' }
    if (type.contains('char') || type.contains('text') || type.contains('clob') || type.contains('string')) { return 'string' }
    if (type.contains('binary') || type.contains('blob') || type == 'bytea') { return 'binary' }
    if (type.contains('uuid')) { return 'uuid' }
    if (type.contains('json')) { return 'object' }
    return 'unknown'
}
var active = (table, columns) => db.camel().table(table).columns(columns).where()
    .eq('tenant_id', tenantId).eq('is_del', 0)
var rowsOf = page => page.list == null ? [] : page.list
var readCollection = (where, windowSize) => {
    // 兼容集合仍完整返回。每次 SQL 仅读固定窗口，以首批总数限制读取次数。
    var rows = []
    var first = where.page(windowSize, 0)
    rows.addAll(rowsOf(first))
    var offset = windowSize
    while (offset < first.total) {
        var next = rowsOf(where.page(windowSize, offset))
        if (next.size() == 0) {
            break
        }
        rows.addAll(next)
        offset = offset + windowSize
    }
    return rows
}
var sourceRows = readCollection(active('db_datasource_t', [
    'tid', 'db_name', 'db_type', 'database_type', 'connection_status', 'is_enable',
    'org_id', 'app_id', 'revision', 'updated_time'
]).orderBy('db_name').orderBy('tid'), 1000)
var sources = []
var sourceById = {}
var sourceIds = []
var manifest = {}
for (row in sourceRows) {
    var sourceId = text(row.tid)
    var engine = (text(row.dbType) == '' ? text(row.databaseType) : text(row.dbType)).toUpperCase()
    if (engine == 'DAMENG') { engine = 'DM8' }
    if (engine == 'KINGBASE8' || engine == 'KINGBASE') { engine = 'KINGBASEES' }
    var source = {id:sourceId, name:text(row.dbName), engine:engine,
        connectionStatus:text(row.connectionStatus), enabled:bool(row.isEnable),
        status:bool(row.isEnable) ? 'READY' : 'DISABLED', orgId:text(row.orgId), applicationId:text(row.appId),
        version:row.revision == null ? 1 : row.revision, updatedAt:row.updatedTime}
    sources.add(source)
    sourceById[sourceId] = source
    sourceIds.add(sourceId)
    manifest[sourceId] = 'metadata:' + sourceId + ':' + DigestUtil.sha256Hex(text(row.updatedTime))
}
var entityIds = input == null || input.entityIds == null ? [] : input.entityIds
if (entityIds.size() > 1000) { exit 400, '一次最多加载 1000 张指定数据表' }
var keyword = input == null ? '' : text(input.entityKeyword).trim()
if (keyword.length() > 200) { exit 400, '检索关键词过长' }
var search = '%' + keyword + '%'
var pageNo = input == null ? 1 : number(input.pageNo)
if (pageNo < 1) { pageNo = 1 }
if (pageNo > 100000) {
    exit 400, '技术实体页码不能超过 100000'
}
var pageSize = input == null ? 200 : number(input.pageSize)
if (pageSize < 1) { pageSize = 200 }
if (pageSize > 500) { pageSize = 500 }
var offset = (pageNo - 1) * pageSize
var tableRows = []
var total = 0
var tableColumns = [
    'tid', 'datasource_id', 'table_name', 'table_name_cn', 'table_comment', 'table_type', 'field_count',
    'revision', 'created_time', 'updated_time', 'resource_domain_id', 'resource_owner', 'asset_desc',
    'resource_plan_id', 'resource_state', 'resource_model_id', 'resource_field_map', 'resource_revision_no'
]
if (entityIds.size() > 0) {
    tableRows = active('db_table_t', tableColumns).in('tid', entityIds).select()
    total = tableRows.size()
} else if (sourceIds.size() > 0) {
    // 先限制到同租户未删除的来源，再计数和分页。每个 IN 子句最多 500 个来源。
    var tablesFromActiveSources = () => active('db_table_t', tableColumns).and(sourcesWhere => {
        var start = 0
        while (start < sourceIds.size()) {
            if (start > 0) {
                sourcesWhere.or()
            }
            var end = Math.min(start + 500, sourceIds.size())
            sourcesWhere.in('datasource_id', sourceIds.subList(start, end))
            start = end
        }
        return sourcesWhere
    })
    var tableQuery = tablesFromActiveSources()
    if (keyword != '') {
        tableQuery.and(matching => matching.like('table_name', search)
            .or().like('table_name_cn', search).or().like('table_comment', search))
    }
    var page = tableQuery.orderBy('table_name').orderBy('tid').page(pageSize, offset)
    total = page.total == null ? 0 : page.total
    tableRows.addAll(rowsOf(page))

    // 已登记资源无论是否匹配技术检索仍参与工作台模型、资源组装。合并时保留分页条目顺序。
    var registered = readCollection(tablesFromActiveSources().isNotNull('resource_domain_id')
        .orderBy('table_name').orderBy('tid'), 1000)
    var loaded = {}
    for (table in tableRows) {
        loaded[text(table.tid)] = true
    }
    for (table in registered) {
        var tableId = text(table.tid)
        if (text(table.resourceDomainId) != '' && loaded[tableId] == null) {
            tableRows.add(table)
            loaded[tableId] = true
        }
    }
}
var tables = []
var tableIds = []
for (row in tableRows) {
    if (sourceById[text(row.datasourceId)] != null) { tables.add(row); tableIds.add(text(row.tid)) }
}
if (entityIds.size() > 0 && tableIds.size() != entityIds.size()) { exit 404, '部分技术表已删除或不属于当前租户' }
var columnRows = []
var columnProjection = [
    'tid', 'table_id', 'column_type', 'data_type', 'column_name', 'column_comment', 'length',
    'precision_length', 'scale', 'nullable', 'ordinal_position', 'primary_key', 'is_unique', 'data_standard_id'
]
// 仅批读已经核验的技术表。按最多 500 个表 ID 分组，避免逐表补查和方言的 IN 上限。
var columnStart = 0
while (columnStart < tableIds.size()) {
    var columnEnd = Math.min(columnStart + 500, tableIds.size())
    var batch = tableIds.subList(columnStart, columnEnd)
    columnRows.addAll(readCollection(active('db_table_column_t', columnProjection).in('table_id', batch)
        .orderBy('table_id').orderBy('ordinal_position').orderBy('tid'), 10000))
    columnStart = columnEnd
}
var fieldsByTable = {}
var primaryByTable = {}
var uniqueByTable = {}
var mappings = []
var standardRows = readCollection(active('da_metadata_t', [
    'tid', 'meta_name', 'meta_code', 'standard_encode', 'biz_def', 'data_type_id', 'field_type', 'field_length',
    'numeric_precision', 'numeric_scale', 'data_category_name', 'publish_status', 'version_no',
    'updated_time', 'created_time', 'is_nullable'
]).orderBy('meta_name').orderBy('tid'), 1000)
var standards = []
var standardById = {}
var standardByCode = {}
for (row in standardRows) {
    var standardId = text(row.tid)
    var revision = text(row.versionNo) != '' ? 'v' + text(row.versionNo) : DigestUtil.sha256Hex(text(row.updatedTime))
    var nativeType = text(row.fieldType) == '' ? text(row.dataTypeId) : text(row.fieldType)
    var published = text(row.publishStatus) == '1'
    var standard = {id:standardId, name:text(row.metaName), code:text(row.metaCode), standardEncode:text(row.standardEncode),
        definition:text(row.bizDef), type:family(nativeType), fieldType:nativeType, nativeType:nativeType,
        length:row.fieldLength, precision:number(row.numericPrecision)>0 ? number(row.numericPrecision) : null,
        scale:number(row.numericPrecision)>0 ? number(row.numericScale) : null, nullable:nullable(row.isNullable),
        domain:text(row.dataCategoryName), status:published ? 'PUBLISHED' : 'DRAFT',
        publishedRevisionId:published ? standardId + ':' + revision : null,
        revisionId:standardId + ':' + revision, version:row.versionNo == null ? 1 : row.versionNo,
        createdAt:row.createdTime, updatedAt:row.updatedTime}
    standards.add(standard)
    standardById[standardId] = standard
    if (text(row.metaCode) != '') { standardByCode[text(row.metaCode).toUpperCase()] = standard }
    if (text(row.standardEncode) != '') { standardByCode[text(row.standardEncode).toUpperCase()] = standard }
}
for (row in columnRows) {
    var tableId = text(row.tableId)
    if (fieldsByTable[tableId] == null) { fieldsByTable[tableId] = []; primaryByTable[tableId] = []; uniqueByTable[tableId] = [] }
    var fieldId = text(row.tid)
    var nativeType = text(row.columnType) == '' ? text(row.dataType) : text(row.columnType)
    var field = {id:fieldId, name:text(row.columnName), path:text(row.columnName), comment:text(row.columnComment),
        nativeType:nativeType, typeFamily:family(nativeType), length:row.length,
        precision:number(row.precisionLength)>0 ? number(row.precisionLength) : null,
        scale:number(row.precisionLength)>0 ? number(row.scale) : null,
        nullable:nullable(row.nullable), ordinal:row.ordinalPosition == null ? 0 : row.ordinalPosition}
    fieldsByTable[tableId].add(field)
    if (bool(row.primaryKey)) { primaryByTable[tableId].add(fieldId) }
    if (bool(row.isUnique)) { uniqueByTable[tableId].add({id:'unique:'+fieldId, kind:'unique', fieldIds:[fieldId], enforced:'unknown'}) }
    var reference = text(row.dataStandardId)
    if (reference != '') {
        var standard = standardById[reference]
        if (standard == null) { standard = standardByCode[reference.toUpperCase()] }
        mappings.add({id:'mapping:'+fieldId, fieldId:fieldId, field_id:fieldId, entityId:tableId, entity_id:tableId,
            mapping_mode:'STANDARD', targetId:standard == null ? reference : standard.id,
            target_id:standard == null ? reference : standard.id,
            targetRevisionId:standard == null ? null : standard.publishedRevisionId,
            state:standard == null ? 'MISSING' : standard.status == 'PUBLISHED' ? 'BOUND' : 'UNPUBLISHED'})
    }
}
var entities = []
var resources = []
for (row in tables) {
    var tableId = text(row.tid)
    var sourceId = text(row.datasourceId)
    var fields = fieldsByTable[tableId] == null ? [] : fieldsByTable[tableId]
    var keys = uniqueByTable[tableId] == null ? [] : uniqueByTable[tableId]
    if (primaryByTable[tableId] != null && primaryByTable[tableId].size()>0) { keys.add({id:'primary:'+tableId,kind:'primary',fieldIds:primaryByTable[tableId],enforced:'unknown'}) }
    var displayName = text(row.tableNameCn) != '' ? text(row.tableNameCn) : text(row.tableComment) != '' ? text(row.tableComment) : text(row.tableName)
    var entity = {id:tableId, sourceId:sourceId, name:text(row.tableName), displayName:displayName,
        comment:text(row.tableComment), kind:text(row.tableType).toLowerCase().contains('view') ? 'view' : 'table',
        shapeBasis:fields.size()>0 && number(row.fieldCount)==fields.size() ? 'declared_complete' : 'unknown',
        constraintsAvailability:'unknown', fields:fields, keys:keys}
    entities.add(entity)
    var revisionId = 'table:'+tableId+':'+text(row.revision)+':'+DigestUtil.sha256Hex(text(row.updatedTime))
    if (text(row.resourceDomainId) != '') {
        resources.add({id:tableId, entityId:tableId, structure:entity, structureRevisionId:revisionId,
            name:displayName, databaseId:text(row.resourceDomainId), domain:'', owner:text(row.resourceOwner),
            description:text(row.assetDesc), origin:text(row.resourcePlanId)=='' ? 'SNAPSHOT' : 'MATERIALIZATION',
            state:row.resourceState=='ARCHIVED' ? 'ARCHIVED' : 'ACTIVE', modelId:row.resourceModelId,
            deploymentId:row.resourcePlanId, designFieldMap:text(row.resourceFieldMap)=='' ? {} : JSONUtil.parse(row.resourceFieldMap),
            version:row.resourceRevisionNo == null ? 1 : row.resourceRevisionNo, createdAt:row.createdTime, updatedAt:row.updatedTime})
    }
}
// 质量观测只返回执行元数据。问题样本的原始行、敏感值和异常详情留在专用接口。
var results = []
var issues = []
var qualityError = null
try {
    var column = (alias, field) => tableRead.column(alias, field, 'TEXT')
    var numeric = (alias, field) => tableRead.column(alias, field, 'NUMBER')
    var time = (alias, field) => tableRead.column(alias, field, 'TIME')
    var runs = tableRead.from('dq_quality_run_t', 'r', tenantId)
    runs.where(tableRead.equal(numeric('r', 'is_del'), 0))
    runs.leftJoin('dq_quality_task_t', 't', 'tid', 'task_id', [
        tableRead.equal(numeric('t', 'is_del'), 0)
    ])
    runs.where(tableRead.not(tableRead.isNull(column('t', 'tid'))))
    var runId = column('r', 'tid')
    var tableId = column('t', 'table_id')
    var checked = numeric('r', 'checked_count')
    results = runs.rows({
        id: runId, entityId: tableId, status: column('r', 'status'), checkedcount: checked,
        violationcount: numeric('r', 'violation_count'),
        score: tableRead.when(tableRead.greaterThan(checked, 0), numeric('r', 'quality_score'), null),
        startedat: time('r', 'started_at'), finishedat: time('r', 'finished_at')
    }, time('r', 'created_time'), 'desc', 200)
    // 旧工作台响应同时提供两个实体标识键，结构化查询后显式保留兼容键。
    for (result in results) {
        result.put('entityid', result.entityId)
    }

    // 样本仅能关联同租户未删除运行和任务。先按运行归组，确保一个样本最多关联一条记录。
    var samples = tableRead.from('dq_quality_issue_sample_t', 'i', tenantId)
    samples.leftJoinGrouped(runs, 'observed_run', 'run_id', 'run_id', {
        run_id: runId, table_id: tableRead.max(tableId)
    }, [runId])
    samples.where(tableRead.not(tableRead.isNull(column('observed_run', 'run_id'))))
    var createdAt = time('i', 'created_time')
    issues = samples.rows({
        id: column('i', 'tid'), entityId: column('observed_run', 'table_id'), runid: column('i', 'run_id'),
        columnname: column('i', 'column_name'), severity: column('i', 'severity'),
        status: column('i', 'issue_status'), createdat: createdAt
    }, createdAt, 'desc', 200)
} catch (e) {
    qualityError = '质量观测读取失败，请稍后重试'
}
var user = currentUser()
var actorId = StpUtil.getLoginIdAsString()
var actorName = user.realName == null ? text(user.userName) : text(user.realName)
var members = [{principalId:actorId, name:actorName, orgId:text(user.orgId)}]
return {sources:sources, entities:entities, resources:resources, standards:standards, mappings:mappings,
    manifest:manifest, members:members, issues:issues, results:results, qualityError:qualityError,
    entityPagination:{total:total,pageNo:pageNo,pageSize:pageSize,loaded:entities.size()},
    actor:{principalId:actorId,name:actorName,orgId:text(user.orgId)}}
