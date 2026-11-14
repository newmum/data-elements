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
  "description": "按 action 识别文件区域、查询登记清单、分页记录或回执，以及保存文件与登记表的规范。按认证租户及逐 ID 归属校验，专用列表分页和保存应用数据源菜单的 ALL/ORG/OWNER 范围。保存需传入清单版本并在事务内更新登记快照和清单，归属不符或版本已变化时拒绝。"
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableWrite
import metadataAsset
import jsons
import systemConfig
import '@/common/configData' as configData
import '@post:/dst/database/metadata/importSnapshot' as importSnapshot
import cn.hutool.json.JSONUtil
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
import io.minio.MinioClient
import io.minio.GetObjectArgs
import okhttp3.OkHttpClient
import java.time.Duration
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.DataFormatter
import java.util.Map
import java.util.List
import java.lang.StringBuilder
import java.lang.Object
import java.lang.Long
import cn.hutool.core.text.csv.CsvBaseReader
import cn.hutool.core.text.csv.CsvReadConfig
import java.util.regex.Pattern
import java.util.TreeMap
import java.util.Date
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneId
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature

// POST /dst/database/metadata/report-registration 按 action 解析文件、查询登记或保存数据规范。
// 查询接收 dbId、分页及筛选参数，返回登记清单、分页记录或回执。解析按文件归属返回识别结果。
// 保存接收清单版本、文件和表定义，按当前租户与数据源菜单范围更新登记快照，并在事务内保留上报回执。
StpUtil.checkLogin()
var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') {
    exit 401, '请重新登录后办理数据上报登记'
}
var actorId = StpUtil.getLoginIdAsString()
var text = value => value == null ? '' : (value + '').trim()
var first = (row, a, b) => row[a] == null ? row[b] : row[a]
var object = value => value instanceof Map ? value : text(value) == '' ? {} : JSONUtil.parseObj(value + '')
var action = text(body.action)
var dbId = text(body.dbId)
var sourcePermission = 'MENU:2081000000000000002'
var source = null
var pool = {}
var saved = { revision: '', files: [], tables: [] }
if (dbId != '') {
    // 租户来自认证上下文，请求只能定位本租户的有效数据源。
    source = db.table('db_datasource_t').where()
        .eq('tid', dbId)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0).selectOne()
    if (source == null) {
        exit 404, '数据源不存在或不属于当前租户'
    }
    pool = object(first(source, 'poolCfg', 'pool_cfg'))
    var accessMode = text(pool.accessMode == null ? pool.dataAccessMode : pool.accessMode)
    if (!['upload', 'report'].contains(accessMode)) {
        exit 400, '仅数据上报方式支持上传登记'
    }
    if (pool.reportRegistration != null) {
        saved = pool.reportRegistration
    }
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
        if (counts[key] == null) { counts[key] = { tableCount: 0, fieldCount: 0, rowCount: 0 } }
        counts[key].tableCount++; counts[key].rowCount = counts[key].rowCount + (table.recordCount == null ? 0 : table.recordCount)
        counts[key].fieldCount = counts[key].fieldCount + (table.fields instanceof List ? table.fields.size() : table.fieldCount == null ? 0 : Math.max(0, table.fieldCount::int))
    }
    for (file in saved.files == null ? [] : saved.files) {
        var last = null; var reportedTables = {}
        for (receipt in receipts) { if (text(receipt.fileId) == text(file.fileId)) { last = receipt; if (receipt.status == 'success') { reportedTables[text(receipt.tableId)] = true } } }
        var count = counts[text(file.fileId)] == null ? { tableCount: 0, fieldCount: 0, rowCount: 0 } : counts[text(file.fileId)]
        rows.add({ fileId: file.fileId, fileName: file.fileName, tableCount: count.tableCount, fieldCount: count.fieldCount, rowCount: count.rowCount,
            reportedTableCount: reportedTables.size(), status: reportedTables.size() > 0 && reportedTables.size() < count.tableCount ? 'partial' : last == null ? 'uploaded' : last.status, reportedAt: last == null ? '' : last.reportedAt,
            reportedTable: last == null ? '' : last.tableName })
    }
    if (action == 'receipts') { rows = receipts }
    var page = body.pageNo == null ? 1 : body.pageNo::int; var size = body.pageSize == null ? 20 : body.pageSize::int
    if (page < 1 || size < 1 || size > 100) { exit 400, '分页参数不正确' }
    var sortField = text(body.sortField); var sortDir = text(body.sortDir)
    if (sortField != '' && !['fileName','tableCount','fieldCount','rowCount','reportedAt'].contains(sortField)) { exit 400, '不支持的排序字段' }
    if (sortDir != '' && !['asc','desc'].contains(sortDir)) { exit 400, '排序方向不正确' }
    if (sortField != '' && sortDir != '') {
        rows = rows.sort((a,b) => {
            var x = a[sortField]; var y = b[sortField]
            if (text(x) == '' || text(y) == '') { return text(x) == text(y) ? text(a.fileId).compareTo(text(b.fileId)) : text(x) == '' ? 1 : -1 }
            var compared = x == y ? text(a.fileId).compareTo(text(b.fileId)) : x < y ? -1 : 1
            return sortDir == 'desc' ? -compared : compared
        })
    }
    var start = Math.min(rows.size(), (page - 1) * size)
    return { list: rows.subList(start, Math.min(rows.size(), start + size)), total: rows.size() }
}
var asTime = value => {
    if (value == null || text(value) == '') { return '' }
    try { if (value instanceof Date) { return Instant.ofEpochMilli(value.getTime()).toString() } } catch (ignored) { }
    var content = text(value).replace(' ', 'T')
    try { return OffsetDateTime.parse(content).toInstant().toString() } catch (ignored) { }
    try { return LocalDateTime.parse(content).atZone(ZoneId.of('Asia/Shanghai')).toInstant().toString() } catch (ignored) { return '' }
}
if (['list','table-page','file-page','report-page'].contains(action)) {
    if (source == null) { exit 400, '请选择数据源' }
    if (action != 'list' && dataScope.visibleDataSourcesFor(sourcePermission, [source]).size() != 1) {
        exit 403, '当前角色无权查看此数据源登记清单'
    }
    var view = publicManifest(saved)
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
        view = { revision: text(saved.revision), files: [], tables: rows }
    }
    if (action == 'list') { return view }
    if (view.tables.size() > 100 || view.files.size() > 100) { exit 400, '上报登记清单最多支持100份文件和100张表' }
    if (text(body.revision) != text(view.revision)) { exit 409, '登记信息已更新，请重新打开工作台' }
    var page = body.pageNo == null ? 1 : body.pageNo::int; var size = body.pageSize == null ? 20 : body.pageSize::int
    if (page < 1 || size < 1 || size > 100) { exit 400, '分页参数不正确' }
    var sortField = text(body.sortField) == '' ? 'updatedAt' : text(body.sortField)
    var sortDir = text(body.sortDir) == '' ? 'desc' : text(body.sortDir)
    if (!['fileName','tableName','tableNameCn','fieldCount','recordCount','updatedAt'].contains(sortField) || !['asc','desc'].contains(sortDir)) { exit 400, '不支持的排序参数' }
    if (action == 'file-page' && !['fileName','fieldCount','recordCount','updatedAt'].contains(sortField)) { exit 400, '不支持的文件排序参数' }
    // 各关联一次批量读取，先逐 ID 核验表和文件归属，再按当前清单分页。
    var ids = view.tables.map(row => text(row.tid)).filter(id => id != '').distinct()
    var snapshots = ids.size() == 0 ? [] : db.table('db_table_t').where().eq('tenant_id', tenantId).eq('datasource_id', dbId).eq('is_del', 0).in('tid', ids).select()
    if (snapshots.size() != ids.size()) { exit 403, '登记清单包含不属于当前数据源的数据表' }
    var fileIds = view.files.map(row => text(row.fileId)).distinct()
    var files = fileIds.size() == 0 ? [] : db.table('file_upload_t').where().eq('tenant_id', tenantId).eq('is_del', 0).in('tid', fileIds).select()
    if (files.size() != fileIds.size()) { exit 403, '登记清单包含不存在或不属于当前租户的文件' }
    var snapshotById = {}; for (row in snapshots) { snapshotById[text(row.tid)] = row }
    var fileById = {}; for (row in files) { fileById[text(row.tid)] = row }
    var reportedIds = {}; var lockedIds = {}
    for (receipt in saved.receipts == null ? [] : saved.receipts) { if (receipt.status == 'success') { reportedIds[text(receipt.tableId)] = true }; if (receipt.status == 'sending') { lockedIds[text(receipt.tableId)] = true } }
    var rows = []; var keys = {}; var search = text(body.search).toLowerCase()
    for (row in view.tables) {
        var key = text(row.key)
        if (key == '' || keys[key] == true) { exit 400, '登记清单标识为空或重复' }; keys[key] = true
        if (row.legacy != true && !fileIds.contains(text(row.fileId))) { exit 403, '数据表未关联当前数据源的登记文件' }
        var snapshot = snapshotById[text(row.tid)]; var file = fileById[text(row.fileId)]
        var time = asTime(row.updatedAt)
        if (time == '' && snapshot != null) { time = asTime(first(snapshot, 'updatedTime', 'updated_time')) }
        if (time == '') { time = asTime(row.createdAt) }
        if (time == '' && file != null) { time = asTime(first(file, 'createdTime', 'created_time')) }
        if (time == '' && snapshot != null) { time = asTime(first(snapshot, 'createdTime', 'created_time')) }
        if (time == '') { for (receipt in saved.receipts == null ? [] : saved.receipts) { if (text(row.tid) != '' && text(receipt.tableId) == text(row.tid) && asTime(receipt.reportedAt) > time) { time = asTime(receipt.reportedAt) } } }
        if (action == 'table-page' && search != '' && !(text(row.fileName) + ' ' + text(row.tableName) + ' ' + text(row.tableNameCn)).toLowerCase().contains(search)) { continue }
        rows.add({ key: key, fileId: text(row.fileId), ready: row.normalized == true && text(row.normalizationVersion) == '1' && text(row.tid) != '', reported: reportedIds[text(row.tid)] == true, locked: lockedIds[text(row.tid)] == true, updatedAt: time, fileName: file == null || text(first(file, 'fileName', 'file_name')) == '' ? text(row.fileName) : text(first(file, 'fileName', 'file_name')), tableName: text(row.tableName), tableNameCn: text(row.tableNameCn), fieldCount: row.fields == null ? 0 : row.fields.size(), recordCount: row.recordCount == null ? 0 : row.recordCount })
    }
    rows = rows.sort((a,b) => {
        var x = a[sortField]; var y = b[sortField]
        if (text(x) == '' || text(y) == '') { return text(x) == text(y) ? text(a.key).compareTo(text(b.key)) : text(x) == '' ? 1 : -1 }
        var compared = sortField == 'updatedAt' ? Instant.parse(text(x)).compareTo(Instant.parse(text(y))) : x == y ? 0 : x < y ? -1 : 1
        return compared == 0 ? text(a.key).compareTo(text(b.key)) : sortDir == 'desc' ? -compared : compared
    })
    if (action == 'file-page') {
        // 按完整文件分组分页，一次响应带回组内所有子项标识。
        var groups = []; var byFile = {}
        for (file in view.files) {
            var id = text(file.fileId)
            if (id == '' || byFile[id] != null) { exit 400, '文件清单标识为空或重复' }
            var storedFile = fileById[id]
            var group = { key: 'file:' + id, fileId: id, fileName: storedFile == null || text(first(storedFile, 'fileName', 'file_name')) == '' ? text(file.fileName) : text(first(storedFile, 'fileName', 'file_name')), tableKeys: [], failedKeys: [], updatedAt: storedFile == null ? '' : asTime(first(storedFile, 'createdTime', 'created_time')), fieldCount: 0, recordCount: 0, readyCount: 0, locked: false, searchText: '' }
            byFile[id] = group; groups.add(group)
        }
        for (row in rows) {
            var id = row.fileId; var group = byFile[id]
            if (group == null && id == '') {
                group = { key: 'legacy', fileId: '', fileName: '历史登记数据表', tableKeys: [], failedKeys: [], updatedAt: '', fieldCount: 0, recordCount: 0, readyCount: 0, locked: true, searchText: '' }
                byFile[id] = group; groups.add(group)
            }
            if (group == null) { exit 403, '数据表未关联当前数据源的文件' }
            group.tableKeys.add(row.key); group.fieldCount += row.fieldCount; group.recordCount += row.recordCount
            if (row.ready || row.reported) { group.readyCount++ }
            if (row.locked) { group.locked = true }
            if (row.updatedAt != '' && (group.updatedAt == '' || Instant.parse(row.updatedAt).compareTo(Instant.parse(group.updatedAt)) > 0)) { group.updatedAt = row.updatedAt }
            group.searchText = group.searchText + ' ' + row.tableName + ' ' + row.tableNameCn
        }
        var failures = view.failedTables == null ? [] : view.failedTables
        if (failures.size() > 100) { exit 400, '待解析区域超过100个' }
        for (failure in failures) {
            var key = text(failure.key); var id = text(failure.fileId)
            if (key == '' || keys[key] == true) { exit 400, '解析清单标识为空或重复' }; keys[key] = true
            var group = byFile[id]
            if (id == '') {
                group = { key: 'failed:' + key, fileId: '', fileName: text(failure.fileName), tableKeys: [], failedKeys: [], updatedAt: '', fieldCount: 0, recordCount: 0, readyCount: 0, locked: false, searchText: '' }; groups.add(group)
            }
            if (group == null) { exit 403, '待解析区域不属于当前数据源文件' }
            group.failedKeys.add(key)
        }
        groups = groups.filter(group => search == '' || (group.fileName + group.searchText).toLowerCase().contains(search))
        groups = groups.sort((a,b) => {
            var field = sortField == 'fileName' ? 'fileName' : sortField == 'fieldCount' ? 'fieldCount' : sortField == 'recordCount' ? 'recordCount' : 'updatedAt'
            var x = a[field]; var y = b[field]
            if (text(x) == '' || text(y) == '') { return text(x) == text(y) ? text(a.key).compareTo(text(b.key)) : text(x) == '' ? 1 : -1 }
            var compared = field == 'updatedAt' ? Instant.parse(text(x)).compareTo(Instant.parse(text(y))) : x == y ? 0 : x < y ? -1 : 1
            return compared == 0 ? text(a.key).compareTo(text(b.key)) : sortDir == 'desc' ? -compared : compared
        })
        // The workbench reads its bounded file tree once: 100 files, 100 failed regions and one legacy group.
        if (body.all == true && groups.size() > 201) { exit 400, '文件及待修复分组超过上限，请先整理登记清单' }
        var offset = body.all == true ? 0 : Math.min(groups.size(), (page - 1) * size)
        var end = body.all == true ? groups.size() : Math.min(groups.size(), offset + size)
        return { revision: text(view.revision), total: groups.size(), list: groups.subList(offset, end).map(group => { key: group.key, fileId: group.fileId, fileName: group.fileName, tableKeys: group.tableKeys, failedKeys: group.failedKeys, updatedAt: group.updatedAt, fieldCount: group.fieldCount, recordCount: group.recordCount, readyCount: group.readyCount, locked: group.locked }) }
    }
    if (action == 'report-page') {
        var filter = text(body.reportStatus) == '' ? 'all' : text(body.reportStatus)
        if (!['all','pending','reported'].contains(filter)) { exit 400, '上报状态不正确' }
        rows = rows.filter(row => (row.ready || row.reported) && (search == '' || (row.fileName + ' ' + row.tableName + ' ' + row.tableNameCn).toLowerCase().contains(search)))
        var reported = rows.filter(row => row.reported).size()
        var summary = { all: rows.size(), reported: reported, pending: rows.size() - reported }
        rows = rows.filter(row => filter == 'all' || (filter == 'reported' ? row.reported : !row.reported))
        var offset = Math.min(rows.size(), (page - 1) * size)
        return { revision: text(view.revision), total: rows.size(), summary: summary, list: rows.subList(offset, Math.min(rows.size(), offset + size)).map(row => { key: row.key, updatedAt: row.updatedAt, fileName: row.fileName }) }
    }
    var start = Math.min(rows.size(), (page - 1) * size)
    return { revision: text(view.revision), total: rows.size(), list: rows.subList(start, Math.min(rows.size(), start + size)).map(row => { key: row.key, updatedAt: row.updatedAt }) }
}

