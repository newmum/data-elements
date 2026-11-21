{
  "properties": {},
  "id": "nifi_pipeline_ui_link_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "07.查询流程原生地址",
  "path": "/link",
  "method": "GET",
  "description": "读取当前行业租户已部署流程的 NiFi 原生页面地址。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    },
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFindCanvas' as findCanvas
import '@/common/nifiPipelineReadHttp' as withHttpResponse
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiNativeView as nativeView
import org.springframework.http.HttpHeaders
import dataScope
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import org.springframework.http.ResponseEntity
import tenantRuntime

// 按查询参数 id 返回当前行业租户已部署流程的 NiFi 原生链接。
// 通过当前平台会话签发十分钟只读详情，按任务绑定节点访问，凭据留在服务端。

var flowId = param.id

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
    if (dataScope.dataSource().scope != 'ALL') {
        return ResponseEntity.status(403).body({error: '当前角色请通过海通画布查看流程详情'})
    }
    // 检查已关联的原生组。新草稿或失败的替换部署不会隐藏旧的已部署流程。
    var groupId = canvas.nifiProcessGroupId()
    if (groupId == null || groupId.isBlank()) {
        return ResponseEntity.badRequest().body({error: '当前任务尚未部署到 NiFi，请先部署流程'})
    }
    var nodeId = canvas.nodeMapping()?.runtimeNodeId()
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, nativeView.open(nodeId, groupId)).body({
        url: '/nifi-ui/#/process-groups/' + groupId, processGroupId: groupId,
        reusesBrowserSession: true, authenticatedByBackend: true, readOnly: true})
})
