{
  "id": "collect_status_v1",
  "groupId": "collection_api_group_v1",
  "name": "01.查询数据源采集状态",
  "path": "/status",
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
  "description": "按可信行业和角色可见数据源读取最近一次或指定采集任务，保留原状态及错误响应。"
}
================================
import '@/collect/job' as status
import '@/collect/jobs' as columns
import '@/collect/response' as respond
import '@/collect/scope' as visibleSources
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import java.util.Locale
import request
import tableRead
import tenantRuntime

// GET /dst/collect/status。按可信行业和角色可见数据源读取最近一次或指定采集任务，保留原状态及错误响应。

return respond(() => {
    var dbIds = request.getValues('dbId')
    if (dbIds == null) {
        dbIds = request.getValues('dbId[]')
    }
    if (dbIds == null || dbIds.isEmpty()) {
        return CommonResponse.fail(StatusCodeEnum.PARAMS_NOT_FOUND.getCode(), '缺少必要参数: dbId', '参数缺失')
    }
    var source = dbIds.join(',').trim()
    if (!source.matches('[A-Za-z0-9_-]{1,64}')) {
        throw new IllegalArgumentException('dbId 格式不正确')
    }
    var values = request.getValues('includeDeletedDetails')
    if (values == null) {
        values = request.getValues('includeDeletedDetails[]')
    }
    var flag = values == null || values.isEmpty() ? '' : values[0].trim().toLowerCase(Locale.ROOT)
    var withDeleted = ['true', 'on', 'yes', '1'].contains(flag)
    if (!withDeleted && !['', 'false', 'off', 'no', '0'].contains(flag)) {
        return CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(), '参数[includeDeletedDetails]类型错误，期望类型: boolean', '参数类型不匹配')
    }

    var tenantId = tenantRuntime.id()
    if (visibleSources(tenantId, [source]).isEmpty()) {
        return status(null, source, null, false)
    }
    var jobIds = request.getValues('jobId')
    if (jobIds == null) {
        jobIds = request.getValues('jobId[]')
    }
    var jobId = jobIds == null ? '' : jobIds.join(',').trim()
    var projection = columns('j', withDeleted)
    var active = tableRead.equal(tableRead.column('j', 'is_del', 'NUMBER'), 0)
    var owned = tableRead.equal(projection.datasource_id, source)
    var jobs = tableRead.from('metadata_table_collection_job_t', 'j', tenantId)
    jobs.where(active)
    jobs.where(owned)
    if (jobId != '') {
        jobs.where(tableRead.equal(projection.tid, jobId))
    }
    var selected = jobs.rows(projection, [projection.created_time, projection.tid], ['desc', 'desc'], 1, true)
    if (selected.isEmpty()) {
        return status(null, source, null, false)
    }

    // 前次成功时间排除本次任务，查询始终限定在同一来源和可信租户。
    var previous = tableRead.from('metadata_table_collection_job_t', 'j', tenantId)
    previous.where(active)
    previous.where(owned)
    previous.where(tableRead.equal(projection.status, 'SUCCEEDED'))
    previous.where(tableRead.not(tableRead.equal(projection.tid, selected[0].tid)))
    var success = previous.rows({finished: projection.finished_time}, [projection.finished_time, projection.tid],
        ['desc', 'desc'], 1, true)
    return status(selected[0], source, success.isEmpty() ? null : success[0].finished, withDeleted)
})
