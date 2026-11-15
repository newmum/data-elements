{
  "properties": {},
  "id": "nifi_map_dict_cols_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "67.查询字典候选字段",
  "path": "/field-mapping/dictionary-columns",
  "description": "读取当前登记字典表的有效字段。",
  "parameters": [],
  "options": [],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingDictionaryColumns' as read

// GET /nifi/api/field-mapping/dictionary-columns 读取当前登记字典表的有效字段。
// GET 和 HEAD 共用 @/common/nifiMappingDictionaryColumns 的业务读取。

return read()
