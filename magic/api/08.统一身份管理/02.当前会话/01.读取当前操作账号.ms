{
  "properties" : { },
  "id" : "787f8928738b51ad96291bd03969907e",
  "script" : null,
  "groupId" : "38dfb5ef9c2052b0acee3e477054544d",
  "name" : "01.读取当前操作账号",
  "createTime" : 1790612165860,
  "updateTime" : 1794837904654,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/me",
  "method" : "GET",
  "parameters" : [ ],
  "options" : [ {
    "name" : "access",
    "value" : "IDAAS_PLATFORM_SESSION",
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
  "description" : "校验平台会话，读取本人资料、身份域、功能权限及应用管理范围，供控制台展示当前登录状态。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：01.读取当前操作账号
// 路由：GET /idaas/session/me
// 业务说明：校验平台会话，读取本人资料、身份域、功能权限及应用管理范围，供控制台展示当前登录状态。
import identityRuntime;
import '@/idaas/api-call' as apiCall;
import '@/idaas/session' as execute;

return identityRuntime.control(()=>apiCall(()=>{
    return execute();
}));
