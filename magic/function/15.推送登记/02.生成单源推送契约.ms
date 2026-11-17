{
  "id": "push_registry_schema_prepare_v1",
  "groupId": "push_registry_function_group_v1",
  "name": "02.生成单源推送契约",
  "path": "/prepare",
  "description": "发布、密钥签发和单源节点同步共用的数据表契约构造，返回待保存的历史版本与海通快照。",
  "returnType": "Object",
  "parameters": [
    {"name": "tenantId", "required": true, "dataType": "String", "type": "java.lang.String"},
    {"name": "datasourceId", "required": true, "dataType": "String", "type": "java.lang.String"}
  ],
  "options": []
}
================================
import cn.hutool.core.util.IdUtil
import cn.hutool.crypto.digest.DigestUtil
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.Math
import java.util.LinkedHashMap
import java.util.List
import java.util.Locale
import java.util.Map
import tableRead
import tenantRuntime

// 发布与单源节点同步共用读模型。一次读取有效表、列和既有版本，先计算契约再交给入口决定事务和投递顺序。
if (tenantRuntime.id() != tenantId) { throw new IllegalArgumentException('推送契约查询租户无效') }
var txt = (alias, name) => tableRead.column(alias, name, 'TEXT')
var num = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var active = alias => tableRead.equal(tableRead.coalesce([num(alias, 'is_del'), 0]), 0)
var text = value => value == null ? '' : (value + '').trim()
var mapper = new ObjectMapper()
var object = value => {
    if (value instanceof Map) { return value }
    if (text(value) == '') { return {} }
    try {
        return mapper.readValue(text(value), Map) || {}
    } catch (error) {
        throw new IllegalArgumentException('数据源推送配置不是有效 JSON')
    }
}
var collect = (query, fields, sorts) => {
    var rows = []
    query.forEachOrderedRow(fields, sorts, sorts.map(term => 'asc'), true, row => rows.add(row))
    return rows
}

var sources = tableRead.from('db_datasource_t', 'd', tenantId)
sources.where(tableRead.equal(txt('d', 'tid'), datasourceId))
sources.where(active('d'))
var found = sources.rows({id: txt('d', 'tid'), name: txt('d', 'db_name'), status: num('d', 'asset_status'),
    poolCfg: tableRead.column('d', 'pool_cfg', 'LOB')}, 1)
if (found.size() == 0) { throw new IllegalArgumentException('登记数据源不存在或无权访问') }
var source = found[0]
var status = (source.status || 0)::int
if (status < 0 || status > 2) { throw new IllegalStateException('数据源状态不支持推送') }
var config = object(source.poolCfg)
var modes = [config.accessMode, config.access_mode, config.dataAccessMode, config.data_access_mode]
    .map(item => text(item)).filter(item => item != '')
if (modes.size() == 0 || !['receive', 'push'].contains(modes[0].toLowerCase(Locale.ROOT))) {
    throw new IllegalArgumentException('该数据源不是数据推送方式')
}
var push = object(config.pushDelivery)
if (push.containsKey('enabled') && !['true', '1', 'yes', 'on'].contains(text(push.enabled).toLowerCase(Locale.ROOT))) {
    throw new IllegalStateException('数据源推送功能尚未启用')
}

