{
  "properties": {},
  "id": "nifi_pipeline_ui_link_http_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "49.海通流程原生地址响应",
  "path": "/nifiPipelineUiLink",
  "description": "为原生地址 GET 和 HEAD 核对租户画布及现有部署关联。",
  "parameters": [
    {
      "name": "flowId",
      "description": "请求路径中的画布标识",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": []
}
================================
import '@/common/nifiFindCanvas' as findCanvas
import '@/common/nifiPipelineReadHttp' as withHttpResponse
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import org.springframework.http.ResponseEntity
import tenantRuntime

// @/common/nifiPipelineUiLink 为 GET 和 HEAD 返回当前租户画布的原生 NiFi 链接。
// 已绑定节点返回该节点原生地址，历史画布沿用原有的原生页面代理。

return withHttpResponse((parameterValues, textParameter, rootCause) => {
    // 请求只以当前可信行业租户及路径标识定位画布。
    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
    }
    var canvas = findCanvas(flowId, tenantId)
    if (canvas == null) {
        return ResponseEntity.notFound().build()
    }
    // 检查已关联的原生组。新草稿或失败的替换部署不会隐藏旧的已部署流程。
    var groupId = canvas.nifiProcessGroupId()
    if (groupId == null || groupId.isBlank()) {
        return ResponseEntity.badRequest().body({error: '当前任务尚未部署到 NiFi，请先部署流程'})
    }
    var nodeId = canvas.nodeMapping()?.runtimeNodeId()
    if (nodeId != null && !nodeId.isBlank()) {
        return ResponseEntity.ok({url: nifi.assignedNodeUiUrl(nodeId, groupId), processGroupId: groupId,
            reusesBrowserSession: false, authenticatedByBackend: false, directNode: true})
    }
    return ResponseEntity.ok({url: '/nifi-ui/#/process-groups/' + groupId, processGroupId: groupId,
        reusesBrowserSession: true, authenticatedByBackend: true})
})
