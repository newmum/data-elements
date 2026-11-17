{
  "id": "collect_fn_tables_v1",
  "groupId": "collection_fn_group_v1",
  "name": "08.定义采集清单读取列",
  "path": "/tables",
  "parameters": [
    {
      "name": "alias",
      "dataType": "Object",
      "required": false,
      "description": "结构化查询别名"
    }
  ],
  "description": "两种清单共用表头列。"
}
================================
import tableRead

// /collect/tables 只读取登记界面展示使用的表头字段。
var result = {}
for (name in ['tid', 'table_name', 'table_name_cn', 'table_type']) {
    result[name] = tableRead.column(alias, name, 'TEXT')
}
result.table_comment = tableRead.column(alias, 'table_comment', 'LOB')
result.field_count = tableRead.column(alias, 'field_count', 'NUMBER')
result.created_time = tableRead.column(alias, 'created_time', 'TIME')
result.updated_time = tableRead.column(alias, 'updated_time', 'TIME')
return result
