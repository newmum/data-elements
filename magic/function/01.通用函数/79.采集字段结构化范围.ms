{
  "properties": {},
  "id": "e30966d29ca7423fa7cde295f8918b28",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "79.采集字段结构化范围",
  "path": "/metadataColumnsScope",
  "description": "清单、分页与批量读取共用字段关联和数据范围。",
  "returnType": "Object",
  "parameters": [
    {
      "name": "tenantId",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "scope",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import tableRead
import '@/common/metadataCatalogScope' as catalogScope

// 字段清单、分页和批读共用字段、采集表、数据源的有效性和数据范围。
var column = (alias, name, type) => tableRead.column(alias, name, type)
var active = alias => tableRead.equal(column(alias, 'is_del', 'NUMBER'), 0)
var query = tableRead.from('db_table_column_t', 'c', tenantId)
query.leftJoin('db_table_t', 't', 'tid', 'table_id', [active('t')])
query.leftJoinFrom('t', 'db_datasource_t', 'd', 'tid', 'datasource_id', [active('d')])
query.where(tableRead.all([
    active('c'), tableRead.not(tableRead.isNull(column('d', 'tid', 'TEXT'))), catalogScope('d', scope)
]))
var projection = {
    tid: column('c', 'tid', 'TEXT'), table_id: column('c', 'table_id', 'TEXT'),
    column_name: column('c', 'column_name', 'TEXT'), column_comment: column('c', 'column_comment', 'LOB'),
    data_type: column('c', 'data_type', 'TEXT'), column_type: column('c', 'column_type', 'TEXT'),
    length: column('c', 'length', 'NUMBER'), nullable: column('c', 'nullable', 'NUMBER'),
    primary_key: column('c', 'primary_key', 'NUMBER'), ordinal_position: column('c', 'ordinal_position', 'NUMBER')
}
return {query: query, projection: projection}
