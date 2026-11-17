{
  "id": "portal_session_v1",
  "groupId": "652292437ed443acba737511e3597522",
  "name": "07.读取当前行业会话",
  "path": "/session",
  "method": "GET",
  "parameters": [],
  "options": [
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
  "paths": [],
  "description": "返回可信行业会话、账号来源与可用租户。同一 GET 资源提供 HEAD。"
}
================================
import '@/tenant-account/available' as available
import '@/tenant-account/profile' as profile
import '@/tenant-account/response' as respond
import com.linewell.dataelement.platform.tenant.application.TenantSessions
import tenantRuntime

// /portal/sso/session 读取行业会话及其实际可访问租户，忽略外部租户参数。
return respond(() => {
    var tenantId = tenantRuntime.id()
    var local = TenantSessions.isLocal()
    var current = profile(tenantId, false)
    var choices = available(tenantId, local, current)
    return {...current, available: choices, accountSource: local ? 'LOCAL' : 'CONTROL'}
})
