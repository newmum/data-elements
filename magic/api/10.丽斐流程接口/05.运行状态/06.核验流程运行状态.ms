{
  "properties": {},
  "id": "nifi_pipeline_status_head_v1",
  "script": null,
  "groupId": "139cd4c65b0f5440a2d10b3650aeacab",
  "name": "06.核验流程运行状态",
  "path": "/status",
  "method": "HEAD",
  "description": "读取当前行业租户的画布运行状态、部署指纹和错误历史。",
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
import '@/common/nifiPipelineStatus' as read

// HEAD /nifi/run/status 核验流程运行状态。 查询参数 id。
// HEAD /nifi/run/status 读取当前行业租户的画布运行状态、部署指纹和错误历史。
// GET 与 HEAD 共用 @/common/nifiPipelineStatus，其中完成租户检查和响应组装。

return read(param.id)
