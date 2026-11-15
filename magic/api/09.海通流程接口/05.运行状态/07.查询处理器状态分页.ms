{
  "properties": {},
  "id": "nifi_processor_page_get_v1",
  "script": null,
  "groupId": "139cd4c65b0f5440a2d10b3650aeacab",
  "name": "07.查询处理器状态分页",
  "path": "/nodes",
  "method": "GET",
  "description": "从当前流程的一次原生快照分页读取处理器状态。",
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
import '@/common/nifiProcessorStatusPage' as read

// GET /nifi/run/nodes 查询处理器状态分页。 查询参数 id。
// GET /nifi/run/nodes 从当前流程的一次原生快照分页读取处理器状态。
// GET 与 HEAD 共用 @/common/nifiProcessorStatusPage，其中完成租户检查和响应组装。

return read(param.id)
