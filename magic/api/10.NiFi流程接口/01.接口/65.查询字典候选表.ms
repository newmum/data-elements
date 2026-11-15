{
  "properties": {},
  "id": "nifi_map_dict_tables_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "65.查询字典候选表",
  "path": "/field-mapping/dictionary-tables",
  "description": "分页读取当前数据源的有效登记字典表。",
  "parameters": [],
  "options": [],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingDictionaryTables' as read

// GET /nifi/api/field-mapping/dictionary-tables 分页读取当前数据源的有效登记字典表。
// GET 和 HEAD 共用 @/common/nifiMappingDictionaryTables 的业务读取。

return read()
