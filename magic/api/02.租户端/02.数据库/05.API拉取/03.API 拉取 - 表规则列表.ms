{
  "properties": {},
  "id": "api_pull_rule_list_01",
  "script": null,
  "groupId": "tenant_magic_api_pull_20261003",
  "name": "03.API 拉取 - 表规则列表",
  "path": "/api-pull/rules",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "读取当前租户 API 拉取来源的逻辑表拉取规则；规则保存本身不创建 NiFi 任务。"
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
