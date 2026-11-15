{
  "properties": {},
  "id": "nifi_pipe_error_bulletin_get_v1",
  "script": null,
  "groupId": "65f6980fe7f15f87b5db13b102130ac7",
  "name": "03.查询流程实时公告",
  "path": "/live",
  "method": "GET",
  "description": "读取当前认证行业租户指定流程的实时错误和警告，按 NiFi 原顺序返回数组。按查询参数 id 一次核对流程归属，流程不可见返回 404，未部署返回 400。此操作只读。",
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

// GET /nifi/event/live 查询流程实时公告。 查询参数 id。
// GET /nifi/event/live 读取当前认证行业租户指定流程的实时错误和警告，按 NiFi 原顺序返回数组。
// 按路径 id 一次核对流程归属，流程不可见返回 404，未部署返回 400。此操作只读。

return flowErrors(param.id, 'bulletins')
