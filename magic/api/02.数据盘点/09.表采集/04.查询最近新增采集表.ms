{
  "id": "collect_additions_v1",
  "groupId": "collection_api_group_v1",
  "name": "04.查询最近新增采集表",
  "path": "/additions",
  "method": "GET",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "按可信行业、来源及最近成功任务读取新增的采集表。"
}
================================
import '@/collect/pageargs' as pageArgs
import '@/collect/response' as respond
import '@/collect/scope' as visibleSources
import '@/collect/tablepage' as tablePage
import '@/collect/tables' as columns
import tableRead
import tenantRuntime

// GET /dst/collect/additions。按可信行业、来源及最近成功任务读取新增的采集表。

return respond(() => {
    var args = pageArgs()
    if (args.error != null) {
        return args.error
    }
    var projection = columns('t')
    var title = tableRead.coalesce([tableRead.blankAsNull(projection.table_name_cn), projection.table_name])
    var sorts = {tableName: projection.table_name, name: projection.table_name, tableNameCn: title,
        fieldCount: projection.field_count, recordCount: tableRead.column('t', 'record_count', 'NUMBER'),
        createdAt: projection.created_time, createdTime: projection.created_time,
        updatedAt: projection.updated_time, updatedTime: projection.updated_time}
    var key = args.sortField == '' ? 'tableName' : args.sortField
    var direction = args.sortDir == '' ? 'asc' : args.sortDir
    if (!sorts.containsKey(key)) {
        throw new IllegalArgumentException('不支持的排序字段：' + key)
    }
    if (!['asc', 'desc'].contains(direction)) {
        throw new IllegalArgumentException('排序方向只能是 asc 或 desc')
    }
    if (!args.source.matches('[A-Za-z0-9_-]{1,64}')) {
        throw new IllegalArgumentException('dbId 格式不正确')
    }
    var tenantId = tenantRuntime.id()
    if (visibleSources(tenantId, [args.source]).isEmpty()) {
        throw new IllegalArgumentException('数据源不存在或无访问权限')
    }

    var jobs = tableRead.from('metadata_table_collection_job_t', 'j', tenantId)
    jobs.where(tableRead.equal(tableRead.column('j', 'is_del', 'NUMBER'), 0))
    jobs.where(tableRead.equal(tableRead.column('j', 'datasource_id', 'TEXT'), args.source))
    jobs.where(tableRead.equal(tableRead.column('j', 'status', 'TEXT'), 'SUCCEEDED'))
    var latest = jobs.rows({started: tableRead.column('j', 'started_time', 'TIME'),
        finished: tableRead.column('j', 'finished_time', 'TIME')},
        [tableRead.column('j', 'created_time', 'TIME'), tableRead.column('j', 'tid', 'TEXT')], ['desc', 'desc'], 1, true)
    if (latest.isEmpty() || latest[0].started == null || latest[0].finished == null) {
        return tablePage({total: 0, rows: []}, args)
    }

    var tables = tableRead.from('db_table_t', 't', tenantId)
    tables.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
    tables.where(tableRead.equal(tableRead.column('t', 'datasource_id', 'TEXT'), args.source))
    tables.where(tableRead.any([tableRead.equal(projection.created_time, latest[0].started),
        tableRead.greaterThan(projection.created_time, latest[0].started)]))
    tables.where(tableRead.not(tableRead.greaterThan(projection.created_time, latest[0].finished)))
    return tablePage(tables.page(projection, sorts[key], direction, 'tid', args.pageNo, args.pageSize), args)
})
