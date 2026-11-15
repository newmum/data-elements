{
  "properties": {},
  "id": "nifi_mapping_dict_tables_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "69.海通字典候选表",
  "path": "/nifiMappingDictionaryTables",
  "description": "分页读取当前数据源的有效登记字典表。",
  "parameters": [],
  "options": []
}
================================
import '@/common/nifiMappingReadHttp' as readHttp
import com.linewell.dataelement.mapping.core.MappingException
import java.lang.IllegalStateException
import java.lang.Math
import org.springframework.http.ResponseEntity
import tableRead
import tenantRuntime

// @/common/nifiMappingDictionaryTables 为 GET 和 HEAD 分页读取当前租户已授权字典数据源的登记表。
// 数据接入使用行业租户范围。字面搜索保留数据库排序规则，按表名和主键稳定分页。

return readHttp((text, numeric, missing, invalid) => {
    var datasourceId = text('datasourceId', null)
    if (datasourceId == null) {
        return missing('datasourceId')
    }
    var page = numeric('page', 1)
    if (page == null) {
        return invalid('page')
    }
    var size = numeric('size', 20)
    if (size == null) {
        return invalid('size')
    }
    var tenantId = tenantRuntime.id()
    if (datasourceId.isBlank()) {
        throw new MappingException('RESOURCE_FORBIDDEN', '请选择当前授权数据源')
    }
    var source = tableRead.from('db_datasource_t', 's', tenantId)
    var sourceId = tableRead.column('s', 'tid', 'TEXT')
    source.where(tableRead.equal(sourceId, datasourceId))
    source.where(tableRead.equal(tableRead.column('s', 'is_del', 'NUMBER'), 0))
    if (source.rows({id: sourceId}, 1).isEmpty()) {
        throw new IllegalStateException('字典数据源不存在或无访问权限')
    }
    page = Math.max(1, page)
    size = Math.max(1, Math.min(100, size))
    var query = tableRead.from('db_table_t', 't', tenantId)
    query.where(tableRead.equal(tableRead.column('t', 'datasource_id', 'TEXT'), datasourceId))
    query.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
    query.where(tableRead.greaterThan(tableRead.column('t', 'asset_status', 'NUMBER'), 0))
    var projection = {tid: tableRead.column('t', 'tid', 'TEXT'), table_name: tableRead.column('t', 'table_name', 'TEXT'),
        table_name_cn: tableRead.column('t', 'table_name_cn', 'TEXT')}
    var search = text('search', '')
    if (!search.isBlank()) {
        if (search.length() > 128) {
            throw new MappingException('BUDGET_EXCEEDED', '表搜索文字超过128字符')
        }
        query.where(tableRead.any([tableRead.containsLiteral(projection.table_name, search.trim()),
            tableRead.containsLiteral(projection.table_name_cn, search.trim())]))
    }
    var result = query.page(projection, projection.table_name, 'asc', 'tid', 'asc', page, size, true)
    result.page = page
    result.size = size
    return ResponseEntity.ok(result)
})
