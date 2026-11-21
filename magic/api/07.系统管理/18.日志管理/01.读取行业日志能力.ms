{
  "id": "tenant_log_capabilities_get_v1",
  "groupId": "sys_log_management_v1",
  "name": "01.读取行业日志能力",
  "path": "/capabilities",
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
  "description": "按当前行业会话及有效角色、菜单计算日志能力。"
}
================================
import '@/common/logTenantCapabilities' as capabilities
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

// GET /sym/logs/capabilities。行业权限由 /common/logTenantCapabilities 统一计算。
return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    .body({code: 0, success: true, data: capabilities()})
