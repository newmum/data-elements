{
  "properties": {},
  "id": "nifi_runtime_overview_get_v1",
  "script": null,
  "groupId": "139cd4c65b0f5440a2d10b3650aeacab",
  "name": "01.查询流程运行总览",
  "path": "/overview",
  "method": "GET",
  "description": "统计认证行业租户的有效流程，按 sortField 和 sortDir 排序返回前五条流程。返回原始 total、running、failed、flows，保留非法排序参数的错误格式。只读业务库中的已保存状态，不请求 NiFi。 GET 与 HEAD 共用 @/common/nifiRuntimeOverview。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiRuntimeOverview' as readOverview

// GET /nifi/run/overview 查询流程运行总览。
// GET /nifi/run/overview 解析 sortField 和 sortDir，在认证行业库统计有效流程，并按白名单字段排序后读取前五条。
// GET 与 HEAD 共用 @/common/nifiRuntimeOverview，无写入或 NiFi 调用。

return readOverview()
