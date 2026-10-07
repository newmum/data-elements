{
  "properties": {},
  "id": "dc4619ca6349542791baee8cb1cb9f25",
  "script": null,
  "groupId": "b3202123a27e56198aed6211ec0f770f",
  "name": "04.人员目录操作占位查询",
  "createTime": 1790612165860,
  "updateTime": 1790946368174,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/operations",
  "description": "当前仅核验平台会话具有中央人员写入权限，然后返回空列表；尚未读取或生成操作记录，此接口保留旧调用兼容。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：04.人员目录操作占位查询
// 路由：GET /idaas/users/operations
// 业务说明：当前仅核验平台会话具有中央人员写入权限，然后返回空列表；尚未读取或生成操作记录，此接口保留旧调用兼容。
import identityRuntime;
import '@/idaas/api-call' as apiCall;
import '@/idaas/directory-scope' as directoryScope;

return identityRuntime.control(()=>apiCall(()=>{
    directoryScope('users:write','workforce');
    return [];
}));
