{
  "properties": {},
  "id": "a52a5aa316e35c7783ce6820cdfa9435",
  "script": null,
  "groupId": "7594148e987057f087c5dca083c0c93c",
  "name": "01.查询人员身份资料列表",
  "createTime": 1790615423659,
  "updateTime": 1791193400395,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "description": "读取符合请求筛选条件的人员身份资料，应用权限及身份域过滤后返回可展示字段；不发生写入。处理控制库的中央人员身份资料及版本，遵守身份域和机构范围，不生成租户本地密码。",
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
// 路由：GET /idaas/subjects/list
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
