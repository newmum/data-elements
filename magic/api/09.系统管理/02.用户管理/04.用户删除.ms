{
  "properties": {
    "localAccountRetirement": "v1"
  },
  "id": "84d4f35904ab45d8a6a9c08ab67ece7e",
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "04.用户删除",
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"id\": \"用户ID\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"id\": \"用户ID\",\n  \"accountDeleted\": false\n}",
  "description": "应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 id。",
  "script": null
}
================================
// 接口：04.用户删除
// 路由：POST /sym/user/delete
// 业务说明：应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 id。
import '@/tenant-account/users' as localUsers
return localUsers('delete',{id:body?.id||id});
