{
  "properties": {},
  "id": "nifi_attribute_sync_get_v1",
  "script": null,
  "groupId": "32618128861a58318b8524b74df21a23",
  "name": "02.查询原生属性差异",
  "path": "/attrs",
  "method": "GET",
  "description": "读取当前行业租户流程的原生属性差异及组件版本。",
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
import '@/common/nifiLifecycleResponse' as respond
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiAttributeSync
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import tenantRuntime

// GET /nifi/node/attrs。读取当前行业流程的一次原生处理器快照，返回调整前的属性差异和版本。

var pipelineId = param.id

return respond(rootCause => {
    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
    }
    var canvas = findCanvas(pipelineId, tenantId)
    if (canvas == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, '流程不存在')
    }
    // 属性读取与部署共用该任务持久化的运行节点，关闭后恢复原线程绑定。
    var runtime = nifi.bindAssignedNode(canvas.nodeMapping()?.runtimeNodeId())
    try {
        return ResponseEntity.ok(new NifiAttributeSync(nifi).preview(canvas))
    } finally {
        runtime.close()
    }
})
