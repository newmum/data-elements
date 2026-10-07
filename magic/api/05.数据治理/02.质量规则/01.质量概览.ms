{
  "properties": {},
  "id": "a1000000000000000000000000000001",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "01.质量概览",
  "path": "/summary",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/summary；只读；汇总当前租户质检任务、近30日实例、质量得分与维度指标；数据质量首页主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：01.质量概览
// 业务说明：POST /dwm/quality/summary；只读；汇总当前租户质检任务、近30日实例、质量得分与维度指标；数据质量首页主接口。

import tenantRuntime
import dataQuality

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 只统计当前会话所在租户的质量任务、执行结果和问题。
return dataQuality.summary()
