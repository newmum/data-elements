{
  "properties": {},
  "id": "fbbbebbeee9346e1813e9b1bf635eb09",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "41.NiFi画布接入任务关联",
  "path": "/ensureNifiCatalogAccessTask",
  "createTime": 1794633802097,
  "updateTime": null,
  "parameters": [
    {
      "name": "rawCatalogTid",
      "description": "可选的目录或来源表标识，来源表须关联当前租户有效数据源",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "pipelineId",
      "description": "调用方已经保存并校验租户归属的画布标识",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "pipelineName",
      "description": "可选画布名称，空值时保留已有任务名称或使用默认名称",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "dsl",
      "description": "已保存的画布设计，从来源和目标节点提取接入任务字段",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": [],
  "description": "在当前行业租户中按已保存画布优先匹配有效接入任务，其次复用未绑定的目录任务，必要时创建。返回任务、目录及旧目录镜像是否适用。沿用调用方的流程互斥和事务，无 NiFi 或目录镜像调用。"
}
================================
import tableRead
import tenantRuntime
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Dsl as PipelineDsl
import com.linewell.dataelement.platform.persistence.id.NumericId
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import java.lang.IllegalStateException
import java.lang.Iterable
import java.time.LocalDateTime
import java.util.Map
import java.util.Objects

// @/common/ensureNifiCatalogAccessTask 按可信行业租户为已保存画布关联接入任务。
// rawCatalogTid 可为目录或技术来源表，pipelineId 必须是调用方已核对的本租户画布。
// 写入沿用调用方的互斥和事务，返回任务 ID、目录 ID 和是否需要旧目录镜像；本函数不调用 NiFi。
var tenantId = tenantRuntime.id()
if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
    throw new IllegalStateException('画布接入任务只能在当前行业租户中关联')
}

var blank = value => value == null || value.trim().isEmpty()
var catalogId = rawCatalogTid == null || rawCatalogTid.isBlank() ? null : rawCatalogTid.trim()
if ('null'.equalsIgnoreCase(catalogId) || 'undefined'.equalsIgnoreCase(catalogId)) {
    catalogId = null
}

var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
var active = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
var readActive = (table, alias, key, value) => {
    var rows = tableRead.from(table, alias, tenantId)
    rows.where(active(alias))
    rows.where(tableRead.equal(textColumn(alias, key), value))
    return rows
}
var taskTable = 'data_access_agg_task_t'
var taskBy = (key, value) => {
    var rows = readActive(taskTable, 'a', key, value).rows({
        tid: textColumn('a', 'tid'),
        pipeline_id: textColumn('a', 'pipeline_id'),
        data_catalog_id: textColumn('a', 'data_catalog_id')
    }, 1)
    return rows.isEmpty() ? null : rows[0]
}

var fields = {
    source_db_id: ['dbId', 'sourceDbId'],
    source_table_id: ['sourceTableId'],
    source_table_primary_key: ['sourceTablePrimaryKey'],
    source_table_increment_key: ['sourceTableIncrementKey', 'incrementalColumn'],
    target_db_id: ['targetDbId'],
    target_table_id: ['targetTableId'],
    target_table_primary_key: ['targetTablePrimaryKey']
}
var firstValue = (values, keys) => {
    for (key in keys) {
        var value = Objects.toString(values.get(key), null)
        if (!blank(value)) {
            return value
        }
    }
    return null
}
var assignFromNodes = values => {
    var typed = dsl instanceof PipelineDsl
    var nodes = typed ? dsl.nodes() : (dsl instanceof Map ? dsl.get('nodes') : null)
    if (!(nodes instanceof Iterable)) {
        return
    }
    // 同类节点沿用历史顺序覆盖。缺失的来源数据源不覆盖先前有效值。
    for (node in nodes) {
        if (!typed && !(node instanceof Map)) {
            continue
        }
        var category = typed ? node.category() : Objects.toString(node.get('category'), null)
        var config = typed ? (node.config() == null ? {} : node.config()) : node.get('config')
        if (!(config instanceof Map)) {
            continue
        }
        if ('source'.equalsIgnoreCase(category)) {
            var sourceDb = firstValue(config, ['registeredDatasourceId', 'selectedDatabaseId', 'sourceDbId', 'dbId'])
            if (sourceDb != null) {
                values.put('source_db_id', sourceDb)
            }
            values.put('source_table_id', firstValue(config, ['sourceTableId']))
            values.put('source_table_primary_key', firstValue(config, ['primaryKey', 'pk', 'updateKeys']))
            values.put('source_table_increment_key', firstValue(config, ['incrementalColumn', 'incrementKey']))
        } else if ('sink'.equalsIgnoreCase(category)) {
            values.put('target_db_id', firstValue(config, ['registeredDatasourceId', 'selectedDatabaseId', 'targetDbId', 'dbId']))
            values.put('target_table_id', firstValue(config, ['targetTableId', 'registeredTableId']))
            values.put('target_table_primary_key', firstValue(config, ['updateKeys', 'primaryKey', 'pk']))
        }
    }
}
var removeNullFields = values => {
    for (key in fields.keySet()) {
        if (values.get(key) == null) {
            values.remove(key)
        }
    }
}
var updateTask = (task, link) => {
    var values = {updated_time: LocalDateTime.now()}
    if (!blank(pipelineName)) {
        values.put('task_name', pipelineName)
    }
    if (link) {
        values.put('pipeline_id', pipelineId)
    }
    assignFromNodes(values)
    // 现有 ORM 对空属性不做清空。更新范围只含本入口负责的字段，避免覆盖监控任务的最新值。
    removeNullFields(values)
    var changed = db.table(taskTable).exclude('updated_by').withBlank().where()
        .eq('tenant_id', tenantId).eq('is_del', 0).eq('tid', task.tid)
        .eq('pipeline_id', task.pipeline_id).update(values)
    if (link && changed == 0) {
        throw new IllegalStateException('接入任务的关联已改变，请重新打开画布后重试')
    }
    if (!link && changed == 0) {
        // 驱动可能对值相同的更新返回零行。只在任务仍归属同一画布时视为成功。
        var current = readActive(taskTable, 'a', 'tid', task.tid)
        current.where(tableRead.equal(textColumn('a', 'pipeline_id'), task.pipeline_id))
        if (current.rows({tid: textColumn('a', 'tid')}, 1).isEmpty()) {
            throw new IllegalStateException('接入任务的关联已改变，请重新打开画布后重试')
        }
    }
}
var linked = blank(pipelineId) ? null : taskBy('pipeline_id', pipelineId)
if (linked != null) {
    updateTask(linked, false)
    // 现有画布先于目录命中。副本或旧工具传入其他标识时，不改动原目录关系。
    return {taskId: linked.tid, catalogId: catalogId, mirrorEligible: catalogId != null && catalogId == linked.data_catalog_id}
}

var conflictingCatalog = '该目录已关联流程，请重新打开已保存的任务后编辑'
var useCatalogTask = (task, mirror) => {
    if (!blank(task.pipeline_id) && task.pipeline_id != pipelineId) {
        throw new IllegalStateException(conflictingCatalog)
    }
    if (blank(task.pipeline_id) && !blank(pipelineId)) {
        updateTask(task, true)
    }
    return {taskId: task.tid, catalogId: catalogId, mirrorEligible: mirror}
}
var byCatalog = catalogId == null ? null : taskBy('data_catalog_id', catalogId)
if (byCatalog != null) {
    return useCatalogTask(byCatalog, true)
}

var mirrorEligible = false
if (catalogId != null) {
    // 先核对有效目录，再匹配归属于现用数据源的同租户技术表；不依赖旧兼容视图。
    var catalogs = readActive('da_catalog_t', 'c', 'tid', catalogId).rows({tid: textColumn('c', 'tid')}, 1)
    mirrorEligible = !catalogs.isEmpty()
    if (!mirrorEligible) {
        var sourceQuery = readActive('db_table_t', 's', 'tid', catalogId)
        sourceQuery.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id', [active('d')])
        sourceQuery.leftJoin('da_catalog_t', 'c', 'tid', 'source_catalog_id', [active('c')])
        sourceQuery.where(tableRead.not(tableRead.isNull(textColumn('d', 'tid'))))
        var sources = sourceQuery.rows({
            registered_catalog: textColumn('c', 'tid'),
            catalog_source: textColumn('c', 'source_table_id')
        }, 1)
        if (sources.isEmpty()) {
            throw new IllegalStateException('目录或来源表不存在或不属于当前租户')
        }
        var source = sources[0]
        if (source.registered_catalog != null && (blank(source.catalog_source) || source.catalog_source == catalogId)) {
            catalogId = source.registered_catalog
            mirrorEligible = true
            var registeredTask = taskBy('data_catalog_id', catalogId)
            if (registeredTask != null) {
                return useCatalogTask(registeredTask, true)
            }
        }
        // 没有可用目录的合法技术表仍保留原标识；仅有效目录读取扩展属性与执行镜像。
    }
}

var values = {}
if (mirrorEligible) {
    var propKeys = []
    for (aliases in fields.values()) {
        propKeys.addAll(aliases)
    }
    var query = readActive('da_prop_t', 'p', 'parent_id', catalogId)
    query.where(tableRead.in(textColumn('p', 'prop_name'), propKeys))
    var props = query.rows({name: textColumn('p', 'prop_name'), value: tableRead.column('p', 'prop_value', 'LOB')}, 1000)
    if (props.size() >= 1000) {
        throw new IllegalStateException('目录接入任务属性超过读取上限')
    }
    var properties = {}
    for (prop in props) {
        properties.put(prop.name, prop.value)
    }
    for (field in fields.keySet()) {
        values.put(field, firstValue(properties, fields.get(field)))
    }
}

// 新任务先填目录属性，然后按保存的节点覆盖。最后移除空值，使用原数据表的列默认值。
assignFromNodes(values)
removeNullFields(values)
var taskId = NumericId.nextId()
var now = LocalDateTime.now()
values.put('tid', taskId)
values.put('tenant_id', tenantId)
if (catalogId != null) {
    values.put('data_catalog_id', catalogId)
}
if (pipelineId != null) {
    values.put('pipeline_id', pipelineId)
}
values.put('task_name', blank(pipelineName) ? '数据接入任务' : pipelineName)
values.put('task_desc', '由流程画布创建')
values.put('task_status', 0)
values.put('is_del', 0)
values.put('created_time', now)
values.put('updated_time', now)
db.table(taskTable).primary('tid').excludes(['created_by', 'updated_by']).withBlank().insert(values)
return {taskId: taskId, catalogId: catalogId, mirrorEligible: mirrorEligible}
