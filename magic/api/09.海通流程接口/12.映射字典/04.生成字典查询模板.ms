{
  "properties": {},
  "id": "nifi_field_map_lookup_v1",
  "script": null,
  "groupId": "e156354f7d79565aa8ed30f16bf35ed1",
  "name": "04.生成字典查询模板",
  "path": "/lookup",
  "description": "按已授权的登记表生成兼容查询模板。",
  "parameters": [],
  "options": [
    {
      "name": "jsonBody",
      "value": "EXACT_TREE"
    }
  ],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiMappingReadHttp' as withHttp
import com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc.RegisteredJdbcDataSourceResolver as sources
import java.lang.IllegalArgumentException
import java.util.HashSet
import java.util.Map
import org.springframework.http.ResponseEntity
import tableRead
import tenantRuntime

// POST /nifi/dict/lookup 生成字典查询模板。
// POST /nifi/dict/lookup 生成兼容客户端使用的参数化字典查询模板。
// 使用可信租户、已登记数据源及表，同时检查两个字段。模板只含公开的资源标识。

return withHttp(() => {
    var type = 'java.util.Map'
    var wire = 'java.util.Map<java.lang.String, java.lang.Object>'
    var signature = wire + ' com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController.lookupTemplate(' + wire + ')'
    var input = readBody(body, Map, type, signature)
    var tenantId = tenantRuntime.id()
    var text = field => {
        var value = input.getOrDefault(field, '')
        return value == null ? 'null' : value.toString()
    }
    var tableId = text('tableId')
    var sourceId = text('datasourceId')
    var endpoint = sources.resolve(tenantId, sourceId, tableId)
    var key = text('keyColumn')
    var value = text('valueColumn')
    var columns = tableRead.from('db_table_column_t', 'c', tenantId)
    var name = tableRead.column('c', 'column_name', 'TEXT')
    columns.where(tableRead.equal(tableRead.column('c', 'table_id', 'TEXT'), tableId))
    columns.where(tableRead.equal(tableRead.column('c', 'is_del', 'NUMBER'), 0))
    columns.where(tableRead.in(name, [key, value]))
    var found = new HashSet()
    for (row in columns.rows({name: name}, 1000)) {
        found.add(row.name == null ? 'null' : row.name.toString())
    }
    if (!found.contains(key) || !found.contains(value)) {
        throw new IllegalArgumentException('字典查询字段不属于选定的已登记表')
    }
    return ResponseEntity.ok({sql: endpoint.lookupStatement(key, value), resultColumn: value, onMissing: 'NULL',
        dataSource: {datasourceId: sourceId}})
})
