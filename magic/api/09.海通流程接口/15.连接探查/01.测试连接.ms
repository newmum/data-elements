{
  "properties": {},
  "id": "nifi_connection_test_post_v1",
  "script": null,
  "groupId": "nifi_connection_probe_group_v1",
  "name": "01.测试连接",
  "path": "/connect",
  "method": "POST",
  "description": "根据组件标识和连接配置验证协议连通性。登记的 Hive 配置从当前租户读取，托管连接由服务端网关探查。返回原始 success、诊断和 HTTP 状态，不写业务记录。",
  "parameters": [],
  "options": [
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
import '@/common/nifiConnectionHealth' as probeHealth
import java.util.Map

// POST /nifi/api/connect 校验节点的协议连通性，接收 manifestKey 和 config。
// 连通性探查和错误状态与数据源登记共用 nifiConnectionHealth。

return withHttp(() => {
    var signature = 'org.springframework.http.ResponseEntity<?> com.linewell.dataelement.integration.nifi.canvas.controller.ConnectionTestController.test(java.util.Map<java.lang.String, java.lang.Object>)'
    var input = readBody(body, Map, 'java.util.Map', signature)
    return probeHealth(input.manifestKey, input.config)
})
