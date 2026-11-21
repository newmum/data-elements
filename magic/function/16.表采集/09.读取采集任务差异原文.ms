{
  "id": "collect_fn_deleted_v1",
  "groupId": "collection_fn_group_v1",
  "name": "09.读取采集任务差异原文",
  "path": "/deleted",
  "parameters": [
    {
      "name": "tenantId",
      "dataType": "Object",
      "required": false,
      "description": "可信会话租户"
    },
    {
      "name": "source",
      "dataType": "Object",
      "required": false,
      "description": "已授权的数据源"
    },
    {
      "name": "successfulOnly",
      "dataType": "Object",
      "required": false,
      "description": "是否限定成功任务"
    }
  ],
  "description": "差异列表与快照排除共用最近任务的历史差异文档。"
}
================================
import tableRead

// /collect/deleted 只读取已授权来源的一个历史任务。快照仅使用成功任务的差异。
var jobs = tableRead.from('metadata_table_collection_job_t', 'j', tenantId)
jobs.where(tableRead.equal(tableRead.column('j', 'is_del', 'NUMBER'), 0))
jobs.where(tableRead.equal(tableRead.column('j', 'datasource_id', 'TEXT'), source))
if (successfulOnly) {
    jobs.where(tableRead.equal(tableRead.column('j', 'status', 'TEXT'), 'SUCCEEDED'))
}
var rows = jobs.rows({deleted: tableRead.column('j', 'deleted_detail', 'LOB')},
    [tableRead.column('j', 'created_time', 'TIME'), tableRead.column('j', 'tid', 'TEXT')], ['desc', 'desc'], 1, true)
return rows.isEmpty() || rows[0].deleted == null ? '[]' : rows[0].deleted + ''
