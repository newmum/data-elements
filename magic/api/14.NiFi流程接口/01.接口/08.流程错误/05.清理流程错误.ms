{
  "properties": {},
  "id": "nifi_flow_error_clear_post_v1",
  "script": null,
  "groupId": "nifi_flow_error_group_v1",
  "name": "05.清理流程错误",
  "path": "/delete/{id}/errors",
  "method": "POST",
  "description": "清理当前认证行业租户指定流程的原生公告和本地错误快照，返回两项操作结果。在共享锁中按路径 id 一次核对归属。流程不可见返回 404，原生协议错误时报告降级。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFlowErrorHttp' as flowErrors
// POST /nifi/api/flows/delete/{id}/errors 清理当前认证行业租户指定流程的原生公告和本地错误快照，返回两项操作结果。
// 在共享锁中按路径 id 一次核对归属。流程不可见返回 404，原生协议错误时报告降级。

return flowErrors(path.id, 'clear')
