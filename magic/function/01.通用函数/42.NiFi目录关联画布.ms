{
  "properties": {},
  "id": "71a48eec67605a2d5da8ce93b2cc02cb",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "42.NiFi目录关联画布",
  "path": "/nifiCatalogPipeline",
  "description": "按目录的接入任务顺序一次关联读取本行业租户已保存画布，跳过缺失、已删除及无法解析画布。供模板与创建入口识别同一已保存设计。",
  "parameters": [
    {
      "name": "catalogId",
      "description": "当前请求中已授权的目录标识",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Dsl as CanvasDsl
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import java.lang.IllegalStateException
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import tableRead
import tenantRuntime

// @/common/nifiCatalogPipeline 根据可信行业库内的任务关联查找最早的有效画布。
// 单次最多读取 100 个候选，跳过损坏记录，保留原始画布设计和部署状态。
var tenantId = tenantRuntime.id()
var task = tableRead.from('data_access_agg_task_t', 'a', tenantId)
var text = (alias, field) => tableRead.column(alias, field, 'TEXT')
var number = (alias, field) => tableRead.column(alias, field, 'NUMBER')
var deleted = number('p', 'is_del')
var id = text('p', 'id')
task.leftJoin('nifi_pipeline_t', 'p', 'id', 'pipeline_id', [tableRead.any([
    tableRead.isNull(deleted), tableRead.not(tableRead.equal(deleted, 1))])])
task.where(tableRead.equal(text('a', 'data_catalog_id'), catalogId))
task.where(tableRead.equal(number('a', 'is_del'), 0))
task.where(tableRead.not(tableRead.isNull(id)))
var rows = task.rows({id: id, name: text('p', 'name'), description: tableRead.column('p', 'description', 'LOB'),
    createdAt: number('p', 'created_at'), updatedAt: number('p', 'updated_at'), dsl: tableRead.column('p', 'dsl_json', 'LOB'),
    processGroupId: text('p', 'nifi_process_group_id'), status: text('p', 'status'),
    deployedHash: text('p', 'last_deployed_hash'), deployedAt: number('p', 'last_deployed_at'), stoppedAt: number('p', 'last_stopped_at'),
    mapping: tableRead.column('p', 'node_mapping_json', 'LOB'), lastBulletinId: number('p', 'last_bulletin_id')},
    [tableRead.column('a', 'created_time', 'TIME'), text('a', 'tid')], ['asc', 'asc'], 101, true)
for (row in rows) {
    try {
        var dsl = row.dsl == null || row.dsl.isBlank() ? null : mapper.readValue(row.dsl, CanvasDsl)
        var mapping = row.mapping == null || row.mapping.isBlank() ? null : mapper.readValue(row.mapping, NifiNodeMapping)
        var status = PipelineStatus.SAVED
        if ('STOP'.equalsIgnoreCase(row.status)) {
            status = PipelineStatus.STOPPED
        } else {
            for (candidate in PipelineStatus.values()) {
                if (candidate.name().equalsIgnoreCase(row.status)) {
                    status = candidate
                    break
                }
            }
        }
        return new Pipeline(row.id, row.name, row.description, row.createdAt?.longValue(), row.updatedAt?.longValue(),
            dsl, row.processGroupId, status, row.deployedHash, row.deployedAt?.longValue(), row.stoppedAt?.longValue(),
            mapping, row.lastBulletinId?.longValue())
    } catch (failure) {
        var cause = failure
        while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
            cause = cause.getCause()
        }
        if (!(cause instanceof JsonProcessingException)) {
            throw failure
        }
    }
}
if (rows.size() > 100) {
    throw new IllegalStateException('目录关联的画布超过 100 个，请检查接入任务配置')
}
return null
