{
  "id": "push_registry_schema_query_v1",
  "groupId": "push_registry_function_group_v1",
  "name": "04.读取已发布推送契约",
  "path": "/read",
  "description": "推送契约 GET 与 HEAD 使用的角色校验和批量读取，返回旧登记读接口的响应信封。",
  "returnType": "Object",
  "parameters": [
    {
      "name": "supplied",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import cn.hutool.extra.spring.SpringUtil
import com.fasterxml.jackson.databind.ObjectMapper
import com.linewell.dataelement.model.common.BizException
import java.lang.Math
import java.util.List
import java.util.Locale
import java.util.Map
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.server.ResponseStatusException
import dataScope
import tableRead
import tenantRuntime

// GET 和 HEAD /dws/push/contracts 共用当前租户和登记菜单权限，按数据源查询已发布的表契约。
// 读取真实登记表确认每个契约仍有效，对外只返回字段、附件约束和公开接收协议。
var errors = SpringUtil.getBean('globalExceptionHandler')
if (supplied == null || supplied.size() == 0) {
    return ResponseEntity.ok(errors.handleMissingServletRequestParameterException(
        new MissingServletRequestParameterException('datasourceId', 'String')))
}
var datasourceId = supplied.size() == 1 ? supplied[0].trim() : ''
if (!datasourceId.matches('[A-Za-z0-9_.-]{1,255}')) {
    return ResponseEntity.ok(errors.handleException(
        new ResponseStatusException(HttpStatus.BAD_REQUEST, 'datasourceId 格式不正确')))
}

var tenantId = tenantRuntime.id()
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
var txt = (alias, name) => tableRead.column(alias, name, 'TEXT')
var num = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var visible = alias => tableRead.equal(tableRead.coalesce([num(alias, 'is_del'), 0]), 0)
var collect = (query, fields, sorts) => {
    var rows = []
    query.forEachOrderedRow(fields, sorts, sorts.map(term => 'asc'), true, row => rows.add(row))
    return rows
}

try {
    var sources = tableRead.from('db_datasource_t', 'd', tenantId)
    sources.where(tableRead.equal(txt('d', 'tid'), datasourceId))
    sources.where(visible('d'))
    var found = sources.rows({
        tid: txt('d', 'tid'), dbName: txt('d', 'db_name'), assetStatus: num('d', 'asset_status'),
        poolCfg: tableRead.column('d', 'pool_cfg', 'LOB'), org_id: txt('d', 'org_id'), created_by: txt('d', 'created_by')
    }, 1)
    if (found.size() == 0) {
        throw new IllegalArgumentException('登记数据源不存在或无权访问')
    }
    if (dataScope.visibleDataSourcesFor('MENU:2081000000000000002', found).isEmpty()) {
        return ResponseEntity.ok(errors.handleBizException(new BizException(403, '登记数据源不存在或无权访问')))
    }
    var source = found[0]
    var status = text(source.assetStatus)::int(0)
    if (status < 0 || status > 2) { throw new IllegalStateException('数据源状态不支持推送') }
    var config = object(source.poolCfg)
    var modes = [config.accessMode, config.access_mode, config.dataAccessMode, config.data_access_mode]
        .map(item => text(item)).filter(item => item != '')
    var mode = modes.size() == 0 ? '' : modes[0]
    if (!['receive', 'push'].contains(mode.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException('该数据源不是数据推送方式')
    }
    var delivery = object(config.pushDelivery)
    if (delivery.containsKey('enabled') && !['true', '1', 'yes', 'on'].contains(text(delivery.enabled).toLowerCase(Locale.ROOT))) {
        throw new IllegalStateException('数据源推送功能尚未启用')
    }

    // 表与契约分开读取，保留不区分大小写的历史表 ID 匹配，并避开异排序规则列关联。
    var registered = tableRead.from('db_table_t', 't', tenantId)
    registered.where(tableRead.equal(txt('t', 'datasource_id'), datasourceId))
    registered.where(tableRead.equal(num('t', 'asset_status'), 2))
    registered.where(visible('t'))
    registered.where(tableRead.any([
        tableRead.isNull(txt('t', 'table_type')),
        tableRead.not(tableRead.equal(txt('t', 'table_type'), 'FILE'))
    ]))
    var registeredIds = {}
    for (row in registered.grouped({id: txt('t', 'tid')}, [])) {
        registeredIds[text(row.id).toLowerCase(Locale.ROOT)] = true
    }

    var published = tableRead.from('data_push_schema_t', 's', tenantId)
    published.where(tableRead.equal(txt('s', 'datasource_id'), datasourceId))
    published.where(tableRead.equal(txt('s', 'status'), 'PUBLISHED'))
    published.where(tableRead.equal(num('s', 'is_del'), 0))
    var schemas = collect(published, {
        schemaId: txt('s', 'tid'), tableId: txt('s', 'table_id'), tableName: txt('s', 'table_name'),
        schemaVersion: num('s', 'schema_version'), schemaHash: txt('s', 'schema_hash'),
        publishedTime: tableRead.column('s', 'published_time', 'TIME'),
        deliveryConfig: tableRead.column('s', 'delivery_config', 'LOB')
    }, [txt('s', 'table_name'), txt('s', 'tid')]).filter(row => registeredIds[text(row.tableId).toLowerCase(Locale.ROOT)] == true)

    // 一次预取一批有效契约的字段，每条字段仍使用当前租户及其契约 ID 两道约束。
    var schemaIds = schemas.map(row => row.schemaId).distinct()
    var fieldsBySchema = {}
    var offset = 0
    while (offset < schemaIds.size()) {
        var end = Math.min(offset + 500, schemaIds.size())
        var fields = tableRead.from('data_push_schema_field_t', 'f', tenantId)
        fields.where(tableRead.in(txt('f', 'schema_id'), schemaIds.subList(offset, end)))
        fields.where(tableRead.equal(num('f', 'is_del'), 0))
        var rows = collect(fields, {
            schemaId: txt('f', 'schema_id'), fieldName: txt('f', 'field_name'), dataType: txt('f', 'data_type'),
            requiredFlag: num('f', 'required_flag'), ordinal: num('f', 'ordinal_position')
        }, [txt('f', 'schema_id'), num('f', 'ordinal_position'), txt('f', 'tid')])
        for (field in rows) {
            if (fieldsBySchema[field.schemaId] == null) { fieldsBySchema[field.schemaId] = [] }
            fieldsBySchema[field.schemaId].add(field)
        }
        offset = end
    }

    var tables = []
    for (schema in schemas) {
        var attachments = {}
        var policies = object(schema.deliveryConfig).attachmentFields
        if (policies instanceof List) {
            for (raw in policies) {
                var policy = object(raw)
                attachments[text(policy.columnName)] = policy
            }
        }
        var fields = []
        for (field in fieldsBySchema[schema.schemaId] || []) {
            var name = text(field.fieldName)
            var item = {fieldName: name, dataType: text(field.dataType), required: (field.requiredFlag || 0)::int == 1,
                ordinal: (field.ordinal || 0)::int}
            if (attachments[name] != null) { item.attachment = attachments[name] }
            fields.add(item)
        }
        tables.add({schemaId: text(schema.schemaId), tableId: text(schema.tableId), tableName: text(schema.tableName),
            schemaVersion: schema.schemaVersion == null ? 1L : schema.schemaVersion::long,
            schemaHash: text(schema.schemaHash), publishedTime: schema.publishedTime, fieldCount: fields.size(), fields: fields})
    }

    return ResponseEntity.ok({code: 0, message: 'success', data: {
        datasourceId: text(source.tid), datasourceName: text(source.dbName), accessMode: 'receive', tables: tables,
        requestHeaders: [
            {name: 'Content-Type', value: 'application/json', required: true, description: '请求体必须为 JSON'},
            {name: 'X-Api-Key', value: '业务调用方身份密钥', required: true,
                description: '用于识别推送业务数据的调用方，并校验其对当前数据源及已登记数据表的推送权限；平台不保存密钥明文，请勿放入请求体或前端代码中'},
            {name: 'Idempotency-Key', value: '每次逻辑请求唯一 UUID', required: true, description: '相同键重试返回同一受理结果'}
        ],
        requiredParameters: [
            {name: 'deliveryType', required: true, description: '投递类型，仅支持 FTP 或 KAFKA；目标地址由登记规范确定'},
            {name: 'datasourceId', required: true, description: '固定为当前数据源编码'},
            {name: 'tableName', required: true, description: '必须为下方已发布的数据表英文名'},
            {name: 'batchId', required: false, description: '调用方批次标识，建议可追溯且不可复用'},
            {name: 'records', required: true, description: '1 至 10,000 条记录，每条仅可包含已登记字段'}
        ],
        endpoints: {
            dataPush: {method: 'POST', path: '/api/v1/push',
                description: '统一表批次推送入口；以 deliveryType 选择已登记并获授权的 FTP 或 Kafka 投递通道', deliveryTypes: [
                    {value: 'FTP', description: '写入当前环境 ODS/FJGHCC 关联的文件存储'},
                    {value: 'KAFKA', description: '写入本数据源已配置且获授权的 Kafka Topic'}
                ]},
            fileUpload: {method: 'POST', path: '/api/file/single', defaultMode: 'SINGLE', chunkedPath: '/api/file/init',
                description: '默认整文件上传，节点默认上限20 MB；大文件可用分片上传。校验就绪后，关联字段填写fileId；大小和格式同时满足字段及集合策略'}
        }
    }})
} catch (error) {
    return ResponseEntity.ok(errors.handleException(error))
}
