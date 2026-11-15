{
  "properties": {},
  "id": "nifi_pipeline_ui_link_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "42.核验流程原生地址",
  "path": "/pipelines/{id}/ui-link",
  "method": "HEAD",
  "description": "读取当前行业租户已部署流程的 NiFi 原生页面地址。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineUiLink' as read
// HEAD /nifi/api/pipelines/{id}/ui-link 读取当前行业租户已部署流程的 NiFi 原生页面地址。
// GET 与 HEAD 共用 @/common/nifiPipelineUiLink，其中完成租户检查和响应组装。

return read(path.id)
