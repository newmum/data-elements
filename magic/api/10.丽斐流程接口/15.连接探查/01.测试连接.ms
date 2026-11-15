{
  "properties": {},
  "id": "nifi_connection_test_post_v1",
  "script": null,
  "groupId": "nifi_connection_probe_group_v1",
  "name": "01.测试连接",
  "path": "/connection-test",
  "method": "POST",
  "description": "根据组件标识和连接配置验证协议连通性。登记的 Hive 配置从当前租户读取，托管连接由服务端网关探查。返回原始 success、诊断和 HTTP 状态，不写业务记录。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiPipelineReadHttp' as withHttp
import '@/common/nifiProbeSettings' as probeSettings
import java.util.LinkedHashMap
import java.util.Map
import nifiConnectionProbe
import org.springframework.http.ResponseEntity

// POST /nifi/api/connection-test 校验节点的协议连通性，接收 manifestKey 和 config。
// 配置来自登记源时校验当前租户，托管的华为数仓使用服务端网关。返回既有原始响应。

return withHttp(() => {
    var signature = 'org.springframework.http.ResponseEntity<?> com.linewell.dataelement.integration.nifi.canvas.controller.ConnectionTestController.test(java.util.Map<java.lang.String, java.lang.Object>)'
    var input = readBody(body, Map, 'java.util.Map', signature)
    var manifest = input.manifestKey == null ? null : input.manifestKey + ''
    var config = input.config instanceof Map ? new LinkedHashMap(input.config) : {}
    try {
        var prepared = probeSettings(manifest, config, 'health', null, null, null)
        var result = prepared.gateway == null ? nifiConnectionProbe.connect(manifest, prepared.config)
            : nifiConnectionProbe.hiveGateway(prepared.gateway)
        return ResponseEntity.ok(result)
    } catch (error) {
        var failure = nifiConnectionProbe.failure(error)
        return ResponseEntity.status(failure.invalid ? 400 : 200).body({success: false, error: failure.message})
    }
})
