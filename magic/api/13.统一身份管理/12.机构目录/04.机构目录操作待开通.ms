{
  "properties": {},
  "id": "b359f3c8593055f9aa8bcd4228f59504",
  "script": null,
  "groupId": "862919dc32c65a92b119a012b4e39477",
  "name": "04.机构目录操作待开通",
  "createTime": 1790612165860,
  "updateTime": 1790946368174,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/operations",
  "description": "当前直接返回业务错误 503，说明旧机构目录操作记录查询尚未开通；不读取或修改机构资料。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：04.机构目录操作待开通
// 路由：GET /idaas/orgs/operations
// 业务说明：当前直接返回业务错误 503，说明旧机构目录操作记录查询尚未开通；不读取或修改机构资料。
import identityRuntime;
import '@/idaas/api-call' as apiCall;

return identityRuntime.control(()=>apiCall(()=>{
    exit 503,'中央目录写入将在第二阶段开通';
}));
