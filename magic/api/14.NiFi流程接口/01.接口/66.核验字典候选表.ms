{
  "properties": {},
  "id": "nifi_map_dict_tables_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "66.核验字典候选表",
  "path": "/field-mapping/dictionary-tables",
  "description": "分页读取当前数据源的有效登记字典表。",
  "parameters": [],
  "options": [],
  "method": "HEAD",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingDictionaryTables' as read

// HEAD /nifi/api/field-mapping/dictionary-tables 分页读取当前数据源的有效登记字典表。
// GET 和 HEAD 共用 @/common/nifiMappingDictionaryTables 的业务读取。

return read()
