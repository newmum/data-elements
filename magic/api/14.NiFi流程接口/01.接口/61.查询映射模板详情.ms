{
  "properties": {},
  "id": "nifi_map_template_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "61.查询映射模板详情",
  "path": "/field-mapping/templates/{id}",
  "description": "读取当前租户固定模板版本正文。",
  "parameters": [],
  "options": [],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingTemplateDetail' as read

// GET /nifi/api/field-mapping/templates/{id} 读取当前租户固定模板版本正文。
// GET 和 HEAD 共用 @/common/nifiMappingTemplateDetail 的业务读取。

return read(path.id)
