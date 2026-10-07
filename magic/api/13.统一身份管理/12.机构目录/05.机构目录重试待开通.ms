{
  "properties": {},
  "id": "c663226508655ed98fc46eeb20b051e6",
  "script": null,
  "groupId": "862919dc32c65a92b119a012b4e39477",
  "name": "05.机构目录重试待开通",
  "createTime": 1790612165860,
  "updateTime": 1790946368174,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/retry",
  "description": "当前直接返回业务错误 503，说明旧机构目录重试流程尚未开通；不读取或修改机构资料，也不会后台执行任务。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：05.机构目录重试待开通
// 路由：POST /idaas/orgs/retry
// 业务说明：当前直接返回业务错误 503，说明旧机构目录重试流程尚未开通；不读取或修改机构资料，也不会后台执行任务。
import identityRuntime;
import '@/idaas/api-call' as apiCall;

return identityRuntime.control(()=>apiCall(()=>{
    exit 503,'中央目录写入将在第二阶段开通';
}));
