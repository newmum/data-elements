{
  "properties": {},
  "id": "nifi_attribute_sync_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "53.核验原生属性差异",
  "path": "/pipelines/{id}/attribute-sync",
  "method": "HEAD",
  "description": "读取当前行业租户流程的原生属性差异及组件版本。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiAttributePreview' as preview

// HEAD /nifi/api/pipelines/{id}/attribute-sync 读取当前行业租户流程的原生属性差异及组件版本。
// GET 和 HEAD 共用 @/common/nifiAttributePreview 的租户校验及原生快照。

return preview(path.id)
