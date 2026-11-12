{
  "properties": {},
  "id": "dwm_metadata_governance_summary_01",
  "script": null,
  "groupId": "dwm_metadata_governance_group_01",
  "name": "10.治理总览统计",
  "createTime": 1790644834214,
  "updateTime": 1790907208399,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/summary",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "只读聚合当前租户数据源、实体、数据元和有效标准覆盖率；首屏不传输完整表字段目录；涉及 db_datasource_t、db_table_t、da_metadata_t、db_table_column_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import tableRead
import java.math.BigDecimal
import java.math.RoundingMode

/*
 * POST /dwm/metadata-governance/summary
 * 不接收筛选参数。返回当前登录租户的数据源、实体、数据元数量，以及字段数、已覆盖字段数和覆盖率。
 * 只读平台保存的有效记录，不连接物理数据源。只有引用有效已发布数据元的字段计入覆盖。
 */
var tenantId = tenantRuntime.id()

var sources = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId).eq('is_del', 0).count()
var standards = db.table('da_metadata_t').where()
    .eq('tenant_id', tenantId).eq('is_del', 0).count()

// 实体统计和字段汇总都只接受归属于同租户有效数据源的有效表。
var activeTables = () => {
    var tables = tableRead.from('db_table_t', 't', tenantId)
    var sourceId = tableRead.column('d', 'tid', 'TEXT')
    tables.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id', [
        tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0)
    ])
    tables.where(tableRead.all([
        tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0),
        tableRead.not(tableRead.isNull(sourceId))
    ]))
    return tables
}
var entities = activeTables().grouped({total: tableRead.count()}, [])[0].total

// 先按表聚合字段。引用失效的字段保留在分母，只有匹配有效已发布数据元的字段计入分子。
var fieldCounts = tableRead.from('db_table_column_t', 'c', tenantId)
var fieldTableId = tableRead.column('c', 'table_id', 'TEXT')
var standardId = tableRead.column('m', 'tid', 'TEXT')
fieldCounts.where(tableRead.equal(tableRead.column('c', 'is_del', 'NUMBER'), 0))
fieldCounts.leftJoin('da_metadata_t', 'm', 'tid', 'data_standard_id', [
    tableRead.equal(tableRead.column('m', 'is_del', 'NUMBER'), 0),
    tableRead.equal(tableRead.column('m', 'publish_status', 'NUMBER'), 1)
])
var totalsByTable = activeTables()
totalsByTable.leftJoinGrouped(fieldCounts, 'field_counts', 'table_id', 'tid', {
    table_id: fieldTableId,
    total_fields: tableRead.count(),
    mapped_fields: tableRead.countIf(tableRead.not(tableRead.isNull(standardId)))
}, [fieldTableId])
var totals = totalsByTable.grouped({
    total_fields: tableRead.coalesce([tableRead.sum(tableRead.column('field_counts', 'total_fields', 'NUMBER')), 0]),
    mapped_fields: tableRead.coalesce([tableRead.sum(tableRead.column('field_counts', 'mapped_fields', 'NUMBER')), 0])
}, [])[0]

var totalFields = totals.total_fields::long
var mappedFields = totals.mapped_fields::long
var coverage = BigDecimal.ZERO.setScale(2)
if (totalFields > 0) {
    coverage = BigDecimal.valueOf(mappedFields).multiply(BigDecimal.valueOf(100L))
        .divide(BigDecimal.valueOf(totalFields), 2, RoundingMode.HALF_UP)
}
return {
    sources: sources,
    entities: entities,
    standards: standards,
    coverage: {totalFields: totalFields, mappedFields: mappedFields, coverage: coverage}
}
