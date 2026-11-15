{
  "properties": {},
  "id": "nifi_pipeline_page_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "17.查询流程分页",
  "path": "/pipelines/page",
  "method": "GET",
  "description": "分页读取认证行业租户的有效 NiFi 流程。page 缺省 1，size 缺省 20 并限制到 1 至 100。支持 sortField、sortDir 和按名称及说明搜索的 q。返回原始 list、total、page、size，非法整数沿用参数类型错误契约。只读业务库，不请求 NiFi。 GET 与 HEAD 共用 @/common/nifiPipelinePage。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelinePage' as readPipelinePage

// GET /nifi/api/pipelines/page 解析 page、size、sortField、sortDir 和 q，经租户校验后按页返回有效流程。
// GET 与 HEAD 共用 @/common/nifiPipelinePage，无写入或 NiFi 调用。

return readPipelinePage()
