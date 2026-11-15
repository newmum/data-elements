{
  "properties": {},
  "id": "nifi_map_templates_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "59.查询映射模板版本",
  "path": "/field-mapping/templates",
  "description": "分页读取当前租户映射模板版本。",
  "parameters": [],
  "options": [],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingTemplates' as read

// GET /nifi/api/field-mapping/templates 分页读取当前租户映射模板版本。
// GET 和 HEAD 共用 @/common/nifiMappingTemplates 的业务读取。

return read()
