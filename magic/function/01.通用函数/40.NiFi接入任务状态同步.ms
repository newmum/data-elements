{
  "properties": {},
  "id": "823dade9f14b4569af1edd40b2ec3ecc",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "40.NiFi接入任务状态同步",
  "path": "/nifiTaskState",
  "createTime": 1794632446250,
  "updateTime": null,
  "parameters": [
    {
      "name": "pipelineId",
      "description": "调用方已经核对归属的 NiFi 流程标识",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "operation",
      "description": "started 记录启动，stopped 记录停止，status 只更新状态，group 绑定或清空流程组",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "value",
      "description": "status 使用停用 0 或异常 2，group 使用流程组字符串或 null，启停时省略",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": [],
  "description": "在调用方的流程锁和事务边界内，按可信行业租户与已核验流程标识批量同步有效接入任务的启停、异常状态或流程组，返回影响行数。保留原更新人和秒级启停时间，不调用 NiFi。"
}
================================
import tenantRuntime
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.Number
import java.lang.String
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// @/common/nifiTaskState 通过已核验的 pipelineId 同步接入任务状态和流程组，返回更新行数。
// 从可信行业上下文取得租户，参与调用方已有事务。只更新当前租户的有效任务，不请求 NiFi。
var tenantId = tenantRuntime.id()
if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
    throw new IllegalStateException('接入任务状态只能在当前行业租户中同步')
}
if (!['started', 'stopped', 'status', 'group'].contains(operation)) {
    throw new IllegalArgumentException('不支持的接入任务状态操作')
}

// 空标识不能匹配未绑定任务，流程组同步沿用原先的空白标识跳过规则。
if (pipelineId == null || (operation == 'group' && pipelineId.trim().isEmpty())) {
    return 0
}

var values = {}
if (operation == 'group') {
    if (value != null && !(value instanceof String)) {
        throw new IllegalArgumentException('流程组标识必须是字符串或空值')
    }
    // 显式写入 null 用于解除部署，其他状态和运行时间仍由各自的操作维护。
    values.put('process_group_id', value)
} else if (operation == 'status') {
    if (!(value instanceof Number) || (value != 0 && value != 2)) {
        throw new IllegalArgumentException('仅状态更新支持停用或异常状态')
    }
    values.put('task_status', value.intValue())
} else {
    // 每次启停共用同一个秒级时间，保留上一次反向操作的时间。
    var started = operation == 'started'
    var runningAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss'))
    values.put('task_status', started ? 1 : 0)
    values.put('schedule_running', runningAt)
    values.put(started ? 'schedule_running_start' : 'schedule_running_end', runningAt)
}

// 用应用时钟记录更新时间，一条按条件更新覆盖流程的所有有效接入任务并保留原更新人。
values.put('updated_time', LocalDateTime.now())
return db.table('data_access_agg_task_t').exclude('updated_by').withBlank().where()
    .eq('tenant_id', tenantId).eq('is_del', 0).eq('pipeline_id', pipelineId)
    .update(values)
