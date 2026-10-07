{
  "properties": {
    "localAccountRetirement": "v1"
  },
  "id": "0db56c0aa4a5420ebc7f303b2fc06e8b",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "06.用户保存修改",
  "createTime": null,
  "updateTime": 1786690392991,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"realName\": \"叶华\",\n  \"phone\": \"13800000000\",\n  \"userName\": \"yehua\",\n  \"password\": \"******\",\n  \"status\": 1,\n  \"orgId\": \"组织ID\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"id\": \"用户ID\"\n}",
  "description": "应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.用户保存修改
// 路由：POST /sym/user/saveOrUpdate
// 业务说明：应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役。
import '@/tenant-account/users' as localUsers
return localUsers('save',body||{});
