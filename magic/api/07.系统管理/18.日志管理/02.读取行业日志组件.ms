{
  "id": "tenant_log_components_get_v1",
  "groupId": "sys_log_management_v1",
  "name": "02.读取行业日志组件",
  "path": "/components",
  "method": "GET",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "auditMode",
      "value": "LOG_READER"
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
  "description": "按行业会话与现行日志菜单授权读取共享编译组件。"
}
================================
import '@/common/logTenantCapabilities' as capabilities
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import '@/common/logComponentBundle' as logComponentBundle
import com.linewell.dataelement.model.common.CommonResponse

// GET /sym/logs/components。行业权限由 /common/logTenantCapabilities 统一计算。
var allowed = capabilities()
if (allowed.isEmpty()) {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        .body(CommonResponse.fail(403, '没有日志访问权限', 'ACCESS_DENIED'))
}
return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    .body({code: 0, success: true, data: logComponentBundle()})
