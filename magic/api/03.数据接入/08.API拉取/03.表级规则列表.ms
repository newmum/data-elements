{
  "properties": {},
  "id": "ods_api_pull_rules_01",
  "script": null,
  "groupId": "ods_api_pull_group_01",
  "name": "03.表级规则列表",
  "path": "/rules",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "API 拉取登记、表规则与 NiFi 任务绑定；不保存明文凭据；读取或计算后返回业务结果。"
}
================================
// 接口：API 表级拉取规则列表
// 业务说明：按当前租户与数据源读取规则；可选择指定逻辑表，不会创建或启动 NiFi 任务。
import tenantRuntime
import apiPullConfig

var tenantId = tenantRuntime.id()
var sourceId = datasourceId == null ? '' : ('' + datasourceId).trim()
if (sourceId == '') { exit 400, '数据源标识不能为空' }
var tableId = sourceTableId == null || ('' + sourceTableId).trim() == '' ? null : ('' + sourceTableId).trim()
apiPullConfig.validateDatasource(sourceId)

return db.select("""
    SELECT tid,
           datasource_id,
           source_table_id,
           service_id,
           rule_name,
           endpoint_method,
           endpoint_path,
           request_template_json,
           response_config_json,
           pagination_config_json,
           incremental_config_json,
           orchestration_config_json,
           mapping_config_json,
           quality_config_json,
           delete_config_json,
           runtime_config_json,
           trigger_mode,
           cron_expression,
           nifi_task_id,
           enabled,
           created_time,
           updated_time
      FROM data_pull_rule_t
     WHERE tenant_id = #{tenantId}
       AND datasource_id = #{sourceId}
       AND (#{tableId} IS NULL OR source_table_id = #{tableId})
       AND is_del = 0
     ORDER BY updated_time DESC, tid
""")
