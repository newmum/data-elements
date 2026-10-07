{
  "properties": {},
  "id": "142663c78c48525fa8dcda96fba493f2",
  "script": null,
  "groupId": "862919dc32c65a92b119a012b4e39477",
  "name": "01.查询中央机构列表",
  "createTime": 1790612165860,
  "updateTime": 1791193400395,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "description": "读取符合请求筛选条件的中央机构，应用权限及身份域过滤后返回可展示字段；不发生写入。处理控制库的中央机构和层级关系；不因本次读写自动修改租户机构。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：查询中央机构列表
// 路由：GET /idaas/orgs/list
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
    return directory('orgs','list',payload);
}));
