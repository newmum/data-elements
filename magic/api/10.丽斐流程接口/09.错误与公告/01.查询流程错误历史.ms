{
  "properties": {},
  "id": "nifi_pipe_error_history_get_v1",
  "script": null,
  "groupId": "65f6980fe7f15f87b5db13b102130ac7",
  "name": "01.查询流程错误历史",
  "path": "/errors",
  "method": "GET",
  "description": "读取当前认证行业租户指定流程的本地错误历史，按既有顺序返回原始快照数组。按查询参数 id 一次查询流程归属，流程不可见时返回空体 404。此操作只读。",
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

// GET /nifi/event/errors 查询流程错误历史。 查询参数 id。
// GET /nifi/event/errors 读取当前认证行业租户指定流程的本地错误历史，按既有顺序返回原始快照数组。
// 按路径 id 一次查询流程归属，流程不可见时返回空体 404。此操作只读。

return flowErrors(param.id, 'history')
