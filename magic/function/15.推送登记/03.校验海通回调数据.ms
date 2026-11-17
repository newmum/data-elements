{
  "id": "push_registry_relay_check_v1",
  "groupId": "push_registry_function_group_v1",
  "name": "03.校验海通回调数据",
  "path": "/verifybatch",
  "description": "验证海通校验及文件投递入口共用的调用方、数据源、登记表和批次字段。外层入口通过服务间访问策略验证凭据。",
  "returnType": "Object",
  "parameters": [
    {"name": "caller", "required": true, "dataType": "String", "type": "java.lang.String"},
    {"name": "batch", "required": true, "dataType": "Object", "type": "java.lang.Object"},
    {"name": "channel", "required": true, "dataType": "String", "type": "java.lang.String"}
  ],
  "options": []
}
================================
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.Math
import java.util.List
import java.util.Locale
import java.util.Map
import identityRuntime
import tableRead

// 海通校验和投递入口使用同一份已发布规范。先在控制库确认调用方绑定，再进入该绑定指向的行业库。
var text = value => value == null ? '' : (value + '').trim()
var datasourceId = text(batch.datasourceId)
var tableName = text(batch.tableName)
if (!caller.matches('[A-Za-z0-9_-]{1,128}')) { throw new IllegalArgumentException('relay client 格式不正确') }
if (!datasourceId.matches('[A-Za-z0-9_-]{1,128}') || datasourceId.length() > 64) {
    throw new IllegalArgumentException('datasourceId 格式不正确')
}
if (!tableName.matches('[A-Za-z0-9_.-]{1,255}')) { throw new IllegalArgumentException('tableName 格式不正确') }
var records = batch.records
if (!(records instanceof List) || records.isEmpty() || records.size() > 10000) {
    throw new IllegalArgumentException('records 必须是 1 到 10000 条记录')
}
for (record in records) {
    if (!(record instanceof Map)) { throw new IllegalArgumentException('records 中每条记录必须是对象') }
}

var tenantId = identityRuntime.control(() => {
    var bindings = db.table('data_push_relay_client_t').columns(['tenant_id', 'datasource_id']).where()
        .eq('relay_client_id', caller).eq('is_enable', 1).eq('is_del', 0)
        .and(w => w.eq('datasource_id', datasourceId).or().isNull('datasource_id')).select()
    var bound = bindings.filter(row => row.datasourceId != null || row.datasource_id != null)
    var eligible = bound.size() > 0 ? bound : bindings
    if (eligible.size() != 1) { throw new IllegalArgumentException('该调用方未绑定推送数据源') }
    return text(eligible[0].tenantId || eligible[0].tenant_id)
})
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
var txt = (alias, name) => tableRead.column(alias, name, 'TEXT')
var num = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var active = alias => tableRead.equal(tableRead.coalesce([num(alias, 'is_del'), 0]), 0)

