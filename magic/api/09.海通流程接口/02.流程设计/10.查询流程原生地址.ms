{
  "properties": {},
  "id": "nifi_pipeline_ui_link_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "10.查询流程原生地址",
  "path": "/link",
  "method": "GET",
  "description": "读取当前行业租户已部署流程的 NiFi 原生页面地址。",
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
import '@/common/nifiPipelineUiLink' as read

// GET /nifi/flow/link 查询流程原生地址。 查询参数 id。
// GET /nifi/flow/link 读取当前行业租户已部署流程的 NiFi 原生页面地址。
// GET 与 HEAD 共用 @/common/nifiPipelineUiLink，其中完成租户检查和响应组装。

return read(param.id)
