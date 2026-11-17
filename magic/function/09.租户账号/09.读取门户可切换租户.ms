{
  "id": "portal_fn_tenants_v1",
  "groupId": "ga_local_accounts_functions_01",
  "name": "09.读取门户可切换租户",
  "path": "/tenants",
  "parameters": [],
  "description": "GET 和 HEAD 可用租户共用，按行业会话的账号来源和成员资格读取。"
}
================================
import '@/tenant-account/available' as available
import '@/tenant-account/response' as respond
import com.linewell.dataelement.platform.tenant.application.TenantSessions
import tenantRuntime

// /tenant-account/tenants 按现有令牌及可信租户读取可用租户。
return respond(() => available(tenantRuntime.id(), TenantSessions.isLocal(), null))
