{
  "id": "collect_fn_jobs_v1",
  "groupId": "collection_fn_group_v1",
  "name": "04.定义采集任务读取列",
  "path": "/jobs",
  "parameters": [
    {
      "name": "alias",
      "dataType": "Object",
      "required": false,
      "description": "结构化查询别名"
    },
    {
      "name": "withDeleted",
      "dataType": "Object",
      "required": false,
      "description": "是否读取历史差异详情"
    }
  ],
  "description": "单项与批量状态查询共用标量列，批量查询跳过大字段。"
}
================================
import tableRead

// /collect/jobs 返回任务状态的结构化列，不把大字段带入进度批量查询。
var columns = {}
for (name in ['tid', 'datasource_id', 'collection_mode', 'status', 'phase']) {
    columns[name] = tableRead.column(alias, name, 'TEXT')
}
for (name in ['scanned_count', 'persisted_count', 'total_count', 'added_count', 'unchanged_count', 'deleted_count']) {
    columns[name] = tableRead.column(alias, name, 'NUMBER')
}
for (name in ['created_time', 'started_time', 'finished_time']) {
    columns[name] = tableRead.column(alias, name, 'TIME')
}
columns.error_message = tableRead.column(alias, 'error_message', 'LOB')
if (withDeleted) {
    columns.deleted_detail = tableRead.column(alias, 'deleted_detail', 'LOB')
}
return columns
