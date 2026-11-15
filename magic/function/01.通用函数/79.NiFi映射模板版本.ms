{
  "properties": {},
  "id": "nifi_mapping_templates_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "79.NiFi映射模板版本",
  "path": "/nifiMappingTemplates",
  "description": "分页读取当前租户映射模板版本。",
  "parameters": [],
  "options": []
}
================================
import '@/common/nifiMappingReadHttp' as readHttp
import com.linewell.dataelement.mapping.core.MappingException
import java.lang.Math
import org.springframework.http.ResponseEntity
import tableRead
import tenantRuntime

// @/common/nifiMappingTemplates 为 GET 和 HEAD 分页读取当前租户不可变模板版本。
// 接受原排序字段，按版本行 ID 稳定分页，不读取模板正文。

return readHttp((text, numeric, missing, invalid) => {
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
    var field = text('sortField', 'created_time')
    if (!['resource_key', 'version_no', 'created_time'].contains(field)) {
        throw new MappingException('INVALID_SORT', '模板排序字段不支持')
    }

    // 业务读取只使用可信租户和 TEMPLATE 资源类型。
    var query = tableRead.from('data_access_mapping_resource_t', 'r', tenantId)
    query.where(tableRead.equal(tableRead.column('r', 'resource_kind', 'TEXT'), 'TEMPLATE'))
    var projection = {tid: tableRead.column('r', 'tid', 'TEXT'), resource_key: tableRead.column('r', 'resource_key', 'TEXT'),
        version_no: tableRead.column('r', 'version_no', 'NUMBER'), resource_hash: tableRead.column('r', 'resource_hash', 'TEXT'),
        created_time: tableRead.column('r', 'created_time', 'TIME')}
    var direction = text('sortDir', 'desc') == 'asc' ? 'asc' : 'desc'
    var result = query.page(projection, projection[field], direction, 'tid', 'asc', page, size, true)
    var rank = (page::long - 1L) * size::long
    for (row in result.rows) {
        rank = rank + 1L
        row.row_no = rank
    }
    return ResponseEntity.ok(result)
})
