{
  "properties": {},
  "id": "nifi_map_dict_cols_head_v1",
  "script": null,
  "groupId": "e156354f7d79565aa8ed30f16bf35ed1",
  "name": "04.核验字典候选字段",
  "path": "/fields",
  "description": "读取当前登记字典表的有效字段。",
  "parameters": [],
  "options": [],
  "method": "HEAD",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingDictionaryColumns' as read

// HEAD /nifi/dict/fields 核验字典候选字段。
// HEAD /nifi/dict/fields 读取当前登记字典表的有效字段。
// GET 和 HEAD 共用 @/common/nifiMappingDictionaryColumns 的业务读取。

return read()
