{
  "properties": {},
  "id": "nifi_connection_health_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "51.海通协议连通性探查",
  "path": "/nifiConnectionHealth",
  "description": "为画布探查和数据源登记复用协议连通性检查。沿用当前行业的登记连接范围、托管 Hive 和协议诊断。",
  "parameters": [
    {
      "name": "manifestKey",
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "submittedConfig",
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiProbeSettings' as probeSettings
import java.util.LinkedHashMap
import java.util.Map
import nifiConnectionProbe
import org.springframework.http.ResponseEntity

// 画布和数据源登记共用连通性检查。登记源以当前行业会话校验，托管连接通过服务端网关探查。

var manifest = manifestKey == null ? null : manifestKey + ''
var config = submittedConfig instanceof Map ? new LinkedHashMap(submittedConfig) : {}
try {
    var prepared = probeSettings(manifest, config, 'health', null, null, null)
    var result = prepared.gateway == null ? nifiConnectionProbe.connect(manifest, prepared.config)
        : nifiConnectionProbe.hiveGateway(prepared.gateway)
    return ResponseEntity.ok(result)
} catch (error) {
    var failure = nifiConnectionProbe.failure(error)
    return ResponseEntity.status(failure.invalid ? 400 : 200).body({success: false, error: failure.message})
}