return identityRuntime.inTenant(tenantId, () => {
    var sources = tableRead.from('db_datasource_t', 'd', tenantId)
    sources.where(tableRead.equal(txt('d', 'tid'), datasourceId))
    sources.where(active('d'))
    var found = sources.rows({status: num('d', 'asset_status'), pool: tableRead.column('d', 'pool_cfg', 'LOB')}, 1)
    if (found.size() == 0) { throw new IllegalArgumentException('登记数据源不存在或无权访问') }
    var source = found[0]
    var status = (source.status || 0)::int
    if (status < 0 || status > 2) { throw new IllegalStateException('数据源状态不支持推送') }
    var config = object(source.pool)
    var modes = [config.accessMode, config.access_mode, config.dataAccessMode, config.data_access_mode]
        .map(item => text(item)).filter(item => item != '')
    if (modes.size() == 0 || !['receive', 'push'].contains(modes[0].toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException('该数据源不是数据推送方式')
    }
    var push = object(config.pushDelivery)
    if (push.containsKey('enabled') && !['true', '1', 'yes', 'on'].contains(text(push.enabled).toLowerCase(Locale.ROOT))) {
        throw new IllegalStateException('数据源推送功能尚未启用')
    }
    if (text(push.relayClientId) != '' && text(push.relayClientId) != caller) {
        throw new IllegalArgumentException('调用方不属于该推送数据源')
    }

    var published = tableRead.from('data_push_schema_t', 's', tenantId)
    published.where(tableRead.equal(txt('s', 'datasource_id'), datasourceId))
    published.where(tableRead.equal(txt('s', 'status'), 'PUBLISHED'))
    published.where(tableRead.equal(num('s', 'is_del'), 0))
    var candidates = published.grouped({id: txt('s', 'tid'), tableId: txt('s', 'table_id'), name: txt('s', 'table_name'),
        version: num('s', 'schema_version'), hash: txt('s', 'schema_hash'),
        configuration: tableRead.column('s', 'delivery_config', 'LOB')}, [])
        .filter(row => tableName.equalsIgnoreCase(text(row.name)))
    var eligible = []
    var tableIds = candidates.map(row => row.tableId).distinct()
    var offset = 0
    // 历史数据表和契约表可能使用不同排序规则，按已发布的表 ID 批量验证其登记归属。
    while (offset < tableIds.size()) {
        var end = Math.min(offset + 500, tableIds.size())
        var tables = tableRead.from('db_table_t', 't', tenantId)
        tables.where(tableRead.equal(txt('t', 'datasource_id'), datasourceId))
        tables.where(tableRead.equal(num('t', 'asset_status'), 2))
        tables.where(active('t'))
        tables.where(tableRead.any([tableRead.isNull(txt('t', 'table_type')),
            tableRead.not(tableRead.equal(txt('t', 'table_type'), 'FILE'))]))
        tables.where(tableRead.in(txt('t', 'tid'), tableIds.subList(offset, end)))
        eligible.addAll(tables.grouped({id: txt('t', 'tid')}, []).map(row => text(row.id)))
        offset = end
    }
    var schemas = candidates.filter(row => eligible.contains(text(row.tableId)))
    if (schemas.size() == 0) {
        throw new IllegalArgumentException('数据表未完成推送规范发布，请重新完成数据源登记')
    }
    var schema = schemas[0]
    var fields = tableRead.from('data_push_schema_field_t', 'f', tenantId)
    fields.where(tableRead.equal(txt('f', 'schema_id'), schema.id))
    fields.where(tableRead.equal(num('f', 'is_del'), 0))
    var rules = {}
    fields.forEachOrderedRow({name: txt('f', 'field_name'), required: num('f', 'required_flag'), type: txt('f', 'data_type')},
        [num('f', 'ordinal_position'), txt('f', 'tid')], ['asc', 'asc'], true, row => { rules[text(row.name)] = row })

    // 批内只遍历已读到内存的规则。调用方版本、租户及目的地址均无选择权。
    for (record in records) {
        for (name in record.keySet()) {
            if (!rules.containsKey(name)) { throw new IllegalArgumentException('存在未登记字段：' + name) }
        }
        for (name in rules.keySet()) {
            var value = record[name]
            var field = rules[name]
            if ((field.required || 0)::int == 1 && (value == null || value instanceof String && value.isBlank())) {
                throw new IllegalArgumentException('缺少必填字段：' + name)
            }
            if (value == null) { continue }
            var type = text(field.type).toLowerCase(Locale.ROOT)
            var normal = value instanceof String || value instanceof Number || value instanceof Boolean
            if (type.contains('bool')) {
                normal = value instanceof Boolean || '0'.equals(value) || '1'.equals(value)
                    || ['true', 'false'].contains((value + '').toLowerCase(Locale.ROOT))
            } else if (type.matches('.*(int|decimal|numeric|double|float|number|real).*')) {
                normal = value instanceof Number || (value + '').matches('[-+]?[0-9]+([.][0-9]+)?')
            }
            if (!normal) { throw new IllegalArgumentException('字段类型不匹配：' + name) }
        }
    }
    var delivery = object(schema.configuration)
    if (!['table-to-ftp', 'table-to-kafka'].contains(channel)) {
        throw new IllegalArgumentException('不支持的推送通道')
    }
    var target = object(channel == 'table-to-ftp' ? delivery.ftp : delivery.kafka)
    return {tenantId: tenantId, records: records, destination: text(target.topic),
        contract: {datasourceId: datasourceId, tableId: text(schema.tableId), tableName: tableName,
            schemaVersion: schema.version::long, schemaHash: text(schema.hash), recordCount: records.size()}}
})
