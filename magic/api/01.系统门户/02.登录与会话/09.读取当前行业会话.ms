{
  "id": "portal_session_v1",
  "groupId": "652292437ed443acba737511e3597522",
  "name": "09.读取当前行业会话",
  "path": "/session",
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
  "description": "按可信行业会话读取租户资料与账号来源。与同址另一方法共用 /tenant-account/session。"
}
================================
import '@/tenant-account/session' as read

// GET /portal/sso/session。与同址另一方法共用 /tenant-account/session。
return read()
