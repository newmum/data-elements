{
  "id": "portal_tenants_v1",
  "groupId": "652292437ed443acba737511e3597522",
  "name": "08.查询会话可用行业",
  "path": "/tenants",
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
  "description": "按当前会话和成员资格返回可切换行业。同一 GET 资源提供 HEAD。"
}
================================
import '@/tenant-account/available' as available
import '@/tenant-account/response' as respond
import com.linewell.dataelement.platform.tenant.application.TenantSessions
import tenantRuntime

// /portal/sso/tenants 按现有令牌及可信租户读取可用租户。
return respond(() => available(tenantRuntime.id(), TenantSessions.isLocal(), null))
