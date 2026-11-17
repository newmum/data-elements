{
  "id": "portal_settings_head_v1",
  "groupId": "652292437ed443acba737511e3597522",
  "name": "08.查询行业登录设置响应头",
  "path": "/settings",
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
  "description": "按启用行业读取公开门户字段。与同址另一方法共用 /tenant-account/settings。"
}
================================
import '@/tenant-account/settings' as read

// HEAD /portal/sso/settings。与同址另一方法共用 /tenant-account/settings。
return read()
