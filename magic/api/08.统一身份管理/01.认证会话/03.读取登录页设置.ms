{
  "properties" : { },
  "id" : "c5e8f3f5dc9d52d5b23709f6e3c2ec15",
  "script" : null,
  "groupId" : "846e66f7a9365dc4a13942533f262c0a",
  "name" : "03.读取登录页设置",
  "createTime" : 1790612165860,
  "updateTime" : 1794837792880,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/settings",
  "method" : "GET",
  "parameters" : [ ],
  "options" : [ {
    "name" : "access",
    "value" : "PUBLIC",
    "description" : null,
    "required" : false,
    "dataType" : "String",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ],
  "requestBody" : "{}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "匿名读取登录页可公开的品牌与安全设置，仅返回允许公开的字段，不暴露密钥及内部认证策略。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import '@/idaas/publicbrand' as publicBrand;

// GET /idaas/auth/settings。与同址 HEAD 共用 /idaas/publicbrand 的公开品牌读取。

return publicBrand();
