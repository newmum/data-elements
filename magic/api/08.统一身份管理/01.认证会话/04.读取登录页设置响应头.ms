{
  "id": "idaas_public_brand_head_v1",
  "groupId": "846e66f7a9365dc4a13942533f262c0a",
  "name": "04.读取登录页设置响应头",
  "path": "/settings",
  "method": "HEAD",
  "description": "沿用同址 GET 的登录品牌读取和公开访问策略，HTTP 响应不含正文。",
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
import '@/idaas/publicbrand' as publicBrand;

// HEAD /idaas/auth/settings。与同址 GET 共用 /idaas/publicbrand 的公开品牌读取。
return publicBrand();
