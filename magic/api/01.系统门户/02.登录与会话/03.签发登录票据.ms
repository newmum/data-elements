{
  "properties": {},
  "id": "9ea20d0b193b46d5988a997fa522965f",
  "script": null,
  "groupId": "652292437ed443acba737511e3597522",
  "name": "03.签发登录票据",
  "createTime": 1782660716313,
  "updateTime": 1784437771366,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/ticket",
  "method": "GET",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "LEGACY_SSO_TICKET"
    }
  ],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "核验当前登录会话，为已登录用户生成业务系统可使用的短期登录票据并返回。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import legacySso;

// 返回框架已在当前会话中向上游签发的票据。
return legacySso.verifiedTicket();
