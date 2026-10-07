{
  "properties": {},
  "id": "metadata_tables_reexplore_diff_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "17.数据库表变更探查",
  "createTime": 1784103600000,
  "updateTime": 1789199477547,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/tables/reExplore",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"dbId\": \"数据库资产ID\",\n    \"keyword\": \"可选\",\n    \"limit\": 0\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据库表变更探查（POST /dst/database/metadata/tables/reExplore）。比较物理表与登记快照，并同步校验字段治理配置中的字典表关联：同名有效字典自动迁移，失效关联标记为待重新关联；使用请求参数 dbId；涉及 db_table_t、db_datasource_t、db_table_column_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：17.数据库表变更探查
// 路由：POST /dst/database/metadata/tables/reExplore
// 业务说明：数据库表变更探查（POST /dst/database/metadata/tables/reExplore）。比较物理表与登记快照，并同步校验字段治理配置中的字典表关联：同名有效字典自动迁移，失效关联标记为待重新关联；使用请求参数 dbId；涉及 db_table_t、db_datasource_t、db_table_column_t 等业务表；执行数据库写入并返回处理结果。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService
import jsons
import cn.hutool.json.JSONUtil
import java.util.Map

/*
 * 元数据管理 17：比较物理库与 db_table_t 登记快照。
 *
 * 只读接口，不修改 MySQL、ES 或物理数据库。字段差异来自 db_table_column_t；
 * 返回 added/modified/deleted/unchanged，供 db-table 页面按结构视图着色。
 */

var textOf = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var lower = (value) => textOf(value).trim().toLowerCase()
var rowValue = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}

var parseGovernanceConfig = (value) => {
    if (blank(value)) { return null }
    try { return value instanceof Map ? value : jsons.toJSONObject(textOf(value)) } catch (e) { return null }
}
var isDictionaryRelation = (relation) => {
    if (relation == null) { return false }
    var enabled = lower(relation.enabled)
    var sourceType = lower(relation.sourceType == null ? relation.mode : relation.sourceType)
    return enabled != 'false' && sourceType != 'enum' && sourceType != 'standard'
        && (!blank(relation.dictionaryTableId) || !blank(relation.dictionaryTable))
}
// Reconcile every active field-governance relation against the current active
// dictionary snapshots.  This repairs same-name ID replacement and persists an
// explicit invalid marker when no usable dictionary asset remains.
var reconcileDictionaryRelations = (datasourceId, registeredRows, deletedRows) => {
    var result = { affectedTables: 0, affectedFields: 0, migratedFields: 0, invalidatedFields: 0, restoredFields: 0 }
    var missingIds = {}
    var missingNames = {}
    for (missing in deletedRows == null ? [] : deletedRows) {
        var missingId = textOf(firstValue(missing, ['tid']))
        var missingName = lower(firstValue(missing, ['tableName', 'table_name']))
        if (!blank(missingId)) { missingIds[missingId] = true }
        if (!blank(missingName)) { missingNames[missingName] = true }
    }
    var dictionaryById = {}
    var dictionaryByName = {}
    for (candidate in registeredRows == null ? [] : registeredRows) {
        var candidateId = textOf(firstValue(candidate, ['tid']))
        var candidateName = lower(firstValue(candidate, ['tableName', 'table_name']))
        var candidateType = textOf(firstValue(candidate, ['businessType', 'business_type']))
        if (candidateType != '字典表' || missingIds[candidateId] == true) { continue }
        dictionaryById[candidateId] = candidate
        if (!blank(candidateName) && missingNames[candidateName] != true) { dictionaryByName[candidateName] = candidate }
    }
    for (owner in registeredRows == null ? [] : registeredRows) {
        var ownerId = textOf(firstValue(owner, ['tid']))
        var config = parseGovernanceConfig(firstValue(owner, ['fieldGovernanceConfig', 'field_governance_config']))
        if (config == null || config.fields == null) { continue }
        var tableChanged = false
        for (field in config.fields) {
            var relation = field.dictionaryRelation == null ? field.dictionary_relation : field.dictionaryRelation
            if (!isDictionaryRelation(relation)) { continue }
            var relationId = textOf(relation.dictionaryTableId)
            var relationName = lower(relation.dictionaryTable)
            var candidate = blank(relationId) ? null : dictionaryById[relationId]
            if (candidate == null && !blank(relationName)) { candidate = dictionaryByName[relationName] }
            if (candidate != null) {
                var candidateId = textOf(firstValue(candidate, ['tid']))
                var candidateName = textOf(firstValue(candidate, ['tableName', 'table_name']))
                var wasInvalid = lower(relation.invalid) == 'true'
                if (relationId != candidateId || relationName != lower(candidateName) || wasInvalid) {
                    relation.dictionaryTableId = candidateId
                    relation.dictionaryTable = candidateName
                    relation.invalid = false
                    relation.invalidReason = ''
                    relation.invalidTableId = ''
                    relation.invalidTableName = ''
                    tableChanged = true
                    result.affectedFields = result.affectedFields + 1
                    if (relationId != candidateId) { result.migratedFields = result.migratedFields + 1 }
                    if (wasInvalid) { result.restoredFields = result.restoredFields + 1 }
                }
            } else {
                var alreadyInvalid = lower(relation.invalid) == 'true'
                if (!alreadyInvalid || textOf(relation.invalidTableId) != relationId || lower(relation.invalidTableName) != relationName) {
                    relation.invalid = true
                    relation.invalidReason = '关联字典已失效，请重新关联'
                    relation.invalidTableId = relationId
                    relation.invalidTableName = textOf(relation.dictionaryTable)
                    tableChanged = true
                    result.affectedFields = result.affectedFields + 1
                    result.invalidatedFields = result.invalidatedFields + 1
                }
            }
        }
        if (tableChanged) {
            db.table('db_table_t').where().eq('tid', ownerId).eq('is_del', 0)
                // Hutool's parser preserves JSON nulls as JSONNull; use its own
                // serializer here so legacy nullable governance fields remain valid JSON.
                .update({ field_governance_config: JSONUtil.toJsonStr(config), updated_time: new Date() })
            result.affectedTables = result.affectedTables + 1
        }
    }
    return result
}
var columnNameOf = (column) => column instanceof Map
    ? firstValue(column, ['columnName', 'column_name'])
    : column == null ? null : column.columnName
