{
  "properties": {},
  "id": "4bd2f846272751cab344701a3258027c",
  "script": null,
  "groupId": "846e66f7a9365dc4a13942533f262c0a",
  "name": "02.退出管理控制台",
  "createTime": 1790612165860,
  "updateTime": 1790946368174,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/logout",
  "description": "识别当前平台会话并撤销服务端令牌，记录退出事件，后续使用该令牌访问平台接口将失败。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：02.退出管理控制台
// 路由：POST /idaas/auth/logout
// 业务说明：识别当前平台会话并撤销服务端令牌，记录退出事件，后续使用该令牌访问平台接口将失败。
import identityRuntime;
import '@/idaas/api-call' as apiCall;
import platformSession;
import '@/idaas/context' as context;
import '@/idaas/audit' as audit;

return identityRuntime.control(()=>apiCall(()=>{
    var ctx=context();
    audit(ctx,'SUCCESS','auth:logout',ctx.userId,null,null);
    platformSession.logout();
    return true;
}));
