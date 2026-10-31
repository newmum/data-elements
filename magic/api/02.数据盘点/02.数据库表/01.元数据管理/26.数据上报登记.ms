{
  "properties": {},
  "id": "report_registration_files_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "26.数据上报登记",
  "path": "/report-registration",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "数据上报登记：批量识别 CSV、Excel 的工作表及连续数据区域，保存实际文件与登记表的关联；按可信租户和逐 ID 归属校验，不调用数据源菜单范围过滤。"
}
================================
import tenantRuntime
import metadataAsset
import jsons
import systemConfig
import '@/common/configData' as configData
import '@post:/dst/database/metadata/importSnapshot' as importSnapshot
import cn.hutool.json.JSONUtil
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
import com.linewell.dataelement.utils.MinioUtils
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.DataFormatter
import java.util.Map
import java.util.List
import java.lang.StringBuilder

var tenantId = tenantRuntime.id()
StpUtil.checkLogin()
var actorId = StpUtil.getLoginIdAsString()
var text = value => value == null ? '' : (value + '').trim()
var first = (row, a, b) => row[a] == null ? row[b] : row[a]
var object = value => value instanceof Map ? value : text(value) == '' ? {} : JSONUtil.parseObj(value + '')
var action = text(body.action)
var dbId = text(body.dbId)
var source = null
var pool = {}
var saved = { revision: '', files: [], tables: [] }
if (dbId != '') {
    source = db.table('db_datasource_t').where().eq('tid', dbId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (source == null) { exit 404, '数据源不存在或不属于当前租户' }
    pool = object(first(source, 'poolCfg', 'pool_cfg'))
    if (!['upload', 'report'].contains(text(pool.accessMode == null ? pool.dataAccessMode : pool.accessMode))) { exit 400, '仅数据上报方式支持上传登记' }
    if (pool.reportRegistration != null) { saved = pool.reportRegistration }
}
var publicManifest = value => {
    var latest = {}
    for (row in value.receipts == null ? [] : value.receipts) { var old = latest[text(row.tableId)]; if (old == null || old.status != 'success' || row.status == 'success') { latest[text(row.tableId)] = row } }
    return { revision: value.revision, files: value.files, tables: value.tables, failedTables: value.failedTables == null ? [] : value.failedTables, receipts: latest.values() }
}
if (['history','receipts'].contains(action)) {
    if (source == null) { exit 400, '请选择数据源' }
    var rows = []; var counts = {}; var receipts = saved.receipts == null ? [] : saved.receipts
    for (table in saved.tables == null ? [] : saved.tables) {
        var key = text(table.fileId)
        if (counts[key] == null) { counts[key] = { tableCount: 0, rowCount: 0 } }
        counts[key].tableCount++; counts[key].rowCount = counts[key].rowCount + (table.recordCount == null ? 0 : table.recordCount)
    }
    for (file in saved.files == null ? [] : saved.files) {
        var last = null; var reportedTables = {}
        for (receipt in receipts) { if (text(receipt.fileId) == text(file.fileId)) { last = receipt; if (receipt.status == 'success') { reportedTables[text(receipt.tableId)] = true } } }
        var count = counts[text(file.fileId)] == null ? { tableCount: 0, rowCount: 0 } : counts[text(file.fileId)]
        rows.add({ fileId: file.fileId, fileName: file.fileName, tableCount: count.tableCount, rowCount: count.rowCount,
            reportedTableCount: reportedTables.size(), status: reportedTables.size() > 0 && reportedTables.size() < count.tableCount ? 'partial' : last == null ? 'uploaded' : last.status, reportedAt: last == null ? '' : last.reportedAt,
            reportedTable: last == null ? '' : last.tableName })
    }
    if (action == 'receipts') { rows = receipts }
    var page = body.pageNo == null ? 1 : body.pageNo::int; var size = body.pageSize == null ? 20 : body.pageSize::int
    if (page < 1 || size < 1 || size > 100) { exit 400, '分页参数不正确' }
    var sortField = text(body.sortField); var sortDir = text(body.sortDir)
    if (sortField != '' && !['fileName','tableCount','rowCount','reportedAt'].contains(sortField)) { exit 400, '不支持的排序字段' }
    if (sortDir != '' && !['asc','desc'].contains(sortDir)) { exit 400, '排序方向不正确' }
    if (sortField != '' && sortDir != '') {
        rows.sort((a,b) => {
            var x = a[sortField]; var y = b[sortField]
            var compared = x == y ? text(a.fileId).compareTo(text(b.fileId)) : x < y ? -1 : 1
            return sortDir == 'desc' ? -compared : compared
        })
    }
    var start = Math.min(rows.size(), (page - 1) * size)
    return { list: rows.subList(start, Math.min(rows.size(), start + size)), total: rows.size() }
}
if (action == 'list') {
    if (source == null) { exit 400, '请选择数据源' }
    if (saved.tables == null || (saved.tables.size() == 0 && text(saved.revision) == '')) {
        var legacy = db.table('db_table_t').where().eq('tenant_id', tenantId).eq('datasource_id', dbId).eq('is_del', 0).select()
        if (legacy.size() > 100) { exit 400, '上报登记清单最多支持 100 张表' }
        var ids = legacy.map(table => table.tid)
        var fields = ids.size() == 0 ? [] : db.table('db_table_column_t').where().eq('tenant_id', tenantId).eq('is_del', 0).in('table_id', ids).orderBy('ordinal_position', 'asc').select()
        var byTable = {}
        for (field in fields) {
            var key = text(first(field, 'tableId', 'table_id'))
            if (byTable[key] == null) { byTable[key] = [] }
            byTable[key].add({ columnName: first(field, 'columnName', 'column_name'), columnComment: first(field, 'columnComment', 'column_comment'), columnType: first(field, 'dataType', 'data_type') == null ? 'varchar' : first(field, 'dataType', 'data_type'), columnLength: 4000, primaryKey: first(field, 'primaryKey', 'primary_key') == 1, nullable: true, isTimestamp: false })
        }
        var rows = []
        for (table in legacy) {
            var columns = byTable[text(table.tid)] == null ? [] : byTable[text(table.tid)]
            rows.add({ tid: table.tid, key: table.tid, tableName: first(table, 'tableName', 'table_name'), tableNameCn: first(table, 'tableNameCn', 'table_name_cn'), fields: columns, fieldCount: columns.size(), recordCount: first(table, 'recordCount', 'record_count'), legacy: true, fileId: '', fileName: '已登记数据表', status: 'success', error: '' })
        }
        return { revision: text(saved.revision), files: [], tables: rows }
    }
    return publicManifest(saved)
}

// No row values leave the parser: the original file remains in authenticated file storage.
var positions = (start, end) => { var result = []; var cursor = start; while (cursor < end) { result.add(cursor); cursor++ }; return result }
var csvRows = bytes => {
    var content = ''
    try { content = Charset.forName('UTF-8').newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)) + '' }
    catch (e) { content = new String(bytes, Charset.forName('GB18030')) }
    content = content.replace('﻿', '')
    var firstLine = content.split('\n', 2)[0]
    var delimiter = firstLine.contains('\t') ? '\t' : firstLine.contains(';') && !firstLine.contains(',') ? ';' : ','
    var rows = []; var row = []; var value = new StringBuilder(); var quoted = false; var i = 0
    while (i < content.length()) {
        var ch = content.substring(i, i + 1)
        if (ch == '"') {
            if (quoted && i + 1 < content.length() && content.substring(i + 1, i + 2) == '"') { value.append('"'); i++ }
            else if (quoted || value.length() == 0) { quoted = !quoted }
            else { throw new IllegalArgumentException('CSV 引号位置不正确') }
        } else if (!quoted && ch == delimiter) { row.add(value.toString()); value.setLength(0) }
        else if (!quoted && (ch == '\n' || ch == '\r')) {
            if (ch == '\r' && i + 1 < content.length() && content.substring(i + 1, i + 2) == '\n') { i++ }
            row.add(value.toString()); rows.add(row); row = []; value.setLength(0)
            if (rows.size() > 20001) { throw new IllegalArgumentException('单个文件最多 20000 行数据，请拆分后上传') }
        } else { value.append(ch) }
        if (row.size() > 200 || value.length() > 65535) { throw new IllegalArgumentException('CSV 每行最多 200 个字段，单字段最多 65535 个字符') }
        i++
    }
    if (quoted) { throw new IllegalArgumentException('CSV 引号未闭合') }
    if (value.length() > 0 || row.size() > 0) { row.add(value.toString()); rows.add(row) }
    return rows
}
var regions = (matrix, sheetName, fileName) => {
    var result = []; var start = -1; var blocks = []
    for (r in positions(0, matrix.size() + 1)) {
        var nonempty = false
        if (r < matrix.size()) { for (cell in matrix[r]) { if (text(cell) != '') { nonempty = true; break } } }
        if (nonempty && start < 0) { start = r }
        if (!nonempty && start >= 0) { blocks.add({ start: start, end: r - 1 }); start = -1 }
    }
    for (block in blocks) {
        var width = 0
        for (r in positions(block.start, block.end + 1)) { width = Math.max(width, matrix[r].size()) }
        if (width > 200) { throw new IllegalArgumentException('每张表最多 200 个字段') }
        var left = -1; var spans = []
        for (c in positions(0, width + 1)) {
            var used = false
            if (c < width) { for (r in positions(block.start, block.end + 1)) { if (c < matrix[r].size() && text(matrix[r][c]) != '') { used = true; break } } }
            if (used && left < 0) { left = c }
            if (!used && left >= 0) { spans.add({ left: left, right: c - 1 }); left = -1 }
        }
        for (span in spans) {
            var headerRow = block.start
            // A single title cell above a wider header is a title, rather than a field.
            var populated = matrix[headerRow].filter(v => text(v) != '').size()
            if (populated == 1 && span.right > span.left && block.end > headerRow) { headerRow++ }
            var headers = []; var fields = []; var seen = {}; var fieldNames = {}; var problem = ''
            for (c in positions(span.left, span.right + 1)) {
                var header = c < matrix[headerRow].size() ? text(matrix[headerRow][c]) : ''
                if (header == '') { problem = '表头存在空字段，请补齐表头后重新上传' }
                if (seen[header] != null) { problem = '表头重复：' + header }
                seen[header] = true
                headers.add(header)
                var name = header.matches('^[A-Za-z][A-Za-z0-9_]{0,63}$') ? header : 'field_' + (c - span.left + 1)
                var baseName = name; var suffix = 2
                while (fieldNames[name.toLowerCase()] == true) { name = baseName.substring(0, Math.min(59, baseName.length())) + '_' + suffix; suffix++ }
                fieldNames[name.toLowerCase()] = true
                fields.add({ columnName: name, columnComment: header.matches('.*[\u4e00-\u9fff].*') ? header : '字段 ' + header,
                    columnType: 'varchar', columnLength: 4000, primaryKey: false, nullable: true, isTimestamp: false })
            }
            if (headerRow >= block.end) { problem = '只识别到表头，没有数据行' }
            var stem = fileName.replaceAll('(?i)\\.(xlsx|xls|csv)$', '')
            var label = sheetName == stem ? stem : stem + ' / ' + sheetName
            var name = (sheetName.matches('^[A-Za-z][A-Za-z0-9_]{0,49}$') ? sheetName : stem.matches('^[A-Za-z][A-Za-z0-9_]{0,49}$') ? stem : 'report_' + IdUtil.fastSimpleUUID().substring(0, 10))
            if (result.size() > 0) { name = name + '_' + (result.size() + 1); label = label + '（' + (result.size() + 1) + '）' }
            result.add({ key: IdUtil.fastSimpleUUID(), tableName: name, tableNameCn: label, sheetName: sheetName,
                fieldCount: fields.size(), recordCount: Math.max(0, block.end - headerRow), fields: fields, headers: headers,
                startRow: headerRow + 1, endRow: block.end + 1, startColumn: span.left + 1, endColumn: span.right + 1,
                status: problem == '' ? 'success' : 'failed', error: problem })
        }
    }
    if (result.size() == 0) { result.add({ key: IdUtil.fastSimpleUUID(), tableName: '', tableNameCn: sheetName, sheetName: sheetName, fieldCount: 0, recordCount: 0, status: 'failed', error: '工作表为空，未识别到表头和数据' }) }
    return result
}
if (action == 'parse') {
    var ids = body.fileIds
    if (!(ids instanceof List) || ids.size() < 1 || ids.size() > 20) { exit 400, '每次请选择 1 至 20 份数据文件' }
    var files = db.table('file_upload_t').where().eq('tenant_id', tenantId).eq('created_by', actorId).eq('is_del', 0).in('tid', ids).select()
    if (files.size() != ids.size()) { exit 403, '存在不属于当前租户或当前上传人的文件' }
    var configuration = configData()
    MinioUtils.getMinioClient(configuration.minio_url, configuration.minio_user, systemConfig.legacyMinioPassword())
    var output = []
    for (file in files) {
        var id = text(file.tid); var fileName = text(first(file, 'fileName', 'file_name')); var tables = []
        try {
            var size = first(file, 'fileSize', 'file_size')
            if (size == null || size > 20971520 || size < 1) { throw new IllegalArgumentException('单份文件大小须在 1 字节至 20 MB 之间') }
            if (!fileName.toLowerCase().matches('.*\\.(csv|xlsx|xls)$')) { throw new IllegalArgumentException('仅支持 CSV、XLSX 和 XLS 文件') }
            var path = text(first(file, 'filePath', 'file_path')); var split = path.indexOf('/')
            var buffer = new ByteArrayOutputStream()
            MinioUtils.downloadFile(path.substring(0, split), path.substring(split + 1), buffer)
            if (fileName.toLowerCase().endsWith('.csv')) { tables = regions(csvRows(buffer.toByteArray()), fileName.replaceAll('(?i)\\.csv$', ''), fileName) }
            else {
                var workbook = WorkbookFactory.create(new ByteArrayInputStream(buffer.toByteArray()))
                try {
                    if (workbook.getNumberOfSheets() > 100) { throw new IllegalArgumentException('单份文件最多 100 个工作表') }
                    var formatter = new DataFormatter(); var cells = 0
                    for (s in positions(0, workbook.getNumberOfSheets())) {
                        var sheet = workbook.getSheetAt(s); var matrix = []
                        if (sheet.getLastRowNum() > 20000) { throw new IllegalArgumentException('单份工作表最多 20000 行，请拆分后上传') }
                        for (r in positions(0, sheet.getLastRowNum() + 1)) {
                            var row = sheet.getRow(r); var values = []; var columns = row == null ? 0 : row.getLastCellNum()
                            if (columns > 200) { throw new IllegalArgumentException('每个区域最多 200 列，请清理多余空白格式') }
                            cells = cells + columns
                            if (cells > 500000) { throw new IllegalArgumentException('单份文件最多读取 500000 个单元格') }
                            for (c in positions(0, columns)) { values.add(formatter.formatCellValue(row.getCell(c))) }
                            matrix.add(values)
                        }
                        tables.addAll(regions(matrix, sheet.getSheetName(), fileName))
                    }
                } finally { workbook.close() }
            }
        } catch (e) { tables = [{ key: IdUtil.fastSimpleUUID(), tableName: '', tableNameCn: fileName, fieldCount: 0, recordCount: 0, status: 'failed', error: text(e.message) == '' ? '无法识别为带表头的数据表' : text(e.message) }] }
        var uniqueNames = {}
        for (table in tables) {
            if (table.status != 'success') { continue }
            var baseName = text(table.tableName).substring(0, Math.min(54, text(table.tableName).length()))
            var suffix = 1
            while (uniqueNames[text(table.tableName).toLowerCase()] != null) { suffix++; table.tableName = baseName + '_' + suffix }
            uniqueNames[text(table.tableName).toLowerCase()] = true
        }
        output.add({ fileId: id, fileName: fileName, tables: tables })
    }
    return { files: output }
}
if (action != 'save' || source == null) { exit 400, '操作无效或尚未保存数据源' }
if (text(body.revision) != text(saved.revision)) { exit 409, '上传清单已被更新，请重新打开数据源后再保存' }
var files = body.files
var tables = body.tables
var removedIds = body.removedTableIds == null ? [] : body.removedTableIds
var modernWorkflow = body.workflowVersion == 2
var normalizedKeys = modernWorkflow && body.normalizedTableKeys != null ? body.normalizedTableKeys : []
if (!(normalizedKeys instanceof List) || normalizedKeys.size() > 100) { exit 400, '规范确认每次最多100张表' }
if (!(files instanceof List) || files.size() > 100 || !(tables instanceof List) || tables.size() > 100 || removedIds.size() > 100) { exit 400, '每个数据源最多保留 100 份文件、100 张有效数据表' }
if (body.failedTables != null && (!(body.failedTables instanceof List) || body.failedTables.size() > 100)) { exit 400, '待修复区域超过100个，请减少文件或整理表头后分批上传' }
var fileIds = files.map(file => text(file.fileId)).distinct()
var ownedFiles = fileIds.size() == 0 ? [] : db.table('file_upload_t').where().eq('tenant_id', tenantId).eq('is_del', 0).in('tid', fileIds).select()
if (ownedFiles.size() != fileIds.size()) { exit 403, '文件不存在或不属于当前租户' }
var oldFileIds = saved.files == null ? [] : saved.files.map(file => text(file.fileId))
for (file in ownedFiles) { if (text(first(file, 'createdBy', 'created_by')) != actorId && !oldFileIds.contains(text(file.tid))) { exit 403, '不能关联其他上传人的未登记文件' } }
var existing = db.table('db_table_t').where().eq('tenant_id', tenantId).eq('datasource_id', dbId).eq('is_del', 0).select()
var existingMap = {}; for (table in existing) { existingMap[text(table.tid)] = table }
var savedByKey = {}; for (old in saved.tables == null ? [] : saved.tables) { savedByKey[text(old.key)] = old }
// Only server-confirmed, unchanged schema keeps its approval; client flags never grant approval.
var schemaSignature = table => {
    var fields = []
    for (field in table.fields == null ? [] : table.fields) { fields.add({ name: text(field.columnName), comment: text(field.columnComment), type: text(field.columnType), length: field.columnLength, nullable: field.nullable, primaryKey: field.primaryKey, timestamp: field.isTimestamp, standard: text(field.standardField), dictionaryRole: text(field.dictionaryRole), timeFormat: text(field.timeFormat), timezone: text(field.timezone) }) }
    return jsons.toJSONStr({ tid: text(table.tid), fileId: text(table.fileId), name: text(table.tableName), comment: text(table.tableNameCn), businessType: text(table.businessType), fields: fields,
        sheet: text(table.sheetName), start: table.startRow, end: table.endRow, left: table.startColumn, right: table.endColumn, header: table.headerMode, dataStart: table.dataStartRow, encoding: table.parseEncoding, delimiter: table.parseDelimiter })
}
var nameCounts = {}; var tableKeys = {}; var submittedById = {}
for (candidate in tables) {
    var key = text(candidate.key); var name = text(candidate.tableName).toLowerCase()
    if (modernWorkflow && (key == '' || tableKeys[key] == true)) { exit 400, '数据表清单标识为空或重复' }
    tableKeys[key] = true; nameCounts[name] = nameCounts[name] == null ? 1 : nameCounts[name] + 1
    var candidateId = text(candidate.tid)
    if (candidateId != '') {
        if (submittedById[candidateId] != null) { exit 400, '数据表标识重复' }
        submittedById[candidateId] = candidate
    }
}
for (receipt in saved.receipts == null ? [] : saved.receipts) {
    if (['sending','success'].contains(text(receipt.status))) {
        var previous = (saved.tables == null ? [] : saved.tables).find(row => text(row.tid) == text(receipt.tableId))
        var submitted = submittedById[text(receipt.tableId)]
        if (previous != null && (submitted == null || (previous.normalized == true && submitted.normalized == false) || schemaSignature(previous) != schemaSignature(submitted))) { exit 409, '已上报或正在上报的数据保留原规范，请追加新的数据文件' }
    }
}
for (key in normalizedKeys) { if (tableKeys[text(key)] != true) { exit 403, '待规范表不在当前上传清单中' } }
var names = {}; var fieldMap = {}; var snapshot = []; var totalFields = 0
for (table in tables) {
    var name = text(table.tableName); var id = text(table.tid)
    var approving = modernWorkflow && normalizedKeys.contains(text(table.key))
    if ((!modernWorkflow || approving) && (!name.matches('^[A-Za-z][A-Za-z0-9_]{0,63}$') || nameCounts[name.toLowerCase()] > 1)) { exit 400, '数据表名须以字母开头，只含字母、数字或下划线，且不能重复' }
    names[name.toLowerCase()] = true
    if (id != '' && existingMap[id] == null) { exit 403, '存在不属于当前数据源的表标识' }
    if (removedIds.contains(id)) { exit 400, '同一张表不能同时保存和删除' }
    if (!fileIds.contains(text(table.fileId)) && !(table.legacy == true && id != '' && existingMap[id] != null)) { exit 400, '数据表未关联到本次上传清单中的实际文件' }
    if (!(table.fields instanceof List) || table.fields.size() < 1 || table.fields.size() > 200) { exit 400, '数据表须包含 1 至 200 个字段' }
    totalFields = totalFields + table.fields.size()
    if (totalFields > 5000) { exit 400, '单次最多保存 5000 个字段' }
    var comment = text(table.tableNameCn)
    if (approving) {
        var header = text(table.headerMode) == '' ? 'single' : text(table.headerMode)
        var headerRows = header == 'none' ? 0 : header == 'bilingual' ? 2 : 1
        var dataStart = table.dataStartRow == null ? (table.startRow == null ? 0 : table.startRow) + headerRows : table.dataStartRow
        var left = table.startColumn == null ? 1 : table.startColumn; var right = table.endColumn == null ? table.fields.size() : table.endColumn
        if (!['single','bilingual','none'].contains(header) || text(table.sheetName) == '' || table.startRow == null || table.startRow < 1 || table.endRow == null || dataStart < table.startRow + headerRows || dataStart > table.endRow || left < 1 || right - left + 1 != table.fields.size() || table.recordCount == null || table.recordCount < 1) { exit 400, '请关联原文件并设置有效的表头和数据区域' }
        if (!comment.matches('.*[\u4e00-\u9fff].*') || !['业务表','日志表','字典表','过程表','备份表'].contains(text(table.businessType))) { exit 400, '请填写表中文名并选择表类型' }
        var seenFields = {}; var timestamps = 0; var codeFields = 0; var labelFields = 0
        for (field in table.fields) {
            var fieldName = text(field.columnName); var fieldType = text(field.columnType).toLowerCase()
            if (!fieldName.matches('^[A-Za-z][A-Za-z0-9_]{0,63}$') || seenFields[fieldName.toLowerCase()] == true || !text(field.columnComment).matches('.*[\u4e00-\u9fff].*')) { exit 400, '字段英文名无效、重复或未填写中文名' }
            seenFields[fieldName.toLowerCase()] = true
            if (!['varchar','char','text','int','bigint','decimal','float','double','date','datetime','timestamp','boolean'].contains(fieldType)) { exit 400, '字段数据类型不受支持' }
            if (!['','DATE','DATETIME'].contains(text(field.standardField))) { exit 400, '字段统一格式无效' }
            if (!['','iso','ymd','dmy','mdy','unix-s','unix-ms'].contains(text(field.timeFormat)) || !['','Asia/Shanghai','Asia/Tokyo','UTC','source'].contains(text(field.timezone))) { exit 400, '日期时间来源格式或时区无效' }
            if (field.isTimestamp == true) {
                timestamps++
                if (!['date','datetime','timestamp'].contains(fieldType) && text(field.standardField) == '') { exit 400, '时间戳字段须选择日期时间类型或统一格式' }
            }
            if (text(field.dictionaryRole) == 'code') { codeFields++ }
            if (text(field.dictionaryRole) == 'label') { labelFields++ }
        }
        if (timestamps > 1) { exit 400, '每张表最多指定一个时间戳字段' }
        if (table.businessType == '字典表' && (codeFields != 1 || labelFields != 1)) { exit 400, '字典表须分别指定一个代码列和名称列' }
        if (table.status == 'failed' || table.legacy == true || text(table.fileId) == '') { exit 400, '请先解析文件并关联数据区域' }
    }
    var previous = savedByKey[text(table.key)]
    var retained = previous != null && previous.normalized == true && previous.normalizationVersion == 1 && table.normalized != false && schemaSignature(previous) == schemaSignature(table)
    table.normalized = approving || retained
    table.normalizationVersion = table.normalized ? 1 : 0
    // 已保存的字段快照仍是权威记录；编辑清单名称不能用旧解析结果重置后续治理配置。
    if ((modernWorkflow ? approving : (id == '' || body.configureFields == true)) && table.legacy != true) { snapshot.add({ tableName: name, tableComment: comment.matches('.*[\u4e00-\u9fff].*') ? comment : '数据表 ' + comment, tableType: '数据表', businessType: modernWorkflow ? table.businessType : '业务表', businessTypeReason: '数据文件上传登记', annotated: true, recordCount: table.recordCount }) }
    fieldMap[name] = table.fields
}
for (id in removedIds) { if (existingMap[text(id)] == null) { exit 403, '待删除表不存在或不属于当前数据源' } }
var changedIds = tables.filter(table => (!modernWorkflow || normalizedKeys.contains(text(table.key))) && text(table.tid) != '' && text(table.tableName) != text(first(existingMap[text(table.tid)], 'tableName', 'table_name'))).map(table => table.tid)
changedIds.addAll(removedIds)
if (changedIds.size() > 0) {
    var used = db.selectInt('select count(*) from data_access_agg_task_t where tenant_id=#{tenantId} and is_del=0 and (source_table_id in (#{changedIds}) or target_table_id in (#{changedIds}))')
    if (used > 0) { exit 409, '已关联接入任务的数据表不能在上传清单中重命名或删除' }
}
var originalBody = body
var result = db.transaction(() => {
    var locked = db.selectOne('select pool_cfg from db_datasource_t where tid=#{dbId} and tenant_id=#{tenantId} and is_del=0 for update')
    if (text(first(locked, 'poolCfg', 'pool_cfg')) != text(first(source, 'poolCfg', 'pool_cfg'))) { throw new IllegalStateException('数据源已被更新，请重新加载后保存') }
    for (table in tables) {
        if (text(table.tid) != '' && (!modernWorkflow || normalizedKeys.contains(text(table.key)))) { db.table('db_table_t').where().eq('tid', table.tid).eq('tenant_id', tenantId).eq('datasource_id', dbId).update({ table_name: table.tableName, table_name_cn: table.tableNameCn, table_comment: table.tableNameCn, record_count: table.recordCount }) }
    }
    body = { dbId: dbId, dataSourceType: 'mysql', tables: snapshot, fieldMap: fieldMap, batchIndex: true }
    var imported = snapshot.size() == 0 ? { rows: [] } : importSnapshot()
    body = originalBody
    if (removedIds.size() > 0) {
        db.table('db_table_column_t').where().eq('tenant_id', tenantId).in('table_id', removedIds).update({ is_del: 1 })
        db.table('db_table_t').where().eq('tenant_id', tenantId).eq('datasource_id', dbId).in('tid', removedIds).update({ is_del: 1 })
    }
    var importedByName = {}; for (table in imported.rows) { importedByName[text(table.tableName)] = table }
    var recorded = []
    for (table in tables) { var row = table; if (importedByName[text(table.tableName)] != null) { row.tid = importedByName[text(table.tableName)].tid }; recorded.add(row) }
    var failedTables = []
    for (row in originalBody.failedTables == null ? [] : originalBody.failedTables) {
        failedTables.add({ key: text(row.key), fileId: text(row.fileId), fileName: text(row.fileName), sheetName: text(row.sheetName), tableName: '', tableNameCn: text(row.tableNameCn), fieldCount: 0, fields: [], recordCount: 0, status: 'failed', error: text(row.error).substring(0, Math.min(500, text(row.error).length())) })
    }
    var manifest = { revision: IdUtil.fastSimpleUUID(), files: files, tables: recorded, failedTables: failedTables, receipts: saved.receipts == null ? [] : saved.receipts }
    pool.reportRegistration = manifest
    db.table('db_datasource_t').where().eq('tid', dbId).eq('tenant_id', tenantId).eq('is_del', 0).update({ pool_cfg: jsons.toJSONStr(pool) })
    metadataAsset.refreshRegistrationStatus(dbId, false)
    return manifest
})
return publicManifest(result)
