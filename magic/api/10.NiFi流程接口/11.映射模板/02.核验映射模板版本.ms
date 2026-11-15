{
  "properties": {},
  "id": "nifi_map_templates_head_v1",
  "script": null,
  "groupId": "94cec97bdd3858bdb107530b5facca22",
  "name": "02.核验映射模板版本",
  "path": "/list",
  "description": "分页读取当前租户映射模板版本。",
  "parameters": [],
  "options": [],
  "method": "HEAD",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingTemplates' as read

// HEAD /nifi/template/list 核验映射模板版本。
// HEAD /nifi/template/list 分页读取当前租户映射模板版本。
// GET 和 HEAD 共用 @/common/nifiMappingTemplates 的业务读取。

return read()
