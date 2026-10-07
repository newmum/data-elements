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
// 读取共享技术元数据投影。只读当前会话租户；数据源凭据、连接串和原始业务记录不进入响应。
// input.entityIds 指定真实 db_table_t.tid 时加载完整字段；默认分页 200 张并补齐已登记的仓库资源。
import tenantRuntime
import jsons
import cn.hutool.json.JSONUtil
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.NumberUtil
import cn.hutool.crypto.digest.DigestUtil
import '@post:/sym/user/me' as currentUser

var tenantId = tenantRuntime.id()
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
var sourceRows = db.select("""
    SELECT tid, db_name, COALESCE(NULLIF(db_type,''),database_type) AS engine,
           connection_status, is_enable, org_id, app_id, revision, updated_time
      FROM db_datasource_t
     WHERE tenant_id = #{tenantId} AND is_del = 0
     ORDER BY db_name, tid
""")
var sources = []
var sourceById = {}
var sourceIds = []
var manifest = {}
for (row in sourceRows) {
    var sourceId = text(row.tid)
    var engine = text(row.engine).toUpperCase()
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
var pageSize = input == null ? 200 : number(input.pageSize)
if (pageSize < 1) { pageSize = 200 }
if (pageSize > 500) { pageSize = 500 }
var offset = (pageNo - 1) * pageSize
var tableRows = []
var total = 0
if (entityIds.size() > 0) {
    tableRows = db.table('db_table_t').where().eq('tenant_id',tenantId).eq('is_del',0).in('tid',entityIds).select()
    total = tableRows.size()
} else {
    var countRow = db.selectOne("""
        SELECT COUNT(*) AS total
          FROM db_table_t t
          JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=#{tenantId} AND d.is_del=0
         WHERE t.tenant_id=#{tenantId} AND t.is_del=0
           AND (#{keyword}='' OR t.table_name LIKE #{search} OR t.table_name_cn LIKE #{search} OR t.table_comment LIKE #{search})
    """)
    total = countRow.total
    tableRows = db.select("""
        SELECT t.* FROM db_table_t t
          JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=#{tenantId} AND d.is_del=0
         WHERE t.tenant_id=#{tenantId} AND t.is_del=0
           AND (#{keyword}='' OR t.table_name LIKE #{search} OR t.table_name_cn LIKE #{search} OR t.table_comment LIKE #{search})
         ORDER BY t.table_name, t.tid LIMIT #{offset}, #{pageSize}
    """)
    var registered = db.select("""
        SELECT t.* FROM db_table_t t
          JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=#{tenantId} AND d.is_del=0
         WHERE t.tenant_id=#{tenantId} AND t.is_del=0
           AND t.resource_domain_id IS NOT NULL AND t.resource_domain_id<>''
         ORDER BY t.table_name, t.tid
    """)
    var loaded = []
    for (table in tableRows) { loaded.add(text(table.tid)) }
    for (table in registered) { if (!loaded.contains(text(table.tid))) { tableRows.add(table); loaded.add(text(table.tid)) } }
}
var tables = []
var tableIds = []
for (row in tableRows) {
    if (sourceById[text(row.datasourceId)] != null) { tables.add(row); tableIds.add(text(row.tid)) }
}
if (entityIds.size() > 0 && tableIds.size() != entityIds.size()) { exit 404, '部分技术表已删除或不属于当前租户' }
var columnRows = tableIds.size() == 0 ? [] : db.table('db_table_column_t').where().eq('tenant_id',tenantId).eq('is_del',0).in('table_id',tableIds).orderBy('table_id','asc').orderBy('ordinal_position','asc').orderBy('tid','asc').select()
var fieldsByTable = {}
var primaryByTable = {}
var uniqueByTable = {}
var mappings = []
var standardRows = db.select("""
    SELECT tid, meta_name, meta_code, standard_encode, biz_def, data_type_id,
           field_type, field_length, numeric_precision, numeric_scale, data_category_name,
           publish_status, version_no, updated_time, created_time, is_nullable
      FROM da_metadata_t WHERE tenant_id=#{tenantId} AND is_del=0
     ORDER BY meta_name, tid
""")
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
// Quality results contain execution metadata only. Row snapshots and sensitive issue values remain behind the quality detail APIs.
var results = []
var issues = []
var qualityError = null
try {
results = db.select("""
    SELECT r.tid AS id, t.table_id AS entity_id, t.table_id AS entityId, r.status,
           r.checked_count AS checkedCount, r.violation_count AS violationCount,
           CASE WHEN r.checked_count>0 THEN r.quality_score ELSE NULL END AS score,
           r.started_at AS startedAt, r.finished_at AS finishedAt
      FROM dq_quality_run_t r
      JOIN dq_quality_task_t t ON t.tid=r.task_id AND t.tenant_id=#{tenantId} AND t.is_del=0
     WHERE r.tenant_id=#{tenantId} AND r.is_del=0 ORDER BY r.created_time DESC LIMIT 200
""")
issues = db.select("""
    SELECT i.tid AS id, t.table_id AS entity_id, i.run_id AS runId, i.column_name AS columnName,
           i.severity, i.issue_status AS status, i.created_time AS createdAt
      FROM dq_quality_issue_sample_t i
      JOIN dq_quality_run_t r ON r.tid=i.run_id AND r.tenant_id=#{tenantId} AND r.is_del=0
      JOIN dq_quality_task_t t ON t.tid=r.task_id AND t.tenant_id=#{tenantId} AND t.is_del=0
     WHERE i.tenant_id=#{tenantId} ORDER BY i.created_time DESC LIMIT 200
""")
} catch (e) { qualityError = '质量观测读取失败：' + text(e.getMessage()) }
var user = currentUser()
var actorId = StpUtil.getLoginIdAsString()
var actorName = user.realName == null ? text(user.userName) : text(user.realName)
var members = [{principalId:actorId, name:actorName, orgId:text(user.orgId)}]
return {sources:sources, entities:entities, resources:resources, standards:standards, mappings:mappings,
    manifest:manifest, members:members, issues:issues, results:results, qualityError:qualityError,
    entityPagination:{total:total,pageNo:pageNo,pageSize:pageSize,loaded:entities.size()},
    actor:{principalId:actorId,name:actorName,orgId:text(user.orgId)}}
