{
  "properties": {},
  "id": "nifi_runtime_overview_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "20.核验流程运行总览",
  "path": "/pipelines/runtime-overview",
  "method": "HEAD",
  "description": "按 sortField 和 sortDir 核验认证行业租户的流程运行总览请求。参数校验、状态和响应头沿用 GET，按 HEAD 协议不传输响应体。只读业务库，不请求 NiFi。 GET 与 HEAD 共用 @/common/nifiRuntimeOverview。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiRuntimeOverview' as readOverview

// HEAD /nifi/api/pipelines/runtime-overview 按 GET 的排序、权限和查询规则返回状态与响应头，无响应体。
// GET 与 HEAD 共用 @/common/nifiRuntimeOverview，无写入或 NiFi 调用。

return readOverview()
