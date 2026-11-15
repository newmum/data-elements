{
  "properties": {},
  "id": "nifi_mapping_plan_scope_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "75.NiFi映射计划范围",
  "path": "/nifiMappingPlanScope",
  "description": "核对可信租户流程和逐项资源引用。",
  "parameters": [
    {
      "name": "pipelineId",
      "description": "流程标识",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "tenantId",
      "description": "可信租户标识",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiFindCanvas' as findCanvas
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineResourceReferences
import com.linewell.dataelement.mapping.core.MappingException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import java.util.ArrayList
import java.util.LinkedHashSet
import tableRead

// @/common/nifiMappingPlanScope 为计划列表、详情及计划恢复核对可信租户和关联资产。
// 关联任务和登记对象各一次有界预取，按 ID 确认归属，独立画布可无来源登记。

var forbidden = () => new MappingException('RESOURCE_FORBIDDEN', '流程或其引用资源不存在或不属于当前租户')
if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
    throw forbidden()
}
var canvas = findCanvas(pipelineId, tenantId)
if (canvas == null) {
    throw forbidden()
}
var references = PipelineResourceReferences.from(canvas)
var sourceIds = references.datasourceIds()
var tableIds = references.tableIds()
var task = tableRead.from('data_access_agg_task_t', 't', tenantId)
task.where(tableRead.equal(tableRead.column('t', 'pipeline_id', 'TEXT'), pipelineId))
task.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
var tasks = task.rows({source_db_id: tableRead.column('t', 'source_db_id', 'TEXT'),
    target_db_id: tableRead.column('t', 'target_db_id', 'TEXT'), source_table_id: tableRead.column('t', 'source_table_id', 'TEXT'),
    target_table_id: tableRead.column('t', 'target_table_id', 'TEXT')}, 101)
if (tasks.size() > 100) {
    throw new MappingException('BUDGET_EXCEEDED', '流程关联任务超过100项')
}
for (row in tasks) {
    PipelineResourceReferences.addTask(canvas, row, references)
}
if (tableIds.size() > 200 || sourceIds.size() > 200) {
    throw new MappingException('BUDGET_EXCEEDED', '流程引用资源超过200项')
}
if (!tableIds.isEmpty()) {
    var query = tableRead.from('db_table_t', 'r', tenantId)
    var id = tableRead.column('r', 'tid', 'TEXT')
    query.where(tableRead.in(id, new ArrayList(tableIds)))
    var rows = query.rows({id: id, source: tableRead.column('r', 'datasource_id', 'TEXT')}, 200)
    var found = new LinkedHashSet()
    for (row in rows) {
        found.add(row.id)
        if (row.source != null && !row.source.isBlank() && row.source != 'null') {
            sourceIds.add(row.source.trim())
        }
    }
    if (!found.equals(tableIds)) {
        throw forbidden()
    }
}
if (sourceIds.isEmpty()) {
    if (!tableIds.isEmpty()) {
        throw forbidden()
    }
    return canvas
}
if (sourceIds.size() > 200) {
    throw new MappingException('BUDGET_EXCEEDED', '流程引用数据源超过200项')
}
var query = tableRead.from('db_datasource_t', 's', tenantId)
var id = tableRead.column('s', 'tid', 'TEXT')
query.where(tableRead.in(id, new ArrayList(sourceIds)))
query.where(tableRead.equal(tableRead.column('s', 'is_del', 'NUMBER'), 0))
var sources = query.rows({id: id}, 200)
var found = new LinkedHashSet()
for (row in sources) {
    found.add(row.id)
}
if (!found.equals(sourceIds)) {
    throw forbidden()
}
return canvas
