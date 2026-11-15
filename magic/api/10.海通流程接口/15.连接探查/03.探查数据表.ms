{
  "properties": {},
  "id": "nifi_tables_probe_post_v1",
  "script": null,
  "groupId": "nifi_connection_probe_group_v1",
  "name": "03.探查数据表",
  "path": "/tables-probe",
  "method": "POST",
  "description": "根据组件连接配置探查可读的表或数据集合，支持关键词和 1 到 500 项限制。登记的 Hive 配置限定当前租户。返回表、总数或原始诊断，不写业务记录。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiPipelineReadHttp' as withHttp
import '@/common/nifiProbeSettings' as probeSettings
import java.lang.Integer
import java.lang.Math
import java.lang.Number
import java.util.LinkedHashMap
import java.util.Map
import nifiConnectionProbe
import org.springframework.http.ResponseEntity

// POST /nifi/api/tables-probe 按节点配置读取可选表和集合。接受关键词和最多 500 项限制。
// 登记源限定当前租户，业务失败保留既有 HTTP 状态与空表数组。

return withHttp(() => {
    var signature = 'org.springframework.http.ResponseEntity<?> com.linewell.dataelement.integration.nifi.canvas.controller.ConnectionTestController.tablesProbe(java.util.Map<java.lang.String, java.lang.Object>)'
    var input = readBody(body, Map, 'java.util.Map', signature)
    var manifest = input.manifestKey == null ? null : input.manifestKey + ''
    var config = input.config instanceof Map ? new LinkedHashMap(input.config) : {}
    var keyword = input.keyword == null ? null : input.keyword + ''
    var limit = 200
    if (input.limit instanceof Number) {
        limit = Math.max(1, Math.min(500, input.limit.intValue()))
    } else if (input.limit != null) {
        try {
            limit = Math.max(1, Math.min(500, Integer.parseInt(input.limit + '')))
        } catch (ignored) {
            // 旧画布发送无法转换的限制时沿用 200 项。
        }
    }
    try {
        var prepared = probeSettings(manifest, config, 'tables', null, keyword, limit)
        var result = prepared.gateway == null ? nifiConnectionProbe.tables(manifest, prepared.config, keyword, limit)
            : nifiConnectionProbe.hiveGateway(prepared.gateway)
        return ResponseEntity.ok(result)
    } catch (error) {
        var failure = nifiConnectionProbe.failure(error)
        return ResponseEntity.status(failure.invalid ? 400 : 200).body({success: false, error: failure.message, tables: []})
    }
})
