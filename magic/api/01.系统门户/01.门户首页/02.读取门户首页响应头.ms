{
  "id": "portal_public_index_head_v1",
  "groupId": "4e0e58d9dda153609f1d61b05d162791",
  "name": "02.读取门户首页响应头",
  "path": "/index",
  "method": "HEAD",
  "description": "沿用同址 GET 的门户配置读取和公开访问策略，HTTP 响应不含正文。",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "PUBLIC",
      "dataType": "String",
      "required": false
    }
  ],
  "headers": [],
  "paths": []
}
================================
import systemConfig

// HEAD /portal/main/index。与同址 GET 共用 systemConfig.publicSettings 的配置读取。
return systemConfig.publicSettings()
