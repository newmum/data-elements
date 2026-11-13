{
  "id": "metadata_file_registration_01",
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "25.文件登记快照",
  "path": "/fileRegistration",
  "method": "POST",
  "parameters": [],
  "headers": [],
  "options": [],
  "description": "非结构化推送文件定义的读取、保存与登记完成。复用当前租户 db_table_t(FILE) 和 db_table_column_t，不探查物理数据库；read/save/complete 均按数据源归属校验，save 支持 dryRun。"
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableWrite
import cn.hutool.core.util.IdUtil
import cn.hutool.json.JSONUtil
import java.util.List
import java.util.HashSet
import java.util.Map
import '@post:/dst/maintenance/refresh' as refreshAssetIndex
import log

/**
 * POST /dst/database/metadata/fileRegistration 读取、预检、保存或结束非结构化文件登记。
 * 请求指定当前租户下的 dbId，保存时提交最多 100 项文件定义。关联表和字段按角色范围批量校验。
 * 写入先锁定授权数据源，再在同一事务内读取快照、更新文件及字段。dryRun 只返回校验结果。
 * 结束登记成功后刷新搜索投影。FILE 是逻辑登记项，不探查或修改外部物理库。
 */
var text = value => value == null ? '' : (value + '').trim()
var value = (row, snake, camel) => row == null ? null : (row[snake] == null ? row[camel] : row[snake])
var object = input => {
    if (input == null || text(input) == '') { return {} }
    if (input instanceof Map) { return input }
    try {
        return JSONUtil.parseObj(input + '')
    } catch (e) {
        exit 422, '文件登记配置不是有效JSON对象'
    }
}

var tenantId = text(tenantRuntime.id())
if (tenantId == '') { exit 401, '请登录并选择当前行业后操作' }
if (body?.tenantId != null || body?.tenant_id != null) {
    exit 400, '行业归属由当前登录会话确定，不接受请求指定'
}
var sourceId = text(body?.dbId)
var action = text(body?.action) || 'read'
if (!['read', 'save', 'complete'].contains(action) || sourceId == '') {
    exit 400, '请提供有效的数据源及文件登记操作'
}

var scope = dataScope.resource('MENU:2081000000000000002')
var sourceConditions = {is_del: 0}
if (scope?.scope == 'OWNER') {
    var ownerId = text(scope.userId)
    if (ownerId == '') { exit 403, '当前账号缺少有效的数据范围' }
    sourceConditions.created_by = ownerId
} else if (scope?.scope == 'ORG') {
    var organizations = scope.organizationIds || []
    if (organizations.size() == 0) { exit 404, '数据源不存在或无权访问' }
    sourceConditions.org_id = organizations
} else if (scope?.scope != 'ALL') {
    exit 403, '当前账号缺少有效的数据范围'
}

var writing = action != 'read' && body?.dryRun != true
var loadSource = () => {
    // 写事务的第一条业务查询即锁定授权父记录，后续读取不会使用加锁前的旧快照。
    if (writing) {
        return tableWrite.lockRow('db_datasource_t', tenantId, 'tid', sourceId,
            ['tid', 'pool_cfg', 'org_id'], sourceConditions)
    }
    var query = db.table('db_datasource_t').where().eq('tenant_id', tenantId).eq('tid', sourceId).eq('is_del', 0)
    if (scope.scope == 'OWNER') {
        query.eq('created_by', sourceConditions.created_by)
    } else if (scope.scope == 'ORG') {
        query.in('org_id', sourceConditions.org_id)
    }
    return query.selectOne()
}

var loadSnapshot = () => {
    // 先按当前源校验文件数上限，再按本批文件 ID 一次预取字段。
    var oldTables = db.table('db_table_t').where()
        .eq('tenant_id', tenantId).eq('datasource_id', sourceId).eq('table_type', 'FILE').eq('is_del', 0)
        .orderBy('created_time').orderBy('tid').select()
    if (oldTables.size() > 100) { exit 422, '文件登记项超过100项，请先整理现有清单' }
    var oldIds = oldTables.map(row => text(row.tid))
    var oldColumns = oldIds.size() == 0 ? [] : db.table('db_table_column_t').where()
        .eq('tenant_id', tenantId).in('table_id', oldIds).eq('is_del', 0).select()
    var snapshot = {rows: [], ids: oldIds, byId: {}, byName: {}, byCode: {}, columns: {}, fieldNames: {}}
    for (column in oldColumns) {
        var tableId = text(value(column, 'table_id', 'tableId'))
        var name = text(value(column, 'column_name', 'columnName'))
        snapshot.columns[tableId + ':' + name] = column
        if (snapshot.fieldNames[tableId] == null) { snapshot.fieldNames[tableId] = [] }
        snapshot.fieldNames[tableId].add(name)
    }
    for (table in oldTables) {
        var id = text(table.tid)
        var policy = object(value(table, 'field_governance_config', 'fieldGovernanceConfig')).fileRegistration || {}
        var maxMb = policy.maxFileSizeMb
        if (maxMb == null && policy.maxFileBytes != null) {
            maxMb = Math.round(policy.maxFileBytes::double / 1048576 * 1000000) / 1000000.0
        }
        var row = {
            tableId: id,
            collectionId: text(value(table, 'table_name', 'tableName')),
            fileCode: text(policy.fileCode || value(table, 'table_name', 'tableName')),
            fileName: text(value(table, 'table_name_cn', 'tableNameCn')),
            fileDescription: text(value(table, 'table_comment', 'tableComment')),
            fileFormat: text(policy.fileFormat),
            maxFileBytes: policy.maxFileBytes,
            maxFileSizeMb: maxMb,
            relatedTableName: text(policy.relatedTableName),
            relatedTableId: text(policy.relatedTableId),
            relatedFieldName: text(policy.relatedFieldName),
            relatedFieldId: text(policy.relatedFieldId),
            registered: (value(table, 'asset_status', 'assetStatus') + '') == '2',
            fieldCount: (snapshot.fieldNames[id] || []).size()
        }
        snapshot.rows.add(row)
        snapshot.byId[id] = row
        snapshot.byName[row.fileName.toLowerCase()] = row
        snapshot.byCode[row.fileCode.toLowerCase()] = row
    }
    return snapshot
}

var normalizeFiles = (incoming, snapshot) => {
    if (!(incoming instanceof List) || incoming.size() == 0 || incoming.size() > 100) {
        exit 422, '请填写1至100项文件登记信息'
    }
    var normalized = []
    var names = new HashSet()
    var requestedIds = new HashSet()
    var requestedCodes = new HashSet()
    var relatedNames = new HashSet()
    for (item in incoming) {
        var name = text(item.fileName)
        var description = text(item.fileDescription)
        var fileCode = text(item.fileCode)
        var format = text(item.fileFormat).toLowerCase().replace('.', '')
        if (name == '' || name.length() > 255 || description == '' || description.length() > 2000) {
            exit 422, '文件名称、文件说明必填，名称最多255字，说明最多2000字'
        }
        if (!names.add(name.toLowerCase())) { exit 422, '同次导入的文件名称不能重复' }
        if (!['jpg', 'jpeg', 'png', 'pdf', 'docx'].contains(format)) {
            exit 422, '文件格式请选择JPG、JPEG、PNG、PDF或DOCX'
        }
        var sizeText = text(item.maxFileSizeMb)
        if (!sizeText.matches('[0-9]+(\\.[0-9]{1,6})?')) {
            exit 422, '文件大小上限必须是大于0的MB数值，最多6位小数'
        }
        var bytes = Math.round(sizeText::double * 1048576)
        if (bytes < 1 || bytes > 1099511627776L) {
            exit 422, '文件大小上限须大于0且不超过1048576MB，实际还受海通节点限制'
        }
        var id = text(item.tableId)
        if (id != '' && snapshot.byId[id] == null) { exit 404, '文件登记ID不存在或不属于当前数据源' }
        // 旧集合 ID 保持稳定，优先按显式 ID 或已登记代码匹配，再按原名称匹配。
        var old = snapshot.byId[id]
        if (id == '') {
            var oldByName = snapshot.byName[name.toLowerCase()]
            old = fileCode == '' ? oldByName : (snapshot.byCode[fileCode.toLowerCase()] || oldByName)
        }
        if (fileCode != '' && !fileCode.matches('[a-z][a-z0-9_]{0,79}') && !(old != null && fileCode == old.fileCode)) {
            exit 422, '文件英文名须以小写字母开头，仅含小写字母、数字和下划线，最多80位'
        }
        if (old != null) { id = old.tableId }
        if (id == '') { id = IdUtil.getSnowflakeNextIdStr() }
        if (fileCode == '') { fileCode = old == null ? 'file_' + id : old.fileCode }
        if (old != null && (old.fileCode != old.collectionId || !old.fileCode.matches('file_[0-9]+')) && fileCode != old.fileCode) {
            exit 409, '文件英文名已作为调用标识使用，不能修改；请新增文件类别'
        }
        var codeKey = fileCode.toLowerCase()
        var codeOwner = snapshot.byCode[codeKey]
        if (codeOwner != null && codeOwner.tableId != id) { exit 422, '同一数据源的文件英文名不能重复' }
        for (other in snapshot.rows) {
            if (other.collectionId == fileCode && other.tableId != id) { exit 422, '文件英文名与已有文件集合编码冲突' }
        }
        if (!requestedIds.add(id)) { exit 422, '同次导入不能重复引用同一文件登记项' }
        if (!requestedCodes.add(codeKey)) { exit 422, '同一数据源的文件英文名不能重复' }

        var relatedTable = text(action == 'complete' && text(item.relatedTableId) != '' ? item.relatedTableId : item.relatedTableName)
        var relatedField = text(action == 'complete' && text(item.relatedFieldId) != '' ? item.relatedFieldId : item.relatedFieldName)
        if (relatedTable.length() > 255 || relatedField.length() > 255 || (relatedTable == '' && relatedField != '')) {
            exit 422, '填写关联字段时须同时填写关联表，名称最多255字'
        }
        if (relatedTable != '') { relatedNames.add(relatedTable) }
        normalized.add({
            tableId: id, collectionId: old == null ? fileCode : old.collectionId, fileCode: fileCode,
            fileName: name, fileDescription: description, fileFormat: format,
            maxFileBytes: bytes, maxFileSizeMb: sizeText::double, relatedTableName: relatedTable,
            relatedFieldName: relatedField, registered: action == 'complete'
        })
    }
    var totalIds = new HashSet(snapshot.ids)
    totalIds.addAll(requestedIds)
    if (totalIds.size() > 100) {
        exit 422, '同一数据源最多100项文件定义；重复导入按名称或登记ID更新，不删除未列出的已有项'
    }
    return {files: normalized, relatedNames: relatedNames}
}

// 正常匹配各读一批。历史同名过多时按唯一 ID 分窗，只保留本次请求所需的匹配和歧义标记。
var scanReferences = (createQuery, projection, idColumn, accept) => {
    var cursor = null
    while (true) {
        var query = createQuery()
        if (cursor != null) { query.where(tableRead.greaterThan(idColumn, cursor)) }
        var candidates = query.rows(projection, idColumn, 'asc', 1000)
        for (candidate in candidates) { accept(candidate) }
        if (candidates.size() < 1000) { break }
        cursor = candidates[candidates.size() - 1].tid
    }
}
var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
var active = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
var newMatches = () => { return {ids: {}, names: {}, ambiguous: new HashSet()} }
var recordMatch = (matches, keys, row, name) => {
    if (keys.contains(text(row.tid))) { matches.ids[text(row.tid)] = row }
    if (keys.contains(name)) {
        if (matches.names[name] == null) { matches.names[name] = row }
        else { matches.ambiguous.add(name) }
    }
}
var resolveMatch = (matches, key) => {
    var exact = matches.ids[key]
    return exact != null ? exact : (matches.ambiguous.contains(key) ? null : matches.names[key])
}

var resolveReferences = (normalized, relatedNames) => {
    var tableMatches = newMatches()
    if (relatedNames.size() > 0) {
        var keys = []
        keys.addAll(relatedNames)
        var referenceId = textColumn('ref_table', 'tid')
        var referenceName = textColumn('ref_table', 'table_name')
        var dbId = textColumn('source_db', 'tid')
        var createQuery = () => {
            var query = tableRead.from('db_table_t', 'ref_table', tenantId)
            query.leftJoin('db_datasource_t', 'source_db', 'tid', 'datasource_id', [active('source_db')])
            query.where(tableRead.all([
                active('ref_table'), tableRead.not(tableRead.isNull(dbId)),
                tableRead.any([tableRead.in(referenceId, keys), tableRead.in(referenceName, keys)])
            ]))
            if (scope.scope == 'OWNER') {
                query.where(tableRead.equal(textColumn('source_db', 'created_by'), text(scope.userId)))
            } else if (scope.scope == 'ORG') {
                query.where(tableRead.in(textColumn('source_db', 'org_id'), scope.organizationIds))
            }
            return query
        }
        scanReferences(createQuery, {
            tid: referenceId, table_name: referenceName, table_type: textColumn('ref_table', 'table_type')
        }, referenceId, table => recordMatch(tableMatches, relatedNames, table, text(table.table_name)))
    }

    // 仅为已唯一定位且非 FILE 的关联表读取本次选中的字段，避免跨表名称组合扩大读取范围。
    var tableByFile = {}
    var fieldKeysByTable = {}
    for (row in normalized) {
        if (row.relatedTableName == '') { continue }
        var table = resolveMatch(tableMatches, row.relatedTableName)
        if (table == null || text(table.table_type) == 'FILE') { continue }
        tableByFile[row.tableId] = table
        if (row.relatedFieldName == '') { continue }
        var tableId = text(table.tid)
        if (fieldKeysByTable[tableId] == null) { fieldKeysByTable[tableId] = [] }
        if (!fieldKeysByTable[tableId].contains(row.relatedFieldName)) {
            fieldKeysByTable[tableId].add(row.relatedFieldName)
        }
    }

    var fieldMatchesByTable = {}
    if (fieldKeysByTable.size() > 0) {
        var fieldId = textColumn('ref_field', 'tid')
        var parentId = textColumn('ref_field', 'table_id')
        var fieldName = textColumn('ref_field', 'column_name')
        var conditions = []
        for (tableId in fieldKeysByTable.keySet()) {
            var fieldKeys = fieldKeysByTable[tableId]
            fieldMatchesByTable[tableId] = newMatches()
            conditions.add(tableRead.all([
                tableRead.equal(parentId, tableId),
                tableRead.any([tableRead.in(fieldId, fieldKeys), tableRead.in(fieldName, fieldKeys)])
            ]))
        }
        var createQuery = () => {
            var query = tableRead.from('db_table_column_t', 'ref_field', tenantId)
            query.where(tableRead.all([active('ref_field'), tableRead.any(conditions)]))
            return query
        }
        scanReferences(createQuery, {tid: fieldId, table_id: parentId, column_name: fieldName}, fieldId, column => {
            var tableId = text(column.table_id)
            if (fieldMatchesByTable[tableId] != null) {
                recordMatch(fieldMatchesByTable[tableId], fieldKeysByTable[tableId], column, text(column.column_name))
            }
        })
    }

    for (row in normalized) {
        if (row.relatedTableName == '') { continue }
        var table = tableByFile[row.tableId]
        if (table == null) { exit 422, '关联表不存在、同名不唯一或不是结构化表；同名时可填写当前行业的登记表ID' }
        row.relatedTableId = text(table.tid)
        row.relatedTableName = text(table.table_name)
        if (row.relatedFieldName != '') {
            var matches = fieldMatchesByTable[row.relatedTableId]
            var column = resolveMatch(matches, row.relatedFieldName)
            if (column == null) {
                if (matches.ambiguous.contains(row.relatedFieldName)) { exit 422, '关联字段同名不唯一，请填写当前关联表的字段ID' }
                exit 422, '关联字段不存在或不属于所选关联表'
            }
            row.relatedFieldId = text(column.tid)
            row.relatedFieldName = text(column.column_name)
        }
    }
}

var attributes = [
    {name: 'file_name', comment: '登记文件名称', type: 'varchar', length: 255, required: true},
    {name: 'file_description', comment: '登记文件说明', type: 'varchar', length: 2000, required: true},
    {name: 'file_format', comment: '允许的文件扩展名', type: 'varchar', length: 16, required: true},
    {name: 'max_file_bytes', comment: '文件大小上限，单位字节', type: 'bigint', length: 0, required: true},
    {name: 'related_table_name', comment: '可选关联数据表', type: 'varchar', length: 255, required: false},
    {name: 'related_field_name', comment: '可选关联数据表字段', type: 'varchar', length: 255, required: false}
]

var applyRegistrationStatus = (normalized, snapshot) => {
    // 相同定义保留已完成状态；修改的文件恢复待登记，未提交的旧文件参与数据源状态计算。
    var merged = {}
    for (id, row in snapshot.byId) { merged[id] = row }
    for (row in normalized) {
        var old = snapshot.byId[row.tableId]
        var unchanged = old != null && old.fileCode == row.fileCode && old.fileName == row.fileName
            && old.fileDescription == row.fileDescription && old.fileFormat == row.fileFormat
            && old.maxFileBytes == row.maxFileBytes && old.relatedTableName == row.relatedTableName
            && old.relatedFieldName == row.relatedFieldName && text(old.relatedTableId) == text(row.relatedTableId)
            && text(old.relatedFieldId) == text(row.relatedFieldId)
        row.registered = action == 'complete' || (unchanged && old.registered == true)
        merged[row.tableId] = row
    }
    if (action == 'complete') {
        var requiredNames = attributes.map(attribute => attribute.name)
        for (row in normalized) {
            var savedNames = snapshot.fieldNames[row.tableId] || []
            if (!savedNames.containsAll(requiredNames)) {
                exit 422, '文件字段快照不完整，请返回第一步重新导入并保存'
            }
        }
    }
    if (merged.size() == 0) { return 0 }
    for (row in merged.values()) {
        if (row.registered != true) { return 0 }
    }
    return 2
}

var writeDefinitions = (normalized, snapshot, source, now) => {
    var insertTables = []
    var updateTables = []
    var insertColumns = []
    var updateColumns = []
    for (row in normalized) {
        var config = JSONUtil.toJsonStr({
            resourceKind: 'FILE',
            fileRegistration: {
                fileCode: row.fileCode, fileFormat: row.fileFormat, maxFileBytes: row.maxFileBytes,
                maxFileSizeMb: row.maxFileSizeMb, relatedTableName: row.relatedTableName, relatedTableId: row.relatedTableId,
                relatedFieldName: row.relatedFieldName, relatedFieldId: row.relatedFieldId
            }
        })
        if (snapshot.byId[row.tableId] == null) {
            insertTables.add({
                tid: row.tableId, datasource_id: sourceId, table_name: row.collectionId, table_name_cn: row.fileName,
                table_comment: row.fileDescription, table_type: 'FILE', business_type: '文件', annotated: 1,
                field_count: attributes.size(), field_governance_config: config, tenant_id: tenantId,
                org_id: value(source, 'org_id', 'orgId'), asset_status: 0, flow_status: 0, is_del: 0,
                created_time: now, updated_time: now, reg_time: now
            })
        } else {
            var status = row.registered ? 2 : 0
            updateTables.add({
                tid: row.tableId, table_name_cn: row.fileName, table_comment: row.fileDescription,
                field_governance_config: config, updated_time: now, asset_status: status, flow_status: status
            })
        }

        var ordinal = 0
        for (attribute in attributes) {
            ordinal++
            var old = snapshot.columns[row.tableId + ':' + attribute.name]
            var column = {
                tid: old == null ? IdUtil.getSnowflakeNextIdStr() : text(old.tid), table_id: row.tableId,
                column_comment: attribute.comment, data_type: attribute.type, column_type: attribute.type,
                length: attribute.length, nullable: attribute.required ? 0 : 1, ordinal_position: ordinal, updated_time: now
            }
            if (old == null) {
                column.column_name = attribute.name
                column.primary_key = 0
                column.tenant_id = tenantId
                column.is_del = 0
                column.created_time = now
                insertColumns.add(column)
            } else {
                updateColumns.add(column)
            }
        }
    }

    // 普通插入复用链式接口，不同记录的字段值用预绑定批量更新，保持原有审计字段处理。
    if (insertTables.size() > 0) { db.table('db_table_t').batchInsert(insertTables, 100) }
    if (insertColumns.size() > 0) { db.table('db_table_column_t').batchInsert(insertColumns, 600) }
    if (updateTables.size() > 0) {
        var updated = tableWrite.batchUpdate('db_table_t', tenantId,
            ['table_name_cn', 'table_comment', 'field_governance_config', 'updated_time', 'asset_status', 'flow_status'],
            ['tid'], {datasource_id: sourceId, table_type: 'FILE', is_del: 0}, updateTables)
        if (updated != updateTables.size()) { exit 409, '文件登记已变化，请刷新后重试' }
    }
    if (updateColumns.size() > 0) {
        var updated = tableWrite.batchUpdate('db_table_column_t', tenantId,
            ['column_comment', 'data_type', 'column_type', 'length', 'nullable', 'ordinal_position', 'updated_time'],
            ['tid', 'table_id'], {is_del: 0}, updateColumns)
        if (updated != updateColumns.size()) { exit 409, '文件字段快照已变化，请刷新后重试' }
    }
}

var executeRegistration = () => {
    var source = loadSource()
    if (source == null) { exit 404, '数据源不存在或不属于当前行业' }
    var config = object(value(source, 'pool_cfg', 'poolCfg'))
    if (!['receive', 'push'].contains(text(config.accessMode)) || text(config.pushDataType).toUpperCase() != 'UNSTRUCTURED') {
        exit 409, '当前数据源不是非结构化数据推送方式，请先在第一步保存类型'
    }
    var snapshot = loadSnapshot()
    if (action == 'read') {
        return {datasourceId: sourceId, list: snapshot.rows, total: snapshot.rows.size(), resourceKind: 'FILE'}
    }

    var input = normalizeFiles(action == 'complete' ? snapshot.rows : body.files, snapshot)
    var normalized = input.files
    resolveReferences(normalized, input.relatedNames)
    var sourceStatus = applyRegistrationStatus(normalized, snapshot)
    if (!writing) {
        return {valid: true, dryRun: true, datasourceId: sourceId, list: normalized, total: normalized.size(), resourceKind: 'FILE'}
    }

    var now = new Date()
    if (action == 'complete') {
        // 只完成已校验的有效文件，并保留现有链式更新的审计处理。
        var updated = db.table('db_table_t').where().eq('tenant_id', tenantId)
            .eq('datasource_id', sourceId).eq('table_type', 'FILE').eq('is_del', 0)
            .in('tid', normalized.map(row => row.tableId)).update({asset_status: 2, flow_status: 2, updated_time: now})
        if (updated != normalized.size()) { exit 409, '文件登记已变化，请刷新后重试' }
    } else {
        writeDefinitions(normalized, snapshot, source, now)
    }
    db.table('db_datasource_t').where().eq('tenant_id', tenantId).eq('tid', sourceId).eq('is_del', 0)
        .update({asset_status: sourceStatus, flow_status: sourceStatus, updated_time: now})
    return {
        datasourceId: sourceId, list: normalized, total: normalized.size(), resourceKind: 'FILE',
        saved: true, assetStatus: sourceStatus
    }
}

var result = writing ? db.transaction(executeRegistration) : executeRegistration()
if (writing && action == 'complete') {
    // 写事务提交后刷新投影。刷新失败保留已提交的登记状态。
    body = {tid: sourceId, assetType: 'db'}
    try {
        refreshAssetIndex()
    } catch (e) {
        log.warn('文件登记状态已保存，数据源搜索投影刷新待重试')
    }
}
return result
