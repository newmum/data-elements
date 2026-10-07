{
  "properties": {},
  "id": "ce1b7dafa9405755b263b849c2c9dc96",
  "script": null,
  "groupId": "b3202123a27e56198aed6211ec0f770f",
  "name": "05.人员目录重试待开通",
  "createTime": 1790612165860,
  "updateTime": 1790946368174,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/retry",
  "description": "当前直接返回业务错误 503，说明旧人员目录重试流程尚未开通；不读取或修改人员资料，也不会后台执行任务。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：05.人员目录重试待开通
// 路由：POST /idaas/users/retry
// 业务说明：当前直接返回业务错误 503，说明旧人员目录重试流程尚未开通；不读取或修改人员资料，也不会后台执行任务。
import identityRuntime;
import '@/idaas/api-call' as apiCall;

return identityRuntime.control(()=>apiCall(()=>{
    exit 503,'中央目录写入将在第二阶段开通';
}));
