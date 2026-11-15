{
  "properties": {},
  "id": "nifi_field_map_caps_head_v1",
  "script": null,
  "groupId": "d49f0cc7292f543b89a960eed54923c1",
  "name": "04.核验字段映射能力",
  "path": "/caps",
  "description": "读取字段映射引擎的能力和参数。",
  "parameters": [],
  "options": [],
  "method": "HEAD",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFieldMappingCapabilities' as read

// HEAD /nifi/map/caps 核验字段映射能力。
// HEAD /nifi/map/caps 返回现用引擎能力及公开参数。
// GET 和 HEAD 共用 @/common/nifiFieldMappingCapabilities。

return read()
