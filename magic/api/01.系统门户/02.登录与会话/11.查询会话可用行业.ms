{
  "id": "portal_tenants_v1",
  "groupId": "652292437ed443acba737511e3597522",
  "name": "11.查询会话可用行业",
  "path": "/tenants",
  "method": "GET",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "读取当前账号有权使用的行业。与同址另一方法共用 /tenant-account/tenants。"
}
================================
import '@/tenant-account/tenants' as read

// GET /portal/sso/tenants。与同址另一方法共用 /tenant-account/tenants。
return read()
