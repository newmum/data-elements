{
  "id": "tenant_log_capabilities_head_v1",
  "groupId": "sys_log_management_v1",
  "name": "02.读取行业日志能力响应头",
  "path": "/capabilities",
  "method": "HEAD",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "按当前行业会话及有效角色、菜单计算日志能力。"
}
================================
import '@/common/logTenantCapabilities' as capabilities
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

// HEAD /sym/logs/capabilities。行业权限由 /common/logTenantCapabilities 统一计算。
return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    .body({code: 0, success: true, data: capabilities()})
