{
  "properties": {},
  "id": "nifi_columns_probe_post_v1",
  "script": null,
  "groupId": "nifi_connection_probe_group_v1",
  "name": "02.探查字段",
  "path": "/columns",
  "method": "POST",
  "description": "根据 manifestKey、config 和表名探查字段结构。登记的 Hive 配置从当前租户读取，托管连接的表必须属于所选库。返回字段及诊断，不写业务记录。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiPipelineReadHttp' as withHttp
import '@/common/nifiProbeSettings' as probeSettings
import java.lang.IllegalArgumentException
import java.util.LinkedHashMap
import java.util.Map
import nifiConnectionProbe
import org.springframework.http.ResponseEntity

// POST /nifi/api/columns 读取选中表的字段。显式表名优先于配置里的表名。
// 登记源限定当前租户，华为数仓网关只探查所选库。返回原始字段和诊断。

return withHttp(() => {
    var signature = 'org.springframework.http.ResponseEntity<?> com.linewell.dataelement.integration.nifi.canvas.controller.ConnectionTestController.columnsProbe(java.util.Map<java.lang.String, java.lang.Object>)'
    var input = readBody(body, Map, 'java.util.Map', signature)
    var manifest = input.manifestKey == null ? null : input.manifestKey + ''
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