var registered = tableRead.from('db_table_t', 't', tenantId)
registered.where(tableRead.equal(txt('t', 'datasource_id'), datasourceId))
registered.where(tableRead.equal(num('t', 'asset_status'), 2))
registered.where(active('t'))
registered.where(tableRead.any([
    tableRead.isNull(txt('t', 'table_type')),
    tableRead.not(tableRead.equal(txt('t', 'table_type'), 'FILE'))
]))
var tables = collect(registered, {
    id: txt('t', 'tid'), name: txt('t', 'table_name'), nameCn: txt('t', 'table_name_cn'),
    comment: txt('t', 'table_comment'), governance: tableRead.column('t', 'field_governance_config', 'LOB')
}, [txt('t', 'table_name'), txt('t', 'tid')])
var tableIds = tables.map(table => table.id)
var columnsByTable = {}
var oldByTable = {}
if (tableIds.size() > 0) {
    var existing = tableRead.from('data_push_schema_t', 's', tenantId)
    existing.where(tableRead.equal(txt('s', 'datasource_id'), datasourceId))
    existing.where(tableRead.equal(num('s', 'is_del'), 0))
    for (row in existing.grouped({tid: txt('s', 'tid'), tableId: txt('s', 'table_id'),
        schemaVersion: num('s', 'schema_version'), schemaHash: txt('s', 'schema_hash')}, [])) {
        if (oldByTable[row.tableId] == null) { oldByTable[row.tableId] = row }
    }
    // 数据源可能已有大量表。每批只读取已确认归属的表字段，保持每一张表内部排序。
    var offset = 0
    while (offset < tableIds.size()) {
        var end = Math.min(offset + 500, tableIds.size())
        var fields = tableRead.from('db_table_column_t', 'c', tenantId)
        fields.where(tableRead.in(txt('c', 'table_id'), tableIds.subList(offset, end)))
        fields.where(active('c'))
        var rows = collect(fields, {
            tableId: txt('c', 'table_id'), name: txt('c', 'column_name'), comment: txt('c', 'column_comment'),
            columnType: txt('c', 'column_type'), dataType: txt('c', 'data_type'), nullable: num('c', 'nullable'),
            primaryKey: num('c', 'primary_key'), ordinal: num('c', 'ordinal_position')
        }, [txt('c', 'table_id'), num('c', 'ordinal_position'), txt('c', 'tid')])
        for (row in rows) {
            if (columnsByTable[row.tableId] == null) { columnsByTable[row.tableId] = [] }
            columnsByTable[row.tableId].add(row)
        }
        offset = end
    }
}

var publications = []
var snapshots = []
for (table in tables) {
    var fields = columnsByTable[table.id] || []
    var name = text(table.name)
    if (fields.size() == 0) { throw new IllegalStateException('数据表 ' + name + ' 未登记字段，无法发布推送规范') }
    var attachmentByName = {}
    var policies = object(table.governance).attachmentFields
    if (policies instanceof List) {
        for (raw in policies) {
            var policy = object(raw)
            attachmentByName[text(policy.columnName)] = policy
        }
    }
    var canonical = []
    var attachments = []
    var columns = []
    for (field in fields) {
        var fieldName = text(field.name)
        if (!fieldName.matches('[A-Za-z0-9_.-]{1,255}')) { throw new IllegalArgumentException('field 格式不正确') }
        var fieldType = text(field.columnType) || text(field.dataType)
        var ordinal = (field.ordinal || 0)::int
        var required = (field.nullable == null ? 1 : field.nullable)::int == 0 || (field.primaryKey || 0)::int == 1
        var definition = {name: fieldName, type: fieldType, required: required, ordinal: ordinal}
        var policy = attachmentByName[fieldName]
        if (policy != null) {
            var attachment = {columnName: fieldName, maxFileBytes: text(policy.maxFileBytes)::long(0L),
                allowedExtensions: policy.allowedExtensions}
            definition.attachment = attachment
            attachments.add(attachment)
        }
        canonical.add(definition)
        columns.add({columnName: fieldName, columnComment: text(field.comment), dataType: fieldType,
            ordinal: ordinal, required: required, enabled: true})
    }

    // 字段及附件约束的 JSON 顺序是既有指纹的一部分。注释不影响版本，旧契约沿用原 ID。
    var hash = DigestUtil.sha256Hex(mapper.writeValueAsString(canonical))
    var previous = oldByTable[table.id]
    var version = previous == null ? 1L : previous.schemaVersion::long + (text(previous.schemaHash) == hash ? 0L : 1L)
    var schemaId = previous == null ? IdUtil.getSnowflakeNextIdStr() : text(previous.tid)
    var delivery = new LinkedHashMap(push)
    delivery.attachmentFields = attachments
    publications.add({old: previous, schemaId: schemaId, tableId: table.id, tableName: name, version: version,
        hash: hash, sourceId: source.id, delivery: mapper.writeValueAsString(delivery), fields: canonical})
    snapshots.add({tableId: table.id, tableName: name, tableComment: text(table.nameCn) || text(table.comment) || name,
        schemaVersion: version + '', enabled: true, columns: columns})
}
return {publications: publications, snapshot: {
    datasourceId: text(source.id), datasourceName: text(source.name), sourceSystemId: '', sourceSystemName: '',
    contractVersion: new Date().getTime() + '', enabled: true, clientIds: ['push-' + datasourceId], tables: snapshots
}}
