{
  "properties": {},
  "id": "nifi_pipeline_stop_post_v1",
  "script": null,
  "groupId": "56eecf4a0fcb5b6cac8df60e39962bb0",
  "name": "04.停止流程",
  "path": "/stop",
  "method": "POST",
  "description": "停止本租户流程并保留部署组。原生任务尚未结束时返回 202。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiStopCanvas' as stopCanvas

// POST /nifi/life/stop 停止流程。 查询参数 id。
// POST /nifi/life/stop 与登记表共用 @/common/nifiStopCanvas。
// 共用函数在共享 NiFi 锁内校验租户、停止原生组并同步流程和接入任务。

return stopCanvas(param.id)
