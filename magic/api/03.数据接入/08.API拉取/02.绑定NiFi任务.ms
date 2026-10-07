{
  "properties": {},
  "id": "ods_api_pull_bind_task_01",
  "script": null,
  "groupId": "ods_api_pull_group_01",
  "name": "02.绑定NiFi任务",
  "path": "/bind-task",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "API 拉取登记、表规则与 NiFi 任务绑定；不保存明文凭据；使用请求参数 datasourceId、sourceTableId、pipelineId。"
}
================================
// 接口：绑定 NiFi 任务
// 业务说明：显式创建接入任务后，把对应 API 表级规则与 NiFi 流程关联；其他类型数据源安全跳过。
import tenantRuntime
import apiPullConfig

var tenantId = tenantRuntime.id()
var datasourceId = body?.datasourceId == null ? '' : ('' + body.datasourceId).trim()
var sourceTableId = body?.sourceTableId == null ? '' : ('' + body.sourceTableId).trim()
var pipelineId = body?.pipelineId == null ? '' : ('' + body.pipelineId).trim()
if (datasourceId == '') { exit 400, '数据源标识不能为空' }
if (sourceTableId == '') { exit 400, '来源表标识不能为空' }
if (pipelineId == '') { exit 400, 'NiFi 流程标识不能为空' }

var datasource = db.selectOne("""
    SELECT db_type, pool_cfg
      FROM db_datasource_t
     WHERE tid = #{datasourceId}
       AND tenant_id = #{tenantId}
       AND is_del = 0
""")
if (datasource == null) { exit 400, '数据源不存在或不属于当前租户' }

// 只有 API 拉取或采集数据源才存在需要绑定的表级规则。
var type = datasource.db_type == null ? '' : ('' + datasource.db_type).toLowerCase()
var pool = datasource.pool_cfg == null ? '' : ('' + datasource.pool_cfg).replaceAll('\\s', '').toLowerCase()
if (type != 'api' || !(pool.contains('"accessmode":"capture"') || pool.contains('"accessmode":"pull"'))) {
    return {applicable: false, message: '当前来源不是 API 数据拉取，未绑定表规则'}
}

// 仅维护已保存的规则，不在这里启动调度或创建 NiFi 流程。
apiPullConfig.prepareRuleSchema()
var changed = db.update("""
    UPDATE data_pull_rule_t
       SET nifi_task_id = #{pipelineId},
           updated_by = 'system',
           updated_time = NOW(6)
     WHERE tenant_id = #{tenantId}
       AND datasource_id = #{datasourceId}
       AND source_table_id = #{sourceTableId}
       AND is_del = 0
""")
return {applicable: true, pipelineId: pipelineId, boundRules: changed,
        message: changed > 0
            ? '表级拉取规则已绑定 NiFi 任务；可在画布中部署、手动启动或按 Cron 调度'
            : '当前表尚未同步出 API 拉取规则，请返回第二步保存表标注后重试'}
