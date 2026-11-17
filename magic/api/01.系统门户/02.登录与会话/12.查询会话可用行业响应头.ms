{
  "id": "portal_tenants_head_v1",
  "groupId": "652292437ed443acba737511e3597522",
  "name": "12.查询会话可用行业响应头",
  "path": "/tenants",
  "method": "HEAD",
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

// HEAD /portal/sso/tenants。与同址另一方法共用 /tenant-account/tenants。
return read()