// 识别只返回表头和区域元数据，原始记录保留在须认证的文件存储中。
var positions = (start, end) => { var result = []; var cursor = start; while (cursor < end) { result.add(cursor); cursor++ }; return result }
var readUploadBytes = stream => {
    try {
        // Bound the actual object too; database metadata is not a memory limit.
        var bytes = stream.readNBytes(20971521)
        if (bytes.length > 20971520) { throw new IllegalArgumentException('实际文件超过 20 MB，请拆分后上传') }
        return bytes
    } finally { stream.close() }
}
var csvRows = bytes => {
    var content = ''
    try { content = Charset.forName('UTF-8').newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)) + '' }
    catch (e) { content = new String(bytes, Charset.forName('GB18030')) }
    content = content.replace('﻿', '')
    var delimiters = ['\t', ',', ';', '|']; var counts = [0, 0, 0, 0]; var headerQuoted = false; var headerMeaningful = false; var cursor = 0
    while (cursor < content.length()) {
        var character = content.substring(cursor, cursor + 1)
        if (character == '"') {
            if (headerQuoted && cursor + 1 < content.length() && content.substring(cursor + 1, cursor + 2) == '"') { cursor++; headerMeaningful = true }
            else { headerQuoted = !headerQuoted }
        } else if (!headerQuoted) {
            if (character == '\r' || character == '\n') {
                if (headerMeaningful) { break }
                counts = [0, 0, 0, 0]
            } else {
                var index = delimiters.indexOf(character)
                if (index >= 0) { counts[index] = counts[index] + 1 }
                else if (character.trim() != '') { headerMeaningful = true }
            }
        } else if (character.trim() != '') { headerMeaningful = true }
        cursor++
    }
    var delimiter = ','; var highest = 0
    for (index in positions(0, delimiters.size())) { if (counts[index] > highest) { highest = counts[index]; delimiter = delimiters[index] } }
    // Native scanning avoids millions of interpreted per-character operations.
    // Validate quoted tokens first: Hutool itself deliberately accepts unfinished quotes.
    var quotedTokens = Pattern.compile('(^|' + Pattern.quote(delimiter) + '|\\r|\\n)"(?:[^"]++|"")*+"')
    if (quotedTokens.matcher(content).replaceAll('$1').contains('"')) { throw new IllegalArgumentException('CSV / TXT 引号未闭合或位置不正确') }
    var config = new CsvReadConfig()
    config.setFieldSeparator(delimiter.charAt(0)); config.setContainsHeader(false); config.setSkipEmptyRows(false)
    config.setErrorOnDifferentFieldCount(false); config.setTrimField(false); config.disableComment()
    var reader = new CsvBaseReader(config); var rows = []; var cells = 0
    // Stream records so row/cell bounds stop materialization immediately.
    reader.readFromStr(content, record => {
        var row = record.getRawList()
        if (rows.size() >= 20001) { throw new IllegalArgumentException('单个文件最多 20000 行数据，请拆分后上传') }
        if (row.size() > 200) { throw new IllegalArgumentException('单份文本文件最多 200 列，请拆分后上传') }
        if (row.find(value => value.length() > 65535) != null) { throw new IllegalArgumentException('CSV / TXT 单字段最多 65535 个字符') }
        cells = cells + row.size()
        if (cells > 500000) { throw new IllegalArgumentException('单份文本文件最多读取 500000 个单元格，请拆分后上传') }
        rows.add(row)
    })
    return rows
}
// 按浏览器 Object.entries 的顺序识别 JSON 字段，保留数字键的排序语义。
var jsonKeys = record => {
    var indices = new TreeMap(); var remaining = []
    for (key in record.keySet()) {
        if (key.length() <= 10 && key.matches('^(0|[1-9][0-9]*)$') && Long.parseLong(key) < 4294967295L) { indices.put(Long.parseLong(key), key) }
        else { remaining.add(key) }
    }
    var keys = []; keys.addAll(indices.values()); keys.addAll(remaining); return keys
}
var jsonRows = bytes => {
    var content = ''
    try { content = Charset.forName('UTF-8').newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)) + '' }
    catch (error) { throw new IllegalArgumentException('JSON 文件需使用 UTF-8 编码，请转换编码后重新上传') }
    if (content.startsWith('﻿')) { content = content.substring(1) }
    var mapper = new ObjectMapper()
    mapper.enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
    mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
    mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    // Streaming preflight bounds allocations before the record collection is materialized.
    var preflight = mapper.getFactory().createParser(content); var overflow = ''
    try {
        var objects = 0; var tokens = 0; var token = preflight.nextToken()
        while (token != null) {
            tokens++
            if (token == JsonToken.START_OBJECT) { objects++ }
            if (objects > 40002) { overflow = 'JSON 对象数量过多，请简化嵌套结构或拆分文件后上传'; break }
            if (tokens > 2000000) { overflow = 'JSON 内容过多，请拆分文件后上传'; break }
            token = preflight.nextToken()
        }
    } catch (error) { throw new IllegalArgumentException('JSON 格式不正确，请检查引号、逗号和括号后重新上传') }
    finally { preflight.close() }
    if (overflow != '') { throw new IllegalArgumentException(overflow) }
    var parsed = null
    try { parsed = mapper.readValue(content, Object) }
    catch (error) { throw new IllegalArgumentException('JSON 格式不正确，请检查引号、逗号和括号后重新上传') }
    var records = null
    if (parsed instanceof List) { records = parsed }
    else if (parsed instanceof Map) {
        var containers = ['data', 'rows', 'list'].filter(key => parsed[key] instanceof List)
        if (containers.size() > 1) { throw new IllegalArgumentException('JSON 包含多个记录数组，请只保留 data、rows 或 list 中的一个') }
        records = containers.size() > 0 ? parsed[containers[0]] : [parsed]
    }
    if (!(records instanceof List) || records.size() == 0) { throw new IllegalArgumentException('JSON 需包含至少一条对象记录，支持对象数组或 data、rows、list 记录数组') }
    if (records.size() > 20000) { throw new IllegalArgumentException('单份 JSON 文件最多 20000 条数据，请拆分后上传') }
    var headers = []; var known = {}; var rowIndex = 0
    for (record in records) {
        rowIndex++
        if (!(record instanceof Map)) { throw new IllegalArgumentException('JSON 第 ' + rowIndex + ' 条记录需为对象') }
        for (key in jsonKeys(record)) {
            if (text(key) == '') { throw new IllegalArgumentException('JSON 第 ' + rowIndex + ' 条记录包含空字段名，请补充后上传') }
            var value = record[key]
            if (value instanceof Map || value instanceof List) { throw new IllegalArgumentException('JSON 第 ' + rowIndex + ' 条记录的「' + key + '」包含嵌套结构，请展开为普通字段后上传') }
            if (!known.containsKey(key)) { known[key] = true; headers.add(key) }
            if (headers.size() > 200) { throw new IllegalArgumentException('单份 JSON 文件最多 200 列，请拆分后上传') }
        }
    }
    if (headers.size() == 0) { throw new IllegalArgumentException('JSON 记录没有字段，请补充字段名和数据后上传') }
    if ((records.size() + 1) * headers.size() > 500000) { throw new IllegalArgumentException('单份文本文件最多读取 500000 个单元格，请拆分后上传') }
    var matrix = [headers]
    for (record in records) {
        var row = []
        for (key in headers) {
            var value = record[key] == null ? '' : record[key] + ''
            if (value.length() > 65535) { throw new IllegalArgumentException('单个字段内容不能超过 65535 个字符') }
            row.add(value)
        }
        matrix.add(row)
    }
    return matrix
}
var regions = (matrix, sheetName, fileName) => {
    var result = []; var start = -1; var blocks = []
    for (r in positions(0, matrix.size() + 1)) {
        var nonempty = fileName.toLowerCase().endsWith('.json') && r < matrix.size()
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
            var recordCount = matrix.subList(headerRow + 1, block.end + 1).filter(row => row.filter(value => text(value) != '').size() > 0).size()
            if (recordCount == 0) { problem = '只识别到表头，没有有效数据行' }
            var stem = fileName.replaceAll('(?i)\\.(xlsx|xls|csv|txt|json)$', '')
            var label = sheetName == stem ? stem : stem + ' / ' + sheetName
            var name = (sheetName.matches('^[A-Za-z][A-Za-z0-9_]{0,49}$') ? sheetName : stem.matches('^[A-Za-z][A-Za-z0-9_]{0,49}$') ? stem : 'report_' + IdUtil.fastSimpleUUID().substring(0, 10))
            if (result.size() > 0) { name = name + '_' + (result.size() + 1); label = label + '（' + (result.size() + 1) + '）' }
            result.add({ key: IdUtil.fastSimpleUUID(), tableName: name, tableNameCn: label, sheetName: sheetName,
                fieldCount: fields.size(), recordCount: recordCount, fields: fields, headers: headers,
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
    var storageHttpClient = new OkHttpClient().newBuilder().connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(15)).callTimeout(Duration.ofSeconds(20)).build()
    var minioClient = MinioClient.builder().endpoint(configuration.minio_url).credentials(configuration.minio_user, systemConfig.legacyMinioPassword()).httpClient(storageHttpClient).build()
    var output = []
    for (file in files) {
        var id = text(file.tid); var fileName = text(first(file, 'fileName', 'file_name')); var tables = []
        try {
            var size = first(file, 'fileSize', 'file_size')
            if (size == null || size > 20971520 || size < 1) { throw new IllegalArgumentException('单份文件大小须在 1 字节至 20 MB 之间') }
            if (!fileName.toLowerCase().matches('.*\\.(csv|xlsx|xls|txt|json)$')) { throw new IllegalArgumentException('仅支持 CSV、TXT、JSON、XLSX 和 XLS 文件') }
            var path = text(first(file, 'filePath', 'file_path')); var split = path.indexOf('/')
            var bytes = null
            try {
                var stream = minioClient.getObject(GetObjectArgs.builder().bucket(path.substring(0, split)).object(path.substring(split + 1)).build())
                bytes = readUploadBytes(stream)
            } catch (e) {
                var message = text(e.message)
                if (message.contains('实际文件超过')) { throw e }
                throw new IllegalArgumentException(message.toLowerCase().matches('.*(timeout|timed out).*') ? '读取原文件超时，请点击“设置解析”重试' : '原文件读取失败，请点击“设置解析”重试')
            }
            if (fileName.toLowerCase().endsWith('.json')) { tables = regions(jsonRows(bytes), fileName.replaceAll('(?i)\\.json$', ''), fileName) }
            else if (fileName.toLowerCase().matches('.*\\.(csv|txt)$')) { tables = regions(csvRows(bytes), fileName.replaceAll('(?i)\\.(csv|txt)$', ''), fileName) }
            else {
                var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))
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
        } catch (e) {
            var problem = text(e.message).replaceFirst('^(?:[\\w$]+\\.)*[\\w$]*(?:Exception|Error):\\s*', '')
            tables = [{ key: IdUtil.fastSimpleUUID(), tableName: '', tableNameCn: fileName, fieldCount: 0, recordCount: 0, status: 'failed', error: problem == '' ? '无法识别为带表头的数据表' : problem }]
        }
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
if (action != 'save' || source == null) {
    exit 400, '操作无效或尚未保存数据源'
}
// 在读取文件与表的登记关系前先应用菜单数据范围和清单版本。
if (dataScope.visibleDataSourcesFor(sourcePermission, [source]).size() != 1) {
    exit 403, '当前角色无权保存此数据源登记信息'
}
if (text(body.revision) != text(saved.revision)) {
    exit 409, '上传清单已被更新，请重新打开数据源后再保存'
}
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
var savedByKey = {}; var savedById = {}; for (old in saved.tables == null ? [] : saved.tables) { savedByKey[text(old.key)] = old; if (text(old.tid) != '') { savedById[text(old.tid)] = old } }
// 只有服务端确认过且内容未变的规范可以保留通过状态。
var fieldTimeRoles = field => field.timeRoles == null ? (field.isTimestamp == true ? ['timestamp'] : []) : field.timeRoles
var schemaSignature = table => {
    var fields = []
    for (field in table.fields == null ? [] : table.fields) { fields.add({ name: text(field.columnName), comment: text(field.columnComment), type: text(field.columnType), length: field.columnLength, nullable: field.nullable, primaryKey: field.primaryKey, timestamp: field.isTimestamp, timeRoles: fieldTimeRoles(field), standard: text(field.standardField), dictionaryRole: text(field.dictionaryRole), timeFormat: text(field.timeFormat), timezone: text(field.timezone) }) }
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
// 登记可继续编辑，成功回执保留当次投递的规范快照。
for (receipt in saved.receipts == null ? [] : saved.receipts) {
    var previous = savedById[text(receipt.tableId)]
    var submitted = submittedById[text(receipt.tableId)]
    if (text(receipt.status) == 'success' && previous != null) {
        if (submitted == null || removedIds.contains(text(receipt.tableId))) { exit 409, '已上报的数据表保留历史记录，不能移除' }
        if (receipt.registrationSnapshot == null) { receipt.registrationSnapshot = JSONUtil.parseObj(jsons.toJSONStr(previous)) }
    }
    if (text(receipt.status) == 'sending' && previous != null && (submitted == null || (previous.normalized == true && submitted.normalized == false) || schemaSignature(previous) != schemaSignature(submitted))) { exit 409, '该表正在上报，请处理完成后再保存规范' }
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
        var seenFields = {}; var timestamps = 0; var codeFields = 0; var labelFields = 0; var assignedTimeRoles = {}
        for (field in table.fields) {
            var fieldName = text(field.columnName); var fieldType = text(field.columnType).toLowerCase()
            if (!fieldName.matches('^[A-Za-z][A-Za-z0-9_]{0,63}$') || seenFields[fieldName.toLowerCase()] == true || !text(field.columnComment).matches('.*[\u4e00-\u9fff].*')) { exit 400, '字段英文名无效、重复或未填写中文名' }
            seenFields[fieldName.toLowerCase()] = true
            if (!['varchar','char','text','int','bigint','decimal','float','double','date','datetime','timestamp','boolean'].contains(fieldType)) { exit 400, '字段数据类型不受支持' }
            if (!['','LXDH','SFZH','DATE','DATETIME'].contains(text(field.standardField))) { exit 400, '字段统一格式无效' }
            var roles = fieldTimeRoles(field)
            if (!(roles instanceof List) || roles.size() > 3 || roles.distinct().size() != roles.size()) { exit 400, '字段时间属性无效或重复' }
            for (role in roles) {
                if (!['timestamp','business','occurrence'].contains(text(role)) || assignedTimeRoles[text(role)] == true) { exit 400, '每种时间属性只能指定一个有效字段' }
                assignedTimeRoles[text(role)] = true
                if (!['DATE','DATETIME'].contains(text(field.standardField)) && (!['date','datetime','timestamp'].contains(fieldType) || ['LXDH','SFZH'].contains(text(field.standardField)))) { exit 400, '时间属性须选择日期时间字段或日期时间统一格式' }
            }
            field.timeRoles = roles
            field.isTimestamp = roles.contains('timestamp')
            if (!['','iso','ymd','dmy','mdy','unix-s','unix-ms'].contains(text(field.timeFormat)) || !['','Asia/Shanghai','Asia/Tokyo','UTC','source'].contains(text(field.timezone))) { exit 400, '日期时间来源格式或时区无效' }
            if (field.isTimestamp == true) {
                timestamps++
                if (!['date','datetime','timestamp'].contains(fieldType) && !['DATE','DATETIME'].contains(text(field.standardField))) { exit 400, '时间戳字段须选择日期时间类型或统一格式' }
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
    // 审计时间由服务端赋值，沿用已有记录的创建时间。
    var now = Instant.now().toString()
    table.createdAt = previous == null ? now : text(previous.createdAt)
    table.updatedAt = previous == null || approving || schemaSignature(previous) != schemaSignature(table) ? now : text(previous.updatedAt)
    // 已保存的字段快照仍是权威记录；编辑清单名称不能用旧解析结果重置后续治理配置。
    if ((modernWorkflow ? approving : (id == '' || body.configureFields == true)) && table.legacy != true) { snapshot.add({ tableName: name, tableComment: comment.matches('.*[\u4e00-\u9fff].*') ? comment : '数据表 ' + comment, tableType: '数据表', businessType: modernWorkflow ? table.businessType : '业务表', businessTypeReason: '数据文件上传登记', annotated: true, recordCount: table.recordCount }) }
    fieldMap[name] = table.fields
}
for (id in removedIds) {
    if (existingMap[text(id)] == null) {
        exit 403, '待删除表不存在或不属于当前数据源'
    }
}
var updatesTable = table => !modernWorkflow || normalizedKeys.contains(text(table.key))
var changedIds = tables.filter(table => {
    var id = text(table.tid)
    return updatesTable(table) && id != ''
        && text(table.tableName) != text(first(existingMap[id], 'tableName', 'table_name'))
}).map(table => table.tid)
changedIds.addAll(removedIds)
if (changedIds.size() > 0) {
    // 已逐项确认表归属，其来源或目标关联一次批量查询即可保护现有任务。
    var task = tableRead.from('data_access_agg_task_t', 'task', tenantId)
    task.where(tableRead.equal(tableRead.column('task', 'is_del', 'NUMBER'), 0))
    task.where(tableRead.any([
        tableRead.in(tableRead.column('task', 'source_table_id', 'TEXT'), changedIds),
        tableRead.in(tableRead.column('task', 'target_table_id', 'TEXT'), changedIds)
    ]))
    var counts = task.grouped({ used: tableRead.count() }, [])
    if (counts[0].used > 0) {
        exit 409, '已关联接入任务的数据表不能在上传清单中重命名或删除'
    }
}
var originalBody = body
var result = db.transaction(() => {
    // 上报可在清单 revision 不变时新增回执。锁内比对完整配置，防止覆盖并发回执或越过原归属范围。
    var locked = tableWrite.lockRow('db_datasource_t', tenantId, 'tid', dbId,
        ['pool_cfg', 'org_id', 'created_by'], { is_del: 0 })
    if (locked == null
        || text(locked.pool_cfg) != text(first(source, 'poolCfg', 'pool_cfg'))
        || text(locked.org_id) != text(first(source, 'orgId', 'org_id'))
        || text(locked.created_by) != text(first(source, 'createdBy', 'created_by'))) {
        throw new IllegalStateException('数据源已被更新，请重新加载后保存')
    }
    for (table in tables) {
        if (text(table.tid) != '' && updatesTable(table)) {
            db.table('db_table_t').where()
                .eq('tid', table.tid)
                .eq('tenant_id', tenantId)
                .eq('datasource_id', dbId)
                .update({
                    table_name: table.tableName,
                    table_name_cn: table.tableNameCn,
                    table_comment: table.tableNameCn,
                    record_count: table.recordCount
                })
        }
    }
    // 快照变更与登记清单在同一事务内写入，内部调用结束后恢复外层请求体。
    var imported = { rows: [] }
    if (snapshot.size() > 0) {
        try {
            body = { dbId: dbId, dataSourceType: 'mysql', tables: snapshot, fieldMap: fieldMap, batchIndex: true }
            imported = importSnapshot()
        } finally {
            body = originalBody
        }
    }
    if (removedIds.size() > 0) {
        db.table('db_table_column_t').where()
            .eq('tenant_id', tenantId)
            .in('table_id', removedIds).update({ is_del: 1 })
        db.table('db_table_t').where()
            .eq('tenant_id', tenantId)
            .eq('datasource_id', dbId)
            .in('tid', removedIds).update({ is_del: 1 })
    }
    var importedByName = {}
    for (table in imported.rows) {
        importedByName[text(table.tableName)] = table
    }
    var recorded = []
    for (table in tables) {
        if (importedByName[text(table.tableName)] != null) {
            table.tid = importedByName[text(table.tableName)].tid
        }
        recorded.add(table)
    }
    var failedTables = []
    for (row in originalBody.failedTables == null ? [] : originalBody.failedTables) {
        var error = text(row.error)
        failedTables.add({
            key: text(row.key), fileId: text(row.fileId), fileName: text(row.fileName), sheetName: text(row.sheetName),
            tableName: '', tableNameCn: text(row.tableNameCn), fieldCount: 0, fields: [], recordCount: 0,
            status: 'failed', error: error.substring(0, Math.min(500, error.length()))
        })
    }
    var manifest = {
        revision: IdUtil.fastSimpleUUID(), files: files, tables: recorded, failedTables: failedTables,
        receipts: saved.receipts == null ? [] : saved.receipts
    }
    pool.reportRegistration = manifest
    db.table('db_datasource_t').where()
        .eq('tid', dbId)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0).update({ pool_cfg: jsons.toJSONStr(pool) })
    metadataAsset.refreshRegistrationStatus(dbId, false)
    return manifest
})
return publicManifest(result)
