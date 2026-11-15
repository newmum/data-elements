{
  "properties": {},
  "id": "nifi_attribute_sync_post_v1",
  "script": null,
  "groupId": "32618128861a58318b8524b74df21a23",
  "name": "04.同步原生属性",
  "path": "/attrs",
  "method": "POST",
  "description": "提交属性差异选择，按原生组件版本检查并同步。",
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
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFindCanvas' as findCanvas
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiLifecycleResponse' as respond
import '@/common/nifiWriteRuntime' as writeRuntime
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiAttributeSync
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiAttributeSync$Request as AttributeRequest
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import tenantRuntime

// POST /nifi/node/attrs 同步原生属性。 查询参数 id。
// POST /nifi/node/attrs 更新当前行业租户流程已预览的原生属性。
// 先校验请求体，再在共享锁内核对画布及原生组件版本。只对已同步或明确保留的值记录基准。

return respond(rootCause => {
    var oldType = 'com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiAttributeSync$Request'
    var signature = 'java.util.List<com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiAttributeSync$Change> '
        + 'com.linewell.dataelement.integration.nifi.canvas.controller.PipelineRunController.syncAttributes(java.lang.String,' + oldType + ')'
    var command = readBody(body, AttributeRequest, oldType, signature)
    return mutation.withMutationLock(() => {
        var tenantId = tenantRuntime.id()
        if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
            throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
        }
        var pipelineId = param.id
        var canvas = findCanvas(pipelineId, tenantId)
        if (canvas == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, '流程不存在')
        }
        var applied = new NifiAttributeSync(nifi).apply(canvas, command)
        for (change in applied.results()) {
            if (['SYNCED', 'KEPT_NATIVE'].contains(change.status())) {
                writeRuntime(pipelineId, tenantId, {mapping: canvas.nodeMapping().withAttributes(applied.baselines())})
                break
            }
        }
        return ResponseEntity.ok(applied.results())
    })
})
