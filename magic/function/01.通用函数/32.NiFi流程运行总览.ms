{
  "properties": {},
  "id": "4a9311c67f104f589affeb51aa1e3a62",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "32.NiFi流程运行总览",
  "path": "/nifiRuntimeOverview",
  "createTime": 1794624308498,
  "updateTime": null,
  "parameters": [
    {
      "name": "sortField",
      "description": "任务名称、最近运行时间或更新时间，默认更新时间",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "sortDir",
      "description": "asc 或 desc，默认 desc",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": [],
  "description": "为海通流程运行总览统计认证行业租户的有效流程，返回总数、运行数、失败数和排序后的前五条流程。只读当前租户库，不请求 NiFi 或来源数据库。"
}
================================
import tableRead
import tableSort
import tenantRuntime

// @/common/nifiRuntimeOverview 为 GET /nifi/api/pipelines/runtime-overview 提供流程运行总览。
// sortField 和 sortDir 选择前五条流程的顺序，返回 total、running、failed 和 flows。
// 仅查询认证租户的有效流程及其已保存状态，不请求 NiFi 或来源库、不修改记录。
var tenantId = tenantRuntime.id()
var request = {
    sortField: sortField == null || sortField.isBlank() ? null : sortField,
    sortDir: sortDir == null || sortDir.isBlank() ? null : sortDir
}
var sortFields = {
    taskName: 'name', latestRunTime: 'last_deployed_at', updatedAt: 'updated_at'
}
var column = tableSort.field(request, sortFields, 'updatedAt')
var direction = tableSort.direction(request)

// 先在认证租户内统计完整有效集合，流程卡片的排序只作用于明细窗口。
var pipeline = tableRead.from('nifi_pipeline_t', 'p', tenantId)
var status = tableRead.column('p', 'status', 'TEXT')
pipeline.where(tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0))
var counts = pipeline.grouped({
    total: tableRead.count(),
    running: tableRead.countIf(tableRead.equal(status, 'RUNNING')),
    failed: tableRead.countIf(tableRead.in(status, ['RUN_ERROR', 'DEPLOY_FAILED']))
}, [])[0]

// 在数据库完整排序后截取前五条，空值在尾部，同值按流程 ID 稳定排列。
var id = tableRead.column('p', 'id', 'TEXT')
var order = tableRead.column('p', column, column == 'name' ? 'TEXT' : 'NUMBER')
var selected = pipeline.rows({
    id: id,
    taskName: tableRead.column('p', 'name', 'TEXT'),
    currentStatus: status,
    processGroupId: tableRead.column('p', 'nifi_process_group_id', 'TEXT'),
    latestRunTime: tableRead.column('p', 'last_deployed_at', 'NUMBER'),
    endTime: tableRead.column('p', 'last_stopped_at', 'NUMBER')
}, [order, id], [direction, 'asc'], 5)

// 只返回约定字段。达梦窗口语句附带的行号不属于此接口，未记录的时间保留空值。
var flows = selected.map(row => {
    return {
        id: row.id, taskName: row.taskName, currentStatus: row.currentStatus,
        processGroupId: row.processGroupId, latestRunTime: row.latestRunTime, endTime: row.endTime
    }
})
return {total: counts.total, running: counts.running, failed: counts.failed, flows: flows}
