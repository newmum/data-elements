{
  "properties": {},
  "id": "nifi_mapping_plan_read_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "72.丽斐批量读取映射计划",
  "path": "/nifiReadMappingPlans",
  "description": "批量读取和核对当前租户、画布下的不可变计划。",
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
    },
    {
      "name": "ids",
      "description": "计划标识集合",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.linewell.dataelement.mapping.core.MappingEngine
import com.linewell.dataelement.mapping.core.MappingException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import tableRead

// @/common/nifiReadMappingPlans 为计划详情、草稿恢复和已发布版本启动读取同一批不可变计划。
// 调用前由入口核对画布权限。查询同时限制可信租户、画布与每个计划标识。

if (ids == null || ids.size() > 100) {
    throw new MappingException('BUDGET_EXCEEDED', '计划引用最多100项')
}
var requested = new LinkedHashSet(ids)
for (id in requested) {
    if (id == null || id.isBlank()) {
        throw new MappingException('RESOURCE_FORBIDDEN', '计划引用无效')
    }
}
var result = new LinkedHashMap()
if (requested.isEmpty()) {
    return result
}
var query = tableRead.from('data_access_mapping_plan_t', 'p', tenantId)
var tid = tableRead.column('p', 'tid', 'TEXT')
query.where(tableRead.in(tid, new ArrayList(requested)))
query.where(tableRead.equal(tableRead.column('p', 'pipeline_id', 'TEXT'), pipelineId))
var content = tableRead.column('p', 'plan_content', 'LOB')
var length = tableRead.textLength(content)
var oversized = tableRead.greaterThan(length, 2097152)
var excessiveTotal = tableRead.greaterThan(tableRead.overAll(tableRead.sum(length)), 33554432)
var rows = query.rows({id: tid, content: tableRead.when(tableRead.any([oversized, excessiveTotal]), null, content),
    tooLarge: tableRead.when(oversized, 1, 0), tooManyBytes: tableRead.when(excessiveTotal, 1, 0),
    hash: tableRead.column('p', 'plan_hash', 'TEXT')}, 101)
var bytes = 0L
var mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
for (row in rows) {
    if (row.tooLarge == 1) {
        throw new MappingException('BUDGET_EXCEEDED', '执行计划超过2MiB')
    }
    if (row.tooManyBytes == 1) {
        throw new MappingException('BUDGET_EXCEEDED', '批量计划内容超过32MiB')
    }
    if (row.content == null) {
        throw new MappingException('RESOURCE_CORRUPTED', '计划内容缺失')
    }
    var size = row.content.getBytes(StandardCharsets.UTF_8).length
    if (size > 2097152) {
        throw new MappingException('BUDGET_EXCEEDED', '执行计划超过2MiB')
    }
    bytes = bytes + size
    if (bytes > 33554432) {
        throw new MappingException('BUDGET_EXCEEDED', '批量计划内容超过32MiB')
    }
    var document = null
    try {
        document = mapper.readTree(row.content)
    } catch (failure) {
        throw new MappingException('RESOURCE_CORRUPTED', '版本内容无法解析')
    }
    if (document == null || !document.isObject() || MappingEngine.hash(document) != row.hash) {
        throw new MappingException('RESOURCE_CORRUPTED', '计划版本摘要不一致')
    }
    if (!requested.contains(row.id) || result.putIfAbsent(row.id, document) != null) {
        throw new MappingException('RESOURCE_CORRUPTED', '计划版本标识重复或不属于请求范围')
    }
}
if (!result.keySet().equals(requested)) {
    throw new MappingException('RESOURCE_FORBIDDEN', '包含不存在、其他租户或其他流程的计划版本')
}
return result
