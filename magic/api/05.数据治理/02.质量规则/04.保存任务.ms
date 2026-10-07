{
  "properties": {},
  "id": "a1000000000000000000000000000004",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "04.保存任务",
  "path": "/task/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"taskName\":\"常住人口基础质量检查\",\"taskCode\":\"DQ_POLICE_POPULATION\",\"datasourceId\":\"dq_demo_source_20260802\",\"tableId\":\"dq_demo_population_meta\",\"shardKey\":\"person_id\",\"shardCount\":8,\"sampleLimit\":200,\"triggerMode\":\"MANUAL\",\"rules\":[{\"ruleName\":\"姓名非空\",\"ruleType\":\"NOT_EMPTY\",\"columnName\":\"person_name\",\"severity\":\"HIGH\",\"weight\":20,\"parameters\":{}}]}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/task/save；写入 dq_quality_task_t 与 dq_quality_task_rule_t；保存真实可执行任务，任意SQL不在允许模型内；任务保存主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：04.保存任务
// 业务说明：POST /dwm/quality/task/save；写入 dq_quality_task_t 与 dq_quality_task_rule_t；保存真实可执行任务，任意SQL不在允许模型内；任务保存主接口。

import tenantRuntime
import dataQuality

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 先检查必需资料；规则和任务在 Java 仓储的一次事务中保存。
if (body == null) { exit 400, '请填写质检任务' }
if (body?.taskName == null || ('' + body.taskName).trim() == '') { exit 400, '任务名称不能为空' }
var taskId = dataQuality.persistTask(body)
// 返回仓储生成的任务标识及最新规则配置。
return dataQuality.taskDetail(taskId)
