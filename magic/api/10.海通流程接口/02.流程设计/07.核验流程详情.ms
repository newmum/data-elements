{
  "properties": {},
  "id": "nifi_pipe_detail_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "07.核验流程详情",
  "path": "/detail",
  "method": "HEAD",
  "description": "按 GET 的租户和流程校验确认已保存画布的 HTTP 状态及响应头。id 取自路径，不可见或无法解析时返回 404。操作只读，HEAD 不发送响应体。",
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
import '@/common/nifiPipelineDetailHttp' as pipelineDetail

// HEAD /nifi/flow/detail 核验流程详情。 查询参数 id。
// HEAD /nifi/flow/detail 按详情契约核验当前租户的已保存画布，返回状态与响应头。
// id 来自路径。不可见或无法解析时返回 404。无业务写入或 NiFi 请求。

return pipelineDetail(param.id)
