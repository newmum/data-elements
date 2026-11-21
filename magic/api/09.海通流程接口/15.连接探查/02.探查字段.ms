{
  "properties": {},
  "id": "nifi_columns_probe_post_v1",
  "script": null,
  "groupId": "nifi_connection_probe_group_v1",
  "name": "02.探查字段",
  "path": "/columns",
  "method": "POST",
  "description": "探查单表字段，或以 datasourceId 和 tables 一次探查当前租户可读的已登记 MySQL 来源库的 1 至 100 张表。托管连接的表必须属于所选库。不写业务记录。",
  "parameters": [],
  "options": [
    {
      "name": "jsonBody",
      "value": "CACHED"
    },
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiPipelineReadHttp' as withHttp
import '@/common/nifiProbeSettings' as probeSettings
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver as connectionProperties
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.LinkedHashMap
import java.util.List
import java.util.Map
import nifiConnectionProbe
import org.springframework.http.ResponseEntity
import tenantRuntime

// POST /nifi/api/columns 读取选中表的字段。显式表名优先于配置里的表名。
// 批量使用当前角色可读的 MySQL 登记源。连接、凭据和实际库名不接受浏览器覆盖。

return withHttp((unused, textParameter, rootCause) => {
    var signature = 'org.springframework.http.ResponseEntity<?> com.linewell.dataelement.integration.nifi.canvas.controller.ConnectionTestController.columnsProbe(java.util.Map<java.lang.String, java.lang.Object>)'
    var input = readBody(body, Map, 'java.util.Map', signature)
    var manifest = input.manifestKey == null ? null : input.manifestKey + ''
    if (input.containsKey('tables')) {
        if (manifest != 'source.mysql' || !(input.tables instanceof List)
            || input.tables.isEmpty() || input.tables.size() > 100
            || input.datasourceId == null || (input.datasourceId + '').isBlank()) {
            return ResponseEntity.badRequest().body({success: false, error: '请选择已登记的 MySQL 来源和 1 至 100 张表'})
        }
        var tenantId = tenantRuntime.id()
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(401).body({success: false, error: '无法识别当前租户，请重新登录'})
        }
        var datasourceId = (input.datasourceId + '').trim()
        var registered = null
        try {
            registered = connectionProperties.resolveBatch(tenantId, [datasourceId], 'DATA_SOURCE').get(datasourceId)
        } catch (error) {
            if (!(rootCause(error) instanceof IllegalStateException)) { throw error }
            return ResponseEntity.status(403).body({success: false, error: '当前租户数据源不存在或无访问权限'})
        }
        try {
            return ResponseEntity.ok(nifiConnectionProbe.mysqlColumnsByTable(registered, input.tables))
        } catch (error) {
            var failure = nifiConnectionProbe.failure(error)
            return ResponseEntity.status(failure.invalid ? 400 : 200).body({success: false,
                error: failure.invalid ? failure.message : '读取已登记 MySQL 来源库字段失败'})
        }
    }
    var config = input.config instanceof Map ? new LinkedHashMap(input.config) : {}
    var table = input.table == null ? '' : (input.table + '').trim()
    if (table == '') {
        table = config.table == null ? '' : (config.table + '').trim()
    }
    try {
        var prepared = probeSettings(manifest, config, 'columns', table, null, null)
        if (table == '') {
            throw new IllegalArgumentException('缺少表名: table')
        }
        var result = prepared.gateway == null ? nifiConnectionProbe.columns(manifest, prepared.config, table)
            : nifiConnectionProbe.hiveGateway(prepared.gateway)
        return ResponseEntity.ok(result)
    } catch (error) {
        var failure = nifiConnectionProbe.failure(error)
        return ResponseEntity.status(failure.invalid ? 400 : 200).body({success: false, error: failure.message})
    }
})
