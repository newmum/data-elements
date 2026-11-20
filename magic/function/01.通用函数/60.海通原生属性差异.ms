{
  "properties" : { },
  "id" : "nifi_attribute_preview_v1",
  "script" : null,
  "groupId" : "0f27d7cf99494ceca369c14f35fbb987",
  "name" : "60.海通原生属性差异",
  "createTime" : null,
  "updateTime" : 1795047201833,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/nifiAttributePreview",
  "description" : "原生属性 GET 和 HEAD 共用租户校验和原生快照。",
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "pipelineId",
    "value" : null,
    "description" : "当前租户流程标识",
    "required" : false,
    "dataType" : "Object",
    "type" : "java.lang.Object",
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ]
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

// @/common/nifiAttributePreview 为 GET、HEAD 读取当前租户画布和原生属性差异。
// NiFi 属性协议适配从一次原生处理器列表组装差异和版本，预览不修改记录。

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