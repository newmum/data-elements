{
  "properties": {},
  "id": "nifi_map_templates_get_v1",
  "script": null,
  "groupId": "94cec97bdd3858bdb107530b5facca22",
  "name": "01.查询映射模板版本",
  "path": "/list",
  "description": "分页读取当前租户映射模板版本。",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingReadHttp' as readHttp
import com.linewell.dataelement.mapping.core.MappingException
import java.lang.Math
import org.springframework.http.ResponseEntity
import tableRead
import tenantRuntime

// GET /nifi/template/list。按当前行业、资源类型和查询条件分页读取不可变模板版本。

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
