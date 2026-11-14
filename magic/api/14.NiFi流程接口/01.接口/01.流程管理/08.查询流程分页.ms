{
  "properties": {},
  "id": "nifi_pipeline_page_get_v1",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "08.查询流程分页",
  "path": "/page",
  "method": "GET",
  "description": "分页读取认证行业租户的有效 NiFi 流程。page 缺省 1，size 缺省 20 并限制到 1 至 100。支持 sortField、sortDir 和按名称及说明搜索的 q。返回原始 list、total、page、size，非法整数沿用参数类型错误契约。只读业务库，不请求 NiFi。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineReadHttp' as readPipeline

// GET /nifi/api/pipelines/page 按 page、size、sortField、sortDir 和 q 读取本租户的流程。
// 返回原始 list、total、page、size，数据在数据库端过滤、排序和分页，无写入或 NiFi 调用。

// 与 HEAD 共用参数解析、权限校验及原始响应格式。
return readPipeline(true)
