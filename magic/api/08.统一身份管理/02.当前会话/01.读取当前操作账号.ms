{
  "properties": {},
  "id": "787f8928738b51ad96291bd03969907e",
  "script": null,
  "groupId": "38dfb5ef9c2052b0acee3e477054544d",
  "name": "01.读取当前操作账号",
  "createTime": 1790612165860,
  "updateTime": 1790946368174,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/me",
  "description": "校验平台会话，读取本人资料、身份域、功能权限及应用管理范围，供控制台展示当前登录状态。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
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