var columnTypeOf = (column) => column instanceof Map
    ? firstValue(column, ['columnType', 'column_type', 'dataType', 'data_type'])
    : column == null ? null : (blank(column.columnType) ? column.dataType : column.columnType)
var columnCommentOf = (column) => column instanceof Map
    ? firstValue(column, ['columnComment', 'column_comment'])
    : column == null ? null : column.columnComment
var normalizeColumnTypeForDiff = (value) => {
    var text = lower(value)
    if (blank(text)) {
        return ''
    }
    var bracket = text.indexOf('(')
    var base = bracket >= 0 ? text.substring(0, bracket).trim() : text
    // JDBC often reports DATETIME as DATETIME(19); this is display size, not a schema change.
    if (base == 'datetime' || base == 'timestamp' || base == 'date' || base == 'time' || base == 'year') {
        return base
    }
    // MySQL integer display width is also not a meaningful structural change.
    if (base == 'int' || base == 'integer' || base == 'bigint' || base == 'smallint' || base == 'tinyint' || base == 'mediumint') {
        return base
    }
    // Length-bearing text/decimal/binary types keep their precision in the normalized value.
    return text.replaceAll('\\s+', ' ')
}
var loadSource = (datasourceId) => {
    // 从 MySQL 业务表读取当前有效数据。
    var source = db.table('db_datasource_t').where().eq('tid', datasourceId).eq('is_del', 0).selectOne()
    if (source == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    var result = {
        tid: datasourceId,
        dbName: rowValue(source, ['dbName', 'db_name']),
        dbType: rowValue(source, ['dbType', 'db_type']),
        username: rowValue(source, ['username']),
        password: rowValue(source, ['password'])
    }
    var connectionConfig = datasourceConnectionConfig('read', rowValue(source, ['tid']), null, false)
    if (connectionConfig != null && !connectionConfig.isEmpty()) { result.putAll(connectionConfig) }
    result.tid = datasourceId
    result.dbName = rowValue(source, ['dbName', 'db_name'])
    result.dbType = rowValue(source, ['dbType', 'db_type'])
    result.username = rowValue(source, ['username'])
    result.password = rowValue(source, ['password'])
    // 返回当前业务分支的标准结果。
    return result
}
var dataSourceOf = (source) => {
    var config = new DataSourceConfig()
    var code = firstValue(source, ['dbType', 'databaseType'])
    try {
        config.setDatabaseType(blank(code) ? DatabaseType.MYSQL : DatabaseType.fromCode(lower(code)))
    } catch (e) {
        config.setDatabaseType(DatabaseType.MYSQL)
    }
    config.setHost(textOf(firstValue(source, ['ftpHost', 'host', 'dbIp'])))
    var port = firstValue(source, ['ftpPort', 'port', 'dbPort'])
    config.setPort(blank(port) ? 3306 : port::int)
    config.setDatabase(textOf(firstValue(source, ['database', 'dbName', 'dbMetaDbName'])))
    config.setUsername(textOf(firstValue(source, ['ftpUsername', 'username', 'dbUser', 'dbMetaUser'])))
    config.setPassword(textOf(firstValue(source, ['ftpPassword', 'password', 'dbPassword', 'dbMetaPassword'])))
    // 保留 FTP 协议、目录、匹配规则及解析选项，使第二步与第一步识别同一批文件。
    config.setConnectorProperties(source)
    // Oracle SID and Service Name use different URL formats; reuse the tested JDBC URL.
    config.setJdbcUrl(firstValue(source, ['jdbcURL', 'jdbcUrl', 'jdbc_url']))
    config.setSchema(firstValue(source, ['schema', 'defaultSchema']))
    // MinIO uses the optional object prefix as its table-discovery scope.
    // Reuse schema only inside the Java MinIO explorer; it keeps the shared
    // DataSourceConfig contract unchanged for every relational connector.
    if (config.getDatabaseType() == DatabaseType.MINIO) {
        config.setSchema(firstValue(source, ['minioPrefix', 'objectPrefix', 'prefix']))
    }
    // 返回当前业务分支的标准结果。
    return config
}
var columnMap = (columns) => {
    var result = {}
    for (column in columns == null ? [] : columns) {
        var name = columnNameOf(column)
        if (!blank(name)) {
            result[lower(name)] = column
        }
    }
    // 返回当前业务分支的标准结果。
    return result
}

// 解析请求体并为可选参数设置默认值。
var datasourceId = body == null ? null : body.dbId
if (blank(datasourceId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'dbId 不能为空'
}
var source = loadSource(datasourceId)
if (source == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '数据源不存在'
}
var config = dataSourceOf(source)
var physicalRows = metadataExplorerService.getTables(config)
// 从 MySQL 业务表读取当前有效数据。
var registeredRows = db.table('db_table_t').where()
    .eq('datasource_id', datasourceId).eq('is_del', 0).select()
// 结构化数据源会随着文件、对象或接口响应变化。先形成本轮可发现表的集合，
// 仅对仍存在的登记表读取字段；不再用已经消失的历史表名调用远端读取。
var physicalTableKeys = {}
for (physical in physicalRows == null ? [] : physicalRows) {
    var physicalTableName = physical.tableName
    if (!blank(physicalTableName)) {
        physicalTableKeys[lower(physicalTableName)] = true
    }
}
var registeredMap = {}
var registeredTableIds = []
var registeredTableNames = []
for (row in registeredRows) {
    var tableName = firstValue(row, ['tableName', 'table_name'])
    if (!blank(tableName)) {
        var tableKey = lower(tableName)
        registeredMap[tableKey] = row
        // 不存在于本轮探查结果的记录将在下方按“已删除”返回，不能再请求其字段。
        if (physicalTableKeys[tableKey] == true) {
            registeredTableNames.add(tableName)
        }
        registeredTableIds.add(firstValue(row, ['tid']))
    }
}
// 一次批量读取仍存在表的字段，避免远端逐表调用及历史表导致整批失败。
var physicalColumnsByTable = metadataExplorerService.getColumnsByTables(config, registeredTableNames)
var storedColumnsByTable = {}
if (registeredTableIds.size() > 0) {
    var storedColumns = db.table('db_table_column_t').where()
        .in('table_id', registeredTableIds).eq('is_del', 0).select()
    for (column in storedColumns) {
        var storedTableId = textOf(firstValue(column, ['tableId', 'table_id']))
        if (storedColumnsByTable[storedTableId] == null) {
            storedColumnsByTable[storedTableId] = []
        }
        storedColumnsByTable[storedTableId].add(column)
    }
}
var added = []
var modified = []
var unchanged = []
var deleted = []

for (physical in physicalRows == null ? [] : physicalRows) {
    var tableName = physical.tableName
    if (blank(tableName)) {
        continue
    }
    var key = lower(tableName)
    var before = registeredMap[key]
    registeredMap[key] = null
    // 表列表接口已经返回字段数。新增表只需要先展示在“新增”列表，不要逐表读取字段，
    // 否则 Kingbase/Oracle 等大库会因为几百张表逐个查字段导致详情页探查超时。
    var currentColumns = before == null ? [] : physicalColumnsByTable[key]
    if (currentColumns == null) {
        currentColumns = []
    }
    var physicalColumnCount = physical.columnCount == null ? 0 : physical.columnCount
    var current = {
        tableName: tableName,
        tableNameEn: tableName,
        tableNameCn: blank(physical.tableComment) ? tableName : physical.tableComment,
        tableComment: blank(physical.tableComment) ? tableName : physical.tableComment,
        tableType: physical.tableType,
        fieldCount: before == null ? physicalColumnCount : (currentColumns == null ? 0 : currentColumns.size()),
        columnCount: before == null ? physicalColumnCount : (currentColumns == null ? 0 : currentColumns.size()),
        recordCount: physical.rowCount == null ? 0 : physical.rowCount,
        storageSize: physical.totalSizeFormatted,
        columns: currentColumns == null ? [] : currentColumns
    }
    if (before == null) {
        added.add(current)
        continue
    }
    var tableId = firstValue(before, ['tid'])
    // 从 MySQL 业务表读取当前有效数据。
    var oldColumns = storedColumnsByTable[textOf(tableId)]
    if (oldColumns == null) {
        oldColumns = []
    }
    var oldMap = columnMap(oldColumns)
    var newMap = columnMap(currentColumns)
    var columnsAdded = []
    var columnsDeleted = []
    var columnsModified = []
    for (columnKey in newMap.keySet()) {
        var afterColumn = newMap[columnKey]
        var beforeColumn = oldMap[columnKey]
        if (beforeColumn == null) {
            columnsAdded.add(columnNameOf(afterColumn))
        } else {
            var beforeType = columnTypeOf(beforeColumn)
            var afterType = columnTypeOf(afterColumn)
            var beforeComment = columnCommentOf(beforeColumn)
            var afterComment = columnCommentOf(afterColumn)
            // Compare field types by database semantics: case and JDBC display width must not reset the workflow.
            if (normalizeColumnTypeForDiff(beforeType) != normalizeColumnTypeForDiff(afterType) || lower(beforeComment) != lower(afterComment)) {
                columnsModified.add(columnNameOf(afterColumn))
            }
            oldMap[columnKey] = null
        }
    }
    for (columnKey in oldMap.keySet()) {
        var oldColumn = oldMap[columnKey]
        if (oldColumn != null) {
            columnsDeleted.add(columnNameOf(oldColumn))
        }
    }
    // 中文表名是登记分类信息，不是物理文件的备注；仅比较真实备注，避免误报结构变更。
    var beforeComment = firstValue(before, ['tableComment', 'table_comment'])
    // Legacy/imported records may have an empty table comment snapshot. A newly read physical comment is enrichment,
    // not a structural change that should reset the registration workflow.
    // FTP 的文件名只是探查备注，不能覆盖或影响用户维护的中文表名。
    var commentChanged = config.getDatabaseType() != DatabaseType.FTP &&
        !blank(beforeComment) && lower(beforeComment) != lower(current.tableComment)
    var fieldCountChanged = oldColumns.size() != current.fieldCount
    var hasChange = commentChanged || fieldCountChanged ||
        columnsAdded.size() > 0 || columnsDeleted.size() > 0 || columnsModified.size() > 0
    if (hasChange) {
        modified.add({
            tableName: tableName,
            before: {
                tid: tableId,
                tableName: firstValue(before, ['tableName', 'table_name']),
                tableNameCn: beforeComment,
                tableComment: beforeComment,
                tableType: firstValue(before, ['tableType', 'table_type']),
                fieldCount: oldColumns.size(),
                recordCount: rowValue(before, ['recordCount', 'record_count']),
                storageSize: firstValue(before, ['storageSize', 'storage_size'])
            },
            after: current,
            changes: {
                tableCommentChanged: commentChanged,
                fieldCountChanged: fieldCountChanged,
                tableTypeChanged: false,
                recordCountChanged: false,
                columnsAdded: columnsAdded,
                columnsDeleted: columnsDeleted,
                columnsModified: columnsModified
            }
        })
    } else {
        unchanged.add(current)
    }
}
for (key in registeredMap.keySet()) {
    var row = registeredMap[key]
    if (row != null) {
        deleted.add({
            tid: firstValue(row, ['tid']),
            tableName: firstValue(row, ['tableName', 'table_name']),
            tableNameEn: firstValue(row, ['tableName', 'table_name']),
            tableNameCn: firstValue(row, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']),
            tableComment: firstValue(row, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn']),
            tableType: firstValue(row, ['tableType', 'table_type']),
            businessType: firstValue(row, ['businessType', 'business_type']),
            fieldCount: rowValue(row, ['fieldCount', 'field_count']),
            recordCount: rowValue(row, ['recordCount', 'record_count'])
        })
    }
}
var relationRepair = reconcileDictionaryRelations(datasourceId, registeredRows, deleted)
// 返回当前业务分支的标准结果。
return {
    summary: {
        totalScanned: physicalRows == null ? 0 : physicalRows.size(),
        addedCount: added.size(),
        modifiedCount: modified.size(),
        deletedCount: deleted.size(),
        unchangedCount: unchanged.size(),
        relationRepair: relationRepair
    },
    added: added,
    modified: modified,
    deleted: deleted,
    unchanged: unchanged,
    relationRepair: relationRepair
}
