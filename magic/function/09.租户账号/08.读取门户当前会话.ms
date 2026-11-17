{
  "id": "portal_fn_session_v1",
  "groupId": "ga_local_accounts_functions_01",
  "name": "08.读取门户当前会话",
  "path": "/session",
  "parameters": [],
  "description": "GET 和 HEAD 当前会话共用，使用可信租户与当前令牌账号源返回租户资料和可用租户。"
}
================================
import '@/tenant-account/available' as available
import '@/tenant-account/profile' as profile
import '@/tenant-account/response' as respond
import com.linewell.dataelement.platform.tenant.application.TenantSessions
import tenantRuntime

// /tenant-account/session 读取行业会话及其实际可访问租户，忽略外部租户参数。
return respond(() => {
    var tenantId = tenantRuntime.id()
    var local = TenantSessions.isLocal()
    var current = profile(tenantId, false)
    var choices = available(tenantId, local, current)
    return {...current, available: choices, accountSource: local ? 'LOCAL' : 'CONTROL'}
})
