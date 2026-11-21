{
  "id": "collect_statuses_v1",
  "groupId": "collection_api_group_v1",
  "name": "02.批量查询数据源采集状态",
  "path": "/statuses",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "jsonBody",
      "value": "OBJECT_MAP"
    },
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "最多 500 个来源。批量校验逐 ID 范围，返回去重后的输入顺序。"
}
================================
import '@/collect/job' as status
import '@/collect/jobs' as columns
import '@/collect/response' as respond
import '@/collect/scope' as visibleSources
import com.linewell.dataelement.platform.configuration.MagicBootConfiguration
import java.util.List
import request
import tableRead
import tenantRuntime

// POST /dst/collect/statuses 按当前角色读取数据源的采集状态，不连接物理数据源。
return respond(() => {
    var jsonError = request.get().getAttribute(MagicBootConfiguration.MAGIC_COLLECTION_STATUS_JSON_ERROR)
    if (jsonError != null) {
        throw jsonError
    }
    if (!(body?.dbIds instanceof List)) {
        throw new IllegalArgumentException('dbIds 必须是数组')
    }
    if (body.dbIds.isEmpty() || body.dbIds.size() > 500) {
        throw new IllegalArgumentException('dbIds 需包含 1 至 500 个数据源标识')
    }
    var ids = []
    for (candidate in body.dbIds) {
        if (!(candidate instanceof String) || !candidate.trim().matches('[A-Za-z0-9_-]{1,64}')) {
            throw new IllegalArgumentException('dbId 格式不正确')
        }
        ids.add(candidate.trim())
    }
    ids = ids.distinct()
    var tenantId = tenantRuntime.id()
    if (visibleSources(tenantId, ids).size() != ids.size()) {
        throw new IllegalArgumentException('数据源不存在或无访问权限')
    }

    var projection = columns('j', false)
    var active = tableRead.equal(tableRead.column('j', 'is_del', 'NUMBER'), 0)
    var requested = tableRead.in(projection.datasource_id, ids)
    var jobs = tableRead.from('metadata_table_collection_job_t', 'j', tenantId)
    jobs.where(active)
    jobs.where(requested)

    // 在数据库内按来源各取一条，避免按请求 ID 逐项读取。
    var recent = jobs.ranked('r', projection, [projection.datasource_id],
        [projection.created_time, projection.tid], ['desc', 'desc'], true)
    var selected = recent.rows(columns('r', false), ids.size())
    var lastBySource = {}
    var previousBySource = {}
    if (!selected.isEmpty()) {
        for (row in selected) {
            lastBySource[row.datasource_id] = row
        }
        var successes = tableRead.from('metadata_table_collection_job_t', 'j', tenantId)
        successes.where(active)
        successes.where(requested)
        successes.where(tableRead.equal(projection.status, 'SUCCEEDED'))
        successes.where(tableRead.not(tableRead.in(projection.tid, selected.map(row => row.tid))))
        var ranked = successes.ranked('p', {source: projection.datasource_id, finished: projection.finished_time},
            [projection.datasource_id], [projection.finished_time, projection.tid], ['desc', 'desc'], true)
        for (row in ranked.rows({source: tableRead.column('p', 'source', 'TEXT'),
                finished: tableRead.column('p', 'finished', 'TIME')}, ids.size())) {
            previousBySource[row.source] = row.finished
        }
    }
    var result = []
    for (id in ids) {
        result.add(status(lastBySource[id], id, previousBySource[id], false))
    }
    return {items: result}
})
