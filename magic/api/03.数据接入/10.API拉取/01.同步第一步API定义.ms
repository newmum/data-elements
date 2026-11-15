{
  "properties": {},
  "id": "ods_api_pull_sync_01",
  "script": null,
  "groupId": "ods_api_pull_group_01",
  "name": "01.同步第一步API定义",
  "path": "/sync",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "接收 datasourceId，按当前租户和数据源登记权限读取已保存的 API 定义。事务内批量同步服务和已登记逻辑表的规则草稿，返回定义、服务、规则数量及其关联标识。保留已有规则的启用状态和 NiFi 绑定。"
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableWrite
import '@/common/apiPullDefinitionError' as apiPullDefinitionError
import cn.hutool.core.util.IdUtil
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.List
import java.util.Locale
import java.util.Map

// POST /ods/api-pull/sync 按 datasourceId 将第一步已保存的 API 定义同步为服务和表级规则草稿。
// 校验登录租户和数据源登记范围，在同一事务内读配置、批量锁定现有服务和规则并写入；保留已启用规则和流程绑定。
// 返回定义、服务、规则数量及各逻辑表的关联标识，尚未登记逻辑表时先同步服务。
var tenantId = tenantRuntime.id()
var text = value => value == null ? '' : ('' + value).trim()
var lower = value => text(value).toLowerCase(Locale.ROOT)
var datasourceId = text(body?.datasourceId)
if (text(tenantId) == '') {
    exit 401, '当前登录租户无效，请重新登录'
}
if (datasourceId == '') {
    exit 400, '数据源标识不能为空'
}

var sourcePermission = 'MENU:2081000000000000002'
var sourceFields = ['tid', 'tenant_id', 'db_type', 'pool_cfg', 'org_id', 'created_by']
var active = { is_del: 0 }
var activeSource = { is_del: 0, datasource_id: datasourceId }
var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var validId = value => text(value) != '' && text(value).equals(value)
var serviceCode = (tableName, serviceName) => {
    // 编码是既有服务的身份，保留 ASCII 归一化和 96 字符截断规则。
    var code = (tableName + '_' + serviceName).toLowerCase(Locale.ROOT)
        .replaceAll('[^a-z0-9]+', '_').replaceAll('^_+|_+$', '')
    return code.length() > 96 ? code.substring(0, 96) : code
}
var definitionsOf = source => {
    if (lower(source.db_type) != 'api') {
        throw new IllegalArgumentException('API 拉取规则仅适用于接入方式为“数据拉取”的 API 数据源')
    }
    var pool = {}
    try {
        var raw = text(source.pool_cfg)
        if (raw != '') {
            pool = new ObjectMapper().readValue(raw, Map)
        }
    } catch (e) {
        throw new IllegalArgumentException('数据源中的 API 拉取配置不是有效 JSON')
    }
    var accessMode = null
    for (key in (pool == null ? [] : pool.keySet())) {
        if (lower(key) == 'accessmode') {
            if (accessMode != null) {
                throw new IllegalArgumentException('API 数据源的接入方式重复，请重新保存定义')
            }
            accessMode = lower(pool.get(key))
        }
    }
    if (!['capture', 'pull'].contains(accessMode)) {
        throw new IllegalArgumentException('API 拉取规则仅适用于接入方式为“数据拉取”的 API 数据源')
    }
    var items = pool.get('apiPullItems')
    return items instanceof List ? items.filter(item => item instanceof Map) : []
}

return db.transaction(() => {
    // 来源记录与第一步配置一起锁定，重复同步读取已提交的最新配置，防止并发重复建规则。
    var source = tableWrite.lockRow('db_datasource_t', tenantId, 'tid', datasourceId, sourceFields, active)
    if (source == null || !tenantId.equals(source.tenant_id) || !datasourceId.equals(source.tid)) {
        throw new IllegalArgumentException('数据源不存在或不属于当前租户')
    }
    if (!dataScope.canReadDataSourceFor(sourcePermission, source)) {
        exit 403, '当前角色无权同步此数据源的拉取规则'
    }
    var definitions = definitionsOf(source)
    if (definitions.size() > 100) {
        exit 400, '一个数据源最多登记 100 条 API'
    }
    if (definitions.isEmpty()) {
        return { datasourceId: datasourceId, definitions: 0, services: 0, rules: 0, entries: [] }
    }
    // 保存数据源和同步共用同一套地址与凭据校验，服务及规则仅接收允许保存的字段。
    var definitionError
    try {
        definitionError = apiPullDefinitionError(definitions)
    } catch (e) {
        exit 400, e.message
    }
    if (definitionError != null) {
        exit 400, definitionError
    }

    var names = []
    var codes = []
    var identities = []
    for (item in definitions) {
        var tableName = text(item.get('tableName'))
        var serviceName = text(item.get('serviceName'))
        var tableKey = lower(tableName)
        var code = serviceCode(tableName, serviceName)
        if (codes.contains(code)) {
            exit 400, 'API 服务编码重复，请调整逻辑表英文名'
        }
        names.add(tableKey)
        codes.add(code)
        identities.add({ item: item, tableName: tableName, tableKey: tableKey, serviceName: serviceName, code: code })
    }

    // 只读取本次定义涉及的逻辑表，并逐项核对租户和数据源，避免宽松排序规则扩大匹配范围。
    var tableLookup = tableRead.from('db_table_t', 's', tenantId)
    tableLookup.where(tableRead.equal(column('s', 'datasource_id'), datasourceId))
    tableLookup.where(tableRead.equal(tableRead.column('s', 'is_del', 'NUMBER'), 0))
    var nameColumn = column('s', 'table_name')
    tableLookup.where(tableRead.in(tableRead.lower(tableRead.trim(nameColumn)), names))
    var scopedTables = tableLookup.grouped({
        tid: column('s', 'tid'), tenant_id: column('s', 'tenant_id'),
        datasource_id: column('s', 'datasource_id'), table_name: nameColumn
    }, [])
    var tables = {}
    var tableIds = []
    for (row in scopedTables) {
        var key = lower(row.table_name)
        if (!names.contains(key)) {
            continue
        }
        if (!tenantId.equals(row.tenant_id) || !datasourceId.equals(row.datasource_id) || !validId(row.tid)) {
            exit 409, '现有逻辑表标识或数据源归属不一致，请核对登记'
        }
        if (tables[key] != null) {
            exit 409, 'API 来源中存在同名逻辑表，请核对登记'
        }
        tables[key] = row.tid
        tableIds.add(row.tid)
    }

    // 先服务后规则，各用一次加锁读取。事务结束前已匹配的行不能被并发删除、重命名或转移。
    var scopedServices = tableWrite.lockRows('data_pull_service_t', tenantId, 'service_code', codes,
        ['tid', 'tenant_id', 'datasource_id', 'service_code'], activeSource)
    var servicesByCode = {}
    for (row in scopedServices) {
        var code = row.service_code
        if (!tenantId.equals(row.tenant_id) || !datasourceId.equals(row.datasource_id)
            || !validId(row.tid) || !codes.contains(code) || servicesByCode[code] != null) {
            exit 409, '现有 API 服务编码或归属不一致，请核对登记'
        }
        servicesByCode[code] = row
    }

    var rulesByTable = {}
    if (!tableIds.isEmpty()) {
        // 表级规则在同租户内唯一。先按登记表读取，发现其他数据源占用规则时整批退出。
        var scopedRules = tableWrite.lockRows('data_pull_rule_t', tenantId, 'source_table_id', tableIds,
            ['tid', 'tenant_id', 'datasource_id', 'source_table_id'], active)
        for (row in scopedRules) {
            var tableId = row.source_table_id
            if (!tenantId.equals(row.tenant_id) || !datasourceId.equals(row.datasource_id)
                || !validId(row.tid) || !tableIds.contains(tableId) || rulesByTable[tableId] != null) {
                exit 409, '现有拉取规则与登记表归属不一致，请核对登记'
            }
            rulesByTable[tableId] = row.tid
        }
    }

    var now = LocalDateTime.now()
    var newServices = []
    var updatedServices = []
    var newRules = []
    var updatedRules = []
    var entries = []
    var ruleCount = 0
    for (identity in identities) {
        var item = identity.item
        var existingService = servicesByCode[identity.code]
        var serviceId = existingService == null ? IdUtil.getSnowflakeNextIdStr() : existingService.tid
        var auth = text(item.get('authType')).toUpperCase(Locale.ROOT)
        var service = {
            tid: serviceId,
            service_name: identity.serviceName,
            base_url: text(item.get('baseUrl')),
            protocol: lower(item.get('baseUrl')).startsWith('https:') ? 'HTTPS' : 'HTTP',
            service_type: text(item.get('apiProtocol')).toUpperCase(Locale.ROOT) == 'SOAP' ? 'SOAP' : 'REST',
            common_headers_json: item.get('commonHeadersJson'),
            auth_type: auth == '' ? 'NONE' : auth,
            credential_ref: item.get('credentialRef'),
            updated_time: now
        }
        if (existingService == null) {
            service.service_code = identity.code
            service.tenant_id = tenantId
            service.datasource_id = datasourceId
            service.created_time = now
            service.is_del = 0
            newServices.add(service)
        } else {
            service.service_code = existingService.service_code
            updatedServices.add(service)
        }

        var tableId = tables[identity.tableKey]
        var entry = {
            tableName: identity.tableName,
            tableComment: item.get('tableComment'),
            serviceId: serviceId,
            ruleReady: tableId != null
        }
        if (tableId != null) {
            var existingRule = rulesByTable[tableId]
            var ruleId = existingRule == null ? IdUtil.getSnowflakeNextIdStr() : existingRule
            var method = text(item.get('endpointMethod')).toUpperCase(Locale.ROOT)
            var cron = text(item.get('captureSchedule')?.cronExpression || item.get('cronExpression'))
            var rule = {
                tid: ruleId,
                source_table_id: tableId,
                service_id: serviceId,
                rule_name: text(item.get('tableComment')) + '拉取规则',
                endpoint_method: method == '' ? 'GET' : method,
                endpoint_path: text(item.get('endpointPath')),
                request_template_json: item.get('requestTemplateJson'),
                response_config_json: item.get('responseConfigJson'),
                pagination_config_json: item.get('paginationConfigJson'),
                incremental_config_json: item.get('incrementalConfigJson'),
                runtime_config_json: item.get('runtimeConfigJson'),
                trigger_mode: cron == '' ? 'MANUAL' : 'CRON',
                cron_expression: cron,
                updated_time: now
            }
            if (existingRule == null) {
                rule.tenant_id = tenantId
                rule.datasource_id = datasourceId
                rule.enabled = 0
                rule.created_time = now
                rule.is_del = 0
                newRules.add(rule)
            } else {
                updatedRules.add(rule)
            }
            entry.tableId = tableId
            entry.ruleId = ruleId
            ruleCount++
        }
        entries.add(entry)
    }

    // 已有行均在当前事务锁内，仍按租户、数据源、活跃状态和精确行键更新。
    // 业务字段不变时，数据库更新计数可能为 0。已有规则保留启用状态、执行设置及 NiFi 关联。
    if (!newServices.isEmpty()) {
        db.table('data_pull_service_t').batchInsert(newServices, 100)
    }
    if (!updatedServices.isEmpty()) {
        tableWrite.batchUpdate('data_pull_service_t', tenantId, [
            'service_name', 'base_url', 'protocol', 'service_type', 'common_headers_json',
            'auth_type', 'credential_ref', 'updated_time'
        ], ['tid', 'service_code'], activeSource, updatedServices)
    }
    if (!newRules.isEmpty()) {
        db.table('data_pull_rule_t').batchInsert(newRules, 100)
    }
    if (!updatedRules.isEmpty()) {
        tableWrite.batchUpdate('data_pull_rule_t', tenantId, [
            'service_id', 'rule_name', 'endpoint_method', 'endpoint_path', 'request_template_json',
            'response_config_json', 'pagination_config_json', 'incremental_config_json',
            'runtime_config_json', 'trigger_mode', 'cron_expression', 'updated_time'
        ], ['tid', 'source_table_id'], activeSource, updatedRules)
    }
    return {
        datasourceId: datasourceId,
        definitions: definitions.size(),
        services: identities.size(),
        rules: ruleCount,
        entries: entries
    }
})
