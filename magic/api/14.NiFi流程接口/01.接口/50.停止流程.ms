{
  "properties": {},
  "id": "nifi_pipeline_stop_post_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "50.停止流程",
  "path": "/pipelines/{id}/stop",
  "method": "POST",
  "description": "停止本租户流程并保留部署组。原生任务尚未结束时返回 202。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiStopCanvas' as stopCanvas

// POST /nifi/api/pipelines/{id}/stop 与登记表共用 @/common/nifiStopCanvas。
// 共用函数在共享 NiFi 锁内校验租户、停止原生组并同步流程和接入任务。

return stopCanvas(path.id)
