{
  "properties": {},
  "id": "datapd_report_data_01",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "08.数据上报落库",
  "createTime": 1788347944807,
  "updateTime": 1788389142945,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/report-data",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"action\":\"list\",\"datasourceId\":\"2095081172819283968\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据上报工作区接口。按登记表字段白名单校验，将实际上报数据和批次记录写入 test1；支持批次列表、实际入库数据预览、导入与删除；使用请求参数 tables；涉及 db_datasource_t、db_table_t、db_table_column_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.数据上报落库
// 路由：POST /dst/database/report-data
// 业务说明：数据上报工作区接口。按登记表字段白名单校验，将实际上报数据和批次记录写入 test1；支持批次列表、实际入库数据预览、导入与删除；使用请求参数 tables；涉及 db_datasource_t、db_table_t、db_table_column_t 等业务表。
import tenantRuntime
import approvalRuntime
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import cn.hutool.core.util.IdUtil
import java.util.Map

/*
 * 数据源 31：数据上报落库。
 *
 * 当前上报存储库固定为 MySQL test1；后续配置化只需替换 storageSchema 的解析方式。
 * 上报数据按登记库 db_table_t / db_table_column_t 的表和字段白名单校验，实际业务行写入
 * test1 中的同名表；批次及批次-表关系也保存在 test1，避免把大量上报状态塞入 pool_cfg。
 */
var storageSchema = 'test1'
var textOf = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) { return value }
    }
    return null
}
var firstText = (row, keys) => {
    var value = first(row, keys)
    return blank(value) ? '' : textOf(value).trim()
}
var identifier = (value, label) => {
    var name = textOf(value).trim()
    if (blank(name) || name.length() > 64 || !name.matches('^[A-Za-z][A-Za-z0-9_]{0,63}$')) {
        exit 400, label + '只能包含英文字母、数字和下划线，且必须以字母开头'
    }
    return name
}
var mysqlType = (column) => {
    var raw = firstText(column, ['columnType', 'column_type', 'dataType', 'data_type']).toLowerCase()
    var length = first(column, ['length', 'columnLength', 'column_length'])
    var size = length == null ? 0 : (length + '')::int
    if (raw == '日期时间' || raw == 'datetime' || raw == 'timestamp') { return 'datetime' }
    if (raw == '日期' || raw == 'date') { return 'date' }
    if (raw == '时间' || raw == 'time') { return 'time' }
    if (raw == '整数' || raw == 'int' || raw == 'integer' || raw == 'bigint' || raw == 'long') { return 'bigint' }
    if (raw == '小数' || raw == '数字' || raw == 'decimal' || raw == 'number' || raw == 'numeric') { return 'decimal(30,10)' }
    if (raw == '布尔' || raw == 'boolean' || raw == 'bool') { return 'tinyint(1)' }
    if (raw == '文本' || raw == 'text' || raw == 'longtext') { return 'text' }
    if (raw.matches('^(var)?char\\([0-9]{1,5}\\)$')) { return raw }
    return 'varchar(' + (size > 0 && size <= 4000 ? size : 500) + ')'
}
var ensureMetadataTables = () => {
    db.update("""
        create table if not exists `test1`.`de_data_report_batch_t` (
          `batch_id` varchar(40) not null,
          `datasource_id` varchar(40) not null,
          `tenant_id` varchar(40) not null,
          `file_id` varchar(64) null,
          `file_name` varchar(255) not null,
          `file_url` varchar(1000) null,
          `table_count` int not null default 0,
          `row_count` bigint not null default 0,
          `status` varchar(20) not null default 'completed',
          `error_message` varchar(1000) null,
          `created_by` varchar(64) null,
          `created_name` varchar(100) null,
          `created_time` datetime not null,
          `is_del` tinyint not null default 0,
          primary key (`batch_id`),
          key `idx_report_batch_source` (`tenant_id`, `datasource_id`, `is_del`, `created_time`)
        ) engine=InnoDB default charset=utf8mb4 comment='数据上报批次'
    """)
    db.update("""
        create table if not exists `test1`.`de_data_report_batch_table_t` (
          `tid` varchar(40) not null,
          `batch_id` varchar(40) not null,
          `datasource_id` varchar(40) not null,
          `tenant_id` varchar(40) not null,
          `table_id` varchar(40) not null,
          `table_name` varchar(64) not null,
          `table_name_cn` varchar(255) null,
          `row_count` bigint not null default 0,
          `created_time` datetime not null,
          `is_del` tinyint not null default 0,
          primary key (`tid`),
          key `idx_report_batch_table` (`batch_id`, `is_del`)
        ) engine=InnoDB default charset=utf8mb4 comment='数据上报批次表明细'
    """)
}
var tenantId = textOf(tenantRuntime.id()).trim()
if (blank(tenantId)) { exit 401, '无法识别当前租户，请重新登录后再操作' }
var datasourceId = firstText(body, ['datasourceId', 'datasource_id', 'dbId', 'tid'])
if (blank(datasourceId)) { exit 400, '数据源不能为空' }
var source = db.table('db_datasource_t').where()
    .eq('tid', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (source == null) { exit 404, '数据源不存在或不属于当前租户' }
var sourceConfig = datasourceConnectionConfig('read', datasourceId, null, false)
var accessMode = firstText(sourceConfig, ['accessMode', 'access_mode']).toLowerCase()
if (accessMode == 'report') { accessMode = 'upload' }
if (accessMode != 'upload') { exit 400, '只有数据上报方式的数据源可以使用上报工作区' }

ensureMetadataTables()
var action = firstText(body, ['action']).toLowerCase()
if (blank(action)) { action = 'list' }
var listBatches = () => db.select("""
    select b.batch_id as batchId,
           b.file_id as fileId,
           b.file_name as fileName,
           b.file_url as fileUrl,
           b.table_count as tableCount,
           b.row_count as rowCount,
           b.status,
           b.created_by as createdBy,
           b.created_name as createdName,
           b.created_time as createdTime,
           group_concat(concat(t.table_name_cn, '（', t.table_name, '，', t.row_count, ' 行）')
                        order by t.table_name separator '、') as tableSummary
      from test1.de_data_report_batch_t b
      left join test1.de_data_report_batch_table_t t
        on t.batch_id = b.batch_id and t.tenant_id = b.tenant_id and t.is_del = 0
     where b.tenant_id = #{tenantId}
       and b.datasource_id = #{datasourceId}
       and b.is_del = 0
     group by b.batch_id, b.file_id, b.file_name, b.file_url, b.table_count, b.row_count,
              b.status, b.created_by, b.created_name, b.created_time
     order by b.created_time desc
""")
if (action == 'list') {
    return { storageDatabase: storageSchema, list: listBatches() }
}

/*
 * 数据预览只读取当前租户、当前数据源、已登记的业务表/日志表。
 * 表名和字段名均取自登记元数据白名单，避免动态 SQL 越权访问 test1 中的其他表。
 */
if (action == 'preview') {
    var reportableTables = db.table('db_table_t').where()
        .eq('datasource_id', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).select()
    var previewTableOptions = []
    var selectedTable = null
    var requestedPreviewTableName = firstText(body, ['tableName', 'table_name'])
    for (registeredTable in reportableTables) {
        var registeredBusinessType = firstText(registeredTable, ['businessType', 'business_type'])
        if (registeredBusinessType != '业务表' && registeredBusinessType != '日志表') { continue }
        var registeredTableName = identifier(firstText(registeredTable, ['tableName', 'table_name']), '数据表名')
        previewTableOptions.add({
            tableId: firstText(registeredTable, ['tid']),
            tableName: registeredTableName,
            tableNameCn: firstText(registeredTable, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']),
            businessType: registeredBusinessType
        })
        if (selectedTable == null && (blank(requestedPreviewTableName) || registeredTableName == requestedPreviewTableName)) {
            selectedTable = registeredTable
        }
    }
    if (selectedTable == null) {
        if (previewTableOptions.size() == 0) {
            return { storageDatabase: storageSchema, tables: [], columns: [], list: [], total: 0, pageNo: 1, pageSize: 20, tableExists: false }
        }
        exit 400, '请选择当前数据源已登记的业务表或日志表'
    }
    var previewTableName = identifier(firstText(selectedTable, ['tableName', 'table_name']), '数据表名')
    var previewTableId = firstText(selectedTable, ['tid'])
    var pageNoText = firstText(body, ['pageNo', 'page_no'])
    var pageSizeText = firstText(body, ['pageSize', 'page_size'])
    if (!blank(pageNoText) && !pageNoText.matches('^[0-9]{1,5}$')) { exit 400, '页码格式错误' }
    if (!blank(pageSizeText) && !pageSizeText.matches('^[0-9]{1,3}$')) { exit 400, '每页条数格式错误' }
    var pageNo = blank(pageNoText) ? 1 : (pageNoText::int)
    var pageSize = blank(pageSizeText) ? 20 : (pageSizeText::int)
    if (pageNo < 1) { pageNo = 1 }
    if (pageSize < 1) { pageSize = 20 }
    if (pageSize > 100) { pageSize = 100 }

    var previewColumns = []
    var selectColumns = []
    var previewColumnIndex = 0
    var registeredPreviewColumns = db.table('db_table_column_t').where()
        .eq('table_id', previewTableId).eq('tenant_id', tenantId).eq('is_del', 0)
        .orderBy('ordinal_position', 'asc').select()
    for (registeredColumn in registeredPreviewColumns) {
        var previewColumnName = identifier(firstText(registeredColumn, ['columnName', 'column_name']), '字段名')
        var previewColumnLabel = firstText(registeredColumn, ['columnComment', 'column_comment', 'columnNameCn', 'column_name_cn', 'comment'])
        if (blank(previewColumnLabel)) { previewColumnLabel = previewColumnName }
        var previewResponseName = 'c' + previewColumnIndex
        previewColumns.add({
            name: previewResponseName,
            sourceName: previewColumnName,
            label: previewColumnLabel,
            columnType: firstText(registeredColumn, ['columnType', 'column_type', 'dataType', 'data_type']),
            length: first(registeredColumn, ['length', 'columnLength', 'column_length'])
        })
        selectColumns.add('`' + previewColumnName + '` as ' + previewResponseName)
        previewColumnIndex = previewColumnIndex + 1
    }
    previewColumns.add({ name: 'c' + previewColumnIndex, sourceName: '_report_batch_id', label: '上报批次' })
    selectColumns.add('`_report_batch_id` as c' + previewColumnIndex)
    previewColumnIndex = previewColumnIndex + 1
    previewColumns.add({ name: 'c' + previewColumnIndex, sourceName: '_report_uploaded_at', label: '上报时间' })
    selectColumns.add('`_report_uploaded_at` as c' + previewColumnIndex)

    var previewExistsParams = { schemaName: storageSchema, tableName: previewTableName }
    var previewTableExists = db.selectInt("""
        select count(1) from information_schema.tables
         where table_schema = #{schemaName} and table_name = #{tableName}
    """, previewExistsParams) > 0
    if (!previewTableExists) {
        return { storageDatabase: storageSchema, tables: previewTableOptions, tableName: previewTableName,
                 columns: previewColumns, list: [], total: 0, pageNo: pageNo, pageSize: pageSize, tableExists: false }
    }
    var reportMarkerCount = db.selectInt("""
        select count(1) from information_schema.columns
         where table_schema = #{schemaName} and table_name = #{tableName}
           and column_name = '_report_datasource_id'
    """, previewExistsParams)
    if (reportMarkerCount == 0) {
        return { storageDatabase: storageSchema, tables: previewTableOptions, tableName: previewTableName,
                 columns: previewColumns, list: [], total: 0, pageNo: pageNo, pageSize: pageSize, tableExists: false }
    }
    var previewParams = { datasourceId: datasourceId, tenantId: tenantId, pageSize: pageSize, offset: (pageNo - 1) * pageSize }
    var previewTotal = db.selectInt('select count(1) from `test1`.`' + previewTableName + '` where `_report_datasource_id` = #{datasourceId} and `_report_tenant_id` = #{tenantId}', previewParams)
    var previewRows = db.select('select ' + selectColumns.join(', ') + ' from `test1`.`' + previewTableName + '` where `_report_datasource_id` = #{datasourceId} and `_report_tenant_id` = #{tenantId} order by `_report_uploaded_at` desc, `_report_row_id` desc limit #{pageSize} offset #{offset}', previewParams)
    return { storageDatabase: storageSchema, tables: previewTableOptions, tableName: previewTableName,
             columns: previewColumns, list: previewRows, total: previewTotal, pageNo: pageNo, pageSize: pageSize, tableExists: true }
}

if (action == 'delete') {
    var batchId = firstText(body, ['batchId', 'batch_id'])
    if (blank(batchId)) { exit 400, '上报批次不能为空' }
    var batch = db.selectOne("""
        select batch_id as batchId, file_id as fileId
          from test1.de_data_report_batch_t
         where batch_id = #{batchId} and datasource_id = #{datasourceId}
           and tenant_id = #{tenantId} and is_del = 0
    """)
    if (batch == null) { exit 404, '上报批次不存在或已删除' }
    var batchTables = db.select("""
        select table_name as tableName
          from test1.de_data_report_batch_table_t
         where batch_id = #{batchId} and datasource_id = #{datasourceId}
           and tenant_id = #{tenantId} and is_del = 0
    """)
    for (batchTable in batchTables) {
        var tableName = identifier(firstText(batchTable, ['tableName', 'table_name', 'tablename']), '数据表名')
        var deleteParams = { batchId: batchId, datasourceId: datasourceId, tenantId: tenantId }
        db.update('delete from `test1`.`' + tableName + '` where `_report_batch_id` = #{batchId} and `_report_datasource_id` = #{datasourceId} and `_report_tenant_id` = #{tenantId}', deleteParams)
    }
    db.update("""
        update test1.de_data_report_batch_table_t set is_del = 1
         where batch_id = #{batchId} and datasource_id = #{datasourceId} and tenant_id = #{tenantId}
    """)
    db.update("""
        update test1.de_data_report_batch_t set is_del = 1
         where batch_id = #{batchId} and datasource_id = #{datasourceId} and tenant_id = #{tenantId}
    """)
    return { batchId: batchId, fileId: firstText(batch, ['fileId', 'file_id', 'fileid']), list: listBatches() }
}

if (action != 'import') { exit 400, '不支持的数据上报操作' }
var uploadedTables = body == null ? null : body.tables
if (uploadedTables == null || uploadedTables.size() == 0) { exit 400, '上报文件中没有可导入的数据' }
if (uploadedTables.size() > 100) { exit 400, '单个上报文件最多包含 100 张数据表' }
var fileName = firstText(body, ['fileName', 'file_name'])
if (blank(fileName)) { exit 400, '上报文件名不能为空' }
var totalRows = 0
for (uploaded in uploadedTables) {
    var inputRows = uploaded == null ? null : uploaded.rows
    totalRows = totalRows + (inputRows == null ? 0 : inputRows.size())
}
if (totalRows <= 0) { exit 400, '上报文件没有填写业务数据' }
if (totalRows > 20000) { exit 400, '单次上报最多导入 20000 行，请拆分文件后重试' }

var principal = approvalRuntime.principal()
var batchId = IdUtil.getSnowflakeNextIdStr()
var nowTime = new Date()
var completedTables = []
var importedTableCount = 0
try {
    for (uploaded in uploadedTables) {
        var requestedTableId = firstText(uploaded, ['tableId', 'table_id', 'tid'])
        var requestedTableName = identifier(firstText(uploaded, ['tableName', 'table_name']), '数据表名')
        var tableQuery = db.table('db_table_t').where()
            .eq('datasource_id', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0)
        var registeredTable = blank(requestedTableId)
            ? tableQuery.eq('table_name', requestedTableName).selectOne()
            : tableQuery.eq('tid', requestedTableId).eq('table_name', requestedTableName).selectOne()
        if (registeredTable == null) { exit 400, '数据表 ' + requestedTableName + ' 未在当前数据源中登记' }
        var businessType = firstText(registeredTable, ['businessType', 'business_type'])
        if (businessType != '业务表' && businessType != '日志表') {
            exit 400, '数据表 ' + requestedTableName + ' 不是业务表或日志表，不能上报业务数据'
        }
        var tableId = firstText(registeredTable, ['tid'])
        var registeredColumns = db.table('db_table_column_t').where()
            .eq('table_id', tableId).eq('tenant_id', tenantId).eq('is_del', 0)
            .orderBy('ordinal_position', 'asc').select()
        if (registeredColumns == null || registeredColumns.size() == 0) {
            exit 400, '数据表 ' + requestedTableName + ' 尚未登记字段'
        }
        var columnMap = {}
        var columnNames = []
        for (column in registeredColumns) {
            var columnName = identifier(firstText(column, ['columnName', 'column_name']), '字段名')
            columnMap[columnName] = column
            columnNames.add(columnName)
        }
        var existsParams = { schemaName: storageSchema, tableName: requestedTableName }
        var existedCount = db.selectInt("""
            select count(1) from information_schema.tables
             where table_schema = #{schemaName} and table_name = #{tableName}
        """, existsParams)
        if (existedCount == 0) {
            var definitions = []
            definitions.add('`_report_row_id` varchar(40) not null')
            for (column in registeredColumns) {
                var columnName = identifier(firstText(column, ['columnName', 'column_name']), '字段名')
                definitions.add('`' + columnName + '` ' + mysqlType(column) + ' null')
            }
            definitions.add('`_report_batch_id` varchar(40) not null')
            definitions.add('`_report_datasource_id` varchar(40) not null')
            definitions.add('`_report_tenant_id` varchar(40) not null')
            definitions.add('`_report_file_name` varchar(255) null')
            definitions.add('`_report_uploaded_at` datetime not null')
            definitions.add('primary key (`_report_row_id`)')
            definitions.add('key `idx_report_batch` (`_report_tenant_id`, `_report_datasource_id`, `_report_batch_id`)')
            db.update('create table `test1`.`' + requestedTableName + '` (' + definitions.join(',') + ') engine=InnoDB default charset=utf8mb4')
        } else {
            var markerCount = db.selectInt("""
                select count(1) from information_schema.columns
                 where table_schema = #{schemaName} and table_name = #{tableName}
                   and column_name = '_report_datasource_id'
            """, existsParams)
            if (markerCount == 0) {
                exit 409, 'test1.' + requestedTableName + ' 已存在且不是平台上报表，为避免覆盖数据已停止导入'
            }
            for (column in registeredColumns) {
                var columnName = identifier(firstText(column, ['columnName', 'column_name']), '字段名')
                var columnParams = { schemaName: storageSchema, tableName: requestedTableName, columnName: columnName }
                var columnCount = db.selectInt("""
                    select count(1) from information_schema.columns
                     where table_schema = #{schemaName} and table_name = #{tableName}
                       and column_name = #{columnName}
                """, columnParams)
                if (columnCount == 0) {
                    db.update('alter table `test1`.`' + requestedTableName + '` add column `' + columnName + '` ' + mysqlType(column) + ' null')
                }
            }
        }
        var inputRows = uploaded == null ? null : uploaded.rows
        if (inputRows == null || inputRows.size() == 0) { continue }
        // 业务错误使用 exit 返回，不会进入 catch；因此必须整表预校验通过后才能写入第一行。
        var inputRowNumber = 0
        for (inputRow in inputRows) {
            inputRowNumber = inputRowNumber + 1
            if (!(inputRow instanceof Map)) { exit 400, requestedTableName + ' 第 ' + inputRowNumber + ' 行格式错误' }
            for (inputColumnName in inputRow.keySet()) {
                if (columnMap[inputColumnName] == null) {
                    exit 400, requestedTableName + ' 包含未登记字段：' + inputColumnName
                }
            }
            for (columnName in columnNames) {
                var column = columnMap[columnName]
                var value = inputRow.get(columnName)
                var nullable = first(column, ['nullable', 'isNullable', 'is_nullable'])
                if ((nullable + '') == '0' && blank(value)) {
                    exit 400, requestedTableName + ' 第 ' + inputRowNumber + ' 行必填字段 ' + columnName + ' 不能为空'
                }
            }
        }
        // 从这里开始只剩数据库写入错误，catch 可以按批次清理已写入的部分数据。
        completedTables.add(requestedTableName)
        inputRowNumber = 0
        for (inputRow in inputRows) {
            inputRowNumber = inputRowNumber + 1
            var insertColumns = []
            var placeholders = []
            var params = {}
            var parameterIndex = 0
            for (columnName in columnNames) {
                var column = columnMap[columnName]
                var value = inputRow.get(columnName)
                var parameterName = 'p' + parameterIndex
                insertColumns.add('`' + columnName + '`')
                placeholders.add('#{' + parameterName + '}')
                params[parameterName] = blank(value) ? null : value
                parameterIndex = parameterIndex + 1
            }
            var systemValues = {
                _report_row_id: IdUtil.getSnowflakeNextIdStr(),
                _report_batch_id: batchId,
                _report_datasource_id: datasourceId,
                _report_tenant_id: tenantId,
                _report_file_name: fileName,
                _report_uploaded_at: nowTime
            }
            for (systemName in systemValues.keySet()) {
                var parameterName = 'p' + parameterIndex
                insertColumns.add('`' + systemName + '`')
                placeholders.add('#{' + parameterName + '}')
                params[parameterName] = systemValues[systemName]
                parameterIndex = parameterIndex + 1
            }
            var insertSql = 'insert into `test1`.`' + requestedTableName + '` (' + insertColumns.join(',') + ') values (' + placeholders.join(',') + ')'
            db.update(insertSql, params)
        }
        var batchTableParams = {
            tid: IdUtil.getSnowflakeNextIdStr(), batchId: batchId, datasourceId: datasourceId,
            tenantId: tenantId, tableId: tableId, tableName: requestedTableName,
            tableNameCn: firstText(registeredTable, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']),
            rowCount: inputRows.size(), createdTime: nowTime
        }
        db.update("""
            insert into test1.de_data_report_batch_table_t
              (tid, batch_id, datasource_id, tenant_id, table_id, table_name, table_name_cn, row_count, created_time, is_del)
            values (#{tid}, #{batchId}, #{datasourceId}, #{tenantId}, #{tableId}, #{tableName}, #{tableNameCn}, #{rowCount}, #{createdTime}, 0)
        """, batchTableParams)
        importedTableCount = importedTableCount + 1
    }
    var batchParams = {
        batchId: batchId, datasourceId: datasourceId, tenantId: tenantId,
        fileId: firstText(body, ['fileId', 'file_id']), fileName: fileName,
        fileUrl: firstText(body, ['fileUrl', 'file_url']), tableCount: importedTableCount,
        rowCount: totalRows, status: 'completed',
        createdBy: principal == null ? '' : firstText(principal, ['userId', 'user_id', 'id']),
        createdName: principal == null ? '' : firstText(principal, ['userName', 'user_name', 'name']),
        createdTime: nowTime
    }
    db.update("""
        insert into test1.de_data_report_batch_t
          (batch_id, datasource_id, tenant_id, file_id, file_name, file_url, table_count, row_count,
           status, created_by, created_name, created_time, is_del)
        values (#{batchId}, #{datasourceId}, #{tenantId}, #{fileId}, #{fileName}, #{fileUrl}, #{tableCount}, #{rowCount},
                #{status}, #{createdBy}, #{createdName}, #{createdTime}, 0)
    """, batchParams)
} catch (e) {
    for (tableName in completedTables) {
        var rollbackParams = { batchId: batchId, datasourceId: datasourceId, tenantId: tenantId }
        try {
            db.update('delete from `test1`.`' + tableName + '` where `_report_batch_id` = #{batchId} and `_report_datasource_id` = #{datasourceId} and `_report_tenant_id` = #{tenantId}', rollbackParams)
        } catch (ignored) { }
    }
    try {
        db.update("delete from test1.de_data_report_batch_table_t where batch_id = #{batchId} and tenant_id = #{tenantId}")
    } catch (ignored) { }
    throw e
}
return { batchId: batchId, storageDatabase: storageSchema, list: listBatches() }
