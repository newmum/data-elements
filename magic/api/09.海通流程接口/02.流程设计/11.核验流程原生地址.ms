{
  "properties": {},
  "id": "nifi_pipeline_ui_link_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "11.核验流程原生地址",
  "path": "/link",
  "method": "HEAD",
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

// HEAD /nifi/flow/link 核验流程原生地址。 查询参数 id。
// HEAD /nifi/flow/link 读取当前行业租户已部署流程的 NiFi 原生页面地址。
// GET 与 HEAD 共用 @/common/nifiPipelineUiLink，其中完成租户检查和响应组装。

return read(param.id)
