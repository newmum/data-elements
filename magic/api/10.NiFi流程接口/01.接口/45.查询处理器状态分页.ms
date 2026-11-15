{
  "properties": {},
  "id": "nifi_processor_page_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "45.查询处理器状态分页",
  "path": "/pipelines/{id}/status/page",
  "method": "GET",
  "description": "从当前流程的一次原生快照分页读取处理器状态。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiProcessorStatusPage' as read
// GET /nifi/api/pipelines/{id}/status/page 从当前流程的一次原生快照分页读取处理器状态。
// GET 与 HEAD 共用 @/common/nifiProcessorStatusPage，其中完成租户检查和响应组装。

return read(path.id)
