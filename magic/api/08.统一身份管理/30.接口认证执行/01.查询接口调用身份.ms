{
  "properties": {},
  "id": "8733f3234268544bbe84d00927f55e99",
  "script": null,
  "groupId": "69d8099e140554c19b063ba451355aad",
  "name": "01.查询接口调用身份",
  "createTime": 1790636675438,
  "updateTime": 1790946368174,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/whoami",
  "description": "校验 Bearer 令牌的签名、受众、应用权限和撤销状态，返回当前接口调用身份及有效授权。",
  "parameters": [],
  "method": "GET",
  "options": [
    {
      "name": "access",
      "value": "IDAAS_API_BEARER"
    }
  ],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
import identityApi;

// 返回框架已核验的当前接口调用身份。来源、配额和 Bearer 权限由注册资源的准入策略校验。
return identityApi.verifiedCaller();
