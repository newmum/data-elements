{
  "properties": {},
  "id": "nifi_mapping_plans_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "64.海通映射计划历史",
  "path": "/nifiMappingPlans",
  "description": "分页读取当前租户授权流程的版本历史。",
  "parameters": [],
  "options": []
}
================================
import '@/common/nifiMappingPlanScope' as planScope
import '@/common/nifiMappingReadHttp' as readHttp
import com.linewell.dataelement.mapping.core.MappingException
import java.lang.Math
import org.springframework.http.ResponseEntity
import tableRead
import tenantRuntime

// @/common/nifiMappingPlans 为 GET 和 HEAD 分页读取当前租户已授权画布的计划历史。
// 先校验参数和画布引用，再在数据库排序及分页。保留原始字段与行序号。

return readHttp((text, numeric, missing, invalid) => {
    var pipelineId = text('pipelineId', null)
    if (pipelineId == null) {
        return missing('pipelineId')
    }
    var page = numeric('page', 1)
    if (page == null) {
        return invalid('page')
    }
    var size = numeric('size', 20)
    if (size == null) {
        return invalid('size')
    }
    page = Math.max(1, page)
    size = Math.max(1, Math.min(100, size))
    var tenantId = tenantRuntime.id()
    planScope(pipelineId, tenantId)

    var query = tableRead.from('data_access_mapping_plan_t', 'p', tenantId)
    query.where(tableRead.equal(tableRead.column('p', 'pipeline_id', 'TEXT'), pipelineId))
    var nodeId = text('nodeId', null)
    var projection = {tid: tableRead.column('p', 'tid', 'TEXT'), node_id: tableRead.column('p', 'node_id', 'TEXT'),
        plan_revision: tableRead.column('p', 'plan_revision', 'NUMBER'), draft_revision: tableRead.column('p', 'draft_revision', 'NUMBER'),
        plan_hash: tableRead.column('p', 'plan_hash', 'TEXT'), engine_version: tableRead.column('p', 'engine_version', 'TEXT'),
        created_by: tableRead.column('p', 'created_by', 'TEXT'), created_time: tableRead.column('p', 'created_time', 'TIME')}
    if (nodeId != null && !nodeId.isBlank()) {
        query.where(tableRead.equal(projection.node_id, nodeId))
    }
    var field = text('sortField', 'created_time')
    if (!['plan_revision', 'created_time', 'node_id'].contains(field)) {
        throw new MappingException('INVALID_SORT', '计划历史排序字段不支持')
    }
    var direction = text('sortDir', 'desc') == 'asc' ? 'asc' : 'desc'
    var result = query.page(projection, projection[field], direction, 'tid', 'asc', page, size, true)
    var rank = (page::long - 1L) * size::long
    for (row in result.rows) {
        rank = rank + 1L
        row.row_no = rank
    }
    result.page = page
    result.size = size
    return ResponseEntity.ok(result)
})
