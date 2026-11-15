{
  "properties": {},
  "id": "nifi_map_template_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "62.核验映射模板详情",
  "path": "/field-mapping/templates/{id}",
  "description": "读取当前租户固定模板版本正文。",
  "parameters": [],
  "options": [],
  "method": "HEAD",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingTemplateDetail' as read

// HEAD /nifi/api/field-mapping/templates/{id} 读取当前租户固定模板版本正文。
// GET 和 HEAD 共用 @/common/nifiMappingTemplateDetail 的业务读取。

return read(path.id)
