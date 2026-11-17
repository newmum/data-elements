{
  "id": "portal_options_head_v1",
  "groupId": "652292437ed443acba737511e3597522",
  "name": "06.查询登录行业响应头",
  "path": "/options",
  "method": "HEAD",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "PUBLIC"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "公开读取可用于登录的行业列表。与同址另一方法共用 /tenant-account/options。"
}
================================
import '@/tenant-account/options' as read

// HEAD /portal/sso/options。与同址另一方法共用 /tenant-account/options。
return read()
