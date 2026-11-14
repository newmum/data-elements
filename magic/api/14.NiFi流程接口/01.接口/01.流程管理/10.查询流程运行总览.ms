{
  "properties": {},
  "id": "nifi_runtime_overview_get_v1",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "10.查询流程运行总览",
  "path": "/runtime-overview",
  "method": "GET",
  "description": "统计认证行业租户的有效流程，按 sortField 和 sortDir 排序返回前五条流程。返回原始 total、running、failed、flows，保留非法排序参数的错误格式。只读业务库中的已保存状态，不请求 NiFi。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineReadHttp' as readPipeline

// GET /nifi/api/pipelines/runtime-overview 统计本租户有效流程的已保存状态。
// sortField 和 sortDir 决定明细顺序，返回原始 total、running、failed、flows，无写入或 NiFi 调用。

// 总览复用数据库聚合与固定五条明细窗口，不逐流程查询。
return readPipeline(false)
