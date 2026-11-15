{
  "properties": {},
  "id": "nifi_map_templates_get_v1",
  "script": null,
  "groupId": "94cec97bdd3858bdb107530b5facca22",
  "name": "01.查询映射模板版本",
  "path": "/list",
  "description": "分页读取当前租户映射模板版本。",
  "parameters": [],
  "options": [],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingTemplates' as read

// GET /nifi/template/list 查询映射模板版本。
// GET /nifi/template/list 分页读取当前租户映射模板版本。
// GET 和 HEAD 共用 @/common/nifiMappingTemplates 的业务读取。

return read()
