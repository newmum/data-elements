{
  "id": "collect_fn_job_v1",
  "groupId": "collection_fn_group_v1",
  "name": "03.装配采集任务状态",
  "path": "/job",
  "parameters": [
    {
      "name": "row",
      "dataType": "Object",
      "required": false,
      "description": "可见的采集任务行"
    },
    {
      "name": "sourceId",
      "dataType": "Object",
      "required": false,
      "description": "已校验来源"
    },
    {
      "name": "previous",
      "dataType": "Object",
      "required": false,
      "description": "本任务之前的最近成功时间"
    },
    {
      "name": "withDeleted",
      "dataType": "Object",
      "required": false,
      "description": "是否返回删除详情"
    }
  ],
  "description": "单项和批量进度共用任务字段、百分比及尚未采集的缺省值。"
}
================================
import cn.hutool.extra.spring.SpringUtil
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.List
import java.util.Map

// /collect/job 装配单项或批量的采集状态。仅明确需要时解码删除详情。
if (row == null) {
    return {exists: false, status: 'NOT_COLLECTED', phase: 'NOT_COLLECTED', datasourceId: sourceId, progressPercent: 0}
}
var text = value => value == null ? '' : (value + '').trim()
var number = value => value == null ? 0 : (value + '')::long(0)
var state = text(row.status)
var phase = text(row.phase)
var total = number(row.total_count)
var persisted = number(row.persisted_count)
var percent = 0
if (state == 'SUCCEEDED') {
    percent = 100
} else if (state != 'FAILED') {
    if (phase == 'QUEUED' || phase == 'SCANNING') {
        percent = 8
    } else if (total <= 0) {
        percent = 92
    } else {
        percent = Math.max(10, Math.min(96, 10 + Math.round(86.0 * persisted / total)))
    }
}
var result = {
    exists: true, jobId: text(row.tid), datasourceId: sourceId, mode: text(row.collection_mode),
    status: state, phase: phase, scannedCount: number(row.scanned_count), persistedCount: persisted,
    totalCount: total, addedCount: number(row.added_count), unchangedCount: number(row.unchanged_count),
    deletedCount: number(row.deleted_count), startedAt: row.started_time, finishedAt: row.finished_time,
    savedBeforeCount: number(row.unchanged_count) + number(row.deleted_count),
    previousSuccessfulAt: previous, error: text(row.error_message), progressPercent: percent
}
if (withDeleted) {
    var deleted = []
    if (text(row.deleted_detail) != '') {
        try {
            var json = SpringUtil.getBean(ObjectMapper.class)
            var detailType = json.getTypeFactory().constructCollectionType(List.class, Map.class)
            var parsed = json.readValue(text(row.deleted_detail), detailType)
            if (parsed != null) {
                deleted = parsed
            }
        } catch (error) {
            // 旧采集任务的历史详情损坏时保留任务状态，详情返回空列表。
        }
    }
    result.deleted = deleted
}
return result
