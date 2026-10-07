{
  "properties": {},
  "id": "1f4fe3caa35253c4b54dbe1a13909ed4",
  "script": null,
  "groupId": "7e715ce4956a53b6a2ff54bbe74211af",
  "name": "01.查询人员任职列表",
  "createTime": 1790615423659,
  "updateTime": 1791193400395,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "description": "读取符合请求筛选条件的人员任职，应用权限及身份域过滤后返回可展示字段；不发生写入。管理人员与机构之间的任职关系，校验人员、机构和身份域后维护控制库记录。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：查询中央人员与任职列表
// 路由：GET /idaas/appointments/list
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
