{
  "properties": {},
  "id": "api_pull_service_list_01",
  "script": null,
  "groupId": "tenant_magic_api_pull_20261003",
  "name": "01.API 拉取 - 接口服务列表",
  "path": "/api-pull/services",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "仅 API 数据拉取来源读取当前租户的数据源接口服务；不返回认证明文。"
}
================================
// 接口：API 接口服务列表
// 业务说明：验证当前租户 API 拉取数据源后，读取服务配置；只返回凭据引用，不返回明文凭据。
import tenantRuntime
import apiPullConfig

var tenantId = tenantRuntime.id()
var sourceId = datasourceId == null ? '' : ('' + datasourceId).trim()
if (sourceId == '') { exit 400, '数据源标识不能为空' }
apiPullConfig.validateDatasource(sourceId)

return db.select("""
    SELECT tid,
           datasource_id,
           service_name,
           service_code,
           base_url,
           path_prefix,
           protocol,
           service_type,
           media_type,
           network_mode,
           outbound_allowlist,
           connect_timeout_ms,
           read_timeout_ms,
           max_response_bytes,
           tls_mode,
           client_cert_ref,
           common_headers_json,
           auth_type,
           credential_ref,
           auth_config_json,
           status,
           created_time,
           updated_time
      FROM data_pull_service_t
     WHERE tenant_id = #{tenantId}
       AND datasource_id = #{sourceId}
       AND is_del = 0
     ORDER BY service_name, tid
""")
