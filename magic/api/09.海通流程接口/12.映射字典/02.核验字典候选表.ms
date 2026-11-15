{
  "properties": {},
  "id": "nifi_map_dict_tables_head_v1",
  "script": null,
  "groupId": "e156354f7d79565aa8ed30f16bf35ed1",
  "name": "02.核验字典候选表",
  "path": "/tables",
  "description": "分页读取当前数据源的有效登记字典表。",
  "parameters": [],
  "options": [],
  "method": "HEAD",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingDictionaryTables' as read

// HEAD /nifi/dict/tables 核验字典候选表。
// HEAD /nifi/dict/tables 分页读取当前数据源的有效登记字典表。
// GET 和 HEAD 共用 @/common/nifiMappingDictionaryTables 的业务读取。

return read()
