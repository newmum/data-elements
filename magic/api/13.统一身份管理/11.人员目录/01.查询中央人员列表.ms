{
  "properties": {},
  "id": "a933e818d7bf53468de98461fe26d5f9",
  "script": null,
  "groupId": "b3202123a27e56198aed6211ec0f770f",
  "name": "01.查询中央人员列表",
  "createTime": 1790612165860,
  "updateTime": 1791193400395,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "description": "读取符合请求筛选条件的中央人员，应用权限及身份域过滤后返回可展示字段；不发生写入。处理控制库的中央人员资料，保持人员资料与租户本地账号隔离；仅显式下发时才影响租户。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：查询中央人员列表
// 路由：GET /idaas/users/list
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;
import '@/idaas/directory' as directory;

return identityRuntime.control(()=>apiCall(()=>{
    var payload={};
    for(key in ['domain','page','size','q','status','orgId','includeChildren']) {
        var input=request.getValues(key);
        if(input!=null && input.size()>0) {
            payload.put(key,input[0]);
        }
    }
    return directory('subjects','list',payload);
}));
