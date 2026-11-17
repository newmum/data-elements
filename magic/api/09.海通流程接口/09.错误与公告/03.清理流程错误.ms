{
  "properties": {},
  "id": "nifi_pipe_error_clear_post_v1",
  "script": null,
  "groupId": "65f6980fe7f15f87b5db13b102130ac7",
  "name": "03.清理流程错误",
  "path": "/clear",
  "method": "POST",
  "description": "清理当前认证行业租户指定流程的原生公告和本地错误快照，返回两项操作结果。在共享锁中按查询参数 id 一次核对归属。流程不可见返回 404，原生协议错误时报告降级。",
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
import '@/common/nifiFlowErrorHttp' as flowErrors

// POST /nifi/event/clear 清理流程错误。 查询参数 id。
// POST /nifi/event/clear 清理当前认证行业租户指定流程的原生公告和本地错误快照，返回两项操作结果。
// 在共享锁中按路径 id 一次核对归属。流程不可见返回 404，原生协议错误时报告降级。

return flowErrors(param.id, 'clear')
